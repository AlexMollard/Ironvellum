"""All tool-directed I/O stays under one fixed, private directory."""
from contextlib import ExitStack, contextmanager, suppress
import os
from pathlib import Path
from uuid import uuid4

from .contract import MAX_CONTEXT, MAX_FRAME, Refusal, require

if os.name == "nt":
    from .windows_files import Directory, lock, validate_path
else:
    from .posix_files import Directory, check_mounts, lock


def selected(value):
    path = Path(value)
    require(path.is_absolute() and len(path.parts) > 1 and ".." not in path.parts, "absolute_path_required")
    require(not str(value).startswith(("//", "\\\\")), "local_path_required")
    if os.name == "nt":
        validate_path(path)
    return path


class LocalFiles:
    def __init__(self, context, outbox):
        self.context_path = selected(context)
        self.outbox_path = selected(outbox)
        if os.name != "nt":
            check_mounts(self.context_path, self.outbox_path)
        require(not self.context_path.is_relative_to(self.outbox_path), "overlapping_paths")

    def __enter__(self):
        self.stack = ExitStack()
        try:
            with ExitStack() as read_handles:
                context_dir = Directory(self.context_path.parent, read_handles)
                with context_dir.open(self.context_path.name, "rb") as stream:
                    self.context = stream.read(MAX_CONTEXT + 1)
                require(len(self.context) <= MAX_CONTEXT, "file_limit")
                self.dir = Directory(self.outbox_path, self.stack, private=True)
                require(self.dir.identity not in context_dir.ancestors | {context_dir.identity}, "overlapping_paths")
            return self
        except BaseException:
            self.stack.close()
            raise

    def __exit__(self, *exc):
        self.stack.close()

    @contextmanager
    def serialized(self):
        with self.dir.open(".ironvellum-lock-v1", "lock") as stream:
            lock(stream)
            require(os.fstat(stream.fileno()).st_size == 0, "outbox_conflict")
            yield

    def read(self, name, limit):
        with self.dir.open(name, "rb") as stream:
            data = stream.read(limit + 1)
        require(len(data) <= limit, "file_limit")
        return data

    def write(self, name, data, *, replace):
        tmp = f".ironvellum-{uuid4()}.tmp"
        with self.dir.open(tmp, "xb") as stream:
            try:
                stream.write(data)
                stream.flush()
                os.fsync(stream.fileno())
            except BaseException:
                stream.close()
                self.dir.unlink(tmp)
                raise
        try:
            if replace:
                self.dir.replace(tmp, name)
            else:
                self.dir.publish_new(tmp, name)
            self.dir.sync()
        finally:
            with suppress(FileNotFoundError):
                self.dir.unlink(tmp)

    def publish(self, name, raw):
        try:
            require(self.read(name, MAX_FRAME) == raw, "outbox_conflict")
        except FileNotFoundError:
            try:
                self.write(name, raw, replace=False)
            except FileExistsError:
                require(self.read(name, MAX_FRAME) == raw, "outbox_conflict")

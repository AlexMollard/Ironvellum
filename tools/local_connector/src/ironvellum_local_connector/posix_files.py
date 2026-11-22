"""Use open directory descriptors throughout, including after a directory rename."""
import ctypes
import errno
import fcntl
import os
from pathlib import Path
import re
import stat
import sys

from .contract import require


LOCAL_FS = {b"ext2", b"ext3", b"ext4", b"xfs", b"btrfs", b"zfs", b"tmpfs", b"overlay",
            b"f2fs", b"bcachefs", b"ntfs3", b"ufs", b"jfs", b"ramfs", b"erofs", b"squashfs"}


class DarwinStatfs(ctypes.Structure):
    # Darwin sys/mount.h, __DARWIN_STRUCT_STATFS64.
    _fields_ = [("prefix", ctypes.c_char * 64), ("flags", ctypes.c_uint32), ("subtype", ctypes.c_uint32),
                ("fstype", ctypes.c_char * 16), ("mount", ctypes.c_char * 1024),
                ("source", ctypes.c_char * 1024), ("reserved", ctypes.c_uint32 * 8)]


def mounted_paths():
    if sys.platform == "darwin":
        libc = ctypes.CDLL(None)
        mounted = ctypes.POINTER(DarwinStatfs)()
        list_mounts = getattr(libc, "getmntinfo$INODE64", libc.getmntinfo)
        count = list_mounts(ctypes.byref(mounted), 2)  # MNT_NOWAIT retrieves cached mount information.
        require(count > 0, "local_volume_required")
        for i in range(count):
            item = mounted[i]
            yield Path(os.fsdecode(item.mount)), bool(item.flags & 0x1000) and item.fstype != b"autofs"
    elif sys.platform.startswith("linux"):
        for line in Path("/proc/self/mountinfo").read_bytes().splitlines():
            prefix, tail = line.split(b" - ", 1)
            name = re.sub(rb"\\([0-7]{3})", lambda m: bytes([int(m[1], 8)]), prefix.split()[4])
            yield Path(os.fsdecode(name)), tail.split()[0] in LOCAL_FS
    else:
        require(False, "unsupported_platform")


def check_mounts(context, outbox):
    # Reject known remote mounts before opening their paths, including mountpoints beneath the outbox.
    for mount, local in mounted_paths():
        if not local:
            require(not (context.is_relative_to(mount) or outbox.is_relative_to(mount)
                         or mount.is_relative_to(outbox)), "local_volume_required")


def check_macos_acl(fd):
    # Mode bits on macOS do not override explicit grants. Require no extended ACL on the outbox.
    libc = ctypes.CDLL(None, use_errno=True)
    libc.acl_get_fd.restype = ctypes.c_void_p
    acl = libc.acl_get_fd(fd)
    if not acl:
        require(ctypes.get_errno() in (errno.ENOENT, errno.ENOTSUP), "private_outbox_required")
        return
    try:
        entry = ctypes.c_void_p()
        require(libc.acl_get_entry(ctypes.c_void_p(acl), 0, ctypes.byref(entry)) != 0
                and ctypes.get_errno() == errno.EINVAL, "private_outbox_required")
    finally:
        libc.acl_free(ctypes.c_void_p(acl))


def local_volume(fd):
    if sys.platform == "darwin":
        result = DarwinStatfs()
        libc = ctypes.CDLL(None)
        statfs = getattr(libc, "fstatfs$INODE64", libc.fstatfs)
        require(statfs(fd, ctypes.byref(result)) == 0 and result.flags & 0x1000, "local_volume_required")
    elif sys.platform.startswith("linux"):
        info = Path(f"/proc/self/fdinfo/{fd}").read_text()
        mount = next(line.split()[1] for line in info.splitlines() if line.startswith("mnt_id:"))
        mounts = Path("/proc/self/mountinfo").read_bytes().splitlines()
        fs = next((line.split(b" - ", 1)[1].split()[0] for line in mounts
                   if line.split(b" ", 1)[0] == mount.encode()), b"")
        require(fs in LOCAL_FS, "local_volume_required")
    else:
        require(False, "unsupported_platform")


class Directory:
    def __init__(self, path, stack, private=False, *, create=True):
        flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW
        fd = os.open("/", flags)
        stack.callback(os.close, fd)
        self.ancestors = set()
        for i, name in enumerate(path.parts[1:], 2):
            local_volume(fd)
            info = os.fstat(fd)
            self.ancestors.add((info.st_dev, info.st_ino))
            if create and private and i == len(path.parts):
                try:
                    os.mkdir(name, 0o700, dir_fd=fd)
                    os.fsync(fd)
                except FileExistsError:
                    pass
            fd = os.open(name, flags, dir_fd=fd)
            stack.callback(os.close, fd)
        local_volume(fd)
        if private:
            info = os.fstat(fd)
            require(info.st_uid == os.getuid() and not info.st_mode & 0o077, "private_outbox_required")
            if sys.platform == "darwin":
                check_macos_acl(fd)
        self.fd = fd
        self.identity = (os.fstat(fd).st_dev, os.fstat(fd).st_ino)

    def open(self, name, mode):
        if mode == "existing_lock":
            mode = "rb"
        flags = {"rb": os.O_RDONLY, "xb": os.O_WRONLY | os.O_CREAT | os.O_EXCL,
                 "lock": os.O_RDWR | os.O_CREAT}[mode]
        if mode != "xb":
            try:
                info = os.stat(name, dir_fd=self.fd, follow_symlinks=False)
                require(stat.S_ISREG(info.st_mode) and info.st_nlink == 1, "regular_file_required")
            except FileNotFoundError:
                if mode != "lock":
                    raise
        fd = os.open(name, flags | os.O_NOFOLLOW | os.O_NONBLOCK, 0o600, dir_fd=self.fd)
        try:
            info = os.fstat(fd)
            require(stat.S_ISREG(info.st_mode) and info.st_nlink == 1, "regular_file_required")
            local_volume(fd)
            return os.fdopen(fd, "r+b" if mode == "lock" else mode)
        except BaseException:
            os.close(fd)
            raise

    def publish_new(self, source, target):
        # An exclusive rename leaves exactly one name even if the process dies immediately afterward.
        libc = ctypes.CDLL(None, use_errno=True)
        if sys.platform == "darwin":
            code = libc.renameatx_np(self.fd, os.fsencode(source), self.fd, os.fsencode(target), 4)
        else:
            code = libc.renameat2(self.fd, os.fsencode(source), self.fd, os.fsencode(target), 1)
        if code:
            number = ctypes.get_errno()
            raise OSError(number, os.strerror(number))

    def replace(self, source, target):
        os.replace(source, target, src_dir_fd=self.fd, dst_dir_fd=self.fd)

    def unlink(self, name):
        os.unlink(name, dir_fd=self.fd)

    def sync(self):
        os.fsync(self.fd)


def lock(stream):
    fcntl.flock(stream.fileno(), fcntl.LOCK_EX)

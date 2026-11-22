"""Local Win32 handles pin directory ancestors against rename/reparse substitution."""
import msvcrt
import os
from pathlib import Path
import re

import ntsecuritycon
import pywintypes
import win32api
import win32con as c
import win32file as f
import win32security as sec

from .contract import Refusal, require


def validate_path(path):
    require(re.fullmatch(r"[A-Za-z]:", path.drive) and path.root == "\\", "local_path_required")
    is_reserved = getattr(os.path, "isreserved", None)
    if is_reserved is None:  # CPython 3.11 and 3.12
        is_reserved = lambda part: Path(part).is_reserved()
    require(all(":" not in part and not part.endswith((".", " ")) and not is_reserved(part)
                and all(ord(char) >= 32 and char not in '<>"|?*' for char in part)
                for part in path.parts[1:]), "invalid_path")
    require(f.GetDriveType(str(path.anchor)) in (2, 3, 6), "local_volume_required")


def _principals():
    token = sec.OpenProcessToken(win32api.GetCurrentProcess(), c.TOKEN_QUERY)
    try:
        user = sec.GetTokenInformation(token, sec.TokenUser)[0]
    finally:
        token.Close()
    return (user, sec.CreateWellKnownSid(sec.WinLocalSystemSid),
            sec.CreateWellKnownSid(sec.WinBuiltinAdministratorsSid))


def _private_attributes():
    acl = sec.ACL()
    for sid in _principals():
        acl.AddAccessAllowedAceEx(sec.ACL_REVISION, c.OBJECT_INHERIT_ACE | c.CONTAINER_INHERIT_ACE,
                                  f.FILE_ALL_ACCESS, sid)
    descriptor = sec.SECURITY_DESCRIPTOR()
    descriptor.SetSecurityDescriptorDacl(True, acl, False)
    descriptor.SetSecurityDescriptorControl(sec.SE_DACL_PROTECTED, sec.SE_DACL_PROTECTED)
    attrs = pywintypes.SECURITY_ATTRIBUTES()
    attrs.SECURITY_DESCRIPTOR = descriptor
    return attrs


def _private(handle):
    descriptor = sec.GetSecurityInfo(handle, sec.SE_FILE_OBJECT,
                                     sec.OWNER_SECURITY_INFORMATION | sec.DACL_SECURITY_INFORMATION)
    trusted = _principals()
    acl = descriptor.GetSecurityDescriptorDacl()
    require(descriptor.GetSecurityDescriptorOwner() in trusted and acl is not None, "private_outbox_required")
    for index in range(acl.GetAceCount()):
        ace = acl.GetAce(index)
        if ace[0][0] != sec.ACCESS_DENIED_ACE_TYPE:
            require(ace[0][0] == sec.ACCESS_ALLOWED_ACE_TYPE and ace[-1] in trusted, "private_outbox_required")


def _open(path, access, share, disposition, attributes=None):
    try:
        return f.CreateFile(str(path), access, share, attributes, disposition,
                            c.FILE_FLAG_BACKUP_SEMANTICS | f.FILE_FLAG_OPEN_REPARSE_POINT, None)
    except pywintypes.error as error:
        if error.winerror in (2, 3):
            raise FileNotFoundError from None
        if error.winerror in (80, 183):
            raise FileExistsError from None
        raise Refusal("storage_unavailable") from None


class Directory:
    def __init__(self, path, stack, private=False, *, create=True):
        validate_path(path)
        self.ancestors = set()
        for ancestor in [*reversed(path.parents), path]:
            if create and private and ancestor == path:
                try:
                    f.CreateDirectory(str(path), _private_attributes())
                except pywintypes.error as e:
                    if e.winerror != 183:
                        raise Refusal("storage_unavailable") from None
            handle = _open(ancestor, ntsecuritycon.FILE_TRAVERSE | ntsecuritycon.FILE_READ_ATTRIBUTES
                           | (c.READ_CONTROL if private and ancestor == path else 0),
                           c.FILE_SHARE_READ | c.FILE_SHARE_WRITE, c.OPEN_EXISTING)
            stack.callback(handle.Close)
            info = f.GetFileInformationByHandle(handle)
            if ancestor != path:
                self.ancestors.add((info[4], info[8], info[9]))
            require(info[0] & c.FILE_ATTRIBUTE_DIRECTORY and not info[0] & c.FILE_ATTRIBUTE_REPARSE_POINT,
                    "regular_directory_required")
        if private:
            _private(handle)
        self.path = path
        self.private = private
        self.identity = (info[4], info[8], info[9])

    def open(self, name, mode):
        existing_lock = mode == "existing_lock"
        if existing_lock:
            mode = "rb"
        access = {"rb": c.GENERIC_READ, "xb": c.GENERIC_WRITE, "lock": c.GENERIC_READ | c.GENERIC_WRITE}[mode]
        disposition = {"rb": c.OPEN_EXISTING, "xb": c.CREATE_NEW, "lock": c.OPEN_ALWAYS}[mode]
        if self.private:
            access |= c.READ_CONTROL
        sharing = c.FILE_SHARE_READ | (c.FILE_SHARE_WRITE if mode == "lock" or existing_lock else 0)
        handle = _open(self.path / name, access, sharing,
                       disposition, _private_attributes() if self.private and mode != "rb" else None)
        try:
            info = f.GetFileInformationByHandle(handle)
            if self.private:
                _private(handle)
            require(f.GetFileType(handle) == c.FILE_TYPE_DISK and not info[0] & (
                c.FILE_ATTRIBUTE_DIRECTORY | c.FILE_ATTRIBUTE_REPARSE_POINT) and info[7] == 1, "regular_file_required")
            flags = os.O_BINARY | {"rb": os.O_RDONLY, "xb": os.O_WRONLY, "lock": os.O_RDWR}[mode]
            fd = msvcrt.open_osfhandle(int(handle), flags)
            handle.Detach()
            return os.fdopen(fd, "r+b" if mode == "lock" else mode)
        finally:
            handle.Close()

    def publish_new(self, source, target):
        # Same-volume MoveFileEx without REPLACE_EXISTING publishes once, without a second link.
        try:
            f.MoveFileEx(str(self.path / source), str(self.path / target), f.MOVEFILE_WRITE_THROUGH)
        except pywintypes.error as e:
            if e.winerror in (80, 183):
                raise FileExistsError from None
            raise Refusal("storage_unavailable") from None

    def replace(self, source, target):
        f.MoveFileEx(str(self.path / source), str(self.path / target), c.MOVEFILE_REPLACE_EXISTING | f.MOVEFILE_WRITE_THROUGH)

    def unlink(self, name):
        (self.path / name).unlink()

    def sync(self):
        pass  # MoveFileEx uses MOVEFILE_WRITE_THROUGH.


def lock(stream):
    f.LockFileEx(msvcrt.get_osfhandle(stream.fileno()), 2, 1, 0, pywintypes.OVERLAPPED())

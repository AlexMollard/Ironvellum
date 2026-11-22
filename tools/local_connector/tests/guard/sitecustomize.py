"""Subprocess-only audit guard. asyncio on Windows uses a local socketpair to wake its loop."""
import os
import socket
import sys
from pathlib import Path

log = Path(os.environ["NETWORK_GUARD_LOG"])
log.write_text("active\n")

def guard(event, arguments):
    if event not in {"socket.connect", "socket.bind", "socket.listen", "socket.getaddrinfo", "socket.gethostbyname",
                     "socket.gethostbyaddr", "socket.getnameinfo", "socket.sendto", "subprocess.Popen", "os.system"}:
        return
    if os.name == "nt" and event in {"socket.connect", "socket.bind", "socket.listen"}:
        frame = sys._getframe(1)
        while frame:
            if frame.f_code.co_name in ("socketpair", "_fallback_socketpair") and frame.f_globals.get("__name__") == "socket":
                return
            frame = frame.f_back
    with log.open("a") as stream:
        stream.write(event + "\n")
    raise PermissionError("Offline test guard")

sys.addaudithook(guard)

# CPython does not emit a socket.listen audit event. Also catch an implicit bind via listen().
original_listen = socket.socket.listen
def guarded_listen(self, *args, **kwargs):
    guard("socket.listen", ())
    return original_listen(self, *args, **kwargs)

socket.socket.listen = guarded_listen

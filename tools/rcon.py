"""Minimal RCON client for the local Godzilla server (reads the port and password from server\\server.properties).

    D:\\voice-assistant\\.venv\\Scripts\\python.exe -I tools\\rcon.py "list" "godzilla ping"

Each argument is one command; output is printed as "> command" followed by the reply.
Talks to this instance's server (set $env:GZ_INSTANCE=N, or tools\dev.ps1 instance N once per worktree; default instance 0).
As a module:  from rcon import Rcon; r = Rcon(); print(r.cmd("list"))
"""
import os, socket, struct, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))     # python -I does not add the script's folder
import gz_env  # noqa: E402  (instance selection: GZ_INSTANCE or <worktree>\.gz_instance; default 0)

SERVER = gz_env.SERVER_DIR      # this instance's server folder: its server.properties has the RCON port and password


def server_properties():
    props = {}
    for line in open(os.path.join(SERVER, "server.properties"), encoding="utf-8"):
        if "=" in line and not line.startswith("#"):
            k, v = line.rstrip("\r\n").split("=", 1)
            props[k] = v
    return props


class Rcon:
    def __init__(self, host="127.0.0.1", port=None, password=None, timeout=20):
        props = server_properties()
        self.sock = socket.create_connection((host, int(port or props.get("rcon.port", 25577))), timeout=timeout)
        self._send(3, password or props["rcon.password"])
        if self._recv()[0] == -1:
            raise PermissionError("RCON password rejected")

    def _send(self, kind, body, ident=7):
        data = struct.pack("<ii", ident, kind) + body.encode("utf-8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(data)) + data)

    def _read(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.sock.recv(n - len(buf))
            if not chunk:
                raise ConnectionError("server closed the connection")
            buf += chunk
        return buf

    def _recv(self):
        size = struct.unpack("<i", self._read(4))[0]
        data = self._read(size)
        ident, kind = struct.unpack("<ii", data[:8])
        return ident, data[8:-2].decode("utf-8", "replace")

    def cmd(self, command):
        self._send(2, command)
        return self._recv()[1]

    def close(self):
        self.sock.close()


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    try:
        r = Rcon()
    except Exception as e:
        print("RCON not reachable:", e, file=sys.stderr)
        sys.exit(2)
    for c in sys.argv[1:]:
        print(f"> {c}\n{r.cmd(c)}")
    try:
        r.close()
    except OSError:
        pass

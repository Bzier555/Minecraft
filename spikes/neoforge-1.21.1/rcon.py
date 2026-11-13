"""Minimal Source RCON client. Usage: [RCON_PASSWORD=...] python rcon.py "cmd1" "cmd2" ...
(defaults to the spike server password; C:\CivServer keeps its password in rcon-password.txt)"""
import os, socket, struct, sys, time

class Rcon:
    def __init__(self, host="127.0.0.1", port=25575, password=None):
        password = password or os.environ.get("RCON_PASSWORD", "civspike")
        self.s = socket.create_connection((host, port), timeout=60)
        self.rid = 0
        self._send(3, password)
        rid, _ = self._recv()
        if rid == -1:
            raise RuntimeError("RCON auth failed")

    def _send(self, typ, body):
        self.rid += 1
        data = struct.pack("<ii", self.rid, typ) + body.encode("utf-8") + b"\x00\x00"
        self.s.sendall(struct.pack("<i", len(data)) + data)

    def _recvn(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.s.recv(n - len(buf))
            if not chunk:
                raise ConnectionError("closed")
            buf += chunk
        return buf

    def _recv(self):
        (length,) = struct.unpack("<i", self._recvn(4))
        payload = self._recvn(length)
        rid, _typ = struct.unpack("<ii", payload[:8])
        return rid, payload[8:-2].decode("utf-8", "replace")

    def cmd(self, c):
        self._send(2, c)
        return self._recv()[1]

if __name__ == "__main__":
    r = Rcon()
    for c in sys.argv[1:]:
        if c.startswith("sleep "):
            time.sleep(float(c.split()[1])); continue
        print(f"> {c}\n{r.cmd(c)}")

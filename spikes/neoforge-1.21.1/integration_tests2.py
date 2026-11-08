"""Second pass after the first run crashed (Exotic Worlds x Recruits async path NPE).
Run with Recruits `UseAsyncPathfinding = false`. Checks persistence, recruits across the seam, OPAC claims."""
import sys, time
from rcon import Rcon

r = Rcon()
def c(cmd):
    out = r.cmd(cmd)
    print(f"> {cmd}\n  {out[:3000]}")
    return out

phase = sys.argv[1] if len(sys.argv) > 1 else "main"
if phase == "main":
    print("== Persistence after crash + restart (colonies created by proxies)")
    c("civspike status")

    print("\n== S0.4 Recruits: proxy-owned army, square formation move across the seam, fight")
    c("kill @e[type=recruits:recruit]")
    c("civspike army nationA 10 1000 345")
    c("civspike enemies 6 -995 350")
    c("civspike armystatus nationA")
    c("civspike move nationA -1000 350")
    for t in range(1, 10):
        time.sleep(10)
        print(f"-- t+{10*t}s"); c("civspike armystatus nationA")
    print("\n-- order back east across the seam")
    c("civspike move nationA 1000 340")
    for t in range(1, 5):
        time.sleep(10)
        print(f"-- t+{10*t}s"); c("civspike armystatus nationA")

    print("\n== S0.5 OPAC: claims by proxy UUIDs on both sides of the seam")
    c("civspike claim nationA 1010 310")
    c("civspike claim nationA -1015 310")
    c("civspike claim nationB -1015 310")
    c("civspike claim nationB -1000 300")
    c("save-all flush")
else:  # after a restart
    print("== After restart: ownership persisted?")
    c("civspike status")
    c("civspike armystatus nationA")
    c("civspike claim nationB 1010 310")
print("DONE")

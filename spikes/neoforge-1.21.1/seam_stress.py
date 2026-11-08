"""Stress test for the Exotic Worlds x Recruits async-path crash: march an army back and forth across the seam.
Targets are given as nearest-copy (unwrapped) coordinates. Run with Recruits UseAsyncPathfinding=true."""
import sys, time
from rcon import Rcon

rounds = int(sys.argv[1]) if len(sys.argv) > 1 else 8
r = Rcon()
def c(cmd):
    out = r.cmd(cmd); print(f"> {cmd}\n  {out[:600]}"); return out

c("kill @e[type=recruits:recruit]")
c("kill @e[tag=civ_enemy]")
c("civspike army nationA 10 1000 345")
time.sleep(3)
for i in range(rounds):
    # west across the seam: canonical -1000 written as 1048; east back: canonical 1000 written as -1048
    target = "1048 345" if i % 2 == 0 else "-1048 345"
    c(f"civspike move nationA {target}")
    time.sleep(20)
    c("civspike armystatus nationA")
print("DONE")

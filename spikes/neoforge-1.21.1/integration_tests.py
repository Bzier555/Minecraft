"""S0.3 / S0.4 / S0.5 integration spike across the torus seam (2048 world, seam at x = +-1024).
Needs the civspike mod (spikes/integration-mod) on the server. Run: python integration_tests.py"""
import time
from rcon import Rcon

r = Rcon()
def c(cmd, show=True):
    out = r.cmd(cmd)
    if show:
        print(f"> {cmd}\n  {out[:3000]}")
    return out

ZA, ZB = 300, 363   # island z-range
EAST = (960, 1023)
WEST = (-1024, -961)

print("== Setup: flat island straddling the east/west seam")
c("gamerule doMobSpawning false", False); c("gamerule doDaylightCycle false", False); c("time set noon", False)
c("difficulty normal", False)
for x0, x1 in (EAST, WEST):
    c(f"forceload add {x0} {ZA} {x1} {ZB}", False)
    c(f"fill {x0} 56 {ZA} {x1} 62 {ZB} minecraft:stone", False)
    c(f"fill {x0} 63 {ZA} {x1} 63 {ZB} minecraft:grass_block", False)
    for y in range(64, 160, 6):
        c(f"fill {x0} {y} {ZA} {x1} {y+5} {ZB} minecraft:air", False)
print(c("execute if block 1023 63 330 minecraft:grass_block", False), "/", c("execute if block -1024 63 330 minecraft:grass_block", False))

print("\n== S0.3 MineColonies: colonies owned by proxy owners")
c("civspike colony nationA rome 990 330")
c("civspike colony nationA veii 300 -600")      # same proxy, second colony via direct API
c("civspike colony nationB carthage -300 600")  # different proxy
c("civspike status")
print("\n-- builder hut ACROSS the seam from Rome's town hall (990 -> -1004 is 44 blocks)")
c("civspike builder nationA 1 -1004 330")
c("civspike status")
print("\n-- keep colony 1 ACTIVE with the proxy as subscriber (no real player online)")
c("civspike subscribe nationA 1")
for minute in range(1, 9):
    time.sleep(60)
    print(f"\n-- t+{minute} min")
    c("civspike status")

print("\n== S0.4 Recruits: proxy-owned army, formation move across the seam, fight")
c("civspike army nationA 10 1000 345")
c("civspike enemies 6 -995 350")
c("civspike armystatus nationA")
c("civspike move nationA -1000 350")
for t in (10, 20, 40, 60):
    time.sleep(10 if t < 40 else 20)
    print(f"-- t+{t}s"); c("civspike armystatus nationA")

print("\n== S0.5 OPAC: claims by proxy UUIDs on both sides of the seam")
c("civspike claim nationA 1010 310")
c("civspike claim nationA -1015 310")
c("civspike claim nationB -1015 310")   # contested chunk: should be refused
c("civspike claim nationB -1015 320")   # same chunk, other block: same chunk -> refused

c("save-all flush")
print("\nDONE")

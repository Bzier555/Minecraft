"""Automated seam tests for the Exotic Worlds torus spike (2048-block world, x/z in [-1024, 1024)).
Builds a sky platform across the east/west seam and checks entities, mob AI and rails crossing it.
Run while the spike server is up: python -I seam_tests.py"""
import time, re
from rcon import Rcon

r = Rcon()
def c(cmd, show=True):
    out = r.cmd(cmd)
    if show: print(f"> {cmd}\n  {out}")
    return out

def pos(selector):
    out = r.cmd(f"data get entity {selector} Pos")
    m = re.findall(r"(-?[\d.]+)d", out)
    return [round(float(v), 2) for v in m] if m else out

Y = 200
E0, E1 = 1000, 1023      # east side of seam
W0, W1 = -1024, -1001    # west side of seam
# Setup
c("forceload add 992 0 1023 31"); c("forceload add -1024 0 -993 31")
for x0, x1 in ((E0, E1), (W0, W1)):
    c(f"fill {x0} {Y} 0 {x1} {Y} 16 minecraft:stone", False)
    c(f"fill {x0} {Y+1} 0 {x1} {Y+4} 16 minecraft:air", False)
    for z in (0, 5, 11, 16):
        c(f"fill {x0} {Y+1} {z} {x1} {Y+3} {z} minecraft:barrier", False)
c("fill 999 201 0 999 203 16 minecraft:barrier", False); c("fill -1000 201 0 -1000 203 16 minecraft:barrier", False)
c("kill @e[tag=seam]", False)
c("gamerule doDaylightCycle false"); c("time set midnight"); c("difficulty normal"); c("gamerule doMobSpawning false")
c("kill @e[type=!player,x=980,y=190,z=-5,dx=50,dy=30,dz=30]", False)
c("kill @e[type=!player,x=-1030,y=190,z=-5,dx=50,dy=30,dz=30]", False)

print("\n== T1: block adjacency across seam (rails)")
c(f"fill 1012 {Y+1} 8 1023 {Y+1} 8 minecraft:powered_rail[shape=east_west,powered=true]")
c(f"fill -1024 {Y+1} 8 -1012 {Y+1} 8 minecraft:powered_rail[shape=east_west,powered=true]")
c(f"fill 1012 {Y} 8 1023 {Y} 8 minecraft:redstone_block")
c(f"fill -1024 {Y} 8 -1012 {Y} 8 minecraft:redstone_block")
print("east-end rail state:", c(f"data get block 1023 {Y+1} 8", False) or "(n/a)")

print("\n== T2: projectile crossing (arrow, no gravity)")
c(f'summon minecraft:arrow 1018.5 {Y+3}.5 13.5 {{NoGravity:1b,Motion:[1.0d,0.0d,0.0d],Tags:["seam","a1"],pickup:0b}}')
time.sleep(0.2); p0 = pos("@e[tag=a1,limit=1]")
time.sleep(1.5); p1 = pos("@e[tag=a1,limit=1]")
print("arrow before:", p0, "after 1.5s:", p1)

print("\n== T3: minecart on powered rail crossing seam")
c(f'summon minecraft:minecart 1014.5 {Y+1}.1 8.5 {{Motion:[0.8d,0.0d,0.0d],Tags:["seam","m1"]}}')
time.sleep(0.2); m0 = pos("@e[tag=m1,limit=1]")
time.sleep(3); m1 = pos("@e[tag=m1,limit=1]")
print("minecart before:", m0, "after 3s:", m1)

print("\n== T4: zombie targets villager across seam (pathfinding + targeting)")
c(f'summon minecraft:villager -1021.5 {Y+1} 2.5 {{Tags:["seam","v1"],NoAI:1b,PersistenceRequired:1b}}')
c(f'summon minecraft:zombie 1020.5 {Y+1} 2.5 {{Tags:["seam","z1"],PersistenceRequired:1b,ArmorItems:[{{}},{{}},{{}},{{id:"minecraft:leather_helmet",count:1}}]}}')
z_start = pos("@e[tag=z1,limit=1]")
for t in range(6):
    time.sleep(5)
    alive = "v1" in c("tag @e[tag=v1,limit=1] list", False) or "has" in c("tag @e[tag=v1,limit=1] list", False)
    print(f"  t={5*(t+1)}s zombie={pos('@e[tag=z1,limit=1]')} villager_present={'No entity' not in c('data get entity @e[tag=v1,limit=1] Health', False)} villager_health={c('data get entity @e[tag=v1,limit=1] Health', False)}")
print("zombie start:", z_start)

print("\n== T5: unowned recruit vs zombie across seam")
c(f'summon recruits:recruit -1018.5 {Y+1} 14.5 {{Tags:["seam","r1"],PersistenceRequired:1b}}')
c(f'summon minecraft:zombie 1020.5 {Y+1} 14.5 {{Tags:["seam","z2"],PersistenceRequired:1b,ArmorItems:[{{}},{{}},{{}},{{id:"minecraft:leather_helmet",count:1}}]}}')
r_start = pos("@e[tag=r1,limit=1]")
for t in range(6):
    time.sleep(5)
    print(f"  t={5*(t+1)}s recruit={pos('@e[tag=r1,limit=1]')} zombie={pos('@e[tag=z2,limit=1]')} zombie_hp={c('data get entity @e[tag=z2,limit=1] Health', False)}")
print("recruit start:", r_start)
print("recruit NBT keys sample:", c("data get entity @e[tag=r1,limit=1]", False)[:600])

"""Smoke test for civcore on the spike server (2,048-block torus). Usage:
  python civcore_smoke.py setup     # new game, two civs, research, 20 turns, save
  python civcore_smoke.py check     # after a restart: state persisted?"""
import sys
from rcon import Rcon

r = Rcon()
def c(cmd):
    out = r.cmd(cmd); print(f"> {cmd}\n  {out}"); return out

phase = sys.argv[1] if len(sys.argv) > 1 else "setup"
if phase == "setup":
    c("civ newgame torus 2048")
    c('civ admin found alice Roma 0 0')
    c('civ admin found alice Ostia 64 0')          # too close (1 tile)
    c('civ admin found bob Edge 1000 330')         # seam exclusion (tile 31)
    c('civ admin found bob Carthage 512 512')
    c('civ admin found bob Spy -100 0')            # alice's land / too close
    c('civ admin research alice agriculture')
    c('civ admin research bob pottery')            # missing prerequisite
    c('civ admin research bob agriculture')
    c("civ tick 20")
    c("civ status")
    c("civ save")
else:
    c("civ status")

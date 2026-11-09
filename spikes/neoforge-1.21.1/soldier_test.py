"""Phase 3 check: armies near an observer become Recruits soldiers, fight at war, and write casualties back.
Needs the spike save with AI civs 1 and 3 at war (see docs/J §1b). Run: python soldier_test.py"""
import re, time
from rcon import Rcon

r = Rcon()
def c(cmd, show=True):
    out = r.cmd(cmd)
    if show:
        print(f"> {cmd}\n  {out[:700]}")
    return out

def count(selector):
    out = r.cmd(f"execute if entity {selector}")
    m = re.search(r"count: (\d+)", out)
    return int(m.group(1)) if m else 0

c("forceload add 180 40 340 150")
c("civ admin army \"ai:1001\" spearmen 5 20 18")    # League side, tile centre (288, 96)
c("civ admin army \"ai:1003\" warriors 5 19 18")    # Brann side, tile centre (224, 96)
c("civ admin observe 256 96")
time.sleep(4)
print("soldiers:", count("@e[tag=civcore.soldier]"), "markers:", count("@e[tag=civcore.army_marker]"))
c("team list")
armies = c("civ armies", False)
ids = re.findall(r"#(\d+) (Aurelian League|Kingdom of Brann) \{(?:spearmen|warriors)=5\}", armies)
print("test armies:", ids)
# Adjacent stacks of civs at war engage without waiting for a turn.
for t in range(12):
    time.sleep(5)
    print(f"t+{5*(t+1)}s soldiers={count('@e[tag=civcore.soldier]')}")
    lines = [l.strip() for l in c("civ armies", False).splitlines()]
    print("  ", [l for l in lines if any(f"#{i} " in l for i, _ in ids)])
c("civ admin observe clear")
time.sleep(4)
print("after observers cleared: soldiers:", count("@e[tag=civcore.soldier]"), "markers:", count("@e[tag=civcore.army_marker]"))
c("civ armies")

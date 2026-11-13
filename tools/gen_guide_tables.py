"""Prints Markdown tables of techs, buildings and units from the shipped ruleset (used in docs/guide.md).
Run: python tools/gen_guide_tables.py > out.md"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "civ-mod", "src", "main", "resources", "data", "civcore", "civ")
short = lambda s: s.replace("civcore:", "").replace("_", " ").title() if s else "—"


def load(kind):
    d = os.path.join(ROOT, kind)
    return [json.load(open(os.path.join(d, f))) for f in sorted(os.listdir(d)) if f.endswith(".json")]


ERA = {"ancient": 0, "medieval": 1, "industrial": 2}
techs = sorted(load("techs"), key=lambda t: (ERA.get(t["era"], 9), t["cost"], t["id"]))
buildings = {b["id"]: b for b in load("buildings")}
units = {u["id"]: u for u in load("units")}

for era in ["ancient", "medieval", "industrial"]:
    print(f"\n#### {era.title()} era\n")
    print("| Technology | Science cost | Requires | Unlocks |")
    print("|---|---|---|---|")
    for t in techs:
        if t["era"] != era:
            continue
        unl = [short(b) for b in t["unlocks"]["buildings"]] + [short(u) + " (unit)" for u in t["unlocks"]["units"]]
        print(f"| {short(t['id'])} | {t['cost']} | {', '.join(short(r) for r in t['requires']) or '—'} | {', '.join(unl) or '—'} |")


def yields(y):
    names = {"food": "food", "production": "production", "gold": "gold", "science": "science", "culture": "culture",
             "happiness": "happiness", "defense": "city defence"}
    parts = [f"+{v} {names[k]}" for k, v in y.items() if k in names]
    if y.get("national"):
        parts.append(f"national science project #{y.get('science_project')}")
    return ", ".join(parts) or "—"


print("\n| Building | Production cost | Needs tech | Effect |")
print("|---|---|---|---|")
for b in sorted(buildings.values(), key=lambda b: (b["cost"], b["id"])):
    print(f"| {short(b['id'])} | {b['cost']} | {short(b.get('requiresTech'))} | {yields(b.get('yields', {}))} |")

print("\n| Unit | Production cost | Strength | Moves per turn | Needs tech | Needs resource |")
print("|---|---|---|---|---|---|")
for u in sorted(units.values(), key=lambda u: (u["cost"], u["id"])):
    print(f"| {short(u['id'])} | {u['cost']} | {u['strength']} | {u['moves']} | {short(u.get('requiresTech'))} | {short(u.get('requiresResource'))} |")

"""Generates the Medieval and Early Industrial ruleset entries (data/civcore/civ/*). Re-run after editing the tables.
Ancient-era files are maintained by hand and are not touched."""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "civ-mod", "src", "main", "resources", "data", "civcore", "civ")
N = lambda s: "civcore:" + s

# id, era, cost, requires, buildings unlocked, units unlocked
TECHS = [
    ("iron_working", "medieval", 110, ["bronze_working"], [], []),
    ("horseback_riding", "medieval", 110, ["animal_husbandry", "the_wheel"], [], ["horsemen"]),
    ("mathematics", "medieval", 120, ["writing", "the_wheel"], [], []),
    ("philosophy", "medieval", 130, ["writing", "calendar"], ["temple"], []),
    ("construction", "medieval", 130, ["masonry", "the_wheel"], ["colosseum"], []),
    ("optics", "medieval", 140, ["sailing", "mathematics"], ["harbor"], []),
    ("engineering", "medieval", 160, ["construction", "mathematics"], ["aqueduct"], []),
    ("feudalism", "medieval", 170, ["construction", "iron_working"], ["castle"], ["pikemen"]),
    ("civil_service", "medieval", 180, ["philosophy", "currency"], [], []),
    ("theology", "medieval", 190, ["philosophy", "calendar"], ["cathedral"], []),
    ("machinery", "medieval", 200, ["engineering", "iron_working"], ["workshop"], ["crossbowmen"]),
    ("chivalry", "medieval", 220, ["horseback_riding", "feudalism"], [], ["knights"]),
    ("gunpowder", "industrial", 300, ["machinery", "chivalry"], [], ["musketmen"]),
    ("printing_press", "industrial", 300, ["theology", "machinery"], ["university"], []),
    ("banking", "industrial", 320, ["civil_service", "currency"], ["bank"], []),
    ("astronomy", "industrial", 330, ["theology", "optics"], ["observatory"], []),
    ("navigation", "industrial", 340, ["optics", "astronomy"], [], []),
    ("metallurgy", "industrial", 360, ["gunpowder"], [], ["cannon"]),
    ("economics", "industrial", 380, ["banking", "printing_press"], ["great_survey"], []),
    ("scientific_theory", "industrial", 420, ["astronomy", "printing_press"], ["public_school"], []),
    ("steam_power", "industrial", 460, ["scientific_theory", "metallurgy"], ["steam_expedition"], []),
    ("industrialization", "industrial", 500, ["steam_power", "economics"], ["factory"], []),
    ("military_science", "industrial", 520, ["metallurgy", "industrialization"], [], ["riflemen"]),
    ("railroad", "industrial", 560, ["industrialization", "navigation"], ["world_exhibition"], []),
]

# id, cost, yields  (requiresTech filled from TECHS)
BUILDINGS = [
    ("temple", 80, {"culture": 2, "happiness": 1}),
    ("colosseum", 110, {"happiness": 3}),
    ("harbor", 100, {"food": 1, "gold": 1}),
    ("aqueduct", 120, {"food": 2}),
    ("castle", 140, {"defense": 8}),
    ("cathedral", 160, {"culture": 3, "happiness": 2}),
    ("workshop", 120, {"production": 2}),
    ("university", 200, {"science": 3}),
    ("bank", 180, {"gold": 3}),
    ("observatory", 180, {"science": 2}),
    ("public_school", 240, {"science": 3}),
    ("factory", 280, {"production": 4}),
    # Science victory: three national projects, built in order (see Victory.SCIENCE_PROJECTS)
    ("great_survey", 400, {"national": 1, "science_project": 1}),
    ("steam_expedition", 600, {"national": 1, "science_project": 2}),
    ("world_exhibition", 800, {"national": 1, "science_project": 3}),
]

# id, cost, strength, moves, requiresResource
UNITS = [
    ("horsemen", 70, 12, 4, "horses"),
    ("pikemen", 80, 16, 2, None),
    ("crossbowmen", 90, 13, 2, None),
    ("knights", 120, 20, 4, "horses"),
    ("musketmen", 140, 24, 2, None),
    ("cannon", 150, 22, 2, "iron"),
    ("riflemen", 200, 34, 2, "iron"),
]


def write(kind, name, obj):
    os.makedirs(os.path.join(ROOT, kind), exist_ok=True)
    with open(os.path.join(ROOT, kind, name + ".json"), "w", newline="\n") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


unlock_b = {b: t for t, _, _, _, bs, _ in TECHS for b in bs}
unlock_u = {u: t for t, _, _, _, _, us in TECHS for u in us}
for tid, era, cost, req, bs, us in TECHS:
    write("techs", tid, {"id": N(tid), "era": era, "cost": cost, "requires": [N(r) for r in req],
                         "unlocks": {"buildings": [N(b) for b in bs], "units": [N(u) for u in us], "stages": ["civ_" + tid]}})
for bid, cost, y in BUILDINGS:
    write("buildings", bid, {"id": N(bid), "cost": cost, "requiresTech": N(unlock_b[bid]), "yields": y})
for uid, cost, st, mv, res in UNITS:
    write("units", uid, {"id": N(uid), "cost": cost, "strength": st, "moves": mv, "requiresTech": N(unlock_u[uid]),
                         "requiresResource": N(res) if res else None})
print(f"wrote {len(TECHS)} techs, {len(BUILDINGS)} buildings, {len(UNITS)} units")

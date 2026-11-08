# H. Minimum Viable Prototype

## Goal

Prove that **a Civilization game loop works inside Minecraft**: a player explores a wrapped (torus) world, founds and grows cities, researches, meets an AI civ, and wins or loses by conquest, all persisting across restarts on a dedicated server. Prove it before investing in MineColonies, Recruits or wrapping integrations.

## Scope

| In | Out (deliberately) |
|---|---|
| Small (2,048²) torus via Exotic Worlds (`torus_medium`) with mostly-ocean seams; cities banned within 3 tiles of a seam | Medium and Large maps |
| 2–4 civs; ≥1 AI civ | Diplomacy beyond war/peace (AI always at war after contact + 10 ticks; peace by command) |
| City founding, territory, built-in protection | MineColonies, OPAC, Recruits |
| 5 yields (food, production, gold, science, culture) | Happiness, trade routes, roads |
| ~12 techs (Ancient era) | Medieval/Industrial eras, governments |
| 6 unit types, abstract armies and combat | Physical soldiers (placeholder: armor-stand banner marker showing army location) |
| ~8 buildings, strategic-only, with a tier-blueprint marker around the City Center | NPC construction |
| Domination + score victory | Science/culture/diplomatic victory |
| Simple strategic map, city screen, tech list | Polished UI |
| Save/load, dedicated server, singleplayer | Large maps |

## Why abstract-only armies in the MVP

Combat, conquest and AI can all be validated on the abstract layer, which is where all the hard design lives. Physical armies (Phase 3) then become a projection of an already-working system. The MVP still shows armies physically as a banner marker entity, and players can walk to them, so the world feels inhabited.

## Playable script (acceptance test)

1. Admin runs `/civ setup size=small civs=2 ai=1 speed=quick seed=1234`, server pregenerates and analyses the map.
2. Player joins, spawns at a balanced start with a Settler Charter and a scout army.
3. Player founds the capital → civ created; borders appear; city screen shows yields.
4. Player queues Warriors → Settlers; researches Agriculture → Pottery (Granary).
5. Player explores with the scout army via the strategic map; discovers the AI's territory (first contact notification).
6. Player founds a second city; borders grow from culture.
7. AI expands to 2–3 cities and eventually attacks.
8. Player builds an army of Spearmen/Archers, moves it to an AI city, sieges and captures it; territory flips.
9. Server restarts mid-war; state resumes exactly.
10. Player captures the AI capital → domination victory screen. (Alternatively, the time limit triggers a score victory.)

## Success criteria

- A full Small-map game is completable in 2–3 hours at Quick speed.
- AI expands, builds and attacks without human help in 100% of seeded headless runs (no stalls).
- Zero state divergence across 20 forced restarts.
- Server MSPT impact of Civ Core < 5 ms average on a Small map with 2 players.
- At least 3 playtesters say the strategic decisions (where to settle, what to build, when to attack) mattered.

## Content list

- **Techs:** Agriculture, Pottery, Animal Husbandry, Mining, Archery, Bronze Working, Masonry, Writing, The Wheel, Sailing, Calendar, Currency.
- **Buildings:** Palace (capital), Granary, Monument, Library, Walls, Barracks, Market, Shrine.
- **Units:** Settlers, Scout, Warriors, Archers, Spearmen, Chariot.
- **Terrain classes:** ocean, coast, grassland, plains, desert, tundra, snow, forest, jungle, hills, mountain (impassable), river flag.
- **Resources:** Wheat, Cattle, Fish, Iron (unlocks with Bronze Working), Horses, Stone; luxuries deferred.

## Exit decision

After MVP playtests, decide: (a) proceed to Phase 2/3 as planned; (b) adjust time model or tile size; (c) stop. Do not start MineColonies integration until the MVP loop is fun on its own.

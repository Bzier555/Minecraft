# A. Project Feasibility Assessment

## 1. Verdict

**Achievable, as a custom strategy mod ("Civ Core") wrapped around reused physical-gameplay mods.** It is not achievable by assembling existing mods and configuring them: nothing in the ecosystem provides a nation-level simulation, abstract cities, tech-driven production, AI civilizations or victory conditions. That core has to be written, and it is the bulk of the project.

What reuse realistically saves:

| System | Reuse coverage | Custom work remaining |
|---|---|---|
| Terrain, biomes, continents | High (Exotic Worlds, Tectonic, Terralith) | Seam placement in ocean, resource placement plan, start balancing, strategic map summary |
| Physical cities / NPC builders | High (MineColonies + Structurize) | Nation ownership of many colonies, abstract sim, reconciliation, AI-owned colonies |
| NPC soldiers, commands, ships | Medium–high (Recruits, Small Ships, siege mods) | Abstract armies, materialization, AI tactical controller, conquest rules |
| Territory protection & map display | Medium (Open Parties and Claims, Xaero/JourneyMap) | Tile-based territory, culture growth, war-time protection rules |
| Industry, trade items | Medium (Create, Lightman's Currency / Numismatics) | National treasury, trade routes, item↔abstract conversion |
| Tech gating | Medium (AStages, KubeJS, JEI) | Tech tree data, research sim, nation-wide stage sync, tech UI |
| Diplomacy, AI civilizations, victory | **None usable** | Entirely custom |
| World wrapping | Full (Exotic Worlds, NeoForge 1.21.1, spike-verified) | Seam guard mixin, nearest-copy order translation, seam exclusion for cities; see [E](E-world-wrapping.md) |

## 2. Largest technical risks (ranked)

| # | Risk | Why it is hard | Mitigation | Redesign trigger? |
|---|---|---|---|---|
| R1 | **Strategic ↔ physical state consistency** (cities and armies) | Two representations of the same city/army; chunk loading, server crashes and entity saves can duplicate or lose units | Single source of truth in Civ Core; physical entities are disposable "projections" tagged with `(objectId, generation)`; stale projections deleted on load. See [D §7](D-architecture.md#7-strategic-vs-physical-simulation) | **Yes** — must be designed before any army/city integration code |
| R2 | **World wrapping** | Mods with their own pathfinding (Recruits, MineColonies) use raw coordinates; Exotic Worlds is pre-1.0 | Exotic Worlds (verified). Seam guard mixin, nearest-copy orders, no cities near seams, seam regression scripts before each version bump. All Civ Core distances use `Topology` | **Yes**: topology must be abstracted from day one |
| R3 | **AI civilizations** | Large design space; must work with no players nearby; must not cheat visibly | AI operates only on abstract state through the same command API as players; utility-based; headless batch testing without Minecraft | No, if command API is shared |
| R4 | **MineColonies integration limits** | One colony per owner; no unloaded sim; player-centric creation; unstable internals; anti-invasive policy | Proxy owner per city (FakePlayer or offline UUID), abstract sim, reconciliation via work orders; pin versions; spike in Phase 0 | Partial — fallback adapter (blueprint paste) keeps the game playable |
| R5 | **Recruits integration** | ARR, no stable API, fast-moving internals, its own factions/claims/sieges overlap ours | Thin bridge using public setters/events; pin version; disable or ignore its faction/claim features; fallback to own simple soldier entity | No |
| R6 | **Server performance** | Hundreds of NPCs, many colonies, big worlds | Physical simulation only near players; cap materialized units; Spark budgets per subsystem; pregenerate worlds | No |
| R7 | **Economy exploits** | Item duplication, automated farms feeding national yields | National yields come from the tile/building model, not item counts; item conversions rate-limited with diminishing returns | No |
| R8 | **Scope** | A full Civ game is years of work for a team | Strict MVP ([H](H-mvp.md)); three eras only; defer diplomacy depth, culture/religion, naval AI | — |

## 3. Platform decision

The brief prefers Forge 47.x and asks for evidence before recommending a change. Evidence gathered 2026-11-08:

| Component | Forge 1.20.1 | NeoForge 1.21.1 |
|---|---|---|
| Loader status | Forge 47.4.28 latest/recommended (2026-10-13) | Actively developed |
| MineColonies | 1.1.1317-snapshot (2026-11-08) | 1.1.1419-snapshot (2026-11-08), release 1.1.1403 |
| Villager Recruits | 1.15.2 (2026-06-29) | 1.15.2 (2026-11-04) |
| Create, KubeJS, OPAC, Xaero, FTB Quests | Yes | Yes |
| Tectonic | 3.0.17 (Nov 2025); **1.20.1 no longer officially supported** | 3.0.28 (Sep 2026) |
| Terralith | 2.5.4 (Jul 2024, frozen) | 2.6.2 (Jun 2026) |
| **Seamless world wrap** | **None.** Must build | **Exotic Worlds** (torus + cylinder, public API) |
| Client performance | Embeddium (last update Jan 2025) | Official Sodium |
| Server performance libs | Canary (Feb 2024, stale), ModernFix, FerriteCore | ModernFix, FerriteCore, (Lithium on NeoForge unverified) |
| 1.20.1-only mods we would lose | — | Talhanation's Siege Weapons; GameStages 1.20.1 build (alternatives needed); Reign of Nether's current line (study only) |
| Java | 17 | 21 |

**Assessment.** Staying on 1.20.1 is possible; the ecosystem is mature and stable. But a new multi-year project would start on a version whose world-generation and performance libraries are freezing, and would have to build world wrapping from scratch, while the same mods plus a working wrap implementation are available one version up. Exotic Worlds' PolyForm Shield licence permits *using* it as a dependency; it only forbids providing a product that competes with it. A Civ modpack is not a world-wrap product (confirm with the author before release).

**Decision (2026-11-08): NeoForge 1.21.1 + Exotic Worlds**, adopted after the Phase 0 spike and an in-game client test ([spike-platform.md](spike-platform.md)):
- the core stack loads with zero errors;
- terrain is seamless across the seams;
- entities and AI cross the seams;
- TPS holds at 20.

Required mitigations:
1. Our seam-guard mixin, which fixes a server crash between Exotic Worlds and Recruits.
2. Targets given on whichever side of the seam is closer, for any mod with its own pathfinding.
3. No city within 192 blocks of a seam, because MineColonies' pathfinding doesn't understand the wraparound.

Forge 1.20.1 remains a documented contingency. The documents below name NeoForge 1.21.1 APIs.

## 4. Required compromises

1. **Continuous time, not turns.** A configurable "strategic tick" (default 60 s) replaces turns. Optional pause when no players are online.
2. **Distant cities are abstract.** MineColonies cities only physically develop while loaded; the abstract layer is authoritative, and the physical city catches up when visited.
3. **Square strategic tiles, not hexes.** Tiles of 4×4 chunks (64×64 blocks) align with chunk claims, map rendering and protection. Hex adjacency can be added later as pure math if desired. See [D §4](D-architecture.md#4-the-strategic-grid).
4. **Terrain fog of war is cosmetic.** Strategic data (who owns what, where armies are) is hidden server-side; terrain the client has loaded is visible.
5. **Three eras** (Ancient, Medieval, Early Industrial) for 1.0.
6. **AI cities are physically simpler** than player cities at first: placed blueprints that upgrade, optionally MineColonies-driven later.

## 5. Licensing constraints

Not legal advice; review before public release.

| Mod | Licence (verified) | Consequence |
|---|---|---|
| MineColonies, Structurize, BlockUI, Domum Ornamentum | GPL-3.0 | Code that links against MineColonies is treated as derived work → the MineColonies adapter must be GPL-3.0. Simplest: license all of Civ Core GPL-3.0 |
| Open Parties and Claims | LGPL-3.0 | Free to depend on |
| KubeJS | LGPL-3.0 | Free to depend on |
| Villager Recruits, Workers, Small Ships, Siege Weapons | All Rights Reserved | Dependency only (pack manifest + compile against the published jar). No forking, no copying code |
| Reign of Nether | GPL-3.0 | Code may be copied into a GPL-3.0 module (RTS camera, selection, formations) |
| Circumnavigate | AGPL-3.0 | A port must stay AGPL with public source; combinable with GPL-3.0 code |
| Looping World | MIT | Free to port |
| Exotic Worlds | PolyForm Shield 1.0.0 | Use as dependency OK; no competing product; porting to 1.20.1 needs author permission |
| Create | Code MIT, assets ARR | Dependency OK |
| FTB Quests/Teams/Chunks, Xaero's maps, Epic Knights | All Rights Reserved | Dependency via CurseForge manifest only; never rehost jars |
| Towny (plugin) | CC BY-NC-ND 3.0 | Design reference only |

**Project policy notes (not legal requirements).** MineColonies' README and AGENTS.md say mixins/coremods into MineColonies are unsupported and block "approved addon" status, and that the maintainers discourage AI-generated implementation code **for MineColonies itself**. Our plan is API-only integration in our own mod and contributes nothing upstream. If we ever submit a PR to MineColonies, that policy applies.

**Distribution.** Publish on CurseForge with mods referenced through the manifest (standard practice). Check each project's "allow distribution" toggle at pack export; the CurseForge app flags any blocked mod. For a server pack or Modrinth `.mrpack`, ARR jars need explicit permission.

## 6. Likely bottlenecks

1. **Civ Core simulation + AI design** — the critical path for everything; no reuse.
2. **Strategic map UI** — custom client screen; needed for a playable strategy game.
3. **MineColonies adapter** — proxy ownership and reconciliation; depends on Phase 0 spike results.
4. **Balancing** — requires headless AI-vs-AI simulation (planned in [I](I-testing.md)).
5. **Playtesting multiplayer** — needs a dedicated test server early.

## 7. Evaluation of proposed map sizes

With 64-block tiles, a block size `S` gives `(S/64)²` tiles. For comparison, Civilization V's "Small" map is 66×42 = 2,772 tiles and "Standard" is 80×52 = 4,160.

| Size | Blocks | Tiles | Chunks to pregenerate | Tiles per civ | Crossing on foot (sprint ≈ 5.6 b/s) | Verdict |
|---|---|---|---|---|---|---|
| Small | 2,048² | 32×32 = 1,024 | 16,384 | 256–512 (2–4 civs) | ≈ 6 min | Good for MVP and 2-player tests |
| Medium | 4,096² | 64×64 = 4,096 | 65,536 | 512–1,024 (4–8 civs) | ≈ 12 min | **Recommended default**; Civ "Standard"-like |
| Large | 8,192² | 128×128 = 16,384 | 262,144 | 1,365–2,048 (8–12 civs) | ≈ 24 min | Feasible with pregeneration; only with abstract cities |

Disk size for pregenerated worlds is roughly several hundred MB (Medium) to a few GB (Large) — an estimate; measure with Chunky in Phase 0. Pregeneration with Chunky is required for Medium and Large so starting-location analysis and play never trigger generation lag. Horses (~14 b/s) and boats halve travel times; consider a fast-travel mechanic between connected own cities (roads).

Recommendation: keep the three sizes, add a **Tiny 1,024²** preset for automated tests, and make tile size a game-setup constant (not changeable mid-game).

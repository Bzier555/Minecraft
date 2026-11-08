# J. Step-by-Step Implementation Guide (Milestone 1)

This guide is for a coding agent working in this repository. Target: **Minecraft 1.21.1 · NeoForge 21.1.265 · Java 21 · ModDevGradle**, adopted after the Phase 0 spike ([spike-platform.md](spike-platform.md)).

APIs marked **(verified)** were compiled and run in the spike mod ([spikes/integration-mod](../spikes/integration-mod/)). APIs marked **(verify)** should be confirmed against Parchment-mapped sources in the IDE before use.

## 0. Ground rules for implementers

1. `civ-sim` must not import `net.minecraft.*`, `net.neoforged.*` or any mod package. Enforce with an ArchUnit test.
2. Only the server thread mutates `GameState`. AI and heavy computation work on immutable snapshots and return `Command`s.
3. Every distance goes through `Topology`. **Every coordinate handed to another mod's pathfinding goes through `Topology.nearestCopy`** (spike finding: Recruits and MineColonies use raw coordinates).
4. No mixins into MineColonies (that project's addon policy). Mixins into vanilla and Exotic Worlds are allowed when necessary; keep each one tiny and documented, like the seam guard.
5. Every projected entity gets a `civ:proj` tag with `objectId` and `generation`. Entities load a few seconds after server start (spike row 19), so never treat a unit as missing before its chunk's entities have loaded.
6. Commit small, with tests. No production feature lands without a unit test or GameTest.

## 1. Phase 0 status

| Spike | Status | Where |
|---|---|---|
| S0.1 Platform | ✅ Adopted NeoForge 1.21.1 + Exotic Worlds | [spike-platform.md](spike-platform.md) §2, §5b |
| S0.2 Pack baseline | ✅ Server boots with the core set, 0 errors. The full pack (C §1–2) is not yet booted | `spikes/neoforge-1.21.1/setup-server.sh` |
| S0.3 MineColonies proxy owner | ✅ Multiple colonies per proxy (direct API). Colonies run only near real players. Builders can't path across seams | §3b rows 11–14 |
| S0.4 Recruits bridge | ✅ Ownership, groups, formations, persistence. Seam crash fixed by our mixin | §3b rows 10, 15–17 |
| S0.5 OPAC proxy claims | ✅ | §3b row 18 |
| S0.6 World preset | ✅ Exotic Worlds presets. Medium/Large pregen times are extrapolated | §3 |
| S0.7 Performance baseline | Partial: TPS 20, median tick 2.4 ms with 1 player, 1 active colony and 10 recruits. Battle load test pending | §5b |
| **Open** | Boot the full pack from C §1–2 on one server; Structurize blueprint paste for AI cities (spike row 14 failed); 200-recruit battle profile | — |

## 1b. Milestone 1 progress (2026-11-08)

| Step | Status |
|---|---|
| 1 Gradle multi-project | ✅ `civ-sim` (pure Java, JUnit 5, jqwik, ArchUnit) and `civ-mod` (ModDevGradle 2.0.148). civ-sim classes are packed into the mod jar (`jar { from … }`) instead of jarJar |
| 2 Model + topology | ✅ `GridTopology` (bounded/cylinder/torus), `TileMapping`, `nearestCopy`, `distanceToSeamOrEdge`; `GameState`, `Civilization`, `City`, `StrategicMap` |
| 3 Ruleset | ✅ JSON loader, validator (references and cycles), content hash; 12 Ancient techs, 8 buildings, 6 units in `civ-mod/src/main/resources/data/civcore/civ/`; reloads with `/reload` |
| 4 Clock + commands | ✅ `StrategicClock` (pause, speed, catch-up cap), sealed `Command` (FoundCity, SetResearch, QueueProduction), `Simulation.apply` and `strategicStep` with placeholder yields |
| 5 Persistence | ✅ **JSON** (not NBT, for now) in `<world>/civcore/game.json`, atomic write, 3 backups; refuses to start over an unreadable save |
| Seam guard | ✅ Moved into civ-mod (`dev.civcore.mod.mixin`); 49 guarded crossings, 0 crashes on the spike server |
| 6 WorldPlan | ✅ Terrain per tile from 16 generator samples, coasts, rivers, start placement (`StartPlacer`). Resource deposits not yet. Details below |
| 7 Cities + territory | ✅ Code complete, **not yet tried by a player**: `civcore:city_center` block (unbreakable, right-click shows the city), `civcore:settler_charter` item (anvil name = city name), one free charter per player per game, built-in break/place protection on foreign tiles |
| 9 (part) | ✅ Cities now yield from tiles: centre + one worked tile per citizen within 2 tiles; citizens eat 2 food; starvation shrinks cities |
| 9 Armies, war, victory | ✅ Abstract armies (stacks of up to 10), A* on tiles (terrain costs, mountains and water blocked, closed borders at peace), Civ-V-style combat, sieges, capture (tiles transfer, capital moves), healing, settlers found cities, war and two-sided peace, domination + score victory, elimination. Army markers in the world: one armor stand per army in the civ's colour, rebuilt from state (never trusted from disk) |
| 10 AI v0 | ✅ `dev.civcore.sim.ai`: deterministic rules + utility scoring; per-civ aggression personality; issues only normal Commands. It researches, fills production (garrison, settlers up to 4 cities, buildings, army), moves settlers to scored sites, declares war when clearly stronger, gathers armies at a staging city and attacks the cheapest reachable enemy city, defends threatened cities, and makes peace when losing. Rules added while tuning: idle stacks on a tile merge (max 10); a 20-turn war cooldown after peace; armies are sent home when peace is signed. `/civ addai [name]` adds an AI civ at the best free start |
| 8, 11–12 | Not started. Next: culture border growth (8), visibility/fog of war (F1.15), then the map and city screens (11) |

**Headless AI results** (`gradlew :civ-sim:aiBatch -Pgames=100 [-Pcivs=4]`, synthetic one-continent maps, 400-turn limit):

| Civs | Games | Domination | Avg game length | Rejected AI commands |
|---|---|---|---|---|
| 2 | 100 | 94% | 127 turns | 0 |
| 4 | 20 | 90% | 195 turns | 0 |

On the spike server (real 2,048 torus, 3 AIs) one AI won by domination within 150 turns. All 150 turns took 347 ms (about 2 ms per turn on the server thread).

**AI limitations (v0):**
- It reads the whole map; there is no fog of war until F1.15.
- Land units only, so civs on separate continents can't fight; those games end by score.
- No trade or diplomacy beyond war and peace.

**Verified on the spike server (2026-11-08):**
- war declared, then a peace offer;
- two 6-spearman armies marched 2 tiles per turn and besieged Carthage, whose HP went to 0;
- the city was captured, Bob eliminated, and the domination victory announced;
- settlers founded cities, and their City Center blocks were placed when the chunk loaded;
- army markers appeared with live labels.

The run found a migration bug, now fixed: saves from before `originalCapital` existed didn't count capitals. 41 civ-sim tests pass.

**World analysis findings (spike server, 2,048 torus, Tectonic + Terralith):**
- **Sampling the generator is slow:** about 200 ms per tile on the server thread (16 samples, each 2 heightmaps + 1 biome). Doing it on the server thread lagged the server about 40 s per minute. It now runs on up to 4 background threads (Small map: 67 s, no lag). Medium and Large estimates: about 4.5 and 18 min, one-off at game setup.
- **Biomes need Exotic Worlds' fold binding.** Without `GenerationProbe.withFold(level, …)` (api.v1.gen) on each worker thread, heights matched but biomes did not (5 of 8 probe points wrong). With it, 8 of 8 match. The binding is done in `ExoticWorldsCompat`.
- Water is decided from heights only (surface above floor), never from biome tags.
- `/civ admin probe <x> <z>` compares a sample with the real chunk. Use it after any terrain-mod update.
- Exotic Worlds' API maven (`raw.githubusercontent.com/anyttng/exotic_worlds/maven/`) returned 404, so civ-mod compiles against the nested jar extracted into the git-ignored `civ-mod/libs/` (see README).

**Verified on the spike server:** the ruleset loads; `/civ newgame torus 2048`; capitals founded; spacing and seam rules reject bad sites; research completes; save/restart/load gives an identical state. `spikes/neoforge-1.21.1/civcore_smoke.py` reproduces this. 24 civ-sim tests pass.

**Commands so far:** `/civ status | techs | tile | civs | armies | found <city> [civ name] | research <tech> | queue <item> | move <army> <x> <z>|here | settle <army> <city> | war <civId> | peace <civId>`. Operator extras: `admin move|war|peace|queue|settle|army|turnlimit`. Operator: `/civ newgame auto | newgame <torus|cylinder|bounded> <sizeBlocks> | starts [n] | tick [n] | pause | resume | save | admin found <issuer> <city> <x> <z> | admin research <issuer> <tech> | admin probe <x> <z>`.

## 2. Repository structure

```text
MC_CIV_MODPACK/
├─ README.md, docs/                      ← planning package
├─ spikes/                               ← Phase 0 (keep for regression checks)
├─ settings.gradle, build.gradle, gradle.properties, gradlew*
├─ civ-sim/                              ← pure Java 21
│  └─ src/main/java/dev/civcore/sim/
│     ├─ model/        GameState, GameSettings, Civilization, City, Tile, TerrainSummary, Army, UnitRecord, Relation, ids
│     ├─ topology/     Topology, BoundedTopology, CylinderTopology, TorusTopology, TilePos
│     ├─ ruleset/      Ruleset, TechDef, BuildingDef, UnitDef, TerrainYieldDef, ResourceDef + RulesetValidator
│     ├─ rules/        YieldCalculator, GrowthRules, ProductionRules, ResearchRules, CultureRules, CombatRules, VictoryRules
│     ├─ command/      Command (sealed), FoundCity, QueueProduction, SetResearch, MoveArmy, DeclareWar, MakePeace…; CommandValidator
│     ├─ engine/       Simulation, StrategicClock, SimRandom, DomainEvent types
│     ├─ visibility/   VisibilityMap, VisibilityRules
│     ├─ ai/           AiController, UtilityAi, InfluenceMap, Personality, SettleSiteScorer
│     ├─ worldplan/    WorldPlan, TerrainSampler (interface), StartPlacer, ResourcePlacer
│     └─ io/           GameStateCodec (JSON), SchemaMigrations
├─ civ-sim-runner/                       ← headless AI batch CLI
├─ civ-mod/                              ← NeoForge mod (modid "civcore")
│  └─ src/main/java/dev/civcore/mod/
│     ├─ CivCoreMod.java                 @Mod entry
│     ├─ registry/     ModBlocks, ModItems, ModBlockEntities, ModEntities, ModAttachments, ModMenus
│     ├─ game/         ServerGame, GameLifecycle, CommandBus
│     ├─ persist/      CivPersistence
│     ├─ data/         RulesetReloadListener
│     ├─ world/        MinecraftTerrainSampler, WorldPlanService, WorldShape (reads Exotic Worlds shape), ResourceFeature
│     ├─ territory/    TerritoryEnforcement, BuiltinTerritory
│     ├─ city/         CityCenterBlock(+Entity), SettlerCharterItem, CityProjection, MarkerBlueprintProjection
│     ├─ army/         ArmyMarkerEntity, ProjectionManager, ProjectionTag
│     ├─ tech/         TechGate, BuiltinTechGate
│     ├─ net/          CivNetwork, payloads
│     ├─ command/      CivCommands
│     ├─ mixin/        SeamSnapGuardMixin, PathAccessor   (moved from the spike)
│     └─ client/       StrategicMapScreen, CityScreen, TechScreen, ClientCivState
├─ civ-compat-minecolonies/              ← Phase 2, GPL-3.0
├─ civ-compat-recruits/                  ← Phase 3
├─ civ-compat-opac/                      ← Phase 2
├─ civ-compat-astages/                   ← Phase 1–2 (optional tech enforcement)
└─ pack/                                 ← packwiz
```

## 3. Milestone 1, step by step

### Step 1: Gradle multi-project (F1.1)

- Start from the NeoForge MDK for 1.21.1 with ModDevGradle (`net.neoforged.moddev` 2.0.x), as used in `spikes/integration-mod`. Use `parchment_mappings_version=2024.11.17`.
- `settings.gradle`: include `civ-sim`, `civ-sim-runner`, `civ-mod`.
- `civ-sim/build.gradle`: `java-library`, toolchain 21. Dependencies: `com.google.code.gson:gson`, `org.slf4j:slf4j-api`. Test dependencies: `org.junit.jupiter:junit-jupiter`, `net.jqwik:jqwik`, `com.tngtech.archunit:archunit-junit5`.
- `civ-mod/build.gradle`: `implementation project(':civ-sim')`, and embed it with `jarJar(project(':civ-sim'))` **(verify the ModDevGradle jarJar syntax)**.
- Run configs: `client`, `server` and `gameTestServer` (the MDK sets `neoforge.enabledGameTestNamespaces`).
- Mod dependencies, as used in the spike:
  ```groovy
  repositories {
    maven { url = "https://ldtteam.jfrog.io/artifactory/modding/" }          // MineColonies family (verified)
    exclusiveContent { forRepository { maven { url = "https://api.modrinth.com/maven" } }
                       filter { includeGroup "maven.modrinth" } }               // Recruits, OPAC, Exotic Worlds
  }
  dependencies {
    compileOnly "com.ldtteam:minecolonies:1.1.1419-1.21.1-snapshot"          // (verify artifact resolves; spike used local jars)
    compileOnly "maven.modrinth:villager-recruits:1.15.2"                     // (verify version id form)
    compileOnly "maven.modrinth:open-parties-and-claims:neoforge-1.21.1-0.33.0"
  }
  ```
  If a coordinate fails to resolve, fall back to the spike's `compileOnly fileTree(...)` on the pinned jars. Exotic Worlds ships its NeoForge jar nested inside a multi-loader jar (`META-INF/jarjar/`), so compile against the extracted inner jar.
- CI (GitHub Actions): JDK 21, then `./gradlew build :civ-sim:test runGameTestServer`.

### Step 2: Model and topology (F1.2)

- Use records for values and final classes for aggregates. IDs are `record CivId(int)`, `CityId(long)`, `ArmyId(long)`.
- `Topology` as in [D §5](D-architecture.md#5-topology): `distance`, `dx/dz`, `normalize`, `nearestCopy`, `neighbors`, `tilesWithin`, plus `distanceToSeam(TilePos)` for the city exclusion rule.
- Property tests:
  - torus distance is symmetric and ≤ W/2 + H/2;
  - `nearestCopy(t, o)` is always within W/2 of `o` on each wrapped axis;
  - `normalize` is idempotent.

### Step 3: Ruleset (F1.3)

- One JSON file per definition under `data/civcore/civ/{techs,buildings,units,terrain,resources}/`, for example:
  ```json
  { "id": "civcore:agriculture", "era": "ancient", "cost": 20,
    "requires": [], "unlocks": { "buildings": ["civcore:granary"], "units": [], "stages": ["civ_agriculture"] } }
  ```
- `RulesetReloadListener extends SimpleJsonResourceReloadListener`, registered in `AddReloadListenerEvent`. It validates references and cycles. Store the ruleset hash in the save.

### Step 4: Clock and commands (F1.4)

- `StrategicClock` accumulates `realMillis × speedFactor` and has a paused flag.
- `ServerGame` listens to `ServerTickEvent.Post` (`net.neoforged.neoforge.event.tick`). On each tick it:
  1. drains `CommandBus`;
  2. runs the continuous step every 20 ticks;
  3. runs the strategic step when due;
  4. dispatches `DomainEvent`s.
- `Command` is a sealed interface validated by `CommandValidator`. Players and AI use the same path.

### Step 5: Persistence (F1.5)

- Store in `server.getWorldPath(LevelResource.ROOT).resolve("civcore")`: `Codec` → `CompoundTag` → `NbtIo.writeCompressed` to a `.tmp` file, then an atomic move. Keep 3 backups.
- Save on `LevelEvent.Save` (overworld) and `ServerStoppingEvent`. Load on `ServerStartedEvent`.
- Use NeoForge **data attachments** (`AttachmentType`, registered via `NeoForgeRegistries.ATTACHMENT_TYPES`) for per-chunk flags, such as "resource deposit placed", and per-entity projection tags (**verify**: whether entity tags or attachments are simpler).
- A round-trip test proves the JSON codec and the NBT codec agree.

### Step 6: World shape and WorldPlan (F1.6)

- `WorldShape` reads the overworld's wrapping from Exotic Worlds' API (`com.exoticworlds.api.v1`, see its [modding.md](https://github.com/anyttng/exotic_worlds/blob/main/docs/modding.md)) **(verify the API methods)**, falling back to the world border. It produces a `Topology`: torus, cylinder or bounded.
- Game setup chooses the Exotic Worlds `level-type` preset. Mapping (spike-verified for the first one):

  | Our size | Exotic Worlds preset |
  |---|---|
  | Small 2,048 | `torus_medium` |
  | Medium 4,096 | `torus_large` |
  | Large 8,192 | `torus_huge` |

  A world preset can only be chosen when the world is created, so `/civ setup` writes `server.properties` and needs a fresh world folder. Alternatively, ship a datapack world preset ([Exotic Worlds dedicated-server.md](https://github.com/anyttng/exotic_worlds/blob/main/docs/dedicated-server.md)).
- `MinecraftTerrainSampler` gets the generator from `level.getChunkSource().getGenerator()` and noise state from `level.getChunkSource().randomState()`:
  - height: `generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState)`;
  - biome: `generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), …, randomState.sampler())`;
  - **(verify)** that this samples the wrapped noise under Exotic Worlds' toroidal generator.

  Alternative: sample the pregenerated heightmaps, as `seam_terrain.py` did.
- `StartPlacer` maximises the minimum topology distance between starts and rejects tiles within 3 tiles of a seam.
- **Seam rule:** no city centre within 3 tiles (192 blocks) of any seam. If a seed puts land on a seam, mark those tiles "frontier" (no cities). Optionally pick seeds whose seams are mostly ocean (seed 0: 126 of 128 ocean).

### Step 7: Cities and territory (F1.7, F1.8)

- Register with `DeferredRegister.Blocks` / `DeferredRegister.Items` (NeoForge 1.21.1): `SettlerCharterItem`, `CityCenterBlock` and its block entity.
- When used, the charter submits a `FoundCity` command. On success the City Center block is placed.
- `BuiltinTerritory` cancels `BlockEvent.BreakEvent`, `BlockEvent.EntityPlaceEvent` and `PlayerInteractEvent.RightClickBlock` in foreign territory that is not at war. OPAC enforcement follows in Phase 2 (spike-verified).

### Step 8: Economy and research (F1.9, F1.10)

- Rules live in `civ-sim/rules`, with unit tests.
- `BuiltinTechGate` is a small locked-item list, checked on item use and crafting results (`PlayerEvent.ItemCraftedEvent` cannot cancel, so filter the recipe result instead, **(verify)** the hook). An `AStagesTechGate` adapter follows if richer locking is needed.
- JEI hiding goes through a JEI plugin (`IJeiRuntime` ingredient manager) **(verify)**.

### Step 9: Abstract armies and victory (F1.11, F1.13)

- A* on the tile grid with topology-aware neighbours.
- `ArmyMarkerEntity` is the first use of `ProjectionManager`. It handles the generation rule and cleanup on load (`EntityJoinLevelEvent#loadedFromDisk`).
- Chunk tickets for materialised armies: NeoForge's `TicketController`, registered in `RegisterTicketControllersEvent` **(verify)**. This replaces Forge's `ForgeChunkManager`.

### Step 10: AI v0 (F1.12)

As in [D §11](D-architecture.md#11-ai-architecture). `civ-sim-runner` runs headless games with a synthetic periodic-noise `TerrainSampler`.

### Step 11: UI and networking (F1.14, F1.15)

- Networking:
  - register in `RegisterPayloadHandlersEvent`: `event.registrar("1").playToClient(TYPE, STREAM_CODEC, handler)`;
  - payloads are `record … implements CustomPacketPayload` with a `StreamCodec`;
  - send with `PacketDistributor.sendToPlayer(player, payload)`.
- `StrategicMapScreen` draws tiles **modulo W/H**, so panning wraps endlessly on a torus.
- `/civ` Brigadier commands: `setup`, `status`, `pause`, `resume`, `speed`, `debug tick <n>`, `debug reveal`, `ai <civ> on|off`, `join`, `leave`.

### Step 12: MVP playtest

Run [H's playable script](H-mvp.md#playable-script-acceptance-test) on a dedicated server built from the packwiz pack. Tag `v0.1.0-mvp`.

## 4. Event and API reference (NeoForge 1.21.1)

| Purpose | API |
|---|---|
| Mod entry | `@Mod("civcore") public CivCoreMod(IEventBus modBus, ModContainer container)` (verified, minus container) |
| Registration | `DeferredRegister.Blocks/Items` via `DeferredRegister.createBlocks/createItems(MODID)`; `DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID)` etc. |
| Game bus | `NeoForge.EVENT_BUS` (verified) |
| Server lifecycle | `ServerStartingEvent` (verified), `ServerStartedEvent`, `ServerStoppingEvent` |
| Tick | `ServerTickEvent.Post` |
| Save | `LevelEvent.Save` |
| Data reload | `AddReloadListenerEvent`, `SimpleJsonResourceReloadListener` |
| Commands | `RegisterCommandsEvent` (verified) |
| Protection | `BlockEvent.BreakEvent`, `BlockEvent.EntityPlaceEvent`, `PlayerInteractEvent.RightClickBlock`, `ExplosionEvent.Detonate` |
| Entities | `EntityJoinLevelEvent` (`loadedFromDisk()`), `LivingDeathEvent` |
| Chunk tickets | `RegisterTicketControllersEvent`, `TicketController` |
| Fake players | `FakePlayerFactory.get(ServerLevel, GameProfile)` (verified). Note that MineColonies refuses fake players as subscribers |
| Networking | `RegisterPayloadHandlersEvent`, `CustomPacketPayload`, `StreamCodec`, `PacketDistributor` |
| Attachments | `AttachmentType`, `NeoForgeRegistries.ATTACHMENT_TYPES` |
| Mixins | `[[mixins]] config="civcore.mixins.json"` in `neoforge.mods.toml`; Mojang names at runtime, no refmap (verified) |
| MineColonies | `IColonyManager.getInstance().createColony(ServerLevel, BlockPos, Player, String, String)`, `getColonyByWorld`, `getBuilding`, `IColony.getServerBuildingManager().addNewBuilding(...)`, `AbstractBlockHut.onBlockPlacedByBuildTool(...)`, `IBuilding.requestUpgrade(Player, BlockPos)`, `IColony.getPermissions().addPlayer(UUID, String, Rank)`, event bus `IMinecoloniesAPI.getInstance().getEventBus().subscribe(...)` **after** server start (verified) |
| Recruits | `ModEntityTypes.RECRUIT.get().create(level)`, `setOwnerUUID(Optional)`, `setIsOwned`, `setGroupUUID`, `setAggroState`, `setFollowState`, `FormationUtils.squareFormation(Vec3 forward, List, Vec3 target, double spacing)`, `RecruitCommanderUtil.setRecruitsMove(List, BlockPos)` (verified; targets must be nearest-copy) |
| OPAC | `OpenPACServerAPI.get(server).getServerClaimsManager().tryToClaim(dim, uuid, 0, cx, cz, cx, cz, false)` → `getResultType()`; `get(dim, cx, cz).getPlayerId()` (verified) |

## 5. Definition of done for Milestone 1

- All F1.* acceptance criteria in [G](G-backlog.md) pass.
- CI is green: unit, property and ArchUnit tests, a 100-game headless AI batch, and GameTests.
- The MVP playtest script passes on a dedicated torus world with 2 human players and 1 AI.

## 6. Risks to watch

| Risk | Early signal | Response |
|---|---|---|
| Exotic Worlds API or behaviour changes (pre-1.0) | Seam tests fail after an update | Pin the version. Rerun `seam_tests.py`, `seam_terrain.py` and `seam_stress.py` before each bump |
| Another mod with custom pathfinding crashes at the seam | "Ticking entity" mentioning `exotic_worlds$shiftBy` | Our seam guard covers any path with a null target. Report other patterns upstream |
| Noise sampling doesn't match generated terrain | Tiles marked land are ocean in the world | Sample pregenerated heightmaps instead |
| Strategic map too slow | FPS drops on Medium | Bake the map to a `DynamicTexture` |
| AI stalls | Zero-growth civs in headless runs | Fallback rules; scenario tests |

## 7. Forge 1.20.1 differences (contingency only)

| Area | NeoForge 1.21.1 (primary) | Forge 1.20.1 |
|---|---|---|
| Java / build | 21 / ModDevGradle | 17 / ForgeGradle 6 + Parchment librarian |
| Metadata | `neoforge.mods.toml` | `mods.toml` |
| Networking | Payload registrar | `SimpleChannel` |
| Tick event | `ServerTickEvent.Post` | `TickEvent.ServerTickEvent` + phase |
| Chunk tickets | `TicketController` | `ForgeChunkManager` |
| Entity/chunk data | Data attachments | Capabilities |
| Wrapping | Exotic Worlds | Staged plan, [E §5](E-world-wrapping.md#5-staged-path) |

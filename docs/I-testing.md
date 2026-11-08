# I. Testing Strategy

## 1. Test layers

| Layer | Tool | Runs | Covers |
|---|---|---|---|
| Unit tests | JUnit 5 on `civ-sim` (no Minecraft) | Every commit, seconds | Formulas, topology math, combat, research, victory, serialisation round-trips |
| Simulation tests | Headless `civ-sim` runner with seeded games | Every commit (small set), nightly (large set) | AI behaviour, balance, determinism, long-game stability |
| Property tests | jqwik (or JUnit parameterised) | Every commit | Topology invariants, save/load equality, no-duplication in projection state machine |
| In-game tests | NeoForge GameTest (`@GameTestHolder`, `@GameTest`; `gradlew runGameTestServer` exits non-zero on failure) | CI | Block entities, protection, founding, projections, adapters |
| Pack boot test | Headless dedicated server with full packwiz pack | CI nightly + before release | Mod compatibility, config errors, startup crash |
| Load tests | Dedicated server + spark + scripted bots | Weekly / milestone | MSPT, memory, entity counts, chunk loading |
| Playtests | Real players | Each milestone | Fun, clarity, exploits |

## 2. By concern

### NeoForge / mod compatibility
- packwiz pack pinned to exact file IDs; a CI job boots a dedicated server with the full pack and fails on `ERROR`/exceptions in log or if "Done" is not reached in N seconds.
- Compatibility matrix spreadsheet: each pair of essential mods with a 10-minute manual scenario (e.g. recruits inside a MineColonies colony; OPAC claim over colony; Small Ships crewed by recruits).
- Adapter GameTests run with the target mod present; the same tests run with it absent to confirm fallbacks load.

### World generation
- Seeded snapshot tests: generate Tiny map for 10 fixed seeds; assert land fraction within configured range, ocean ring beyond radius, no land at world border.
- WorldPlan tests: start positions ≥ min distance (topology-aware); each start meets food/production minimum; resource counts per type within tolerance; deterministic for the same seed.
- Visual review: BlueMap render of 5 seeds per preset each milestone.

### World wrapping
- Regression scripts from the Phase 0 spike: [`seam_tests.py`](../spikes/neoforge-1.21.1/seam_tests.py), [`seam_terrain.py`](../spikes/neoforge-1.21.1/seam_terrain.py) and [`seam_stress.py`](../spikes/neoforge-1.21.1/seam_stress.py). Run them on every Exotic Worlds, Recruits or terrain-mod version bump.
- Topology unit tests: distance symmetry, triangle inequality, `distance ≤ W/2` on wrapped axes, normalisation idempotent.
- Physical seam tests (GameTest or scripted server tests): player, boat, Small Ship, arrow, horse with rider, recruit following player cross the seam; check position, velocity, passengers, no duplication, no void fall.
- City placement rules: founding within N tiles of a seam rejected.

### Dedicated multiplayer
- Always develop against a local dedicated server (`runServer`) as well as client.
- Tests with 2–4 clients: simultaneous commands on the same city; joining/leaving a civ; late join; latency simulated with a proxy (e.g. `tc`/clumsy) at 200 ms.
- Packet filtering test: log every outbound Civ packet for a client in a test game; assert no unexplored tile or unseen army appears.

### Save/load consistency
- Property test: random game → serialise → deserialise → `equals` (deep) and same hash.
- Determinism test: run 500 ticks; save at tick 250, reload, run to 500; compare hash with uninterrupted run.
- Crash test: kill -9 the server at random times during 50 runs; on restart the state equals the last completed save; no `*.tmp` corruption; projections cleaned (no stale entities with old generation).
- Schema migration tests: saves from each previous schema version load.

### AI decision-making
- Headless batch runner: `java -jar civ-sim-runner.jar --games 1000 --map small --civs 4 --seed-range 1-1000` → CSV of outcomes (winner, victory type, turn, cities per civ over time).
- Assertions: no stalls (civ with zero growth/production change for 50 ticks), no illegal commands, win-rate per personality within band, average game length in band.
- Scenario tests: fixed situations (AI capital threatened → builds defenders; AI with unused settler → founds city within 10 ticks).
- AI visibility test: AI controller receives only its visibility snapshot (assert by construction + test that AI decisions don't change when hidden enemy units are moved).

### Strategic simulation performance
- JMH microbenchmarks for strategic step and AI turn on Small/Medium/Large state sizes.
- Budgets: strategic tick < 20 ms total on Large with 12 civs (spread over ticks if exceeded); continuous step < 1 ms; AI off-thread.

### City and army integration
- Projection fuzz test (GameTest): randomly move a test player in and out of range, unload chunks, save/reload, kill entities; assert unit counts in records equal entities alive + deaths, and no entity with stale generation survives a load.
- MineColonies: founding creates colony with proxy owner; work order created from queue; `BuildingConstructionModEvent` updates strategic city; conquest transfers ownership.
- Recruits: materialised army count/HP match record; orders translate; deaths write back.
- Calibration: run 50 physical battles (scripted, no players) between equal-strength projected armies and compare to abstract combat predictions; tune abstract formula.

### Scalability across map sizes

| Scenario | Map | Civs (AI) | Players | Metric target |
|---|---|---|---|---|
| Baseline | Small | 2 (1) | 1 | MSPT < 25 |
| Typical | Medium | 6 (4) | 4 | MSPT < 35, RAM < 6 GB |
| Stress | Large | 12 (8) | 12 | MSPT < 45, RAM < 10 GB |
| Battle | Medium | 2 | 2 | 200 materialised soldiers + 2 colonies: MSPT < 45 |

Measured with spark over 30 minutes; players simulated with real clients or a bot client (e.g. headless clients; tooling to choose in Phase 0).

## 3. CI pipeline

1. `gradlew :civ-sim:test` (unit + property + small sim batch)
2. `gradlew build`
3. `gradlew runGameTestServer`
4. Nightly: full-pack dedicated server boot; 1,000-game AI batch; benchmark comparison against last baseline (fail on > 20% regression)

# Phase 0 Spike S0.1 — NeoForge 1.21.1 + Exotic Worlds

**Date:** 2026-11-08 · **Result: server-side GO.** Client-side checks are still pending (§5).

## 1. What was tested

A dedicated server with NeoForge **21.1.265** (Java 21), with the world set to `level-type=exotic_worlds:torus_medium`. That gives a 2,048 × 2,048-block torus with x and z in [−1024, 1024).

| Mod | Version | Source |
|---|---|---|
| Exotic Worlds | 1.3.3+mc1.21.1 | Modrinth |
| MineColonies | 1.1.1419-1.21.1-snapshot | ldtteam maven |
| Structurize / BlockUI / Domum Ornamentum / MultiPiston | 1.0.839 / 1.0.212 / 1.0.237 / 1.2.58 | ldtteam maven |
| Villager Recruits | 1.15.2 (1.21.1) | Modrinth |
| Open Parties and Claims | neoforge-1.21.1-0.33.0 | Modrinth |
| Tectonic + Lithostitched | 3.0.28 / 1.8.1 | Modrinth |
| Terralith | 2.6.2 | Modrinth |
| spark, Chunky | 1.10.124 / 1.4.23 | Modrinth |

Reproduce with [`spikes/neoforge-1.21.1/`](../spikes/neoforge-1.21.1/): `setup-server.sh <dir>`, then `start-server.sh <dir>`, then `seam_tests.py` and `seam_terrain.py`.

Note: MineColonies and its libraries are **not on Modrinth for NeoForge**. They come from `https://ldtteam.jfrog.io/artifactory/modding/com/ldtteam/` (CurseForge for players). Domum Ornamentum 1.21.1 is published as the `domum-ornamentum` artifact with a `-main.jar` classifier.

## 2. Results

| # | Test | Result |
|---|---|---|
| 1 | All 13 mods load together on a dedicated server | ✅ Boot in 13 s. **0 ERROR/Exception lines** over the whole session. Warnings were harmless (OPAC optional integrations absent, Structurize found no external packs) |
| 2 | Torus shape applied with Tectonic + Terralith installed | ✅ Log: `World shape: minecraft:overworld generator=exotic_worlds:toroidal … TORUS width 128 chunks (2048 blocks)`. The Nether is 256 blocks (1:8) and the End is 6,144 blocks |
| 3 | Pregeneration of the whole world (Chunky) | ✅ 16,641 chunks in **8 min 41 s** (≈ 58 chunks/s), **205 MB** on disk |
| 4 | Terrain continuity across seams (Tectonic + Terralith) | ✅ **Seamless.** Height difference between neighbouring columns (OCEAN_FLOOR heightmap, 512 samples per boundary) is shown below |
| 5 | Coordinates outside the world | ✅ Commands reject them ("1024 is outside this world. It must be between -1024 and 1023"). There is only one canonical coordinate range |
| 6 | Projectile crossing (arrow, x = 1022 → −1000) | ✅ Crossed in flight, keeping velocity and height |
| 7 | Minecart on powered rails across the seam | ✅ Crossed (1016 → −1010). Block adjacency and redstone power work across the seam |
| 8 | Vanilla mob AI across the seam | ✅ A zombie at x = 1020 targeted a villager at x = −1021, **pathed across the seam and killed it**, then walked back |
| 9 | Recruits entity across the seam | ✅ An unowned recruit at x = −1018 fought a zombie that crossed from x = 1020; melee landed across the seam and both moved freely around it |

Terrain continuity (absolute height difference between adjacent columns, in blocks):

| Boundary | Mean | Median | Max | Share > 8 |
|---|---|---|---|---|
| **Seam x: 1023 / −1024** | 0.39 | 0 | 12 | 1.2% |
| Interior x: 511 / 512 | 0.79 | 0 | 16 | 2.1% |
| Interior x: −1 / 0 | 0.84 | 0 | 40 | 2.0% |
| **Seam z: 1023 / −1024** | 0.38 | 0 | 15 | 1.0% |
| Interior z: 511 / 512 | 0.36 | 0 | 9 | 0.4% |

The seams are statistically indistinguishable from ordinary chunk borders. This was the main open risk: Exotic Worlds' docs warn that other mods' generators end in a cliff at the edge, but Tectonic and Terralith work through vanilla noise settings, so Exotic Worlds wraps them.

## 3. Projections from the measurements

| Our preset | Exotic Worlds preset | Chunks | Estimated pregen time | Estimated disk |
|---|---|---|---|---|
| Small 2,048² | `torus_medium` | 16.6 k | 9 min (measured) | 205 MB (measured) |
| Medium 4,096² | `torus_large` | 66 k | ≈ 35 min | ≈ 0.8 GB |
| Large 8,192² | `torus_huge` | 263 k | ≈ 2.3 h | ≈ 3.3 GB |

These are linear extrapolations from one run on this PC. Exotic Worlds' preset names are one size step off ours ("medium" = 2,048), so Civ Core's setup must map them explicitly. Custom sizes are possible through a world-preset datapack (see its `docs/dedicated-server.md`).

## 3b. Integration spike (S0.3 / S0.4 / S0.5), driven from code

A small test mod ([`spikes/integration-mod/`](../spikes/integration-mod/), GPL-3.0, server-side only) adds `/civspike` commands. It uses only public APIs and methods, with no mixins. [`integration_tests.py`](../spikes/neoforge-1.21.1/integration_tests.py) and [`integration_tests2.py`](../spikes/neoforge-1.21.1/integration_tests2.py) drive it over RCON. The test site was a flat stone island built straddling the east–west seam (x 960 … 1023 | −1024 … −961, z 300 … 363). On seed 0 the seam is ocean at 126 of 128 sampled points.

| # | Test | Result |
|---|---|---|
| 10 | **Crash: Exotic Worlds × Recruits async pathfinding** | ❌→✅ **Fixed by us.** Exotic Worlds' `PathMixin.exotic_worlds$shiftBy` offsets vanilla `Path#target` without a null check. Recruits' `AsyncPath` passes `null` to `super(…)` and keeps its real target in its own field, so *every* async recruit path crossing the seam crashed the server ("Ticking entity" NPE), and the JVM then hung instead of exiting. Fix: [`SeamSnapGuardMixin`](../spikes/integration-mod/src/main/java/dev/civcore/spike/mixin/SeamSnapGuardMixin.java) runs just before Exotic Worlds' `SeamSnap.shiftNavigation` and drops any path whose vanilla target is null; the mob re-plans next tick on the new side. **Verified:** with async pathfinding **on**, 10 recruits made 8 crossings, the guard fired 74 times (each one would have been a crash), and there were 0 crashes. The guard is Civ Core's permanent fix; nothing will be filed upstream |
| 11 | Several colonies owned by one proxy (FakePlayer) | ✅ Calling `IColonyManager.createColony` directly bypasses the one-colony-per-owner check. The proxy `nationA` owns colonies 1 and 2, `nationB` owns colony 3. `getIColonyByOwner` returns only the first, so Civ Core must keep its own city → colony map |
| 12 | Colony claim and building across the seam | ✅ A builder hut placed at x = −1004 registered to colony 1, whose town hall is at x = 990, 44 blocks away across the seam. A `BUILD 0→1` work order was created at the wrapped position. The colony border wraps |
| 13 | Proxy-owned colony ACTIVE with no real player | ❌ **Not possible without mixins.** MineColonies explicitly rejects FakePlayers as subscribers (`ColonyPackageManager.addCloseSubscriber` / `addImportantColonyPlayer`: "Adding fakeplayer as subscriber: this should not happen"), and a colony is ACTIVE only with real subscribers. **This matches the plan:** distant cities run abstractly in Civ Core, and colonies run when a real player is near. Builder progress therefore needs the client checklist |
| 14 | Instant level-1 town hall paste via `CreativeBuildingStructureHandler.loadAndPlaceStructureWithRotation` with a FakePlayer | ❌ Placed nothing; the town hall stayed at L0. Cause not investigated. The AI-city blueprint paste (F2.3) needs its own spike, probably calling Structurize placement directly |
| 15 | Recruits owned by a proxy UUID; orders without a player | ✅ 10 recruits spawned with `setOwnerUUID`, `setGroupUUID` and aggressive stance, then moved with `FormationUtils.squareFormation(forward, list, target, spacing)`, a public static helper that needs no player. **Formations are confirmed to exist in Recruits** |
| 16 | Move order to a point across the seam | ⚠ Recruits uses **raw coordinates**. Ordered to x = −1000 from x = 1000, the army walked *away* from the seam (the long way) and stopped at the island edge. Ordered to the unwrapped copy x = 1048, 7 of 10 crossed the seam, but then kept walking past the target, because arrival is checked against raw coordinates. **Civ Core's Recruits bridge must issue orders as nearest-copy coordinates and re-issue them as units cross.** This was already planned as `Topology.nearestCopy`; it is now required |
| 17 | Combat across the seam | ✅ Zombies on the west side crossed and were killed by recruits on the east side (6 → 1 alive) |
| 18 | OPAC claims by proxy UUIDs on both sides of the seam | ✅ `tryToClaim` → `SUCCESSFUL_CLAIM` for chunks 63,19 and −64,19 (adjacent across the seam). Another proxy claiming the same chunk → `ALREADY_CLAIMED` |
| 19 | Persistence across restarts | ✅ Colony owners and buildings, OPAC claims, and recruit ownership and HP all survive a restart. Note: entities load a few seconds after `Done`, so Civ Core's projection reconciliation must wait for entity load before declaring a unit missing |

**Config decisions from this spike:** Recruits `UseAsyncPathfinding` can stay **on** while the seam-guard mixin is present (turn it off if Civ Core ever ships without the guard); Recruits `[Claiming] AllowClaiming=false` (exists; Civ Core owns territory); MineColonies `creativeresolve=true` only on the test server.

## 4. Not tested on the server

- **A MineColonies colony near the seam.** Founding needs a player or code (the S0.3 spike).
- **A recruit commanded by its owner across the seam** (follow, move-to). That needs a player or the S0.4 bridge code.
- **OPAC claims spanning the seam.**
- **Performance under players.** spark output does not come back over RCON. Profile in-game.

## 5. Client checklist (needs a person)

**Ready-made setup:**
- **Server:** `C:\Users\Bzier\civ-spike-server`. It is pregenerated, has the test island at the seam, and has the `civspike` mod, with Recruits async pathfinding off. Start it with `spikes/neoforge-1.21.1/start-server.sh ~/civ-spike-server` and type `op <your name>` in its console.
- **Client:** `C:\Users\Bzier\civ-spike-client.zip`. In the CurseForge app, go to **Create Custom Profile → Import** and pick that zip. It contains NeoForge 21.1.265, all the spike mods and Xaero's maps. Rebuild it with `build-client-zip.sh`.
- **Test site:** colony "rome" has its town hall at **990 64 330**, a builder hut across the seam at **−1004 64 330**, and recruits nearby. Walking there as a real player should make the colony ACTIVE so the builder starts working, with materials auto-supplied for the test.

1. Walk, sprint, ride a horse and sail a boat across both seams. Is the transition invisible (no chunk pop-in, no rubber-banding)?
2. Look across the seam from about 50 blocks away. Is terrain rendered on the far side?
3. Fly over the 4-way corner (±1024, ±1024).
4. Found a MineColonies colony with its town hall about 40 blocks from the seam. Place a builder hut across the seam and let it build.
5. Hire a recruit and order follow, move-to and attack across the seam.
6. Claim chunks on both sides of the seam with OPAC. Check protection and Xaero's display.
7. Check Xaero's World Map near the seam (it will likely show two ends, which is acceptable).
8. Run `/spark tps` and `/spark health` while doing the above.


## 5b. Client results (2026-11-08, played by the project owner, about 30 min)

| Area | Result |
|---|---|
| Seam crossing (walking, looking across, building) | ✅ "No problems with the seam." Nothing visibly wrong |
| Xaero minimap near the seam | ✅ Worked, showing the island on both sides |
| Colony with a real player nearby | ✅ `rome` went **ACTIVE** as soon as a real player arrived, spawned 4 citizens and hired a builder. This confirms MineColonies wakes up for real players and ignores fake ones |
| **Builder working across the seam** | ❌ **MineColonies' own pathfinding is not wrap-aware.** The builder claimed the work order and then logged `is trying to walk too far! distance:1994 from (990,64,329) to (-1004,64,330)` **1,595 times** in 26 min. It measures raw coordinates (1,994 blocks) instead of the 50-block distance across the seam, so it never walks over. Exotic Worlds wraps vanilla navigation and ships compat mixins for several mods, but **not for MineColonies' custom navigator** |
| Performance (player in an active colony, recruits nearby) | ✅ TPS 20.0. Tick time median 2.4 ms, 95th percentile 4.0–4.2 ms, max 5.7 ms. Process CPU 1%. Memory 1.5 / 6.0 GB |
| OPAC claiming | Not tested (claim UI unclear). The code test already passed (§3b row 18) |
| Server stop | The console received `stopstop` (no command ran) while the log was flooded by the builder error. The window was then closed, killing the process, so the world kept only its last autosave. Not a mod issue; it is a side effect of the log spam. Use `stop` once in a quiet console, or `python rcon.py stop` |

**What this means:**
- MineColonies cities must not straddle a seam. The plan already says "forbid cities within N tiles of a seam" and "put seams in ocean" ([E §3](E-world-wrapping.md#3-technical-analysis-of-seamless-wrapping)). This is now a **hard rule**: no city centre within 3 tiles (192 blocks) of a seam, and Civ Core rejects colony buildings whose wrapped and raw distances to the town hall differ.
- On seed 0 the east–west seam is ocean at 126 of 128 points, so this costs little.
- A real fix would be a MineColonies navigation compat inside Exotic Worlds, which is where its other compat mixins live. We won't pursue that upstream, and MineColonies' addon policy discourages mixins into its code, so the exclusion zone is the fix.
- Recruits (move orders, §3b row 16) and MineColonies (this row) both show the same pattern: **mods with custom pathfinding need the seam handled for them.** Civ Core's adapters own that job (nearest-copy targets; no cross-seam assignments).

## 6. Decision

Update after the integration spike: still **GO**, with two required mitigations:
1. Ship the seam-guard mixin in Civ Core as a permanent fix (no upstream fix is planned).
2. Build nearest-copy order translation into the Recruits bridge.

The MineColonies fake-player restriction confirms the abstract-city design and does not change it.

**Final (after client test): ADOPT NeoForge 1.21.1 + Exotic Worlds**, with a third mitigation: a seam exclusion zone for cities (§5b), because MineColonies' pathfinding is not wrap-aware.

Server-side evidence supports adopting **NeoForge 1.21.1 + Exotic Worlds**. Wrapping, terrain continuity, entities, redstone and vanilla/Recruits AI all work across the seam with the planned terrain stack, and the whole set loads with zero errors. Confirm with the client checklist (about 30 minutes of play), then:

- Switch the pack target to NeoForge 1.21.1 (update [C](C-modpack-foundation.md) §6 substitutions and the [J](J-implementation-guide.md) §8 table to primary).
- Drop the staged 1.20.1 wrap plan ([E](E-world-wrapping.md) §5) to a contingency.
- Before public release, ask the Exotic Worlds author to confirm that a modpack depending on it is fine under PolyForm Shield.

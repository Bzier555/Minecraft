# Minecraft Civilization — Planning Package

Research and planning for a Civilization-style 4X modpack on Minecraft Java. No production code has been written yet; this folder contains the planning deliverables requested in the project brief.

Research date: **2026-11-08**. Mod versions, dates and licences were checked against GitHub repositories (including LICENSE files and source), CurseForge, Modrinth and official documentation. Anything not confirmed from a primary source is marked **unverified** in the documents.

## Building

Requires JDK 21. From the project folder:

```bash
./gradlew build
```

This runs the `civ-sim` tests and produces `civ-mod/build/libs/civcore-<version>.jar`.

`civ-mod` compiles against Exotic Worlds' API from its installed jar. Before the first build, extract the nested NeoForge jar once:

```bash
unzip -o -j ~/civ-spike-server/mods/exotic_worlds-1.3.3+mc1.21.1.jar "META-INF/jarjar/*neoforge*.jar" -d civ-mod/libs
```

**To try it:** put the jar in the spike server's `mods/` folder (see [spike-platform.md](docs/spike-platform.md)) and in the client profile (`spikes/neoforge-1.21.1/build-client-zip.sh`). Then:
1. run `/civ newgame auto` and wait for "World analysed";
2. use the Settler Charter each player receives on the ground to found a capital;
3. check progress with `/civ status`.

## Documents

| # | Document | What it answers |
|---|---|---|
| A | [Feasibility assessment](docs/A-feasibility.md) | Is this achievable, what are the biggest risks, and which platform to build on |
| B | [Technology inventory](docs/B-technology-inventory.md) | Every relevant mod and repository: links, versions, licences, reuse recommendation |
| C | [Recommended modpack foundation](docs/C-modpack-foundation.md) | Essential / optional / dev / experimental / avoid lists |
| D | [System architecture](docs/D-architecture.md) | Civ Core design, data model, simulation timing, strategic vs physical layers, adapters |
| E | [World wrapping report](docs/E-world-wrapping.md) | Whether a toroidal world is practical and a staged path to get there |
| F | [Game design specification](docs/F-game-design.md) | How a game plays from founding to victory |
| G | [Prioritized backlog](docs/G-backlog.md) | Features with priority, dependencies, risk, effort, acceptance criteria |
| H | [Minimum viable prototype](docs/H-mvp.md) | The smallest playable game that validates the concept |
| I | [Testing strategy](docs/I-testing.md) | How to test compatibility, worldgen, AI, save/load, performance |
| J | [Implementation guide](docs/J-implementation-guide.md) | Step-by-step tasks for a coding agent to start milestone 1 |
| — | [Platform spike report](docs/spike-platform.md) · [Client test plan](docs/client-test-plan.md) | Phase 0 evidence for the NeoForge 1.21.1 decision |

## Executive summary

**The project is feasible, with one large custom component.** No existing mod provides a Civilization game. Existing mods provide strong *physical* building blocks: NPC cities (MineColonies), NPC armies (Villager Recruits), ships (Small Ships), territory protection (Open Parties and Claims), terrain (Tectonic + Terralith) and industry (Create). What does not exist anywhere, and must be custom, is the **Civ Core**: a server-authoritative strategic simulation that owns nations, cities, territory, yields, research, diplomacy, armies-as-abstract-groups, AI civilizations and victory. Everything else is adapters.

**Key findings**

1. **MineColonies is usable but cannot be the "city brain".** It is GPL-3.0 with a real Java API and event bus. But it allows **one colony per owner per dimension** (verified in `CreateColonyMessage.java`), **does not simulate unloaded colonies** (verified in `Colony.java`), and its colony creation requires a `ServerPlayer` plus a placed town hall. Civ Core must simulate cities abstractly and drive MineColonies as the *physical* representation of nearby cities.
2. **Villager Recruits is the best army layer**, but it is All Rights Reserved with no documented API. It exposes public entity setters, formation helpers and events that our bridge mod calls (verified in the spike). Use as a dependency only, never fork.
3. **Seamless torus wrapping works on NeoForge 1.21.1 via Exotic Worlds** (PolyForm Shield licence; depending on it is allowed). It does not exist on Forge 1.20.1. "Seamless World Wrap" does not appear to exist as a mod; the phrase is Circumnavigate's tagline.
4. **Fog of war can be made server-authoritative for strategic information** (cities, armies, resources, ownership) because Civ Core owns that data. It cannot fully hide *terrain* from a client that has loaded the chunks.
5. **Territory and nation mods** are almost all All Rights Reserved and small. **Open Parties and Claims** (LGPL-3.0, documented server API) is the right protection and map-display backend; nation rules go in Civ Core.

## Platform: NeoForge 1.21.1 (decided 2026-11-08)

The brief preferred Forge 1.20.1 and asked for evidence before any change. The evidence ([A §3](docs/A-feasibility.md#3-platform-decision)) and a Phase 0 spike ([spike-platform.md](docs/spike-platform.md)) led to adopting **Minecraft 1.21.1 + NeoForge 21.1.265 + Exotic Worlds** for seamless torus wrapping:

- **The only seamless world-wrap implementation (Exotic Worlds) exists only on 1.21.1+.** Every core mod also ships for 1.21.1, and the 1.20.1 world-generation and performance libraries are freezing.
- **Spike results:**
  - The core stack loaded with zero errors.
  - Tectonic + Terralith terrain is as smooth across the seams as across ordinary chunk borders.
  - Arrows, minecarts, mob pathfinding and Recruits combat cross the seam.
  - In-game crossing looked seamless.
  - TPS held at 20 (median tick 2.4 ms) with a player in an active colony.
- **Issues found and handled:**
  - Recruits' async pathfinding crashed the server at the seam (a missing null check in Exotic Worlds). **Fixed** by a small mixin in our mod: 8 crossings, 74 guarded cases, 0 crashes.
  - Recruits move orders and MineColonies builders use raw coordinates. Civ Core gives units their target on whichever side of the seam is closer, and **no city may sit within 192 blocks of a seam**.
  - MineColonies refuses fake players as subscribers, so colonies run only when a real player is nearby. That matches the abstract-city design.
- **Lost by moving off 1.20.1** (all replaced, see [C](docs/C-modpack-foundation.md)): GameStages → AStages or our own gate; talhanation's Siege Weapons → Medieval Siege Machines; Embeddium → Sodium; Canary → Lithium.

The Forge 1.20.1 path remains documented as a contingency ([C §6](docs/C-modpack-foundation.md#6-forge-1201-contingency), [E §5](docs/E-world-wrapping.md#5-staged-path), [J §7](docs/J-implementation-guide.md#7-forge-1201-differences-contingency-only)).

## Other decisions flagged for you

| Decision | Why it matters | Where |
|---|---|---|
| Project licence | MineColonies is GPL-3.0; an adapter that links it is a derived work. Simplest: release Civ Core under GPL-3.0 (or LGPL-3.0 core + GPL adapters) | [A §5](docs/A-feasibility.md#5-licensing-constraints) |
| Strategic tile size | Fixes the scale of the whole game (recommend 4×4 chunks = 64 blocks) | [D §4](docs/D-architecture.md#4-the-strategic-grid) |
| Time model | Real-time "strategic ticks" (recommend 60 s default) instead of turns | [F §2](docs/F-game-design.md#2-time-model) |
| MineColonies policy | Its maintainers discourage invasive (mixin) addons and AI-generated contributions **to MineColonies itself**. Our integration stays API-only in our own mod | [A §5](docs/A-feasibility.md#5-licensing-constraints) |

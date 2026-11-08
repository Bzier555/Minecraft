# C. Recommended Modpack Foundation

Target: **Minecraft 1.21.1, NeoForge 21.1.265, Java 21.** This was adopted 2026-11-08 after the Phase 0 platform spike; see [spike-platform.md](spike-platform.md).

Versions are the latest NeoForge 1.21.1 builds seen on 2026-11-08 (Modrinth API, ldtteam maven, CurseForge). Rows marked ✔ were **run together on the spike server**. Pin exact file IDs in packwiz before the first public build.

Principle: **one system per job.** Every mod below either physically represents something Civ Core owns, or is a supporting library. Anything that runs its own competing strategic layer (factions, claims, research trees, raids) is disabled, configured off, or excluded.

## 1. Essential (MVP and Phases 2–3)

| Mod | Version (1.21.1) | Function in this pack | Depends on | Config notes |
|---|---|---|---|---|
| **Civ Core** (ours) | — | Strategic simulation, nations, cities, territory, research, AI, victory, strategic map. Includes the **seam-guard mixin** for Exotic Worlds | — | — |
| ✔ **Exotic Worlds** | 1.3.3+mc1.21.1 | Torus or cylinder world with seamless wrapping | — | World preset chosen at game setup. Its preset names are one size off ours: `torus_medium` = 2,048 blocks |
| ✔ **MineColonies** | 1.1.1419-1.21.1-snapshot | Physical player cities (Phase 2) | Structurize, BlockUI, Domum Ornamentum, MultiPiston | `enablecolonyraids=false` (Civ Core owns warfare). **No colony within 192 blocks of a seam**: its pathfinding is not wrap-aware |
| ✔ Structurize / BlockUI / Domum Ornamentum / MultiPiston | 1.0.839 / 1.0.212 / 1.0.237 / 1.2.58 | Blueprints, style packs, AI city pastes | — | Civ-era style packs |
| ✔ **Villager Recruits** | 1.15.2 | Physical soldiers (Phase 3) | — | `[Claiming] AllowClaiming=false`. `UseAsyncPathfinding` may stay on **only with Civ Core's seam guard installed** |
| **Small Ships** | 2.0.2-neoforge-1.21.1 | Naval units | — | — |
| ✔ **Open Parties and Claims** | neoforge-1.21.1-0.33.0 | Block protection and claim display for nation territory | — | Players cannot claim manually; Civ Core claims through the API under proxy UUIDs (verified) |
| ✔ **Tectonic** + **Lithostitched** | 3.0.28 / 1.8.1 | Continents, mountain ranges | — | Verified seamless across torus seams |
| ✔ **Terralith** | 2.6.2 | Biome variety | Lithostitched | Use the mod version, not the datapack |
| **KubeJS** (+ Rhino, Architectury) | 2101.7.2 / 2101.2.8 / 13.0.11 | Recipe and era changes, small server scripts | Rhino, Architectury | Pack scripts only; no core game logic in JS. The 1.21 KubeJS script API differs from 1.20.1 |
| **AStages** | 2.5.3-1.21.1 | Enforces tech locks per player (stages synced from nation tech) | — | ARR. GameStages has **no** 1.21.1 build. Fallback: Civ Core's built-in item and recipe gate |
| **JEI** | 19.70.x | Recipe viewer; hides locked items | — | — |
| ✔ **Xaero's Minimap + World Map** | 26.6.0 / 1.47.0 | Navigation; shows OPAC territory | — | Server: disable entity radar and cave mode for fair play. Exotic Worlds ships a Xaero compat |
| Epic Knights | 10.17 | Era weapons and armour | Architectury, Cloth Config | — |
| ModernFix, FerriteCore | 5.27.27 / 7.0.3 | Load time, memory | — | — |
| ✔ spark | 1.10.124 | Profiling (always installed; ops only) | — | — |
| ✔ Chunky | 1.4.23 | Pregeneration | — | Pregenerate during game setup (2,048² took 8 min 41 s) |

## 2. Optional gameplay enhancements (after MVP)

| Mod | Version (1.21.1) | Function | Notes |
|---|---|---|---|
| Create | 6.0.10 | Early Industrial production, trains | Gate behind Early Industrial tech. Exotic Worlds ships a Create compat |
| Lightman's Currency *or* Create: Numismatics | 2.3.0.6 / 1.1.0 | Physical coins, traders, team bank accounts | Choose one. The treasury is authoritative in Civ Core |
| Medieval Siege Machines | 1.33 | Siege engines | Replaces talhanation's Siege Weapons, which has no 1.21.1 build. Check the Recruits compat addon's 1.21.1 build before relying on recruits crewing engines |
| Musket Mod | 1.5.4 | Gunpowder units | Early Industrial |
| Towns and Towers | 1.13.11 | Ruins and independent-settlement structures | Exploration rewards. Check structure placement on the torus |
| Item Obliterator | 2.3.0 | Remove anachronistic items | — |
| FTB Quests | CurseForge only | Tutorials and onboarding | ARR; not the tech tree |
| BlueMap | 5.7 | Admin or spectator web map | Server-side only. Seam behaviour unknown |
| Lithium | mc1.21.1-0.15.4 | Server tick optimisation | Replaces Canary/Radium. Exotic Worlds ships a Lithium compat. Test with MineColonies and Recruits |
| Sodium | 0.8.13 | Client FPS | Replaces Embeddium. Exotic Worlds ships a Sodium compat |

## 3. Development utilities (dev profile only)

- spark, Chunky (also in release)
- BlueMap (admin view of the whole world)
- JEI, Jade (block info)
- packwiz (in git); the CurseForge app for export testing
- `civspike` integration mod ([spikes/integration-mod](../spikes/integration-mod/)), until Civ Core replaces it
- A Civ Core debug overlay (ours): tile grid, territory, abstract army positions

## 4. Experimental integrations (spikes, not committed)

| Mod / approach | Question to answer |
|---|---|
| Reign of Nether code (GPL) | Can its RTS camera and selection drive Recruits entities? Check its 1.21.1 status |
| Hundred Years Warfare (0.8.0r for 1.21.1) | Does it have better formations than Recruits? (Closed source, ARR) |
| JourneyMap API (6.0.9) vs Xaero | Which gives better civ overlays on a torus? |
| MineColonies navigation compat for Exotic Worlds | Upstream request: would make cross-seam colonies possible |
| AI Improvements | No 1.21.1 build seen (last 2024). Find an alternative for cheaper soldier AI |

## 5. Avoid

| Mod | Reason |
|---|---|
| FTB Chunks / FTB Teams as the territory base | ARR, undocumented API, overlaps OPAC |
| Any other faction or nation mod (Nation, Warring Nations, Faction Friction, Factions Proper, CivClash, Timo's Nations) | Each runs its own territory and war rules that conflict with Civ Core |
| Villager Workers | Overlaps the MineColonies city workforce |
| Guard Villagers | Overlaps Recruits |
| Millénaire | ARR, and its villages overlap Civ Core's city-states |
| C2ME | Thread-safety risk with custom worldgen and MineColonies (Exotic Worlds has a compat, but don't take the risk for MVP) |
| Other terrain generators that replace the noise router (e.g. ReTerraForged, Continents) | Exotic Worlds only wraps vanilla-noise-based terrain unless it ships a compat; anything else must be verified for seam continuity with `seam_terrain.py` |
| Oceanus / world-border falloff | Unnecessary on a torus (no edge) |
| Valkyrien Skies | Heavy; no NPC crews; high risk with wrapping |
| Mods that despawn or freeze distant entities (Let Me Despawn and similar) | Can delete or freeze materialised armies. Civ Core manages unit lifecycle itself |

## 6. Forge 1.20.1 contingency

This applies only if NeoForge 1.21.1 has to be abandoned. Use the staged wrap plan in [E §5](E-world-wrapping.md#5-staged-path):
- Oceanus + More Density Functions, with a world border
- a Looping World port
- GameStages + ItemStages
- Embeddium, Canary
- talhanation's Siege Weapons

## 7. Pack build process

1. packwiz repository (`pack/`) in git is the source of truth: `pack.toml` (`minecraft = 1.21.1`, `neoforge = 21.1.265`), one `.pw.toml` per mod with pinned file IDs, `config/`, `defaultconfigs/`, `kubejs/`, datapacks.
2. `packwiz curseforge export` produces the CurseForge zip. ARR mods are referenced by manifest, never bundled. MineColonies and its libraries are on CurseForge (not Modrinth) for NeoForge.
3. The dedicated-server pack is built by the same pipeline. The spike's [`setup-server.sh`](../spikes/neoforge-1.21.1/setup-server.sh) shows the download sources. For anything public, check redistribution permissions per ARR mod.

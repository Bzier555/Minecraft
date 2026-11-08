# B. Existing Technology Inventory

Checked 2026-11-08. **Versions below are mostly the Forge 1.20.1 builds researched first; the project has since adopted NeoForge 1.21.1. See [C](C-modpack-foundation.md) for the 1.21.1 versions actually used.** "Verified" means seen on the project's own page, repository, LICENSE file or source. Items marked *unverified* need re-checking before you lock versions. **Rec** = Use / Adapt / Study / Reject.

Integration types used below:
- **Dependency**: ship the official jar in the pack and call its API at runtime. No code copied.
- **Fork/Port**: copy and modify source. Only possible where the licence allows.
- **Study**: read for design; copy no code.

---

## 1. Cities and settlements

| Project | Links | Versions / Loader | Licence | Reusable features | Integration | Maintenance | Limitations | Rec |
|---|---|---|---|---|---|---|---|---|
| **MineColonies** | [GitHub](https://github.com/ldtteam/minecolonies) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/minecolonies) · [Wiki](https://minecolonies.com/wiki) | 1.20.1 Forge (`version/main`, 1.1.1317-snapshot, 2026-11-08); 1.21.1 NeoForge (`version/1.21`, 1.1.1419-snapshot) | GPL-3.0 (LICENSE file) | Town hall, ~40 jobs, builders constructing blueprints, request/logistics system, research, raids, happiness, quests | Dependency + Java API (`IMinecoloniesAPI`, `IColonyManager`, `IColony`, `IPermissions`, `IWorkManager`, `IBuilding`) + own event bus (`ColonyCreatedModEvent`, `BuildingConstructionModEvent`, `CitizenDiedModEvent`, …) | Several releases per day | **One colony per owner per dimension** (verified in `CreateColonyMessage.java`); no simulation when unloaded (`ColonyState` ACTIVE/UNLOADED/INACTIVE); `createColony` needs a `ServerPlayer` and an existing town hall block; no conquest mechanic; internals unstable; discourages mixin addons | **Use** as physical city layer |
| **Structurize** (+ BlockUI, Domum Ornamentum) | [GitHub](https://github.com/ldtteam/structurize) | 1.20.1 Forge; 1.21.1 NeoForge | GPL-3.0 | Blueprint format, style packs (data only), Scan/Build tools, `StructurePlacer` | Dependency; data-only style packs; placement API is internal-ish | Active | Placement API undocumented | **Use** (Civ-era style packs; blueprint paste for AI cities) |
| MineColonies addons (Compatibility, Tweaks, Colony Workshop) | [Compatibility](https://www.curseforge.com/minecraft/mc-mods/minecolonies-compatibility) · [Tweaks](https://www.curseforge.com/minecraft/mc-mods/minecolonies-tweaks) · [Colony Workshop](https://www.curseforge.com/minecraft/mc-mods/colony-workshop) | 1.20.1 Forge | *unverified* | Examples of third-party extension | Study | 2026 releases | Source/licence not checked | **Study** |
| **Millénaire** | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/millenaire) · [GitLab](https://gitlab.com/Millenaire/Public) | 1.12.2 Forge; 1.21.1 NeoForge (9.0.2, Sep 2026). **No 1.20.1** | Custom, All Rights Reserved | Culture-based growing villages, trade, reputation | — | Revived on 1.21.1 | Wrong version for 1.20.1; restrictive | **Study** (independent-settlement design) |
| Millénaire rewrites | [WangMioG](https://github.com/WangMioG/Millenaire-rewrite) · [gblfxt](https://github.com/gblfxt/Millenaire-rewrite-1.21.1) | Alpha | Claims MIT; provenance unclear | — | — | 2025–Jan 2026 | Immature | **Reject** |
| MCA Reborn | [GitHub](https://github.com/Luke100000/minecraft-comes-alive) · [Modrinth](https://modrinth.com/mod/minecraft-comes-alive-reborn) | 1.20.1 Forge 7.7.1-beta.5 | GPL-3.0 | Human villagers, families | — | Active | Social flavour only; untested with MineColonies | **Reject** for core (optional flavour) |
| Towns and Towers | [Modrinth](https://modrinth.com/mod/towns-and-towers) | 1.20.1 Forge | CC-BY-NC-SA-4.0 | Worldgen structures | — | Dec 2023 | Decoration only | **Optional** (independent settlements/ruins) |
| Project Sid (Altera) | [Paper](https://arxiv.org/abs/2411.00114) · [GitHub](https://github.com/altera-al/project-sid) | Research | No licence shown | LLM agent civilisations | — | Nov 2024 | Not a mod; LLM-based (brief prefers deterministic AI) | **Study** only |

## 2. Military, siege, naval

| Project | Links | Versions / Loader | Licence | Reusable features | Integration | Maintenance | Limitations | Rec |
|---|---|---|---|---|---|---|---|---|
| **Villager Recruits** | [GitHub](https://github.com/talhanation/recruits) · [Modrinth](https://modrinth.com/mod/villager-recruits) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/recruits) · [Wiki](https://github.com/talhanation/wiki/wiki/Villager-Recruits-Wiki) | 1.20.1 Forge 1.15.2 (Jun 2026); 1.21.1 NeoForge 1.15.2 (Nov 2026) | **All Rights Reserved** (README; no LICENSE file) | Unit classes (recruit, shieldman, bowman, crossbowman, horseman, nomad; more in source), command screen (move/follow/hold/stances), groups that work while unloaded, upkeep, factions, claims, sieges, async pathfinding pool | Dependency + bridge: `AbstractRecruitEntity` public setters (`setOwnerUUID`, `setFollowState`, `setMovePos`+`setShouldMovePos`, `setHoldPos`, `setAggroState`, `setGroupUUID`, `assignToPlayer`), Forge events (`RecruitEvent.Hired/Dismissed/Promoted/LevelUp`, `ClaimEvent`, `SiegeEvent`, `DiplomacyEvent`, …) | Very active | No documented API; internals refactored often; own faction/claim/siege system overlaps Civ Core; formations *unverified* | **Use** (army physical layer) |
| Villager Workers | [Modrinth](https://modrinth.com/mod/villager-workers) | 1.20.1 Forge | ARR | Farmer, miner, builder, merchant… | Requires Recruits | Active | Overlaps MineColonies | **Reject** (pick MineColonies for cities) |
| **Small Ships** | [GitHub](https://github.com/talhanation/smallships) · [Modrinth](https://modrinth.com/mod/small-ships) | 1.20–1.20.1 Forge/Fabric/NeoForge | ARR | 7 sailing ships, cannons, wind, ramming, sinking; Recruits can crew them | Dependency | Updated Oct 2026 | — | **Use** (naval units) |
| Siege Weapons (talhanation) | [Modrinth](https://modrinth.com/mod/siegeweapons) | **1.20.1 only** | ARR | Catapult, ballista, siege table | Dependency; Recruits compat | ~Apr 2026 | 1.20.1 only | **Use** on 1.20.1 |
| Medieval Siege Machines | [Modrinth](https://modrinth.com/mod/medieval-siege-machines) | 1.20.1 Forge v1.39 | ARR | Trebuchet, ram, ladders, culverin, mortar | Dependency | Jan 2026 | Dependency identity *unverified* | **Use** (alt to Siege Weapons) |
| Recruits–Medieval Siege Machine compat | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/recruits-medieval-siege-machine-mod-compat-addon) | 1.20.1 Forge 2.1.0 | **MIT** | Recruits operate siege engines | Dependency; **MIT code is a worked example of integrating with Recruits** | Feb 2026 | — | **Use + study** |
| Epic Knights | [GitHub](https://github.com/Magistu/Epic-Knights) · [Modrinth](https://modrinth.com/mod/epic-knights-shields-armor-and-weapons) | 1.20.1 Forge 10.x | ARR (pack use allowed) | Era armour/weapons; Recruits compat | Dependency | Nov 2026 | — | **Use** |
| Musket Mod | [GitHub](https://github.com/ewewukek/mc-musketmod) | 1.20.1 | MIT | Muskets; Recruits compat folder exists | Dependency | Oct 2024 | — | **Optional** (Early Industrial era) |
| Hundred Years Warfare | [Modrinth](https://modrinth.com/mod/hundred-years-warfare) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/hundred-years-warfare) | 1.20.1 Forge 0.8.0r; 1.21.1 NeoForge/Fabric | ARR, no source | RTS mode, formations, command wheel, unit counters | — | Active, early alpha | Closed, overlaps Recruits | **Study** / evaluate as alternative |
| **Reign of Nether** | [GitHub](https://github.com/SoLegendary/reignofnether) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/reign-of-nether-rts-in-minecraft) | 1.20.1 Forge 1.5.1c (Nov 2026) | **GPL-3.0** | Orthographic RTS camera, box select, `UnitFormations`, `FormationDragMove`, fog of war, minimap | Adapt code into a GPL-3.0 module | Weekly releases | Separate game mode; its own unit classes | **Adapt** (RTS command view) / reject as pack mod |
| Guard Villagers | [GitHub](https://github.com/seymourimadeit/guardvillagers) | 1.20.1 Forge | Code MIT, assets custom | Village guards | — | 1.20.1 may be frozen | Overlaps Recruits | **Reject** |
| Create Big Cannons | [GitHub](https://github.com/Cannoneers-of-Create/CreateBigCannons) | 1.20.1 (repo now 1.21.1-focused) | MIT + CC BY-NC-SA | Artillery | — | Jun 2026 | NPCs can't operate | **Optional** late-era |
| Valkyrien Skies 2 + Eureka | [VS2](https://github.com/ValkyrienSkies/Valkyrien-Skies-2) · [Eureka](https://github.com/ValkyrienSkies/Eureka) | 1.20.1 Forge | LGPL-3.0 / Apache-2.0 | Physics ships | — | Active | Heavy; no NPC crews; likely conflicts with wrapping | **Avoid** for MVP |

## 3. Territory, nations, diplomacy

| Project | Links | Versions / Loader | Licence | Reusable features | Integration | Maintenance | Limitations | Rec |
|---|---|---|---|---|---|---|---|---|
| **Open Parties and Claims** | [GitHub](https://github.com/thexaero/open-parties-and-claims) · [Modrinth](https://modrinth.com/mod/open-parties-and-claims) · [Javadoc](https://thexaero.github.io/open-parties-and-claims/javadoc/xaero/pac/common/server/api/OpenPACServerAPI.html) | 1.18.2–1.21.x; Forge/NeoForge/Fabric (0.33.0, Oct 2026) | **LGPL-3.0** | Chunk claims, protection, parties, ally parties, forceloading, Xaero map display | `OpenPACServerAPI.get(server)` → `getServerClaimsManager().tryToClaim(...)`, `getPartyManager()`, `IServerPartyAPI.addAllyParty/isAlly/addMember/setRank` | Active | "Beta"; no war state; claims are per-player UUID (proxy owner needed, *unverified*) | **Use** (protection + display backend) |
| FTB Chunks / Teams / Library | [GitHub](https://github.com/FTBTeam/FTB-Chunks) | 1.20.1 Forge | **All Rights Reserved** ("visible source") | Claims, teams, map | No documented API | Active | Cannot fork; API undocumented | **Reject** as base |
| Nation (TMagic20) | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/nation) | 1.20.1 Forge; 1.21.1 NeoForge | ARR | Ranks, war/peace/alliance on top of OPAC | — | Aug 2026 | Small; ARR | **Study** (closest design) |
| Warring Nations | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/warring-nations) | **1.21.1 NeoForge only** | Custom source-available | 10 war types, peace deals, governments, treasuries, vassals, rebellions | — | Active | Built on FTB | **Study** (best diplomacy reference) |
| Timo's Nations: Control & War (+ Tech & Policies addon) | [Nations](https://www.curseforge.com/minecraft/mc-mods/timos-nations-control-war) · [Addon](https://www.curseforge.com/minecraft/mc-mods/timos-nation-addon-technology-policies) | 1.20.1 Forge | ARR | Capitals/cities/outposts, connectivity-scaled claim cost, phased wars, research and policy cards | — | Oct 2026 | Small, ARR | **Study** (very Civ-like) |
| Faction Friction | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/faction-friction) | 1.20.1 Forge; 1.21.1 NeoForge | ARR | Sieges, capital capture, white peace, map overlays | — | Oct 2026 | Conflicts with OPAC | **Study** |
| Factions Proper | [Modrinth](https://modrinth.com/mod/factions-proper) | 1.20.1 Forge | CC-BY-NC-SA-4.0 | Power claims, timed sieges | — | ~Mar 2026 | No source | **Study** |
| CivClash | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/civclash) | 1.20.1 Forge | ARR | Influence heatmap, tiers Band→State | — | Pre-alpha | Prototype | **Study** (cultural border idea) |
| FactionCore | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/factioncore) | 1.20.1 Forge | MIT | Voted wars | — | Aug 2026 | Tiny; no source found | **Study** |
| Towny (Paper plugin) | [GitHub](https://github.com/TownyAdvanced/Towny) | Paper only | CC BY-NC-ND 3.0 | Towns/nations, SiegeWar addon | — | Active | No derivatives, wrong platform | **Study** concepts |
| Civs (Spigot) | [GitHub](https://github.com/Multitallented/Civs) | Spigot | GPL-3.0 | Towns, regions | — | — | Wrong platform | **Study** |

## 4. Economy, industry, trade

| Project | Links | Versions / Loader | Licence | Features | Rec |
|---|---|---|---|---|---|
| **Lightman's Currency** | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/lightmans-currency) · [GitHub](https://github.com/Lightman314/LightmansCurrency) | 1.20.1 Forge 2.3.0.6 | Apache-2.0 per CurseForge (LICENSE file *unverified*) | Coins, team bank accounts, trader blocks, network traders | **Use** — physical coins + team accounts map to treasury |
| Create: Numismatics | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/numismatics) | 1.20.1 Forge 1.1.0 | LGPL-3.0 | Vendors, bank terminals; needs Create | **Alternative** (pick one currency mod) |
| **Create** | [Modrinth](https://modrinth.com/mod/create) · [Licence](https://github.com/Creators-of-Create/Create/blob/mc1.20.1/dev/LICENSE.md) | 1.20.1 Forge (6.0.x *unverified*) | Code MIT, assets ARR | Industry, trains | **Use** (Early Industrial era; gated by tech) |
| Road generators (RoadWeaver, Vaskars Roads…) | [RoadWeaver](https://www.curseforge.com/minecraft/mc-mods/roadweaver) | 1.20.1 Forge *unverified* | *unverified* | Worldgen roads between structures | **Study later** |

No Forge 1.20.1 mod provides caravans, trade routes or diplomacy-level trade. Custom.

## 5. Progression and tech gating

| Project | Links | Versions / Loader | Licence | Features | Rec |
|---|---|---|---|---|---|
| **KubeJS** | [GitHub](https://github.com/KubeJS-Mods/KubeJS) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/kubejs) | 1.20.1 Forge 2001.6.5 | LGPL-3.0 | Recipes, tags, events, `Java.loadClass`, persistent data, per-player stages | **Use** (pack scripting, recipe changes, era recipes) |
| **GameStages** + ItemStages | [GameStages](https://www.curseforge.com/minecraft/mc-mods/game-stages) | 1.20.1 Forge 15.0.2 (Oct 2024) | LGPL-2.1 | Per-player stage API; item locks | **Use** as enforcement backend fed by Civ Core (1.21.1 availability *unverified*) |
| AStages | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/astages) | 1.20.1 Forge 2.5.3 | ARR | Player/server stages; recipe, item, dimension, mob, structure locks | **Alternative** enforcement backend |
| JStages | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/jstages) | 1.20.1 | MIT | KubeJS ↔ stages bridge | Optional |
| Item Obliterator | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/item-obliterator) | 1.20.1 | Custom | Permanently remove items | **Use** (remove anachronistic items) |
| FTB Quests | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/ftb-quests-forge) | 1.20.1 Forge | ARR | Team-shared quest trees | **Optional** (tutorials/objectives); **not** the tech tree — research is abstract points and AI must use it |
| Heracles / Odyssey Quests | [GitHub](https://github.com/terrarium-earth/Heracles) | 1.20.1 frozen | MIT | Tree-style quests | Study (UI ideas) |

## 6. Maps and fog of war

| Project | Links | Versions | Licence | Features | Rec |
|---|---|---|---|---|---|
| Xaero's Minimap / World Map | [CurseForge](https://www.curseforge.com/minecraft/mc-mods/xaeros-minimap) | 1.20.1 / 1.21.1 | ARR | Displays OPAC claims; server can disable cave mode/radar; Fair-play edition | **Use** (navigation; shows OPAC territory for free) |
| JourneyMap + journeymap-api | [API](https://github.com/TeamJM/journeymap-api) · [Modrinth](https://modrinth.com/mod/journeymap) | 1.20.1 5.9.x (API 1.x); 6.0 beta (API 2.0, breaking) | ARR; API usable as dependency | Documented plugin API: markers, polygon overlays | **Alternative** (better overlay API; choose Xaero *or* JourneyMap) |
| Antique Atlas 4 + Surveyor | [Modrinth](https://modrinth.com/mod/antique-atlas-4) | 1.20.1 Forge listed | *unverified* | Exploration-revealed map | **Study** |
| BlueMap / squaremap / Dynmap | [BlueMap](https://modrinth.com/mod/bluemap) | 1.20.1 Forge | MIT / MIT / Apache-2.0 | Web maps | **Use admin-only** (spectators, debugging) |
| Reign of Nether fog of war | see above | | GPL-3.0 | RTS fog | **Study** |

**No mod offers server-authoritative strategic fog of war.** Civ Core's own strategic map screen provides it (see [D §10](D-architecture.md#10-client-ui-and-strategic-map)).

## 7. World generation and wrapping

See [E-world-wrapping.md](E-world-wrapping.md) for full detail.

| Project | Links | Versions | Licence | Rec |
|---|---|---|---|---|
| **Exotic Worlds** (ex-Toroidal World) | [GitHub](https://github.com/anyttng/exotic_worlds) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/exotic-worlds) | 1.21.1, 26.x; NeoForge/Fabric | PolyForm Shield 1.0.0 | **Use** if on NeoForge 1.21.1; study otherwise |
| Circumnavigate | [GitHub](https://github.com/FamroFexl/Circumnavigate) | Fabric 1.20.4/1.21.1, alpha | AGPL-3.0 | **Study/adapt** |
| Looping World | [GitHub](https://github.com/neuromuser/looping-world) | Fabric 1.19.2/1.20.1/1.21.1 | MIT | **Adapt** (teleport-wrap fallback) |
| Tectonic | [Modrinth](https://modrinth.com/mod/tectonic) · [GitHub](https://github.com/Apollounknowndev/tectonic) | 1.20.1 Forge 3.0.17; 1.21.1 | MIT | **Use** |
| Terralith | [Modrinth](https://modrinth.com/mod/terralith) | 1.20.1 Forge 2.5.4 | Stardust Labs licence | **Use** (with Tectonic, mod versions) |
| More Density Functions | [Modrinth](https://modrinth.com/mod/more-density-functions) · [GitHub](https://github.com/klinbee/More-Density-Functions) | 1.20.1 Forge 2.3.0 | MIT | **Use** (edge falloff, experiments) |
| Oceanus | [Modrinth](https://modrinth.com/datapack/oceanus) · [GitHub](https://github.com/Lizdtre/Oceanus) | 1.20.1–1.21.8 | MIT | **Use or fork** (ocean-ring edge; author no longer updating) |
| Lithostitched | [Modrinth](https://modrinth.com/mod/lithostitched) | 1.20.1 Forge | MIT | Use if needed |
| Continents | [Modrinth](https://modrinth.com/mod/continents) | 1.20.x | Stardust Labs licence | Alternative to Tectonic (don't combine) |
| Bounded Worlds | [GitHub](https://github.com/AntoineDehan/Bounded-Worlds) | 1.20.1 Forge only | ARR | Optional |
| Atlas (itsmiir) | [GitHub](https://github.com/itsmiir/atlas) | Fabric ≤1.20.1, inactive | CC0 | Adapt for hand-painted maps (later) |

## 8. Tooling and performance

| Tool | Links | Licence | Use |
|---|---|---|---|
| Forge 47.4.28 MDK, ForgeGradle 6, Java 17 | [Forge files](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html) · [MDK](https://github.com/MinecraftForge/MDK) | LGPL-2.1 | Build |
| Parchment mappings | [Docs](https://parchmentmc.org/docs/getting-started) | CC0 | Parameter names |
| MixinExtras | [GitHub](https://github.com/LlamaLad7/MixinExtras) | MIT | Only if mixins are needed |
| CurseMaven / Modrinth Maven | [CurseMaven](https://www.cursemaven.com/) · [Modrinth Maven](https://support.modrinth.com/en/articles/8801191-modrinth-maven) | — | Compile against mod jars |
| Forge GameTest | [Docs](https://docs.minecraftforge.net/en/1.20.x/misc/gametest/) | — | In-game automated tests |
| spark | [Modrinth](https://modrinth.com/mod/spark) | GPL-3.0 | Profiling |
| Chunky | [Modrinth](https://modrinth.com/mod/chunky) | GPL-3.0 | Pregeneration |
| packwiz | [GitHub](https://github.com/packwiz/packwiz) | MIT | Pack source of truth in git, CurseForge export |
| ModernFix, FerriteCore | [ModernFix](https://github.com/embeddedt/ModernFix) | LGPL-3.0 / MIT | Essential performance |
| Canary *or* Radium | [Canary](https://modrinth.com/mod/canary) | LGPL-3.0 | Server tick (stale; test) |
| Embeddium | — | LGPL-3.0 | Client FPS (1.20.1) |
| AI Improvements | [GitHub](https://github.com/BuiltBrokenModding/AI-Improvements) | ARR | Cheaper mob look AI |

## 9. Projects searched but not found

- **"Seamless World Wrap"**: no mod with this name was found; the phrase is Circumnavigate's tagline. Treat as nonexistent unless you have a link.
- A Forge 1.20.1 mod providing caravans/trade routes, standalone diplomacy, or AI civilisations: none found.
- "Grand Economy" for Forge 1.20.1: not found.

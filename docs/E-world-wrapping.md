# E. World Wrapping Feasibility Report

## 1. Summary

- **A truly seamless torus is technically practical**: Exotic Worlds does it today on NeoForge 1.21.1 and Fabric (and 26.x). It is **not available on Forge 1.20.1**, and building it there is a large, fragile mixin project.
- **"Seamless World Wrap"** does not appear to exist as a separate mod. The phrase "Finite, Tiled, Seamless World Wrapping" is Circumnavigate's tagline; the CurseForge slug `toroidal-world` now redirects to Exotic Worlds.
- **Decision (2026-11-08):** NeoForge 1.21.1 with **Exotic Worlds as a dependency**, verified in the Phase 0 spike ([spike-platform.md](spike-platform.md)). Requirements that came out of the spike:
  - our seam-guard mixin, which fixes a crash with Recruits' async paths;
  - nearest-copy targets for Recruits and MineColonies;
  - no cities within 192 blocks of a seam.

  The Forge 1.20.1 staged plan in §5 is kept as a contingency.
- Regardless of path, Civ Core uses a `Topology` abstraction for every strategic distance, so the gameplay layer is ready for any of these.

## 2. Projects investigated

| Project | Links | Versions / Loader | Licence | How it works | Status | Port feasibility | Rec |
|---|---|---|---|---|---|---|---|
| **Exotic Worlds** (formerly Toroidal World) | [GitHub](https://github.com/anyttng/exotic_worlds) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/exotic-worlds) · [How it works](https://github.com/anyttng/exotic_worlds/blob/main/docs/how-it-works.md) · [Modding API](https://github.com/anyttng/exotic_worlds/blob/main/docs/modding.md) | 1.21.1, 26.1.2, 26.2, 26.3; NeoForge + Fabric | PolyForm Shield 1.0.0 (verified) | World width stored in the chunk generator at creation; vanilla noise snapped to a lattice that fits the lap, so terrain is continuous; wrapped structure placement grids; server sends chunks/entities/updates translated to the copy nearest each player (client never sees the seam); same-tick entity transfer; a shared nearest-copy distance used by AI, pathfinding, projectiles, explosions, sounds, raids; torus and cylinder modes; public API `com.exoticworlds.api.v1` | Active (Nov 2026), pre-1.0 | Using as a dependency: allowed. Porting to Forge 1.20.1: likely "competing product" → needs author's written permission; heavy backport | **Use** on NeoForge 1.21.1 |
| **Circumnavigate** | [GitHub](https://github.com/FamroFexl/Circumnavigate) · [Modrinth](https://modrinth.com/mod/circumnavigate) · [Wiki](https://github.com/FamroFexl/Circumnavigate/wiki) | Fabric 1.20.4 and 1.21.1; server-side | AGPL-3.0 (verified) | Wraps chunk sending/loading, block interaction, collision/reach, entity teleport and hitboxes, redstone/block updates, portals. Render distance capped to world width | Explicitly alpha; terrain does **not** blend at seam; no wrapped pathfinding or structure generation | Legal (must stay AGPL, source public). Difficulty high: Fabric 1.20.4 mixins → Forge 1.20.1 | **Study / adapt** |
| **Looping World** | [GitHub](https://github.com/neuromuser/looping-world) · [CurseForge](https://www.curseforge.com/minecraft/mc-mods/looping-world) | Fabric 1.19.2, 1.20.1, 1.21.1 | MIT (verified) | At the vanilla world border, mirrors player to the opposite side and teleports to safe spot | Jan 2026 | Trivial, legal | **Adapt** (stage-2 fallback) |
| Wrap Around World (datapack) | [Modrinth](https://modrinth.com/mod/wrap-around-world) | Needs 1.20.2+ macros | MIT | Scoreboard teleport | — | Doesn't run on 1.20.1 | Reject |

## 3. Technical analysis of seamless wrapping

| Concern | Difficulty | What is required |
|---|---|---|
| Terrain continuity | Medium | Noise must be periodic with period W. Either snap noise lattice to fit W (Exotic Worlds) or sample 4D noise on a Clifford torus. All 3D noises (caves, aquifers, ore veins, surface rules) need the same treatment; other mods' custom generators break at the seam |
| Structure generation | Medium | Placement grid spacing must divide W, or use wrapped placement |
| Chunk sending / rendering | **High** | Server must send chunks near the seam translated to coordinates nearest the player; client sees a continuous world. Render distance must stay below half the map width |
| Chunk loading / tickets | High | Two coordinate copies represent one physical chunk; tickets, chunk holders and saving must canonicalise |
| Entity and projectile transfer | High | Entities crossing the boundary teleport within one tick with velocity, riders, leashes and paths preserved |
| NPC pathfinding across seam | High | Path nodes must use nearest-copy coordinates; MineColonies and Recruits pathfinders are custom and may not respect wrapping (**untested anywhere**) |
| Redstone, lighting, fluids | Medium–high | Neighbour lookups at the seam must canonicalise. Undocumented in all three projects |
| Other mods reading raw coordinates | Ongoing | Minimaps, waypoints, OPAC claims, MineColonies colony borders, Create contraptions. Each may misbehave at the seam |
| Multiplayer / dedicated server | Medium | All above are server-side; Exotic Worlds and Circumnavigate are designed server-authoritative |
| Strategic distance | Easy | Civ Core `Topology` (see [D §5](D-architecture.md#5-topology)) |

**Practical mitigation regardless of implementation:** place the seams in **deep ocean** (Civ maps traditionally wrap through ocean). Then terrain continuity, structures, pathfinding, redstone and city/colony issues mostly vanish because nothing is built on the seam. Only boats, ships, projectiles and swimming players cross it. Civ Core's start placement and city founding rules forbid cities within N tiles of a seam.

## 4. Can 1.20.1 terrain be made periodic with datapacks?

- Vanilla 1.20.1 datapacks: **no** (no coordinate, modulo or trig density functions).
- With **More Density Functions** (MIT, Forge 1.20.1): edge falloff is easy (Oceanus does it). Wrapping one axis may be possible in principle by building `shifted_noise` shifts from `moredfs:x`, `sin`, `cos` so 2D climate noises sample a circle of circumference W. This is **untested reasoning**; 3D noises would still break.
- Clean solution: Java density-function type or chunk generator that samples periodic noise. No published Forge 1.20.1 example exists.

Since the recommended seams are in ocean, **terrain periodicity is not needed on 1.20.1**: force continentalness to deep ocean in a band around the seams (edge falloff, exactly what Oceanus does) and the terrain on both sides is the same: open ocean.

## 5. Staged path

### On NeoForge 1.21.1 (chosen; spike done)

1. **Spike (Phase 0): done.** Results are in [spike-platform.md](spike-platform.md).
   - Worked: terrain continuity, entities, rails, vanilla AI, Recruits combat, OPAC claims, in-game visuals.
   - Broken, now handled: a Recruits async-path crash (fixed by our mixin), and Recruits and MineColonies pathfinding using raw coordinates.
2. Use Exotic Worlds' API to read map width/shape at game setup; feed `TorusTopology`.
3. Still bias seams into ocean (Civ Core continentalness shaping or start rules) to minimise mod-compat issues; forbid cities near seams if the spike shows colony issues.

### On Forge 1.20.1 (contingency only)

| Stage | Deliverable | Effort | Result |
|---|---|---|---|
| 1 (MVP) | Finite world: Tectonic + Terralith + More Density Functions + Oceanus ocean ring (or square ocean frame) + vanilla world border just outside. `BoundedTopology` | S | Playable, Civ-like island/continent map with ocean edge |
| 2 | **Teleport wrap through ocean** (Phase 5): Forge port of Looping World (MIT) extended to boats, Small Ships, ridden entities and projectiles; square ocean frame instead of ring so opposite edges match; teleport line in mid-ocean with a fog/storm effect. `CylinderTopology` or `TorusTopology` for strategy | M | Circumnavigation works; jump is barely noticeable at sea; strategic distances wrap |
| 3 (optional) | True seamless cylinder wrap: chunk translation for clients near the seam, ported from Circumnavigate (AGPL module) or by agreement with Exotic Worlds' author | XL | Visually seamless; high maintenance |

Stage 2 is the realistic "functioning fallback" and is enough for Civ gameplay, since Civ wraps through ocean anyway.

## 6. Strategic distance and the map UI

- All strategic rules use `Topology.distance`. On wrapped maps the strategic map screen renders tiles modulo W/H so the player can scroll endlessly.
- Physical placement (materialising an army, pasting a city) uses `Topology.nearestCopy` so things appear on the side of the seam nearest the observing player (only matters for true seamless wrap).

## 7. Risks and open questions

- Exotic Worlds is pre-1.0; API may change. Pin version.
- Other mods' behaviour at the seam (MineColonies borders, Recruits pathfinding, OPAC claims) is untested anywhere; the spike must test it.
- Licensing: confirm with Exotic Worlds' author that a Civ modpack depending on it is not "competing".
- Cylinder vs torus: Civ uses cylinders (east–west wrap with polar edges). A cylinder needs only one seam and is safer. **Recommend cylinder for 1.0, torus as an option.**

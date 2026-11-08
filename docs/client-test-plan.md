# Client Test Plan: NeoForge 1.21.1 + Exotic Worlds Spike

**Purpose:** confirm the things only a person in the game can see before we adopt NeoForge 1.21.1. The server-side tests passed already (see [spike-platform.md](spike-platform.md)). These tests cover how the seam looks, maps, colonies running with a real player nearby, and giving recruits orders.

**Time:** about 45 minutes. **Priority:** sections 1–4 decide the platform question. Sections 5–7 are good to have.

---

## 0. Setup (10 min)

1. **Start the server** from Git Bash in the project folder:
   ```bash
   spikes/neoforge-1.21.1/start-server.sh ~/civ-spike-server
   ```
   Wait for `Done (…)! For help, type "help"`. Then type `op <your Minecraft name>` in that same window.
2. **Import the client.** In the CurseForge app, go to **My Modpacks → Create Custom Profile → Import**, then pick `C:\Users\Bzier\civ-spike-client.zip`. Launch it.
3. **Join** via Multiplayer → Direct Connection → `localhost`.
4. **Video settings:** render distance **12**, so you can see across the seam. Press **F3** to show coordinates during every test.

**Map facts:**
- The world runs from **−1024 to 1023** on both X and Z.
- The east–west seam is between x = 1023 and x = −1024. The north–south seam is between z = 1023 and z = −1024.
- At the seam, the world loops: walking east past x = 1023 puts you at x = −1024.

| Place | Command |
|---|---|
| Test island across the east–west seam (built for you, flat grass) | `/tp @s 1010 70 330` |
| Colony "rome": town hall | `/tp @s 985 70 330` |
| Rome's builder hut, across the seam | `/tp @s -1000 70 330` |
| The four-seam corner | `/tp @s 1015 120 1015` |
| Old test platform from the first spike, in the sky | `/tp @s 1010 205 8` |

**Recording results:** use the table in §8. Take a screenshot (F2) of anything odd. If the server crashes, keep `~/civ-spike-server/crash-reports/` and `~/civ-spike-server/logs/latest.log`.

---

## 1. Crossing the seam (most important, 10 min)

Go to `/tp @s 1010 70 330`, use survival (`/gamemode survival`), and look east (+X). The seam is 14 blocks in front of you.

| # | Do this | Pass if | Watch for |
|---|---|---|---|
| 1.1 | Walk east across the seam, then back west | F3 X jumps from 1023 to −1024 and you notice nothing else | A stutter, being pulled back, a black or missing chunk, a lighting change |
| 1.2 | Sprint-jump across, both directions | Same as 1.1 | Same |
| 1.3 | Stand 3 blocks before the seam and look across, then look back after crossing | Terrain on the far side is drawn and matches up | Terrain missing past the seam, or chunks popping in late |
| 1.4 | Place blocks right on the seam: one at x = 1023 and one at x = −1024, side by side | They sit next to each other with no gap | A gap, or a block vanishing |
| 1.5 | Pour water at x = 1022 so it flows east | The water flows across | Water stopping at the seam |
| 1.6 | Ride a horse across: `/summon minecraft:horse ~ ~ ~ {Tame:1b,SaddleItem:{id:"minecraft:saddle",count:1}}` | You stay mounted and crossing is smooth | Being dismounted, the horse lagging behind you |
| 1.7 | Sail a boat across on open ocean. Go to `/tp @s 1000 70 600`, which is mostly ocean along the seam, then `/give @s oak_boat` | Smooth, and you stay in the boat | Being ejected, or the boat jerking |
| 1.8 | Fly in creative over the corner (`/tp @s 1015 120 1015`, `/gamemode creative`) and cross diagonally both ways | All four quadrants are drawn and crossing is smooth | Missing chunks in one quadrant |
| 1.9 | Fire arrows across the seam at a mob on the other side | The arrow crosses and hits | The arrow disappearing at the seam |

## 2. Maps (5 min)

| # | Do this | Pass if | Expected, OK to record |
|---|---|---|---|
| 2.1 | Watch Xaero's minimap while walking across the seam | It keeps updating and centres on you | It may "jump" sideways; acceptable if it recovers |
| 2.2 | Open Xaero's world map (M) near the seam | It shows explored terrain on both sides | Both sides may appear at opposite ends of the map; note how it looks |
| 2.3 | Put a waypoint across the seam and walk to it | The waypoint is reachable | The distance shown may be the long way round; note it |

## 3. MineColonies across the seam (10–15 min)

**A. The colony made by code ("rome").** Go to `/tp @s 985 70 330` and stay within about 50 blocks.

| # | Do this | Pass if |
|---|---|---|
| 3.1 | Wait about 1 minute. In chat, run `/civspike status` | Colony 1 shows `state=ACTIVE` and `citizens` above 0 |
| 3.2 | Wait 5–10 minutes near the seam and watch the builder hut at **−1000 64 330**, across the seam | A builder citizen walks across the seam and builds the hut. Materials are supplied automatically for the test. Check `/civspike status`: the work order goes away and the builder shows **L1** |
| 3.3 | Note what happens if nothing builds | Record the `/civspike status` output. A town hall at level 0 may block builders; that is useful to know too |

**B. A colony you found yourself (the normal player flow).**

| # | Do this | Pass if |
|---|---|---|
| 3.4 | `/tp @s 1005 70 345`, `/gamemode creative`. Get a town hall (`/give @s minecolonies:blockhuttownhall`) and the Structurize build tool from the creative inventory (Structurize tab). Place the town hall about 15 blocks from the seam on the island and found the colony | The colony is founded and the GUI opens |
| 3.5 | Place a builder hut (`/give @s minecolonies:blockhutbuilder`) **across the seam** at about x = −1005 | The hut joins *your* colony (its GUI shows your colony name) |
| 3.6 | Have the builder build its own hut. Use the hut GUI's build button, switch to survival and wait | The builder walks across the seam and finishes the hut |
| 3.7 | Look at the colony border (claimed area) with the MineColonies colony-border overlay | The border continues across the seam |

## 4. Recruits across the seam (10 min)

Go to `/tp @s 1005 70 340`. In survival, run `/give @s emerald 64`. Spawn 3 recruits with `/give @s recruits:recruit_spawn_egg 3` and right-click each one with emeralds to hire it. Open the Recruits command screen; find its key under **Options → Controls → Recruits**.

| # | Do this | Pass if | Known issue (OK to confirm) |
|---|---|---|---|
| 4.1 | Order **Follow** and walk across the seam and back | They follow without stopping, teleporting oddly or crashing the server | — |
| 4.2 | Stand on the east side, aim at a block just across the seam (west side) and order **Move** | They cross the seam to that block | **Expected bug:** they may walk the long way round, west. Record which way they go |
| 4.3 | Spawn zombies across the seam with `/summon minecraft:zombie -1010 64 340`, then set the recruits to **Aggressive** | They cross and fight | — |
| 4.4 | Watch the server window during all of the above | No "Ticking entity" error and no crash | This is the crash we fixed; it must not come back |

## 5. Claims (3 min, optional)

| # | Do this | Pass if |
|---|---|---|
| 5.1 | Open Open Parties and Claims' claim screen (`/openpac-claims` or from Xaero's world map) and claim chunks on both sides of the seam | The claims work and Xaero shows them on both sides |
| 5.2 | On Xaero's map, look at chunks 63,19 and −64,19 (block −1015, 310), which the code test already claimed for "civ_nationA" | They show as claimed by someone else |

## 6. Performance (2 min)

| # | Do this | Record |
|---|---|---|
| 6.1 | While standing in Rome with recruits nearby, run `/spark tps` | The TPS and MSPT numbers |
| 6.2 | Run `/spark health` | Memory use |
| 6.3 | Note your client FPS (F3) at the seam and away from it | Both FPS numbers |

## 7. Restart (3 min)

| # | Do this | Pass if |
|---|---|---|
| 7.1 | Type `stop` in the server window, start it again, and rejoin | Your colony, the hut levels, your recruits (allow about 5 s for them to appear) and your claims are all still there |
| 7.2 | Cross the seam once more | Still smooth, no crash |

---

## 8. Results (fill in and send back)

| Test | Pass / Fail / Partial | Notes (what you saw) |
|---|---|---|
| 1.1 Walk across | | |
| 1.2 Sprint-jump | | |
| 1.3 Look across | | |
| 1.4 Blocks on the seam | | |
| 1.5 Water | | |
| 1.6 Horse | | |
| 1.7 Boat | | |
| 1.8 Corner flight | | |
| 1.9 Arrows | | |
| 2.1 Minimap | | |
| 2.2 World map | | |
| 2.3 Waypoint | | |
| 3.1 Rome active | | |
| 3.2 Builder across seam (Rome) | | |
| 3.4 Found own colony | | |
| 3.5 Hut across seam joins colony | | |
| 3.6 Builder builds across seam | | |
| 3.7 Border wraps | | |
| 4.1 Follow across | | |
| 4.2 Move across (which way?) | | |
| 4.3 Fight across | | |
| 4.4 No crash | | |
| 5.x Claims | | |
| 6.x TPS / MSPT / memory / FPS | | |
| 7.x Restart | | |

**Decision rule:**
- **Adopt NeoForge 1.21.1** if sections 1, 3B and 4.1/4.4 pass. Map oddities (2.x) and 4.2 are expected, and we fix them in our own code.
- **Re-evaluate** if crossing is visibly broken (1.1–1.3, 1.8) or builders can't work across the seam (3.6).

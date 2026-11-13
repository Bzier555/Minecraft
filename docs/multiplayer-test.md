# Multiplayer Test Plan: `C:\CivServer`

**Server:** `C:\CivServer`, set up like `C:\DrehmalServer`:
- `start.bat`, 4–10 GB memory;
- port 25565, so your existing playit.gg tunnel works;
- online mode, whitelist on (Bzier555, onepiggy, Canned_Fruit), Bzier555 as operator;
- the world is a pregenerated 4,096 × 4,096 Exotic Worlds torus (Tectonic + Terralith).

**New to Civ Core?** Everyone should read Part 1 of [guide.md](guide.md) (also `C:\CivServer\GUIDE.md`) first. It explains the game, the screens and the rules.

**Players:** 3 people and 1–2 AI civilizations. Plan on **2–3 hours**. The game clock pauses whenever nobody is online.

> Drehmal and Civ Core both use port 25565: run only one server at a time.

---

## Before the session (host)

1. **Start the server:** double-click `C:\CivServer\start.bat` and wait for `Done (…)!`.
2. **Start the tunnel:** start playit.gg as you do for Drehmal. The tunnel already points at local port 25565, so the address your friends use stays the same.
3. **Send your friends** `C:\CivServer\CivCore-CurseForge.zip` (≈ 96 MB). Each of them imports it: CurseForge → **My Modpacks → Create Custom Profile → Import**, then launches "Civ Core" and joins the playit address. Their profile must be this exact one; Civ Core adds a block and an item, so vanilla or other packs can't join.
4. **Pick the starting setup in the server window**, before anyone founds a city:
   - `civ status` should say "Turn 0" with no civilizations.
   - `civ addai` once (or twice for a harder game). The AI takes the best free start, so add AIs **before** players found cities.
   - Optional: `civ admin turnlimit 250` so the session ends with a score victory if nobody wins earlier.
   - Turns are 60 seconds. To skip quiet stretches, the host can run `civ tick 5`.
5. **Add another player** with `whitelist add <name>`.

## Opening (first 15 minutes)

| # | Who | Do | Watch for |
|---|---|---|---|
| 1 | Everyone | Join; each gets a **Settler Charter** | Charter arrives once per player per game |
| 2 | Everyone | Press **Y**; each player's map shows only their own area | Fog of war: nobody sees the others yet |
| 3 | Host | `civ starts 4` in the console, then read out the suggested start coordinates | Everyone picks a different start |
| 4 | Everyone | Walk or `/tp` to your start; rename the charter in an anvil; use it on the ground | Capital founded, borders in your colour, an announcement for everyone |
| 5 | Two players (optional co-op) | **Share one civilization:** the second player doesn't found a city. The host runs `civ civs` to get the civ's number, then `civ admin addmember <number> <player>` | Both see the same cities, map and research; both can give orders. The unused charter can found that civ's next city |
| 6 | Everyone | City screen: queue Warriors, then Settlers; tech screen: pick research | Each player sees only their own cities' details |

## Mid game (60–90 minutes)

| # | Do | Watch for |
|---|---|---|
| 7 | Expand to 2–3 cities each (Settlers → select on map → move → **Found city here**) | Settlers march; City Center appears when the chunk loads |
| 8 | Visit another player's land and try to break or place blocks | Blocked: "This land belongs to …" |
| 9 | Meet each other: walk around or send a Scout | The other civ appears on your map and in the diplomacy buttons |
| 10 | **War between two players**: one declares from the map panel | Both get the announcement; Recruits soldiers appear when either player is near the armies |
| 11 | Fight alongside your soldiers; kill enemy soldiers yourself | Each kill reduces that army (`/civ armies`); soldiers on the same civ don't hit each other |
| 12 | Two players watch the same battle from opposite sides | Both see the same soldiers; no duplicates; no lag spike |
| 13 | One player walks far away mid-battle while another stays | Soldiers stay while anyone is near; vanish when everyone has left |
| 14 | Besiege and capture a player's city | City changes hands; its land recolours; the owner gets the news |
| 15 | Peace: one offers, the other accepts from the map panel | War ends; armies inside the other's borders are sent home; 20-turn treaty |
| 16 | Civics: switch to Monarchy (needs Currency), adopt a policy | Anarchy for 2 turns; bonuses in the side panel |

## Stress and edge cases (30 minutes)

| # | Do | Watch for |
|---|---|---|
| 17 | One player logs out for 10+ minutes while the others keep playing | Their cities keep building (offline governor); no wars declared for them |
| 18 | Everyone logs out, wait a minute, everyone logs back in | The turn timer paused while empty |
| 19 | Host: type `stop` **once** in the server window, restart with `start.bat`, everyone rejoins | Cities, armies, borders, research and wars all restored; no soldiers duplicated |
| 20 | Cross the world seam (x or z = ±2048), on foot and by boat; take an army across | Seamless; no "Ticking entity" crash in the server window |
| 21 | Big battle: host spawns stacks with `civ admin army "<issuer>" spearmen 10 <tx> <tz>` near players at war | Server TPS: run `/spark tps` during the fight |
| 22 | Host runs `/spark tps` and `/spark health` a few times during the session | TPS 20, memory comfortably under 10 GB |
| 23 | Let the game end, or use the turn limit | Victory announcement; commands then refuse with "The game is over" |

## Things we already know are missing

- Joining another player's civilization works only through the operator command (`civ admin addmember`); there is no invite or leave flow yet.
- Renaming a civilization, and choosing its colour.
- Roads, foreign trade routes, ships and naval combat.
- MineColonies builders constructing cities. City buildings are abstract; the City Center is the only block.
- City defenders in sieges: city defence is abstract.
- A game-speed setting (60-second turns are fixed; use `civ tick`).

## Collect afterwards

- Each player's notes: confusing, broken, slow or boring.
- Screenshots of anything wrong (F2).
- From the host:
  - `C:\CivServer\logs\latest.log` and any `crash-reports\`;
  - the save `C:\CivServer\world\civcore\game.json`;
  - the `/spark tps` numbers.

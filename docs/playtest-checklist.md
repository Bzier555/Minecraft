# Play-Test Checklist (prototype, 2026-11-08)

Everything below has been built and checked on the server without a player. These are the things only a person in the game can confirm. Allow about 60–90 minutes; sections are independent.

## Setup (5 min)

1. Start the server from Git Bash in `spikes/neoforge-1.21.1`:
   ```bash
   ./start-server.sh ~/civ-spike-server
   ```
2. Re-import `C:\Users\Bzier\civ-spike-client.zip` in CurseForge (Create Custom Profile → Import), launch, and join `localhost`.
3. In the server console:
   - `op <your name>`
   - `civ newgame auto`, then wait for "World analysed" (about a minute)
   - `civ addai` (once or twice)
4. Rejoin, or relog once, to receive your **Settler Charter**.

Useful operator commands:
- `/civ tick <n>` skips turns.
- `/civ admin army "<issuer>" <unit> <n> <tileX> <tileZ>` spawns an army; issuer is your UUID or an AI id like `ai:1001` (see the save file).
- `/civ civs`, `/civ armies` and `/civ status` show the game state.

## 1. Founding and land

| # | Try | Expect |
|---|---|---|
| 1.1 | Rename the charter in an anvil, then use it on the ground at a spot suggested by `/civ starts` | A City Center block appears, borders appear, a chat announcement, the charter is used up |
| 1.2 | Use a charter on water or right next to the seam | Refused, with a reason in the action bar |
| 1.3 | Have the AI's land inside your view, then try to break a block there | "This land belongs to …" and the block stays |
| 1.4 | Right-click your City Center | The city screen opens |

## 2. Screens

| # | Try | Expect |
|---|---|---|
| 2.1 | Press **Y** | Map of your surroundings; unexplored tiles black; your borders in your colour |
| 2.2 | Drag, scroll, hover | Pans, zooms, wraps endlessly; hover names the terrain and resource |
| 2.3 | Click your city, then **Manage** | City screen; queue Warriors and a Monument; click a queued item to remove it |
| 2.4 | **Technologies** | Three era blocks; available techs clickable; tooltips show requirements and unlocks |
| 2.5 | **Civics** | Despotism current; once Currency is known, switch to Monarchy (2 turns of anarchy); adopt a policy once you have 60 culture |
| 2.6 | Side panel | Turn timer, gold, research, happiness, iron/horses, culture and project progress |
| 2.7 | Anything clipped, unreadable or slow at your GUI scale | Note your GUI scale and window size |

## 3. Armies and soldiers

| # | Try | Expect |
|---|---|---|
| 3.1 | Queue Warriors, wait or `/civ tick`, then select your army on the map and right-click a tile | The army marches 2 tiles per turn; its marker armor stand moves when you're away |
| 3.2 | Walk within ~128 blocks of your army | It turns into Recruits soldiers wearing your colour's team name |
| 3.3 | Declare war on an AI from the map side panel, then march at its city | Its armies near you appear as soldiers and fight yours; you can fight too. Every kill reduces that army in `/civ armies` |
| 3.4 | Walk more than ~200 blocks away mid-battle | Soldiers vanish; the battle continues by the rules; walking back brings them back at their saved health |
| 3.5 | Take an AI city (siege it to 0 hp, then move in) | City changes hands; its land changes colour |
| 3.6 | Watch the server log | No "Ticking entity" crash, especially near the seam |

## 4. Long game

| # | Try | Expect |
|---|---|---|
| 4.1 | Play or `/civ tick` to the Medieval era | New buildings and units; Knights need horses, Swordsmen and Riflemen need iron |
| 4.2 | Log out for a while with the server running, then come back | Your cities kept building (offline governor); no wars were declared for you |
| 4.3 | Let a game finish | A victory announcement (domination, science, culture, or score if a turn limit is set) |

## Send back

- what felt confusing, broken, slow or boring;
- screenshots of anything wrong;
- `logs/latest.log` and any `crash-reports/`.

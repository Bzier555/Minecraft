# Civ Core: The Complete Guide

**Audience:** this guide assumes you know Minecraft but nothing about Civ Core. It explains:
- what the game is;
- how to start it and play it;
- every screen and every command;
- every rule, with its exact numbers;
- how to find and fix problems.

**Using it:**
- Read **Part 1** first; it is enough to play your first session.
- **Part 2** has the detailed rules, to look up when something surprises you.
- **Part 3** is for the host or operator: admin commands, debugging and troubleshooting.

**Versions:** Minecraft 1.21.1, NeoForge 21.1.265, Civ Core 0.1.0. Every number below is taken from the code and the ruleset files, not from memory.

---

## Contents

**Part 1: Playing**
1. [The big idea](#1-the-big-idea)
2. [Setting up](#2-setting-up)
3. [Your first 20 minutes, step by step](#3-your-first-20-minutes-step-by-step)
4. [The screens and controls](#4-the-screens-and-controls)

**Part 2: How everything works**

5. [The map, tiles and coordinates](#5-the-map-tiles-and-coordinates)
6. [Turns and the game clock](#6-turns-and-the-game-clock)
7. [Cities](#7-cities)
8. [Food, growth and production](#8-food-growth-and-production)
9. [Culture and borders](#9-culture-and-borders)
10. [Gold, upkeep and connections](#10-gold-upkeep-and-connections)
11. [Resources and happiness](#11-resources-and-happiness)
12. [Science and the technology tree](#12-science-and-the-technology-tree)
13. [Buildings](#13-buildings)
14. [Units and armies](#14-units-and-armies)
15. [Movement, combat, sieges and capture](#15-movement-combat-sieges-and-capture)
16. [Soldiers in the world (Recruits)](#16-soldiers-in-the-world-recruits)
17. [Diplomacy: war and peace](#17-diplomacy-war-and-peace)
18. [Civics: governments and policies](#18-civics-governments-and-policies)
19. [Fog of war](#19-fog-of-war)
20. [Territory protection](#20-territory-protection)
21. [AI civilizations and the offline governor](#21-ai-civilizations-and-the-offline-governor)
22. [Victory and the end of the game](#22-victory-and-the-end-of-the-game)

**Part 3: Commands, admin and debugging**

23. [Player commands](#23-player-commands)
24. [Operator and admin commands](#24-operator-and-admin-commands)
25. [Files, saves and logs](#25-files-saves-and-logs)
26. [Troubleshooting: symptom → cause → fix](#26-troubleshooting-symptom--cause--fix)
27. [Debugging recipes](#27-debugging-recipes)
28. [Known limitations](#28-known-limitations)
29. [Glossary](#29-glossary)

---

# Part 1: Playing

## 1. The big idea

Civ Core puts a game of *Civilization* on top of a Minecraft world. Two layers run at the same time.

**The strategic layer (the "Civ game")**
- The server divides the world into a grid of **tiles**. Each tile is a 64 × 64-block square, the size of 4 × 4 chunks.
- On that grid you own **cities**, research **technologies**, build **buildings**, train **units** and move **armies**.
- You declare **war** and make **peace**.
- All of this is calculated by the server every **turn**. A turn passes automatically every 60 seconds; nobody clicks "end turn".
- You control this layer through the **strategic map** (press **Y**) and the **city screen**.

**The physical layer (normal Minecraft)**
- You still walk around, mine, build, farm and fight as normal.
- Your cities appear in the world as a glowing **City Center** block.
- Your territory protects your builds: foreign players can't break or place blocks on your land.
- Your **armies become real soldiers** (Villager Recruits mod) when a player comes near them. You can watch them fight, and you can join in. Kill an enemy soldier and that enemy unit is gone from the strategic game too.

**How the layers connect**
- The Civ game does **not** use your Minecraft items. A Library is not a building you construct out of blocks; it is an entry in a city's list that gives +2 science per turn.
- The only Civ Core things in the world are:
  - the City Center block;
  - the Settler Charter item;
  - the soldiers;
  - territory protection.
- The rest of Minecraft (your house, your farm, your diamonds) is yours to enjoy, but it doesn't change the Civ numbers.

**The world itself** is an *Exotic Worlds* **torus**:
- It is 4,096 × 4,096 blocks and **wraps around**. Walk east past x = 2048 and you arrive at x = −2048, with no wall and no loading screen. North and south work the same way.
- The world is finite. You can sail around it, and neighbours can come at you from any direction.

---

## 2. Setting up

### 2.1 The host (once)

1. Start the server: double-click `C:\CivServer\start.bat`. Wait until the window shows `Done (…)! For help, type "help"`.
2. Start the playit.gg tunnel as you do for Drehmal. The address your friends use is the same, because both servers use port **25565**.
   - **Run only one of the two servers at a time.**
3. In the server window, check the game. These are console commands; in-game, an operator types them with a `/` in front.
   ```
   civ status
   ```
   - If it says "No Civ game yet", or you want a fresh game, run:
     ```
     civ newgame auto
     ```
     Then wait for the message `[Civ] World analysed: N of 4096 tiles are land.`. This takes from under a minute to a few minutes.
   - Until that message appears, the map shows "Analysing the world..." and AIs can't be added.
4. Optional: add AI opponents **before** players found cities. Each AI takes the best free starting spot.
   ```
   civ addai
   civ addai Rome
   ```
5. Optional: set a turn limit so the game ends with a score victory.
   ```
   civ admin turnlimit 250
   ```
6. To stop the server, type `stop` **once** and wait for "Press any key". Stopping saves the Civ game.

### 2.2 Each player (once)

1. Install the **CurseForge app**.
2. Get `CivCore-CurseForge.zip` from the host.
3. In CurseForge: **My Modpacks → Create Custom Profile → "Import"** (small link at the top) → pick the zip. The profile is called **"Civ Core"**.
4. Press **Play**. In Minecraft, go to **Multiplayer → Add Server** and enter the playit address.
5. You must use this exact profile. The server checks that your mods match, and a vanilla client or another modpack can't join.

The client profile also includes **Xaero's Minimap and World Map**. They are optional but useful for finding your way. The server is the same without them.

### 2.3 What's in the pack

| Mod | What it does here |
|---|---|
| **Civ Core** | This game: tiles, cities, research, armies, AI, the screens |
| **Exotic Worlds** | Makes the world a 4,096-block torus that wraps around |
| **Tectonic + Terralith** | Better terrain and biomes |
| **Villager Recruits** | The soldiers your armies turn into near players |
| **MineColonies** | Installed, but **not used by Civ Core yet**; it is there for later. Colony raids are switched off. |
| **Open Parties and Claims** | Installed, but Civ Core does its own territory protection. Don't rely on OPAC claims for Civ land. |
| **spark** | Performance monitor (`/spark tps`) |
| **Chunky** | Server only: pregenerated the world |
| Xaero's maps | Client only: minimap and world map |

---

## 3. Your first 20 minutes, step by step

**Step 1: join.**
- You'll get a chat message: *"[Civ] You received a Settler Charter."*
- The **Settler Charter** is a paper-like item in your inventory. You get one per game.
- It founds your **capital**, which also creates your **civilization**.

**Step 2: look at the map.** Press **Y** to open the strategic map.
- You see the tiles near you; everything else is dark (unexplored).
- The top-right panel shows the turn and the seconds until the next one.
- Press **Esc** to close it.

**Step 3: choose a spot.** A good capital:

| Wants | Avoid |
|---|---|
| Grassland and plains, for food | Ocean, coast or mountain tiles as the centre: you can't found a city there |
| Some hills or forest, for production | Within 3 tiles (192 blocks) of the world seam at x or z = ±2048; it is refused |
| A river, for +1 gold per tile | Within 3 tiles of another city's centre; it is refused |
| Resources nearby, especially luxuries (gems, furs, wine) and strategic resources (iron, horses) | Another civ's land |

- To check the tile you are standing on, type `/civ tile`. It prints the terrain, the owner and any resource.
- The host can run `civ starts 4` to get the best starting spots, with ready-made `/tp` commands.

**Step 4: name and found the city.**
1. *(Optional)* Put the charter in an **anvil** and rename it, e.g. "Avalon". That becomes the city's name. Unnamed cities are called "City 1", "City 2", and so on.
2. Hold the charter and **right-click the ground**. A glowing **City Center** block appears on top of the block you clicked.
3. Chat announces your capital.

What you get:
- a civilization called "*YourName*'s Civilization";
- the 3 × 3 tiles around the city (192 × 192 blocks) in your colour;
- a free **Palace** in the capital (+2 production, +2 science, +2 gold, +1 culture, +2 happiness).

> Lost your charter before founding? Type `/civ found "City Name" Civ Name` while standing on the spot. It works only if you don't have a civilization yet; operators can always use it.

**Step 5: build something.**
1. Right-click your **City Center** to open the city screen. Or press Y, click the city tile, then click **Manage *City***.
2. The right column lists everything you can build, with its cost and roughly how many turns it takes. Click an item to add it to the queue on the left.
3. A good opening is:
   1. **Warriors**, for defence and exploring;
   2. **Settlers**, for your second city;
   3. **Monument**, for culture and borders.
4. Click an item in the queue to remove it. Its stored production is kept for the next item.

**Step 6: research something.**
1. Press **Y**, then **Technologies** (bottom right).
2. Click a tech you can research. Ancient-era techs without requirements come first; start with **Agriculture**.
3. Pick the next tech whenever one finishes. Research stops until you choose again.

**Step 7: wait and play.**
- Every 60 seconds a turn passes. Your cities grow, produce and research.
- Chat announces what happens, e.g. *"Avalon completed civcore:warriors."*
- Meanwhile, play Minecraft: build your town around the City Center, gather and explore. Walking around reveals the map for your civilization.

**Step 8: move an army.**
- When Warriors finish, an **army** (a stack of units) appears on your city tile.
- On the map:
  1. **Left-click** the city tile to select the army. Clicking the tile again cycles through the armies on it.
  2. **Right-click** any tile to send the army there.
- It moves 2 tiles per turn (fewer through hills, forest, jungle and marsh).

**Step 9: found a second city.**
1. When your **Settlers** finish, select them on the map.
2. Right-click a good spot at least 3 tiles away to send them there.
3. When they arrive, select them and click **Found city here**.
4. The City Center block appears when someone is near enough for that chunk to be loaded.

Now you're playing. Part 2 explains every rule in detail.

---

## 4. The screens and controls

### 4.1 Keys

| Key or action | What it does |
|---|---|
| **Y** | Opens the strategic map. Rebind it under Options → Controls → Key Binds → "Civ Core". |
| Right-click the **City Center** | Opens that city's screen: yours shows full details, a foreign one shows a summary in chat |
| Right-click the ground with the **Settler Charter** | Founds a city there |
| **Esc** | Closes any Civ screen |

### 4.2 The strategic map (Y)

The map is on the left and the info panel on the right. The hint at the top reads *"Drag: pan  Wheel: zoom  Left: select  Right: move army"*.

| Action | Effect |
|---|---|
| **Left-drag** | Pan the map. It wraps around, like the world. |
| **Mouse wheel** | Zoom in and out |
| **Left-click a tile** | Select it. The panel shows terrain, owner and city. If armies are on it, the first one is selected (your own first). |
| **Left-click the same tile again** | Cycle to the next army on that tile, then to none |
| **Right-click a tile** with your army selected | Order that army to march there |
| Hover | The bottom-left corner shows the tile's coordinates and terrain |

**What the colours mean:**
- **Terrain colours:** blue water, green grass, yellow desert, white snow, dark green forest, grey mountains, and so on.
- **Black** means unexplored: you have never seen it.
- A **coloured tint or border** is a civilization's territory, in that civ's colour.
- **Small coloured squares** are resources: pale for bonus, red for strategic, purple for luxury. Zoom in to see their initial letter.
- **Cities** and **armies** are drawn in their civ's colour. The selected tile has a yellow outline.
- Tiles you have explored but **can't see right now** look dimmer. You won't see enemy armies there (see [§19](#19-fog-of-war)).

**Info panel, from top to bottom:**
- `Turn 37 | next 42s`, or `(paused)` when the clock is stopped.
- `Gold 120 | Writing 31/55`: your treasury and current research progress.
- `Happy +3` with your strategic resources. `Luxuries: gems, wine`.
- `Monarchy | upkeep 2`: your government, any anarchy, and gold spent on the army each turn.
- `Culture 640/32000 | Projects 0/3`: progress toward culture and science victories.
- Details of the selected tile, city or army:
  - an army shows health, tiles per turn, and "Marching to (x,z)";
  - otherwise it says "Right-click a tile to move".

**Buttons, at the bottom of the panel:**

| Button | When it appears | Effect |
|---|---|---|
| **Technologies** / **Civics** | Always | Open those screens |
| **Found city here** | Your settlers are selected | Founds a city on the settlers' tile |
| **Manage *City*** | One of your cities is selected | Opens its city screen |
| **Declare war: *Civ*** | You have met the civ and are at peace with it | Declares war at once. Everyone is told. |
| **Treaty *N*t: *Civ*** | A peace treaty is in force; greyed out | Shows the turns until war is allowed again |
| **Offer peace: *Civ*** | You are at war with the civ | Sends a peace offer |
| **Peace offered: *Civ*** | You have offered peace; greyed out | Waiting for their answer |
| **Accept peace: *Civ*** | They offered peace | Makes peace immediately |

### 4.3 The city screen

**Left column:**
- *Name* (with "(capital)" for the capital).
- `Population 3 (grows in 4 turns)`. It says "not growing" with no surplus, or **"STARVING"** with a deficit.
- `Food 12/25 +8 -6`: stored food / needed to grow, food made per turn, food eaten.
- `Production +5 stored 14`.
- `Gold +3 Defence 100/100`. Defence is the city's health, from 0 to 100.
- `Culture +3 22/35 (border grows in 5)`.
- **Building now (click to remove):** the queue. The top item is being built now.
- **Buildings:** what the city already has.

**Right column, "Add to queue":**
- every building and unit this city may build now, as `name (cost, str N for units, ~turns)`;
- click one to add it to the end of the queue;
- items you can't build yet (wrong tech, missing resource, already built) are not listed.

A **Map** button returns to the strategic map.

### 4.4 The technology screen

- Techs are grouped by era: **Ancient**, **Medieval** and **Industrial**.
- `[x]` means researched; `[>]` means being researched now.
- Hover a tech to see what it requires and what it unlocks.
- Click a tech whose requirements you have to research it.
- The heading shows "Researching X: N science so far".

### 4.5 The civics screen

- **Government:** one button per government. Hover for its bonuses. Click one you have unlocked to switch, which costs 2 turns of anarchy.
- **Policies:** click to adopt, as long as you have a free slot and enough culture. The header shows `Policies 1/2 | next costs 120 culture (you have 150)`.

All details are in [§18](#18-civics-governments-and-policies).

---

# Part 2: How everything works

## 5. The map, tiles and coordinates

### 5.1 Tiles

- The world is **4,096 × 4,096 blocks**, from x = −2048 to 2047 and z = −2048 to 2047.
- Each **tile** is **64 × 64 blocks**, so the map is **64 × 64 = 4,096 tiles**.
- Tiles are numbered from **(0, 0)** in the north-west corner to **(63, 63)**. Here is the conversion:

| | Formula |
|---|---|
| Block → tile | `tile x = floor((block x + 2048) / 64)` (the same for z) |
| Tile → block, centre | `block x = −2048 + 64 × tile x + 32` |
| Example | Block (0, 0) is tile (32, 32). Tile (10, 50) is centred on block (−1376, 1184). |

- Commands that take **block** coordinates: `/civ move`, `/civ admin found`, `/civ admin probe`, `/civ admin observe`.
- Commands that take **tile** coordinates: `/civ admin move`, `/civ admin army`.
- `/civ armies` prints both.

**Distances:**
- City spacing and ranges use the **Chebyshev** distance: the larger of the x difference and the z difference. Diagonal steps count as 1.
- Everything wraps: tile 63 is next to tile 0.

### 5.2 Terrain

- When a game starts, the server **analyses** every tile. It samples 16 points of the real terrain per tile (heights, water, biomes) and gives the tile one terrain type.
- Analysis runs in the background. The strategic map shows "Analysing the world..." until it finishes.
- Before that, unanalysed tiles count as plains.

| Terrain | Food | Prod. | Gold | Found a city? | Move cost | Defence |
|---|---|---|---|---|---|---|
| Ocean | 1 | 0 | 1 | No | Land armies can't enter | — |
| Coast | 1 | 0 | 1 | No | Land armies can't enter | — |
| Grassland | **2** | 0 | 0 | Yes | 1 | ×1.0 |
| Plains | 1 | 1 | 0 | Yes | 1 | ×1.0 |
| Desert | 0 | 0 | 0 | Yes | 1 | ×1.0 |
| Tundra | 1 | 0 | 0 | Yes | 1 | ×1.0 |
| Snow | 0 | 0 | 0 | Yes | 1 | ×1.0 |
| Forest | 1 | 1 | 0 | Yes | 2 | **×1.25** |
| Jungle | 1 | 0 | 0 | Yes | 2 | **×1.25** |
| Marsh | 1 | 0 | 0 | Yes | 2 | ×0.85 (bad for defenders) |
| Hills | 0 | **2** | 0 | Yes | 2 | **×1.25** |
| Mountain | 0 | 0 | 0 | No | Impassable | — |

A **river** through the tile adds **+1 gold**.

### 5.3 Resources

- Some tiles carry a resource, which adds to the tile's yield.
- You "have" a resource when the tile is **inside your borders**. You don't need to work it.

| Resource | Kind | Bonus | Found on | Visible after |
|---|---|---|---|---|
| Wheat | Bonus | +1 food | Plains, grassland | Always |
| Cattle | Bonus | +1 food | Grassland | Always |
| Fish | Bonus | +2 food | Coast | Always |
| Stone | Bonus | +1 production | Plains, desert, tundra, hills | Always |
| **Iron** | Strategic | +1 production | Hills, tundra, desert, plains | **Bronze Working** |
| **Horses** | Strategic | +1 production | Plains, grassland, tundra | **Animal Husbandry** |
| **Gems** | Luxury | +2 gold | Hills, jungle, desert | Always |
| **Furs** | Luxury | +2 gold | Forest, tundra, snow | Always |
| **Wine** | Luxury | +2 gold | Plains, grassland, hills | Always |

- **Strategic** resources are needed to build some units (§14).
- **Luxuries** give happiness (§11).
- You can't see iron or horses, or use them, until you know the revealing tech.

### 5.4 The seam

- On a torus the "edge" is not a wall. It is the line where x or z = ±2048, i.e. tile 0 meets tile 63.
- Players, boats and soldiers cross it seamlessly.
- **Cities can't be founded within 3 tiles of it.** The refusal reads "Too close to the edge of the world".
- In practice, keep city centres between tiles 3 and 60 on both axes, which is blocks −1856 to 1855.

---

## 6. Turns and the game clock

- A **turn** passes every **60 seconds** of server time. You don't press anything.
- **The clock pauses when nobody is online.** The countdown continues when the first player joins.
- The clock counts in server ticks. If the server lags, the game slows down instead of skipping turns. At most 3 overdue turns are processed at once.
- The host can stop the clock with `civ pause` (for example during a break) and restart it with `civ resume`. The map shows "(paused)".
- The host can also skip turns: `civ tick 5` runs 5 turns immediately.

**What happens in one turn, in this order:**
1. **AI civs** and **governed offline players** decide and give their orders ([§21](#21-ai-civilizations-and-the-offline-governor)).
2. For each civ, each city:
   - collects its **food**: it grows or starves;
   - collects its **production**: it advances the queue and finishes items;
   - collects its **gold** and **science**.
3. For each civ: gold is added; **army upkeep** is paid; a bankrupt civ loses a unit; **science** goes into research.
4. **Culture** is added; borders may grow.
5. **Armies move**; battles and sieges are resolved; units heal or suffer attrition; cities repair.
6. **Victory** is checked.
7. **Fog of war** is updated, events go to chat, and the world (borders, City Centers, soldiers) is synced.

The game is **saved automatically** with the world, and on `stop`.

---

## 7. Cities

### 7.1 Founding a city

There are three ways:

| Way | Who | Notes |
|---|---|---|
| **Settler Charter** (right-click the ground) | Any player | One free charter per player per game, given on joining if you have no civ yet. The first city creates your civ; a member of an existing civ who uses a charter founds a new city for that civ. |
| **Settlers unit** → **Found city here** | Civ members | The normal way to expand. The Settlers unit is used up. |
| `/civ found "Name" [Civ name]` | Anyone without a civ (and operators) | A fallback for a lost charter. It founds at your feet and places the City Center. |

**Rules, each with its refusal message:**
- The centre tile must be land and passable: no ocean, coast or mountain. *"Cities can't be founded on ocean."*
- It must be at least **3 tiles** from the seam. *"Too close to the edge of the world."*
- It must be at least **3 tiles** (Chebyshev) from every other city, any civ's. *"Too close to Avalon (2 tiles; need 3)."*
- It must not be on another civ's land. *"That land belongs to …"*
- It needs a name not already used by another city. *"There is already a city called …"*
- No game running: *"No game is running."*
- Charters work only in the Overworld.

**What a new city gets:**
- **Population 1** and **100/100 defence**.
- It claims every **unowned** tile in the 3 × 3 square around it.
- Your **first** city becomes the **capital** and gets a free **Palace**. It is also an **original capital**, which matters for domination victory.

**The City Center block:**
- It is **indestructible** (like bedrock) and glows.
- When a city is founded from the map (Settlers), the block appears at the tile's centre on the surface once that chunk is loaded.
- Right-clicking it opens the city screen for its owner.

### 7.2 Which tiles a city works

Each city works:
- its **centre tile**, which always gives at least **2 food and 1 production**;
- plus **one more tile per point of population**.

The extra tiles are chosen automatically:
- only tiles **within 2** of the centre (a 5 × 5 square) that **your civ owns** and are passable;
- the best first, scored by food × 4 + production × 3 + gold × 2 (growth comes first).

**Example.** A population-3 city on plains (raised to 2/1/0 as the centre) works three tiles: grassland with wheat (3/0/0), grassland with a river (2/0/1) and hills (0/2/0). In total: 2 + 3 + 2 + 0 = **7 food**, 1 + 0 + 0 + 2 = **3 production**, **1 gold**, before buildings.

> To give a city more tiles, its borders must cover them: culture (§9). Two cities close together share the same 5 × 5 areas, and each tile can be counted by both.

### 7.3 City defence (health)

| Rule | Value |
|---|---|
| Starting and maximum defence | 100 |
| Repair per turn, when not attacked that turn | +10 |
| Defence after capture | 25 |

- The city's **strength** in a siege is: (**8** + **2 × population** + walls bonus) × max(0.25, defence / 100).
- **Walls** add +5 and a **Castle** adds +8.

---

## 8. Food, growth and production

### 8.1 Food and growth

- Each citizen **eats 2 food** per turn.
- **Surplus** = food made (tiles + buildings + policies) − 2 × population.
- The surplus accumulates. The city grows when stored food reaches **10 + 5 × population**:

| From population | 1 | 2 | 3 | 4 | 5 | 6 | 8 | 10 |
|---|---|---|---|---|---|---|---|---|
| Food to grow | 15 | 20 | 25 | 30 | 35 | 40 | 50 | 60 |

- When the city grows, the store resets to 0.
- If your civ's **happiness is negative**, the surplus is **divided by 4**: unhappy cities grow very slowly.
- If stored food goes **below 0**, the city **starves**: it loses 1 population (never below 1) and its store resets.

### 8.2 Production

- **Production per turn** = tile production + building production, then adjusted by your government and policies (e.g. +10%).
- If your happiness is **−10 or lower**, production is cut to **two-thirds**.
- Production goes into the **first item in the queue**. When the stored amount reaches the item's cost:
  - the item is finished;
  - the rest **carries over** to the next item;
  - at most one item finishes per city per turn.
- An **empty queue** stores nothing. Production is wasted, so keep something queued.
- **Removing** an item doesn't lose the stored production.

**Queue rules, with their refusal messages:**
- You need the required tech. *"Requires civcore:writing."*
- A building can't be built twice in one city, or queued twice.
- **National projects** (Great Survey, Steam Expedition, World Exhibition):
  - one per civ, anywhere;
  - they must be built in order.
- A unit that needs a **strategic resource** needs a free one (§11.1). *"Needs a free source of iron inside your borders."*
- **Settlers** can't be queued while your happiness is **−10 or lower**.

---

## 9. Culture and borders

**Culture per turn:**
- Each city makes **1 culture** plus its buildings' culture (Monument +2, Shrine +1, Temple +2, Cathedral +3, Palace +1).
- Government and policies adjust it: Theocracy +25%, Monumental Architecture +20%.
- Culture counts **twice**, for two different things:
  1. **The city's border store.** When it reaches the threshold, the city claims **one more tile**:
     - the threshold is **15 + 10 × (tiles already gained this way)**: 15, 25, 35, 45, …;
     - the tile is the best **unowned** tile within **3** of the city that touches your territory;
     - "best" means the highest food × 3 + production × 2 + gold, with nearer tiles winning ties.
     - If there is nothing left to take, the store waits at the threshold.
  2. **Your civ's culture total.** It is spent on **policies** (§18) and counts toward a **culture victory** (§22).
- Borders never take **owned** land; you can't push another civ out with culture.
- The city screen shows "border grows in N".

---

## 10. Gold, upkeep and connections

**Income per turn:**
- the tiles' gold (rivers, coast, luxuries);
- plus buildings (Palace +2, Market +2, Harbor +1, Bank +3);
- plus **connections**;
- then adjusted by government and policies.

**Connections:**
- Each of your cities that is connected to your capital gives **+2 gold**.
- A city is connected when:
  - it is within **12 tiles** of the capital **and** your armies could walk there by land; **or**
  - both it and the capital have a **Harbor**.

**Upkeep:**
- Each city supports **2 military units for free**.
- Every further unit costs **1 gold per turn**; Settlers don't count.
- The *Professional Army* policy halves upkeep.

**Bankruptcy:**
- If your gold falls below 0, it is set to 0.
- Your **weakest unit deserts**, once per turn, until you can pay. Chat says *"… cannot pay its army: a unit of warriors deserted."*

Gold has no other use yet. You can't buy items, rush production or trade. It is a buffer for upkeep, and it shows how your economy is doing.

---

## 11. Resources and happiness

### 11.1 Strategic resources

- Each **iron** or **horses** tile inside your borders is **1 source**, once you know its tech (Bronze Working for iron, Animal Husbandry for horses).
- Each unit that needs the resource **uses 1 source** for as long as it exists. A unit **already in a queue** uses one too.
- So with 2 iron tiles you can have 2 Swordsmen, Cannon or Riflemen, counting ones alive and queued.
- When a unit dies, its source is free again.

| Needs Horses | Needs Iron |
|---|---|
| Chariot, Horsemen, Knights | Swordsmen, Cannon, Riflemen |

### 11.2 Happiness

Happiness is counted **for the whole civ**:

```
happiness = 6
          + 4 per different luxury you own (gems, furs, wine: 3 possible = +12)
          + buildings (Palace +2, Shrine +2, Temple +1, Colosseum +3, Cathedral +2)
          + government (Monarchy +2, Theocracy +2, Const. Monarchy +3) + Civic Pride policy (+3)
          − 2 per city
          − half your total population (rounded down)
```

Only **different** luxuries count; a second gems tile adds nothing.

| Happiness | Effect |
|---|---|
| 0 or more | Normal |
| Below 0 | Cities grow **4× slower** |
| −10 or less | Also: production **−⅓**, and **no Settlers** can be queued |

**Example.** 3 cities with 12 people in total, the Palace, one Shrine and wine: 6 + 4 + 2 + 2 − 6 − 6 = **+2**.

---

## 12. Science and the technology tree

**Science per turn:**
- **1 per citizen** in all your cities;
- plus buildings (Palace +2, Library +2, Observatory +2, University +3, Public School +3);
- then adjusted by government and policies (Republic +15%, Scholasticism +10%).

**Research:**
- You research **one tech at a time**.
- When it finishes, extra science carries over, but research then **waits** until you choose the next tech. Keep an eye on chat for "discovered".
- **Switching** to a different tech before finishing **loses the progress** on the old one.
- Each tech requires all the techs listed under "Requires".

**Anarchy** (the 2 turns after changing government) stops **all** science and gold.

### Ancient era

| Technology | Science cost | Requires | Unlocks |
|---|---|---|---|
| Agriculture | 20 | — | — |
| Animal Husbandry | 35 | Agriculture | Reveals **Horses** |
| Archery | 35 | Agriculture | Archers (unit) |
| Mining | 35 | Agriculture | — |
| Pottery | 35 | Agriculture | Granary |
| Bronze Working | 55 | Mining | Barracks, Spearmen (unit), Swordsmen (unit); reveals **Iron** |
| Masonry | 55 | Mining | Walls |
| Sailing | 55 | Pottery | — |
| The Wheel | 55 | Animal Husbandry | Chariot (unit) |
| Writing | 55 | Pottery | Library |
| Calendar | 80 | Pottery | Shrine |
| Currency | 80 | Bronze Working | Market; **Monarchy** government |

### Medieval era

| Technology | Science cost | Requires | Unlocks |
|---|---|---|---|
| Horseback Riding | 110 | Animal Husbandry, The Wheel | Horsemen (unit) |
| Iron Working | 110 | Bronze Working | — |
| Mathematics | 120 | Writing, The Wheel | — |
| Construction | 130 | Masonry, The Wheel | Colosseum |
| Philosophy | 130 | Writing, Calendar | Temple; **Republic** government |
| Optics | 140 | Sailing, Mathematics | Harbor |
| Engineering | 160 | Construction, Mathematics | Aqueduct |
| Feudalism | 170 | Construction, Iron Working | Castle, Pikemen (unit) |
| Civil Service | 180 | Philosophy, Currency | — |
| Theology | 190 | Philosophy, Calendar | Cathedral; **Theocracy** government |
| Machinery | 200 | Engineering, Iron Working | Workshop, Crossbowmen (unit) |
| Chivalry | 220 | Horseback Riding, Feudalism | Knights (unit) |

### Industrial era

| Technology | Science cost | Requires | Unlocks |
|---|---|---|---|
| Gunpowder | 300 | Machinery, Chivalry | Musketmen (unit) |
| Printing Press | 300 | Theology, Machinery | University |
| Banking | 320 | Civil Service, Currency | Bank; **Merchant Republic** government |
| Astronomy | 330 | Theology, Optics | Observatory |
| Navigation | 340 | Optics, Astronomy | — |
| Metallurgy | 360 | Gunpowder | Cannon (unit) |
| Economics | 380 | Banking, Printing Press | Great Survey (science project 1); **Constitutional Monarchy** |
| Scientific Theory | 420 | Astronomy, Printing Press | Public School |
| Steam Power | 460 | Scientific Theory, Metallurgy | Steam Expedition (science project 2) |
| Industrialization | 500 | Steam Power, Economics | Factory |
| Military Science | 520 | Metallurgy, Industrialization | Riflemen (unit) |
| Railroad | 560 | Industrialization, Navigation | World Exhibition (science project 3) |

A tech marked "—" is a stepping stone to later techs.

---

## 13. Buildings

**Rules:**
- Buildings are **abstract**: they are entries in the city's list, not structures in the world.
- Each building is built **once per city**.
- Effects are **per turn** and apply **only to that city**, except happiness, which is national.
- Culture and science feed your national totals.

| Building | Production cost | Needs tech | Effect |
|---|---|---|---|
| Monument | 30 | — | +2 culture |
| Granary | 40 | Pottery | +2 food |
| Shrine | 40 | Calendar | +1 culture, +2 happiness |
| Barracks | 50 | Bronze Working | *No effect yet* (placeholder) |
| Walls | 50 | Masonry | +5 city defence strength |
| Library | 60 | Writing | +2 science |
| Market | 70 | Currency | +2 gold |
| Temple | 80 | Philosophy | +2 culture, +1 happiness |
| Harbor | 100 | Optics | +1 food, +1 gold; connects to a capital that also has a Harbor |
| Palace | 100 | — (free in the capital) | +2 production, +2 science, +2 gold, +1 culture, +2 happiness |
| Colosseum | 110 | Construction | +3 happiness |
| Aqueduct | 120 | Engineering | +2 food |
| Workshop | 120 | Machinery | +2 production |
| Castle | 140 | Feudalism | +8 city defence strength |
| Cathedral | 160 | Theology | +3 culture, +2 happiness |
| Bank | 180 | Banking | +3 gold |
| Observatory | 180 | Astronomy | +2 science |
| University | 200 | Printing Press | +3 science |
| Public School | 240 | Scientific Theory | +3 science |
| Factory | 280 | Industrialization | +4 production |
| **Great Survey** | 400 | Economics | Science project 1 of 3 (one per civ) |
| **Steam Expedition** | 600 | Steam Power | Science project 2 of 3; needs project 1 finished |
| **World Exhibition** | 800 | Railroad | Science project 3 of 3; needs project 2. **Finishing it wins a science victory.** |

The Palace is not offered in the build list; the capital gets it free. If you lose your capital, the Palace stays in that city and your next city becomes the capital (without a Palace).

---

## 14. Units and armies

### 14.1 Units

| Unit | Production cost | Strength | Moves per turn | Needs tech | Needs resource | Soldier in the world |
|---|---|---|---|---|---|---|
| Scout | 20 | 1 | 3 | — | — | Recruit, wooden sword |
| Warriors | 25 | 6 | 2 | — | — | Recruit, wooden sword |
| Archers | 35 | 5 | 2 | Archery | — | Bowman |
| Spearmen | 45 | 11 | 2 | Bronze Working | — | Shieldman |
| Settlers | 50 | 0 | 2 | — | — | *(no soldier)* |
| Chariot | 55 | 10 | 4 | The Wheel | Horses | Horseman |
| Swordsmen | 60 | 14 | 2 | Bronze Working | Iron | Recruit, iron sword |
| Horsemen | 70 | 12 | 4 | Horseback Riding | Horses | Horseman |
| Pikemen | 80 | 16 | 2 | Feudalism | — | Shieldman |
| Crossbowmen | 90 | 13 | 2 | Machinery | — | Crossbowman |
| Knights | 120 | 20 | 4 | Chivalry | Horses | Horseman |
| Musketmen | 140 | 24 | 2 | Gunpowder | — | Crossbowman |
| Cannon | 150 | 22 | 2 | Metallurgy | Iron | Crossbowman |
| Riflemen | 200 | 34 | 2 | Military Science | Iron | Crossbowman |

- **Strength** is what counts in combat. Ranged units have no special ranged attack in the strategic game yet; strength is all that matters.
- **Moves** are tiles per turn on easy terrain (§15.1).

### 14.2 Armies (stacks)

**What an army is:**
- An **army** is a stack of **up to 10 units** on one tile.
- There are **three kinds**, and they never mix:
  1. **Settlers** stacks;
  2. **Scout** stacks;
  3. **military** stacks (everything else).

**How armies form:**
- A finished unit joins a **waiting** (not moving) army of the same kind on the city tile, if it has room. Otherwise it forms a new army.
- **Idle** armies of the same civ and kind on the same tile **merge** at the start of each turn, up to 10.
- An army moves at the speed of its **slowest** unit.
- An army's **health** (shown in %) is the average of its units' health. Each unit has its own **100 HP**.

**Army numbers:**
- Every army has an **id number**, shown on the map ("Army #57") and in `/civ armies`.
- Commands use it, e.g. `/civ move 57 here`.

---

## 15. Movement, combat, sieges and capture

### 15.1 Movement

**Giving orders:**
- Select your army on the map and right-click a destination. The server finds the shortest route.
- To cancel an order, give a new one, e.g. right-click the army's own tile.

**Movement points:**
- Each turn an army gets **moves = its slowest unit's moves**.
- Entering open terrain costs **1**. Entering **hills, forest, jungle or marsh** costs **2**.
- An army always makes at least one step per turn, even if the step costs more than it has.

**Where armies can go:**
- **Not** onto **ocean, coast or mountains**. There are no ships yet.
- **Not into the territory of a civ you are at peace with.** Borders are closed in peacetime. *"No route to (x,z) (water, mountains, or closed borders)."*
- **Into anyone's territory once you are at war with them.**

**Interruptions:**
- If the next tile is blocked (a city or army of a civ you're at peace with), the march stops.
- **Attacking ends the move** for that turn.

### 15.2 Battles (army against army)

When your army steps onto a tile holding an enemy army (you must be at war), it attacks the **strongest** army there.

**1. Strength**
- **Attacker strength** = the sum of each unit's strength × its health % × (1 + your strength bonus).
  - The strength bonus comes from the Military Tradition policy (+10%).
  - Example: 3 full-health Spearmen = 33.
- **Defender strength** = the same calculation × the **terrain bonus** (hills, forest or jungle ×1.25; marsh ×0.85) × **1.5 if standing in a city**.

**2. Damage**
- Both sides deal damage at the same time, using this formula:

```
damage = 30 × (my strength / their strength) ^ 0.7 × random(0.8 … 1.2)      (between 1 and 100)
```

- **Every unit in the army** takes that damage.
- Units at 0 HP die. An army with no units left is destroyed.

**What the numbers mean:**

| Ratio | Damage dealt, roughly |
|---|---|
| Even fight (1:1) | ~30 each way |
| 2:1 | Stronger side deals ~49 and takes ~18 |
| 3:1 | Deals ~65, takes ~14 |

Fight at good odds, on good ground.

**Other rules:**
- Results are **deterministic**: they depend on the game seed, the turn and the army ids. Reloading the save gives the same result.
- **Special case:** if **both** armies are currently **materialised as soldiers** (§16), the strategic battle is skipped. The soldiers fight it out for real, and every soldier killed removes its unit.

### 15.3 Sieges and capture

**Siege:**
1. An army stepping onto an enemy **city** tile attacks the city, as long as no enemy army defends it. Settlers can't besiege.
2. The city's strength (§7.3) is compared with the army's, and the city loses **defence** points by the same damage formula.
3. The city hits back with **half** the formula's damage.
4. A city attacked this turn doesn't repair.

**Capture:** when the city's defence is **0**, the next army to step in **captures** it. After capture:
- the city changes owner and keeps its buildings;
- its defence becomes **25**;
- its population **drops by 1** (minimum 1);
- its production queue and stored production are **cleared**;
- the old owner's land within 3 tiles that is **closer to this city** than to any of their other cities changes hands with it;
- if it was the old owner's capital, their next city becomes the capital.

**Practical consequences:**
- Taking a city usually takes **several turns of siege**, so bring a large stack.
- Walls and Castles, and a big population, make it much harder.
- Defending armies inside the city get **×1.5**.

### 15.4 Healing and supply

At the end of each turn, armies that **didn't fight** heal:

| Where the army is | Per unit, per turn |
|---|---|
| Inside your borders | +10 HP |
| Elsewhere, but within **6 tiles** of one of your cities | +5 HP |
| **Out of supply**: outside your borders and more than 6 tiles from all your cities | **−5 HP (attrition)**. Units can die; chat says "starved far from home". |

**Physical** armies (currently soldiers) don't heal or suffer attrition while materialised. Their soldiers' real health is used instead.

---

## 16. Soldiers in the world (Recruits)

This is where the strategic game meets Minecraft combat.

**Materialising:**
- When **any player** (any civ, or none) comes within **128 blocks** of an army's tile centre, that army **materialises**.
  - It becomes one **Recruits soldier per unit**, at the unit's health.
  - Soldiers are named "*Civ name Unit*" and equipped by unit type (see the unit table).
  - **Settlers don't become soldiers.** A settler stack shows in the world as an armour stand wearing a helmet in its civ's colour.
- The army is now **physical**:
  - its soldiers walk to the tile it is moving to, or hold position at its tile;
  - they **fight automatically** using Recruits' AI, but only enemies of a civ **you are at war with**.
  - At peace, soldiers of different civs ignore each other.
- **Engaging:** soldiers engage an enemy soldier or a hostile **player** within **24 blocks**. They chase targets up to about **96 blocks**. They also engage when an enemy physical army stands on the same or a neighbouring tile.

**Players and soldiers:**
- **Your own civ's soldiers never attack you**, and you are on their team.
- Enemy soldiers attack you on sight during a war, unless you are in creative or spectator mode.
- **You can kill enemy soldiers yourself.** Each kill removes exactly that unit from that army, as if it died in a strategic battle.
  - When the last soldier dies, the army is destroyed and chat announces it.
  - Anything that kills a soldier counts: arrows, lava, TNT, mobs.

**Dematerialising:**
- When **no player is within 192 blocks** **and** the soldiers haven't fought for about **10 seconds** (200 ticks), the army **dematerialises**.
- The soldiers vanish, and each unit keeps the health its soldier had.

**Limits and safety:**
- At most **300 soldiers** exist at once, server-wide. Further armies stay abstract.
- Soldiers are **never saved** with the world. On restart, any leftovers are deleted and armies start abstract again. This prevents duplicates.
- If a soldier somehow survives a crash, it is removed and no unit is created from it.
- The Recruits mod's own features (hiring recruits, upkeep, hunger and morale, claiming) are partly turned off on this server. Treat Civ soldiers as part of Civ Core, not as Recruits you hire.

---

## 17. Diplomacy: war and peace

**Meeting:**
- You can only deal with civs you have **met**, i.e. seen at least one of their tiles, cities or armies.
- They then appear as buttons in the map panel and in `/civ civs`.

**Declaring war:**
- Use the **Declare war** button, or `/civ war <civ#>`. It takes effect **immediately**, without needing the other side to agree, and **everyone** is told.
- While at war:
  - each side's borders are open to the other's armies;
  - their armies fight when they meet;
  - cities can be besieged;
  - their soldiers fight in the world;
  - their soldiers attack the other civ's players.
- **Territory protection** (§20) still applies during war: you can't grief enemy builds.

**Making peace:**
1. One side clicks **Offer peace** (or `/civ peace <civ#>`).
2. The other side clicks **Accept peace** (or runs the same command). Peace takes effect **immediately**.

On peace:
- both sides' armies standing in the other's territory are **sent home**, teleported strategically to their nearest own city;
- a **20-turn treaty** starts. Neither side can declare war on the other until it ends; the button shows "Treaty *N*t".
- Declaring war cancels any pending peace offers between the two civs.

There are no alliances, trade deals or tributes yet.

---

## 18. Civics: governments and policies

### 18.1 Governments

Everyone starts in **Despotism**. Discovering the listed tech unlocks a government.

| Government | Needs tech | Policy slots | Bonuses |
|---|---|---|---|
| Despotism | — | 1 | — |
| Monarchy | Currency | 2 | +2 happiness, +10% production |
| Republic | Philosophy | 3 | +15% science, +10% gold |
| Theocracy | Theology | 2 | +2 happiness, +25% culture |
| Merchant Republic | Banking | 3 | +5% science, +25% gold |
| Constitutional Monarchy | Economics | 4 | +3 happiness, +10% science, +10% gold, +10% production |

**Switching government:**
- It causes **2 turns of anarchy**: **no science and no gold income** during that time. Upkeep is still paid.
- If the new government has fewer slots, your most recently adopted policies are dropped.

### 18.2 Policies

- Policies cost **culture**: **60 for the first, then +60 each** (60, 120, 180, 240, …).
- "Free culture" is your total culture minus what you've spent on policies.
- Spending culture on policies **does not** lower your progress toward a culture victory. That uses your total.

| Policy | Effect |
|---|---|
| Agrarian Reform | +1 food in every city |
| Professional Army | Unit upkeep halved |
| Scholasticism | +10% science |
| Trade Guilds | +15% gold |
| Civic Pride | +3 happiness |
| Military Tradition | +10% unit strength (battles and sieges) |
| Monumental Architecture | +20% culture |
| Work Ethic | +10% production |

Percentages from the government and policies add together, and are applied to each city's yield, rounded down.

---

## 19. Fog of war

Each civ has its own view of the map.

**What you see right now:**
- tiles within **3** of your cities;
- tiles within **2** of your armies;
- tiles within **2** of wherever a member of your civ is **standing in the world**. So exploring on foot or horseback reveals the map.

**What you remember:**
- Once seen, a tile stays **explored**. You remember its terrain, owner and cities.
- You only see **armies** on tiles you can see **right now**.

**Rules for players:**
- Players without a civ see nothing on the map.
- The server only ever sends each player what their civ can see. The map isn't hiding data you could hack out; the data isn't there.
- The AI also only acts on cities it has seen.

---

## 20. Territory protection

- On a tile owned by a civ **you don't belong to**, you **can't break or place blocks**. The message reads *"This land belongs to …"*. This applies **at war and at peace**.
- On your own civ's land, and on unowned land, you build freely.
- **Creative-mode operators** are exempt.
- Protection follows **tiles**, i.e. 64 × 64-block columns from bedrock to sky, not individual claims. When a border tile changes hands (culture growth, capture), the protection changes with it.
- Civ Core does this itself and does **not** create Open Parties and Claims claims. OPAC is installed but separate, so don't use OPAC claims to protect Civ land.

---

## 21. AI civilizations and the offline governor

### 21.1 AI civilizations

- The host adds them with `civ addai [Name]`. Each AI:
  - gets a starting site as far from existing cities as possible, on good land;
  - founds its capital there immediately.
- Each turn, the AI gives the **same commands a player could**; it has no cheats. It reads the whole game state, except that it only targets cities it has seen.

**What the AI does:**
- **Research:** picks techs it values (more aggressive AIs favour military techs).
- **Civics:** switches government and adopts policies when it can.
- **Production:**
  - fills empty queues;
  - keeps about 2 military units per city in peace and 4 per city in war, scaled by aggression;
  - expands to about 4 cities;
  - builds Settlers, buildings and science projects.
- **Settlers:** walk to the best site within 8 tiles and found a city.
- **Scouts:** explore the nearest unexplored land.

**War:**
- Each AI has an **aggression** of 0.8 to 1.4, fixed per civ and game.
- It **won't declare war before turn ~43–75** (60 ÷ aggression).
- After that, it declares war on the nearest rival it can reach when its army is clearly stronger: at least 1.2 × their army, and 0.5 × their city defences, both divided by its aggression.
- At war, it sends stacks of at least 3 units at the cheapest enemy city and defends cities under threat (enemies within 3 tiles).

**Peace:**
- It **offers peace** when its army is under half the enemy's strength.
- It **accepts** your offer if its army isn't stronger than yours.

AI civs are named "Civilization N", or whatever name you gave, and use city names like Alba, Brisa and Corvo.

### 21.2 The offline governor

- When **every member** of a player's civ is **offline**, that civ is run by a **governor** for each turn the server is running. That can happen when others are online, or when the host has turned off pausing.
- The governor **only** does:
  - research;
  - production;
  - Settlers founding cities.
- It **never**:
  - declares war or makes peace;
  - changes civics;
  - moves military armies.
- Your civ keeps developing while you sleep, but nobody can be dragged into a war while away.
- Turn it off with `civ admin governor false`. Then offline civs just keep their current queues and research.

---

## 22. Victory and the end of the game

| Victory | How to win | Notes |
|---|---|---|
| **Domination** | One civ owns **every original capital**. | Needs at least 2 civs that have founded a capital. Your own must be in your hands too. |
| **Science** | Finish all three science projects, in order: Great Survey (400), Steam Expedition (600), World Exhibition (800). | Needs Economics, Steam Power, Railroad |
| **Culture** | Reach the culture target in **total culture** earned. | Automatically **8,000 culture per 1,024 tiles**, at least 4,000. On this 4,096-tile map that is **32,000**. The map panel shows your progress. |
| **Score** | Only if a **turn limit** is set: at that turn, the highest score wins. | Score = 3 × population + 10 × cities + 5 × techs + 1 × owned tile |

**Elimination:**
- A civ that has founded a city and then has **no cities and no armies** left is **eliminated**.
- Its members can still walk around, but have no civ to play.

**When someone wins:**
- Everyone gets an announcement.
- **All Civ commands are refused** from then on: *"The game is over: X won a culture victory."*
- To play again, an operator runs `civ newgame auto`.

The host can switch victory types on and off: `civ admin victory culture false`. The culture target can be changed: `civ admin culturetarget 20000`.

---

# Part 3: Commands, admin and debugging

## 23. Player commands

Anyone can use these in chat. Use **Tab** to autocomplete names.

| Command | What it does |
|---|---|
| `/civ status` | Game summary: turn, your civ, your cities in detail (population, food, production, queue, buildings) |
| `/civ map` | Opens the strategic map (same as **Y**) |
| `/civ tile` | Shows the tile you stand on: coordinates, terrain, owner, resource |
| `/civ civs` | Every civ, with **#id**, controller (player or AI), city count, score, and war or peace with you |
| `/civ armies` | Your armies: **#id**, units, health, tile, block position, holding or moving |
| `/civ techs` | Tech list: `[x]` known, `[>]` available, `[ ]` locked |
| `/civ research <tech>` | Research a tech, e.g. `/civ research writing` |
| `/civ queue <item>` | Add a building or unit to the queue of **your city nearest to you**, e.g. `/civ queue settlers` |
| `/civ move <army#> here` | Send an army to where you stand |
| `/civ move <army#> <x> <z>` | Send an army to block coordinates |
| `/civ settle <army#> <City Name>` | Found a city with that army's Settlers on its current tile |
| `/civ war <civ#>` | Declare war |
| `/civ peace <civ#>` | Offer peace, or accept their offer |
| `/civ government <NAME>` | e.g. `/civ government MONARCHY` |
| `/civ policy <NAME>` | e.g. `/civ policy WORK_ETHIC` |
| `/civ found "<City>" [Civ name]` | Found your **first** city at your feet, if you lost your charter. Quote names with spaces. |

---

## 24. Operator and admin commands

**Who can run these:**
- operators (permission level 2), in game with `/`;
- the **server console**, without `/`.

Bzier555 is an operator. To make someone else an operator: `op <name>`.

### 24.1 Game control

| Command | What it does |
|---|---|
| `civ newgame auto` | **Starts a new game**, sized to this Exotic Worlds world. It **replaces** the current game; the old save is kept as a backup. Then wait for "World analysed". |
| `civ newgame <torus\|cylinder\|bounded> <sizeBlocks>` | New game with a manual shape. Only for non-Exotic Worlds testing worlds. |
| `civ addai [Name]` | Adds an AI civ at the best free site and founds its capital. Needs the analysis to be finished. |
| `civ starts [n]` | Lists the best n (1–16) start sites with `/tp` commands |
| `civ tick [n]` | Runs n turns (1–1000) **right now** |
| `civ pause` / `civ resume` | Stops or restarts the turn clock |
| `civ save` | Saves `world/civcore/game.json` now |
| `civ admin turnlimit <turn>` | Ends the game with a score victory at that turn; 0 means no limit |
| `civ admin victory <domination\|science\|culture\|score> <true\|false>` | Turns a victory type on or off |
| `civ admin culturetarget <n>` | Sets the culture-victory target; 0 means automatic |
| `civ admin governor <true\|false>` | Offline governor on or off |
| `civ admin addmember <civ#> <player>` | Puts an online player who has no civ into civ #. This is how two people **share a civilization**. |

### 24.2 Acting for a civilization ("issuer" commands)

**Issuers:**
- These commands act **as** some civ member, named by an **issuer** string:
  - for an **AI civ**: `"ai:<civ#>"`, e.g. `"ai:12"`. Get the number from `civ civs`.
  - for a **player**: their **UUID**, e.g. `"4d47d715-2f9a-4911-a16c-6973d7c0fdfa"`, which is Bzier555's.
- **Always put the issuer in double quotes.** The colon in `ai:12` breaks the command otherwise.
- Use these to test, to fix a stuck situation, or to set up scenarios.

| Command | What it does |
|---|---|
| `civ admin found "<issuer>" "<City>" <x> <z>` | Founds a city for that issuer at **block** x z. It creates a civ if the issuer has none. |
| `civ admin move "<issuer>" <army#> <tx> <tz>` | Orders an army to **tile** tx tz |
| `civ admin war "<issuer>" <civ#>` | Declares war as that issuer |
| `civ admin peace "<issuer>" <civ#>` | Offers or accepts peace as that issuer |
| `civ admin queue "<issuer>" <item>` | Queues an item in the issuer's capital |
| `civ admin settle "<issuer>" <army#> <City>` | Founds a city with that army's Settlers |
| `civ admin research "<issuer>" <tech>` | Sets research |
| `civ admin army "<issuer>" <unit> <n> <tx> <tz>` | **Spawns** an army of n (1–10) units at **tile** tx tz, from nothing |

### 24.3 Debug tools

| Command | What it does |
|---|---|
| `civ admin probe <x> <z>` | Compares the terrain analysis with the real world at a block: sampled floor and surface heights against the real heights, water and biome. Use it when a tile's terrain looks wrong. |
| `civ admin observe <x> <z>` | Makes armies near that **block** materialise **as if a player stood there**. Use it to test soldiers without walking. |
| `civ admin observe clear` | Removes all observe points |

Useful vanilla and other commands:
- `/spark tps`: server speed. 20 is perfect; under 15 is lagging.
- `/spark health`: memory and CPU.
- `/spark profiler start` … `/spark profiler stop`: gives a link to a lag report.
- `/tp @s <x> ~ <z>`: teleport.
- `/gamemode spectator`: watch without being attacked or blocked.
- `/kill @e[tag=civcore.soldier]`: removes all soldiers. **Warning:** this counts as killing them, so the units die. Prefer walking away (§16) or restarting.

---

## 25. Files, saves and logs

All paths are inside `C:\CivServer`.

| File | What it is |
|---|---|
| `start.bat` | Starts the server, with 4–10 GB of memory (set in `user_jvm_args.txt`) |
| `server.properties` | Port 25565, whitelist, world type `exotic_worlds:torus_large`, RCON |
| `world\` | The Minecraft world |
| `world\civcore\game.json` | **The Civ game save**: every civ, city, army and tile. Human-readable JSON. |
| `world\civcore\game.json.1`, `.2`, `.3` | The previous three saves; `.1` is the newest |
| `config\` | Mod configs (MineColonies, Recruits, …) |
| `mods\` | Server mods. Clients need the **same** set, minus Chunky. |
| `logs\latest.log` | The current server log. **Send this when reporting a problem.** |
| `crash-reports\` | Written when the server crashes |
| `rcon-password.txt` | Password for remote console tools. **Keep it local; never forward port 25575.** |
| `CivCore-CurseForge.zip` | The client modpack to give to players |
| `GUIDE.md` | This guide |

**Backups and restoring:**
- To back up the whole game: **stop the server**, then copy the `world` folder.
- To **roll the Civ game back** one save:
  1. Stop the server.
  2. Rename `game.json` to `game.json.bad`.
  3. Copy `game.json.1` to `game.json`.
  4. Start the server.
- The Minecraft world itself is not rolled back by this, only the Civ state.

**Log lines worth knowing** (search `latest.log` for "Civ"):
- `Civ game loaded: turn 37, 3 civs, 8 cities`: the save loaded correctly.
- `Civ world analysis started / finished`: terrain analysis.
- `Civ ruleset changed since last save`: the techs, buildings or units files changed. This is harmless; it is a note.
- `Could not load …game.json; Civ Core is disabled for this session`: a **broken save**. Restore a backup (above).
- `Failed to save the Civ game`: disk or permission problem. Check free space and OneDrive or antivirus locks.

---

## 26. Troubleshooting: symptom → cause → fix

### Joining

| Symptom | Cause | Fix |
|---|---|---|
| "Mod rejections" or "missing mods" on join | Your client isn't the Civ Core profile, or is an old version | Re-import the latest `CivCore-CurseForge.zip` and play **that** profile |
| "You are not white-listed" | Whitelist is on | Host: `whitelist add <name>` |
| Can't connect at all | Server or playit isn't running, or the Drehmal server is using the port | Start `start.bat` and playit; stop Drehmal |
| Join takes a long time, or "Timed out" | First join on a big modpack | Try again. Give the client at least 6 GB in CurseForge → profile → Settings. |

### Founding and cities

| Symptom | Cause | Fix |
|---|---|---|
| No Settler Charter | Already given once this game, or you already have a civ | `/civ found "Name"` if you have no civ. If you do, train Settlers. |
| "Cities can't be founded on …" | Water, coast or mountain tile | Move a tile inland (`/civ tile` to check) |
| "Too close to the edge of the world" | Within 3 tiles of x or z = ±2048 | Move toward the middle |
| "Too close to *City*" | Within 3 tiles of another city | Move further away |
| "That land belongs to …" | Another civ's land | Settle elsewhere, or take it in a war |
| "No Civ game is running" | No game started, or the save failed to load | Host: `civ status`; `civ newgame auto`; check the log |
| City founded from the map but no City Center block | The chunk wasn't loaded | Walk there; it appears when the chunk loads |
| City "not growing" or "STARVING" | Not enough food tiles, or unhappy | Grow borders, Granary or Aqueduct, Agrarian Reform; fix happiness |
| Production does nothing | **Empty queue**, or stuck on an expensive item | Queue something; remove the expensive item |
| Can't queue a unit: "Needs a free source of iron" | Every iron source is in use by units or queues | Get another iron tile, or lose or remove a unit using it |
| Can't queue Settlers: "too unhappy" | Happiness −10 or less | Luxuries, Shrine, Temple, Colosseum, Civic Pride; fewer cities |

### Research and civics

| Symptom | Cause | Fix |
|---|---|---|
| Research isn't progressing | No tech selected; the last one finished | Pick one on the Technologies screen |
| Science and gold stuck at 0 for 2 turns | Anarchy after changing government | Wait 2 turns |
| "No free policy slot" | Government's slot limit | Better government |
| "Needs N culture" | Not enough free culture | Build culture buildings; wait |

### Armies and war

| Symptom | Cause | Fix |
|---|---|---|
| "No route to …" | Water, mountains, or **the borders of a civ you're at peace with** | Route around, or declare war first |
| Army stops partway | It ran into a city or army of a civ you're at peace with, or it attacked | Give a new order |
| Can't declare war: "treaty holds for N turns" | 20-turn treaty after peace | Wait |
| Army losing HP while idle | Out of supply: outside your land and more than 6 tiles from your cities | Bring it home or closer to a city |
| Units deserting | Bankrupt: upkeep is more than income | Fewer units, more gold, Professional Army |
| Can't see my army's soldiers | Nobody within 128 blocks, 300-soldier cap reached, or the chunk isn't loaded | Go within 128 blocks of the army's tile centre (`/civ armies` gives the block) |
| Soldiers just stand there at war | They only fight enemies within ~24 blocks, or on the same or a next tile | Move the army onto or next to the enemy, or lead them |
| My soldiers attack me | Shouldn't happen: same team | Report with `latest.log`. Check `/civ civs` that you're in the civ. |
| Soldiers left over after a crash | Leftovers from an unclean stop | They are removed on the next load; units are not duplicated |

### Server

| Symptom | Cause | Fix |
|---|---|---|
| Turns don't advance | Nobody online (paused), or `civ pause` | Join, or `civ resume` |
| Turns skip quickly after lag | Catching up overdue turns, at most 3 at once | Normal |
| Lag (`/spark tps` under 15) | Big battles, many soldiers, chunk generation past the pregenerated area | Fewer soldiers near players. `/spark profiler`. Don't fly far beyond the world at speed. |
| Server window closed itself | Crash | Read `crash-reports\` and the end of `logs\latest.log` |
| "Ticking entity" crash near x or z = ±2048 | A Recruits pathfinding seam bug, which Civ Core guards against | Report it with the crash report. It should not happen with this build. |
| `stop` typed twice, or the window closed with X | Unclean shutdown | Usually fine; the Civ save is written on stop. If the game state looks old, it was restored from the last autosave. |

---

## 27. Debugging recipes

**"Is the game alive?"**

`civ status` → `civ civs` → `civ armies`.

Compare the turn number a minute later. It should be +1 if someone is online.

**"Something happened and I don't know why."**
1. Search `logs\latest.log` for `[Civ]`. Every event (completions, battles, wars, captures) is announced there in order, with timestamps.
2. Open `world\civcore\game.json` in a text editor; it is readable.
   - Each city has `foodStored`, `productionStored`, `productionQueue`, `buildings`, `hp`.
   - Each civ has `gold`, `knownTechs`, `currentResearch`, `researchProgress`, `members`.

**"Test a war quickly without waiting."**
1. `civ addai`, then `civ civs` to get its number, e.g. 7.
2. `civ admin army "ai:7" civcore:swordsmen 6 <tx> <tz>`, next to your city. `/civ tile` gives your tile.
3. `civ admin war "ai:7" <your civ#>`.
4. `civ tick 3` and watch the chat, or stand near it to watch the soldiers.

**"Test soldiers without walking there."**

`civ admin observe <x> <z>` at the army's block position, then `/tp` there as a spectator to watch. Use `civ admin observe clear` when done.

**"The terrain on the map looks wrong."**

Stand there and run `/civ tile`, then `civ admin probe <x> <z>`. Send both outputs. The analysis samples 16 points per tile, so a tile that is half water, half land can come out either way.

**"Start over."**

`civ newgame auto` (it keeps the world, resets the Civ game; the old save becomes a backup). Charters are handed out again on join.

**"The game is finished and I want to keep playing."**

There is no "continue"; start a new game, or turn the winning victory type off **before** it triggers.

**"Talk to the server without being in game."**
- Type in the server window.
- Or use the local RCON script on the host PC:
  ```
  RCON_PASSWORD=<contents of rcon-password.txt> python spikes/neoforge-1.21.1/rcon.py "civ status"
  ```

**"Report a bug"** with:
- what you did, step by step;
- what you expected;
- what happened;
- screenshots (F2);
- the time it happened;
- the host's `logs\latest.log` (and `crash-reports\` if any);
- `world\civcore\game.json`.

---

## 28. Known limitations

These are not bugs; they are not built yet.

- **No ships or naval combat.** Land armies can't cross water, and Harbors only give gold.
- **No roads or trade routes**, and no buying or trading with gold. City connections are calculated, not built.
- **Buildings are abstract.** MineColonies doesn't build cities yet; the City Center is the only block.
- **City defence is abstract.** No defenders spawn when a city is besieged, and the City Center can't be attacked physically.
- **Ranged units have no ranged attack** in the strategic game; it uses their strength only.
- **Barracks has no effect** yet.
- **Joining a civilization** is only possible through `civ admin addmember`. There is no invite or leave yet.
- **No renaming** of civs, and no choice of civ colour.
- **Fixed 60-second turns.** Use `civ tick` and `civ pause` to control pace.
- **No alliances**, open-borders deals or tribute.
- OPAC claims are **not linked** to Civ territory.

---

## 29. Glossary

| Term | Meaning |
|---|---|
| **Tile** | A 64 × 64-block square of the world; the strategic game's grid cell |
| **Turn** | One step of the strategic game, every 60 s while someone is online |
| **Civ, civilization** | A player's (or several players', or an AI's) nation: cities, research, armies |
| **City Center** | The indestructible glowing block marking a city |
| **Settler Charter** | Item that founds a city; one free per player per game |
| **Population** | A city's size. Each citizen works one tile, eats 2 food and gives 1 science. |
| **Queue** | A city's build list; the top item gets the production |
| **Army, stack** | Up to 10 units of one kind moving together |
| **Materialise** | An army turning into real soldiers because a player is near |
| **Upkeep** | Gold per turn for military units above 2 per city |
| **Supply** | Being in your land or within 6 tiles of your cities; outside it, armies suffer attrition |
| **Anarchy** | 2 turns of no science and no gold after changing government |
| **Treaty** | 20 turns after peace during which neither side can declare war |
| **Original capital** | A civ's first city. Holding all of them is a domination victory. |
| **Governor** | The automatic manager for a civ whose players are all offline |
| **Issuer** | Who a command acts for: a player UUID or `ai:<civ#>` |
| **Seam** | The line (x or z = ±2048) where the torus world wraps |
| **Explored / visible** | Seen at some point / seen right now (fog of war) |

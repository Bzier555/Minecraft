package dev.civcore.sim.engine;

import dev.civcore.sim.command.Command;
import dev.civcore.sim.command.Command.FoundCity;
import dev.civcore.sim.command.Command.QueueProduction;
import dev.civcore.sim.command.Command.SetResearch;
import dev.civcore.sim.command.CommandResult;
import dev.civcore.sim.model.City;
import dev.civcore.sim.model.Civilization;
import dev.civcore.sim.model.GameState;
import dev.civcore.sim.model.StrategicMap;
import dev.civcore.sim.ruleset.Ruleset;
import dev.civcore.sim.ruleset.Ruleset.BuildingDef;
import dev.civcore.sim.ruleset.Ruleset.TechDef;
import dev.civcore.sim.ruleset.Ruleset.UnitDef;
import dev.civcore.sim.topology.TileMapping;
import dev.civcore.sim.topology.TilePos;
import dev.civcore.sim.topology.Topology;
import dev.civcore.sim.rules.TileYields;
import dev.civcore.sim.rules.Military;
import dev.civcore.sim.rules.Victory;
import dev.civcore.sim.model.Army;
import dev.civcore.sim.command.Command.MoveArmy;
import dev.civcore.sim.command.Command.DeclareWar;
import dev.civcore.sim.command.Command.ProposePeace;
import dev.civcore.sim.command.Command.SettleWithArmy;
import dev.civcore.sim.worldplan.TerrainType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Rules entry point. Pure functions over {@link GameState}; the caller (server thread) owns the state.
 *
 * <p>Cities yield from their tiles (centre + one worked tile per population, {@link TileYields}) plus buildings.
 * Science is population-based. Numbers are MVP balance placeholders (backlog F1.9).
 */
public final class Simulation {
    /** Tiles claimed around a new city (Chebyshev radius). */
    public static final int FOUNDING_CLAIM_RADIUS = 1;
    private static final int[] PALETTE = {0xC0392B, 0x2980B9, 0x27AE60, 0x8E44AD, 0xD35400, 0x16A085, 0xF1C40F, 0x7F8C8D,
        0xE84393, 0x2C3E50, 0x6AB04C, 0xA0522D};

    private Simulation() {
    }

    // ------------------------------------------------------------------ commands

    public static CommandResult apply(GameState s, Ruleset r, Command cmd) {
        if (s.finished()) return CommandResult.fail("The game is over: " + s.civs.get(s.winnerCivId).name + " won a " + s.victoryType + " victory.");
        return switch (cmd) {
            case FoundCity f -> foundCity(s, r, f);
            case SetResearch sr -> setResearch(s, r, sr);
            case QueueProduction q -> queueProduction(s, r, q);
            case MoveArmy m -> moveArmy(s, r, m);
            case DeclareWar w -> declareWar(s, w);
            case ProposePeace p -> proposePeace(s, p);
            case SettleWithArmy sw -> settleWithArmy(s, r, sw);
            case dev.civcore.sim.command.Command.DequeueProduction d -> dequeue(s, d);
        };
    }

    static Optional<Army> ownArmy(GameState s, String issuer, long armyId) {
        Optional<Civilization> civ = s.civOfMember(issuer);
        Army a = s.armies.get(armyId);
        if (civ.isEmpty() || a == null || a.civId != civ.get().id) return Optional.empty();
        return Optional.of(a);
    }

    static CommandResult moveArmy(GameState s, Ruleset r, MoveArmy m) {
        Optional<Army> army = ownArmy(s, m.issuer(), m.armyId());
        if (army.isEmpty()) return CommandResult.fail("Not your army.");
        TilePos goal = s.topology().normalizeOrNull(m.tileX(), m.tileZ());
        if (goal == null) return CommandResult.fail("That tile is outside the map.");
        Army a = army.get();
        List<TilePos> path = Military.findPath(s, a.civId, a.tile(), goal);
        if (path.isEmpty() && !goal.equals(a.tile())) return CommandResult.fail("No route to " + goal + " (water, mountains, or closed borders).");
        a.path.clear();
        for (TilePos p : path) a.path.add(new int[]{p.x(), p.z()});
        return CommandResult.ok("Army " + a.id + " marching to " + goal + " (" + path.size() + " tiles, " + Military.moves(a, r) + " per turn).");
    }

    static CommandResult declareWar(GameState s, DeclareWar w) {
        Optional<Civilization> civ = s.civOfMember(w.issuer());
        Civilization target = s.civs.get(w.targetCivId());
        if (civ.isEmpty()) return CommandResult.fail("You don't have a civilization.");
        if (target == null || target.id == civ.get().id) return CommandResult.fail("Unknown civilization.");
        if (s.atWar(civ.get().id, target.id)) return CommandResult.fail("Already at war with " + target.name + ".");
        long cooldown = s.warCooldown(civ.get().id, target.id);
        if (cooldown > 0) return CommandResult.fail("The peace treaty with " + target.name + " holds for " + cooldown + " more turns.");
        s.wars.add(GameState.warKey(civ.get().id, target.id));
        s.peaceOffers.remove(civ.get().id + ">" + target.id);
        s.peaceOffers.remove(target.id + ">" + civ.get().id);
        return CommandResult.ok(civ.get().name + " declared war on " + target.name + "!");
    }

    static CommandResult proposePeace(GameState s, ProposePeace p) {
        Optional<Civilization> civ = s.civOfMember(p.issuer());
        Civilization target = s.civs.get(p.targetCivId());
        if (civ.isEmpty() || target == null) return CommandResult.fail("Unknown civilization.");
        int me = civ.get().id;
        if (!s.atWar(me, target.id)) return CommandResult.fail("You are not at war with " + target.name + ".");
        if (s.peaceOffers.remove(target.id + ">" + me)) {
            s.wars.remove(GameState.warKey(me, target.id));
            s.peaceTurns.put(GameState.warKey(me, target.id), s.turn);
            Military.expelAfterPeace(s, me, target.id);
            return CommandResult.ok(civ.get().name + " and " + target.name + " made peace.");
        }
        s.peaceOffers.add(me + ">" + target.id);
        return CommandResult.ok(civ.get().name + " offered peace to " + target.name + ".");
    }

    static CommandResult settleWithArmy(GameState s, Ruleset r, SettleWithArmy sw) {
        Optional<Army> army = ownArmy(s, sw.issuer(), sw.armyId());
        if (army.isEmpty()) return CommandResult.fail("Not your army.");
        Army a = army.get();
        if (!a.hasUnit(Military.SETTLERS)) return CommandResult.fail("That army has no settlers.");
        TileMapping m = s.settings.mapping();
        TilePos t = a.tile();
        // blockY is unknown to the simulation (Integer.MIN_VALUE); the mod places the City Center on the surface and updates it.
        CommandResult res = foundCity(s, r, new FoundCity(sw.issuer(), null, sw.cityName(), m.centerBlockX(t), Integer.MIN_VALUE, m.centerBlockZ(t)));
        if (!res.ok()) return res;
        for (int i = 0; i < a.units.size(); i++) {
            if (a.units.get(i).type.equals(Military.SETTLERS)) {
                a.units.remove(i);
                break;
            }
        }
        if (a.units.isEmpty()) s.armies.remove(a.id);
        return res;
    }

    static CommandResult foundCity(GameState s, Ruleset r, FoundCity f) {
        Topology topo = s.topology();
        TileMapping m = s.settings.mapping();
        TilePos tile = topo.normalizeOrNull(m.tileX(f.blockX()), m.tileZ(f.blockZ()));
        if (tile == null) return CommandResult.fail("That position is outside the map.");
        TerrainType terrain = s.map.terrain(tile);
        if (terrain != null && !terrain.settleable()) {
            return CommandResult.fail("Cities can't be founded on " + terrain.name().toLowerCase() + ".");
        }
        int seamDist = topo.distanceToSeamOrEdge(tile);
        if (seamDist < s.settings.seamExclusionTiles()) {
            return CommandResult.fail("Too close to the edge of the world (" + seamDist + " tiles; need " + s.settings.seamExclusionTiles() + ").");
        }
        for (City other : s.cities.values()) {
            int d = topo.chebyshev(tile, other.tile());
            if (d < s.settings.minCitySpacing()) {
                return CommandResult.fail("Too close to " + other.name + " (" + d + " tiles; need " + s.settings.minCitySpacing() + ").");
            }
        }
        Optional<Civilization> existing = s.civOfMember(f.issuer());
        int tileOwner = s.map.owner(tile);
        if (tileOwner != StrategicMap.NO_OWNER && (existing.isEmpty() || existing.get().id != tileOwner)) {
            return CommandResult.fail("That land belongs to " + s.civs.get(tileOwner).name + ".");
        }
        if (f.cityName() == null || f.cityName().isBlank()) return CommandResult.fail("A city needs a name.");
        if (s.cities.values().stream().anyMatch(c -> c.name.equalsIgnoreCase(f.cityName().trim()))) {
            return CommandResult.fail("There is already a city called " + f.cityName().trim() + ".");
        }

        Civilization civ = existing.orElseGet(() -> createCiv(s, f.issuer(), f.civName()));
        City city = new City();
        city.id = s.allocateId();
        city.civId = civ.id;
        city.originalCivId = civ.id;
        city.name = f.cityName();
        city.tileX = tile.x();
        city.tileZ = tile.z();
        city.blockX = f.blockX();
        city.blockY = f.blockY();
        city.blockZ = f.blockZ();
        city.foundedTurn = s.turn;
        s.cities.put(city.id, city);
        civ.cityIds.add(city.id);
        boolean capital = civ.capitalCityId < 0;
        if (capital) {
            civ.capitalCityId = city.id;
            city.buildings.add("civcore:palace");
            city.originalCapital = civ.cityIds.size() == 1;
        }
        int claimed = 0;
        for (TilePos p : topo.within(tile, FOUNDING_CLAIM_RADIUS)) {
            if (s.map.owner(p) == StrategicMap.NO_OWNER) {
                s.map.setOwner(p, civ.id);
                claimed++;
            }
        }
        return CommandResult.ok((capital ? "Founded the capital " : "Founded ") + city.name + " of " + civ.name
            + " at tile " + tile + " (" + claimed + " tiles claimed).");
    }

    private static Civilization createCiv(GameState s, String issuer, String civName) {
        int id = (int) s.allocateId();
        String name = civName == null || civName.isBlank() ? "Civilization " + id : civName;
        Civilization civ = new Civilization(id, name, PALETTE[s.civs.size() % PALETTE.length]);
        civ.members.add(issuer);
        if (issuer.startsWith("ai:")) civ.controller = "ai";
        s.civs.put(id, civ);
        return civ;
    }

    static CommandResult setResearch(GameState s, Ruleset r, SetResearch c) {
        Optional<Civilization> civ = s.civOfMember(c.issuer());
        if (civ.isEmpty()) return CommandResult.fail("You don't belong to a civilization yet. Found a city first.");
        TechDef tech = r.techs().get(c.techId());
        if (tech == null) return CommandResult.fail("Unknown technology " + c.techId() + ".");
        Civilization v = civ.get();
        if (v.knownTechs.contains(tech.id())) return CommandResult.fail("Already researched.");
        List<String> missing = tech.requires().stream().filter(t -> !v.knownTechs.contains(t)).toList();
        if (!missing.isEmpty()) return CommandResult.fail("Requires " + String.join(", ", missing) + ".");
        // Switching away from an unfinished tech loses its progress; overflow from a finished tech carries over.
        if (v.currentResearch != null && !tech.id().equals(v.currentResearch)) v.researchProgress = 0;
        v.currentResearch = tech.id();
        return CommandResult.ok("Researching " + tech.id() + " (" + tech.cost() + " science).");
    }

    static CommandResult dequeue(GameState s, dev.civcore.sim.command.Command.DequeueProduction d) {
        Optional<Civilization> civ = s.civOfMember(d.issuer());
        City city = s.cities.get(d.cityId());
        if (civ.isEmpty() || city == null || city.civId != civ.get().id) return CommandResult.fail("Not your city.");
        if (d.index() < 0 || d.index() >= city.productionQueue.size()) return CommandResult.fail("Nothing at that place in the queue.");
        String removed = city.productionQueue.remove(d.index());
        return CommandResult.ok(city.name + " will no longer build " + removed + ".");
    }

    static CommandResult queueProduction(GameState s, Ruleset r, QueueProduction q) {
        Optional<Civilization> civ = s.civOfMember(q.issuer());
        City city = s.cities.get(q.cityId());
        if (civ.isEmpty() || city == null || city.civId != civ.get().id) return CommandResult.fail("Not your city.");
        Civilization v = civ.get();
        BuildingDef b = r.buildings().get(q.itemId());
        UnitDef u = r.units().get(q.itemId());
        if (b == null && u == null) return CommandResult.fail("Unknown building or unit " + q.itemId() + ".");
        String requires = b != null ? b.requiresTech() : u.requiresTech();
        if (requires != null && !v.knownTechs.contains(requires)) return CommandResult.fail("Requires " + requires + ".");
        if (u != null && u.requiresResource() != null) {
            var res = dev.civcore.sim.worldplan.ResourceType.byId(u.requiresResource());
            if (res != null && dev.civcore.sim.rules.Resources.available(s, r, v, res) < 1) {
                return CommandResult.fail("Needs a free source of " + res.name().toLowerCase() + " inside your borders.");
            }
        }
        if (u != null && u.id().equals(Military.SETTLERS) && dev.civcore.sim.rules.Resources.happiness(s, r, v) <= dev.civcore.sim.rules.Resources.VERY_UNHAPPY) {
            return CommandResult.fail("Your people are too unhappy to found new cities.");
        }
        if (b != null && (city.buildings.contains(b.id()) || city.productionQueue.contains(b.id()))) {
            return CommandResult.fail(city.name + " already has or is building " + b.id() + ".");
        }
        city.productionQueue.add(q.itemId());
        return CommandResult.ok(city.name + " will build " + q.itemId() + ".");
    }

    // ------------------------------------------------------------------ strategic tick

    /** Advances one strategic tick. Returns human-readable events (typed DomainEvents come later). */
    public static List<String> strategicStep(GameState s, Ruleset r) {
        List<String> events = new ArrayList<>();
        if (s.finished()) return events;
        s.turn++;
        s.rulesetHash = r.hash();
        for (Civilization civ : s.civs.values()) {
            int science = 0;
            int gold = 0;
            int happiness = dev.civcore.sim.rules.Resources.happiness(s, r, civ);
            civ.happiness = happiness;
            for (long cityId : civ.cityIds) {
                City city = s.cities.get(cityId);
                if (city == null || city.civId != civ.id) continue;
                Map<String, Integer> y = buildingYields(city, r);
                TileYields.Yield tiles = TileYields.city(s, city);
                science += city.population + y.getOrDefault("science", 0);
                gold += tiles.gold() + y.getOrDefault("gold", 0);
                growCity(city, tiles.food() + y.getOrDefault("food", 0), happiness, events);
                int production = tiles.production() + y.getOrDefault("production", 0);
                if (happiness <= dev.civcore.sim.rules.Resources.VERY_UNHAPPY) production = production * 2 / 3;
                produce(s, city, r, production, events);
            }
            civ.gold += gold;
            research(civ, r, science, events);
        }
        dev.civcore.sim.rules.Culture.step(s, r, events);
        Military.step(s, r, events);
        Victory.check(s, events);
        dev.civcore.sim.view.Visibility.update(s, java.util.Map.of());
        return events;
    }

    static Map<String, Integer> buildingYields(City city, Ruleset r) {
        Map<String, Integer> total = new java.util.HashMap<>();
        for (String b : city.buildings) {
            BuildingDef def = r.buildings().get(b);
            if (def != null) def.yields().forEach((k, v) -> total.merge(k, v, Integer::sum));
        }
        return total;
    }

    /** Food needed to grow from the given population. */
    public static int growthThreshold(int population) {
        return 10 + 5 * population;
    }

    /** Each citizen eats 2 food; the surplus accumulates toward growth, a deficit starves the city. */
    public static final int FOOD_PER_CITIZEN = 2;

    private static void growCity(City city, int food, int happiness, List<String> events) {
        int surplus = food - FOOD_PER_CITIZEN * city.population;
        if (surplus > 0 && happiness < 0) surplus /= 4; // unhappy cities grow far more slowly
        city.foodStored += surplus;
        if (city.foodStored < 0) {
            if (city.population > 1) {
                city.population--;
                events.add(city.name + " is starving and shrank to population " + city.population + ".");
            }
            city.foodStored = 0;
            return;
        }
        if (city.foodStored >= growthThreshold(city.population)) {
            city.foodStored = 0;
            city.population++;
            events.add(city.name + " grew to population " + city.population + ".");
        }
    }

    private static void produce(GameState s, City city, Ruleset r, int production, List<String> events) {
        if (city.productionQueue.isEmpty()) return;
        city.productionStored += production;
        String item = city.productionQueue.get(0);
        BuildingDef b = r.buildings().get(item);
        UnitDef u = r.units().get(item);
        int cost = b != null ? b.cost() : u != null ? u.cost() : 0;
        if (cost == 0) { // ruleset changed under us: drop the item
            city.productionQueue.remove(0);
            events.add(city.name + " dropped unknown item " + item + ".");
            return;
        }
        if (city.productionStored >= cost) {
            city.productionStored -= cost;
            city.productionQueue.remove(0);
            if (b != null) city.buildings.add(b.id());
            if (u != null) Military.spawnUnit(s, city, u.id());
            events.add(city.name + " completed " + item + ".");
        }
    }

    private static void research(Civilization civ, Ruleset r, int science, List<String> events) {
        if (civ.currentResearch == null) return;
        TechDef tech = r.techs().get(civ.currentResearch);
        if (tech == null) {
            civ.currentResearch = null;
            return;
        }
        civ.researchProgress += science;
        if (civ.researchProgress >= tech.cost()) {
            civ.researchProgress -= tech.cost();
            civ.knownTechs.add(tech.id());
            civ.currentResearch = null;
            events.add(civ.name + " discovered " + tech.id() + ".");
        }
    }
}

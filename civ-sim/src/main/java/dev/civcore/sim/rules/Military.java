package dev.civcore.sim.rules;

import dev.civcore.sim.model.Army;
import dev.civcore.sim.model.City;
import dev.civcore.sim.model.Civilization;
import dev.civcore.sim.model.GameState;
import dev.civcore.sim.model.StrategicMap;
import dev.civcore.sim.ruleset.Ruleset;
import dev.civcore.sim.ruleset.Ruleset.UnitDef;
import dev.civcore.sim.topology.TilePos;
import dev.civcore.sim.topology.Topology;
import dev.civcore.sim.worldplan.TerrainType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;

/**
 * Abstract armies: movement on the tile grid, battles, sieges and capture (docs/F-game-design.md §6).
 * Deterministic: randomness comes from the game seed, turn and object ids.
 */
public final class Military {
    public static final int MAX_STACK = 10;
    public static final int BASE_CITY_STRENGTH = 8;
    public static final int HEAL_OWN_TERRITORY = 10;
    public static final int HEAL_ELSEWHERE = 5;
    public static final int CITY_HEAL = 10;
    public static final int CAPTURED_CITY_HP = 25;
    public static final String SETTLERS = "civcore:settlers";

    private Military() {
    }

    // ------------------------------------------------------------------ helpers

    public static int moves(Army a, Ruleset r) {
        int m = Integer.MAX_VALUE;
        for (Army.Unit u : a.units) {
            UnitDef d = r.units().get(u.type);
            m = Math.min(m, d == null ? 1 : Math.max(1, d.moves()));
        }
        return m == Integer.MAX_VALUE ? 1 : m;
    }

    /** Combat strength of a stack: sum of unit strength scaled by health. */
    public static double strength(Army a, Ruleset r) {
        double s = 0;
        for (Army.Unit u : a.units) {
            UnitDef d = r.units().get(u.type);
            s += (d == null ? 0 : d.strength()) * u.hp / (double) Army.FULL_HP;
        }
        return s;
    }

    public static double cityStrength(GameState s, City c, Ruleset r) {
        int walls = 0;
        for (String b : c.buildings) {
            var def = r.buildings().get(b);
            if (def != null) walls += def.yields().getOrDefault("defense", 0);
        }
        return (BASE_CITY_STRENGTH + 2 * c.population + walls) * Math.max(0.25, c.hp / (double) City.MAX_HP);
    }

    /** Defensive multiplier of a tile. */
    public static double terrainDefense(GameState s, TilePos p) {
        TerrainType t = s.map.terrain(p);
        if (t == TerrainType.HILLS || t == TerrainType.FOREST || t == TerrainType.JUNGLE) return 1.25;
        if (t == TerrainType.MARSH) return 0.85;
        return 1.0;
    }

    public static int moveCost(GameState s, TilePos p) {
        TerrainType t = s.map.terrain(p);
        if (t == null) return 1;
        return switch (t) {
            case HILLS, FOREST, JUNGLE, MARSH -> 2;
            default -> 1;
        };
    }

    /** Land units may enter: passable land, not territory of a civ we are at peace with. */
    public static boolean enterable(GameState s, int civId, TilePos p) {
        TerrainType t = s.map.terrain(p);
        if (t != null && (!t.land || !t.passable)) return false;
        int owner = s.map.owner(p);
        return owner == StrategicMap.NO_OWNER || owner == civId || s.atWar(owner, civId);
    }

    public static Optional<City> cityOn(GameState s, TilePos p) {
        return s.cities.values().stream().filter(c -> c.tileX == p.x() && c.tileZ == p.z()).findFirst();
    }

    public static List<Army> armiesOn(GameState s, TilePos p) {
        List<Army> out = new ArrayList<>();
        for (Army a : s.armies.values()) if (a.tileX == p.x() && a.tileZ == p.z()) out.add(a);
        return out;
    }

    // ------------------------------------------------------------------ pathfinding

    /**
     * A* path from the army's tile to {@code goal} (excluding the start). The goal itself may be an enemy-held tile
     * (an attack); every intermediate tile must be enterable. Empty if unreachable.
     */
    public static List<TilePos> findPath(GameState s, int civId, TilePos start, TilePos goal) {
        Topology topo = s.topology();
        if (start.equals(goal)) return List.of();
        Map<TilePos, Double> g = new HashMap<>();
        Map<TilePos, TilePos> from = new HashMap<>();
        record Node(TilePos p, double f, long seq) {
        }
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(Node::f).thenComparingLong(Node::seq));
        long seq = 0;
        g.put(start, 0.0);
        open.add(new Node(start, topo.distance(start, goal), seq++));
        Set<TilePos> closed = new HashSet<>();
        int limit = s.map.width * s.map.height;
        while (!open.isEmpty() && closed.size() < limit) {
            TilePos cur = open.poll().p();
            if (cur.equals(goal)) break;
            if (!closed.add(cur)) continue;
            for (TilePos n : topo.neighbors(cur)) {
                boolean isGoal = n.equals(goal);
                if (!isGoal && !enterable(s, civId, n)) continue;
                if (isGoal) {
                    TerrainType t = s.map.terrain(n);
                    if (t != null && (!t.land || !t.passable)) continue;
                }
                double step = moveCost(s, n) * (n.x() != cur.x() && n.z() != cur.z() ? 1.01 : 1.0);
                double ng = g.get(cur) + step;
                if (ng < g.getOrDefault(n, Double.MAX_VALUE)) {
                    g.put(n, ng);
                    from.put(n, cur);
                    open.add(new Node(n, ng + topo.distance(n, goal), seq++));
                }
            }
        }
        if (!from.containsKey(goal)) return List.of();
        List<TilePos> path = new ArrayList<>();
        for (TilePos p = goal; !p.equals(start); p = from.get(p)) path.add(0, p);
        return path;
    }

    // ------------------------------------------------------------------ production

    /** A city finished a unit: join a holding army of the same kind on the city tile, or form a new one. */
    public static Army spawnUnit(GameState s, City city, String unitType) {
        String kind = kindOf(unitType);
        for (Army a : armiesOn(s, city.tile())) {
            if (a.civId == city.civId && a.path.isEmpty() && a.units.size() < MAX_STACK && kindOf(a).equals(kind)) {
                a.units.add(newUnit(s, unitType));
                return a;
            }
        }
        Army a = new Army();
        a.id = s.allocateId();
        a.civId = city.civId;
        a.tileX = city.tileX;
        a.tileZ = city.tileZ;
        a.units.add(newUnit(s, unitType));
        s.armies.put(a.id, a);
        return a;
    }

    public static final String SCOUT = "civcore:scout";

    /** Stacks only merge with their own kind: settlers, scouts and fighting units stay apart. */
    static String kindOf(String unitType) {
        return SETTLERS.equals(unitType) ? "settlers" : SCOUT.equals(unitType) ? "scouts" : "military";
    }

    static String kindOf(Army a) {
        return a.units.isEmpty() ? "military" : kindOf(a.units.get(0).type);
    }

    public static Army.Unit newUnit(GameState s, String type) {
        Army.Unit u = new Army.Unit(type);
        u.uid = s.allocateId();
        return u;
    }

    // ------------------------------------------------------------------ strategic step

    /** Moves every army along its path, resolving battles, sieges and captures; then heals. */
    public static void step(GameState s, Ruleset r, List<String> events) {
        mergeIdleArmies(s);
        Set<Long> fought = new HashSet<>();
        Set<Long> citiesHit = new HashSet<>();
        for (Long id : new ArrayList<>(s.armies.keySet())) {
            Army a = s.armies.get(id);
            if (a == null || a.path.isEmpty()) continue;
            int movesLeft = moves(a, r);
            boolean movedAny = false;
            while (!a.path.isEmpty() && s.armies.containsKey(a.id)) {
                TilePos next = new TilePos(a.path.get(0)[0], a.path.get(0)[1]);
                int cost = moveCost(s, next);
                if (movedAny && cost > movesLeft) break;
                Outcome o = enter(s, r, a, next, events, fought, citiesHit);
                if (o == Outcome.BLOCKED) {
                    a.path.clear();
                    break;
                }
                if (o == Outcome.FOUGHT) break; // attacking ends the move
                a.path.remove(0);
                movedAny = true;
                movesLeft -= cost;
                if (movesLeft <= 0) break;
            }
        }
        heal(s, events, fought, citiesHit);
    }

    /** On peace, each side's armies standing in the other's territory return to their nearest own city. */
    public static void expelAfterPeace(GameState s, int civA, int civB) {
        for (Army a : s.armies.values()) {
            int other = a.civId == civA ? civB : a.civId == civB ? civA : -1;
            if (other < 0 || s.map.owner(a.tile()) != other) continue;
            s.cities.values().stream().filter(c -> c.civId == a.civId)
                .min(Comparator.<City>comparingDouble(c -> s.topology().distance(a.tile(), c.tile())).thenComparingLong(c -> c.id))
                .ifPresent(home -> {
                    a.moveTo(home.tile());
                    a.path.clear();
                });
        }
    }

    /** Idle armies of one civ sharing a tile (and kind: settlers vs soldiers) merge, up to MAX_STACK units. */
    static void mergeIdleArmies(GameState s) {
        Map<String, Army> keep = new HashMap<>();
        for (Long id : new ArrayList<>(s.armies.keySet())) {
            Army a = s.armies.get(id);
            if (!a.path.isEmpty() || a.physical) continue; // physical stacks keep their soldiers' identity
            String key = a.civId + "@" + a.tileX + "," + a.tileZ + kindOf(a);
            Army into = keep.get(key);
            if (into == null || into.units.size() + a.units.size() > MAX_STACK) {
                keep.put(key, a);
                continue;
            }
            into.units.addAll(a.units);
            into.generation++;
            s.armies.remove(a.id);
        }
    }

    enum Outcome { MOVED, FOUGHT, BLOCKED }

    private static Outcome enter(GameState s, Ruleset r, Army a, TilePos next, List<String> events, Set<Long> fought, Set<Long> citiesHit) {
        // Enemy armies on the tile: fight the strongest.
        List<Army> hostile = armiesOn(s, next).stream().filter(o -> o.civId != a.civId).toList();
        for (Army o : hostile) {
            if (!s.atWar(a.civId, o.civId)) return Outcome.BLOCKED;
        }
        Optional<City> city = cityOn(s, next);
        if (city.isPresent() && city.get().civId != a.civId && !s.atWar(a.civId, city.get().civId)) return Outcome.BLOCKED;
        if (!hostile.isEmpty()) {
            Army defender = hostile.stream().max(Comparator.comparingDouble(o -> strength(o, r))).orElseThrow();
            battle(s, r, a, defender, next, events);
            fought.add(a.id);
            fought.add(defender.id);
            return Outcome.FOUGHT;
        }
        if (city.isPresent() && city.get().civId != a.civId) {
            City c = city.get();
            if (strength(a, r) <= 0) return Outcome.BLOCKED; // settlers can't besiege
            if (c.hp > 0) {
                siege(s, r, a, c, events);
                fought.add(a.id);
                citiesHit.add(c.id);
                return Outcome.FOUGHT;
            }
            capture(s, a, c, events);
        }
        if (!enterable(s, a.civId, next) && city.isEmpty()) return Outcome.BLOCKED;
        a.moveTo(next);
        return Outcome.MOVED;
    }

    private static Random rng(GameState s, long a, long b) {
        return new Random(s.settings.seed() * 31 + s.turn * 1_000_003L + a * 7919 + b);
    }

    /** Damage dealt by {@code att} strength to {@code def} strength (Civ-V-like curve), 0..100. */
    static int damage(double att, double def, Random rng) {
        if (att <= 0) return 0;
        if (def <= 0) return Army.FULL_HP;
        double ratio = att / def;
        double d = 30 * Math.pow(ratio, 0.7) * (0.8 + 0.4 * rng.nextDouble());
        return (int) Math.max(1, Math.min(Army.FULL_HP, Math.round(d)));
    }

    /** Army strength including its civ's government and policy bonuses. */
    public static double effectiveStrength(GameState s, Army a, Ruleset r) {
        Civilization c = s.civs.get(a.civId);
        int pct = c == null ? 0 : Civics.modifiers(s, c).strengthPct();
        return strength(a, r) * (100 + pct) / 100.0;
    }

    private static void battle(GameState s, Ruleset r, Army att, Army def, TilePos at, List<String> events) {
        if (att.physical && def.physical) return; // both sides are soldiers in the world: they fight there
        Random rng = rng(s, att.id, def.id);
        double a = effectiveStrength(s, att, r);
        double d = effectiveStrength(s, def, r) * terrainDefense(s, at) * (cityOn(s, at).isPresent() ? 1.5 : 1.0);
        int toDef = damage(a, d, rng);
        int toAtt = damage(d, a, rng);
        applyDamage(att, toAtt);
        applyDamage(def, toDef);
        String an = civName(s, att.civId), dn = civName(s, def.civId);
        events.add(an + "'s army attacked " + dn + "'s army: dealt " + toDef + ", took " + toAtt + " damage.");
        removeIfDestroyed(s, att, events);
        removeIfDestroyed(s, def, events);
    }

    private static void siege(GameState s, Ruleset r, Army att, City c, List<String> events) {
        Random rng = rng(s, att.id, c.id);
        double a = effectiveStrength(s, att, r);
        double d = cityStrength(s, c, r);
        int toCity = damage(a, d, rng);
        int toAtt = damage(d, a, rng) / 2; // cities hit back weaker than armies
        c.hp = Math.max(0, c.hp - toCity);
        applyDamage(att, toAtt);
        events.add(civName(s, att.civId) + " besieged " + c.name + ": city hp " + c.hp + "/" + City.MAX_HP + ", army took " + toAtt + " damage.");
        removeIfDestroyed(s, att, events);
    }

    private static void capture(GameState s, Army a, City c, List<String> events) {
        Civilization oldCiv = s.civs.get(c.civId);
        Civilization newCiv = s.civs.get(a.civId);
        int oldId = c.civId;
        c.civId = a.civId;
        c.hp = CAPTURED_CITY_HP;
        c.population = Math.max(1, c.population - 1);
        c.productionQueue.clear();
        c.productionStored = 0;
        if (oldCiv != null) {
            oldCiv.cityIds.remove(c.id);
            if (oldCiv.capitalCityId == c.id) {
                oldCiv.capitalCityId = oldCiv.cityIds.isEmpty() ? -1 : oldCiv.cityIds.get(0);
            }
        }
        newCiv.cityIds.add(c.id);
        Culture.transferOnCapture(s, c, oldId);
        events.add(newCiv.name + " captured " + c.name + (oldCiv == null ? "" : " from " + oldCiv.name) + "!");
    }

    private static void applyDamage(Army a, int dmg) {
        if (dmg <= 0) return;
        for (Army.Unit u : a.units) u.hp -= dmg;
        a.units.removeIf(u -> u.hp <= 0);
        a.revision++;
    }

    /** A projected soldier died in the world: remove its unit. Returns the army if it was destroyed. */
    public static Optional<Army> unitKilled(GameState s, long armyId, long uid, List<String> events) {
        Army a = s.armies.get(armyId);
        if (a == null) return Optional.empty();
        a.units.removeIf(u -> u.uid == uid);
        if (a.units.isEmpty()) {
            s.armies.remove(a.id);
            events.add(civName(s, a.civId) + "'s army was destroyed in battle.");
            return Optional.of(a);
        }
        return Optional.empty();
    }

    private static void removeIfDestroyed(GameState s, Army a, List<String> events) {
        if (a.units.isEmpty() && s.armies.remove(a.id) != null) {
            events.add(civName(s, a.civId) + "'s army was destroyed.");
        }
    }

    /** Armies farther than this from their own cities, outside their borders, are out of supply. */
    public static final int SUPPLY_RANGE = 6;
    public static final int ATTRITION = 5;

    public static boolean inSupply(GameState s, Army a) {
        if (s.map.owner(a.tile()) == a.civId) return true;
        for (City c : s.cities.values()) {
            if (c.civId == a.civId && s.topology().chebyshev(c.tile(), a.tile()) <= SUPPLY_RANGE) return true;
        }
        return false;
    }

    private static void heal(GameState s, List<String> events, Set<Long> fought, Set<Long> citiesHit) {
        for (Army a : List.copyOf(s.armies.values())) {
            if (fought.contains(a.id) || a.physical) continue;
            if (!inSupply(s, a)) {
                for (Army.Unit u : a.units) u.hp -= ATTRITION;
                a.units.removeIf(u -> u.hp <= 0);
                a.revision++;
                if (a.units.isEmpty()) {
                    s.armies.remove(a.id);
                    events.add(civName(s, a.civId) + "'s army starved far from home.");
                }
                continue;
            }
            int amount = s.map.owner(a.tile()) == a.civId ? HEAL_OWN_TERRITORY : HEAL_ELSEWHERE;
            for (Army.Unit u : a.units) u.hp = Math.min(Army.FULL_HP, u.hp + amount);
        }
        for (City c : s.cities.values()) {
            if (!citiesHit.contains(c.id)) c.hp = Math.min(City.MAX_HP, c.hp + CITY_HEAL);
        }
    }

    static String civName(GameState s, int civId) {
        Civilization c = s.civs.get(civId);
        return c == null ? "civ " + civId : c.name;
    }
}

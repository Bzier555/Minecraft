package dev.civcore.sim.ai;

import dev.civcore.sim.command.Command;
import dev.civcore.sim.model.Army;
import dev.civcore.sim.model.City;
import dev.civcore.sim.model.Civilization;
import dev.civcore.sim.model.GameState;
import dev.civcore.sim.model.StrategicMap;
import dev.civcore.sim.rules.Military;
import dev.civcore.sim.rules.TileYields;
import dev.civcore.sim.ruleset.Ruleset;
import dev.civcore.sim.ruleset.Ruleset.BuildingDef;
import dev.civcore.sim.ruleset.Ruleset.TechDef;
import dev.civcore.sim.ruleset.Ruleset.UnitDef;
import dev.civcore.sim.topology.TilePos;
import dev.civcore.sim.topology.Topology;
import dev.civcore.sim.worldplan.TerrainType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic rule-and-utility AI, v0 (backlog F1.12). Produces the same {@link Command}s a player would; it has no
 * special access to the rules. Limitation for now: it reads the whole game state (no fog of war until F1.15).
 *
 * <p>Each strategic tick: pick research, fill empty production queues, move settlers to good sites and found cities,
 * decide on war and peace, send armies to attack or defend.
 */
public final class AiController {

    /** Tunable behaviour. */
    public record Personality(double aggression, int targetCities, int earliestWarTurn, int attackStackSize) {
        public static final Personality DEFAULT = new Personality(1.0, 4, 40, 3);

        /** Deterministic per-civ variation so identical AIs don't mirror each other: aggression 0.8..1.4. */
        public static Personality of(GameState s, Civilization c) {
            long h = (c.id * 0x9E3779B97F4A7C15L) ^ s.settings.seed();
            h ^= (h >>> 29);
            double aggression = 0.8 + Math.floorMod(h, 7) / 10.0;
            return new Personality(aggression, 4, (int) (60 / aggression), 3);
        }
    }

    public static final String ISSUER_PREFIX = "ai:";
    public static final int SITE_SEARCH_RADIUS = 8;
    public static final int THREAT_RADIUS = 3;

    private static final List<String> CITY_NAMES = List.of("Alba", "Brisa", "Corvo", "Delta", "Esker", "Fenn", "Garra",
        "Holm", "Isca", "Jura", "Kaur", "Lumo", "Mora", "Nesk", "Orla", "Pyre", "Quel", "Rhun", "Sela", "Tarn", "Ulme",
        "Vask", "Wren", "Yara", "Zell");

    private AiController() {
    }

    public static boolean isAi(Civilization c) {
        return "ai".equals(c.controller);
    }

    public static String issuer(Civilization c) {
        return c.members.isEmpty() ? ISSUER_PREFIX + c.id : c.members.get(0);
    }

    public static List<Command> decide(GameState s, Ruleset r, Civilization civ, Personality p) {
        List<Command> out = new ArrayList<>();
        if (civ.eliminated || s.finished()) return out;
        String me = issuer(civ);
        research(s, r, civ, me, p, out);
        diplomacy(s, r, civ, me, p, out);
        production(s, r, civ, me, p, out);
        settlers(s, r, civ, me, out);
        // Civs we are making peace with this turn: their land closes once the commands apply, so don't attack them.
        java.util.Set<Integer> peaceNow = new java.util.HashSet<>();
        for (Command c : out) {
            if (c instanceof Command.ProposePeace pp && s.peaceOffers.contains(pp.targetCivId() + ">" + civ.id)) peaceNow.add(pp.targetCivId());
        }
        armies(s, r, civ, me, p, out, peaceNow);
        return out;
    }

    // ------------------------------------------------------------------ research

    static void research(GameState s, Ruleset r, Civilization civ, String me, Personality p, List<Command> out) {
        if (civ.currentResearch != null) return;
        r.techs().values().stream()
            .filter(t -> !civ.knownTechs.contains(t.id()) && civ.knownTechs.containsAll(t.requires()))
            .max(Comparator.<TechDef>comparingDouble(t -> techValue(t, p)).thenComparing(TechDef::id, Comparator.reverseOrder()))
            .ifPresent(t -> out.add(new Command.SetResearch(me, t.id())));
    }

    static double techValue(TechDef t, Personality p) {
        double v = 100.0 / t.cost();
        v += 1.5 * t.unlocks().buildings().size();
        v += 1.5 * p.aggression() * t.unlocks().units().size();
        return v;
    }

    // ------------------------------------------------------------------ war and peace

    static double militaryStrength(GameState s, Ruleset r, int civId) {
        double sum = 0;
        for (Army a : s.armies.values()) if (a.civId == civId) sum += Military.strength(a, r);
        return sum;
    }

    static double defenceStrength(GameState s, Ruleset r, int civId) {
        double sum = militaryStrength(s, r, civId);
        for (City c : s.cities.values()) if (c.civId == civId) sum += Military.cityStrength(s, c, r);
        return sum;
    }

    static Optional<City> nearestEnemyCity(GameState s, Civilization civ, TilePos from, boolean requireWar) {
        return s.cities.values().stream()
            .filter(c -> c.civId != civ.id && (!requireWar || s.atWar(civ.id, c.civId)))
            .min(Comparator.<City>comparingDouble(c -> s.topology().distance(from, c.tile())).thenComparingLong(c -> c.id));
    }

    static void diplomacy(GameState s, Ruleset r, Civilization civ, String me, Personality p, List<Command> out) {
        City capital = s.cities.get(civ.capitalCityId);
        if (capital == null) return;
        double mine = militaryStrength(s, r, civ.id);
        boolean atWar = false;
        for (Civilization other : s.civs.values()) {
            if (other.id == civ.id || other.eliminated || !s.atWar(civ.id, other.id)) continue;
            atWar = true;
            double theirs = militaryStrength(s, r, other.id);
            boolean theyOffered = s.peaceOffers.contains(other.id + ">" + civ.id);
            if (mine < 0.5 * theirs || (theyOffered && mine <= theirs)) out.add(new Command.ProposePeace(me, other.id));
        }
        if (atWar || s.turn < p.earliestWarTurn()) return;
        // Consider attacking the nearest reachable rival that is clearly weaker.
        Optional<City> target = nearestEnemyCity(s, civ, capital.tile(), false);
        if (target.isEmpty()) return;
        int targetCiv = target.get().civId;
        if (s.warCooldown(civ.id, targetCiv) > 0) return;
        double theirDefence = defenceStrength(s, r, targetCiv);
        double theirArmy = militaryStrength(s, r, targetCiv);
        // Attack when our army clearly outweighs theirs; cities defend themselves, so demand more if theirs are strong.
        boolean strongEnough = mine >= Math.max(1.2 * theirArmy, 0.5 * theirDefence) / p.aggression();
        if (strongEnough && reachableIgnoringBorders(s, civ.id, capital.tile(), target.get().tile())) {
            out.add(new Command.DeclareWar(me, targetCiv));
        }
    }

    /** Path over land, treating every border as open (as it would be once at war). */
    static boolean reachableIgnoringBorders(GameState s, int civId, TilePos from, TilePos to) {
        Topology topo = s.topology();
        java.util.ArrayDeque<TilePos> q = new java.util.ArrayDeque<>();
        java.util.Set<TilePos> seen = new java.util.HashSet<>();
        q.add(from);
        seen.add(from);
        while (!q.isEmpty()) {
            TilePos c = q.poll();
            if (c.equals(to)) return true;
            for (TilePos n : topo.neighbors(c)) {
                TerrainType t = s.map.terrain(n);
                if ((t != null && (!t.land || !t.passable)) || !seen.add(n)) continue;
                q.add(n);
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ production

    static boolean available(Civilization civ, String requiresTech) {
        return requiresTech == null || civ.knownTechs.contains(requiresTech);
    }

    static Optional<UnitDef> bestMilitaryUnit(Ruleset r, Civilization civ) {
        return r.units().values().stream()
            .filter(u -> u.strength() > 1 && available(civ, u.requiresTech()))
            .max(Comparator.<UnitDef>comparingInt(UnitDef::strength).thenComparing(UnitDef::id));
    }

    static boolean threatened(GameState s, City c) {
        for (Army a : s.armies.values()) {
            if (a.civId != c.civId && s.atWar(a.civId, c.civId) && s.topology().chebyshev(a.tile(), c.tile()) <= THREAT_RADIUS) return true;
        }
        return false;
    }

    static int garrisonUnits(GameState s, City c) {
        int n = 0;
        for (Army a : Military.armiesOn(s, c.tile())) if (a.civId == c.civId && !a.hasUnit(Military.SETTLERS)) n += a.units.size();
        return n;
    }

    static void production(GameState s, Ruleset r, Civilization civ, String me, Personality p, List<Command> out) {
        long cities = s.cities.values().stream().filter(c -> c.civId == civ.id).count();
        long settlersPending = s.armies.values().stream().filter(a -> a.civId == civ.id && a.hasUnit(Military.SETTLERS)).count()
            + s.cities.values().stream().filter(c -> c.civId == civ.id && c.productionQueue.contains(Military.SETTLERS)).count();
        long militaryUnits = s.armies.values().stream().filter(a -> a.civId == civ.id && !a.hasUnit(Military.SETTLERS))
            .mapToLong(a -> a.units.size()).sum();
        boolean atWar = s.civs.values().stream().anyMatch(o -> s.atWar(civ.id, o.id));
        Optional<UnitDef> unit = bestMilitaryUnit(r, civ);

        for (City c : s.cities.values()) {
            if (c.civId != civ.id || !c.productionQueue.isEmpty()) continue;
            String pick = null;
            if (unit.isPresent() && (threatened(s, c) || garrisonUnits(s, c) == 0)) {
                pick = unit.get().id();
            } else if (cities + settlersPending < p.targetCities() && c.population >= 2 && settlersPending == 0) {
                pick = Military.SETTLERS;
                settlersPending++;
            } else if (unit.isPresent() && militaryUnits < (atWar ? 4 : 2) * cities * p.aggression()) {
                pick = unit.get().id();
                militaryUnits++;
            } else {
                pick = bestBuilding(r, civ, c, atWar).map(BuildingDef::id).orElse(unit.map(UnitDef::id).orElse(null));
            }
            if (pick != null) out.add(new Command.QueueProduction(me, c.id, pick));
        }
    }

    static Optional<BuildingDef> bestBuilding(Ruleset r, Civilization civ, City c, boolean atWar) {
        return r.buildings().values().stream()
            .filter(b -> !c.buildings.contains(b.id()) && available(civ, b.requiresTech()) && !b.id().equals("civcore:palace"))
            .max(Comparator.<BuildingDef>comparingDouble(b -> buildingValue(b, atWar)).thenComparing(BuildingDef::id));
    }

    static double buildingValue(BuildingDef b, boolean atWar) {
        var y = b.yields();
        double v = 3.0 * y.getOrDefault("food", 0) + 2.0 * y.getOrDefault("production", 0) + 2.0 * y.getOrDefault("science", 0)
            + 1.5 * y.getOrDefault("gold", 0) + 1.0 * y.getOrDefault("culture", 0) + (atWar ? 1.0 : 0.2) * y.getOrDefault("defense", 0);
        return v * 40.0 / b.cost();
    }

    // ------------------------------------------------------------------ settlers

    /** Score of a city site for this civ, or -1 if founding there is not allowed. */
    static int siteScore(GameState s, Civilization civ, TilePos p) {
        Topology topo = s.topology();
        TerrainType t = s.map.terrain(p);
        if (t != null && !t.settleable()) return -1;
        if (topo.distanceToSeamOrEdge(p) < s.settings.seamExclusionTiles()) return -1;
        int owner = s.map.owner(p);
        if (owner != StrategicMap.NO_OWNER && owner != civ.id) return -1;
        for (City c : s.cities.values()) if (topo.chebyshev(p, c.tile()) < s.settings.minCitySpacing()) return -1;
        int score = 0;
        for (TilePos n : topo.within(p, TileYields.WORK_RADIUS)) {
            TerrainType nt = s.map.terrain(n);
            if (nt != null && !nt.passable) continue;
            TileYields.Yield y = TileYields.of(s, n);
            score += 3 * y.food() + 2 * y.production() + y.gold();
        }
        return score;
    }

    static void settlers(GameState s, Ruleset r, Civilization civ, String me, List<Command> out) {
        for (Army a : s.armies.values()) {
            if (a.civId != civ.id || !a.hasUnit(Military.SETTLERS) || !a.path.isEmpty()) continue;
            int here = siteScore(s, civ, a.tile());
            TilePos best = null;
            double bestValue = Double.NEGATIVE_INFINITY;
            for (TilePos p : s.topology().within(a.tile(), SITE_SEARCH_RADIUS)) {
                int sc = siteScore(s, civ, p);
                if (sc < 0) continue;
                double value = sc - 1.5 * s.topology().distance(a.tile(), p);
                if (value > bestValue || (value == bestValue && best != null && (p.z() < best.z() || (p.z() == best.z() && p.x() < best.x())))) {
                    if (!p.equals(a.tile()) && Military.findPath(s, civ.id, a.tile(), p).isEmpty()) continue;
                    bestValue = value;
                    best = p;
                }
            }
            if (best == null) continue;
            if (best.equals(a.tile()) || (here >= 0 && here - 0.0 >= bestValue)) {
                out.add(new Command.SettleWithArmy(me, a.id, nextCityName(s, civ)));
            } else {
                out.add(new Command.MoveArmy(me, a.id, best.x(), best.z()));
            }
        }
    }

    /**
     * Best tile for a new AI civilization's capital: a valid site as far as possible from every existing city,
     * preferring better land. Empty if the map has no valid site left.
     */
    public static Optional<TilePos> newCivStart(GameState s) {
        Civilization nobody = new Civilization(-1, "", 0);
        TilePos best = null;
        double bestValue = -1;
        for (int z = 0; z < s.map.height; z++) {
            for (int x = 0; x < s.map.width; x++) {
                TilePos p = new TilePos(x, z);
                int score = siteScore(s, nobody, p);
                TerrainType t = s.map.terrain(p);
                if (score < 0 || t == null) continue;
                double far = s.cities.isEmpty() ? 10 : s.cities.values().stream()
                    .mapToDouble(c -> s.topology().distance(p, c.tile())).min().orElse(10);
                double value = Math.min(far, 12) * 10 + score;
                if (value > bestValue) {
                    bestValue = value;
                    best = p;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    static String nextCityName(GameState s, Civilization civ) {
        java.util.Set<String> used = new java.util.HashSet<>();
        for (City c : s.cities.values()) used.add(c.name.toLowerCase());
        int start = civ.id % CITY_NAMES.size();
        for (int i = 0; i < CITY_NAMES.size(); i++) {
            String n = CITY_NAMES.get((start + i) % CITY_NAMES.size());
            if (!used.contains(n.toLowerCase())) return n;
        }
        for (int i = 2; ; i++) {
            String n = CITY_NAMES.get(start) + " " + i;
            if (!used.contains(n.toLowerCase())) return n;
        }
    }

    // ------------------------------------------------------------------ armies

    static boolean canReach(GameState s, Army a, TilePos goal) {
        return a.tile().equals(goal) || !Military.findPath(s, a.civId, a.tile(), goal).isEmpty();
    }

    /** Enemy army strength on a city's tile plus the city's own defence. */
    static double targetDefence(GameState s, Ruleset r, City c) {
        double d = Military.cityStrength(s, c, r);
        for (Army o : Military.armiesOn(s, c.tile())) if (o.civId == c.civId) d += Military.strength(o, r) * 1.5;
        return d;
    }

    /** The reachable enemy city that is cheapest to take, weighing defence and distance. */
    static Optional<City> bestTarget(GameState s, Ruleset r, Civilization civ, Army a, java.util.Set<Integer> peaceNow) {
        City best = null;
        double bestCost = Double.MAX_VALUE;
        for (City c : s.cities.values()) {
            if (!s.atWar(civ.id, c.civId) || peaceNow.contains(c.civId)) continue;
            double cost = targetDefence(s, r, c) + 2.0 * s.topology().distance(a.tile(), c.tile());
            if (cost < bestCost && !Military.findPath(s, civ.id, a.tile(), c.tile()).isEmpty()) {
                bestCost = cost;
                best = c;
            }
        }
        return Optional.ofNullable(best);
    }

    static void armies(GameState s, Ruleset r, Civilization civ, String me, Personality p, List<Command> out, java.util.Set<Integer> peaceNow) {
        List<City> myCities = s.cities.values().stream().filter(c -> c.civId == civ.id).toList();
        if (myCities.isEmpty()) return;
        boolean atWar = s.civs.values().stream().anyMatch(o -> s.atWar(civ.id, o.id) && !peaceNow.contains(o.id));
        for (Army a : List.copyOf(s.armies.values())) {
            if (a.civId != civ.id || a.hasUnit(Military.SETTLERS) || !a.path.isEmpty()) continue;
            Optional<City> onCity = myCities.stream().filter(c -> c.tile().equals(a.tile())).findFirst();
            // Defend: a threatened city nearby pulls idle armies home.
            Optional<City> threatened = myCities.stream().filter(c -> threatened(s, c))
                .min(Comparator.comparingDouble(c -> s.topology().distance(a.tile(), c.tile())));
            if (threatened.isPresent() && !threatened.get().tile().equals(a.tile()) && canReach(s, a, threatened.get().tile())) {
                out.add(new Command.MoveArmy(me, a.id, threatened.get().tileX, threatened.get().tileZ));
                continue;
            }
            if (atWar) {
                Optional<City> target = bestTarget(s, r, civ, a, peaceNow);
                if (target.isPresent()) {
                    double needed = 1.2 * targetDefence(s, r, target.get());
                    if (a.units.size() >= p.attackStackSize() && Military.strength(a, r) >= needed) {
                        out.add(new Command.MoveArmy(me, a.id, target.get().tileX, target.get().tileZ));
                        continue;
                    }
                    // Too weak alone: gather at our city closest to the target so armies merge into a stack.
                    City staging = myCities.stream().min(Comparator.<City>comparingDouble(c -> s.topology().distance(c.tile(), target.get().tile()))
                        .thenComparingLong(c -> c.id)).orElseThrow();
                    if (!staging.tile().equals(a.tile()) && !(onCity.isPresent() && threatened(s, onCity.get())) && canReach(s, a, staging.tile())) {
                        out.add(new Command.MoveArmy(me, a.id, staging.tileX, staging.tileZ));
                    }
                    continue;
                }
            }
            if (onCity.isEmpty()) {
                myCities.stream().filter(c -> canReach(s, a, c.tile()))
                    .min(Comparator.<City>comparingDouble(c -> s.topology().distance(a.tile(), c.tile())).thenComparingLong(c -> c.id))
                    .ifPresent(home -> out.add(new Command.MoveArmy(me, a.id, home.tileX, home.tileZ)));
            }
        }
    }
}

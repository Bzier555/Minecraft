package dev.civcore.sim.model;

import dev.civcore.sim.topology.Topology;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** The whole strategic game. Single source of truth on the server (docs/D-architecture.md §1). */
public final class GameState {
    public static final int SCHEMA_VERSION = 1;

    public int schemaVersion = SCHEMA_VERSION;
    /** Unique per game (set by the server at /civ newgame); not used by rules. */
    public String gameId = "";
    public GameSettings settings;
    /** Strategic ticks elapsed ("turns"). */
    public long turn;
    public long nextId = 1;
    /** Hash of the ruleset this game was last stepped with; a mismatch on load is reported. */
    public String rulesetHash = "";
    public Map<Integer, Civilization> civs = new LinkedHashMap<>();
    public Map<Long, City> cities = new LinkedHashMap<>();
    public Map<Long, Army> armies = new LinkedHashMap<>();
    /** Fog of war: tiles each civ has ever seen (row-major), see dev.civcore.sim.view.Visibility. */
    public Map<Integer, boolean[]> explored = new java.util.TreeMap<>();
    /** Pairs at war, as "lowId:highId". */
    public java.util.Set<String> wars = new java.util.TreeSet<>();
    /** Outstanding peace offers, as "fromId>toId". */
    public java.util.Set<String> peaceOffers = new java.util.TreeSet<>();
    /** Turn each pair last made peace, keyed like {@link #wars}; war can't be re-declared for a while. */
    public Map<String, Long> peaceTurns = new java.util.TreeMap<>();
    public static final int WAR_COOLDOWN_TURNS = 20;
    /** Turn on which the game ends by score (0 = no limit). */
    public long turnLimit;
    /** Winner once the game is over, else -1. */
    public int winnerCivId = -1;
    public String victoryType = "";
    /** Victory types in play: domination, science, culture, score. */
    public java.util.Set<String> victoryTypes = new java.util.TreeSet<>(java.util.List.of("domination", "science", "culture", "score"));
    /** Culture needed for a culture victory; 0 = automatic (scaled by map size). */
    public int cultureVictoryCulture;
    /** Human civs with no member online have their cities run by the AI governor (no war decisions). */
    public boolean offlineGovernor = true;
    public StrategicMap map;

    private transient Topology topology;

    public GameState() {
    }

    public static GameState newGame(GameSettings settings) {
        GameState s = new GameState();
        s.settings = settings;
        s.map = new StrategicMap(settings.widthTiles(), settings.heightTiles());
        return s;
    }

    public Topology topology() {
        if (topology == null) topology = settings.topologyShape();
        return topology;
    }

    public long allocateId() {
        return nextId++;
    }

    public static String warKey(int a, int b) {
        return Math.min(a, b) + ":" + Math.max(a, b);
    }

    public boolean atWar(int a, int b) {
        return a != b && wars.contains(warKey(a, b));
    }

    /** Turns until these two may declare war again (0 = allowed). */
    public long warCooldown(int a, int b) {
        Long t = peaceTurns.get(warKey(a, b));
        return t == null ? 0 : Math.max(0, t + WAR_COOLDOWN_TURNS - turn);
    }

    public boolean finished() {
        return winnerCivId >= 0;
    }

    public Optional<Civilization> civOfMember(String playerId) {
        return civs.values().stream().filter(c -> c.members.contains(playerId)).findFirst();
    }
}

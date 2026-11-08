package dev.civcore.mod.game;

import dev.civcore.mod.CivCoreMod;
import dev.civcore.mod.data.RulesetReloadListener;
import dev.civcore.mod.persist.CivPersistence;
import dev.civcore.sim.command.Command;
import dev.civcore.sim.command.CommandResult;
import dev.civcore.sim.engine.Simulation;
import dev.civcore.sim.engine.StrategicClock;
import dev.civcore.sim.model.GameSettings;
import dev.civcore.sim.model.GameState;
import dev.civcore.sim.worldplan.WorldAnalysis;
import dev.civcore.sim.worldplan.TerrainClassifier;
import dev.civcore.sim.topology.TilePos;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import dev.civcore.mod.world.MinecraftTerrainSampler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Owns the strategic game on the server thread: loading, ticking, command application and saving.
 * There is at most one game per server; it exists once an operator runs /civ newgame.
 */
public final class ServerGame {
    /** Server ticks are counted as 50 ms of game time, so a lagging server slows the game rather than skipping. */
    private static final long MILLIS_PER_SERVER_TICK = 50;

    private static ServerGame instance;

    private final MinecraftServer server;
    private final CivPersistence persistence;
    private final StrategicClock clock = new StrategicClock(60_000);
    private GameState state; // null until /civ newgame
    private final dev.civcore.mod.army.ArmyMarkers markers = new dev.civcore.mod.army.ArmyMarkers();
    private int tickCounter;
    private boolean pauseWhenEmpty = true;
    /**
     * World analysis runs on background threads: sampling the generator costs ~100-200 ms per tile with Tectonic
     * (measured on the spike server), far too slow for the server thread. Results are applied on the server thread.
     */
    private CompletableFuture<TerrainClassifier.TileTerrain[]> analysis;
    private GameState analysing;
    private final AtomicInteger analysedTiles = new AtomicInteger();
    private static final int ANALYSIS_THREADS = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));

    private ServerGame(MinecraftServer server) {
        this.server = server;
        this.persistence = new CivPersistence(server.getWorldPath(LevelResource.ROOT).resolve("civcore"));
    }

    public static void start(MinecraftServer server) {
        ServerGame g = new ServerGame(server);
        try {
            g.persistence.load().ifPresent(s -> {
                g.adopt(s);
                CivCoreMod.LOG.info("Civ game loaded: turn {}, {} civs, {} cities", s.turn, s.civs.size(), s.cities.size());
                String now = RulesetReloadListener.current().hash();
                if (!s.rulesetHash.isEmpty() && !s.rulesetHash.equals(now)) {
                    CivCoreMod.LOG.warn("Civ ruleset changed since last save ({} -> {})", s.rulesetHash, now);
                }
            });
        } catch (IOException | RuntimeException e) {
            // Refuse to run on a save we cannot read rather than silently starting a new game over it.
            CivCoreMod.LOG.error("Could not load {}; Civ Core is disabled for this session. Fix or remove the file.", g.persistence.file(), e);
            return;
        }
        instance = g;
    }

    public static void stop() {
        if (instance != null) instance.save();
        instance = null;
    }

    public static Optional<ServerGame> current() {
        return Optional.ofNullable(instance);
    }

    private void adopt(GameState s) {
        this.state = s;
        clock.setPeriodMillis(s.settings.strategicTickSeconds() * 1000L);
        if (!s.map.analysed) startAnalysis(s);
    }

    private void startAnalysis(GameState s) {
        var sampler = new MinecraftTerrainSampler(server.overworld());
        var mapping = s.settings.mapping();
        int width = s.map.width, total = s.map.width * s.map.height;
        analysedTiles.set(0);
        analysing = s;
        long started = System.nanoTime();
        ForkJoinPool pool = new ForkJoinPool(ANALYSIS_THREADS);
        analysis = CompletableFuture.supplyAsync(() -> {
            TerrainClassifier.TileTerrain[] out = new TerrainClassifier.TileTerrain[total];
            var level = server.overworld();
            IntStream.range(0, total).parallel().forEach(i -> dev.civcore.mod.world.ExoticWorldsCompat.withFold(level, () -> {
                out[i] = WorldAnalysis.classifyTile(mapping, sampler, new TilePos(i % width, i / width));
                analysedTiles.incrementAndGet();
            }));
            return out;
        }, pool).whenComplete((r, e) -> pool.shutdown());
        CivCoreMod.LOG.info("Civ world analysis started: {} tiles on {} threads", total, ANALYSIS_THREADS);
        analysis.thenRun(() -> CivCoreMod.LOG.info("Civ world analysis sampled {} tiles in {} s", total, (System.nanoTime() - started) / 1_000_000_000));
    }

    /** Analysis progress as "done/total" tiles, or empty when finished. */
    public Optional<String> analysisProgress() {
        return analysis == null ? Optional.empty() : Optional.of(analysedTiles.get() + "/" + (state.map.width * state.map.height));
    }

    private void pollAnalysis() {
        if (!analysis.isDone()) return;
        var finished = analysis;
        GameState target = analysing;
        analysis = null;
        analysing = null;
        if (target != state) return; // a new game replaced this one meanwhile
        try {
            TerrainClassifier.TileTerrain[] tiles = finished.join();
            for (int i = 0; i < tiles.length; i++) {
                state.map.setTerrain(new TilePos(i % state.map.width, i / state.map.width), tiles[i].type(), tiles[i].river());
            }
            WorldAnalysis.finish(state);
        } catch (RuntimeException e) {
            CivCoreMod.LOG.error("Civ world analysis failed; it will be retried on the next server start", e);
            return;
        }
        int land = 0;
        for (int t : state.map.terrain) if (t >= 0 && dev.civcore.sim.worldplan.TerrainType.values()[t].land) land++;
        server.getPlayerList().broadcastSystemMessage(Component.literal("[Civ] World analysed: " + land + " of "
            + state.map.terrain.length + " tiles are land."), false);
        CivCoreMod.LOG.info("Civ world analysis finished ({} land tiles of {})", land, state.map.terrain.length);
        save();
    }

    public Optional<GameState> state() {
        return Optional.ofNullable(state);
    }

    public StrategicClock clock() {
        return clock;
    }

    public void setPauseWhenEmpty(boolean v) {
        this.pauseWhenEmpty = v;
    }

    public boolean pauseWhenEmpty() {
        return pauseWhenEmpty;
    }

    public CivPersistence persistence() {
        return persistence;
    }

    /** Starts a new game, replacing any existing one (the old save is kept as a backup by the next save). */
    public void newGame(GameSettings settings) {
        GameState s = GameState.newGame(settings);
        s.gameId = java.util.UUID.randomUUID().toString();
        adopt(s);
        save();
    }

    public CommandResult submit(Command cmd) {
        if (state == null) return CommandResult.fail("No game is running. An operator must run /civ newgame.");
        return Simulation.apply(state, RulesetReloadListener.current(), cmd);
    }

    public void tick() {
        if (state == null) return;
        if (analysis != null) pollAnalysis();
        if (++tickCounter % 20 == 0) syncWorld();
        boolean empty = server.getPlayerList().getPlayerCount() == 0;
        if (pauseWhenEmpty && empty) return;
        int due = clock.advance(MILLIS_PER_SERVER_TICK);
        for (int i = 0; i < due; i++) step();
    }

    /** Runs one strategic tick now and announces its events. */
    public void step() {
        if (state == null) return;
        for (String rejected : dev.civcore.sim.ai.AiTurn.run(state, RulesetReloadListener.current())) {
            CivCoreMod.LOG.debug("AI command rejected: {}", rejected);
        }
        List<String> events = Simulation.strategicStep(state, RulesetReloadListener.current());
        for (String ev : events) {
            server.getPlayerList().broadcastSystemMessage(Component.literal("[Civ] " + ev), false);
        }
        syncWorld();
    }

    /** Tiles where each civ's online members stand (they extend that civ's vision). */
    private java.util.Map<Integer, List<TilePos>> memberTiles() {
        java.util.Map<Integer, List<TilePos>> out = new java.util.HashMap<>();
        var m = state.settings.mapping();
        for (var p : server.getPlayerList().getPlayers()) {
            if (p.level().dimension() != net.minecraft.world.level.Level.OVERWORLD) continue;
            state.civOfMember(p.getStringUUID()).ifPresent(c -> {
                TilePos t = state.topology().normalizeOrNull(m.tileX(p.getBlockX()), m.tileZ(p.getBlockZ()));
                if (t != null) out.computeIfAbsent(c.id, k -> new java.util.ArrayList<>()).add(t);
            });
        }
        return out;
    }

    /** The fog-of-war-filtered view for one player, as JSON. */
    public Optional<String> viewFor(net.minecraft.server.level.ServerPlayer player) {
        if (state == null) return Optional.empty();
        int civ = state.civOfMember(player.getStringUUID()).map(c -> c.id).orElse(-1);
        var tiles = memberTiles().getOrDefault(civ, List.of());
        var view = dev.civcore.sim.view.ViewBuilder.build(state, RulesetReloadListener.current(), civ, tiles, state.finished(),
            clock.millisUntilNext() / 1000, clock.paused());
        return Optional.of(dev.civcore.sim.view.ViewBuilder.toJson(view));
    }

    public String defaultCityName(String playerId) {
        return state == null ? "City" : state.civOfMember(playerId)
            .map(c -> dev.civcore.sim.ai.AiController.nextCityName(state, c)).orElse("City " + (state.cities.size() + 1));
    }

    /** Brings the physical world in line with the strategic state: fog of war, pending City Centers, army markers. */
    public void syncWorld() {
        if (state == null) return;
        dev.civcore.sim.view.Visibility.update(state, memberTiles());
        var level = server.overworld();
        dev.civcore.mod.army.ArmyMarkers.placePendingCityCenters(level, state);
        markers.sync(level, state);
    }

    public void save() {
        if (state == null) return;
        try {
            persistence.save(state);
        } catch (IOException e) {
            CivCoreMod.LOG.error("Failed to save the Civ game to {}", persistence.file(), e);
        }
    }
}

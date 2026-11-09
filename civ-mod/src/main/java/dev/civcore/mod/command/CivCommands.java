package dev.civcore.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.civcore.mod.data.RulesetReloadListener;
import dev.civcore.mod.game.ServerGame;
import dev.civcore.sim.command.Command;
import dev.civcore.sim.command.CommandResult;
import dev.civcore.sim.model.City;
import dev.civcore.sim.model.Civilization;
import dev.civcore.sim.model.GameSettings;
import dev.civcore.sim.model.GameState;
import dev.civcore.sim.ruleset.Ruleset;
import dev.civcore.sim.topology.TilePos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/** /civ commands. A temporary text UI until the strategic map and city screens exist (backlog F1.14). */
public final class CivCommands {
    private CivCommands() {
    }

    private static final SuggestionProvider<CommandSourceStack> TECHS = (ctx, b) ->
        SharedSuggestionProvider.suggestResource(RulesetReloadListener.current().techs().keySet().stream().map(ResourceLocation::parse), b);
    private static final SuggestionProvider<CommandSourceStack> ITEMS = (ctx, b) ->
        SharedSuggestionProvider.suggestResource(Stream.concat(RulesetReloadListener.current().buildings().keySet().stream(),
            RulesetReloadListener.current().units().keySet().stream()).map(ResourceLocation::parse), b);

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("civ")
            .then(Commands.literal("status").executes(CivCommands::status))
            .then(Commands.literal("techs").executes(CivCommands::techs))
            .then(Commands.literal("tile").executes(CivCommands::tile))
            .then(Commands.literal("map").executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                return game(ctx).map(g -> {
                    dev.civcore.mod.net.CivNetwork.sendView(g, p);
                    dev.civcore.mod.net.CivNetwork.open(p, "map", 0);
                    return 1;
                }).orElse(0);
            }))
            .then(Commands.literal("civs").executes(CivCommands::civs))
            .then(Commands.literal("armies").executes(CivCommands::armies))
            .then(Commands.literal("move")
                .then(Commands.argument("army", IntegerArgumentType.integer(1))
                    .then(Commands.literal("here").executes(ctx -> moveHere(ctx)))
                    .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer()).executes(CivCommands::moveTo)))))
            .then(Commands.literal("settle")
                .then(Commands.argument("army", IntegerArgumentType.integer(1))
                    .then(Commands.argument("city", StringArgumentType.greedyString()).executes(CivCommands::settle))))
            .then(Commands.literal("war")
                .then(Commands.argument("civ", IntegerArgumentType.integer(1)).executes(ctx -> war(ctx, true))))
            .then(Commands.literal("peace")
                .then(Commands.argument("civ", IntegerArgumentType.integer(1)).executes(ctx -> war(ctx, false))))
            .then(Commands.literal("addai").requires(s -> s.hasPermission(2))
                .executes(ctx -> addAi(ctx, null))
                .then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx -> addAi(ctx, StringArgumentType.getString(ctx, "name")))))
            .then(Commands.literal("starts").requires(s -> s.hasPermission(2))
                .executes(ctx -> starts(ctx, 4))
                .then(Commands.argument("n", IntegerArgumentType.integer(1, 16)).executes(ctx -> starts(ctx, IntegerArgumentType.getInteger(ctx, "n")))))
            .then(Commands.literal("found")
                .then(Commands.argument("city", StringArgumentType.string())
                    .executes(ctx -> found(ctx, null))
                    .then(Commands.argument("civName", StringArgumentType.greedyString())
                        .executes(ctx -> found(ctx, StringArgumentType.getString(ctx, "civName"))))))
            .then(Commands.literal("research")
                .then(Commands.argument("tech", ResourceLocationArgument.id()).suggests(TECHS).executes(CivCommands::research)))
            .then(Commands.literal("queue")
                .then(Commands.argument("item", ResourceLocationArgument.id()).suggests(ITEMS).executes(CivCommands::queue)))
            .then(Commands.literal("newgame").requires(s -> s.hasPermission(2))
                .then(Commands.literal("auto").executes(CivCommands::newGameAuto))
                .then(Commands.argument("topology", StringArgumentType.word())
                    .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[]{"torus", "cylinder", "bounded"}, b))
                    .then(Commands.argument("sizeBlocks", IntegerArgumentType.integer(256, 16384))
                        .executes(CivCommands::newGame))))
            .then(Commands.literal("tick").requires(s -> s.hasPermission(2))
                .executes(ctx -> tick(ctx, 1))
                .then(Commands.argument("n", IntegerArgumentType.integer(1, 1000)).executes(ctx -> tick(ctx, IntegerArgumentType.getInteger(ctx, "n")))))
            .then(Commands.literal("pause").requires(s -> s.hasPermission(2)).executes(ctx -> pause(ctx, true)))
            .then(Commands.literal("resume").requires(s -> s.hasPermission(2)).executes(ctx -> pause(ctx, false)))
            .then(Commands.literal("save").requires(s -> s.hasPermission(2)).executes(CivCommands::save))
            // Operator/console commands acting for any issuer id (e.g. "ai:1" or a test name). Used by tests and AI debugging.
            .then(Commands.literal("admin").requires(s -> s.hasPermission(2))
                .then(Commands.literal("found")
                    .then(Commands.argument("issuer", StringArgumentType.string())
                        .then(Commands.argument("city", StringArgumentType.string())
                            .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                    .executes(CivCommands::adminFound))))))
                .then(Commands.literal("move")
                    .then(Commands.argument("issuer", StringArgumentType.string())
                        .then(Commands.argument("army", IntegerArgumentType.integer(1))
                            .then(Commands.argument("tx", IntegerArgumentType.integer())
                                .then(Commands.argument("tz", IntegerArgumentType.integer())
                                    .executes(ctx -> adminSubmit(ctx, new Command.MoveArmy(StringArgumentType.getString(ctx, "issuer"),
                                        IntegerArgumentType.getInteger(ctx, "army"), IntegerArgumentType.getInteger(ctx, "tx"), IntegerArgumentType.getInteger(ctx, "tz")))))))))
                .then(Commands.literal("war")
                    .then(Commands.argument("issuer", StringArgumentType.string())
                        .then(Commands.argument("civ", IntegerArgumentType.integer(1))
                            .executes(ctx -> adminSubmit(ctx, new Command.DeclareWar(StringArgumentType.getString(ctx, "issuer"), IntegerArgumentType.getInteger(ctx, "civ")))))))
                .then(Commands.literal("peace")
                    .then(Commands.argument("issuer", StringArgumentType.string())
                        .then(Commands.argument("civ", IntegerArgumentType.integer(1))
                            .executes(ctx -> adminSubmit(ctx, new Command.ProposePeace(StringArgumentType.getString(ctx, "issuer"), IntegerArgumentType.getInteger(ctx, "civ")))))))
                .then(Commands.literal("queue")
                    .then(Commands.argument("issuer", StringArgumentType.string())
                        .then(Commands.argument("item", ResourceLocationArgument.id()).suggests(ITEMS).executes(CivCommands::adminQueue))))
                .then(Commands.literal("settle")
                    .then(Commands.argument("issuer", StringArgumentType.string())
                        .then(Commands.argument("army", IntegerArgumentType.integer(1))
                            .then(Commands.argument("city", StringArgumentType.greedyString())
                                .executes(ctx -> adminSubmit(ctx, new Command.SettleWithArmy(StringArgumentType.getString(ctx, "issuer"),
                                    IntegerArgumentType.getInteger(ctx, "army"), StringArgumentType.getString(ctx, "city"))))))))
                .then(Commands.literal("army")
                    .then(Commands.argument("issuer", StringArgumentType.string())
                        .then(Commands.argument("unit", ResourceLocationArgument.id()).suggests(ITEMS)
                            .then(Commands.argument("n", IntegerArgumentType.integer(1, 10))
                                .then(Commands.argument("tx", IntegerArgumentType.integer())
                                    .then(Commands.argument("tz", IntegerArgumentType.integer()).executes(CivCommands::adminArmy)))))))
                .then(Commands.literal("observe")
                    .then(Commands.literal("clear").executes(ctx -> {
                        if (net.neoforged.fml.ModList.get().isLoaded("recruits")) ObserveHook.clear();
                        return msg(ctx, "Observers cleared.");
                    }))
                    .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer()).executes(ctx -> {
                            if (!net.neoforged.fml.ModList.get().isLoaded("recruits")) return reply(ctx, CommandResult.fail("Recruits is not installed."));
                            ObserveHook.add(IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "z"));
                            return msg(ctx, "Armies near that point now materialise as if a player were there.");
                        }))))
                .then(Commands.literal("turnlimit")
                    .then(Commands.argument("turn", IntegerArgumentType.integer(0)).executes(CivCommands::turnLimit)))
                .then(Commands.literal("probe")
                    .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer()).executes(CivCommands::probe))))
                .then(Commands.literal("research")
                    .then(Commands.argument("issuer", StringArgumentType.string())
                        .then(Commands.argument("tech", ResourceLocationArgument.id()).suggests(TECHS).executes(CivCommands::adminResearch)))))
        );
    }

    // ------------------------------------------------------------------ helpers

    private static Optional<ServerGame> game(CommandContext<CommandSourceStack> ctx) {
        Optional<ServerGame> g = ServerGame.current();
        if (g.isEmpty()) ctx.getSource().sendFailure(Component.literal("Civ Core is not running on this server (see the server log)."));
        return g;
    }

    private static int reply(CommandContext<CommandSourceStack> ctx, CommandResult r) {
        if (r.ok()) {
            ctx.getSource().sendSuccess(() -> Component.literal(r.message()), true);
            return 1;
        }
        ctx.getSource().sendFailure(Component.literal(r.message()));
        return 0;
    }

    private static int msg(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    /** Short ids ("agriculture") resolve to the civcore namespace. */
    private static String resolveId(ResourceLocation id, java.util.Set<String> known) {
        String full = id.toString();
        if (known.contains(full)) return full;
        String civ = "civcore:" + id.getPath();
        return known.contains(civ) ? civ : full;
    }

    // ------------------------------------------------------------------ commands

    private static int newGame(CommandContext<CommandSourceStack> ctx) {
        String topo = StringArgumentType.getString(ctx, "topology");
        int size = IntegerArgumentType.getInteger(ctx, "sizeBlocks");
        if (!topo.equals("torus") && !topo.equals("cylinder") && !topo.equals("bounded")) {
            ctx.getSource().sendFailure(Component.literal("Topology must be torus, cylinder or bounded."));
            return 0;
        }
        return game(ctx).map(g -> {
            g.newGame(GameSettings.centred(topo, size, ctx.getSource().getLevel().getSeed()));
            GameSettings s = g.state().orElseThrow().settings;
            return msg(ctx, "New Civ game: " + topo + " " + size + " blocks = " + s.widthTiles() + "x" + s.heightTiles()
                + " tiles of " + s.tileSize() + ". Players found their capital with /civ found <name> [civilization name].");
        }).orElse(0);
    }

    /** New game sized to the Exotic Worlds shape of the overworld. */
    private static int newGameAuto(CommandContext<CommandSourceStack> ctx) {
        var level = ctx.getSource().getServer().overworld();
        var shape = dev.civcore.mod.world.ExoticWorldsCompat.shape(level);
        if (shape.isEmpty()) return reply(ctx, CommandResult.fail("This world doesn't wrap east-west; use /civ newgame bounded <sizeBlocks>."));
        var sh = shape.get();
        int tile = GameSettings.DEFAULT_TILE_SIZE;
        GameSettings s = new GameSettings(sh.topology(), sh.widthX() / tile, sh.widthZ() / tile, tile, sh.minX(), sh.minZ(),
            level.getSeed(), 60, 3, 3);
        return game(ctx).map(g -> {
            g.newGame(s);
            return msg(ctx, "New Civ game on this " + sh.topology() + " world: " + sh.widthX() + "x" + sh.widthZ() + " blocks = "
                + s.widthTiles() + "x" + s.heightTiles() + " tiles. Analysing terrain...");
        }).orElse(0);
    }

    private static int found(CommandContext<CommandSourceStack> ctx, String civName) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        BlockPos pos = p.blockPosition();
        String city = StringArgumentType.getString(ctx, "city");
        return game(ctx).map(g -> reply(ctx, g.submit(new Command.FoundCity(p.getStringUUID(), civName, city, pos.getX(), pos.getY(), pos.getZ())))).orElse(0);
    }

    private static int research(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        String id = resolveId(ResourceLocationArgument.getId(ctx, "tech"), RulesetReloadListener.current().techs().keySet());
        return game(ctx).map(g -> reply(ctx, g.submit(new Command.SetResearch(p.getStringUUID(), id)))).orElse(0);
    }

    private static int queue(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Ruleset r = RulesetReloadListener.current();
        java.util.Set<String> items = new java.util.HashSet<>(r.buildings().keySet());
        items.addAll(r.units().keySet());
        String id = resolveId(ResourceLocationArgument.getId(ctx, "item"), items);
        return game(ctx).map(g -> {
            Optional<GameState> st = g.state();
            Optional<City> city = st.flatMap(s -> nearestOwnCity(s, p));
            if (city.isEmpty()) return reply(ctx, CommandResult.fail("You have no city. Found one with /civ found <name>."));
            return reply(ctx, g.submit(new Command.QueueProduction(p.getStringUUID(), city.get().id, id)));
        }).orElse(0);
    }

    private static int adminFound(CommandContext<CommandSourceStack> ctx) {
        String issuer = StringArgumentType.getString(ctx, "issuer");
        int x = IntegerArgumentType.getInteger(ctx, "x"), z = IntegerArgumentType.getInteger(ctx, "z");
        String city = StringArgumentType.getString(ctx, "city");
        // Height unknown here (the chunk may be unloaded): the City Center is placed when the chunk loads.
        return game(ctx).map(g -> {
            int res = reply(ctx, g.submit(new Command.FoundCity(issuer, "Civ of " + issuer, city, x, Integer.MIN_VALUE, z)));
            g.syncWorld();
            return res;
        }).orElse(0);
    }

    private static int adminResearch(CommandContext<CommandSourceStack> ctx) {
        String issuer = StringArgumentType.getString(ctx, "issuer");
        String id = resolveId(ResourceLocationArgument.getId(ctx, "tech"), RulesetReloadListener.current().techs().keySet());
        return game(ctx).map(g -> reply(ctx, g.submit(new Command.SetResearch(issuer, id)))).orElse(0);
    }

    private static int tile(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        return game(ctx).map(g -> {
            if (g.state().isEmpty()) return reply(ctx, CommandResult.fail("No game."));
            GameState s = g.state().get();
            var m = s.settings.mapping();
            TilePos t = s.topology().normalizeOrNull(m.tileX(p.getBlockX()), m.tileZ(p.getBlockZ()));
            if (t == null) return reply(ctx, CommandResult.fail("Outside the map."));
            var terrain = s.map.terrain(t);
            var y = dev.civcore.sim.rules.TileYields.of(s, t);
            int owner = s.map.owner(t);
            return msg(ctx, "Tile " + t + ": " + (terrain == null ? "not analysed yet" : terrain.name().toLowerCase())
                + (s.map.river(t) ? " + river" : "") + ", yields food " + y.food() + " prod " + y.production() + " gold " + y.gold()
                + ", owner " + (owner < 0 ? "none" : s.civs.get(owner).name)
                + ", " + s.topology().distanceToSeamOrEdge(t) + " tiles from the world's edge");
        }).orElse(0);
    }

    private static int starts(CommandContext<CommandSourceStack> ctx, int n) {
        return game(ctx).map(g -> {
            if (g.state().isEmpty() || !g.state().get().map.analysed) return reply(ctx, CommandResult.fail("The world has not been analysed yet."));
            GameState s = g.state().get();
            var m = s.settings.mapping();
            StringBuilder sb = new StringBuilder("Suggested starts:");
            for (TilePos t : dev.civcore.sim.worldplan.StartPlacer.place(s, n)) {
                sb.append("\n  tile ").append(t).append(" ").append(s.map.terrain(t).name().toLowerCase())
                  .append(" score ").append(dev.civcore.sim.worldplan.StartPlacer.score(s, t))
                  .append(" -> /tp @s ").append(m.centerBlockX(t)).append(" ~ ").append(m.centerBlockZ(t));
            }
            return msg(ctx, sb.toString());
        }).orElse(0);
    }

    /** Compares the generator sample used by world analysis with the real (generated) chunk at x, z. */
    private static int probe(CommandContext<CommandSourceStack> ctx) {
        int x = IntegerArgumentType.getInteger(ctx, "x"), z = IntegerArgumentType.getInteger(ctx, "z");
        var level = ctx.getSource().getServer().overworld();
        var holder = new java.util.concurrent.atomic.AtomicReference<dev.civcore.sim.worldplan.TerrainSampler.Sample>();
        dev.civcore.mod.world.ExoticWorldsCompat.withFold(level, () -> holder.set(new dev.civcore.mod.world.MinecraftTerrainSampler(level).sample(x, z)));
        var sample = holder.get();
        level.getChunk(x >> 4, z >> 4); // load (generated chunks only on a pregenerated world)
        int realFloor = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, x, z) - 1;
        int realSurface = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        var realBiome = level.getBiome(new BlockPos(x, Math.max(realFloor, level.getSeaLevel()), z));
        return msg(ctx, "PROBE " + x + " " + z + " sampled floor=" + sample.floorHeight() + " surface=" + sample.surfaceHeight()
            + " biome=" + sample.biome() + " | real floor=" + realFloor + " surface=" + realSurface
            + " biome=" + dev.civcore.mod.world.MinecraftTerrainSampler.classify(realBiome) + " (" + realBiome.getRegisteredName() + ")");
    }

    /** Keeps Recruits-dependent classes out of this class's constant pool resolution until used. */
    private static final class ObserveHook {
        static void add(int x, int z) {
            dev.civcore.mod.army.RecruitsProjection.OBSERVERS.add(new net.minecraft.world.phys.Vec3(x, 64, z));
        }

        static void clear() {
            dev.civcore.mod.army.RecruitsProjection.OBSERVERS.clear();
        }
    }

    private static final String[] AI_NAMES = {"Aurelian League", "Kingdom of Brann", "Corsair Republic", "Duchy of Elm",
        "Free Cities of Ost", "Iron Khanate", "Jade Dominion", "Moorland Clans"};

    /** Adds an AI civilization with its capital at the best free start. */
    private static int addAi(CommandContext<CommandSourceStack> ctx, String name) {
        return game(ctx).map(g -> {
            if (g.state().isEmpty() || !g.state().get().map.analysed) return reply(ctx, CommandResult.fail("Wait until the world has been analysed."));
            GameState s = g.state().get();
            var start = dev.civcore.sim.ai.AiController.newCivStart(s);
            if (start.isEmpty()) return reply(ctx, CommandResult.fail("No room left for another civilization."));
            long aiCount = s.civs.values().stream().filter(c -> "ai".equals(c.controller)).count();
            String civName = name != null ? name : AI_NAMES[(int) (aiCount % AI_NAMES.length)];
            String issuer = dev.civcore.sim.ai.AiController.ISSUER_PREFIX + (s.nextId + 1000);
            var m = s.settings.mapping();
            TilePos t = start.get();
            CommandResult r = g.submit(new Command.FoundCity(issuer, civName, civName.split(" ")[civName.split(" ").length - 1],
                m.centerBlockX(t), Integer.MIN_VALUE, m.centerBlockZ(t)));
            if (r.ok()) {
                g.syncWorld();
                ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(Component.literal("[Civ] " + civName
                    + " (AI) has joined the game at /tp @s " + m.centerBlockX(t) + " ~ " + m.centerBlockZ(t)), false);
            }
            return reply(ctx, r);
        }).orElse(0);
    }

    // ------------------------------------------------------------------ military

    private static int adminSubmit(CommandContext<CommandSourceStack> ctx, Command cmd) {
        return game(ctx).map(g -> {
            int res = reply(ctx, g.submit(cmd));
            g.syncWorld();
            return res;
        }).orElse(0);
    }

    private static int adminQueue(CommandContext<CommandSourceStack> ctx) {
        String issuer = StringArgumentType.getString(ctx, "issuer");
        Ruleset r = RulesetReloadListener.current();
        java.util.Set<String> items = new java.util.HashSet<>(r.buildings().keySet());
        items.addAll(r.units().keySet());
        String id = resolveId(ResourceLocationArgument.getId(ctx, "item"), items);
        return game(ctx).map(g -> {
            var civ = g.state().flatMap(s -> s.civOfMember(issuer));
            if (civ.isEmpty()) return reply(ctx, CommandResult.fail("No civ for " + issuer));
            return reply(ctx, g.submit(new Command.QueueProduction(issuer, civ.get().capitalCityId, id)));
        }).orElse(0);
    }

    /** Operator cheat for tests: put an army of n units on a tile for the issuer's civ. */
    private static int adminArmy(CommandContext<CommandSourceStack> ctx) {
        String issuer = StringArgumentType.getString(ctx, "issuer");
        String unit = resolveId(ResourceLocationArgument.getId(ctx, "unit"), RulesetReloadListener.current().units().keySet());
        int n = IntegerArgumentType.getInteger(ctx, "n");
        int tx = IntegerArgumentType.getInteger(ctx, "tx"), tz = IntegerArgumentType.getInteger(ctx, "tz");
        return game(ctx).map(g -> {
            if (g.state().isEmpty()) return reply(ctx, CommandResult.fail("No game."));
            GameState s = g.state().get();
            var civ = s.civOfMember(issuer);
            TilePos t = s.topology().normalizeOrNull(tx, tz);
            if (civ.isEmpty() || t == null || !RulesetReloadListener.current().units().containsKey(unit)) {
                return reply(ctx, CommandResult.fail("Need an existing civ, a valid tile and unit."));
            }
            var a = new dev.civcore.sim.model.Army();
            a.id = s.allocateId();
            a.civId = civ.get().id;
            a.tileX = t.x();
            a.tileZ = t.z();
            for (int i = 0; i < n; i++) a.units.add(dev.civcore.sim.rules.Military.newUnit(s, unit));
            s.armies.put(a.id, a);
            g.syncWorld();
            return reply(ctx, CommandResult.ok("Army " + a.id + " (" + n + " " + unit + ") placed at " + t + "."));
        }).orElse(0);
    }

    private static int turnLimit(CommandContext<CommandSourceStack> ctx) {
        int turn = IntegerArgumentType.getInteger(ctx, "turn");
        return game(ctx).map(g -> g.state().map(s -> {
            s.turnLimit = turn;
            return msg(ctx, turn == 0 ? "No turn limit." : "The game ends by score at turn " + turn + ".");
        }).orElse(0)).orElse(0);
    }

    private static int civs(CommandContext<CommandSourceStack> ctx) {
        return game(ctx).map(g -> g.state().map(s -> {
            StringBuilder sb = new StringBuilder("Civilizations:");
            ServerPlayer p = ctx.getSource().getPlayer();
            Optional<Civilization> mine = p == null ? Optional.empty() : s.civOfMember(p.getStringUUID());
            for (Civilization c : s.civs.values()) {
                long cities = s.cities.values().stream().filter(x -> x.civId == c.id).count();
                sb.append("\n  #").append(c.id).append(' ').append(c.name).append(" (").append(c.controller).append(") ")
                  .append(cities).append(" cities, score ").append(dev.civcore.sim.rules.Victory.score(s, c))
                  .append(c.eliminated ? " ELIMINATED" : "");
                if (mine.isPresent() && mine.get().id != c.id) sb.append(s.atWar(mine.get().id, c.id) ? " [WAR]" : " [peace]");
            }
            if (s.finished()) sb.append("\nGame over: ").append(s.civs.get(s.winnerCivId).name).append(" won a ").append(s.victoryType).append(" victory.");
            return msg(ctx, sb.toString());
        }).orElse(0)).orElse(0);
    }

    private static int armies(CommandContext<CommandSourceStack> ctx) {
        return game(ctx).map(g -> g.state().map(s -> {
            ServerPlayer p = ctx.getSource().getPlayer();
            Optional<Civilization> mine = p == null ? Optional.empty() : s.civOfMember(p.getStringUUID());
            StringBuilder sb = new StringBuilder("Armies:");
            var m = s.settings.mapping();
            for (var a : s.armies.values()) {
                if (mine.isPresent() && a.civId != mine.get().id) continue;
                Map<String, Integer> counts = new java.util.TreeMap<>();
                int hp = 0;
                for (var u : a.units) {
                    counts.merge(u.type.replace("civcore:", ""), 1, Integer::sum);
                    hp += u.hp;
                }
                sb.append("\n  #").append(a.id).append(' ').append(s.civs.get(a.civId).name).append(' ').append(counts)
                  .append(" hp ").append(a.units.isEmpty() ? 0 : hp / a.units.size()).append("% at tile ").append(a.tile())
                  .append(" (block ").append(m.centerBlockX(a.tile())).append(' ').append(m.centerBlockZ(a.tile())).append(')')
                  .append(a.path.isEmpty() ? " holding" : " moving, " + a.path.size() + " tiles to go");
            }
            return msg(ctx, sb.toString());
        }).orElse(0)).orElse(0);
    }

    private static int moveTo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        int x = IntegerArgumentType.getInteger(ctx, "x"), z = IntegerArgumentType.getInteger(ctx, "z");
        return moveToBlock(ctx, x, z);
    }

    private static int moveHere(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        return moveToBlock(ctx, p.getBlockX(), p.getBlockZ());
    }

    private static int moveToBlock(CommandContext<CommandSourceStack> ctx, int bx, int bz) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        long army = IntegerArgumentType.getInteger(ctx, "army");
        return game(ctx).map(g -> g.state().map(s -> {
            var m = s.settings.mapping();
            return reply(ctx, g.submit(new Command.MoveArmy(p.getStringUUID(), army, m.tileX(bx), m.tileZ(bz))));
        }).orElse(0)).orElse(0);
    }

    private static int settle(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        long army = IntegerArgumentType.getInteger(ctx, "army");
        String city = StringArgumentType.getString(ctx, "city");
        return game(ctx).map(g -> {
            int res = reply(ctx, g.submit(new Command.SettleWithArmy(p.getStringUUID(), army, city)));
            g.syncWorld();
            return res;
        }).orElse(0);
    }

    private static int war(CommandContext<CommandSourceStack> ctx, boolean war) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        int civ = IntegerArgumentType.getInteger(ctx, "civ");
        return game(ctx).map(g -> {
            CommandResult r = g.submit(war ? new Command.DeclareWar(p.getStringUUID(), civ) : new Command.ProposePeace(p.getStringUUID(), civ));
            if (r.ok()) ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(Component.literal("[Civ] " + r.message()), false);
            else ctx.getSource().sendFailure(Component.literal(r.message()));
            return r.ok() ? 1 : 0;
        }).orElse(0);
    }

    private static Optional<City> nearestOwnCity(GameState s, ServerPlayer p) {
        Optional<Civilization> civ = s.civOfMember(p.getStringUUID());
        if (civ.isEmpty()) return Optional.empty();
        var m = s.settings.mapping();
        TilePos here = s.topology().normalizeOrNull(m.tileX(p.getBlockX()), m.tileZ(p.getBlockZ()));
        return civ.get().cityIds.stream().map(s.cities::get).filter(c -> c != null && c.civId == civ.get().id)
            .min(Comparator.comparingDouble(c -> here == null ? 0 : s.topology().distance(here, c.tile())));
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        return game(ctx).map(g -> {
            if (g.state().isEmpty()) return msg(ctx, "No Civ game yet. An operator can start one with /civ newgame torus <sizeBlocks>.");
            GameState s = g.state().get();
            Ruleset r = RulesetReloadListener.current();
            StringBuilder sb = new StringBuilder();
            g.analysisProgress().ifPresent(pr -> sb.append("Analysing the world: ").append(pr).append(" tiles\n"));
            sb.append("Turn ").append(s.turn).append(" | next in ").append(g.clock().millisUntilNext() / 1000).append("s")
              .append(g.clock().paused() ? " (paused)" : "")
              .append(" | ").append(s.settings.topology()).append(' ').append(s.settings.widthTiles()).append('x').append(s.settings.heightTiles())
              .append(" tiles | ").append(s.civs.size()).append(" civs, ").append(s.cities.size()).append(" cities");
            ServerPlayer p = ctx.getSource().getPlayer();
            // Players see their own civ; the console sees every civ.
            java.util.List<Civilization> shown = p == null ? java.util.List.copyOf(s.civs.values())
                : s.civOfMember(p.getStringUUID()).map(java.util.List::of).orElse(java.util.List.of());
            for (Civilization c : shown) {
                sb.append("\n").append(c.name).append(": gold ").append(c.gold).append(", techs ").append(c.knownTechs.size())
                  .append(", tiles ").append(s.map.countOwnedBy(c.id));
                if (c.currentResearch != null) {
                    sb.append(", researching ").append(c.currentResearch).append(" ").append(c.researchProgress).append('/')
                      .append(Optional.ofNullable(r.techs().get(c.currentResearch)).map(Ruleset.TechDef::cost).orElse(0));
                } else {
                    sb.append(", not researching (/civ research <tech>)");
                }
                for (long id : c.cityIds) {
                    City city = s.cities.get(id);
                    if (city == null) continue;
                    sb.append("\n  ").append(city.name).append(id == c.capitalCityId ? " (capital)" : "")
                      .append(" pop ").append(city.population).append(" food ").append(city.foodStored).append('/')
                      .append(dev.civcore.sim.engine.Simulation.growthThreshold(city.population))
                      .append(" prod ").append(city.productionStored)
                      .append(" queue ").append(city.productionQueue)
                      .append(" built ").append(city.buildings);
                }
            }
            if (p != null && shown.isEmpty()) {
                sb.append("\nYou have no civilization. Found your capital with /civ found <city> [civilization name].");
            }
            return msg(ctx, sb.toString());
        }).orElse(0);
    }

    private static int techs(CommandContext<CommandSourceStack> ctx) {
        return game(ctx).map(g -> {
            ServerPlayer p = ctx.getSource().getPlayer();
            java.util.Set<String> known = g.state().flatMap(s -> p == null ? Optional.empty() : s.civOfMember(p.getStringUUID()))
                .map(c -> c.knownTechs).orElse(new java.util.LinkedHashSet<>());
            StringBuilder sb = new StringBuilder("Technologies ([x] known, [>] available):");
            RulesetReloadListener.current().techs().values().stream().sorted(Comparator.comparingInt(Ruleset.TechDef::cost).thenComparing(Ruleset.TechDef::id))
                .forEach(t -> {
                    String mark = known.contains(t.id()) ? "[x]" : known.containsAll(t.requires()) ? "[>]" : "[ ]";
                    sb.append("\n ").append(mark).append(' ').append(t.id()).append(" (").append(t.cost()).append(")");
                });
            return msg(ctx, sb.toString());
        }).orElse(0);
    }

    private static int tick(CommandContext<CommandSourceStack> ctx, int n) {
        return game(ctx).map(g -> {
            if (g.state().isEmpty()) return reply(ctx, CommandResult.fail("No game."));
            for (int i = 0; i < n; i++) g.step();
            return msg(ctx, "Advanced " + n + " turn(s); now turn " + g.state().get().turn + ".");
        }).orElse(0);
    }

    private static int pause(CommandContext<CommandSourceStack> ctx, boolean paused) {
        return game(ctx).map(g -> {
            g.clock().setPaused(paused);
            return msg(ctx, paused ? "Civ clock paused." : "Civ clock resumed.");
        }).orElse(0);
    }

    private static int save(CommandContext<CommandSourceStack> ctx) {
        return game(ctx).map(g -> {
            g.save();
            return msg(ctx, "Saved to " + g.persistence().file());
        }).orElse(0);
    }
}

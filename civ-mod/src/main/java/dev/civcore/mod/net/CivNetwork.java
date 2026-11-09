package dev.civcore.mod.net;

import com.google.gson.Gson;
import dev.civcore.mod.CivCoreMod;
import dev.civcore.mod.game.ServerGame;
import dev.civcore.sim.command.Command;
import dev.civcore.sim.command.CommandResult;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Client/server messages (docs/D-architecture.md §9). The server sends each player a {@link dev.civcore.sim.view.ClientView}
 * filtered by fog of war; the client sends actions, which become normal {@link Command}s issued by that player.
 */
public final class CivNetwork {
    private static final Gson GSON = new Gson();

    /** Server → client: gzipped ClientView JSON. */
    public record ViewPayload(byte[] gz) implements CustomPacketPayload {
        public static final Type<ViewPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CivCoreMod.MOD_ID, "view"));
        public static final StreamCodec<ByteBuf, ViewPayload> CODEC = ByteBufCodecs.BYTE_ARRAY.map(ViewPayload::new, ViewPayload::gz);

        @Override
        public Type<ViewPayload> type() {
            return TYPE;
        }
    }

    /** Server → client: open a screen ("map", "city" with id, "tech"). */
    public record OpenPayload(String screen, long id) implements CustomPacketPayload {
        public static final Type<OpenPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CivCoreMod.MOD_ID, "open"));
        public static final StreamCodec<ByteBuf, OpenPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, OpenPayload::screen, ByteBufCodecs.VAR_LONG, OpenPayload::id, OpenPayload::new);

        @Override
        public Type<OpenPayload> type() {
            return TYPE;
        }
    }

    /** Client → server: an action as JSON ({@link Action}). */
    public record ActionPayload(String json) implements CustomPacketPayload {
        public static final Type<ActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CivCoreMod.MOD_ID, "action"));
        public static final StreamCodec<ByteBuf, ActionPayload> CODEC = ByteBufCodecs.stringUtf8(32767).map(ActionPayload::new, ActionPayload::json);

        @Override
        public Type<ActionPayload> type() {
            return TYPE;
        }
    }

    /** What a client may ask for. Anything else is ignored. */
    public static final class Action {
        public String type; // view | move | queue | dequeue (x = index) | research | settle | war | peace
        public long id;
        public int x, z;
        public String item;
        public String name;
        public int civ;
    }

    private CivNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar reg = event.registrar("1");
        // Client handlers live in a client-only class, referenced only inside the lambdas (never loaded on a server).
        reg.playToClient(ViewPayload.TYPE, ViewPayload.CODEC, (p, ctx) -> dev.civcore.mod.client.ClientCivState.acceptView(p.gz()));
        reg.playToClient(OpenPayload.TYPE, OpenPayload.CODEC, (p, ctx) -> dev.civcore.mod.client.ClientCivState.open(p.screen(), p.id()));
        reg.playToServer(ActionPayload.TYPE, ActionPayload.CODEC, CivNetwork::onAction);
    }

    // ------------------------------------------------------------------ server side

    static void onAction(ActionPayload payload, IPayloadContext ctx) {
        if (!(ctx.player() instanceof ServerPlayer player)) return;
        Action a;
        try {
            a = GSON.fromJson(payload.json(), Action.class);
        } catch (RuntimeException e) {
            return;
        }
        if (a == null || a.type == null) return;
        ServerGame.current().ifPresent(g -> {
            String me = player.getStringUUID();
            Command cmd = switch (a.type) {
                case "move" -> new Command.MoveArmy(me, a.id, a.x, a.z);
                case "queue" -> new Command.QueueProduction(me, a.id, a.item);
                case "dequeue" -> new Command.DequeueProduction(me, a.id, a.x);
                case "research" -> new Command.SetResearch(me, a.item);
                case "settle" -> new Command.SettleWithArmy(me, a.id, a.name == null || a.name.isBlank() ? g.defaultCityName(me) : a.name.trim());
                case "war" -> new Command.DeclareWar(me, a.civ);
                case "peace" -> new Command.ProposePeace(me, a.civ);
                default -> null;
            };
            if (cmd != null) {
                CommandResult r = g.submit(cmd);
                player.displayClientMessage(Component.literal(r.message()), true);
                if (r.ok() && (cmd instanceof Command.DeclareWar || cmd instanceof Command.ProposePeace || cmd instanceof Command.SettleWithArmy)) {
                    player.server.getPlayerList().broadcastSystemMessage(Component.literal("[Civ] " + r.message()), false);
                }
                if (r.ok()) g.syncWorld();
            }
            sendView(g, player);
        });
    }

    public static void sendView(ServerGame g, ServerPlayer player) {
        g.viewFor(player).ifPresent(json -> PacketDistributor.sendToPlayer(player, new ViewPayload(gzip(json))));
    }

    public static void open(ServerPlayer player, String screen, long id) {
        PacketDistributor.sendToPlayer(player, new OpenPayload(screen, id));
    }

    // ------------------------------------------------------------------ util

    public static byte[] gzip(String s) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); GZIPOutputStream gz = new GZIPOutputStream(bos)) {
            gz.write(s.getBytes(StandardCharsets.UTF_8));
            gz.finish();
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String gunzip(byte[] b) {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(b))) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Client side: sends an action. */
    public static void sendAction(Action a) {
        PacketDistributor.sendToServer(new ActionPayload(GSON.toJson(a)));
    }
}

package dev.civcore.mod.territory;

import dev.civcore.mod.game.ServerGame;
import dev.civcore.sim.model.Civilization;
import dev.civcore.sim.model.GameState;
import dev.civcore.sim.model.StrategicMap;
import dev.civcore.sim.topology.TilePos;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Optional;

/**
 * Fallback territory protection (backlog F1.8): players may not break or place blocks on tiles owned by a civ they
 * don't belong to. Creative-mode operators are exempt. War-time rules and OPAC enforcement come later.
 */
public final class BuiltinTerritory {
    private BuiltinTerritory() {
    }

    /** Name of the foreign civ owning this block's tile, if the player may not build there. */
    public static Optional<String> foreignOwner(LevelAccessor level, BlockPos pos, Player player) {
        if (!(level instanceof Level l) || l.dimension() != Level.OVERWORLD) return Optional.empty();
        if (player instanceof ServerPlayer sp && sp.isCreative() && sp.hasPermissions(2)) return Optional.empty();
        Optional<GameState> st = ServerGame.current().flatMap(ServerGame::state);
        if (st.isEmpty()) return Optional.empty();
        GameState s = st.get();
        var m = s.settings.mapping();
        TilePos tile = s.topology().normalizeOrNull(m.tileX(pos.getX()), m.tileZ(pos.getZ()));
        if (tile == null) return Optional.empty();
        int owner = s.map.owner(tile);
        if (owner == StrategicMap.NO_OWNER) return Optional.empty();
        Optional<Civilization> mine = s.civOfMember(player.getStringUUID());
        if (mine.isPresent() && mine.get().id == owner) return Optional.empty();
        Civilization c = s.civs.get(owner);
        return Optional.of(c == null ? "another civilization" : c.name);
    }

    public static void onBreak(BlockEvent.BreakEvent e) {
        foreignOwner(e.getLevel(), e.getPos(), e.getPlayer()).ifPresent(name -> {
            e.setCanceled(true);
            e.getPlayer().displayClientMessage(Component.literal("This land belongs to " + name + "."), true);
        });
    }

    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        foreignOwner(e.getLevel(), e.getPos(), p).ifPresent(name -> {
            e.setCanceled(true);
            p.displayClientMessage(Component.literal("This land belongs to " + name + "."), true);
        });
    }
}

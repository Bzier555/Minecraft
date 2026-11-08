package dev.civcore.mod.city;

import dev.civcore.mod.game.ServerGame;
import dev.civcore.sim.model.City;
import dev.civcore.sim.model.GameState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

/** The City Center block. Right-click shows the city (a screen replaces this in F1.14). */
public class CityCenterBlock extends Block {
    public CityCenterBlock(Properties props) {
        super(props);
    }

    public static Optional<City> cityAt(GameState s, BlockPos pos) {
        return s.cities.values().stream().filter(c -> c.blockX == pos.getX() && c.blockY == pos.getY() && c.blockZ == pos.getZ()).findFirst();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        Optional<GameState> s = ServerGame.current().flatMap(ServerGame::state);
        Optional<City> city = s.flatMap(st -> cityAt(st, pos));
        if (city.isEmpty()) {
            player.sendSystemMessage(Component.literal("This City Center belongs to no city."));
            return InteractionResult.CONSUME;
        }
        boolean mine = s.get().civOfMember(player.getStringUUID()).map(c -> c.id == city.get().civId).orElse(false);
        if (mine && player instanceof net.minecraft.server.level.ServerPlayer sp) {
            ServerGame.current().ifPresent(g -> dev.civcore.mod.net.CivNetwork.sendView(g, sp));
            dev.civcore.mod.net.CivNetwork.open(sp, "city", city.get().id);
        } else {
            player.sendSystemMessage(Component.literal(CityInfo.describe(s.get(), city.get())));
        }
        return InteractionResult.CONSUME;
    }
}

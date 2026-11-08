package dev.civcore.mod.city;

import dev.civcore.mod.game.ServerGame;
import dev.civcore.mod.registry.ModContent;
import dev.civcore.sim.command.Command;
import dev.civcore.sim.command.CommandResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Use on the ground to found a city there. Name the charter in an anvil to name the city; otherwise it gets a
 * numbered name. A player's first city also founds their civilization.
 */
public class SettlerCharterItem extends Item {
    public SettlerCharterItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(ctx.getPlayer() instanceof ServerPlayer player)) return InteractionResult.FAIL;
        if (level.dimension() != Level.OVERWORLD) {
            player.displayClientMessage(Component.literal("Cities can only be founded in the Overworld."), true);
            return InteractionResult.FAIL;
        }
        BlockPos pos = ctx.getClickedPos().above();
        if (!level.getBlockState(pos).canBeReplaced()) {
            player.displayClientMessage(Component.literal("There's no room for a City Center here."), true);
            return InteractionResult.FAIL;
        }
        Optional<ServerGame> game = ServerGame.current();
        if (game.isEmpty() || game.get().state().isEmpty()) {
            player.displayClientMessage(Component.literal("No Civ game is running."), true);
            return InteractionResult.FAIL;
        }
        ItemStack stack = ctx.getItemInHand();
        Component custom = stack.get(DataComponents.CUSTOM_NAME);
        String cityName = custom != null ? custom.getString() : "City " + (game.get().state().get().cities.size() + 1);
        String civName = player.getGameProfile().getName() + "'s Civilization";

        CommandResult r = game.get().submit(new Command.FoundCity(player.getStringUUID(), civName, cityName, pos.getX(), pos.getY(), pos.getZ()));
        if (!r.ok()) {
            player.displayClientMessage(Component.literal(r.message()), true);
            return InteractionResult.FAIL;
        }
        level.setBlockAndUpdate(pos, ModContent.CITY_CENTER.get().defaultBlockState());
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.server.getPlayerList().broadcastSystemMessage(Component.literal("[Civ] " + r.message()), false);
        game.get().save();
        return InteractionResult.CONSUME;
    }
}

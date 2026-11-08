package dev.civcore.mod;

import dev.civcore.mod.command.CivCommands;
import dev.civcore.mod.data.RulesetReloadListener;
import dev.civcore.mod.game.ServerGame;
import dev.civcore.mod.registry.ModContent;
import dev.civcore.mod.territory.BuiltinTerritory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(CivCoreMod.MOD_ID)
public final class CivCoreMod {
    public static final String MOD_ID = "civcore";
    public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

    public CivCoreMod(IEventBus modBus) {
        ModContent.register(modBus);
        IEventBus game = NeoForge.EVENT_BUS;
        game.addListener(BuiltinTerritory::onBreak);
        game.addListener(BuiltinTerritory::onPlace);
        game.addListener(CivCoreMod::giveStarterCharter);
        game.addListener(dev.civcore.mod.army.ArmyMarkers::onEntityJoin);
        game.addListener((AddReloadListenerEvent e) -> e.addListener(new RulesetReloadListener()));
        game.addListener((RegisterCommandsEvent e) -> CivCommands.register(e.getDispatcher()));
        game.addListener((ServerStartedEvent e) -> ServerGame.start(e.getServer()));
        game.addListener((ServerTickEvent.Post e) -> ServerGame.current().ifPresent(ServerGame::tick));
        game.addListener((LevelEvent.Save e) -> {
            if (e.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
                ServerGame.current().ifPresent(ServerGame::save);
            }
        });
        game.addListener((ServerStoppingEvent e) -> ServerGame.stop());
    }

    private static final String CHARTER_GIVEN = "civcore.starter_charter_given";

    /** Each player without a civ gets one Settler Charter, once per game, to found their capital. */
    private static void giveStarterCharter(PlayerEvent.PlayerLoggedInEvent e) {
        var player = e.getEntity();
        ServerGame.current().flatMap(ServerGame::state).ifPresent(s -> {
            String key = CHARTER_GIVEN + "." + s.gameId;
            if (s.civOfMember(player.getStringUUID()).isPresent() || player.getPersistentData().getBoolean(key)) return;
            player.getInventory().add(new ItemStack(ModContent.SETTLER_CHARTER.get()));
            player.getPersistentData().putBoolean(key, true);
            player.sendSystemMessage(Component.literal("[Civ] You received a Settler Charter. Use it on the ground to found your capital "
                + "(rename it in an anvil to name the city). /civ status shows your civilization."));
        });
    }
}

package dev.civcore.mod.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Client-only wiring. Loaded only when running on a client (see CivCoreMod). */
public final class ClientSetup {
    public static final KeyMapping OPEN_MAP = new KeyMapping("key.civcore.map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_Y, "key.categories.civcore");

    private ClientSetup() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener((RegisterKeyMappingsEvent e) -> e.register(OPEN_MAP));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            while (OPEN_MAP.consumeClick()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && mc.screen == null) ClientCivState.open("map", 0);
            }
        });
    }
}

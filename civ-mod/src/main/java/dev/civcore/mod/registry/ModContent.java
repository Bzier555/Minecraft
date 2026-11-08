package dev.civcore.mod.registry;

import dev.civcore.mod.CivCoreMod;
import dev.civcore.mod.city.CityCenterBlock;
import dev.civcore.mod.city.SettlerCharterItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Blocks and items. Registering content means clients need the mod too. */
public final class ModContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CivCoreMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(CivCoreMod.MOD_ID);

    /** Marks a city's centre. Unbreakable by players and explosions; changes hands only through capture (later). */
    public static final DeferredBlock<CityCenterBlock> CITY_CENTER = BLOCKS.register("city_center",
        () -> new CityCenterBlock(BlockBehaviour.Properties.of()
            .mapColor(MapColor.GOLD).strength(-1.0F, 3_600_000.0F).noLootTable().sound(SoundType.LODESTONE).lightLevel(s -> 12)));

    /** Founds a city where it is used. Its custom name (anvil) becomes the city's name. */
    public static final DeferredItem<SettlerCharterItem> SETTLER_CHARTER = ITEMS.register("settler_charter",
        () -> new SettlerCharterItem(new Item.Properties().stacksTo(1)));

    private ModContent() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener((BuildCreativeModeTabContentsEvent e) -> {
            if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) e.accept(SETTLER_CHARTER.get());
        });
    }
}

package dev.civcore.mod.army;

import dev.civcore.mod.registry.ModContent;
import dev.civcore.sim.model.Army;
import dev.civcore.sim.model.City;
import dev.civcore.sim.model.Civilization;
import dev.civcore.sim.model.GameState;
import dev.civcore.sim.topology.TileMapping;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Placeholder physical projection of abstract armies (backlog F1.11): one named armor stand per army, wearing a
 * helmet in the civ's colour, standing on the army's tile. Applies the projection rules of docs/D-architecture.md
 * §7.2: markers carry (armyId, generation) and any loaded marker that doesn't match the current state is discarded.
 * Recruits entities replace this in Phase 3.
 */
public final class ArmyMarkers {
    public static final String TAG = "civcore.army_marker";
    private static final String KEY_ID = "civcore.army_id";
    private static final String KEY_GEN = "civcore.army_gen";

    private final Map<Long, UUID> markers = new HashMap<>();

    /** Creates, moves and removes markers so they match the armies. Only touches loaded chunks. */
    public void sync(ServerLevel level, GameState s) {
        // remove markers whose army is gone or moved
        markers.entrySet().removeIf(e -> {
            Army a = s.armies.get(e.getKey());
            Entity ent = level.getEntity(e.getValue());
            if (ent == null) return true; // unloaded or killed; recreated below if still wanted
            if (a == null || a.physical || ent.getPersistentData().getInt(KEY_GEN) != a.generation) {
                ent.discard();
                return true;
            }
            ent.setCustomName(label(s, a));
            return false;
        });
        TileMapping m = s.settings.mapping();
        Map<Long, Integer> slotOnTile = new HashMap<>();
        for (Army a : s.armies.values()) {
            long tileKey = ((long) a.tileX << 32) | (a.tileZ & 0xffffffffL);
            int slot = slotOnTile.merge(tileKey, 1, Integer::sum) - 1;
            if (markers.containsKey(a.id) || a.physical) continue;
            int x = m.centerBlockX(a.tile()) + 2 + slot * 2;
            int z = m.centerBlockZ(a.tile()) + 2;
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            ArmorStand stand = EntityType.ARMOR_STAND.create(level);
            if (stand == null) continue;
            stand.moveTo(x + 0.5, y, z + 0.5, 0, 0);
            CompoundTag t = new CompoundTag();
            stand.saveWithoutId(t);
            t.putBoolean("Marker", true);
            t.putInt("DisabledSlots", 4144959); // no taking or placing equipment
            t.putBoolean("ShowArms", true);
            stand.load(t);
            stand.setInvulnerable(true);
            stand.setNoGravity(true);
            stand.addTag(TAG);
            stand.getPersistentData().putLong(KEY_ID, a.id);
            stand.getPersistentData().putInt(KEY_GEN, a.generation);
            ItemStack helmet = new ItemStack(Items.LEATHER_HELMET);
            Civilization civ = s.civs.get(a.civId);
            helmet.set(DataComponents.DYED_COLOR, new DyedItemColor(civ == null ? 0xFFFFFF : civ.color, false));
            stand.setItemSlot(EquipmentSlot.HEAD, helmet);
            stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(a.hasUnit("civcore:settlers") ? Items.WOODEN_HOE : Items.IRON_SWORD));
            stand.setCustomName(label(s, a));
            stand.setCustomNameVisible(true);
            level.addFreshEntity(stand);
            markers.put(a.id, stand.getUUID());
        }
    }

    private static Component label(GameState s, Army a) {
        Civilization civ = s.civs.get(a.civId);
        Map<String, Integer> counts = new java.util.TreeMap<>();
        int hp = 0;
        for (Army.Unit u : a.units) {
            counts.merge(u.type.replace("civcore:", ""), 1, Integer::sum);
            hp += u.hp;
        }
        StringBuilder sb = new StringBuilder(civ == null ? "?" : civ.name).append(" #").append(a.id).append(": ");
        counts.forEach((k, v) -> sb.append(v).append(' ').append(k).append(' '));
        sb.append("(").append(a.units.isEmpty() ? 0 : hp / a.units.size()).append("% hp)");
        if (!a.path.isEmpty()) sb.append(" -> (").append(a.path.get(a.path.size() - 1)[0]).append(',').append(a.path.get(a.path.size() - 1)[1]).append(')');
        return Component.literal(sb.toString());
    }

    /**
     * Markers are never trusted from disk (crash, restart, or the army moved while unloaded): every saved marker is
     * dropped as it loads and {@link #sync} recreates the ones the current state needs. This is the simplest form of
     * the generation rule and cannot duplicate an army.
     */
    public static void onEntityJoin(EntityJoinLevelEvent e) {
        if (e.loadedFromDisk() && (e.getEntity().getTags().contains(TAG) || e.getEntity().getTags().contains("civcore.soldier"))) {
            e.setCanceled(true); // markers and soldiers are rebuilt from the game state, never loaded
        }
    }

    /** Places City Center blocks for cities founded without a known height (settlers), once their chunk is loaded. */
    public static void placePendingCityCenters(ServerLevel level, GameState s) {
        for (City c : s.cities.values()) {
            if (c.blockY != Integer.MIN_VALUE || !level.hasChunk(c.blockX >> 4, c.blockZ >> 4)) continue;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.blockX, c.blockZ);
            BlockPos pos = new BlockPos(c.blockX, y, c.blockZ);
            level.setBlockAndUpdate(pos, ModContent.CITY_CENTER.get().defaultBlockState());
            c.blockY = y;
        }
    }
}

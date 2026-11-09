package dev.civcore.mod.army;

import com.talhanation.recruits.FactionEvents;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.init.ModEntityTypes;
import com.talhanation.recruits.util.FormationUtils;
import com.talhanation.recruits.world.RecruitsDiplomacyManager;
import dev.civcore.mod.CivCoreMod;
import dev.civcore.sim.model.Army;
import dev.civcore.sim.model.Civilization;
import dev.civcore.sim.model.GameState;
import dev.civcore.sim.rules.Military;
import dev.civcore.sim.topology.TileMapping;
import dev.civcore.sim.topology.Topology;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Physical projection of armies as Villager Recruits soldiers (backlog F3.1-F3.3, docs/D-architecture.md §7).
 *
 * <ul>
 *   <li>An army materialises when a player comes within {@link #MATERIALISE_RANGE} blocks of it; one soldier per unit,
 *       at the unit's health. It dematerialises (health written back, soldiers removed) once no player is within
 *       {@link #DEMATERIALISE_RANGE} and it hasn't fought for a while.</li>
 *   <li>Each civ is a scoreboard team; Civ Core's wars set Recruits' team diplomacy to ENEMY, so soldiers fight with
 *       Recruits' own AI only at war. Soldiers stay in NEUTRAL stance (no peacetime brawls).</li>
 *   <li>Soldiers carry (armyId, unit uid). A soldier's death removes exactly that unit. Soldiers saved to disk are never
 *       trusted on load (dropped); the army just becomes abstract again, so nothing can duplicate.</li>
 * </ul>
 * Only loaded when the Recruits mod is present.
 */
public final class RecruitsProjection {
    public static final String TAG = "civcore.soldier";
    static final String KEY_ARMY = "civcore.army_id";
    static final String KEY_UID = "civcore.unit_uid";
    public static final double MATERIALISE_RANGE = 128;
    public static final double DEMATERIALISE_RANGE = 192;
    static final double ENGAGE_RANGE = 24;
    /** Engaged soldiers farther than this from their foe walk to it before attacking. */
    static final double CHASE_DISTANCE = 5;
    /** How far engaged soldiers look for someone to attack (about a tile and a half). */
    static final double ENGAGE_TARGET_RANGE = 96;
    static final long ENGAGED_TICKS = 200;
    static final int MAX_SOLDIERS = 300;

    private final Map<Long, Map<Long, UUID>> live = new HashMap<>(); // army -> unit uid -> entity
    private final Map<Long, Integer> revisionAtSpawn = new HashMap<>();
    private final Map<Long, Long> engagedUntil = new HashMap<>();
    private final Map<Long, String> lastOrderedTile = new HashMap<>();
    private String lastDiplomacy = "";
    /** Operator test aid: positions that count as "a player is here" (see /civ admin observe). */
    public static final List<Vec3> OBSERVERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    // ------------------------------------------------------------------ main loop (every second)

    public void sync(ServerLevel level, GameState s) {
        syncTeams(level, s);
        List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator()).toList();
        long now = level.getGameTime();
        int soldiers = live.values().stream().mapToInt(Map::size).sum();

        // drop bookkeeping for armies that no longer exist
        for (Long id : new ArrayList<>(live.keySet())) {
            if (!s.armies.containsKey(id)) {
                discardAll(level, id);
            }
        }
        for (Army a : List.copyOf(s.armies.values())) {
            if (a.hasUnit(Military.SETTLERS)) continue; // settlers stay as markers
            Vec3 centre = centre(s, a, players);
            double nearest = nearestPlayer(players, centre);
            for (Vec3 o : OBSERVERS) nearest = Math.min(nearest, Math.hypot(o.x - centre.x, o.z - centre.z));
            if (!a.physical) {
                if (nearest <= MATERIALISE_RANGE && soldiers + a.units.size() <= MAX_SOLDIERS && level.hasChunkAt(BlockPos.containing(centre))) {
                    soldiers += materialise(level, s, a, centre);
                }
                continue;
            }
            // physical army
            if (revisionAtSpawn.getOrDefault(a.id, a.revision) != a.revision) {
                // the rules changed health abstractly (e.g. a siege): rebuild the soldiers to match
                discardAll(level, a.id);
                a.physical = false;
                continue;
            }
            List<AbstractRecruitEntity> alive = soldiers(level, a);
            if (alive.isEmpty()) { // unloaded behind our back: back to abstract, units intact
                live.remove(a.id);
                a.physical = false;
                continue;
            }
            writeBack(a, alive);
            if (a.units.size() > alive.size()) spawnMissing(level, s, a, alive);
            boolean engaged = enemyNear(level, s, a, alive);
            if (engaged) engagedUntil.put(a.id, now + ENGAGED_TICKS);
            boolean fighting = engagedUntil.getOrDefault(a.id, 0L) > now;
            if (fighting) {
                List<LivingEntity> foes = enemies(level, s, a, alive, ENGAGE_TARGET_RANGE);
                for (AbstractRecruitEntity r : alive) {
                    if (r.getFollowState() != 0) r.setFollowState(0);
                    if (foes.isEmpty()) {
                        r.setShouldMovePos(false);
                        continue;
                    }
                    LivingEntity foe = foes.stream().min(java.util.Comparator.comparingDouble(f -> f.distanceToSqr(r))).orElseThrow();
                    // Recruits only melee targets it can see and drops a target it loses sight of, and won't fight while
                    // walking: so walk to the nearest foe until close, then attack it.
                    if (r.distanceToSqr(foe) > CHASE_DISTANCE * CHASE_DISTANCE) {
                        r.setMovePos(foe.blockPosition());
                        r.setShouldMovePos(true);
                    } else {
                        r.setShouldMovePos(false);
                        if (r.getTarget() != foe) r.setTarget(foe);
                    }
                }
                lastOrderedTile.remove(a.id);
            } else {
                orderToTile(s, a, alive, centre);
            }
            // No one is watching: the battle continues abstractly, whatever state it is in.
            if (nearest > DEMATERIALISE_RANGE) dematerialise(level, a, alive);
        }
    }

    /** Everything back to abstract (server stopping). */
    public void dematerialiseAll(ServerLevel level, GameState s) {
        for (Army a : s.armies.values()) {
            if (!a.physical) continue;
            List<AbstractRecruitEntity> alive = soldiers(level, a);
            if (!alive.isEmpty()) dematerialise(level, a, alive);
            a.physical = false;
        }
        live.clear();
    }

    // ------------------------------------------------------------------ materialise / dematerialise

    private int materialise(ServerLevel level, GameState s, Army a, Vec3 centre) {
        Map<Long, UUID> map = live.computeIfAbsent(a.id, k -> new HashMap<>());
        int i = 0;
        for (Army.Unit u : a.units) {
            AbstractRecruitEntity r = spawnSoldier(level, s, a, u, centre, i++);
            if (r != null) map.put(u.uid, r.getUUID());
        }
        a.physical = !map.isEmpty();
        revisionAtSpawn.put(a.id, a.revision);
        lastOrderedTile.put(a.id, a.tileX + "," + a.tileZ);
        return map.size();
    }

    private void spawnMissing(ServerLevel level, GameState s, Army a, List<AbstractRecruitEntity> alive) {
        Map<Long, UUID> map = live.computeIfAbsent(a.id, k -> new HashMap<>());
        Vec3 at = alive.get(0).position();
        int i = alive.size();
        for (Army.Unit u : a.units) {
            if (map.containsKey(u.uid)) continue;
            AbstractRecruitEntity r = spawnSoldier(level, s, a, u, at, i++);
            if (r != null) map.put(u.uid, r.getUUID());
        }
    }

    private void dematerialise(ServerLevel level, Army a, List<AbstractRecruitEntity> alive) {
        writeBack(a, alive);
        for (AbstractRecruitEntity r : alive) r.discard();
        live.remove(a.id);
        engagedUntil.remove(a.id);
        lastOrderedTile.remove(a.id);
        a.physical = false;
    }

    private void discardAll(ServerLevel level, long armyId) {
        Map<Long, UUID> map = live.remove(armyId);
        if (map != null) {
            for (UUID id : map.values()) {
                Entity e = level.getEntity(id);
                if (e != null) e.discard();
            }
        }
        engagedUntil.remove(armyId);
        lastOrderedTile.remove(armyId);
    }

    private AbstractRecruitEntity spawnSoldier(ServerLevel level, GameState s, Army a, Army.Unit u, Vec3 centre, int index) {
        EntityType<? extends AbstractRecruitEntity> type = switch (u.type) {
            case "civcore:spearmen", "civcore:pikemen" -> ModEntityTypes.RECRUIT_SHIELDMAN.get();
            case "civcore:archers" -> ModEntityTypes.BOWMAN.get();
            case "civcore:crossbowmen", "civcore:musketmen", "civcore:riflemen", "civcore:cannon" -> ModEntityTypes.CROSSBOWMAN.get();
            case "civcore:chariot", "civcore:horsemen", "civcore:knights" -> ModEntityTypes.HORSEMAN.get();
            default -> ModEntityTypes.RECRUIT.get();
        };
        AbstractRecruitEntity r = type.create(level);
        if (r == null) return null;
        int x = (int) Math.floor(centre.x) + (index % 5) * 2 - 4;
        int z = (int) Math.floor(centre.z) + (index / 5) * 2 - 2;
        if (!level.hasChunkAt(new BlockPos(x, 0, z))) return null;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        r.moveTo(x + 0.5, y, z + 0.5, 0, 0);
        r.finalizeSpawn(level, level.getCurrentDifficultyAt(r.blockPosition()), MobSpawnType.COMMAND, null);
        equip(r, u.type);
        Civilization civ = s.civs.get(a.civId);
        r.setCustomName(Component.literal((civ == null ? "?" : civ.name) + " " + label(u.type)));
        r.setPersistenceRequired();
        r.addTag(TAG);
        r.getPersistentData().putLong(KEY_ARMY, a.id);
        r.getPersistentData().putLong(KEY_UID, u.uid);
        level.addFreshEntity(r);
        r.setOwnerUUID(Optional.of(proxy(s, a.civId)));
        r.setIsOwned(true);
        r.setAggroState(0); // neutral: fights monsters and Recruits-diplomacy enemies only
        r.setFollowState(2); // hold until ordered
        r.setHealth(Math.max(1f, r.getMaxHealth() * u.hp / (float) Army.FULL_HP));
        r.setHunger(100f);
        r.setMoral(100f);
        PlayerTeam team = team(level, s, a.civId);
        if (team != null) level.getScoreboard().addPlayerToTeam(r.getScoreboardName(), team);
        return r;
    }

    private static void equip(AbstractRecruitEntity r, String type) {
        switch (type) {
            case "civcore:warriors", "civcore:scout" -> r.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.WOODEN_SWORD));
            case "civcore:swordsmen" -> {
                r.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                r.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                r.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            }
            case "civcore:pikemen" -> {
                r.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                r.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                r.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                r.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
            }
            case "civcore:knights" -> {
                r.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                r.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                r.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            }
            case "civcore:spearmen" -> {
                r.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
                r.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
                r.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            }
            default -> {
                // archers and horsemen keep the equipment Recruits gives them
            }
        }
    }

    private static String label(String type) {
        String s = type.replace("civcore:", "");
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ------------------------------------------------------------------ running battles

    private List<AbstractRecruitEntity> soldiers(ServerLevel level, Army a) {
        List<AbstractRecruitEntity> out = new ArrayList<>();
        Map<Long, UUID> map = live.get(a.id);
        if (map == null) return out;
        map.entrySet().removeIf(e -> !(level.getEntity(e.getValue()) instanceof AbstractRecruitEntity r && r.isAlive()));
        for (UUID id : map.values()) out.add((AbstractRecruitEntity) level.getEntity(id));
        return out;
    }

    private static void writeBack(Army a, List<AbstractRecruitEntity> alive) {
        Map<Long, Integer> hp = new HashMap<>();
        for (AbstractRecruitEntity r : alive) {
            hp.put(r.getPersistentData().getLong(KEY_UID), Math.max(1, Math.min(Army.FULL_HP, Math.round(r.getHealth() / r.getMaxHealth() * Army.FULL_HP))));
        }
        for (Army.Unit u : a.units) {
            Integer h = hp.get(u.uid);
            if (h != null) u.hp = h;
        }
    }

    /**
     * Engaged if an enemy soldier or player is close, or an enemy physical army stands on this or a neighbouring tile
     * (the strategic layer put them in contact, so their soldiers must close and fight).
     */
    private boolean enemyNear(ServerLevel level, GameState s, Army a, List<AbstractRecruitEntity> alive) {
        for (Army o : s.armies.values()) {
            if (o.physical && o.civId != a.civId && s.atWar(a.civId, o.civId) && s.topology().chebyshev(o.tile(), a.tile()) <= 1) return true;
        }
        return !enemies(level, s, a, alive, ENGAGE_RANGE).isEmpty();
    }

    private List<LivingEntity> enemies(ServerLevel level, GameState s, Army a, List<AbstractRecruitEntity> alive, double range) {
        List<LivingEntity> out = new ArrayList<>();
        AABB box = null;
        for (AbstractRecruitEntity r : alive) box = box == null ? r.getBoundingBox() : box.minmax(r.getBoundingBox());
        if (box == null) return out;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box.inflate(range))) {
            if (!e.isAlive()) continue;
            if (e.getTags().contains(TAG)) {
                Army other = s.armies.get(e.getPersistentData().getLong(KEY_ARMY));
                if (other != null && s.atWar(a.civId, other.civId)) out.add(e);
            } else if (e instanceof ServerPlayer p && !p.isSpectator() && !p.isCreative()) {
                var civ = s.civOfMember(p.getStringUUID());
                if (civ.isPresent() && s.atWar(a.civId, civ.get().id)) out.add(e);
            }
        }
        return out;
    }

    private void orderToTile(GameState s, Army a, List<AbstractRecruitEntity> alive, Vec3 centre) {
        // March toward the next tile on the path (so a stack blocked by enemies closes in and fights), else hold here.
        dev.civcore.sim.topology.TilePos goal = a.path.isEmpty() ? a.tile() : new dev.civcore.sim.topology.TilePos(a.path.get(0)[0], a.path.get(0)[1]);
        String key = goal.x() + "," + goal.z();
        if (key.equals(lastOrderedTile.get(a.id))) return;
        lastOrderedTile.put(a.id, key);
        TileMapping m = s.settings.mapping();
        centre = new Vec3(m.centerBlockX(goal) + 0.5, centre.y, m.centerBlockZ(goal) + 0.5);
        Vec3 from = alive.get(0).position();
        Vec3 target = new Vec3(nearestCopyX(s, centre.x, from.x), centre.y, nearestCopyZ(s, centre.z, from.z));
        Vec3 forward = new Vec3(target.x - from.x, 0, target.z - from.z);
        forward = forward.lengthSqr() > 1e-6 ? forward.normalize() : new Vec3(1, 0, 0);
        FormationUtils.squareFormation(forward, alive, target, 1.75D);
    }

    // ------------------------------------------------------------------ teams and diplomacy

    static String teamName(int civId) {
        return "civ" + civId;
    }

    private PlayerTeam team(ServerLevel level, GameState s, int civId) {
        Scoreboard sb = level.getScoreboard();
        String name = teamName(civId);
        PlayerTeam t = sb.getPlayerTeam(name);
        if (t == null) {
            t = sb.addPlayerTeam(name);
            t.setAllowFriendlyFire(false);
            Civilization c = s.civs.get(civId);
            if (c != null) {
                t.setDisplayName(Component.literal(c.name));
                t.setColor(nearestColour(c.color));
            }
        }
        return t;
    }

    /** Teams for every civ, members in them, and Recruits diplomacy matching Civ Core's wars. */
    private void syncTeams(ServerLevel level, GameState s) {
        for (Civilization c : s.civs.values()) {
            PlayerTeam t = team(level, s, c.id);
            for (String member : c.members) {
                try {
                    ServerPlayer p = level.getServer().getPlayerList().getPlayer(UUID.fromString(member));
                    if (p != null && p.getTeam() != t) level.getScoreboard().addPlayerToTeam(p.getScoreboardName(), t);
                } catch (IllegalArgumentException ignored) {
                    // AI issuers are not UUIDs
                }
            }
        }
        String key = String.join(";", s.wars) + "|" + s.civs.size();
        if (key.equals(lastDiplomacy) || FactionEvents.recruitsDiplomacyManager == null) return;
        lastDiplomacy = key;
        for (Civilization a : s.civs.values()) {
            for (Civilization b : s.civs.values()) {
                if (a.id >= b.id) continue;
                var status = s.atWar(a.id, b.id) ? RecruitsDiplomacyManager.DiplomacyStatus.ENEMY : RecruitsDiplomacyManager.DiplomacyStatus.NEUTRAL;
                FactionEvents.recruitsDiplomacyManager.setRelation(teamName(a.id), teamName(b.id), status, level, false);
                FactionEvents.recruitsDiplomacyManager.setRelation(teamName(b.id), teamName(a.id), status, level, false);
            }
        }
        CivCoreMod.LOG.debug("Recruits diplomacy synced: {}", key);
    }

    private static ChatFormatting nearestColour(int rgb) {
        ChatFormatting best = ChatFormatting.WHITE;
        long bestD = Long.MAX_VALUE;
        for (ChatFormatting f : ChatFormatting.values()) {
            Integer c = f.getColor();
            if (c == null) continue;
            long dr = ((c >> 16) & 255) - ((rgb >> 16) & 255), dg = ((c >> 8) & 255) - ((rgb >> 8) & 255), db = (c & 255) - (rgb & 255);
            long d = dr * dr + dg * dg + db * db;
            if (d < bestD) {
                bestD = d;
                best = f;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ geometry

    /** Owner of a civ's soldiers: a stable offline UUID (Recruits treats same-owner soldiers as friends). */
    static UUID proxy(GameState s, int civId) {
        return UUID.nameUUIDFromBytes(("civ-proxy:" + s.gameId + ":" + civId).getBytes(StandardCharsets.UTF_8));
    }

    /** The army's tile centre, on the copy of the world nearest the closest player (matters near a seam). */
    private static Vec3 centre(GameState s, Army a, List<ServerPlayer> players) {
        TileMapping m = s.settings.mapping();
        double x = m.centerBlockX(a.tile()) + 0.5, z = m.centerBlockZ(a.tile()) + 0.5;
        ServerPlayer near = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer p : players) {
            double d = Math.abs(nearestCopyX(s, x, p.getX()) - p.getX()) + Math.abs(nearestCopyZ(s, z, p.getZ()) - p.getZ());
            if (d < best) {
                best = d;
                near = p;
            }
        }
        if (near != null) {
            x = nearestCopyX(s, x, near.getX());
            z = nearestCopyZ(s, z, near.getZ());
        }
        return new Vec3(x, 64, z);
    }

    private static double nearestCopyX(GameState s, double target, double observer) {
        if (!s.topology().wrapsX()) return target;
        long period = (long) s.settings.widthTiles() * s.settings.tileSize();
        return target + (Topology.nearestCopy((long) Math.floor(target), (long) Math.floor(observer), period) - Math.floor(target));
    }

    private static double nearestCopyZ(GameState s, double target, double observer) {
        if (!s.topology().wrapsZ()) return target;
        long period = (long) s.settings.heightTiles() * s.settings.tileSize();
        return target + (Topology.nearestCopy((long) Math.floor(target), (long) Math.floor(observer), period) - Math.floor(target));
    }

    private static double nearestPlayer(List<ServerPlayer> players, Vec3 c) {
        double best = Double.MAX_VALUE;
        for (ServerPlayer p : players) best = Math.min(best, Math.hypot(p.getX() - c.x, p.getZ() - c.z));
        return best;
    }

    // ------------------------------------------------------------------ events

    /** A soldier died: remove exactly its unit from its army. */
    public static void onDeath(LivingEntity e, GameState s, List<String> events) {
        if (!e.getTags().contains(TAG)) return;
        Military.unitKilled(s, e.getPersistentData().getLong(KEY_ARMY), e.getPersistentData().getLong(KEY_UID), events);
    }
}

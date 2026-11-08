package dev.civcore.spike;

import com.ldtteam.structurize.api.RotationMirror;
import com.ldtteam.structurize.storage.StructurePacks;
import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.blocks.AbstractBlockHut;
import com.minecolonies.api.blocks.ModBlocks;
import com.minecolonies.api.colony.ICitizenData;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.api.colony.buildings.IBuilding;
import com.minecolonies.api.colony.workorders.IServerWorkOrder;
import com.minecolonies.api.eventbus.events.colony.ColonyCreatedModEvent;
import com.minecolonies.api.eventbus.events.colony.buildings.BuildingConstructionModEvent;
import com.minecolonies.api.tileentities.AbstractTileEntityColonyBuilding;
import com.minecolonies.api.util.CreativeBuildingStructureHandler;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.talhanation.recruits.entities.AbstractRecruitEntity;
import com.talhanation.recruits.init.ModEntityTypes;
import com.talhanation.recruits.util.FormationUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI;
import xaero.pac.common.server.api.OpenPACServerAPI;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phase 0 integration spike (S0.3 MineColonies proxy owner, S0.4 Recruits bridge, S0.5 OPAC proxy claims).
 * Every command prints a single "SPIKE ..." line so RCON scripts can parse results.
 */
@Mod("civspike")
public class CivSpikeMod {
    static final Logger LOG = LoggerFactory.getLogger("civspike");
    static final String PACK = "Medieval Oak";
    static final String TOWNHALL = "fundamentals/townhall1.blueprint";
    static final String BUILDER = "fundamentals/builder1.blueprint";

    public CivSpikeMod(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(this::onCommands);
        // MineColonies' API instance is only set later in loading; subscribe once the server starts.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStartingEvent e) -> subscribeColonyEvents());
    }

    private static boolean subscribed;
    private static void subscribeColonyEvents() {
        if (subscribed) return;
        subscribed = true;
        IMinecoloniesAPI.getInstance().getEventBus().subscribe(BuildingConstructionModEvent.class,
            e -> LOG.info("SPIKE event BuildingConstruction colony={} building={} level={} order={}", e.getColony().getID(),
                e.getBuilding().getPosition(), e.getBuilding().getBuildingLevel(), e.getWorkOrder().getWorkOrderType()));
    }

    /** A stable proxy owner per civ/city name: the identity Civ Core would use to own a colony on a nation's behalf. */
    static FakePlayer proxy(ServerLevel level, String name) {
        UUID id = UUID.nameUUIDFromBytes(("civ-proxy:" + name).getBytes(StandardCharsets.UTF_8));
        return FakePlayerFactory.get(level, new GameProfile(id, "civ_" + name));
    }

    static int reply(CommandContext<CommandSourceStack> ctx, String msg) {
        LOG.info(msg);
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    static BlockPos surface(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private void onCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("civspike").requires(s -> s.hasPermission(2))
            .then(Commands.literal("colony")
                .then(Commands.argument("owner", StringArgumentType.word())
                    .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                            .then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(this::colony))))))
            .then(Commands.literal("builder")
                .then(Commands.argument("owner", StringArgumentType.word())
                    .then(Commands.argument("colony", IntegerArgumentType.integer())
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                            .then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(this::builder))))))
            .then(Commands.literal("subscribe")
                .then(Commands.argument("owner", StringArgumentType.word())
                    .then(Commands.argument("colony", IntegerArgumentType.integer())
                        .executes(this::subscribe))))
            .then(Commands.literal("status").executes(this::status))
            .then(Commands.literal("army")
                .then(Commands.argument("owner", StringArgumentType.word())
                    .then(Commands.argument("n", IntegerArgumentType.integer(1, 50))
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                            .then(Commands.argument("z", IntegerArgumentType.integer())
                                .executes(this::army))))))
            .then(Commands.literal("move")
                .then(Commands.argument("owner", StringArgumentType.word())
                    .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                            .executes(this::move)))))
            .then(Commands.literal("enemies")
                .then(Commands.argument("n", IntegerArgumentType.integer(1, 50))
                    .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                            .executes(this::enemies)))))
            .then(Commands.literal("armystatus")
                .then(Commands.argument("owner", StringArgumentType.word()).executes(this::armyStatus)))
            .then(Commands.literal("claim")
                .then(Commands.argument("owner", StringArgumentType.word())
                    .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                            .executes(this::claim)))))
        );
    }

    // ---------------------------------------------------------------- MineColonies (S0.3)

    private int colony(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        String ownerName = StringArgumentType.getString(ctx, "owner");
        String name = StringArgumentType.getString(ctx, "name");
        BlockPos pos = surface(level, IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "z"));
        FakePlayer owner = proxy(level, ownerName);
        owner.setPos(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        IColony existing = IColonyManager.getInstance().getIColonyByOwner(level, owner);
        try {
            BlockState th = ModBlocks.blockHutTownHall.defaultBlockState();
            level.setBlockAndUpdate(pos, th);
            ((AbstractBlockHut<?>) th.getBlock()).onBlockPlacedByBuildTool(level, pos, th, owner, ItemStack.EMPTY, RotationMirror.NONE, PACK, TOWNHALL);
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof AbstractTileEntityColonyBuilding hut)) {
                return reply(ctx, "SPIKE colony FAIL no town hall block entity at " + pos);
            }
            hut.setStructurePack(StructurePacks.getStructurePack(PACK));
            hut.setBlueprintPath(TOWNHALL);
            // Direct API call: bypasses the one-colony-per-owner check that lives in CreateColonyMessage.
            IColony colony = IColonyManager.getInstance().createColony(level, pos, owner, name, PACK);
            IBuilding building = colony.getServerBuildingManager().addNewBuilding(hut, level);
            IMinecoloniesAPI.getInstance().getEventBus().post(new ColonyCreatedModEvent(colony));
            // Paste the level-1 town hall instantly (the "AI city blueprint paste" path).
            CreativeBuildingStructureHandler.loadAndPlaceStructureWithRotation(level,
                StructurePacks.getBlueprintFuture(PACK, TOWNHALL, level.registryAccess()), pos, RotationMirror.NONE, true, owner);
            return reply(ctx, "SPIKE colony OK id=" + colony.getID() + " name=" + name + " owner=" + owner.getUUID()
                + " pos=" + pos.toShortString() + " townhallLevel=" + (building == null ? "null" : building.getBuildingLevel())
                + " ownerAlreadyHadColony=" + (existing == null ? "none" : existing.getID())
                + " byOwnerNow=" + IColonyManager.getInstance().getIColonyByOwner(level, owner.getUUID()).getID());
        } catch (Exception e) {
            LOG.error("colony failed", e);
            return reply(ctx, "SPIKE colony FAIL " + e);
        }
    }

    private int builder(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        FakePlayer owner = proxy(level, StringArgumentType.getString(ctx, "owner"));
        IColony colony = IColonyManager.getInstance().getColonyByWorld(IntegerArgumentType.getInteger(ctx, "colony"), level);
        if (colony == null) return reply(ctx, "SPIKE builder FAIL no colony");
        BlockPos pos = surface(level, IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "z"));
        try {
            BlockState bh = ModBlocks.blockHutBuilder.defaultBlockState();
            level.setBlockAndUpdate(pos, bh);
            ((AbstractBlockHut<?>) bh.getBlock()).onBlockPlacedByBuildTool(level, pos, bh, owner, ItemStack.EMPTY, RotationMirror.NONE, PACK, BUILDER);
            IBuilding building = IColonyManager.getInstance().getBuilding(level, pos);
            if (building == null) return reply(ctx, "SPIKE builder FAIL building not registered at " + pos.toShortString()
                + " colonyAtPos=" + idOf(IColonyManager.getInstance().getColonyByPosFromWorld(level, pos)));
            if (building.getTileEntity() != null) building.getTileEntity().setColony(colony);
            building.setStructurePack(PACK);
            building.setBlueprintPath(BUILDER);
            building.setBuildingLevel(0);
            building.requestUpgrade(owner, pos);
            return reply(ctx, "SPIKE builder OK pos=" + pos.toShortString() + " colony=" + building.getColony().getID()
                + " workOrders=" + colony.getWorkManager().getWorkOrders().size());
        } catch (Exception e) {
            LOG.error("builder failed", e);
            return reply(ctx, "SPIKE builder FAIL " + e);
        }
    }

    /** Keeps a colony ACTIVE with no real player by registering the proxy as a close subscriber standing in the colony. */
    private int subscribe(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        FakePlayer owner = proxy(level, StringArgumentType.getString(ctx, "owner"));
        IColony colony = IColonyManager.getInstance().getColonyByWorld(IntegerArgumentType.getInteger(ctx, "colony"), level);
        if (colony == null) return reply(ctx, "SPIKE subscribe FAIL no colony");
        BlockPos c = colony.getCenter();
        owner.setPos(c.getX() + 0.5, c.getY() + 1, c.getZ() + 0.5);
        colony.getPackageManager().addImportantColonyPlayer(owner);
        colony.getPackageManager().addCloseSubscriber(owner);
        return reply(ctx, "SPIKE subscribe OK colony=" + colony.getID() + " subscribers=" + colony.getPackageManager().getCloseSubscribers().size());
    }

    private int status(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        StringBuilder sb = new StringBuilder("SPIKE status");
        for (IColony colony : IColonyManager.getInstance().getAllColonies()) {
            sb.append(" | colony ").append(colony.getID()).append(' ').append(colony.getName())
              .append(" state=").append(colony.getState())
              .append(" owner=").append(colony.getPermissions().getOwner())
              .append(" center=").append(colony.getCenter().toShortString());
            BlockPos ctr = colony.getCenter();
            int solid = 0;
            for (BlockPos p : BlockPos.betweenClosed(ctr.offset(-12, 1, -12), ctr.offset(12, 15, 12))) {
                if (!level.getBlockState(p).isAir()) solid++;
            }
            sb.append(" blocksAboveGround=").append(solid);
            List<ICitizenData> citizens = colony.getCitizenManager().getCitizens();
            sb.append(" citizens=").append(citizens.size()).append(" [");
            for (ICitizenData cd : citizens) {
                sb.append(cd.getName()).append(':').append(cd.getJob() == null ? "none" : cd.getJob().getClass().getSimpleName())
                  .append('@').append(cd.getEntity().map(e -> e.blockPosition().toShortString()).orElse("unloaded")).append("; ");
            }
            sb.append("] buildings=[");
            colony.getServerBuildingManager().getBuildings().values().forEach(b ->
                sb.append(b.getClass().getSimpleName()).append(" L").append(b.getBuildingLevel()).append('@').append(b.getPosition().toShortString()).append("; "));
            sb.append("] workOrders=[");
            for (IServerWorkOrder wo : colony.getWorkManager().getWorkOrders().values()) {
                sb.append(wo.getWorkOrderType()).append(' ').append(wo.getCurrentLevel()).append("->").append(wo.getTargetLevel())
                  .append('@').append(wo.getLocation().toShortString()).append(" claimed=").append(wo.isClaimed()).append("; ");
            }
            sb.append(']');
        }
        return reply(ctx, sb.toString());
    }

    static String idOf(IColony c) { return c == null ? "none" : String.valueOf(c.getID()); }

    // ---------------------------------------------------------------- Recruits (S0.4)

    static List<AbstractRecruitEntity> recruitsOf(ServerLevel level, UUID owner) {
        List<AbstractRecruitEntity> out = new ArrayList<>();
        for (var e : level.getAllEntities()) {
            if (e instanceof AbstractRecruitEntity r && owner.equals(r.getOwnerUUID()) && r.isAlive()) out.add(r);
        }
        return out;
    }

    private int army(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        FakePlayer owner = proxy(level, StringArgumentType.getString(ctx, "owner"));
        int n = IntegerArgumentType.getInteger(ctx, "n");
        int x = IntegerArgumentType.getInteger(ctx, "x"), z = IntegerArgumentType.getInteger(ctx, "z");
        UUID group = UUID.nameUUIDFromBytes(("civ-army:" + owner.getUUID()).getBytes(StandardCharsets.UTF_8));
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            BlockPos p = surface(level, x + (i % 5) * 2, z + (i / 5) * 2);
            AbstractRecruitEntity r = ModEntityTypes.RECRUIT.get().create(level);
            if (r == null) continue;
            r.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
            r.finalizeSpawn(level, level.getCurrentDifficultyAt(p), MobSpawnType.COMMAND, null);
            r.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            r.setPersistenceRequired();
            r.addTag("civ_proj");
            level.addFreshEntity(r);
            r.setOwnerUUID(java.util.Optional.of(owner.getUUID()));
            r.setIsOwned(true);
            r.setGroupUUID(group);
            r.setAggroState(1); // aggressive
            r.setFollowState(2); // hold
            spawned++;
        }
        return reply(ctx, "SPIKE army OK spawned=" + spawned + " owner=" + owner.getUUID() + " owned=" + recruitsOf(level, owner.getUUID()).size());
    }

    private int move(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        FakePlayer owner = proxy(level, StringArgumentType.getString(ctx, "owner"));
        List<AbstractRecruitEntity> rs = recruitsOf(level, owner.getUUID());
        if (rs.isEmpty()) return reply(ctx, "SPIKE move FAIL no recruits");
        BlockPos t = surface(level, IntegerArgumentType.getInteger(ctx, "x"), IntegerArgumentType.getInteger(ctx, "z"));
        Vec3 target = Vec3.atBottomCenterOf(t);
        Vec3 centre = rs.get(0).position();
        Vec3 forward = new Vec3(target.x - centre.x, 0, target.z - centre.z);
        if (forward.lengthSqr() > 1e-6) forward = forward.normalize(); else forward = new Vec3(1, 0, 0);
        FormationUtils.squareFormation(forward, rs, target, 1.75D);
        return reply(ctx, "SPIKE move OK n=" + rs.size() + " target=" + t.toShortString() + " firstMovePos=" + rs.get(0).getMovePos());
    }

    private int enemies(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        int n = IntegerArgumentType.getInteger(ctx, "n");
        int x = IntegerArgumentType.getInteger(ctx, "x"), z = IntegerArgumentType.getInteger(ctx, "z");
        int spawned = 0;
        for (int i = 0; i < n; i++) {
            BlockPos p = surface(level, x + (i % 5) * 2, z + (i / 5) * 2);
            Zombie zb = EntityType.ZOMBIE.create(level);
            if (zb == null) continue;
            zb.moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 0, 0);
            zb.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            zb.setPersistenceRequired();
            zb.addTag("civ_enemy");
            level.addFreshEntity(zb);
            spawned++;
        }
        return reply(ctx, "SPIKE enemies OK spawned=" + spawned);
    }

    private int armyStatus(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        FakePlayer owner = proxy(level, StringArgumentType.getString(ctx, "owner"));
        List<AbstractRecruitEntity> rs = recruitsOf(level, owner.getUUID());
        StringBuilder sb = new StringBuilder("SPIKE armystatus alive=" + rs.size());
        double sx = 0, sz = 0;
        for (AbstractRecruitEntity r : rs) { sx += r.getX(); sz += r.getZ(); }
        if (!rs.isEmpty()) sb.append(String.format(" meanX=%.1f meanZ=%.1f", sx / rs.size(), sz / rs.size()));
        sb.append(" positions=[");
        for (AbstractRecruitEntity r : rs) sb.append(String.format("%.0f,%.0f(hp%.0f,fs%d) ", r.getX(), r.getZ(), r.getHealth(), r.getFollowState()));
        long zombies = level.getEntitiesOfClass(Zombie.class, new AABB(-1100, -64, -1100, 1100, 320, 1100), zb -> zb.getTags().contains("civ_enemy") && zb.isAlive()).size();
        sb.append("] enemiesAlive=").append(zombies);
        return reply(ctx, sb.toString());
    }

    // ---------------------------------------------------------------- Open Parties and Claims (S0.5)

    private int claim(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        FakePlayer owner = proxy(level, StringArgumentType.getString(ctx, "owner"));
        int cx = IntegerArgumentType.getInteger(ctx, "x") >> 4, cz = IntegerArgumentType.getInteger(ctx, "z") >> 4;
        var claims = OpenPACServerAPI.get(level.getServer()).getServerClaimsManager();
        var result = claims.tryToClaim(Level.OVERWORLD.location(), owner.getUUID(), 0, cx, cz, cx, cz, false);
        IPlayerChunkClaimAPI now = claims.get(Level.OVERWORLD.location(), cx, cz);
        return reply(ctx, "SPIKE claim chunk=" + cx + "," + cz + " result=" + result.getResultType()
            + " ownerNow=" + (now == null ? "none" : now.getPlayerId()));
    }
}

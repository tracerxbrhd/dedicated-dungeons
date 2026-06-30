package dev.underworld.dungeons.protection;

import dev.underworld.dungeons.config.DungeonServerConfig;
import dev.underworld.dungeons.runtime.DungeonRuntime;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.block.CreateFluidSourceEvent;

/** Central protection boundary for generated and legacy dungeon slots. */
public final class DungeonProtectionService {
    private DungeonProtectionService() {}

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (DungeonServerConfig.PROTECT_BLOCKS.get() && protectedPosition(event.getLevel(), event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (DungeonServerConfig.PROTECT_BLOCKS.get() && protectedPosition(event.getLevel(), event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (DungeonServerConfig.PROTECT_BLOCKS.get() && protectedPosition(event.getLevel(), event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!DungeonServerConfig.PROTECT_EXPLOSIONS.get() || !(event.getLevel() instanceof ServerLevel level)
            || level.dimension() != DungeonRuntime.ARENA_DIMENSION) return;
        DungeonRuntime runtime = DungeonRuntime.get(level.getServer());
        event.getAffectedBlocks().removeIf(runtime::contains);
    }

    @SubscribeEvent
    public static void onFluidBlock(BlockEvent.FluidPlaceBlockEvent event) {
        if (DungeonServerConfig.PROTECT_FIRE_AND_FLUIDS.get() && protectedPosition(event.getLevel(), event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFluidSource(CreateFluidSourceEvent event) {
        if (DungeonServerConfig.PROTECT_FIRE_AND_FLUIDS.get() && protectedPosition(event.getLevel(), event.getPos()))
            event.setCanConvert(false);
    }

    @SubscribeEvent
    public static void onDangerousUse(PlayerInteractEvent.RightClickBlock event) {
        if (!DungeonServerConfig.PROTECT_FIRE_AND_FLUIDS.get()) return;
        var item = event.getItemStack().getItem();
        boolean dangerous = item == Items.FLINT_AND_STEEL || item == Items.FIRE_CHARGE || item == Items.WATER_BUCKET
            || item == Items.LAVA_BUCKET || item == Items.POWDER_SNOW_BUCKET;
        if (dangerous && protectedPosition(event.getLevel(), event.getPos())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onTeleport(EntityTeleportEvent event) {
        if (!DungeonServerConfig.PROTECT_TELEPORTS.get() || !(event.getEntity() instanceof ServerPlayer player)
            || !(player.level() instanceof ServerLevel level) || level.dimension() != DungeonRuntime.ARENA_DIMENSION) return;
        DungeonRuntime runtime = DungeonRuntime.get(level.getServer());
        if (runtime.insideAssignedSession(player, player.blockPosition())
            && !runtime.insideAssignedSession(player, BlockPos.containing(event.getTarget()))) event.setCanceled(true);
    }

    private static boolean protectedPosition(net.minecraft.world.level.LevelAccessor accessor, BlockPos position) {
        if (!(accessor instanceof ServerLevel level) || level.dimension() != DungeonRuntime.ARENA_DIMENSION) return false;
        return DungeonRuntime.get(level.getServer()).contains(position);
    }
}

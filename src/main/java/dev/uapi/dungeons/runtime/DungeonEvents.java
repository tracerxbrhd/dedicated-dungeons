package dev.uapi.dungeons.runtime;

import dev.uapi.difficulty.DifficultyRank;
import dev.uapi.event.UApiEvents;
import dev.uapi.instance.InstanceManager;
import dev.uapi.dungeons.config.DungeonServerConfig;
import dev.uapi.dungeons.portal.PortalOrigin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;

public final class DungeonEvents {
    private static int secondTicks;
    private static long nextRandomPortalTick;
    private DungeonEvents() {}

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        PortalManager.get(event.getServer()).tickLightning();
        if (++secondTicks >= 20) {
            secondTicks = 0;
            PortalManager.get(event.getServer()).tick();
            DungeonRuntime.get(event.getServer()).tick();
        }
        if (!DungeonServerConfig.RANDOM_PORTALS.get()
            || event.getServer().getTickCount() < nextRandomPortalTick) return;
        int extra = DungeonServerConfig.PORTAL_CHECK_RANDOM_TICKS.get();
        if (nextRandomPortalTick == 0) {
            nextRandomPortalTick = event.getServer().getTickCount() + DungeonServerConfig.PORTAL_CHECK_INTERVAL.get()
                + (extra <= 0 ? 0 : event.getServer().overworld().random.nextInt(extra + 1));
            return;
        }
        nextRandomPortalTick = event.getServer().getTickCount() + DungeonServerConfig.PORTAL_CHECK_INTERVAL.get()
            + (extra <= 0 ? 0 : event.getServer().overworld().random.nextInt(extra + 1));
        PortalManager portals = PortalManager.get(event.getServer());
        int maxPortals = DungeonServerConfig.MAX_ACTIVE_PORTALS.get();
        if (maxPortals > 0 && portals.activeCount() >= maxPortals) return;
        List<ServerPlayer> candidates = event.getServer().getPlayerList().getPlayers().stream()
            .filter(player -> player.isAlive() && !player.isSpectator())
            .filter(player -> allowedDimension(player.level().dimension().location()))
            .filter(player -> InstanceManager.get(event.getServer()).findByPlayer(player.getUUID()).isEmpty())
            .toList();
        if (candidates.isEmpty()) return;
        int start = event.getServer().overworld().random.nextInt(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            ServerPlayer selected = candidates.get((start + i) % candidates.size());
            if (portals.createFor(selected, randomRank(selected), PortalOrigin.RANDOM).isPresent()) break;
        }
    }

    @SubscribeEvent
    public static void onInstanceLifecycle(UApiEvents.InstanceLifecycle event) {
        if (event.stage() != UApiEvents.InstanceLifecycle.Stage.COMPLETED
            && event.stage() != UApiEvents.InstanceLifecycle.Stage.FAILED) return;
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        PortalManager portals = PortalManager.get(server);
        DungeonRuntime runtime = DungeonRuntime.get(server);
        if (event.stage() == UApiEvents.InstanceLifecycle.Stage.FAILED) {
            boolean waitingPortal = portals.shatterForInstance(event.instance().id(), true);
            if (!waitingPortal && "all_players_dead".equals(event.reason())) {
                var session = runtime.session(event.instance().id());
                if (session != null) portals.shatterAt(session.originDimension(), session.originPosition(),
                    event.instance().difficulty(), true);
            }
        }
        if (runtime.hasSession(event.instance().id())) runtime.requestFinish(event.instance());
        else InstanceManager.get(server).remove(event.instance().id(), "terminal_without_session");
        if (event.stage() == UApiEvents.InstanceLifecycle.Stage.COMPLETED)
            portals.closeForInstance(event.instance().id());
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        var server = event.getEntity().getServer(); if (server == null) return;
        DungeonRuntime runtime = DungeonRuntime.get(server);
        if (event.getEntity() instanceof ServerPlayer player) runtime.hideBossBars(player);
        runtime.bossKilled(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            DungeonRuntime.get(player.getServer()).recoverPendingReturn(player);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        DungeonRuntime runtime = DungeonRuntime.get(player.getServer());
        runtime.hideBossBars(player);
        runtime.recoverPendingReturn(player);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        releaseServer(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        // Idempotent second boundary catches a late lifecycle callback that recreated a runtime.
        releaseServer(event.getServer());
    }

    private static void releaseServer(net.minecraft.server.MinecraftServer server) {
        PortalManager.stop(server);
        DungeonRuntime.stop(server);
        secondTicks = 0;
        nextRandomPortalTick = 0;
    }

    private static boolean allowedDimension(ResourceLocation id) {
        for (String value : DungeonServerConfig.ALLOWED_DIMENSIONS.get().split(","))
            if (value.trim().equals(id.toString())) return true;
        return false;
    }

    private static DifficultyRank randomRank(ServerPlayer player) {
        int roll = player.getRandom().nextInt(1000);
        if (roll < 2) return DifficultyRank.ANOMALY;
        if (roll < 7) return DifficultyRank.S;
        if (roll < 27) return DifficultyRank.A;
        if (roll < 100) return DifficultyRank.B;
        if (roll < 300) return DifficultyRank.C;
        if (roll < 600) return DifficultyRank.D;
        return DifficultyRank.E;
    }
}

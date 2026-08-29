package dev.uapi.dungeons.network;

import dev.uapi.dungeons.DedicatedDungeonsMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Clears session tokens and rate-limit buckets at their owning lifecycle boundary. */
@EventBusSubscriber(modid = DedicatedDungeonsMod.MOD_ID)
public final class DungeonDeploymentSessionEvents {
    private DungeonDeploymentSessionEvents() {
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null)
            DungeonDeploymentServerController.clearPlayer(player.getServer(), player.getUUID());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        DungeonDeploymentServerController.clearServer(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        DungeonDeploymentServerController.clearServer(event.getServer());
    }
}

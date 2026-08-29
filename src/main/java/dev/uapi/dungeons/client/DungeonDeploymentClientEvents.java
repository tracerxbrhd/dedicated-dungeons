package dev.uapi.dungeons.client;

import dev.uapi.dungeons.DedicatedDungeonsMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Client connection lifecycle for short-lived deployment tokens. */
@EventBusSubscriber(modid = DedicatedDungeonsMod.MOD_ID, value = Dist.CLIENT)
public final class DungeonDeploymentClientEvents {
    private DungeonDeploymentClientEvents() {
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        DungeonDeploymentClientPayloadHandlers.clearSession();
    }
}

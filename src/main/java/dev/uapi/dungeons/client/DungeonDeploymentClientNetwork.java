package dev.uapi.dungeons.client;

import dev.uapi.dungeons.DedicatedDungeonsMod;
import dev.uapi.dungeons.network.DungeonDeploymentNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Client-only payload registration; this class is never discovered on a dedicated server. */
@EventBusSubscriber(modid = DedicatedDungeonsMod.MOD_ID, value = Dist.CLIENT)
public final class DungeonDeploymentClientNetwork {
    private DungeonDeploymentClientNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        DungeonDeploymentClientPayloadHandlers.register(event.registrar(DungeonDeploymentNetwork.PROTOCOL_VERSION));
    }
}

package dev.uapi.dungeons.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Versioned deployment channel. All C2S work is re-enqueued onto the authoritative server thread. */
public final class DungeonDeploymentNetwork {
    public static final String PROTOCOL_VERSION = "1";
    public static final int SCHEMA_VERSION = 1;

    private DungeonDeploymentNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(DungeonDeploymentActionPayload.TYPE,
            DungeonDeploymentActionPayload.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof ServerPlayer player)
                    DungeonDeploymentServerController.handle(player, payload);
            }));
        if (FMLEnvironment.dist != Dist.CLIENT) registerServerNoops(registrar);
    }

    private static void registerServerNoops(PayloadRegistrar registrar) {
        registrar.playToClient(DungeonDeploymentOpenPayload.TYPE, DungeonDeploymentOpenPayload.STREAM_CODEC,
            (payload, context) -> { });
        registrar.playToClient(DungeonDeploymentStatePayload.TYPE, DungeonDeploymentStatePayload.STREAM_CODEC,
            (payload, context) -> { });
    }
}

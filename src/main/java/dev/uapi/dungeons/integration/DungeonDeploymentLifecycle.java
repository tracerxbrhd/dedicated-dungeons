package dev.uapi.dungeons.integration;

import dev.uapi.api.services.ServiceRegistration;
import dev.uapi.api.services.ServiceScope;
import dev.uapi.api.services.UApiServices;
import dev.uapi.dungeons.api.DungeonDeploymentService;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Publishes one server-scoped deployment adapter and releases it before shutdown. */
public final class DungeonDeploymentLifecycle {
    private static ServiceRegistration registration;

    private DungeonDeploymentLifecycle() {
    }

    @SubscribeEvent
    public static synchronized void onServerStarted(ServerStartedEvent event) {
        closeRegistration();
        registration = UApiServices.register(DungeonDeploymentService.class,
            new UApiDungeonDeploymentService(), ServiceScope.SERVER);
    }

    @SubscribeEvent
    public static synchronized void onServerStopping(ServerStoppingEvent event) {
        closeRegistration();
    }

    private static void closeRegistration() {
        if (registration == null) return;
        registration.close();
        registration = null;
    }
}

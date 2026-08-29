package dev.uapi.dungeons.client;

import dev.uapi.dungeons.network.DungeonDeploymentAction;
import dev.uapi.dungeons.network.DungeonDeploymentActionPayload;
import dev.uapi.dungeons.network.DungeonDeploymentNetwork;
import dev.uapi.dungeons.network.DungeonDeploymentOpenPayload;
import dev.uapi.dungeons.network.DungeonDeploymentResponseStatus;
import dev.uapi.dungeons.network.DungeonDeploymentStatePayload;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Client-only payload bridge; no class in this file is loaded by a dedicated server handler. */
@OnlyIn(Dist.CLIENT)
public final class DungeonDeploymentClientPayloadHandlers {
    private static ClientSession active;

    private DungeonDeploymentClientPayloadHandlers() {
    }

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(DungeonDeploymentOpenPayload.TYPE, DungeonDeploymentOpenPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> open(payload)));
        registrar.playToClient(DungeonDeploymentStatePayload.TYPE, DungeonDeploymentStatePayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> update(payload)));
    }

    /** Drops the client token when the connection that issued it goes away. */
    public static void clearSession() {
        ClientSession session = active;
        active = null;
        if (session == null) return;
        session.pending = false;
        session.sessionActive = false;
        if (session.screen != null) {
            session.screen.setPending(false);
            session.screen.setSessionActive(false);
        }
    }

    private static void open(DungeonDeploymentOpenPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload.schemaVersion() != DungeonDeploymentNetwork.SCHEMA_VERSION) {
            if (minecraft.player != null) minecraft.player.displayClientMessage(Component.translatable(
                "screen.dedicated_dungeons.deployment.result.invalid_request"), false);
            return;
        }
        ClientSession session = new ClientSession(payload.sessionId());
        DungeonDeploymentScreen screen = new DungeonDeploymentScreen(payload.rank(), payload.archetypeId(),
            payload.deployment(), session);
        session.screen = screen;
        active = session;
        minecraft.setScreen(screen);
    }

    private static void update(DungeonDeploymentStatePayload payload) {
        ClientSession session = active;
        if (session == null || !session.sessionId.equals(payload.sessionId()))
            return;
        if (payload.schemaVersion() != DungeonDeploymentNetwork.SCHEMA_VERSION) {
            session.pending = false;
            session.sessionActive = false;
            session.screen.setPending(false);
            session.screen.setSessionActive(false);
            session.screen.showStatus(Component.translatable(
                "screen.dedicated_dungeons.deployment.result.invalid_request"), true);
            return;
        }
        if (payload.requestId() <= session.lastAppliedRequest) return;
        session.lastAppliedRequest = payload.requestId();
        session.pending = false;
        session.screen.update(payload.deployment());
        session.screen.setPending(false);
        boolean error = switch (payload.status()) {
            case OPENED, REFRESHED, READY_CHECK_STARTED, LAUNCHED -> false;
            default -> true;
        };
        Component message = Component.translatable("screen.dedicated_dungeons.deployment.result."
            + payload.status().name().toLowerCase(java.util.Locale.ROOT));
        session.screen.showStatus(message, error);
        if (payload.status() == DungeonDeploymentResponseStatus.LAUNCHED) {
            session.sessionActive = false;
            Minecraft.getInstance().setScreen(null);
        } else if (payload.status() == DungeonDeploymentResponseStatus.SESSION_EXPIRED) {
            session.sessionActive = false;
            session.screen.setSessionActive(false);
        }
    }

    private static final class ClientSession implements DungeonDeploymentScreen.Actions {
        private final UUID sessionId;
        private DungeonDeploymentScreen screen;
        private long nextRequestId = 1;
        private long lastAppliedRequest;
        private int refreshTicks;
        private boolean pending;
        private boolean sessionActive = true;

        private ClientSession(UUID sessionId) {
            this.sessionId = sessionId;
        }

        @Override public void refresh() { send(DungeonDeploymentAction.REFRESH); }

        @Override public void startReadyCheck() { send(DungeonDeploymentAction.START_READY_CHECK); }

        @Override public void deploy() { send(DungeonDeploymentAction.LAUNCH); }

        @Override
        public void tick() {
            if (!sessionActive) return;
            if (++refreshTicks < 20) return;
            refreshTicks = 0;
            if (!pending && screen.snapshot().readiness() == dev.uapi.dungeons.api.DungeonDeploymentReadiness.CHECKING)
                send(DungeonDeploymentAction.REFRESH);
        }

        @Override
        public void closed() {
            sessionActive = false;
            if (active == this) active = null;
        }

        private void send(DungeonDeploymentAction action) {
            if (active != this || !sessionActive || pending) return;
            long requestId = nextRequestId++;
            if (requestId < 0) {
                screen.setSessionActive(false);
                return;
            }
            pending = true;
            screen.setPending(true);
            PacketDistributor.sendToServer(new DungeonDeploymentActionPayload(
                DungeonDeploymentNetwork.SCHEMA_VERSION, sessionId, requestId, action));
        }
    }
}

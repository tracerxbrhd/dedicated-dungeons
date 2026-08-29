package dev.uapi.dungeons.network;

import dev.uapi.api.network.ActorActionRateLimiter;
import dev.uapi.api.network.RateLimitDecision;
import dev.uapi.api.services.ServiceScope;
import dev.uapi.api.services.UApiServices;
import dev.uapi.difficulty.DifficultyRank;
import dev.uapi.dungeons.DedicatedDungeonsMod;
import dev.uapi.dungeons.api.DungeonDeployment;
import dev.uapi.dungeons.api.DungeonDeploymentReadiness;
import dev.uapi.dungeons.api.DungeonDeploymentService;
import dev.uapi.dungeons.config.DungeonServerConfig;
import dev.uapi.dungeons.content.DungeonContentRegistry;
import dev.uapi.dungeons.item.DungeonKeyItem;
import dev.uapi.dungeons.runtime.PortalManager;
import java.time.Duration;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-owned deployment sessions. Every mutation is rate-limited and bound to the opening key. */
public final class DungeonDeploymentServerController {
    private static final ResourceLocation OPEN_ACTION = id("open");
    private static final Map<DungeonDeploymentAction, ResourceLocation> ACTION_IDS = Map.of(
        DungeonDeploymentAction.REFRESH, id("refresh"),
        DungeonDeploymentAction.START_READY_CHECK, id("ready_check"),
        DungeonDeploymentAction.LAUNCH, id("launch"));
    private static final Map<MinecraftServer, ServerState> STATES = new IdentityHashMap<>();

    private DungeonDeploymentServerController() {
    }

    public static synchronized boolean openFromKey(
        ServerPlayer player,
        DifficultyRank rank,
        InteractionHand hand
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(rank, "rank");
        Objects.requireNonNull(hand, "hand");
        if (!player.isAlive() || player.isRemoved() || player.isSpectator()) return false;
        ItemStack openingStack = player.getItemInHand(hand);
        if (!(openingStack.getItem() instanceof DungeonKeyItem key) || key.rank() != rank
            || openingStack.isEmpty()) return false;
        ServerState state = state(player.getServer());
        if (!state.openLimiter.tryAcquire(player.getUUID(), OPEN_ACTION).allowed()) return false;
        DungeonDeployment deployment;
        try {
            deployment = service().map(value -> value.preview(player.getUUID()))
                .orElseGet(() -> unavailable(player.getUUID()));
        } catch (RuntimeException exception) {
            DedicatedDungeonsMod.LOGGER.error("Deployment preview failed for {}", player.getUUID(), exception);
            deployment = unavailable(player.getUUID());
        }
        deployment = bounded(player.getUUID(), deployment);
        if (!deployment.ownerId().equals(player.getUUID())) return false;
        long now = System.currentTimeMillis();
        long timeoutMillis = Duration.ofSeconds(DungeonServerConfig.DEPLOYMENT_SESSION_TIMEOUT_SECONDS.get())
            .toMillis();
        Session session = new Session(UUID.randomUUID(), player.getUUID(), rank,
            DungeonContentRegistry.DEFAULT_DUNGEON, hand, now + timeoutMillis, deployment);
        state.sessions.put(player.getUUID(), session);
        PacketDistributor.sendToPlayer(player, new DungeonDeploymentOpenPayload(
            DungeonDeploymentNetwork.SCHEMA_VERSION, session.id, rank, session.archetypeId, deployment));
        return true;
    }

    public static synchronized void handle(ServerPlayer player, DungeonDeploymentActionPayload payload) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(payload, "payload");
        ServerState state = state(player.getServer());
        Session session = state.sessions.get(player.getUUID());
        RateLimitDecision rate = state.actionLimiter.tryAcquire(player.getUUID(), ACTION_IDS.get(payload.action()));
        if (!rate.allowed()) {
            respond(player, payload, DungeonDeploymentResponseStatus.RATE_LIMITED,
                session == null ? unavailable(player.getUUID()) : session.lastDeployment);
            return;
        }
        if (payload.schemaVersion() != DungeonDeploymentNetwork.SCHEMA_VERSION) {
            respond(player, payload, DungeonDeploymentResponseStatus.INVALID_REQUEST,
                session == null ? unavailable(player.getUUID()) : session.lastDeployment);
            return;
        }
        long now = System.currentTimeMillis();
        if (session == null || !player.isAlive() || player.isRemoved() || player.isSpectator()
            || !session.playerId.equals(player.getUUID())
            || !session.id.equals(payload.sessionId()) || now >= session.expiresAtMillis) {
            if (session != null && (now >= session.expiresAtMillis || !player.isAlive()
                || player.isRemoved() || player.isSpectator())) state.sessions.remove(player.getUUID(), session);
            respond(player, payload, DungeonDeploymentResponseStatus.SESSION_EXPIRED,
                session == null ? unavailable(player.getUUID()) : session.lastDeployment);
            return;
        }
        if (payload.requestId() <= session.lastRequestId) {
            respond(player, payload, DungeonDeploymentResponseStatus.INVALID_REQUEST, session.lastDeployment);
            return;
        }
        session.lastRequestId = payload.requestId();
        switch (payload.action()) {
            case REFRESH -> refresh(player, payload, session, false);
            case START_READY_CHECK -> refresh(player, payload, session, true);
            case LAUNCH -> launch(player, payload, state, session);
        }
    }

    public static synchronized void clearPlayer(MinecraftServer server, UUID playerId) {
        ServerState state = STATES.get(server);
        if (state == null) return;
        state.sessions.remove(playerId);
        state.openLimiter.clearActor(playerId);
        state.actionLimiter.clearActor(playerId);
    }

    public static synchronized void clearServer(MinecraftServer server) {
        ServerState state = STATES.remove(server);
        if (state == null) return;
        state.sessions.clear();
        state.openLimiter.clear();
        state.actionLimiter.clear();
    }

    private static void refresh(ServerPlayer player, DungeonDeploymentActionPayload payload,
                                Session session, boolean startReadyCheck) {
        if (startReadyCheck && !matchingKey(player, session)) {
            respond(player, payload, DungeonDeploymentResponseStatus.KEY_MISSING, session.lastDeployment);
            return;
        }
        DungeonDeploymentService service = service().orElse(null);
        if (service == null) {
            session.lastDeployment = unavailable(player.getUUID());
            respond(player, payload, DungeonDeploymentResponseStatus.NOT_READY, session.lastDeployment);
            return;
        }
        try {
            session.lastDeployment = startReadyCheck
                ? service.startReadyCheck(player.getUUID(), Duration.ofSeconds(
                    DungeonServerConfig.DEPLOYMENT_READY_TIMEOUT_SECONDS.get()))
                : service.refresh(player.getUUID(), session.lastDeployment.readyCheckId());
            session.lastDeployment = bounded(player.getUUID(), session.lastDeployment);
            boolean activeCheck = session.lastDeployment.readiness() == DungeonDeploymentReadiness.CHECKING
                || session.lastDeployment.readiness() == DungeonDeploymentReadiness.ALL_READY;
            respond(player, payload, startReadyCheck
                ? activeCheck ? DungeonDeploymentResponseStatus.READY_CHECK_STARTED
                    : DungeonDeploymentResponseStatus.NOT_READY
                : DungeonDeploymentResponseStatus.REFRESHED, session.lastDeployment);
        } catch (RuntimeException exception) {
            DedicatedDungeonsMod.LOGGER.error("Deployment refresh failed for {}", player.getUUID(), exception);
            respond(player, payload, DungeonDeploymentResponseStatus.INVALID_REQUEST, session.lastDeployment);
        }
    }

    private static void launch(ServerPlayer player, DungeonDeploymentActionPayload payload,
                               ServerState state, Session session) {
        DungeonDeploymentService service = service().orElse(null);
        if (service == null) {
            session.lastDeployment = unavailable(player.getUUID());
            respond(player, payload, DungeonDeploymentResponseStatus.NOT_READY, session.lastDeployment);
            return;
        }
        try {
            // First refresh drives a useful response. PortalManager refreshes again immediately
            // before InstanceManager.create, so this check cannot authorize a later stale launch.
            session.lastDeployment = bounded(player.getUUID(), service.refresh(
                player.getUUID(), session.lastDeployment.readyCheckId()));
            boolean keyPresent = matchingKey(player, session);
            DungeonDeploymentLaunchPolicy.Decision decision = DungeonDeploymentLaunchPolicy.evaluate(
                player.getUUID(), session.lastDeployment, keyPresent);
            if (decision != DungeonDeploymentLaunchPolicy.Decision.ALLOW) {
                DungeonDeploymentResponseStatus status = decision == DungeonDeploymentLaunchPolicy.Decision.KEY_MISSING
                    ? DungeonDeploymentResponseStatus.KEY_MISSING
                    : decision == DungeonDeploymentLaunchPolicy.Decision.NOT_READY
                        ? DungeonDeploymentResponseStatus.NOT_READY
                        : DungeonDeploymentResponseStatus.INVALID_REQUEST;
                respond(player, payload, status, session.lastDeployment);
                return;
            }

            PortalManager.DeploymentLaunchResult result = PortalManager.get(player.getServer()).launchDeployment(
                player, session.rank, session.archetypeId, session.lastDeployment);
            session.lastDeployment = bounded(player.getUUID(), result.deployment());
            if (!result.launched()) {
                respond(player, payload, session.lastDeployment.canDeploy()
                    ? DungeonDeploymentResponseStatus.LAUNCH_FAILED
                    : DungeonDeploymentResponseStatus.NOT_READY, session.lastDeployment);
                return;
            }
            ItemStack key = player.getItemInHand(session.hand);
            if (DungeonServerConfig.CONSUME_KEY.get() && !player.getAbilities().instabuild) key.shrink(1);
            state.sessions.remove(player.getUUID(), session);
            respond(player, payload, DungeonDeploymentResponseStatus.LAUNCHED, session.lastDeployment);
        } catch (RuntimeException exception) {
            DedicatedDungeonsMod.LOGGER.error("Deployment launch failed for {}", player.getUUID(), exception);
            respond(player, payload, DungeonDeploymentResponseStatus.LAUNCH_FAILED, session.lastDeployment);
        }
    }

    private static boolean matchingKey(ServerPlayer player, Session session) {
        ItemStack stack = player.getItemInHand(session.hand);
        return stack.getItem() instanceof DungeonKeyItem key && key.rank() == session.rank && !stack.isEmpty();
    }

    private static void respond(ServerPlayer player, DungeonDeploymentActionPayload request,
                                DungeonDeploymentResponseStatus status, DungeonDeployment deployment) {
        PacketDistributor.sendToPlayer(player, new DungeonDeploymentStatePayload(
            DungeonDeploymentNetwork.SCHEMA_VERSION, request.sessionId(), request.requestId(), status, deployment));
    }

    private static Optional<DungeonDeploymentService> service() {
        return UApiServices.find(DungeonDeploymentService.class, ServiceScope.SERVER);
    }

    private static DungeonDeployment unavailable(UUID playerId) {
        return new DungeonDeployment(playerId, Optional.empty(), Set.of(playerId), Optional.empty(),
            DungeonDeploymentReadiness.SERVICE_UNAVAILABLE, 0);
    }

    private static DungeonDeployment bounded(UUID playerId, DungeonDeployment deployment) {
        if (!deployment.ownerId().equals(playerId)) {
            DedicatedDungeonsMod.LOGGER.error("Deployment provider returned owner {} for actor {}",
                deployment.ownerId(), playerId);
            return unavailable(playerId);
        }
        if (deployment.participants().size() <= DungeonDeploymentWireCodec.MAXIMUM_PARTICIPANTS)
            return deployment;
        DedicatedDungeonsMod.LOGGER.error("Deployment for {} exceeds the {} participant wire bound",
            playerId, DungeonDeploymentWireCodec.MAXIMUM_PARTICIPANTS);
        return unavailable(playerId);
    }

    private static ServerState state(MinecraftServer server) {
        return STATES.computeIfAbsent(server, ignored -> new ServerState());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(DedicatedDungeonsMod.MOD_ID, "deployment/" + path);
    }

    private static final class ServerState {
        private final Map<UUID, Session> sessions = new HashMap<>();
        private final ActorActionRateLimiter openLimiter = new ActorActionRateLimiter(
            2, Duration.ofSeconds(1), 4096);
        private final ActorActionRateLimiter actionLimiter = new ActorActionRateLimiter(
            4, Duration.ofSeconds(1), 16_384);
    }

    private static final class Session {
        private final UUID id;
        private final UUID playerId;
        private final DifficultyRank rank;
        private final ResourceLocation archetypeId;
        private final InteractionHand hand;
        private final long expiresAtMillis;
        private long lastRequestId;
        private DungeonDeployment lastDeployment;

        private Session(UUID id, UUID playerId, DifficultyRank rank, ResourceLocation archetypeId,
                        InteractionHand hand, long expiresAtMillis, DungeonDeployment lastDeployment) {
            this.id = id;
            this.playerId = playerId;
            this.rank = rank;
            this.archetypeId = archetypeId;
            this.hand = hand;
            this.expiresAtMillis = expiresAtMillis;
            this.lastDeployment = lastDeployment;
        }
    }
}

package dev.uapi.dungeons.integration;

import dev.uapi.api.services.ServiceScope;
import dev.uapi.api.services.UApiServices;
import dev.uapi.api.social.ReadyCheckPhase;
import dev.uapi.api.social.ReadyCheckService;
import dev.uapi.api.social.ReadyCheckSnapshot;
import dev.uapi.api.social.ReadyCheckStartRequest;
import dev.uapi.api.social.ReadyCheckUpdateResult;
import dev.uapi.api.social.SocialGroup;
import dev.uapi.api.social.SocialGroupService;
import dev.uapi.dungeons.DedicatedDungeonsMod;
import dev.uapi.dungeons.api.DungeonDeployment;
import dev.uapi.dungeons.api.DungeonDeploymentReadiness;
import dev.uapi.dungeons.api.DungeonDeploymentService;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Optional Guild/Party adapter. Absence of a provider deliberately resolves to a solo deployment. */
public final class UApiDungeonDeploymentService implements DungeonDeploymentService {
    private static final ResourceLocation SERVICE_ID = ResourceLocation.fromNamespaceAndPath(
        DedicatedDungeonsMod.MOD_ID, "deployment");

    @Override
    public ResourceLocation serviceId() {
        return SERVICE_ID;
    }

    @Override
    public DungeonDeployment preview(UUID ownerId) {
        Objects.requireNonNull(ownerId, "ownerId");
        Optional<SocialGroup> party = activeParty(ownerId);
        if (party.isEmpty()) return solo(ownerId);
        return snapshot(ownerId, party.orElseThrow(), activeCheck(party.orElseThrow().groupId()),
            DungeonDeploymentReadiness.NOT_REQUESTED);
    }

    @Override
    public DungeonDeployment startReadyCheck(UUID ownerId, Duration timeout) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative())
            throw new IllegalArgumentException("timeout must be positive");

        Optional<SocialGroup> party = activeParty(ownerId);
        if (party.isEmpty()) return solo(ownerId);
        SocialGroup group = party.orElseThrow();
        Optional<ReadyCheckService> readyChecks = readyChecks();
        if (readyChecks.isEmpty())
            return snapshot(ownerId, group, Optional.empty(), DungeonDeploymentReadiness.SERVICE_UNAVAILABLE);

        Optional<ReadyCheckSnapshot> active = readyChecks.orElseThrow().getActiveReadyCheck(group.groupId());
        if (active.isPresent()) return snapshot(ownerId, group, active, DungeonDeploymentReadiness.NOT_REQUESTED);

        SocialGroupService social = socialGroups().orElseThrow();
        if (!social.canInitiateReadyCheck(group.groupId(), ownerId))
            return snapshot(ownerId, group, Optional.empty(), DungeonDeploymentReadiness.UNAUTHORIZED);
        Set<UUID> participants = participants(social, group, ownerId);
        ReadyCheckUpdateResult result = readyChecks.orElseThrow().startReadyCheck(
            new ReadyCheckStartRequest(group.groupId(), ownerId, participants, timeout));
        return snapshot(ownerId, group, result.snapshot(), result.applied()
            ? DungeonDeploymentReadiness.CHECKING
            : DungeonDeploymentReadiness.UNAUTHORIZED);
    }

    @Override
    public DungeonDeployment refresh(UUID ownerId) {
        return refresh(ownerId, Optional.empty());
    }

    @Override
    public DungeonDeployment refresh(UUID ownerId, Optional<UUID> observedReadyCheckId) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(observedReadyCheckId, "observedReadyCheckId");
        Optional<SocialGroup> party = activeParty(ownerId);
        if (party.isEmpty()) return solo(ownerId);
        SocialGroup group = party.orElseThrow();
        Optional<ReadyCheckService> readyChecks = readyChecks();
        if (readyChecks.isEmpty())
            return snapshot(ownerId, group, Optional.empty(), DungeonDeploymentReadiness.SERVICE_UNAVAILABLE);
        ReadyCheckService service = readyChecks.orElseThrow();
        Optional<ReadyCheckSnapshot> active = service.getActiveReadyCheck(group.groupId());
        Optional<ReadyCheckSnapshot> check = active.isPresent() ? active : observedReadyCheckId
            .flatMap(service::getReadyCheck)
            .filter(value -> value.groupId().equals(group.groupId()));
        return snapshot(ownerId, group, check, DungeonDeploymentReadiness.NOT_REQUESTED);
    }

    @Override
    public boolean isEligibleParticipant(UUID groupId, UUID playerId) {
        Objects.requireNonNull(groupId, "groupId");
        Objects.requireNonNull(playerId, "playerId");
        return socialGroups().map(service -> service.isMember(groupId, playerId)).orElse(false);
    }

    @Override
    public boolean canLaunch(UUID ownerId, DungeonDeployment deployment) {
        Objects.requireNonNull(ownerId, "ownerId");
        Objects.requireNonNull(deployment, "deployment");
        if (!deployment.ownerId().equals(ownerId)) return false;
        if (deployment.groupId().isEmpty()) return deployment.readiness().canDeploy();
        UUID groupId = deployment.groupId().orElseThrow();
        if (deployment.readiness() != DungeonDeploymentReadiness.ALL_READY
            || deployment.readyCheckId().isEmpty()) return false;
        SocialGroupService social = socialGroups().orElse(null);
        ReadyCheckService ready = readyChecks().orElse(null);
        if (social == null || ready == null || !social.isMember(groupId, ownerId)
            || !social.canInitiateReadyCheck(groupId, ownerId)) return false;
        Optional<SocialGroup> currentGroup = activeParty(ownerId)
            .filter(group -> group.groupId().equals(groupId));
        if (currentGroup.isEmpty()) return false;
        Set<UUID> currentParticipants = participants(social, currentGroup.orElseThrow(), ownerId);
        if (!currentParticipants.equals(deployment.participants())) return false;
        UUID checkId = deployment.readyCheckId().orElseThrow();
        Optional<ReadyCheckSnapshot> active = ready.getActiveReadyCheck(groupId);
        if (active.isPresent() && !active.orElseThrow().checkId().equals(checkId)) return false;
        return ready.getReadyCheck(checkId)
            .filter(check -> check.groupId().equals(groupId))
            .filter(check -> check.phase() == ReadyCheckPhase.ALL_READY)
            .filter(check -> check.participants().keySet().equals(currentParticipants))
            .isPresent();
    }

    private Optional<SocialGroup> activeParty(UUID ownerId) {
        return socialGroups().flatMap(service -> service.getActiveParty(ownerId))
            .filter(group -> group.contains(ownerId));
    }

    private Optional<ReadyCheckSnapshot> activeCheck(UUID groupId) {
        return readyChecks().flatMap(service -> service.getActiveReadyCheck(groupId));
    }

    private DungeonDeployment snapshot(
        UUID ownerId,
        SocialGroup group,
        Optional<ReadyCheckSnapshot> check,
        DungeonDeploymentReadiness fallback
    ) {
        SocialGroupService social = socialGroups().orElse(null);
        Set<UUID> participants = participants(social, group, ownerId);
        Optional<ReadyCheckSnapshot> applicableCheck = check
            .filter(value -> value.groupId().equals(group.groupId()))
            .filter(value -> value.participants().keySet().equals(participants));
        DungeonDeploymentReadiness readiness = applicableCheck.map(value -> readiness(value.phase())).orElse(fallback);
        long revision = Math.max(group.revision(), applicableCheck.map(ReadyCheckSnapshot::revision).orElse(0L));
        return new DungeonDeployment(ownerId, Optional.of(group.groupId()), participants,
            applicableCheck.map(ReadyCheckSnapshot::checkId), readiness, revision);
    }

    private static Set<UUID> participants(SocialGroupService service, SocialGroup group, UUID ownerId) {
        LinkedHashSet<UUID> participants = new LinkedHashSet<>();
        if (service != null) participants.addAll(service.getReadyCheckParticipants(group.groupId()));
        if (participants.isEmpty()) participants.addAll(group.memberIds());
        participants.retainAll(group.memberIds());
        participants.add(ownerId);
        return participants;
    }

    private static DungeonDeploymentReadiness readiness(ReadyCheckPhase phase) {
        return switch (phase) {
            case ACTIVE -> DungeonDeploymentReadiness.CHECKING;
            case ALL_READY -> DungeonDeploymentReadiness.ALL_READY;
            case DECLINED -> DungeonDeploymentReadiness.DECLINED;
            case TIMED_OUT -> DungeonDeploymentReadiness.TIMED_OUT;
            case CANCELLED -> DungeonDeploymentReadiness.CANCELLED;
        };
    }

    private static DungeonDeployment solo(UUID ownerId) {
        return new DungeonDeployment(ownerId, Optional.empty(), Set.of(ownerId), Optional.empty(),
            DungeonDeploymentReadiness.SOLO_READY, 0L);
    }

    private static Optional<SocialGroupService> socialGroups() {
        return UApiServices.find(SocialGroupService.class, ServiceScope.SERVER);
    }

    private static Optional<ReadyCheckService> readyChecks() {
        return UApiServices.find(ReadyCheckService.class, ServiceScope.SERVER);
    }
}

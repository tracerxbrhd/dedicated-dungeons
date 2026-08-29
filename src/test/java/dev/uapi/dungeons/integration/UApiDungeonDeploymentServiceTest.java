package dev.uapi.dungeons.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.uapi.api.services.ServiceRegistration;
import dev.uapi.api.services.ServiceScope;
import dev.uapi.api.services.UApiServices;
import dev.uapi.api.social.ReadyCheckParticipantState;
import dev.uapi.api.social.ReadyCheckPhase;
import dev.uapi.api.social.ReadyCheckResult;
import dev.uapi.api.social.ReadyCheckService;
import dev.uapi.api.social.ReadyCheckSnapshot;
import dev.uapi.api.social.ReadyCheckStartRequest;
import dev.uapi.api.social.ReadyCheckUpdateResult;
import dev.uapi.api.social.SocialGroup;
import dev.uapi.api.social.SocialGroupMember;
import dev.uapi.api.social.SocialGroupRoleFlag;
import dev.uapi.api.social.SocialGroupService;
import dev.uapi.api.social.SocialGroupType;
import dev.uapi.dungeons.api.DungeonDeployment;
import dev.uapi.dungeons.api.DungeonDeploymentReadiness;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class UApiDungeonDeploymentServiceTest {
    @Test
    void retainsObservedTerminalReadyCheckAndRejectsChangedParticipants() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID groupId = UUID.randomUUID();
        UUID checkId = UUID.randomUUID();
        MutableSocialGroups social = new MutableSocialGroups(group(groupId, owner, member));
        TerminalReadyChecks ready = new TerminalReadyChecks(allReady(checkId, groupId, owner, member));

        try (ServiceRegistration ignoredSocial = UApiServices.register(
                 SocialGroupService.class, social, ServiceScope.SERVER);
             ServiceRegistration ignoredReady = UApiServices.register(
                 ReadyCheckService.class, ready, ServiceScope.SERVER)) {
            UApiDungeonDeploymentService service = new UApiDungeonDeploymentService();
            DungeonDeployment deployment = service.refresh(owner, Optional.of(checkId));

            assertEquals(DungeonDeploymentReadiness.ALL_READY, deployment.readiness());
            assertEquals(Optional.of(checkId), deployment.readyCheckId());
            assertTrue(service.canLaunch(owner, deployment));

            social.group = group(groupId, owner, member, UUID.randomUUID());
            DungeonDeployment changed = service.refresh(owner, Optional.of(checkId));
            assertEquals(DungeonDeploymentReadiness.NOT_REQUESTED, changed.readiness());
            assertTrue(changed.readyCheckId().isEmpty());
            assertFalse(service.canLaunch(owner, changed));
        }
    }

    private static ReadyCheckSnapshot allReady(UUID checkId, UUID groupId, UUID owner, UUID member) {
        Instant created = Instant.parse("2026-01-01T00:00:00Z");
        Instant completed = created.plusSeconds(1);
        return new ReadyCheckSnapshot(checkId, groupId, owner, created, created.plusSeconds(30), 2,
            Map.of(owner, ReadyCheckParticipantState.READY, member, ReadyCheckParticipantState.READY),
            ReadyCheckPhase.ALL_READY, Optional.of(new ReadyCheckResult(checkId, ReadyCheckPhase.ALL_READY,
                completed, Optional.of(owner), id("all_ready"))));
    }

    private static SocialGroup group(UUID groupId, UUID... players) {
        List<SocialGroupMember> members = java.util.Arrays.stream(players)
            .map(player -> new SocialGroupMember(player, player.toString(), Optional.empty(),
                player.equals(players[0]) ? Set.of(SocialGroupRoleFlag.LEADER)
                    : Set.of(SocialGroupRoleFlag.MEMBER), true))
            .toList();
        return new SocialGroup(groupId, SocialGroupType.PARTY, "Test party", players.length, members);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", path);
    }

    private static final class MutableSocialGroups implements SocialGroupService {
        private SocialGroup group;

        private MutableSocialGroups(SocialGroup group) { this.group = group; }

        @Override public ResourceLocation serviceId() { return id("test_social_groups"); }

        @Override public Optional<SocialGroup> getActiveParty(UUID playerId) {
            return group.contains(playerId) ? Optional.of(group) : Optional.empty();
        }

        @Override public Optional<SocialGroup> getGuild(UUID playerId) { return Optional.empty(); }

        @Override public List<SocialGroup> getGroups(UUID playerId) {
            return getActiveParty(playerId).stream().toList();
        }

        @Override public Optional<SocialGroup> getGroup(UUID requestedGroupId) {
            return group.groupId().equals(requestedGroupId) ? Optional.of(group) : Optional.empty();
        }

        @Override public Set<UUID> getReadyCheckParticipants(UUID requestedGroupId) {
            return group.groupId().equals(requestedGroupId) ? group.memberIds() : Set.of();
        }

        @Override public boolean canInitiateReadyCheck(UUID requestedGroupId, UUID playerId) {
            return group.groupId().equals(requestedGroupId)
                && group.member(playerId).map(SocialGroupMember::isLeader).orElse(false);
        }
    }

    private static final class TerminalReadyChecks implements ReadyCheckService {
        private final ReadyCheckSnapshot snapshot;

        private TerminalReadyChecks(ReadyCheckSnapshot snapshot) { this.snapshot = snapshot; }

        @Override public ResourceLocation serviceId() { return id("test_ready_checks"); }

        @Override public Optional<ReadyCheckSnapshot> getReadyCheck(UUID checkId) {
            return snapshot.checkId().equals(checkId) ? Optional.of(snapshot) : Optional.empty();
        }

        @Override public Optional<ReadyCheckSnapshot> getActiveReadyCheck(UUID groupId) {
            return Optional.empty();
        }

        @Override public ReadyCheckUpdateResult startReadyCheck(ReadyCheckStartRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override public ReadyCheckUpdateResult respondReadyCheck(
            UUID checkId, UUID participantId, ReadyCheckParticipantState response
        ) {
            throw new UnsupportedOperationException();
        }

        @Override public ReadyCheckUpdateResult cancelReadyCheck(
            UUID checkId, UUID actorId, ResourceLocation reasonCode
        ) {
            throw new UnsupportedOperationException();
        }

        @Override public List<ReadyCheckUpdateResult> expireReadyChecks() { return List.of(); }
    }
}

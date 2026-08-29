package dev.uapi.dungeons.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

final class DungeonDeploymentServiceContractTest {
    @Test
    void defaultLaunchPolicyAllowsReadySoloAndFailsClosedForGroups() {
        UUID owner = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        DungeonDeploymentService service = new MinimalService();
        DungeonDeployment solo = new DungeonDeployment(owner, Optional.empty(), Set.of(owner), Optional.empty(),
            DungeonDeploymentReadiness.SOLO_READY, 0);
        DungeonDeployment unreadySolo = new DungeonDeployment(owner, Optional.empty(), Set.of(owner),
            Optional.empty(), DungeonDeploymentReadiness.NOT_REQUESTED, 0);
        DungeonDeployment group = new DungeonDeployment(owner, Optional.of(UUID.randomUUID()),
            Set.of(owner, member), Optional.of(UUID.randomUUID()), DungeonDeploymentReadiness.ALL_READY, 1);

        assertTrue(service.canLaunch(owner, solo));
        assertFalse(service.canLaunch(owner, unreadySolo));
        assertFalse(service.canLaunch(UUID.randomUUID(), solo));
        assertFalse(service.canLaunch(owner, group));
    }

    private static final class MinimalService implements DungeonDeploymentService {
        private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(
            "dedicated_dungeons", "test_deployment_service");

        @Override public ResourceLocation serviceId() { return ID; }

        @Override public DungeonDeployment preview(UUID ownerId) { throw new UnsupportedOperationException(); }

        @Override public DungeonDeployment startReadyCheck(UUID ownerId, Duration timeout) {
            throw new UnsupportedOperationException();
        }

        @Override public DungeonDeployment refresh(UUID ownerId) { throw new UnsupportedOperationException(); }

        @Override public boolean isEligibleParticipant(UUID groupId, UUID playerId) { return false; }
    }
}

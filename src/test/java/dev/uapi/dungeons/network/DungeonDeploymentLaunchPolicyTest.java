package dev.uapi.dungeons.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.uapi.dungeons.api.DungeonDeployment;
import dev.uapi.dungeons.api.DungeonDeploymentReadiness;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

final class DungeonDeploymentLaunchPolicyTest {
    @Test
    void permitsOnlyTheOwnerWithAKeyAndAuthoritativeReadyState() {
        UUID owner = UUID.randomUUID();
        DungeonDeployment ready = deployment(owner, DungeonDeploymentReadiness.SOLO_READY);
        DungeonDeployment checking = deployment(owner, DungeonDeploymentReadiness.CHECKING);

        assertEquals(DungeonDeploymentLaunchPolicy.Decision.ALLOW,
            DungeonDeploymentLaunchPolicy.evaluate(owner, ready, true));
        assertEquals(DungeonDeploymentLaunchPolicy.Decision.KEY_MISSING,
            DungeonDeploymentLaunchPolicy.evaluate(owner, ready, false));
        assertEquals(DungeonDeploymentLaunchPolicy.Decision.NOT_READY,
            DungeonDeploymentLaunchPolicy.evaluate(owner, checking, true));
        assertEquals(DungeonDeploymentLaunchPolicy.Decision.OWNER_MISMATCH,
            DungeonDeploymentLaunchPolicy.evaluate(UUID.randomUUID(), ready, true));
    }

    private static DungeonDeployment deployment(UUID owner, DungeonDeploymentReadiness readiness) {
        return new DungeonDeployment(owner, Optional.empty(), Set.of(owner), Optional.empty(), readiness, 0);
    }
}

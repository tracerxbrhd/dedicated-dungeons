package dev.uapi.dungeons.network;

import dev.uapi.dungeons.api.DungeonDeployment;
import java.util.Objects;
import java.util.UUID;

/** Pure launch guard shared by the network handler and narrow contract tests. */
public final class DungeonDeploymentLaunchPolicy {
    public enum Decision {
        ALLOW,
        OWNER_MISMATCH,
        NOT_READY,
        KEY_MISSING
    }

    private DungeonDeploymentLaunchPolicy() {
    }

    public static Decision evaluate(UUID actorId, DungeonDeployment deployment, boolean keyPresent) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(deployment, "deployment");
        if (!actorId.equals(deployment.ownerId())) return Decision.OWNER_MISMATCH;
        if (!keyPresent) return Decision.KEY_MISSING;
        return deployment.canDeploy() ? Decision.ALLOW : Decision.NOT_READY;
    }
}

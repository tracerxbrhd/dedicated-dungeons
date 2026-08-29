package dev.uapi.dungeons.api;

import dev.uapi.api.services.UApiService;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative boundary used by dungeon selection screens and optional integrations.
 * Implementations consume U-API social contracts and never depend on provider implementation classes.
 */
public interface DungeonDeploymentService extends UApiService {
    /** Builds a current solo-or-party preview. This call never starts a ready check. */
    DungeonDeployment preview(UUID ownerId);

    /** Starts, or returns, the authoritative ready check for the owner's active party. */
    DungeonDeployment startReadyCheck(UUID ownerId, Duration timeout);

    /** Refreshes party membership and the current ready-check state. */
    DungeonDeployment refresh(UUID ownerId);

    /**
     * Refreshes while retaining the server-issued check observed by an existing deployment session.
     * Providers which retain terminal checks should override this method so an {@code ALL_READY}
     * result remains observable after it leaves the provider's active-check index.
     */
    default DungeonDeployment refresh(UUID ownerId, Optional<UUID> observedReadyCheckId) {
        return refresh(ownerId);
    }

    /** Checks membership against the group captured when a portal was created. */
    boolean isEligibleParticipant(UUID groupId, UUID playerId);

    /** Re-checks whether the owner may launch this current solo/group snapshot. */
    default boolean canLaunch(UUID ownerId, DungeonDeployment deployment) {
        return deployment.ownerId().equals(ownerId) && deployment.groupId().isEmpty()
            && deployment.canDeploy();
    }
}

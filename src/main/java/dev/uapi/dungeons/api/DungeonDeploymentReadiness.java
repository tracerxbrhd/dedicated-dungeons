package dev.uapi.dungeons.api;

/** Coarse state used by deployment screens without exposing a social provider's internals. */
public enum DungeonDeploymentReadiness {
    /** No social provider or no active party; the owner can deploy alone. */
    SOLO_READY,
    /** A party exists but no ready check has been started. */
    NOT_REQUESTED,
    /** The authoritative ready check is still collecting answers. */
    CHECKING,
    /** Every eligible participant accepted the deployment. */
    ALL_READY,
    /** At least one participant declined. */
    DECLINED,
    /** The check expired before every participant accepted. */
    TIMED_OUT,
    /** The check was cancelled by an authorized actor. */
    CANCELLED,
    /** A party exists, but no ready-check provider is currently available. */
    SERVICE_UNAVAILABLE,
    /** The player is not allowed to initiate a check for this group. */
    UNAUTHORIZED;

    public boolean canDeploy() {
        return this == SOLO_READY || this == ALL_READY;
    }
}

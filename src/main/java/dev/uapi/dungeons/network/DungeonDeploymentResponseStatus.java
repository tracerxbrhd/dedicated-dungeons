package dev.uapi.dungeons.network;

/** Stable machine-readable result codes for deployment UI operations. */
public enum DungeonDeploymentResponseStatus {
    OPENED,
    REFRESHED,
    READY_CHECK_STARTED,
    NOT_READY,
    RATE_LIMITED,
    SESSION_EXPIRED,
    KEY_MISSING,
    INVALID_REQUEST,
    LAUNCH_FAILED,
    LAUNCHED
}

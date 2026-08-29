package dev.uapi.dungeons.network;

/** Client requests supported by one server-issued deployment session. */
public enum DungeonDeploymentAction {
    REFRESH,
    START_READY_CHECK,
    LAUNCH
}

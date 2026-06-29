package dev.underworld.dungeons.portal;

public enum PortalOrigin {
    RANDOM,
    COMMAND,
    KEY,
    DEBUG;

    public boolean appliesWorldLimits() {
        return this == RANDOM;
    }
}

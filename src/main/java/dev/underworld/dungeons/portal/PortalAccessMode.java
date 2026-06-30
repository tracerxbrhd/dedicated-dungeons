package dev.underworld.dungeons.portal;

import java.util.Locale;

public enum PortalAccessMode {
    PUBLIC_NEARBY,
    OWNER_ONLY,
    PARTY,
    PARTY_OR_NEARBY;

    public static PortalAccessMode byName(String value, PortalAccessMode fallback) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}

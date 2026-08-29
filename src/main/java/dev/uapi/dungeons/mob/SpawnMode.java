package dev.uapi.dungeons.mob;

import java.util.Locale;

public enum SpawnMode {
    DIRECT,
    SPAWNER,
    ROOM_ACTIVATION;

    public static SpawnMode parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("unknown spawn mode '" + value + "'");
        }
    }
}

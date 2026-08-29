package dev.uapi.dungeons.marker;

import java.util.Locale;

public enum DungeonMarkerType {
    PLAYER_SPAWN,
    ROOM_CONNECTOR,
    SPAWNER,
    LOOT,
    BOSS_SPAWN,
    EXIT_PORTAL;

    public String id() {
        return name().toLowerCase(Locale.ROOT) + "_marker";
    }
}

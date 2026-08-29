package dev.uapi.dungeons.loot;

import java.util.Locale;

public enum LootContainerType {
    CHEST,
    BARREL,
    TRAPPED_CHEST;

    public static LootContainerType parse(String value) {
        return value == null ? CHEST : valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}

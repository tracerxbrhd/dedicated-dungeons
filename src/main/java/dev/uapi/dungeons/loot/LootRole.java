package dev.uapi.dungeons.loot;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Open, data-driven loot role. Built-in names are constants, but datapacks may introduce additional roles
 * without adding a Java enum value.
 */
public record LootRole(String value) {
    private static final Pattern VALID = Pattern.compile("[A-Z][A-Z0-9_./-]{0,63}");

    public static final LootRole COMMON = new LootRole("COMMON");
    public static final LootRole HIDDEN = new LootRole("HIDDEN");
    public static final LootRole SUPPLY = new LootRole("SUPPLY");
    public static final LootRole ELITE = new LootRole("ELITE");
    public static final LootRole REWARD = new LootRole("REWARD");
    public static final LootRole BOSS = new LootRole("BOSS");

    public LootRole {
        value = Objects.requireNonNull(value, "value").trim().toUpperCase(Locale.ROOT);
        if (!VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid loot role '" + value + "'");
        }
    }

    public static LootRole parse(String value) {
        return new LootRole(value);
    }

    @Override
    public String toString() {
        return value;
    }
}

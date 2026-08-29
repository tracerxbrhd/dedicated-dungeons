package dev.uapi.dungeons.mob;

import java.util.Locale;
import java.util.Set;

/**
 * Open-ended logical role used by dungeon mob markers.
 *
 * <p>Built-in names are normalized to upper case for authoring convenience. Namespaced roles remain
 * lower-case resource-style identifiers, so datapacks can add roles without a Java registry entry.</p>
 */
public record SpawnRole(String value) {
    private static final Set<String> BUILTIN = Set.of(
        "AMBIENT", "COMMON", "RANGED", "HEAVY", "SUPPORT", "ELITE", "BOSS", "SWARM", "GUARD", "MINIBOSS");

    public static final SpawnRole AMBIENT = new SpawnRole("AMBIENT");
    public static final SpawnRole COMMON = new SpawnRole("COMMON");
    public static final SpawnRole RANGED = new SpawnRole("RANGED");
    public static final SpawnRole HEAVY = new SpawnRole("HEAVY");
    public static final SpawnRole SUPPORT = new SpawnRole("SUPPORT");
    public static final SpawnRole ELITE = new SpawnRole("ELITE");
    public static final SpawnRole BOSS = new SpawnRole("BOSS");
    public static final SpawnRole SWARM = new SpawnRole("SWARM");
    public static final SpawnRole GUARD = new SpawnRole("GUARD");
    public static final SpawnRole MINIBOSS = new SpawnRole("MINIBOSS");

    public SpawnRole {
        if (value == null) throw new IllegalArgumentException("spawn role must not be null");
        String trimmed = value.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("spawn role must not be empty");
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (BUILTIN.contains(upper)) {
            value = upper;
        } else {
            String normalized = trimmed.toLowerCase(Locale.ROOT);
            if (!normalized.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
                throw new IllegalArgumentException(
                    "custom spawn role must be a namespaced identifier: " + trimmed);
            }
            value = normalized;
        }
    }

    public static SpawnRole parse(String value) {
        return new SpawnRole(value);
    }

    public boolean minibossLike() {
        return equals(ELITE) || equals(MINIBOSS) || equals(BOSS);
    }

    @Override
    public String toString() {
        return value;
    }
}

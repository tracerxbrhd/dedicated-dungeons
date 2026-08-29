package dev.uapi.dungeons.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.uapi.integration.IntegrationService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;

public final class EncounterDefinitions {
    private EncounterDefinitions() {}

    public record MobEntry(ResourceLocation entityType, int weight, int minimumLevel, int maximumLevel,
                           int minimumTier, int maximumTier, int minimumCount, int maximumCount,
                           String role, List<String> tags, List<String> requiredMods,
                           boolean canEquip, double equipmentChance, boolean arenaAllowed) {
        public MobEntry {
            if (weight < 1 || minimumLevel < 0 || maximumLevel < minimumLevel
                || minimumTier < 0 || maximumTier < minimumTier
                || minimumCount < 1 || maximumCount < minimumCount
                || equipmentChance < -1.0 || equipmentChance > 1.0)
                throw new IllegalArgumentException("invalid mob pool entry range");
            tags = List.copyOf(tags);
            requiredMods = List.copyOf(requiredMods);
        }
        public boolean available(int level, int tier) {
            return level >= minimumLevel && level <= maximumLevel && tier >= minimumTier && tier <= maximumTier
                && requiredMods.stream().allMatch(IntegrationService::isLoaded);
        }
        public boolean minibossLike() {
            return role.equals("elite") || role.equals("miniboss") || role.equals("boss");
        }
        public boolean allowedInArena() { return arenaAllowed && !minibossLike(); }
    }

    public record MobPool(ResourceLocation id, int formatVersion, List<MobEntry> entries, ResourceLocation fallback,
                          List<String> requiredMods) {
        public MobPool {
            requireFormat(formatVersion);
            entries = List.copyOf(entries);
            requiredMods = List.copyOf(requiredMods);
            if (entries.isEmpty()) throw new IllegalArgumentException("mob pool must not be empty");
        }
        public boolean available() { return requiredMods.stream().allMatch(IntegrationService::isLoaded); }
        public Optional<MobEntry> choose(int level, int tier, RandomSource random) {
            return choose(level, tier, random, entry -> true);
        }
        public Optional<MobEntry> choose(int level, int tier, RandomSource random, Predicate<MobEntry> filter) {
            List<MobEntry> choices = entries.stream()
                .filter(entry -> entry.available(level, tier) && filter.test(entry)).toList();
            long total = choices.stream().mapToLong(MobEntry::weight).sum();
            if (total <= 0) return Optional.empty();
            long selected = Math.floorMod(random.nextLong(), total);
            for (MobEntry entry : choices) {
                selected -= entry.weight();
                if (selected < 0) return Optional.of(entry);
            }
            return Optional.of(choices.getLast());
        }
        public static MobPool parse(ResourceLocation id, JsonObject json) {
            List<MobEntry> entries = new ArrayList<>();
            for (JsonElement raw : requiredArray(json, "entries")) {
                JsonObject value = raw.getAsJsonObject();
                entries.add(new MobEntry(requiredId(value, "entity"), positive(value, "weight", 1),
                    nonNegative(value, "minimum_level", 0), positive(value, "maximum_level", 1_000_000),
                    nonNegative(value, "minimum_tier", 0), nonNegative(value, "maximum_tier", 1_000_000),
                    positive(value, "minimum_count", 1), positive(value, "maximum_count", 1),
                    optionalString(value, "role", "melee").toLowerCase(Locale.ROOT),
                    strings(value, "tags"), strings(value, "required_mods"),
                    optionalBoolean(value, "can_equip", false),
                    boundedDouble(value, "equipment_chance", -1.0, -1.0, 1.0),
                    optionalBoolean(value, "arena_allowed", true)));
            }
            return new MobPool(id, nonNegative(json, "format_version", 1), entries,
                nullableId(json, "fallback"), strings(json, "required_mods"));
        }
    }

    public record EncounterPool(ResourceLocation id, int formatVersion, ResourceLocation mobPool,
                                int minimumGroups, int maximumGroups, ResourceLocation fallback,
                                List<String> requiredMods) {
        public EncounterPool {
            requireFormat(formatVersion);
            if (minimumGroups < 1 || maximumGroups < minimumGroups)
                throw new IllegalArgumentException("invalid encounter group range");
            requiredMods = List.copyOf(requiredMods);
        }
        public boolean available() { return requiredMods.stream().allMatch(IntegrationService::isLoaded); }
        public static EncounterPool parse(ResourceLocation id, JsonObject json) {
            return new EncounterPool(id, nonNegative(json, "format_version", 1), requiredId(json, "mob_pool"),
                positive(json, "minimum_groups", 1), positive(json, "maximum_groups", 1),
                nullableId(json, "fallback"), strings(json, "required_mods"));
        }
    }

    public record BossPool(ResourceLocation id, int formatVersion,
                           List<DungeonContentTypes.WeightedId> bosses, ResourceLocation fallback,
                           List<String> requiredMods) {
        public BossPool {
            requireFormat(formatVersion);
            bosses = List.copyOf(bosses);
            requiredMods = List.copyOf(requiredMods);
            if (bosses.isEmpty()) throw new IllegalArgumentException("boss pool must not be empty");
        }
        public boolean available() { return requiredMods.stream().allMatch(IntegrationService::isLoaded); }
        public static BossPool parse(ResourceLocation id, JsonObject json) {
            List<DungeonContentTypes.WeightedId> bosses = new ArrayList<>();
            for (JsonElement raw : requiredArray(json, "bosses"))
                bosses.add(DungeonContentTypes.WeightedId.parse(raw, "boss"));
            return new BossPool(id, nonNegative(json, "format_version", 1), bosses,
                nullableId(json, "fallback"), strings(json, "required_mods"));
        }
    }

    private static void requireFormat(int value) {
        if (value != DungeonDefinition.CURRENT_FORMAT)
            throw new IllegalArgumentException(value > DungeonDefinition.CURRENT_FORMAT
                ? "unsupported newer format_version " + value
                : "unsupported legacy format_version " + value);
    }

    private static JsonArray requiredArray(JsonObject json, String field) {
        if (!json.has(field) || !json.get(field).isJsonArray())
            throw new IllegalArgumentException("missing array '" + field + "'");
        return json.getAsJsonArray(field);
    }
    private static ResourceLocation requiredId(JsonObject json, String field) {
        ResourceLocation value = ResourceLocation.tryParse(optionalString(json, field, ""));
        if (value == null) throw new IllegalArgumentException("invalid resource location in '" + field + "'");
        return value;
    }
    private static ResourceLocation nullableId(JsonObject json, String field) {
        if (!json.has(field)) return null;
        ResourceLocation value = ResourceLocation.tryParse(json.get(field).getAsString());
        if (value == null) throw new IllegalArgumentException("invalid resource location in '" + field + "'");
        return value;
    }
    private static String optionalString(JsonObject json, String field, String fallback) {
        return json.has(field) ? json.get(field).getAsString() : fallback;
    }
    private static boolean optionalBoolean(JsonObject json, String field, boolean fallback) {
        return json.has(field) ? json.get(field).getAsBoolean() : fallback;
    }
    private static double boundedDouble(JsonObject json, String field, double fallback, double minimum, double maximum) {
        double value = json.has(field) ? json.get(field).getAsDouble() : fallback;
        if (!Double.isFinite(value) || value < minimum || value > maximum)
            throw new IllegalArgumentException(field + " must be in [" + minimum + "," + maximum + "]");
        return value;
    }
    private static int positive(JsonObject json, String field, int fallback) {
        int value = json.has(field) ? json.get(field).getAsInt() : fallback;
        if (value < 1 || value > 1_000_000) throw new IllegalArgumentException(field + " must be in [1,1000000]");
        return value;
    }
    private static int nonNegative(JsonObject json, String field, int fallback) {
        int value = json.has(field) ? json.get(field).getAsInt() : fallback;
        if (value < 0 || value > 1_000_000) throw new IllegalArgumentException(field + " must be in [0,1000000]");
        return value;
    }
    private static List<String> strings(JsonObject json, String field) {
        if (!json.has(field)) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray(field)) values.add(element.getAsString());
        return values;
    }
}

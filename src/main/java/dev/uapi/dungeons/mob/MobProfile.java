package dev.uapi.dungeons.mob;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.uapi.integration.IntegrationService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Datapack-defined role-to-entity distribution with optional inheritance and bounded count scaling. */
public record MobProfile(ResourceLocation id, ResourceLocation parent, Map<SpawnRole, RoleEntries> roles,
                         Scaling scaling) {
    public MobProfile {
        roles = Map.copyOf(roles);
        scaling = scaling == null ? Scaling.DEFAULT : scaling;
        if (roles.isEmpty()) throw new IllegalArgumentException("mob profile must define at least one role");
    }

    public static MobProfile parse(ResourceLocation resourceId, JsonObject json) {
        if (json.has("id")) {
            ResourceLocation declared = parseId(json.get("id").getAsString(), "id");
            if (!resourceId.equals(declared)) {
                throw new IllegalArgumentException(
                    "declared id " + declared + " does not match resource id " + resourceId);
            }
        }
        ResourceLocation parent = json.has("parent")
            ? parseId(json.get("parent").getAsString(), "parent") : null;
        if (!json.has("roles") || !json.get("roles").isJsonObject()) {
            throw new IllegalArgumentException("missing object 'roles'");
        }
        Map<SpawnRole, RoleEntries> roles = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> raw : json.getAsJsonObject("roles").entrySet()) {
            roles.put(SpawnRole.parse(raw.getKey()), RoleEntries.parse(raw.getValue()));
        }
        Scaling scaling = json.has("scaling")
            ? Scaling.parse(json.getAsJsonObject("scaling")) : Scaling.DEFAULT;
        return new MobProfile(resourceId, parent, roles, scaling);
    }

    public record RoleEntries(boolean replace, List<Entry> entries) {
        public RoleEntries {
            entries = List.copyOf(entries);
            if (entries.isEmpty()) throw new IllegalArgumentException("mob profile role has no entries");
        }

        static RoleEntries parse(JsonElement element) {
            JsonObject json = element.getAsJsonObject();
            if (!json.has("entries") || !json.get("entries").isJsonArray()) {
                throw new IllegalArgumentException("mob profile role is missing entries array");
            }
            List<Entry> entries = new ArrayList<>();
            for (JsonElement value : json.getAsJsonArray("entries")) {
                entries.add(Entry.parse(value.getAsJsonObject()));
            }
            return new RoleEntries(json.has("replace") && json.get("replace").getAsBoolean(), entries);
        }
    }

    public record Entry(ResourceLocation entity, int weight, int minimumCount, int maximumCount,
                        int minimumDifficulty, int maximumDifficulty,
                        int minimumPartySize, int maximumPartySize,
                        CompoundTag nbt, List<String> requiredMods,
                        boolean canEquip, double equipmentChance, boolean arenaAllowed) {
        public Entry {
            if (weight < 0 || weight > 1_000_000)
                throw new IllegalArgumentException("weight must be in [0,1000000]");
            range(minimumCount, maximumCount, 1, "count");
            range(minimumDifficulty, maximumDifficulty, 0, "difficulty");
            range(minimumPartySize, maximumPartySize, 1, "party_size");
            if (!Double.isFinite(equipmentChance) || equipmentChance < -1.0 || equipmentChance > 1.0)
                throw new IllegalArgumentException("equipment_chance must be in [-1,1]");
            nbt = nbt == null ? null : nbt.copy();
            requiredMods = List.copyOf(requiredMods);
            for (String mod : requiredMods) {
                if (!mod.matches("[a-z][a-z0-9_]{1,63}"))
                    throw new IllegalArgumentException("invalid required mod id '" + mod + "'");
            }
        }

        public boolean available(int difficulty, int partySize, boolean arena) {
            return difficulty >= minimumDifficulty && difficulty <= maximumDifficulty
                && partySize >= minimumPartySize && partySize <= maximumPartySize
                && (!arena || arenaAllowed)
                && requiredMods.stream().allMatch(IntegrationService::isLoaded);
        }

        static Entry parse(JsonObject json) {
            int minimumCount = integer(json, "min_count", integer(json, "minimum_count", 1));
            int maximumCount = integer(json, "max_count", integer(json, "maximum_count", minimumCount));
            int minimumDifficulty = integer(json, "min_difficulty", integer(json, "minimum_tier", 0));
            int maximumDifficulty = integer(json, "max_difficulty", integer(json, "maximum_tier", 1_000_000));
            int minimumParty = integer(json, "min_party_size", 1);
            int maximumParty = integer(json, "max_party_size", 1_000_000);
            return new Entry(
                parseId(requiredString(json, "entity"), "entity"),
                integer(json, "weight", 1),
                minimumCount, maximumCount,
                minimumDifficulty, maximumDifficulty,
                minimumParty, maximumParty,
                compound(json, "nbt"),
                strings(json, "required_mods"),
                json.has("can_equip") && json.get("can_equip").getAsBoolean(),
                json.has("equipment_chance") ? json.get("equipment_chance").getAsDouble() : -1.0,
                !json.has("arena_allowed") || json.get("arena_allowed").getAsBoolean());
        }
    }

    public record Scaling(int baseCount, int countPerAdditionalPlayer, int maximumCount,
                          double difficultyCountMultiplier) {
        public static final Scaling DEFAULT = new Scaling(1, 0, 16, 0.0);

        public Scaling {
            if (baseCount < 1 || baseCount > 1_000_000)
                throw new IllegalArgumentException("scaling.base_count must be in [1,1000000]");
            if (countPerAdditionalPlayer < 0 || countPerAdditionalPlayer > 1_000_000)
                throw new IllegalArgumentException(
                    "scaling.count_per_additional_player must be in [0,1000000]");
            if (maximumCount < baseCount || maximumCount > 1_000_000)
                throw new IllegalArgumentException("scaling.maximum_count must be >= base_count and <= 1000000");
            if (!Double.isFinite(difficultyCountMultiplier)
                || difficultyCountMultiplier < 0.0 || difficultyCountMultiplier > 100.0) {
                throw new IllegalArgumentException(
                    "scaling.difficulty_count_multiplier must be in [0,100]");
            }
        }

        static Scaling parse(JsonObject json) {
            return new Scaling(
                integer(json, "base_count", 1),
                integer(json, "count_per_additional_player", 0),
                integer(json, "maximum_count", 16),
                json.has("difficulty_count_multiplier")
                    ? json.get("difficulty_count_multiplier").getAsDouble() : 0.0);
        }

        public int scale(int selectedCount, int difficulty, int partySize, int globalMaximum) {
            int base = Math.max(baseCount, selectedCount);
            long players = (long) Math.max(0, partySize - 1) * countPerAdditionalPlayer;
            long difficultyBonus = (long) Math.floor(
                Math.max(0, difficulty) * base * difficultyCountMultiplier);
            long result = (long) base + players + difficultyBonus;
            return (int) Math.max(1L, Math.min(result, Math.min(maximumCount, globalMaximum)));
        }
    }

    private static void range(int minimum, int maximum, int floor, String field) {
        if (minimum < floor || maximum < minimum || maximum > 1_000_000)
            throw new IllegalArgumentException("invalid " + field + " range");
    }

    private static ResourceLocation parseId(String value, String field) {
        ResourceLocation result = ResourceLocation.tryParse(value);
        if (result == null) throw new IllegalArgumentException(
            "invalid resource location in '" + field + "': " + value);
        return result;
    }

    private static String requiredString(JsonObject json, String field) {
        if (!json.has(field) || !json.get(field).isJsonPrimitive())
            throw new IllegalArgumentException("missing string '" + field + "'");
        return json.get(field).getAsString();
    }

    private static int integer(JsonObject json, String field, int fallback) {
        return json.has(field) ? json.get(field).getAsInt() : fallback;
    }

    private static List<String> strings(JsonObject json, String field) {
        if (!json.has(field)) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonElement value : json.getAsJsonArray(field)) values.add(value.getAsString());
        return values;
    }

    private static CompoundTag compound(JsonObject json, String field) {
        if (!json.has(field)) return null;
        if (!json.get(field).isJsonObject()) throw new IllegalArgumentException(field + " must be an object");
        try {
            return TagParser.parseTag(json.get(field).toString());
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid " + field + ": " + exception.getMessage(), exception);
        }
    }
}

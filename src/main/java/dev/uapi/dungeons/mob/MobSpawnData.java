package dev.uapi.dungeons.mob;

import com.google.gson.JsonObject;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;

/**
 * Sparse authoring overrides carried by a spawner marker. Null means that the next resolver layer may decide.
 */
public record MobSpawnData(
    SpawnRole role,
    ResourceLocation profile,
    ResourceLocation entity,
    SpawnMode mode,
    Integer count,
    Integer wave,
    String group,
    Long seed,
    CompoundTag entityNbt,
    Integer initialDelay,
    Integer minimumDelay,
    Integer maximumDelay,
    Integer spawnCount,
    Integer maximumNearbyEntities,
    Integer requiredPlayerRange,
    Integer spawnRange,
    Integer totalSpawnLimit
) {
    public static final MobSpawnData EMPTY = new MobSpawnData(
        null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null);

    public MobSpawnData {
        entityNbt = entityNbt == null ? null : entityNbt.copy();
        validatePositive(count, "count");
        validateNonNegative(wave, "wave");
        validateNonNegative(initialDelay, "initial_delay");
        validatePositive(minimumDelay, "min_delay");
        validatePositive(maximumDelay, "max_delay");
        validatePositive(spawnCount, "spawn_count");
        validatePositive(maximumNearbyEntities, "max_nearby_entities");
        validatePositive(requiredPlayerRange, "required_player_range");
        validatePositive(spawnRange, "spawn_range");
        validatePositive(totalSpawnLimit, "total_spawn_limit");
        if (minimumDelay != null && maximumDelay != null && minimumDelay > maximumDelay) {
            throw new IllegalArgumentException("min_delay must not exceed max_delay");
        }
        if (group != null && group.isBlank()) throw new IllegalArgumentException("group must not be blank");
    }

    public boolean empty() {
        return this.equals(EMPTY);
    }

    /** Explicit values in {@code override} replace values from this object. */
    public MobSpawnData merge(MobSpawnData override) {
        if (override == null || override.empty()) return this;
        return new MobSpawnData(
            pick(override.role, role),
            pick(override.profile, profile),
            pick(override.entity, entity),
            pick(override.mode, mode),
            pick(override.count, count),
            pick(override.wave, wave),
            pick(override.group, group),
            pick(override.seed, seed),
            pick(override.entityNbt, entityNbt),
            pick(override.initialDelay, initialDelay),
            pick(override.minimumDelay, minimumDelay),
            pick(override.maximumDelay, maximumDelay),
            pick(override.spawnCount, spawnCount),
            pick(override.maximumNearbyEntities, maximumNearbyEntities),
            pick(override.requiredPlayerRange, requiredPlayerRange),
            pick(override.spawnRange, spawnRange),
            pick(override.totalSpawnLimit, totalSpawnLimit));
    }

    public static MobSpawnData fromJson(JsonObject json) {
        return new MobSpawnData(
            json.has("spawn_role") ? SpawnRole.parse(json.get("spawn_role").getAsString()) : null,
            id(json, "spawn_profile"),
            firstId(json, "entity", "mob", "entity_id", "spawn_entity", "spawner_entity"),
            json.has("spawn_mode") ? SpawnMode.parse(json.get("spawn_mode").getAsString()) : null,
            integer(json, "count"),
            integer(json, "wave"),
            json.has("group") ? json.get("group").getAsString() : null,
            json.has("seed") ? json.get("seed").getAsLong() : null,
            compound(json, "entity_nbt"),
            integer(json, "initial_delay"),
            integer(json, "min_delay"),
            integer(json, "max_delay"),
            integer(json, "spawn_count"),
            integer(json, "max_nearby_entities"),
            integer(json, "required_player_range"),
            integer(json, "spawn_range"),
            integer(json, "total_spawn_limit"));
    }

    public void save(CompoundTag tag) {
        if (role != null) tag.putString("spawn_role", role.value());
        if (profile != null) tag.putString("spawn_profile", profile.toString());
        if (entity != null) tag.putString("entity", entity.toString());
        if (mode != null) tag.putString("spawn_mode", mode.name());
        putInt(tag, "count", count);
        putInt(tag, "wave", wave);
        if (group != null) tag.putString("group", group);
        if (seed != null) tag.putLong("seed", seed);
        if (entityNbt != null && !entityNbt.isEmpty()) tag.put("entity_nbt", entityNbt.copy());
        putInt(tag, "initial_delay", initialDelay);
        putInt(tag, "min_delay", minimumDelay);
        putInt(tag, "max_delay", maximumDelay);
        putInt(tag, "spawn_count", spawnCount);
        putInt(tag, "max_nearby_entities", maximumNearbyEntities);
        putInt(tag, "required_player_range", requiredPlayerRange);
        putInt(tag, "spawn_range", spawnRange);
        putInt(tag, "total_spawn_limit", totalSpawnLimit);
    }

    public static MobSpawnData load(CompoundTag tag) {
        SpawnRole role = null;
        if (tag.contains("spawn_role", Tag.TAG_STRING)) {
            try { role = SpawnRole.parse(tag.getString("spawn_role")); }
            catch (IllegalArgumentException ignored) {}
        }
        SpawnMode mode = null;
        if (tag.contains("spawn_mode", Tag.TAG_STRING)) {
            try { mode = SpawnMode.parse(tag.getString("spawn_mode")); }
            catch (IllegalArgumentException ignored) {}
        }
        return new MobSpawnData(
            role,
            readId(tag, "spawn_profile"),
            firstId(tag, "entity", "mob", "entity_id", "spawn_entity", "spawner_entity"),
            mode,
            readInt(tag, "count"),
            readInt(tag, "wave"),
            tag.contains("group", Tag.TAG_STRING) ? tag.getString("group") : null,
            tag.contains("seed", Tag.TAG_LONG) ? tag.getLong("seed") : null,
            tag.contains("entity_nbt", Tag.TAG_COMPOUND) ? tag.getCompound("entity_nbt") : null,
            readInt(tag, "initial_delay"),
            readInt(tag, "min_delay"),
            readInt(tag, "max_delay"),
            readInt(tag, "spawn_count"),
            readInt(tag, "max_nearby_entities"),
            readInt(tag, "required_player_range"),
            readInt(tag, "spawn_range"),
            readInt(tag, "total_spawn_limit"));
    }

    private static CompoundTag compound(JsonObject json, String field) {
        if (!json.has(field)) return null;
        if (!json.get(field).isJsonObject()) throw new IllegalArgumentException(field + " must be an object");
        try {
            return TagParser.parseTag(json.get(field).toString());
        } catch (Exception exception) {
            throw new IllegalArgumentException("invalid entity_nbt: " + exception.getMessage(), exception);
        }
    }

    private static ResourceLocation id(JsonObject json, String field) {
        if (!json.has(field)) return null;
        ResourceLocation value = ResourceLocation.tryParse(json.get(field).getAsString());
        if (value == null) throw new IllegalArgumentException("invalid resource location in '" + field + "'");
        return value;
    }

    private static ResourceLocation firstId(JsonObject json, String... fields) {
        for (String field : fields) {
            ResourceLocation value = id(json, field);
            if (value != null) return value;
        }
        return null;
    }

    private static ResourceLocation readId(CompoundTag tag, String field) {
        if (!tag.contains(field, Tag.TAG_STRING)) return null;
        return ResourceLocation.tryParse(tag.getString(field));
    }

    private static ResourceLocation firstId(CompoundTag tag, String... fields) {
        for (String field : fields) {
            ResourceLocation value = readId(tag, field);
            if (value != null) return value;
        }
        return null;
    }

    private static Integer integer(JsonObject json, String field) {
        return json.has(field) ? json.get(field).getAsInt() : null;
    }

    private static Integer readInt(CompoundTag tag, String field) {
        return tag.contains(field, Tag.TAG_ANY_NUMERIC) ? tag.getInt(field) : null;
    }

    private static void putInt(CompoundTag tag, String field, Integer value) {
        if (value != null) tag.putInt(field, value);
    }

    private static void validatePositive(Integer value, String field) {
        if (value != null && (value < 1 || value > 1_000_000))
            throw new IllegalArgumentException(field + " must be in [1,1000000]");
    }

    private static void validateNonNegative(Integer value, String field) {
        if (value != null && (value < 0 || value > 1_000_000))
            throw new IllegalArgumentException(field + " must be in [0,1000000]");
    }

    private static <T> T pick(T preferred, T fallback) {
        return preferred != null ? preferred : fallback;
    }
}

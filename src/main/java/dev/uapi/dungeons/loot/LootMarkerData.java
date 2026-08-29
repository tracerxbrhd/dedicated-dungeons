package dev.uapi.dungeons.loot;

import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/**
 * Optional authoring data carried by a loot marker. Null means "not specified here", allowing each
 * resolution layer to fall through independently.
 */
public record LootMarkerData(
    LootRole role,
    ResourceLocation profile,
    ResourceLocation table,
    LootContainerType containerType,
    Long seed,
    Boolean locked
) {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    public static final LootMarkerData EMPTY = new LootMarkerData(null, null, null, null, null, null);

    public boolean empty() {
        return role == null && profile == null && table == null && containerType == null
            && seed == null && locked == null;
    }

    /** Values explicitly present in {@code override} replace the corresponding base values. */
    public LootMarkerData merge(LootMarkerData override) {
        if (override == null || override.empty()) return this;
        return new LootMarkerData(
            override.role != null ? override.role : role,
            override.profile != null ? override.profile : profile,
            override.table != null ? override.table : table,
            override.containerType != null ? override.containerType : containerType,
            override.seed != null ? override.seed : seed,
            override.locked != null ? override.locked : locked
        );
    }

    public static LootMarkerData fromJson(JsonObject json, ResourceLocation legacyProfile) {
        LootRole role = json.has("loot_role") ? LootRole.parse(json.get("loot_role").getAsString()) : null;
        ResourceLocation profile = id(json, "loot_profile");
        ResourceLocation table = id(json, "loot_table");
        LootContainerType type = json.has("container_type")
            ? LootContainerType.parse(json.get("container_type").getAsString()) : null;
        Long seed = json.has("loot_seed") ? json.get("loot_seed").getAsLong() : null;
        Boolean locked = json.has("locked") ? json.get("locked").getAsBoolean() : null;
        // In format_version 1 room JSON, loot-marker "profile" meant a direct loot table.
        if (table == null) table = legacyProfile;
        return new LootMarkerData(role, profile, table, type, seed, locked);
    }

    public void save(CompoundTag tag) {
        if (role != null) tag.putString("loot_role", role.value());
        if (profile != null) tag.putString("loot_profile", profile.toString());
        if (table != null) tag.putString("loot_table", table.toString());
        if (containerType != null) tag.putString("container_type", containerType.name());
        if (seed != null) tag.putLong("loot_seed", seed);
        if (locked != null) tag.putBoolean("locked", locked);
    }

    public static LootMarkerData load(CompoundTag tag) {
        LootRole role = null;
        if (tag.contains("loot_role", Tag.TAG_STRING)) {
            try {
                role = LootRole.parse(tag.getString("loot_role"));
            } catch (IllegalArgumentException exception) {
                LOGGER.warn("Ignoring invalid loot marker role '{}'", tag.getString("loot_role"));
            }
        }
        ResourceLocation profile = readId(tag, "loot_profile");
        ResourceLocation table = readId(tag, "loot_table");
        if (table == null) table = readId(tag, "LootTable");
        if (table == null) table = readId(tag, "lootTable");
        if (table == null) table = readId(tag, "profile");

        LootContainerType type = null;
        if (tag.contains("container_type", Tag.TAG_STRING)) {
            try {
                type = LootContainerType.parse(tag.getString("container_type"));
            } catch (IllegalArgumentException exception) {
                LOGGER.warn("Ignoring unsupported loot marker container_type '{}'",
                    tag.getString("container_type"));
            }
        }
        Long seed = null;
        if (tag.contains("loot_seed", Tag.TAG_LONG)) seed = tag.getLong("loot_seed");
        else if (tag.contains("LootTableSeed", Tag.TAG_LONG)) seed = tag.getLong("LootTableSeed");
        Boolean locked = tag.contains("locked", Tag.TAG_BYTE) ? tag.getBoolean("locked") : null;
        return new LootMarkerData(role, profile, table, type, seed, locked);
    }

    private static ResourceLocation id(JsonObject json, String field) {
        if (!json.has(field)) return null;
        ResourceLocation value = ResourceLocation.tryParse(json.get(field).getAsString());
        if (value == null) throw new IllegalArgumentException("invalid resource location in '" + field + "'");
        return value;
    }

    private static ResourceLocation readId(CompoundTag tag, String field) {
        if (!tag.contains(field, Tag.TAG_STRING)) return null;
        ResourceLocation value = ResourceLocation.tryParse(tag.getString(field));
        if (value == null) {
            LOGGER.warn("Ignoring invalid loot marker resource location {}='{}'",
                field, tag.getString(field));
        }
        return value;
    }
}

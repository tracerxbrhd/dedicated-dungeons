package dev.uapi.dungeons.loot;

import com.mojang.logging.LogUtils;
import dev.uapi.dungeons.config.DungeonServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** One explicit, testable precedence chain for every generated dungeon container. */
public final class DungeonLootResolver {
    public static final ResourceLocation BUILTIN_PROFILE =
        ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", "default");
    public static final ResourceLocation BUILTIN_FALLBACK =
        ResourceLocation.fromNamespaceAndPath("minecraft", "chests/stronghold_crossing");
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    public record Context(
        ResourceLocation dungeonId,
        ResourceLocation themeId,
        ResourceLocation roomId,
        BlockPos position,
        ResourceLocation roomProfile,
        Map<LootRole, ResourceLocation> roomOverrides,
        ResourceLocation dungeonProfile,
        Map<String, ResourceLocation> dungeonOverrides,
        ResourceLocation themeProfile
    ) {
        public Context {
            roomOverrides = roomOverrides == null ? Map.of() : Map.copyOf(roomOverrides);
            dungeonOverrides = dungeonOverrides == null ? Map.of() : Map.copyOf(dungeonOverrides);
        }
    }

    public record Resolved(ResourceLocation table, LootRole role, LootContainerType containerType,
                           long seed, boolean locked, String source) {}

    private DungeonLootResolver() {}

    public static Resolved resolve(LootMarkerData marker, Context context, long seed) {
        marker = marker == null ? LootMarkerData.EMPTY : marker;
        LootRole role = marker.role() != null ? marker.role() : configuredRole();
        LootContainerType container = marker.containerType() == null ? LootContainerType.CHEST : marker.containerType();
        long effectiveSeed = marker.seed() == null ? seed : marker.seed();

        ResourceLocation direct = usableTable(marker.table(), context, "helper direct loot_table");
        if (direct != null) return result(direct, role, container, effectiveSeed, marker, "helper_direct");

        ResourceLocation selected = profileTable(marker.profile(), role, effectiveSeed, context, "helper loot_profile");
        if (selected != null) return result(selected, role, container, effectiveSeed, marker, "helper_profile");

        selected = usableTable(context.roomOverrides().get(role), context, "room loot_overrides." + role);
        if (selected != null) return result(selected, role, container, effectiveSeed, marker, "room_override");

        selected = profileTable(context.roomProfile(), role, effectiveSeed, context, "room loot_profile");
        if (selected != null) return result(selected, role, container, effectiveSeed, marker, "room_profile");

        selected = usableTable(context.dungeonOverrides().get(role.value()), context,
            "dungeon loot_overrides." + role);
        if (selected == null) {
            // Accept lowercase/mixed keys from codecs while keeping roles case-insensitive.
            selected = context.dungeonOverrides().entrySet().stream()
                .filter(entry -> {
                    try { return LootRole.parse(entry.getKey()).equals(role); }
                    catch (IllegalArgumentException ignored) { return false; }
                })
                .map(Map.Entry::getValue).map(value -> usableTable(value, context,
                    "dungeon loot_overrides." + role)).filter(java.util.Objects::nonNull).findFirst().orElse(null);
        }
        if (selected != null) return result(selected, role, container, effectiveSeed, marker, "dungeon_override");

        selected = profileTable(context.dungeonProfile(), role, effectiveSeed, context, "dungeon loot_profile");
        if (selected != null) return result(selected, role, container, effectiveSeed, marker, "dungeon_profile");

        selected = profileTable(context.themeProfile(), role, effectiveSeed, context, "theme loot_profile");
        if (selected != null) return result(selected, role, container, effectiveSeed, marker, "theme_profile");

        ResourceLocation configuredProfile = configuredId(config(
            DungeonServerConfig.DEFAULT_LOOT_PROFILE, "dedicated_dungeons:default"));
        selected = profileTable(configuredProfile, role, effectiveSeed, context, "server default_profile");
        if (selected != null) return result(selected, role, container, effectiveSeed, marker, "server_profile");

        selected = profileTable(BUILTIN_PROFILE, role, effectiveSeed, context, "built-in default profile");
        if (selected != null) return result(selected, role, container, effectiveSeed, marker, "builtin_profile");

        ResourceLocation configuredFallback = configuredId(config(
            DungeonServerConfig.FALLBACK_LOOT_TABLE, "minecraft:chests/stronghold_crossing"));
        selected = usableTable(configuredFallback, context, "server fallback_loot_table");
        if (selected == null) selected = BUILTIN_FALLBACK;
        return result(selected, role, container, effectiveSeed, marker, "fallback_table");
    }

    public static long seed(long worldSeed, UUID instanceId, BlockPos position, ResourceLocation dungeonId) {
        long value = worldSeed ^ instanceId.getMostSignificantBits()
            ^ Long.rotateLeft(instanceId.getLeastSignificantBits(), 17)
            ^ position.asLong();
        byte[] bytes = dungeonId.toString().getBytes(StandardCharsets.UTF_8);
        for (byte current : bytes) value = (value ^ (current & 0xffL)) * 0x100000001b3L;
        return mix64(value);
    }

    static void clearWarnings() {
        WARNED.clear();
    }

    private static Resolved result(ResourceLocation table, LootRole role, LootContainerType container,
                                   long seed, LootMarkerData marker, String source) {
        return new Resolved(table, role, container, seed, Boolean.TRUE.equals(marker.locked()), source);
    }

    private static ResourceLocation profileTable(ResourceLocation profile, LootRole role, long seed,
                                                 Context context, String source) {
        if (profile == null) return null;
        if (!LootProfileManager.containsProfile(profile)) {
            warn(context, source + " references missing profile '" + profile + "'",
                config(DungeonServerConfig.WARN_ON_MISSING_LOOT_PROFILE, true));
            return null;
        }
        ResourceLocation table = LootProfileManager.resolve(profile, role, seed).orElse(null);
        if (table == null) {
            warn(context, source + " has no usable table for role " + role,
                config(DungeonServerConfig.WARN_ON_MISSING_LOOT_TABLE, true));
        }
        return table;
    }

    private static ResourceLocation usableTable(ResourceLocation table, Context context, String source) {
        if (table == null) return null;
        if (!LootProfileManager.containsLootTable(table)) {
            warn(context, source + " references missing loot table '" + table + "'",
                config(DungeonServerConfig.WARN_ON_MISSING_LOOT_TABLE, true));
            return null;
        }
        return table;
    }

    private static void warn(Context context, String message, boolean enabled) {
        if (!enabled) return;
        String key = context.dungeonId() + "|" + context.roomId() + "|" + message;
        if (!WARNED.add(key)) return;
        LOGGER.warn(
            "Dungeon loot fallback: {}; dungeon={}, theme={}, room={}, first_position={}",
            message, context.dungeonId(), context.themeId(), context.roomId(), context.position());
    }

    private static LootRole configuredRole() {
        try {
            return LootRole.parse(config(DungeonServerConfig.DEFAULT_LOOT_ROLE, "COMMON"));
        } catch (IllegalArgumentException exception) {
            return LootRole.COMMON;
        }
    }

    private static ResourceLocation configuredId(String value) {
        return ResourceLocation.tryParse(value);
    }

    private static <T> T config(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            return value.get();
        } catch (IllegalStateException exception) {
            return fallback;
        }
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}

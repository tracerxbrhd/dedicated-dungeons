package dev.uapi.dungeons.mob;

import com.mojang.logging.LogUtils;
import dev.uapi.dungeons.config.DungeonServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Single precedence chain for marker, room, dungeon, theme, server and built-in mob choices. */
public final class DungeonMobResolver {
    public static final ResourceLocation BUILTIN_PROFILE =
        ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", "default");
    public static final ResourceLocation BUILTIN_FALLBACK =
        ResourceLocation.withDefaultNamespace("zombie");
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();

    public record Context(
        ResourceLocation dungeonId,
        ResourceLocation themeId,
        ResourceLocation roomId,
        BlockPos position,
        SpawnRole roomDefaultRole,
        ResourceLocation roomProfile,
        Map<SpawnRole, ResourceLocation> roomOverrides,
        ResourceLocation dungeonProfile,
        Map<SpawnRole, ResourceLocation> dungeonOverrides,
        ResourceLocation themeProfile,
        int difficulty,
        int partySize,
        boolean arena
    ) {
        public Context {
            roomOverrides = roomOverrides == null ? Map.of() : Map.copyOf(roomOverrides);
            dungeonOverrides = dungeonOverrides == null ? Map.of() : Map.copyOf(dungeonOverrides);
            partySize = Math.max(1, partySize);
        }
    }

    public record Resolved(ResourceLocation entity, SpawnRole role, ResourceLocation profile,
                           SpawnMode mode, int count, long seed, CompoundTag entityNbt,
                           boolean canEquip, double equipmentChance, String source,
                           MobSpawnData spawnerSettings) {
        public Resolved {
            entityNbt = entityNbt == null ? null : entityNbt.copy();
        }
    }

    private DungeonMobResolver() {}

    public static Resolved resolve(MobSpawnData marker, Context context, long baseSeed) {
        marker = marker == null ? MobSpawnData.EMPTY : marker;
        long effectiveSeed = marker.seed() == null ? baseSeed : marker.seed();
        SpawnRole role = resolveRole(marker, context);
        if (context.arena() && role.minibossLike()) {
            warn(context, role, null, "arena marker requested miniboss-like role; falling back to COMMON");
            role = SpawnRole.COMMON;
        }
        SpawnMode mode = marker.mode() == null ? SpawnMode.DIRECT : marker.mode();

        if (marker.entity() != null && !(context.arena() && role.minibossLike())) {
            if (MobProfileManager.entityRegistered(marker.entity())) {
                int count = cap(marker.count() == null ? 1 : marker.count());
                return new Resolved(marker.entity(), role, null, mode, count, effectiveSeed,
                    marker.entityNbt(), false, -1.0, "helper_direct", marker);
            }
            warnEntity(context, role, marker.entity(),
                "helper direct entity is not registered; continuing fallback");
        }

        Candidate candidate = profile(marker.profile(), role, context, effectiveSeed, "helper_profile");
        if (candidate == null) {
            candidate = profile(context.roomOverrides().get(role), role, context, effectiveSeed,
                "room_role_override");
        }
        if (candidate == null) {
            candidate = profile(context.roomProfile(), role, context, effectiveSeed, "room_profile");
        }
        if (candidate == null) {
            candidate = profile(context.dungeonOverrides().get(role), role, context, effectiveSeed,
                "dungeon_role_override");
        }
        if (candidate == null) {
            candidate = profile(context.dungeonProfile(), role, context, effectiveSeed, "dungeon_profile");
        }
        if (candidate == null) {
            candidate = profile(context.themeProfile(), role, context, effectiveSeed, "theme_profile");
        }
        if (candidate == null) {
            candidate = profile(configuredId(config(DungeonServerConfig.DEFAULT_MOB_PROFILE,
                "dedicated_dungeons:default")), role, context, effectiveSeed, "server_profile");
        }
        if (candidate == null) {
            candidate = profile(BUILTIN_PROFILE, role, context, effectiveSeed, "builtin_profile");
        }
        if (candidate == null && !role.equals(SpawnRole.COMMON)) {
            warn(context, role, null, "role has no usable entries; retrying COMMON");
            role = SpawnRole.COMMON;
            candidate = profile(marker.profile(), role, context, effectiveSeed, "helper_profile_common");
            if (candidate == null) candidate = profile(context.roomProfile(), role, context, effectiveSeed,
                "room_profile_common");
            if (candidate == null) candidate = profile(context.dungeonProfile(), role, context, effectiveSeed,
                "dungeon_profile_common");
            if (candidate == null) candidate = profile(context.themeProfile(), role, context, effectiveSeed,
                "theme_profile_common");
            if (candidate == null) candidate = profile(BUILTIN_PROFILE, role, context, effectiveSeed,
                "builtin_profile_common");
        }
        if (candidate == null) {
            ResourceLocation configured = configuredId(config(DungeonServerConfig.FALLBACK_MOB_ENTITY,
                "minecraft:zombie"));
            ResourceLocation fallback = configured != null
                && MobProfileManager.entityRegistered(configured) ? configured : BUILTIN_FALLBACK;
            warn(context, role, fallback, "no usable profile entry; using final fallback entity");
            return new Resolved(fallback, role, null, mode,
                cap(marker.count() == null ? 1 : marker.count()), effectiveSeed,
                marker.entityNbt(), false, -1.0, "fallback_entity", marker);
        }

        // Implicit COMMON points gain a bounded chance to become HEAVY as difficulty rises.
        if (marker.role() == null && role.equals(SpawnRole.COMMON) && !context.arena()) {
            long roll = Math.floorMod(mix64(effectiveSeed ^ 0x4d4f425f48454156L), 10_000L);
            long threshold = Math.min(4_500L, Math.max(0, context.difficulty()) * 750L);
            if (roll < threshold) {
                Candidate heavy = profile(candidate.profile(), SpawnRole.HEAVY, context,
                    effectiveSeed ^ 0x4845415659L, candidate.source() + "_difficulty_heavy");
                if (heavy != null) {
                    role = SpawnRole.HEAVY;
                    candidate = heavy;
                }
            }
        }

        MobProfile.Entry entry = candidate.selection().entry();
        int selected = randomInclusive(entry.minimumCount(), entry.maximumCount(), effectiveSeed ^ 0x434f554e54L);
        int count = marker.count() != null ? marker.count()
            : candidate.selection().scaling().scale(selected, context.difficulty(), context.partySize(),
                configuredMaximum());
        count = cap(count);
        CompoundTag nbt = merge(entry.nbt(), marker.entityNbt());
        return new Resolved(entry.entity(), role, candidate.profile(), mode, count, effectiveSeed, nbt,
            entry.canEquip(), entry.equipmentChance(), candidate.source(), marker);
    }

    public static long seed(long worldSeed, UUID instanceId, ResourceLocation roomId, BlockPos position,
                            int wave, String group) {
        long value = worldSeed ^ instanceId.getMostSignificantBits()
            ^ Long.rotateLeft(instanceId.getLeastSignificantBits(), 19)
            ^ position.asLong() ^ ((long) wave << 32);
        value = hash(value, roomId == null ? "" : roomId.toString());
        return mix64(hash(value, group == null ? "" : group));
    }

    static void clearWarnings() {
        WARNED.clear();
    }

    private static Candidate profile(ResourceLocation profile, SpawnRole role, Context context,
                                     long seed, String source) {
        if (profile == null) return null;
        if (!MobProfileManager.containsProfile(profile)) {
            warn(context, role, null, source + " references missing profile '" + profile + "'");
            return null;
        }
        MobProfileManager.Selection selected = MobProfileManager.resolve(profile, role,
            context.difficulty(), context.partySize(), context.arena(), seed).orElse(null);
        if (selected == null) {
            warn(context, role, null, source + " has no registered positive-weight entity for role");
            return null;
        }
        return new Candidate(profile, selected, source);
    }

    private static SpawnRole resolveRole(MobSpawnData marker, Context context) {
        if (marker.role() != null) return marker.role();
        if (context.roomDefaultRole() != null) return context.roomDefaultRole();
        try {
            return SpawnRole.parse(config(DungeonServerConfig.DEFAULT_MOB_ROLE, "COMMON"));
        } catch (IllegalArgumentException exception) {
            return SpawnRole.COMMON;
        }
    }

    private static CompoundTag merge(CompoundTag base, CompoundTag override) {
        if (base == null && override == null) return null;
        CompoundTag result = base == null ? new CompoundTag() : base.copy();
        if (override != null) result.merge(override.copy());
        return result;
    }

    private static int randomInclusive(int minimum, int maximum, long seed) {
        return minimum + (int) Math.floorMod(mix64(seed), (long) maximum - minimum + 1L);
    }

    private static int configuredMaximum() {
        return config(DungeonServerConfig.MAXIMUM_SPAWN_COUNT_PER_MARKER, 16);
    }

    private static int cap(int value) {
        return Math.max(1, Math.min(value, configuredMaximum()));
    }

    private static ResourceLocation configuredId(String value) {
        return ResourceLocation.tryParse(value);
    }

    private static void warn(Context context, SpawnRole role, ResourceLocation entity, String message) {
        if (!config(DungeonServerConfig.WARN_ON_MISSING_MOB_PROFILE, true)) return;
        logWarning(context, role, entity, message);
    }

    private static void warnEntity(Context context, SpawnRole role, ResourceLocation entity, String message) {
        if (!config(DungeonServerConfig.WARN_ON_MISSING_MOB_ENTITY, true)) return;
        logWarning(context, role, entity, message);
    }

    private static void logWarning(
        Context context, SpawnRole role, ResourceLocation entity, String message
    ) {
        String key = context.dungeonId() + "|" + context.roomId() + "|" + context.position() + "|" + message;
        if (!WARNED.add(key)) return;
        LOGGER.warn(
            "Dungeon mob fallback: {}; dungeon={}, theme={}, room={}, instance_marker={}, role={}, entity={}",
            message, context.dungeonId(), context.themeId(), context.roomId(), context.position(), role, entity);
    }

    private static long hash(long value, String text) {
        for (byte current : text.getBytes(StandardCharsets.UTF_8)) {
            value = (value ^ (current & 0xffL)) * 0x100000001b3L;
        }
        return value;
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static <T> T config(
        net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<T> value, T fallback) {
        try { return value.get(); }
        catch (IllegalStateException exception) { return fallback; }
    }

    private record Candidate(ResourceLocation profile, MobProfileManager.Selection selection, String source) {}
}

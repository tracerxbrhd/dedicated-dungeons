package dev.uapi.dungeons.level;

import dev.uapi.dungeons.DedicatedDungeonsMod;
import dev.uapi.dungeons.config.DungeonServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class DungeonLevelService {
    public static final ResourceLocation FIXED =
        ResourceLocation.fromNamespaceAndPath(DedicatedDungeonsMod.MOD_ID, "fixed");
    public static final ResourceLocation VANILLA_EXPERIENCE =
        ResourceLocation.fromNamespaceAndPath("u_api", "vanilla_experience");
    private static final java.util.Set<ResourceLocation> WARNED_MISSING = ConcurrentHashMap.newKeySet();

    private DungeonLevelService() {}

    public static int effectiveLevel(ServerPlayer initiator, Collection<ServerPlayer> participants) {
        List<ServerPlayer> ordered = new ArrayList<>(participants);
        ordered.removeIf(player -> player == null || player.isRemoved());
        ordered.sort(Comparator.comparing(player -> player.getUUID().toString()));
        ordered.remove(initiator);
        ordered.addFirst(initiator);
        List<Integer> levels = ordered.stream().map(DungeonLevelService::playerLevel).toList();
        int result = EffectiveLevelStrategy.byName(DungeonServerConfig.MULTIPLAYER_LEVEL_STRATEGY.get()).combine(levels);
        if (DungeonServerConfig.DEBUG_LEVEL_SELECTION.get()) {
            DedicatedDungeonsMod.LOGGER.info("Effective dungeon level {} from participants {} using {}", result,
                ordered.stream().map(ServerPlayer::getUUID).toList(), DungeonServerConfig.MULTIPLAYER_LEVEL_STRATEGY.get());
        }
        return result;
    }

    public static int playerLevel(ServerPlayer player) {
        ResourceLocation requested = ResourceLocation.tryParse(DungeonServerConfig.LEVEL_PROVIDER.get());
        if (requested == null) requested = VANILLA_EXPERIENCE;
        if (FIXED.equals(requested)) return DungeonServerConfig.FIXED_LEVEL.get();
        Optional<Integer> provided = uApiLevel(requested, player);
        if (provided.isEmpty()) {
            if (WARNED_MISSING.add(requested)) {
                DedicatedDungeonsMod.LOGGER.warn("Configured level provider {} is unavailable; using {}",
                    requested, VANILLA_EXPERIENCE);
            }
            return Math.max(0, player.experienceLevel);
        }
        return provided.get();
    }

    /**
     * U-API 2.0.0 predates the neutral level-provider registry. Reflection keeps this 1.21.1
     * release binary-compatible with that build while automatically enabling the registry once
     * it is present in a later compatible U-API revision.
     */
    private static Optional<Integer> uApiLevel(ResourceLocation id, ServerPlayer player) {
        if (VANILLA_EXPERIENCE.equals(id)) return Optional.of(Math.max(0, player.experienceLevel));
        try {
            Class<?> registry = Class.forName("dev.uapi.api.level.UApiLevelProviders");
            Method find = registry.getMethod("find", ResourceLocation.class);
            if (((Optional<?>) find.invoke(null, id)).isEmpty()) return Optional.empty();
            Method level = registry.getMethod("level", ResourceLocation.class, ServerPlayer.class);
            return Optional.of(Math.max(0, (Integer) level.invoke(null, id, player)));
        } catch (ReflectiveOperationException | LinkageError exception) {
            return Optional.empty();
        }
    }
}

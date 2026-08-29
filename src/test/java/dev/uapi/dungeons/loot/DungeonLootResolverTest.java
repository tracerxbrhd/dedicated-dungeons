package dev.uapi.dungeons.loot;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class DungeonLootResolverTest {
    private static final LootRole ROLE = LootRole.COMMON;
    private static final ResourceLocation DIRECT = id("chests/direct");
    private static final ResourceLocation HELPER_TABLE = id("chests/helper");
    private static final ResourceLocation DUNGEON_TABLE = id("chests/dungeon");
    private static final ResourceLocation FALLBACK = ResourceLocation.parse("minecraft:chests/stronghold_crossing");
    private static final ResourceLocation HELPER_PROFILE = id("helper");
    private static final ResourceLocation DUNGEON_PROFILE = id("dungeon");

    @BeforeEach
    void installProfiles() {
        LootProfile helper = profile(HELPER_PROFILE, HELPER_TABLE);
        LootProfile dungeon = profile(DUNGEON_PROFILE, DUNGEON_TABLE);
        LootProfile builtIn = profile(DungeonLootResolver.BUILTIN_PROFILE, FALLBACK);
        LootProfileManager.replaceForTests(Map.of(
            HELPER_PROFILE, helper,
            DUNGEON_PROFILE, dungeon,
            DungeonLootResolver.BUILTIN_PROFILE, builtIn
        ), Set.of(DIRECT, HELPER_TABLE, DUNGEON_TABLE, FALLBACK));
    }

    @AfterEach
    void clearProfiles() {
        LootProfileManager.replaceForTests(Map.of(), Set.of());
    }

    @Test
    void directLootTableHasHighestPriority() {
        LootMarkerData marker = new LootMarkerData(ROLE, HELPER_PROFILE, DIRECT, null, null, null);
        assertEquals(DIRECT, DungeonLootResolver.resolve(marker, context(DUNGEON_PROFILE), 1L).table());
        assertEquals("helper_direct", DungeonLootResolver.resolve(marker, context(DUNGEON_PROFILE), 1L).source());
    }

    @Test
    void helperProfileOverridesDungeonProfileAndResolvesRole() {
        LootMarkerData marker = new LootMarkerData(ROLE, HELPER_PROFILE, null, null, null, null);
        assertEquals(HELPER_TABLE, DungeonLootResolver.resolve(marker, context(DUNGEON_PROFILE), 2L).table());
    }

    @Test
    void missingProfileFallsBackWithoutCrashing() {
        LootMarkerData marker = new LootMarkerData(ROLE, id("missing"), null, null, null, null);
        assertEquals(DUNGEON_TABLE, DungeonLootResolver.resolve(marker, context(DUNGEON_PROFILE), 3L).table());
    }

    @Test
    void customNamespaceDirectTableIsAssignedNormally() {
        ResourceLocation custom = ResourceLocation.parse("example_pack:chests/frozen_reward");
        LootProfileManager.replaceForTests(LootProfileManager.profiles(),
            Set.of(DIRECT, HELPER_TABLE, DUNGEON_TABLE, FALLBACK, custom));
        LootMarkerData marker = new LootMarkerData(LootRole.BOSS, null, custom, null, null, null);
        assertEquals(custom, DungeonLootResolver.resolve(marker, context(null), 4L).table());
    }

    @Test
    void separateMarkersResolveIndependently() {
        LootMarkerData first = new LootMarkerData(ROLE, null, DIRECT, LootContainerType.CHEST, 11L, null);
        LootMarkerData second = new LootMarkerData(ROLE, HELPER_PROFILE, null, LootContainerType.BARREL, 22L, null);
        var resolvedFirst = DungeonLootResolver.resolve(first, context(DUNGEON_PROFILE), 100L);
        var resolvedSecond = DungeonLootResolver.resolve(second, context(DUNGEON_PROFILE), 100L);
        assertEquals(DIRECT, resolvedFirst.table());
        assertEquals(11L, resolvedFirst.seed());
        assertEquals(HELPER_TABLE, resolvedSecond.table());
        assertEquals(22L, resolvedSecond.seed());
        assertEquals(LootContainerType.BARREL, resolvedSecond.containerType());
    }

    @Test
    void deterministicSeedDiffersBetweenInstancesAndPositions() {
        UUID first = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("10000000-0000-0000-0000-000000000002");
        long base = DungeonLootResolver.seed(42L, first, new BlockPos(1, 64, 1), id("dungeon"));
        assertNotEquals(base, DungeonLootResolver.seed(42L, second, new BlockPos(1, 64, 1), id("dungeon")));
        assertNotEquals(base, DungeonLootResolver.seed(42L, first, new BlockPos(2, 64, 1), id("dungeon")));
        assertEquals(base, DungeonLootResolver.seed(42L, first, new BlockPos(1, 64, 1), id("dungeon")));
    }

    private static DungeonLootResolver.Context context(ResourceLocation dungeonProfile) {
        return new DungeonLootResolver.Context(id("dungeon"), id("theme"), id("room"), BlockPos.ZERO,
            null, Map.of(), dungeonProfile, Map.of(), null);
    }

    private static LootProfile profile(ResourceLocation id, ResourceLocation table) {
        return new LootProfile(id, null, Map.of(ROLE,
            new LootProfile.Entry(List.of(new LootProfile.WeightedTable(table, 1)))));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", path);
    }
}

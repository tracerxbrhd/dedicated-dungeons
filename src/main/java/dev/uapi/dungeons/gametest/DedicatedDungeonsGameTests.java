package dev.uapi.dungeons.gametest;

import dev.uapi.dungeons.content.DungeonContentRegistry;
import dev.uapi.dungeons.mob.DungeonMobResolver;
import dev.uapi.dungeons.mob.MobProfileManager;
import dev.uapi.dungeons.mob.MobSpawnData;
import dev.uapi.dungeons.mob.SpawnMode;
import dev.uapi.dungeons.mob.SpawnRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Map;

/**
 * Small live-server checks for behavior that unit tests cannot prove: reload listeners populate the
 * bundled test content and an absent modded entity fails safely.
 */
@GameTestHolder("dedicated_dungeons")
@PrefixGameTestTemplate(false)
public final class DedicatedDungeonsGameTests {
    private DedicatedDungeonsGameTests() {}

    @GameTest(template = "test/hall", timeoutTicks = 40)
    public static void bundledTestDungeonLoads(GameTestHelper helper) {
        helper.assertTrue(DungeonContentRegistry.dungeon(DungeonContentRegistry.DEFAULT_DUNGEON).isPresent(),
            "The bundled Forgotten Depths test dungeon was not loaded");
        helper.assertTrue(MobProfileManager.containsProfile(DungeonMobResolver.BUILTIN_PROFILE),
            "The built-in fallback mob profile was not loaded by the server reload listener");
        helper.succeed();
    }

    @GameTest(template = "test/hall", timeoutTicks = 40)
    public static void absentModdedDirectEntityUsesFallback(GameTestHelper helper) {
        MobSpawnData marker = new MobSpawnData(
            SpawnRole.COMMON,
            null,
            ResourceLocation.fromNamespaceAndPath("missing_test_mod", "missing_mob"),
            SpawnMode.DIRECT,
            1,
            null,
            null,
            null,
            new CompoundTag(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null);
        DungeonMobResolver.Context context = new DungeonMobResolver.Context(
            ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", "gametest"),
            null,
            ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", "test/hall"),
            BlockPos.ZERO,
            SpawnRole.COMMON,
            DungeonMobResolver.BUILTIN_PROFILE,
            Map.of(),
            null,
            Map.of(),
            null,
            1,
            1,
            false);

        DungeonMobResolver.Resolved resolved = DungeonMobResolver.resolve(marker, context, 42L);
        helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.containsKey(resolved.entity()),
            "An unavailable direct modded entity must resolve to a registered fallback");
        helper.assertFalse(resolved.entity().equals(marker.entity()),
            "The unavailable direct modded entity must never be returned");
        helper.succeed();
    }
}

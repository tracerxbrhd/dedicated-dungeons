package dev.uapi.dungeons.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static dev.uapi.dungeons.mob.MobProfileManagerTest.entry;
import static dev.uapi.dungeons.mob.MobProfileManagerTest.id;
import static dev.uapi.dungeons.mob.MobProfileManagerTest.profile;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class DungeonMobResolverTest {
    @BeforeEach
    void prepare() {
        MobProfileManager.replaceForTests(Map.of());
    }

    @AfterEach
    void clear() {
        MobProfileManager.replaceForTests(Map.of());
    }

    @Test
    void directEntityOverrideHasHighestPriority() {
        MobProfile helper = profile(id("helper"), null, SpawnRole.COMMON, false,
            entry("minecraft:skeleton", 1));
        MobProfileManager.replaceForTests(Map.of(helper.id(), helper));
        MobSpawnData marker = data(SpawnRole.COMMON, helper.id(),
            ResourceLocation.parse("minecraft:creeper"), 1);
        var result = DungeonMobResolver.resolve(marker, context(null, null, null), 7L);
        assertEquals(ResourceLocation.parse("minecraft:creeper"), result.entity());
        assertEquals("helper_direct", result.source());
    }

    @Test
    void helperProfileOverridesRoomAndDungeonProfiles() {
        MobProfile helper = profile(id("helper"), null, SpawnRole.COMMON, false,
            entry("minecraft:creeper", 1));
        MobProfile room = profile(id("room"), null, SpawnRole.COMMON, false,
            entry("minecraft:skeleton", 1));
        MobProfile dungeon = profile(id("dungeon"), null, SpawnRole.COMMON, false,
            entry("minecraft:zombie", 1));
        MobProfileManager.replaceForTests(Map.of(helper.id(), helper, room.id(), room, dungeon.id(), dungeon));
        var result = DungeonMobResolver.resolve(data(SpawnRole.COMMON, helper.id(), null, 1),
            context(room.id(), dungeon.id(), null), 8L);
        assertEquals(ResourceLocation.parse("minecraft:creeper"), result.entity());
        assertEquals("helper_profile", result.source());
    }

    @Test
    void roomProfileOverridesDungeonAndTheme() {
        MobProfile room = profile(id("room"), null, SpawnRole.COMMON, false,
            entry("minecraft:skeleton", 1));
        MobProfile dungeon = profile(id("dungeon"), null, SpawnRole.COMMON, false,
            entry("minecraft:zombie", 1));
        MobProfile theme = profile(id("theme"), null, SpawnRole.COMMON, false,
            entry("minecraft:spider", 1));
        MobProfileManager.replaceForTests(Map.of(room.id(), room, dungeon.id(), dungeon, theme.id(), theme));
        var result = DungeonMobResolver.resolve(MobSpawnData.EMPTY,
            context(room.id(), dungeon.id(), theme.id()), 9L);
        assertEquals(ResourceLocation.parse("minecraft:skeleton"), result.entity());
        assertEquals("room_profile", result.source());
    }

    @Test
    void markerCountAndNbtOverrideProfileDefaults() {
        CompoundTag profileNbt = new CompoundTag();
        profileNbt.putBoolean("PersistenceRequired", true);
        CompoundTag markerNbt = new CompoundTag();
        markerNbt.putString("CustomName", "{\"text\":\"Guard\"}");
        MobProfile.Entry configured = new MobProfile.Entry(ResourceLocation.parse("minecraft:zombie"),
            1, 2, 2, 0, 6, 1, 16, profileNbt, java.util.List.of(), true, 0.5, true);
        MobProfile profile = profile(id("profile"), null, SpawnRole.COMMON, false, configured);
        MobProfileManager.replaceForTests(Map.of(profile.id(), profile));
        MobSpawnData marker = new MobSpawnData(SpawnRole.COMMON, profile.id(), null, SpawnMode.DIRECT,
            3, null, null, null, markerNbt, null, null, null, null, null, null, null, null);
        var result = DungeonMobResolver.resolve(marker, context(null, null, null), 10L);
        assertEquals(3, result.count());
        assertEquals("Guard", result.entityNbt().getString("CustomName").contains("Guard") ? "Guard" : "");
        assertEquals(true, result.entityNbt().getBoolean("PersistenceRequired"));
    }

    private static DungeonMobResolver.Context context(ResourceLocation room, ResourceLocation dungeon,
                                                      ResourceLocation theme) {
        return new DungeonMobResolver.Context(id("test_dungeon"), id("test_theme"), id("test_room"),
            BlockPos.ZERO, SpawnRole.COMMON, room, Map.of(), dungeon, Map.of(), theme,
            0, 1, false);
    }

    private static MobSpawnData data(SpawnRole role, ResourceLocation profile,
                                     ResourceLocation entity, Integer count) {
        return new MobSpawnData(role, profile, entity, SpawnMode.DIRECT, count,
            null, null, null, null, null, null, null, null, null, null, null, null);
    }
}

package dev.uapi.dungeons.mob;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class MobSpawnDataTest {
    @Test
    void canonicalBlockEntityDataSurvivesStructureNbtRoundTrip() {
        CompoundTag entityNbt = new CompoundTag();
        entityNbt.putBoolean("PersistenceRequired", true);
        MobSpawnData expected = new MobSpawnData(SpawnRole.RANGED,
            ResourceLocation.parse("example_pack:frozen"),
            ResourceLocation.parse("minecraft:stray"), SpawnMode.SPAWNER,
            2, 3, "north", 42L, entityNbt,
            20, 100, 300, 2, 8, 20, 5, 12);
        CompoundTag tag = new CompoundTag();
        expected.save(tag);
        assertEquals(expected, MobSpawnData.load(tag));
    }

    @Test
    void legacyEntityKeysStillLoad() {
        for (String key : java.util.List.of("mob", "entity_id", "spawn_entity", "spawner_entity")) {
            CompoundTag tag = new CompoundTag();
            tag.putString(key, "minecraft:zombie");
            assertEquals(ResourceLocation.parse("minecraft:zombie"), MobSpawnData.load(tag).entity(), key);
        }
    }
}

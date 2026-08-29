package dev.uapi.dungeons.loot;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class LootMarkerDataTest {
    @Test
    void canonicalBlockEntityDataSurvivesStructureNbtRoundTrip() {
        LootMarkerData expected = new LootMarkerData(LootRole.SUPPLY,
            ResourceLocation.parse("example_pack:ice_dungeon"),
            ResourceLocation.parse("example_pack:chests/supplies"),
            LootContainerType.BARREL, 987654321L, true);
        CompoundTag tag = new CompoundTag();
        expected.save(tag);
        assertEquals(expected, LootMarkerData.load(tag));
    }

    @Test
    void legacyLootTableAndSeedStillLoad() {
        CompoundTag tag = new CompoundTag();
        tag.putString("LootTable", "minecraft:chests/stronghold_crossing");
        tag.putLong("LootTableSeed", 42L);
        LootMarkerData loaded = LootMarkerData.load(tag);
        assertEquals(ResourceLocation.parse("minecraft:chests/stronghold_crossing"), loaded.table());
        assertEquals(42L, loaded.seed());
    }

    @Test
    void invalidLegacyIdDoesNotCrashOrEnterRuntimeState() {
        CompoundTag tag = new CompoundTag();
        tag.putString("LootTable", "bad id");
        LootMarkerData loaded = LootMarkerData.load(tag);
        assertNull(loaded.table());
    }
}

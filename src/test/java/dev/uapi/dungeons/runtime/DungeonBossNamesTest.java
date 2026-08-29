package dev.uapi.dungeons.runtime;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

final class DungeonBossNamesTest {
    @Test
    void nameIsStableForTheSameDungeonButVariesAcrossSeeds() {
        ResourceLocation boss = ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", "zombie_champion");

        assertEquals(DungeonBossNames.generate(42L, boss), DungeonBossNames.generate(42L, boss));
        assertNotEquals(DungeonBossNames.generate(42L, boss), DungeonBossNames.generate(43L, boss));
    }
}

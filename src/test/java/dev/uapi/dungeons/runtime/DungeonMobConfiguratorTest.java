package dev.uapi.dungeons.runtime;

import net.minecraft.world.entity.MobSpawnType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DungeonMobConfiguratorTest {
    @Test
    void enablesApotheosisAffixAugmentationsOnlyForDungeonEncountersWhenInstalled() {
        assertEquals(MobSpawnType.SPAWN_EGG, DungeonMobConfigurator.dungeonSpawnType(true));
        assertEquals(MobSpawnType.SPAWNER, DungeonMobConfigurator.dungeonSpawnType(false));
    }
}

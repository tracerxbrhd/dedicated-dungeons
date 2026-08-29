package dev.uapi.dungeons.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DungeonSessionPersistenceTest {
    @Test
    void preservesClearConditionAndBossDefeatAcrossRestart() {
        UUID instanceId = UUID.fromString("12345678-1234-5678-9abc-def012345678");
        DungeonSession session = new DungeonSession(instanceId, 3,
            ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", "forgotten_depths"),
            ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                ResourceLocation.withDefaultNamespace("overworld")),
            new BlockPos(12, 70, -4), 27, 99L, "boss_and_encounters", new BlockPos(4096, 64, 0));
        session.bossDefeated(true);
        session.startedAtMillis(123_456L);
        session.nextPressureAtMillis(234_567L);
        session.pressureWaves(7);

        DungeonSession loaded = DungeonSession.load(session.save());

        assertEquals("boss_and_encounters", loaded.clearCondition());
        assertTrue(loaded.bossDefeated());
        assertEquals(27, loaded.selectedLevel());
        assertEquals(99L, loaded.seed());
        assertEquals(123_456L, loaded.startedAtMillis());
        assertEquals(234_567L, loaded.nextPressureAtMillis());
        assertEquals(7, loaded.pressureWaves());
    }
}

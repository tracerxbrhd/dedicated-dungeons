package dev.uapi.dungeons.generation;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DungeonLootAssignmentTest {
    @Test
    void containerKeepsDeferredLootTableUntilVanillaOpensIt() {
        ResourceLocation table = ResourceLocation.parse("example_pack:chests/custom_boss_reward");
        var assignment = DungeonGenerationService.deferredLootAssignment(table, 123L);
        assertEquals(table, assignment.table().location());
        assertEquals(123L, assignment.seed());
    }
}

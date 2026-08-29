package dev.uapi.dungeons.generation;

import dev.uapi.dungeons.content.DungeonContentRegistry.WeightedValue;
import dev.uapi.dungeons.content.DungeonContentTypes.Boss;
import dev.uapi.dungeons.content.DungeonContentTypes.Requirements;
import dev.uapi.dungeons.content.DungeonContentTypes.RoomSize;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DungeonGeometryTest {
    @Test
    void rotatesLocalCoordinatesAroundStructureOrigin() {
        BlockPos point = new BlockPos(3, 2, 5);
        assertEquals(new BlockPos(-5, 2, 3), DungeonGraphPlanner.rotate(point, Rotation.CLOCKWISE_90));
        assertEquals(new BlockPos(-3, 2, -5), DungeonGraphPlanner.rotate(point, Rotation.CLOCKWISE_180));
        assertEquals(new BlockPos(5, 2, -3), DungeonGraphPlanner.rotate(point, Rotation.COUNTERCLOCKWISE_90));
    }

    @Test
    void collisionUsesInclusiveBounds() {
        WorldBounds room = new WorldBounds(0, 0, 0, 10, 5, 10);
        assertTrue(room.intersects(new WorldBounds(10, 1, 10, 20, 4, 20)));
        assertFalse(room.intersects(new WorldBounds(11, 0, 0, 20, 5, 10)));
    }

    @Test
    void excludesThePreviousBossWhenAnotherCompatibleBossExists() {
        Boss hydra = boss("hydra");
        Boss gorgon = boss("gorgon");
        List<WeightedValue<Boss>> choices = List.of(new WeightedValue<>(hydra, 3),
            new WeightedValue<>(gorgon, 2));

        List<WeightedValue<Boss>> filtered = DungeonGraphPlanner.withoutImmediateRepeat(
            choices, hydra.id());

        assertEquals(List.of(gorgon), filtered.stream().map(WeightedValue::value).toList());
        List<WeightedValue<Boss>> onlyHydra = List.of(choices.getFirst());
        assertEquals(onlyHydra, DungeonGraphPlanner.withoutImmediateRepeat(onlyHydra, hydra.id()));
    }

    private static Boss boss(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", path);
        return new Boss(id, ResourceLocation.withDefaultNamespace("zombie"), Set.of(RoomSize.BOSS),
            80.0, 8.0, ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", "basic"),
            1, 0, 6, new Requirements(List.of()));
    }
}

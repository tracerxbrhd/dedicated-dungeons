package dev.uapi.dungeons.data;

import dev.uapi.dungeons.content.DungeonContentTypes.MarkerType;
import dev.uapi.dungeons.generation.GeneratedDungeonPlan;
import dev.uapi.dungeons.generation.WorldBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GeneratedDungeonPlanPersistenceTest {
    @Test
    void preservesMarkersAndBossSnapshotAcrossSaveLoad() {
        var bounds = new WorldBounds(0, 60, 0, 20, 80, 20);
        var marker = new GeneratedDungeonPlan.PlacedMarker(MarkerType.BOSS_SPAWN, new BlockPos(10, 65, 10),
            Direction.WEST, resource("vanilla_bosses"), "boss", 0);
        var piece = new GeneratedDungeonPlan.Piece(GeneratedDungeonPlan.PieceType.ROOM, resource("boss_chamber"),
            BlockPos.ZERO, Rotation.CLOCKWISE_90, bounds, List.of(new BlockPos(0, 65, 10)));
        var plan = new GeneratedDungeonPlan(resource("forgotten_depths"), resource("basic"),
            resource("zombie_champion"), ResourceLocation.withDefaultNamespace("zombie"), 120.0, 12.0,
            resource("basic"), List.of(piece), bounds, new BlockPos(5, 65, 5), new BlockPos(10, 65, 10),
            new BlockPos(18, 65, 10), List.of(marker));

        GeneratedDungeonPlan loaded = GeneratedDungeonPlan.load(plan.save());
        assertEquals(ResourceLocation.withDefaultNamespace("zombie"), loaded.bossEntityType());
        assertEquals(120.0, loaded.bossHealth());
        assertEquals(12.0, loaded.bossDamage());
        assertEquals(marker, loaded.markers().getFirst());
        assertEquals(piece, loaded.pieces().getFirst());
    }

    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", path);
    }
}

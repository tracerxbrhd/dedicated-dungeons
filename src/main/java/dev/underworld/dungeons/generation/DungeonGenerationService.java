package dev.underworld.dungeons.generation;

import dev.underworld.dungeons.content.DungeonContentRegistry;
import dev.underworld.dungeons.content.DungeonContentTypes.Palette;
import dev.underworld.dungeons.generation.GeneratedDungeonPlan.Piece;
import dev.underworld.dungeons.generation.GeneratedDungeonPlan.PieceType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.AABB;

/** Applies an already validated plan. Planning remains side-effect free. */
public final class DungeonGenerationService {
    private DungeonGenerationService() {}

    public static boolean build(ServerLevel level, GeneratedDungeonPlan plan) {
        for (Piece piece : plan.pieces()) {
            ResourcePiece definition = definition(piece);
            if (definition == null) return false;
            if (definition.structure() == null) buildProcedural(level, piece.bounds(), definition.palette());
            else {
                var template = level.getStructureManager().get(definition.structure()).orElse(null);
                if (template == null) return false;
                StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(piece.rotation())
                    .setIgnoreEntities(false).setFinalizeEntities(true);
                if (!template.placeInWorld(level, piece.origin(), piece.origin(), settings, level.random, Block.UPDATE_ALL)) return false;
            }
        }
        for (Piece piece : plan.pieces()) for (BlockPos doorway : piece.doorways()) carveDoorway(level, doorway);
        return true;
    }

    public static void cleanup(ServerLevel level, GeneratedDungeonPlan plan) {
        level.getEntities((Entity) null, toAabb(plan.bounds()), entity -> !(entity instanceof Player)).forEach(Entity::discard);
        for (Piece piece : plan.pieces()) fillAir(level, piece.bounds());
        for (Piece piece : plan.pieces()) for (BlockPos doorway : piece.doorways())
            for (BlockPos pos : BlockPos.betweenClosed(doorway.offset(-1, 0, -1), doorway.offset(1, 3, 1)))
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    public static void forceChunks(ServerLevel level, WorldBounds bounds, boolean forced) {
        ChunkPos min = new ChunkPos(new BlockPos(bounds.minX() - 16, 0, bounds.minZ() - 16));
        ChunkPos max = new ChunkPos(new BlockPos(bounds.maxX() + 16, 0, bounds.maxZ() + 16));
        for (int x = min.x; x <= max.x; x++) for (int z = min.z; z <= max.z; z++) level.setChunkForced(x, z, forced);
    }

    private static void buildProcedural(ServerLevel level, WorldBounds bounds, Palette palette) {
        Block floor = BuiltInRegistries.BLOCK.get(palette.floor());
        Block wall = BuiltInRegistries.BLOCK.get(palette.wall());
        Block accent = BuiltInRegistries.BLOCK.get(palette.accent());
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) for (int z = bounds.minZ(); z <= bounds.maxZ(); z++)
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                boolean floorOrCeiling = y == bounds.minY() || y == bounds.maxY();
                boolean edge = x == bounds.minX() || x == bounds.maxX() || z == bounds.minZ() || z == bounds.maxZ();
                Block block = floorOrCeiling ? floor : edge ? (((x * 31 + y * 17 + z) & 15) == 0 ? accent : wall) : Blocks.AIR;
                level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
    }

    private static void carveDoorway(ServerLevel level, BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 2, 1)))
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static void fillAir(ServerLevel level, WorldBounds bounds) {
        for (BlockPos pos : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(),
            bounds.maxX(), bounds.maxY(), bounds.maxZ()))
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static AABB toAabb(WorldBounds bounds) {
        return new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX() + 1, bounds.maxY() + 1, bounds.maxZ() + 1);
    }

    private static ResourcePiece definition(Piece piece) {
        if (piece.type() == PieceType.ROOM) return DungeonContentRegistry.room(piece.definitionId())
            .map(value -> new ResourcePiece(value.structure(), value.palette())).orElse(null);
        return DungeonContentRegistry.connector(piece.definitionId())
            .map(value -> new ResourcePiece(value.structure(), value.palette())).orElse(null);
    }

    private record ResourcePiece(net.minecraft.resources.ResourceLocation structure, Palette palette) {}
}

package dev.underworld.dungeons.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;

import java.util.List;

public record GeneratedDungeonPlan(ResourceLocation themeId, ResourceLocation archetypeId,
                                   ResourceLocation bossId, ResourceLocation rewardPool,
                                   List<Piece> pieces, WorldBounds bounds,
                                   BlockPos playerSpawn, BlockPos bossSpawn, BlockPos exitPosition) {
    public GeneratedDungeonPlan { pieces = List.copyOf(pieces); }

    public enum PieceType { ROOM, CONNECTOR }
    public record Piece(PieceType type, ResourceLocation definitionId, BlockPos origin,
                        Rotation rotation, WorldBounds bounds, List<BlockPos> doorways) {
        public Piece { doorways = List.copyOf(doorways); }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("theme", themeId.toString()); tag.putString("archetype", archetypeId.toString());
        tag.putString("boss", bossId.toString()); tag.putString("rewardPool", rewardPool.toString());
        tag.put("bounds", bounds.save()); tag.putLong("playerSpawn", playerSpawn.asLong());
        tag.putLong("bossSpawn", bossSpawn.asLong()); tag.putLong("exit", exitPosition.asLong());
        ListTag pieceList = new ListTag();
        for (Piece piece : pieces) {
            CompoundTag value = new CompoundTag();
            value.putString("type", piece.type().name()); value.putString("definition", piece.definitionId().toString());
            value.putLong("origin", piece.origin().asLong()); value.putString("rotation", piece.rotation().name());
            value.put("bounds", piece.bounds().save());
            value.putLongArray("doorways", piece.doorways().stream().mapToLong(BlockPos::asLong).toArray());
            pieceList.add(value);
        }
        tag.put("pieces", pieceList);
        return tag;
    }

    public static GeneratedDungeonPlan load(CompoundTag tag) {
        List<Piece> pieces = new java.util.ArrayList<>();
        for (Tag raw : tag.getList("pieces", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) raw;
            List<BlockPos> doors = java.util.Arrays.stream(value.getLongArray("doorways")).mapToObj(BlockPos::of).toList();
            pieces.add(new Piece(PieceType.valueOf(value.getString("type")), ResourceLocation.parse(value.getString("definition")),
                BlockPos.of(value.getLong("origin")), Rotation.valueOf(value.getString("rotation")),
                WorldBounds.load(value.getCompound("bounds")), doors));
        }
        return new GeneratedDungeonPlan(ResourceLocation.parse(tag.getString("theme")),
            ResourceLocation.parse(tag.getString("archetype")), ResourceLocation.parse(tag.getString("boss")),
            ResourceLocation.parse(tag.getString("rewardPool")), pieces, WorldBounds.load(tag.getCompound("bounds")),
            BlockPos.of(tag.getLong("playerSpawn")), BlockPos.of(tag.getLong("bossSpawn")), BlockPos.of(tag.getLong("exit")));
    }
}

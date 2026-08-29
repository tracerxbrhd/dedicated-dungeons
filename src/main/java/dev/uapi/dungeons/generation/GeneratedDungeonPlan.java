package dev.uapi.dungeons.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import dev.uapi.dungeons.content.DungeonContentTypes.MarkerType;
import dev.uapi.dungeons.loot.LootMarkerData;
import dev.uapi.dungeons.mob.MobSpawnData;

import java.util.List;

public record GeneratedDungeonPlan(ResourceLocation themeId, ResourceLocation archetypeId,
                                   ResourceLocation bossId, ResourceLocation bossEntityType,
                                   double bossHealth, double bossDamage, ResourceLocation rewardPool,
                                   List<Piece> pieces, WorldBounds bounds,
                                   BlockPos playerSpawn, BlockPos bossSpawn, BlockPos exitPosition,
                                   List<PlacedMarker> markers) {
    public GeneratedDungeonPlan {
        pieces = List.copyOf(pieces);
        markers = List.copyOf(markers);
    }

    public GeneratedDungeonPlan(ResourceLocation themeId, ResourceLocation archetypeId,
                                ResourceLocation bossId, ResourceLocation rewardPool,
                                List<Piece> pieces, WorldBounds bounds,
                                BlockPos playerSpawn, BlockPos bossSpawn, BlockPos exitPosition) {
        this(themeId, archetypeId, bossId, ResourceLocation.withDefaultNamespace("zombie"), 80.0, 8.0,
            rewardPool, pieces, bounds, playerSpawn, bossSpawn, exitPosition, List.of());
    }

    public enum PieceType { ROOM, CONNECTOR }
    public record Piece(PieceType type, ResourceLocation definitionId, BlockPos origin,
                        Rotation rotation, WorldBounds bounds, List<BlockPos> doorways) {
        public Piece { doorways = List.copyOf(doorways); }
    }

    public record PlacedMarker(MarkerType type, BlockPos position, Direction facing,
                               ResourceLocation profile, String group, int order,
                               ResourceLocation roomId, LootMarkerData loot, MobSpawnData spawn) {
        public PlacedMarker {
            loot = loot == null ? LootMarkerData.EMPTY : loot;
            spawn = spawn == null ? MobSpawnData.EMPTY : spawn;
        }
        public PlacedMarker(MarkerType type, BlockPos position, Direction facing,
                            ResourceLocation profile, String group, int order) {
            this(type, position, facing, profile, group, order, null,
                LootMarkerData.EMPTY, MobSpawnData.EMPTY);
        }
        public PlacedMarker(MarkerType type, BlockPos position, Direction facing,
                            ResourceLocation profile, String group, int order,
                            ResourceLocation roomId, LootMarkerData loot) {
            this(type, position, facing, profile, group, order, roomId, loot, MobSpawnData.EMPTY);
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("theme", themeId.toString()); tag.putString("archetype", archetypeId.toString());
        tag.putString("boss", bossId.toString()); tag.putString("bossEntity", bossEntityType.toString());
        tag.putDouble("bossHealth", bossHealth); tag.putDouble("bossDamage", bossDamage);
        tag.putString("rewardPool", rewardPool.toString());
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
        ListTag markerList = new ListTag();
        for (PlacedMarker marker : markers) {
            CompoundTag value = new CompoundTag();
            value.putString("type", marker.type().name());
            value.putLong("position", marker.position().asLong());
            value.putString("facing", marker.facing().getName());
            if (marker.profile() != null) value.putString("profile", marker.profile().toString());
            value.putString("group", marker.group());
            value.putInt("order", marker.order());
            if (marker.roomId() != null) value.putString("room", marker.roomId().toString());
            CompoundTag loot = new CompoundTag();
            marker.loot().save(loot);
            if (!loot.isEmpty()) value.put("loot", loot);
            CompoundTag spawn = new CompoundTag();
            marker.spawn().save(spawn);
            if (!spawn.isEmpty()) value.put("spawn", spawn);
            markerList.add(value);
        }
        tag.put("markers", markerList);
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
        List<PlacedMarker> markers = new java.util.ArrayList<>();
        for (Tag raw : tag.getList("markers", Tag.TAG_COMPOUND)) {
            CompoundTag value = (CompoundTag) raw;
            Direction facing = Direction.byName(value.getString("facing"));
            markers.add(new PlacedMarker(MarkerType.valueOf(value.getString("type")),
                BlockPos.of(value.getLong("position")), facing == null ? Direction.NORTH : facing,
                value.contains("profile") ? ResourceLocation.parse(value.getString("profile")) : null,
                value.getString("group"), value.getInt("order"),
                value.contains("room") ? ResourceLocation.parse(value.getString("room")) : null,
                value.contains("loot", Tag.TAG_COMPOUND)
                    ? LootMarkerData.load(value.getCompound("loot")) : LootMarkerData.EMPTY,
                value.contains("spawn", Tag.TAG_COMPOUND)
                    ? MobSpawnData.load(value.getCompound("spawn")) : MobSpawnData.EMPTY));
        }
        return new GeneratedDungeonPlan(ResourceLocation.parse(tag.getString("theme")),
            ResourceLocation.parse(tag.getString("archetype")), ResourceLocation.parse(tag.getString("boss")),
            tag.contains("bossEntity") ? ResourceLocation.parse(tag.getString("bossEntity")) : ResourceLocation.withDefaultNamespace("zombie"),
            tag.contains("bossHealth") ? tag.getDouble("bossHealth") : 80.0,
            tag.contains("bossDamage") ? tag.getDouble("bossDamage") : 8.0,
            ResourceLocation.parse(tag.getString("rewardPool")), pieces, WorldBounds.load(tag.getCompound("bounds")),
            BlockPos.of(tag.getLong("playerSpawn")), BlockPos.of(tag.getLong("bossSpawn")), BlockPos.of(tag.getLong("exit")), markers);
    }
}

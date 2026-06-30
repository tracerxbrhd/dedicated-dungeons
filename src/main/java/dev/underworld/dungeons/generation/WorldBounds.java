package dev.underworld.dungeons.generation;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Inclusive integer bounds, safe to persist without floating-point conversion. */
public record WorldBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    public WorldBounds {
        if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("invalid world bounds");
    }
    public boolean contains(BlockPos pos) {
        return pos.getX() >= minX && pos.getX() <= maxX && pos.getY() >= minY && pos.getY() <= maxY
            && pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }
    public boolean intersects(WorldBounds other) {
        return maxX >= other.minX && minX <= other.maxX && maxY >= other.minY && minY <= other.maxY
            && maxZ >= other.minZ && minZ <= other.maxZ;
    }
    public WorldBounds union(WorldBounds other) {
        return new WorldBounds(Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
            Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ));
    }
    public WorldBounds inflate(int horizontal, int vertical) {
        return new WorldBounds(minX - horizontal, minY - vertical, minZ - horizontal,
            maxX + horizontal, maxY + vertical, maxZ + horizontal);
    }
    public int sizeX() { return maxX - minX + 1; }
    public int sizeY() { return maxY - minY + 1; }
    public int sizeZ() { return maxZ - minZ + 1; }
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("minX", minX); tag.putInt("minY", minY); tag.putInt("minZ", minZ);
        tag.putInt("maxX", maxX); tag.putInt("maxY", maxY); tag.putInt("maxZ", maxZ);
        return tag;
    }
    public static WorldBounds load(CompoundTag tag) {
        return new WorldBounds(tag.getInt("minX"), tag.getInt("minY"), tag.getInt("minZ"),
            tag.getInt("maxX"), tag.getInt("maxY"), tag.getInt("maxZ"));
    }
}

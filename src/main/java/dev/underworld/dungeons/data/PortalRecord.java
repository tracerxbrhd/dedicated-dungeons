package dev.underworld.dungeons.data;

import dev.underworld.api.difficulty.DifficultyRank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.UUID;

public record PortalRecord(UUID id, UUID ownerId, UUID instanceId, ResourceKey<Level> dimension,
                           BlockPos position, DifficultyRank rank, long deadlineMillis) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id); tag.putUUID("owner", ownerId); tag.putUUID("instance", instanceId);
        tag.putString("dimension", dimension.location().toString()); tag.putLong("position", position.asLong());
        tag.putString("rank", rank.name()); tag.putLong("deadline", deadlineMillis);
        return tag;
    }
    public static PortalRecord load(CompoundTag tag) {
        return new PortalRecord(tag.getUUID("id"), tag.getUUID("owner"), tag.getUUID("instance"),
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("dimension"))),
            BlockPos.of(tag.getLong("position")), DifficultyRank.byName(tag.getString("rank")), tag.getLong("deadline"));
    }
}

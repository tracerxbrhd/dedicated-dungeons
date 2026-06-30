package dev.underworld.dungeons.data;

import dev.underworld.api.difficulty.DifficultyRank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import dev.underworld.dungeons.portal.PortalOrigin;

import java.util.UUID;

public record PortalRecord(UUID id, UUID ownerId, UUID instanceId, ResourceKey<Level> dimension,
                           BlockPos position, DifficultyRank rank, ResourceLocation archetypeId, PortalOrigin origin,
                           long deadlineMillis, boolean entered) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id); tag.putUUID("owner", ownerId); tag.putUUID("instance", instanceId);
        tag.putString("dimension", dimension.location().toString()); tag.putLong("position", position.asLong());
        tag.putString("rank", rank.name()); tag.putString("archetype", archetypeId.toString());
        tag.putString("origin", origin.name()); tag.putLong("deadline", deadlineMillis); tag.putBoolean("entered", entered);
        return tag;
    }
    public PortalRecord markEntered() {
        return entered ? this : new PortalRecord(id, ownerId, instanceId, dimension, position, rank, archetypeId,
            origin, deadlineMillis, true);
    }
    public static PortalRecord load(CompoundTag tag) {
        return new PortalRecord(tag.getUUID("id"), tag.getUUID("owner"), tag.getUUID("instance"),
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("dimension"))),
            BlockPos.of(tag.getLong("position")), DifficultyRank.byName(tag.getString("rank")),
            ResourceLocation.parse(tag.contains("archetype") ? tag.getString("archetype") : "dedicated_dungeons:basic"),
            tag.contains("origin") ? PortalOrigin.valueOf(tag.getString("origin")) : PortalOrigin.RANDOM,
            tag.getLong("deadline"), tag.getBoolean("entered"));
    }
}

package dev.uapi.dungeons.data;

import dev.uapi.difficulty.DifficultyRank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import dev.uapi.dungeons.portal.PortalOrigin;

import java.util.UUID;
import java.util.Optional;
import java.util.Objects;

public record PortalRecord(UUID id, UUID ownerId, UUID instanceId, ResourceKey<Level> dimension,
                           BlockPos position, DifficultyRank rank, ResourceLocation archetypeId, PortalOrigin origin,
                           long deadlineMillis, boolean entered, Optional<UUID> groupId, int selectedLevel) {
    public PortalRecord {
        groupId = Objects.requireNonNull(groupId, "groupId");
        selectedLevel = Math.max(0, selectedLevel);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id); tag.putUUID("owner", ownerId); tag.putUUID("instance", instanceId);
        tag.putString("dimension", dimension.location().toString()); tag.putLong("position", position.asLong());
        tag.putString("rank", rank.name()); tag.putString("archetype", archetypeId.toString());
        tag.putString("origin", origin.name()); tag.putLong("deadline", deadlineMillis); tag.putBoolean("entered", entered);
        tag.putInt("selectedLevel", selectedLevel);
        groupId.ifPresent(value -> tag.putUUID("socialGroup", value));
        return tag;
    }
    public PortalRecord markEntered() {
        return entered ? this : new PortalRecord(id, ownerId, instanceId, dimension, position, rank, archetypeId,
            origin, deadlineMillis, true, groupId, selectedLevel);
    }
    public static PortalRecord load(CompoundTag tag) {
        return new PortalRecord(tag.getUUID("id"), tag.getUUID("owner"), tag.getUUID("instance"),
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("dimension"))),
            BlockPos.of(tag.getLong("position")), DifficultyRank.byName(tag.getString("rank")),
            ResourceLocation.parse(tag.getString("archetype")),
            PortalOrigin.valueOf(tag.getString("origin")),
            tag.getLong("deadline"), tag.getBoolean("entered"),
            tag.hasUUID("socialGroup") ? Optional.of(tag.getUUID("socialGroup")) : Optional.empty(),
            tag.contains("selectedLevel") ? tag.getInt("selectedLevel") : DifficultyRank.byName(tag.getString("rank")).minimumLevel());
    }
}

package dev.underworld.dungeons.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.UUID;

public final class DungeonSession {
    private final UUID instanceId;
    private final int slot;
    private final ResourceLocation templateId;
    private final ResourceKey<Level> originDimension;
    private final BlockPos originPosition;
    private UUID bossId;
    private boolean exitActive;
    private boolean bossEnraged;

    public DungeonSession(UUID instanceId, int slot, ResourceLocation templateId,
                          ResourceKey<Level> originDimension, BlockPos originPosition) {
        this.instanceId = instanceId; this.slot = slot; this.templateId = templateId;
        this.originDimension = originDimension; this.originPosition = originPosition.immutable();
    }
    public UUID instanceId() { return instanceId; }
    public int slot() { return slot; }
    public ResourceLocation templateId() { return templateId; }
    public ResourceKey<Level> originDimension() { return originDimension; }
    public BlockPos originPosition() { return originPosition; }
    public UUID bossId() { return bossId; }
    public void bossId(UUID bossId) { this.bossId = bossId; }
    public boolean exitActive() { return exitActive; }
    public void exitActive(boolean value) { exitActive = value; }
    public boolean bossEnraged() { return bossEnraged; }
    public void bossEnraged(boolean value) { bossEnraged = value; }
    public BlockPos center() { return new BlockPos(slot * 512, 64, 0); }
    public BlockPos exitPosition() { return center().offset(0, 1, 10); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("instance", instanceId); tag.putInt("slot", slot); tag.putString("template", templateId.toString());
        tag.putString("originDimension", originDimension.location().toString());
        tag.putLong("originPosition", originPosition.asLong());
        if (bossId != null) tag.putUUID("boss", bossId);
        tag.putBoolean("exit", exitActive); tag.putBoolean("enraged", bossEnraged);
        return tag;
    }
    public static DungeonSession load(CompoundTag tag) {
        ResourceKey<Level> originDimension = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.parse(tag.contains("originDimension") ? tag.getString("originDimension") : "minecraft:overworld"));
        BlockPos originPosition = tag.contains("originPosition") ? BlockPos.of(tag.getLong("originPosition")) : BlockPos.ZERO;
        DungeonSession session = new DungeonSession(tag.getUUID("instance"), tag.getInt("slot"),
            ResourceLocation.parse(tag.getString("template")), originDimension, originPosition);
        if (tag.hasUUID("boss")) session.bossId(tag.getUUID("boss"));
        session.exitActive(tag.getBoolean("exit")); session.bossEnraged(tag.getBoolean("enraged"));
        return session;
    }
}

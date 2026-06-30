package dev.underworld.dungeons.data;

import dev.underworld.dungeons.config.DungeonServerConfig;
import dev.underworld.dungeons.generation.GeneratedDungeonPlan;
import dev.underworld.dungeons.generation.WorldBounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.UUID;
import java.util.LinkedHashSet;
import java.util.Set;

public final class DungeonSession {
    private final UUID instanceId;
    private final int slot;
    private final ResourceLocation templateId;
    private final ResourceKey<Level> originDimension;
    private final BlockPos originPosition;
    private UUID bossId;
    private boolean exitActive;
    private boolean bossEnraged;
    private final BlockPos center;
    private GeneratedDungeonPlan plan;
    private final Set<UUID> eligibleRewards = new LinkedHashSet<>();
    private final Set<UUID> claimedRewards = new LinkedHashSet<>();
    private final Set<UUID> returnedPlayers = new LinkedHashSet<>();
    private boolean cleanupPrepared;
    private int currentWave;
    private long nextWaveAtMillis;

    public DungeonSession(UUID instanceId, int slot, ResourceLocation templateId,
                          ResourceKey<Level> originDimension, BlockPos originPosition) {
        this(instanceId, slot, templateId, originDimension, originPosition,
            new BlockPos(slot * DungeonServerConfig.INSTANCE_SLOT_SPACING.get(), 64, 0));
    }
    private DungeonSession(UUID instanceId, int slot, ResourceLocation templateId,
                           ResourceKey<Level> originDimension, BlockPos originPosition, BlockPos center) {
        this.instanceId = instanceId; this.slot = slot; this.templateId = templateId;
        this.originDimension = originDimension; this.originPosition = originPosition.immutable(); this.center = center.immutable();
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
    public BlockPos center() { return center; }
    public BlockPos exitPosition() { return plan == null ? center().offset(0, 1, 10) : plan.exitPosition(); }
    public GeneratedDungeonPlan plan() { return plan; }
    public void plan(GeneratedDungeonPlan value) { plan = value; }
    public WorldBounds bounds() { return plan == null ? null : plan.bounds(); }
    public Set<UUID> eligibleRewards() { return eligibleRewards; }
    public Set<UUID> claimedRewards() { return claimedRewards; }
    public boolean markClaimed(UUID playerId) { return eligibleRewards.contains(playerId) && claimedRewards.add(playerId); }
    public Set<UUID> returnedPlayers() { return returnedPlayers; }
    public boolean cleanupPrepared() { return cleanupPrepared; }
    public void cleanupPrepared(boolean value) { cleanupPrepared = value; }
    public int currentWave() { return currentWave; }
    public void currentWave(int value) { currentWave = value; }
    public long nextWaveAtMillis() { return nextWaveAtMillis; }
    public void nextWaveAtMillis(long value) { nextWaveAtMillis = value; }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("instance", instanceId); tag.putInt("slot", slot); tag.putString("template", templateId.toString());
        tag.putString("originDimension", originDimension.location().toString());
        tag.putLong("originPosition", originPosition.asLong());
        tag.putLong("center", center.asLong());
        if (bossId != null) tag.putUUID("boss", bossId);
        tag.putBoolean("exit", exitActive); tag.putBoolean("enraged", bossEnraged);
        if (plan != null) tag.put("plan", plan.save());
        tag.putIntArray("eligibleRewards", eligibleRewards.stream().flatMapToInt(id -> java.util.Arrays.stream(uuidInts(id))).toArray());
        tag.putIntArray("claimedRewards", claimedRewards.stream().flatMapToInt(id -> java.util.Arrays.stream(uuidInts(id))).toArray());
        tag.putIntArray("returnedPlayers", returnedPlayers.stream().flatMapToInt(id -> java.util.Arrays.stream(uuidInts(id))).toArray());
        tag.putBoolean("cleanupPrepared", cleanupPrepared);
        tag.putInt("currentWave", currentWave);
        tag.putLong("nextWaveAt", nextWaveAtMillis);
        return tag;
    }
    public static DungeonSession load(CompoundTag tag) {
        ResourceKey<Level> originDimension = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.parse(tag.contains("originDimension") ? tag.getString("originDimension") : "minecraft:overworld"));
        BlockPos originPosition = tag.contains("originPosition") ? BlockPos.of(tag.getLong("originPosition")) : BlockPos.ZERO;
        BlockPos center = tag.contains("center") ? BlockPos.of(tag.getLong("center")) : new BlockPos(tag.getInt("slot") * 512, 64, 0);
        DungeonSession session = new DungeonSession(tag.getUUID("instance"), tag.getInt("slot"),
            ResourceLocation.parse(tag.getString("template")), originDimension, originPosition, center);
        if (tag.hasUUID("boss")) session.bossId(tag.getUUID("boss"));
        session.exitActive(tag.getBoolean("exit")); session.bossEnraged(tag.getBoolean("enraged"));
        if (tag.contains("plan")) session.plan(GeneratedDungeonPlan.load(tag.getCompound("plan")));
        loadUuids(tag.getIntArray("eligibleRewards"), session.eligibleRewards);
        loadUuids(tag.getIntArray("claimedRewards"), session.claimedRewards);
        loadUuids(tag.getIntArray("returnedPlayers"), session.returnedPlayers);
        session.cleanupPrepared(tag.getBoolean("cleanupPrepared"));
        session.currentWave(tag.getInt("currentWave"));
        session.nextWaveAtMillis(tag.getLong("nextWaveAt"));
        return session;
    }

    private static int[] uuidInts(UUID id) {
        return new int[]{(int) (id.getMostSignificantBits() >> 32), (int) id.getMostSignificantBits(),
            (int) (id.getLeastSignificantBits() >> 32), (int) id.getLeastSignificantBits()};
    }
    private static void loadUuids(int[] values, Set<UUID> output) {
        for (int i = 0; i + 3 < values.length; i += 4) {
            long most = ((long) values[i] << 32) | (values[i + 1] & 0xffffffffL);
            long least = ((long) values[i + 2] << 32) | (values[i + 3] & 0xffffffffL);
            output.add(new UUID(most, least));
        }
    }
}

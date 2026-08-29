package dev.uapi.dungeons.data;

import dev.uapi.dungeons.config.DungeonServerConfig;
import dev.uapi.dungeons.generation.GeneratedDungeonPlan;
import dev.uapi.dungeons.generation.WorldBounds;
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
    private final int selectedLevel;
    private final long seed;
    private final String clearCondition;
    private final ResourceKey<Level> originDimension;
    private final BlockPos originPosition;
    private UUID bossId;
    private boolean exitActive;
    private boolean bossEnraged;
    private boolean bossDefeated;
    private final BlockPos center;
    private GeneratedDungeonPlan plan;
    private final Set<UUID> eligibleRewards = new LinkedHashSet<>();
    private final Set<UUID> claimedRewards = new LinkedHashSet<>();
    private final Set<UUID> returnedPlayers = new LinkedHashSet<>();
    private boolean cleanupPrepared;
    private int currentWave;
    private long nextWaveAtMillis;
    private long startedAtMillis;
    private long nextPressureAtMillis;
    private int pressureWaves;

    public DungeonSession(UUID instanceId, int slot, ResourceLocation templateId,
                          ResourceKey<Level> originDimension, BlockPos originPosition) {
        this(instanceId, slot, templateId, originDimension, originPosition, 0,
            instanceId.getMostSignificantBits() ^ instanceId.getLeastSignificantBits(),
            "boss", new BlockPos(slot * DungeonServerConfig.INSTANCE_SLOT_SPACING.get(), 64, 0));
    }
    public DungeonSession(UUID instanceId, int slot, ResourceLocation templateId,
                          ResourceKey<Level> originDimension, BlockPos originPosition,
                          int selectedLevel, long seed, String clearCondition) {
        this(instanceId, slot, templateId, originDimension, originPosition, selectedLevel, seed, clearCondition,
            new BlockPos(slot * DungeonServerConfig.INSTANCE_SLOT_SPACING.get(), 64, 0));
    }
    DungeonSession(UUID instanceId, int slot, ResourceLocation templateId,
                   ResourceKey<Level> originDimension, BlockPos originPosition,
                   int selectedLevel, long seed, String clearCondition, BlockPos center) {
        this.instanceId = instanceId; this.slot = slot; this.templateId = templateId;
        this.originDimension = originDimension; this.originPosition = originPosition.immutable(); this.center = center.immutable();
        this.selectedLevel = Math.max(0, selectedLevel); this.seed = seed;
        this.startedAtMillis = System.currentTimeMillis();
        this.clearCondition = switch (clearCondition) {
            case "all_encounters", "boss_and_encounters" -> clearCondition;
            default -> "boss";
        };
    }
    public UUID instanceId() { return instanceId; }
    public int slot() { return slot; }
    public ResourceLocation templateId() { return templateId; }
    public int selectedLevel() { return selectedLevel; }
    public long seed() { return seed; }
    public String clearCondition() { return clearCondition; }
    public ResourceKey<Level> originDimension() { return originDimension; }
    public BlockPos originPosition() { return originPosition; }
    public UUID bossId() { return bossId; }
    public void bossId(UUID bossId) { this.bossId = bossId; }
    public boolean exitActive() { return exitActive; }
    public void exitActive(boolean value) { exitActive = value; }
    public boolean bossEnraged() { return bossEnraged; }
    public void bossEnraged(boolean value) { bossEnraged = value; }
    public boolean bossDefeated() { return bossDefeated; }
    public void bossDefeated(boolean value) { bossDefeated = value; }
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
    public long startedAtMillis() { return startedAtMillis; }
    public void startedAtMillis(long value) { startedAtMillis = Math.max(0L, value); }
    public long nextPressureAtMillis() { return nextPressureAtMillis; }
    public void nextPressureAtMillis(long value) { nextPressureAtMillis = Math.max(0L, value); }
    public int pressureWaves() { return pressureWaves; }
    public void pressureWaves(int value) { pressureWaves = Math.max(0, value); }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("instance", instanceId); tag.putInt("slot", slot); tag.putString("template", templateId.toString());
        tag.putString("originDimension", originDimension.location().toString());
        tag.putLong("originPosition", originPosition.asLong());
        tag.putLong("center", center.asLong());
        tag.putInt("selectedLevel", selectedLevel);
        tag.putLong("seed", seed);
        tag.putString("clearCondition", clearCondition);
        if (bossId != null) tag.putUUID("boss", bossId);
        tag.putBoolean("exit", exitActive); tag.putBoolean("enraged", bossEnraged);
        tag.putBoolean("bossDefeated", bossDefeated);
        if (plan != null) tag.put("plan", plan.save());
        tag.putIntArray("eligibleRewards", eligibleRewards.stream().flatMapToInt(id -> java.util.Arrays.stream(uuidInts(id))).toArray());
        tag.putIntArray("claimedRewards", claimedRewards.stream().flatMapToInt(id -> java.util.Arrays.stream(uuidInts(id))).toArray());
        tag.putIntArray("returnedPlayers", returnedPlayers.stream().flatMapToInt(id -> java.util.Arrays.stream(uuidInts(id))).toArray());
        tag.putBoolean("cleanupPrepared", cleanupPrepared);
        tag.putInt("currentWave", currentWave);
        tag.putLong("nextWaveAt", nextWaveAtMillis);
        tag.putLong("startedAt", startedAtMillis);
        tag.putLong("nextPressureAt", nextPressureAtMillis);
        tag.putInt("pressureWaves", pressureWaves);
        return tag;
    }
    public static DungeonSession load(CompoundTag tag) {
        ResourceKey<Level> originDimension = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.parse(tag.getString("originDimension")));
        if (!tag.contains("originPosition") || !tag.contains("center")) {
            throw new IllegalArgumentException("current dungeon session is missing origin/center position");
        }
        BlockPos originPosition = BlockPos.of(tag.getLong("originPosition"));
        BlockPos center = BlockPos.of(tag.getLong("center"));
        DungeonSession session = new DungeonSession(tag.getUUID("instance"), tag.getInt("slot"),
            ResourceLocation.parse(tag.getString("template")), originDimension, originPosition,
            tag.getInt("selectedLevel"), tag.contains("seed") ? tag.getLong("seed")
                : tag.getUUID("instance").getMostSignificantBits() ^ tag.getUUID("instance").getLeastSignificantBits(),
            tag.contains("clearCondition") ? tag.getString("clearCondition") : "boss", center);
        if (tag.hasUUID("boss")) session.bossId(tag.getUUID("boss"));
        session.exitActive(tag.getBoolean("exit")); session.bossEnraged(tag.getBoolean("enraged"));
        session.bossDefeated(tag.getBoolean("bossDefeated"));
        if (tag.contains("plan")) session.plan(GeneratedDungeonPlan.load(tag.getCompound("plan")));
        loadUuids(tag.getIntArray("eligibleRewards"), session.eligibleRewards);
        loadUuids(tag.getIntArray("claimedRewards"), session.claimedRewards);
        loadUuids(tag.getIntArray("returnedPlayers"), session.returnedPlayers);
        session.cleanupPrepared(tag.getBoolean("cleanupPrepared"));
        session.currentWave(tag.getInt("currentWave"));
        session.nextWaveAtMillis(tag.getLong("nextWaveAt"));
        if (tag.contains("startedAt")) session.startedAtMillis(tag.getLong("startedAt"));
        session.nextPressureAtMillis(tag.getLong("nextPressureAt"));
        session.pressureWaves(tag.getInt("pressureWaves"));
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

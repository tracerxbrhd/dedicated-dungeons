package dev.uapi.dungeons.runtime;

import com.mojang.logging.LogUtils;
import dev.uapi.event.UApiEvents;
import dev.uapi.instance.InstanceManager;
import dev.uapi.instance.InstancePhase;
import dev.uapi.instance.InstanceView;
import dev.uapi.instance.InstanceType;
import dev.uapi.difficulty.DifficultyRank;
import dev.uapi.reward.RewardContext;
import dev.uapi.reward.RewardRegistry;
import dev.uapi.dungeons.DedicatedDungeonsMod;
import dev.uapi.dungeons.data.DungeonSavedData;
import dev.uapi.dungeons.data.DungeonSession;
import dev.uapi.dungeons.data.PortalRecord;
import dev.uapi.dungeons.content.DungeonContentRegistry;
import dev.uapi.dungeons.content.DungeonDefinition;
import dev.uapi.dungeons.content.DungeonContentTypes.MarkerType;
import dev.uapi.dungeons.content.DungeonContentTypes.Room;
import dev.uapi.dungeons.config.DungeonServerConfig;
import dev.uapi.dungeons.generation.DungeonGenerationService;
import dev.uapi.dungeons.generation.DungeonGraphPlanner;
import dev.uapi.dungeons.generation.GeneratedDungeonPlan;
import dev.uapi.dungeons.generation.WorldBounds;
import dev.uapi.dungeons.level.DungeonLevelService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class DungeonRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceKey<Level> ARENA_DIMENSION = ResourceKey.create(Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath(DedicatedDungeonsMod.MOD_ID, "dungeon_arena"));
    private static final Map<MinecraftServer, DungeonRuntime> INSTANCES = new HashMap<>();
    private final MinecraftServer server;
    private final DungeonSavedData data;
    private final Map<UUID, ServerBossEvent> bossBars = new HashMap<>();
    private final Map<UUID, Integer> missingBossTicks = new HashMap<>();
    private final Set<UUID> cleaning = new HashSet<>();

    private DungeonRuntime(MinecraftServer server) { this.server = server; this.data = DungeonSavedData.get(server); }
    public static synchronized DungeonRuntime get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, DungeonRuntime::new);
    }
    /** Drops transient bars/guards while leaving SavedData sessions available for restart recovery. */
    public static synchronized void stop(MinecraftServer server) {
        DungeonRuntime runtime = INSTANCES.remove(server);
        if (runtime == null) return;
        runtime.bossBars.values().forEach(ServerBossEvent::removeAllPlayers);
        runtime.bossBars.clear();
        runtime.missingBossTicks.clear();
        runtime.cleaning.clear();
    }
    public java.util.Collection<DungeonSession> sessions() { return java.util.List.copyOf(data.sessions().values()); }

    public boolean contains(BlockPos position) {
        for (DungeonSession session : data.sessions().values()) {
            if (contains(session, position)) return true;
        }
        return false;
    }

    public boolean insideAssignedSession(ServerPlayer player, BlockPos position) {
        InstanceView instance = InstanceManager.get(server).findByPlayer(player.getUUID()).orElse(null);
        if (instance == null) return false;
        DungeonSession session = data.sessions().get(instance.id());
        return session != null && contains(session, position);
    }

    public boolean start(PortalRecord portal, ServerPlayer player, ResourceLocation archetypeId) {
        UUID instanceId = portal.instanceId();
        ServerLevel level = server.getLevel(ARENA_DIMENSION);
        if (level == null) {
            player.sendSystemMessage(Component.translatable("message.dedicated_dungeons.missing_dimension"));
            InstanceManager.get(server).fail(instanceId, "missing_arena_dimension"); return false;
        }
        InstanceManager manager = InstanceManager.get(server);
        InstanceView instance = manager.find(instanceId).orElse(null);
        if (instance == null) return false;
        if (instance.type().isSurvival()) return startSurvival(portal, player, instance);
        if (!manager.transition(instanceId, InstancePhase.GENERATING, 30, "portal_entered")) return false;
        int slot;
        try {
            slot = allocateSlot();
        } catch (IllegalStateException exception) {
            manager.fail(instanceId, "slot_allocation_failed");
            player.sendSystemMessage(Component.literal(exception.getMessage()));
            return false;
        }
        DungeonDefinition dungeon = DungeonContentRegistry.dungeon(portal.archetypeId()).orElse(null);
        if (dungeon == null) {
            manager.fail(instanceId, "dungeon_definition_unavailable");
            player.sendSystemMessage(Component.translatable("message.dedicated_dungeons.invalid_archetype", portal.archetypeId()));
            return false;
        }
        long seed = instanceId.getMostSignificantBits() ^ instanceId.getLeastSignificantBits()
            ^ ((long) portal.selectedLevel() << 32);
        DungeonSession session = new DungeonSession(instanceId, slot, portal.archetypeId(),
            portal.dimension(), portal.position(), portal.selectedLevel(), seed, dungeon.rules().clearCondition());
        data.sessions().put(instanceId, session); data.changed();
        DungeonGraphPlanner.Result generated;
        try {
            generated = DungeonGraphPlanner.plan(dungeon, instance.difficulty(), session.center(),
                net.minecraft.util.RandomSource.create(seed),
                data.lastBossByRank().get(instance.difficulty().name()));
        } catch (RuntimeException exception) {
            LOGGER.error("Unexpected planner error for dungeon {} definition {}", instanceId, portal.archetypeId(), exception);
            manager.fail(instanceId, "planner_exception");
            return false;
        }
        if (!generated.successful()) {
            LOGGER.error("Dungeon {} could not build definition {}: {}", instanceId, portal.archetypeId(), generated.error());
            manager.fail(instanceId, "planning_failed:" + generated.error());
            return false;
        }
        session.plan(generated.plan());
        data.lastBossByRank().put(instance.difficulty().name(), generated.plan().bossId());
        data.changed();
        DungeonGenerationService.forceChunks(level, generated.plan().bounds(), true);
        boolean placed;
        try { placed = DungeonGenerationService.build(level, generated.plan(), session.selectedLevel(),
            instance.difficulty().ordinal(), instance.id(), session.templateId(), true,
            Math.max(1, instance.participants().size())); }
        catch (RuntimeException exception) {
            placed = false;
            LOGGER.error("Unexpected placement error for dungeon {} definition {}", instanceId, portal.archetypeId(), exception);
        }
        if (!placed) {
            DungeonGenerationService.cleanup(level, generated.plan());
            DungeonGenerationService.forceChunks(level, generated.plan().bounds(), false);
            manager.fail(instanceId, "placement_failed");
            return false;
        }
        BlockPos spawn = spawnFor(session, player, instance);
        player.teleportTo(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0);
        if (!manager.transition(instanceId, InstancePhase.ACTIVE, 0, "generation_complete")) return false;
        if ("all_encounters".equals(session.clearCondition())) {
            player.sendSystemMessage(Component.translatable("message.dedicated_dungeons.started"));
            return true;
        }
        LivingEntity boss = spawnBoss(level, session, session.plan(), instance);
        if (boss == null) { manager.fail(instanceId, "boss_spawn_failed"); return false; }
        session.bossId(boss.getUUID()); data.changed();
        bossBars.put(instance.id(), createBossBar(instance, boss));
        manager.transition(instanceId, InstancePhase.BOSS_ACTIVE, 0, "boss_active");
        player.sendSystemMessage(Component.translatable("message.dedicated_dungeons.started"));
        return true;
    }

    private boolean startSurvival(PortalRecord portal, ServerPlayer player, InstanceView instance) {
        UUID instanceId = instance.id();
        ServerLevel level = server.getLevel(ARENA_DIMENSION);
        if (level == null) {
            player.sendSystemMessage(Component.translatable("message.dedicated_dungeons.missing_dimension"));
            InstanceManager.get(server).fail(instanceId, "missing_arena_dimension");
            return false;
        }
        InstanceManager manager = InstanceManager.get(server);
        if (!manager.transition(instanceId, InstancePhase.GENERATING, 30, "survival_portal_entered")) return false;
        int slot;
        try { slot = allocateSlot(); }
        catch (IllegalStateException exception) {
            manager.fail(instanceId, "slot_allocation_failed");
            player.sendSystemMessage(Component.literal(exception.getMessage()));
            return false;
        }
        DungeonDefinition dungeon = DungeonContentRegistry.dungeon(portal.archetypeId()).orElse(null);
        if (dungeon == null) {
            manager.fail(instanceId, "dungeon_definition_unavailable");
            return false;
        }
        long seed = instanceId.getMostSignificantBits() ^ instanceId.getLeastSignificantBits()
            ^ ((long) portal.selectedLevel() << 32);
        DungeonSession session = new DungeonSession(instanceId, slot, portal.archetypeId(),
            portal.dimension(), portal.position(), portal.selectedLevel(), seed, dungeon.rules().clearCondition());
        data.sessions().put(instanceId, session);
        GeneratedDungeonPlan arena = createSurvivalPlan(session, dungeon);
        if (arena == null) {
            manager.fail(instanceId, "survival_definition_invalid");
            return false;
        }
        session.plan(arena);
        DungeonGenerationService.forceChunks(level, arena.bounds(), true);
        if (!DungeonGenerationService.build(level, arena, session.selectedLevel(), instance.difficulty().ordinal(),
            instance.id(), session.templateId(), false, Math.max(1, instance.participants().size()))) {
            DungeonGenerationService.cleanup(level, arena);
            DungeonGenerationService.forceChunks(level, arena.bounds(), false);
            manager.fail(instanceId, "survival_placement_failed");
            return false;
        }
        BlockPos spawn = spawnFor(session, player, instance);
        player.teleportTo(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0);
        session.nextWaveAtMillis(System.currentTimeMillis()
            + DungeonServerConfig.SURVIVAL_WAVE_DELAY_SECONDS.get() * 1000L);
        data.changed();
        if (!manager.transition(instanceId, InstancePhase.RUNNING, 0,
            "survival_generation_complete")) return false;
        player.sendSystemMessage(Component.translatable("message.dedicated_dungeons.survival_started",
            DungeonServerConfig.SURVIVAL_MAX_WAVES.get()));
        return true;
    }

    private GeneratedDungeonPlan createSurvivalPlan(DungeonSession session, DungeonDefinition dungeon) {
        ResourceLocation roomId = ResourceLocation.tryParse(DungeonServerConfig.SURVIVAL_ARENA_ROOM.get());
        Room room = roomId == null ? null : DungeonContentRegistry.room(roomId).orElse(null);
        if (room == null || !room.available() || !room.tags().contains("arena")) return null;
        var local = room.bounds();
        BlockPos origin = session.center();
        WorldBounds pieceBounds = new WorldBounds(origin.getX() + local.minX(), origin.getY() + local.minY(),
            origin.getZ() + local.minZ(), origin.getX() + local.maxX(), origin.getY() + local.maxY(),
            origin.getZ() + local.maxZ());
        var piece = new GeneratedDungeonPlan.Piece(GeneratedDungeonPlan.PieceType.ROOM, room.id(), origin,
            Rotation.NONE, pieceBounds, room.connectors().stream().map(point -> origin.offset(point.position())).toList());
        BlockPos playerSpawn = worldMarker(room, MarkerType.PLAYER_SPAWN, origin, origin.offset(0, 0, 6));
        BlockPos mobSpawn = worldMarker(room, MarkerType.SPAWNER, origin, origin);
        BlockPos exit = worldMarker(room, MarkerType.EXIT_PORTAL, origin, origin.offset(0, 0, 8));
        java.util.List<GeneratedDungeonPlan.PlacedMarker> markers = room.markerDefinitions().entrySet().stream()
            .flatMap(entry -> entry.getValue().stream().map(marker -> new GeneratedDungeonPlan.PlacedMarker(
                entry.getKey(), origin.offset(marker.position()), marker.facing(), marker.profile(), marker.group(),
                marker.order(), room.id(), marker.loot(), marker.spawn())))
            .toList();
        return new GeneratedDungeonPlan(room.theme(),
            ResourceLocation.fromNamespaceAndPath(DedicatedDungeonsMod.MOD_ID, "survival_arena"),
            ResourceLocation.fromNamespaceAndPath(DedicatedDungeonsMod.MOD_ID, "none"),
            ResourceLocation.withDefaultNamespace("zombie"), 20.0, 3.0, dungeon.pools().rewardPool(),
            java.util.List.of(piece), pieceBounds, playerSpawn, mobSpawn, exit, markers);
    }

    private static BlockPos worldMarker(Room room, MarkerType type, BlockPos origin, BlockPos fallback) {
        return room.markers(type).stream().findFirst().map(origin::offset).orElse(fallback).immutable();
    }

    public void tick() {
        ServerLevel level = server.getLevel(ARENA_DIMENSION); if (level == null) return;
        InstanceManager manager = InstanceManager.get(server);
        for (DungeonSession session : new ArrayList<>(data.sessions().values())) {
            InstanceView instance = manager.find(session.instanceId()).orElse(null);
            if (instance == null) {
                if (!session.cleanupPrepared()) cleanupBlocks(level, session);
                data.sessions().remove(session.instanceId()); data.changed();
                continue;
            }
            if (instance.phase().terminal()) {
                finish(instance);
                continue;
            }
            enforceBounds(level, session, instance);
            enforceMobBounds(level, session, instance);
            if (instance.phase() == InstancePhase.REWARD || instance.phase() == InstancePhase.REWARD_PHASE)
                grantAvailableRewards(session, instance);
            if (instance.type().isSurvival()) {
                tickSurvival(level, session, instance, manager);
            } else {
                Entity entity = session.bossId() == null ? null : level.getEntity(session.bossId());
                if (entity instanceof LivingEntity boss && boss.isAlive()) {
                missingBossTicks.remove(instance.id());
                ServerBossEvent bar = bossBars.computeIfAbsent(instance.id(), id -> createBossBar(instance, boss));
                bar.setProgress(Math.max(0, boss.getHealth() / boss.getMaxHealth()));
                syncBossBarPlayers(bar, level, session, instance);
                if (!session.bossEnraged() && boss.getHealth() <= boss.getMaxHealth() * 0.5f) {
                    session.bossEnraged(true); data.changed();
                    boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 600, 1));
                    boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 600, 0));
                    instance.participants().stream().map(server.getPlayerList()::getPlayer).filter(java.util.Objects::nonNull)
                        .forEach(player -> player.sendSystemMessage(Component.translatable("message.dedicated_dungeons.boss_enraged")));
                }
                } else if (instance.phase() == InstancePhase.BOSS_ACTIVE && !session.bossDefeated()
                    && session.plan() != null) {
                    int missing = missingBossTicks.merge(instance.id(), 1, Integer::sum);
                    if (missing >= 5) {
                        LivingEntity restored = spawnBoss(level, session, session.plan(), instance);
                        if (restored != null) {
                            session.bossId(restored.getUUID());
                            bossBars.put(instance.id(), createBossBar(instance, restored));
                            missingBossTicks.remove(instance.id());
                            data.changed();
                            LOGGER.warn("Restored missing boss {} for active dungeon {} after restart/removal",
                                session.plan().bossEntityType(), instance.id());
                        }
                    }
                }
                boolean encountersAlive = encountersAlive(level, instance.id());
                if (instance.phase() == InstancePhase.ACTIVE
                    && "all_encounters".equals(session.clearCondition()) && !encountersAlive) {
                    completeStandard(session, instance, "encounters_cleared");
                } else if (instance.phase() == InstancePhase.BOSS_ACTIVE
                    && "boss_and_encounters".equals(session.clearCondition())
                    && session.bossDefeated() && !encountersAlive) {
                    completeStandard(session, instance, "boss_and_encounters_cleared");
                }
            }
            if (session.exitActive()) {
                BlockPos exit = session.exitPosition();
                ensureExitPortalVisual(level, session, instance);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, exit.getX() + 0.5, exit.getY() + 1.5,
                    exit.getZ() + 0.5, 34, 0.9, 1.4, 0.35, 0.04);
                level.sendParticles(ParticleTypes.END_ROD, exit.getX() + 0.5, exit.getY() + 1.5,
                    exit.getZ() + 0.5, 5, 0.65, 1.0, 0.25, 0.01);
                for (UUID playerId : instance.participants()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null && !session.returnedPlayers().contains(playerId)
                        && player.level() == level && player.distanceToSqr(
                        exit.getX() + 0.5, exit.getY() + 0.5, exit.getZ() + 0.5) < 4.0) {
                        if (manager.returnPlayer(player, instance)) {
                            session.returnedPlayers().add(playerId);
                            ServerBossEvent bar = bossBars.get(instance.id());
                            if (bar != null) bar.removePlayer(player);
                            data.changed();
                        }
                    }
                }
                if (!instance.participants().isEmpty()
                    && session.returnedPlayers().containsAll(instance.participants()))
                    manager.complete(instance.id(), "all_players_exited");
            }
        }
    }

    private void tickSurvival(ServerLevel level, DungeonSession session, InstanceView instance,
                              InstanceManager manager) {
        if (instance.phase() == InstancePhase.REWARD || instance.phase() == InstancePhase.REWARD_PHASE) return;
        String tag = survivalTag(instance.id());
        boolean mobsAlive = false;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof LivingEntity living && living.isAlive() && entity.getTags().contains(tag)) {
                mobsAlive = true;
                break;
            }
        }
        if (mobsAlive) return;
        long now = System.currentTimeMillis();
        if (session.currentWave() >= DungeonServerConfig.SURVIVAL_MAX_WAVES.get()
            && session.nextWaveAtMillis() == 0L) {
            if (manager.transition(instance.id(), InstancePhase.REWARD_PHASE,
                instance.difficulty().rewardSeconds(), "survival_complete")) {
                session.eligibleRewards().addAll(instance.participants());
                grantAvailableRewards(session, instance);
                session.exitActive(true);
                data.changed();
                instance.participants().stream().map(server.getPlayerList()::getPlayer)
                    .filter(java.util.Objects::nonNull).forEach(player -> player.sendSystemMessage(
                        Component.translatable("message.dedicated_dungeons.survival_complete")));
            }
            return;
        }
        if (session.nextWaveAtMillis() == 0L) {
            session.nextWaveAtMillis(now + DungeonServerConfig.SURVIVAL_WAVE_DELAY_SECONDS.get() * 1000L);
            data.changed();
            return;
        }
        if (now < session.nextWaveAtMillis()) return;
        int wave = session.currentWave() + 1;
        session.currentWave(wave);
        session.nextWaveAtMillis(0L);
        if (!spawnSurvivalWave(level, session, instance, wave)) {
            manager.fail(instance.id(), "survival_encounter_pool_unavailable");
            return;
        }
        data.changed();
        instance.participants().stream().map(server.getPlayerList()::getPlayer)
            .filter(java.util.Objects::nonNull).forEach(player -> player.sendSystemMessage(
                Component.translatable("message.dedicated_dungeons.survival_wave", wave,
                    DungeonServerConfig.SURVIVAL_MAX_WAVES.get())));
    }

    private boolean spawnSurvivalWave(ServerLevel level, DungeonSession session, InstanceView instance, int wave) {
        int count = Math.min(128, DungeonServerConfig.SURVIVAL_BASE_MOBS.get()
            + Math.max(0, wave - 1) * DungeonServerConfig.SURVIVAL_MOBS_PER_WAVE.get());
        BlockPos center = session.plan() == null ? session.center() : session.plan().bossSpawn();
        ResourceLocation encounterId = session.plan() == null ? null : session.plan().markers().stream()
            .filter(marker -> marker.type() == MarkerType.SPAWNER).map(GeneratedDungeonPlan.PlacedMarker::profile)
            .filter(java.util.Objects::nonNull).findFirst().orElse(null);
        var encounter = encounterId == null ? null : DungeonContentRegistry.resolveEncounterPool(encounterId).orElse(null);
        if (encounter == null) return false;
        for (int index = 0; index < count; index++) {
            var entry = DungeonContentRegistry.chooseMobEntry(encounter.mobPool(), session.selectedLevel(),
                instance.difficulty().ordinal(), level.random, value -> value.allowedInArena()).orElse(null);
            if (entry == null) return false;
            ResourceLocation typeId = entry.entityType();
            Entity created = BuiltInRegistries.ENTITY_TYPE.get(typeId).create(level);
            if (!(created instanceof Mob mob)) return false;
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            double distance = 2.0 + level.random.nextDouble() * 6.0;
            mob.setPos(center.getX() + 0.5 + Math.cos(angle) * distance, center.getY(),
                center.getZ() + 0.5 + Math.sin(angle) * distance);
            mob.setPersistenceRequired();
            mob.addTag(survivalTag(instance.id()));
            DungeonMobConfigurator.prepare(level, mob, instance.difficulty().ordinal(), entry.canEquip(),
                entry.equipmentChance(), true);
            double waveScale = 1.0 + Math.max(0, wave - 1) * 0.12;
            var health = mob.getAttribute(Attributes.MAX_HEALTH);
            if (health != null) health.setBaseValue(health.getBaseValue() * instance.difficulty().healthMultiplier()
                * waveScale);
            var damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damage != null) damage.setBaseValue(damage.getBaseValue() * instance.difficulty().damageMultiplier()
                * waveScale);
            mob.setHealth(mob.getMaxHealth());
            level.addFreshEntity(mob);
            NeoForge.EVENT_BUS.post(new UApiEvents.MobLifecycle(instance, mob,
                UApiEvents.MobLifecycle.Stage.MOB_SPAWNED));
        }
        return true;
    }

    public boolean bossKilled(UUID bossId) {
        DungeonSession session = data.sessions().values().stream().filter(value -> bossId.equals(value.bossId())).findFirst().orElse(null);
        if (session == null) return false;
        InstanceManager manager = InstanceManager.get(server);
        InstanceView instance = manager.find(session.instanceId()).orElse(null); if (instance == null) return false;
        ServerLevel level = server.getLevel(ARENA_DIMENSION);
        if (level != null) {
            Entity boss = level.getEntity(bossId);
            if (boss != null) NeoForge.EVENT_BUS.post(new UApiEvents.MobLifecycle(instance, boss,
                UApiEvents.MobLifecycle.Stage.BOSS_KILLED));
        }
        ServerBossEvent bar = bossBars.remove(instance.id()); if (bar != null) bar.removeAllPlayers();
        missingBossTicks.remove(instance.id());
        session.bossId(null);
        session.bossDefeated(true);
        data.changed();
        if ("boss".equals(session.clearCondition())) return completeStandard(session, instance, "boss_killed");
        if ("boss_and_encounters".equals(session.clearCondition())) {
            int remaining = level == null ? 0 : encounterCount(level, instance.id());
            if (remaining == 0)
                return completeStandard(session, instance, "boss_and_encounters_cleared");
            instance.participants().stream().map(server.getPlayerList()::getPlayer)
                .filter(java.util.Objects::nonNull)
                .forEach(player -> player.sendSystemMessage(Component.translatable(
                    "message.dedicated_dungeons.boss_defeated_remaining", remaining)));
        }
        return true;
    }

    private boolean completeStandard(DungeonSession session, InstanceView instance, String reason) {
        if (session.exitActive()) return true;
        InstanceManager manager = InstanceManager.get(server);
        if (!manager.transition(instance.id(), InstancePhase.REWARD_PHASE,
            instance.difficulty().rewardSeconds(), reason)) return false;
        session.eligibleRewards().addAll(instance.participants());
        grantAvailableRewards(session, instance);
        session.exitActive(true);
        ServerLevel level = server.getLevel(ARENA_DIMENSION);
        if (level != null && session.plan() != null) {
            for (GeneratedDungeonPlan.PlacedMarker marker : session.plan().markers()) {
                if (marker.type() == MarkerType.SPAWNER
                    && level.getBlockState(marker.position()).is(Blocks.SPAWNER)) {
                    level.setBlock(marker.position(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        data.changed();
        return true;
    }

    private static boolean encountersAlive(ServerLevel level, UUID instanceId) {
        return encounterCount(level, instanceId) > 0;
    }

    private static int encounterCount(ServerLevel level, UUID instanceId) {
        String tag = DungeonGenerationService.encounterTag(instanceId);
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof LivingEntity living && living.isAlive() && entity.getTags().contains(tag)) count++;
        }
        return count;
    }

    public void finish(InstanceView instance) {
        DungeonSession session = data.sessions().get(instance.id());
        if (session == null || !cleaning.add(instance.id())) return;
        InstanceManager manager = InstanceManager.get(server);
        try {
            // Returning players is deliberately first: cleanup must never be able to strand them.
            returnAvailablePlayers(instance, session, manager);
            if (!session.cleanupPrepared()) {
                manager.transition(instance.id(), InstancePhase.CLEANUP_PENDING, 0, "runtime_cleanup");
                ServerLevel level = server.getLevel(ARENA_DIMENSION);
                try {
                    if (level != null) cleanupBlocks(level, session);
                } catch (RuntimeException exception) {
                    LOGGER.error("Could not clean dungeon {} after returning its players; cleanup will retry",
                        instance.id(), exception);
                    return;
                }
                ServerBossEvent bar = bossBars.remove(instance.id()); if (bar != null) bar.removeAllPlayers();
                session.cleanupPrepared(true); data.changed();
            }
            if (session.returnedPlayers().containsAll(instance.participants())) {
                data.sessions().remove(instance.id()); data.changed();
                manager.remove(instance.id(), "dungeon_cleaned");
            }
        } finally {
            cleaning.remove(instance.id());
        }
    }

    public boolean hasSession(UUID instanceId) {
        return data.sessions().containsKey(instanceId);
    }

    public DungeonSession session(UUID instanceId) {
        return data.sessions().get(instanceId);
    }

    public boolean startDirect(ServerPlayer player, DifficultyRank rank, ResourceLocation archetypeId) {
        return startDirect(player, rank, archetypeId, DungeonLevelService.playerLevel(player));
    }

    public boolean startDirect(ServerPlayer player, DifficultyRank rank, ResourceLocation archetypeId, int selectedLevel) {
        var definition = DungeonContentRegistry.dungeon(archetypeId).orElse(null);
        if (definition == null) {
            player.sendSystemMessage(Component.translatable("message.dedicated_dungeons.invalid_archetype", archetypeId));
            return false;
        }
        InstanceManager manager = InstanceManager.get(server);
        try {
            var instance = manager.create(archetypeId, InstanceType.BOSS_DUNGEON, rank, player, 30);
            PortalRecord synthetic = new PortalRecord(UUID.randomUUID(), player.getUUID(), instance.id(),
                player.level().dimension(), player.blockPosition(), rank, archetypeId,
                dev.uapi.dungeons.portal.PortalOrigin.COMMAND, System.currentTimeMillis() + 30_000L, false,
                Optional.empty(), selectedLevel);
            if (!manager.addParticipant(instance.id(), player)) return false;
            return start(synthetic, player, archetypeId);
        } catch (IllegalStateException exception) {
            player.sendSystemMessage(Component.literal(exception.getMessage()));
            return false;
        }
    }

    public boolean startSurvivalDirect(ServerPlayer player, DifficultyRank rank) {
        InstanceManager manager = InstanceManager.get(server);
        try {
            int selectedLevel = DungeonLevelService.playerLevel(player);
            ResourceLocation definition = DungeonContentRegistry.chooseDungeon(selectedLevel,
                player.level().dimension().location(), player.getRandom())
                .map(dev.uapi.dungeons.content.DungeonSelector.Candidate::id).orElse(null);
            if (definition == null) return false;
            var instance = manager.create(definition, InstanceType.SURVIVAL_ARENA, rank, player, 30);
            PortalRecord synthetic = new PortalRecord(UUID.randomUUID(), player.getUUID(), instance.id(),
                player.level().dimension(), player.blockPosition(), rank, definition,
                dev.uapi.dungeons.portal.PortalOrigin.COMMAND, System.currentTimeMillis() + 30_000L, false,
                Optional.empty(), selectedLevel);
            if (!manager.addParticipant(instance.id(), player)) return false;
            return start(synthetic, player, definition);
        } catch (IllegalStateException exception) {
            player.sendSystemMessage(Component.literal(exception.getMessage()));
            return false;
        }
    }

    public boolean teleportToSession(ServerPlayer player, UUID instanceId) {
        DungeonSession session = data.sessions().get(instanceId);
        if (session == null) return false;
        ServerLevel level = server.getLevel(ARENA_DIMENSION);
        if (level == null) return false;
        if (session.plan() == null) return false;
        InstanceView instance = InstanceManager.get(server).find(instanceId).orElse(null);
        if (instance == null) return false;
        BlockPos position = spawnFor(session, player, instance);
        player.teleportTo(level, position.getX() + 0.5, position.getY(), position.getZ() + 0.5,
            player.getYRot(), player.getXRot());
        return true;
    }

    public void requestFinish(InstanceView instance) {
        if (!hasSession(instance.id())) return;
        finish(instance);
    }

    public int cleanupAll() {
        InstanceManager manager = InstanceManager.get(server);
        int count = data.sessions().size();
        for (DungeonSession session : new ArrayList<>(data.sessions().values()))
            manager.find(session.instanceId()).ifPresentOrElse(this::finish, () -> {
                ServerLevel level = server.getLevel(ARENA_DIMENSION); if (level != null) cleanupBlocks(level, session);
                data.sessions().remove(session.instanceId()); data.changed();
            });
        return count;
    }

    public boolean cleanup(UUID instanceId) {
        DungeonSession session = data.sessions().get(instanceId);
        if (session == null) return false;
        InstanceManager manager = InstanceManager.get(server);
        InstanceView instance = manager.find(instanceId).orElse(null);
        if (instance != null) {
            manager.fail(instanceId, "admin_cleanup");
            finish(instance);
        } else {
            ServerLevel level = server.getLevel(ARENA_DIMENSION);
            if (level != null) cleanupBlocks(level, session);
            data.sessions().remove(instanceId);
            data.changed();
        }
        return true;
    }

    private int allocateSlot() {
        Set<Integer> used = data.sessions().values().stream().map(DungeonSession::slot).collect(java.util.stream.Collectors.toSet());
        int maxSlot = Integer.MAX_VALUE / dev.uapi.dungeons.config.DungeonServerConfig.INSTANCE_SLOT_SPACING.get() - 1;
        for (int i = 0; i <= maxSlot; i++) if (!used.contains(i)) return i;
        throw new IllegalStateException("No coordinate-safe dungeon arena slots remain");
    }

    private LivingEntity spawnBoss(ServerLevel level, DungeonSession session, GeneratedDungeonPlan plan, InstanceView instance) {
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.getOptional(plan.bossEntityType()).orElse(null);
        if (entityType == null) {
            LOGGER.error("Boss definition {} selected missing entity type {} for dungeon {}",
                plan.bossId(), plan.bossEntityType(), instance.id());
            return null;
        }
        Entity entity = entityType.create(level);
        if (!(entity instanceof LivingEntity boss)) {
            LOGGER.error("Boss definition {} entity type {} did not create a living entity for dungeon {}",
                plan.bossId(), plan.bossEntityType(), instance.id());
            return null;
        }
        BlockPos position = plan.bossSpawn();
        boss.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        if (boss instanceof Mob mob)
            DungeonMobConfigurator.prepareBoss(level, mob, instance.difficulty().ordinal());
        configureBoss(boss, session, plan, instance);
        if (!level.addFreshEntity(boss)) {
            LOGGER.error("Could not add boss definition {} entity {} to dungeon {} at {}",
                plan.bossId(), plan.bossEntityType(), instance.id(), position);
            return null;
        }
        LOGGER.info("Spawned dungeon boss definition {} as entity {} for dungeon {}",
            plan.bossId(), plan.bossEntityType(), instance.id());
        NeoForge.EVENT_BUS.post(new UApiEvents.MobLifecycle(instance, boss,
            UApiEvents.MobLifecycle.Stage.BOSS_SPAWNED));
        return boss;
    }

    private static void configureBoss(LivingEntity boss, DungeonSession session, GeneratedDungeonPlan plan,
                                      InstanceView instance) {
        boss.setCustomName(DungeonBossNames.generate(session.seed(), plan.bossId()));
        boss.setCustomNameVisible(true);
        boss.addTag(instanceTag(instance.id()));
        if (boss instanceof Mob mob) mob.setPersistenceRequired();
        var health = boss.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(plan.bossHealth() * instance.difficulty().healthMultiplier());
        var damage = boss.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) damage.setBaseValue(plan.bossDamage() * instance.difficulty().damageMultiplier());
        var armor = boss.getAttribute(Attributes.ARMOR);
        if (armor != null) armor.setBaseValue(Math.min(30, 4 * instance.difficulty().armorMultiplier()));
        boss.setHealth(boss.getMaxHealth());
    }

    private ServerBossEvent createBossBar(InstanceView instance, LivingEntity boss) {
        return new ServerBossEvent(boss.getDisplayName(), BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.PROGRESS);
    }

    private void syncBossBarPlayers(ServerBossEvent bar, ServerLevel level, DungeonSession session,
                                    InstanceView instance) {
        // A ServerBossEvent keeps its audience until it is explicitly removed. Rebuild it every tick so dead,
        // respawned, disconnected and returned players cannot keep the dungeon boss bar in another dimension.
        bar.removeAllPlayers();
        for (UUID playerId : instance.participants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null && player.isAlive() && player.level() == level
                && contains(session, player.blockPosition())) bar.addPlayer(player);
        }
    }

    public void hideBossBars(ServerPlayer player) {
        bossBars.values().forEach(bar -> bar.removePlayer(player));
    }

    private void ensureExitPortalVisual(ServerLevel level, DungeonSession session, InstanceView instance) {
        BlockPos exit = session.exitPosition();
        Direction facing = session.plan() == null ? Direction.NORTH : session.plan().markers().stream()
            .filter(marker -> marker.type() == MarkerType.EXIT_PORTAL && marker.position().equals(exit))
            .map(GeneratedDungeonPlan.PlacedMarker::facing).findFirst().orElse(Direction.NORTH);
        String tag = exitVisualTag(instance.id());
        boolean exists = !level.getEntitiesOfClass(Display.BlockDisplay.class,
            new net.minecraft.world.phys.AABB(exit).inflate(4), entity -> entity.getTags().contains(tag)).isEmpty();
        if (exists) return;
        for (int horizontal = -1; horizontal <= 1; horizontal++) for (int y = 0; y <= 3; y++) {
            boolean frame = horizontal == -1 || horizontal == 1 || y == 0 || y == 3;
            BlockPos visualPos = facing.getAxis() == Direction.Axis.Z
                ? exit.offset(horizontal, y, 0) : exit.offset(0, y, horizontal);
            Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
            CompoundTag nbt = new CompoundTag();
            nbt.put("block_state", NbtUtils.writeBlockState((frame
                ? exitFrameBlock(instance.difficulty()) : exitPortalBlock(instance.difficulty())).defaultBlockState()));
            display.load(nbt);
            display.setPos(visualPos.getX(), visualPos.getY(), visualPos.getZ());
            display.addTag(tag);
            level.addFreshEntity(display);
        }
        level.playSound(null, exit, SoundEvents.END_PORTAL_SPAWN, SoundSource.AMBIENT, 1.1f, 1.2f);
    }

    private static Block exitFrameBlock(DifficultyRank rank) {
        return switch (rank) {
            case E, D -> Blocks.QUARTZ_BRICKS;
            case C, B -> Blocks.PRISMARINE_BRICKS;
            case A -> Blocks.SEA_LANTERN;
            case S -> Blocks.PURPUR_PILLAR;
            case ANOMALY -> Blocks.END_STONE_BRICKS;
        };
    }

    private static Block exitPortalBlock(DifficultyRank rank) {
        return switch (rank) {
            case E, D -> Blocks.LIGHT_BLUE_STAINED_GLASS;
            case C, B -> Blocks.CYAN_STAINED_GLASS;
            case A -> Blocks.YELLOW_STAINED_GLASS;
            case S -> Blocks.MAGENTA_STAINED_GLASS;
            case ANOMALY -> Blocks.TINTED_GLASS;
        };
    }

    private void cleanupBlocks(ServerLevel level, DungeonSession session) {
        List<Entity> discard = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity == null) continue;
            if (entity.getTags().contains(survivalTag(session.instanceId()))
                || entity.getTags().contains(DungeonGenerationService.encounterTag(session.instanceId())))
                discard.add(entity);
        }
        if (DungeonServerConfig.CLEANUP_INSTANCE_MOBS.get()) discard.forEach(Entity::discard);
        if (session.plan() != null) {
            DungeonGenerationService.cleanup(level, session.plan());
            DungeonGenerationService.forceChunks(level, session.plan().bounds(), false);
        }
    }

    private void grantAvailableRewards(DungeonSession session, InstanceView instance) {
        if (session.plan() == null) return;
        ResourceLocation pool = session.plan().rewardPool();
        for (UUID playerId : new ArrayList<>(session.eligibleRewards())) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null && session.markClaimed(playerId)) {
                RewardRegistry.grant(pool, new RewardContext(instance, player));
                player.sendSystemMessage(Component.translatable("message.dedicated_dungeons.victory",
                    instance.difficulty().rewardSeconds()));
                data.changed();
            }
        }
    }

    private void enforceBounds(ServerLevel level, DungeonSession session, InstanceView instance) {
        if (!dev.uapi.dungeons.config.DungeonServerConfig.ENFORCE_INSTANCE_BOUNDS.get()) return;
        BlockPos safe = session.plan() == null ? session.center() : session.plan().playerSpawn();
        for (UUID playerId : instance.participants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null && player.level() == level && !contains(session, player.blockPosition())) {
                player.teleportTo(level, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, player.getYRot(), player.getXRot());
                player.sendSystemMessage(Component.translatable("message.dedicated_dungeons.out_of_bounds"));
            }
        }
    }

    private void enforceMobBounds(ServerLevel level, DungeonSession session, InstanceView instance) {
        if (server.getTickCount() % 20 != 0 || session.bounds() == null) return;
        String encounter = DungeonGenerationService.encounterTag(instance.id());
        String survival = survivalTag(instance.id());
        String boss = instanceTag(instance.id());
        BlockPos safe = session.plan() == null ? session.center() : session.plan().bossSpawn();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Mob mob) || contains(session, entity.blockPosition())) continue;
            if (!entity.getTags().contains(encounter) && !entity.getTags().contains(survival)
                && !entity.getTags().contains(boss)) continue;
            mob.getNavigation().stop();
            mob.setPos(safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5);
        }
    }

    public void recoverPendingReturn(ServerPlayer player) {
        InstanceManager manager = InstanceManager.get(server);
        for (DungeonSession session : new ArrayList<>(data.sessions().values())) {
            InstanceView instance = manager.find(session.instanceId()).orElse(null);
            if (instance == null || !instance.phase().terminal() || !instance.participants().contains(player.getUUID())
                || session.returnedPlayers().contains(player.getUUID())) continue;
            if (manager.returnPlayer(player, instance)) {
                session.returnedPlayers().add(player.getUUID()); data.changed();
                finish(instance);
            }
        }
    }

    private void returnAvailablePlayers(InstanceView instance, DungeonSession session, InstanceManager manager) {
        for (UUID playerId : instance.participants()) {
            if (session.returnedPlayers().contains(playerId)) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null && player.isAlive() && manager.returnPlayer(player, instance)) {
                session.returnedPlayers().add(playerId); data.changed();
            }
        }
    }

    private static String instanceTag(UUID id) { return "dd_instance_" + id; }
    private static String survivalTag(UUID id) { return "dd_survival_" + id; }
    private static String exitVisualTag(UUID id) { return "dd_exit_" + id; }

    private boolean contains(DungeonSession session, BlockPos position) {
        return session.bounds() != null && session.bounds().contains(position);
    }

    private static BlockPos spawnFor(DungeonSession session, ServerPlayer player, InstanceView instance) {
        if (session.plan() == null) return session.center();
        List<BlockPos> spawns = session.plan().markers().stream()
            .filter(marker -> marker.type() == MarkerType.PLAYER_SPAWN)
            .sorted(java.util.Comparator.comparing(GeneratedDungeonPlan.PlacedMarker::group)
                .thenComparingInt(GeneratedDungeonPlan.PlacedMarker::order))
            .map(GeneratedDungeonPlan.PlacedMarker::position).toList();
        if (spawns.isEmpty()) return session.plan().playerSpawn();
        List<UUID> participants = new ArrayList<>(instance.participants());
        participants.sort(java.util.Comparator.comparing(UUID::toString));
        int index = Math.max(0, participants.indexOf(player.getUUID()));
        return spawns.get(index % spawns.size());
    }
}

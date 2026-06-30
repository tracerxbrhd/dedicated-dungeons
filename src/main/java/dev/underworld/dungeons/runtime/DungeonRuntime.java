package dev.underworld.dungeons.runtime;

import com.mojang.logging.LogUtils;
import dev.underworld.api.event.UnderworldEvents;
import dev.underworld.api.instance.InstanceManager;
import dev.underworld.api.instance.InstancePhase;
import dev.underworld.api.instance.InstanceView;
import dev.underworld.api.instance.InstanceType;
import dev.underworld.api.difficulty.DifficultyRank;
import dev.underworld.api.reward.RewardContext;
import dev.underworld.api.reward.RewardRegistry;
import dev.underworld.dungeons.DedicatedDungeonsMod;
import dev.underworld.dungeons.data.DungeonSavedData;
import dev.underworld.dungeons.data.DungeonSession;
import dev.underworld.dungeons.data.PortalRecord;
import dev.underworld.dungeons.content.DungeonContentRegistry;
import dev.underworld.dungeons.content.DungeonContentTypes.Boss;
import dev.underworld.dungeons.content.DungeonContentTypes.MarkerType;
import dev.underworld.dungeons.content.DungeonContentTypes.Room;
import dev.underworld.dungeons.config.DungeonServerConfig;
import dev.underworld.dungeons.generation.DungeonGenerationService;
import dev.underworld.dungeons.generation.DungeonGraphPlanner;
import dev.underworld.dungeons.generation.GeneratedDungeonPlan;
import dev.underworld.dungeons.generation.WorldBounds;
import dev.underworld.dungeons.template.DungeonTemplate;
import dev.underworld.dungeons.template.DungeonTemplates;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.ChunkPos;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DungeonRuntime {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceKey<Level> ARENA_DIMENSION = ResourceKey.create(Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath(DedicatedDungeonsMod.MOD_ID, "underworld_arena"));
    private static final ResourceLocation LEGACY_TEMPLATE =
        ResourceLocation.fromNamespaceAndPath(DedicatedDungeonsMod.MOD_ID, "basic");
    private static final Map<MinecraftServer, DungeonRuntime> INSTANCES = new HashMap<>();
    private final MinecraftServer server;
    private final DungeonSavedData data;
    private final Map<UUID, ServerBossEvent> bossBars = new HashMap<>();
    private final Set<UUID> cleaning = new HashSet<>();

    private DungeonRuntime(MinecraftServer server) { this.server = server; this.data = DungeonSavedData.get(server); }
    public static DungeonRuntime get(MinecraftServer server) { return INSTANCES.computeIfAbsent(server, DungeonRuntime::new); }
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
        DungeonSession session = new DungeonSession(instanceId, slot, LEGACY_TEMPLATE,
            portal.dimension(), portal.position());
        data.sessions().put(instanceId, session); data.changed();
        DungeonTemplate template = DungeonTemplates.get(LEGACY_TEMPLATE);
        DungeonGraphPlanner.Result generated = null;
        try {
            generated = DungeonGraphPlanner.plan(archetypeId, instance.difficulty(), session.center(), level.random);
        } catch (RuntimeException exception) {
            LOGGER.error("Unexpected planner error for dungeon {}; using legacy template", instanceId, exception);
        }
        if (generated != null && generated.successful()) {
            session.plan(generated.plan()); data.changed();
            DungeonGenerationService.forceChunks(level, generated.plan().bounds(), true);
            boolean placed;
            try { placed = DungeonGenerationService.build(level, generated.plan()); }
            catch (RuntimeException exception) {
                placed = false;
                LOGGER.error("Unexpected placement error for dungeon {}; using legacy template", instanceId, exception);
            }
            if (!placed) {
                DungeonGenerationService.cleanup(level, generated.plan());
                DungeonGenerationService.forceChunks(level, generated.plan().bounds(), false);
                session.plan(null); data.changed();
                LOGGER.error("Dungeon {} could not place data-driven plan; using legacy template {}", instanceId, LEGACY_TEMPLATE);
            }
        } else if (generated != null) LOGGER.warn("Dungeon {} could not build data-driven plan ({}); using legacy template {}",
            instanceId, generated.error(), LEGACY_TEMPLATE);
        if (session.plan() == null) {
            forceChunks(level, session, template, true);
            buildArena(level, session, template);
        }
        BlockPos spawn = session.plan() == null ? session.center() : session.plan().playerSpawn();
        player.teleportTo(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0);
        if (!manager.transition(instanceId, InstancePhase.ACTIVE, instance.difficulty().runSeconds(), "generation_complete")) return false;
        LivingEntity boss = session.plan() == null ? spawnBoss(level, session, template, instance)
            : spawnBoss(level, session, session.plan(), instance);
        if (boss == null) { manager.fail(instanceId, "boss_spawn_failed"); return false; }
        session.bossId(boss.getUUID()); data.changed();
        bossBars.put(instance.id(), createBossBar(instance, boss));
        manager.transition(instanceId, InstancePhase.BOSS_ACTIVE, instance.difficulty().runSeconds(), "boss_active");
        player.sendSystemMessage(Component.translatable("message.dedicated_dungeons.started",
            instance.difficulty().runSeconds() / 60));
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
        DungeonSession session = new DungeonSession(instanceId, slot, LEGACY_TEMPLATE,
            portal.dimension(), portal.position());
        data.sessions().put(instanceId, session);
        DungeonTemplate template = DungeonTemplates.get(LEGACY_TEMPLATE);
        GeneratedDungeonPlan arena = createSurvivalPlan(session, template);
        if (arena != null) {
            session.plan(arena);
            DungeonGenerationService.forceChunks(level, arena.bounds(), true);
            if (!DungeonGenerationService.build(level, arena)) {
                DungeonGenerationService.cleanup(level, arena);
                DungeonGenerationService.forceChunks(level, arena.bounds(), false);
                session.plan(null);
            }
        }
        if (session.plan() == null) {
            forceChunks(level, session, template, true);
            buildArena(level, session, template);
        }
        BlockPos spawn = session.plan() == null ? session.center() : session.plan().playerSpawn();
        player.teleportTo(level, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, 0, 0);
        session.nextWaveAtMillis(System.currentTimeMillis()
            + DungeonServerConfig.SURVIVAL_WAVE_DELAY_SECONDS.get() * 1000L);
        data.changed();
        if (!manager.transition(instanceId, InstancePhase.RUNNING, instance.difficulty().runSeconds(),
            "survival_generation_complete")) return false;
        player.sendSystemMessage(Component.translatable("message.dedicated_dungeons.survival_started",
            DungeonServerConfig.SURVIVAL_MAX_WAVES.get(), instance.difficulty().runSeconds() / 60));
        return true;
    }

    private GeneratedDungeonPlan createSurvivalPlan(DungeonSession session, DungeonTemplate template) {
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
        BlockPos mobSpawn = worldMarker(room, MarkerType.MOB_SPAWN, origin, origin);
        BlockPos exit = worldMarker(room, MarkerType.EXIT, origin, origin.offset(0, 0, 8));
        return new GeneratedDungeonPlan(room.theme(),
            ResourceLocation.fromNamespaceAndPath(DedicatedDungeonsMod.MOD_ID, "survival_arena"),
            ResourceLocation.fromNamespaceAndPath(DedicatedDungeonsMod.MOD_ID, "none"), template.rewardPool(),
            java.util.List.of(piece), pieceBounds, playerSpawn, mobSpawn, exit);
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
            if (instance.phase() == InstancePhase.REWARD || instance.phase() == InstancePhase.REWARD_PHASE)
                grantAvailableRewards(session, instance);
            if (instance.type().isSurvival()) {
                tickSurvival(level, session, instance, manager);
            } else {
                Entity entity = session.bossId() == null ? null : level.getEntity(session.bossId());
                if (entity instanceof LivingEntity boss && boss.isAlive()) {
                ServerBossEvent bar = bossBars.computeIfAbsent(instance.id(), id -> createBossBar(instance, boss));
                bar.setProgress(Math.max(0, boss.getHealth() / boss.getMaxHealth()));
                instance.participants().stream().map(server.getPlayerList()::getPlayer).filter(java.util.Objects::nonNull).forEach(bar::addPlayer);
                if (!session.bossEnraged() && boss.getHealth() <= boss.getMaxHealth() * 0.5f) {
                    session.bossEnraged(true); data.changed();
                    boss.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 600, 1));
                    boss.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 20 * 600, 0));
                    instance.participants().stream().map(server.getPlayerList()::getPlayer).filter(java.util.Objects::nonNull)
                        .forEach(player -> player.sendSystemMessage(Component.translatable("message.dedicated_dungeons.boss_enraged")));
                }
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
        spawnSurvivalWave(level, session, instance, wave);
        data.changed();
        instance.participants().stream().map(server.getPlayerList()::getPlayer)
            .filter(java.util.Objects::nonNull).forEach(player -> player.sendSystemMessage(
                Component.translatable("message.dedicated_dungeons.survival_wave", wave,
                    DungeonServerConfig.SURVIVAL_MAX_WAVES.get())));
    }

    private void spawnSurvivalWave(ServerLevel level, DungeonSession session, InstanceView instance, int wave) {
        int count = Math.min(128, DungeonServerConfig.SURVIVAL_BASE_MOBS.get()
            + Math.max(0, wave - 1) * DungeonServerConfig.SURVIVAL_MOBS_PER_WAVE.get());
        int eliteEvery = DungeonServerConfig.SURVIVAL_ELITE_EVERY_WAVES.get();
        boolean eliteWave = eliteEvery > 0 && wave % eliteEvery == 0;
        BlockPos center = session.plan() == null ? session.center() : session.plan().bossSpawn();
        ResourceLocation[] pool = {
            ResourceLocation.withDefaultNamespace("zombie"), ResourceLocation.withDefaultNamespace("skeleton"),
            ResourceLocation.withDefaultNamespace("spider"), ResourceLocation.withDefaultNamespace("husk")
        };
        for (int index = 0; index < count; index++) {
            ResourceLocation typeId = pool[level.random.nextInt(Math.min(pool.length, 2 + wave / 2))];
            Entity created = BuiltInRegistries.ENTITY_TYPE.get(typeId).create(level);
            if (!(created instanceof Mob mob)) continue;
            double angle = level.random.nextDouble() * Math.PI * 2.0;
            double distance = 2.0 + level.random.nextDouble() * 6.0;
            mob.setPos(center.getX() + 0.5 + Math.cos(angle) * distance, center.getY(),
                center.getZ() + 0.5 + Math.sin(angle) * distance);
            mob.setPersistenceRequired();
            mob.addTag(survivalTag(instance.id()));
            double waveScale = 1.0 + Math.max(0, wave - 1) * 0.12;
            boolean elite = eliteWave && index == 0;
            var health = mob.getAttribute(Attributes.MAX_HEALTH);
            if (health != null) health.setBaseValue(health.getBaseValue() * instance.difficulty().healthMultiplier()
                * waveScale * (elite ? 2.0 : 1.0));
            var damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
            if (damage != null) damage.setBaseValue(damage.getBaseValue() * instance.difficulty().damageMultiplier()
                * waveScale * (elite ? 1.5 : 1.0));
            mob.setHealth(mob.getMaxHealth());
            if (elite) {
                mob.setCustomName(Component.translatable("entity.dedicated_dungeons.survival_elite", wave));
                mob.setCustomNameVisible(true);
                mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 3600, 0));
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 20 * 3600, 0));
            }
            level.addFreshEntity(mob);
            NeoForge.EVENT_BUS.post(new UnderworldEvents.MobLifecycle(instance, mob,
                UnderworldEvents.MobLifecycle.Stage.MOB_SPAWNED));
        }
    }

    public boolean bossKilled(UUID bossId) {
        DungeonSession session = data.sessions().values().stream().filter(value -> bossId.equals(value.bossId())).findFirst().orElse(null);
        if (session == null) return false;
        InstanceManager manager = InstanceManager.get(server);
        InstanceView instance = manager.find(session.instanceId()).orElse(null); if (instance == null) return false;
        if (!manager.transition(instance.id(), InstancePhase.REWARD_PHASE, instance.difficulty().rewardSeconds(), "boss_killed")) return false;
        ServerLevel level = server.getLevel(ARENA_DIMENSION);
        if (level != null) {
            Entity boss = level.getEntity(bossId);
            if (boss != null) NeoForge.EVENT_BUS.post(new UnderworldEvents.MobLifecycle(instance, boss,
                UnderworldEvents.MobLifecycle.Stage.BOSS_KILLED));
        }
        ServerBossEvent bar = bossBars.remove(instance.id()); if (bar != null) bar.removeAllPlayers();
        session.eligibleRewards().addAll(instance.participants());
        grantAvailableRewards(session, instance);
        session.exitActive(true); data.changed(); return true;
    }

    public void finish(InstanceView instance) {
        DungeonSession session = data.sessions().get(instance.id());
        if (session == null || !cleaning.add(instance.id())) return;
        InstanceManager manager = InstanceManager.get(server);
        if (!session.cleanupPrepared()) {
            manager.transition(instance.id(), InstancePhase.CLEANUP_PENDING, 0, "runtime_cleanup");
            ServerLevel level = server.getLevel(ARENA_DIMENSION);
            if (level != null) cleanupBlocks(level, session);
            ServerBossEvent bar = bossBars.remove(instance.id()); if (bar != null) bar.removeAllPlayers();
            session.cleanupPrepared(true); data.changed();
        }
        returnAvailablePlayers(instance, session, manager);
        if (session.returnedPlayers().containsAll(instance.participants())) {
            data.sessions().remove(instance.id()); data.changed();
            manager.remove(instance.id(), "dungeon_cleaned");
        }
        cleaning.remove(instance.id());
    }

    public boolean hasSession(UUID instanceId) {
        return data.sessions().containsKey(instanceId);
    }

    public DungeonSession session(UUID instanceId) {
        return data.sessions().get(instanceId);
    }

    public boolean startDirect(ServerPlayer player, DifficultyRank rank, ResourceLocation archetypeId) {
        var definition = DungeonContentRegistry.resolveArchetype(archetypeId)
            .filter(value -> value.difficulties().contains(rank)).orElse(null);
        if (definition == null) {
            player.sendSystemMessage(Component.translatable("message.dedicated_dungeons.invalid_archetype", archetypeId));
            return false;
        }
        InstanceManager manager = InstanceManager.get(server);
        try {
            var instance = manager.create(definition.id(), InstanceType.BOSS_DUNGEON, rank, player, 30);
            PortalRecord synthetic = new PortalRecord(UUID.randomUUID(), player.getUUID(), instance.id(),
                player.level().dimension(), player.blockPosition(), rank, definition.id(),
                dev.underworld.dungeons.portal.PortalOrigin.COMMAND, System.currentTimeMillis() + 30_000L, false);
            if (!manager.addParticipant(instance.id(), player)) return false;
            return start(synthetic, player, definition.id());
        } catch (IllegalStateException exception) {
            player.sendSystemMessage(Component.literal(exception.getMessage()));
            return false;
        }
    }

    public boolean startSurvivalDirect(ServerPlayer player, DifficultyRank rank) {
        InstanceManager manager = InstanceManager.get(server);
        try {
            ResourceLocation definition = ResourceLocation.fromNamespaceAndPath(DedicatedDungeonsMod.MOD_ID, "survival_arena");
            var instance = manager.create(definition, InstanceType.SURVIVAL_ARENA, rank, player, 30);
            PortalRecord synthetic = new PortalRecord(UUID.randomUUID(), player.getUUID(), instance.id(),
                player.level().dimension(), player.blockPosition(), rank, definition,
                dev.underworld.dungeons.portal.PortalOrigin.COMMAND, System.currentTimeMillis() + 30_000L, false);
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
        BlockPos position = session.plan() == null ? session.center() : session.plan().playerSpawn();
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

    private int allocateSlot() {
        Set<Integer> used = data.sessions().values().stream().map(DungeonSession::slot).collect(java.util.stream.Collectors.toSet());
        int maxSlot = Integer.MAX_VALUE / dev.underworld.dungeons.config.DungeonServerConfig.INSTANCE_SLOT_SPACING.get() - 1;
        for (int i = 0; i <= maxSlot; i++) if (!used.contains(i)) return i;
        throw new IllegalStateException("No coordinate-safe dungeon arena slots remain");
    }

    private void buildArena(ServerLevel level, DungeonSession session, DungeonTemplate template) {
        Block floor = BuiltInRegistries.BLOCK.get(template.floorBlock());
        Block wall = BuiltInRegistries.BLOCK.get(template.wallBlock());
        Block accent = BuiltInRegistries.BLOCK.get(template.accentBlock());
        int radius = template.size() / 2; BlockPos center = session.center();
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            level.setBlock(center.offset(x, -1, z), floor.defaultBlockState(), Block.UPDATE_CLIENTS);
            for (int y = 0; y < template.wallHeight(); y++) {
                boolean edge = Math.abs(x) == radius || Math.abs(z) == radius;
                level.setBlock(center.offset(x, y, z), edge
                    ? ((x + z + y) % 7 == 0 ? accent : wall).defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    private LivingEntity spawnBoss(ServerLevel level, DungeonSession session, DungeonTemplate template, InstanceView instance) {
        Entity entity = BuiltInRegistries.ENTITY_TYPE.get(template.bossType()).create(level);
        if (!(entity instanceof LivingEntity boss)) return null;
        boss.setPos(session.center().getX() + 0.5, session.center().getY(), session.center().getZ() - 8.5);
        boss.setCustomName(Component.translatable("entity.dedicated_dungeons.dungeon_boss",
            instance.difficulty().displayName())); boss.setCustomNameVisible(true); boss.addTag(instanceTag(instance.id()));
        if (boss instanceof Mob mob) mob.setPersistenceRequired();
        var health = boss.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(template.bossHealth() * instance.difficulty().healthMultiplier());
        var damage = boss.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) damage.setBaseValue(template.bossDamage() * instance.difficulty().damageMultiplier());
        var armor = boss.getAttribute(Attributes.ARMOR);
        if (armor != null) armor.setBaseValue(Math.min(30, 4 * instance.difficulty().armorMultiplier()));
        boss.setHealth(boss.getMaxHealth()); level.addFreshEntity(boss);
        NeoForge.EVENT_BUS.post(new UnderworldEvents.MobLifecycle(instance, boss,
            UnderworldEvents.MobLifecycle.Stage.BOSS_SPAWNED));
        return boss;
    }

    private LivingEntity spawnBoss(ServerLevel level, DungeonSession session, GeneratedDungeonPlan plan, InstanceView instance) {
        Boss definition = DungeonContentRegistry.boss(plan.bossId()).orElse(null);
        if (definition == null) return null;
        Entity entity = BuiltInRegistries.ENTITY_TYPE.get(definition.entityType()).create(level);
        if (!(entity instanceof LivingEntity boss)) return null;
        BlockPos position = plan.bossSpawn();
        boss.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        configureBoss(boss, definition.health(), definition.damage(), instance);
        level.addFreshEntity(boss);
        NeoForge.EVENT_BUS.post(new UnderworldEvents.MobLifecycle(instance, boss,
            UnderworldEvents.MobLifecycle.Stage.BOSS_SPAWNED));
        return boss;
    }

    private static void configureBoss(LivingEntity boss, double baseHealth, double baseDamage, InstanceView instance) {
        boss.setCustomName(Component.translatable("entity.dedicated_dungeons.dungeon_boss",
            instance.difficulty().displayName())); boss.setCustomNameVisible(true); boss.addTag(instanceTag(instance.id()));
        if (boss instanceof Mob mob) mob.setPersistenceRequired();
        var health = boss.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(baseHealth * instance.difficulty().healthMultiplier());
        var damage = boss.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) damage.setBaseValue(baseDamage * instance.difficulty().damageMultiplier());
        var armor = boss.getAttribute(Attributes.ARMOR);
        if (armor != null) armor.setBaseValue(Math.min(30, 4 * instance.difficulty().armorMultiplier()));
        boss.setHealth(boss.getMaxHealth());
    }

    private ServerBossEvent createBossBar(InstanceView instance, LivingEntity boss) {
        return new ServerBossEvent(boss.getDisplayName(), BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.PROGRESS);
    }

    private void ensureExitPortalVisual(ServerLevel level, DungeonSession session, InstanceView instance) {
        BlockPos exit = session.exitPosition();
        String tag = exitVisualTag(instance.id());
        boolean exists = !level.getEntitiesOfClass(Display.BlockDisplay.class,
            new net.minecraft.world.phys.AABB(exit).inflate(4), entity -> entity.getTags().contains(tag)).isEmpty();
        if (exists) return;
        for (int x = -1; x <= 1; x++) for (int y = 0; y <= 3; y++) {
            boolean frame = x == -1 || x == 1 || y == 0 || y == 3;
            Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
            CompoundTag nbt = new CompoundTag();
            nbt.put("block_state", NbtUtils.writeBlockState((frame
                ? exitFrameBlock(instance.difficulty()) : exitPortalBlock(instance.difficulty())).defaultBlockState()));
            display.load(nbt);
            display.setPos(exit.getX() + x, exit.getY() + y, exit.getZ());
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
        level.getAllEntities().forEach(entity -> {
            if (entity.getTags().contains(survivalTag(session.instanceId()))) entity.discard();
        });
        if (session.plan() != null) {
            DungeonGenerationService.cleanup(level, session.plan());
            DungeonGenerationService.forceChunks(level, session.plan().bounds(), false);
            return;
        }
        DungeonTemplate template = DungeonTemplates.get(session.templateId());
        int radius = template.size() / 2; BlockPos center = session.center();
        level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(center).inflate(radius + 4, template.wallHeight() + 4, radius + 4),
            entity -> !(entity instanceof ServerPlayer)).forEach(Entity::discard);
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++)
            for (int y = -1; y < template.wallHeight(); y++)
                level.setBlock(center.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        forceChunks(level, session, template, false);
    }

    private void forceChunks(ServerLevel level, DungeonSession session, DungeonTemplate template, boolean forced) {
        int radius = template.size() / 2 + 16; BlockPos center = session.center();
        ChunkPos min = new ChunkPos(center.offset(-radius, 0, -radius));
        ChunkPos max = new ChunkPos(center.offset(radius, 0, radius));
        for (int x = min.x; x <= max.x; x++) for (int z = min.z; z <= max.z; z++) level.setChunkForced(x, z, forced);
    }

    private void grantAvailableRewards(DungeonSession session, InstanceView instance) {
        ResourceLocation pool = session.plan() == null ? DungeonTemplates.get(session.templateId()).rewardPool()
            : session.plan().rewardPool();
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
        if (!dev.underworld.dungeons.config.DungeonServerConfig.ENFORCE_INSTANCE_BOUNDS.get()) return;
        BlockPos safe = session.plan() == null ? session.center() : session.plan().playerSpawn();
        for (UUID playerId : instance.participants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null && player.level() == level && !contains(session, player.blockPosition())) {
                player.teleportTo(level, safe.getX() + 0.5, safe.getY(), safe.getZ() + 0.5, player.getYRot(), player.getXRot());
                player.sendSystemMessage(Component.translatable("message.dedicated_dungeons.out_of_bounds"));
            }
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
        if (session.bounds() != null) return session.bounds().contains(position);
        DungeonTemplate template = DungeonTemplates.get(session.templateId());
        BlockPos center = session.center(); int radius = template.size() / 2;
        return Math.abs(position.getX() - center.getX()) <= radius
            && Math.abs(position.getZ() - center.getZ()) <= radius
            && position.getY() >= center.getY() - 1
            && position.getY() < center.getY() + template.wallHeight();
    }
}

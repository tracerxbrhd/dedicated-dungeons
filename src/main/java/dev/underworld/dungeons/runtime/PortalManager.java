package dev.underworld.dungeons.runtime;

import dev.underworld.api.difficulty.DifficultyRank;
import dev.underworld.api.event.UnderworldEvents;
import dev.underworld.api.instance.InstanceManager;
import dev.underworld.api.instance.InstancePhase;
import dev.underworld.api.instance.InstanceView;
import dev.underworld.api.instance.InstanceType;
import dev.underworld.dungeons.config.DungeonServerConfig;
import dev.underworld.dungeons.data.DungeonSavedData;
import dev.underworld.dungeons.data.DungeonSession;
import dev.underworld.dungeons.data.PortalRecord;
import dev.underworld.dungeons.content.DungeonContentRegistry;
import dev.underworld.dungeons.portal.FailureMobPool;
import dev.underworld.dungeons.portal.FailureMobPools;
import dev.underworld.dungeons.portal.PortalOrigin;
import dev.underworld.dungeons.portal.PortalAccessMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class PortalManager {
    private static final Map<MinecraftServer, PortalManager> INSTANCES = new HashMap<>();
    private static final ResourceLocation PORTAL_TYPE = ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", "dungeon");
    private static final ResourceLocation TEMPLATE = ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", "basic");

    private final MinecraftServer server;
    private final DungeonSavedData data;
    private final Map<UUID, Integer> notifiedSeconds = new HashMap<>();
    private final Map<UUID, ServerBossEvent> countdownBars = new HashMap<>();
    private final List<PendingLightning> pendingLightning = new ArrayList<>();

    private PortalManager(MinecraftServer server) {
        this.server = server;
        this.data = DungeonSavedData.get(server);
    }

    public static PortalManager get(MinecraftServer server) {
        return INSTANCES.computeIfAbsent(server, PortalManager::new);
    }

    public int activeCount() { return data.portals().size(); }
    public java.util.Collection<PortalRecord> active() { return List.copyOf(data.portals().values()); }

    public Optional<PortalRecord> createFor(ServerPlayer player, DifficultyRank rank, PortalOrigin origin) {
        ResourceLocation archetype = origin == PortalOrigin.RANDOM
            ? DungeonContentRegistry.chooseAnyArchetype(rank, player.getRandom()).map(value -> value.id()).orElse(TEMPLATE)
            : TEMPLATE;
        InstanceType type = origin == PortalOrigin.RANDOM ? chooseRandomType(player) : InstanceType.BOSS_DUNGEON;
        Optional<PortalRecord> created = createFor(player, rank, origin, archetype, type);
        if (origin == PortalOrigin.RANDOM && created.isPresent()) recordRandomType(type);
        return created;
    }

    public Optional<PortalRecord> createFor(ServerPlayer player, DifficultyRank rank, PortalOrigin origin,
                                            ResourceLocation requestedArchetype) {
        return createFor(player, rank, origin, requestedArchetype, InstanceType.BOSS_DUNGEON);
    }

    public Optional<PortalRecord> createFor(ServerPlayer player, DifficultyRank rank, PortalOrigin origin,
                                            ResourceLocation requestedArchetype, InstanceType type) {
        int maxPortals = DungeonServerConfig.MAX_ACTIVE_PORTALS.get();
        if (origin.appliesWorldLimits() && maxPortals > 0 && activeCount() >= maxPortals) return Optional.empty();
        if (InstanceManager.get(server).findByPlayer(player.getUUID()).isPresent()) {
            if (!origin.appliesWorldLimits()) player.sendSystemMessage(Component.translatable(
                "message.dedicated_dungeons.create_failed_active"));
            return Optional.empty();
        }
        if (origin.appliesWorldLimits() && isOnRandomCooldown(player.getUUID(), rank)) return Optional.empty();

        Optional<BlockPos> position = origin == PortalOrigin.RANDOM
            ? findRandomSafePosition(player) : Optional.of(findPersonalPortalPosition(player));
        if (position.isEmpty()) {
            if (!origin.appliesWorldLimits()) player.sendSystemMessage(Component.translatable(
                "message.dedicated_dungeons.create_failed_position"));
            return Optional.empty();
        }

        // PortalManager owns the visible entry timer. The small grace period prevents the API timer
        // from winning the same server tick and bypassing shatter effects.
        ResourceLocation archetype = DungeonContentRegistry.resolveArchetype(requestedArchetype)
            .filter(value -> value.difficulties().contains(rank)).map(value -> value.id()).orElse(null);
        if (archetype == null && !requestedArchetype.equals(TEMPLATE)) {
            player.sendSystemMessage(Component.translatable("message.dedicated_dungeons.invalid_archetype", requestedArchetype));
            return Optional.empty();
        }
        if (archetype == null) archetype = TEMPLATE;
        dev.underworld.api.instance.ManagedInstance instance;
        try {
            instance = InstanceManager.get(server).create(archetype, type, rank,
                player, rank.entrySeconds() + 5);
        } catch (IllegalStateException exception) {
            player.sendSystemMessage(Component.translatable("message.dedicated_dungeons.create_failed", exception.getMessage()));
            return Optional.empty();
        }
        PortalRecord portal = new PortalRecord(UUID.randomUUID(), player.getUUID(), instance.id(),
            player.level().dimension(), position.get(), rank, archetype, origin,
            System.currentTimeMillis() + rank.entrySeconds() * 1000L, false);
        data.portals().put(portal.id(), portal);
        if (origin.appliesWorldLimits()) applyRandomCooldown(player.getUUID(), rank);
        data.changed();

        spawnVisual(portal);
        spawnAppearanceEffects(portal);
        player.sendSystemMessage(Component.translatable("message.dedicated_dungeons.portal_appeared_detailed",
            portalName(portal), rank.entrySeconds(), portal.position().getX(), portal.position().getY(), portal.position().getZ()));
        NeoForge.EVENT_BUS.post(new UnderworldEvents.PortalLifecycle(PORTAL_TYPE,
            UnderworldEvents.PortalLifecycle.Stage.APPEARED, portal.dimension(), portal.position(), player));
        return Optional.of(portal);
    }

    public void tick() {
        long now = System.currentTimeMillis();
        for (PortalRecord portal : new ArrayList<>(data.portals().values())) {
            ServerLevel level = server.getLevel(portal.dimension());
            if (level == null) continue;
            InstanceManager manager = InstanceManager.get(server);
            InstanceView instance = manager.find(portal.instanceId()).orElse(null);
            if (instance == null || instance.phase().terminal()) {
                closePortal(portal);
                continue;
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(portal.ownerId());
            if (!portal.entered() && now >= portal.deadlineMillis()) {
                shatter(portal, owner, true, true, "portal_expired");
                continue;
            }

            if (!portal.entered()) {
                int seconds = Math.max(0, (int) Math.ceil((portal.deadlineMillis() - now) / 1000.0));
                updateCountdownBar(portal, seconds);
                if ((seconds == 60 || seconds == 30 || seconds == 10)
                    && notifiedSeconds.getOrDefault(portal.id(), -1) != seconds) {
                    notifiedSeconds.put(portal.id(), seconds);
                    if (owner != null) owner.sendSystemMessage(Component.translatable(
                        "message.dedicated_dungeons.portal_timer", portalName(portal), seconds));
                }
            } else {
                removeCountdownBar(portal.id());
            }

            sendPortalParticles(level, portal, false);
            for (ServerPlayer candidate : server.getPlayerList().getPlayers()) {
                if (candidate.level() != level || candidate.distanceToSqr(portal.position().getX() + 0.5,
                    portal.position().getY() + 1.0, portal.position().getZ() + 0.5) >= 4.0) continue;
                if (canEnter(portal, instance, candidate)) enter(portal, candidate);
            }
        }
        cleanupExpiredMobs();
    }

    public void tickLightning() {
        long tick = server.getTickCount();
        Iterator<PendingLightning> iterator = pendingLightning.iterator();
        while (iterator.hasNext()) {
            PendingLightning pending = iterator.next();
            if (tick < pending.nextTick) continue;
            strike(pending.dimension, pending.position);
            pending.remaining--;
            if (pending.remaining <= 0) iterator.remove();
            else pending.nextTick = tick + DungeonServerConfig.LIGHTNING_INTERVAL_TICKS.get();
        }
    }

    public boolean remove(UUID id, String reason) {
        PortalRecord portal = data.portals().remove(id);
        if (portal == null) return false;
        removeVisual(portal);
        removeCountdownBar(portal.id());
        notifiedSeconds.remove(id);
        data.changed();
        InstanceManager manager = InstanceManager.get(server);
        manager.fail(portal.instanceId(), reason);
        manager.remove(portal.instanceId(), reason);
        return true;
    }

    public int removeAll(String reason) {
        List<UUID> ids = new ArrayList<>(data.portals().keySet());
        ids.forEach(id -> remove(id, reason));
        return ids.size();
    }

    /** Handles a failure raised by the API before PortalManager's own expiry tick. */
    public boolean shatterForInstance(UUID instanceId, boolean spawnMobs) {
        PortalRecord portal = data.portals().values().stream()
            .filter(value -> value.instanceId().equals(instanceId)).findFirst().orElse(null);
        if (portal == null) return false;
        ServerPlayer owner = server.getPlayerList().getPlayer(portal.ownerId());
        shatter(portal, owner, spawnMobs, false, "instance_failed");
        return true;
    }

    public boolean closeForInstance(UUID instanceId) {
        PortalRecord portal = data.portals().values().stream()
            .filter(value -> value.instanceId().equals(instanceId)).findFirst().orElse(null);
        if (portal == null) return false;
        closePortal(portal);
        return true;
    }

    public void shatterAt(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                          BlockPos position, DifficultyRank rank, boolean spawnMobs) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) return;
        level.sendParticles(ParticleTypes.EXPLOSION, position.getX() + 0.5, position.getY() + 1.5,
            position.getZ() + 0.5, 12, 1.5, 2.0, 1.5, 0.02);
        level.playSound(null, position, SoundEvents.GLASS_BREAK, SoundSource.AMBIENT, 2.0f, 0.45f);
        scheduleLightning(dimension, position, DungeonServerConfig.FAILURE_LIGHTNING_STRIKES.get());
        if (spawnMobs && DungeonServerConfig.SPAWN_MOBS_ON_EXPIRE.get()) spawnFailureMobs(dimension, position, rank);
    }

    private void enter(PortalRecord portal, ServerPlayer player) {
        InstanceManager manager = InstanceManager.get(server);
        if (!manager.addParticipant(portal.instanceId(), player)) return;
        DungeonRuntime runtime = DungeonRuntime.get(server);
        boolean entered = runtime.hasSession(portal.instanceId())
            ? runtime.teleportToSession(player, portal.instanceId())
            : runtime.start(portal, player, portal.archetypeId());
        if (!entered) return;
        PortalRecord current = data.portals().get(portal.id());
        if (current != null && !current.entered()) data.portals().put(portal.id(), current.markEntered());
        data.changed();
        removeCountdownBar(portal.id());
        NeoForge.EVENT_BUS.post(new UnderworldEvents.PortalLifecycle(PORTAL_TYPE,
            UnderworldEvents.PortalLifecycle.Stage.ENTERED, portal.dimension(), portal.position(), player));
    }

    private boolean canEnter(PortalRecord portal, InstanceView instance, ServerPlayer player) {
        if (!player.isAlive() || player.isRemoved()) return false;
        if (player.isSpectator() && !DungeonServerConfig.ALLOW_SPECTATOR_ENTRY.get()) return false;
        if (instance.phase().terminal()) return false;
        boolean participant = instance.participants().contains(player.getUUID());
        DungeonRuntime runtime = DungeonRuntime.get(server);
        DungeonSession session = runtime.session(instance.id());
        if (session != null && session.returnedPlayers().contains(player.getUUID())) return false;
        if (!participant && instance.phase() != InstancePhase.WAITING_FOR_ENTRY
            && !DungeonServerConfig.ALLOW_LATE_JOIN.get()) return false;
        Optional<InstanceView> assigned = InstanceManager.get(server).findByPlayer(player.getUUID());
        if (assigned.isPresent() && !assigned.get().id().equals(instance.id())) return false;

        PortalAccessMode fallback = portal.origin() == PortalOrigin.KEY
            ? PortalAccessMode.OWNER_ONLY : PortalAccessMode.PUBLIC_NEARBY;
        String configured = portal.origin() == PortalOrigin.KEY
            ? DungeonServerConfig.PERSONAL_PORTAL_ACCESS.get() : DungeonServerConfig.RANDOM_PORTAL_ACCESS.get();
        PortalAccessMode mode = PortalAccessMode.byName(configured, fallback);
        if (participant || player.getUUID().equals(portal.ownerId())) return true;
        if (mode == PortalAccessMode.OWNER_ONLY || mode == PortalAccessMode.PARTY) return false;
        int range = DungeonServerConfig.NEARBY_ACCESS_RADIUS.get();
        return player.level().dimension().equals(portal.dimension())
            && player.distanceToSqr(portal.position().getX() + 0.5, portal.position().getY() + 1.0,
                portal.position().getZ() + 0.5) <= range * range;
    }

    private void closePortal(PortalRecord portal) {
        if (data.portals().remove(portal.id()) == null) return;
        removeVisual(portal);
        removeCountdownBar(portal.id());
        notifiedSeconds.remove(portal.id());
        data.changed();
    }

    private void shatter(PortalRecord portal, ServerPlayer owner, boolean spawnMobs,
                         boolean transitionInstance, String reason) {
        if (data.portals().remove(portal.id()) == null) return;
        data.changed();
        removeVisual(portal);
        removeCountdownBar(portal.id());
        notifiedSeconds.remove(portal.id());
        shatterAt(portal.dimension(), portal.position(), portal.rank(), spawnMobs);
        if (owner != null) owner.sendSystemMessage(Component.translatable(
            "message.dedicated_dungeons.portal_expired", portalName(portal)));
        NeoForge.EVENT_BUS.post(new UnderworldEvents.PortalLifecycle(PORTAL_TYPE,
            UnderworldEvents.PortalLifecycle.Stage.EXPIRED, portal.dimension(), portal.position(), owner));
        if (transitionInstance) InstanceManager.get(server).fail(portal.instanceId(), reason);
    }

    private Optional<BlockPos> findRandomSafePosition(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        int min = DungeonServerConfig.MIN_PORTAL_DISTANCE.get();
        int max = Math.max(min, DungeonServerConfig.MAX_PORTAL_DISTANCE.get());
        for (int attempt = 0; attempt < 24; attempt++) {
            double angle = player.getRandom().nextDouble() * Math.PI * 2;
            int distance = min + player.getRandom().nextInt(max - min + 1);
            int x = player.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = player.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (isSafe(level, pos)) return Optional.of(pos);
        }
        return Optional.empty();
    }

    private BlockPos findPersonalPortalPosition(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        Direction direction = player.getDirection();
        BlockPos preferred = player.blockPosition().offset(direction.getStepX() * 4, 0, direction.getStepZ() * 4);
        for (int radius = 0; radius <= 4; radius++) {
            for (int dy = 4; dy >= -6; dy--) {
                BlockPos candidate = preferred.offset(radius == 0 ? 0 : level.random.nextInt(radius * 2 + 1) - radius,
                    dy, radius == 0 ? 0 : level.random.nextInt(radius * 2 + 1) - radius);
                if (isSafe(level, candidate)) return candidate;
            }
        }
        // Block displays have no collision. At extreme build heights or while flying, a floating portal is valid.
        return preferred;
    }

    private static boolean isSafe(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir()
            && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    private void updateCountdownBar(PortalRecord portal, int seconds) {
        ServerBossEvent bar = countdownBars.computeIfAbsent(portal.id(), id -> new ServerBossEvent(
            Component.translatable("bossbar.dedicated_dungeons.portal", portalName(portal), seconds),
            barColor(portal.rank()), BossEvent.BossBarOverlay.PROGRESS));
        bar.setName(Component.translatable("bossbar.dedicated_dungeons.portal", portalName(portal), seconds));
        bar.setProgress(Math.max(0, Math.min(1, seconds / (float) portal.rank().entrySeconds())));
        bar.removeAllPlayers();
        ServerLevel level = server.getLevel(portal.dimension());
        if (level == null) return;
        int range = DungeonServerConfig.PORTAL_BAR_RANGE.get();
        server.getPlayerList().getPlayers().stream()
            .filter(player -> player.level() == level)
            .filter(player -> player.distanceToSqr(portal.position().getX() + 0.5,
                portal.position().getY() + 1.0, portal.position().getZ() + 0.5) <= range * range)
            .forEach(bar::addPlayer);
    }

    private void removeCountdownBar(UUID portalId) {
        ServerBossEvent bar = countdownBars.remove(portalId);
        if (bar != null) bar.removeAllPlayers();
    }

    private void spawnVisual(PortalRecord portal) {
        ServerLevel level = server.getLevel(portal.dimension());
        if (level == null) return;
        String tag = visualTag(portal.id());
        for (int x = -2; x <= 2; x++) for (int y = 0; y <= 4; y++) {
            boolean frame = x == -2 || x == 2 || y == 0 || y == 4;
            Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
            CompoundTag nbt = new CompoundTag();
            nbt.put("block_state", NbtUtils.writeBlockState(frame
                ? frameBlock(portal.rank()).defaultBlockState() : portalBlock(portal.rank()).defaultBlockState()));
            display.load(nbt);
            display.setPos(portal.position().getX() + x, portal.position().getY() + y, portal.position().getZ());
            display.addTag(tag);
            level.addFreshEntity(display);
        }
    }

    private void spawnAppearanceEffects(PortalRecord portal) {
        ServerLevel level = server.getLevel(portal.dimension());
        if (level == null) return;
        sendPortalParticles(level, portal, true);
        float volume = 0.55f + portal.rank().ordinal() * 0.16f;
        float pitch = Math.max(0.35f, 1.2f - portal.rank().ordinal() * 0.11f);
        level.playSound(null, portal.position(), portal.rank().ordinal() >= DifficultyRank.A.ordinal()
            ? SoundEvents.RESPAWN_ANCHOR_DEPLETE.value() : SoundEvents.PORTAL_TRIGGER, SoundSource.AMBIENT, volume, pitch);
        int configured = DungeonServerConfig.APPEARANCE_LIGHTNING_STRIKES.get();
        int strikes = switch (portal.rank()) {
            case E -> configured <= 0 ? 0 : Math.max(1, configured / 3);
            case D -> configured <= 0 ? 0 : Math.max(1, configured / 2);
            case C -> configured <= 0 ? 0 : Math.max(1, configured - 1);
            case B -> configured;
            case A -> configured + 1;
            case S -> configured + 2;
            case ANOMALY -> configured + 4;
        };
        scheduleLightning(portal.dimension(), portal.position(), strikes);
    }

    private void sendPortalParticles(ServerLevel level, PortalRecord portal, boolean burst) {
        double intensity = DungeonServerConfig.PORTAL_EFFECT_INTENSITY.get();
        if (intensity <= 0) return;
        int base = burst ? 24 : 6;
        int count = Math.max(1, (int) Math.round(base * intensity * (1.0 + portal.rank().ordinal() * 0.28)));
        var particles = switch (portal.rank()) {
            case E -> ParticleTypes.PORTAL;
            case D -> ParticleTypes.REVERSE_PORTAL;
            case C -> ParticleTypes.ENCHANT;
            case B -> ParticleTypes.SOUL_FIRE_FLAME;
            case A -> ParticleTypes.FLAME;
            case S -> ParticleTypes.WITCH;
            case ANOMALY -> ParticleTypes.SCULK_SOUL;
        };
        level.sendParticles(particles, portal.position().getX() + 0.5, portal.position().getY() + 2.5,
            portal.position().getZ() + 0.5, count, 1.8, 2.2, 0.25, burst ? 0.08 : 0.02);
    }

    private static net.minecraft.world.level.block.Block frameBlock(DifficultyRank rank) {
        return switch (rank) {
            case E -> Blocks.OBSIDIAN;
            case D -> Blocks.CRYING_OBSIDIAN;
            case C -> Blocks.AMETHYST_BLOCK;
            case B -> Blocks.POLISHED_BLACKSTONE;
            case A -> Blocks.MAGMA_BLOCK;
            case S -> Blocks.RESPAWN_ANCHOR;
            case ANOMALY -> Blocks.SCULK_CATALYST;
        };
    }

    private static net.minecraft.world.level.block.Block portalBlock(DifficultyRank rank) {
        return switch (rank) {
            case E -> Blocks.PURPLE_STAINED_GLASS;
            case D -> Blocks.MAGENTA_STAINED_GLASS;
            case C -> Blocks.BLUE_STAINED_GLASS;
            case B -> Blocks.ORANGE_STAINED_GLASS;
            case A -> Blocks.RED_STAINED_GLASS;
            case S -> Blocks.TINTED_GLASS;
            case ANOMALY -> Blocks.SCULK;
        };
    }

    private void removeVisual(PortalRecord portal) {
        ServerLevel level = server.getLevel(portal.dimension());
        if (level == null) return;
        String tag = visualTag(portal.id());
        level.getEntitiesOfClass(Display.BlockDisplay.class, new AABB(portal.position()).inflate(6),
            entity -> entity.getTags().contains(tag)).forEach(Entity::discard);
    }

    private void scheduleLightning(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                                   BlockPos position, int count) {
        if (count <= 0 || DungeonServerConfig.LIGHTNING_MODE.get().equalsIgnoreCase("OFF")) return;
        pendingLightning.add(new PendingLightning(dimension, position.immutable(), count, server.getTickCount()));
    }

    private void strike(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos position) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) return;
        String mode = DungeonServerConfig.LIGHTNING_MODE.get().toUpperCase();
        if (mode.equals("OFF")) return;
        LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
        bolt.setPos(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        bolt.setVisualOnly(!mode.equals("REAL"));
        level.addFreshEntity(bolt);
    }

    private void spawnFailureMobs(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                                  BlockPos position, DifficultyRank rank) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;
        FailureMobPool pool = FailureMobPools.get(ResourceLocation.parse(DungeonServerConfig.FAILURE_MOB_POOL.get()));
        int count = DungeonServerConfig.EXPIRED_MOB_COUNT.get() + rank.ordinal();
        for (int i = 0; i < count; i++) {
            FailureMobPool.Entry entry = pool.choose(rank, level.random);
            if (entry == null) break;
            Entity entity = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(entry.entityType()).create(level);
            if (!(entity instanceof Mob mob)) continue;
            mob.setPos(position.getX() + level.random.nextInt(7) - 3,
                position.getY(), position.getZ() + level.random.nextInt(7) - 3);
            mob.setPersistenceRequired();
            mob.addTag("dedicated_dungeons:expired_portal_mob");
            if (mob instanceof Zombie zombie) zombie.setCanBreakDoors(false);
            scaleFailureMob(mob, rank);
            level.addFreshEntity(mob);
            NeoForge.EVENT_BUS.post(new UnderworldEvents.MobLifecycle(null, mob,
                UnderworldEvents.MobLifecycle.Stage.MOB_SPAWNED));
        }
    }

    private static void scaleFailureMob(LivingEntity mob, DifficultyRank rank) {
        var health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) health.setBaseValue(health.getBaseValue() * rank.healthMultiplier());
        var damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) damage.setBaseValue(damage.getBaseValue() * rank.damageMultiplier());
        mob.setHealth(mob.getMaxHealth());
    }

    private void cleanupExpiredMobs() {
        int maxAge = DungeonServerConfig.EXPIRED_MOB_LIFETIME.get() * 20;
        for (ServerLevel level : server.getAllLevels()) level.getAllEntities().forEach(entity -> {
            if (entity.tickCount > maxAge
                && entity.getTags().contains("dedicated_dungeons:expired_portal_mob")) entity.discard();
        });
    }

    private boolean isOnRandomCooldown(UUID playerId, DifficultyRank rank) {
        return System.currentTimeMillis() < data.randomCooldowns().getOrDefault(cooldownKey(playerId, rank), 0L);
    }

    private void applyRandomCooldown(UUID playerId, DifficultyRank rank) {
        int min = Math.min(DungeonServerConfig.COOLDOWN_RANDOM_MIN_SECONDS.get(),
            DungeonServerConfig.COOLDOWN_RANDOM_MAX_SECONDS.get());
        int max = Math.max(DungeonServerConfig.COOLDOWN_RANDOM_MIN_SECONDS.get(),
            DungeonServerConfig.COOLDOWN_RANDOM_MAX_SECONDS.get());
        int random = min + (max == min ? 0 : server.overworld().random.nextInt(max - min + 1));
        int base = switch (rank) {
            case E -> DungeonServerConfig.COOLDOWN_E_SECONDS.get();
            case D -> DungeonServerConfig.COOLDOWN_D_SECONDS.get();
            case C -> DungeonServerConfig.COOLDOWN_C_SECONDS.get();
            case B -> DungeonServerConfig.COOLDOWN_B_SECONDS.get();
            case A -> DungeonServerConfig.COOLDOWN_A_SECONDS.get();
            case S -> DungeonServerConfig.COOLDOWN_S_SECONDS.get();
            case ANOMALY -> DungeonServerConfig.COOLDOWN_ANOMALY_SECONDS.get();
        };
        data.randomCooldowns().put(cooldownKey(playerId, rank), System.currentTimeMillis() + (base + random) * 1000L);
    }

    private static String cooldownKey(UUID playerId, DifficultyRank rank) { return playerId + "_" + rank.name(); }
    private InstanceType chooseRandomType(ServerPlayer player) {
        int boss = DungeonServerConfig.BOSS_DUNGEON_WEIGHT.get();
        int survival = DungeonServerConfig.SURVIVAL_ARENA_WEIGHT.get();
        int pity = DungeonServerConfig.SURVIVAL_PITY_AFTER_BOSS_PORTALS.get();
        if (survival > 0 && pity > 0 && data.randomBossPortalStreak() >= pity)
            return InstanceType.SURVIVAL_ARENA;
        int total = boss + survival;
        if (survival <= 0 || total <= 0) return InstanceType.BOSS_DUNGEON;
        return player.getRandom().nextInt(total) < survival ? InstanceType.SURVIVAL_ARENA : InstanceType.BOSS_DUNGEON;
    }

    private void recordRandomType(InstanceType type) {
        data.randomBossPortalStreak(type.isSurvival() ? 0 : data.randomBossPortalStreak() + 1);
    }
    private static String visualTag(UUID id) { return "dd_portal_" + id; }
    private static Component portalName(PortalRecord portal) {
        Component theme = DungeonContentRegistry.themeForArchetype(portal.archetypeId())
            .<Component>map(value -> Component.translatable(value.translationKey()))
            .orElse(Component.translatable("dungeon_theme.dedicated_dungeons.underworld"));
        return Component.translatable("portal.dedicated_dungeons.named", theme, portal.rank().displayName());
    }
    private static BossEvent.BossBarColor barColor(DifficultyRank rank) {
        return switch (rank) {
            case E, D -> BossEvent.BossBarColor.GREEN;
            case C -> BossEvent.BossBarColor.BLUE;
            case B -> BossEvent.BossBarColor.PURPLE;
            case A -> BossEvent.BossBarColor.YELLOW;
            case S -> BossEvent.BossBarColor.RED;
            case ANOMALY -> BossEvent.BossBarColor.PURPLE;
        };
    }

    private static final class PendingLightning {
        private final net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension;
        private final BlockPos position;
        private int remaining;
        private long nextTick;
        private PendingLightning(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                                 BlockPos position, int remaining, long nextTick) {
            this.dimension = dimension;
            this.position = position;
            this.remaining = remaining;
            this.nextTick = nextTick;
        }
    }
}

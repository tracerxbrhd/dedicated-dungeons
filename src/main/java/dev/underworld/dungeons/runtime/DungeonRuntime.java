package dev.underworld.dungeons.runtime;

import dev.underworld.api.event.UnderworldEvents;
import dev.underworld.api.instance.InstanceManager;
import dev.underworld.api.instance.InstancePhase;
import dev.underworld.api.instance.InstanceView;
import dev.underworld.api.reward.RewardContext;
import dev.underworld.api.reward.RewardRegistry;
import dev.underworld.dungeons.DedicatedDungeonsMod;
import dev.underworld.dungeons.data.DungeonSavedData;
import dev.underworld.dungeons.data.DungeonSession;
import dev.underworld.dungeons.data.PortalRecord;
import dev.underworld.dungeons.template.DungeonTemplate;
import dev.underworld.dungeons.template.DungeonTemplates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class DungeonRuntime {
    public static final ResourceKey<Level> ARENA_DIMENSION = ResourceKey.create(Registries.DIMENSION,
        ResourceLocation.fromNamespaceAndPath(DedicatedDungeonsMod.MOD_ID, "underworld_arena"));
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
            DungeonTemplate template = DungeonTemplates.get(session.templateId());
            BlockPos center = session.center(); int radius = template.size() / 2;
            if (Math.abs(position.getX() - center.getX()) <= radius
                && Math.abs(position.getZ() - center.getZ()) <= radius
                && position.getY() >= center.getY() - 1
                && position.getY() < center.getY() + template.wallHeight()) return true;
        }
        return false;
    }

    public boolean start(PortalRecord portal, ServerPlayer player, ResourceLocation templateId) {
        UUID instanceId = portal.instanceId();
        ServerLevel level = server.getLevel(ARENA_DIMENSION);
        if (level == null) {
            player.sendSystemMessage(Component.translatable("message.dedicated_dungeons.missing_dimension"));
            InstanceManager.get(server).fail(instanceId, "missing_arena_dimension"); return false;
        }
        InstanceManager manager = InstanceManager.get(server);
        InstanceView instance = manager.find(instanceId).orElse(null);
        if (instance == null || !manager.transition(instanceId, InstancePhase.PREPARING, 30, "portal_entered")) return false;
        int slot = allocateSlot();
        DungeonSession session = new DungeonSession(instanceId, slot, templateId,
            portal.dimension(), portal.position());
        data.sessions().put(instanceId, session); data.changed();
        DungeonTemplate template = DungeonTemplates.get(templateId);
        forceChunks(level, session, template, true);
        buildArena(level, session, template);
        player.teleportTo(level, session.center().getX() + 0.5, session.center().getY(),
            session.center().getZ() + 0.5, 0, 0);
        LivingEntity boss = spawnBoss(level, session, template, instance);
        if (boss == null) { manager.fail(instanceId, "boss_spawn_failed"); return false; }
        session.bossId(boss.getUUID()); data.changed();
        bossBars.put(instance.id(), createBossBar(instance, boss));
        manager.transition(instanceId, InstancePhase.RUNNING, instance.difficulty().runSeconds(), "arena_ready");
        player.sendSystemMessage(Component.translatable("message.dedicated_dungeons.started",
            instance.difficulty().runSeconds() / 60));
        return true;
    }

    public void tick() {
        ServerLevel level = server.getLevel(ARENA_DIMENSION); if (level == null) return;
        InstanceManager manager = InstanceManager.get(server);
        for (DungeonSession session : new ArrayList<>(data.sessions().values())) {
            InstanceView instance = manager.find(session.instanceId()).orElse(null);
            if (instance == null) { cleanupBlocks(level, session); continue; }
            if (instance.phase().terminal()) {
                if (participantsReadyForReturn(instance)) finish(instance);
                continue;
            }
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
            if (session.exitActive()) {
                BlockPos exit = session.exitPosition();
                level.sendParticles(ParticleTypes.PORTAL, exit.getX() + 0.5, exit.getY() + 1,
                    exit.getZ() + 0.5, 25, 0.8, 1.2, 0.8, 0.04);
                for (UUID playerId : instance.participants()) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null && player.level() == level && player.distanceToSqr(
                        exit.getX() + 0.5, exit.getY() + 0.5, exit.getZ() + 0.5) < 4.0) {
                        manager.complete(instance.id(), "exit_portal"); break;
                    }
                }
            }
        }
    }

    public boolean bossKilled(UUID bossId) {
        DungeonSession session = data.sessions().values().stream().filter(value -> bossId.equals(value.bossId())).findFirst().orElse(null);
        if (session == null) return false;
        InstanceManager manager = InstanceManager.get(server);
        InstanceView instance = manager.find(session.instanceId()).orElse(null); if (instance == null) return false;
        if (!manager.transition(instance.id(), InstancePhase.REWARD, instance.difficulty().rewardSeconds(), "boss_killed")) return false;
        ServerLevel level = server.getLevel(ARENA_DIMENSION);
        if (level != null) {
            Entity boss = level.getEntity(bossId);
            if (boss != null) NeoForge.EVENT_BUS.post(new UnderworldEvents.MobLifecycle(instance, boss,
                UnderworldEvents.MobLifecycle.Stage.BOSS_KILLED));
        }
        ServerBossEvent bar = bossBars.remove(instance.id()); if (bar != null) bar.removeAllPlayers();
        for (UUID playerId : instance.participants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player != null) {
                RewardRegistry.grant(DungeonTemplates.get(session.templateId()).rewardPool(), new RewardContext(instance, player));
                player.sendSystemMessage(Component.translatable("message.dedicated_dungeons.victory",
                    instance.difficulty().rewardSeconds()));
            }
        }
        session.exitActive(true); data.changed(); return true;
    }

    public void finish(InstanceView instance) {
        DungeonSession session = data.sessions().get(instance.id());
        if (session == null || !cleaning.add(instance.id())) return;
        InstanceManager manager = InstanceManager.get(server); manager.returnAll(instance);
        ServerLevel level = server.getLevel(ARENA_DIMENSION);
        if (level != null) cleanupBlocks(level, session);
        ServerBossEvent bar = bossBars.remove(instance.id()); if (bar != null) bar.removeAllPlayers();
        data.sessions().remove(instance.id()); data.changed();
        manager.remove(instance.id(), "dungeon_cleaned"); cleaning.remove(instance.id());
    }

    public boolean hasSession(UUID instanceId) {
        return data.sessions().containsKey(instanceId);
    }

    public DungeonSession session(UUID instanceId) {
        return data.sessions().get(instanceId);
    }

    public void requestFinish(InstanceView instance) {
        if (!hasSession(instance.id())) return;
        if (participantsReadyForReturn(instance)) finish(instance);
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
        for (int i = 0; i < 1024; i++) if (!used.contains(i)) return i;
        throw new IllegalStateException("No free dungeon arena slots");
    }

    private boolean participantsReadyForReturn(InstanceView instance) {
        for (UUID playerId : instance.participants()) {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (player == null || !player.isAlive()) return false;
        }
        return true;
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
            instance.difficulty().name())); boss.setCustomNameVisible(true); boss.addTag(instanceTag(instance.id()));
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

    private ServerBossEvent createBossBar(InstanceView instance, LivingEntity boss) {
        return new ServerBossEvent(boss.getDisplayName(), BossEvent.BossBarColor.PURPLE,
            BossEvent.BossBarOverlay.PROGRESS);
    }

    private void cleanupBlocks(ServerLevel level, DungeonSession session) {
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

    private static String instanceTag(UUID id) { return "dd_instance_" + id; }
}

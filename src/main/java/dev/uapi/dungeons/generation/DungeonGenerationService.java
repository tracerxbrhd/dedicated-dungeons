package dev.uapi.dungeons.generation;

import dev.uapi.dungeons.content.DungeonContentRegistry;
import dev.uapi.dungeons.config.DungeonServerConfig;
import dev.uapi.dungeons.content.DungeonContentTypes.Room;
import dev.uapi.dungeons.content.DungeonContentTypes.Palette;
import dev.uapi.dungeons.content.DungeonContentTypes.MarkerType;
import dev.uapi.dungeons.content.DungeonDefinition;
import dev.uapi.dungeons.content.EncounterDefinitions.EncounterPool;
import dev.uapi.dungeons.DedicatedDungeonsMod;
import dev.uapi.dungeons.generation.GeneratedDungeonPlan.Piece;
import dev.uapi.dungeons.generation.GeneratedDungeonPlan.PieceType;
import dev.uapi.dungeons.generation.GeneratedDungeonPlan.PlacedMarker;
import dev.uapi.dungeons.marker.DungeonMarkerBlock;
import dev.uapi.dungeons.marker.LootMarkerBlockEntity;
import dev.uapi.dungeons.marker.SpawnerMarkerBlockEntity;
import dev.uapi.dungeons.loot.DungeonLootResolver;
import dev.uapi.dungeons.loot.LootContainerType;
import dev.uapi.dungeons.loot.LootMarkerData;
import dev.uapi.dungeons.mob.DungeonMobResolver;
import dev.uapi.dungeons.mob.MobSpawnData;
import dev.uapi.dungeons.mob.SpawnMode;
import dev.uapi.dungeons.mob.SpawnRole;
import dev.uapi.dungeons.runtime.DungeonMobConfigurator;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;

/** Applies an already validated plan. Planning remains side-effect free. */
public final class DungeonGenerationService {
    private DungeonGenerationService() {}

    public static boolean build(ServerLevel level, GeneratedDungeonPlan plan) {
        return build(level, plan, 0, 0, new UUID(0L, 0L), true);
    }

    public static boolean build(ServerLevel level, GeneratedDungeonPlan plan, int selectedLevel, int tier) {
        return build(level, plan, selectedLevel, tier, new UUID(0L, 0L), true);
    }

    public static boolean build(ServerLevel level, GeneratedDungeonPlan plan, int selectedLevel, int tier,
                                UUID instanceId, boolean materializeEncounters) {
        return build(level, plan, selectedLevel, tier, instanceId, plan.archetypeId(),
            materializeEncounters, 1);
    }

    public static boolean build(ServerLevel level, GeneratedDungeonPlan plan, int selectedLevel, int tier,
                                UUID instanceId, net.minecraft.resources.ResourceLocation dungeonId,
                                boolean materializeEncounters) {
        return build(level, plan, selectedLevel, tier, instanceId, dungeonId, materializeEncounters, 1);
    }

    public static boolean build(ServerLevel level, GeneratedDungeonPlan plan, int selectedLevel, int tier,
                                UUID instanceId, net.minecraft.resources.ResourceLocation dungeonId,
                                boolean materializeEncounters, int partySize) {
        if (!prevalidate(level, plan)) return false;
        for (Piece piece : plan.pieces()) {
            ResourcePiece definition = definition(piece);
            if (definition == null) return rollback(level, plan, "definition disappeared during generation");
            if (definition.structure() == null) buildProcedural(level, piece.bounds(), definition.palette());
            else {
                var template = level.getStructureManager().get(definition.structure()).orElse(null);
                if (template == null) return rollback(level, plan, "structure disappeared during generation: " + definition.structure());
                StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(piece.rotation())
                    .setIgnoreEntities(false).setFinalizeEntities(true);
                if (!template.placeInWorld(level, piece.origin(), piece.origin(), settings, level.random, Block.UPDATE_ALL))
                    return rollback(level, plan, "structure placement returned false: " + definition.structure());
            }
        }
        for (PlacedMarker marker : plan.markers()) {
            if (!replaceMarker(level, plan, marker, selectedLevel, tier, instanceId, dungeonId,
                materializeEncounters, partySize))
                return rollback(level, plan, "marker replacement failed at " + marker.position() + " for " + marker.type());
        }
        for (Piece piece : plan.pieces()) for (BlockPos doorway : piece.doorways()) carveDoorway(level, doorway);
        if (containsMarkerBlock(level, plan.bounds()))
            return rollback(level, plan, "an undeclared marker block remained after post-processing");
        java.util.List<BlockPos> spawns = plan.markers().stream().filter(marker -> marker.type() == MarkerType.PLAYER_SPAWN)
            .map(PlacedMarker::position).toList();
        if (spawns.isEmpty()) spawns = java.util.List.of(plan.playerSpawn());
        for (BlockPos spawn : spawns) if (!safePlayerSpawn(level, spawn))
            return rollback(level, plan, "player spawn is obstructed or has no solid floor: " + spawn);
        return true;
    }

    private static boolean prevalidate(ServerLevel level, GeneratedDungeonPlan plan) {
        for (Piece piece : plan.pieces()) {
            ResourcePiece definition = definition(piece);
            if (definition == null) return false;
            if (definition.structure() != null && level.getStructureManager().get(definition.structure()).isEmpty()) return false;
        }
        return true;
    }

    private static boolean replaceMarker(ServerLevel level, GeneratedDungeonPlan plan, PlacedMarker marker,
                                         int selectedLevel, int tier, UUID instanceId,
                                         net.minecraft.resources.ResourceLocation dungeonId,
                                         boolean materializeEncounters, int partySize) {
        var state = level.getBlockState(marker.position());
        if (!(state.getBlock() instanceof DungeonMarkerBlock markerBlock)
            || !markerBlock.markerType().name().equals(marker.type().name())
            || state.getValue(DungeonMarkerBlock.FACING) != marker.facing()) return false;
        return switch (marker.type()) {
            case PLAYER_SPAWN, ROOM_CONNECTOR, BOSS_SPAWN, EXIT_PORTAL -> {
                level.setBlock(marker.position(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                yield true;
            }
            case SPAWNER -> {
                MobSpawnData authored = marker.spawn();
                if (level.getBlockEntity(marker.position()) instanceof SpawnerMarkerBlockEntity blockEntity) {
                    authored = authored.merge(blockEntity.data());
                }
                if (!materializeEncounters) {
                    level.setBlock(marker.position(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    yield true;
                }
                if (authored.empty() && marker.profile() != null) {
                    level.setBlock(marker.position(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    yield spawnEncounter(level, marker, selectedLevel, tier, instanceId);
                }
                yield spawnProfiled(level, plan, marker, authored, tier, instanceId, dungeonId, partySize);
            }
            case LOOT -> placeLoot(level, plan, marker, instanceId, dungeonId);
        };
    }

    private static boolean spawnEncounter(ServerLevel level, PlacedMarker marker, int selectedLevel, int tier,
                                          UUID instanceId) {
        if (marker.profile() == null) {
            DedicatedDungeonsMod.LOGGER.error("Spawner marker {} has no encounter profile", marker.position());
            return false;
        }
        EncounterPool encounter = DungeonContentRegistry.resolveEncounterPool(marker.profile()).orElse(null);
        if (encounter == null) {
            DedicatedDungeonsMod.LOGGER.error("Spawner marker {} references unavailable encounter pool {}",
                marker.position(), marker.profile());
            return false;
        }
        int groups = randomInclusive(level, encounter.minimumGroups(), encounter.maximumGroups());
        int spawned = 0;
        for (int group = 0; group < groups; group++) {
            var entry = DungeonContentRegistry.chooseMobEntry(encounter.mobPool(), selectedLevel, tier,
                level.random).orElse(null);
            if (entry == null) {
                DedicatedDungeonsMod.LOGGER.error(
                    "Encounter pool {} has no usable mob entry for selected level {} and tier {} (mob pool {})",
                    encounter.id(), selectedLevel, tier, encounter.mobPool());
                return false;
            }
            int count = randomInclusive(level, entry.minimumCount(), entry.maximumCount());
            for (int index = 0; index < count; index++) {
                Entity created = BuiltInRegistries.ENTITY_TYPE.getOptional(entry.entityType())
                    .map(type -> type.create(level)).orElse(null);
                if (!(created instanceof Mob mob)) {
                    DedicatedDungeonsMod.LOGGER.error("Encounter pool {} could not create mob entity {}",
                        encounter.id(), entry.entityType());
                    return false;
                }
                double angle = level.random.nextDouble() * Math.PI * 2.0;
                double distance = 1.5 + level.random.nextDouble() * 2.5;
                mob.setPos(marker.position().getX() + 0.5 + Math.cos(angle) * distance,
                    marker.position().getY(), marker.position().getZ() + 0.5 + Math.sin(angle) * distance);
                mob.setPersistenceRequired();
                mob.addTag(encounterTag(instanceId));
                DungeonMobConfigurator.prepareDungeon(level, mob, tier, entry.canEquip(),
                    entry.equipmentChance());
                double scale = Math.min(8.0, 1.0 + Math.max(0, selectedLevel) * 0.02 + Math.max(0, tier) * 0.15);
                var health = mob.getAttribute(Attributes.MAX_HEALTH);
                if (health != null) health.setBaseValue(health.getBaseValue() * scale);
                var damage = mob.getAttribute(Attributes.ATTACK_DAMAGE);
                if (damage != null) damage.setBaseValue(damage.getBaseValue() * scale);
                mob.setHealth(mob.getMaxHealth());
                if (!level.addFreshEntity(mob)) {
                    DedicatedDungeonsMod.LOGGER.error("Encounter pool {} could not add entity {} at {}",
                        encounter.id(), entry.entityType(), mob.blockPosition());
                    return false;
                }
                if (++spawned >= 128) return true;
            }
        }
        return true;
    }

    private static boolean spawnProfiled(ServerLevel level, GeneratedDungeonPlan plan, PlacedMarker marker,
                                         MobSpawnData authored, int tier, UUID instanceId,
                                         net.minecraft.resources.ResourceLocation dungeonId, int partySize) {
        Room room = marker.roomId() == null ? null : DungeonContentRegistry.room(marker.roomId()).orElse(null);
        DungeonDefinition dungeon = DungeonContentRegistry.dungeon(dungeonId).orElse(null);
        var theme = DungeonContentRegistry.resolveTheme(plan.themeId()).orElse(null);
        Map<SpawnRole, net.minecraft.resources.ResourceLocation> dungeonOverrides =
            new LinkedHashMap<>();
        if (dungeon != null) dungeon.pools().mobRoleOverrides().forEach((role, profile) ->
            dungeonOverrides.put(SpawnRole.parse(role), profile));
        DungeonMobResolver.Context context = new DungeonMobResolver.Context(
            dungeonId, plan.themeId(), marker.roomId(), marker.position(),
            room == null ? null : room.defaultMobRole(),
            room == null ? null : room.mobProfile(),
            room == null ? Map.of() : room.mobRoleOverrides(),
            dungeon == null ? null : dungeon.pools().mobProfile(),
            dungeonOverrides,
            theme == null ? null : theme.mobProfile(),
            tier, Math.max(1, partySize),
            room != null && room.tags().contains("arena"));
        int wave = authored.wave() == null ? 0 : authored.wave();
        String group = authored.group() == null ? marker.group() : authored.group();
        long seed = config(DungeonServerConfig.DETERMINISTIC_MOB_SELECTION, true)
            ? DungeonMobResolver.seed(level.getSeed(), instanceId, marker.roomId(), marker.position(), wave, group)
            : level.random.nextLong();
        DungeonMobResolver.Resolved resolved = DungeonMobResolver.resolve(authored, context, seed);
        if (resolved.mode() == SpawnMode.SPAWNER) {
            return placeSpawner(level, marker, resolved, instanceId);
        }
        if (resolved.mode() == SpawnMode.ROOM_ACTIVATION) {
            DedicatedDungeonsMod.LOGGER.warn(
                "ROOM_ACTIVATION is not available in the current room lifecycle; using DIRECT for dungeon={}, room={}, position={}",
                dungeonId, marker.roomId(), marker.position());
        }
        level.setBlock(marker.position(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        return spawnDirect(level, marker, resolved, tier, instanceId);
    }

    private static boolean spawnDirect(ServerLevel level, PlacedMarker marker,
                                       DungeonMobResolver.Resolved resolved, int tier, UUID instanceId) {
        String markerTag = markerTag(instanceId, marker.position());
        for (Entity entity : level.getAllEntities()) {
            if (entity.getTags().contains(markerTag)) return true;
        }
        var type = BuiltInRegistries.ENTITY_TYPE.getOptional(resolved.entity()).orElse(null);
        if (type == null) return false;
        int spawned = 0;
        for (int index = 0; index < resolved.count(); index++) {
            Entity created = type.create(level);
            if (!(created instanceof Mob mob)) return false;
            positionMob(level, mob, marker.position(), index, resolved.seed());
            DungeonMobConfigurator.prepareDungeon(level, mob, tier, resolved.canEquip(),
                resolved.equipmentChance());
            applySafeEntityNbt(mob, resolved.entityNbt());
            mob.addTag(encounterTag(instanceId));
            mob.addTag(markerTag);
            mob.setPersistenceRequired();
            if (!level.noCollision(mob)) {
                positionMob(level, mob, marker.position().above(), index, resolved.seed() ^ 0x434f4c4cL);
            }
            if (!level.addFreshEntity(mob)) return false;
            spawned++;
        }
        return spawned == resolved.count();
    }

    private static boolean placeSpawner(ServerLevel level, PlacedMarker marker,
                                        DungeonMobResolver.Resolved resolved, UUID instanceId) {
        level.setBlock(marker.position(), Blocks.SPAWNER.defaultBlockState(), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(marker.position()) instanceof SpawnerBlockEntity blockEntity)) return false;
        MobSpawnData settings = resolved.spawnerSettings();
        CompoundTag entity = safeEntityNbt(resolved.entityNbt());
        entity.putString("id", resolved.entity().toString());
        entity.putBoolean("PersistenceRequired", true);
        ListTag tags = entity.contains("Tags", net.minecraft.nbt.Tag.TAG_LIST)
            ? entity.getList("Tags", net.minecraft.nbt.Tag.TAG_STRING) : new ListTag();
        appendTag(tags, encounterTag(instanceId));
        appendTag(tags, markerTag(instanceId, marker.position()));
        entity.put("Tags", tags);

        CompoundTag spawnData = new CompoundTag();
        spawnData.put("entity", entity);
        CompoundTag spawner = new CompoundTag();
        spawner.put("SpawnData", spawnData);
        spawner.putShort("Delay", (short) bounded(settings.initialDelay(), 20, 0, 32_767));
        spawner.putShort("MinSpawnDelay", (short) bounded(settings.minimumDelay(), 200, 1, 32_767));
        spawner.putShort("MaxSpawnDelay", (short) bounded(settings.maximumDelay(), 800, 1, 32_767));
        spawner.putShort("SpawnCount", (short) bounded(settings.spawnCount(),
            Math.min(4, resolved.count()), 1, 128));
        spawner.putShort("MaxNearbyEntities", (short) bounded(settings.maximumNearbyEntities(),
            Math.max(6, resolved.count() * 2), 1, 256));
        spawner.putShort("RequiredPlayerRange", (short) bounded(settings.requiredPlayerRange(), 16, 1, 128));
        spawner.putShort("SpawnRange", (short) bounded(settings.spawnRange(), 4, 1, 32));
        blockEntity.getSpawner().load(level, marker.position(), spawner);
        blockEntity.setChanged();
        if (settings.totalSpawnLimit() != null) {
            DedicatedDungeonsMod.LOGGER.warn(
                "Vanilla SPAWNER at {} cannot persist total_spawn_limit={}; live count remains bounded and the spawner is removed when the run clears",
                marker.position(), settings.totalSpawnLimit());
        }
        return true;
    }

    private static void positionMob(ServerLevel level, Mob mob, BlockPos center, int index, long seed) {
        double angle = ((Math.floorMod(seed, 10_000L) / 10_000.0) * Math.PI * 2.0)
            + index * 2.399963229728653;
        double distance = index == 0 ? 0.0 : 1.25 + (index % 4) * 0.55;
        mob.setPos(center.getX() + 0.5 + Math.cos(angle) * distance,
            center.getY(), center.getZ() + 0.5 + Math.sin(angle) * distance);
    }

    private static void applySafeEntityNbt(Mob mob, CompoundTag source) {
        if (source == null || !config(DungeonServerConfig.ALLOW_DIRECT_ENTITY_NBT, true)) return;
        double x = mob.getX(), y = mob.getY(), z = mob.getZ();
        mob.load(safeEntityNbt(source));
        mob.setPos(x, y, z);
    }

    private static CompoundTag safeEntityNbt(CompoundTag source) {
        CompoundTag safe = source == null ? new CompoundTag() : source.copy();
        for (String key : java.util.List.of(
            "id", "UUID", "UUIDMost", "UUIDLeast", "Pos", "Motion", "Rotation", "Dimension",
            "Passengers", "Leash")) safe.remove(key);
        return safe;
    }

    private static void appendTag(ListTag tags, String value) {
        for (net.minecraft.nbt.Tag tag : tags) {
            if (tag.getAsString().equals(value)) return;
        }
        tags.add(StringTag.valueOf(value));
    }

    private static int bounded(Integer value, int fallback, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value == null ? fallback : value, maximum));
    }

    public static String markerTag(UUID instanceId, BlockPos position) {
        return "dd_marker_" + Long.toUnsignedString(instanceId.getMostSignificantBits()
            ^ instanceId.getLeastSignificantBits() ^ position.asLong(), 36);
    }

    private static int randomInclusive(ServerLevel level, int minimum, int maximum) {
        return minimum + level.random.nextInt(maximum - minimum + 1);
    }

    public static String encounterTag(UUID instanceId) {
        return "dd_encounter_" + instanceId;
    }

    private static boolean placeLoot(ServerLevel level, GeneratedDungeonPlan plan, PlacedMarker marker,
                                     UUID instanceId, net.minecraft.resources.ResourceLocation dungeonId) {
        LootMarkerData authored = marker.loot();
        if (level.getBlockEntity(marker.position()) instanceof LootMarkerBlockEntity blockEntity) {
            authored = authored.merge(blockEntity.data());
        }
        Room room = marker.roomId() == null ? null : DungeonContentRegistry.room(marker.roomId()).orElse(null);
        DungeonDefinition dungeon = DungeonContentRegistry.dungeon(dungeonId).orElse(null);
        var theme = DungeonContentRegistry.resolveTheme(plan.themeId()).orElse(null);
        DungeonLootResolver.Context context = new DungeonLootResolver.Context(
            dungeonId, plan.themeId(), marker.roomId(), marker.position(),
            room == null ? null : room.lootProfile(),
            room == null ? java.util.Map.of() : room.lootOverrides(),
            dungeon == null ? null : dungeon.pools().lootProfile(),
            dungeon == null ? java.util.Map.of() : dungeon.pools().lootOverrides(),
            theme == null ? null : theme.lootProfile());
        long seed = DungeonServerConfig.DETERMINISTIC_LOOT_SEEDS.get()
            ? DungeonLootResolver.seed(level.getSeed(), instanceId, marker.position(), dungeonId)
            : level.random.nextLong();
        DungeonLootResolver.Resolved resolved = DungeonLootResolver.resolve(authored, context, seed);

        level.setBlock(marker.position(), containerState(resolved.containerType(), marker.facing()), Block.UPDATE_ALL);
        if (!(level.getBlockEntity(marker.position()) instanceof RandomizableContainerBlockEntity container)) return false;
        assignLootTable(container, resolved.table(), resolved.seed());
        return true;
    }

    static void assignLootTable(RandomizableContainerBlockEntity container,
                                net.minecraft.resources.ResourceLocation table, long seed) {
        DeferredLootAssignment assignment = deferredLootAssignment(table, seed);
        container.setLootTable(assignment.table());
        container.setLootTableSeed(assignment.seed());
        container.setChanged();
    }

    static DeferredLootAssignment deferredLootAssignment(net.minecraft.resources.ResourceLocation table, long seed) {
        return new DeferredLootAssignment(ResourceKey.create(Registries.LOOT_TABLE, table), seed);
    }

    record DeferredLootAssignment(ResourceKey<LootTable> table, long seed) {}

    private static net.minecraft.world.level.block.state.BlockState containerState(LootContainerType type,
                                                                                   net.minecraft.core.Direction facing) {
        return switch (type) {
            case BARREL -> Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, facing);
            case TRAPPED_CHEST -> Blocks.TRAPPED_CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
            case CHEST -> Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing);
        };
    }

    private static boolean safePlayerSpawn(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
            && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
            && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty();
    }

    private static boolean containsMarkerBlock(ServerLevel level, WorldBounds bounds) {
        for (BlockPos pos : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(),
            bounds.maxX(), bounds.maxY(), bounds.maxZ())) {
            if (level.getBlockState(pos).getBlock() instanceof DungeonMarkerBlock) return true;
        }
        return false;
    }

    private static boolean rollback(ServerLevel level, GeneratedDungeonPlan plan, String reason) {
        DedicatedDungeonsMod.LOGGER.error("Rolling back dungeon plan {}: {}", plan.archetypeId(), reason);
        cleanup(level, plan);
        return false;
    }

    public static void cleanup(ServerLevel level, GeneratedDungeonPlan plan) {
        level.getEntities((Entity) null, toAabb(plan.bounds()), DungeonGenerationService::dungeonOwned)
            .forEach(Entity::discard);
        for (Piece piece : plan.pieces()) fillAir(level, piece.bounds());
        for (Piece piece : plan.pieces()) for (BlockPos doorway : piece.doorways())
            for (BlockPos pos : BlockPos.betweenClosed(doorway.offset(-1, 0, -1), doorway.offset(1, 3, 1)))
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    public static void forceChunks(ServerLevel level, WorldBounds bounds, boolean forced) {
        ChunkPos min = new ChunkPos(new BlockPos(bounds.minX() - 16, 0, bounds.minZ() - 16));
        ChunkPos max = new ChunkPos(new BlockPos(bounds.maxX() + 16, 0, bounds.maxZ() + 16));
        for (int x = min.x; x <= max.x; x++) for (int z = min.z; z <= max.z; z++) level.setChunkForced(x, z, forced);
    }

    private static void buildProcedural(ServerLevel level, WorldBounds bounds, Palette palette) {
        Block floor = BuiltInRegistries.BLOCK.get(palette.floor());
        Block wall = BuiltInRegistries.BLOCK.get(palette.wall());
        Block accent = BuiltInRegistries.BLOCK.get(palette.accent());
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) for (int z = bounds.minZ(); z <= bounds.maxZ(); z++)
            for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
                boolean floorOrCeiling = y == bounds.minY() || y == bounds.maxY();
                boolean edge = x == bounds.minX() || x == bounds.maxX() || z == bounds.minZ() || z == bounds.maxZ();
                Block block = floorOrCeiling ? floor : edge ? (((x * 31 + y * 17 + z) & 15) == 0 ? accent : wall) : Blocks.AIR;
                level.setBlock(new BlockPos(x, y, z), block.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
    }

    private static void carveDoorway(ServerLevel level, BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, 0, -1), center.offset(1, 2, 1)))
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static void fillAir(ServerLevel level, WorldBounds bounds) {
        for (BlockPos pos : BlockPos.betweenClosed(bounds.minX(), bounds.minY(), bounds.minZ(),
            bounds.maxX(), bounds.maxY(), bounds.maxZ()))
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
    }

    private static AABB toAabb(WorldBounds bounds) {
        return new AABB(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX() + 1, bounds.maxY() + 1, bounds.maxZ() + 1);
    }

    private static boolean dungeonOwned(Entity entity) {
        if (entity instanceof Player) return false;
        return entity.getTags().stream().anyMatch(tag -> tag.startsWith("dd_encounter_")
            || tag.startsWith("dd_instance_") || tag.startsWith("dd_survival_")
            || tag.startsWith("dd_marker_") || tag.startsWith("dd_exit_"));
    }

    private static <T> T config(net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<T> value, T fallback) {
        try { return value.get(); }
        catch (IllegalStateException exception) { return fallback; }
    }

    private static ResourcePiece definition(Piece piece) {
        if (piece.type() == PieceType.ROOM) return DungeonContentRegistry.room(piece.definitionId())
            .map(value -> new ResourcePiece(value.structure(), value.palette())).orElse(null);
        return DungeonContentRegistry.connector(piece.definitionId())
            .map(value -> new ResourcePiece(value.structure(), value.palette())).orElse(null);
    }

    private record ResourcePiece(net.minecraft.resources.ResourceLocation structure, Palette palette) {}
}

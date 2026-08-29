package dev.uapi.dungeons.generation;

import dev.uapi.difficulty.DifficultyRank;
import dev.uapi.dungeons.config.DungeonServerConfig;
import dev.uapi.dungeons.content.DungeonContentRegistry;
import dev.uapi.dungeons.content.DungeonContentRegistry.WeightedValue;
import dev.uapi.dungeons.content.DungeonContentTypes;
import dev.uapi.dungeons.content.DungeonContentTypes.Archetype;
import dev.uapi.dungeons.content.DungeonContentTypes.Boss;
import dev.uapi.dungeons.content.DungeonContentTypes.ConnectionPoint;
import dev.uapi.dungeons.content.DungeonContentTypes.Connector;
import dev.uapi.dungeons.content.DungeonContentTypes.MarkerType;
import dev.uapi.dungeons.content.DungeonContentTypes.Room;
import dev.uapi.dungeons.content.DungeonContentTypes.RoomPool;
import dev.uapi.dungeons.content.DungeonDefinition;
import dev.uapi.dungeons.generation.GeneratedDungeonPlan.Piece;
import dev.uapi.dungeons.generation.GeneratedDungeonPlan.PieceType;
import dev.uapi.dungeons.generation.GeneratedDungeonPlan.PlacedMarker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Rotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Builds an isolated room graph without touching the world. Failed attempts are safe to discard. */
public final class DungeonGraphPlanner {
    public record Result(GeneratedDungeonPlan plan, String error) {
        public boolean successful() { return plan != null; }
        static Result success(GeneratedDungeonPlan plan) { return new Result(plan, ""); }
        static Result failure(String error) { return new Result(null, error); }
    }

    private record OpenPort(BlockPos position, Direction facing, String type) {}
    private record Placement(Piece piece, List<OpenPort> ports) {}

    private DungeonGraphPlanner() {}

    public static Result plan(ResourceLocation requestedArchetype, DifficultyRank difficulty,
                              BlockPos slotCenter, RandomSource random) {
        Archetype archetype = DungeonContentRegistry.resolveArchetype(requestedArchetype).orElse(null);
        if (archetype == null) return Result.failure("missing_or_unavailable_archetype:" + requestedArchetype);
        DungeonDefinition.Rules rules = new DungeonDefinition.Rules(archetype.minMainRooms() + 2,
            archetype.maxMainRooms() + 2, 0, Integer.MAX_VALUE, 64,
            DungeonServerConfig.GENERATION_ATTEMPTS.get(), "boss", 30);
        return planResolved(archetype, DungeonContentRegistry.availableBosses(archetype, difficulty), difficulty,
            slotCenter, random, rules, DungeonServerConfig.GENERATION_ATTEMPTS.get());
    }

    public static Result plan(DungeonDefinition dungeon, DifficultyRank difficulty,
                              BlockPos slotCenter, RandomSource random) {
        return plan(dungeon, difficulty, slotCenter, random, null);
    }

    public static Result plan(DungeonDefinition dungeon, DifficultyRank difficulty,
                              BlockPos slotCenter, RandomSource random, ResourceLocation excludedBoss) {
        Archetype archetype = DungeonContentRegistry.resolveArchetype(dungeon.archetype()).orElse(null);
        if (archetype == null) return Result.failure("missing_or_unavailable_archetype:" + dungeon.archetype());
        if (DungeonContentRegistry.resolveBossPool(dungeon.pools().bossPool()).isEmpty())
            return Result.failure("missing_or_unavailable_boss_pool:" + dungeon.pools().bossPool());
        int attempts = Math.min(DungeonServerConfig.GENERATION_ATTEMPTS.get(), dungeon.rules().generationAttempts());
        List<WeightedValue<Boss>> bosses = withoutImmediateRepeat(DungeonContentRegistry.availableBosses(
            dungeon.pools().bossPool(), difficulty), excludedBoss);
        return planResolved(archetype, bosses, difficulty,
            slotCenter, random, dungeon.rules(), attempts);
    }

    static List<WeightedValue<Boss>> withoutImmediateRepeat(List<WeightedValue<Boss>> bosses,
                                                            ResourceLocation excludedBoss) {
        if (excludedBoss == null || bosses.size() < 2) return bosses;
        List<WeightedValue<Boss>> filtered = bosses.stream()
            .filter(value -> !value.value().id().equals(excludedBoss)).toList();
        return filtered.isEmpty() ? bosses : filtered;
    }

    private static Result planResolved(Archetype archetype, List<WeightedValue<Boss>> bosses,
                                       DifficultyRank difficulty, BlockPos slotCenter,
                                       RandomSource random, DungeonDefinition.Rules rules, int attempts) {
        if (!archetype.difficulties().contains(difficulty)) return Result.failure("difficulty_not_supported:" + difficulty);
        RoomPool pool = DungeonContentRegistry.resolveRoomPool(archetype.roomPool()).orElse(null);
        if (pool == null) return Result.failure("missing_or_unavailable_room_pool:" + archetype.roomPool());
        List<WeightedValue<Room>> allRooms = DungeonContentRegistry.availableRooms(pool);
        List<WeightedValue<Room>> entrances = tagged(allRooms, "entrance");
        List<WeightedValue<Room>> normal = tagged(allRooms, "normal");
        List<WeightedValue<Room>> bossRooms = tagged(allRooms, "boss");
        List<WeightedValue<Room>> deadEnds = tagged(allRooms, "dead_end");
        List<WeightedValue<Connector>> connectors = DungeonContentRegistry.availableConnectors(archetype);
        if (entrances.isEmpty() || normal.isEmpty() || bossRooms.isEmpty() || connectors.isEmpty() || bosses.isEmpty())
            return Result.failure("archetype_has_no_usable_required_pieces:" + archetype.id());

        String lastError = "no_attempt";
        for (int attempt = 0; attempt < attempts; attempt++) {
            Boss boss = DungeonContentRegistry.choose(bosses, random).orElseThrow();
            List<WeightedValue<Room>> fittingBossRooms = bossRooms.stream()
                .filter(value -> boss.roomSizes().contains(value.value().size())).toList();
            if (fittingBossRooms.isEmpty()) { lastError = "no_boss_room_fits:" + boss.id(); continue; }
            Result result = attempt(archetype, entrances, normal, fittingBossRooms, deadEnds, connectors,
                boss, slotCenter, random, rules);
            if (result.successful()) return result;
            lastError = result.error();
        }
        return Result.failure("generation_attempts_exhausted:" + lastError);
    }

    private static Result attempt(Archetype archetype, List<WeightedValue<Room>> entrances,
                                  List<WeightedValue<Room>> normal, List<WeightedValue<Room>> bossRooms,
                                  List<WeightedValue<Room>> deadEnds, List<WeightedValue<Connector>> connectors,
                                  Boss boss, BlockPos center, RandomSource random, DungeonDefinition.Rules rules) {
        List<Piece> pieces = new ArrayList<>();
        List<OpenPort> branchPorts = new ArrayList<>();
        Room entrance = DungeonContentRegistry.choose(entrances, random).orElseThrow();
        Placement entrancePlacement = initial(entrance, center);
        pieces.add(entrancePlacement.piece());
        OpenPort current = choosePort(entrancePlacement.ports(), random, null);
        addOtherPorts(branchPorts, entrancePlacement.ports(), current);
        BlockPos playerSpawn = marker(entrance, MarkerType.PLAYER_SPAWN, entrancePlacement.piece(), center);

        int minimumMainRooms = Math.max(archetype.minMainRooms(),
            Math.max(rules.minimumRooms() - 2, rules.minimumDepth() - 1));
        int maximumMainRooms = Math.min(archetype.maxMainRooms(),
            Math.min(rules.maximumRooms() - 2, rules.maximumDepth() - 1));
        if (maximumMainRooms < minimumMainRooms) return Result.failure("dungeon_graph_constraints_have_no_solution");
        int mainRooms = minimumMainRooms + random.nextInt(maximumMainRooms - minimumMainRooms + 1);
        if (3L + mainRooms * 2L > DungeonServerConfig.MAX_GENERATED_PIECES.get()) return Result.failure("piece_limit");
        for (int index = 0; index < mainRooms; index++) {
            Placement corridor = attachConnector(current, DungeonContentRegistry.choose(connectors, random).orElseThrow(), pieces, random);
            if (corridor == null) return Result.failure("connector_collision");
            pieces.add(corridor.piece());
            current = corridor.ports().getFirst();
            Room room = DungeonContentRegistry.choose(normal, random).orElseThrow();
            Placement placedRoom = attachRoom(current, room, pieces, random);
            if (placedRoom == null) return Result.failure("main_room_collision");
            pieces.add(placedRoom.piece());
            current = choosePort(placedRoom.ports(), random, current.facing());
            if (current == null) return Result.failure("main_room_has_no_exit");
            addOtherPorts(branchPorts, placedRoom.ports(), current);
        }

        Placement finalCorridor = attachConnector(current, DungeonContentRegistry.choose(connectors, random).orElseThrow(), pieces, random);
        if (finalCorridor == null) return Result.failure("boss_connector_collision");
        pieces.add(finalCorridor.piece()); current = finalCorridor.ports().getFirst();
        Room bossRoom = DungeonContentRegistry.choose(bossRooms, random).orElseThrow();
        Placement placedBossRoom = attachRoom(current, bossRoom, pieces, random);
        if (placedBossRoom == null) return Result.failure("boss_room_collision");
        pieces.add(placedBossRoom.piece());

        Collections.shuffle(branchPorts, new java.util.Random(random.nextLong()));
        int maximumBranches = Math.min(rules.branches(), Math.max(0, rules.maximumRooms() - mainRooms - 2));
        int branches = 0;
        for (OpenPort branch : branchPorts) {
            if (branches >= maximumBranches) break;
            if (deadEnds.isEmpty() || random.nextDouble() > archetype.deadEndChance()) continue;
            if (pieces.size() + 2 > DungeonServerConfig.MAX_GENERATED_PIECES.get()) break;
            Placement branchConnector = attachConnector(branch, DungeonContentRegistry.choose(connectors, random).orElseThrow(), pieces, random);
            if (branchConnector == null) continue;
            Room deadEnd = DungeonContentRegistry.choose(deadEnds, random).orElseThrow();
            List<Piece> withConnector = new ArrayList<>(pieces); withConnector.add(branchConnector.piece());
            Placement branchRoom = attachRoom(branchConnector.ports().getFirst(), deadEnd, withConnector, random);
            if (branchRoom != null) {
                pieces.add(branchConnector.piece());
                pieces.add(branchRoom.piece());
                branches++;
            }
        }

        if (pieces.size() > DungeonServerConfig.MAX_GENERATED_PIECES.get()) return Result.failure("piece_limit");
        WorldBounds bounds = pieces.stream().map(Piece::bounds).reduce(WorldBounds::union).orElseThrow().inflate(8, 4);
        int maxDiameter = DungeonServerConfig.MAX_DUNGEON_DIAMETER.get();
        int slotRadius = DungeonServerConfig.INSTANCE_SLOT_SPACING.get() / 2 - 16;
        if (bounds.sizeX() > maxDiameter || bounds.sizeZ() > maxDiameter) return Result.failure("diameter_limit");
        if (bounds.minX() < center.getX() - slotRadius || bounds.maxX() > center.getX() + slotRadius
            || bounds.minZ() < center.getZ() - slotRadius || bounds.maxZ() > center.getZ() + slotRadius)
            return Result.failure("slot_boundary");
        BlockPos bossSpawn = marker(bossRoom, MarkerType.BOSS_SPAWN, placedBossRoom.piece(),
            placedBossRoom.piece().origin());
        BlockPos exit = marker(bossRoom, MarkerType.EXIT_PORTAL, placedBossRoom.piece(), bossSpawn.offset(0, 0, 4));
        return Result.success(new GeneratedDungeonPlan(archetype.theme(), archetype.id(), boss.id(), boss.entityType(),
            boss.health(), boss.damage(), boss.rewardPool(),
            pieces, bounds, playerSpawn, bossSpawn, exit, placedMarkers(pieces)));
    }

    private static Placement initial(Room room, BlockPos center) {
        BlockPos anchor = room.markers(MarkerType.PLAYER_SPAWN).stream().findFirst().orElse(BlockPos.ZERO);
        BlockPos origin = center.subtract(anchor);
        Piece piece = piece(PieceType.ROOM, room.id(), origin, Rotation.NONE, room.bounds(), room.connectors());
        return new Placement(piece, ports(room.connectors(), origin, Rotation.NONE));
    }

    private static Placement attachRoom(OpenPort source, Room room, List<Piece> existing, RandomSource random) {
        return attach(PieceType.ROOM, room.id(), room.bounds(), room.connectors(), source, existing, random);
    }

    private static Placement attachConnector(OpenPort source, Connector connector, List<Piece> existing, RandomSource random) {
        Placement placement = attach(PieceType.CONNECTOR, connector.id(), connector.bounds(), connector.points(), source, existing, random);
        if (placement == null) return null;
        List<OpenPort> outputs = placement.ports().stream().filter(port -> !port.position().equals(source.position().relative(source.facing()))).toList();
        return outputs.isEmpty() ? null : new Placement(placement.piece(), outputs);
    }

    private static Placement attach(PieceType type, ResourceLocation id, DungeonContentTypes.Bounds localBounds,
                                    List<ConnectionPoint> points, OpenPort source, List<Piece> existing,
                                    RandomSource random) {
        List<ConnectionPoint> shuffled = new ArrayList<>(points);
        Collections.shuffle(shuffled, new java.util.Random(random.nextLong()));
        Rotation[] rotations = Rotation.values();
        int offset = random.nextInt(rotations.length);
        for (ConnectionPoint point : shuffled) for (int index = 0; index < rotations.length; index++) {
            Rotation rotation = rotations[(index + offset) % rotations.length];
            if (!point.type().equals(source.type()) || rotation.rotate(point.facing()) != source.facing().getOpposite()) continue;
            BlockPos target = source.position().relative(source.facing());
            BlockPos origin = target.subtract(rotate(point.position(), rotation));
            Piece piece = piece(type, id, origin, rotation, localBounds, points);
            if (existing.stream().anyMatch(value -> value.bounds().intersects(piece.bounds()))) continue;
            List<OpenPort> open = ports(points, origin, rotation).stream()
                .filter(value -> !value.position().equals(target)).toList();
            return new Placement(piece, open);
        }
        return null;
    }

    private static Piece piece(PieceType type, ResourceLocation id, BlockPos origin, Rotation rotation,
                               DungeonContentTypes.Bounds local, List<ConnectionPoint> points) {
        WorldBounds bounds = transformBounds(local, origin, rotation);
        List<BlockPos> doors = points.stream().map(point -> origin.offset(rotate(point.position(), rotation))).toList();
        return new Piece(type, id, origin.immutable(), rotation, bounds, doors);
    }

    private static List<OpenPort> ports(List<ConnectionPoint> points, BlockPos origin, Rotation rotation) {
        return points.stream().map(point -> new OpenPort(origin.offset(rotate(point.position(), rotation)),
            rotation.rotate(point.facing()), point.type())).toList();
    }

    private static OpenPort choosePort(List<OpenPort> ports, RandomSource random, Direction incoming) {
        List<OpenPort> choices = ports.stream().filter(port -> incoming == null || port.facing() != incoming.getOpposite()).toList();
        return choices.isEmpty() ? null : choices.get(random.nextInt(choices.size()));
    }

    private static void addOtherPorts(List<OpenPort> target, List<OpenPort> all, OpenPort used) {
        all.stream().filter(port -> used == null || !port.equals(used)).forEach(target::add);
    }

    private static BlockPos marker(Room room, MarkerType type, Piece piece, BlockPos fallback) {
        return room.markers(type).stream().findFirst()
            .map(pos -> piece.origin().offset(rotate(pos, piece.rotation()))).orElse(fallback).immutable();
    }

    private static List<WeightedValue<Room>> tagged(List<WeightedValue<Room>> rooms, String tag) {
        return rooms.stream().filter(value -> value.value().tags().contains(tag)).toList();
    }

    private static List<PlacedMarker> placedMarkers(List<Piece> pieces) {
        List<PlacedMarker> result = new ArrayList<>();
        for (Piece piece : pieces) {
            if (piece.type() != PieceType.ROOM) continue;
            Room room = DungeonContentRegistry.room(piece.definitionId()).orElse(null);
            if (room == null) continue;
            room.markerDefinitions().forEach((type, markers) -> markers.forEach(marker -> result.add(new PlacedMarker(
                type, piece.origin().offset(rotate(marker.position(), piece.rotation())),
                piece.rotation().rotate(marker.facing()), marker.profile(), marker.group(), marker.order(),
                room.id(), marker.loot(), marker.spawn()))));
        }
        return List.copyOf(result);
    }

    public static BlockPos rotate(BlockPos pos, Rotation rotation) {
        return switch (rotation) {
            case NONE -> pos;
            case CLOCKWISE_90 -> new BlockPos(-pos.getZ(), pos.getY(), pos.getX());
            case CLOCKWISE_180 -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
            case COUNTERCLOCKWISE_90 -> new BlockPos(pos.getZ(), pos.getY(), -pos.getX());
        };
    }

    private static WorldBounds transformBounds(DungeonContentTypes.Bounds bounds, BlockPos origin, Rotation rotation) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int x : new int[]{bounds.minX(), bounds.maxX()})
            for (int y : new int[]{bounds.minY(), bounds.maxY()})
                for (int z : new int[]{bounds.minZ(), bounds.maxZ()}) {
                    BlockPos value = origin.offset(rotate(new BlockPos(x, y, z), rotation));
                    minX = Math.min(minX, value.getX()); minY = Math.min(minY, value.getY()); minZ = Math.min(minZ, value.getZ());
                    maxX = Math.max(maxX, value.getX()); maxY = Math.max(maxY, value.getY()); maxZ = Math.max(maxZ, value.getZ());
                }
        return new WorldBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }
}

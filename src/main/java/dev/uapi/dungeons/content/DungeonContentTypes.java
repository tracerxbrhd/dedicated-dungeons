package dev.uapi.dungeons.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.uapi.difficulty.DifficultyRank;
import dev.uapi.integration.IntegrationService;
import dev.uapi.dungeons.loot.LootMarkerData;
import dev.uapi.dungeons.loot.LootRole;
import dev.uapi.dungeons.mob.MobSpawnData;
import dev.uapi.dungeons.mob.SpawnRole;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** JSON-facing value types used by third-party dungeon theme packs. */
public final class DungeonContentTypes {
    private DungeonContentTypes() {}

    public enum RoomSize { SMALL, MEDIUM, LARGE, BOSS, ARENA }
    public enum MarkerType { PLAYER_SPAWN, ROOM_CONNECTOR, SPAWNER, LOOT, BOSS_SPAWN, EXIT_PORTAL }

    public record Marker(BlockPos position, Direction facing, ResourceLocation profile, String group, int order,
                         LootMarkerData loot, MobSpawnData spawn) {
        public Marker {
            loot = loot == null ? LootMarkerData.EMPTY : loot;
            spawn = spawn == null ? MobSpawnData.EMPTY : spawn;
        }
        public Marker(BlockPos position, Direction facing, ResourceLocation profile, String group, int order) {
            this(position, facing, profile, group, order, LootMarkerData.EMPTY, MobSpawnData.EMPTY);
        }
        public Marker(BlockPos position, Direction facing, ResourceLocation profile, String group, int order,
                      LootMarkerData loot) {
            this(position, facing, profile, group, order, loot, MobSpawnData.EMPTY);
        }
    }

    public record Requirements(List<String> requiredMods) {
        public Requirements {
            for (String modId : requiredMods) {
                if (!modId.matches("[a-z][a-z0-9_]{1,63}"))
                    throw new IllegalArgumentException("invalid required mod id: " + modId);
            }
            requiredMods = List.copyOf(requiredMods);
        }
        public boolean available() { return IntegrationService.areLoaded(requiredMods); }
        static Requirements parse(JsonObject json) {
            return new Requirements(strings(json, "required_mods"));
        }
    }

    /** Inclusive local block bounds. */
    public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public Bounds {
            if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("bounds min must not exceed max");
            long x = (long) maxX - minX + 1, y = (long) maxY - minY + 1, z = (long) maxZ - minZ + 1;
            if (x > 2048 || y > 512 || z > 2048) throw new IllegalArgumentException("bounds exceed safe limits (2048 x 512 x 2048)");
        }
        public int sizeX() { return maxX - minX + 1; }
        public int sizeY() { return maxY - minY + 1; }
        public int sizeZ() { return maxZ - minZ + 1; }
        static Bounds parse(JsonObject json) {
            JsonArray min = requiredArray(json, "min");
            JsonArray max = requiredArray(json, "max");
            requireVec(min, "bounds.min"); requireVec(max, "bounds.max");
            return new Bounds(min.get(0).getAsInt(), min.get(1).getAsInt(), min.get(2).getAsInt(),
                max.get(0).getAsInt(), max.get(1).getAsInt(), max.get(2).getAsInt());
        }
    }

    public record ConnectionPoint(String name, BlockPos position, Direction facing, String type) {
        static ConnectionPoint parse(JsonObject json) {
            String name = requiredString(json, "name");
            BlockPos position = blockPos(requiredArray(json, "pos"), "connector.pos");
            Direction facing = Direction.byName(requiredString(json, "facing").toLowerCase(Locale.ROOT));
            if (facing == null || facing.getAxis().isVertical()) throw new IllegalArgumentException("connector facing must be horizontal");
            return new ConnectionPoint(name, position, facing, optionalString(json, "type", "default"));
        }
    }

    public record Palette(ResourceLocation floor, ResourceLocation wall, ResourceLocation accent) {
        static Palette parse(JsonObject owner) {
            JsonObject json = owner.has("palette") ? owner.getAsJsonObject("palette") : new JsonObject();
            return new Palette(optionalId(json, "floor", "minecraft:deepslate_tiles"),
                optionalId(json, "wall", "minecraft:polished_blackstone_bricks"),
                optionalId(json, "accent", "minecraft:crying_obsidian"));
        }
    }

    public record Room(ResourceLocation id, ResourceLocation theme, ResourceLocation structure, RoomSize size,
                       Bounds bounds, List<ConnectionPoint> connectors,
                       Map<MarkerType, List<Marker>> markerDefinitions, Set<String> tags,
                       int weight, Palette palette, Requirements requirements,
                       ResourceLocation lootProfile, Map<LootRole, ResourceLocation> lootOverrides,
                       ResourceLocation mobProfile, Map<SpawnRole, ResourceLocation> mobRoleOverrides,
                       SpawnRole defaultMobRole) {
        public Room {
            connectors = List.copyOf(connectors); tags = Set.copyOf(tags);
            EnumMap<MarkerType, List<Marker>> markerCopy = new EnumMap<>(MarkerType.class);
            markerDefinitions.forEach((key, value) -> markerCopy.put(key, List.copyOf(value)));
            markerDefinitions = Map.copyOf(markerCopy);
            lootOverrides = Map.copyOf(lootOverrides);
            mobRoleOverrides = Map.copyOf(mobRoleOverrides);
        }
        public boolean available() { return requirements.available(); }
        public List<BlockPos> markers(MarkerType type) {
            return markerDefinitions.getOrDefault(type, List.of()).stream().map(Marker::position).toList();
        }
        public List<Marker> markerDefinitions(MarkerType type) {
            return markerDefinitions.getOrDefault(type, List.of());
        }

        public static Room parse(ResourceLocation id, JsonObject json) {
            requireFormat(json);
            List<ConnectionPoint> connectors = new ArrayList<>();
            for (JsonElement element : requiredArray(json, "connectors")) connectors.add(ConnectionPoint.parse(element.getAsJsonObject()));
            Set<String> tags = new LinkedHashSet<>(strings(json, "tags"));
            if (connectors.isEmpty() && !tags.contains("arena"))
                throw new IllegalArgumentException("non-arena room requires at least one connector");
            return new Room(id, requiredId(json, "theme"), nullableId(json, "structure"),
                enumValue(RoomSize.class, optionalString(json, "size", "medium"), "room size"),
                Bounds.parse(requiredObject(json, "bounds")), connectors, markerMap(json),
                tags, positive(json, "weight", 1), Palette.parse(json), Requirements.parse(json),
                nullableId(json, "loot_profile"), DungeonContentTypes.lootOverrides(json),
                nullableId(json, "mob_profile"), DungeonContentTypes.mobRoleOverrides(json),
                json.has("default_mob_role")
                    ? SpawnRole.parse(json.get("default_mob_role").getAsString()) : null);
        }
    }

    public record Connector(ResourceLocation id, ResourceLocation structure, Bounds bounds,
                            List<ConnectionPoint> points, int weight, Palette palette, Requirements requirements) {
        public Connector { points = List.copyOf(points); }
        public boolean available() { return requirements.available(); }
        public static Connector parse(ResourceLocation id, JsonObject json) {
            requireFormat(json);
            List<ConnectionPoint> points = new ArrayList<>();
            for (JsonElement element : requiredArray(json, "connectors")) points.add(ConnectionPoint.parse(element.getAsJsonObject()));
            if (points.size() != 2) throw new IllegalArgumentException("connector piece must have exactly two connectors");
            return new Connector(id, nullableId(json, "structure"), Bounds.parse(requiredObject(json, "bounds")),
                points, positive(json, "weight", 1), Palette.parse(json), Requirements.parse(json));
        }
    }

    public record WeightedId(ResourceLocation id, int weight) {
        static WeightedId parse(JsonElement element, String field) {
            if (element.isJsonPrimitive()) return new WeightedId(parseId(element.getAsString(), field), 1);
            JsonObject json = element.getAsJsonObject();
            return new WeightedId(requiredId(json, field), positive(json, "weight", 1));
        }
    }

    public record RoomPool(ResourceLocation id, ResourceLocation theme, List<WeightedId> rooms,
                           ResourceLocation fallback, Requirements requirements) {
        public RoomPool { rooms = List.copyOf(rooms); }
        public boolean available() { return requirements.available(); }
        public static RoomPool parse(ResourceLocation id, JsonObject json) {
            requireFormat(json);
            List<WeightedId> rooms = new ArrayList<>();
            for (JsonElement element : requiredArray(json, "rooms")) rooms.add(WeightedId.parse(element, "room"));
            if (rooms.isEmpty()) throw new IllegalArgumentException("room pool must not be empty");
            return new RoomPool(id, requiredId(json, "theme"), rooms, nullableId(json, "fallback"), Requirements.parse(json));
        }
    }

    public record Boss(ResourceLocation id, ResourceLocation entityType, Set<RoomSize> roomSizes,
                       double health, double damage, ResourceLocation rewardPool,
                       int weight, int minimumTier, int maximumTier, Requirements requirements) {
        public Boss { roomSizes = Set.copyOf(roomSizes); }
        public boolean available() { return requirements.available(); }
        public boolean supports(DifficultyRank difficulty) {
            int tier = difficulty.ordinal();
            return tier >= minimumTier && tier <= maximumTier;
        }
        public static Boss parse(ResourceLocation id, JsonObject json) {
            requireFormat(json);
            Set<RoomSize> sizes = new LinkedHashSet<>();
            for (String value : strings(json, "room_sizes")) sizes.add(enumValue(RoomSize.class, value, "boss room size"));
            if (sizes.isEmpty()) sizes.add(RoomSize.BOSS);
            int minimumTier = nonNegative(json, "minimum_tier", 0);
            int maximumTier = nonNegative(json, "maximum_tier", DifficultyRank.values().length - 1);
            if (maximumTier < minimumTier) throw new IllegalArgumentException("boss maximum_tier must not be below minimum_tier");
            return new Boss(id, requiredId(json, "entity"), sizes,
                positiveDouble(json, "base_health", 80), positiveDouble(json, "base_damage", 8),
                requiredId(json, "reward_pool"), positive(json, "weight", 1),
                minimumTier, maximumTier, Requirements.parse(json));
        }
    }

    public record Archetype(ResourceLocation id, ResourceLocation theme, ResourceLocation roomPool,
                            List<WeightedId> connectors, List<WeightedId> bosses,
                            int minMainRooms, int maxMainRooms, double deadEndChance,
                            Set<DifficultyRank> difficulties, ResourceLocation fallback,
                            Requirements requirements) {
        public Archetype {
            connectors = List.copyOf(connectors); bosses = List.copyOf(bosses); difficulties = Set.copyOf(difficulties);
            if (minMainRooms < 1 || maxMainRooms < minMainRooms) throw new IllegalArgumentException("invalid main_path range");
            if (maxMainRooms > 512) throw new IllegalArgumentException("main_path.max must not exceed 512");
            if (deadEndChance < 0 || deadEndChance > 1) throw new IllegalArgumentException("dead_end_chance must be in [0,1]");
        }
        public boolean available() { return requirements.available(); }
        public static Archetype parse(ResourceLocation id, JsonObject json) {
            requireFormat(json);
            JsonObject path = requiredObject(json, "main_path");
            List<WeightedId> connectors = weighted(json, "connectors", "connector");
            List<WeightedId> bosses = weighted(json, "bosses", "boss");
            if (connectors.isEmpty() || bosses.isEmpty()) throw new IllegalArgumentException("archetype requires connectors and bosses");
            Set<DifficultyRank> difficulties = new LinkedHashSet<>();
            for (String value : strings(json, "difficulties"))
                difficulties.add(enumValue(DifficultyRank.class, value, "difficulty"));
            if (difficulties.isEmpty()) difficulties.addAll(List.of(DifficultyRank.values()));
            return new Archetype(id, requiredId(json, "theme"), requiredId(json, "room_pool"), connectors, bosses,
                positive(path, "min", 3), positive(path, "max", 5), optionalDouble(json, "dead_end_chance", 0.25),
                difficulties, nullableId(json, "fallback"), Requirements.parse(json));
        }
    }

    public record Theme(ResourceLocation id, String translationKey, List<WeightedId> archetypes,
                        ResourceLocation fallback, Requirements requirements, ResourceLocation lootProfile,
                        ResourceLocation mobProfile) {
        public Theme { archetypes = List.copyOf(archetypes); }
        public boolean available() { return requirements.available(); }
        public static Theme parse(ResourceLocation id, JsonObject json) {
            requireFormat(json);
            List<WeightedId> archetypes = weighted(json, "archetypes", "archetype");
            if (archetypes.isEmpty()) throw new IllegalArgumentException("theme requires at least one archetype");
            return new Theme(id, optionalString(json, "translation_key", "dungeon_theme." + id.getNamespace() + "." + id.getPath()),
                archetypes, nullableId(json, "fallback"), Requirements.parse(json),
                nullableId(json, "loot_profile"), nullableId(json, "mob_profile"));
        }
    }

    private static List<WeightedId> weighted(JsonObject json, String arrayName, String idField) {
        List<WeightedId> result = new ArrayList<>();
        JsonArray array = optionalArray(json, arrayName);
        for (JsonElement element : array) result.add(WeightedId.parse(element, idField));
        return result;
    }

    private static void requireFormat(JsonObject json) {
        if (!json.has("format_version") || !json.get("format_version").isJsonPrimitive())
            throw new IllegalArgumentException("missing integer 'format_version'");
        int value = json.get("format_version").getAsInt();
        if (value != DungeonDefinition.CURRENT_FORMAT)
            throw new IllegalArgumentException(value > DungeonDefinition.CURRENT_FORMAT
                ? "unsupported newer format_version " + value
                : "unsupported legacy format_version " + value);
    }

    private static Map<MarkerType, List<Marker>> markerMap(JsonObject json) {
        Map<MarkerType, List<Marker>> result = new EnumMap<>(MarkerType.class);
        if (!json.has("markers")) return result;
        JsonObject markers = json.getAsJsonObject("markers");
        for (Map.Entry<String, JsonElement> entry : markers.entrySet()) {
            MarkerType type = markerType(entry.getKey());
            List<Marker> definitions = new ArrayList<>();
            for (JsonElement element : entry.getValue().getAsJsonArray()) {
                if (element.isJsonArray()) {
                    definitions.add(new Marker(blockPos(element.getAsJsonArray(), "marker"), Direction.NORTH,
                        null, "default", definitions.size()));
                } else {
                    JsonObject value = element.getAsJsonObject();
                    Direction facing = Direction.byName(optionalString(value, "facing", "north").toLowerCase(Locale.ROOT));
                    if (facing == null || facing.getAxis().isVertical())
                        throw new IllegalArgumentException("marker facing must be horizontal");
                    ResourceLocation legacyProfile = nullableId(value, "profile");
                    LootMarkerData loot = type == MarkerType.LOOT
                        ? LootMarkerData.fromJson(value, legacyProfile) : LootMarkerData.EMPTY;
                    MobSpawnData spawn = type == MarkerType.SPAWNER
                        ? MobSpawnData.fromJson(value) : MobSpawnData.EMPTY;
                    definitions.add(new Marker(blockPos(requiredArray(value, "pos"), "marker.pos"), facing,
                        legacyProfile, optionalString(value, "group", "default"),
                        nonNegative(value, "order", definitions.size()), loot, spawn));
                }
            }
            result.put(type, definitions);
        }
        return result;
    }

    private static MarkerType markerType(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "player_spawn" -> MarkerType.PLAYER_SPAWN;
            case "room_connector", "connector" -> MarkerType.ROOM_CONNECTOR;
            case "spawner", "mob_spawn" -> MarkerType.SPAWNER;
            case "loot", "loot_spawn" -> MarkerType.LOOT;
            case "boss_spawn" -> MarkerType.BOSS_SPAWN;
            case "exit_portal", "exit", "return_portal" -> MarkerType.EXIT_PORTAL;
            default -> throw new IllegalArgumentException("unknown marker: " + value);
        };
    }

    private static Map<LootRole, ResourceLocation> lootOverrides(JsonObject json) {
        if (!json.has("loot_overrides")) return Map.of();
        if (!json.get("loot_overrides").isJsonObject())
            throw new IllegalArgumentException("'loot_overrides' must be an object");
        Map<LootRole, ResourceLocation> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("loot_overrides").entrySet()) {
            ResourceLocation table = ResourceLocation.tryParse(entry.getValue().getAsString());
            if (table == null)
                throw new IllegalArgumentException("invalid resource location in loot_overrides." + entry.getKey());
            result.put(LootRole.parse(entry.getKey()), table);
        }
        return result;
    }

    private static Map<SpawnRole, ResourceLocation> mobRoleOverrides(JsonObject json) {
        if (!json.has("mob_role_overrides")) return Map.of();
        if (!json.get("mob_role_overrides").isJsonObject())
            throw new IllegalArgumentException("'mob_role_overrides' must be an object");
        Map<SpawnRole, ResourceLocation> result = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("mob_role_overrides").entrySet()) {
            ResourceLocation profile = ResourceLocation.tryParse(entry.getValue().getAsString());
            if (profile == null)
                throw new IllegalArgumentException(
                    "invalid resource location in mob_role_overrides." + entry.getKey());
            result.put(SpawnRole.parse(entry.getKey()), profile);
        }
        return result;
    }

    private static JsonObject requiredObject(JsonObject json, String field) {
        if (!json.has(field) || !json.get(field).isJsonObject()) throw new IllegalArgumentException("missing object '" + field + "'");
        return json.getAsJsonObject(field);
    }
    private static JsonArray requiredArray(JsonObject json, String field) {
        if (!json.has(field) || !json.get(field).isJsonArray()) throw new IllegalArgumentException("missing array '" + field + "'");
        return json.getAsJsonArray(field);
    }
    private static JsonArray optionalArray(JsonObject json, String field) {
        return json.has(field) ? json.getAsJsonArray(field) : new JsonArray();
    }
    private static String requiredString(JsonObject json, String field) {
        if (!json.has(field) || !json.get(field).isJsonPrimitive()) throw new IllegalArgumentException("missing string '" + field + "'");
        return json.get(field).getAsString();
    }
    private static String optionalString(JsonObject json, String field, String fallback) {
        return json.has(field) ? json.get(field).getAsString() : fallback;
    }
    private static int positive(JsonObject json, String field, int fallback) {
        int value = json.has(field) ? json.get(field).getAsInt() : fallback;
        if (value < 1 || value > 1_000_000) throw new IllegalArgumentException(field + " must be between 1 and 1000000");
        return value;
    }
    private static int nonNegative(JsonObject json, String field, int fallback) {
        int value = json.has(field) ? json.get(field).getAsInt() : fallback;
        if (value < 0 || value > 1_000_000) throw new IllegalArgumentException(field + " must be between 0 and 1000000");
        return value;
    }
    private static double positiveDouble(JsonObject json, String field, double fallback) {
        double value = optionalDouble(json, field, fallback);
        if (value <= 0 || !Double.isFinite(value)) throw new IllegalArgumentException(field + " must be a positive finite number");
        return value;
    }
    private static double optionalDouble(JsonObject json, String field, double fallback) {
        return json.has(field) ? json.get(field).getAsDouble() : fallback;
    }
    private static ResourceLocation requiredId(JsonObject json, String field) {
        return parseId(requiredString(json, field), field);
    }
    private static ResourceLocation optionalId(JsonObject json, String field, String fallback) {
        return parseId(optionalString(json, field, fallback), field);
    }
    private static ResourceLocation nullableId(JsonObject json, String field) {
        return json.has(field) ? parseId(json.get(field).getAsString(), field) : null;
    }
    private static ResourceLocation parseId(String value, String field) {
        ResourceLocation result = ResourceLocation.tryParse(value);
        if (result == null) throw new IllegalArgumentException("invalid resource location in '" + field + "': " + value);
        return result;
    }
    private static BlockPos blockPos(JsonArray array, String field) {
        requireVec(array, field);
        return new BlockPos(array.get(0).getAsInt(), array.get(1).getAsInt(), array.get(2).getAsInt());
    }
    private static void requireVec(JsonArray array, String field) {
        if (array.size() != 3) throw new IllegalArgumentException(field + " must contain exactly three integers");
    }
    private static List<String> strings(JsonObject json, String field) {
        if (!json.has(field)) return List.of();
        List<String> values = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray(field)) values.add(element.getAsString());
        return values;
    }
    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String field) {
        try { return Enum.valueOf(type, value.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw new IllegalArgumentException("unknown " + field + ": " + value); }
    }
}

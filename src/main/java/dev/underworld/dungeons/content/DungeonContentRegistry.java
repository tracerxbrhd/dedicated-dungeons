package dev.underworld.dungeons.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.underworld.api.difficulty.DifficultyRank;
import dev.underworld.dungeons.content.DungeonContentTypes.Archetype;
import dev.underworld.dungeons.content.DungeonContentTypes.Boss;
import dev.underworld.dungeons.content.DungeonContentTypes.Connector;
import dev.underworld.dungeons.content.DungeonContentTypes.Room;
import dev.underworld.dungeons.content.DungeonContentTypes.RoomPool;
import dev.underworld.dungeons.content.DungeonContentTypes.Theme;
import dev.underworld.dungeons.content.DungeonContentTypes.WeightedId;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;
import org.slf4j.Logger;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

/** Atomic, validated snapshot of all dungeon JSON supplied by every datapack namespace. */
public final class DungeonContentRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation DEFAULT_THEME = ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", "underworld");
    public static final ResourceLocation DEFAULT_ARCHETYPE = ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", "basic");

    public enum Severity { ERROR, WARNING }
    private enum DefinitionKind { THEME, ARCHETYPE, ROOM_POOL, ROOM, CONNECTOR, BOSS }
    private record DefinitionKey(DefinitionKind kind, ResourceLocation id) {}
    public record Issue(Severity severity, ResourceLocation definition, String message) {}
    public record ValidationReport(List<Issue> issues, int themes, int archetypes, int roomPools,
                                   int rooms, int connectors, int bosses) {
        public ValidationReport { issues = List.copyOf(issues); }
        public long errors() { return issues.stream().filter(issue -> issue.severity() == Severity.ERROR).count(); }
        public long warnings() { return issues.stream().filter(issue -> issue.severity() == Severity.WARNING).count(); }
        public boolean valid() { return errors() == 0; }
    }

    private record Snapshot(Map<ResourceLocation, Theme> themes, Map<ResourceLocation, Archetype> archetypes,
                            Map<ResourceLocation, RoomPool> roomPools, Map<ResourceLocation, Room> rooms,
                            Map<ResourceLocation, Connector> connectors, Map<ResourceLocation, Boss> bosses,
                            Set<DefinitionKey> invalid, ValidationReport report) {
        static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Set.of(),
                new ValidationReport(List.of(), 0, 0, 0, 0, 0, 0));
        }
    }

    private static volatile Snapshot snapshot = Snapshot.empty();
    private DungeonContentRegistry() {}

    public static void reload(ResourceManager manager) {
        List<Issue> issues = new ArrayList<>();
        Map<ResourceLocation, Theme> themes = load(manager, "dungeon_themes", Theme::parse, issues);
        Map<ResourceLocation, Archetype> archetypes = load(manager, "dungeon_archetypes", Archetype::parse, issues);
        Map<ResourceLocation, RoomPool> pools = load(manager, "dungeon_room_pools", RoomPool::parse, issues);
        Map<ResourceLocation, Room> rooms = load(manager, "dungeon_rooms", Room::parse, issues);
        Map<ResourceLocation, Connector> connectors = load(manager, "dungeon_connectors", Connector::parse, issues);
        Map<ResourceLocation, Boss> bosses = load(manager, "dungeon_bosses", Boss::parse, issues);
        Set<DefinitionKey> invalid = validate(manager, themes, archetypes, pools, rooms, connectors, bosses, issues);
        ValidationReport report = new ValidationReport(issues, themes.size(), archetypes.size(), pools.size(),
            rooms.size(), connectors.size(), bosses.size());
        snapshot = new Snapshot(Map.copyOf(themes), Map.copyOf(archetypes), Map.copyOf(pools), Map.copyOf(rooms),
            Map.copyOf(connectors), Map.copyOf(bosses), Set.copyOf(invalid), report);
        LOGGER.info("Loaded dungeon content: {} themes, {} archetypes, {} pools, {} rooms, {} connectors, {} bosses ({} errors, {} warnings)",
            report.themes(), report.archetypes(), report.roomPools(), report.rooms(), report.connectors(), report.bosses(),
            report.errors(), report.warnings());
        issues.forEach(issue -> {
            String text = "Dungeon data " + issue.definition() + ": " + issue.message();
            if (issue.severity() == Severity.ERROR) LOGGER.error(text); else LOGGER.warn(text);
        });
    }

    public static ValidationReport report() { return snapshot.report(); }
    public static Map<ResourceLocation, Theme> themes() { return snapshot.themes(); }
    public static Map<ResourceLocation, Archetype> archetypes() { return snapshot.archetypes(); }
    public static Map<ResourceLocation, Room> rooms() { return snapshot.rooms(); }
    public static Map<ResourceLocation, Connector> connectors() { return snapshot.connectors(); }
    public static Map<ResourceLocation, Boss> bosses() { return snapshot.bosses(); }
    public static Optional<Room> room(ResourceLocation id) { return Optional.ofNullable(snapshot.rooms().get(id)); }
    public static Optional<Connector> connector(ResourceLocation id) { return Optional.ofNullable(snapshot.connectors().get(id)); }
    public static Optional<Boss> boss(ResourceLocation id) { return Optional.ofNullable(snapshot.bosses().get(id)); }

    public static Optional<Theme> resolveTheme(ResourceLocation requested) {
        return resolve(requested, snapshot.themes(), Theme::available, Theme::fallback);
    }

    public static Optional<Archetype> resolveArchetype(ResourceLocation requested) {
        return resolve(requested, snapshot.archetypes(), value -> archetypeUsable(value),
            Archetype::fallback);
    }

    public static Optional<RoomPool> resolveRoomPool(ResourceLocation requested) {
        return resolve(requested, snapshot.roomPools(), DungeonContentRegistry::roomPoolUsable,
            RoomPool::fallback);
    }

    public static Optional<Archetype> chooseArchetype(ResourceLocation themeId, DifficultyRank difficulty, RandomSource random) {
        Theme theme = resolveTheme(themeId).orElse(null);
        if (theme == null) return Optional.empty();
        List<WeightedValue<Archetype>> choices = new ArrayList<>();
        for (WeightedId entry : theme.archetypes()) resolveArchetype(entry.id())
            .filter(value -> value.difficulties().contains(difficulty))
            .ifPresent(value -> choices.add(new WeightedValue<>(value, entry.weight())));
        return choose(choices, random);
    }

    public static Optional<Archetype> chooseAnyArchetype(DifficultyRank difficulty, RandomSource random) {
        List<WeightedValue<Archetype>> choices = new ArrayList<>();
        for (Theme theme : snapshot.themes().values()) {
            if (!theme.available() || invalid(DefinitionKind.THEME, theme.id())) continue;
            for (WeightedId entry : theme.archetypes()) resolveArchetype(entry.id())
                .filter(value -> value.difficulties().contains(difficulty))
                .ifPresent(value -> choices.add(new WeightedValue<>(value, entry.weight())));
        }
        return choose(choices, random);
    }

    public static Optional<Theme> themeForArchetype(ResourceLocation archetypeId) {
        return resolveArchetype(archetypeId).flatMap(value -> resolveTheme(value.theme()));
    }

    public static List<WeightedValue<Room>> availableRooms(RoomPool pool) {
        List<WeightedValue<Room>> result = new ArrayList<>();
        for (WeightedId entry : pool.rooms()) {
            Room room = snapshot.rooms().get(entry.id());
            if (room != null && room.available() && !invalid(DefinitionKind.ROOM, room.id()))
                result.add(new WeightedValue<>(room, weightedProduct(entry.weight(), room.weight())));
        }
        return List.copyOf(result);
    }

    public static List<WeightedValue<Connector>> availableConnectors(Archetype archetype) {
        List<WeightedValue<Connector>> result = new ArrayList<>();
        for (WeightedId entry : archetype.connectors()) {
            Connector value = snapshot.connectors().get(entry.id());
            if (value != null && value.available() && !invalid(DefinitionKind.CONNECTOR, value.id()))
                result.add(new WeightedValue<>(value, weightedProduct(entry.weight(), value.weight())));
        }
        return List.copyOf(result);
    }

    public static List<WeightedValue<Boss>> availableBosses(Archetype archetype) {
        List<WeightedValue<Boss>> result = new ArrayList<>();
        for (WeightedId entry : archetype.bosses()) {
            Boss value = snapshot.bosses().get(entry.id());
            if (value != null && value.available() && !invalid(DefinitionKind.BOSS, value.id()))
                result.add(new WeightedValue<>(value, weightedProduct(entry.weight(), value.weight())));
        }
        return List.copyOf(result);
    }

    public record WeightedValue<T>(T value, int weight) {}
    public static <T> Optional<T> choose(List<WeightedValue<T>> choices, RandomSource random) {
        long total = choices.stream().mapToLong(WeightedValue::weight).sum();
        if (total <= 0) return Optional.empty();
        long selected = Math.floorMod(random.nextLong(), total);
        for (WeightedValue<T> choice : choices) {
            selected -= choice.weight();
            if (selected < 0) return Optional.of(choice.value());
        }
        return Optional.of(choices.getLast().value());
    }

    private static <T> Optional<T> resolve(ResourceLocation requested, Map<ResourceLocation, T> values,
                                           java.util.function.Predicate<T> usable,
                                           java.util.function.Function<T, ResourceLocation> fallback) {
        Set<ResourceLocation> seen = new HashSet<>();
        ResourceLocation current = requested;
        while (current != null && seen.add(current)) {
            T value = values.get(current);
            if (value == null) return Optional.empty();
            if (usable.test(value)) return Optional.of(value);
            current = fallback.apply(value);
        }
        return Optional.empty();
    }

    private static <T> Map<ResourceLocation, T> load(ResourceManager manager, String root,
                                                      BiFunction<ResourceLocation, JsonObject, T> parser,
                                                      List<Issue> issues) {
        Map<ResourceLocation, T> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources(root,
            id -> id.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation file = entry.getKey();
            String path = file.getPath().substring(root.length() + 1, file.getPath().length() - 5);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path);
            try (Reader reader = entry.getValue().openAsReader()) {
                result.put(id, parser.apply(id, JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception exception) {
                issues.add(new Issue(Severity.ERROR, id, "cannot parse " + root + ": " + rootCause(exception)));
            }
        }
        return result;
    }

    private static Set<DefinitionKey> validate(ResourceManager resources, Map<ResourceLocation, Theme> themes,
                                                   Map<ResourceLocation, Archetype> archetypes,
                                                   Map<ResourceLocation, RoomPool> pools, Map<ResourceLocation, Room> rooms,
                                                   Map<ResourceLocation, Connector> connectors,
                                                   Map<ResourceLocation, Boss> bosses, List<Issue> issues) {
        Set<DefinitionKey> invalid = new HashSet<>();
        themes.values().forEach(theme -> {
            availability(theme.id(), theme.requirements().requiredMods(), theme.available(), issues);
            theme.archetypes().forEach(value -> require(DefinitionKind.THEME, theme.id(), "archetype", value.id(), archetypes, invalid, issues));
            optionalReference(DefinitionKind.THEME, theme.id(), "fallback theme", theme.fallback(), themes, invalid, issues);
        });
        archetypes.values().forEach(archetype -> {
            availability(archetype.id(), archetype.requirements().requiredMods(), archetype.available(), issues);
            require(DefinitionKind.ARCHETYPE, archetype.id(), "theme", archetype.theme(), themes, invalid, issues);
            require(DefinitionKind.ARCHETYPE, archetype.id(), "room pool", archetype.roomPool(), pools, invalid, issues);
            archetype.connectors().forEach(value -> require(DefinitionKind.ARCHETYPE, archetype.id(), "connector", value.id(), connectors, invalid, issues));
            archetype.bosses().forEach(value -> require(DefinitionKind.ARCHETYPE, archetype.id(), "boss", value.id(), bosses, invalid, issues));
            optionalReference(DefinitionKind.ARCHETYPE, archetype.id(), "fallback archetype", archetype.fallback(), archetypes, invalid, issues);
        });
        pools.values().forEach(pool -> {
            availability(pool.id(), pool.requirements().requiredMods(), pool.available(), issues);
            require(DefinitionKind.ROOM_POOL, pool.id(), "theme", pool.theme(), themes, invalid, issues);
            pool.rooms().forEach(value -> require(DefinitionKind.ROOM_POOL, pool.id(), "room", value.id(), rooms, invalid, issues));
            optionalReference(DefinitionKind.ROOM_POOL, pool.id(), "fallback room pool", pool.fallback(), pools, invalid, issues);
        });
        rooms.values().forEach(room -> {
            availability(room.id(), room.requirements().requiredMods(), room.available(), issues);
            require(DefinitionKind.ROOM, room.id(), "theme", room.theme(), themes, invalid, issues);
            validateStructure(resources, DefinitionKind.ROOM, room.id(), room.structure(), invalid, issues);
            validatePalette(DefinitionKind.ROOM, room.id(), room.palette(), invalid, issues);
        });
        connectors.values().forEach(connector -> {
            availability(connector.id(), connector.requirements().requiredMods(), connector.available(), issues);
            validateStructure(resources, DefinitionKind.CONNECTOR, connector.id(), connector.structure(), invalid, issues);
            validatePalette(DefinitionKind.CONNECTOR, connector.id(), connector.palette(), invalid, issues);
        });
        bosses.values().forEach(boss -> {
            availability(boss.id(), boss.requirements().requiredMods(), boss.available(), issues);
            if (!BuiltInRegistries.ENTITY_TYPE.containsKey(boss.entityType())) error(DefinitionKind.BOSS, boss.id(), "unknown entity '" + boss.entityType() + "'", invalid, issues);
        });
        detectFallbackCycles(DefinitionKind.THEME, themes, Theme::fallback, "theme", invalid, issues);
        detectFallbackCycles(DefinitionKind.ARCHETYPE, archetypes, Archetype::fallback, "archetype", invalid, issues);
        detectFallbackCycles(DefinitionKind.ROOM_POOL, pools, RoomPool::fallback, "room pool", invalid, issues);
        return invalid;
    }

    private static void validateStructure(ResourceManager resources, DefinitionKind ownerKind, ResourceLocation owner,
                                          ResourceLocation structure, Set<DefinitionKey> invalid, List<Issue> issues) {
        if (structure == null) return;
        ResourceLocation file = ResourceLocation.fromNamespaceAndPath(structure.getNamespace(), "structure/" + structure.getPath() + ".nbt");
        if (resources.getResource(file).isEmpty()) error(ownerKind, owner, "missing structure '" + structure + "'", invalid, issues);
    }

    private static void validatePalette(DefinitionKind ownerKind, ResourceLocation owner, DungeonContentTypes.Palette palette,
                                        Set<DefinitionKey> invalid, List<Issue> issues) {
        for (ResourceLocation block : List.of(palette.floor(), palette.wall(), palette.accent()))
            if (!BuiltInRegistries.BLOCK.containsKey(block)) error(ownerKind, owner, "unknown palette block '" + block + "'", invalid, issues);
    }

    private static void availability(ResourceLocation id, List<String> mods, boolean available, List<Issue> issues) {
        if (!available) issues.add(new Issue(Severity.WARNING, id, "disabled; missing one of required_mods " + mods));
    }

    private static <T> void require(DefinitionKind ownerKind, ResourceLocation owner, String targetKind,
                                    ResourceLocation target, Map<ResourceLocation, T> values,
                                    Set<DefinitionKey> invalid, List<Issue> issues) {
        if (!values.containsKey(target)) error(ownerKind, owner, "missing " + targetKind + " '" + target + "'", invalid, issues);
    }

    private static <T> void optionalReference(DefinitionKind ownerKind, ResourceLocation owner, String targetKind,
                                              ResourceLocation target, Map<ResourceLocation, T> values,
                                              Set<DefinitionKey> invalid, List<Issue> issues) {
        if (target != null) require(ownerKind, owner, targetKind, target, values, invalid, issues);
    }

    private static void error(DefinitionKind ownerKind, ResourceLocation owner, String message,
                              Set<DefinitionKey> invalid, List<Issue> issues) {
        invalid.add(new DefinitionKey(ownerKind, owner)); issues.add(new Issue(Severity.ERROR, owner, message));
    }

    private static <T> void detectFallbackCycles(DefinitionKind ownerKind, Map<ResourceLocation, T> values,
                                                 java.util.function.Function<T, ResourceLocation> fallback,
                                                 String kind, Set<DefinitionKey> invalid, List<Issue> issues) {
        for (Map.Entry<ResourceLocation, T> entry : values.entrySet()) {
            Set<ResourceLocation> seen = new HashSet<>();
            ResourceLocation current = entry.getKey();
            while (current != null && seen.add(current)) {
                T value = values.get(current); current = value == null ? null : fallback.apply(value);
            }
            if (current != null) error(ownerKind, entry.getKey(), kind + " fallback cycle", invalid, issues);
        }
    }

    private static boolean invalid(DefinitionKind kind, ResourceLocation id) {
        return snapshot.invalid().contains(new DefinitionKey(kind, id));
    }

    private static boolean roomPoolUsable(RoomPool pool) {
        if (!pool.available() || invalid(DefinitionKind.ROOM_POOL, pool.id())) return false;
        return pool.rooms().stream().map(WeightedId::id).map(snapshot.rooms()::get)
            .anyMatch(room -> room != null && room.available() && !invalid(DefinitionKind.ROOM, room.id()));
    }

    private static boolean archetypeUsable(Archetype archetype) {
        if (!archetype.available() || invalid(DefinitionKind.ARCHETYPE, archetype.id())) return false;
        boolean pool = resolveRoomPool(archetype.roomPool()).isPresent();
        boolean connector = archetype.connectors().stream().map(WeightedId::id).map(snapshot.connectors()::get)
            .anyMatch(value -> value != null && value.available() && !invalid(DefinitionKind.CONNECTOR, value.id()));
        boolean boss = archetype.bosses().stream().map(WeightedId::id).map(snapshot.bosses()::get)
            .anyMatch(value -> value != null && value.available() && !invalid(DefinitionKind.BOSS, value.id()));
        return pool && connector && boss;
    }

    private static String rootCause(Throwable throwable) {
        while (throwable.getCause() != null) throwable = throwable.getCause();
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    private static int weightedProduct(int first, int second) {
        return (int) Math.min(Integer.MAX_VALUE, (long) first * second);
    }
}

package dev.uapi.dungeons.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import dev.uapi.difficulty.DifficultyRank;
import dev.uapi.dungeons.content.DungeonContentTypes.Archetype;
import dev.uapi.dungeons.content.DungeonContentTypes.Boss;
import dev.uapi.dungeons.content.DungeonContentTypes.Connector;
import dev.uapi.dungeons.content.DungeonContentTypes.Room;
import dev.uapi.dungeons.content.DungeonContentTypes.RoomPool;
import dev.uapi.dungeons.content.DungeonContentTypes.Theme;
import dev.uapi.dungeons.content.DungeonContentTypes.WeightedId;
import dev.uapi.dungeons.content.EncounterDefinitions.BossPool;
import dev.uapi.dungeons.content.EncounterDefinitions.EncounterPool;
import dev.uapi.dungeons.content.EncounterDefinitions.MobEntry;
import dev.uapi.dungeons.content.EncounterDefinitions.MobPool;
import dev.uapi.dungeons.loot.LootProfileManager;
import dev.uapi.dungeons.mob.MobProfileManager;
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
import java.util.function.Predicate;

/** Atomic, validated snapshot of all dungeon JSON supplied by every datapack namespace. */
public final class DungeonContentRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation DEFAULT_THEME = ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", "forgotten_depths");
    public static final ResourceLocation DEFAULT_ARCHETYPE = ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", "basic");
    public static final ResourceLocation DEFAULT_DUNGEON = ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", "forgotten_depths");

    public enum Severity { ERROR, WARNING }
    private enum DefinitionKind { DUNGEON, LEVEL_PROFILE, THEME, ARCHETYPE, ROOM_POOL, ROOM, CONNECTOR, BOSS,
        MOB_POOL, ENCOUNTER_POOL, BOSS_POOL }
    private record DefinitionKey(DefinitionKind kind, ResourceLocation id) {}
    public record Issue(Severity severity, ResourceLocation definition, String message) {}
    public record ValidationReport(List<Issue> issues, int themes, int archetypes, int roomPools,
                                   int rooms, int connectors, int bosses, int dungeons, int levelProfiles,
                                   int mobPools, int encounterPools, int bossPools) {
        public ValidationReport { issues = List.copyOf(issues); }
        public long errors() { return issues.stream().filter(issue -> issue.severity() == Severity.ERROR).count(); }
        public long warnings() { return issues.stream().filter(issue -> issue.severity() == Severity.WARNING).count(); }
        public boolean valid() { return errors() == 0; }
    }

    private record Snapshot(Map<ResourceLocation, Theme> themes, Map<ResourceLocation, Archetype> archetypes,
                            Map<ResourceLocation, RoomPool> roomPools, Map<ResourceLocation, Room> rooms,
                            Map<ResourceLocation, Connector> connectors, Map<ResourceLocation, Boss> bosses,
                            Map<ResourceLocation, DungeonDefinition> dungeons,
                            Map<ResourceLocation, LevelProfile> levelProfiles,
                            Map<ResourceLocation, MobPool> mobPools,
                            Map<ResourceLocation, EncounterPool> encounterPools,
                            Map<ResourceLocation, BossPool> bossPools,
                            Set<DefinitionKey> invalid, ValidationReport report) {
        static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Set.of(),
                new ValidationReport(List.of(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        }
    }

    private static volatile Snapshot snapshot = Snapshot.empty();
    private static volatile ValidationReport lastReport = snapshot.report();
    private DungeonContentRegistry() {}

    public static void reload(ResourceManager manager) {
        LootProfileManager.reload(manager);
        MobProfileManager.reload(manager);
        List<Issue> issues = new ArrayList<>();
        Map<ResourceLocation, Theme> themes = loadCombined(manager, "dungeon_themes",
            "dedicated_dungeons/themes", Theme::parse, issues);
        Map<ResourceLocation, Archetype> archetypes = loadCombined(manager, "dungeon_archetypes",
            "dedicated_dungeons/archetypes", Archetype::parse, issues);
        Map<ResourceLocation, RoomPool> pools = loadCombined(manager, "dungeon_room_pools",
            "dedicated_dungeons/room_pools", RoomPool::parse, issues);
        Map<ResourceLocation, Room> rooms = loadCombined(manager, "dungeon_rooms",
            "dedicated_dungeons/rooms", Room::parse, issues);
        Map<ResourceLocation, Connector> connectors = loadCombined(manager, "dungeon_connectors",
            "dedicated_dungeons/connector_profiles", Connector::parse, issues);
        Map<ResourceLocation, Boss> bosses = loadCombined(manager, "dungeon_bosses",
            "dedicated_dungeons/bosses", Boss::parse, issues);
        Map<ResourceLocation, DungeonDefinition> dungeons = loadCodec(manager,
            "dedicated_dungeons/dungeons", DungeonDefinition.CODEC, issues);
        Map<ResourceLocation, LevelProfile> levelProfiles = loadCodec(manager,
            "dedicated_dungeons/level_profiles", LevelProfile.CODEC, issues);
        Map<ResourceLocation, MobPool> mobPools = load(manager, "dedicated_dungeons/mob_pools", MobPool::parse, issues);
        Map<ResourceLocation, EncounterPool> encounterPools = load(manager,
            "dedicated_dungeons/encounter_pools", EncounterPool::parse, issues);
        Map<ResourceLocation, BossPool> bossPools = load(manager,
            "dedicated_dungeons/boss_pools", BossPool::parse, issues);
        Set<DefinitionKey> invalid = validate(manager, themes, archetypes, pools, rooms, connectors, bosses,
            dungeons, levelProfiles, mobPools, encounterPools, bossPools, issues);
        ValidationReport report = new ValidationReport(issues, themes.size(), archetypes.size(), pools.size(),
            rooms.size(), connectors.size(), bosses.size(), dungeons.size(), levelProfiles.size(), mobPools.size(),
            encounterPools.size(), bossPools.size());
        Snapshot candidate = new Snapshot(Map.copyOf(themes), Map.copyOf(archetypes), Map.copyOf(pools), Map.copyOf(rooms),
            Map.copyOf(connectors), Map.copyOf(bosses), Map.copyOf(dungeons), Map.copyOf(levelProfiles),
            Map.copyOf(mobPools), Map.copyOf(encounterPools), Map.copyOf(bossPools), Set.copyOf(invalid), report);
        lastReport = report;
        if (report.valid()) {
            snapshot = candidate;
        } else {
            LOGGER.error("Rejected dungeon content reload with {} errors; retaining previous valid snapshot containing {} dungeons",
                report.errors(), snapshot.dungeons().size());
        }
        LOGGER.info("Loaded dungeon content: {} dungeons, {} level profiles, {} themes, {} archetypes, {} room pools, {} rooms, {} connectors, {} bosses, {} mob pools, {} encounter pools, {} boss pools ({} errors, {} warnings)",
            report.dungeons(), report.levelProfiles(),
            report.themes(), report.archetypes(), report.roomPools(), report.rooms(), report.connectors(), report.bosses(),
            report.mobPools(), report.encounterPools(), report.bossPools(),
            report.errors(), report.warnings());
        issues.forEach(issue -> {
            String text = "Dungeon data " + issue.definition() + ": " + issue.message();
            if (issue.severity() == Severity.ERROR) LOGGER.error(text); else LOGGER.warn(text);
        });
    }

    /** The latest attempted reload report, including errors from a snapshot that was atomically rejected. */
    public static ValidationReport report() { return lastReport; }
    public static Map<ResourceLocation, Theme> themes() { return snapshot.themes(); }
    public static Map<ResourceLocation, Archetype> archetypes() { return snapshot.archetypes(); }
    public static Map<ResourceLocation, Room> rooms() { return snapshot.rooms(); }
    public static Map<ResourceLocation, Connector> connectors() { return snapshot.connectors(); }
    public static Map<ResourceLocation, Boss> bosses() { return snapshot.bosses(); }
    public static Map<ResourceLocation, DungeonDefinition> dungeons() { return snapshot.dungeons(); }
    public static Map<ResourceLocation, LevelProfile> levelProfiles() { return snapshot.levelProfiles(); }
    public static Map<ResourceLocation, MobPool> mobPools() { return snapshot.mobPools(); }
    public static Map<ResourceLocation, EncounterPool> encounterPools() { return snapshot.encounterPools(); }
    public static Map<ResourceLocation, BossPool> bossPools() { return snapshot.bossPools(); }
    public static Optional<Room> room(ResourceLocation id) { return Optional.ofNullable(snapshot.rooms().get(id)); }
    public static Optional<Connector> connector(ResourceLocation id) { return Optional.ofNullable(snapshot.connectors().get(id)); }
    public static Optional<Boss> boss(ResourceLocation id) { return Optional.ofNullable(snapshot.bosses().get(id)); }
    public static Optional<DungeonDefinition> dungeon(ResourceLocation id) {
        DungeonDefinition value = snapshot.dungeons().get(id);
        return value == null || invalid(DefinitionKind.DUNGEON, id) || !value.available()
            ? Optional.empty() : Optional.of(value);
    }

    public static Optional<MobPool> resolveMobPool(ResourceLocation requested) {
        return resolve(requested, snapshot.mobPools(),
            value -> value.available() && !invalid(DefinitionKind.MOB_POOL, value.id()), MobPool::fallback);
    }

    /**
     * Selects an entry after level/tier filtering and follows pool fallbacks when the otherwise available pool has
     * no usable entry. This matters when a player crosses from one level band to the next between dungeon runs.
     */
    public static Optional<MobEntry> chooseMobEntry(ResourceLocation requested, int level, int tier,
                                                     RandomSource random) {
        return chooseMobEntry(requested, level, tier, random, entry -> true);
    }

    public static Optional<MobEntry> chooseMobEntry(ResourceLocation requested, int level, int tier,
                                                     RandomSource random, Predicate<MobEntry> filter) {
        Set<ResourceLocation> seen = new HashSet<>();
        ResourceLocation current = requested;
        while (current != null && seen.add(current)) {
            MobPool pool = snapshot.mobPools().get(current);
            if (pool == null) return Optional.empty();
            if (pool.available() && !invalid(DefinitionKind.MOB_POOL, pool.id())) {
                Optional<MobEntry> selected = pool.choose(level, tier, random, filter);
                if (selected.isPresent()) return selected;
            }
            current = pool.fallback();
        }
        return Optional.empty();
    }

    public static Optional<EncounterPool> resolveEncounterPool(ResourceLocation requested) {
        return resolve(requested, snapshot.encounterPools(),
            value -> value.available() && !invalid(DefinitionKind.ENCOUNTER_POOL, value.id()), EncounterPool::fallback);
    }

    public static Optional<BossPool> resolveBossPool(ResourceLocation requested) {
        return resolve(requested, snapshot.bossPools(), DungeonContentRegistry::bossPoolUsable,
            BossPool::fallback);
    }

    public static Optional<DungeonSelector.Candidate> chooseDungeon(int effectiveLevel, ResourceLocation dimension,
                                                                    RandomSource random) {
        return DungeonSelector.select(snapshot.dungeons(), snapshot.levelProfiles(), effectiveLevel, dimension,
            dev.uapi.integration.IntegrationService::isLoaded, random);
    }

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

    public static List<WeightedValue<Boss>> availableBosses(Archetype archetype, DifficultyRank difficulty) {
        List<WeightedValue<Boss>> result = new ArrayList<>();
        for (WeightedId entry : archetype.bosses()) {
            Boss value = snapshot.bosses().get(entry.id());
            if (value != null && value.available() && value.supports(difficulty)
                && !invalid(DefinitionKind.BOSS, value.id()))
                result.add(new WeightedValue<>(value, weightedProduct(entry.weight(), value.weight())));
        }
        return List.copyOf(result);
    }

    public static List<WeightedValue<Boss>> availableBosses(BossPool pool, DifficultyRank difficulty) {
        List<WeightedValue<Boss>> result = new ArrayList<>();
        for (WeightedId entry : pool.bosses()) {
            Boss value = snapshot.bosses().get(entry.id());
            if (value != null && value.available() && value.supports(difficulty)
                && !invalid(DefinitionKind.BOSS, value.id()))
                result.add(new WeightedValue<>(value, weightedProduct(entry.weight(), value.weight())));
        }
        return List.copyOf(result);
    }

    /**
     * Resolves the first pool in a fallback chain that contains a boss usable at the requested rank.
     * Pool availability alone is insufficient because a guarded high-rank pool may be valid but have
     * no entry for an E-rank dungeon.
     */
    public static List<WeightedValue<Boss>> availableBosses(ResourceLocation requested,
                                                             DifficultyRank difficulty) {
        Set<ResourceLocation> seen = new HashSet<>();
        ResourceLocation current = requested;
        while (current != null && seen.add(current)) {
            BossPool pool = snapshot.bossPools().get(current);
            if (pool == null) return List.of();
            if (pool.available() && !invalid(DefinitionKind.BOSS_POOL, pool.id())) {
                List<WeightedValue<Boss>> choices = availableBosses(pool, difficulty);
                if (!choices.isEmpty()) return choices;
            }
            current = pool.fallback();
        }
        return List.of();
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

    private static <T> Map<ResourceLocation, T> loadCombined(ResourceManager manager, String legacyRoot,
                                                              String currentRoot,
                                                              BiFunction<ResourceLocation, JsonObject, T> parser,
                                                              List<Issue> issues) {
        Map<ResourceLocation, T> result = new LinkedHashMap<>(load(manager, legacyRoot, parser, issues));
        result.putAll(load(manager, currentRoot, parser, issues));
        return result;
    }

    private static <T> Map<ResourceLocation, T> loadCodec(ResourceManager manager, String root, Codec<T> codec,
                                                           List<Issue> issues) {
        Map<ResourceLocation, T> result = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources(root,
            id -> id.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation file = entry.getKey();
            String path = file.getPath().substring(root.length() + 1, file.getPath().length() - 5);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path);
            try (Reader reader = entry.getValue().openAsReader()) {
                T parsed = codec.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                    .getOrThrow(message -> new IllegalArgumentException("codec: " + message));
                result.put(id, parsed);
            } catch (Exception exception) {
                issues.add(new Issue(Severity.ERROR, id, "cannot parse " + file + ": " + rootCause(exception)));
            }
        }
        return result;
    }

    private static Set<DefinitionKey> validate(ResourceManager resources, Map<ResourceLocation, Theme> themes,
                                                   Map<ResourceLocation, Archetype> archetypes,
                                                   Map<ResourceLocation, RoomPool> pools, Map<ResourceLocation, Room> rooms,
                                                   Map<ResourceLocation, Connector> connectors,
                                                   Map<ResourceLocation, Boss> bosses,
                                                   Map<ResourceLocation, DungeonDefinition> dungeons,
                                                   Map<ResourceLocation, LevelProfile> levelProfiles,
                                                   Map<ResourceLocation, MobPool> mobPools,
                                                   Map<ResourceLocation, EncounterPool> encounterPools,
                                                   Map<ResourceLocation, BossPool> bossPools,
                                                   List<Issue> issues) {
        Set<DefinitionKey> invalid = new HashSet<>();
        dungeons.forEach((id, dungeon) -> {
            availability(id, dungeon.requirements().requiredMods(), dungeon.available(), issues);
            require(DefinitionKind.DUNGEON, id, "archetype", dungeon.archetype(), archetypes, invalid, issues);
            require(DefinitionKind.DUNGEON, id, "level profile", dungeon.levelProfile(), levelProfiles, invalid, issues);
            require(DefinitionKind.DUNGEON, id, "encounter pool", dungeon.pools().encounterPool(), encounterPools, invalid, issues);
            require(DefinitionKind.DUNGEON, id, "boss pool", dungeon.pools().bossPool(), bossPools, invalid, issues);
            optionalLootProfile(id, "dungeon", dungeon.pools().lootProfile(), issues);
            dungeon.pools().lootOverrides().forEach((role, table) ->
                optionalLootTable(id, "dungeon loot_overrides." + role, table, issues));
            optionalMobProfile(id, "dungeon", dungeon.pools().mobProfile(), issues);
            dungeon.pools().mobRoleOverrides().forEach((role, profile) ->
                optionalMobProfile(id, "dungeon mob_role_overrides." + role, profile, issues));
            Archetype archetype = archetypes.get(dungeon.archetype());
            if (archetype != null) {
                int minimumMainRooms = Math.max(archetype.minMainRooms(),
                    Math.max(dungeon.rules().minimumRooms() - 2, dungeon.rules().minimumDepth() - 1));
                int maximumMainRooms = Math.min(archetype.maxMainRooms(),
                    Math.min(dungeon.rules().maximumRooms() - 2, dungeon.rules().maximumDepth() - 1));
                if (maximumMainRooms < minimumMainRooms)
                    error(DefinitionKind.DUNGEON, id, "room/depth constraints have no solution with archetype main_path",
                        invalid, issues);
            }
        });
        mobPools.forEach((id, pool) -> {
            availability(id, pool.requiredMods(), pool.available(), issues);
            if (pool.available()) pool.entries().forEach(entry -> {
                boolean entryAvailable = entry.requiredMods().stream().allMatch(dev.uapi.integration.IntegrationService::isLoaded);
                if (entryAvailable && !BuiltInRegistries.ENTITY_TYPE.containsKey(entry.entityType()))
                    error(DefinitionKind.MOB_POOL, id, "unknown entity '" + entry.entityType() + "'", invalid, issues);
            });
            optionalReference(DefinitionKind.MOB_POOL, id, "fallback mob pool", pool.fallback(), mobPools, invalid, issues);
        });
        encounterPools.forEach((id, pool) -> {
            availability(id, pool.requiredMods(), pool.available(), issues);
            require(DefinitionKind.ENCOUNTER_POOL, id, "mob pool", pool.mobPool(), mobPools, invalid, issues);
            optionalReference(DefinitionKind.ENCOUNTER_POOL, id, "fallback encounter pool", pool.fallback(), encounterPools, invalid, issues);
        });
        bossPools.forEach((id, pool) -> {
            availability(id, pool.requiredMods(), pool.available(), issues);
            pool.bosses().forEach(entry -> require(DefinitionKind.BOSS_POOL, id, "boss", entry.id(), bosses, invalid, issues));
            optionalReference(DefinitionKind.BOSS_POOL, id, "fallback boss pool", pool.fallback(), bossPools, invalid, issues);
        });
        themes.values().forEach(theme -> {
            availability(theme.id(), theme.requirements().requiredMods(), theme.available(), issues);
            theme.archetypes().forEach(value -> require(DefinitionKind.THEME, theme.id(), "archetype", value.id(), archetypes, invalid, issues));
            optionalReference(DefinitionKind.THEME, theme.id(), "fallback theme", theme.fallback(), themes, invalid, issues);
            optionalLootProfile(theme.id(), "theme", theme.lootProfile(), issues);
            optionalMobProfile(theme.id(), "theme", theme.mobProfile(), issues);
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
            optionalLootProfile(room.id(), "room", room.lootProfile(), issues);
            room.lootOverrides().forEach((role, table) ->
                optionalLootTable(room.id(), "room loot_overrides." + role, table, issues));
            optionalMobProfile(room.id(), "room", room.mobProfile(), issues);
            room.mobRoleOverrides().forEach((role, profile) ->
                optionalMobProfile(room.id(), "room mob_role_overrides." + role, profile, issues));
            if (room.tags().contains("entrance") && room.markers(DungeonContentTypes.MarkerType.PLAYER_SPAWN).isEmpty())
                error(DefinitionKind.ROOM, room.id(), "entrance room has no player_spawn marker", invalid, issues);
            if (room.tags().contains("boss") && room.markers(DungeonContentTypes.MarkerType.BOSS_SPAWN).size() != 1)
                error(DefinitionKind.ROOM, room.id(), "boss room requires exactly one boss_spawn marker", invalid, issues);
            if (room.tags().contains("boss") && room.markers(DungeonContentTypes.MarkerType.EXIT_PORTAL).isEmpty())
                error(DefinitionKind.ROOM, room.id(), "boss room has no exit_portal marker", invalid, issues);
        });
        connectors.values().forEach(connector -> {
            availability(connector.id(), connector.requirements().requiredMods(), connector.available(), issues);
            validateStructure(resources, DefinitionKind.CONNECTOR, connector.id(), connector.structure(), invalid, issues);
            validatePalette(DefinitionKind.CONNECTOR, connector.id(), connector.palette(), invalid, issues);
        });
        bosses.values().forEach(boss -> {
            availability(boss.id(), boss.requirements().requiredMods(), boss.available(), issues);
            if (boss.available() && !BuiltInRegistries.ENTITY_TYPE.containsKey(boss.entityType())) error(DefinitionKind.BOSS, boss.id(), "unknown entity '" + boss.entityType() + "'", invalid, issues);
        });
        detectFallbackCycles(DefinitionKind.THEME, themes, Theme::fallback, "theme", invalid, issues);
        detectFallbackCycles(DefinitionKind.ARCHETYPE, archetypes, Archetype::fallback, "archetype", invalid, issues);
        detectFallbackCycles(DefinitionKind.ROOM_POOL, pools, RoomPool::fallback, "room pool", invalid, issues);
        detectFallbackCycles(DefinitionKind.MOB_POOL, mobPools, MobPool::fallback, "mob pool", invalid, issues);
        detectFallbackCycles(DefinitionKind.ENCOUNTER_POOL, encounterPools, EncounterPool::fallback, "encounter pool", invalid, issues);
        detectFallbackCycles(DefinitionKind.BOSS_POOL, bossPools, BossPool::fallback, "boss pool", invalid, issues);
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

    private static void optionalLootProfile(ResourceLocation owner, String source, ResourceLocation profile,
                                            List<Issue> issues) {
        if (profile != null && !LootProfileManager.containsProfile(profile)) {
            issues.add(new Issue(Severity.WARNING, owner,
                source + " references missing loot profile '" + profile + "'; runtime fallback will be used"));
        }
    }

    private static void optionalLootTable(ResourceLocation owner, String source, ResourceLocation table,
                                          List<Issue> issues) {
        if (table != null && !LootProfileManager.containsLootTable(table)) {
            issues.add(new Issue(Severity.WARNING, owner,
                source + " references missing loot table '" + table + "'; runtime fallback will be used"));
        }
    }

    private static void optionalMobProfile(ResourceLocation owner, String source, ResourceLocation profile,
                                           List<Issue> issues) {
        if (profile != null && !MobProfileManager.containsProfile(profile)) {
            issues.add(new Issue(Severity.WARNING, owner,
                source + " references missing mob profile '" + profile + "'; runtime fallback will be used"));
        }
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

    private static boolean bossPoolUsable(BossPool pool) {
        if (!pool.available() || invalid(DefinitionKind.BOSS_POOL, pool.id())) return false;
        return pool.bosses().stream().map(WeightedId::id).map(snapshot.bosses()::get)
            .anyMatch(boss -> boss != null && boss.available() && !invalid(DefinitionKind.BOSS, boss.id()));
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

package dev.uapi.dungeons.loot;

import com.google.gson.JsonParser;
import dev.uapi.dungeons.DedicatedDungeonsMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Atomic immutable snapshot of datapack loot profiles and known loot-table resources. */
public final class LootProfileManager {
    public record ReloadReport(int profiles, int lootTables, List<String> errors, List<String> warnings,
                               long generation) {
        public ReloadReport {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }
        public boolean valid() { return errors.isEmpty(); }
    }

    private record Snapshot(Map<ResourceLocation, LootProfile> profiles, Set<ResourceLocation> lootTables,
                            long generation) {
        static Snapshot empty() { return new Snapshot(Map.of(), Set.of(), 0); }
    }

    private static volatile Snapshot snapshot = Snapshot.empty();
    private static volatile ReloadReport lastReport = new ReloadReport(0, 0, List.of(), List.of(), 0);

    private LootProfileManager() {}

    public static ReloadReport reload(ResourceManager manager) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<ResourceLocation, LootProfile> profiles = new LinkedHashMap<>();
        String root = "dedicated_dungeons/loot_profiles";
        for (Map.Entry<ResourceLocation, Resource> resource : manager.listResources(root,
            id -> id.getPath().endsWith(".json")).entrySet()) {
            ResourceLocation file = resource.getKey();
            String path = file.getPath().substring(root.length() + 1, file.getPath().length() - 5);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path);
            try (Reader reader = resource.getValue().openAsReader()) {
                profiles.put(id, LootProfile.parse(id, JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception exception) {
                errors.add(id + ": " + rootCause(exception));
            }
        }

        Set<ResourceLocation> tables = new HashSet<>();
        String tableRoot = "loot_table";
        for (ResourceLocation file : manager.listResources(tableRoot,
            id -> id.getPath().endsWith(".json")).keySet()) {
            String path = file.getPath().substring(tableRoot.length() + 1, file.getPath().length() - 5);
            tables.add(ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path));
        }
        validate(profiles, tables, errors, warnings);

        long generation = snapshot.generation() + 1;
        ReloadReport report = new ReloadReport(profiles.size(), tables.size(), errors, warnings, generation);
        lastReport = report;
        if (report.valid()) {
            snapshot = new Snapshot(Map.copyOf(profiles), Set.copyOf(tables), generation);
            DungeonLootResolver.clearWarnings();
        } else {
            DedicatedDungeonsMod.LOGGER.error(
                "Rejected loot-profile reload with {} errors; retaining generation {} containing {} profiles",
                errors.size(), snapshot.generation(), snapshot.profiles().size());
        }
        errors.forEach(value -> DedicatedDungeonsMod.LOGGER.error("Loot profile error: {}", value));
        warnings.forEach(value -> DedicatedDungeonsMod.LOGGER.warn("Loot profile warning: {}", value));
        DedicatedDungeonsMod.LOGGER.info("Loaded {} loot profiles and indexed {} loot tables (generation {})",
            report.valid() ? profiles.size() : snapshot.profiles().size(),
            report.valid() ? tables.size() : snapshot.lootTables().size(), snapshot.generation());
        return report;
    }

    public static ReloadReport report() {
        return lastReport;
    }

    public static long generation() {
        return snapshot.generation();
    }

    public static Map<ResourceLocation, LootProfile> profiles() {
        return snapshot.profiles();
    }

    public static boolean containsProfile(ResourceLocation id) {
        return snapshot.profiles().containsKey(id);
    }

    public static boolean containsLootTable(ResourceLocation id) {
        return snapshot.lootTables().contains(id);
    }

    public static Optional<ResourceLocation> resolve(ResourceLocation profileId, LootRole role, long seed) {
        Set<ResourceLocation> seen = new HashSet<>();
        ResourceLocation current = profileId;
        while (current != null && seen.add(current)) {
            LootProfile profile = snapshot.profiles().get(current);
            if (profile == null) return Optional.empty();
            LootProfile.Entry entry = profile.roles().get(role);
            if (entry != null) {
                ResourceLocation selected = entry.choose(seed ^ current.hashCode() ^ role.hashCode(),
                    snapshot.lootTables()::contains);
                if (selected != null) return Optional.of(selected);
            }
            current = profile.parent();
        }
        return Optional.empty();
    }

    static void validate(Map<ResourceLocation, LootProfile> profiles, Set<ResourceLocation> tables,
                         List<String> errors, List<String> warnings) {
        for (LootProfile profile : profiles.values()) {
            if (profile.parent() != null && !profiles.containsKey(profile.parent())) {
                warnings.add(profile.id() + ": missing parent profile '" + profile.parent() + "'");
            }
            profile.roles().forEach((role, entry) -> entry.tables().forEach(table -> {
                if (!tables.contains(table.table())) {
                    warnings.add(profile.id() + " role " + role + ": missing loot table '" + table.table() + "'");
                }
            }));
        }
        for (ResourceLocation start : profiles.keySet()) {
            Set<ResourceLocation> seen = new HashSet<>();
            ResourceLocation current = start;
            while (current != null && seen.add(current)) {
                LootProfile value = profiles.get(current);
                current = value == null ? null : value.parent();
            }
            if (current != null) errors.add(start + ": loot profile parent cycle through '" + current + "'");
        }
    }

    static void replaceForTests(Map<ResourceLocation, LootProfile> profiles, Set<ResourceLocation> tables) {
        snapshot = new Snapshot(Map.copyOf(profiles), Set.copyOf(tables), snapshot.generation() + 1);
        DungeonLootResolver.clearWarnings();
    }

    private static String rootCause(Throwable throwable) {
        while (throwable.getCause() != null) throwable = throwable.getCause();
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }
}

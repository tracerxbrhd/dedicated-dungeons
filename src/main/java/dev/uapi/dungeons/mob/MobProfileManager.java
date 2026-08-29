package dev.uapi.dungeons.mob;

import com.google.gson.JsonParser;
import dev.uapi.dungeons.DedicatedDungeonsMod;
import net.minecraft.core.registries.BuiltInRegistries;
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

/** Atomic immutable snapshot of valid datapack mob profiles. Broken resources are isolated and rejected. */
public final class MobProfileManager {
    public record ReloadReport(int discovered, int accepted, List<String> errors, List<String> warnings,
                               long generation) {
        public ReloadReport {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
        }
    }

    public record Selection(ResourceLocation profile, MobProfile.Entry entry, MobProfile.Scaling scaling) {}

    private record Snapshot(Map<ResourceLocation, MobProfile> profiles, long generation) {
        static Snapshot empty() { return new Snapshot(Map.of(), 0); }
    }

    private static volatile Snapshot snapshot = Snapshot.empty();
    private static volatile ReloadReport lastReport = new ReloadReport(0, 0, List.of(), List.of(), 0);
    private static volatile java.util.function.Predicate<ResourceLocation> entityAvailability =
        MobProfileManager::registeredEntity;

    private MobProfileManager() {}

    public static ReloadReport reload(ResourceManager manager) {
        entityAvailability = MobProfileManager::registeredEntity;
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<ResourceLocation, MobProfile> parsed = new LinkedHashMap<>();
        String root = "dedicated_dungeons/mob_profiles";
        int discovered = 0;
        for (Map.Entry<ResourceLocation, Resource> resource : manager.listResources(root,
            id -> id.getPath().endsWith(".json")).entrySet()) {
            discovered++;
            ResourceLocation file = resource.getKey();
            String path = file.getPath().substring(root.length() + 1, file.getPath().length() - 5);
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path);
            try (Reader reader = resource.getValue().openAsReader()) {
                parsed.put(id, MobProfile.parse(id, JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception exception) {
                errors.add(id + ": " + rootCause(exception));
            }
        }

        Set<ResourceLocation> rejected = validate(parsed, errors, warnings);
        boolean changed;
        do {
            changed = false;
            for (MobProfile profile : parsed.values()) {
                if (!rejected.contains(profile.id()) && profile.parent() != null
                    && rejected.contains(profile.parent())) {
                    rejected.add(profile.id());
                    errors.add(profile.id() + ": parent profile '" + profile.parent() + "' was rejected");
                    changed = true;
                }
            }
        } while (changed);
        Map<ResourceLocation, MobProfile> accepted = new LinkedHashMap<>(parsed);
        rejected.forEach(accepted::remove);

        boolean retainPrevious = discovered > 0 && accepted.isEmpty() && !errors.isEmpty()
            && !snapshot.profiles().isEmpty();
        long generation = retainPrevious ? snapshot.generation() : snapshot.generation() + 1;
        if (!retainPrevious) {
            snapshot = new Snapshot(Map.copyOf(accepted), generation);
            DungeonMobResolver.clearWarnings();
        }
        lastReport = new ReloadReport(discovered,
            retainPrevious ? snapshot.profiles().size() : accepted.size(), errors, warnings, generation);
        errors.forEach(value -> DedicatedDungeonsMod.LOGGER.error("Mob profile error: {}", value));
        warnings.forEach(value -> DedicatedDungeonsMod.LOGGER.warn("Mob profile warning: {}", value));
        DedicatedDungeonsMod.LOGGER.info(
            "{} {} of {} mob profiles (generation {}, {} rejected diagnostics, {} warnings)",
            retainPrevious ? "Retained" : "Loaded",
            retainPrevious ? snapshot.profiles().size() : accepted.size(),
            discovered, generation, errors.size(), warnings.size());
        return lastReport;
    }

    public static ReloadReport report() { return lastReport; }
    public static long generation() { return snapshot.generation(); }
    public static Map<ResourceLocation, MobProfile> profiles() { return snapshot.profiles(); }
    public static boolean containsProfile(ResourceLocation id) { return snapshot.profiles().containsKey(id); }

    public static boolean hasUsableRole(ResourceLocation profile, SpawnRole role, int difficulty,
                                        int partySize, boolean arena) {
        return !effectiveEntries(profile, role).stream()
            .filter(value -> value.available(difficulty, partySize, arena))
            .filter(value -> entityAvailability.test(value.entity()))
            .toList().isEmpty();
    }

    public static Optional<Selection> resolve(ResourceLocation profileId, SpawnRole role, int difficulty,
                                              int partySize, boolean arena, long seed) {
        MobProfile profile = snapshot.profiles().get(profileId);
        if (profile == null) return Optional.empty();
        List<MobProfile.Entry> available = effectiveEntries(profileId, role).stream()
            .filter(entry -> entry.available(difficulty, partySize, arena))
            .filter(entry -> entityAvailability.test(entry.entity()))
            .toList();
        long total = available.stream().mapToLong(MobProfile.Entry::weight).sum();
        if (total <= 0) return Optional.empty();
        long selected = Math.floorMod(mix64(seed ^ profileId.hashCode() ^ role.hashCode()), total);
        for (MobProfile.Entry entry : available) {
            selected -= entry.weight();
            if (selected < 0) return Optional.of(new Selection(profileId, entry, profile.scaling()));
        }
        return Optional.of(new Selection(profileId, available.getLast(), profile.scaling()));
    }

    static List<MobProfile.Entry> effectiveEntries(ResourceLocation profileId, SpawnRole role) {
        List<MobProfile> chain = new ArrayList<>();
        Set<ResourceLocation> seen = new HashSet<>();
        ResourceLocation current = profileId;
        while (current != null && seen.add(current)) {
            MobProfile profile = snapshot.profiles().get(current);
            if (profile == null) break;
            chain.add(profile);
            current = profile.parent();
        }
        java.util.Collections.reverse(chain);
        List<MobProfile.Entry> result = new ArrayList<>();
        for (MobProfile profile : chain) {
            MobProfile.RoleEntries local = profile.roles().get(role);
            if (local == null) continue;
            if (local.replace()) result.clear();
            result.addAll(local.entries());
        }
        return List.copyOf(result);
    }

    static Set<ResourceLocation> validate(Map<ResourceLocation, MobProfile> profiles,
                                          List<String> errors, List<String> warnings) {
        Set<ResourceLocation> rejected = new HashSet<>();
        for (MobProfile profile : profiles.values()) {
            if (profile.parent() != null && !profiles.containsKey(profile.parent())) {
                errors.add(profile.id() + ": missing parent profile '" + profile.parent() + "'");
                rejected.add(profile.id());
            }
            profile.roles().forEach((role, values) -> values.entries().forEach(entry -> {
                if (entry.requiredMods().stream().allMatch(dev.uapi.integration.IntegrationService::isLoaded)
                    && !entityAvailability.test(entry.entity())) {
                    warnings.add(profile.id() + " role " + role
                        + ": entity '" + entry.entity() + "' is not currently registered; entry is unavailable");
                }
            }));
        }
        for (ResourceLocation start : profiles.keySet()) {
            Set<ResourceLocation> seen = new HashSet<>();
            ResourceLocation current = start;
            while (current != null && seen.add(current)) {
                MobProfile profile = profiles.get(current);
                current = profile == null ? null : profile.parent();
            }
            if (current != null) {
                errors.add(start + ": mob profile parent cycle through '" + current + "'");
                rejected.addAll(seen);
            }
        }
        return rejected;
    }

    static void replaceForTests(Map<ResourceLocation, MobProfile> profiles) {
        snapshot = new Snapshot(Map.copyOf(profiles), snapshot.generation() + 1);
        entityAvailability = ignored -> true;
        DungeonMobResolver.clearWarnings();
    }

    static boolean entityRegistered(ResourceLocation id) {
        return entityAvailability.test(id);
    }

    private static boolean registeredEntity(ResourceLocation id) {
        return BuiltInRegistries.ENTITY_TYPE.containsKey(id);
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }

    private static String rootCause(Throwable throwable) {
        while (throwable.getCause() != null) throwable = throwable.getCause();
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }
}

package dev.uapi.dungeons.content;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public final class DungeonSelector {
    public record Candidate(ResourceLocation id, DungeonDefinition definition, long weight, boolean fallback) {}

    private DungeonSelector() {}

    public static List<Candidate> candidates(Map<ResourceLocation, DungeonDefinition> definitions,
                                             Map<ResourceLocation, LevelProfile> profiles,
                                             int effectiveLevel, ResourceLocation dimension,
                                             Predicate<String> modLoaded) {
        List<Candidate> direct = collect(definitions, profiles, effectiveLevel, dimension, modLoaded, false);
        if (!direct.isEmpty()) return direct;
        List<Candidate> available = collect(definitions, profiles, effectiveLevel, dimension, modLoaded, true);
        if (available.isEmpty()) return List.of();
        int nearest = available.stream().mapToInt(candidate -> distanceToRange(effectiveLevel, candidate.definition())).min().orElseThrow();
        return available.stream().filter(candidate -> distanceToRange(effectiveLevel, candidate.definition()) == nearest)
            .map(candidate -> new Candidate(candidate.id(), candidate.definition(), candidate.weight(), true)).toList();
    }

    public static Optional<Candidate> select(Map<ResourceLocation, DungeonDefinition> definitions,
                                             Map<ResourceLocation, LevelProfile> profiles,
                                             int effectiveLevel, ResourceLocation dimension,
                                             Predicate<String> modLoaded, RandomSource random) {
        List<Candidate> candidates = candidates(definitions, profiles, effectiveLevel, dimension, modLoaded);
        long total = candidates.stream().mapToLong(Candidate::weight).sum();
        if (total <= 0) return Optional.empty();
        long selected = Math.floorMod(random.nextLong(), total);
        for (Candidate candidate : candidates) {
            selected -= candidate.weight();
            if (selected < 0) return Optional.of(candidate);
        }
        return Optional.of(candidates.getLast());
    }

    private static List<Candidate> collect(Map<ResourceLocation, DungeonDefinition> definitions,
                                           Map<ResourceLocation, LevelProfile> profiles,
                                           int level, ResourceLocation dimension,
                                           Predicate<String> modLoaded, boolean allowOutsideRange) {
        List<Candidate> result = new ArrayList<>();
        definitions.forEach((id, definition) -> {
            LevelProfile profile = profiles.get(definition.levelProfile());
            if (profile == null || !definition.allowsDimension(dimension)
                || definition.requirements().requiredMods().stream().anyMatch(modLoaded.negate())) return;
            boolean inside = level >= definition.minimumLevel() && level <= definition.maximumLevel();
            if (!inside && (!allowOutsideRange || !profile.nearestTierFallback())) return;
            double multiplier = profile.multiplier(level, definition.recommendedLevel());
            long weight = Math.max(1L, Math.round(definition.baseWeight() * multiplier * 1_000.0));
            result.add(new Candidate(id, definition, weight, !inside));
        });
        result.sort(Comparator.comparing(candidate -> candidate.id().toString()));
        return List.copyOf(result);
    }

    private static int distanceToRange(int level, DungeonDefinition definition) {
        if (level < definition.minimumLevel()) return definition.minimumLevel() - level;
        if (level > definition.maximumLevel()) return level - definition.maximumLevel();
        return 0;
    }
}

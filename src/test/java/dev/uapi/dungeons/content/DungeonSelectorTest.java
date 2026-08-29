package dev.uapi.dungeons.content;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DungeonSelectorTest {
    private static final ResourceLocation PROFILE = id("standard");
    private static final LevelProfile LEVEL_PROFILE = new LevelProfile(1, 2, true, 0.1, 0.05, 1.0);

    @Test
    void filtersDimensionAndRequiredMods() {
        var definitions = Map.of(
            id("valid"), definition(5, 15, List.of(), List.of(ResourceLocation.withDefaultNamespace("overworld"))),
            id("wrong_dimension"), definition(5, 15, List.of(), List.of(ResourceLocation.withDefaultNamespace("the_nether"))),
            id("missing_mod"), definition(5, 15, List.of("not_installed"), List.of())
        );
        var candidates = DungeonSelector.candidates(definitions, Map.of(PROFILE, LEVEL_PROFILE), 10,
            ResourceLocation.withDefaultNamespace("overworld"), mod -> false);
        assertEquals(List.of(id("valid")), candidates.stream().map(DungeonSelector.Candidate::id).toList());
    }

    @Test
    void usesNearestFallbackAndSelectionIsSeeded() {
        var definitions = Map.of(
            id("low"), definition(0, 10, List.of(), List.of()),
            id("high"), definition(30, 40, List.of(), List.of())
        );
        var profiles = Map.of(PROFILE, LEVEL_PROFILE);
        var candidates = DungeonSelector.candidates(definitions, profiles, 20,
            ResourceLocation.withDefaultNamespace("overworld"), mod -> true);
        assertFalse(candidates.isEmpty());
        assertTrue(candidates.stream().allMatch(DungeonSelector.Candidate::fallback));
        var first = DungeonSelector.select(definitions, profiles, 20, ResourceLocation.withDefaultNamespace("overworld"),
            mod -> true, RandomSource.create(12345L)).orElseThrow();
        var second = DungeonSelector.select(definitions, profiles, 20, ResourceLocation.withDefaultNamespace("overworld"),
            mod -> true, RandomSource.create(12345L)).orElseThrow();
        assertEquals(first.id(), second.id());
    }

    private static DungeonDefinition definition(int minimum, int maximum, List<String> requiredMods,
                                                 List<ResourceLocation> dimensions) {
        return new DungeonDefinition(1, "name", "description", List.of("test"), List.of(), id("basic"), PROFILE,
            (minimum + maximum) / 2, minimum, maximum, 10,
            new DungeonDefinition.Pools(id("encounters"), id("bosses"), id("rewards")),
            new DungeonDefinition.Rules(3, 5, 1, 5, 0, 10, "boss", 30), dimensions,
            new DungeonDefinition.Requirements(requiredMods, List.of()));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", path);
    }
}

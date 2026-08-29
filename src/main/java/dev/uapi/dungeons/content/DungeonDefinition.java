package dev.uapi.dungeons.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.uapi.integration.IntegrationService;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Versioned top-level dungeon definition. Active instances persist the selected plan as a snapshot. */
public record DungeonDefinition(
    int formatVersion,
    String displayName,
    String description,
    List<String> themes,
    List<String> tags,
    ResourceLocation archetype,
    ResourceLocation levelProfile,
    int recommendedLevel,
    int minimumLevel,
    int maximumLevel,
    int baseWeight,
    Pools pools,
    Rules rules,
    List<ResourceLocation> allowedDimensions,
    Requirements requirements
) {
    public static final int CURRENT_FORMAT = 1;
    private static final ResourceLocation STANDARD_LEVEL_PROFILE =
        ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", "standard");

    public static final Codec<Pools> POOLS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        ResourceLocation.CODEC.fieldOf("encounter_pool").forGetter(Pools::encounterPool),
        ResourceLocation.CODEC.fieldOf("boss_pool").forGetter(Pools::bossPool),
        ResourceLocation.CODEC.fieldOf("reward_pool").forGetter(Pools::rewardPool),
        ResourceLocation.CODEC.optionalFieldOf("loot_profile").forGetter(
            value -> Optional.ofNullable(value.lootProfile())),
        Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC).optionalFieldOf("loot_overrides", Map.of())
            .forGetter(Pools::lootOverrides),
        ResourceLocation.CODEC.optionalFieldOf("mob_profile").forGetter(
            value -> Optional.ofNullable(value.mobProfile())),
        Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC).optionalFieldOf("mob_role_overrides", Map.of())
            .forGetter(Pools::mobRoleOverrides)
    ).apply(instance, (encounters, bosses, rewards, lootProfile, lootOverrides, mobProfile, mobOverrides) ->
        new Pools(encounters, bosses, rewards, lootProfile.orElse(null), lootOverrides,
            mobProfile.orElse(null), mobOverrides)));

    public static final Codec<Rules> RULES_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("minimum_rooms", 3).forGetter(Rules::minimumRooms),
        Codec.INT.optionalFieldOf("maximum_rooms", 7).forGetter(Rules::maximumRooms),
        Codec.INT.optionalFieldOf("minimum_depth", 2).forGetter(Rules::minimumDepth),
        Codec.INT.optionalFieldOf("maximum_depth", 8).forGetter(Rules::maximumDepth),
        Codec.INT.optionalFieldOf("branches", 1).forGetter(Rules::branches),
        Codec.INT.optionalFieldOf("generation_attempts", 24).forGetter(Rules::generationAttempts),
        Codec.STRING.optionalFieldOf("clear_condition", "boss").forGetter(Rules::clearCondition),
        Codec.INT.optionalFieldOf("cleanup_delay_seconds", 30).forGetter(Rules::cleanupDelaySeconds)
    ).apply(instance, Rules::new));

    public static final Codec<Requirements> REQUIREMENTS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.STRING.listOf().optionalFieldOf("required_mods", List.of()).forGetter(Requirements::requiredMods),
        Codec.STRING.listOf().optionalFieldOf("optional_mods", List.of()).forGetter(Requirements::optionalMods)
    ).apply(instance, Requirements::new));

    public static final Codec<DungeonDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("format_version", CURRENT_FORMAT).forGetter(DungeonDefinition::formatVersion),
        Codec.STRING.fieldOf("display_name").forGetter(DungeonDefinition::displayName),
        Codec.STRING.fieldOf("description").forGetter(DungeonDefinition::description),
        Codec.STRING.listOf().optionalFieldOf("themes", List.of()).forGetter(DungeonDefinition::themes),
        Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(DungeonDefinition::tags),
        ResourceLocation.CODEC.fieldOf("archetype").forGetter(DungeonDefinition::archetype),
        ResourceLocation.CODEC.optionalFieldOf("level_profile", STANDARD_LEVEL_PROFILE).forGetter(DungeonDefinition::levelProfile),
        Codec.INT.fieldOf("recommended_level").forGetter(DungeonDefinition::recommendedLevel),
        Codec.INT.fieldOf("minimum_level").forGetter(DungeonDefinition::minimumLevel),
        Codec.INT.fieldOf("maximum_level").forGetter(DungeonDefinition::maximumLevel),
        Codec.INT.optionalFieldOf("base_weight", 1).forGetter(DungeonDefinition::baseWeight),
        POOLS_CODEC.fieldOf("pools").forGetter(DungeonDefinition::pools),
        RULES_CODEC.optionalFieldOf("rules", new Rules(3, 7, 2, 8, 1, 24, "boss", 30)).forGetter(DungeonDefinition::rules),
        ResourceLocation.CODEC.listOf().optionalFieldOf("allowed_dimensions", List.of()).forGetter(DungeonDefinition::allowedDimensions),
        REQUIREMENTS_CODEC.optionalFieldOf("requirements", new Requirements(List.of(), List.of())).forGetter(DungeonDefinition::requirements)
    ).apply(instance, DungeonDefinition::new));

    public DungeonDefinition {
        if (formatVersion != CURRENT_FORMAT) {
            throw new IllegalArgumentException(formatVersion > CURRENT_FORMAT
                ? "unsupported newer format_version " + formatVersion + "; current version is " + CURRENT_FORMAT
                : "unsupported legacy format_version " + formatVersion + "; migrate to " + CURRENT_FORMAT);
        }
        themes = List.copyOf(themes);
        tags = List.copyOf(tags);
        allowedDimensions = List.copyOf(allowedDimensions);
        if (minimumLevel < 0 || maximumLevel < minimumLevel || recommendedLevel < minimumLevel
            || recommendedLevel > maximumLevel) throw new IllegalArgumentException("invalid level range");
        if (baseWeight < 1 || baseWeight > 1_000_000) throw new IllegalArgumentException("base_weight must be in [1,1000000]");
    }

    public boolean available() {
        return requirements.requiredMods().stream().allMatch(IntegrationService::isLoaded);
    }

    public boolean allowsDimension(ResourceLocation dimension) {
        return allowedDimensions.isEmpty() || allowedDimensions.contains(dimension);
    }

    public record Pools(ResourceLocation encounterPool, ResourceLocation bossPool, ResourceLocation rewardPool,
                        ResourceLocation lootProfile, Map<String, ResourceLocation> lootOverrides,
                        ResourceLocation mobProfile, Map<String, ResourceLocation> mobRoleOverrides) {
        public Pools {
            lootOverrides = Map.copyOf(lootOverrides);
            lootOverrides.keySet().forEach(dev.uapi.dungeons.loot.LootRole::parse);
            mobRoleOverrides = Map.copyOf(mobRoleOverrides);
            mobRoleOverrides.keySet().forEach(dev.uapi.dungeons.mob.SpawnRole::parse);
        }
        public Pools(ResourceLocation encounterPool, ResourceLocation bossPool, ResourceLocation rewardPool) {
            this(encounterPool, bossPool, rewardPool, null, Map.of(), null, Map.of());
        }
        public Pools(ResourceLocation encounterPool, ResourceLocation bossPool, ResourceLocation rewardPool,
                     ResourceLocation lootProfile, Map<String, ResourceLocation> lootOverrides) {
            this(encounterPool, bossPool, rewardPool, lootProfile, lootOverrides, null, Map.of());
        }
    }

    public record Rules(int minimumRooms, int maximumRooms, int minimumDepth, int maximumDepth,
                        int branches, int generationAttempts, String clearCondition, int cleanupDelaySeconds) {
        public Rules {
            if (minimumRooms < 1 || maximumRooms < minimumRooms) throw new IllegalArgumentException("invalid room range");
            if (minimumDepth < 0 || maximumDepth < minimumDepth) throw new IllegalArgumentException("invalid depth range");
            if (branches < 0 || branches > 64) throw new IllegalArgumentException("branches must be in [0,64]");
            if (generationAttempts < 1 || generationAttempts > 256) throw new IllegalArgumentException("generation_attempts must be in [1,256]");
            if (!List.of("boss", "all_encounters", "boss_and_encounters").contains(clearCondition))
                throw new IllegalArgumentException("unknown clear_condition '" + clearCondition + "'");
            if (cleanupDelaySeconds < 0 || cleanupDelaySeconds > 86_400)
                throw new IllegalArgumentException("cleanup_delay_seconds must be in [0,86400]");
        }
    }

    public record Requirements(List<String> requiredMods, List<String> optionalMods) {
        public Requirements {
            requiredMods = validateModIds(requiredMods, "required_mods");
            optionalMods = validateModIds(optionalMods, "optional_mods");
        }

        private static List<String> validateModIds(List<String> values, String field) {
            for (String value : values) {
                if (!value.matches("[a-z][a-z0-9_]{1,63}"))
                    throw new IllegalArgumentException("invalid mod id in " + field + ": " + value);
            }
            return List.copyOf(values);
        }
    }
}

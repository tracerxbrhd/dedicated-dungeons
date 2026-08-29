package dev.uapi.dungeons.content;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record LevelProfile(int formatVersion, int tolerance, boolean nearestTierFallback,
                           double distancePenalty, double minimumMultiplier, double exponent) {
    public static final Codec<LevelProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.optionalFieldOf("format_version", DungeonDefinition.CURRENT_FORMAT).forGetter(LevelProfile::formatVersion),
        Codec.INT.optionalFieldOf("tolerance", 4).forGetter(LevelProfile::tolerance),
        Codec.BOOL.optionalFieldOf("nearest_tier_fallback", true).forGetter(LevelProfile::nearestTierFallback),
        Codec.DOUBLE.optionalFieldOf("distance_penalty", 0.08).forGetter(LevelProfile::distancePenalty),
        Codec.DOUBLE.optionalFieldOf("minimum_multiplier", 0.05).forGetter(LevelProfile::minimumMultiplier),
        Codec.DOUBLE.optionalFieldOf("exponent", 1.0).forGetter(LevelProfile::exponent)
    ).apply(instance, LevelProfile::new));

    public LevelProfile {
        if (formatVersion != DungeonDefinition.CURRENT_FORMAT)
            throw new IllegalArgumentException("unsupported format_version " + formatVersion);
        if (tolerance < 0 || tolerance > 10_000) throw new IllegalArgumentException("tolerance must be in [0,10000]");
        if (!Double.isFinite(distancePenalty) || distancePenalty < 0)
            throw new IllegalArgumentException("distance_penalty must be finite and non-negative");
        if (!Double.isFinite(minimumMultiplier) || minimumMultiplier <= 0 || minimumMultiplier > 1)
            throw new IllegalArgumentException("minimum_multiplier must be in (0,1]");
        if (!Double.isFinite(exponent) || exponent <= 0) throw new IllegalArgumentException("exponent must be positive");
    }

    public double multiplier(int effectiveLevel, int recommendedLevel) {
        int distance = Math.abs(effectiveLevel - recommendedLevel);
        if (distance <= tolerance) return 1.0;
        double adjusted = distance - tolerance;
        return Math.max(minimumMultiplier, 1.0 / Math.pow(1.0 + adjusted * distancePenalty, exponent));
    }
}

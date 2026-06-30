package dev.underworld.dungeons.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class DungeonServerConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue RANDOM_PORTALS;
    public static final ModConfigSpec.IntValue PORTAL_CHECK_INTERVAL;
    public static final ModConfigSpec.IntValue PORTAL_CHECK_RANDOM_TICKS;
    public static final ModConfigSpec.IntValue MAX_ACTIVE_PORTALS;
    public static final ModConfigSpec.IntValue MIN_PORTAL_DISTANCE;
    public static final ModConfigSpec.IntValue MAX_PORTAL_DISTANCE;
    public static final ModConfigSpec.ConfigValue<String> LIGHTNING_MODE;
    public static final ModConfigSpec.BooleanValue SPAWN_MOBS_ON_EXPIRE;
    public static final ModConfigSpec.IntValue EXPIRED_MOB_COUNT;
    public static final ModConfigSpec.IntValue EXPIRED_MOB_LIFETIME;
    public static final ModConfigSpec.ConfigValue<String> ALLOWED_DIMENSIONS;
    public static final ModConfigSpec.ConfigValue<String> FAILURE_MOB_POOL;
    public static final ModConfigSpec.IntValue APPEARANCE_LIGHTNING_STRIKES;
    public static final ModConfigSpec.IntValue FAILURE_LIGHTNING_STRIKES;
    public static final ModConfigSpec.IntValue LIGHTNING_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue PORTAL_BAR_RANGE;
    public static final ModConfigSpec.ConfigValue<String> RANDOM_PORTAL_ACCESS;
    public static final ModConfigSpec.ConfigValue<String> PERSONAL_PORTAL_ACCESS;
    public static final ModConfigSpec.IntValue NEARBY_ACCESS_RADIUS;
    public static final ModConfigSpec.BooleanValue ALLOW_SPECTATOR_ENTRY;
    public static final ModConfigSpec.BooleanValue ALLOW_LATE_JOIN;
    public static final ModConfigSpec.IntValue COOLDOWN_RANDOM_MIN_SECONDS;
    public static final ModConfigSpec.IntValue COOLDOWN_RANDOM_MAX_SECONDS;
    public static final ModConfigSpec.IntValue COOLDOWN_E_SECONDS;
    public static final ModConfigSpec.IntValue COOLDOWN_D_SECONDS;
    public static final ModConfigSpec.IntValue COOLDOWN_C_SECONDS;
    public static final ModConfigSpec.IntValue COOLDOWN_B_SECONDS;
    public static final ModConfigSpec.IntValue COOLDOWN_A_SECONDS;
    public static final ModConfigSpec.IntValue COOLDOWN_S_SECONDS;
    public static final ModConfigSpec.IntValue COOLDOWN_ANOMALY_SECONDS;
    public static final ModConfigSpec.DoubleValue PORTAL_EFFECT_INTENSITY;
    public static final ModConfigSpec.BooleanValue CONSUME_KEY;
    public static final ModConfigSpec.BooleanValue DEBUG_ITEMS_ENABLED;
    public static final ModConfigSpec.IntValue INSTANCE_SLOT_SPACING;
    public static final ModConfigSpec.IntValue MAX_DUNGEON_DIAMETER;
    public static final ModConfigSpec.IntValue MAX_GENERATED_PIECES;
    public static final ModConfigSpec.IntValue GENERATION_ATTEMPTS;
    public static final ModConfigSpec.BooleanValue PROTECT_BLOCKS;
    public static final ModConfigSpec.BooleanValue PROTECT_EXPLOSIONS;
    public static final ModConfigSpec.BooleanValue PROTECT_FIRE_AND_FLUIDS;
    public static final ModConfigSpec.BooleanValue PROTECT_TELEPORTS;
    public static final ModConfigSpec.BooleanValue ENFORCE_INSTANCE_BOUNDS;
    public static final ModConfigSpec.IntValue BOSS_DUNGEON_WEIGHT;
    public static final ModConfigSpec.IntValue SURVIVAL_ARENA_WEIGHT;
    public static final ModConfigSpec.IntValue SURVIVAL_PITY_AFTER_BOSS_PORTALS;
    public static final ModConfigSpec.ConfigValue<String> SURVIVAL_ARENA_ROOM;
    public static final ModConfigSpec.IntValue SURVIVAL_MAX_WAVES;
    public static final ModConfigSpec.IntValue SURVIVAL_BASE_MOBS;
    public static final ModConfigSpec.IntValue SURVIVAL_MOBS_PER_WAVE;
    public static final ModConfigSpec.IntValue SURVIVAL_WAVE_DELAY_SECONDS;
    public static final ModConfigSpec.IntValue SURVIVAL_ELITE_EVERY_WAVES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("portals");
        RANDOM_PORTALS = builder.define("randomPortals", true);
        PORTAL_CHECK_INTERVAL = builder.comment("Ticks between candidate checks; 6000 is five minutes.")
            .defineInRange("checkIntervalTicks", 6000, 200, 72000);
        PORTAL_CHECK_RANDOM_TICKS = builder.comment("Additional random delay between world portal checks.")
            .defineInRange("checkRandomDelayTicks", 6000, 0, 144000);
        MAX_ACTIVE_PORTALS = builder.comment("0 means unlimited. This limit never applies to commands or keys.")
            .defineInRange("maxActivePortals", 0, 0, 100000);
        MIN_PORTAL_DISTANCE = builder.defineInRange("minimumDistance", 16, 8, 256);
        MAX_PORTAL_DISTANCE = builder.defineInRange("maximumDistance", 32, 8, 512);
        ALLOWED_DIMENSIONS = builder.comment("Comma-separated dimension ids.")
            .define("allowedDimensions", "minecraft:overworld");
        LIGHTNING_MODE = builder.comment("DECORATIVE, REAL or OFF.")
            .define("lightningMode", "DECORATIVE");
        APPEARANCE_LIGHTNING_STRIKES = builder.defineInRange("appearanceLightningStrikes", 3, 0, 32);
        FAILURE_LIGHTNING_STRIKES = builder.defineInRange("failureLightningStrikes", 5, 0, 32);
        LIGHTNING_INTERVAL_TICKS = builder.defineInRange("lightningIntervalTicks", 8, 1, 200);
        PORTAL_BAR_RANGE = builder.defineInRange("countdownBarRange", 50, 8, 256);
        RANDOM_PORTAL_ACCESS = builder.comment("PUBLIC_NEARBY, OWNER_ONLY, PARTY_OR_NEARBY or PARTY.",
            "PARTY currently falls back to owner-only until a party provider is installed.")
            .define("randomPortalAccess", "PUBLIC_NEARBY");
        PERSONAL_PORTAL_ACCESS = builder.comment("Access policy for portals opened by dungeon keys.")
            .define("personalPortalAccess", "OWNER_ONLY");
        NEARBY_ACCESS_RADIUS = builder.comment("Maximum distance from a portal at which public access is valid.")
            .defineInRange("nearbyAccessRadius", 64, 2, 256);
        ALLOW_SPECTATOR_ENTRY = builder.define("allowSpectatorEntry", false);
        ALLOW_LATE_JOIN = builder.comment("Allow new participants to enter an already running instance.")
            .define("allowLateJoin", true);
        SPAWN_MOBS_ON_EXPIRE = builder.define("spawnMobsOnExpiredPortal", true);
        EXPIRED_MOB_COUNT = builder.defineInRange("expiredPortalMobCount", 3, 0, 16);
        EXPIRED_MOB_LIFETIME = builder.defineInRange("expiredMobLifetimeSeconds", 120, 10, 3600);
        FAILURE_MOB_POOL = builder.define("failureMobPool", "dedicated_dungeons:default");
        builder.pop();
        builder.push("randomPortalCooldowns");
        COOLDOWN_RANDOM_MIN_SECONDS = builder.defineInRange("randomExtraMinSeconds", 30, 0, 86400);
        COOLDOWN_RANDOM_MAX_SECONDS = builder.defineInRange("randomExtraMaxSeconds", 300, 0, 86400);
        COOLDOWN_E_SECONDS = builder.defineInRange("rankESeconds", 300, 0, 604800);
        COOLDOWN_D_SECONDS = builder.defineInRange("rankDSeconds", 600, 0, 604800);
        COOLDOWN_C_SECONDS = builder.defineInRange("rankCSeconds", 1200, 0, 604800);
        COOLDOWN_B_SECONDS = builder.defineInRange("rankBSeconds", 2400, 0, 604800);
        COOLDOWN_A_SECONDS = builder.defineInRange("rankASeconds", 4800, 0, 604800);
        COOLDOWN_S_SECONDS = builder.defineInRange("rankSSeconds", 9600, 0, 604800);
        COOLDOWN_ANOMALY_SECONDS = builder.defineInRange("rankAnomalySeconds", 19200, 0, 604800);
        builder.pop();
        builder.push("portalEffects");
        PORTAL_EFFECT_INTENSITY = builder.comment("Global particle/sound intensity multiplier; 0 disables particles.")
            .defineInRange("intensity", 1.0, 0.0, 4.0);
        builder.pop();
        builder.push("items");
        CONSUME_KEY = builder.define("consumeKey", true);
        DEBUG_ITEMS_ENABLED = builder.define("debugItemsEnabled", true);
        builder.pop();
        builder.push("instances");
        INSTANCE_SLOT_SPACING = builder.comment("Distance between isolated dungeon slots in the technical dimension.")
            .defineInRange("slotSpacing", 1024, 256, 16384);
        MAX_DUNGEON_DIAMETER = builder.comment("Maximum generated width/depth, including padding.")
            .defineInRange("maxDungeonDiameter", 384, 32, 2048);
        MAX_GENERATED_PIECES = builder.defineInRange("maxGeneratedPieces", 64, 3, 512);
        GENERATION_ATTEMPTS = builder.defineInRange("generationAttempts", 24, 1, 256);
        PROTECT_BLOCKS = builder.define("protectBlocks", true);
        PROTECT_EXPLOSIONS = builder.define("protectExplosions", true);
        PROTECT_FIRE_AND_FLUIDS = builder.define("protectFireAndFluids", true);
        PROTECT_TELEPORTS = builder.define("protectTeleports", true);
        ENFORCE_INSTANCE_BOUNDS = builder.define("enforceBounds", true);
        builder.pop();
        builder.push("survivalArena");
        BOSS_DUNGEON_WEIGHT = builder.comment("Random world portal selection weight for boss dungeons.")
            .defineInRange("bossDungeonWeight", 60, 0, 100000);
        SURVIVAL_ARENA_WEIGHT = builder.comment("Random world portal selection weight for survival arenas.")
            .defineInRange("survivalArenaWeight", 40, 0, 100000);
        SURVIVAL_PITY_AFTER_BOSS_PORTALS = builder.comment(
            "Force the next random portal to be Survival Arena after this many consecutive boss portals.",
            "0 disables pity protection.")
            .defineInRange("pityAfterBossPortals", 2, 0, 1000);
        SURVIVAL_ARENA_ROOM = builder.comment("Data-driven arena room definition used by Survival Arena.")
            .define("arenaRoom", "dedicated_dungeons:survival_arena");
        SURVIVAL_MAX_WAVES = builder.defineInRange("maxWaves", 8, 1, 100);
        SURVIVAL_BASE_MOBS = builder.defineInRange("baseMobCount", 3, 1, 64);
        SURVIVAL_MOBS_PER_WAVE = builder.defineInRange("additionalMobsPerWave", 1, 0, 32);
        SURVIVAL_WAVE_DELAY_SECONDS = builder.defineInRange("waveDelaySeconds", 5, 0, 300);
        SURVIVAL_ELITE_EVERY_WAVES = builder.comment("0 disables elite waves.")
            .defineInRange("eliteEveryWaves", 3, 0, 100);
        builder.pop();
        SPEC = builder.build();
    }

    private DungeonServerConfig() {}
}

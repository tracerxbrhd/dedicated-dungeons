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
    public static final ModConfigSpec.IntValue COOLDOWN_RANDOM_MIN_SECONDS;
    public static final ModConfigSpec.IntValue COOLDOWN_RANDOM_MAX_SECONDS;
    public static final ModConfigSpec.IntValue COOLDOWN_E_SECONDS;
    public static final ModConfigSpec.IntValue COOLDOWN_D_SECONDS;
    public static final ModConfigSpec.IntValue COOLDOWN_C_SECONDS;
    public static final ModConfigSpec.IntValue COOLDOWN_B_SECONDS;
    public static final ModConfigSpec.IntValue COOLDOWN_A_SECONDS;
    public static final ModConfigSpec.IntValue COOLDOWN_S_SECONDS;
    public static final ModConfigSpec.ConfigValue<String> KEY_RANK;
    public static final ModConfigSpec.BooleanValue CONSUME_KEY;
    public static final ModConfigSpec.BooleanValue DEBUG_ITEMS_ENABLED;

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
        builder.pop();
        builder.push("items");
        KEY_RANK = builder.comment("Rank opened by a normal dungeon key.").define("keyRank", "C");
        CONSUME_KEY = builder.define("consumeKey", true);
        DEBUG_ITEMS_ENABLED = builder.define("debugItemsEnabled", true);
        builder.pop();
        SPEC = builder.build();
    }

    private DungeonServerConfig() {}
}

package dev.uapi.dungeons.config;

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
    public static final ModConfigSpec.IntValue DEPLOYMENT_READY_TIMEOUT_SECONDS;
    public static final ModConfigSpec.IntValue DEPLOYMENT_SESSION_TIMEOUT_SECONDS;
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
    public static final ModConfigSpec.ConfigValue<String> LEVEL_PROVIDER;
    public static final ModConfigSpec.IntValue FIXED_LEVEL;
    public static final ModConfigSpec.ConfigValue<String> MULTIPLAYER_LEVEL_STRATEGY;
    public static final ModConfigSpec.BooleanValue DEBUG_LEVEL_SELECTION;
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
    public static final ModConfigSpec.BooleanValue UNIQUE_RANDOM_PORTAL_PER_RANK;
    public static final ModConfigSpec.BooleanValue PORTAL_PRESSURE_ENABLED;
    public static final ModConfigSpec.IntValue PORTAL_PRESSURE_INTERVAL_SECONDS;
    public static final ModConfigSpec.IntValue PORTAL_PRESSURE_BASE_MOBS;
    public static final ModConfigSpec.IntValue PORTAL_PRESSURE_GROWTH_WAVES;
    public static final ModConfigSpec.IntValue PORTAL_PRESSURE_MAX_WAVE_MOBS;
    public static final ModConfigSpec.IntValue PORTAL_PRESSURE_MAX_ALIVE_PER_PORTAL;
    public static final ModConfigSpec.IntValue PORTAL_PRESSURE_MAX_ALIVE_SERVER;
    public static final ModConfigSpec.IntValue PORTAL_PRESSURE_MOB_LIFETIME_SECONDS;
    public static final ModConfigSpec.ConfigValue<String> PORTAL_PRESSURE_MOB_POOL;
    public static final ModConfigSpec.DoubleValue MOB_EQUIPMENT_BASE_CHANCE;
    public static final ModConfigSpec.DoubleValue MOB_EQUIPMENT_CHANCE_PER_TIER;
    public static final ModConfigSpec.DoubleValue MOB_EQUIPMENT_MAX_CHANCE;
    public static final ModConfigSpec.ConfigValue<String> DEFAULT_MOB_PROFILE;
    public static final ModConfigSpec.ConfigValue<String> DEFAULT_MOB_ROLE;
    public static final ModConfigSpec.ConfigValue<String> FALLBACK_MOB_ENTITY;
    public static final ModConfigSpec.BooleanValue DETERMINISTIC_MOB_SELECTION;
    public static final ModConfigSpec.BooleanValue CLEANUP_INSTANCE_MOBS;
    public static final ModConfigSpec.BooleanValue WARN_ON_MISSING_MOB_PROFILE;
    public static final ModConfigSpec.BooleanValue WARN_ON_MISSING_MOB_ENTITY;
    public static final ModConfigSpec.BooleanValue ALLOW_DIRECT_ENTITY_NBT;
    public static final ModConfigSpec.IntValue MAXIMUM_SPAWN_COUNT_PER_MARKER;
    public static final ModConfigSpec.ConfigValue<String> DEFAULT_LOOT_PROFILE;
    public static final ModConfigSpec.ConfigValue<String> DEFAULT_LOOT_ROLE;
    public static final ModConfigSpec.ConfigValue<String> FALLBACK_LOOT_TABLE;
    public static final ModConfigSpec.BooleanValue DETERMINISTIC_LOOT_SEEDS;
    public static final ModConfigSpec.BooleanValue WARN_ON_MISSING_LOOT_PROFILE;
    public static final ModConfigSpec.BooleanValue WARN_ON_MISSING_LOOT_TABLE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("World portal behavior and access rules.").push("general");
        RANDOM_PORTALS = builder.comment("Allow automatic world portal spawning. Default: true.")
            .define("randomPortals", true);
        PORTAL_CHECK_INTERVAL = builder.comment(
            "Base ticks between automatic portal checks; 20 ticks is approximately one second.",
            "Valid range: 200..72000. Default: 6000 (five minutes).")
            .defineInRange("checkIntervalTicks", 6000, 200, 72000);
        PORTAL_CHECK_RANDOM_TICKS = builder.comment(
            "Maximum additional random delay added to each portal check.",
            "Valid range: 0..144000 ticks. Default: 6000.")
            .defineInRange("checkRandomDelayTicks", 6000, 0, 144000);
        MAX_ACTIVE_PORTALS = builder.comment(
            "Maximum automatically spawned active portals. Default: 0 (unlimited).",
            "This limit never applies to commands or dungeon keys.")
            .defineInRange("maxActivePortals", 0, 0, 100000);
        MIN_PORTAL_DISTANCE = builder.comment("Minimum horizontal spawn distance from the player. Default: 16 blocks.")
            .defineInRange("minimumDistance", 16, 8, 256);
        MAX_PORTAL_DISTANCE = builder.comment("Maximum horizontal spawn distance from the player. Default: 32 blocks.")
            .defineInRange("maximumDistance", 32, 8, 512);
        ALLOWED_DIMENSIONS = builder.comment(
            "Comma-separated dimension IDs where automatic portals may appear.",
            "Default: minecraft:overworld.")
            .define("allowedDimensions", "minecraft:overworld");
        LIGHTNING_MODE = builder.comment("Portal lightning mode: DECORATIVE, REAL or OFF. Default: DECORATIVE.")
            .define("lightningMode", "DECORATIVE");
        APPEARANCE_LIGHTNING_STRIKES = builder.comment("Lightning strikes when a portal appears. Default: 3.")
            .defineInRange("appearanceLightningStrikes", 3, 0, 32);
        FAILURE_LIGHTNING_STRIKES = builder.comment("Lightning strikes when a portal fails or expires. Default: 5.")
            .defineInRange("failureLightningStrikes", 5, 0, 32);
        LIGHTNING_INTERVAL_TICKS = builder.comment("Ticks between repeated lightning effects. Default: 8.")
            .defineInRange("lightningIntervalTicks", 8, 1, 200);
        PORTAL_BAR_RANGE = builder.comment("Range in blocks for the portal countdown boss bar. Default: 50.")
            .defineInRange("countdownBarRange", 50, 8, 256);
        RANDOM_PORTAL_ACCESS = builder.comment("PUBLIC_NEARBY, OWNER_ONLY, PARTY_OR_NEARBY or PARTY.",
            "PARTY currently falls back to owner-only until a party provider is installed.")
            .define("randomPortalAccess", "PUBLIC_NEARBY");
        PERSONAL_PORTAL_ACCESS = builder.comment(
            "Access policy for portals opened by dungeon keys. PARTY keeps solo portals owner-only",
            "while allowing members of the authoritative group captured at deployment. Default: PARTY.")
            .define("personalPortalAccess", "PARTY");
        NEARBY_ACCESS_RADIUS = builder.comment("Maximum distance from a portal at which public access is valid.")
            .defineInRange("nearbyAccessRadius", 64, 2, 256);
        ALLOW_SPECTATOR_ENTRY = builder.comment("Allow spectator players to enter portals. Default: false.")
            .define("allowSpectatorEntry", false);
        ALLOW_LATE_JOIN = builder.comment("Allow new participants to enter an already running instance.")
            .define("allowLateJoin", true);
        DEPLOYMENT_READY_TIMEOUT_SECONDS = builder.comment(
            "Ready-check timeout used by the dungeon deployment screen. Default: 30 seconds.")
            .defineInRange("deploymentReadyTimeoutSeconds", 30, 5, 300);
        DEPLOYMENT_SESSION_TIMEOUT_SECONDS = builder.comment(
            "Maximum lifetime of a dungeon-key deployment session. Default: 120 seconds.")
            .defineInRange("deploymentSessionTimeoutSeconds", 120, 15, 900);
        SPAWN_MOBS_ON_EXPIRE = builder.comment("Spawn failure-pool mobs when an unused portal expires. Default: true.")
            .define("spawnMobsOnExpiredPortal", true);
        EXPIRED_MOB_COUNT = builder.comment("Number of mobs spawned by an expired portal. Default: 3.")
            .defineInRange("expiredPortalMobCount", 3, 0, 16);
        EXPIRED_MOB_LIFETIME = builder.comment("Lifetime of failure mobs in seconds. Default: 120.")
            .defineInRange("expiredMobLifetimeSeconds", 120, 10, 3600);
        FAILURE_MOB_POOL = builder.comment("Datapack failure mob pool ID. Default: dedicated_dungeons:default.")
            .define("failureMobPool", "dedicated_dungeons:default");
        UNIQUE_RANDOM_PORTAL_PER_RANK = builder.comment(
            "Keep at most one automatically spawned portal of each rank active at a time.",
            "Command, debug and dungeon-key portals are not restricted. Default: true.")
            .define("uniqueRandomPortalPerRank", true);
        PORTAL_PRESSURE_ENABLED = builder.comment(
            "After the former rank run timer elapses, periodically spawn rank-balanced mobs by an entered world portal.",
            "The dungeon itself has no active-phase expiry. Default: true.")
            .define("portalPressureEnabled", true);
        PORTAL_PRESSURE_INTERVAL_SECONDS = builder.comment("Seconds between portal pressure waves. Default: 60.")
            .defineInRange("portalPressureIntervalSeconds", 60, 10, 3600);
        PORTAL_PRESSURE_BASE_MOBS = builder.comment("Mob count in the first pressure wave. Default: 1.")
            .defineInRange("portalPressureBaseMobs", 1, 0, 32);
        PORTAL_PRESSURE_GROWTH_WAVES = builder.comment(
            "Add one mob after this many completed pressure waves. Default: 2.")
            .defineInRange("portalPressureGrowthEveryWaves", 2, 1, 100);
        PORTAL_PRESSURE_MAX_WAVE_MOBS = builder.comment("Hard cap for one pressure wave. Default: 6.")
            .defineInRange("portalPressureMaxWaveMobs", 6, 1, 32);
        PORTAL_PRESSURE_MAX_ALIVE_PER_PORTAL = builder.comment(
            "Maximum living pressure mobs owned by one portal. Default: 18.")
            .defineInRange("portalPressureMaxAlivePerPortal", 18, 1, 256);
        PORTAL_PRESSURE_MAX_ALIVE_SERVER = builder.comment(
            "Global cap for all living pressure mobs. Default: 64.")
            .defineInRange("portalPressureMaxAliveServer", 64, 1, 1024);
        PORTAL_PRESSURE_MOB_LIFETIME_SECONDS = builder.comment(
            "Discard pressure mobs older than this many seconds. Default: 300.")
            .defineInRange("portalPressureMobLifetimeSeconds", 300, 30, 3600);
        PORTAL_PRESSURE_MOB_POOL = builder.comment(
            "Rank-filtered mob pool used by portal pressure waves.")
            .define("portalPressureMobPool", "dedicated_dungeons:integrated_scaling");
        builder.pop();
        builder.comment("Automatic portal cooldowns in seconds. Commands and keys bypass them.").push("difficulty");
        COOLDOWN_RANDOM_MIN_SECONDS = builder.comment("Minimum random extra cooldown. Default: 30 seconds.")
            .defineInRange("randomExtraMinSeconds", 30, 0, 86400);
        COOLDOWN_RANDOM_MAX_SECONDS = builder.comment("Maximum random extra cooldown. Default: 300 seconds.")
            .defineInRange("randomExtraMaxSeconds", 300, 0, 86400);
        COOLDOWN_E_SECONDS = builder.comment("Rank E base cooldown. Default: 300 seconds.")
            .defineInRange("rankESeconds", 300, 0, 604800);
        COOLDOWN_D_SECONDS = builder.comment("Rank D base cooldown. Default: 600 seconds.")
            .defineInRange("rankDSeconds", 600, 0, 604800);
        COOLDOWN_C_SECONDS = builder.comment("Rank C base cooldown. Default: 1200 seconds.")
            .defineInRange("rankCSeconds", 1200, 0, 604800);
        COOLDOWN_B_SECONDS = builder.comment("Rank B base cooldown. Default: 2400 seconds.")
            .defineInRange("rankBSeconds", 2400, 0, 604800);
        COOLDOWN_A_SECONDS = builder.comment("Rank A base cooldown. Default: 4800 seconds.")
            .defineInRange("rankASeconds", 4800, 0, 604800);
        COOLDOWN_S_SECONDS = builder.comment("Rank S base cooldown. Default: 9600 seconds.")
            .defineInRange("rankSSeconds", 9600, 0, 604800);
        COOLDOWN_ANOMALY_SECONDS = builder.comment("ANOMALY base cooldown. Default: 19200 seconds.")
            .defineInRange("rankAnomalySeconds", 19200, 0, 604800);
        builder.pop();
        builder.comment("Client-facing portal effects.").push("ui");
        PORTAL_EFFECT_INTENSITY = builder.comment("Global particle/sound intensity multiplier; 0 disables particles.")
            .defineInRange("intensity", 1.0, 0.0, 4.0);
        builder.pop();
        builder.comment("Dungeon key behavior.").push("items");
        CONSUME_KEY = builder.comment("Consume a dungeon key after successfully creating its portal. Default: true.")
            .define("consumeKey", true);
        builder.pop();
        builder.comment("Player-level provider and multiplayer dungeon selection.").push("levelSelection");
        LEVEL_PROVIDER = builder.comment(
            "Resource location of a U-API level provider.",
            "Built-in: u_api:vanilla_experience. Use dedicated_dungeons:fixed for fixedLevel.",
            "Optional progression mods may register their own provider without becoming a dependency.")
            .define("provider", "u_api:vanilla_experience");
        FIXED_LEVEL = builder.comment("Level used when provider is dedicated_dungeons:fixed.")
            .defineInRange("fixedLevel", 1, 0, 1_000_000);
        MULTIPLAYER_LEVEL_STRATEGY = builder.comment(
            "INITIATOR, MAXIMUM, MINIMUM, AVERAGE or MEDIAN. Default: MEDIAN.")
            .define("multiplayerStrategy", "MEDIAN");
        DEBUG_LEVEL_SELECTION = builder.comment("Log dungeon filtering and effective-level decisions.")
            .define("debugSelection", false);
        builder.pop();
        builder.comment("Random equipment for compatible humanoid dungeon mobs.").push("mobEquipment");
        MOB_EQUIPMENT_BASE_CHANCE = builder.comment("Base equipment roll chance at rank E. Default: 0.12.")
            .defineInRange("baseChance", 0.12, 0.0, 1.0);
        MOB_EQUIPMENT_CHANCE_PER_TIER = builder.comment("Chance added for every rank above E. Default: 0.08.")
            .defineInRange("chancePerTier", 0.08, 0.0, 1.0);
        MOB_EQUIPMENT_MAX_CHANCE = builder.comment("Global maximum equipment roll chance. Default: 0.65.")
            .defineInRange("maximumChance", 0.65, 0.0, 1.0);
        builder.pop();
        builder.comment(
            "Global fallbacks for data-driven dungeon mob profiles. Entity lists and weights belong in datapacks.")
            .push("mobs");
        DEFAULT_MOB_PROFILE = builder.comment(
            "Profile used when helper, room, dungeon and theme do not select one.")
            .define("defaultProfile", "dedicated_dungeons:default");
        DEFAULT_MOB_ROLE = builder.comment("Role used by an unconfigured mob marker.")
            .define("defaultRole", "COMMON");
        FALLBACK_MOB_ENTITY = builder.comment("Last-resort registered entity type.")
            .define("fallbackEntity", "minecraft:zombie");
        DETERMINISTIC_MOB_SELECTION = builder.comment(
            "Derive mob selection from world, instance, room and marker data.")
            .define("deterministicSelection", true);
        CLEANUP_INSTANCE_MOBS = builder.comment(
            "Discard only tagged dungeon-owned mobs when an instance closes.")
            .define("cleanupInstanceMobs", true);
        WARN_ON_MISSING_MOB_PROFILE = builder.comment(
            "Warn once per marker when a requested mob profile or role is unavailable.")
            .define("warnOnMissingProfile", true);
        WARN_ON_MISSING_MOB_ENTITY = builder.comment(
            "Warn once per marker when a requested entity type is not registered.")
            .define("warnOnMissingEntity", true);
        ALLOW_DIRECT_ENTITY_NBT = builder.comment(
            "Allow sanitized entity_nbt from marker/profile data. Entity ID, UUID and position are never accepted.")
            .define("allowDirectEntityNbt", true);
        MAXIMUM_SPAWN_COUNT_PER_MARKER = builder.comment(
            "Hard cap after profile, difficulty and party count scaling.")
            .defineInRange("maximumSpawnCountPerMarker", 16, 1, 128);
        builder.pop();
        builder.comment("Global fallbacks for data-driven dungeon containers. Item contents remain in datapack loot tables.")
            .push("loot");
        DEFAULT_LOOT_PROFILE = builder.comment(
            "Profile used when helper, room, dungeon and theme do not select one.")
            .define("defaultProfile", "dedicated_dungeons:default");
        DEFAULT_LOOT_ROLE = builder.comment("Role used by an unconfigured loot marker.")
            .define("defaultRole", "COMMON");
        FALLBACK_LOOT_TABLE = builder.comment("Last-resort Minecraft loot table.")
            .define("fallbackLootTable", "minecraft:chests/stronghold_crossing");
        DETERMINISTIC_LOOT_SEEDS = builder.comment(
            "Derive seeds from world, instance, dungeon and container position instead of consuming level RNG.")
            .define("deterministicSeeds", true);
        WARN_ON_MISSING_LOOT_PROFILE = builder.comment("Warn once per dungeon/room when a requested profile is absent.")
            .define("warnOnMissingProfile", true);
        WARN_ON_MISSING_LOOT_TABLE = builder.comment("Warn once per dungeon/room when a requested table is absent.")
            .define("warnOnMissingLootTable", true);
        builder.pop();
        builder.comment("Instance generation, isolation and protection.").push("instances");
        INSTANCE_SLOT_SPACING = builder.comment("Distance between isolated dungeon slots in the technical dimension.")
            .defineInRange("slotSpacing", 1024, 256, 16384);
        MAX_DUNGEON_DIAMETER = builder.comment("Maximum generated width/depth, including padding.")
            .defineInRange("maxDungeonDiameter", 384, 32, 2048);
        MAX_GENERATED_PIECES = builder.comment("Maximum rooms/pieces in one generated dungeon. Default: 64.")
            .defineInRange("maxGeneratedPieces", 64, 3, 512);
        GENERATION_ATTEMPTS = builder.comment("Maximum graph-planning attempts before generation fails. Default: 24.")
            .defineInRange("generationAttempts", 24, 1, 256);
        PROTECT_BLOCKS = builder.comment("Prevent normal block changes inside active instances. Default: true.")
            .define("protectBlocks", true);
        PROTECT_EXPLOSIONS = builder.comment("Prevent explosion damage to instance blocks. Default: true.")
            .define("protectExplosions", true);
        PROTECT_FIRE_AND_FLUIDS = builder.comment("Prevent fire and fluid spread in instances. Default: true.")
            .define("protectFireAndFluids", true);
        PROTECT_TELEPORTS = builder.comment("Prevent teleporting outside assigned instance bounds. Default: true.")
            .define("protectTeleports", true);
        ENFORCE_INSTANCE_BOUNDS = builder.comment("Continuously enforce planned instance boundaries. Default: true.")
            .define("enforceBounds", true);
        builder.pop();
        builder.comment("World portal type selection and Survival Arena generation.").push("worldgen");
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
        SURVIVAL_MAX_WAVES = builder.comment("Total Survival Arena waves. Default: 8.")
            .defineInRange("maxWaves", 8, 1, 100);
        SURVIVAL_BASE_MOBS = builder.comment("Mob count in the first wave. Default: 3.")
            .defineInRange("baseMobCount", 3, 1, 64);
        SURVIVAL_MOBS_PER_WAVE = builder.comment("Additional mobs added per wave. Default: 1.")
            .defineInRange("additionalMobsPerWave", 1, 0, 32);
        SURVIVAL_WAVE_DELAY_SECONDS = builder.comment("Delay between waves in seconds. Default: 5.")
            .defineInRange("waveDelaySeconds", 5, 0, 300);
        builder.pop();
        builder.comment("Development tools. Disable on public servers.").push("debug");
        DEBUG_ITEMS_ENABLED = builder.comment("Expose and enable Dedicated Dungeons debug items. Default: true.")
            .define("debugItemsEnabled", true);
        builder.pop();
        SPEC = builder.build();
    }

    private DungeonServerConfig() {}
}

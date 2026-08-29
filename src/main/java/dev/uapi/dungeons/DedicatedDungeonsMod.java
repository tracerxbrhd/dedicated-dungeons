package dev.uapi.dungeons;

import dev.uapi.dungeons.command.DungeonCommands;
import dev.uapi.dungeons.config.DungeonServerConfig;
import dev.uapi.dungeons.runtime.DungeonEvents;
import dev.uapi.dungeons.protection.DungeonProtectionService;
import dev.uapi.dungeons.content.DungeonContentReloadListener;
import dev.uapi.dungeons.item.DungeonKeyItem;
import dev.uapi.dungeons.item.DebugCleanupItem;
import dev.uapi.dungeons.item.DebugArenaItem;
import dev.uapi.dungeons.item.DebugFinishItem;
import dev.uapi.dungeons.item.DebugPortalItem;
import dev.uapi.dungeons.integration.DungeonDeploymentLifecycle;
import dev.uapi.dungeons.network.DungeonDeploymentNetwork;
import dev.uapi.dungeons.marker.DungeonMarkerType;
import dev.uapi.dungeons.marker.DungeonMarkers;
import dev.uapi.creative.UApiCreativeTabs;
import dev.uapi.command.UApiCommandRegistry;
import dev.uapi.difficulty.DifficultyRank;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(DedicatedDungeonsMod.MOD_ID)
public final class DedicatedDungeonsMod {
    public static final String MOD_ID = "dedicated_dungeons";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredItem<Item> DUNGEON_KEY_E = key("dungeon_key_e", DifficultyRank.E);
    public static final DeferredItem<Item> DUNGEON_KEY_D = key("dungeon_key_d", DifficultyRank.D);
    public static final DeferredItem<Item> DUNGEON_KEY_C = key("dungeon_key_c", DifficultyRank.C);
    public static final DeferredItem<Item> DUNGEON_KEY_B = key("dungeon_key_b", DifficultyRank.B);
    public static final DeferredItem<Item> DUNGEON_KEY_A = key("dungeon_key_a", DifficultyRank.A);
    public static final DeferredItem<Item> DUNGEON_KEY_S = key("dungeon_key_s", DifficultyRank.S);
    public static final DeferredItem<Item> DUNGEON_KEY_ANOMALY = key("dungeon_key_anomaly", DifficultyRank.ANOMALY);
    public static final DeferredItem<Item> DEBUG_PORTAL_E = debugPortal("debug_portal_e", DifficultyRank.E);
    public static final DeferredItem<Item> DEBUG_PORTAL_D = debugPortal("debug_portal_d", DifficultyRank.D);
    public static final DeferredItem<Item> DEBUG_PORTAL_C = debugPortal("debug_portal_c", DifficultyRank.C);
    public static final DeferredItem<Item> DEBUG_PORTAL_B = debugPortal("debug_portal_b", DifficultyRank.B);
    public static final DeferredItem<Item> DEBUG_PORTAL_A = debugPortal("debug_portal_a", DifficultyRank.A);
    public static final DeferredItem<Item> DEBUG_PORTAL_S = debugPortal("debug_portal_s", DifficultyRank.S);
    public static final DeferredItem<Item> DEBUG_PORTAL_ANOMALY = debugPortal("debug_portal_anomaly", DifficultyRank.ANOMALY);
    public static final DeferredItem<Item> DEBUG_ARENA_E = debugArena("debug_arena_e", DifficultyRank.E);
    public static final DeferredItem<Item> DEBUG_ARENA_D = debugArena("debug_arena_d", DifficultyRank.D);
    public static final DeferredItem<Item> DEBUG_ARENA_C = debugArena("debug_arena_c", DifficultyRank.C);
    public static final DeferredItem<Item> DEBUG_ARENA_B = debugArena("debug_arena_b", DifficultyRank.B);
    public static final DeferredItem<Item> DEBUG_ARENA_A = debugArena("debug_arena_a", DifficultyRank.A);
    public static final DeferredItem<Item> DEBUG_ARENA_S = debugArena("debug_arena_s", DifficultyRank.S);
    public static final DeferredItem<Item> DEBUG_ARENA_ANOMALY = debugArena("debug_arena_anomaly", DifficultyRank.ANOMALY);
    public static final DeferredItem<Item> DEBUG_FINISH = ITEMS.register("debug_finish",
        () -> new DebugFinishItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> DEBUG_CLEANUP = ITEMS.register("debug_cleanup",
        () -> new DebugCleanupItem(new Item.Properties().stacksTo(1)));
    public static final UApiCreativeTabs.Registrar CREATIVE_TAB = UApiCreativeTabs.create(
        MOD_ID, "main", "itemGroup.dedicated_dungeons", () -> DUNGEON_KEY_E.get().getDefaultInstance());
    public DedicatedDungeonsMod(IEventBus modBus, ModContainer container) {
        UApiCommandRegistry.registerSection("dungeons", DungeonCommands::create);
        modBus.addListener(DungeonDeploymentNetwork::register);
        DungeonMarkers.registerBus(modBus);
        ITEMS.register(modBus);
        registerCreativeItems();
        CREATIVE_TAB.registerBus(modBus);
        container.registerConfig(ModConfig.Type.COMMON, DungeonServerConfig.SPEC, "uapi/dedicated-dungeons/server.toml");
        NeoForge.EVENT_BUS.register(DungeonEvents.class);
        NeoForge.EVENT_BUS.register(DungeonDeploymentLifecycle.class);
        NeoForge.EVENT_BUS.register(DungeonProtectionService.class);
        NeoForge.EVENT_BUS.register(DungeonContentReloadListener.class);
    }

    private static void registerCreativeItems() {
        int order = 0;
        for (String rank : new String[]{"e", "d", "c", "b", "a", "s", "anomaly"})
            CREATIVE_TAB.add(id("dungeon_key_" + rank), order++);
        int debugOrder = 100;
        for (String rank : new String[]{"e", "d", "c", "b", "a", "s", "anomaly"})
            CREATIVE_TAB.add(id("debug_portal_" + rank),
                debugOrder++, () -> DungeonServerConfig.DEBUG_ITEMS_ENABLED.get());
        CREATIVE_TAB.add(id("debug_finish"), 110,
            () -> DungeonServerConfig.DEBUG_ITEMS_ENABLED.get());
        CREATIVE_TAB.add(id("debug_cleanup"), 111,
            () -> DungeonServerConfig.DEBUG_ITEMS_ENABLED.get());
        int arenaOrder = 120;
        for (String rank : new String[]{"e", "d", "c", "b", "a", "s", "anomaly"})
            CREATIVE_TAB.add(id("debug_arena_" + rank),
                arenaOrder++, () -> DungeonServerConfig.DEBUG_ITEMS_ENABLED.get());
        int markerOrder = 200;
        for (DungeonMarkerType marker : DungeonMarkerType.values()) {
            CREATIVE_TAB.add(id(marker.id()), markerOrder++);
        }
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static DeferredItem<Item> key(String id, DifficultyRank rank) {
        return ITEMS.register(id, () -> new DungeonKeyItem(rank, new Item.Properties().stacksTo(16)));
    }

    private static DeferredItem<Item> debugPortal(String id, DifficultyRank rank) {
        return ITEMS.register(id, () -> new DebugPortalItem(rank, new Item.Properties().stacksTo(1)));
    }

    private static DeferredItem<Item> debugArena(String id, DifficultyRank rank) {
        return ITEMS.register(id, () -> new DebugArenaItem(rank, new Item.Properties().stacksTo(1)));
    }
}

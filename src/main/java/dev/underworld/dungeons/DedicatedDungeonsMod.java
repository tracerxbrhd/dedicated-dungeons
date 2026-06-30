package dev.underworld.dungeons;

import dev.underworld.dungeons.command.DungeonCommands;
import dev.underworld.dungeons.config.DungeonServerConfig;
import dev.underworld.dungeons.runtime.DungeonEvents;
import dev.underworld.dungeons.protection.DungeonProtectionService;
import dev.underworld.dungeons.template.DungeonTemplateReloadListener;
import dev.underworld.dungeons.item.DungeonKeyItem;
import dev.underworld.dungeons.item.DebugCleanupItem;
import dev.underworld.dungeons.item.DebugFinishItem;
import dev.underworld.dungeons.item.DebugPortalItem;
import dev.underworld.api.creative.UnderworldCreativeTabs;
import dev.underworld.api.difficulty.DifficultyRank;
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
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredItem<Item> DUNGEON_KEY_E = key("dungeon_key_e", DifficultyRank.E);
    public static final DeferredItem<Item> DUNGEON_KEY_D = key("dungeon_key_d", DifficultyRank.D);
    public static final DeferredItem<Item> DUNGEON_KEY_C = key("dungeon_key_c", DifficultyRank.C);
    public static final DeferredItem<Item> DUNGEON_KEY_B = key("dungeon_key_b", DifficultyRank.B);
    public static final DeferredItem<Item> DUNGEON_KEY_A = key("dungeon_key_a", DifficultyRank.A);
    public static final DeferredItem<Item> DUNGEON_KEY_S = key("dungeon_key_s", DifficultyRank.S);
    public static final DeferredItem<Item> DUNGEON_KEY_ANOMALY = key("dungeon_key_anomaly", DifficultyRank.ANOMALY);
    public static final DeferredItem<Item> DEBUG_PORTAL_E = ITEMS.register("debug_portal_e",
        () -> new DebugPortalItem(DifficultyRank.E, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> DEBUG_PORTAL_S = ITEMS.register("debug_portal_s",
        () -> new DebugPortalItem(DifficultyRank.S, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> DEBUG_PORTAL_ANOMALY = ITEMS.register("debug_portal_anomaly",
        () -> new DebugPortalItem(DifficultyRank.ANOMALY, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> DEBUG_FINISH = ITEMS.register("debug_finish",
        () -> new DebugFinishItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> DEBUG_CLEANUP = ITEMS.register("debug_cleanup",
        () -> new DebugCleanupItem(new Item.Properties().stacksTo(1)));
    public DedicatedDungeonsMod(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        registerCreativeItems();
        container.registerConfig(ModConfig.Type.COMMON, DungeonServerConfig.SPEC, "dedicated_dungeons.toml");
        NeoForge.EVENT_BUS.register(DungeonEvents.class);
        NeoForge.EVENT_BUS.register(DungeonProtectionService.class);
        NeoForge.EVENT_BUS.register(DungeonCommands.class);
        NeoForge.EVENT_BUS.register(DungeonTemplateReloadListener.class);
    }

    private static void registerCreativeItems() {
        int order = 0;
        for (String rank : new String[]{"e", "d", "c", "b", "a", "s", "anomaly"})
            UnderworldCreativeTabs.register(id("dungeon_key_" + rank), UnderworldCreativeTabs.Section.DUNGEONS, order++);
        UnderworldCreativeTabs.register(id("debug_portal_e"), UnderworldCreativeTabs.Section.DEBUG, 100,
            () -> DungeonServerConfig.DEBUG_ITEMS_ENABLED.get());
        UnderworldCreativeTabs.register(id("debug_portal_s"), UnderworldCreativeTabs.Section.DEBUG, 101,
            () -> DungeonServerConfig.DEBUG_ITEMS_ENABLED.get());
        UnderworldCreativeTabs.register(id("debug_portal_anomaly"), UnderworldCreativeTabs.Section.DEBUG, 102,
            () -> DungeonServerConfig.DEBUG_ITEMS_ENABLED.get());
        UnderworldCreativeTabs.register(id("debug_finish"), UnderworldCreativeTabs.Section.DEBUG, 103,
            () -> DungeonServerConfig.DEBUG_ITEMS_ENABLED.get());
        UnderworldCreativeTabs.register(id("debug_cleanup"), UnderworldCreativeTabs.Section.DEBUG, 104,
            () -> DungeonServerConfig.DEBUG_ITEMS_ENABLED.get());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    private static DeferredItem<Item> key(String id, DifficultyRank rank) {
        return ITEMS.register(id, () -> new DungeonKeyItem(rank, new Item.Properties().stacksTo(16)));
    }
}

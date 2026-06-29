package dev.underworld.dungeons;

import dev.underworld.dungeons.command.DungeonCommands;
import dev.underworld.dungeons.config.DungeonServerConfig;
import dev.underworld.dungeons.runtime.DungeonEvents;
import dev.underworld.dungeons.template.DungeonTemplateReloadListener;
import dev.underworld.dungeons.item.DungeonKeyItem;
import dev.underworld.dungeons.item.DebugCleanupItem;
import dev.underworld.dungeons.item.DebugFinishItem;
import dev.underworld.dungeons.item.DebugPortalItem;
import dev.underworld.api.difficulty.DifficultyRank;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(DedicatedDungeonsMod.MOD_ID)
public final class DedicatedDungeonsMod {
    public static final String MOD_ID = "dedicated_dungeons";
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);
    public static final DeferredItem<Item> DUNGEON_KEY = ITEMS.register("dungeon_key",
        () -> new DungeonKeyItem(new Item.Properties().stacksTo(16)));
    public static final DeferredItem<Item> DEBUG_PORTAL_E = ITEMS.register("debug_portal_e",
        () -> new DebugPortalItem(DifficultyRank.E, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> DEBUG_PORTAL_S = ITEMS.register("debug_portal_s",
        () -> new DebugPortalItem(DifficultyRank.S, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> DEBUG_FINISH = ITEMS.register("debug_finish",
        () -> new DebugFinishItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> DEBUG_CLEANUP = ITEMS.register("debug_cleanup",
        () -> new DebugCleanupItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("underworld_dungeons", () ->
        CreativeModeTab.builder().title(Component.translatable("itemGroup.dedicated_dungeons"))
            .icon(() -> DUNGEON_KEY.get().getDefaultInstance()).displayItems((parameters, output) -> {
                output.accept(DUNGEON_KEY.get());
                output.accept(DEBUG_PORTAL_E.get());
                output.accept(DEBUG_PORTAL_S.get());
                output.accept(DEBUG_FINISH.get());
                output.accept(DEBUG_CLEANUP.get());
            }).build());

    public DedicatedDungeonsMod(IEventBus modBus, ModContainer container) {
        ITEMS.register(modBus);
        TABS.register(modBus);
        container.registerConfig(ModConfig.Type.COMMON, DungeonServerConfig.SPEC, "dedicated_dungeons.toml");
        NeoForge.EVENT_BUS.register(DungeonEvents.class);
        NeoForge.EVENT_BUS.register(DungeonCommands.class);
        NeoForge.EVENT_BUS.register(DungeonTemplateReloadListener.class);
    }
}

package dev.underworld.dungeons.template;

import com.google.gson.JsonParser;
import dev.underworld.dungeons.DedicatedDungeonsMod;
import dev.underworld.dungeons.portal.FailureMobPool;
import dev.underworld.dungeons.portal.FailureMobPools;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

public final class DungeonTemplateReloadListener {
    private DungeonTemplateReloadListener() {}

    @SubscribeEvent
    public static void register(AddReloadListenerEvent event) { event.addListener(new Listener()); }

    private static final class Listener implements ResourceManagerReloadListener {
        private static final String ROOT = "dungeon_templates";
        @Override public void onResourceManagerReload(ResourceManager manager) {
            Map<ResourceLocation, DungeonTemplate> loaded = new HashMap<>();
            for (Map.Entry<ResourceLocation, Resource> resource : manager.listResources(ROOT,
                id -> id.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation file = resource.getKey();
                String path = file.getPath().substring((ROOT + "/").length(), file.getPath().length() - 5);
                try (Reader reader = resource.getValue().openAsReader()) {
                    loaded.put(ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path),
                        DungeonTemplate.parse(JsonParser.parseReader(reader).getAsJsonObject()));
                } catch (Exception exception) {
                    throw new IllegalStateException("Failed to load dungeon template " + file, exception);
                }
            }
            DungeonTemplates.replace(loaded);

            Map<ResourceLocation, FailureMobPool> mobPools = new HashMap<>();
            String mobRoot = "portal_failure_pools";
            for (Map.Entry<ResourceLocation, Resource> resource : manager.listResources(mobRoot,
                id -> id.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation file = resource.getKey();
                String path = file.getPath().substring((mobRoot + "/").length(), file.getPath().length() - 5);
                try (Reader reader = resource.getValue().openAsReader()) {
                    mobPools.put(ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path),
                        FailureMobPool.parse(JsonParser.parseReader(reader).getAsJsonObject()));
                } catch (Exception exception) {
                    throw new IllegalStateException("Failed to load portal failure pool " + file, exception);
                }
            }
            FailureMobPools.replace(mobPools);
        }
    }
}

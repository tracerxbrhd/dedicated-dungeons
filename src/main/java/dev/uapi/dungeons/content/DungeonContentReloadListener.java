package dev.uapi.dungeons.content;

import com.google.gson.JsonParser;
import dev.uapi.dungeons.DedicatedDungeonsMod;
import dev.uapi.dungeons.portal.FailureMobPool;
import dev.uapi.dungeons.portal.FailureMobPools;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;

/** Installs a fully validated immutable content snapshot for every datapack reload. */
public final class DungeonContentReloadListener {
    private DungeonContentReloadListener() {}

    @SubscribeEvent
    public static void register(AddReloadListenerEvent event) {
        event.addListener(new Listener());
    }

    private static final class Listener implements ResourceManagerReloadListener {
        @Override
        public void onResourceManagerReload(ResourceManager manager) {
            Map<ResourceLocation, FailureMobPool> pools = new LinkedHashMap<>();
            String root = "portal_failure_pools";
            for (Map.Entry<ResourceLocation, Resource> resource : manager.listResources(root,
                id -> id.getPath().endsWith(".json")).entrySet()) {
                ResourceLocation file = resource.getKey();
                String path = file.getPath().substring((root + "/").length(), file.getPath().length() - 5);
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), path);
                try (Reader reader = resource.getValue().openAsReader()) {
                    pools.put(id, FailureMobPool.parse(JsonParser.parseReader(reader).getAsJsonObject()));
                } catch (Exception exception) {
                    DedicatedDungeonsMod.LOGGER.error("Portal failure pool {} was rejected; other dungeon data remains loadable",
                        file, exception);
                }
            }
            FailureMobPools.replace(pools);
            DungeonContentRegistry.reload(manager);
        }
    }
}

package dev.underworld.dungeons.template;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public final class DungeonTemplates {
    private static volatile Map<ResourceLocation, DungeonTemplate> templates = Map.of();
    private DungeonTemplates() {}
    public static void replace(Map<ResourceLocation, DungeonTemplate> loaded) { templates = Map.copyOf(loaded); }
    public static DungeonTemplate get(ResourceLocation id) {
        DungeonTemplate template = templates.get(id);
        if (template == null) throw new IllegalStateException("Missing dungeon template: " + id);
        return template;
    }
}

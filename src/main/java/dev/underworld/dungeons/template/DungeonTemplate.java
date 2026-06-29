package dev.underworld.dungeons.template;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

public record DungeonTemplate(int size, int wallHeight, ResourceLocation floorBlock,
                              ResourceLocation wallBlock, ResourceLocation accentBlock,
                              ResourceLocation bossType, double bossHealth, double bossDamage,
                              ResourceLocation rewardPool) {
    public static DungeonTemplate parse(JsonObject json) {
        return new DungeonTemplate(
            Math.max(15, GsonHelper.getAsInt(json, "size", 31)),
            Math.max(2, GsonHelper.getAsInt(json, "wall_height", 5)),
            ResourceLocation.parse(GsonHelper.getAsString(json, "floor_block", "minecraft:deepslate_tiles")),
            ResourceLocation.parse(GsonHelper.getAsString(json, "wall_block", "minecraft:polished_blackstone_bricks")),
            ResourceLocation.parse(GsonHelper.getAsString(json, "accent_block", "minecraft:crying_obsidian")),
            ResourceLocation.parse(GsonHelper.getAsString(json, "boss", "minecraft:zombie")),
            Math.max(1, GsonHelper.getAsDouble(json, "boss_health", 80)),
            Math.max(0, GsonHelper.getAsDouble(json, "boss_damage", 8)),
            ResourceLocation.parse(GsonHelper.getAsString(json, "reward_pool", "dedicated_dungeons:basic")));
    }
}

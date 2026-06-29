package dev.underworld.dungeons.portal;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;

public final class FailureMobPools {
    private static volatile Map<ResourceLocation, FailureMobPool> pools = Map.of();
    private FailureMobPools() {}
    public static void replace(Map<ResourceLocation, FailureMobPool> loaded) { pools = Map.copyOf(loaded); }
    public static FailureMobPool get(ResourceLocation id) {
        FailureMobPool pool = pools.get(id);
        if (pool == null) throw new IllegalStateException("Missing portal failure mob pool: " + id);
        return pool;
    }
}

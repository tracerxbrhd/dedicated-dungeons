package dev.uapi.dungeons.runtime;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

/** Deterministic combinatorial boss names; the same persisted dungeon seed restores the same name. */
public final class DungeonBossNames {
    private static final int TITLES = 16;
    private static final int STARTS = 16;
    private static final int MIDDLES = 16;
    private static final int ENDS = 16;
    private static final int EPITHETS = 16;

    private DungeonBossNames() {}

    public static Component generate(long dungeonSeed, ResourceLocation bossId) {
        long seed = mix(dungeonSeed ^ bossId.toString().hashCode() * 0x9E3779B97F4A7C15L);
        RandomSource random = RandomSource.create(seed);
        Component title = Component.translatable(key("title", random.nextInt(TITLES)));
        Component core = Component.empty()
            .append(Component.translatable(key("syllable.start", random.nextInt(STARTS))))
            .append(Component.translatable(key("syllable.middle", random.nextInt(MIDDLES))));
        if (random.nextFloat() < 0.38f)
            core = core.copy().append(Component.translatable(key("syllable.middle", random.nextInt(MIDDLES))));
        core = core.copy().append(Component.translatable(key("syllable.end", random.nextInt(ENDS))));
        Component epithet = Component.translatable(key("epithet", random.nextInt(EPITHETS)));
        return Component.translatable("entity.dedicated_dungeons.generated_boss_name", title, core, epithet);
    }

    private static String key(String group, int index) {
        return "boss_name.dedicated_dungeons." + group + "." + index;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}

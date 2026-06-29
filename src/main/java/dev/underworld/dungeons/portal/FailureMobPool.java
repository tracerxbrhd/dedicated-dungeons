package dev.underworld.dungeons.portal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.underworld.api.difficulty.DifficultyRank;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

public record FailureMobPool(List<Entry> entries) {
    public record Entry(ResourceLocation entityType, int weight, DifficultyRank minimumRank) {}

    public static FailureMobPool parse(JsonObject json) {
        List<Entry> entries = new ArrayList<>();
        for (JsonElement element : GsonHelper.getAsJsonArray(json, "entries")) {
            JsonObject entry = element.getAsJsonObject();
            entries.add(new Entry(ResourceLocation.parse(GsonHelper.getAsString(entry, "entity")),
                Math.max(1, GsonHelper.getAsInt(entry, "weight", 1)),
                DifficultyRank.byName(GsonHelper.getAsString(entry, "minimum_rank", "E"))));
        }
        return new FailureMobPool(List.copyOf(entries));
    }

    public Entry choose(DifficultyRank rank, RandomSource random) {
        List<Entry> eligible = entries.stream()
            .filter(entry -> rank.ordinal() >= entry.minimumRank().ordinal()).toList();
        if (eligible.isEmpty()) return null;
        int selected = random.nextInt(eligible.stream().mapToInt(Entry::weight).sum());
        for (Entry entry : eligible) {
            selected -= entry.weight();
            if (selected < 0) return entry;
        }
        return eligible.get(eligible.size() - 1);
    }
}

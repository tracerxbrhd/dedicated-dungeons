package dev.uapi.dungeons.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A datapack profile mapping open-ended logical roles to one or more weighted Minecraft loot tables. */
public record LootProfile(ResourceLocation id, ResourceLocation parent, Map<LootRole, Entry> roles) {
    public LootProfile {
        roles = Map.copyOf(roles);
    }

    public static LootProfile parse(ResourceLocation resourceId, JsonObject json) {
        if (json.has("id")) {
            ResourceLocation declared = parseId(json.get("id").getAsString(), "id");
            if (!declared.equals(resourceId)) {
                throw new IllegalArgumentException("declared id " + declared + " does not match resource id " + resourceId);
            }
        }
        ResourceLocation parent = json.has("parent")
            ? parseId(json.get("parent").getAsString(), "parent") : null;
        if (!json.has("roles") || !json.get("roles").isJsonObject()) {
            throw new IllegalArgumentException("missing object 'roles'");
        }
        Map<LootRole, Entry> roles = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> role : json.getAsJsonObject("roles").entrySet()) {
            roles.put(LootRole.parse(role.getKey()), Entry.parse(role.getValue()));
        }
        return new LootProfile(resourceId, parent, roles);
    }

    public record Entry(List<WeightedTable> tables) {
        public Entry {
            tables = List.copyOf(tables);
            if (tables.isEmpty()) throw new IllegalArgumentException("loot profile role must contain a table");
        }

        static Entry parse(JsonElement element) {
            if (element.isJsonPrimitive()) {
                return new Entry(List.of(new WeightedTable(parseId(element.getAsString(), "table"), 1)));
            }
            JsonObject json = element.getAsJsonObject();
            List<WeightedTable> tables = new ArrayList<>();
            if (json.has("table")) {
                tables.add(new WeightedTable(parseId(json.get("table").getAsString(), "table"), 1));
            }
            if (json.has("tables")) {
                if (!json.get("tables").isJsonArray()) throw new IllegalArgumentException("'tables' must be an array");
                for (JsonElement value : json.getAsJsonArray("tables")) {
                    if (value.isJsonPrimitive()) {
                        tables.add(new WeightedTable(parseId(value.getAsString(), "tables"), 1));
                    } else {
                        JsonObject weighted = value.getAsJsonObject();
                        ResourceLocation table = parseId(requiredString(weighted, "table"), "tables.table");
                        int weight = weighted.has("weight") ? weighted.get("weight").getAsInt() : 1;
                        tables.add(new WeightedTable(table, weight));
                    }
                }
            }
            return new Entry(tables);
        }

        public ResourceLocation choose(long seed, java.util.function.Predicate<ResourceLocation> usable) {
            List<WeightedTable> available = tables.stream().filter(value -> usable.test(value.table())).toList();
            long total = available.stream().mapToLong(WeightedTable::weight).sum();
            if (total <= 0) return null;
            long selected = Math.floorMod(mix64(seed), total);
            for (WeightedTable value : available) {
                selected -= value.weight();
                if (selected < 0) return value.table();
            }
            return available.getLast().table();
        }
    }

    public record WeightedTable(ResourceLocation table, int weight) {
        public WeightedTable {
            if (weight < 1 || weight > 1_000_000)
                throw new IllegalArgumentException("loot table weight must be in [1,1000000]");
        }
    }

    private static ResourceLocation parseId(String value, String field) {
        ResourceLocation result = ResourceLocation.tryParse(value);
        if (result == null) throw new IllegalArgumentException("invalid resource location in '" + field + "': " + value);
        return result;
    }

    private static String requiredString(JsonObject json, String field) {
        if (!json.has(field) || !json.get(field).isJsonPrimitive())
            throw new IllegalArgumentException("missing string '" + field + "'");
        return json.get(field).getAsString();
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}

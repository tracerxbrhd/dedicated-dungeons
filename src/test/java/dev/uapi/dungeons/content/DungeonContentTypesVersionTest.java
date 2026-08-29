package dev.uapi.dungeons.content;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class DungeonContentTypesVersionTest {
    private static final String ARENA = """
        {
          "format_version": 1,
          "theme": "dedicated_dungeons:forgotten_depths",
          "structure": "dedicated_dungeons:test/survival_arena",
          "size": "arena",
          "bounds": { "min": [0, 0, 0], "max": [16, 6, 16] },
          "connectors": [],
          "markers": {},
          "tags": ["arena"],
          "palette": {
            "floor": "minecraft:stone",
            "wall": "minecraft:stone_bricks",
            "accent": "minecraft:polished_andesite"
          }
        }
        """;

    @Test
    void allowsIsolatedArenaButRejectsMissingVersion() {
        var id = ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", "arena_test");
        var room = DungeonContentTypes.Room.parse(id, JsonParser.parseString(ARENA).getAsJsonObject());
        assertEquals(DungeonContentTypes.RoomSize.ARENA, room.size());
        assertThrows(IllegalArgumentException.class, () -> DungeonContentTypes.Room.parse(id,
            JsonParser.parseString(ARENA.replace("\"format_version\": 1,", "")).getAsJsonObject()));
    }
}

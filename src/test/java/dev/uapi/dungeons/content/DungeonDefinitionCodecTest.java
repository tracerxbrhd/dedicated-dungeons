package dev.uapi.dungeons.content;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DungeonDefinitionCodecTest {
    private static final String VALID = """
        {
          "format_version": 1,
          "display_name": "test.name",
          "description": "test.description",
          "archetype": "dedicated_dungeons:basic",
          "recommended_level": 10,
          "minimum_level": 5,
          "maximum_level": 20,
          "pools": {
            "encounter_pool": "dedicated_dungeons:test",
            "boss_pool": "dedicated_dungeons:test",
            "reward_pool": "dedicated_dungeons:basic"
          }
        }
        """;

    @Test
    void decodesCurrentFormat() {
        var result = DungeonDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(VALID)).result();
        assertTrue(result.isPresent());
        assertEquals(10, result.orElseThrow().recommendedLevel());
    }

    @Test
    void rejectsNewerFormatAndInvalidLevelRange() {
        var newer = VALID.replace("\"format_version\": 1", "\"format_version\": 2");
        var invalidRange = VALID.replace("\"minimum_level\": 5", "\"minimum_level\": 25");
        assertThrows(IllegalArgumentException.class,
            () -> DungeonDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(newer)));
        assertThrows(IllegalArgumentException.class,
            () -> DungeonDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(invalidRange)));
    }
}

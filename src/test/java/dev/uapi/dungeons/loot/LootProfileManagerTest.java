package dev.uapi.dungeons.loot;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LootProfileManagerTest {
    private static final ResourceLocation TABLE_A = id("chests/a");
    private static final ResourceLocation TABLE_B = id("chests/b");

    @AfterEach
    void clear() {
        LootProfileManager.replaceForTests(Map.of(), Set.of());
    }

    @Test
    void parentProfileSuppliesMissingRole() {
        LootProfile parent = profile(id("parent"), null, LootRole.BOSS, TABLE_A);
        LootProfile child = profile(id("child"), parent.id(), LootRole.COMMON, TABLE_B);
        LootProfileManager.replaceForTests(Map.of(parent.id(), parent, child.id(), child), Set.of(TABLE_A, TABLE_B));
        assertEquals(TABLE_A, LootProfileManager.resolve(child.id(), LootRole.BOSS, 1L).orElseThrow());
        assertEquals(TABLE_B, LootProfileManager.resolve(child.id(), LootRole.COMMON, 1L).orElseThrow());
    }

    @Test
    void cyclicInheritanceIsDetected() {
        LootProfile first = profile(id("first"), id("second"), LootRole.COMMON, TABLE_A);
        LootProfile second = profile(id("second"), id("first"), LootRole.COMMON, TABLE_B);
        List<String> errors = new ArrayList<>();
        LootProfileManager.validate(Map.of(first.id(), first, second.id(), second),
            Set.of(TABLE_A, TABLE_B), errors, new ArrayList<>());
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(value -> value.contains("cycle")));
    }

    @Test
    void replacingSnapshotModelsDatapackReloadAtomically() {
        LootProfile before = profile(id("runtime"), null, LootRole.COMMON, TABLE_A);
        LootProfile after = profile(id("runtime"), null, LootRole.COMMON, TABLE_B);
        LootProfileManager.replaceForTests(Map.of(before.id(), before), Set.of(TABLE_A));
        long firstGeneration = LootProfileManager.generation();
        assertEquals(TABLE_A, LootProfileManager.resolve(before.id(), LootRole.COMMON, 0L).orElseThrow());
        LootProfileManager.replaceForTests(Map.of(after.id(), after), Set.of(TABLE_B));
        assertTrue(LootProfileManager.generation() > firstGeneration);
        assertEquals(TABLE_B, LootProfileManager.resolve(after.id(), LootRole.COMMON, 0L).orElseThrow());
    }

    @Test
    void invalidResourceLocationIsRejectedDuringProfileParsing() {
        var json = JsonParser.parseString("""
            {"roles":{"COMMON":{"table":"not valid:table"}}}
            """).getAsJsonObject();
        boolean rejected = false;
        try {
            LootProfile.parse(id("invalid"), json);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue(rejected);
    }

    private static LootProfile profile(ResourceLocation id, ResourceLocation parent,
                                       LootRole role, ResourceLocation table) {
        return new LootProfile(id, parent, Map.of(role,
            new LootProfile.Entry(List.of(new LootProfile.WeightedTable(table, 1)))));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", path);
    }
}

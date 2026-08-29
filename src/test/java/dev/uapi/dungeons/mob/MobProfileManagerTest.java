package dev.uapi.dungeons.mob;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MobProfileManagerTest {
    @BeforeEach
    void prepare() {
        MobProfileManager.replaceForTests(Map.of());
    }

    @AfterEach
    void clear() {
        MobProfileManager.replaceForTests(Map.of());
    }

    @Test
    void customRoleNeedsNoJavaEnumChange() {
        SpawnRole role = SpawnRole.parse("example_pack:crypt_guard");
        assertEquals("example_pack:crypt_guard", role.value());
        MobProfile profile = profile(id("custom"), null, role, false, entry("minecraft:zombie", 1));
        MobProfileManager.replaceForTests(Map.of(profile.id(), profile));
        assertEquals(ResourceLocation.parse("minecraft:zombie"),
            MobProfileManager.resolve(profile.id(), role, 0, 1, false, 4L).orElseThrow().entry().entity());
    }

    @Test
    void replaceFalseMergesParentAndChildRole() {
        MobProfile parent = profile(id("parent"), null, SpawnRole.COMMON, false,
            entry("minecraft:zombie", 1));
        MobProfile child = profile(id("child"), parent.id(), SpawnRole.COMMON, false,
            entry("minecraft:skeleton", 1));
        MobProfileManager.replaceForTests(Map.of(parent.id(), parent, child.id(), child));
        assertEquals(2, MobProfileManager.effectiveEntries(child.id(), SpawnRole.COMMON).size());
    }

    @Test
    void replaceTrueDiscardsParentRole() {
        MobProfile parent = profile(id("parent"), null, SpawnRole.COMMON, false,
            entry("minecraft:zombie", 1));
        MobProfile child = profile(id("child"), parent.id(), SpawnRole.COMMON, true,
            entry("minecraft:skeleton", 1));
        MobProfileManager.replaceForTests(Map.of(parent.id(), parent, child.id(), child));
        assertEquals(List.of(ResourceLocation.parse("minecraft:skeleton")),
            MobProfileManager.effectiveEntries(child.id(), SpawnRole.COMMON).stream()
                .map(MobProfile.Entry::entity).toList());
    }

    @Test
    void cyclicInheritanceIsDetected() {
        MobProfile first = profile(id("first"), id("second"), SpawnRole.COMMON, false,
            entry("minecraft:zombie", 1));
        MobProfile second = profile(id("second"), id("first"), SpawnRole.COMMON, false,
            entry("minecraft:skeleton", 1));
        List<String> errors = new ArrayList<>();
        var rejected = MobProfileManager.validate(Map.of(first.id(), first, second.id(), second),
            errors, new ArrayList<>());
        assertFalse(rejected.isEmpty());
        assertTrue(errors.stream().anyMatch(value -> value.contains("cycle")));
    }

    @Test
    void zeroWeightRoleReturnsEmptyInsteadOfCrashing() {
        MobProfile profile = profile(id("zero"), null, SpawnRole.COMMON, false,
            entry("minecraft:zombie", 0));
        MobProfileManager.replaceForTests(Map.of(profile.id(), profile));
        assertTrue(MobProfileManager.resolve(profile.id(), SpawnRole.COMMON,
            0, 1, false, 1L).isEmpty());
    }

    @Test
    void parserRejectsNegativeWeightAndInvertedRanges() {
        var negative = JsonParser.parseString("""
            {"roles":{"COMMON":{"entries":[{"entity":"minecraft:zombie","weight":-1}]}}}
            """).getAsJsonObject();
        var range = JsonParser.parseString("""
            {"roles":{"COMMON":{"entries":[{"entity":"minecraft:zombie","min_count":3,"max_count":1}]}}}
            """).getAsJsonObject();
        assertRejected(negative);
        assertRejected(range);
    }

    @Test
    void weightedSelectionStronglyFavoursLargerWeight() {
        MobProfile profile = new MobProfile(id("weighted"), null,
            Map.of(SpawnRole.COMMON, new MobProfile.RoleEntries(false, List.of(
                entry("minecraft:zombie", 100), entry("minecraft:skeleton", 1)))),
            MobProfile.Scaling.DEFAULT);
        MobProfileManager.replaceForTests(Map.of(profile.id(), profile));
        int zombies = 0;
        for (long seed = 0; seed < 1000; seed++) {
            if (MobProfileManager.resolve(profile.id(), SpawnRole.COMMON, 0, 1, false, seed)
                .orElseThrow().entry().entity().equals(ResourceLocation.parse("minecraft:zombie"))) zombies++;
        }
        assertTrue(zombies > 930, "weighted selection produced only " + zombies + " zombies");
    }

    @Test
    void snapshotReplacementModelsDatapackReload() {
        MobProfile first = profile(id("runtime"), null, SpawnRole.COMMON, false,
            entry("minecraft:zombie", 1));
        MobProfile second = profile(id("runtime"), null, SpawnRole.COMMON, false,
            entry("minecraft:skeleton", 1));
        MobProfileManager.replaceForTests(Map.of(first.id(), first));
        long before = MobProfileManager.generation();
        MobProfileManager.replaceForTests(Map.of(second.id(), second));
        assertTrue(MobProfileManager.generation() > before);
        assertEquals(ResourceLocation.parse("minecraft:skeleton"),
            MobProfileManager.resolve(second.id(), SpawnRole.COMMON, 0, 1, false, 2L)
                .orElseThrow().entry().entity());
    }

    @Test
    void scalingIsBoundedForDifficultyAndParty() {
        MobProfile.Scaling scaling = new MobProfile.Scaling(2, 1, 6, 0.25);
        assertEquals(2, scaling.scale(1, 0, 1, 16));
        assertEquals(6, scaling.scale(3, 6, 8, 16));
        assertEquals(4, scaling.scale(2, 0, 8, 4));
    }

    @Test
    void differentInstancesProduceDifferentDeterministicSeeds() {
        var room = id("room");
        var position = new BlockPos(4, 70, 9);
        long first = DungeonMobResolver.seed(42L, new java.util.UUID(1, 2), room, position, 1, "main");
        long second = DungeonMobResolver.seed(42L, new java.util.UUID(1, 3), room, position, 1, "main");
        assertNotEquals(first, second);
    }

    private static void assertRejected(com.google.gson.JsonObject json) {
        boolean rejected = false;
        try {
            MobProfile.parse(id("invalid"), json);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        assertTrue(rejected);
    }

    static MobProfile profile(ResourceLocation id, ResourceLocation parent, SpawnRole role,
                              boolean replace, MobProfile.Entry... entries) {
        return new MobProfile(id, parent,
            Map.of(role, new MobProfile.RoleEntries(replace, List.of(entries))),
            MobProfile.Scaling.DEFAULT);
    }

    static MobProfile.Entry entry(String entity, int weight) {
        return new MobProfile.Entry(ResourceLocation.parse(entity), weight, 1, 1,
            0, 6, 1, 16, null, List.of(), true, -1.0, true);
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", path);
    }
}

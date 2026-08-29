package dev.uapi.dungeons.content;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.uapi.dungeons.content.DungeonContentTypes.Boss;
import dev.uapi.dungeons.content.EncounterDefinitions.BossPool;
import dev.uapi.dungeons.content.EncounterDefinitions.MobPool;
import dev.uapi.dungeons.loot.LootProfile;
import dev.uapi.dungeons.loot.LootRole;
import dev.uapi.dungeons.mob.MobProfile;
import dev.uapi.dungeons.mob.SpawnRole;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuiltinPoolResourceTest {
    private static final String ROOT = "data/dedicated_dungeons/dedicated_dungeons/";

    @Test
    void builtInEncounterHasNormalAndArenaEntriesAcrossEveryRank() {
        MobPool pool = MobPool.parse(id("vanilla_scaling"), json("mob_pools/vanilla_scaling.json"));
        RandomSource random = RandomSource.create(42L);

        for (int tier = 0; tier <= 6; tier++) {
            assertTrue(pool.choose(0, tier, random).isPresent(), "no built-in mob at tier " + tier);
            assertTrue(pool.choose(0, tier, random, value -> value.allowedInArena()).isPresent(),
                "no arena-safe built-in mob at tier " + tier);
        }
        assertTrue(pool.entries().stream().anyMatch(value -> value.canEquip() && value.minimumTier() == 0));
        assertTrue(pool.entries().stream().filter(value -> value.minibossLike())
            .noneMatch(value -> value.allowedInArena()));
    }

    @Test
    void integratedBossPoolUsesVerifiedGuardedEntitiesRankLimitsAndVanillaFallback() {
        Map<String, ExpectedBoss> expected = Map.of(
            "irons_dead_king", new ExpectedBoss("irons_spellbooks:dead_king", "irons_spellbooks"),
            "irons_fire_boss", new ExpectedBoss("irons_spellbooks:fire_boss", "irons_spellbooks"),
            "iceandfire_hydra", new ExpectedBoss("iceandfire:hydra", "iceandfire"),
            "iceandfire_cyclops", new ExpectedBoss("iceandfire:cyclops", "iceandfire")
        );
        for (Map.Entry<String, ExpectedBoss> entry : expected.entrySet()) {
            Boss boss = Boss.parse(id(entry.getKey()), json("bosses/" + entry.getKey() + ".json"));
            assertEquals(ResourceLocation.parse(entry.getValue().entity()), boss.entityType());
            assertEquals(List.of(entry.getValue().modId()), boss.requirements().requiredMods());
        }

        BossPool pool = BossPool.parse(id("integrated_bosses"), json("boss_pools/integrated_bosses.json"));
        assertEquals(id("vanilla_bosses"), pool.fallback());
        Set<String> integrated = pool.bosses().stream()
            .map(value -> value.id().getPath()).collect(java.util.stream.Collectors.toSet());
        assertTrue(integrated.containsAll(expected.keySet()));
        assertTrue(integrated.containsAll(Set.of("mowzies_wroughtnaut", "mowzies_frostmaw",
            "mowzies_umvuthi", "friendsandfoes_wildfire", "rotten_dead_beard", "rotten_immortal")));

        Boss hydra = Boss.parse(id("iceandfire_hydra"), json("bosses/iceandfire_hydra.json"));
        assertFalse(hydra.supports(dev.uapi.difficulty.DifficultyRank.E));
        assertFalse(hydra.supports(dev.uapi.difficulty.DifficultyRank.B));
        assertTrue(hydra.supports(dev.uapi.difficulty.DifficultyRank.A));
        Boss deadKing = Boss.parse(id("irons_dead_king"), json("bosses/irons_dead_king.json"));
        assertFalse(deadKing.supports(dev.uapi.difficulty.DifficultyRank.A));
        assertTrue(deadKing.supports(dev.uapi.difficulty.DifficultyRank.S));
    }

    @Test
    void integratedMobPoolCannotExposeMinibossEntriesToAnArena() {
        MobPool pool = MobPool.parse(id("integrated_scaling"), json("mob_pools/integrated_scaling.json"));
        assertTrue(pool.entries().stream().anyMatch(value -> value.requiredMods().contains("mowziesmobs")));
        assertTrue(pool.entries().stream().anyMatch(value -> value.requiredMods().contains("endermanoverhaul")));
        assertTrue(pool.entries().stream().anyMatch(value -> value.requiredMods().contains("friendsandfoes")));
        assertTrue(pool.entries().stream().anyMatch(value -> value.requiredMods().contains("rottencreatures")));
        assertTrue(pool.entries().stream().anyMatch(value -> value.requiredMods().contains("irons_spellbooks")));
        assertTrue(pool.entries().stream().anyMatch(value -> value.requiredMods().contains("iceandfire")));
        assertTrue(pool.entries().stream().filter(value -> value.minibossLike())
            .noneMatch(value -> value.allowedInArena()));
    }

    @Test
    void everyOptionalStandalonePoolIsIncludedInTheIntegratedPools() {
        MobPool integratedMobs = MobPool.parse(id("integrated_scaling"),
            json("mob_pools/integrated_scaling.json"));
        Set<ResourceLocation> integratedEntities = integratedMobs.entries().stream()
            .map(EncounterDefinitions.MobEntry::entityType)
            .collect(java.util.stream.Collectors.toSet());
        for (String poolName : List.of("iceandfire_mobs", "mowzies_mobs", "enderman_overhaul_mobs",
            "irons_spellbooks_mobs", "friendsandfoes_mobs", "rottencreatures_mobs")) {
            MobPool optional = MobPool.parse(id(poolName), json("mob_pools/" + poolName + ".json"));
            assertTrue(integratedEntities.containsAll(optional.entries().stream()
                    .map(EncounterDefinitions.MobEntry::entityType).toList()),
                poolName + " has entities missing from integrated_scaling");
        }

        BossPool integratedBosses = BossPool.parse(id("integrated_bosses"),
            json("boss_pools/integrated_bosses.json"));
        Set<ResourceLocation> integratedBossIds = integratedBosses.bosses().stream()
            .map(DungeonContentTypes.WeightedId::id)
            .collect(java.util.stream.Collectors.toSet());
        for (String poolName : List.of("iceandfire_bosses", "mowzies_bosses",
            "irons_spellbooks_bosses", "friendsandfoes_bosses", "rottencreatures_bosses")) {
            BossPool optional = BossPool.parse(id(poolName), json("boss_pools/" + poolName + ".json"));
            assertTrue(integratedBossIds.containsAll(optional.bosses().stream()
                    .map(DungeonContentTypes.WeightedId::id).toList()),
                poolName + " has bosses missing from integrated_bosses");
        }
    }

    @Test
    void everyDebugArenaItemUsesItsOwnDistinctTexture() {
        Set<String> hashes = new java.util.HashSet<>();
        for (String rank : List.of("e", "d", "c", "b", "a", "s", "anomaly")) {
            String name = "debug_arena_" + rank;
            JsonObject model = resourceJson("assets/dedicated_dungeons/models/item/" + name + ".json");
            assertEquals("dedicated_dungeons:item/" + name,
                model.getAsJsonObject("textures").get("layer0").getAsString());
            byte[] arena = resourceBytes("assets/dedicated_dungeons/textures/item/" + name + ".png");
            byte[] portal = resourceBytes("assets/dedicated_dungeons/textures/item/debug_portal_" + rank + ".png");
            assertFalse(java.util.Arrays.equals(arena, portal), name + " reuses the portal icon");
            hashes.add(java.util.HexFormat.of().formatHex(digest(arena)));
        }
        assertEquals(7, hashes.size(), "arena rank icons must be distinct from one another");
    }

    @Test
    void vanillaBossFallbackCoversEveryDungeonRank() {
        BossPool pool = BossPool.parse(id("vanilla_bosses"), json("boss_pools/vanilla_bosses.json"));
        List<Boss> bosses = pool.bosses().stream()
            .map(value -> Boss.parse(value.id(), json("bosses/" + value.id().getPath() + ".json"))).toList();

        for (dev.uapi.difficulty.DifficultyRank rank : dev.uapi.difficulty.DifficultyRank.values()) {
            assertTrue(bosses.stream().anyMatch(value -> value.supports(rank)),
                "vanilla fallback has no boss for " + rank);
        }
    }

    @Test
    void builtInStructurePersistsLootMarkerBlockEntityAuthoringData() {
        for (String room : List.of("hall", "boss_chamber")) {
            byte[] nbt = resourceBytes("data/dedicated_dungeons/structure/test/" + room + ".nbt");
            String binary;
            try (var compressed = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(nbt))) {
                binary = new String(compressed.readAllBytes(), StandardCharsets.ISO_8859_1);
            } catch (java.io.IOException exception) {
                throw new AssertionError("cannot decompress structure " + room, exception);
            }
            assertTrue(binary.contains("loot_role"), room + " lost loot marker BlockEntity data");
            assertTrue(binary.contains("dedicated_dungeons:loot_marker"), room + " lost loot marker BlockEntity id");
        }
    }

    @Test
    void internalStructureFixturesPersistSpawnerMarkerBlockEntityAuthoringData() {
        for (String room : List.of("hall", "survival_arena")) {
            byte[] nbt = resourceBytes("data/dedicated_dungeons/structure/test/" + room + ".nbt");
            String binary;
            try (var compressed = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(nbt))) {
                binary = new String(compressed.readAllBytes(), StandardCharsets.ISO_8859_1);
            } catch (java.io.IOException exception) {
                throw new AssertionError("cannot decompress structure " + room, exception);
            }
            assertTrue(binary.contains("spawn_role"), room + " lost spawner marker BlockEntity data");
            assertTrue(binary.contains("dedicated_dungeons:spawner_marker"),
                room + " lost spawner marker BlockEntity id");
        }
    }

    @Test
    void bundledTestDungeonIsPlayableAcrossTheSupportedLevelRange() {
        DungeonDefinition dungeon = DungeonDefinition.CODEC.parse(JsonOps.INSTANCE,
            json("dungeons/forgotten_depths.json")).getOrThrow();
        assertEquals(id("basic"), dungeon.archetype());
        assertEquals(id("test_encounters"), dungeon.pools().encounterPool());
        assertEquals(id("vanilla_bosses"), dungeon.pools().bossPool());
        assertEquals("boss_and_encounters", dungeon.rules().clearCondition());
        assertEquals(0, dungeon.minimumLevel());
        assertEquals(1_000_000, dungeon.maximumLevel());
        assertTrue(dungeon.tags().containsAll(List.of("test", "demo", "vanilla")));
    }

    @Test
    void builtInLootProfilesCoverEveryStandardRoleAndUseSingularLootTableResources() {
        LootProfile profile = LootProfile.parse(id("default"), json("loot_profiles/default.json"));
        for (LootRole role : List.of(LootRole.COMMON, LootRole.HIDDEN, LootRole.SUPPLY,
            LootRole.ELITE, LootRole.REWARD, LootRole.BOSS)) {
            var entry = profile.roles().get(role);
            assertNotNull(entry, "default loot profile is missing " + role);
            for (LootProfile.WeightedTable table : entry.tables()) {
                String path = "data/" + table.table().getNamespace() + "/loot_table/"
                    + table.table().getPath() + ".json";
                assertNotNull(BuiltinPoolResourceTest.class.getClassLoader().getResource(path),
                    "missing singular loot_table resource " + path);
            }
        }
        LootProfile themed = LootProfile.parse(id("forgotten_depths"),
            json("loot_profiles/forgotten_depths.json"));
        assertEquals(id("default"), themed.parent());
    }

    @Test
    void defaultMobProfileIsRoleBasedModCompatibleAndKeepsHydraOutOfLowRanks() {
        MobProfile profile = MobProfile.parse(id("default"), json("mob_profiles/default.json"));
        for (SpawnRole role : List.of(SpawnRole.AMBIENT, SpawnRole.COMMON, SpawnRole.RANGED,
            SpawnRole.HEAVY, SpawnRole.SUPPORT, SpawnRole.ELITE, SpawnRole.BOSS)) {
            assertNotNull(profile.roles().get(role), "default mob profile is missing " + role);
        }
        Set<String> optionalMods = profile.roles().values().stream()
            .flatMap(value -> value.entries().stream())
            .flatMap(value -> value.requiredMods().stream())
            .collect(java.util.stream.Collectors.toSet());
        assertTrue(optionalMods.containsAll(Set.of("iceandfire", "mowziesmobs", "endermanoverhaul",
            "irons_spellbooks", "friendsandfoes", "rottencreatures")));
        var hydra = profile.roles().get(SpawnRole.BOSS).entries().stream()
            .filter(value -> value.entity().toString().equals("iceandfire:hydra")).findFirst().orElseThrow();
        assertEquals(4, hydra.minimumDifficulty());
        assertFalse(hydra.arenaAllowed());
        assertTrue(profile.roles().get(SpawnRole.ELITE).entries().stream()
            .noneMatch(MobProfile.Entry::arenaAllowed));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", path);
    }

    private static JsonObject json(String path) {
        return resourceJson(ROOT + path);
    }

    private static JsonObject resourceJson(String path) {
        var stream = BuiltinPoolResourceTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, "missing test resource " + path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new AssertionError("cannot read test resource " + path, exception);
        }
    }

    private static byte[] resourceBytes(String path) {
        var stream = BuiltinPoolResourceTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(stream, "missing test resource " + path);
        try (stream) {
            return stream.readAllBytes();
        } catch (java.io.IOException exception) {
            throw new AssertionError("cannot read test resource " + path, exception);
        }
    }

    private static byte[] digest(byte[] value) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(value);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private record ExpectedBoss(String entity, String modId) {}
}

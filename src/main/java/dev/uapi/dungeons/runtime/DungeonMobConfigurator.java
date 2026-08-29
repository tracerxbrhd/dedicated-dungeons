package dev.uapi.dungeons.runtime;

import dev.uapi.dungeons.DedicatedDungeonsMod;
import dev.uapi.dungeons.config.DungeonServerConfig;
import dev.uapi.integration.IntegrationService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Set;

/**
 * Performs the normal NeoForge mob-spawn finalization and applies conservative, rank-aware equipment.
 *
 * <p>Calling {@link Mob#finalizeSpawn} is also the intentionally dependency-free Apotheosis integration.
 * Standard dungeon mobs use an affix-eligible spawn type while Apotheosis is loaded, allowing its stock
 * random-affix-item augmentation to equip a guaranteed drop. Arena mobs use the same spawn type but have
 * the two documented Apotheosis elite handoff keys removed before joining the level, so they retain affix
 * gear without silently becoming minibosses.</p>
 */
public final class DungeonMobConfigurator {
    private static final String APOTHEOSIS_MINIBOSS = "apoth.miniboss";
    private static final String APOTHEOSIS_MINIBOSS_PLAYER = "apoth.miniboss.player";
    private static final Set<String> OPTIONAL_EQUIPMENT_USERS = Set.of(
        "iceandfire:dread_thrall",
        "iceandfire:dread_knight",
        "iceandfire:dread_lich",
        "rottencreatures:burned",
        "rottencreatures:frostbitten",
        "rottencreatures:swampy",
        "rottencreatures:undead_miner",
        "rottencreatures:mummy",
        "rottencreatures:glacial_hunter",
        "rottencreatures:dead_beard",
        "rottencreatures:immortal",
        "rottencreatures:zap"
    );

    private DungeonMobConfigurator() {}

    public static void prepare(ServerLevel level, Mob mob, int tier, boolean canEquip,
                               double entryEquipmentChance, boolean arena) {
        prepare(level, mob, tier, canEquip, entryEquipmentChance,
            arena ? MobSpawnType.SPAWN_EGG : MobSpawnType.SPAWNER, arena);
    }

    /**
     * Prepares a normal room encounter. Apotheosis excludes {@code SPAWNER} from its built-in
     * {@code random_affix_items} augmentation, so use {@code SPAWN_EGG} only when that mod is present.
     */
    public static void prepareDungeon(ServerLevel level, Mob mob, int tier, boolean canEquip,
                                      double entryEquipmentChance) {
        MobSpawnType spawnType = dungeonSpawnType(IntegrationService.isLoaded("apotheosis"));
        prepare(level, mob, tier, canEquip, entryEquipmentChance, spawnType, false);
    }

    static MobSpawnType dungeonSpawnType(boolean apotheosisLoaded) {
        return apotheosisLoaded ? MobSpawnType.SPAWN_EGG : MobSpawnType.SPAWNER;
    }

    private static void prepare(ServerLevel level, Mob mob, int tier, boolean canEquip,
                                double entryEquipmentChance, MobSpawnType spawnType,
                                boolean suppressApotheosisMiniboss) {
        try {
            mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                spawnType, null);
        } catch (RuntimeException exception) {
            // Optional mobs do not all implement finalizeSpawn defensively. Their base entity is still usable.
            DedicatedDungeonsMod.LOGGER.warn("Mob {} rejected dungeon spawn finalization",
                BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()), exception);
        }
        // Finalization may install a mod mob's signature weapon. Fill only the slots it left empty.
        if (canEquip) maybeEquip(mob, tier, entryEquipmentChance, level.random);
        if (suppressApotheosisMiniboss) {
            mob.getPersistentData().remove(APOTHEOSIS_MINIBOSS);
            mob.getPersistentData().remove(APOTHEOSIS_MINIBOSS_PLAYER);
        }
    }

    public static void prepareBoss(ServerLevel level, Mob mob, int tier) {
        prepare(level, mob, tier, supportsEquipment(mob), -1.0, true);
    }

    public static boolean supportsEquipment(Mob mob) {
        if (mob instanceof Zombie || mob instanceof AbstractSkeleton || mob instanceof AbstractIllager) return true;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        return OPTIONAL_EQUIPMENT_USERS.contains(id.toString());
    }

    private static void maybeEquip(Mob mob, int tier, double requestedChance, RandomSource random) {
        double configured = DungeonServerConfig.MOB_EQUIPMENT_BASE_CHANCE.get()
            + Math.max(0, tier) * DungeonServerConfig.MOB_EQUIPMENT_CHANCE_PER_TIER.get();
        double chance = requestedChance >= 0.0 ? requestedChance : configured;
        chance = Math.min(chance, DungeonServerConfig.MOB_EQUIPMENT_MAX_CHANCE.get());
        if (random.nextDouble() >= chance) return;

        Equipment equipment = equipmentFor(Math.max(0, Math.min(6, tier)), mob);
        equipIfEmpty(mob, EquipmentSlot.MAINHAND, equipment.weapon(), 0.025f);
        if (random.nextDouble() < 0.72) equipIfEmpty(mob, EquipmentSlot.HEAD, equipment.helmet(), 0.015f);
        if (random.nextDouble() < 0.55) equipIfEmpty(mob, EquipmentSlot.CHEST, equipment.chestplate(), 0.015f);
        if (random.nextDouble() < 0.42) equipIfEmpty(mob, EquipmentSlot.LEGS, equipment.leggings(), 0.015f);
        if (random.nextDouble() < 0.62) equipIfEmpty(mob, EquipmentSlot.FEET, equipment.boots(), 0.015f);
    }

    private static void equipIfEmpty(Mob mob, EquipmentSlot slot, Item item, float dropChance) {
        if (item == Items.AIR || !mob.getItemBySlot(slot).isEmpty()) return;
        mob.setItemSlot(slot, new ItemStack(item));
        mob.setDropChance(slot, dropChance);
    }

    private static Equipment equipmentFor(int tier, Mob mob) {
        String path = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();
        boolean ranged = mob instanceof AbstractSkeleton || path.equals("stray") || path.equals("bogged");
        boolean crossbow = path.equals("pillager");
        Item weapon = crossbow ? Items.CROSSBOW : ranged ? Items.BOW : switch (tier) {
            case 0 -> Items.STONE_SWORD;
            case 1, 2 -> Items.IRON_SWORD;
            case 3, 4 -> Items.DIAMOND_SWORD;
            default -> Items.NETHERITE_SWORD;
        };
        return switch (tier) {
            case 0 -> new Equipment(weapon, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE,
                Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS);
            case 1 -> new Equipment(weapon, Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE,
                Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS);
            case 2 -> new Equipment(weapon, Items.IRON_HELMET, Items.IRON_CHESTPLATE,
                Items.IRON_LEGGINGS, Items.IRON_BOOTS);
            case 3 -> new Equipment(weapon, Items.IRON_HELMET, Items.DIAMOND_CHESTPLATE,
                Items.IRON_LEGGINGS, Items.DIAMOND_BOOTS);
            case 4 -> new Equipment(weapon, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE,
                Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
            default -> new Equipment(weapon, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE,
                Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS);
        };
    }

    private record Equipment(Item weapon, Item helmet, Item chestplate, Item leggings, Item boots) {}
}

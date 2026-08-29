package dev.uapi.dungeons.marker;

import dev.uapi.dungeons.DedicatedDungeonsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

public final class DungeonMarkers {
    private static final Map<DungeonMarkerType, DeferredBlock<DungeonMarkerBlock>> BLOCKS =
        new EnumMap<>(DungeonMarkerType.class);

    public static final DeferredBlock<DungeonMarkerBlock> PLAYER_SPAWN = register(DungeonMarkerType.PLAYER_SPAWN);
    public static final DeferredBlock<DungeonMarkerBlock> ROOM_CONNECTOR = register(DungeonMarkerType.ROOM_CONNECTOR);
    public static final DeferredBlock<DungeonMarkerBlock> SPAWNER = register(DungeonMarkerType.SPAWNER);
    public static final DeferredBlock<DungeonMarkerBlock> LOOT = register(DungeonMarkerType.LOOT);
    public static final DeferredBlock<DungeonMarkerBlock> BOSS_SPAWN = register(DungeonMarkerType.BOSS_SPAWN);
    public static final DeferredBlock<DungeonMarkerBlock> EXIT_PORTAL = register(DungeonMarkerType.EXIT_PORTAL);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, DedicatedDungeonsMod.MOD_ID);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LootMarkerBlockEntity>> LOOT_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("loot_marker", () -> BlockEntityType.Builder.of(
            LootMarkerBlockEntity::new, LOOT.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SpawnerMarkerBlockEntity>>
        SPAWNER_BLOCK_ENTITY = BLOCK_ENTITIES.register("spawner_marker", () -> BlockEntityType.Builder.of(
            SpawnerMarkerBlockEntity::new, SPAWNER.get()).build(null));

    private DungeonMarkers() {}

    public static void registerBus(IEventBus bus) {
        DedicatedDungeonsMod.BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }

    public static DeferredBlock<DungeonMarkerBlock> block(DungeonMarkerType type) {
        return BLOCKS.get(type);
    }

    private static DeferredBlock<DungeonMarkerBlock> register(DungeonMarkerType type) {
        String id = type.id();
        DeferredBlock<DungeonMarkerBlock> block = DedicatedDungeonsMod.BLOCKS.register(id,
            () -> new DungeonMarkerBlock(type, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
                .strength(-1.0F, 3_600_000.0F).sound(SoundType.AMETHYST).noLootTable()
                .pushReaction(PushReaction.BLOCK)));
        DedicatedDungeonsMod.ITEMS.register(id,
            () -> new BlockItem(block.get(), new Item.Properties().stacksTo(64)));
        BLOCKS.put(type, block);
        return block;
    }
}

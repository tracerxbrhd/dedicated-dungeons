package dev.uapi.dungeons.marker;

import dev.uapi.dungeons.mob.MobSpawnData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Structure-template and /data authoring carrier for mob spawn overrides; intentionally has no GUI. */
public final class SpawnerMarkerBlockEntity extends BlockEntity {
    private MobSpawnData data = MobSpawnData.EMPTY;

    public SpawnerMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(DungeonMarkers.SPAWNER_BLOCK_ENTITY.get(), pos, state);
    }

    public MobSpawnData data() {
        return data;
    }

    public void data(MobSpawnData value) {
        data = value == null ? MobSpawnData.EMPTY : value;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        data.save(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        try {
            data = MobSpawnData.load(tag);
        } catch (IllegalArgumentException exception) {
            data = MobSpawnData.EMPTY;
        }
    }
}

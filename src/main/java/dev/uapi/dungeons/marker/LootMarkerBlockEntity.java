package dev.uapi.dungeons.marker;

import dev.uapi.dungeons.loot.LootMarkerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Server-side data carrier saved verbatim into structure templates; it intentionally has no menu or GUI. */
public final class LootMarkerBlockEntity extends BlockEntity {
    private LootMarkerData data = LootMarkerData.EMPTY;

    public LootMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(DungeonMarkers.LOOT_BLOCK_ENTITY.get(), pos, state);
    }

    public LootMarkerData data() {
        return data;
    }

    public void data(LootMarkerData value) {
        data = value == null ? LootMarkerData.EMPTY : value;
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
        data = LootMarkerData.load(tag);
    }
}

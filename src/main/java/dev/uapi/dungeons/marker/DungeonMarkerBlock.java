package dev.uapi.dungeons.marker;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Creative-only structure-authoring marker. Generation always replaces it before players enter. */
public final class DungeonMarkerBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<DungeonMarkerBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
        Codec.STRING.xmap(value -> DungeonMarkerType.valueOf(value.toUpperCase(java.util.Locale.ROOT)),
            value -> value.name().toLowerCase(java.util.Locale.ROOT)).fieldOf("marker_type").forGetter(DungeonMarkerBlock::markerType),
        propertiesCodec()
    ).apply(instance, DungeonMarkerBlock::new));
    private final DungeonMarkerType markerType;

    public DungeonMarkerBlock(DungeonMarkerType markerType, BlockBehaviour.Properties properties) {
        super(properties);
        this.markerType = markerType;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public DungeonMarkerType markerType() {
        return markerType;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return switch (markerType) {
            case LOOT -> new LootMarkerBlockEntity(pos, state);
            case SPAWNER -> new SpawnerMarkerBlockEntity(pos, state);
            default -> null;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }
}

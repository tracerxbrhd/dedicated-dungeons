package dev.underworld.dungeons.item;

import dev.underworld.api.difficulty.DifficultyRank;
import dev.underworld.dungeons.config.DungeonServerConfig;
import dev.underworld.dungeons.portal.PortalOrigin;
import dev.underworld.dungeons.runtime.PortalManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class DebugPortalItem extends Item {
    private final DifficultyRank rank;
    public DebugPortalItem(DifficultyRank rank, Properties properties) { super(properties); this.rank = rank; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.sidedSuccess(stack, true);
        if (!(player instanceof ServerPlayer serverPlayer) || !serverPlayer.hasPermissions(2)
            || !DungeonServerConfig.DEBUG_ITEMS_ENABLED.get()) return InteractionResultHolder.fail(stack);
        return PortalManager.get(serverPlayer.getServer()).createFor(serverPlayer, rank, PortalOrigin.DEBUG).isPresent()
            ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
    }
}

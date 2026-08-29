package dev.uapi.dungeons.item;

import dev.uapi.difficulty.DifficultyRank;
import dev.uapi.dungeons.config.DungeonServerConfig;
import dev.uapi.dungeons.runtime.DungeonRuntime;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Operator-only shortcut that creates and enters a survival arena without waiting for a random portal. */
public final class DebugArenaItem extends Item {
    private final DifficultyRank rank;

    public DebugArenaItem(DifficultyRank rank, Properties properties) {
        super(properties);
        this.rank = rank;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.sidedSuccess(stack, true);
        if (!(player instanceof ServerPlayer serverPlayer) || !serverPlayer.hasPermissions(2)
            || !DungeonServerConfig.DEBUG_ITEMS_ENABLED.get()) return InteractionResultHolder.fail(stack);
        return DungeonRuntime.get(serverPlayer.getServer()).startSurvivalDirect(serverPlayer, rank)
            ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dedicated_dungeons.admin_item")
            .withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("tooltip.dedicated_dungeons.creative_testing")
            .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dedicated_dungeons.debug_arena", rank.name())
            .withStyle(ChatFormatting.AQUA));
    }
}

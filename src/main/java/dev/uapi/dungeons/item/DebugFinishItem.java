package dev.uapi.dungeons.item;

import dev.uapi.instance.InstanceManager;
import dev.uapi.dungeons.config.DungeonServerConfig;
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

public final class DebugFinishItem extends Item {
    public DebugFinishItem(Properties properties) { super(properties); }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) return InteractionResultHolder.sidedSuccess(stack, true);
        if (!(player instanceof ServerPlayer serverPlayer) || !serverPlayer.hasPermissions(2)
            || !DungeonServerConfig.DEBUG_ITEMS_ENABLED.get()) return InteractionResultHolder.fail(stack);
        InstanceManager manager = InstanceManager.get(serverPlayer.getServer());
        boolean done = manager.findByPlayer(serverPlayer.getUUID())
            .map(instance -> manager.complete(instance.id(), "debug_item")).orElse(false);
        return done ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dedicated_dungeons.admin_item").withStyle(ChatFormatting.DARK_RED));
        tooltip.add(Component.translatable("tooltip.dedicated_dungeons.creative_testing").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.dedicated_dungeons.debug_finish").withStyle(ChatFormatting.GOLD));
    }
}

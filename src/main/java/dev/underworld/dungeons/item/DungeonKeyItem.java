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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class DungeonKeyItem extends Item {
    private final DifficultyRank rank;
    public DungeonKeyItem(DifficultyRank rank, Properties properties) { super(properties); this.rank = rank; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer))
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        boolean created = PortalManager.get(serverPlayer.getServer()).createFor(serverPlayer, rank, PortalOrigin.KEY).isPresent();
        if (created && DungeonServerConfig.CONSUME_KEY.get() && !player.getAbilities().instabuild) stack.shrink(1);
        return created ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.dedicated_dungeons.dungeon_key", rank.displayName()));
    }
}

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

public final class DungeonKeyItem extends Item {
    public DungeonKeyItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer))
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
        DifficultyRank rank = DifficultyRank.byName(DungeonServerConfig.KEY_RANK.get());
        boolean created = PortalManager.get(serverPlayer.getServer()).createFor(serverPlayer, rank, PortalOrigin.KEY).isPresent();
        if (created && DungeonServerConfig.CONSUME_KEY.get() && !player.getAbilities().instabuild) stack.shrink(1);
        return created ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }
}

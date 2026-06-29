package dev.underworld.dungeons.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.underworld.api.difficulty.DifficultyRank;
import dev.underworld.api.instance.InstanceManager;
import dev.underworld.dungeons.runtime.DungeonRuntime;
import dev.underworld.dungeons.runtime.PortalManager;
import dev.underworld.dungeons.portal.PortalOrigin;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class DungeonCommands {
    private DungeonCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("dungeon").requires(source -> source.hasPermission(2))
            .then(Commands.literal("portal").then(Commands.argument("rank", StringArgumentType.word()).executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                return PortalManager.get(context.getSource().getServer()).createFor(player,
                    DifficultyRank.byName(StringArgumentType.getString(context, "rank")), PortalOrigin.COMMAND).isPresent() ? 1 : 0;
            })))
            .then(Commands.literal("portal_for").then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("rank", StringArgumentType.word()).executes(context ->
                    PortalManager.get(context.getSource().getServer()).createFor(EntityArgument.getPlayer(context, "player"),
                        DifficultyRank.byName(StringArgumentType.getString(context, "rank")), PortalOrigin.COMMAND).isPresent() ? 1 : 0))))
            .then(Commands.literal("active").executes(context -> {
                int portals = PortalManager.get(context.getSource().getServer()).activeCount();
                int sessions = DungeonRuntime.get(context.getSource().getServer()).sessions().size();
                context.getSource().sendSuccess(() -> Component.literal("Portals: " + portals + ", sessions: " + sessions), false);
                return portals + sessions;
            }))
            .then(Commands.literal("finish").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                var manager = InstanceManager.get(context.getSource().getServer());
                return manager.findByPlayer(player.getUUID()).map(instance -> manager.complete(instance.id(), "admin_command") ? 1 : 0).orElse(0);
            }))
            .then(Commands.literal("remove_portals").executes(context ->
                PortalManager.get(context.getSource().getServer()).removeAll("admin_command")))
            .then(Commands.literal("cleanup").executes(context ->
                DungeonRuntime.get(context.getSource().getServer()).cleanupAll())));
    }
}

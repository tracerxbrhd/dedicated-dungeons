package dev.underworld.dungeons.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.underworld.api.difficulty.DifficultyRank;
import dev.underworld.api.instance.InstanceManager;
import dev.underworld.api.instance.InstanceType;
import dev.underworld.dungeons.content.DungeonContentRegistry;
import dev.underworld.dungeons.runtime.DungeonRuntime;
import dev.underworld.dungeons.runtime.PortalManager;
import dev.underworld.dungeons.portal.PortalOrigin;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
            }).then(Commands.argument("archetype", ResourceLocationArgument.id()).executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();
                return PortalManager.get(context.getSource().getServer()).createFor(player,
                    DifficultyRank.byName(StringArgumentType.getString(context, "rank")), PortalOrigin.COMMAND,
                    ResourceLocationArgument.getId(context, "archetype")).isPresent() ? 1 : 0;
            }))))
            .then(Commands.literal("portal_for").then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("rank", StringArgumentType.word()).executes(context ->
                    PortalManager.get(context.getSource().getServer()).createFor(EntityArgument.getPlayer(context, "player"),
                        DifficultyRank.byName(StringArgumentType.getString(context, "rank")), PortalOrigin.COMMAND).isPresent() ? 1 : 0)
                    .then(Commands.argument("archetype", ResourceLocationArgument.id()).executes(context ->
                        PortalManager.get(context.getSource().getServer()).createFor(EntityArgument.getPlayer(context, "player"),
                            DifficultyRank.byName(StringArgumentType.getString(context, "rank")), PortalOrigin.COMMAND,
                            ResourceLocationArgument.getId(context, "archetype")).isPresent() ? 1 : 0)))))
            .then(Commands.literal("start").then(Commands.argument("player", EntityArgument.player())
                .then(Commands.argument("rank", StringArgumentType.word())
                    .then(Commands.argument("archetype", ResourceLocationArgument.id()).executes(context ->
                        DungeonRuntime.get(context.getSource().getServer()).startDirect(EntityArgument.getPlayer(context, "player"),
                            DifficultyRank.byName(StringArgumentType.getString(context, "rank")),
                            ResourceLocationArgument.getId(context, "archetype")) ? 1 : 0)))))
            .then(Commands.literal("start_survival").then(Commands.argument("rank", StringArgumentType.word())
                .executes(context -> DungeonRuntime.get(context.getSource().getServer()).startSurvivalDirect(
                    context.getSource().getPlayerOrException(),
                    DifficultyRank.byName(StringArgumentType.getString(context, "rank"))) ? 1 : 0)))
            .then(Commands.literal("spawn_portal").then(Commands.argument("type", StringArgumentType.word())
                .then(Commands.argument("rank", StringArgumentType.word()).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    InstanceType type = parseType(StringArgumentType.getString(context, "type"));
                    return PortalManager.get(context.getSource().getServer()).createFor(player,
                        DifficultyRank.byName(StringArgumentType.getString(context, "rank")), PortalOrigin.COMMAND,
                        ResourceLocation.fromNamespaceAndPath("dedicated_dungeons", "basic"), type).isPresent() ? 1 : 0;
                }))))
            .then(Commands.literal("list").executes(context -> {
                var manager = InstanceManager.get(context.getSource().getServer());
                DungeonRuntime runtime = DungeonRuntime.get(context.getSource().getServer());
                for (var instance : manager.all()) {
                    var session = runtime.session(instance.id());
                    String detail = session != null && session.plan() != null
                        ? ", theme=" + session.plan().themeId() + ", pieces=" + session.plan().pieces().size() : ", legacy/future portal";
                    context.getSource().sendSuccess(() -> Component.literal(instance.id() + " phase=" + instance.phase()
                        + " rank=" + instance.difficulty() + " definition=" + instance.definitionId() + detail), false);
                }
                return manager.all().size();
            }))
            .then(Commands.literal("tp").then(Commands.argument("instance", UuidArgument.uuid()).executes(context ->
                DungeonRuntime.get(context.getSource().getServer()).teleportToSession(context.getSource().getPlayerOrException(),
                    UuidArgument.getUuid(context, "instance")) ? 1 : 0)))
            .then(Commands.literal("validate_data").executes(context -> {
                var report = DungeonContentRegistry.report();
                context.getSource().sendSuccess(() -> Component.literal("Dungeon data: themes=" + report.themes()
                    + ", archetypes=" + report.archetypes() + ", pools=" + report.roomPools() + ", rooms=" + report.rooms()
                    + ", connectors=" + report.connectors() + ", bosses=" + report.bosses() + ", errors=" + report.errors()
                    + ", warnings=" + report.warnings()), false);
                report.issues().stream().limit(20).forEach(issue -> context.getSource().sendFailure(
                    Component.literal(issue.severity() + " " + issue.definition() + ": " + issue.message())));
                return report.valid() ? 1 : 0;
            }))
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

    private static InstanceType parseType(String value) {
        return value.equalsIgnoreCase("survival") || value.equalsIgnoreCase("survival_arena")
            ? InstanceType.SURVIVAL_ARENA : InstanceType.BOSS_DUNGEON;
    }
}

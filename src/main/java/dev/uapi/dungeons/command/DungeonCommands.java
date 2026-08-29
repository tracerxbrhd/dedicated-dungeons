package dev.uapi.dungeons.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.uapi.difficulty.DifficultyRank;
import dev.uapi.dungeons.DedicatedDungeonsMod;
import dev.uapi.dungeons.content.DungeonContentRegistry;
import dev.uapi.dungeons.content.DungeonSelector;
import dev.uapi.dungeons.loot.LootProfileManager;
import dev.uapi.dungeons.mob.MobProfileManager;
import dev.uapi.dungeons.runtime.DungeonRuntime;
import dev.uapi.instance.InstanceManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class DungeonCommands {
    private DungeonCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> create() {
        return Commands.literal("dungeons").requires(source -> source.hasPermission(2))
            .then(Commands.literal("validate").executes(context -> validate(context.getSource())))
            .then(Commands.literal("validate_data").executes(context -> validate(context.getSource())))
            .then(Commands.literal("list")
                .then(Commands.literal("dungeons").executes(context -> listDungeons(context.getSource())))
                .then(Commands.literal("rooms").executes(context -> listRooms(context.getSource())))
                .then(Commands.literal("instances").executes(context -> listInstances(context.getSource()))))
            .then(Commands.literal("generate")
                .then(Commands.argument("dungeon", ResourceLocationArgument.id())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(
                        DungeonContentRegistry.dungeons().keySet(), builder))
                    .executes(context -> generate(context.getSource(),
                        ResourceLocationArgument.getId(context, "dungeon"), null))))
            .then(Commands.literal("generate_for_level")
                .then(Commands.argument("level", IntegerArgumentType.integer(0, 1_000_000))
                    .executes(context -> generateForLevel(context.getSource(),
                        IntegerArgumentType.getInteger(context, "level")))))
            .then(Commands.literal("inspect").executes(context -> inspect(context.getSource())))
            .then(Commands.literal("abort")
                .then(Commands.argument("instance", UuidArgument.uuid()).executes(context -> abort(
                    context.getSource(), UuidArgument.getUuid(context, "instance")))))
            .then(Commands.literal("cleanup")
                .then(Commands.argument("instance", UuidArgument.uuid()).executes(context -> cleanup(
                    context.getSource(), UuidArgument.getUuid(context, "instance"))))
                .executes(context -> {
                    int count = DungeonRuntime.get(context.getSource().getServer()).cleanupAll();
                    context.getSource().sendSuccess(() -> Component.literal("Scheduled cleanup for " + count + " dungeon instances"), true);
                    return count;
                }))
            .then(Commands.literal("reload_debug").executes(context -> reloadDebug(context.getSource())));
    }

    private static int validate(CommandSourceStack source) {
        var report = DungeonContentRegistry.report();
        var loot = LootProfileManager.report();
        var mobs = MobProfileManager.report();
        source.sendSuccess(() -> Component.literal("Dungeon data: dungeons=" + report.dungeons()
            + ", level_profiles=" + report.levelProfiles() + ", rooms=" + report.rooms()
            + ", room_pools=" + report.roomPools() + ", connectors=" + report.connectors()
            + ", mob_pools=" + report.mobPools() + ", encounter_pools=" + report.encounterPools()
            + ", boss_pools=" + report.bossPools() + ", loot_profiles=" + loot.profiles()
            + ", loot_tables=" + loot.lootTables() + ", mob_profiles=" + mobs.accepted()
            + "/" + mobs.discovered()
            + ", errors=" + (report.errors() + loot.errors().size() + mobs.errors().size())
            + ", warnings=" + (report.warnings() + loot.warnings().size() + mobs.warnings().size())), false);
        report.issues().stream().limit(50).forEach(issue -> {
            Component message = Component.literal(issue.severity() + " " + issue.definition() + ": " + issue.message());
            if (issue.severity() == DungeonContentRegistry.Severity.ERROR) source.sendFailure(message);
            else source.sendSuccess(() -> message, false);
        });
        loot.errors().stream().limit(25).forEach(value ->
            source.sendFailure(Component.literal("ERROR loot profile: " + value)));
        loot.warnings().stream().limit(25).forEach(value ->
            source.sendSuccess(() -> Component.literal("WARNING loot profile: " + value), false));
        mobs.errors().stream().limit(25).forEach(value ->
            source.sendFailure(Component.literal("ERROR mob profile: " + value)));
        mobs.warnings().stream().limit(25).forEach(value ->
            source.sendSuccess(() -> Component.literal("WARNING mob profile: " + value), false));
        return report.valid() && loot.valid() && mobs.errors().isEmpty() ? 1 : 0;
    }

    private static int listDungeons(CommandSourceStack source) {
        DungeonContentRegistry.dungeons().forEach((id, definition) -> source.sendSuccess(() -> Component.literal(
            id + " levels=" + definition.minimumLevel() + ".." + definition.maximumLevel()
                + " recommended=" + definition.recommendedLevel() + " weight=" + definition.baseWeight()), false));
        return DungeonContentRegistry.dungeons().size();
    }

    private static int listRooms(CommandSourceStack source) {
        DungeonContentRegistry.rooms().forEach((id, room) -> source.sendSuccess(() -> Component.literal(
            id + " size=" + room.size() + " tags=" + room.tags() + " connectors=" + room.connectors().size()), false));
        return DungeonContentRegistry.rooms().size();
    }

    private static int listInstances(CommandSourceStack source) {
        var manager = InstanceManager.get(source.getServer());
        DungeonRuntime runtime = DungeonRuntime.get(source.getServer());
        manager.all().forEach(instance -> {
            var session = runtime.session(instance.id());
            String detail = session == null ? " waiting_for_entry" : " level=" + session.selectedLevel()
                + " seed=" + session.seed() + " pieces=" + (session.plan() == null ? 0 : session.plan().pieces().size());
            source.sendSuccess(() -> Component.literal(instance.id() + " phase=" + instance.phase()
                + " definition=" + instance.definitionId() + detail), false);
        });
        return manager.all().size();
    }

    private static int generate(CommandSourceStack source, ResourceLocation dungeon, Integer selectedLevel) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            if (DungeonContentRegistry.dungeon(dungeon).isEmpty()) {
                source.sendFailure(Component.literal("Unknown or invalid dungeon: " + dungeon));
                return 0;
            }
            int level = selectedLevel == null ? player.experienceLevel : selectedLevel;
            boolean started = DungeonRuntime.get(source.getServer()).startDirect(player, rankForLevel(level), dungeon, level);
            if (!started) source.sendFailure(Component.literal("Dungeon generation failed; see server log for the controlled rollback reason"));
            return started ? 1 : 0;
        } catch (Exception exception) {
            DedicatedDungeonsMod.LOGGER.error("Admin dungeon generation failed", exception);
            source.sendFailure(Component.literal("Dungeon generation failed; details were written to the server log"));
            return 0;
        }
    }

    private static int generateForLevel(CommandSourceStack source, int level) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); }
        catch (Exception exception) { source.sendFailure(Component.literal("This command requires a player source")); return 0; }
        var selected = DungeonContentRegistry.chooseDungeon(level, player.level().dimension().location(), player.getRandom());
        if (selected.isEmpty()) {
            source.sendFailure(Component.literal("No valid dungeon for level " + level + " in " + player.level().dimension().location()));
            return 0;
        }
        DungeonSelector.Candidate candidate = selected.orElseThrow();
        source.sendSuccess(() -> Component.literal("Selected " + candidate.id() + " weight=" + candidate.weight()
            + (candidate.fallback() ? " (nearest-tier fallback)" : "")), false);
        return generate(source, candidate.id(), level);
    }

    private static int inspect(CommandSourceStack source) {
        ServerPlayer player;
        try { player = source.getPlayerOrException(); }
        catch (Exception exception) { source.sendFailure(Component.literal("This command requires a player source")); return 0; }
        var instance = InstanceManager.get(source.getServer()).findAssignedByPlayer(player.getUUID()).orElse(null);
        if (instance == null) { source.sendFailure(Component.literal("You are not assigned to a dungeon instance")); return 0; }
        var session = DungeonRuntime.get(source.getServer()).session(instance.id());
        source.sendSuccess(() -> Component.literal("Instance " + instance.id() + " phase=" + instance.phase()
            + " definition=" + instance.definitionId() + " level=" + (session == null ? "pending" : session.selectedLevel())
            + " bounds=" + (session == null ? "pending" : session.bounds())), false);
        return 1;
    }

    private static int abort(CommandSourceStack source, UUID instanceId) {
        boolean result = InstanceManager.get(source.getServer()).fail(instanceId, "admin_abort");
        if (!result) source.sendFailure(Component.literal("Instance cannot be aborted: " + instanceId));
        return result ? 1 : 0;
    }

    private static int cleanup(CommandSourceStack source, UUID instanceId) {
        boolean result = DungeonRuntime.get(source.getServer()).cleanup(instanceId);
        if (!result) source.sendFailure(Component.literal("Unknown dungeon session: " + instanceId));
        return result ? 1 : 0;
    }

    private static int reloadDebug(CommandSourceStack source) {
        source.getServer().reloadResources(source.getServer().getPackRepository().getSelectedIds())
            .whenComplete((ignored, error) -> source.getServer().execute(() -> {
                if (error == null) {
                    source.sendSuccess(() -> Component.literal("Datapacks reloaded; run /uapi dungeons validate for the full report"), true);
                } else {
                    DedicatedDungeonsMod.LOGGER.error("Dungeon reload_debug failed", error);
                    source.sendFailure(Component.literal("Datapack reload failed; details were written to the server log"));
                }
            }));
        source.sendSuccess(() -> Component.literal("Datapack reload scheduled"), false);
        return 1;
    }

    private static DifficultyRank rankForLevel(int level) {
        DifficultyRank selected = DifficultyRank.E;
        for (DifficultyRank rank : DifficultyRank.values()) if (level >= rank.minimumLevel()) selected = rank;
        return selected;
    }
}

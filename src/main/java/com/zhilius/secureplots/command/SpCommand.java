package com.zhilius.secureplots.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.config.SecurePlotsConfig;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.registry.Registries;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

import java.util.*;

public class SpCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
                registerCommands(dispatcher));
    }

    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        for (String name : new String[]{"sp", "secureplots"}) {
            dispatcher.register(CommandManager.literal(name)

                // /sp help
                .then(CommandManager.literal("help")
                        .executes(ctx -> executeHelp(ctx.getSource())))

                // /sp view
                .then(CommandManager.literal("view")
                        .executes(ctx -> executeView(ctx.getSource())))

                // /sp list
                .then(CommandManager.literal("list")
                        .executes(ctx -> executeList(ctx.getSource())))

                // /sp info [plot]
                .then(CommandManager.literal("info")
                        .executes(ctx -> executeInfo(ctx.getSource(), null))
                        .then(CommandManager.argument("plot", StringArgumentType.greedyString())
                                .executes(ctx -> executeInfo(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "plot")))))

                // /sp rename <new name>
                .then(CommandManager.literal("rename")
                        .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                .executes(ctx -> executeRename(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name")))))

                // /sp add <player> <plot|all>
                .then(CommandManager.literal("add")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                                .then(CommandManager.argument("plot", StringArgumentType.greedyString())
                                        .executes(ctx -> executeAdd(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                StringArgumentType.getString(ctx, "plot"))))))

                // /sp remove <player> <plot|all>
                .then(CommandManager.literal("remove")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                                .then(CommandManager.argument("plot", StringArgumentType.greedyString())
                                        .executes(ctx -> executeRemove(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                StringArgumentType.getString(ctx, "plot"))))))

                // /sp tp [plot]
                .then(CommandManager.literal("tp")
                        .executes(ctx -> executeTp(ctx.getSource(), null))
                        .then(CommandManager.argument("plot", StringArgumentType.greedyString())
                                .executes(ctx -> executeTp(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "plot")))))

                // /sp flag [flag] [value] [plot]
                .then(CommandManager.literal("flag")
                        .executes(ctx -> executeFlagList(ctx.getSource()))
                        .then(CommandManager.argument("flag", StringArgumentType.word())
                                .executes(ctx -> executeFlagInfo(ctx.getSource(), StringArgumentType.getString(ctx, "flag")))
                                .then(CommandManager.argument("value", BoolArgumentType.bool())
                                        .executes(ctx -> executeFlagSet(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "flag"),
                                                BoolArgumentType.getBool(ctx, "value"),
                                                null))
                                        .then(CommandManager.argument("plot", StringArgumentType.greedyString())
                                                .executes(ctx -> executeFlagSet(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "flag"),
                                                        BoolArgumentType.getBool(ctx, "value"),
                                                        StringArgumentType.getString(ctx, "plot")))))))

                // /sp perm [player] [perm] [value] [plot]
                .then(CommandManager.literal("perm")
                        .executes(ctx -> executePermList(ctx.getSource()))
                        .then(CommandManager.argument("player", StringArgumentType.word())
                                .then(CommandManager.argument("perm", StringArgumentType.word())
                                        .executes(ctx -> executePermShow(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "player"),
                                                StringArgumentType.getString(ctx, "perm")))
                                        .then(CommandManager.argument("value", BoolArgumentType.bool())
                                                .executes(ctx -> executePermSet(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "player"),
                                                        StringArgumentType.getString(ctx, "perm"),
                                                        BoolArgumentType.getBool(ctx, "value"),
                                                        null))
                                                .then(CommandManager.argument("plot", StringArgumentType.greedyString())
                                                        .executes(ctx -> executePermSet(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "player"),
                                                                StringArgumentType.getString(ctx, "perm"),
                                                                BoolArgumentType.getBool(ctx, "value"),
                                                                StringArgumentType.getString(ctx, "plot"))))))))

                // /sp fly [value] [plot]
                .then(CommandManager.literal("fly")
                        .executes(ctx -> executeFlyToggle(ctx.getSource(), null))
                        .then(CommandManager.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> executeFlyToggle(ctx.getSource(),
                                        BoolArgumentType.getBool(ctx, "value")))
                                .then(CommandManager.argument("plot", StringArgumentType.greedyString())
                                        .executes(ctx -> executeFlySet(
                                                ctx.getSource(),
                                                BoolArgumentType.getBool(ctx, "value"),
                                                StringArgumentType.getString(ctx, "plot"))))))

                // /sp group ...
                .then(CommandManager.literal("group")
                        .executes(ctx -> executeGroupList(ctx.getSource()))
                        .then(CommandManager.literal("create")
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .executes(ctx -> executeGroupCreate(
                                                ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                        .then(CommandManager.literal("delete")
                                .then(CommandManager.argument("name", StringArgumentType.word())
                                        .executes(ctx -> executeGroupDelete(
                                                ctx.getSource(), StringArgumentType.getString(ctx, "name")))))
                        .then(CommandManager.literal("addmember")
                                .then(CommandManager.argument("group", StringArgumentType.word())
                                        .then(CommandManager.argument("player", StringArgumentType.word())
                                                .executes(ctx -> executeGroupAddMember(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "group"),
                                                        StringArgumentType.getString(ctx, "player"))))))
                        .then(CommandManager.literal("removemember")
                                .then(CommandManager.argument("group", StringArgumentType.word())
                                        .then(CommandManager.argument("player", StringArgumentType.word())
                                                .executes(ctx -> executeGroupRemoveMember(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "group"),
                                                        StringArgumentType.getString(ctx, "player"))))))
                        .then(CommandManager.literal("setperm")
                                .then(CommandManager.argument("group", StringArgumentType.word())
                                        .then(CommandManager.argument("perm", StringArgumentType.word())
                                                .then(CommandManager.argument("value", BoolArgumentType.bool())
                                                        .executes(ctx -> executeGroupSetPerm(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "group"),
                                                                StringArgumentType.getString(ctx, "perm"),
                                                                BoolArgumentType.getBool(ctx, "value"))))))))

                // /sp plot particle <type|clear>
                // /sp plot weather <clear|rain|thunder>
                // /sp plot time <day|night|noon|midnight|<ticks>|reset>
                // /sp plot music <sound_id|clear>
                .then(CommandManager.literal("plot")
                        .then(CommandManager.literal("particle")
                                .executes(ctx -> executeParticleHelp(ctx.getSource()))
                                .then(CommandManager.argument("type", StringArgumentType.word())
                                        .suggests((ctx, builder) -> suggestParticles(builder))
                                        .executes(ctx -> executeSetParticle(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "type")))))
                        .then(CommandManager.literal("weather")
                                .then(CommandManager.argument("type", StringArgumentType.word())
                                        .executes(ctx -> executeSetWeather(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "type")))))
                        .then(CommandManager.literal("time")
                                .then(CommandManager.argument("value", StringArgumentType.word())
                                        .executes(ctx -> executeSetTime(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "value")))))
                        .then(CommandManager.literal("music")
                                .executes(ctx -> executeMusicHelp(ctx.getSource()))
                                .then(CommandManager.argument("sound", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> suggestMusic(builder))
                                        .executes(ctx -> executeSetMusic(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "sound"))))))
            );
        }
    }







    // ── /sp plot particle (no args) → list options ────────────────────────────
    private static int executeParticleHelp(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        player.sendMessage(Text.literal("§e══ Particle options ══"), false);
        player.sendMessage(Text.literal("§7Usage: §e/sp plot particle <type|clear>"), false);
        player.sendMessage(Text.literal("§7Type §eclear§7 to remove particles."), false);
        player.sendMessage(Text.literal("§7Common options (Tab autocompletes all):"), false);
        for (String p : COMMON_PARTICLES) {
            if (!p.equals("clear")) player.sendMessage(Text.literal("  §a" + p), false);
        }
        return 1;
    }

    // ── /sp plot music (no args) → list options ────────────────────────────
    private static int executeMusicHelp(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        player.sendMessage(Text.literal("§e══ Music options ══"), false);
        player.sendMessage(Text.literal("§7Usage: §e/sp plot music <sound_id|clear>"), false);
        player.sendMessage(Text.literal("§7Type §eclear§7 to remove music."), false);
        player.sendMessage(Text.literal("§7Music disc options:"), false);
        for (String s : COMMON_MUSIC) {
            if (s.contains("music_disc")) player.sendMessage(Text.literal("  §b" + s), false);
        }
        player.sendMessage(Text.literal("§7Background music options:"), false);
        for (String s : COMMON_MUSIC) {
            if (s.contains("music.") && !s.equals("clear")) player.sendMessage(Text.literal("  §b" + s), false);
        }
        return 1;
    }

    // ── Suggestion providers ───────────────────────────────────────────────────

    private static final String[] COMMON_PARTICLES = {
        "clear",
        "minecraft:happy_villager", "minecraft:angry_villager",
        "minecraft:heart", "minecraft:crit", "minecraft:enchanted_hit",
        "minecraft:flame", "minecraft:smoke", "minecraft:large_smoke",
        "minecraft:soul_fire_flame", "minecraft:soul",
        "minecraft:snowflake", "minecraft:snow", "minecraft:rain",
        "minecraft:bubble", "minecraft:bubble_pop", "minecraft:dripping_water",
        "minecraft:dripping_lava", "minecraft:falling_water", "minecraft:falling_lava",
        "minecraft:portal", "minecraft:end_rod", "minecraft:witch",
        "minecraft:totem_of_undying", "minecraft:explosion", "minecraft:poof",
        "minecraft:firework", "minecraft:flash", "minecraft:glow",
        "minecraft:electric_spark", "minecraft:scrape", "minecraft:spore_blossom_air",
        "minecraft:cherry_leaves", "minecraft:dripping_honey", "minecraft:falling_honey",
        "minecraft:mycelium", "minecraft:enchant", "minecraft:nautilus",
        "minecraft:note", "minecraft:dragon_breath", "minecraft:suspended",
        "minecraft:crimson_spore", "minecraft:warped_spore"
    };

    private static final String[] COMMON_MUSIC = {
        "clear",
        "minecraft:music.game", "minecraft:music.creative", "minecraft:music.end",
        "minecraft:music.nether.basalt_deltas", "minecraft:music.nether.crimson_forest",
        "minecraft:music.nether.nether_wastes", "minecraft:music.nether.soul_sand_valley",
        "minecraft:music.nether.warped_forest",
        "minecraft:music.under_water",
        "minecraft:music_disc.13", "minecraft:music_disc.cat", "minecraft:music_disc.blocks",
        "minecraft:music_disc.chirp", "minecraft:music_disc.far", "minecraft:music_disc.mall",
        "minecraft:music_disc.mellohi", "minecraft:music_disc.stal", "minecraft:music_disc.strad",
        "minecraft:music_disc.ward", "minecraft:music_disc.11", "minecraft:music_disc.wait",
        "minecraft:music_disc.otherside", "minecraft:music_disc.5",
        "minecraft:music_disc.pigstep", "minecraft:music_disc.relic",
        "minecraft:music_disc.creator", "minecraft:music_disc.creator_music_box",
        "minecraft:music_disc.precipice"
    };

    private static java.util.concurrent.CompletableFuture<Suggestions> suggestParticles(SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        for (String s : COMMON_PARTICLES) {
            if (s.startsWith(remaining) || s.contains(remaining)) builder.suggest(s);
        }
        // Also suggest from the live registry
        for (net.minecraft.util.Identifier id : Registries.PARTICLE_TYPE.getIds()) {
            String full = id.toString();
            if (full.startsWith(remaining) || full.contains(remaining)) builder.suggest(full);
        }
        return builder.buildFuture();
    }

    private static java.util.concurrent.CompletableFuture<Suggestions> suggestMusic(SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        for (String s : COMMON_MUSIC) {
            if (s.startsWith(remaining) || s.contains(remaining)) builder.suggest(s);
        }
        // Also suggest from the live registry
        for (net.minecraft.util.Identifier id : Registries.SOUND_EVENT.getIds()) {
            String full = id.toString();
            if ((full.contains("music") || full.contains("disc")) &&
                    (full.startsWith(remaining) || full.contains(remaining))) builder.suggest(full);
        }
        return builder.buildFuture();
    }

    // ── /sp help ──────────────────────────────────────────────────────────────
    private static int executeHelp(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        player.sendMessage(Text.translatable("sp.help.header"), false);
        player.sendMessage(Text.translatable("sp.help.add"), false);
        player.sendMessage(Text.translatable("sp.help.flag"), false);
        player.sendMessage(Text.translatable("sp.help.flag_set"), false);
        player.sendMessage(Text.translatable("sp.help.fly"), false);
        player.sendMessage(Text.translatable("sp.help.group"), false);
        player.sendMessage(Text.translatable("sp.help.group_addmember"), false);
        player.sendMessage(Text.translatable("sp.help.group_create"), false);
        player.sendMessage(Text.translatable("sp.help.group_delete"), false);
        player.sendMessage(Text.translatable("sp.help.group_removemember"), false);
        player.sendMessage(Text.translatable("sp.help.group_setperm"), false);
        player.sendMessage(Text.translatable("sp.help.info"), false);
        player.sendMessage(Text.translatable("sp.help.list"), false);
        player.sendMessage(Text.translatable("sp.help.perm"), false);
        player.sendMessage(Text.translatable("sp.help.perm_set"), false);
        player.sendMessage(Text.translatable("sp.help.remove"), false);
        player.sendMessage(Text.translatable("sp.help.rename"), false);
        player.sendMessage(Text.translatable("sp.help.tp"), false);
        player.sendMessage(Text.translatable("sp.help.view"), false);
        player.sendMessage(Text.translatable("sp.help.plot_particle"), false);
        player.sendMessage(Text.translatable("sp.help.plot_weather"), false);
        player.sendMessage(Text.translatable("sp.help.plot_time"), false);
        player.sendMessage(Text.translatable("sp.help.plot_music"), false);
        player.sendMessage(Text.translatable("sp.help.footer"), false);
        return 1;
    }

    // ── /sp view ──────────────────────────────────────────────────────────────
    private static int executeView(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotData nearest = getNearestOwnedPlot(player, PlotManager.getOrCreate(player.getServerWorld()));
        if (nearest == null) {
            player.sendMessage(Text.translatable("sp.error.no_plots").formatted(Formatting.RED), false);
            return 0;
        }
        ModPackets.sendShowPlotBorder(player, nearest);
        player.sendMessage(
            Text.translatable("sp.view.showing",
                Text.literal(nearest.getPlotName()).formatted(Formatting.YELLOW),
                Text.literal(nearest.getSize().getRadius() + "x" + nearest.getSize().getRadius()).formatted(Formatting.AQUA)),
            false);
        return 1;
    }

    // ── /sp list ──────────────────────────────────────────────────────────────
    private static int executeList(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        List<PlotData> plots = PlotManager.getOrCreate(player.getServerWorld()).getPlayerPlots(player.getUuid());
        if (plots.isEmpty()) {
            player.sendMessage(Text.translatable("sp.error.no_plots").formatted(Formatting.RED), false);
            return 0;
        }
        player.sendMessage(Text.translatable("sp.list.header", plots.size()).formatted(Formatting.YELLOW, Formatting.BOLD), false);
        for (int i = 0; i < plots.size(); i++) {
            PlotData p = plots.get(i);
            BlockPos c = p.getCenter();
            String tpFlag = p.hasFlag(PlotData.Flag.ALLOW_TP) ? " §b[TP]" : "";
            player.sendMessage(
                Text.literal("  " + (i + 1) + ". ").formatted(Formatting.GRAY)
                    .append(Text.literal(p.getPlotName()).formatted(Formatting.WHITE))
                    .append(Text.literal(" [" + p.getSize().getDisplayName() + "]").formatted(getTierFormatting(p.getSize().tier)))
                    .append(Text.literal("  " + c.getX() + ", " + c.getY() + ", " + c.getZ()).formatted(Formatting.DARK_GRAY))
                    .append(Text.literal(tpFlag)),
                false);
        }
        return 1;
    }

    // ── /sp info [plot] ───────────────────────────────────────────────────────
    private static int executeInfo(ServerCommandSource source, String plotArg) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());

        PlotData plot;
        if (plotArg == null) {
            plot = manager.getPlotAt(player.getBlockPos());
            if (plot == null) {
                player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false);
                return 0;
            }
        } else {
            List<PlotData> owned = manager.getPlayerPlots(player.getUuid());
            List<PlotData> resolved = resolvePlots(owned, plotArg, player);
            if (resolved == null || resolved.isEmpty()) return 0;
            plot = resolved.get(0);
        }

        BlockPos c = plot.getCenter();
        int sz = plot.getSize().getRadius();
        player.sendMessage(Text.translatable("sp.info.header", plot.getPlotName()).formatted(Formatting.YELLOW, Formatting.BOLD), false);
        player.sendMessage(Text.translatable("sp.info.owner", plot.getOwnerName()), false);
        player.sendMessage(Text.translatable("sp.info.tier", plot.getSize().getDisplayName()), false);
        player.sendMessage(Text.translatable("sp.info.size", sz + "x" + sz), false);
        player.sendMessage(Text.translatable("sp.info.coords", c.getX(), c.getY(), c.getZ()), false);

        // Ambient effects
        if (!plot.getParticleEffect().isBlank())
            player.sendMessage(Text.translatable("sp.info.particle", plot.getParticleEffect()), false);
        if (!plot.getWeatherType().isBlank())
            player.sendMessage(Text.translatable("sp.info.weather", plot.getWeatherType()), false);
        if (plot.getPlotTime() >= 0)
            player.sendMessage(Text.translatable("sp.info.time", plot.getPlotTime()), false);
        if (!plot.getMusicSound().isBlank())
            player.sendMessage(Text.translatable("sp.info.music", plot.getMusicSound()), false);

        // Active flags
        if (!plot.getFlags().isEmpty()) {
            StringBuilder flagStr = new StringBuilder();
            for (PlotData.Flag f : plot.getFlags()) {
                if (flagStr.length() > 0) flagStr.append(", ");
                flagStr.append(f.name().toLowerCase());
            }
            player.sendMessage(Text.translatable("sp.info.flags", flagStr.toString()), false);
        }

        // Groups
        if (!plot.getGroups().isEmpty()) {
            player.sendMessage(Text.translatable("sp.info.groups", plot.getGroups().size()), false);
            for (PlotData.PermissionGroup g : plot.getGroups()) {
                player.sendMessage(Text.literal("    \u2022 §d" + g.name + " §8(" + g.members.size() + " members, " + g.permissions.size() + " perms)"), false);
            }
        }

        // Members
        Map<UUID, PlotData.Role> members = plot.getMembers();
        if (members.isEmpty()) {
            player.sendMessage(Text.translatable("sp.info.no_members"), false);
        } else {
            player.sendMessage(Text.translatable("sp.info.members", members.size()), false);
            for (Map.Entry<UUID, PlotData.Role> entry : members.entrySet()) {
                player.sendMessage(
                    Text.literal("    \u2022 ").formatted(Formatting.DARK_GRAY)
                        .append(Text.literal(plot.getMemberName(entry.getKey())).formatted(Formatting.WHITE))
                        .append(Text.literal(" [" + entry.getValue().name().toLowerCase() + "]").formatted(Formatting.GRAY)),
                    false);
            }
        }
        return 1;
    }

    // ── /sp rename ────────────────────────────────────────────────────────────
    private static int executeRename(ServerCommandSource source, String newName) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) {
            player.sendMessage(Text.translatable("sp.rename.not_inside").formatted(Formatting.RED), false);
            return 0;
        }
        if (!plot.getOwnerId().equals(player.getUuid())) {
            player.sendMessage(Text.translatable("sp.error.not_owner").formatted(Formatting.RED), false);
            return 0;
        }
        newName = newName.trim();
        if (newName.isEmpty() || newName.length() > 32) {
            player.sendMessage(Text.translatable("sp.rename.invalid_name").formatted(Formatting.RED), false);
            return 0;
        }
        for (PlotData p : manager.getPlayerPlots(player.getUuid())) {
            if (p != plot && p.getPlotName().equalsIgnoreCase(newName)) {
                player.sendMessage(Text.translatable("sp.rename.duplicate").formatted(Formatting.RED), false);
                return 0;
            }
        }
        String old = plot.getPlotName();
        plot.setPlotName(newName);
        manager.markDirty();
        player.sendMessage(
            Text.literal("\u2714 ").formatted(Formatting.GREEN)
                .append(Text.literal("\"" + old + "\"").formatted(Formatting.GRAY))
                .append(Text.literal(" \u2192 ").formatted(Formatting.GREEN))
                .append(Text.literal("\"" + newName + "\"").formatted(Formatting.YELLOW)),
            false);
        return 1;
    }

    // ── /sp add ───────────────────────────────────────────────────────────────
    // Supports both online and offline players via UserCache.
    private static int executeAdd(ServerCommandSource source, String targetName, String plotArg) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        List<PlotData> owned = manager.getPlayerPlots(player.getUuid());
        if (owned.isEmpty()) {
            player.sendMessage(Text.translatable("sp.error.no_plots").formatted(Formatting.RED), false);
            return 0;
        }

        // Resolve UUID: try online first, then user-cache (offline)
        UUID targetUuid = null;
        String resolvedName = targetName;
        ServerPlayerEntity onlineTarget = source.getServer().getPlayerManager().getPlayer(targetName);
        if (onlineTarget != null) {
            targetUuid    = onlineTarget.getUuid();
            resolvedName  = onlineTarget.getName().getString();
        } else {
            Optional<GameProfile> profile = source.getServer().getUserCache().findByName(targetName);
            if (profile.isPresent()) {
                targetUuid   = profile.get().getId();
                resolvedName = profile.get().getName();
            }
        }

        if (targetUuid == null) {
            player.sendMessage(Text.translatable("sp.add.player_not_found", targetName).formatted(Formatting.RED), false);
            return 0;
        }
        if (targetUuid.equals(player.getUuid())) {
            player.sendMessage(Text.translatable("sp.add.self").formatted(Formatting.RED), false);
            return 0;
        }

        List<PlotData> targets = resolvePlots(owned, plotArg, player);
        if (targets == null) return 0;

        int added = 0;
        final UUID fUuid = targetUuid;
        final String fName = resolvedName;
        for (PlotData plot : targets) {
            if (plot.getRoleOf(fUuid) == PlotData.Role.VISITOR) {
                plot.addMember(fUuid, fName, PlotData.Role.MEMBER);
                added++;
            }
        }
        if (added == 0) {
            player.sendMessage(Text.translatable("sp.add.already_member", resolvedName).formatted(Formatting.YELLOW), false);
            return 0;
        }
        manager.markDirty();
        String desc = targets.size() == 1 ? "\"" + targets.get(0).getPlotName() + "\"" : targets.size() + " plots";
        player.sendMessage(Text.translatable("sp.add.success", resolvedName, desc).formatted(Formatting.GREEN), false);
        if (onlineTarget != null)
            onlineTarget.sendMessage(Text.translatable("sp.add.notified", desc, player.getName().getString()).formatted(Formatting.GREEN), false);
        return 1;
    }

    // ── /sp remove ────────────────────────────────────────────────────────────
    private static int executeRemove(ServerCommandSource source, String targetName, String plotArg) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        List<PlotData> owned = manager.getPlayerPlots(player.getUuid());
        if (owned.isEmpty()) {
            player.sendMessage(Text.translatable("sp.error.no_plots").formatted(Formatting.RED), false);
            return 0;
        }
        List<PlotData> targets = resolvePlots(owned, plotArg, player);
        if (targets == null) return 0;

        UUID targetUuid = null;
        for (PlotData plot : targets) {
            for (UUID uuid : plot.getMembers().keySet()) {
                if (plot.getMemberName(uuid).equalsIgnoreCase(targetName)) { targetUuid = uuid; break; }
            }
            if (targetUuid != null) break;
        }
        if (targetUuid == null) {
            player.sendMessage(Text.translatable("sp.remove.not_member", targetName).formatted(Formatting.RED), false);
            return 0;
        }
        int removed = 0;
        for (PlotData plot : targets) {
            if (plot.getMembers().containsKey(targetUuid)) { plot.removeMember(targetUuid); removed++; }
        }
        manager.markDirty();
        String desc = targets.size() == 1 ? "\"" + targets.get(0).getPlotName() + "\"" : targets.size() + " plots";
        player.sendMessage(Text.translatable("sp.remove.success", targetName, desc).formatted(Formatting.GREEN), false);
        ServerPlayerEntity online = source.getServer().getPlayerManager().getPlayer(targetUuid);
        if (online != null)
            online.sendMessage(Text.translatable("sp.remove.notified", desc, player.getName().getString()).formatted(Formatting.RED), false);
        return 1;
    }

    // ── /sp tp [plot] ─────────────────────────────────────────────────────────
    private static int executeTp(ServerCommandSource source, String plotArg) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());

        PlotData plot = null;
        if (plotArg == null) {
            List<PlotData> owned = manager.getPlayerPlots(player.getUuid());
            if (!owned.isEmpty()) plot = owned.get(0);
        } else {
            List<PlotData> owned = manager.getPlayerPlots(player.getUuid());
            List<PlotData> resolved = resolvePlots(owned, plotArg, player);
            if (resolved != null && !resolved.isEmpty()) {
                plot = resolved.get(0);
            } else {
                for (PlotData p : manager.getAllPlots()) {
                    if (p.getPlotName().equalsIgnoreCase(plotArg) && p.hasFlag(PlotData.Flag.ALLOW_TP)) {
                        plot = p; break;
                    }
                }
            }
        }

        if (plot == null) {
            player.sendMessage(Text.translatable("sp.tp.not_found").formatted(Formatting.RED), false);
            return 0;
        }

        boolean isOwner   = plot.getOwnerId().equals(player.getUuid());
        boolean isMember  = plot.getRoleOf(player.getUuid()) != PlotData.Role.VISITOR;
        boolean tpPublic  = plot.hasFlag(PlotData.Flag.ALLOW_TP);
        boolean isAdmin   = player.getCommandTags().contains(adminTag());

        if (!isOwner && !isMember && !tpPublic && !isAdmin) {
            player.sendMessage(Text.translatable("sp.tp.not_allowed", plot.getPlotName()).formatted(Formatting.RED), false);
            return 0;
        }

        if (!(player.getWorld() instanceof ServerWorld sw)) return 0;
        BlockPos c = plot.getCenter();
        double tpY = sw.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(c.getX(), c.getY(), c.getZ())).getY();
        player.teleport(sw, c.getX() + 0.5, tpY, c.getZ() + 0.5, Set.of(), player.getYaw(), player.getPitch());
        player.sendMessage(Text.translatable("sp.tp.success", plot.getPlotName()).formatted(Formatting.GREEN), false);
        sw.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
        return 1;
    }

    // ── /sp flag ──────────────────────────────────────────────────────────────
    private static int executeFlagList(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        player.sendMessage(Text.translatable("sp.flag.list_header"), false);
        for (PlotData.Flag f : PlotData.Flag.values())
            player.sendMessage(Text.literal("  §7" + f.name().toLowerCase()), false);
        player.sendMessage(Text.translatable("sp.flag.usage"), false);
        return 1;
    }

    private static int executeFlagInfo(ServerCommandSource source, String flagName) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotData.Flag flag = parseFlag(player, flagName);
        if (flag == null) return 0;
        PlotData plot = PlotManager.getOrCreate(player.getServerWorld()).getPlotAt(player.getBlockPos());
        if (plot == null) {
            player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false);
            return 0;
        }
        boolean on = plot.hasFlag(flag);
        player.sendMessage(Text.translatable("sp.flag.info", flag.name().toLowerCase(), plot.getPlotName(), on ? "§a[ON]" : "§c[OFF]"), false);
        return 1;
    }

    private static int executeFlagSet(ServerCommandSource source, String flagName, boolean value, String plotArg) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotData.Flag flag = parseFlag(player, flagName);
        if (flag == null) return 0;

        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = resolveSinglePlot(player, manager, plotArg);
        if (plot == null) return 0;

        if (!plot.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_FLAGS) && !player.getCommandTags().contains(adminTag())) {
            player.sendMessage(Text.translatable("sp.error.no_flag_perm").formatted(Formatting.RED), false);
            return 0;
        }
        plot.setFlag(flag, value);
        manager.markDirty();
        player.sendMessage(Text.translatable("sp.flag.set", flag.name().toLowerCase(), value ? "enabled" : "disabled", plot.getPlotName()).formatted(Formatting.GREEN), false);
        return 1;
    }

    // ── /sp perm ──────────────────────────────────────────────────────────────
    private static int executePermList(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        player.sendMessage(Text.translatable("sp.perm.list_header"), false);
        for (PlotData.Permission p : PlotData.Permission.values())
            player.sendMessage(Text.literal("  §7" + p.name().toLowerCase()), false);
        player.sendMessage(Text.translatable("sp.perm.usage"), false);
        return 1;
    }

    private static int executePermShow(ServerCommandSource source, String targetName, String permName) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotData.Permission perm = parsePerm(player, permName);
        if (perm == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) {
            player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false);
            return 0;
        }
        UUID targetUuid = findMemberUuid(plot, targetName);
        if (targetUuid == null) {
            player.sendMessage(Text.translatable("sp.perm.not_member", targetName).formatted(Formatting.RED), false);
            return 0;
        }
        boolean has = plot.hasPermission(targetUuid, perm);
        player.sendMessage(Text.translatable("sp.perm.show", targetName, perm.name().toLowerCase(), has ? "§a\u2714" : "§c\u2717"), false);
        return 1;
    }

    private static int executePermSet(ServerCommandSource source, String targetName, String permName, boolean value, String plotArg) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotData.Permission perm = parsePerm(player, permName);
        if (perm == null) return 0;

        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = resolveSinglePlot(player, manager, plotArg);
        if (plot == null) return 0;

        if (!plot.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_PERMS) && !player.getCommandTags().contains(adminTag())) {
            player.sendMessage(Text.translatable("sp.error.no_perm_perm").formatted(Formatting.RED), false);
            return 0;
        }
        UUID targetUuid = findMemberUuid(plot, targetName);
        if (targetUuid == null) {
            player.sendMessage(Text.translatable("sp.perm.not_member_in", targetName, plot.getPlotName()).formatted(Formatting.RED), false);
            return 0;
        }
        plot.setPermission(targetUuid, perm, value);
        manager.markDirty();
        player.sendMessage(Text.translatable("sp.perm.set", perm.name().toLowerCase(), value ? "enabled" : "disabled", targetName).formatted(Formatting.GREEN), false);
        return 1;
    }

    // ── /sp fly ───────────────────────────────────────────────────────────────
    private static int executeFlyToggle(ServerCommandSource source, Boolean value) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) {
            player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false);
            return 0;
        }
        if (!plot.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_FLAGS) && !player.getCommandTags().contains(adminTag())) {
            player.sendMessage(Text.translatable("sp.error.no_fly_perm").formatted(Formatting.RED), false);
            return 0;
        }
        boolean newValue = value != null ? value : !plot.hasFlag(PlotData.Flag.ALLOW_FLY);
        return applyFlyChange(player, manager, plot, newValue);
    }

    private static int executeFlySet(ServerCommandSource source, boolean value, String plotArg) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = resolveSinglePlot(player, manager, plotArg);
        if (plot == null) return 0;
        if (!plot.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_FLAGS) && !player.getCommandTags().contains(adminTag())) {
            player.sendMessage(Text.translatable("sp.error.no_fly_perm").formatted(Formatting.RED), false);
            return 0;
        }
        return applyFlyChange(player, manager, plot, value);
    }

    private static int applyFlyChange(ServerPlayerEntity player, PlotManager manager, PlotData plot, boolean enable) {
        plot.setFlag(PlotData.Flag.ALLOW_FLY, enable);
        for (UUID uuid : plot.getMembers().keySet())
            plot.setPermission(uuid, PlotData.Permission.FLY, enable);
        manager.markDirty();
        player.sendMessage(Text.translatable("sp.fly.set", enable ? "enabled" : "disabled", plot.getPlotName()).formatted(Formatting.GREEN), false);
        return 1;
    }

    // ── /sp group ─────────────────────────────────────────────────────────────
    private static int executeGroupList(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) {
            player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false);
            return 0;
        }
        if (plot.getGroups().isEmpty()) {
            player.sendMessage(Text.translatable("sp.group.empty"), false);
        } else {
            player.sendMessage(Text.translatable("sp.group.list_header", plot.getPlotName()), false);
            for (PlotData.PermissionGroup g : plot.getGroups())
                player.sendMessage(Text.literal("  §d" + g.name + " §8\u2014 " + g.members.size() + " members, " + g.permissions.size() + " perms"), false);
        }
        player.sendMessage(Text.translatable("sp.group.usage"), false);
        return 1;
    }

    private static int executeGroupCreate(ServerCommandSource source, String groupName) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) {
            player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false);
            return 0;
        }
        if (!plot.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS) && !player.getCommandTags().contains(adminTag())) {
            player.sendMessage(Text.translatable("sp.error.no_group_perm").formatted(Formatting.RED), false);
            return 0;
        }
        if (plot.getGroup(groupName) != null) {
            player.sendMessage(Text.translatable("sp.group.already_exists").formatted(Formatting.YELLOW), false);
            return 0;
        }
        plot.getOrCreateGroup(groupName);
        manager.markDirty();
        player.sendMessage(Text.translatable("sp.group.created", groupName).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeGroupDelete(ServerCommandSource source, String groupName) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) {
            player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false);
            return 0;
        }
        if (!plot.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS) && !player.getCommandTags().contains(adminTag())) {
            player.sendMessage(Text.translatable("sp.error.no_group_perm").formatted(Formatting.RED), false);
            return 0;
        }
        if (!plot.removeGroup(groupName)) {
            player.sendMessage(Text.translatable("sp.group.not_found", groupName).formatted(Formatting.RED), false);
            return 0;
        }
        manager.markDirty();
        player.sendMessage(Text.translatable("sp.group.deleted", groupName).formatted(Formatting.GREEN), false);
        return 1;
    }

    /**
     * /sp group addmember <group> <player>
     * Supports offline players that are already plot members (their name is stored in memberNames).
     */
    private static int executeGroupAddMember(ServerCommandSource source, String groupName, String targetName) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) {
            player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false);
            return 0;
        }
        PlotData.PermissionGroup group = plot.getGroup(groupName);
        if (group == null) {
            player.sendMessage(Text.translatable("sp.group.not_found", groupName).formatted(Formatting.RED), false);
            return 0;
        }

        // findMemberUuid already searches the stored member names (works for offline players)
        UUID targetUuid = findMemberUuid(plot, targetName);
        if (targetUuid == null) {
            player.sendMessage(Text.translatable("sp.group.member_not_found", targetName).formatted(Formatting.RED), false);
            return 0;
        }
        if (group.members.contains(targetUuid)) {
            player.sendMessage(Text.translatable("sp.group.already_in_group", targetName).formatted(Formatting.YELLOW), false);
            return 0;
        }
        group.members.add(targetUuid);
        manager.markDirty();
        player.sendMessage(Text.translatable("sp.group.member_added", targetName, groupName).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeGroupRemoveMember(ServerCommandSource source, String groupName, String targetName) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) {
            player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false);
            return 0;
        }
        PlotData.PermissionGroup group = plot.getGroup(groupName);
        if (group == null) {
            player.sendMessage(Text.translatable("sp.group.not_found", groupName).formatted(Formatting.RED), false);
            return 0;
        }
        UUID targetUuid = findMemberUuid(plot, targetName);
        if (targetUuid == null || !group.members.remove(targetUuid)) {
            player.sendMessage(Text.translatable("sp.group.not_in_group", targetName).formatted(Formatting.YELLOW), false);
            return 0;
        }
        manager.markDirty();
        player.sendMessage(Text.translatable("sp.group.member_removed", targetName, groupName).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeGroupSetPerm(ServerCommandSource source, String groupName, String permName, boolean value) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotData.Permission perm = parsePerm(player, permName);
        if (perm == null) return 0;

        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) {
            player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false);
            return 0;
        }
        if (!plot.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS) && !player.getCommandTags().contains(adminTag())) {
            player.sendMessage(Text.translatable("sp.error.no_group_perm").formatted(Formatting.RED), false);
            return 0;
        }
        PlotData.PermissionGroup group = plot.getGroup(groupName);
        if (group == null) {
            player.sendMessage(Text.translatable("sp.group.not_found", groupName).formatted(Formatting.RED), false);
            return 0;
        }
        if (value) group.permissions.add(perm); else group.permissions.remove(perm);
        manager.markDirty();
        player.sendMessage(Text.translatable("sp.group.perm_set", perm.name().toLowerCase(), value ? "enabled" : "disabled", groupName).formatted(Formatting.GREEN), false);
        return 1;
    }

    // ── /sp plot particle|weather|time|music ─────────────────────────────────

    private static int executeSetParticle(ServerCommandSource source, String type) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotData plot = getOwnedPlotAt(player);
        if (plot == null) return 0;

        String value;
        if (type.equalsIgnoreCase("clear") || type.equalsIgnoreCase("none")) {
            value = "";
        } else {
            // Accept both "happy_villager" and "minecraft:happy_villager"
            value = type.contains(":") ? type : "minecraft:" + type;
        }
        plot.setParticleEffect(value);
        PlotManager.getOrCreate(player.getServerWorld()).markDirty();
        if (value.isEmpty())
            player.sendMessage(Text.translatable("sp.plot.particle_cleared").formatted(Formatting.GREEN), false);
        else
            player.sendMessage(Text.translatable("sp.plot.particle_set", value).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeSetWeather(ServerCommandSource source, String type) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotData plot = getOwnedPlotAt(player);
        if (plot == null) return 0;

        String value = type.equalsIgnoreCase("clear") || type.equalsIgnoreCase("none")
            ? "" : type.toUpperCase();
        if (!value.isEmpty() && !value.equals("CLEAR") && !value.equals("RAIN") && !value.equals("THUNDER")) {
            player.sendMessage(Text.translatable("sp.plot.weather_invalid").formatted(Formatting.RED), false);
            return 0;
        }
        plot.setWeatherType(value);
        PlotManager.getOrCreate(player.getServerWorld()).markDirty();
        if (value.isEmpty())
            player.sendMessage(Text.translatable("sp.plot.weather_cleared").formatted(Formatting.GREEN), false);
        else
            player.sendMessage(Text.translatable("sp.plot.weather_set", value.toLowerCase()).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeSetTime(ServerCommandSource source, String valueStr) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotData plot = getOwnedPlotAt(player);
        if (plot == null) return 0;

        long ticks;
        switch (valueStr.toLowerCase()) {
            case "day"      -> ticks = 1000;
            case "noon"     -> ticks = 6000;
            case "sunset"   -> ticks = 12000;
            case "night"    -> ticks = 13000;
            case "midnight" -> ticks = 18000;
            case "sunrise"  -> ticks = 23000;
            case "reset", "clear", "none" -> ticks = -1;
            default -> {
                try { ticks = Long.parseLong(valueStr); }
                catch (NumberFormatException e) {
                    player.sendMessage(Text.translatable("sp.plot.time_invalid").formatted(Formatting.RED), false);
                    return 0;
                }
            }
        }

        plot.setPlotTime(ticks);
        PlotManager.getOrCreate(player.getServerWorld()).markDirty();
        if (ticks < 0)
            player.sendMessage(Text.translatable("sp.plot.time_cleared").formatted(Formatting.GREEN), false);
        else
            player.sendMessage(Text.translatable("sp.plot.time_set", ticks).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeSetMusic(ServerCommandSource source, String soundId) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotData plot = getOwnedPlotAt(player);
        if (plot == null) return 0;

        String value = soundId.equalsIgnoreCase("clear") || soundId.equalsIgnoreCase("none") ? "" : soundId;
        plot.setMusicSound(value);
        PlotManager.getOrCreate(player.getServerWorld()).markDirty();
        if (value.isEmpty())
            player.sendMessage(Text.translatable("sp.plot.music_cleared").formatted(Formatting.GREEN), false);
        else
            player.sendMessage(Text.translatable("sp.plot.music_set", value).formatted(Formatting.GREEN), false);
        return 1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns the plot at the player's position if they own/manage it, or sends an error. */
    private static PlotData getOwnedPlotAt(ServerPlayerEntity player) {
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) {
            player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false);
            return null;
        }
        if (!plot.getOwnerId().equals(player.getUuid()) && !player.getCommandTags().contains(adminTag())) {
            player.sendMessage(Text.translatable("sp.error.not_owner").formatted(Formatting.RED), false);
            return null;
        }
        return plot;
    }

    private static PlotData resolveSinglePlot(ServerPlayerEntity player, PlotManager manager, String plotArg) {
        if (plotArg == null) {
            PlotData plot = manager.getPlotAt(player.getBlockPos());
            if (plot == null) {
                player.sendMessage(Text.translatable("sp.error.not_in_plot_arg").formatted(Formatting.RED), false);
                return null;
            }
            if (!plot.getOwnerId().equals(player.getUuid())
                    && plot.getRoleOf(player.getUuid()) != PlotData.Role.ADMIN
                    && !player.getCommandTags().contains(adminTag())) {
                player.sendMessage(Text.translatable("sp.error.not_owner_or_admin").formatted(Formatting.RED), false);
                return null;
            }
            return plot;
        }
        List<PlotData> owned = manager.getPlayerPlots(player.getUuid());
        List<PlotData> resolved = resolvePlots(owned, plotArg, player);
        if (resolved == null || resolved.isEmpty()) return null;
        return resolved.get(0);
    }

    private static List<PlotData> resolvePlots(List<PlotData> owned, String plotArg, ServerPlayerEntity player) {
        if (plotArg.equalsIgnoreCase("all")) return new ArrayList<>(owned);
        try {
            int index = Integer.parseInt(plotArg);
            if (index < 1 || index > owned.size()) {
                player.sendMessage(Text.translatable("sp.error.invalid_index", owned.size()).formatted(Formatting.RED), false);
                return null;
            }
            return List.of(owned.get(index - 1));
        } catch (NumberFormatException ignored) {}
        for (PlotData p : owned) {
            if (p.getPlotName().equalsIgnoreCase(plotArg)) return List.of(p);
        }
        player.sendMessage(
            Text.translatable("sp.error.plot_not_found", plotArg).formatted(Formatting.RED)
                .append(Text.literal(" "))
                .append(Text.literal("/sp list").formatted(Formatting.YELLOW)),
            false);
        return null;
    }

    private static PlotData getNearestOwnedPlot(ServerPlayerEntity player, PlotManager manager) {
        List<PlotData> owned = manager.getPlayerPlots(player.getUuid());
        if (owned.isEmpty()) return null;
        BlockPos pp = player.getBlockPos();
        return owned.stream()
                .min((a, b) -> Double.compare(pp.getSquaredDistance(a.getCenter()), pp.getSquaredDistance(b.getCenter())))
                .orElse(null);
    }

    private static UUID findMemberUuid(PlotData plot, String name) {
        for (UUID uuid : plot.getMembers().keySet()) {
            if (plot.getMemberName(uuid).equalsIgnoreCase(name)) return uuid;
        }
        return null;
    }

    private static PlotData.Flag parseFlag(ServerPlayerEntity player, String name) {
        try { return PlotData.Flag.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) {
            player.sendMessage(Text.translatable("sp.error.unknown_flag", name).formatted(Formatting.RED), false);
            return null;
        }
    }

    private static PlotData.Permission parsePerm(ServerPlayerEntity player, String name) {
        try { return PlotData.Permission.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) {
            player.sendMessage(Text.translatable("sp.error.unknown_perm", name).formatted(Formatting.RED), false);
            return null;
        }
    }

    private static String adminTag() {
        return SecurePlotsConfig.INSTANCE != null ? SecurePlotsConfig.INSTANCE.adminTag : "plot_admin";
    }

    private static Formatting getTierFormatting(int tier) {
        return switch (tier) {
            case 0 -> Formatting.GOLD;
            case 1 -> Formatting.YELLOW;
            case 2 -> Formatting.GREEN;
            case 3 -> Formatting.AQUA;
            case 4 -> Formatting.DARK_PURPLE;
            default -> Formatting.WHITE;
        };
    }
}

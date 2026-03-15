/*
 * SecurePlots - A Fabric mod for Minecraft 1.21.1
 * Copyright (C) 2025 TupacGodoy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.zhilius.secureplots.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.zhilius.secureplots.config.SecurePlotsConfig;
import com.zhilius.secureplots.config.SecurePlotsConfig.ResolvedPerks;
import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotManager;
import com.zhilius.secureplots.plot.PlotSize;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.zhilius.secureplots.block.ModBlocks;
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
import java.util.concurrent.CompletableFuture;

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
                        .executes(ctx -> executeRename(ctx.getSource(),
                            StringArgumentType.getString(ctx, "name")))))

                // /sp add <player> <plot|all>
                .then(CommandManager.literal("add")
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .then(CommandManager.argument("plot", StringArgumentType.greedyString())
                            .executes(ctx -> executeAdd(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                StringArgumentType.getString(ctx, "plot"))))))

                // /sp remove <player> <plot|all>
                .then(CommandManager.literal("remove")
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .then(CommandManager.argument("plot", StringArgumentType.greedyString())
                            .executes(ctx -> executeRemove(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                StringArgumentType.getString(ctx, "plot"))))))

                // /sp role <player> <member|admin>
                .then(CommandManager.literal("role")
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .then(CommandManager.argument("role", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                builder.suggest("member"); builder.suggest("admin");
                                return builder.buildFuture();
                            })
                            .executes(ctx -> executeSetRole(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                StringArgumentType.getString(ctx, "role"))))))

                // /sp tp [plot]
                .then(CommandManager.literal("tp")
                    .executes(ctx -> executeTp(ctx.getSource(), null))
                    .then(CommandManager.argument("plot", StringArgumentType.greedyString())
                        .executes(ctx -> executeTp(ctx.getSource(),
                            StringArgumentType.getString(ctx, "plot")))))

                // /sp upgrade
                .then(CommandManager.literal("upgrade")
                    .executes(ctx -> executeUpgrade(ctx.getSource())))

                // /sp myrank  — shows the player their current rank perks
                .then(CommandManager.literal("myrank")
                    .executes(ctx -> executeMyRank(ctx.getSource())))

                // /sp flag [flag] [value] [plot]
                .then(CommandManager.literal("flag")
                    .executes(ctx -> executeFlagList(ctx.getSource()))
                    .then(CommandManager.argument("flag", StringArgumentType.word())
                        .suggests((ctx, builder) -> {
                            for (PlotData.Flag f : PlotData.Flag.values())
                                builder.suggest(f.name().toLowerCase());
                            return builder.buildFuture();
                        })
                        .executes(ctx -> executeFlagInfo(ctx.getSource(),
                            StringArgumentType.getString(ctx, "flag")))
                        .then(CommandManager.argument("value", BoolArgumentType.bool())
                            .executes(ctx -> executeFlagSet(ctx.getSource(),
                                StringArgumentType.getString(ctx, "flag"),
                                BoolArgumentType.getBool(ctx, "value"), null))
                            .then(CommandManager.argument("plot", StringArgumentType.greedyString())
                                .executes(ctx -> executeFlagSet(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "flag"),
                                    BoolArgumentType.getBool(ctx, "value"),
                                    StringArgumentType.getString(ctx, "plot")))))))

                // /sp perm [player] [perm] [value] [plot]
                .then(CommandManager.literal("perm")
                    .executes(ctx -> executePermList(ctx.getSource()))
                    .then(CommandManager.argument("player", StringArgumentType.word())
                        .then(CommandManager.argument("perm", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                for (PlotData.Permission p : PlotData.Permission.values())
                                    builder.suggest(p.name().toLowerCase());
                                return builder.buildFuture();
                            })
                            .executes(ctx -> executePermShow(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"),
                                StringArgumentType.getString(ctx, "perm")))
                            .then(CommandManager.argument("value", BoolArgumentType.bool())
                                .executes(ctx -> executePermSet(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "player"),
                                    StringArgumentType.getString(ctx, "perm"),
                                    BoolArgumentType.getBool(ctx, "value"), null))
                                .then(CommandManager.argument("plot", StringArgumentType.greedyString())
                                    .executes(ctx -> executePermSet(ctx.getSource(),
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
                            .executes(ctx -> executeFlySet(ctx.getSource(),
                                BoolArgumentType.getBool(ctx, "value"),
                                StringArgumentType.getString(ctx, "plot"))))))

                // /sp group ...
                .then(CommandManager.literal("group")
                    .executes(ctx -> executeGroupList(ctx.getSource()))
                    .then(CommandManager.literal("create")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                            .executes(ctx -> executeGroupCreate(ctx.getSource(),
                                StringArgumentType.getString(ctx, "name")))))
                    .then(CommandManager.literal("delete")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                            .executes(ctx -> executeGroupDelete(ctx.getSource(),
                                StringArgumentType.getString(ctx, "name")))))
                    .then(CommandManager.literal("addmember")
                        .then(CommandManager.argument("group", StringArgumentType.word())
                            .then(CommandManager.argument("player", StringArgumentType.word())
                                .executes(ctx -> executeGroupAddMember(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "group"),
                                    StringArgumentType.getString(ctx, "player"))))))
                    .then(CommandManager.literal("removemember")
                        .then(CommandManager.argument("group", StringArgumentType.word())
                            .then(CommandManager.argument("player", StringArgumentType.word())
                                .executes(ctx -> executeGroupRemoveMember(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "group"),
                                    StringArgumentType.getString(ctx, "player"))))))
                    .then(CommandManager.literal("setperm")
                        .then(CommandManager.argument("group", StringArgumentType.word())
                            .then(CommandManager.argument("perm", StringArgumentType.word())
                                .then(CommandManager.argument("value", BoolArgumentType.bool())
                                    .executes(ctx -> executeGroupSetPerm(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "group"),
                                        StringArgumentType.getString(ctx, "perm"),
                                        BoolArgumentType.getBool(ctx, "value"))))))))

                // /sp plot particle|weather|time|music|enter|exit
                .then(CommandManager.literal("plot")
                    .then(CommandManager.literal("particle")
                        .executes(ctx -> executeParticleHelp(ctx.getSource()))
                        .then(CommandManager.argument("type", StringArgumentType.word())
                            .suggests((ctx, builder) -> suggestParticles(builder))
                            .executes(ctx -> executeSetParticle(ctx.getSource(),
                                StringArgumentType.getString(ctx, "type")))))
                    .then(CommandManager.literal("weather")
                        .then(CommandManager.argument("type", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                for (String w : new String[]{"clear","rain","thunder","none"})
                                    builder.suggest(w);
                                return builder.buildFuture();
                            })
                            .executes(ctx -> executeSetWeather(ctx.getSource(),
                                StringArgumentType.getString(ctx, "type")))))
                    .then(CommandManager.literal("time")
                        .then(CommandManager.argument("value", StringArgumentType.word())
                            .suggests((ctx, builder) -> {
                                for (String t : new String[]{"day","noon","sunset","night","midnight","sunrise","reset"})
                                    builder.suggest(t);
                                return builder.buildFuture();
                            })
                            .executes(ctx -> executeSetTime(ctx.getSource(),
                                StringArgumentType.getString(ctx, "value")))))
                    .then(CommandManager.literal("music")
                        .executes(ctx -> executeMusicHelp(ctx.getSource()))
                        .then(CommandManager.argument("sound", StringArgumentType.greedyString())
                            .suggests((ctx, builder) -> suggestMusic(builder))
                            .executes(ctx -> executeSetMusic(ctx.getSource(),
                                StringArgumentType.getString(ctx, "sound")))))
                    // NEW: /sp plot enter <message|clear>
                    .then(CommandManager.literal("enter")
                        .then(CommandManager.argument("message", StringArgumentType.greedyString())
                            .executes(ctx -> executeSetMessage(ctx.getSource(),
                                StringArgumentType.getString(ctx, "message"), true))))
                    // NEW: /sp plot exit <message|clear>
                    .then(CommandManager.literal("exit")
                        .then(CommandManager.argument("message", StringArgumentType.greedyString())
                            .executes(ctx -> executeSetMessage(ctx.getSource(),
                                StringArgumentType.getString(ctx, "message"), false)))))

                // /sp admin ...  (requires adminOpLevel)
                .then(CommandManager.literal("admin")
                    .requires(src -> src.hasPermissionLevel(
                        SecurePlotsConfig.INSTANCE != null ? SecurePlotsConfig.INSTANCE.adminOpLevel : 2))
                    // /sp admin listall [page]  — all plots on the server
                    .then(CommandManager.literal("listall")
                        .executes(ctx -> executeAdminListAll(ctx.getSource(), 1, null))
                        .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                            .executes(ctx -> executeAdminListAll(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "page"), null))))
                    // /sp admin search <player> [page]  — all plots of a player
                    .then(CommandManager.literal("search")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .executes(ctx -> executeAdminSearch(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player"), 1))
                            .then(CommandManager.argument("page", IntegerArgumentType.integer(1))
                                .executes(ctx -> executeAdminSearch(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "player"),
                                    IntegerArgumentType.getInteger(ctx, "page"))))))
                    // /sp admin nearby [count]  — plots nearest to the admin (or given coords)
                    .then(CommandManager.literal("nearby")
                        .executes(ctx -> executeAdminNearby(ctx.getSource(), null, null, null, 10))
                        .then(CommandManager.argument("count", IntegerArgumentType.integer(1, 50))
                            .executes(ctx -> executeAdminNearby(ctx.getSource(), null, null, null,
                                IntegerArgumentType.getInteger(ctx, "count")))
                            .then(CommandManager.argument("x", IntegerArgumentType.integer())
                                .then(CommandManager.argument("y", IntegerArgumentType.integer())
                                    .then(CommandManager.argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> executeAdminNearby(ctx.getSource(),
                                            IntegerArgumentType.getInteger(ctx, "x"),
                                            IntegerArgumentType.getInteger(ctx, "y"),
                                            IntegerArgumentType.getInteger(ctx, "z"),
                                            IntegerArgumentType.getInteger(ctx, "count"))))))))
                    .then(CommandManager.literal("list")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .executes(ctx -> executeAdminList(ctx.getSource(),
                                StringArgumentType.getString(ctx, "player")))))
                    .then(CommandManager.literal("info")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .then(CommandManager.argument("plot", StringArgumentType.greedyString())
                                .executes(ctx -> executeAdminInfo(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "player"),
                                    StringArgumentType.getString(ctx, "plot"))))))
                    .then(CommandManager.literal("tp")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .then(CommandManager.argument("plot", StringArgumentType.greedyString())
                                .executes(ctx -> executeAdminTp(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "player"),
                                    StringArgumentType.getString(ctx, "plot"))))))
                    .then(CommandManager.literal("delete")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .then(CommandManager.argument("plot", StringArgumentType.greedyString())
                                .executes(ctx -> executeAdminDelete(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "player"),
                                    StringArgumentType.getString(ctx, "plot"))))))
                    .then(CommandManager.literal("rename")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .then(CommandManager.argument("plot", StringArgumentType.word())
                                .then(CommandManager.argument("name", StringArgumentType.greedyString())
                                    .executes(ctx -> executeAdminRename(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player"),
                                        StringArgumentType.getString(ctx, "plot"),
                                        StringArgumentType.getString(ctx, "name")))))))
                    .then(CommandManager.literal("setowner")
                        .then(CommandManager.argument("newowner", StringArgumentType.word())
                            .executes(ctx -> executeAdminSetOwner(ctx.getSource(),
                                StringArgumentType.getString(ctx, "newowner")))))
                    .then(CommandManager.literal("upgrade")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .then(CommandManager.argument("plot", StringArgumentType.greedyString())
                                .executes(ctx -> executeAdminUpgrade(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "player"),
                                    StringArgumentType.getString(ctx, "plot"))))))
                    .then(CommandManager.literal("rank")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .then(CommandManager.argument("plot", StringArgumentType.word())
                                .then(CommandManager.argument("value", BoolArgumentType.bool())
                                    .executes(ctx -> executeAdminRank(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player"),
                                        StringArgumentType.getString(ctx, "plot"),
                                        BoolArgumentType.getBool(ctx, "value")))))))
                    .then(CommandManager.literal("particle")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .then(CommandManager.argument("plot", StringArgumentType.word())
                                .then(CommandManager.argument("type", StringArgumentType.greedyString())
                                    .suggests((ctx, b) -> suggestParticles(b))
                                    .executes(ctx -> executeAdminAmbient(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player"),
                                        StringArgumentType.getString(ctx, "plot"),
                                        "particle",
                                        StringArgumentType.getString(ctx, "type")))))))
                    .then(CommandManager.literal("music")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .then(CommandManager.argument("plot", StringArgumentType.word())
                                .then(CommandManager.argument("sound", StringArgumentType.greedyString())
                                    .suggests((ctx, b) -> suggestMusic(b))
                                    .executes(ctx -> executeAdminAmbient(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player"),
                                        StringArgumentType.getString(ctx, "plot"),
                                        "music",
                                        StringArgumentType.getString(ctx, "sound")))))))
                    .then(CommandManager.literal("weather")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .then(CommandManager.argument("plot", StringArgumentType.word())
                                .then(CommandManager.argument("type", StringArgumentType.word())
                                    .suggests((ctx, b) -> {
                                        for (String w : new String[]{"clear","rain","thunder","none"}) b.suggest(w);
                                        return b.buildFuture();
                                    })
                                    .executes(ctx -> executeAdminAmbient(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player"),
                                        StringArgumentType.getString(ctx, "plot"),
                                        "weather",
                                        StringArgumentType.getString(ctx, "type")))))))
                    .then(CommandManager.literal("time")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .then(CommandManager.argument("plot", StringArgumentType.word())
                                .then(CommandManager.argument("value", StringArgumentType.word())
                                    .suggests((ctx, b) -> {
                                        for (String t : new String[]{"day","noon","night","midnight","reset"}) b.suggest(t);
                                        return b.buildFuture();
                                    })
                                    .executes(ctx -> executeAdminAmbient(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player"),
                                        StringArgumentType.getString(ctx, "plot"),
                                        "time",
                                        StringArgumentType.getString(ctx, "value")))))))
                    .then(CommandManager.literal("enter")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .then(CommandManager.argument("plot", StringArgumentType.word())
                                .then(CommandManager.argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> executeAdminAmbient(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player"),
                                        StringArgumentType.getString(ctx, "plot"),
                                        "enter",
                                        StringArgumentType.getString(ctx, "message")))))))
                    .then(CommandManager.literal("exit")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .then(CommandManager.argument("plot", StringArgumentType.word())
                                .then(CommandManager.argument("message", StringArgumentType.greedyString())
                                    .executes(ctx -> executeAdminAmbient(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "player"),
                                        StringArgumentType.getString(ctx, "plot"),
                                        "exit",
                                        StringArgumentType.getString(ctx, "message")))))))
                    .then(CommandManager.literal("setrank")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .then(CommandManager.argument("tag", StringArgumentType.word())
                                .executes(ctx -> executeAdminSetRank(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "player"),
                                    StringArgumentType.getString(ctx, "tag"), true)))))
                    .then(CommandManager.literal("removerank")
                        .then(CommandManager.argument("player", StringArgumentType.word())
                            .then(CommandManager.argument("tag", StringArgumentType.word())
                                .executes(ctx -> executeAdminSetRank(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "player"),
                                    StringArgumentType.getString(ctx, "tag"), false)))))
                    .then(CommandManager.literal("reload")
                        .executes(ctx -> executeAdminReload(ctx.getSource()))))
            );
        }
    }

    // ── Suggestion providers ──────────────────────────────────────────────────

    private static final String[] COMMON_PARTICLES = {
        "clear",
        "minecraft:happy_villager","minecraft:angry_villager","minecraft:heart","minecraft:crit",
        "minecraft:enchanted_hit","minecraft:flame","minecraft:smoke","minecraft:large_smoke",
        "minecraft:soul_fire_flame","minecraft:soul","minecraft:snowflake","minecraft:snow",
        "minecraft:rain","minecraft:bubble","minecraft:bubble_pop","minecraft:dripping_water",
        "minecraft:dripping_lava","minecraft:falling_water","minecraft:falling_lava",
        "minecraft:portal","minecraft:end_rod","minecraft:witch","minecraft:totem_of_undying",
        "minecraft:explosion","minecraft:poof","minecraft:firework","minecraft:flash",
        "minecraft:glow","minecraft:electric_spark","minecraft:scrape",
        "minecraft:spore_blossom_air","minecraft:cherry_leaves","minecraft:dripping_honey",
        "minecraft:falling_honey","minecraft:mycelium","minecraft:enchant","minecraft:nautilus",
        "minecraft:note","minecraft:dragon_breath","minecraft:suspended",
        "minecraft:crimson_spore","minecraft:warped_spore"
    };

    private static final String[] COMMON_MUSIC = {
        "clear",
        "minecraft:music.game","minecraft:music.creative","minecraft:music.end",
        "minecraft:music.nether.basalt_deltas","minecraft:music.nether.crimson_forest",
        "minecraft:music.nether.nether_wastes","minecraft:music.nether.soul_sand_valley",
        "minecraft:music.nether.warped_forest","minecraft:music.under_water",
        "minecraft:music_disc.13","minecraft:music_disc.cat","minecraft:music_disc.blocks",
        "minecraft:music_disc.chirp","minecraft:music_disc.far","minecraft:music_disc.mall",
        "minecraft:music_disc.mellohi","minecraft:music_disc.stal","minecraft:music_disc.strad",
        "minecraft:music_disc.ward","minecraft:music_disc.11","minecraft:music_disc.wait",
        "minecraft:music_disc.otherside","minecraft:music_disc.5","minecraft:music_disc.pigstep",
        "minecraft:music_disc.relic","minecraft:music_disc.creator",
        "minecraft:music_disc.creator_music_box","minecraft:music_disc.precipice"
    };

    private static CompletableFuture<Suggestions> suggestParticles(SuggestionsBuilder builder) {
        String rem = builder.getRemaining().toLowerCase();
        for (String s : COMMON_PARTICLES) if (s.contains(rem)) builder.suggest(s);
        for (net.minecraft.util.Identifier id : Registries.PARTICLE_TYPE.getIds()) {
            String full = id.toString();
            if (full.contains(rem)) builder.suggest(full);
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestMusic(SuggestionsBuilder builder) {
        String rem = builder.getRemaining().toLowerCase();
        for (String s : COMMON_MUSIC) if (s.contains(rem)) builder.suggest(s);
        for (net.minecraft.util.Identifier id : Registries.SOUND_EVENT.getIds()) {
            String full = id.toString();
            if ((full.contains("music") || full.contains("disc")) && full.contains(rem))
                builder.suggest(full);
        }
        return builder.buildFuture();
    }

    // ── /sp help ──────────────────────────────────────────────────────────────

    private static int executeHelp(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        String[] keys = {
            "sp.help.header",
            "sp.help.add","sp.help.remove","sp.help.rename","sp.help.role",
            "sp.help.list","sp.help.info","sp.help.view","sp.help.tp",
            "sp.help.upgrade",
            "sp.help.flag","sp.help.flag_set",
            "sp.help.perm","sp.help.perm_set",
            "sp.help.fly",
            "sp.help.group","sp.help.group_create","sp.help.group_delete",
            "sp.help.group_addmember","sp.help.group_removemember","sp.help.group_setperm",
            "sp.help.plot_particle","sp.help.plot_weather",
            "sp.help.plot_time","sp.help.plot_music",
            "sp.help.plot_enter","sp.help.plot_exit",
            "sp.help.footer"
        };
        for (String key : keys) player.sendMessage(Text.translatable(key), false);
        return 1;
    }

    // ── /sp plot particle / music help ────────────────────────────────────────

    private static int executeParticleHelp(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        player.sendMessage(Text.translatable("sp.help.plot_particle_header"), false);
        player.sendMessage(Text.translatable("sp.help.plot_particle_usage"), false);
        player.sendMessage(Text.translatable("sp.help.plot_particle_hint"), false);
        for (String p : COMMON_PARTICLES) if (!p.equals("clear")) player.sendMessage(Text.literal("  §a" + p), false);
        return 1;
    }

    private static int executeMusicHelp(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        player.sendMessage(Text.translatable("sp.help.plot_music_header"), false);
        player.sendMessage(Text.translatable("sp.help.plot_music_usage"), false);
        player.sendMessage(Text.translatable("sp.help.plot_music_discs"), false);
        for (String s : COMMON_MUSIC) if (s.contains("music_disc")) player.sendMessage(Text.literal("  §b" + s), false);
        player.sendMessage(Text.translatable("sp.help.plot_music_bg"), false);
        for (String s : COMMON_MUSIC) if (s.contains("music.") && !s.equals("clear")) player.sendMessage(Text.literal("  §b" + s), false);
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
        player.sendMessage(Text.translatable("sp.view.showing",
            Text.literal(nearest.getPlotName()).formatted(Formatting.YELLOW),
            Text.literal(nearest.getSize().getRadius() + "x" + nearest.getSize().getRadius()).formatted(Formatting.AQUA)), false);
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
                    .append(Text.literal(tpFlag)), false);
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
            if (plot == null) { player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false); return 0; }
        } else {
            List<PlotData> resolved = resolvePlots(manager.getPlayerPlots(player.getUuid()), plotArg, player);
            if (resolved == null || resolved.isEmpty()) return 0;
            plot = resolved.get(0);
        }
        printPlotInfo(player, plot);
        return 1;
    }

    // ── /sp rename ────────────────────────────────────────────────────────────

    private static int executeRename(ServerCommandSource source, String newName) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) { player.sendMessage(Text.translatable("sp.rename.not_inside").formatted(Formatting.RED), false); return 0; }
        if (!plot.getOwnerId().equals(player.getUuid())) {
            player.sendMessage(Text.translatable("sp.error.not_owner").formatted(Formatting.RED), false); return 0;
        }
        // Rank check
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg != null) {
            ResolvedPerks perks = cfg.resolvePerks(player);
            if (!perks.canRename) {
                player.sendMessage(Text.translatable("sp.rank.no_perm", "rename").formatted(Formatting.RED), false); return 0;
            }
        }
        return doRename(player, manager, plot, newName);
    }

    // ── /sp add ───────────────────────────────────────────────────────────────

    private static int executeAdd(ServerCommandSource source, String targetName, String plotArg) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        List<PlotData> owned = manager.getPlayerPlots(player.getUuid());
        if (owned.isEmpty()) { player.sendMessage(Text.translatable("sp.error.no_plots").formatted(Formatting.RED), false); return 0; }

        UUID targetUuid = null; String resolvedName = targetName;
        ServerPlayerEntity online = source.getServer().getPlayerManager().getPlayer(targetName);
        if (online != null) { targetUuid = online.getUuid(); resolvedName = online.getName().getString(); }
        else {
            Optional<GameProfile> profile = source.getServer().getUserCache().findByName(targetName);
            if (profile.isPresent()) { targetUuid = profile.get().getId(); resolvedName = profile.get().getName(); }
        }
        if (targetUuid == null) { player.sendMessage(Text.translatable("sp.add.player_not_found", targetName).formatted(Formatting.RED), false); return 0; }
        if (targetUuid.equals(player.getUuid())) { player.sendMessage(Text.translatable("sp.add.self").formatted(Formatting.RED), false); return 0; }

        List<PlotData> targets = resolvePlots(owned, plotArg, player);
        if (targets == null) return 0;
        int added = 0;
        final UUID fUuid = targetUuid; final String fName = resolvedName;
        for (PlotData p : targets) if (p.getRoleOf(fUuid) == PlotData.Role.VISITOR) { p.addMember(fUuid, fName, PlotData.Role.MEMBER); added++; }
        if (added == 0) { player.sendMessage(Text.translatable("sp.add.already_member", resolvedName).formatted(Formatting.YELLOW), false); return 0; }
        manager.markDirty();
        String desc = targets.size() == 1 ? "\"" + targets.get(0).getPlotName() + "\"" : targets.size() + " plots";
        player.sendMessage(Text.translatable("sp.add.success", resolvedName, desc).formatted(Formatting.GREEN), false);
        if (online != null) online.sendMessage(Text.translatable("sp.add.notified", desc, player.getName().getString()).formatted(Formatting.GREEN), false);
        return 1;
    }

    // ── /sp remove ────────────────────────────────────────────────────────────

    private static int executeRemove(ServerCommandSource source, String targetName, String plotArg) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        List<PlotData> owned = manager.getPlayerPlots(player.getUuid());
        if (owned.isEmpty()) { player.sendMessage(Text.translatable("sp.error.no_plots").formatted(Formatting.RED), false); return 0; }
        List<PlotData> targets = resolvePlots(owned, plotArg, player);
        if (targets == null) return 0;
        UUID targetUuid = null;
        for (PlotData plot : targets) { targetUuid = findMemberUuid(plot, targetName); if (targetUuid != null) break; }
        if (targetUuid == null) { player.sendMessage(Text.translatable("sp.remove.not_member", targetName).formatted(Formatting.RED), false); return 0; }
        final UUID fUuid = targetUuid;
        for (PlotData plot : targets) if (plot.getMembers().containsKey(fUuid)) plot.removeMember(fUuid);
        manager.markDirty();
        String desc = targets.size() == 1 ? "\"" + targets.get(0).getPlotName() + "\"" : targets.size() + " plots";
        player.sendMessage(Text.translatable("sp.remove.success", targetName, desc).formatted(Formatting.GREEN), false);
        ServerPlayerEntity onlineTarget = source.getServer().getPlayerManager().getPlayer(fUuid);
        if (onlineTarget != null) onlineTarget.sendMessage(Text.translatable("sp.remove.notified", desc, player.getName().getString()).formatted(Formatting.RED), false);
        return 1;
    }

    // ── /sp role <player> <member|admin> ─────────────────────────────────────

    private static int executeSetRole(ServerCommandSource source, String targetName, String roleName) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) { player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false); return 0; }
        if (!plot.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_PERMS) && !player.getCommandTags().contains(adminTag())) {
            player.sendMessage(Text.translatable("sp.error.no_perm_perm").formatted(Formatting.RED), false); return 0;
        }
        PlotData.Role newRole;
        try { newRole = PlotData.Role.valueOf(roleName.toUpperCase()); }
        catch (IllegalArgumentException e) {
            player.sendMessage(Text.literal("§c✗ Invalid role. Use: member or admin").formatted(Formatting.RED), false); return 0;
        }
        if (newRole == PlotData.Role.OWNER || newRole == PlotData.Role.VISITOR) {
            player.sendMessage(Text.literal("§c✗ Role must be 'member' or 'admin'.").formatted(Formatting.RED), false); return 0;
        }
        UUID targetUuid = findMemberUuid(plot, targetName);
        if (targetUuid == null) { player.sendMessage(Text.translatable("sp.perm.not_member", targetName).formatted(Formatting.RED), false); return 0; }
        plot.getMembers().put(targetUuid, newRole);
        manager.markDirty();
        player.sendMessage(Text.literal("§a✔ §f" + targetName + " §ais now §f" + newRole.name().toLowerCase() + " §ain §e\"" + plot.getPlotName() + "\""), false);
        return 1;
    }

    // ── /sp tp [plot] ─────────────────────────────────────────────────────────

    private static int executeTp(ServerCommandSource source, String plotArg) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        if (SecurePlotsConfig.INSTANCE != null && !SecurePlotsConfig.INSTANCE.enablePlotTeleport) {
            player.sendMessage(Text.translatable("sp.tp.not_found").formatted(Formatting.RED), false); return 0;
        }
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = null;
        if (plotArg == null) {
            List<PlotData> owned = manager.getPlayerPlots(player.getUuid());
            if (!owned.isEmpty()) plot = owned.get(0);
        } else {
            List<PlotData> owned = manager.getPlayerPlots(player.getUuid());
            List<PlotData> resolved = resolvePlots(owned, plotArg, player);
            if (resolved != null && !resolved.isEmpty()) { plot = resolved.get(0); }
            else { for (PlotData p : manager.getAllPlots()) if (p.getPlotName().equalsIgnoreCase(plotArg) && p.hasFlag(PlotData.Flag.ALLOW_TP)) { plot = p; break; } }
        }
        if (plot == null) { player.sendMessage(Text.translatable("sp.tp.not_found").formatted(Formatting.RED), false); return 0; }
        boolean isOwner  = plot.getOwnerId().equals(player.getUuid());
        boolean isMember = plot.getRoleOf(player.getUuid()) != PlotData.Role.VISITOR;
        boolean tpPublic = plot.hasFlag(PlotData.Flag.ALLOW_TP);
        boolean isAdmin  = player.getCommandTags().contains(adminTag());
        if (!isOwner && !isMember && !tpPublic && !isAdmin) {
            player.sendMessage(Text.translatable("sp.tp.not_allowed", plot.getPlotName()).formatted(Formatting.RED), false); return 0;
        }
        if (!(player.getWorld() instanceof ServerWorld sw)) return 0;
        BlockPos c = plot.getCenter();
        double tpY = sw.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, c).getY();
        player.teleport(sw, c.getX() + 0.5, tpY, c.getZ() + 0.5, Set.of(), player.getYaw(), player.getPitch());
        player.sendMessage(Text.translatable("sp.tp.success", plot.getPlotName()).formatted(Formatting.GREEN), false);
        sw.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
        return 1;
    }

    // ── /sp upgrade ───────────────────────────────────────────────────────────

    private static int executeUpgrade(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg != null && !cfg.enableUpgrades) {
            player.sendMessage(Text.literal("§c✗ Upgrades are disabled on this server."), false); return 0;
        }
        if (!(player.getWorld() instanceof ServerWorld sw)) return 0;
        PlotManager manager = PlotManager.getOrCreate(sw);
        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) { player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false); return 0; }
        if (!plot.getOwnerId().equals(player.getUuid())) {
            player.sendMessage(Text.translatable("sp.error.not_owner").formatted(Formatting.RED), false); return 0;
        }
        {
            SecurePlotsConfig _cfg = SecurePlotsConfig.INSTANCE;
            if (_cfg != null) {
                ResolvedPerks _perks = _cfg.resolvePerks(player);
                if (!_perks.canUpgrade) {
                    player.sendMessage(Text.translatable("sp.rank.no_perm", "upgrade").formatted(Formatting.RED), false); return 0;
                }
            }
        }
        PlotSize next = plot.getSize().next();
        if (next == null) { player.sendMessage(Text.translatable("sp.upgrade.max_level").formatted(Formatting.RED), false); return 0; }

        // Check and consume materials
        SecurePlotsConfig.UpgradeCost cost = cfg != null ? cfg.getUpgradeCost(plot.getSize().tier) : null;
        if (cost != null) {
            for (var ic : cost.items) {
                var item = Registries.ITEM.get(net.minecraft.util.Identifier.of(ic.itemId));
                if (countItem(player, item) < ic.amount) {
                    player.sendMessage(Text.literal("§c✗ You don't have enough " + ic.itemId + " (" + ic.amount + " needed)."), false);
                    return 0;
                }
            }
            for (var ic : cost.items)
                removeItem(player, Registries.ITEM.get(net.minecraft.util.Identifier.of(ic.itemId)), ic.amount);
        }

        plot.setSize(next);
        sw.setBlockState(plot.getCenter(), ModBlocks.fromTier(next.tier).getDefaultState());
        manager.markDirty();
        player.sendMessage(Text.translatable("sp.upgrade.success", next.getDisplayName(), next.getRadius(), next.getRadius()).formatted(Formatting.GREEN), false);
        ModPackets.sendShowPlotBorder(player, plot);
        return 1;
    }

    // ── /sp flag ──────────────────────────────────────────────────────────────

    private static int executeFlagList(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        player.sendMessage(Text.translatable("sp.flag.list_header"), false);
        for (PlotData.Flag f : PlotData.Flag.values()) player.sendMessage(Text.literal("  §7" + f.name().toLowerCase()), false);
        player.sendMessage(Text.translatable("sp.flag.usage"), false);
        return 1;
    }

    private static int executeFlagInfo(ServerCommandSource source, String flagName) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotData.Flag flag = parseFlag(player, flagName);
        if (flag == null) return 0;
        PlotData plot = PlotManager.getOrCreate(player.getServerWorld()).getPlotAt(player.getBlockPos());
        if (plot == null) { player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false); return 0; }
        player.sendMessage(Text.translatable("sp.flag.info", flag.name().toLowerCase(), plot.getPlotName(), plot.hasFlag(flag) ? "§a[ON]" : "§c[OFF]"), false);
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
            player.sendMessage(Text.translatable("sp.error.no_flag_perm").formatted(Formatting.RED), false); return 0;
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
        for (PlotData.Permission p : PlotData.Permission.values()) player.sendMessage(Text.literal("  §7" + p.name().toLowerCase()), false);
        player.sendMessage(Text.translatable("sp.perm.usage"), false);
        return 1;
    }

    private static int executePermShow(ServerCommandSource source, String targetName, String permName) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotData.Permission perm = parsePerm(player, permName);
        if (perm == null) return 0;
        PlotData plot = PlotManager.getOrCreate(player.getServerWorld()).getPlotAt(player.getBlockPos());
        if (plot == null) { player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false); return 0; }
        UUID targetUuid = findMemberUuid(plot, targetName);
        if (targetUuid == null) { player.sendMessage(Text.translatable("sp.perm.not_member", targetName).formatted(Formatting.RED), false); return 0; }
        player.sendMessage(Text.translatable("sp.perm.show", targetName, perm.name().toLowerCase(), plot.hasPermission(targetUuid, perm) ? "§a\u2714" : "§c\u2717"), false);
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
            player.sendMessage(Text.translatable("sp.error.no_perm_perm").formatted(Formatting.RED), false); return 0;
        }
        UUID targetUuid = findMemberUuid(plot, targetName);
        if (targetUuid == null) { player.sendMessage(Text.translatable("sp.perm.not_member_in", targetName, plot.getPlotName()).formatted(Formatting.RED), false); return 0; }
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
        if (plot == null) { player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false); return 0; }
        if (!plot.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_FLAGS) && !player.getCommandTags().contains(adminTag())) {
            player.sendMessage(Text.translatable("sp.error.no_fly_perm").formatted(Formatting.RED), false); return 0;
        }
        return applyFlyChange(player, manager, plot, value != null ? value : !plot.hasFlag(PlotData.Flag.ALLOW_FLY));
    }

    private static int executeFlySet(ServerCommandSource source, boolean value, String plotArg) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = resolveSinglePlot(player, manager, plotArg);
        if (plot == null) return 0;
        if (!plot.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_FLAGS) && !player.getCommandTags().contains(adminTag())) {
            player.sendMessage(Text.translatable("sp.error.no_fly_perm").formatted(Formatting.RED), false); return 0;
        }
        return applyFlyChange(player, manager, plot, value);
    }

    private static int applyFlyChange(ServerPlayerEntity player, PlotManager manager, PlotData plot, boolean enable) {
        plot.setFlag(PlotData.Flag.ALLOW_FLY, enable);
        for (UUID uuid : plot.getMembers().keySet()) plot.setPermission(uuid, PlotData.Permission.FLY, enable);
        manager.markDirty();
        player.sendMessage(Text.translatable("sp.fly.set", enable ? "enabled" : "disabled", plot.getPlotName()).formatted(Formatting.GREEN), false);
        return 1;
    }

    // ── /sp group ─────────────────────────────────────────────────────────────

    private static int executeGroupList(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        if (SecurePlotsConfig.INSTANCE != null && !SecurePlotsConfig.INSTANCE.enablePermissionGroups) {
            player.sendMessage(Text.translatable("sp.error.no_group_perm").formatted(Formatting.RED), false); return 0;
        }
        PlotData plot = PlotManager.getOrCreate(player.getServerWorld()).getPlotAt(player.getBlockPos());
        if (plot == null) { player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false); return 0; }
        if (plot.getGroups().isEmpty()) { player.sendMessage(Text.translatable("sp.group.empty"), false); }
        else {
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
        if (SecurePlotsConfig.INSTANCE != null && !SecurePlotsConfig.INSTANCE.enablePermissionGroups) {
            player.sendMessage(Text.translatable("sp.error.no_group_perm").formatted(Formatting.RED), false); return 0;
        }
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) { player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false); return 0; }
        if (!plot.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS) && !player.getCommandTags().contains(adminTag())) {
            player.sendMessage(Text.translatable("sp.error.no_group_perm").formatted(Formatting.RED), false); return 0;
        }
        if (plot.getGroup(groupName) != null) { player.sendMessage(Text.translatable("sp.group.already_exists").formatted(Formatting.YELLOW), false); return 0; }
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
        if (plot == null) { player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false); return 0; }
        if (!plot.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS) && !player.getCommandTags().contains(adminTag())) {
            player.sendMessage(Text.translatable("sp.error.no_group_perm").formatted(Formatting.RED), false); return 0;
        }
        if (!plot.removeGroup(groupName)) { player.sendMessage(Text.translatable("sp.group.not_found", groupName).formatted(Formatting.RED), false); return 0; }
        manager.markDirty();
        player.sendMessage(Text.translatable("sp.group.deleted", groupName).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeGroupAddMember(ServerCommandSource source, String groupName, String targetName) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) { player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false); return 0; }
        PlotData.PermissionGroup group = plot.getGroup(groupName);
        if (group == null) { player.sendMessage(Text.translatable("sp.group.not_found", groupName).formatted(Formatting.RED), false); return 0; }
        UUID targetUuid = findMemberUuid(plot, targetName);
        if (targetUuid == null) { player.sendMessage(Text.translatable("sp.group.member_not_found", targetName).formatted(Formatting.RED), false); return 0; }
        if (group.members.contains(targetUuid)) { player.sendMessage(Text.translatable("sp.group.already_in_group", targetName).formatted(Formatting.YELLOW), false); return 0; }
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
        if (plot == null) { player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false); return 0; }
        PlotData.PermissionGroup group = plot.getGroup(groupName);
        if (group == null) { player.sendMessage(Text.translatable("sp.group.not_found", groupName).formatted(Formatting.RED), false); return 0; }
        UUID targetUuid = findMemberUuid(plot, targetName);
        if (targetUuid == null || !group.members.remove(targetUuid)) { player.sendMessage(Text.translatable("sp.group.not_in_group", targetName).formatted(Formatting.YELLOW), false); return 0; }
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
        if (plot == null) { player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false); return 0; }
        if (!plot.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS) && !player.getCommandTags().contains(adminTag())) {
            player.sendMessage(Text.translatable("sp.error.no_group_perm").formatted(Formatting.RED), false); return 0;
        }
        PlotData.PermissionGroup group = plot.getGroup(groupName);
        if (group == null) { player.sendMessage(Text.translatable("sp.group.not_found", groupName).formatted(Formatting.RED), false); return 0; }
        if (value) group.permissions.add(perm); else group.permissions.remove(perm);
        manager.markDirty();
        player.sendMessage(Text.translatable("sp.group.perm_set", perm.name().toLowerCase(), value ? "enabled" : "disabled", groupName).formatted(Formatting.GREEN), false);
        return 1;
    }

    // ── /sp plot particle|weather|time|music|enter|exit ───────────────────────

    private static int executeSetParticle(ServerCommandSource source, String type) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotData plot = getOwnedPlotAt(player);
        if (plot == null) return 0;
        {
            SecurePlotsConfig _cfg = SecurePlotsConfig.INSTANCE;
            if (_cfg != null && !_cfg.resolvePerks(player).canSetParticles) {
                player.sendMessage(Text.translatable("sp.rank.no_perm", "particles").formatted(Formatting.RED), false); return 0;
            }
        }
        String value = type.equalsIgnoreCase("clear") || type.equalsIgnoreCase("none") ? ""
            : (type.contains(":") ? type : "minecraft:" + type);
        plot.setParticleEffect(value);
        PlotManager.getOrCreate(player.getServerWorld()).markDirty();
        player.sendMessage(value.isEmpty() ? Text.translatable("sp.plot.particle_cleared").formatted(Formatting.GREEN)
            : Text.translatable("sp.plot.particle_set", value).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeSetWeather(ServerCommandSource source, String type) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotData plot = getOwnedPlotAt(player);
        if (plot == null) return 0;
        String value = type.equalsIgnoreCase("clear") || type.equalsIgnoreCase("none") ? "" : type.toUpperCase();
        if (!value.isEmpty() && !value.equals("CLEAR") && !value.equals("RAIN") && !value.equals("THUNDER")) {
            player.sendMessage(Text.translatable("sp.plot.weather_invalid").formatted(Formatting.RED), false); return 0;
        }
        plot.setWeatherType(value);
        PlotManager.getOrCreate(player.getServerWorld()).markDirty();
        player.sendMessage(value.isEmpty() ? Text.translatable("sp.plot.weather_cleared").formatted(Formatting.GREEN)
            : Text.translatable("sp.plot.weather_set", value.toLowerCase()).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeSetTime(ServerCommandSource source, String valueStr) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotData plot = getOwnedPlotAt(player);
        if (plot == null) return 0;
        long ticks = parseTime(player, valueStr);
        if (ticks == Long.MIN_VALUE) return 0;
        plot.setPlotTime(ticks);
        PlotManager.getOrCreate(player.getServerWorld()).markDirty();
        player.sendMessage(ticks < 0 ? Text.translatable("sp.plot.time_cleared").formatted(Formatting.GREEN)
            : Text.translatable("sp.plot.time_set", ticks).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeSetMusic(ServerCommandSource source, String soundId) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotData plot = getOwnedPlotAt(player);
        if (plot == null) return 0;
        {
            SecurePlotsConfig _cfg = SecurePlotsConfig.INSTANCE;
            if (_cfg != null && !_cfg.resolvePerks(player).canSetMusic) {
                player.sendMessage(Text.translatable("sp.rank.no_perm", "music").formatted(Formatting.RED), false); return 0;
            }
        }
        String value = soundId.equalsIgnoreCase("clear") || soundId.equalsIgnoreCase("none") ? "" : soundId;
        plot.setMusicSound(value);
        PlotManager.getOrCreate(player.getServerWorld()).markDirty();
        player.sendMessage(value.isEmpty() ? Text.translatable("sp.plot.music_cleared").formatted(Formatting.GREEN)
            : Text.translatable("sp.plot.music_set", value).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeSetMessage(ServerCommandSource source, String msg, boolean isEnter) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotData plot = getOwnedPlotAt(player);
        if (plot == null) return 0;
        String value = msg.equalsIgnoreCase("clear") || msg.equalsIgnoreCase("none") ? "" : msg;
        if (isEnter) plot.setEnterMessage(value); else plot.setExitMessage(value);
        PlotManager.getOrCreate(player.getServerWorld()).markDirty();
        player.sendMessage(value.isEmpty()
            ? Text.literal("§a✔ " + (isEnter ? "Enter" : "Exit") + " message cleared.")
            : Text.translatable("sp.message.updated", value).formatted(Formatting.GREEN), false);
        return 1;
    }

    // ── /sp admin listall ────────────────────────────────────────────────────

    private static final int PAGE_SIZE = 10;

    private static int executeAdminListAll(ServerCommandSource source, int page, String filter) {
        ServerPlayerEntity admin = source.getPlayer();
        if (admin == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(admin.getServerWorld());
        List<PlotData> all = manager.getAllPlots();
        if (all.isEmpty()) { admin.sendMessage(Text.literal("§7No plots on this server."), false); return 0; }

        // Sort by owner name then plot name
        all.sort(Comparator.comparing(PlotData::getOwnerName).thenComparing(PlotData::getPlotName));

        int total = all.size();
        int pages  = (int) Math.ceil(total / (double) PAGE_SIZE);
        int p      = Math.max(1, Math.min(page, pages));
        int start  = (p - 1) * PAGE_SIZE;
        int end    = Math.min(start + PAGE_SIZE, total);

        admin.sendMessage(Text.literal("§6🛡 All plots — page §e" + p + "§6/§e" + pages + " §8(total: " + total + ")").formatted(Formatting.BOLD), false);
        for (int i = start; i < end; i++) {
            PlotData pd = all.get(i);
            BlockPos  c  = pd.getCenter();
            admin.sendMessage(Text.literal(
                "  §7" + (i + 1) + ". §f" + pd.getPlotName() +
                " §8[" + pd.getSize().getDisplayName() + "] " +
                "§7owner: §a" + pd.getOwnerName() +
                " §8@ " + c.getX() + ", " + c.getY() + ", " + c.getZ()), false);
        }
        if (pages > 1)
            admin.sendMessage(Text.literal("§8Use §e/sp admin listall " + (p + 1) + " §8for next page."), false);
        return 1;
    }

    // ── /sp admin search <player> [page] ─────────────────────────────────────

    private static int executeAdminSearch(ServerCommandSource source, String ownerName, int page) {
        ServerPlayerEntity admin = source.getPlayer();
        if (admin == null) return 0;
        UUID ownerUuid = resolveUuid(source, ownerName);
        if (ownerUuid == null) {
            admin.sendMessage(Text.translatable("sp.add.player_not_found", ownerName).formatted(Formatting.RED), false);
            return 0;
        }
        List<PlotData> plots = PlotManager.getOrCreate(admin.getServerWorld()).getPlayerPlots(ownerUuid);
        if (plots.isEmpty()) { admin.sendMessage(Text.literal("§7" + ownerName + " has no plots."), false); return 0; }

        int total = plots.size();
        int pages  = (int) Math.ceil(total / (double) PAGE_SIZE);
        int p      = Math.max(1, Math.min(page, pages));
        int start  = (p - 1) * PAGE_SIZE;
        int end    = Math.min(start + PAGE_SIZE, total);

        admin.sendMessage(Text.literal("§6🛡 Plots of §e" + ownerName + " §8(§e" + total + "§8) — page " + p + "/" + pages).formatted(Formatting.BOLD), false);
        for (int i = start; i < end; i++) {
            PlotData pd = plots.get(i);
            BlockPos  c  = pd.getCenter();
            admin.sendMessage(Text.literal(
                "  " + (i + 1) + ". §f" + pd.getPlotName() +
                " §8[" + pd.getSize().getDisplayName() + "]" +
                " §8@ " + c.getX() + ", " + c.getY() + ", " + c.getZ()), false);
        }
        if (pages > 1)
            admin.sendMessage(Text.literal("§8Use §e/sp admin search " + ownerName + " " + (p + 1) + " §8for next page."), false);
        return 1;
    }

    // ── /sp admin nearby [count] [x y z] ─────────────────────────────────────

    private static int executeAdminNearby(ServerCommandSource source,
                                          Integer ox, Integer oy, Integer oz, int count) {
        ServerPlayerEntity admin = source.getPlayer();
        if (admin == null) return 0;

        // Use given coords or fall back to admin's position
        final double refX, refZ;
        if (ox != null && oz != null) {
            refX = ox; refZ = oz;
        } else {
            refX = admin.getX(); refZ = admin.getZ();
        }

        PlotManager manager = PlotManager.getOrCreate(admin.getServerWorld());
        List<PlotData> all  = manager.getAllPlots();
        if (all.isEmpty()) { admin.sendMessage(Text.literal("§7No plots on this server."), false); return 0; }

        // Sort by 2D distance (XZ) to the reference point
        all.sort(Comparator.comparingDouble(pd -> {
            double dx = pd.getCenter().getX() - refX;
            double dz = pd.getCenter().getZ() - refZ;
            return dx * dx + dz * dz;
        }));

        int shown = Math.min(count, all.size());
        String origin = (ox != null && oz != null)
            ? "§8coords §e" + ox + ", " + (oy != null ? oy : "~") + ", " + oz
            : "§8your position";
        admin.sendMessage(Text.literal("§6🛡 " + shown + " nearest plot(s) to " + origin).formatted(Formatting.BOLD), false);

        for (int i = 0; i < shown; i++) {
            PlotData pd = all.get(i);
            BlockPos  c  = pd.getCenter();
            int dist = (int) Math.sqrt(Math.pow(c.getX() - refX, 2) + Math.pow(c.getZ() - refZ, 2));
            admin.sendMessage(Text.literal(
                "  " + (i + 1) + ". §f" + pd.getPlotName() +
                " §8[" + pd.getSize().getDisplayName() + "]" +
                " §7owner: §a" + pd.getOwnerName() +
                " §8@ " + c.getX() + ", " + c.getY() + ", " + c.getZ() +
                " §8(§e" + dist + "m§8)"), false);
        }
        return 1;
    }

    // ── /sp admin ─────────────────────────────────────────────────────────────

    private static int executeAdminList(ServerCommandSource source, String ownerName) {
        ServerPlayerEntity admin = source.getPlayer();
        if (admin == null) return 0;
        UUID ownerUuid = resolveUuid(source, ownerName);
        if (ownerUuid == null) { admin.sendMessage(Text.translatable("sp.add.player_not_found", ownerName).formatted(Formatting.RED), false); return 0; }
        List<PlotData> plots = PlotManager.getOrCreate(admin.getServerWorld()).getPlayerPlots(ownerUuid);
        if (plots.isEmpty()) { admin.sendMessage(Text.literal("§7" + ownerName + " has no plots."), false); return 0; }
        admin.sendMessage(Text.literal("§6🛡 Plots of " + ownerName + " (" + plots.size() + ")").formatted(Formatting.BOLD), false);
        for (int i = 0; i < plots.size(); i++) {
            PlotData p = plots.get(i);
            BlockPos c = p.getCenter();
            admin.sendMessage(Text.literal("  " + (i + 1) + ". §f" + p.getPlotName() + " §8[" + p.getSize().getDisplayName() + "] " + c.getX() + ", " + c.getY() + ", " + c.getZ()), false);
        }
        return 1;
    }

    private static int executeAdminInfo(ServerCommandSource source, String ownerName, String plotArg) {
        ServerPlayerEntity admin = source.getPlayer();
        if (admin == null) return 0;
        UUID ownerUuid = resolveUuid(source, ownerName);
        if (ownerUuid == null) { admin.sendMessage(Text.translatable("sp.add.player_not_found", ownerName).formatted(Formatting.RED), false); return 0; }
        PlotManager manager = PlotManager.getOrCreate(admin.getServerWorld());
        List<PlotData> owned = manager.getPlayerPlots(ownerUuid);
        List<PlotData> resolved = resolvePlots(owned, plotArg, admin);
        if (resolved == null || resolved.isEmpty()) return 0;
        printPlotInfo(admin, resolved.get(0));
        return 1;
    }

    private static int executeAdminTp(ServerCommandSource source, String ownerName, String plotArg) {
        ServerPlayerEntity admin = source.getPlayer();
        if (admin == null) return 0;
        UUID ownerUuid = resolveUuid(source, ownerName);
        if (ownerUuid == null) { admin.sendMessage(Text.translatable("sp.add.player_not_found", ownerName).formatted(Formatting.RED), false); return 0; }
        PlotManager manager = PlotManager.getOrCreate(admin.getServerWorld());
        List<PlotData> owned = manager.getPlayerPlots(ownerUuid);
        List<PlotData> resolved = resolvePlots(owned, plotArg, admin);
        if (resolved == null || resolved.isEmpty()) return 0;
        PlotData plot = resolved.get(0);
        if (!(admin.getWorld() instanceof ServerWorld sw)) return 0;
        BlockPos c = plot.getCenter();
        double tpY = sw.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, c).getY();
        admin.teleport(sw, c.getX() + 0.5, tpY, c.getZ() + 0.5, Set.of(), admin.getYaw(), admin.getPitch());
        admin.sendMessage(Text.translatable("sp.tp.success", plot.getPlotName()).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeAdminDelete(ServerCommandSource source, String ownerName, String plotArg) {
        ServerPlayerEntity admin = source.getPlayer();
        if (admin == null) return 0;
        UUID ownerUuid = resolveUuid(source, ownerName);
        if (ownerUuid == null) { admin.sendMessage(Text.translatable("sp.add.player_not_found", ownerName).formatted(Formatting.RED), false); return 0; }
        PlotManager manager = PlotManager.getOrCreate(admin.getServerWorld());
        List<PlotData> owned = manager.getPlayerPlots(ownerUuid);
        if (owned.isEmpty()) { admin.sendMessage(Text.translatable("sp.error.no_plots").formatted(Formatting.RED), false); return 0; }
        List<PlotData> targets = plotArg.equalsIgnoreCase("all") ? new ArrayList<>(owned) : resolvePlots(owned, plotArg, admin);
        if (targets == null) return 0;
        for (PlotData p : targets) manager.removePlot(p.getCenter());
        admin.sendMessage(Text.literal("§a\u2714 Deleted " + targets.size() + " plot(s) owned by §f" + ownerName), false);
        return 1;
    }

    private static int executeAdminRename(ServerCommandSource source, String ownerName, String plotArg, String newName) {
        ServerPlayerEntity admin = source.getPlayer();
        if (admin == null) return 0;
        UUID ownerUuid = resolveUuid(source, ownerName);
        if (ownerUuid == null) { admin.sendMessage(Text.translatable("sp.add.player_not_found", ownerName).formatted(Formatting.RED), false); return 0; }
        PlotManager manager = PlotManager.getOrCreate(admin.getServerWorld());
        List<PlotData> owned = manager.getPlayerPlots(ownerUuid);
        List<PlotData> resolved = resolvePlots(owned, plotArg, admin);
        if (resolved == null || resolved.isEmpty()) return 0;
        return doRename(admin, manager, resolved.get(0), newName);
    }

    private static int executeAdminSetOwner(ServerCommandSource source, String newOwnerName) {
        ServerPlayerEntity admin = source.getPlayer();
        if (admin == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(admin.getServerWorld());
        PlotData plot = manager.getPlotAt(admin.getBlockPos());
        if (plot == null) { admin.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false); return 0; }
        ServerPlayerEntity newOwner = source.getServer().getPlayerManager().getPlayer(newOwnerName);
        UUID newUuid; String resolvedName;
        if (newOwner != null) { newUuid = newOwner.getUuid(); resolvedName = newOwner.getName().getString(); }
        else {
            Optional<GameProfile> profile = source.getServer().getUserCache().findByName(newOwnerName);
            if (profile.isEmpty()) { admin.sendMessage(Text.translatable("sp.add.player_not_found", newOwnerName).formatted(Formatting.RED), false); return 0; }
            newUuid = profile.get().getId(); resolvedName = profile.get().getName();
        }
        plot.addMember(newUuid, resolvedName, PlotData.Role.MEMBER);
        plot.getMembers().put(newUuid, PlotData.Role.OWNER);
        manager.markDirty();
        admin.sendMessage(Text.literal("§a\u2714 §fOwnership of §e\"" + plot.getPlotName() + "§e\" §ftransferred to §a" + resolvedName), false);
        if (newOwner != null) newOwner.sendMessage(Text.literal("§eYou have been given ownership of §f\"" + plot.getPlotName() + "\""), false);
        return 1;
    }

    private static int executeAdminUpgrade(ServerCommandSource source, String ownerName, String plotArg) {
        ServerPlayerEntity admin = source.getPlayer();
        if (admin == null) return 0;
        UUID ownerUuid = resolveUuid(source, ownerName);
        if (ownerUuid == null) { admin.sendMessage(Text.translatable("sp.add.player_not_found", ownerName).formatted(Formatting.RED), false); return 0; }
        if (!(admin.getWorld() instanceof ServerWorld sw)) return 0;
        PlotManager manager = PlotManager.getOrCreate(sw);
        List<PlotData> owned = manager.getPlayerPlots(ownerUuid);
        List<PlotData> resolved = resolvePlots(owned, plotArg, admin);
        if (resolved == null || resolved.isEmpty()) return 0;
        PlotData plot = resolved.get(0);
        PlotSize next = plot.getSize().next();
        if (next == null) { admin.sendMessage(Text.translatable("sp.upgrade.max_level").formatted(Formatting.RED), false); return 0; }
        plot.setSize(next);
        sw.setBlockState(plot.getCenter(), ModBlocks.fromTier(next.tier).getDefaultState());
        manager.markDirty();
        admin.sendMessage(Text.translatable("sp.upgrade.success", next.getDisplayName(), next.getRadius(), next.getRadius()).formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeAdminRank(ServerCommandSource source, String ownerName, String plotArg, boolean value) {
        ServerPlayerEntity admin = source.getPlayer();
        if (admin == null) return 0;
        UUID ownerUuid = resolveUuid(source, ownerName);
        if (ownerUuid == null) { admin.sendMessage(Text.translatable("sp.add.player_not_found", ownerName).formatted(Formatting.RED), false); return 0; }
        PlotManager manager = PlotManager.getOrCreate(admin.getServerWorld());
        List<PlotData> owned = manager.getPlayerPlots(ownerUuid);
        List<PlotData> resolved = resolvePlots(owned, plotArg, admin);
        if (resolved == null || resolved.isEmpty()) return 0;
        PlotData plot = resolved.get(0);
        plot.setHasRank(value);
        manager.markDirty();
        admin.sendMessage(Text.literal("§a\u2714 §fPlot §e\"" + plot.getPlotName() + "§e\" rank protection: §f" + (value ? "§aenabled (immune to expiry)" : "§cdisabled")), false);
        return 1;
    }

    /** Generic admin ambient setter: particle, music, weather, time, enter, exit */
    private static int executeAdminAmbient(ServerCommandSource source, String ownerName, String plotArg, String field, String value) {
        ServerPlayerEntity admin = source.getPlayer();
        if (admin == null) return 0;
        UUID ownerUuid = resolveUuid(source, ownerName);
        if (ownerUuid == null) { admin.sendMessage(Text.translatable("sp.add.player_not_found", ownerName).formatted(Formatting.RED), false); return 0; }
        PlotManager manager = PlotManager.getOrCreate(admin.getServerWorld());
        List<PlotData> owned = manager.getPlayerPlots(ownerUuid);
        List<PlotData> resolved = resolvePlots(owned, plotArg, admin);
        if (resolved == null || resolved.isEmpty()) return 0;
        PlotData plot = resolved.get(0);
        String clear = value.equalsIgnoreCase("clear") || value.equalsIgnoreCase("none") ? "" : value;
        switch (field) {
            case "particle" -> plot.setParticleEffect(clear.isEmpty() ? "" : (clear.contains(":") ? clear : "minecraft:" + clear));
            case "music"    -> plot.setMusicSound(clear);
            case "weather"  -> {
                if (!clear.isEmpty() && !clear.equalsIgnoreCase("CLEAR") && !clear.equalsIgnoreCase("RAIN") && !clear.equalsIgnoreCase("THUNDER")) {
                    admin.sendMessage(Text.translatable("sp.plot.weather_invalid").formatted(Formatting.RED), false); return 0;
                }
                plot.setWeatherType(clear);
            }
            case "time" -> {
                long ticks = parseTime(admin, value);
                if (ticks == Long.MIN_VALUE) return 0;
                plot.setPlotTime(ticks);
            }
            case "enter" -> plot.setEnterMessage(clear);
            case "exit"  -> plot.setExitMessage(clear);
        }
        manager.markDirty();
        admin.sendMessage(Text.literal("§a\u2714 §f" + field + " updated for §e\"" + plot.getPlotName() + "\"").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int executeAdminReload(ServerCommandSource source) {
        ServerPlayerEntity admin = source.getPlayer();
        SecurePlotsConfig.load();
        com.zhilius.secureplots.config.BorderConfig.load();
        for (ServerPlayerEntity p : source.getServer().getPlayerManager().getPlayerList())
            ModPackets.sendSyncBorderConfig(p);
        Text msg = Text.literal("§a\u2714 SecurePlots config reloaded.");
        if (admin != null) admin.sendMessage(msg, false);
        else source.sendFeedback(() -> msg, false);
        return 1;
    }

    // ── /sp myrank ────────────────────────────────────────────────────────────

    private static int executeMyRank(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg == null) { player.sendMessage(Text.literal("§cConfig not loaded."), false); return 0; }

        ResolvedPerks perks = cfg.resolvePerks(player);
        int maxPlots = cfg.getMaxPlotsFor(player);

        player.sendMessage(Text.literal("§e══ Your Plot Rank Perks ══"), false);
        player.sendMessage(Text.literal("§7Max plots:    §f" + (maxPlots == 0 ? "unlimited" : maxPlots)), false);
        player.sendMessage(Text.literal("§7Max tier:     §f" + perks.maxTier + " (" + cfg.getTierConfig(perks.maxTier).displayName + ")"), false);
        player.sendMessage(Text.literal("§7Rename:       " + perkIcon(perks.canRename)), false);
        player.sendMessage(Text.literal("§7Music:        " + perkIcon(perks.canSetMusic)), false);
        player.sendMessage(Text.literal("§7Particles:    " + perkIcon(perks.canSetParticles)), false);
        player.sendMessage(Text.literal("§7Weather:      " + perkIcon(perks.canSetWeather)), false);
        player.sendMessage(Text.literal("§7Time:         " + perkIcon(perks.canSetTime)), false);
        player.sendMessage(Text.literal("§7Enter/Exit:   " + perkIcon(perks.canSetEnterExit)), false);
        player.sendMessage(Text.literal("§7Teleport:     " + perkIcon(perks.canTp)), false);
        player.sendMessage(Text.literal("§7Fly:          " + perkIcon(perks.canFly)), false);
        player.sendMessage(Text.literal("§7Upgrade:      " + perkIcon(perks.canUpgrade)), false);
        player.sendMessage(Text.literal("§7Groups:       " + perkIcon(perks.canGroups)), false);
        player.sendMessage(Text.literal("§7Rank protect: " + perkIcon(perks.hasRankProtection)), false);
        return 1;
    }

    private static String perkIcon(boolean enabled) {
        return enabled ? "§a✔" : "§c✗";
    }

    // ── /sp admin setrank / removerank ────────────────────────────────────────

    private static int executeAdminSetRank(ServerCommandSource source, String targetName, String tag, boolean add) {
        ServerPlayerEntity admin = source.getPlayer();
        if (admin == null) return 0;
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(targetName);
        if (target == null) {
            if (admin != null) admin.sendMessage(Text.translatable("sp.add.player_not_found", targetName).formatted(Formatting.RED), false);
            return 0;
        }
        if (add) {
            target.addCommandTag(tag);
            admin.sendMessage(Text.literal("§a✔ §fTag §e" + tag + " §fadded to §a" + targetName), false);
            target.sendMessage(Text.literal("§aYou received the rank tag §e" + tag), false);
        } else {
            target.removeCommandTag(tag);
            admin.sendMessage(Text.literal("§a✔ §fTag §e" + tag + " §fremoved from §c" + targetName), false);
        }
        return 1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static PlotData getOwnedPlotAt(ServerPlayerEntity player) {
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) { player.sendMessage(Text.translatable("sp.error.not_in_plot").formatted(Formatting.RED), false); return null; }
        if (!plot.getOwnerId().equals(player.getUuid()) && !player.getCommandTags().contains(adminTag())) {
            player.sendMessage(Text.translatable("sp.error.not_owner").formatted(Formatting.RED), false); return null;
        }
        return plot;
    }

    private static PlotData resolveSinglePlot(ServerPlayerEntity player, PlotManager manager, String plotArg) {
        if (plotArg == null) {
            PlotData plot = manager.getPlotAt(player.getBlockPos());
            if (plot == null) { player.sendMessage(Text.translatable("sp.error.not_in_plot_arg").formatted(Formatting.RED), false); return null; }
            if (!plot.getOwnerId().equals(player.getUuid()) && plot.getRoleOf(player.getUuid()) != PlotData.Role.ADMIN && !player.getCommandTags().contains(adminTag())) {
                player.sendMessage(Text.translatable("sp.error.not_owner_or_admin").formatted(Formatting.RED), false); return null;
            }
            return plot;
        }
        List<PlotData> resolved = resolvePlots(manager.getPlayerPlots(player.getUuid()), plotArg, player);
        return (resolved == null || resolved.isEmpty()) ? null : resolved.get(0);
    }

    private static List<PlotData> resolvePlots(List<PlotData> owned, String plotArg, ServerPlayerEntity player) {
        if (plotArg.equalsIgnoreCase("all")) return new ArrayList<>(owned);
        try {
            int index = Integer.parseInt(plotArg);
            if (index < 1 || index > owned.size()) {
                player.sendMessage(Text.translatable("sp.error.invalid_index", owned.size()).formatted(Formatting.RED), false); return null;
            }
            return List.of(owned.get(index - 1));
        } catch (NumberFormatException ignored) {}
        for (PlotData p : owned) if (p.getPlotName().equalsIgnoreCase(plotArg)) return List.of(p);
        player.sendMessage(Text.translatable("sp.error.plot_not_found", plotArg).formatted(Formatting.RED)
            .append(Text.literal(" ")).append(Text.literal("/sp list").formatted(Formatting.YELLOW)), false);
        return null;
    }

    private static PlotData getNearestOwnedPlot(ServerPlayerEntity player, PlotManager manager) {
        List<PlotData> owned = manager.getPlayerPlots(player.getUuid());
        if (owned.isEmpty()) return null;
        BlockPos pp = player.getBlockPos();
        return owned.stream().min(Comparator.comparingDouble(p -> pp.getSquaredDistance(p.getCenter()))).orElse(null);
    }

    private static UUID findMemberUuid(PlotData plot, String name) {
        for (UUID uuid : plot.getMembers().keySet()) if (plot.getMemberName(uuid).equalsIgnoreCase(name)) return uuid;
        return null;
    }

    /** Resolves a player name to UUID via online players or UserCache. */
    private static UUID resolveUuid(ServerCommandSource source, String name) {
        ServerPlayerEntity online = source.getServer().getPlayerManager().getPlayer(name);
        if (online != null) return online.getUuid();
        Optional<GameProfile> profile = source.getServer().getUserCache().findByName(name);
        return profile.map(GameProfile::getId).orElse(null);
    }

    private static int doRename(ServerPlayerEntity player, PlotManager manager, PlotData plot, String newName) {
        newName = newName.trim();
        if (newName.isEmpty() || newName.length() > 32) { player.sendMessage(Text.translatable("sp.rename.invalid_name").formatted(Formatting.RED), false); return 0; }
        for (PlotData p : manager.getPlayerPlots(plot.getOwnerId())) {
            if (p != plot && p.getPlotName().equalsIgnoreCase(newName)) { player.sendMessage(Text.translatable("sp.rename.duplicate").formatted(Formatting.RED), false); return 0; }
        }
        String old = plot.getPlotName();
        plot.setPlotName(newName);
        manager.markDirty();
        player.sendMessage(Text.literal("\u2714 ").formatted(Formatting.GREEN)
            .append(Text.literal("\"" + old + "\"").formatted(Formatting.GRAY))
            .append(Text.literal(" \u2192 ").formatted(Formatting.GREEN))
            .append(Text.literal("\"" + newName + "\"").formatted(Formatting.YELLOW)), false);
        return 1;
    }

    private static long parseTime(ServerPlayerEntity player, String valueStr) {
        return switch (valueStr.toLowerCase()) {
            case "day"      -> 1000L;
            case "noon"     -> 6000L;
            case "sunset"   -> 12000L;
            case "night"    -> 13000L;
            case "midnight" -> 18000L;
            case "sunrise"  -> 23000L;
            case "reset","clear","none" -> -1L;
            default -> {
                try { yield Long.parseLong(valueStr); }
                catch (NumberFormatException e) {
                    player.sendMessage(Text.translatable("sp.plot.time_invalid").formatted(Formatting.RED), false);
                    yield Long.MIN_VALUE;
                }
            }
        };
    }

    private static void printPlotInfo(ServerPlayerEntity player, PlotData plot) {
        BlockPos c = plot.getCenter(); int sz = plot.getSize().getRadius();
        player.sendMessage(Text.translatable("sp.info.header",  plot.getPlotName()).formatted(Formatting.YELLOW, Formatting.BOLD), false);
        player.sendMessage(Text.translatable("sp.info.owner",   plot.getOwnerName()), false);
        player.sendMessage(Text.translatable("sp.info.tier",    plot.getSize().getDisplayName()), false);
        player.sendMessage(Text.translatable("sp.info.size",    sz + "x" + sz), false);
        player.sendMessage(Text.translatable("sp.info.coords",  c.getX(), c.getY(), c.getZ()), false);
        if (!plot.getParticleEffect().isBlank()) player.sendMessage(Text.translatable("sp.info.particle", plot.getParticleEffect()), false);
        if (!plot.getWeatherType().isBlank())    player.sendMessage(Text.translatable("sp.info.weather",  plot.getWeatherType()), false);
        if (plot.getPlotTime() >= 0)             player.sendMessage(Text.translatable("sp.info.time",     plot.getPlotTime()), false);
        if (!plot.getMusicSound().isBlank())     player.sendMessage(Text.translatable("sp.info.music",    plot.getMusicSound()), false);
        if (!plot.getEnterMessage().isBlank())   player.sendMessage(Text.literal("§7  Enter msg: §f" + plot.getEnterMessage()), false);
        if (!plot.getExitMessage().isBlank())    player.sendMessage(Text.literal("§7  Exit msg:  §f" + plot.getExitMessage()), false);
        if (!plot.getFlags().isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (PlotData.Flag f : plot.getFlags()) { if (sb.length() > 0) sb.append(", "); sb.append(f.name().toLowerCase()); }
            player.sendMessage(Text.translatable("sp.info.flags", sb.toString()), false);
        }
        if (!plot.getGroups().isEmpty()) {
            player.sendMessage(Text.translatable("sp.info.groups", plot.getGroups().size()), false);
            for (PlotData.PermissionGroup g : plot.getGroups())
                player.sendMessage(Text.literal("    \u2022 §d" + g.name + " §8(" + g.members.size() + " members, " + g.permissions.size() + " perms)"), false);
        }
        Map<UUID, PlotData.Role> members = plot.getMembers();
        if (members.isEmpty()) { player.sendMessage(Text.translatable("sp.info.no_members"), false); }
        else {
            player.sendMessage(Text.translatable("sp.info.members", members.size()), false);
            for (Map.Entry<UUID, PlotData.Role> entry : members.entrySet())
                player.sendMessage(Text.literal("    \u2022 ").formatted(Formatting.DARK_GRAY)
                    .append(Text.literal(plot.getMemberName(entry.getKey())).formatted(Formatting.WHITE))
                    .append(Text.literal(" [" + entry.getValue().name().toLowerCase() + "]").formatted(Formatting.GRAY)), false);
        }
    }

    private static PlotData.Flag parseFlag(ServerPlayerEntity player, String name) {
        try { return PlotData.Flag.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { player.sendMessage(Text.translatable("sp.error.unknown_flag", name).formatted(Formatting.RED), false); return null; }
    }

    private static PlotData.Permission parsePerm(ServerPlayerEntity player, String name) {
        try { return PlotData.Permission.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { player.sendMessage(Text.translatable("sp.error.unknown_perm", name).formatted(Formatting.RED), false); return null; }
    }

    private static String adminTag() {
        return SecurePlotsConfig.INSTANCE != null ? SecurePlotsConfig.INSTANCE.adminTag : "plot_admin";
    }

    private static Formatting getTierFormatting(int tier) {
        return switch (tier) {
            case 0 -> Formatting.GOLD; case 1 -> Formatting.YELLOW; case 2 -> Formatting.GREEN;
            case 3 -> Formatting.AQUA; case 4 -> Formatting.DARK_PURPLE; default -> Formatting.WHITE;
        };
    }

    private static int countItem(ServerPlayerEntity player, net.minecraft.item.Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            net.minecraft.item.ItemStack s = player.getInventory().getStack(i);
            if (s.getItem() == item) count += s.getCount();
        }
        return count;
    }

    private static void removeItem(ServerPlayerEntity player, net.minecraft.item.Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
            net.minecraft.item.ItemStack s = player.getInventory().getStack(i);
            if (s.getItem() == item) { int take = Math.min(s.getCount(), remaining); s.decrement(take); remaining -= take; }
        }
    }
}
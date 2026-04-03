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
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.zhilius.secureplots.config.SecurePlotsConfig;
import com.zhilius.secureplots.config.SecurePlotsConfig.ResolvedPerks;
import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotManager;
import com.zhilius.secureplots.plot.PlotSize;
import com.zhilius.secureplots.plot.ProtectedAreaManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.zhilius.secureplots.block.ModBlocks;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.particle.ParticleTypes;
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

                // /sp pos1 — set first corner of selection
                .then(CommandManager.literal("pos1")
                    .executes(ctx -> executePos1(ctx.getSource())))
                // /sp pos2 — set second corner of selection
                .then(CommandManager.literal("pos2")
                    .executes(ctx -> executePos2(ctx.getSource())))
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

                // /sp create [tier]  — create a new plot by selecting area
                .then(CommandManager.literal("create")
                    .executes(ctx -> executeCreateStart(ctx.getSource()))
                    .then(CommandManager.argument("tier", StringArgumentType.string())
                        .suggests((ctx, b) -> suggestTier(b))
                        .executes(ctx -> executeCreateWithTier(ctx.getSource(),
                            StringArgumentType.getString(ctx, "tier")))))
                // /sp claim [tier] — claim current position as plot center
                .then(CommandManager.literal("claim")
                    .executes(ctx -> executeClaim(ctx.getSource(), null))
                    .then(CommandManager.argument("tier", StringArgumentType.string())
                        .suggests((ctx, b) -> suggestTier(b))
                        .executes(ctx -> executeClaim(ctx.getSource(),
                            StringArgumentType.getString(ctx, "tier")))))

                // /sp areas — list available predefined areas
                .then(CommandManager.literal("areas")
                    .executes(ctx -> executeAreasList(ctx.getSource()))
                    .then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests((ctx, b) -> suggestAreas(b))
                        .executes(ctx -> executeAreasInfo(ctx.getSource(),
                            StringArgumentType.getString(ctx, "name")))))

                // /sp claimarea <name> [tier] — claim a predefined area
                .then(CommandManager.literal("claimarea")
                    .then(CommandManager.argument("name", StringArgumentType.word())
                        .suggests((ctx, b) -> suggestAreas(b))
                        .executes(ctx -> executeClaimArea(ctx.getSource(),
                            StringArgumentType.getString(ctx, "name"), null))
                        .then(CommandManager.argument("tier", StringArgumentType.string())
                            .suggests((ctx, b) -> suggestTier(b))
                            .executes(ctx -> executeClaimArea(ctx.getSource(),
                                StringArgumentType.getString(ctx, "name"),
                                StringArgumentType.getString(ctx, "tier"))))))

                // /sp help admin - admin help
                .then(CommandManager.literal("help")
                    .then(CommandManager.literal("admin")
                        .executes(ctx -> executeAdminHelp(ctx.getSource()))))

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
                    // /sp admin savearea <name> [tier] [requiredRank] — save current selection as predefined area
                    .then(CommandManager.literal("savearea")
                        .then(CommandManager.argument("name", StringArgumentType.word())
                            .executes(ctx -> executeAdminSaveArea(ctx.getSource(),
                                StringArgumentType.getString(ctx, "name"), null, null))
                            .then(CommandManager.argument("tier", StringArgumentType.string())
                                .suggests((ctx, b) -> suggestTier(b))
                                .executes(ctx -> executeAdminSaveArea(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "name"),
                                    StringArgumentType.getString(ctx, "tier"), null))
                                .then(CommandManager.argument("requiredRank", StringArgumentType.word())
                                    .executes(ctx -> executeAdminSaveArea(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "name"),
                                        StringArgumentType.getString(ctx, "tier"),
                                        StringArgumentType.getString(ctx, "requiredRank")))))))
                    .then(CommandManager.literal("reload")
                        .executes(ctx -> executeAdminReload(ctx.getSource()))))
                    // /sp admin protectedarea ... — manage protected areas
                    .then(CommandManager.literal("protectedarea")
                        .then(CommandManager.literal("create")
                            .then(CommandManager.argument("name", StringArgumentType.word())
                                .executes(ctx -> executeCreateProtectedArea(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "name")))))
                        .then(CommandManager.literal("remove")
                            .then(CommandManager.argument("name", StringArgumentType.word())
                                .suggests((ctx, b) -> suggestProtectedAreas(b))
                                .executes(ctx -> executeRemoveProtectedArea(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "name")))))
                        .then(CommandManager.literal("list")
                            .executes(ctx -> executeListProtectedAreas(ctx.getSource())))
                        .then(CommandManager.literal("info")
                            .then(CommandManager.argument("name", StringArgumentType.word())
                                .suggests((ctx, b) -> suggestProtectedAreas(b))
                                .executes(ctx -> executeInfoProtectedArea(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "name")))))
                        .then(CommandManager.literal("toggle")
                            .then(CommandManager.argument("name", StringArgumentType.word())
                                .suggests((ctx, b) -> suggestProtectedAreas(b))
                                .executes(ctx -> executeToggleProtectedArea(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "name")))))
                        .then(CommandManager.literal("addowner")
                            .then(CommandManager.argument("area", StringArgumentType.word())
                                .suggests((ctx, b) -> suggestProtectedAreas(b))
                                .then(CommandManager.argument("player", StringArgumentType.word())
                                    .executes(ctx -> executeAddProtectedAreaOwner(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "area"),
                                        StringArgumentType.getString(ctx, "player"))))))
                        .then(CommandManager.literal("removeowner")
                            .then(CommandManager.argument("area", StringArgumentType.word())
                                .suggests((ctx, b) -> suggestProtectedAreas(b))
                                .then(CommandManager.argument("player", StringArgumentType.word())
                                    .executes(ctx -> executeRemoveProtectedAreaOwner(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "area"),
                                        StringArgumentType.getString(ctx, "player"))))))
                        .then(CommandManager.literal("setflags")
                            .then(CommandManager.argument("name", StringArgumentType.word())
                                .suggests((ctx, b) -> suggestProtectedAreas(b))
                                .then(CommandManager.argument("break", BoolArgumentType.bool())
                                    .then(CommandManager.argument("place", BoolArgumentType.bool())
                                        .then(CommandManager.argument("interact", BoolArgumentType.bool())
                                            .executes(ctx -> executeSetProtectedAreaFlags(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name"),
                                                BoolArgumentType.getBool(ctx, "break"),
                                                BoolArgumentType.getBool(ctx, "place"),
                                                BoolArgumentType.getBool(ctx, "interact"))))))))))
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

    private static CompletableFuture<Suggestions> suggestPos(ServerCommandSource source, SuggestionsBuilder builder) {
        builder.suggest("~ ~ ~");
        builder.suggest("^ ^ ^");
        builder.suggest("here");
        if (source.getEntity() != null) {
            BlockPos p = source.getEntity().getBlockPos();
            builder.suggest(p.getX() + " " + p.getY() + " " + p.getZ());
        }
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestTier(SuggestionsBuilder builder) {
        for (String t : new String[]{"bronze", "gold", "emerald", "diamond", "netherite", "0", "1", "2", "3", "4"})
            builder.suggest(t);
        return builder.buildFuture();
    }

    // ── /sp help ──────────────────────────────────────────────────────────────

    private static int executeAdminHelp(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        player.sendMessage(Text.literal("§e§l=== SecurePlots Admin Help ===").formatted(Formatting.BOLD), false);
        player.sendMessage(Text.literal("§7§oManage plots:"), false);
        player.sendMessage(Text.literal("  §b/sp admin listall [page] §7- List all plots on server"), false);
        player.sendMessage(Text.literal("  §b/sp admin search <player> [page] §7- Search player's plots"), false);
        player.sendMessage(Text.literal("  §b/sp admin nearby [count] §7- List nearby plots"), false);
        player.sendMessage(Text.literal("  §b/sp admin info <player> <plot> §7- Show plot info"), false);
        player.sendMessage(Text.literal("  §b/sp admin tp <player> <plot> §7- Teleport to plot"), false);
        player.sendMessage(Text.literal("  §b/sp admin delete <player> <plot> §7- Delete a plot"), false);
        player.sendMessage(Text.literal("  §b/sp admin rename <player> <plot> <name> §7- Rename plot"), false);
        player.sendMessage(Text.literal("  §b/sp admin setowner <newowner> §7- Change plot owner"), false);
        player.sendMessage(Text.literal("  §b/sp admin upgrade <player> <plot> §7- Upgrade plot"), false);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("§7§oManage predefined areas:"), false);
        player.sendMessage(Text.literal("  §b/sp admin savearea <name> [tier] [rank] §7- Save selection as area"), false);
        player.sendMessage(Text.literal("  §b/sp admin setrank <player> <tag> §7- Give player rank tag"), false);
        player.sendMessage(Text.literal("  §b/sp admin removerank <player> <tag> §7- Remove rank tag"), false);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("§7§oManage protected areas:"), false);
        player.sendMessage(Text.literal("  §b/sp admin protectedarea create <name> §7- Create protected area from selection"), false);
        player.sendMessage(Text.literal("  §b/sp admin protectedarea remove <name> §7- Remove protected area"), false);
        player.sendMessage(Text.literal("  §b/sp admin protectedarea list §7- List all protected areas"), false);
        player.sendMessage(Text.literal("  §b/sp admin protectedarea info <name> §7- Show protected area details"), false);
        player.sendMessage(Text.literal("  §b/sp admin protectedarea toggle <name> §7- Enable/disable protected area"), false);
        player.sendMessage(Text.literal("  §b/sp admin protectedarea addowner <area> <player> §7- Add area owner"), false);
        player.sendMessage(Text.literal("  §b/sp admin protectedarea setflags <name> <break> <place> <interact> §7- Set protections"), false);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("§7§oPlot customization (admin):"), false);
        player.sendMessage(Text.literal("  §b/sp admin particle/music/weather/time/enter/exit §7- Customize plot"), false);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("§7§oOther:"), false);
        player.sendMessage(Text.literal("  §b/sp admin reload §7- Reload config"), false);
        return 1;
    }

    private static int executeHelp(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        player.sendMessage(Text.literal("§e§l=== SecurePlots Help ===").formatted(Formatting.BOLD), false);
        player.sendMessage(Text.literal("§7§oClaim/Create plots:"), false);
        player.sendMessage(Text.literal("  §b/sp claim [tier] §7- Claim plot at current position"), false);
        player.sendMessage(Text.literal("  §b/sp pos1 §7- Set first corner of selection"), false);
        player.sendMessage(Text.literal("  §b/sp pos2 §7- Set second corner of selection"), false);
        player.sendMessage(Text.literal("  §b/sp create [tier] §7- Create plot from selection"), false);
        player.sendMessage(Text.literal("  §b/sp areas §7- List available predefined areas"), false);
        player.sendMessage(Text.literal("  §b/sp claimarea <name> [tier] §7- Claim a predefined area"), false);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("§7§oManage your plots:"), false);
        player.sendMessage(Text.literal("  §b/sp list §7- List your plots"), false);
        player.sendMessage(Text.literal("  §b/sp info [plot] §7- Show plot info"), false);
        player.sendMessage(Text.literal("  §b/sp rename <name> §7- Rename your plot"), false);
        player.sendMessage(Text.literal("  §b/sp add <player> <plot|all> §7- Add member"), false);
        player.sendMessage(Text.literal("  §b/sp remove <player> <plot|all> §7- Remove member"), false);
        player.sendMessage(Text.literal("  §b/sp role <player> <member|admin> §7- Set role"), false);
        player.sendMessage(Text.literal("  §b/sp tp [plot] §7- Teleport to plot"), false);
        player.sendMessage(Text.literal("  §b/sp upgrade §7- Upgrade plot tier"), false);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("§7§oFlags & Permissions:"), false);
        player.sendMessage(Text.literal("  §b/sp flag [flag] [value] §7- View/set plot flags"), false);
        player.sendMessage(Text.literal("  §b/sp perm [player] [perm] [value] §7- Set permissions"), false);
        player.sendMessage(Text.literal("  §b/sp group §7- Manage permission groups"), false);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("§7§oPlot customization:"), false);
        player.sendMessage(Text.literal("  §b/sp plot particle <type> §7- Set particle effect"), false);
        player.sendMessage(Text.literal("  §b/sp plot weather <type> §7- Set weather override"), false);
        player.sendMessage(Text.literal("  §b/sp plot time <value> §7- Set time override"), false);
        player.sendMessage(Text.literal("  §b/sp plot music <sound> §7- Set ambient music"), false);
        player.sendMessage(Text.literal("  §b/sp plot enter <message> §7- Set enter message"), false);
        player.sendMessage(Text.literal("  §b/sp plot exit <message> §7- Set exit message"), false);
        player.sendMessage(Text.literal(""), false);
        player.sendMessage(Text.literal("§7For admin commands, use §b/sp help admin"), false);
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

    // ── /sp create — start selection mode ─────────────────────────────────────

    // Public selection maps for use by PlotSelectorItem
    public static final Map<UUID, BlockPos> SELECTION_POS1 = new HashMap<>();
    public static final Map<UUID, BlockPos> SELECTION_POS2 = new HashMap<>();

    private static int executeCreateStart(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        player.sendMessage(Text.literal("§e══ Create Plot — Selection Mode ══").formatted(Formatting.BOLD), false);
        player.sendMessage(Text.literal("§71. Use §b/sp pos1 §7to set the first corner"), false);
        player.sendMessage(Text.literal("§72. Use §b/sp pos2 §7to set the second corner"), false);
        player.sendMessage(Text.literal("§73. Use §b/sp create <tier> §7to finalize"), false);
        player.sendMessage(Text.literal("§7Tip: You can also use §b/sp claim [tier] §7to claim at current position"), false);
        return 1;
    }

    // ── /sp pos1 / pos2 — selection helpers ───────────────────────────────────

    private static int executePos1(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        BlockPos pos = player.getBlockPos();
        SELECTION_POS1.put(player.getUuid(), pos);
        player.sendMessage(Text.literal("§a✓ Position 1 set: §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
        spawnSelectionParticles(player.getWorld(), pos, 10);
        return 1;
    }

    private static int executePos2(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        BlockPos pos1 = SELECTION_POS1.get(player.getUuid());
        if (pos1 == null) {
            player.sendMessage(Text.literal("§c✗ Set position 1 first with §b/sp pos1"), false);
            return 0;
        }
        BlockPos pos2 = player.getBlockPos();
        player.sendMessage(Text.literal("§a✓ Position 2 set: §f" + pos2.getX() + ", " + pos2.getY() + ", " + pos2.getZ()), false);
        player.sendMessage(Text.literal("§eSelection: §f" + pos1.getX() + "," + pos1.getY() + "," + pos1.getZ() + " §7→ §f" + pos2.getX() + "," + pos2.getY() + "," + pos2.getZ()), false);
        spawnSelectionParticles(player.getWorld(), pos2, 10);
        spawnSelectionOutlineParticles(player.getWorld(), pos1, pos2);
        return 1;
    }

    // ── /sp create <tier> — finalize with current selection ───────────────────

    private static int executeCreateWithPositions(CommandContext<ServerCommandSource> ctx, String pos1Str, String pos2Str, String tierStr) {
        ServerCommandSource source = ctx.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        BlockPos pos1 = parsePosition(pos1Str, player);
        BlockPos pos2 = parsePosition(pos2Str, player);
        if (pos1 == null || pos2 == null) {
            player.sendMessage(Text.literal("§c✗ Invalid positions. Use format: x y z or ~ ~ ~"), false);
            return 0;
        }

        return executeCreateFinalize(player, pos1, pos2, tierStr);
    }

    private static int executeCreateWithSelection(ServerPlayerEntity player, String tierStr) {
        BlockPos pos1 = SELECTION_POS1.get(player.getUuid());
        if (pos1 == null) {
            player.sendMessage(Text.literal("§c✗ No selection. Use §b/sp pos1 §7and §b/sp pos2§7, or use §b/sp claim"), false);
            return 0;
        }
        BlockPos pos2 = player.getBlockPos();
        return executeCreateFinalize(player, pos1, pos2, tierStr);
    }

    private static int executeCreateFinalize(ServerPlayerEntity player, BlockPos pos1, BlockPos pos2, String tierStr) {
        PlotManager manager = PlotManager.getOrCreate((ServerWorld) player.getWorld());

        // Calculate center and size from selection
        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        int centerY = (Math.min(pos1.getY(), pos2.getY()) + Math.max(pos1.getY(), pos2.getY())) / 2;
        BlockPos center = new BlockPos(centerX, centerY, centerZ);

        // Calculate required radius
        int radiusX = maxX - minX + 1;
        int radiusZ = maxZ - minZ + 1;
        int requiredRadius = Math.max(radiusX, radiusZ);

        // Find best fitting tier
        PlotSize targetSize = parseTier(player, tierStr);
        if (targetSize == null) return 0;

        // Check if the selected size fits the tier
        if (requiredRadius > targetSize.getRadius()) {
            player.sendMessage(Text.literal("§c✗ Selection too large for " + targetSize.getDisplayName() + " tier (max: " + targetSize.getRadius() + "x" + targetSize.getRadius() + ")"), false);
            player.sendMessage(Text.literal("§7Required: " + requiredRadius + "x" + requiredRadius), false);
            return 0;
        }

        // Check if player can place here
        if (!manager.canPlace(center, targetSize)) {
            player.sendMessage(Text.literal("§c✗ Cannot place plot here — overlaps with another plot"), false);
            return 0;
        }

        // Check structure collision
        if (!canPlaceAtStructure(player, center, targetSize)) {
            player.sendMessage(Text.literal("§c✗ Cannot place plot here — collides with a structure"), false);
            return 0;
        }

        // Create the plot
        long currentTick = player.getWorld().getTime();
        PlotData plot = new PlotData(player.getUuid(), player.getName().getString(), center, targetSize, currentTick);
        manager.addPlot(plot);

        player.sendMessage(Text.literal("§a✓ Plot created: §e\"" + plot.getPlotName() + "\""), false);
        player.sendMessage(Text.literal("§7Center: §f" + center.getX() + ", " + center.getY() + ", " + center.getZ()), false);
        player.sendMessage(Text.literal("§7Size: §f" + targetSize.getRadius() + "x" + targetSize.getRadius() + " (" + targetSize.getDisplayName() + ")"), false);

        // Place the plot block
        ((ServerWorld) player.getWorld()).setBlockState(center, ModBlocks.fromTier(targetSize.tier).getDefaultState());

        SELECTION_POS1.remove(player.getUuid());
        return 1;
    }

    // ── /sp claim [tier] — claim at current position ──────────────────────────

    private static int executeClaim(ServerCommandSource source, String tierStr) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        PlotManager manager = PlotManager.getOrCreate((ServerWorld) player.getWorld());
        BlockPos center = player.getBlockPos();

        // Check if already in a plot
        PlotData existing = manager.getPlotAt(center);
        if (existing != null) {
            player.sendMessage(Text.literal("§c✗ Already inside plot §e\"" + existing.getPlotName() + "\""), false);
            return 0;
        }

        // Default to bronze tier if not specified
        PlotSize size = tierStr != null ? parseTier(player, tierStr) : PlotSize.BRONZE;
        if (size == null) return 0;

        // Check placement
        if (!manager.canPlace(center, size)) {
            player.sendMessage(Text.literal("§c✗ Cannot place plot here — too close to another plot"), false);
            return 0;
        }

        if (!canPlaceAtStructure(player, center, size)) {
            player.sendMessage(Text.literal("§c✗ Cannot place plot here — collides with a structure"), false);
            return 0;
        }

        // Create the plot
        long currentTick = player.getWorld().getTime();
        PlotData plot = new PlotData(player.getUuid(), player.getName().getString(), center, size, currentTick);
        manager.addPlot(plot);

        player.sendMessage(Text.literal("§a✓ Plot claimed: §e\"" + plot.getPlotName() + "\""), false);
        player.sendMessage(Text.literal("§7Center: §f" + center.getX() + ", " + center.getY() + ", " + center.getZ()), false);
        player.sendMessage(Text.literal("§7Size: §f" + size.getRadius() + "x" + size.getRadius() + " (" + size.getDisplayName() + ")"), false);

        // Place the plot block
        ((ServerWorld) player.getWorld()).setBlockState(center, ModBlocks.fromTier(size.tier).getDefaultState());

        return 1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static BlockPos parsePosition(String posStr, ServerPlayerEntity player) {
        if (posStr.equalsIgnoreCase("here")) return player.getBlockPos();
        if (posStr.equals("~ ~ ~")) return player.getBlockPos();

        String[] parts = posStr.split(" ");
        if (parts.length != 3) return null;

        try {
            int x = parseCoord(parts[0], player.getBlockPos().getX());
            int y = parseCoord(parts[1], player.getBlockPos().getY());
            int z = parseCoord(parts[2], player.getBlockPos().getZ());
            return new BlockPos(x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseCoord(String coord, int current) {
        if (coord.equals("~")) return current;
        if (coord.startsWith("~")) return current + Integer.parseInt(coord.substring(1));
        return Integer.parseInt(coord);
    }

    private static PlotSize parseTier(ServerPlayerEntity player, String tierStr) {
        if (tierStr == null) return PlotSize.BRONZE;

        try {
            int tier = Integer.parseInt(tierStr);
            if (tier >= 0 && tier <= 4) return PlotSize.fromTier(tier);
        } catch (NumberFormatException ignored) {}

        try {
            return PlotSize.valueOf(tierStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(Text.literal("§c✗ Unknown tier: §e" + tierStr), false);
            player.sendMessage(Text.literal("§7Valid tiers: bronze(0), gold(1), emerald(2), diamond(3), netherite(4)"), false);
            return null;
        }
    }

    private static boolean canPlaceAtStructure(ServerPlayerEntity player, BlockPos center, PlotSize size) {
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg == null || cfg.blockedStructurePrefixes == null || cfg.blockedStructurePrefixes.isEmpty())
            return true;

        int half = size.getRadius() / 2;
        // Simplified check — just check center chunk for structures
        // Full implementation would scan the entire area
        return true; // TODO: Implement structure check if needed
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

    // ── /sp areas — list available predefined areas ───────────────────────────

    private static int executeAreasList(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg == null || cfg.predefinedAreas == null || cfg.predefinedAreas.isEmpty()) {
            player.sendMessage(Text.literal("§c✗ No predefined areas available.").formatted(Formatting.RED), false);
            return 0;
        }

        List<SecurePlotsConfig.PredefinedArea> available = new ArrayList<>();
        for (SecurePlotsConfig.PredefinedArea area : cfg.predefinedAreas) {
            if (area.available) available.add(area);
        }

        if (available.isEmpty()) {
            player.sendMessage(Text.literal("§c✗ All predefined areas have been claimed.").formatted(Formatting.RED), false);
            return 0;
        }

        player.sendMessage(Text.literal("§e§l=== Available Plot Areas ===").formatted(Formatting.BOLD), false);
        for (SecurePlotsConfig.PredefinedArea area : available) {
            PlotSize size = PlotSize.fromTier(area.tier);
            String reqRank = area.requiredRank != null && !area.requiredRank.isEmpty() ? " §7[Rank: §c" + area.requiredRank + "§7]" : "";
            player.sendMessage(Text.literal("  §a" + area.name + " §8- §f" + size.getDisplayName() + " §7(" + size.getRadius() + "x" + size.getRadius() + ")")
                .append(Text.literal(reqRank)), false);
            player.sendMessage(Text.literal("    §7Center: §f" + area.centerX + ", " + area.centerY + ", " + area.centerZ).formatted(Formatting.GRAY), false);
        }
        player.sendMessage(Text.literal("§eUse §b/sp claimarea <name> [tier] §eto claim an area.").formatted(Formatting.YELLOW), false);
        return 1;
    }

    private static int executeAreasInfo(ServerCommandSource source, String areaName) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg == null || cfg.predefinedAreas == null) {
            player.sendMessage(Text.literal("§c✗ No predefined areas configured.").formatted(Formatting.RED), false);
            return 0;
        }

        SecurePlotsConfig.PredefinedArea target = null;
        for (SecurePlotsConfig.PredefinedArea area : cfg.predefinedAreas) {
            if (area.name.equalsIgnoreCase(areaName)) {
                target = area;
                break;
            }
        }

        if (target == null) {
            player.sendMessage(Text.literal("§c✗ Unknown area: §e" + areaName).formatted(Formatting.RED), false);
            return 0;
        }

        PlotSize size = PlotSize.fromTier(target.tier);
        player.sendMessage(Text.literal("§e§l=== " + target.name + " ===").formatted(Formatting.BOLD), false);
        player.sendMessage(Text.literal("§7Tier: §f" + size.getDisplayName() + " §8(" + size.getRadius() + "x" + size.getRadius() + ")"), false);
        player.sendMessage(Text.literal("§7Center: §f" + target.centerX + ", " + target.centerY + ", " + target.centerZ), false);
        player.sendMessage(Text.literal("§7Status: " + (target.available ? "§aAvailable" : "§cClaimed")), false);
        if (target.requiredRank != null && !target.requiredRank.isEmpty()) {
            player.sendMessage(Text.literal("§7Required Rank: §c" + target.requiredRank), false);
        }
        if (target.oneTimeClaim) {
            player.sendMessage(Text.literal("§7One-time claim: §eYes"), false);
        }
        return 1;
    }

    // ── /sp claimarea <name> [tier] — claim a predefined area ─────────────────

    private static int executeClaimArea(ServerCommandSource source, String areaName, String tierStr) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg == null || cfg.predefinedAreas == null || cfg.predefinedAreas.isEmpty()) {
            player.sendMessage(Text.literal("§c✗ No predefined areas configured.").formatted(Formatting.RED), false);
            return 0;
        }

        SecurePlotsConfig.PredefinedArea target = null;
        for (SecurePlotsConfig.PredefinedArea area : cfg.predefinedAreas) {
            if (area.name.equalsIgnoreCase(areaName)) {
                target = area;
                break;
            }
        }

        if (target == null) {
            player.sendMessage(Text.literal("§c✗ Unknown area: §e" + areaName).formatted(Formatting.RED), false);
            return 0;
        }

        if (!target.available) {
            player.sendMessage(Text.literal("§c✗ This area has already been claimed.").formatted(Formatting.RED), false);
            return 0;
        }

        // Check rank requirement
        if (target.requiredRank != null && !target.requiredRank.isEmpty()) {
            if (!player.getCommandTags().contains(target.requiredRank)) {
                player.sendMessage(Text.literal("§c✗ You need the §e" + target.requiredRank + " §crank to claim this area.").formatted(Formatting.RED), false);
                return 0;
            }
        }

        PlotManager manager = PlotManager.getOrCreate((ServerWorld) player.getWorld());
        BlockPos center = new BlockPos(target.centerX, target.centerY, target.centerZ);

        // Check if already in a plot
        PlotData existing = manager.getPlotAt(center);
        if (existing != null) {
            player.sendMessage(Text.literal("§c✗ This area is already a plot.").formatted(Formatting.RED), false);
            return 0;
        }

        // Determine tier
        PlotSize size = tierStr != null ? parseTier(player, tierStr) : PlotSize.fromTier(target.tier);
        if (size == null) return 0;

        // Use the predefined tier if no tier specified or if tier is higher than allowed
        if (tierStr == null) {
            size = PlotSize.fromTier(target.tier);
        }

        // Check placement
        if (!manager.canPlace(center, size)) {
            player.sendMessage(Text.literal("§c✗ Cannot place plot here — too close to another plot").formatted(Formatting.RED), false);
            return 0;
        }

        // Create the plot
        long currentTick = player.getWorld().getTime();
        PlotData plot = new PlotData(player.getUuid(), player.getName().getString(), center, size, currentTick);
        manager.addPlot(plot);

        // Mark area as claimed if one-time claim
        if (target.oneTimeClaim) {
            target.available = false;
            SecurePlotsConfig.save();
        }

        player.sendMessage(Text.literal("§a✓ Plot claimed: §e\"" + plot.getPlotName() + "\"").formatted(Formatting.GREEN), false);
        player.sendMessage(Text.literal("§7Center: §f" + center.getX() + ", " + center.getY() + ", " + center.getZ()), false);
        player.sendMessage(Text.literal("§7Size: §f" + size.getRadius() + "x" + size.getRadius() + " (" + size.getDisplayName() + ")"), false);

        // Place the plot block
        ((ServerWorld) player.getWorld()).setBlockState(center, ModBlocks.fromTier(size.tier).getDefaultState());

        return 1;
    }

    // ── /sp admin savearea <name> [tier] [requiredRank] ───────────────────────

    private static int executeAdminSaveArea(ServerCommandSource source, String areaName, String tierStr, String requiredRank) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        BlockPos pos1 = SELECTION_POS1.get(player.getUuid());
        BlockPos pos2 = SELECTION_POS2.get(player.getUuid());

        if (pos1 == null || pos2 == null) {
            player.sendMessage(Text.literal("§c✗ Incomplete selection. Set both positions first.").formatted(Formatting.RED), false);
            player.sendMessage(Text.literal("§7Use §b/sp pos1 §7and §b/sp pos2 §7to select the area.").formatted(Formatting.GRAY), false);
            return 0;
        }

        // Calculate center
        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        int centerY = (Math.min(pos1.getY(), pos2.getY()) + Math.max(pos1.getY(), pos2.getY())) / 2;

        // Calculate required radius
        int radiusX = maxX - minX + 1;
        int radiusZ = maxZ - minZ + 1;
        int requiredRadius = Math.max(radiusX, radiusZ);

        // Determine tier
        PlotSize size = tierStr != null ? parseTier(player, tierStr) : null;
        if (size == null) {
            // Auto-detect best tier
            size = getBestFittingTier(requiredRadius);
            if (size == null) {
                player.sendMessage(Text.literal("§c✗ Selection too large for any tier.").formatted(Formatting.RED), false);
                return 0;
            }
        }

        // Check if selection fits the tier
        if (requiredRadius > size.getRadius()) {
            player.sendMessage(Text.literal("§c✗ Selection too large for " + size.getDisplayName() + " tier (max: " + size.getRadius() + "x" + size.getRadius() + ")").formatted(Formatting.RED), false);
            return 0;
        }

        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg == null) {
            player.sendMessage(Text.literal("§c✗ Config not loaded.").formatted(Formatting.RED), false);
            return 0;
        }

        // Check if name already exists
        for (SecurePlotsConfig.PredefinedArea existing : cfg.predefinedAreas) {
            if (existing.name.equalsIgnoreCase(areaName)) {
                player.sendMessage(Text.literal("§c✗ An area named §e" + areaName + " §calready exists.").formatted(Formatting.RED), false);
                return 0;
            }
        }

        // Create new predefined area
        SecurePlotsConfig.PredefinedArea newArea = new SecurePlotsConfig.PredefinedArea(
            areaName, centerX, centerY, centerZ, size.tier
        );
        newArea.requiredRank = requiredRank != null && !requiredRank.isEmpty() ? requiredRank : "";
        newArea.oneTimeClaim = true;
        newArea.available = true;

        cfg.predefinedAreas.add(newArea);
        SecurePlotsConfig.save();

        player.sendMessage(Text.literal("§a✓ Predefined area saved: §e" + areaName).formatted(Formatting.GREEN), false);
        player.sendMessage(Text.literal("§7Center: §f" + centerX + ", " + centerY + ", " + centerZ), false);
        player.sendMessage(Text.literal("§7Tier: §f" + size.getDisplayName() + " §8(" + size.getRadius() + "x" + size.getRadius() + ")"), false);
        if (requiredRank != null && !requiredRank.isEmpty()) {
            player.sendMessage(Text.literal("§7Required Rank: §c" + requiredRank), false);
        }
        player.sendMessage(Text.literal("§ePlayers can now claim this area with §b/sp claimarea " + areaName).formatted(Formatting.YELLOW), false);

        // Clear selection
        SELECTION_POS1.remove(player.getUuid());
        SELECTION_POS2.remove(player.getUuid());

        return 1;
    }

    private static PlotSize getBestFittingTier(int requiredRadius) {
        for (int tier = 0; tier <= 4; tier++) {
            PlotSize size = PlotSize.fromTier(tier);
            if (size.getRadius() >= requiredRadius) {
                return size;
            }
        }
        return null;
    }

    private static CompletableFuture<Suggestions> suggestAreas(SuggestionsBuilder builder) {
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg != null && cfg.predefinedAreas != null) {
            for (SecurePlotsConfig.PredefinedArea area : cfg.predefinedAreas) {
                if (area.available) builder.suggest(area.name);
            }
        }
        return builder.buildFuture();
    }

    // ── Selection particles ────────────────────────────────────────────────────

    /** Spawns particles at a single block position. */
    private static void spawnSelectionParticles(net.minecraft.world.World world, BlockPos pos, int count) {
        if (world instanceof ServerWorld sw) {
            for (int i = 0; i < count; i++) {
                double x = pos.getX() + 0.5 + world.random.nextDouble() * 0.8 - 0.4;
                double y = pos.getY() + 0.5 + world.random.nextDouble() * 0.8 - 0.4;
                double z = pos.getZ() + 0.5 + world.random.nextDouble() * 0.8 - 0.4;
                sw.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0);
            }
        }
    }

    /** Spawns particle outline around the selected area. */
    private static void spawnSelectionOutlineParticles(net.minecraft.world.World world, BlockPos pos1, BlockPos pos2) {
        if (!(world instanceof ServerWorld sw)) return;

        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        // Top and bottom edges
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                spawnParticleAt(sw, x, minY, z);
                spawnParticleAt(sw, x, maxY, z);
            }
        }

        // Vertical edges at corners
        for (int y = minY; y <= maxY; y++) {
            spawnParticleAt(sw, minX, y, minZ);
            spawnParticleAt(sw, minX, y, maxZ);
            spawnParticleAt(sw, maxX, y, minZ);
            spawnParticleAt(sw, maxX, y, maxZ);
        }

        // Horizontal edges along X
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y += Math.max(1, (maxY - minY) / 3)) {
                spawnParticleAt(sw, x, y, minZ);
                spawnParticleAt(sw, x, y, maxZ);
            }
        }

        // Horizontal edges along Z
        for (int z = minZ; z <= maxZ; z++) {
            for (int y = minY; y <= maxY; y += Math.max(1, (maxY - minY) / 3)) {
                spawnParticleAt(sw, minX, y, z);
                spawnParticleAt(sw, maxX, y, z);
            }
        }
    }

    private static void spawnParticleAt(ServerWorld world, int x, int y, int z) {
        world.spawnParticles(ParticleTypes.END_ROD, x + 0.5, y + 0.5, z + 0.5, 1, 0, 0, 0, 0);
    }

    // ══ Protected Area Commands ═══════════════════════════════════════════════

    private static CompletableFuture<Suggestions> suggestProtectedAreas(SuggestionsBuilder builder) {
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg != null && cfg.protectedAreas != null) {
            for (SecurePlotsConfig.ProtectedArea area : cfg.protectedAreas) {
                builder.suggest(area.name);
            }
        }
        return builder.buildFuture();
    }

    // /sp admin protectedarea create <name> — create from current selection
    private static int executeCreateProtectedArea(ServerCommandSource source, String name) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;

        BlockPos pos1 = SELECTION_POS1.get(player.getUuid());
        BlockPos pos2 = SELECTION_POS2.get(player.getUuid());

        if (pos1 == null || pos2 == null) {
            player.sendMessage(Text.literal("§c✗ Incomplete selection. Set both positions first.").formatted(Formatting.RED), false);
            player.sendMessage(Text.literal("§7Use §b/sp pos1 §7and §b/sp pos2 §7to select the area.").formatted(Formatting.GRAY), false);
            return 0;
        }

        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg == null) {
            player.sendMessage(Text.literal("§c✗ Config not loaded.").formatted(Formatting.RED), false);
            return 0;
        }

        // Check if name already exists
        for (SecurePlotsConfig.ProtectedArea existing : cfg.protectedAreas) {
            if (existing.name.equalsIgnoreCase(name)) {
                player.sendMessage(Text.literal("§c✗ A protected area named §e" + name + " §calready exists.").formatted(Formatting.RED), false);
                return 0;
            }
        }

        // Create protected area from selection
        SecurePlotsConfig.ProtectedArea area = new SecurePlotsConfig.ProtectedArea(
            name,
            pos1.getX(), pos1.getY(), pos1.getZ(),
            pos2.getX(), pos2.getY(), pos2.getZ()
        );
        area.dimension = player.getWorld().getRegistryKey().getValue().toString();
        area.allowedPlayers.add(player.getName().getString());

        cfg.protectedAreas.add(area);
        SecurePlotsConfig.save();

        player.sendMessage(Text.literal("§a✓ Protected area created: §e" + name).formatted(Formatting.GREEN), false);
        player.sendMessage(Text.literal("§7Bounds: §f(" + area.x1 + ", " + area.y1 + ", " + area.z1 + ") §7→ §f(" + area.x2 + ", " + area.y2 + ", " + area.z2 + ")").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("§7Dimension: §f" + area.dimension).formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("§eYou are set as an owner. Use §b/sp admin protectedarea addowner §7to add more.").formatted(Formatting.YELLOW), false);

        // Clear selection
        SELECTION_POS1.remove(player.getUuid());
        SELECTION_POS2.remove(player.getUuid());

        return 1;
    }

    // /sp admin protectedarea remove <name>
    private static int executeRemoveProtectedArea(ServerCommandSource source, String name) {
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg == null) return 0;

        boolean removed = cfg.protectedAreas.removeIf(a -> a.name.equalsIgnoreCase(name));
        if (removed) {
            SecurePlotsConfig.save();
            source.sendMessage(Text.literal("§a✓ Protected area removed: §e" + name).formatted(Formatting.GREEN), false);
            return 1;
        } else {
            source.sendMessage(Text.literal("§c✗ Protected area not found: §e" + name).formatted(Formatting.RED), false);
            return 0;
        }
    }

    // /sp admin protectedarea list
    private static int executeListProtectedAreas(ServerCommandSource source) {
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        ServerPlayerEntity player = source.getPlayer();
        if (cfg == null || player == null) return 0;

        if (cfg.protectedAreas.isEmpty()) {
            player.sendMessage(Text.literal("§7No protected areas defined.").formatted(Formatting.GRAY), false);
            return 1;
        }

        player.sendMessage(Text.literal("§e§l=== Protected Areas ===").formatted(Formatting.BOLD), false);
        for (SecurePlotsConfig.ProtectedArea area : cfg.protectedAreas) {
            String status = area.enabled ? "§a[ON]" : "§c[OFF]";
            player.sendMessage(Text.literal("  " + status + " §f" + area.name +
                " §7(" + area.dimension + ") §8[" + area.allowedPlayers.size() + " owners]").formatted(Formatting.GRAY), false);
        }
        player.sendMessage(Text.literal("§7Use §b/sp admin protectedarea info <name> §7for details.").formatted(Formatting.GRAY), false);
        return 1;
    }

    // /sp admin protectedarea info <name>
    private static int executeInfoProtectedArea(ServerCommandSource source, String name) {
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        ServerPlayerEntity player = source.getPlayer();
        if (cfg == null || player == null) return 0;

        SecurePlotsConfig.ProtectedArea area = null;
        for (SecurePlotsConfig.ProtectedArea a : cfg.protectedAreas) {
            if (a.name.equalsIgnoreCase(name)) {
                area = a;
                break;
            }
        }

        if (area == null) {
            player.sendMessage(Text.literal("§c✗ Protected area not found: §e" + name).formatted(Formatting.RED), false);
            return 0;
        }

        player.sendMessage(Text.literal("§e§l=== " + area.name + " ===").formatted(Formatting.BOLD), false);
        player.sendMessage(Text.literal("§7Status: " + (area.enabled ? "§aActive" : "§cDisabled")).formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("§7Dimension: §f" + area.dimension).formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("§7Bounds: §f(" + area.x1 + ", " + area.y1 + ", " + area.z1 + ")").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("§7      §f(" + area.x2 + ", " + area.y2 + ", " + area.z2 + ")").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("§7Protections:").formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("  §8• §7Break: " + (area.protectBreak ? "§aYes" : "§cNo")).formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("  §8• §7Place: " + (area.protectPlace ? "§aYes" : "§cNo")).formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("  §8• §7Interact: " + (area.protectInteract ? "§aYes" : "§cNo")).formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("  §8• §7Containers: " + (area.protectContainers ? "§aYes" : "§cNo")).formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("  §8• §7Require Auth: " + (area.requireAuth ? "§aYes" : "§cNo")).formatted(Formatting.GRAY), false);
        player.sendMessage(Text.literal("§7Owners (" + area.allowedPlayers.size() + "):").formatted(Formatting.GRAY), false);
        for (String owner : area.allowedPlayers) {
            player.sendMessage(Text.literal("  §8• §f" + owner).formatted(Formatting.GRAY), false);
        }
        return 1;
    }

    // /sp admin protectedarea toggle <name>
    private static int executeToggleProtectedArea(ServerCommandSource source, String name) {
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg == null) return 0;

        for (SecurePlotsConfig.ProtectedArea area : cfg.protectedAreas) {
            if (area.name.equalsIgnoreCase(name)) {
                area.enabled = !area.enabled;
                SecurePlotsConfig.save();
                source.sendMessage(Text.literal("§a✓ Protected area '" + name + "' " + (area.enabled ? "enabled" : "disabled")).formatted(Formatting.GREEN), false);
                return 1;
            }
        }

        source.sendMessage(Text.literal("§c✗ Protected area not found: §e" + name).formatted(Formatting.RED), false);
        return 0;
    }

    // /sp admin protectedarea addowner <area> <player>
    private static int executeAddProtectedAreaOwner(ServerCommandSource source, String areaName, String playerName) {
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg == null) return 0;

        for (SecurePlotsConfig.ProtectedArea area : cfg.protectedAreas) {
            if (area.name.equalsIgnoreCase(areaName)) {
                if (area.allowedPlayers.stream().anyMatch(p -> p.equalsIgnoreCase(playerName))) {
                    source.sendMessage(Text.literal("§c✗ §f" + playerName + " §cis already an owner.").formatted(Formatting.RED), false);
                    return 0;
                }
                area.allowedPlayers.add(playerName);
                SecurePlotsConfig.save();
                source.sendMessage(Text.literal("§a✓ Added §f" + playerName + " §aas owner of §e" + areaName).formatted(Formatting.GREEN), false);
                return 1;
            }
        }

        source.sendMessage(Text.literal("§c✗ Protected area not found: §e" + areaName).formatted(Formatting.RED), false);
        return 0;
    }

    // /sp admin protectedarea removeowner <area> <player>
    private static int executeRemoveProtectedAreaOwner(ServerCommandSource source, String areaName, String playerName) {
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg == null) return 0;

        for (SecurePlotsConfig.ProtectedArea area : cfg.protectedAreas) {
            if (area.name.equalsIgnoreCase(areaName)) {
                boolean removed = area.allowedPlayers.removeIf(p -> p.equalsIgnoreCase(playerName));
                if (removed) {
                    SecurePlotsConfig.save();
                    source.sendMessage(Text.literal("§a✓ Removed §f" + playerName + " §afrom §e" + areaName).formatted(Formatting.GREEN), false);
                    return 1;
                } else {
                    source.sendMessage(Text.literal("§c✗ §f" + playerName + " §cis not an owner.").formatted(Formatting.RED), false);
                    return 0;
                }
            }
        }

        source.sendMessage(Text.literal("§c✗ Protected area not found: §e" + areaName).formatted(Formatting.RED), false);
        return 0;
    }

    // /sp admin protectedarea setflags <name> <break> <place> <interact>
    private static int executeSetProtectedAreaFlags(ServerCommandSource source, String name, boolean protectBreak, boolean protectPlace, boolean protectInteract) {
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg == null) return 0;

        for (SecurePlotsConfig.ProtectedArea area : cfg.protectedAreas) {
            if (area.name.equalsIgnoreCase(name)) {
                area.protectBreak = protectBreak;
                area.protectPlace = protectPlace;
                area.protectInteract = protectInteract;
                area.protectContainers = protectInteract; // Containers follow interact by default
                SecurePlotsConfig.save();
                source.sendMessage(Text.literal("§a✓ Updated protections for §e" + name).formatted(Formatting.GREEN), false);
                source.sendMessage(Text.literal("§7Break: " + (protectBreak ? "§aYes" : "§cNo") +
                    " §8| Place: " + (protectPlace ? "§aYes" : "§cNo") +
                    " §8| Interact: " + (protectInteract ? "§aYes" : "§cNo")).formatted(Formatting.GRAY), false);
                return 1;
            }
        }

        source.sendMessage(Text.literal("§c✗ Protected area not found: §e" + name).formatted(Formatting.RED), false);
        return 0;
    }
}
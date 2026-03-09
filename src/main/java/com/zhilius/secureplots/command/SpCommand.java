package com.zhilius.secureplots.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.config.SecurePlotsConfig;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

                // /sp rename <nuevo nombre>
                .then(CommandManager.literal("rename")
                        .then(CommandManager.argument("nombre", StringArgumentType.greedyString())
                                .executes(ctx -> executeRename(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "nombre")))))

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

                // /sp tp [plot]  — teleportar a una plot propia (o ajena si el flag ALLOW_TP está activo)
                .then(CommandManager.literal("tp")
                        .executes(ctx -> executeTp(ctx.getSource(), null))
                        .then(CommandManager.argument("plot", StringArgumentType.greedyString())
                                .executes(ctx -> executeTp(ctx.getSource(),
                                        StringArgumentType.getString(ctx, "plot")))))

                // /sp flag <flag> <true|false> [plot]
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

                // /sp perm <player> <perm> <true|false> [plot]
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

                // /sp fly <on|off> [plot]  — toggle ALLOW_FLY flag + FLY perm para todos los miembros
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
            );
        }
    }

    // ── /sp help ──────────────────────────────────────────────────────────────
    private static int executeHelp(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        player.sendMessage(Text.literal("§e═══════════════════════════════"), false);
        player.sendMessage(Text.literal("  §6§l🛡 Secure Plots — Comandos").formatted(Formatting.BOLD), false);
        player.sendMessage(Text.literal("§e═══════════════════════════════"), false);
        player.sendMessage(Text.literal("§e/sp view §7— Muestra el borde de tu parcela más cercana"), false);
        player.sendMessage(Text.literal("§e/sp list §7— Lista todas tus parcelas"), false);
        player.sendMessage(Text.literal("§e/sp info [parcela] §7— Info de una parcela"), false);
        player.sendMessage(Text.literal("§e/sp rename <nombre> §7— Renombra la parcela donde estás"), false);
        player.sendMessage(Text.literal("§e/sp tp [parcela] §7— Teleportate a una parcela"), false);
        player.sendMessage(Text.literal("§e/sp add <jugador> <parcela|all> §7— Agrega un miembro"), false);
        player.sendMessage(Text.literal("§e/sp remove <jugador> <parcela|all> §7— Quita un miembro"), false);
        player.sendMessage(Text.literal("§e/sp flag §7— Lista los flags disponibles"), false);
        player.sendMessage(Text.literal("§e/sp flag <flag> <true|false> [parcela] §7— Cambia un flag"), false);
        player.sendMessage(Text.literal("§e/sp perm §7— Lista los permisos disponibles"), false);
        player.sendMessage(Text.literal("§e/sp perm <jugador> <perm> <true|false> [parcela] §7— Cambia un permiso"), false);
        player.sendMessage(Text.literal("§e/sp group §7— Lista los grupos de la parcela"), false);
        player.sendMessage(Text.literal("§e/sp group create <nombre> §7— Crea un grupo"), false);
        player.sendMessage(Text.literal("§e/sp group delete <nombre> §7— Elimina un grupo"), false);
        player.sendMessage(Text.literal("§e/sp group addmember <grupo> <jugador> §7— Agrega al grupo"), false);
        player.sendMessage(Text.literal("§e/sp group removemember <grupo> <jugador> §7— Quita del grupo"), false);
        player.sendMessage(Text.literal("§e/sp group setperm <grupo> <perm> <true|false> §7— Perm. de grupo"), false);
        player.sendMessage(Text.literal("§e═══════════════════════════════"), false);
        return 1;
    }

    // ── /sp view ──────────────────────────────────────────────────────────────
    private static int executeView(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotData nearest = getNearestOwnedPlot(player, PlotManager.getOrCreate(player.getServerWorld()));
        if (nearest == null) {
            player.sendMessage(Text.literal("✗ No tenés protecciones.").formatted(Formatting.RED), false);
            return 0;
        }
        ModPackets.sendShowPlotBorder(player, nearest);
        player.sendMessage(
            Text.literal("Mostrando borde de: ").formatted(Formatting.GRAY)
                .append(Text.literal(nearest.getPlotName()).formatted(Formatting.YELLOW))
                .append(Text.literal(" (" + nearest.getSize().getRadius() + "x" + nearest.getSize().getRadius() + ")").formatted(Formatting.AQUA)),
            false);
        return 1;
    }

    // ── /sp list ──────────────────────────────────────────────────────────────
    private static int executeList(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        List<PlotData> plots = PlotManager.getOrCreate(player.getServerWorld()).getPlayerPlots(player.getUuid());
        if (plots.isEmpty()) {
            player.sendMessage(Text.literal("✗ No tenés protecciones colocadas.").formatted(Formatting.RED), false);
            return 0;
        }
        player.sendMessage(Text.literal("═══════════════════════════").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("  🛡 Tus protecciones (" + plots.size() + ")").formatted(Formatting.YELLOW, Formatting.BOLD), false);
        player.sendMessage(Text.literal("═══════════════════════════").formatted(Formatting.GOLD), false);
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
        player.sendMessage(Text.literal("═══════════════════════════").formatted(Formatting.GOLD), false);
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
                player.sendMessage(Text.literal("✗ No estás dentro de ninguna protección.").formatted(Formatting.RED), false);
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
        player.sendMessage(Text.literal("═══════════════════════════").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("  🛡 " + plot.getPlotName()).formatted(Formatting.YELLOW, Formatting.BOLD), false);
        player.sendMessage(Text.literal("═══════════════════════════").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("  Dueño: ").formatted(Formatting.GRAY).append(Text.literal(plot.getOwnerName()).formatted(Formatting.WHITE)), false);
        player.sendMessage(Text.literal("  Nivel: ").formatted(Formatting.GRAY).append(Text.literal(plot.getSize().getDisplayName()).formatted(Formatting.AQUA)), false);
        player.sendMessage(Text.literal("  Tamaño: ").formatted(Formatting.GRAY).append(Text.literal(sz + "x" + sz + " bloques").formatted(Formatting.AQUA)), false);
        player.sendMessage(Text.literal("  Coords: ").formatted(Formatting.GRAY).append(Text.literal(c.getX() + ", " + c.getY() + ", " + c.getZ()).formatted(Formatting.WHITE)), false);

        // Flags activas
        if (!plot.getFlags().isEmpty()) {
            StringBuilder flagStr = new StringBuilder();
            for (PlotData.Flag f : plot.getFlags()) {
                if (flagStr.length() > 0) flagStr.append(", ");
                flagStr.append(f.name().toLowerCase());
            }
            player.sendMessage(Text.literal("  Flags: ").formatted(Formatting.GRAY)
                .append(Text.literal(flagStr.toString()).formatted(Formatting.GREEN)), false);
        }

        // Grupos
        if (!plot.getGroups().isEmpty()) {
            player.sendMessage(Text.literal("  Grupos (" + plot.getGroups().size() + "):").formatted(Formatting.GRAY), false);
            for (PlotData.PermissionGroup g : plot.getGroups()) {
                player.sendMessage(Text.literal("    • §d" + g.name + " §8(" + g.members.size() + " miembros, " + g.permissions.size() + " permisos)"), false);
            }
        }

        // Miembros
        Map<UUID, PlotData.Role> members = plot.getMembers();
        if (members.isEmpty()) {
            player.sendMessage(Text.literal("  Miembros: ").formatted(Formatting.GRAY).append(Text.literal("Ninguno").formatted(Formatting.DARK_GRAY)), false);
        } else {
            player.sendMessage(Text.literal("  Miembros (" + members.size() + "):").formatted(Formatting.GRAY), false);
            for (Map.Entry<UUID, PlotData.Role> entry : members.entrySet()) {
                player.sendMessage(
                    Text.literal("    • ").formatted(Formatting.DARK_GRAY)
                        .append(Text.literal(plot.getMemberName(entry.getKey())).formatted(Formatting.WHITE))
                        .append(Text.literal(" [" + entry.getValue().name().toLowerCase() + "]").formatted(Formatting.GRAY)),
                    false);
            }
        }
        player.sendMessage(Text.literal("═══════════════════════════").formatted(Formatting.GOLD), false);
        return 1;
    }

    // ── /sp rename ────────────────────────────────────────────────────────────
    private static int executeRename(ServerCommandSource source, String newName) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) {
            player.sendMessage(Text.literal("✗ Tenés que estar dentro de tu protección para renombrarla.").formatted(Formatting.RED), false);
            return 0;
        }
        if (!plot.getOwnerId().equals(player.getUuid())) {
            player.sendMessage(Text.literal("✗ Solo el dueño puede renombrar.").formatted(Formatting.RED), false);
            return 0;
        }
        newName = newName.trim();
        if (newName.isEmpty() || newName.length() > 32) {
            player.sendMessage(Text.literal("✗ Nombre inválido (1-32 caracteres).").formatted(Formatting.RED), false);
            return 0;
        }
        for (PlotData p : manager.getPlayerPlots(player.getUuid())) {
            if (p != plot && p.getPlotName().equalsIgnoreCase(newName)) {
                player.sendMessage(Text.literal("✗ Ya tenés una protección con ese nombre.").formatted(Formatting.RED), false);
                return 0;
            }
        }
        String old = plot.getPlotName();
        plot.setPlotName(newName);
        manager.markDirty();
        player.sendMessage(
            Text.literal("✔ ").formatted(Formatting.GREEN)
                .append(Text.literal("\"" + old + "\"").formatted(Formatting.GRAY))
                .append(Text.literal(" → ").formatted(Formatting.GREEN))
                .append(Text.literal("\"" + newName + "\"").formatted(Formatting.YELLOW)),
            false);
        return 1;
    }

    // ── /sp add ───────────────────────────────────────────────────────────────
    private static int executeAdd(ServerCommandSource source, String targetName, String plotArg) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        List<PlotData> owned = manager.getPlayerPlots(player.getUuid());
        if (owned.isEmpty()) {
            player.sendMessage(Text.literal("✗ No tenés protecciones.").formatted(Formatting.RED), false);
            return 0;
        }
        ServerPlayerEntity target = source.getServer().getPlayerManager().getPlayer(targetName);
        if (target == null) {
            player.sendMessage(Text.literal("✗ Jugador \"" + targetName + "\" no encontrado o no está en línea.").formatted(Formatting.RED), false);
            return 0;
        }
        if (target.getUuid().equals(player.getUuid())) {
            player.sendMessage(Text.literal("✗ No podés agregarte a vos mismo.").formatted(Formatting.RED), false);
            return 0;
        }
        List<PlotData> targets = resolvePlots(owned, plotArg, player);
        if (targets == null) return 0;

        int added = 0;
        for (PlotData plot : targets) {
            if (plot.getRoleOf(target.getUuid()) == PlotData.Role.VISITOR) {
                plot.addMember(target.getUuid(), target.getName().getString(), PlotData.Role.MEMBER);
                added++;
            }
        }
        if (added == 0) {
            player.sendMessage(Text.literal("✗ " + targetName + " ya tiene acceso.").formatted(Formatting.YELLOW), false);
            return 0;
        }
        manager.markDirty();
        String desc = targets.size() == 1 ? "\"" + targets.get(0).getPlotName() + "\"" : targets.size() + " protecciones";
        player.sendMessage(Text.literal("✔ ").formatted(Formatting.GREEN)
                .append(Text.literal(targetName).formatted(Formatting.WHITE))
                .append(Text.literal(" agregado a " + desc).formatted(Formatting.GREEN)), false);
        target.sendMessage(Text.literal("✔ Fuiste agregado a " + desc + " de " + player.getName().getString()).formatted(Formatting.GREEN), false);
        return 1;
    }

    // ── /sp remove ────────────────────────────────────────────────────────────
    private static int executeRemove(ServerCommandSource source, String targetName, String plotArg) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        List<PlotData> owned = manager.getPlayerPlots(player.getUuid());
        if (owned.isEmpty()) {
            player.sendMessage(Text.literal("✗ No tenés protecciones.").formatted(Formatting.RED), false);
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
            player.sendMessage(Text.literal("✗ " + targetName + " no es miembro.").formatted(Formatting.RED), false);
            return 0;
        }
        int removed = 0;
        for (PlotData plot : targets) {
            if (plot.getMembers().containsKey(targetUuid)) { plot.removeMember(targetUuid); removed++; }
        }
        manager.markDirty();
        String desc = targets.size() == 1 ? "\"" + targets.get(0).getPlotName() + "\"" : targets.size() + " protecciones";
        player.sendMessage(Text.literal("✔ ").formatted(Formatting.GREEN)
                .append(Text.literal(targetName).formatted(Formatting.WHITE))
                .append(Text.literal(" eliminado de " + desc).formatted(Formatting.GREEN)), false);
        ServerPlayerEntity online = source.getServer().getPlayerManager().getPlayer(targetUuid);
        if (online != null) online.sendMessage(Text.literal("✗ Te quitaron el acceso a " + desc + " de " + player.getName().getString()).formatted(Formatting.RED), false);
        return 1;
    }

    // ── /sp tp [plot] ─────────────────────────────────────────────────────────
    private static int executeTp(ServerCommandSource source, String plotArg) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());

        PlotData plot = null;
        if (plotArg == null) {
            // La más cercana que sea propia o tenga TP público
            List<PlotData> owned = manager.getPlayerPlots(player.getUuid());
            if (!owned.isEmpty()) plot = owned.get(0);
        } else {
            // Buscar por nombre entre todas las plots (propia o ajena si TP activo)
            List<PlotData> owned = manager.getPlayerPlots(player.getUuid());
            List<PlotData> resolved = resolvePlots(owned, plotArg, player);
            if (resolved != null && !resolved.isEmpty()) {
                plot = resolved.get(0);
            } else {
                // Buscar también en plots ajenas con ALLOW_TP
                for (PlotData p : manager.getAllPlots()) {
                    if (p.getPlotName().equalsIgnoreCase(plotArg) && p.hasFlag(PlotData.Flag.ALLOW_TP)) {
                        plot = p; break;
                    }
                }
            }
        }

        if (plot == null) {
            player.sendMessage(Text.literal("✗ No encontré ninguna parcela accesible con ese nombre.").formatted(Formatting.RED), false);
            return 0;
        }

        boolean isOwner = plot.getOwnerId().equals(player.getUuid());
        boolean isMember = plot.getRoleOf(player.getUuid()) != PlotData.Role.VISITOR;
        boolean tpPublic = plot.hasFlag(PlotData.Flag.ALLOW_TP);
        boolean isAdmin = player.getCommandTags().contains(SecurePlotsConfig.INSTANCE != null ? SecurePlotsConfig.INSTANCE.adminTag : "plot_admin");

        if (!isOwner && !isMember && !tpPublic && !isAdmin) {
            player.sendMessage(Text.literal("✗ El TP no está habilitado en \"" + plot.getPlotName() + "\".").formatted(Formatting.RED), false);
            return 0;
        }

        if (!(player.getWorld() instanceof ServerWorld sw)) return 0;
        BlockPos c = plot.getCenter();
        double tpY = sw.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, new BlockPos(c.getX(), c.getY(), c.getZ())).getY();
        player.teleport(sw, c.getX() + 0.5, tpY, c.getZ() + 0.5, java.util.Set.of(), player.getYaw(), player.getPitch());
        player.sendMessage(Text.literal("§a✔ Teleportado a §e" + plot.getPlotName()), false);
        sw.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
        return 1;
    }

    // ── /sp flag ──────────────────────────────────────────────────────────────
    private static int executeFlagList(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        player.sendMessage(Text.literal("§e═══ Flags disponibles ═══"), false);
        for (PlotData.Flag f : PlotData.Flag.values()) {
            player.sendMessage(Text.literal("  §7" + f.name().toLowerCase()), false);
        }
        player.sendMessage(Text.literal("§7Uso: §e/sp flag <flag> <true|false> [parcela]"), false);
        return 1;
    }

    private static int executeFlagInfo(ServerCommandSource source, String flagName) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotData.Flag flag = parseFlag(player, flagName);
        if (flag == null) return 0;
        PlotData plot = PlotManager.getOrCreate(player.getServerWorld()).getPlotAt(player.getBlockPos());
        if (plot == null) {
            player.sendMessage(Text.literal("✗ No estás dentro de ninguna parcela.").formatted(Formatting.RED), false);
            return 0;
        }
        boolean on = plot.hasFlag(flag);
        player.sendMessage(Text.literal("§eFlag §f" + flag.name().toLowerCase() + " §een §e\"" + plot.getPlotName() + "\"§e: " + (on ? "§a[ON]" : "§c[OFF]")), false);
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

        if (!plot.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_FLAGS)
                && !player.getCommandTags().contains(SecurePlotsConfig.INSTANCE != null ? SecurePlotsConfig.INSTANCE.adminTag : "plot_admin")) {
            player.sendMessage(Text.literal("✗ No tenés permiso para cambiar flags.").formatted(Formatting.RED), false);
            return 0;
        }

        plot.setFlag(flag, value);
        manager.markDirty();
        player.sendMessage(Text.literal("§a✔ Flag §f" + flag.name().toLowerCase() + " §a" + (value ? "activada" : "desactivada") + " en §e\"" + plot.getPlotName() + "\""), false);
        return 1;
    }

    // ── /sp perm ──────────────────────────────────────────────────────────────
    private static int executePermList(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        player.sendMessage(Text.literal("§e═══ Permisos disponibles ═══"), false);
        for (PlotData.Permission p : PlotData.Permission.values()) {
            player.sendMessage(Text.literal("  §7" + p.name().toLowerCase()), false);
        }
        player.sendMessage(Text.literal("§7Uso: §e/sp perm <jugador> <permiso> <true|false> [parcela]"), false);
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
            player.sendMessage(Text.literal("✗ No estás dentro de ninguna parcela.").formatted(Formatting.RED), false);
            return 0;
        }

        UUID targetUuid = findMemberUuid(plot, targetName);
        if (targetUuid == null) {
            player.sendMessage(Text.literal("✗ \"" + targetName + "\" no es miembro de esta parcela.").formatted(Formatting.RED), false);
            return 0;
        }
        boolean has = plot.hasPermission(targetUuid, perm);
        player.sendMessage(Text.literal("§e" + targetName + " §7tiene §f" + perm.name().toLowerCase() + "§7: " + (has ? "§a✔" : "§c✗")), false);
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

        if (!plot.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_PERMS)
                && !player.getCommandTags().contains(SecurePlotsConfig.INSTANCE != null ? SecurePlotsConfig.INSTANCE.adminTag : "plot_admin")) {
            player.sendMessage(Text.literal("✗ No tenés permiso para cambiar permisos.").formatted(Formatting.RED), false);
            return 0;
        }

        UUID targetUuid = findMemberUuid(plot, targetName);
        if (targetUuid == null) {
            player.sendMessage(Text.literal("✗ \"" + targetName + "\" no es miembro de \"" + plot.getPlotName() + "\".").formatted(Formatting.RED), false);
            return 0;
        }

        plot.setPermission(targetUuid, perm, value);
        manager.markDirty();
        player.sendMessage(Text.literal("§a✔ Permiso §f" + perm.name().toLowerCase() + " §a" + (value ? "activado" : "desactivado") + " para §e" + targetName), false);
        return 1;
    }

    // ── /sp fly [true|false] [plot] ───────────────────────────────────────────
    // Shortcut para toggle del flag ALLOW_FLY + permiso FLY para todos los miembros.
    // Solo jugadores con el tag "plot_admin" o dueño/admin de la plot pueden usarlo.
    private static int executeFlyToggle(ServerCommandSource source, Boolean value) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) {
            player.sendMessage(Text.literal("✗ No estás dentro de ninguna parcela.").formatted(Formatting.RED), false);
            return 0;
        }
        if (!plot.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_FLAGS)
                && !player.getCommandTags().contains(SecurePlotsConfig.INSTANCE != null ? SecurePlotsConfig.INSTANCE.adminTag : "plot_admin")) {
            player.sendMessage(Text.literal("✗ No tenés permiso para cambiar el fly de esta parcela.").formatted(Formatting.RED), false);
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
        if (!plot.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_FLAGS)
                && !player.getCommandTags().contains(SecurePlotsConfig.INSTANCE != null ? SecurePlotsConfig.INSTANCE.adminTag : "plot_admin")) {
            player.sendMessage(Text.literal("✗ No tenés permiso para cambiar el fly de esta parcela.").formatted(Formatting.RED), false);
            return 0;
        }
        return applyFlyChange(player, manager, plot, value);
    }

    private static int applyFlyChange(ServerPlayerEntity player, PlotManager manager, PlotData plot, boolean enable) {
        // Toggle flag global ALLOW_FLY
        plot.setFlag(PlotData.Flag.ALLOW_FLY, enable);
        // También activar/desactivar el permiso FLY para todos los miembros existentes
        for (java.util.UUID uuid : plot.getMembers().keySet()) {
            plot.setPermission(uuid, PlotData.Permission.FLY, enable);
        }
        manager.markDirty();
        String status = enable ? "§aactivado" : "§cdesactivado";
        player.sendMessage(Text.literal("§a✔ Fly §f" + status + " §aen §e\"" + plot.getPlotName() + "\""), false);
        player.sendMessage(Text.literal("§7El flag global y el permiso de todos los miembros fue actualizado."), false);
        return 1;
    }

    // ── /sp group ─────────────────────────────────────────────────────────────
    private static int executeGroupList(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) {
            player.sendMessage(Text.literal("✗ No estás dentro de ninguna parcela.").formatted(Formatting.RED), false);
            return 0;
        }
        if (plot.getGroups().isEmpty()) {
            player.sendMessage(Text.literal("§7Esta parcela no tiene grupos."), false);
        } else {
            player.sendMessage(Text.literal("§e═══ Grupos de \"" + plot.getPlotName() + "\" ═══"), false);
            for (PlotData.PermissionGroup g : plot.getGroups()) {
                player.sendMessage(Text.literal("  §d" + g.name + " §8— " + g.members.size() + " miembros, " + g.permissions.size() + " permisos"), false);
            }
        }
        player.sendMessage(Text.literal("§7Uso: §e/sp group <create|delete|addmember|removemember|setperm>"), false);
        return 1;
    }

    private static int executeGroupCreate(ServerCommandSource source, String groupName) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) {
            player.sendMessage(Text.literal("✗ No estás dentro de ninguna parcela.").formatted(Formatting.RED), false);
            return 0;
        }
        if (!plot.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS)
                && !player.getCommandTags().contains(SecurePlotsConfig.INSTANCE != null ? SecurePlotsConfig.INSTANCE.adminTag : "plot_admin")) {
            player.sendMessage(Text.literal("✗ No tenés permiso para gestionar grupos.").formatted(Formatting.RED), false);
            return 0;
        }
        if (plot.getGroup(groupName) != null) {
            player.sendMessage(Text.literal("✗ Ya existe un grupo con ese nombre.").formatted(Formatting.YELLOW), false);
            return 0;
        }
        plot.getOrCreateGroup(groupName);
        manager.markDirty();
        player.sendMessage(Text.literal("§a✔ Grupo §d\"" + groupName + "\" §acreado."), false);
        return 1;
    }

    private static int executeGroupDelete(ServerCommandSource source, String groupName) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) {
            player.sendMessage(Text.literal("✗ No estás dentro de ninguna parcela.").formatted(Formatting.RED), false);
            return 0;
        }
        if (!plot.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS)
                && !player.getCommandTags().contains(SecurePlotsConfig.INSTANCE != null ? SecurePlotsConfig.INSTANCE.adminTag : "plot_admin")) {
            player.sendMessage(Text.literal("✗ No tenés permiso para gestionar grupos.").formatted(Formatting.RED), false);
            return 0;
        }
        if (!plot.removeGroup(groupName)) {
            player.sendMessage(Text.literal("✗ Grupo \"" + groupName + "\" no encontrado.").formatted(Formatting.RED), false);
            return 0;
        }
        manager.markDirty();
        player.sendMessage(Text.literal("§a✔ Grupo §d\"" + groupName + "\" §celiminado."), false);
        return 1;
    }

    private static int executeGroupAddMember(ServerCommandSource source, String groupName, String targetName) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) {
            player.sendMessage(Text.literal("✗ No estás dentro de ninguna parcela.").formatted(Formatting.RED), false);
            return 0;
        }
        PlotData.PermissionGroup group = plot.getGroup(groupName);
        if (group == null) {
            player.sendMessage(Text.literal("✗ Grupo \"" + groupName + "\" no encontrado.").formatted(Formatting.RED), false);
            return 0;
        }
        UUID targetUuid = findMemberUuid(plot, targetName);
        if (targetUuid == null) {
            player.sendMessage(Text.literal("✗ \"" + targetName + "\" no es miembro de esta parcela.").formatted(Formatting.RED), false);
            return 0;
        }
        if (group.members.contains(targetUuid)) {
            player.sendMessage(Text.literal("✗ " + targetName + " ya está en ese grupo.").formatted(Formatting.YELLOW), false);
            return 0;
        }
        group.members.add(targetUuid);
        manager.markDirty();
        player.sendMessage(Text.literal("§a✔ " + targetName + " agregado al grupo §d\"" + groupName + "\""), false);
        return 1;
    }

    private static int executeGroupRemoveMember(ServerCommandSource source, String groupName, String targetName) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());
        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) {
            player.sendMessage(Text.literal("✗ No estás dentro de ninguna parcela.").formatted(Formatting.RED), false);
            return 0;
        }
        PlotData.PermissionGroup group = plot.getGroup(groupName);
        if (group == null) {
            player.sendMessage(Text.literal("✗ Grupo \"" + groupName + "\" no encontrado.").formatted(Formatting.RED), false);
            return 0;
        }
        UUID targetUuid = findMemberUuid(plot, targetName);
        if (targetUuid == null || !group.members.remove(targetUuid)) {
            player.sendMessage(Text.literal("✗ " + targetName + " no está en ese grupo.").formatted(Formatting.YELLOW), false);
            return 0;
        }
        manager.markDirty();
        player.sendMessage(Text.literal("§a✔ " + targetName + " quitado del grupo §d\"" + groupName + "\""), false);
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
            player.sendMessage(Text.literal("✗ No estás dentro de ninguna parcela.").formatted(Formatting.RED), false);
            return 0;
        }
        if (!plot.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS)
                && !player.getCommandTags().contains(SecurePlotsConfig.INSTANCE != null ? SecurePlotsConfig.INSTANCE.adminTag : "plot_admin")) {
            player.sendMessage(Text.literal("✗ No tenés permiso para gestionar grupos.").formatted(Formatting.RED), false);
            return 0;
        }
        PlotData.PermissionGroup group = plot.getGroup(groupName);
        if (group == null) {
            player.sendMessage(Text.literal("✗ Grupo \"" + groupName + "\" no encontrado.").formatted(Formatting.RED), false);
            return 0;
        }
        if (value) group.permissions.add(perm); else group.permissions.remove(perm);
        manager.markDirty();
        player.sendMessage(Text.literal("§a✔ Permiso §f" + perm.name().toLowerCase() + " §a" + (value ? "activado" : "desactivado") + " en grupo §d\"" + groupName + "\""), false);
        return 1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private static PlotData resolveSinglePlot(ServerPlayerEntity player, PlotManager manager, String plotArg) {
        if (plotArg == null) {
            PlotData plot = manager.getPlotAt(player.getBlockPos());
            if (plot == null) {
                player.sendMessage(Text.literal("✗ No estás dentro de ninguna parcela. Especificá una con el argumento [parcela].").formatted(Formatting.RED), false);
                return null;
            }
            // Debe ser owner o admin
            if (!plot.getOwnerId().equals(player.getUuid())
                    && plot.getRoleOf(player.getUuid()) != PlotData.Role.ADMIN
                    && !player.getCommandTags().contains(SecurePlotsConfig.INSTANCE != null ? SecurePlotsConfig.INSTANCE.adminTag : "plot_admin")) {
                player.sendMessage(Text.literal("✗ Solo el dueño o admin puede hacer esto.").formatted(Formatting.RED), false);
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
                player.sendMessage(Text.literal("✗ Número inválido. Tenés " + owned.size() + " protección(es) (1-" + owned.size() + ").").formatted(Formatting.RED), false);
                return null;
            }
            return List.of(owned.get(index - 1));
        } catch (NumberFormatException ignored) {}
        for (PlotData p : owned) {
            if (p.getPlotName().equalsIgnoreCase(plotArg)) return List.of(p);
        }
        player.sendMessage(
            Text.literal("✗ No encontré una protección con nombre o número \"" + plotArg + "\". Usá ").formatted(Formatting.RED)
                .append(Text.literal("/sp list").formatted(Formatting.YELLOW))
                .append(Text.literal(" para ver tus protecciones.").formatted(Formatting.RED)),
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
        try {
            return PlotData.Flag.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(Text.literal("✗ Flag desconocida: \"" + name + "\". Usá §e/sp flag §7para ver la lista.").formatted(Formatting.RED), false);
            return null;
        }
    }

    private static PlotData.Permission parsePerm(ServerPlayerEntity player, String name) {
        try {
            return PlotData.Permission.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(Text.literal("✗ Permiso desconocido: \"" + name + "\". Usá §e/sp perm §7para ver la lista.").formatted(Formatting.RED), false);
            return null;
        }
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

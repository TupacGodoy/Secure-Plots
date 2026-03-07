package com.zhilius.secureplots.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

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

                // /sp clearholos — elimina todos los holograms renderizados
                .then(CommandManager.literal("clearholos")
                        .executes(ctx -> executeClearHolos(ctx.getSource())))
                // /sp testholo — spawna un TextDisplay de prueba donde estás parado
                .then(CommandManager.literal("testholo")
                        .executes(ctx -> executeTestHolo(ctx.getSource())))
            );
        }
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
                .append(Text.literal(" (" + nearest.getSize().radius + "x" + nearest.getSize().radius + ")").formatted(Formatting.AQUA)),
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
            player.sendMessage(
                Text.literal("  " + (i + 1) + ". ").formatted(Formatting.GRAY)
                    .append(Text.literal(p.getPlotName()).formatted(Formatting.WHITE))
                    .append(Text.literal(" [" + p.getSize().displayName + "]").formatted(getTierFormatting(p.getSize().tier)))
                    .append(Text.literal("  " + c.getX() + ", " + c.getY() + ", " + c.getZ()).formatted(Formatting.DARK_GRAY)),
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
            // Sin argumento: la protección donde está parado
            plot = manager.getPlotAt(player.getBlockPos());
            if (plot == null) {
                player.sendMessage(Text.literal("✗ No estás dentro de ninguna protección. Usá /sp info <nombre o número>.").formatted(Formatting.RED), false);
                return 0;
            }
        } else {
            List<PlotData> owned = manager.getPlayerPlots(player.getUuid());
            List<PlotData> resolved = resolvePlots(owned, plotArg, player);
            if (resolved == null || resolved.isEmpty()) return 0;
            plot = resolved.get(0);
        }

        BlockPos c = plot.getCenter();
        int size = plot.getSize().radius;
        player.sendMessage(Text.literal("═══════════════════════════").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("  🛡 " + plot.getPlotName()).formatted(Formatting.YELLOW, Formatting.BOLD), false);
        player.sendMessage(Text.literal("═══════════════════════════").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("  Dueño: ").formatted(Formatting.GRAY).append(Text.literal(plot.getOwnerName()).formatted(Formatting.WHITE)), false);
        player.sendMessage(Text.literal("  Nivel: ").formatted(Formatting.GRAY).append(Text.literal(plot.getSize().displayName).formatted(Formatting.AQUA)), false);
        player.sendMessage(Text.literal("  Tamaño: ").formatted(Formatting.GRAY).append(Text.literal(size + "x" + size + " bloques").formatted(Formatting.AQUA)), false);
        player.sendMessage(Text.literal("  Coords: ").formatted(Formatting.GRAY).append(Text.literal(c.getX() + ", " + c.getY() + ", " + c.getZ()).formatted(Formatting.WHITE)), false);
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

    // ── /sp rename <nombre> (debe estar parado dentro de la plot) ─────────────
    private static int executeRename(ServerCommandSource source, String newName) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotManager manager = PlotManager.getOrCreate(player.getServerWorld());

        PlotData plot = manager.getPlotAt(player.getBlockPos());
        if (plot == null) {
            player.sendMessage(Text.literal("✗ Tenés que estar parado dentro de tu protección para renombrarla.").formatted(Formatting.RED), false);
            return 0;
        }
        if (!plot.getOwnerId().equals(player.getUuid())) {
            player.sendMessage(Text.literal("✗ Solo el dueño puede renombrar la protección.").formatted(Formatting.RED), false);
            return 0;
        }
        newName = newName.trim();
        if (newName.isEmpty()) {
            player.sendMessage(Text.literal("✗ El nombre no puede estar vacío.").formatted(Formatting.RED), false);
            return 0;
        }
        if (newName.length() > 32) {
            player.sendMessage(Text.literal("✗ El nombre no puede tener más de 32 caracteres.").formatted(Formatting.RED), false);
            return 0;
        }
        for (PlotData p : manager.getPlayerPlots(player.getUuid())) {
            if (p != plot && p.getPlotName().equalsIgnoreCase(newName)) {
                player.sendMessage(Text.literal("✗ Ya tenés una protección con el nombre \"" + newName + "\".").formatted(Formatting.RED), false);
                return 0;
            }
        }
        String oldName = plot.getPlotName();
        plot.setPlotName(newName);
        manager.markDirty();
        player.sendMessage(
            Text.literal("✔ ").formatted(Formatting.GREEN)
                .append(Text.literal("\"" + oldName + "\"").formatted(Formatting.GRAY))
                .append(Text.literal(" → ").formatted(Formatting.GREEN))
                .append(Text.literal("\"" + newName + "\"").formatted(Formatting.YELLOW)),
            false);
        return 1;
    }

    // ── /sp add <player> <plot|all> ───────────────────────────────────────────
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
            player.sendMessage(Text.literal("✗ " + targetName + " ya tiene acceso en esa(s) protección(es).").formatted(Formatting.YELLOW), false);
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

    // ── /sp remove <player> <plot|all> ───────────────────────────────────────
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
            for (Map.Entry<UUID, PlotData.Role> entry : plot.getMembers().entrySet()) {
                if (plot.getMemberName(entry.getKey()).equalsIgnoreCase(targetName)) {
                    targetUuid = entry.getKey();
                    break;
                }
            }
            if (targetUuid != null) break;
        }
        if (targetUuid == null) {
            player.sendMessage(Text.literal("✗ " + targetName + " no es miembro de esa(s) protección(es).").formatted(Formatting.RED), false);
            return 0;
        }
        int removed = 0;
        for (PlotData plot : targets) {
            if (plot.getMembers().containsKey(targetUuid)) {
                plot.removeMember(targetUuid);
                removed++;
            }
        }
        manager.markDirty();
        String desc = targets.size() == 1 ? "\"" + targets.get(0).getPlotName() + "\"" : targets.size() + " protecciones";
        player.sendMessage(Text.literal("✔ ").formatted(Formatting.GREEN)
                .append(Text.literal(targetName).formatted(Formatting.WHITE))
                .append(Text.literal(" eliminado de " + desc).formatted(Formatting.GREEN)), false);
        ServerPlayerEntity online = source.getServer().getPlayerManager().getPlayer(targetUuid);
        if (online != null) {
            online.sendMessage(Text.literal("✗ Te quitaron el acceso a " + desc + " de " + player.getName().getString()).formatted(Formatting.RED), false);
        }
        return 1;
    }

    // ── /sp clearholos ────────────────────────────────────────────────────────
    private static int executeClearHolos(ServerCommandSource source) {
        com.zhilius.secureplots.hologram.PlotHologram.clearAll(source.getServer());
        if (source.getPlayer() != null) {
            source.getPlayer().sendMessage(
                Text.literal("✔ Todos los holograms eliminados.").formatted(Formatting.GREEN), false);
        }
        return 1;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Resuelve una lista de plots a partir de:
     *  - "all"        → todos los plots del jugador
     *  - "1", "2"...  → por índice (1-based)
     *  - cualquier otro texto → por nombre exacto (case-insensitive)
     */
    private static List<PlotData> resolvePlots(List<PlotData> owned, String plotArg, ServerPlayerEntity player) {
        if (plotArg.equalsIgnoreCase("all")) return new ArrayList<>(owned);

        // Intentar por número
        try {
            int index = Integer.parseInt(plotArg);
            if (index < 1 || index > owned.size()) {
                player.sendMessage(Text.literal("✗ Número inválido. Tenés " + owned.size() + " protección(es) (1-" + owned.size() + ").").formatted(Formatting.RED), false);
                return null;
            }
            return List.of(owned.get(index - 1));
        } catch (NumberFormatException ignored) {}

        // Intentar por nombre (case-insensitive)
        for (PlotData p : owned) {
            if (p.getPlotName().equalsIgnoreCase(plotArg)) {
                return List.of(p);
            }
        }

        // No encontrado
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

    private static int executeTestHolo(ServerCommandSource source) {
        net.minecraft.server.network.ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        if (!(source.getWorld() instanceof net.minecraft.server.world.ServerWorld sw)) return 0;

        net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity disp =
                net.minecraft.entity.EntityType.TEXT_DISPLAY.create(sw);
        if (disp == null) {
            player.sendMessage(net.minecraft.text.Text.literal("§cERROR: create() null"), false);
            return 0;
        }

        net.minecraft.nbt.NbtCompound nbt = new net.minecraft.nbt.NbtCompound();
        net.minecraft.nbt.NbtList rotation = new net.minecraft.nbt.NbtList();
        rotation.add(net.minecraft.nbt.NbtFloat.of(player.getYaw() + 180f));
        rotation.add(net.minecraft.nbt.NbtFloat.of(0f));
        nbt.put("Rotation", rotation);
        nbt.putByte("billboard", (byte) 0);
        nbt.putString("text", "{\"text\":\"§6§lTEST FIJO\\n§7Si ves esto fijo, funciona.\"}");
        nbt.putInt("background", 0x60000000);
        nbt.putInt("line_width", 200);
        disp.readNbt(nbt);
        disp.setPos(player.getX(), player.getY() + 2.0, player.getZ());

        boolean ok = sw.spawnEntity(disp);
        player.sendMessage(net.minecraft.text.Text.literal(ok ? "§aOK spawned" : "§cFAIL"), false);
        com.zhilius.secureplots.SecurePlots.LOGGER.info("[testholo] ok={}", ok);

        final java.util.UUID id = disp.getUuid();
        new java.util.Timer().schedule(new java.util.TimerTask() {
            @Override public void run() {
                sw.getServer().execute(() -> { net.minecraft.entity.Entity e = sw.getEntity(id); if (e != null) e.discard(); });
            }
        }, 10000);
        return 1;
    }
}

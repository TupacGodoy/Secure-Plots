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
                    .then(CommandManager.literal("view")
                            .executes(ctx -> executeView(ctx.getSource())))
                    .then(CommandManager.literal("list")
                            .executes(ctx -> executeList(ctx.getSource())))
                    .then(CommandManager.literal("add")
                            .then(CommandManager.argument("player", StringArgumentType.word())
                                    .then(CommandManager.argument("plot", StringArgumentType.word())
                                            .executes(ctx -> executeAdd(
                                                    ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "player"),
                                                    StringArgumentType.getString(ctx, "plot"))))))
                    .then(CommandManager.literal("remove")
                            .then(CommandManager.argument("player", StringArgumentType.word())
                                    .then(CommandManager.argument("plot", StringArgumentType.word())
                                            .executes(ctx -> executeRemove(
                                                    ctx.getSource(),
                                                    StringArgumentType.getString(ctx, "player"),
                                                    StringArgumentType.getString(ctx, "plot"))))))
                    .then(CommandManager.literal("info")
                            .executes(ctx -> executeInfo(ctx.getSource()))));
        }
    }

    // /sp view
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

    // /sp list
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

    // /sp add <player> <plot#|all>
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
        String desc = targets.size() == 1 ? targets.get(0).getPlotName() : targets.size() + " protecciones";
        player.sendMessage(Text.literal("✔ ").formatted(Formatting.GREEN)
                .append(Text.literal(targetName).formatted(Formatting.WHITE))
                .append(Text.literal(" agregado a " + desc).formatted(Formatting.GREEN)), false);
        target.sendMessage(Text.literal("✔ Fuiste agregado a " + desc + " de " + player.getName().getString()).formatted(Formatting.GREEN), false);
        return 1;
    }

    // /sp remove <player> <plot#|all>
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
        if (removed == 0) {
            player.sendMessage(Text.literal("✗ " + targetName + " no era miembro de esa(s) protección(es).").formatted(Formatting.RED), false);
            return 0;
        }
        manager.markDirty();
        String desc = targets.size() == 1 ? targets.get(0).getPlotName() : targets.size() + " protecciones";
        player.sendMessage(Text.literal("✔ ").formatted(Formatting.GREEN)
                .append(Text.literal(targetName).formatted(Formatting.WHITE))
                .append(Text.literal(" eliminado de " + desc).formatted(Formatting.GREEN)), false);
        ServerPlayerEntity online = source.getServer().getPlayerManager().getPlayer(targetUuid);
        if (online != null) {
            online.sendMessage(Text.literal("✗ Te quitaron el acceso a " + desc + " de " + player.getName().getString()).formatted(Formatting.RED), false);
        }
        return 1;
    }

    // /sp info
    private static int executeInfo(ServerCommandSource source) {
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) return 0;
        PlotData plot = PlotManager.getOrCreate(player.getServerWorld()).getPlotAt(player.getBlockPos());
        if (plot == null) {
            player.sendMessage(Text.literal("✗ No estás dentro de ninguna protección.").formatted(Formatting.RED), false);
            return 0;
        }
        BlockPos c = plot.getCenter();
        int size = plot.getSize().radius;
        player.sendMessage(Text.literal("═══════════════════════════").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("  🛡 " + plot.getPlotName()).formatted(Formatting.YELLOW, Formatting.BOLD), false);
        player.sendMessage(Text.literal("═══════════════════════════").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("  Dueño: ").formatted(Formatting.GRAY).append(Text.literal(plot.getOwnerName()).formatted(Formatting.WHITE)), false);
        player.sendMessage(Text.literal("  Tier: ").formatted(Formatting.GRAY).append(Text.literal(plot.getSize().displayName).formatted(Formatting.AQUA)), false);
        player.sendMessage(Text.literal("  Radio: ").formatted(Formatting.GRAY).append(Text.literal(size + "x" + size + " bloques").formatted(Formatting.AQUA)), false);
        player.sendMessage(Text.literal("  Coordenadas: ").formatted(Formatting.GRAY).append(Text.literal(c.getX() + ", " + c.getY() + ", " + c.getZ()).formatted(Formatting.WHITE)), false);
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

    // ─── Helpers ──────────────────────────────────────────────────────────────────

    private static List<PlotData> resolvePlots(List<PlotData> owned, String plotArg, ServerPlayerEntity player) {
        if (plotArg.equalsIgnoreCase("all")) return new ArrayList<>(owned);
        int index;
        try {
            index = Integer.parseInt(plotArg);
        } catch (NumberFormatException e) {
            player.sendMessage(Text.literal("✗ Usá un número de la lista (/sp list) o \"all\".").formatted(Formatting.RED), false);
            return null;
        }
        if (index < 1 || index > owned.size()) {
            player.sendMessage(Text.literal("✗ Número inválido. Tenés " + owned.size() + " protección(es) (1-" + owned.size() + ").").formatted(Formatting.RED), false);
            return null;
        }
        return List.of(owned.get(index - 1));
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
}

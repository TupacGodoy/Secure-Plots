package com.zhilius.secureplots.item;

import com.zhilius.secureplots.config.SecurePlotsConfig;
import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotManager;
import com.zhilius.secureplots.plot.PlotSubdivision;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * Herramienta de subdivisiones — MC 1.21.1
 * Usa DataComponentTypes.CUSTOM_DATA (NbtComponent) para almacenar estado en el ítem.
 */
public class PlotStakeItem extends Item {

    public PlotStakeItem(Settings settings) { super(settings); }

    // ── NBT helpers (1.21.1: CUSTOM_DATA / NbtComponent) ─────────────────────

    private static NbtCompound readNbt(ItemStack stack) {
        NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
        return comp != null ? comp.copyNbt() : null;
    }

    private static void writeNbt(ItemStack stack, NbtCompound nbt) {
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
    }

    private static NbtCompound getOrCreate(ItemStack stack) {
        NbtComponent comp = stack.get(DataComponentTypes.CUSTOM_DATA);
        return comp != null ? comp.copyNbt() : new NbtCompound();
    }

    // ── Accessors públicos ────────────────────────────────────────────────────

    public static boolean isUseY(ItemStack stack) {
        NbtCompound nbt = readNbt(stack);
        return nbt != null && nbt.getBoolean("useY");
    }

    public static void setUseY(ItemStack stack, boolean val) {
        NbtCompound nbt = getOrCreate(stack);
        nbt.putBoolean("useY", val);
        writeNbt(stack, nbt);
    }

    public static String getActiveSub(ItemStack stack) {
        NbtCompound nbt = readNbt(stack);
        return nbt != null ? nbt.getString("activeSubName") : "";
    }

    public static void setActiveSub(ItemStack stack, String name, BlockPos plotCenter) {
        NbtCompound nbt = getOrCreate(stack);
        nbt.putString("activeSubName", name);
        nbt.putString("activePlotPos",
            plotCenter.getX() + "," + plotCenter.getY() + "," + plotCenter.getZ());
        writeNbt(stack, nbt);
    }

    public static BlockPos getActivePlotPos(ItemStack stack) {
        NbtCompound nbt = readNbt(stack);
        if (nbt == null) return null;
        String raw = nbt.getString("activePlotPos");
        if (raw == null || raw.isEmpty()) return null;
        try {
            String[] p = raw.split(",");
            return new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        } catch (Exception e) { return null; }
    }

    public static void clearActive(ItemStack stack) {
        NbtCompound nbt = readNbt(stack);
        if (nbt == null) return;
        nbt.remove("activeSubName");
        nbt.remove("activePlotPos");
        writeNbt(stack, nbt);
    }

    // ── use() — shift+clic en aire: toggle Y ─────────────────────────────────

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);
        if (!(user instanceof ServerPlayerEntity player)) return TypedActionResult.pass(stack);
        if (!(world instanceof ServerWorld sw)) return TypedActionResult.pass(stack);

        if (player.isSneaking()) {
            boolean cur = isUseY(stack);
            setUseY(stack, !cur);
            player.sendMessage(Text.literal("§dSubdivisión Y-particionada: "
                + (!cur ? "§aACTIVADA" : "§cDESACTIVADA")), true);

            // Actualizar flag en la sub activa
            String subName = getActiveSub(stack);
            BlockPos plotPos = getActivePlotPos(stack);
            if (!subName.isEmpty() && plotPos != null) {
                PlotData plot = PlotManager.getOrCreate(sw).getPlot(plotPos);
                if (plot != null) {
                    PlotSubdivision sub = plot.getSubdivision(subName);
                    if (sub != null) { sub.useY = !cur; PlotManager.getOrCreate(sw).markDirty(); }
                    ModPackets.sendShowSubdivisions(player, plot);
                }
            }
            return TypedActionResult.success(stack);
        }
        return TypedActionResult.pass(stack);
    }

    // ── onUseBlock — clic derecho en bloque ───────────────────────────────────

    public static ActionResult onUseBlock(ServerPlayerEntity player, ServerWorld world,
                                          Hand hand, BlockHitResult hit) {
        ItemStack stack = player.getStackInHand(hand);
        if (!(stack.getItem() instanceof PlotStakeItem)) return ActionResult.PASS;

        BlockPos clickedPos = hit.getBlockPos();
        PlotManager manager = PlotManager.getOrCreate(world);
        PlotData plot = manager.getPlotAt(clickedPos);

        if (plot == null) {
            player.sendMessage(Text.literal("§c✗ No estás dentro de ninguna parcela."), true);
            return ActionResult.FAIL;
        }

        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg != null && cfg.subdivisionTool.requireManagePermission) {
            if (!plot.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_SUBDIVISIONS)
                    && !player.getCommandTags().contains("plot_admin")) {
                player.sendMessage(
                    Text.literal("§c✗ No tenés permiso para crear subdivisiones aquí."), true);
                return ActionResult.FAIL;
            }
        }

        String subName   = getActiveSub(stack);
        BlockPos plotPos = getActivePlotPos(stack);

        if (subName.isEmpty() || plotPos == null || !plotPos.equals(plot.getCenter())) {
            subName = nextSubName(plot);
            setActiveSub(stack, subName, plot.getCenter());
            plot.getOrCreateSubdivision(subName);
            manager.markDirty();
            player.sendMessage(Text.literal("§d✎ Nueva subdivisión §e\"" + subName
                + "\"§d — Click derecho para agregar puntos"), false);
        }

        PlotSubdivision sub = plot.getSubdivision(subName);
        if (sub == null) sub = plot.getOrCreateSubdivision(subName);

        int maxPts = (cfg != null && cfg.maxSubdivisionPoints > 0) ? cfg.maxSubdivisionPoints : 32;
        if (sub.points.size() >= maxPts) {
            player.sendMessage(Text.literal(
                "§c✗ Máximo de " + maxPts + " puntos. Usá /sp sub finish para cerrar."), true);
            return ActionResult.FAIL;
        }

        if (player.isSneaking()) {
            if (sub.points.isEmpty()) {
                player.sendMessage(Text.literal("§7No hay puntos para eliminar."), true);
            } else {
                sub.removeLastPoint();
                manager.markDirty();
                player.sendMessage(
                    Text.literal("§e✗ Punto eliminado. Quedan: " + sub.points.size()), true);
            }
        } else {
            int x = clickedPos.getX(), z = clickedPos.getZ();
            sub.addPoint(x, z);
            if (isUseY(stack)) {
                int y = clickedPos.getY();
                if (sub.points.size() == 1) { sub.yMin = y; sub.yMax = y + 1; }
                else { sub.yMin = Math.min(sub.yMin, y); sub.yMax = Math.max(sub.yMax, y + 1); }
                sub.useY = true;
            }
            manager.markDirty();
            player.sendMessage(Text.literal(
                "§a✔ Punto #" + sub.points.size() + " §8[" + x + ", " + z + "]"
                + (isUseY(stack) ? " §7Y:" + clickedPos.getY() : "")), true);
        }

        ModPackets.sendShowSubdivisions(player, plot);
        return ActionResult.SUCCESS;
    }

    // ── finishSubdivision ─────────────────────────────────────────────────────

    public static boolean finishSubdivision(ServerPlayerEntity player, ServerWorld world) {
        ItemStack stack = findToolStack(player);
        if (stack == null) {
            player.sendMessage(
                Text.literal("§c✗ Necesitás la herramienta de subdivisiones en mano."), false);
            return false;
        }
        String subName = getActiveSub(stack);
        BlockPos plotPos = getActivePlotPos(stack);
        if (subName.isEmpty() || plotPos == null) {
            player.sendMessage(Text.literal("§7No hay subdivisión activa."), false);
            return false;
        }
        PlotManager manager = PlotManager.getOrCreate(world);
        PlotData plot = manager.getPlot(plotPos);
        if (plot == null) { clearActive(stack); return false; }
        PlotSubdivision sub = plot.getSubdivision(subName);
        if (sub == null) { clearActive(stack); return false; }
        if (!sub.isValid()) {
            player.sendMessage(Text.literal(
                "§c✗ Necesitás al menos 3 puntos (tiene " + sub.points.size() + ")."), false);
            return false;
        }
        manager.markDirty();
        clearActive(stack);
        player.sendMessage(Text.literal(
            "§a✔ Subdivisión §e\"" + subName + "\"§a finalizada con "
            + sub.points.size() + " puntos."), false);
        ModPackets.sendShowSubdivisions(player, plot);
        return true;
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                               List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("§dHerramienta de subdivisiones")
            .styled(s -> s.withItalic(false)));
        tooltip.add(Text.literal("§7Click §f→ Agregar punto")
            .styled(s -> s.withItalic(false)));
        tooltip.add(Text.literal("§7Shift+Click §f→ Quitar último punto")
            .styled(s -> s.withItalic(false)));
        tooltip.add(Text.literal("§7Shift+Clic (aire) §f→ Toggle Y")
            .styled(s -> s.withItalic(false)));
        tooltip.add(Text.literal("§8Modo Y: " + (isUseY(stack) ? "§aCON altura" : "§7SIN altura"))
            .styled(s -> s.withItalic(false)));
        String sub = getActiveSub(stack);
        if (!sub.isEmpty())
            tooltip.add(Text.literal("§6Editando: §e" + sub).styled(s -> s.withItalic(false)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String nextSubName(PlotData plot) {
        int i = 1;
        while (plot.getSubdivision("Zona " + i) != null) i++;
        return "Zona " + i;
    }

    public static ItemStack findToolStack(PlayerEntity player) {
        ItemStack main = player.getMainHandStack();
        if (main.getItem() instanceof PlotStakeItem) return main;
        ItemStack off = player.getOffHandStack();
        if (off.getItem() instanceof PlotStakeItem) return off;
        return null;
    }
}

package com.zhilius.secureplots.item;

import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.item.Item.TooltipContext;

import java.util.List;

public class PlotblueprintItem extends Item {

    public PlotblueprintItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            PlotManager manager = PlotManager.getOrCreate((ServerWorld) world);
            BlockPos playerPos = player.getBlockPos();

            // Find the nearest plot within 200 blocks
            PlotData nearest = null;
            double nearestDist = Double.MAX_VALUE;

            for (PlotData data : manager.getAllPlots()) {
                if (!data.getOwnerId().equals(player.getUuid()) &&
                        data.getRoleOf(player.getUuid()) == PlotData.Role.VISITOR)
                    continue;

                double dist = playerPos.getSquaredDistance(data.getCenter());
                if (dist < nearestDist && dist < 200 * 200) {
                    nearest = data;
                    nearestDist = dist;
                }
            }

            if (nearest != null) {
                // Send packet to show border and open info
                ModPackets.sendShowPlotBorder(player, nearest);
                player.sendMessage(Text.literal(""), false);
                player.sendMessage(Text.literal("🗺 Plano de: " + nearest.getPlotName()).formatted(Formatting.GOLD,
                        Formatting.BOLD), false);
                player.sendMessage(Text.literal("  Tamaño: " + nearest.getSize().displayName + " ("
                        + nearest.getSize().getRadius() + "x" + nearest.getSize().getRadius() + ")").formatted(Formatting.AQUA),
                        false);
                player.sendMessage(Text.literal("  Dueño: " + nearest.getOwnerName()).formatted(Formatting.WHITE),
                        false);
                if (!nearest.hasRank()) {
                    player.sendMessage(
                            Text.literal("  Expira en: " + nearest.getDaysRemaining(world.getTime()) + " días")
                                    .formatted(Formatting.YELLOW),
                            false);
                } else {
                    player.sendMessage(Text.literal("  Permanente (con rango)").formatted(Formatting.GREEN), false);
                }
                player.sendMessage(Text.literal("  Sneak + clic para abrir menú").formatted(Formatting.GRAY), false);
            } else {
                // Show all nearby plots as overview
                List<PlotData> playerPlots = manager.getPlayerPlots(player.getUuid());
                if (playerPlots.isEmpty()) {
                    player.sendMessage(Text.literal("No tenés protecciones colocadas.").formatted(Formatting.RED),
                            false);
                } else {
                    player.sendMessage(Text.literal("Tus protecciones:").formatted(Formatting.GOLD), false);
                    for (PlotData plot : playerPlots) {
                        BlockPos c = plot.getCenter();
                        player.sendMessage(Text.literal(
                                "  • " + plot.getPlotName() + " en " + c.getX() + ", " + c.getY() + ", " + c.getZ())
                                .formatted(Formatting.WHITE), false);
                    }
                    player.sendMessage(
                            Text.literal("Acercate a una protección para ver sus límites.").formatted(Formatting.GRAY),
                            false);
                }
            }
        }

        // Sneak + use = open menu of nearest plot
        if (!world.isClient && user.isSneaking() && user instanceof ServerPlayerEntity player) {
            PlotManager manager = PlotManager.getOrCreate((ServerWorld) world);
            PlotData atPos = manager.getPlotAt(player.getBlockPos());
            if (atPos != null) {
                // Find the block entity and open screen
                BlockPos center = atPos.getCenter();
                var be = world.getBlockEntity(center);
                if (be instanceof com.zhilius.secureplots.blockentity.PlotBlockEntity plotBE) {
                    plotBE.openScreen(player);
                }
            }
        }

        return TypedActionResult.success(user.getStackInHand(hand));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Muestra los límites de protecciones cercanas").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Sneak + clic para abrir el menú").formatted(Formatting.DARK_GRAY));
    }
}

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

            // Si el jugador está dentro de una protección, abrir el menú de gestión
            PlotData atPos = manager.getPlotAt(player.getBlockPos());
            if (atPos != null && atPos.getRoleOf(player.getUuid()) != PlotData.Role.VISITOR) {
                ModPackets.sendShowPlotBorder(player, atPos);
                ModPackets.sendOpenPlotScreen(player, atPos.getCenter(), atPos);
                return TypedActionResult.success(user.getStackInHand(hand));
            }

            // Si no está en ninguna protección, mostrar lista de protecciones propias
            List<PlotData> playerPlots = manager.getPlayerPlots(player.getUuid());
            if (playerPlots.isEmpty()) {
                player.sendMessage(Text.literal("✗ No tenés protecciones colocadas.").formatted(Formatting.RED), false);
            } else {
                player.sendMessage(Text.literal("═══════════════════════════").formatted(Formatting.GOLD), false);
                player.sendMessage(Text.literal("  🗺 Tus protecciones").formatted(Formatting.YELLOW, Formatting.BOLD), false);
                player.sendMessage(Text.literal("═══════════════════════════").formatted(Formatting.GOLD), false);
                for (int i = 0; i < playerPlots.size(); i++) {
                    PlotData p = playerPlots.get(i);
                    BlockPos c = p.getCenter();
                    player.sendMessage(
                        Text.literal("  " + (i + 1) + ". ").formatted(Formatting.GRAY)
                            .append(Text.literal(p.getPlotName()).formatted(Formatting.WHITE))
                            .append(Text.literal(" [" + p.getSize().displayName + "]").formatted(Formatting.AQUA))
                            .append(Text.literal("  " + c.getX() + ", " + c.getY() + ", " + c.getZ()).formatted(Formatting.DARK_GRAY)),
                        false);
                }
                player.sendMessage(Text.literal("  Parate dentro de una protección para gestionarla.").formatted(Formatting.GRAY), false);
                player.sendMessage(Text.literal("═══════════════════════════").formatted(Formatting.GOLD), false);
            }
        }

        return TypedActionResult.success(user.getStackInHand(hand));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Usalo dentro de una protección para gestionarla").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Fuera de una: muestra tu lista de protecciones").formatted(Formatting.DARK_GRAY));
    }
}

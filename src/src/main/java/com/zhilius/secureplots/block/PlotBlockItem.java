package com.zhilius.secureplots.block;

import com.zhilius.secureplots.plot.PlotSize;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.item.Item.TooltipContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * BlockItem personalizado para los bloques de parcela.
 * Muestra en el tooltip el tamaño de protección que otorga el bloque.
 */
public class PlotBlockItem extends BlockItem {

    private final int tier;

    public PlotBlockItem(Block block, int tier) {
        super(block, new Item.Settings());
        this.tier = tier;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);

        PlotSize plotSize = PlotSize.fromTier(this.tier);
        int size = plotSize.getRadius();

        tooltip.add(Text.literal("Protege: " + size + "x" + size + " bloques").formatted(Formatting.AQUA));
        tooltip.add(Text.literal("Tier " + (this.tier + 1) + " — " + plotSize.displayName).formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Coloca para reclamar tu parcela.").formatted(Formatting.DARK_GRAY));
    }
}


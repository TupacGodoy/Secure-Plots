package com.zhilius.secureplots.block;

import com.zhilius.secureplots.SecurePlots;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlocks {

    public static final Block PLOT_BLOCK = register("plot_block",
            new PlotBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.DIAMOND_BLUE)
                    .strength(50f, 1200f)
                    .luminance(state -> 8)
                    .requiresTool()));

    private static Block register(String name, Block block) {
        Identifier id = Identifier.of(SecurePlots.MOD_ID, name);
        // Also register as item
        Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
        return Registry.register(Registries.BLOCK, id, block);
    }

    public static void initialize() {
        // Called to trigger static initialization
        SecurePlots.LOGGER.info("Registrando bloques de Secure Plots...");
    }
}

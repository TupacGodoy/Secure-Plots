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

    // Tier 0 - Bronze
    public static final Block BRONZE_PLOT_BLOCK = register("bronze_plot_block",
            new PlotBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.ORANGE)
                    .strength(50f, 1200f)
                    .luminance(state -> 4)
                    .requiresTool(), 0));

    // Tier 1 - Gold
    public static final Block GOLD_PLOT_BLOCK = register("gold_plot_block",
            new PlotBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.GOLD)
                    .strength(50f, 1200f)
                    .luminance(state -> 5)
                    .requiresTool(), 1));

    // Tier 2 - Emerald
    public static final Block emerald_PLOT_BLOCK = register("emerald_plot_block",
            new PlotBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.GREEN)
                    .strength(50f, 1200f)
                    .luminance(state -> 6)
                    .requiresTool(), 2));

    // Tier 3 - Diamond
    public static final Block DIAMOND_PLOT_BLOCK = register("diamond_plot_block",
            new PlotBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.DIAMOND_BLUE)
                    .strength(50f, 1200f)
                    .luminance(state -> 7)
                    .requiresTool(), 3));

    // Tier 4 - Netherite
    public static final Block NETHERITE_PLOT_BLOCK = register("netherite_plot_block",
            new PlotBlock(AbstractBlock.Settings.create()
                    .mapColor(MapColor.BLACK)
                    .strength(50f, 1200f)
                    .luminance(state -> 8)
                    .requiresTool(), 4));

    /** Estaca de Parcela — delimita subdivisiones */
    public static final Block PLOT_STAKE_BLOCK = registerStake("plot_stake_block",
            new PlotStakeBlock(
                    AbstractBlock.Settings.create()
                            .mapColor(MapColor.BROWN)
                            .strength(2f, 6f)
                            .nonOpaque()));

    public static Block fromTier(int tier) {
        return switch (tier) {
            case 0 -> BRONZE_PLOT_BLOCK;
            case 1 -> GOLD_PLOT_BLOCK;
            case 2 -> emerald_PLOT_BLOCK;
            case 3 -> DIAMOND_PLOT_BLOCK;
            case 4 -> NETHERITE_PLOT_BLOCK;
            default -> BRONZE_PLOT_BLOCK;
        };
    }

    private static Block register(String name, Block block) {
        Identifier id = Identifier.of(SecurePlots.MOD_ID, name);
        Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
        return Registry.register(Registries.BLOCK, id, block);
    }

    /** Registra el bloque con un PlotStakeBlockItem personalizado */
    private static Block registerStake(String name, Block block) {
        Identifier id = Identifier.of(SecurePlots.MOD_ID, name);
        Registry.register(Registries.ITEM, id, new PlotStakeBlockItem(block, new Item.Settings()));
        return Registry.register(Registries.BLOCK, id, block);
    }

    public static void initialize() {
        SecurePlots.LOGGER.info("Registrando bloques de Secure Plots...");
    }
}

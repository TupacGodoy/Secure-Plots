/*
 * SecurePlots - A Fabric mod for Minecraft 1.21.1
 * Copyright (C) 2025 TupacGodoy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zhilius.secureplots.block;

import com.zhilius.secureplots.SecurePlots;
import com.zhilius.secureplots.config.SecurePlotsConfig;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlocks {

    public static final Block BRONZE_PLOT_BLOCK    = register("bronze_plot_block",    new PlotBlock(buildSettings(MapColor.ORANGE,       0), 0));
    public static final Block GOLD_PLOT_BLOCK      = register("gold_plot_block",      new PlotBlock(buildSettings(MapColor.GOLD,         1), 1));
    // Fixed: was "emerald_PLOT_BLOCK" (inconsistent naming)
    public static final Block EMERALD_PLOT_BLOCK   = register("emerald_plot_block",   new PlotBlock(buildSettings(MapColor.GREEN,        2), 2));
    public static final Block DIAMOND_PLOT_BLOCK   = register("diamond_plot_block",   new PlotBlock(buildSettings(MapColor.DIAMOND_BLUE, 3), 3));
    public static final Block NETHERITE_PLOT_BLOCK = register("netherite_plot_block", new PlotBlock(buildSettings(MapColor.BLACK,        4), 4));

    private static AbstractBlock.Settings buildSettings(MapColor color, int tier) {
        int   luminance       = 4 + tier;
        float hardness        = 50f;
        float blastResistance = 1200f;

        if (SecurePlotsConfig.INSTANCE != null) {
            SecurePlotsConfig.TierConfig tc = SecurePlotsConfig.INSTANCE.getTierConfig(tier);
            luminance        = tc.luminance;
            hardness         = tc.hardness;
            blastResistance  = tc.blastResistance;
        }

        final int lum = luminance;
        return AbstractBlock.Settings.create()
                .mapColor(color)
                .strength(hardness, blastResistance)
                .luminance(state -> lum)
                .requiresTool();
    }

    public static Block fromTier(int tier) {
        return switch (tier) {
            case 0  -> BRONZE_PLOT_BLOCK;
            case 1  -> GOLD_PLOT_BLOCK;
            case 2  -> EMERALD_PLOT_BLOCK;
            case 3  -> DIAMOND_PLOT_BLOCK;
            case 4  -> NETHERITE_PLOT_BLOCK;
            default -> BRONZE_PLOT_BLOCK;
        };
    }

    private static Block register(String name, Block block) {
        Identifier id = Identifier.of(SecurePlots.MOD_ID, name);
        Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
        return Registry.register(Registries.BLOCK, id, block);
    }

    public static void initialize() {
        SecurePlots.LOGGER.info("Registering Secure Plots blocks...");
    }
}

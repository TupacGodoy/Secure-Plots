/*
 * SecurePlots - A Fabric mod for Minecraft 1.21.1
 * Copyright (C) 2025 TupacGodoy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
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

    // Los valores de luminance/hardness/blastResistance se leen del config.
    // El config ya estará cargado cuando se llame a initialize() desde SecurePlots.onInitialize().

    // Tier 0 - Bronze
    public static final Block BRONZE_PLOT_BLOCK = register("bronze_plot_block",
            new PlotBlock(buildSettings(MapColor.ORANGE, 0), 0));

    // Tier 1 - Gold
    public static final Block GOLD_PLOT_BLOCK = register("gold_plot_block",
            new PlotBlock(buildSettings(MapColor.GOLD, 1), 1));

    // Tier 2 - Emerald
    public static final Block emerald_PLOT_BLOCK = register("emerald_plot_block",
            new PlotBlock(buildSettings(MapColor.GREEN, 2), 2));

    // Tier 3 - Diamond
    public static final Block DIAMOND_PLOT_BLOCK = register("diamond_plot_block",
            new PlotBlock(buildSettings(MapColor.DIAMOND_BLUE, 3), 3));

    // Tier 4 - Netherite
    public static final Block NETHERITE_PLOT_BLOCK = register("netherite_plot_block",
            new PlotBlock(buildSettings(MapColor.BLACK, 4), 4));

    /**
     * Construye los settings del bloque leyendo luminance, hardness y blastResistance del config.
     * Si el config no está disponible (arranque muy temprano), usa los valores por defecto.
     */
    private static AbstractBlock.Settings buildSettings(MapColor color, int tier) {
        int luminance = 4 + tier;       // fallback
        float hardness = 50f;
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

    public static void initialize() {
        SecurePlots.LOGGER.info("Registrando bloques de Secure Plots...");
    }
}

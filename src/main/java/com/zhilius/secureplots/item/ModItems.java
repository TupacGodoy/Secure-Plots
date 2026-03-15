/*
 * SecurePlots - A Fabric mod for Minecraft 1.21.1
 * Copyright (C) 2025 TupacGodoy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zhilius.secureplots.item;

import com.zhilius.secureplots.SecurePlots;
import com.zhilius.secureplots.block.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public class ModItems {

    public static final Item PLOT_BLUEPRINT = register("plot_blueprint",
        new PlotblueprintItem(new Item.Settings()
            .maxCount(1)
            .rarity(Rarity.UNCOMMON)));

    public static final ItemGroup SECURE_PLOTS_GROUP = Registry.register(
        Registries.ITEM_GROUP,
        Identifier.of(SecurePlots.MOD_ID, "secure_plots"),
        FabricItemGroup.builder()
            .displayName(Text.literal("Secure Plots"))
            .icon(() -> new ItemStack(ModBlocks.BRONZE_PLOT_BLOCK))
            .entries((context, entries) -> {
                entries.add(PLOT_BLUEPRINT);
                entries.add(ModBlocks.BRONZE_PLOT_BLOCK);
                entries.add(ModBlocks.GOLD_PLOT_BLOCK);
                entries.add(ModBlocks.EMERALD_PLOT_BLOCK);
                entries.add(ModBlocks.DIAMOND_PLOT_BLOCK);
                entries.add(ModBlocks.NETHERITE_PLOT_BLOCK);
            })
            .build());

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(SecurePlots.MOD_ID, name), item);
    }

    public static void initialize() {
        SecurePlots.LOGGER.info("Registering Secure Plots items...");
    }
}
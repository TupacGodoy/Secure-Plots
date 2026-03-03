package com.zhilius.secureplots.item;

import com.zhilius.secureplots.SecurePlots;
import com.zhilius.secureplots.block.ModBlocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;

public class ModItems {

    public static final Item PLOT_WAND = register("plot_wand",
            new PlotWandItem(new Item.Settings()
                    .maxCount(1)
                    .rarity(Rarity.UNCOMMON)
            ));

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of(SecurePlots.MOD_ID, name), item);
    }

    public static void initialize() {
        SecurePlots.LOGGER.info("Registrando items de Secure Plots...");

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(PLOT_WAND);
            entries.add(ModBlocks.BRONZE_PLOT_BLOCK.asItem());
            entries.add(ModBlocks.IRON_PLOT_BLOCK.asItem());
            entries.add(ModBlocks.GOLD_PLOT_BLOCK.asItem());
            entries.add(ModBlocks.DIAMOND_PLOT_BLOCK.asItem());
            entries.add(ModBlocks.NETHERITE_PLOT_BLOCK.asItem());
            entries.add(ModBlocks.QUANTUM_PLOT_BLOCK.asItem());
        });
    }
}

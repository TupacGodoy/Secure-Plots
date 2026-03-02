package com.zhilius.secureplots.item;

import com.zhilius.secureplots.SecurePlots;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

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

        net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.modifyEntriesEvent(
            net.minecraft.item.ItemGroups.FUNCTIONAL
        ).register(entries -> {
            entries.add(PLOT_WAND);
            entries.add(com.zhilius.secureplots.block.ModBlocks.PLOT_BLOCK.asItem());
        });
    }
}

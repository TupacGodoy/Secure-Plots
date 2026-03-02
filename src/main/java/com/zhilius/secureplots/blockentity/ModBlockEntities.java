package com.zhilius.secureplots.blockentity;

import com.zhilius.secureplots.block.ModBlocks;
import com.zhilius.secureplots.SecurePlots;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlockEntities {

    public static BlockEntityType<PlotBlockEntity> PLOT_BLOCK_ENTITY;

    public static void initialize() {
        PLOT_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(SecurePlots.MOD_ID, "plot_block_entity"),
                BlockEntityType.Builder.create(PlotBlockEntity::new, ModBlocks.PLOT_BLOCK).build()
        );
        SecurePlots.LOGGER.info("Registrando block entities de Secure Plots...");
    }
}

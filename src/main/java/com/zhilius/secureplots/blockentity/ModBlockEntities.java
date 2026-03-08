package com.zhilius.secureplots.blockentity;

import com.zhilius.secureplots.block.ModBlocks;
import com.zhilius.secureplots.SecurePlots;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModBlockEntities {

    public static BlockEntityType<PlotBlockEntity> PLOT_BLOCK_ENTITY;
    public static BlockEntityType<com.zhilius.secureplots.blockentity.PlotStakeBlockEntity> PLOT_STAKE_BLOCK_ENTITY;

    public static void initialize() {
        PLOT_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(SecurePlots.MOD_ID, "plot_block_entity"),
                BlockEntityType.Builder.create(PlotBlockEntity::new,
                        ModBlocks.BRONZE_PLOT_BLOCK,
                        ModBlocks.emerald_PLOT_BLOCK,
                        ModBlocks.GOLD_PLOT_BLOCK,
                        ModBlocks.DIAMOND_PLOT_BLOCK,
                        ModBlocks.NETHERITE_PLOT_BLOCK).build());

        PLOT_STAKE_BLOCK_ENTITY = Registry.register(
                Registries.BLOCK_ENTITY_TYPE,
                Identifier.of(SecurePlots.MOD_ID, "plot_stake_block_entity"),
                BlockEntityType.Builder.create(
                        com.zhilius.secureplots.blockentity.PlotStakeBlockEntity::new,
                        ModBlocks.PLOT_STAKE_BLOCK).build());

        SecurePlots.LOGGER.info("Registrando block entities de Secure Plots...");
    }
}

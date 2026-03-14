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
                BlockEntityType.Builder.create(PlotBlockEntity::new,
                        ModBlocks.BRONZE_PLOT_BLOCK,
                        ModBlocks.emerald_PLOT_BLOCK,
                        ModBlocks.GOLD_PLOT_BLOCK,
                        ModBlocks.DIAMOND_PLOT_BLOCK,
                        ModBlocks.NETHERITE_PLOT_BLOCK).build());
        SecurePlots.LOGGER.info("Registrando block entities de Secure Plots...");
    }
}

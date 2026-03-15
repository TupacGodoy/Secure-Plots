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

import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PlotBlockEntity extends BlockEntity {

    private int tickCounter = 0;

    public PlotBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLOT_BLOCK_ENTITY, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, PlotBlockEntity be) {
        if (world.isClient) return;

        // Check for expired plots every 5 minutes (6000 ticks)
        if (++be.tickCounter >= 6000) {
            be.tickCounter = 0;
            PlotManager manager = PlotManager.getOrCreate((ServerWorld) world);
            PlotData data = manager.getPlot(pos);
            if (data != null && data.isExpired(world.getTime())) {
                manager.removePlot(pos);
                world.breakBlock(pos, false);
            }
        }
    }

    public void openScreen(ServerPlayerEntity player) {
        PlotManager manager = PlotManager.getOrCreate((ServerWorld) world);
        PlotData data = manager.getPlot(pos);
        if (data != null) ModPackets.sendOpenPlotScreen(player, pos, data);
    }
}
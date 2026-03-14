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
package com.zhilius.secureplots.client;

import com.zhilius.secureplots.plot.PlotData;
import net.minecraft.util.math.BlockPos;

// Disabled — hologram is handled server-side by PlotHologram (TextDisplayEntity)
public class PlotHologramClient {
    public static void register() {}
    public static void show(PlotData data, BlockPos pos, long durationMs) {}
    public static void show(PlotData data, BlockPos pos, long durationMs, float yaw) {}
    public static void hide(BlockPos pos) {}
    public static void clear() {}
}

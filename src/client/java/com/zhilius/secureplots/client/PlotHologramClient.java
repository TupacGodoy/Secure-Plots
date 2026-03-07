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

package com.zhilius.secureplots.plot;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends action-bar HUD messages when players enter or leave a plot area.
 * Checks every 10 ticks to avoid overhead.
 */
public class PlotAreaTracker {

    // Last known plot center per player (null key value = outside all plots)
    private static final Map<UUID, BlockPos> lastPlot = new ConcurrentHashMap<>();

    private static int tick = 0;
    private static final int CHECK_INTERVAL = 10;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PlotAreaTracker::onTick);
    }

    private static void onTick(MinecraftServer server) {
        if (++tick < CHECK_INTERVAL) return;
        tick = 0;

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (!(player.getWorld() instanceof ServerWorld sw)) continue;

            PlotManager manager = PlotManager.getOrCreate(sw);
            PlotData current = manager.getPlotAt(player.getBlockPos());

            UUID id = player.getUuid();
            BlockPos prevCenter = lastPlot.get(id);
            BlockPos currentCenter = current != null ? current.getCenter() : null;

            boolean same = (prevCenter == null && currentCenter == null)
                    || (prevCenter != null && prevCenter.equals(currentCenter));

            if (same) continue;

            if (currentCenter != null) {
                // Entered a plot
                player.sendMessage(
                    Text.literal("🛡 ").formatted(Formatting.GOLD)
                        .append(Text.literal(current.getPlotName()).formatted(Formatting.YELLOW, Formatting.BOLD))
                        .append(Text.literal("  de  ").formatted(Formatting.GRAY))
                        .append(Text.literal(current.getOwnerName()).formatted(Formatting.WHITE)),
                    true);
                lastPlot.put(id, currentCenter);
            } else {
                // Left a plot — clear action bar with empty message
                player.sendMessage(Text.literal(""), true);
                lastPlot.remove(id);
            }
        }
    }
}

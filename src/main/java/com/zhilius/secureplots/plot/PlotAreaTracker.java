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
 * Also manages fly ability granted by the FLY flag or per-member FLY permission.
 * Checks every 10 ticks to avoid overhead.
 */
public class PlotAreaTracker {

    private static final Map<UUID, BlockPos> lastPlot = new ConcurrentHashMap<>();
    // Track which players currently have fly granted BY this mod (so we don't revoke fly they had before)
    private static final Map<UUID, Boolean> flyGranted = new ConcurrentHashMap<>();

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

            // Always re-evaluate fly even if plot hasn't changed (permissions may have changed)
            updateFly(player, current);

            if (same) continue;

            if (currentCenter != null) {
                // Entered a plot
                if (current.hasFlag(PlotData.Flag.GREETINGS)) {
                    player.sendMessage(
                        Text.literal("🛡 ").formatted(Formatting.GOLD)
                            .append(Text.literal(current.getPlotName()).formatted(Formatting.YELLOW, Formatting.BOLD))
                            .append(Text.literal("  de  ").formatted(Formatting.GRAY))
                            .append(Text.literal(current.getOwnerName()).formatted(Formatting.WHITE)),
                        true);
                }
                lastPlot.put(id, currentCenter);
            } else {
                // Left a plot
                if (current == null) {
                    player.sendMessage(Text.literal(""), true);
                }
                lastPlot.remove(id);
            }
        }
    }

    /**
     * Grants or revokes fly based on whether the player has the FLY permission
     * in the plot they're currently standing in.
     * Never revokes fly if the player already had it before entering (e.g. creative, op).
     */
    private static void updateFly(ServerPlayerEntity player, PlotData plot) {
        UUID id = player.getUuid();

        // If player is in creative or already an op with fly, don't touch it
        if (player.isCreative() || player.isSpectator()) return;

        boolean shouldHaveFly = plot != null && plot.hasPermission(id, PlotData.Permission.FLY);

        boolean currentlyGrantedByUs = flyGranted.getOrDefault(id, false);
        boolean currentlyFlying = player.getAbilities().allowFlying;

        if (shouldHaveFly && !currentlyFlying) {
            // Grant fly
            player.getAbilities().allowFlying = true;
            player.sendAbilitiesUpdate();
            flyGranted.put(id, true);
        } else if (!shouldHaveFly && currentlyGrantedByUs) {
            // Revoke fly (only if we were the ones who granted it)
            player.getAbilities().allowFlying = false;
            player.getAbilities().flying = false;
            player.sendAbilitiesUpdate();
            flyGranted.remove(id);
        }
    }

    /**
     * Called when a player disconnects — clean up fly state.
     */
    public static void onPlayerLeave(ServerPlayerEntity player) {
        UUID id = player.getUuid();
        if (flyGranted.remove(id) != null) {
            player.getAbilities().allowFlying = false;
            player.getAbilities().flying = false;
            player.sendAbilitiesUpdate();
        }
        lastPlot.remove(id);
    }
}

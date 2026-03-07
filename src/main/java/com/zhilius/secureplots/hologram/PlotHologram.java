package com.zhilius.secureplots.hologram;

import com.zhilius.secureplots.plot.PlotData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Server-side hologram stub.
 * The actual hologram rendering happens client-side in PlotHologramClient.
 * This class only handles cleanup of any stale TextDisplayEntity that might
 * have persisted from older mod versions.
 */
public class PlotHologram {

    private static final String TAG = "sp_holo";

    public static void registerTicker() {
        // Kill any stale sp_holo entities that saved to disk from old mod versions
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof DisplayEntity.TextDisplayEntity disp) {
                Text name = disp.getCustomName();
                if (name != null && TAG.equals(name.getString())) {
                    world.getServer().execute(disp::discard);
                }
            }
        });
    }

    /** No-op — hologram is rendered client-side now. */
    public static void spawn(ServerWorld world, BlockPos blockPos, PlotData data,
                              int durationTicks, float playerYaw) {
        // Nothing to do — client renders hologram via ShowPlotBorderPayload
    }

    /** No-op — nothing to remove server-side. */
    public static void remove(ServerWorld world, BlockPos pos) {}

    /** Always returns true — no space check needed. */
    public static boolean hasSpace(ServerWorld world, BlockPos blockPos) {
        return true;
    }

    /** No-op. */
    public static void clearAll(net.minecraft.server.MinecraftServer server) {}
}

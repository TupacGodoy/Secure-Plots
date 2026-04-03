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
package com.zhilius.secureplots.hologram;

import com.zhilius.secureplots.config.SecurePlotsConfig;
import com.zhilius.secureplots.plot.ProtectedAreaManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.*;

/**
 * Shows particle effects at protected area corners to visualize boundaries.
 */
public class ProtectedAreaHologram {

    // Track players who recently saw boundary particles
    private static final Map<UUID, Long> playerLastParticle = new HashMap<>();
    private static final long PARTICLE_COOLDOWN = 5000; // 5 seconds

    public static void register() {
        // Clear holograms on entity load (cleanup from previous sessions)
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof DisplayEntity.TextDisplayEntity disp) {
                Text name = disp.getCustomName();
                if (name != null && name.getString().contains("[ProtectedArea]")) {
                    world.getServer().execute(disp::discard);
                }
            }
        });

        // Spawn boundary particles periodically for players near protected areas
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 100 == 0) { // Every 5 seconds
                for (ServerWorld world : server.getWorlds()) {
                    spawnBoundaryParticles(world);
                }
            }
        });
    }

    private static void spawnBoundaryParticles(ServerWorld world) {
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg == null || cfg.protectedAreas == null) return;

        ProtectedAreaManager manager = ProtectedAreaManager.getOrCreate(world);
        String dimension = world.getRegistryKey().getValue().toString();

        for (SecurePlotsConfig.ProtectedArea area : cfg.protectedAreas) {
            if (!area.enabled || !area.dimension.equals(dimension)) continue;

            // Spawn particles at corners for nearby players
            for (ServerPlayerEntity player : world.getPlayers()) {
                UUID playerId = player.getUuid();
                long now = System.currentTimeMillis();

                if (playerLastParticle.containsKey(playerId) &&
                    now - playerLastParticle.get(playerId) < PARTICLE_COOLDOWN) {
                    continue;
                }

                // Check if player is within 64 blocks of any corner
                BlockPos[] corners = getAreaCorners(area);
                boolean nearCorner = false;
                for (BlockPos corner : corners) {
                    if (player.squaredDistanceTo(corner.getX(), corner.getY(), corner.getZ()) < 4096) {
                        nearCorner = true;
                        break;
                    }
                }

                if (nearCorner) {
                    playerLastParticle.put(playerId, now);
                    spawnCornerParticles(world, area);
                    break;
                }
            }
        }
    }

    private static BlockPos[] getAreaCorners(SecurePlotsConfig.ProtectedArea area) {
        return new BlockPos[] {
            new BlockPos(area.x1, area.y1, area.z1),
            new BlockPos(area.x2, area.y1, area.z1),
            new BlockPos(area.x1, area.y1, area.z2),
            new BlockPos(area.x2, area.y1, area.z2),
            new BlockPos(area.x1, area.y2, area.z1),
            new BlockPos(area.x2, area.y2, area.z1),
            new BlockPos(area.x1, area.y2, area.z2),
            new BlockPos(area.x2, area.y2, area.z2)
        };
    }

    private static void spawnCornerParticles(ServerWorld world, SecurePlotsConfig.ProtectedArea area) {
        BlockPos[] corners = getAreaCorners(area);

        for (BlockPos corner : corners) {
            world.spawnParticles(
                net.minecraft.particle.ParticleTypes.END_ROD,
                corner.getX() + 0.5, corner.getY() + 0.5, corner.getZ() + 0.5,
                1, 0.0, 0.0, 0.0, 0.0
            );
        }

        // Spawn area name hologram at center
        double centerX = (area.x1 + area.x2) / 2.0;
        double centerY = (area.y1 + area.y2) / 2.0 + 2;
        double centerZ = (area.z1 + area.z2) / 2.0;

        DisplayEntity.TextDisplayEntity textDisplay = EntityType.TEXT_DISPLAY.create(world);
        if (textDisplay == null) return;
        textDisplay.refreshPositionAndAngles(centerX, centerY, centerZ, 0, 0);
        textDisplay.setText(Text.literal(area.name).formatted(Formatting.GOLD).formatted(Formatting.BOLD));
        textDisplay.setCustomName(Text.literal("[ProtectedArea]"));
        textDisplay.setCustomNameVisible(false);
        textDisplay.setBillboardMode(DisplayEntity.TextDisplayEntity.BillboardMode.FIXED);

        world.spawnEntity(textDisplay);

        // Schedule removal after 3 seconds (60 ticks)
        final UUID hologramId = textDisplay.getUuid();
        final long spawnTime = world.getTime();
        ServerTickEvents.END_WORLD_TICK.register(world2 -> {
            if (world2 != world) return;
            if (world2.getTime() - spawnTime >= 60) {
                net.minecraft.entity.Entity entity = world.getEntity(hologramId);
                if (entity != null) entity.discard();
            }
        });
    }

    /**
     * Manually trigger boundary particles for a player (e.g., on /sp protectedarea highlight)
     */
    public static void showBoundariesForPlayer(ServerPlayerEntity player, SecurePlotsConfig.ProtectedArea area) {
        ServerWorld world = (ServerWorld) player.getWorld();
        if (!area.dimension.equals(world.getRegistryKey().getValue().toString())) {
            player.sendMessage(Text.literal("§cArea is in a different dimension").formatted(Formatting.RED), false);
            return;
        }
        spawnCornerParticles(world, area);
        player.sendMessage(Text.literal("§eShowing boundaries for: §f" + area.name).formatted(Formatting.YELLOW), false);
    }
}

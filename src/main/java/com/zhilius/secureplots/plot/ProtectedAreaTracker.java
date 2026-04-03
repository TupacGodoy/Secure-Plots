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
package com.zhilius.secureplots.plot;

import com.zhilius.secureplots.config.SecurePlotsConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks player positions relative to protected areas and sends enter/exit notifications.
 */
public class ProtectedAreaTracker {

    // Track which areas each player was in during the last tick
    private static final Map<UUID, Set<String>> playerAreas = new HashMap<>();

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Cleanup expired temporary areas every 10 seconds (200 ticks)
            if (server.getTicks() % 200 == 0) {
                for (ServerWorld world : server.getWorlds()) {
                    ProtectedAreaManager manager = ProtectedAreaManager.getOrCreate(world);
                    if (manager.cleanupExpiredAreas()) {
                        SecurePlotsConfig.save();
                    }
                }
            }

            // Check player positions every second (20 ticks)
            if (server.getTicks() % 20 == 0) {
                for (ServerWorld world : server.getWorlds()) {
                    checkPlayerPositions(world);
                }
            }
        });
    }

    private static void checkPlayerPositions(ServerWorld world) {
        ProtectedAreaManager manager = ProtectedAreaManager.getOrCreate(world);
        String dimension = world.getRegistryKey().getValue().toString();

        for (ServerPlayerEntity player : world.getPlayers()) {
            UUID playerId = player.getUuid();
            BlockPos pos = player.getBlockPos();

            // Get current areas
            Set<String> currentAreaNames = new HashSet<>();
            var currentAreas = manager.getAreasAt(pos, dimension);
            for (SecurePlotsConfig.ProtectedArea area : currentAreas) {
                if (area.enabled) {
                    currentAreaNames.add(area.name);
                }
            }

            // Get previous areas
            Set<String> previousAreas = playerAreas.getOrDefault(playerId, new HashSet<>());

            // Find entered areas (in current but not in previous)
            for (String areaName : currentAreaNames) {
                if (!previousAreas.contains(areaName)) {
                    onPlayerEnter(player, areaName);
                }
            }

            // Find exited areas (in previous but not in current)
            for (String areaName : previousAreas) {
                if (!currentAreaNames.contains(areaName)) {
                    onPlayerExit(player, areaName);
                }
            }

            // Update tracking
            playerAreas.put(playerId, currentAreaNames);
        }
    }

    private static void onPlayerEnter(ServerPlayerEntity player, String areaName) {
        SecurePlotsConfig.ProtectedArea area = findAreaByName(areaName);
        if (area == null || !area.enabled) return;

        // Send notification if enabled
        if (area.showNotifications) {
            player.sendMessage(Text.literal("§e► Entering protected area: §f" + areaName).formatted(Formatting.YELLOW), true);
        }
    }

    private static void onPlayerExit(ServerPlayerEntity player, String areaName) {
        SecurePlotsConfig.ProtectedArea area = findAreaByName(areaName);
        if (area == null || !area.enabled) return;

        // Send notification if enabled
        if (area.showNotifications) {
            player.sendMessage(Text.literal("§e◄ Leaving protected area: §f" + areaName).formatted(Formatting.YELLOW), true);
        }
    }

    private static SecurePlotsConfig.ProtectedArea findAreaByName(String name) {
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg == null) return null;

        for (SecurePlotsConfig.ProtectedArea area : cfg.protectedAreas) {
            if (area.name.equalsIgnoreCase(name)) {
                return area;
            }
        }
        return null;
    }

    /**
     * Clears tracking for a disconnected player.
     */
    public static void onPlayerDisconnect(UUID playerId) {
        playerAreas.remove(playerId);
    }
}

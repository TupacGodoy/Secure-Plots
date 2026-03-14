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
package com.zhilius.secureplots.screen;

import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks players who are expected to type a member name in chat. */
public class PendingAdd {
    private static final Map<UUID, BlockPos> pending = new ConcurrentHashMap<>();

    public static void set(UUID player, BlockPos plotPos) {
        pending.put(player, plotPos);
    }

    public static BlockPos consume(UUID player) {
        return pending.remove(player);
    }

    public static boolean has(UUID player) {
        return pending.containsKey(player);
    }
}

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

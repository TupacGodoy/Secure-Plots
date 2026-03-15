/*
 * SecurePlots - A Fabric mod for Minecraft 1.21.1
 * Copyright (C) 2025 TupacGodoy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zhilius.secureplots.plot;

import com.zhilius.secureplots.config.SecurePlotsConfig;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.*;
import java.util.stream.Collectors;

public class PlotManager extends PersistentState {

    private static final String KEY = "secure_plots_data";

    private final Map<BlockPos, PlotData> plots = new HashMap<>();

    // ── Static factory ────────────────────────────────────────────────────────

    public static PlotManager getOrCreate(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        return manager.getOrCreate(new PersistentState.Type<>(
            PlotManager::new,
            (nbt, registries) -> { PlotManager pm = new PlotManager(); pm.readNbt(nbt); return pm; },
            null), KEY);
    }

    // ── Placement ─────────────────────────────────────────────────────────────

    public boolean canPlace(BlockPos center, PlotSize size) {
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg != null && cfg.allowNestedPlots) return true;

        int buffer = cfg != null ? cfg.plotBuffer : 15;
        int half   = size.getRadius() / 2;
        int minX = center.getX() - half - buffer,  maxX = center.getX() + half + buffer;
        int minZ = center.getZ() - half - buffer,  maxZ = center.getZ() + half + buffer;

        for (PlotData other : plots.values()) {
            int oh   = other.getSize().getRadius() / 2;
            int oMinX = other.getCenter().getX() - oh, oMaxX = other.getCenter().getX() + oh;
            int oMinZ = other.getCenter().getZ() - oh, oMaxZ = other.getCenter().getZ() + oh;
            if (minX <= oMaxX && maxX >= oMinX && minZ <= oMaxZ && maxZ >= oMinZ) return false;
        }
        return true;
    }

    public void addPlot(PlotData data) {
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg != null && cfg.maxPlotsPerPlayer > 0) {
            long owned = plots.values().stream()
                .filter(p -> p.getOwnerId().equals(data.getOwnerId()))
                .count();
            if (owned >= cfg.maxPlotsPerPlayer) return;
        }

        String base = data.getOwnerName() + "'s Plot";
        String name = base;
        int counter = 2;
        while (isNameTaken(data.getOwnerId(), name)) name = base + " " + counter++;
        data.setPlotName(name);
        plots.put(data.getCenter(), data);
        markDirty();
    }

    public boolean isNameTaken(UUID ownerId, String name) {
        return plots.values().stream()
            .anyMatch(p -> p.getOwnerId().equals(ownerId) && p.getPlotName().equalsIgnoreCase(name));
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public void removePlot(BlockPos center)  { plots.remove(center); markDirty(); }
    public PlotData getPlot(BlockPos center) { return plots.get(center); }

    public PlotData getPlotAt(BlockPos pos) {
        for (PlotData data : plots.values())
            if (isInsidePlot(pos, data)) return data;
        return null;
    }

    public boolean isInsidePlot(BlockPos pos, PlotData data) {
        int half  = data.getSize().getRadius() / 2;
        BlockPos c = data.getCenter();
        return pos.getX() >= c.getX() - half && pos.getX() <= c.getX() + half
            && pos.getZ() >= c.getZ() - half && pos.getZ() <= c.getZ() + half;
    }

    public List<PlotData> getAllPlots()                 { return new ArrayList<>(plots.values()); }

    public List<PlotData> getPlayerPlots(UUID playerId) {
        return plots.values().stream()
            .filter(d -> d.getOwnerId().equals(playerId))
            .collect(Collectors.toList());
    }

    /** Updates lastOwnerSeenTick on all plots owned by this player. */
    public void updateOwnerSeen(UUID ownerId, long currentTick) {
        boolean changed = false;
        for (PlotData data : plots.values()) {
            if (data.getOwnerId().equals(ownerId)) {
                data.setLastOwnerSeenTick(currentTick);
                changed = true;
            }
        }
        if (changed) markDirty();
    }

    public void removeExpiredPlots(long currentTick) {
        if (plots.values().removeIf(p -> p.isExpired(currentTick))) markDirty();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void readNbt(NbtCompound nbt) {
        plots.clear();
        NbtList list = nbt.getList("plots", 10);
        for (int i = 0; i < list.size(); i++) {
            PlotData data = PlotData.fromNbt(list.getCompound(i));
            plots.put(data.getCenter(), data);
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtList list = new NbtList();
        for (PlotData data : plots.values()) list.add(data.toNbt());
        nbt.put("plots", list);
        return nbt;
    }
}
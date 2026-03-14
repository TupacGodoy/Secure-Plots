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

    // ── Config helpers ────────────────────────────────────────────────────────

    private int getBuffer() {
        return SecurePlotsConfig.INSTANCE != null ? SecurePlotsConfig.INSTANCE.plotBuffer : 15;
    }

    private List<String> getBlockedPrefixes() {
        if (SecurePlotsConfig.INSTANCE != null && SecurePlotsConfig.INSTANCE.blockedStructurePrefixes != null)
            return SecurePlotsConfig.INSTANCE.blockedStructurePrefixes;
        return Arrays.asList("cobbleverse:", "legendarymonuments:");
    }

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
        // allowNestedPlots: if true, skip proximity check entirely
        if (cfg != null && cfg.allowNestedPlots) return true;

        int buffer   = getBuffer();
        int halfSize = size.getRadius() / 2;
        int minX = center.getX() - halfSize - buffer;
        int maxX = center.getX() + halfSize + buffer;
        int minZ = center.getZ() - halfSize - buffer;
        int maxZ = center.getZ() + halfSize + buffer;

        for (PlotData other : plots.values()) {
            int half  = other.getSize().getRadius() / 2;
            int oMinX = other.getCenter().getX() - half;
            int oMaxX = other.getCenter().getX() + half;
            int oMinZ = other.getCenter().getZ() - half;
            int oMaxZ = other.getCenter().getZ() + half;
            if (minX <= oMaxX && maxX >= oMinX && minZ <= oMaxZ && maxZ >= oMinZ)
                return false;
        }
        return true;
    }

    public void addPlot(PlotData data) {
        // Enforce maxPlotsPerPlayer (0 = unlimited)
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg != null && cfg.maxPlotsPerPlayer > 0) {
            long owned = plots.values().stream()
                .filter(p -> p.getOwnerId().equals(data.getOwnerId()))
                .count();
            if (owned >= cfg.maxPlotsPerPlayer) return; // caller should check canPlace first; silently bail
        }

        String baseName  = data.getOwnerName() + "'s Plot";
        String finalName = baseName;
        int counter = 2;
        while (isNameTaken(data.getOwnerId(), finalName)) {
            finalName = baseName + " " + counter++;
        }
        data.setPlotName(finalName);
        plots.put(data.getCenter(), data);
        markDirty();
    }

    public boolean isNameTaken(UUID ownerId, String name) {
        return plots.values().stream()
            .anyMatch(p -> p.getOwnerId().equals(ownerId) && p.getPlotName().equalsIgnoreCase(name));
    }

    // ── CRUD ─────────────────────────────────────────────────────────────────

    public void removePlot(BlockPos center) {
        plots.remove(center);
        markDirty();
    }

    public PlotData getPlot(BlockPos center) {
        return plots.get(center);
    }

    public PlotData getPlotAt(BlockPos pos) {
        for (PlotData data : plots.values()) {
            if (isInsidePlot(pos, data)) return data;
        }
        return null;
    }

    public boolean isInsidePlot(BlockPos pos, PlotData data) {
        int half = data.getSize().getRadius() / 2;
        BlockPos c = data.getCenter();
        return pos.getX() >= c.getX() - half && pos.getX() <= c.getX() + half
            && pos.getZ() >= c.getZ() - half && pos.getZ() <= c.getZ() + half;
    }

    public List<PlotData> getAllPlots() {
        return new ArrayList<>(plots.values());
    }

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
        boolean removed = plots.values().removeIf(p -> p.isExpired(currentTick));
        if (removed) markDirty();
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

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

public class PlotManager extends PersistentState {

    private static final String KEY = "secure_plots_data";

    // Primary map: block position of the plot block → plot data
    private final Map<BlockPos, PlotData> plots = new HashMap<>();

    // ── Spatial index for O(1) lookup ─────────────────────────────────────────
    // Key: chunk-column "cx,cz" (in chunk coordinates, i.e. blockX >> 4).
    // Value: all plots whose AABB overlaps that chunk column.
    // Built lazily and rebuilt on add/remove.
    private final Map<Long, List<PlotData>> chunkIndex = new HashMap<>();
    private boolean indexDirty = true;

    /** Packs chunk coordinates into a single long key. */
    private static long chunkKey(int cx, int cz) {
        return ((long)(cx & 0xFFFFFFFFL) << 32) | (cz & 0xFFFFFFFFL);
    }

    /** Rebuilds the spatial chunk index from scratch. */
    private void rebuildIndex() {
        chunkIndex.clear();
        for (PlotData data : plots.values()) {
            int half   = data.getSize().getRadius() / 2;
            BlockPos c = data.getCenter();
            int minCx  = (c.getX() - half) >> 4;
            int maxCx  = (c.getX() + half) >> 4;
            int minCz  = (c.getZ() - half) >> 4;
            int maxCz  = (c.getZ() + half) >> 4;
            for (int cx = minCx; cx <= maxCx; cx++) {
                for (int cz = minCz; cz <= maxCz; cz++) {
                    chunkIndex.computeIfAbsent(chunkKey(cx, cz), k -> new ArrayList<>()).add(data);
                }
            }
        }
        indexDirty = false;
    }

    private void ensureIndex() {
        if (indexDirty) rebuildIndex();
    }

    // ── Secondary index: owner → plots ───────────────────────────────────────
    // Avoids O(n) iteration in getPlayerPlots() and updateOwnerSeen().
    private final Map<UUID, List<PlotData>> ownerIndex = new HashMap<>();

    private void addToOwnerIndex(PlotData data) {
        ownerIndex.computeIfAbsent(data.getOwnerId(), k -> new ArrayList<>()).add(data);
    }

    private void removeFromOwnerIndex(PlotData data) {
        List<PlotData> list = ownerIndex.get(data.getOwnerId());
        if (list != null) { list.remove(data); if (list.isEmpty()) ownerIndex.remove(data.getOwnerId()); }
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
        if (cfg != null && cfg.allowNestedPlots) return true;

        int buffer = cfg != null ? cfg.plotBuffer : 15;
        int half   = size.getRadius() / 2;
        int minX = center.getX() - half - buffer,  maxX = center.getX() + half + buffer;
        int minZ = center.getZ() - half - buffer,  maxZ = center.getZ() + half + buffer;

        // Use chunk index to only check nearby plots
        ensureIndex();
        Set<PlotData> candidates = new HashSet<>();
        int minCx = minX >> 4, maxCx = maxX >> 4;
        int minCz = minZ >> 4, maxCz = maxZ >> 4;
        for (int cx = minCx; cx <= maxCx; cx++)
            for (int cz = minCz; cz <= maxCz; cz++) {
                List<PlotData> bucket = chunkIndex.get(chunkKey(cx, cz));
                if (bucket != null) candidates.addAll(bucket);
            }

        for (PlotData other : candidates) {
            int oh    = other.getSize().getRadius() / 2;
            int oMinX = other.getCenter().getX() - oh, oMaxX = other.getCenter().getX() + oh;
            int oMinZ = other.getCenter().getZ() - oh, oMaxZ = other.getCenter().getZ() + oh;
            if (minX <= oMaxX && maxX >= oMinX && minZ <= oMaxZ && maxZ >= oMinZ) return false;
        }
        return true;
    }

    public void addPlot(PlotData data) {
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg != null && cfg.maxPlotsPerPlayer > 0) {
            List<PlotData> owned = ownerIndex.getOrDefault(data.getOwnerId(), Collections.emptyList());
            if (owned.size() >= cfg.maxPlotsPerPlayer) return;
        }

        String base = data.getOwnerName() + "'s Plot";
        String name = base;
        int counter = 2;
        while (isNameTaken(data.getOwnerId(), name)) name = base + " " + counter++;
        data.setPlotName(name);
        plots.put(data.getCenter(), data);
        addToOwnerIndex(data);
        indexDirty = true;
        markDirty();
    }

    public boolean isNameTaken(UUID ownerId, String name) {
        List<PlotData> owned = ownerIndex.getOrDefault(ownerId, Collections.emptyList());
        return owned.stream().anyMatch(p -> p.getPlotName().equalsIgnoreCase(name));
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public void removePlot(BlockPos center) {
        PlotData data = plots.remove(center);
        if (data != null) {
            removeFromOwnerIndex(data);
            indexDirty = true;
        }
        markDirty();
    }

    public PlotData getPlot(BlockPos center) { return plots.get(center); }

    /**
     * O(k) spatial lookup where k = plots in the player's chunk column only.
     * Previously O(n) over all plots.
     */
    public PlotData getPlotAt(BlockPos pos) {
        ensureIndex();
        int cx = pos.getX() >> 4;
        int cz = pos.getZ() >> 4;
        List<PlotData> bucket = chunkIndex.get(chunkKey(cx, cz));
        if (bucket == null) return null;
        for (PlotData data : bucket)
            if (isInsidePlot(pos, data)) return data;
        return null;
    }

    public boolean isInsidePlot(BlockPos pos, PlotData data) {
        int half  = data.getSize().getRadius() / 2;
        BlockPos c = data.getCenter();
        return pos.getX() >= c.getX() - half && pos.getX() <= c.getX() + half
            && pos.getZ() >= c.getZ() - half && pos.getZ() <= c.getZ() + half;
    }

    public List<PlotData> getAllPlots() { return new ArrayList<>(plots.values()); }

    /** O(1) via owner index instead of O(n) stream. */
    public List<PlotData> getPlayerPlots(UUID playerId) {
        return new ArrayList<>(ownerIndex.getOrDefault(playerId, Collections.emptyList()));
    }

    /** O(k) via owner index instead of O(n). */
    public void updateOwnerSeen(UUID ownerId, long currentTick) {
        List<PlotData> owned = ownerIndex.get(ownerId);
        if (owned == null || owned.isEmpty()) return;
        for (PlotData data : owned) data.setLastOwnerSeenTick(currentTick);
        markDirty();
    }

    public void removeExpiredPlots(long currentTick) {
        List<BlockPos> toRemove = new ArrayList<>();
        for (PlotData data : plots.values())
            if (data.isExpired(currentTick)) toRemove.add(data.getCenter());
        if (!toRemove.isEmpty()) {
            for (BlockPos pos : toRemove) removePlot(pos);
            markDirty();
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private void readNbt(NbtCompound nbt) {
        plots.clear();
        ownerIndex.clear();
        chunkIndex.clear();
        NbtList list = nbt.getList("plots", 10);
        for (int i = 0; i < list.size(); i++) {
            PlotData data = PlotData.fromNbt(list.getCompound(i));
            plots.put(data.getCenter(), data);
            addToOwnerIndex(data);
        }
        indexDirty = true; // rebuild spatial index on first use
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtList list = new NbtList();
        for (PlotData data : plots.values()) list.add(data.toNbt());
        nbt.put("plots", list);
        return nbt;
    }
}
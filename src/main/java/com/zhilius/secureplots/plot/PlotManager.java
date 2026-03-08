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

    private static final String KEY   = "secure_plots_data";
    private static final int BUFFER   = 15;

    private final Map<BlockPos, PlotData> plots = new HashMap<>();

    public static PlotManager getOrCreate(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        return manager.getOrCreate(new PersistentState.Type<>(
                PlotManager::new,
                (nbt, registries) -> {
                    PlotManager pm = new PlotManager();
                    pm.readNbt(nbt);
                    return pm;
                },
                null), KEY);
    }

    // ── Verificación de placement ──────────────────────────────────────────────

    /**
     * Retorna true si la plot PUEDE colocarse:
     *  1. No choca con otras plots existentes (+buffer)
     *  2. No está en una zona bloqueada por config
     */
    public boolean canPlace(BlockPos center, PlotSize size) {
        int halfSize = size.radius / 2;
        int cx = center.getX(), cz = center.getZ();

        // 1. Colisión con otras plots
        int minX = cx - halfSize - BUFFER;
        int maxX = cx + halfSize + BUFFER;
        int minZ = cz - halfSize - BUFFER;
        int maxZ = cz + halfSize + BUFFER;

        for (PlotData other : plots.values()) {
            int otherHalf = other.getSize().radius / 2;
            int oMinX = other.getCenter().getX() - otherHalf;
            int oMaxX = other.getCenter().getX() + otherHalf;
            int oMinZ = other.getCenter().getZ() - otherHalf;
            int oMaxZ = other.getCenter().getZ() + otherHalf;
            if (minX <= oMaxX && maxX >= oMinX && minZ <= oMaxZ && maxZ >= oMinZ)
                return false;
        }

        // 2. Zonas bloqueadas por config
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg != null && cfg.isBlockedByZone(cx, cz, halfSize)) {
            return false;
        }

        return true;
    }

    /**
     * Retorna el label de la zona bloqueada que impide colocar, o null si no hay.
     * Útil para darle feedback específico al jugador.
     */
    public String getBlockingZoneLabel(BlockPos center, PlotSize size) {
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg == null) return null;
        int halfSize = size.radius / 2;
        for (SecurePlotsConfig.BlockedZone zone : cfg.blockedZones) {
            if (zone.overlapsPlot(center.getX(), center.getZ(), halfSize)) {
                return zone.label.isEmpty() ? zone.type : zone.label;
            }
        }
        return null;
    }

    // ── CRUD ──────────────────────────────────────────────────────────────────

    public void addPlot(PlotData data) {
        String baseName  = data.getOwnerName() + "'s Plot";
        String finalName = baseName;
        int counter = 2;
        while (isNameTaken(data.getOwnerId(), finalName)) {
            finalName = baseName + " " + counter;
            counter++;
        }
        data.setPlotName(finalName);
        plots.put(data.getCenter(), data);
        markDirty();
    }

    public boolean isNameTaken(UUID ownerId, String name) {
        for (PlotData p : plots.values()) {
            if (p.getOwnerId().equals(ownerId) && p.getPlotName().equalsIgnoreCase(name))
                return true;
        }
        return false;
    }

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
        int half = data.getSize().radius / 2;
        BlockPos c = data.getCenter();
        return pos.getX() >= c.getX() - half && pos.getX() <= c.getX() + half
                && pos.getZ() >= c.getZ() - half && pos.getZ() <= c.getZ() + half;
    }

    public List<PlotData> getAllPlots() {
        return new ArrayList<>(plots.values());
    }

    public List<PlotData> getPlayerPlots(UUID playerId) {
        List<PlotData> result = new ArrayList<>();
        for (PlotData data : plots.values()) {
            if (data.getOwnerId().equals(playerId)) result.add(data);
        }
        return result;
    }

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
        List<BlockPos> toRemove = new ArrayList<>();
        for (Map.Entry<BlockPos, PlotData> entry : plots.entrySet()) {
            if (entry.getValue().isExpired(currentTick)) toRemove.add(entry.getKey());
        }
        for (BlockPos pos : toRemove) plots.remove(pos);
        if (!toRemove.isEmpty()) markDirty();
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

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

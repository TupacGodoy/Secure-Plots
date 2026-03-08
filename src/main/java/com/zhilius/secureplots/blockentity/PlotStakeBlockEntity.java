package com.zhilius.secureplots.blockentity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Block entity para la Estaca de Parcela.
 * Almacena: owner, sessionId (grupo de 4 estacas), índice (0-3), plotCenter, subdivisionName.
 */
public class PlotStakeBlockEntity extends BlockEntity {

    private UUID ownerId;
    private UUID sessionId;
    private int  stakeIndex = 0;
    private BlockPos plotCenter;
    private String subdivisionName = "";

    public PlotStakeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLOT_STAKE_BLOCK_ENTITY, pos, state);
    }

    // ── Tick (para partículas servidor-side) ──────────────────────────────────

    public static void tick(World world, BlockPos pos, BlockState state, PlotStakeBlockEntity be) {
        // Partículas manejadas en cliente vía packet al colocar
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────

    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID id) { this.ownerId = id; markDirty(); }

    public UUID getSessionId() { return sessionId; }
    public void setSessionId(UUID id) { this.sessionId = id; markDirty(); }

    public int getStakeIndex() { return stakeIndex; }
    public void setStakeIndex(int i) { this.stakeIndex = i; markDirty(); }

    public BlockPos getPlotCenter() { return plotCenter; }
    public void setPlotCenter(BlockPos pos) { this.plotCenter = pos; markDirty(); }

    public String getSubdivisionName() { return subdivisionName; }
    public void setSubdivisionName(String name) { this.subdivisionName = name != null ? name : ""; markDirty(); }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (ownerId   != null) nbt.putString("ownerId",   ownerId.toString());
        if (sessionId != null) nbt.putString("sessionId", sessionId.toString());
        nbt.putInt("stakeIndex", stakeIndex);
        nbt.putString("subdivisionName", subdivisionName != null ? subdivisionName : "");
        if (plotCenter != null) {
            nbt.putInt("plotCX", plotCenter.getX());
            nbt.putInt("plotCY", plotCenter.getY());
            nbt.putInt("plotCZ", plotCenter.getZ());
        }
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        try { if (nbt.contains("ownerId"))   ownerId   = UUID.fromString(nbt.getString("ownerId")); }   catch (Exception ignored) {}
        try { if (nbt.contains("sessionId")) sessionId = UUID.fromString(nbt.getString("sessionId")); } catch (Exception ignored) {}
        stakeIndex = nbt.getInt("stakeIndex");
        subdivisionName = nbt.getString("subdivisionName");
        if (nbt.contains("plotCX")) {
            plotCenter = new BlockPos(nbt.getInt("plotCX"), nbt.getInt("plotCY"), nbt.getInt("plotCZ"));
        }
    }
}

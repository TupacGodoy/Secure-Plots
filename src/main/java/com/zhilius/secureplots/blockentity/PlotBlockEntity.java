package com.zhilius.secureplots.blockentity;

import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class PlotBlockEntity extends BlockEntity {

    private static final int PARTICLE_INTERVAL = 20;
    private int tickCounter = 0;

    public PlotBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLOT_BLOCK_ENTITY, pos, state);
    }

    public static void tick(World world, BlockPos pos, BlockState state, PlotBlockEntity be) {
        if (world.isClient)
            return;

        be.tickCounter++;

        if (be.tickCounter % 6000 == 0) {
            PlotManager manager = PlotManager.getOrCreate((ServerWorld) world);
            PlotData data = manager.getPlot(pos);
            if (data != null && data.isExpired(world.getTime())) {
                manager.removePlot(pos);
                world.breakBlock(pos, false);
            }
        }
    }

    public void openScreen(ServerPlayerEntity player) {
        PlotManager manager = PlotManager.getOrCreate((ServerWorld) world);
        PlotData data = manager.getPlot(pos);
        if (data != null) {
            ModPackets.sendOpenPlotScreen(player, pos, data);
        }
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
    }
}
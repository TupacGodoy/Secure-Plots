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
package com.zhilius.secureplots.mixin;

import com.zhilius.secureplots.config.SecurePlotsConfig;
import com.zhilius.secureplots.plot.ProtectedAreaManager;
import net.minecraft.block.FluidBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to prevent liquid flow in protected areas.
 */
@Mixin(FluidBlock.class)
public class LiquidFlowMixin {

    @Inject(method = "flow", at = @At("HEAD"), cancellable = true)
    private void onFlow(net.minecraft.world.WorldAccess world, BlockPos pos, net.minecraft.block.BlockState state, CallbackInfo ci) {
        if (world.isClient() || SecurePlotsConfig.INSTANCE == null) return;

        ServerWorld serverWorld = (ServerWorld) world;
        ProtectedAreaManager manager = ProtectedAreaManager.getOrCreate(serverWorld);
        String dimension = serverWorld.getRegistryKey().getValue().toString();

        if (manager.isLiquidProtected(pos, dimension)) {
            ci.cancel();
        }
    }
}

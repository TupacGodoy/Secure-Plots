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
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to intercept container access (chests, barrels, etc.) for protected areas.
 */
@Mixin(GenericContainerScreenHandler.class)
public class GenericContainerScreenHandlerMixin {

    @Inject(method = "canPlayerUse", at = @At("HEAD"), cancellable = true)
    private void onCanPlayerUse(CallbackInfoReturnable<Boolean> cir) {
        GenericContainerScreenHandler handler = (GenericContainerScreenHandler) (Object) this;
        PlayerInventory playerInv = handler.getPlayerInventory();

        if (!(playerInv.player instanceof ServerPlayerEntity player)) return;

        World world = player.getWorld();
        if (world.isClient || SecurePlotsConfig.INSTANCE == null
                || SecurePlotsConfig.INSTANCE.protectedAreas == null
                || SecurePlotsConfig.INSTANCE.protectedAreas.isEmpty()) return;

        BlockPos pos = handler.getPos().orElse(player.getBlockPos());
        ProtectedAreaManager paManager = ProtectedAreaManager.getOrCreate((ServerWorld) world);
        String dimension = world.getRegistryKey().getValue().toString();

        if (!paManager.canAccessContainers(player, pos, dimension)) {
            player.sendMessage(net.minecraft.text.Text.literal("§c✗ You cannot access this container (protected area)").formatted(net.minecraft.util.Formatting.RED), true);
            cir.setReturnValue(false);
            cir.cancel();
        }
    }
}

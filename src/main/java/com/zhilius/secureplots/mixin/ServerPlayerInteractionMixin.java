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
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin into ServerPlayerInteractionManager to intercept block break and interact events
 * for protected area enforcement.
 */
@Mixin(net.minecraft.server.network.ServerPlayerInteractionManager.class)
public class ServerPlayerInteractionMixin {

    @Inject(method = "tryBreakBlock", at = @At("HEAD"), cancellable = true)
    private void onTryBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        net.minecraft.server.network.ServerPlayerInteractionManager manager =
            (net.minecraft.server.network.ServerPlayerInteractionManager) (Object) this;
        ServerPlayerEntity player = manager.player;
        World world = player.getWorld();

        if (!world.isClient && SecurePlotsConfig.INSTANCE != null
                && SecurePlotsConfig.INSTANCE.protectedAreas != null
                && !SecurePlotsConfig.INSTANCE.protectedAreas.isEmpty()) {

            ProtectedAreaManager paManager = ProtectedAreaManager.getOrCreate((ServerWorld) world);
            String dimension = world.getRegistryKey().getValue().toString();

            if (!paManager.canBreak(player, pos, dimension)) {
                player.sendMessage(net.minecraft.text.Text.literal("§c✗ You cannot break blocks here (protected area)").formatted(net.minecraft.util.Formatting.RED), true);
                cir.setReturnValue(false);
                cir.cancel();
            }
        }
    }

    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    private void onInteractBlock(ServerPlayerEntity player, World world, BlockState state,
                                  BlockPos pos, Hand hand, BlockHitResult hit,
                                  CallbackInfoReturnable<ActionResult> cir) {
        if (!world.isClient && SecurePlotsConfig.INSTANCE != null
                && SecurePlotsConfig.INSTANCE.protectedAreas != null
                && !SecurePlotsConfig.INSTANCE.protectedAreas.isEmpty()) {

            ProtectedAreaManager paManager = ProtectedAreaManager.getOrCreate((ServerWorld) world);
            String dimension = world.getRegistryKey().getValue().toString();

            if (!paManager.canInteract(player, pos, dimension)) {
                player.sendMessage(net.minecraft.text.Text.literal("§c✗ You cannot interact here (protected area)").formatted(net.minecraft.util.Formatting.RED), true);
                cir.setReturnValue(ActionResult.FAIL);
                cir.cancel();
            }
        }
    }

    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    private void onInteractItem(ServerPlayerEntity player, World world, Hand hand,
                                 CallbackInfoReturnable<ActionResult> cir) {
        if (!world.isClient && SecurePlotsConfig.INSTANCE != null
                && SecurePlotsConfig.INSTANCE.protectedAreas != null
                && !SecurePlotsConfig.INSTANCE.protectedAreas.isEmpty()) {

            BlockPos pos = player.getBlockPos();
            ProtectedAreaManager paManager = ProtectedAreaManager.getOrCreate((ServerWorld) world);
            String dimension = world.getRegistryKey().getValue().toString();

            // Check if player is in a protected area that requires auth
            var areas = paManager.getAreasAt(pos, dimension);
            for (var area : areas) {
                if (area.requireAuth && !paManager.isPlayerAllowed(area, player)) {
                    player.sendMessage(net.minecraft.text.Text.literal("§c✗ You cannot use items here (protected area)").formatted(net.minecraft.util.Formatting.RED), true);
                    cir.setReturnValue(ActionResult.FAIL);
                    cir.cancel();
                    return;
                }
            }
        }
    }

    @Inject(method = "tryPlaceBlock", at = @At("HEAD"), cancellable = true)
    private void onTryPlaceBlock(ServerPlayerEntity player, World world, BlockPos pos,
                                  BlockHitResult hit, CallbackInfoReturnable<Boolean> cir) {
        if (!world.isClient && SecurePlotsConfig.INSTANCE != null
                && SecurePlotsConfig.INSTANCE.protectedAreas != null
                && !SecurePlotsConfig.INSTANCE.protectedAreas.isEmpty()) {

            ProtectedAreaManager paManager = ProtectedAreaManager.getOrCreate((ServerWorld) world);
            String dimension = world.getRegistryKey().getValue().toString();

            if (!paManager.canPlace(player, pos, dimension)) {
                player.sendMessage(net.minecraft.text.Text.literal("§c✗ You cannot place blocks here (protected area)").formatted(net.minecraft.util.Formatting.RED), true);
                cir.setReturnValue(false);
                cir.cancel();
            }
        }
    }
}

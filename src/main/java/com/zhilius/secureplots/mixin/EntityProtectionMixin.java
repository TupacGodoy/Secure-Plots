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
import net.minecraft.entity.Entity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin to protect entities in protected areas.
 */
@Mixin(Entity.class)
public class EntityProtectionMixin {

    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    private void onDamage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;
        ServerWorld world = (ServerWorld) entity.getWorld();

        if (world == null || SecurePlotsConfig.INSTANCE == null) return;

        ProtectedAreaManager manager = ProtectedAreaManager.getOrCreate(world);
        String dimension = world.getRegistryKey().getValue().toString();
        BlockPos pos = entity.getBlockPos();

        if (manager.isEntityProtected(pos, dimension)) {
            // Allow damage from admins or owners
            if (source.getAttacker() instanceof net.minecraft.server.network.ServerPlayerEntity attacker) {
                if (!manager.isPlayerAllowed(manager.getAreaAt(pos, dimension), attacker)) {
                    attacker.sendMessage(net.minecraft.text.Text.literal("§c✗ You cannot damage entities here (protected area)").formatted(net.minecraft.util.Formatting.RED), true);
                    cir.setReturnValue(false);
                    cir.cancel();
                }
            } else {
                // Block all non-player damage
                cir.setReturnValue(false);
                cir.cancel();
            }
        }
    }
}

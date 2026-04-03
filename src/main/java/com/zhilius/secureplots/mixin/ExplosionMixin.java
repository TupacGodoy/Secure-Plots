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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Mixin to cancel explosions in protected areas.
 */
@Mixin(Explosion.class)
public class ExplosionMixin {

    @Shadow @Final private World world;
    @Shadow @Final private double x;
    @Shadow @Final private double y;
    @Shadow @Final private double z;

    @Inject(method = "affectWorld", at = @At("HEAD"), cancellable = true)
    private void onAffectWorld(boolean particles, CallbackInfo ci) {
        Explosion explosion = (Explosion) (Object) this;

        if (!(world instanceof ServerWorld serverWorld) || SecurePlotsConfig.INSTANCE == null) return;

        ProtectedAreaManager manager = ProtectedAreaManager.getOrCreate(serverWorld);
        String dimension = serverWorld.getRegistryKey().getValue().toString();

        // Check if explosion origin is in a protected area
        BlockPos origin = BlockPos.ofFloored(x, y, z);
        if (manager.isExplosionProtected(origin, dimension)) {
            ci.cancel();
        }
    }
}

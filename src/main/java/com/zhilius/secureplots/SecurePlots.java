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
package com.zhilius.secureplots;

import com.zhilius.secureplots.block.ModBlocks;
import com.zhilius.secureplots.blockentity.ModBlockEntities;
import com.zhilius.secureplots.command.SpCommand;
import com.zhilius.secureplots.config.SecurePlotsConfig;
import com.zhilius.secureplots.item.ModItems;
import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.hologram.PlotHologram;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecurePlots implements ModInitializer {

    public static final String MOD_ID = "secure-plots";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Inicializando Secure Plots...");

        // Load config
        SecurePlotsConfig.load();

        // Register content
        ModBlocks.initialize();
        ModBlockEntities.initialize();
        ModItems.initialize();

        // Register network packets
        ModPackets.registerServerHandlers();

        // Register commands
        SpCommand.register();

        // Register hologram ticker
        PlotHologram.registerTicker();

        // Register chat listener for pending rename/add
        com.zhilius.secureplots.screen.PlotChatListener.register();

        // Register area entry HUD messages
        com.zhilius.secureplots.plot.PlotAreaTracker.register();

        // Register protection events
        registerProtectionEvents();

        // Blueprint works from both hands
        registerBlueprintOffhand();

        // Track owner activity for inactivity expiry
        registerActivityTracking();

        LOGGER.info("Secure Plots listo!");
    }

    // How many blocks outside the plot border members can still interact from
    private static final int MEMBER_REACH_BONUS = 5;

    private void registerActivityTracking() {
        // Update lastOwnerSeenTick whenever a player joins or leaves the server
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.player;
            var world = server.getOverworld();
            long tick = world.getTime();
            com.zhilius.secureplots.plot.PlotManager.getOrCreate(world)
                .updateOwnerSeen(player.getUuid(), tick);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var player = handler.player;
            var world = server.getOverworld();
            long tick = world.getTime();
            com.zhilius.secureplots.plot.PlotManager.getOrCreate(world)
                .updateOwnerSeen(player.getUuid(), tick);
            // Revoke any fly granted by SecurePlots
            com.zhilius.secureplots.plot.PlotAreaTracker.onPlayerLeave(player);
        });
    }

    private void registerBlueprintOffhand() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient) return TypedActionResult.pass(player.getStackInHand(hand));
            // Only handle off-hand; main-hand is already handled by Item.use()
            if (hand != Hand.OFF_HAND) return TypedActionResult.pass(player.getStackInHand(hand));
            net.minecraft.item.ItemStack offStack = player.getStackInHand(Hand.OFF_HAND);
            if (!(offStack.getItem() instanceof com.zhilius.secureplots.item.PlotblueprintItem blueprint))
                return TypedActionResult.pass(offStack);
            // Delegate to the item's use() method
            return blueprint.use(world, player, Hand.OFF_HAND);
        });
    }

    private void registerProtectionEvents() {
        // Breaking blocks: only allowed if player has permission AND
        // the target block is inside the plot
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient) return ActionResult.PASS;
            var sw = (net.minecraft.server.world.ServerWorld) world;
            var manager = com.zhilius.secureplots.plot.PlotManager.getOrCreate(sw);
            var plot = manager.getPlotAt(pos);
            if (plot == null) return ActionResult.PASS;
            // Has direct permission → always allow break
            if (plot.canBuild(player.getUuid())) return ActionResult.PASS;
            player.sendMessage(Text.literal("✗ This area is protected.").formatted(Formatting.RED), true);
            return ActionResult.FAIL;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            var sw = (net.minecraft.server.world.ServerWorld) world;
            var manager = com.zhilius.secureplots.plot.PlotManager.getOrCreate(sw);
            net.minecraft.util.math.BlockPos target = hitResult.getBlockPos();
            boolean isPlacing = player.getStackInHand(hand).getItem() instanceof net.minecraft.item.BlockItem;
            net.minecraft.util.math.BlockPos effectiveTarget = isPlacing
                ? target.offset(hitResult.getSide()) : target;
            var plot = manager.getPlotAt(effectiveTarget);
            if (plot == null) return ActionResult.PASS;

            // Direct permission (player is inside or was already checked canBuild)
            if (plot.canBuild(player.getUuid())) return ActionResult.PASS;

            // Extended reach: members standing up to MEMBER_REACH_BONUS blocks outside
            // the plot border can still interact with non-placement actions (doors, levers, etc.)
            if (!isPlacing && plot.canBuild(player.getUuid())) {
                // Player has permission but isn't inside the plot — check distance to border
                net.minecraft.util.math.BlockPos center = plot.getCenter();
                int r = plot.getSize().getRadius();
                net.minecraft.util.math.BlockPos pp = player.getBlockPos();
                int dx = Math.max(0, Math.abs(pp.getX() - center.getX()) - r);
                int dz = Math.max(0, Math.abs(pp.getZ() - center.getZ()) - r);
                if (dx <= MEMBER_REACH_BONUS && dz <= MEMBER_REACH_BONUS) {
                    return ActionResult.PASS;
                }
            }

            player.sendMessage(Text.literal("✗ This area is protected.").formatted(Formatting.RED), true);
            return ActionResult.FAIL;
        });
    }
}

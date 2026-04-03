/*
 * SecurePlots - A Fabric mod for Minecraft 1.21.1
 * Copyright (C) 2025 TupacGodoy
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.zhilius.secureplots;

import com.zhilius.secureplots.block.ModBlocks;
import com.zhilius.secureplots.blockentity.ModBlockEntities;
import com.zhilius.secureplots.command.SpCommand;
import com.zhilius.secureplots.config.BorderConfig;
import com.zhilius.secureplots.config.SecurePlotsConfig;
import com.zhilius.secureplots.hologram.PlotHologram;
import com.zhilius.secureplots.item.ModItems;
import com.zhilius.secureplots.item.PlotblueprintItem;
import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.plot.PlotAreaTracker;
import com.zhilius.secureplots.plot.PlotData.Permission;
import com.zhilius.secureplots.plot.PlotManager;
import com.zhilius.secureplots.plot.ProtectedAreaTracker;
import com.zhilius.secureplots.recipe.DynamicRecipeManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class SecurePlots implements ModInitializer {

    public static final String MOD_ID = "secure-plots";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** How many blocks outside the plot border members can still interact from. */
    private static final int MEMBER_REACH_BONUS = 5;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Secure Plots...");

        boolean firstRun = !new File(
            FabricLoader.getInstance().getConfigDir().toFile(),
            "secure_plots.json").exists();

        SecurePlotsConfig.load();
        BorderConfig.load();

        ModBlocks.initialize();
        ModBlockEntities.initialize();
        ModItems.initialize();

        ModPackets.registerServerHandlers();
        SpCommand.register();
        PlotHologram.registerTicker();
        DynamicRecipeManager.register();
        PlotAreaTracker.register();
        ProtectedAreaTracker.register();

        registerProtectionEvents();
        registerBlueprintOffhand();
        registerActivityTracking();

        LOGGER.info("Secure Plots ready!");

        if (firstRun) {
            LOGGER.info("  ╔══════════════════════════════════════════╗");
            LOGGER.info("  ║        Thanks for using Secure Plots!    ║");
            LOGGER.info("  ║  If you enjoy it, consider supporting:   ║");
            LOGGER.info("  ║       https://ko-fi.com/zhilius           ║");
            LOGGER.info("  ╚══════════════════════════════════════════╝");
        }
    }

    // ── Event registration ────────────────────────────────────────────────────

    private void registerActivityTracking() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.player;
            var world  = server.getOverworld();
            PlotManager.getOrCreate(world).updateOwnerSeen(player.getUuid(), world.getTime());
            ModPackets.sendSyncBorderConfig(player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var player = handler.player;
            var world  = server.getOverworld();
            PlotManager.getOrCreate(world).updateOwnerSeen(player.getUuid(), world.getTime());
            PlotAreaTracker.onPlayerLeave(player);
            ProtectedAreaTracker.onPlayerDisconnect(player.getUuid());
        });
    }

    private void registerBlueprintOffhand() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient || hand != Hand.OFF_HAND)
                return TypedActionResult.pass(player.getStackInHand(hand));

            ItemStack offStack = player.getStackInHand(Hand.OFF_HAND);
            if (!(offStack.getItem() instanceof PlotblueprintItem blueprint))
                return TypedActionResult.pass(offStack);

            // Skip if main hand also has a blueprint (it will handle the action)
            ItemStack mainStack = player.getMainHandStack();
            if (!mainStack.isEmpty() && mainStack.getItem() instanceof PlotblueprintItem)
                return TypedActionResult.pass(offStack);

            return blueprint.use(world, player, Hand.OFF_HAND);
        });
    }

    private void registerProtectionEvents() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient) return ActionResult.PASS;
            SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
            if (cfg == null || !cfg.enableProtection) return ActionResult.PASS;

            var plot = PlotManager.getOrCreate((ServerWorld) world).getPlotAt(pos);
            if (plot == null || plot.canBuild(player.getUuid())) return ActionResult.PASS;

            player.sendMessage(Text.translatable("sp.protected"), true);
            return ActionResult.FAIL;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
            if (cfg == null || !cfg.enableProtection) return ActionResult.PASS;

            boolean isPlacing  = player.getStackInHand(hand).getItem() instanceof BlockItem;
            BlockPos target    = hitResult.getBlockPos();
            BlockPos effective = isPlacing ? target.offset(hitResult.getSide()) : target;

            var plot = PlotManager.getOrCreate((ServerWorld) world).getPlotAt(effective);
            if (plot == null || plot.canBuild(player.getUuid())) return ActionResult.PASS;

            // Extended reach: members can interact from just outside the border
            if (!isPlacing && plot.hasPermission(player.getUuid(), Permission.INTERACT)
                    && withinReach(player.getBlockPos(), plot.getCenter(), plot.getSize().getRadius())) {
                return ActionResult.PASS;
            }

            player.sendMessage(Text.translatable("sp.protected"), true);
            return ActionResult.FAIL;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
            if (cfg == null || !cfg.enablePvpControl) return ActionResult.PASS;
            if (!(entity instanceof PlayerEntity)) return ActionResult.PASS;

            var plot = PlotManager.getOrCreate((ServerWorld) world).getPlotAt(player.getBlockPos());
            if (plot == null || plot.hasPermission(player.getUuid(), Permission.PVP))
                return ActionResult.PASS;

            player.sendMessage(Text.translatable("sp.pvp.disabled"), true);
            return ActionResult.FAIL;
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns true if the player is within MEMBER_REACH_BONUS blocks of the plot border. */
    private static boolean withinReach(BlockPos player, BlockPos center, int radius) {
        int dx = Math.max(0, Math.abs(player.getX() - center.getX()) - radius);
        int dz = Math.max(0, Math.abs(player.getZ() - center.getZ()) - radius);
        return dx <= MEMBER_REACH_BONUS && dz <= MEMBER_REACH_BONUS;
    }
}
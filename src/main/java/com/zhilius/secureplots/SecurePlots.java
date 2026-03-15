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
import com.zhilius.secureplots.config.SecurePlotsConfig;
import com.zhilius.secureplots.item.ModItems;
import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.hologram.PlotHologram;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecurePlots implements ModInitializer {

    public static final String MOD_ID = "secure-plots";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // How many blocks outside the plot border members can still interact from
    private static final int MEMBER_REACH_BONUS = 5;

    private static final String KOFI_URL = "https://ko-fi.com/zhilius";

    @Override
    public void onInitialize() {
        LOGGER.info("Inicializando Secure Plots...");

        boolean firstRun = !new java.io.File(
            net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().toFile(),
            "secure_plots.json").exists();

        SecurePlotsConfig.load();
        com.zhilius.secureplots.config.BorderConfig.load();

        ModBlocks.initialize();
        ModBlockEntities.initialize();
        ModItems.initialize();

        ModPackets.registerServerHandlers();
        SpCommand.register();
        PlotHologram.registerTicker();

        com.zhilius.secureplots.screen.PlotChatListener.register();
        com.zhilius.secureplots.plot.PlotAreaTracker.register();

        registerProtectionEvents();
        registerBlueprintOffhand();
        registerActivityTracking();

        LOGGER.info("Secure Plots listo!");

        if (firstRun) {
            LOGGER.info("  ");
            LOGGER.info("  ╔══════════════════════════════════════════╗");
            LOGGER.info("  ║        Thanks for using Secure Plots!    ║");
            LOGGER.info("  ║  If you enjoy it, consider supporting:   ║");
            LOGGER.info("  ║       https://ko-fi.com/zhilius           ║");
            LOGGER.info("  ╚══════════════════════════════════════════╝");
            LOGGER.info("  ");
        }
    }

    private void registerActivityTracking() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.player;
            var world  = server.getOverworld();
            long tick  = world.getTime();
            com.zhilius.secureplots.plot.PlotManager.getOrCreate(world)
                .updateOwnerSeen(player.getUuid(), tick);
            ModPackets.sendSyncBorderConfig(player);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var player = handler.player;
            var world  = server.getOverworld();
            long tick  = world.getTime();
            com.zhilius.secureplots.plot.PlotManager.getOrCreate(world)
                .updateOwnerSeen(player.getUuid(), tick);
            com.zhilius.secureplots.plot.PlotAreaTracker.onPlayerLeave(player);
        });
    }

    private void registerBlueprintOffhand() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient) return TypedActionResult.pass(player.getStackInHand(hand));
            if (hand != Hand.OFF_HAND) return TypedActionResult.pass(player.getStackInHand(hand));
            net.minecraft.item.ItemStack offStack = player.getStackInHand(Hand.OFF_HAND);
            if (!(offStack.getItem() instanceof com.zhilius.secureplots.item.PlotblueprintItem blueprint))
                return TypedActionResult.pass(offStack);
            // Delegate to the item's use() method only if main hand is not also a blueprint
            net.minecraft.item.ItemStack mainStack = player.getMainHandStack();
            if (!mainStack.isEmpty() && mainStack.getItem() instanceof com.zhilius.secureplots.item.PlotblueprintItem)
                return TypedActionResult.pass(offStack);
            return blueprint.use(world, player, Hand.OFF_HAND);
        });
    }

    private void registerProtectionEvents() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient) return ActionResult.PASS;
            SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
            if (cfg != null && !cfg.enableProtection) return ActionResult.PASS;
            var sw      = (net.minecraft.server.world.ServerWorld) world;
            var manager = com.zhilius.secureplots.plot.PlotManager.getOrCreate(sw);
            var plot    = manager.getPlotAt(pos);
            if (plot == null) return ActionResult.PASS;
            if (plot.canBuild(player.getUuid())) return ActionResult.PASS;
            player.sendMessage(Text.translatable("sp.protected"), true);
            return ActionResult.FAIL;
        });

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
            if (cfg != null && !cfg.enableProtection) return ActionResult.PASS;
            var sw      = (net.minecraft.server.world.ServerWorld) world;
            var manager = com.zhilius.secureplots.plot.PlotManager.getOrCreate(sw);
            net.minecraft.util.math.BlockPos target = hitResult.getBlockPos();
            boolean isPlacing = player.getStackInHand(hand).getItem()
                                instanceof net.minecraft.item.BlockItem;
            net.minecraft.util.math.BlockPos effectiveTarget = isPlacing
                ? target.offset(hitResult.getSide()) : target;
            var plot = manager.getPlotAt(effectiveTarget);
            if (plot == null) return ActionResult.PASS;

            // Owner/member with direct BUILD permission — always allow
            if (plot.canBuild(player.getUuid())) return ActionResult.PASS;

            // Extended reach: members standing up to MEMBER_REACH_BONUS blocks outside
            // can still interact with non-placement actions (doors, levers, etc.)
            if (!isPlacing && plot.hasPermission(player.getUuid(),
                    com.zhilius.secureplots.plot.PlotData.Permission.INTERACT)) {
                net.minecraft.util.math.BlockPos center = plot.getCenter();
                int r  = plot.getSize().getRadius();
                net.minecraft.util.math.BlockPos pp = player.getBlockPos();
                int dx = Math.max(0, Math.abs(pp.getX() - center.getX()) - r);
                int dz = Math.max(0, Math.abs(pp.getZ() - center.getZ()) - r);
                if (dx <= MEMBER_REACH_BONUS && dz <= MEMBER_REACH_BONUS) {
                    return ActionResult.PASS;
                }
            }

            player.sendMessage(Text.translatable("sp.protected"), true);
            return ActionResult.FAIL;
        });

        // PVP control: block player-vs-player damage inside plots that have ALLOW_PVP off
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register(
                (player, world, hand, entity, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            SecurePlotsConfig cfgPvp = SecurePlotsConfig.INSTANCE;
            if (cfgPvp != null && !cfgPvp.enablePvpControl) return ActionResult.PASS;
            if (!(entity instanceof net.minecraft.entity.player.PlayerEntity)) return ActionResult.PASS;
            var sw      = (net.minecraft.server.world.ServerWorld) world;
            var manager = com.zhilius.secureplots.plot.PlotManager.getOrCreate(sw);
            var plot    = manager.getPlotAt(player.getBlockPos());
            if (plot == null) return ActionResult.PASS;
            // If the plot allows PVP (via flag or permission), let it through
            if (plot.hasPermission(player.getUuid(), com.zhilius.secureplots.plot.PlotData.Permission.PVP))
                return ActionResult.PASS;
            player.sendMessage(Text.translatable("sp.pvp.disabled"), true);
            return ActionResult.FAIL;
        });
    }
}

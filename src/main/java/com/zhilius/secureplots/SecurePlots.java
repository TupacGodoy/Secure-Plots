package com.zhilius.secureplots;

import com.zhilius.secureplots.block.ModBlocks;
import com.zhilius.secureplots.blockentity.ModBlockEntities;
import com.zhilius.secureplots.command.SpCommand;
import com.zhilius.secureplots.config.SecurePlotsConfig;
import com.zhilius.secureplots.item.ModItems;
import com.zhilius.secureplots.item.PlotStakeItem;
import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.hologram.PlotHologram;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecurePlots implements ModInitializer {

    public static final String MOD_ID = "secure-plots";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Configurable via config (loaded below); fallback default
    private static int memberReachBonus = 5;

    @Override
    public void onInitialize() {
        LOGGER.info("Inicializando Secure Plots...");

        SecurePlotsConfig.load();
        memberReachBonus = SecurePlotsConfig.INSTANCE.memberReachBonus;

        ModBlocks.initialize();
        ModBlockEntities.initialize();
        ModItems.initialize();

        ModPackets.registerServerHandlers();
        SpCommand.register();
        PlotHologram.registerTicker();

        com.zhilius.secureplots.screen.PlotChatListener.register();
        com.zhilius.secureplots.plot.PlotAreaTracker.register();

        registerProtectionEvents();
        registerActivityTracking();

        LOGGER.info("Secure Plots listo!");
    }

    private void registerActivityTracking() {
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
            com.zhilius.secureplots.plot.PlotAreaTracker.onPlayerLeave(player);
        });
    }

    private void registerProtectionEvents() {

        // ── Breaking blocks ───────────────────────────────────────────────────
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient) return ActionResult.PASS;
            var sw = (ServerWorld) world;
            var manager = com.zhilius.secureplots.plot.PlotManager.getOrCreate(sw);
            var plot = manager.getPlotAt(pos);
            if (plot == null) return ActionResult.PASS;

            // Subdivision-aware permission check
            if (plot.canBuildAt(player.getUuid(), pos)) return ActionResult.PASS;

            player.sendMessage(Text.literal("✗ Esta zona está protegida.").formatted(Formatting.RED), true);
            return ActionResult.FAIL;
        });

        // ── Using blocks (interact / place) ───────────────────────────────────
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            var sw = (ServerWorld) world;
            var manager = com.zhilius.secureplots.plot.PlotManager.getOrCreate(sw);

            BlockPos target = hitResult.getBlockPos();
            boolean isPlacing = player.getStackInHand(hand).getItem() instanceof net.minecraft.item.BlockItem;
            BlockPos effectiveTarget = isPlacing ? target.offset(hitResult.getSide()) : target;

            // ── Subdivision tool: intercept before protection check ────────────
            if (player.getStackInHand(hand).getItem() instanceof PlotStakeItem
                    && player instanceof ServerPlayerEntity sp) {
                return PlotStakeItem.onUseBlock(sp, sw, hand, hitResult);
            }

            var plot = manager.getPlotAt(effectiveTarget);
            if (plot == null) return ActionResult.PASS;

            // Subdivision-aware permission
            if (plot.canBuildAt(player.getUuid(), effectiveTarget)) return ActionResult.PASS;

            // Extended reach for interact (not place) from just outside the border
            if (!isPlacing) {
                BlockPos center = plot.getCenter();
                int r = plot.getSize().radius;
                BlockPos pp = player.getBlockPos();
                int dx = Math.max(0, Math.abs(pp.getX() - center.getX()) - r);
                int dz = Math.max(0, Math.abs(pp.getZ() - center.getZ()) - r);
                int reach = SecurePlotsConfig.INSTANCE != null
                        ? SecurePlotsConfig.INSTANCE.memberReachBonus
                        : memberReachBonus;
                if (dx <= reach && dz <= reach && plot.canInteractAt(player.getUuid(), effectiveTarget)) {
                    return ActionResult.PASS;
                }
            }

            player.sendMessage(Text.literal("✗ Esta zona está protegida.").formatted(Formatting.RED), true);
            return ActionResult.FAIL;
        });
    }
}

package com.zhilius.secureplots;

import com.zhilius.secureplots.block.ModBlocks;
import com.zhilius.secureplots.blockentity.ModBlockEntities;
import com.zhilius.secureplots.config.SecurePlotsConfig;
import com.zhilius.secureplots.item.ModItems;
import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.hologram.PlotHologram;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
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

        // Register hologram ticker
        PlotHologram.registerTicker();

        // Protect plots from modification by non-members
        registerProtectionEvents();

        LOGGER.info("Secure Plots listo!");
    }

    private void registerProtectionEvents() {
        // Prevent non-members from breaking blocks inside a plot
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient) return ActionResult.PASS;

            var serverWorld = (net.minecraft.server.world.ServerWorld) world;
            var manager = com.zhilius.secureplots.plot.PlotManager.getOrCreate(serverWorld);
            var plot = manager.getPlotAt(pos);

            if (plot != null && !plot.canBuild(player.getUuid())) {
                player.sendMessage(Text.literal("✗ Esta zona está protegida.").formatted(Formatting.RED), true);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });

        // Prevent non-members from placing blocks inside a plot
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient) return ActionResult.PASS;
            var heldStack = player.getStackInHand(hand);
            if (!(heldStack.getItem() instanceof net.minecraft.item.BlockItem)) return ActionResult.PASS;

            var serverWorld = (net.minecraft.server.world.ServerWorld) world;
            var manager = com.zhilius.secureplots.plot.PlotManager.getOrCreate(serverWorld);
            var plot = manager.getPlotAt(hitResult.getBlockPos().offset(hitResult.getSide()));

            if (plot != null && !plot.canBuild(player.getUuid())) {
                player.sendMessage(Text.literal("✗ Esta zona está protegida.").formatted(Formatting.RED), true);
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }
}

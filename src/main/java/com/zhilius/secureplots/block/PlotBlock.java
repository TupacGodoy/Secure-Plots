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
package com.zhilius.secureplots.block;

import com.mojang.serialization.MapCodec;
import com.zhilius.secureplots.blockentity.PlotBlockEntity;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotManager;
import com.zhilius.secureplots.plot.PlotSize;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item.TooltipContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

// Helper for building translated Text with appended colored values
// Used to avoid verbose chaining in sendMessage calls.

public class PlotBlock extends BlockWithEntity {

    // Tier de este bloque específico (0=bronze, 1=iron/emerald, 2=gold, 3=diamond, 4=netherite)
    private final int tier;

    public PlotBlock(Settings settings, int tier) {
        super(settings);
        this.tier = tier;
    }

    // Constructor sin tier para el codec (usa tier 0 como fallback)
    public PlotBlock(Settings settings) {
        this(settings, 0);
    }

    @Override
    public MapCodec<? extends BlockWithEntity> getCodec() {
        return createCodec(PlotBlock::new);
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new PlotBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state,
            BlockEntityType<T> type) {
        if (world.isClient)
            return null;
        return BlockWithEntity.validateTicker(type,
                com.zhilius.secureplots.blockentity.ModBlockEntities.PLOT_BLOCK_ENTITY,
                (w, pos, s, be) -> PlotBlockEntity.tick(w, pos, s, be));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            com.zhilius.secureplots.plot.PlotManager manager =
                com.zhilius.secureplots.plot.PlotManager.getOrCreate((net.minecraft.server.world.ServerWorld) world);
            com.zhilius.secureplots.plot.PlotData data = manager.getPlot(pos);
            if (data != null) {
                com.zhilius.secureplots.network.ModPackets.sendShowPlotBorder((ServerPlayerEntity) player, data);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, net.minecraft.entity.LivingEntity placer,
            net.minecraft.item.ItemStack stack) {
        if (!world.isClient && placer instanceof ServerPlayerEntity player) {
            PlotManager manager = PlotManager.getOrCreate((net.minecraft.server.world.ServerWorld) world);

            // Usar el PlotSize correspondiente al tier de ESTE bloque
            PlotSize plotSize = PlotSize.fromTier(this.tier);

            if (!manager.canPlace(pos, plotSize)) {
                world.breakBlock(pos, true, player);
                player.sendMessage(Text.translatable("sp.block.too_close"), false);
                return;
            }

            UUID ownerId = player.getUuid();
            String ownerName = player.getName().getString();
            long tick = world.getTime();

            // maxPlotsPerPlayer check (0 = unlimited)
            com.zhilius.secureplots.config.SecurePlotsConfig cfgPl =
                com.zhilius.secureplots.config.SecurePlotsConfig.INSTANCE;
            if (cfgPl != null && cfgPl.maxPlotsPerPlayer > 0) {
                long owned = manager.getPlayerPlots(ownerId).size();
                if (owned >= cfgPl.maxPlotsPerPlayer) {
                    world.breakBlock(pos, true, player);
                    player.sendMessage(Text.translatable("sp.block.max_plots", cfgPl.maxPlotsPerPlayer), false);
                    return;
                }
            }

            PlotData data = new PlotData(ownerId, ownerName, pos, plotSize, tick);
            manager.addPlot(data);

            int r = plotSize.getRadius();
            player.sendMessage(Text.translatable("sp.block.created_header"), false);
            player.sendMessage(Text.translatable("sp.block.created_title").formatted(Formatting.YELLOW, Formatting.BOLD), false);
            player.sendMessage(Text.translatable("sp.block.created_header"), false);
            player.sendMessage(Text.translatable("sp.block.created_size").formatted(Formatting.GRAY)
                    .append(Text.translatable("sp.block.created_size_value", r, r).formatted(Formatting.AQUA)), false);
            player.sendMessage(Text.translatable("sp.block.created_tier").formatted(Formatting.GRAY)
                    .append(Text.literal(plotSize.getDisplayName()).formatted(Formatting.AQUA)), false);
            player.sendMessage(Text.translatable("sp.block.created_hint"), false);
            player.sendMessage(Text.translatable("sp.block.created_header"), false);

            // HUD message (action bar)
            player.sendMessage(
                Text.translatable("sp.block.created_hud", plotSize.getDisplayName(), r, r)
                    .formatted(Formatting.GREEN, Formatting.BOLD),
                true);

            // Sound
            world.playSound(null, pos,
                SoundEvents.UI_TOAST_CHALLENGE_COMPLETE,
                SoundCategory.PLAYERS, 0.6f, 1.2f);

            com.zhilius.secureplots.network.ModPackets.sendOpenPlotScreen(player, pos, data);
            com.zhilius.secureplots.network.ModPackets.sendShowPlotBorder(player, data);
        }
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            com.zhilius.secureplots.config.SecurePlotsConfig cfg =
                com.zhilius.secureplots.config.SecurePlotsConfig.INSTANCE;
            PlotManager manager = PlotManager.getOrCreate((net.minecraft.server.world.ServerWorld) world);
            PlotData data = manager.getPlot(pos);
            if (data != null) {
                int opLevel = cfg != null ? cfg.adminOpLevel : 2;
                boolean isAdmin = player.hasPermissionLevel(opLevel);
                // plotBlocksUnbreakable: non-owners cannot break the block at all
                if (cfg != null && cfg.plotBlocksUnbreakable
                        && !data.getOwnerId().equals(player.getUuid()) && !isAdmin) {
                    player.sendMessage(Text.translatable("sp.block.not_owner"), false);
                    world.setBlockState(pos, state);
                    return state;
                }
                if (!data.getOwnerId().equals(player.getUuid()) && !isAdmin) {
                    player.sendMessage(Text.translatable("sp.block.not_owner"), false);
                    world.setBlockState(pos, state);
                    return state;
                }
                manager.removePlot(pos);
                com.zhilius.secureplots.network.ModPackets.sendHidePlotBorder(
                    (net.minecraft.server.network.ServerPlayerEntity) player, pos);
                player.sendMessage(Text.translatable("sp.block.removed"), false);
            }
        }
        return super.onBreak(world, pos, state, player);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType options) {
        PlotSize plotSize = PlotSize.fromTier(this.tier);
        int size = plotSize.getRadius();
        tooltip.add(Text.translatable("sp.block.tooltip_size", size, size).formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("sp.block.tooltip_tier", this.tier + 1, plotSize.getDisplayName()).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("sp.block.tooltip_hint").formatted(Formatting.DARK_GRAY));
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}
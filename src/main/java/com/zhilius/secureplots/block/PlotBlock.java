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
import com.zhilius.secureplots.blockentity.ModBlockEntities;
import com.zhilius.secureplots.blockentity.PlotBlockEntity;
import com.zhilius.secureplots.config.SecurePlotsConfig;
import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotManager;
import com.zhilius.secureplots.plot.PlotSize;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

public class PlotBlock extends BlockWithEntity {

    /** Tier of this block (0=Bronze … 4=Netherite). */
    private final int tier;

    public PlotBlock(Settings settings, int tier) {
        super(settings);
        this.tier = tier;
    }

    /** No-arg constructor required by codec — defaults to tier 0. */
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
        if (world.isClient) return null;
        return BlockWithEntity.validateTicker(type, ModBlockEntities.PLOT_BLOCK_ENTITY,
            (w, pos, s, be) -> PlotBlockEntity.tick(w, pos, s, be));
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                               PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient) {
            PlotManager manager = PlotManager.getOrCreate((ServerWorld) world);
            PlotData data = manager.getPlot(pos);
            if (data != null)
                ModPackets.sendShowPlotBorder((ServerPlayerEntity) player, data);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state,
                         LivingEntity placer, ItemStack stack) {
        if (world.isClient || !(placer instanceof ServerPlayerEntity player)) return;

        PlotManager manager   = PlotManager.getOrCreate((ServerWorld) world);
        PlotSize    plotSize  = PlotSize.fromTier(this.tier);
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;

        if (!manager.canPlace(pos, plotSize)) {
            world.breakBlock(pos, true, player);
            player.sendMessage(Text.translatable("sp.block.too_close"), false);
            return;
        }

        if (cfg != null && cfg.maxPlotsPerPlayer > 0
                && manager.getPlayerPlots(player.getUuid()).size() >= cfg.maxPlotsPerPlayer) {
            world.breakBlock(pos, true, player);
            player.sendMessage(Text.translatable("sp.block.max_plots", cfg.maxPlotsPerPlayer), false);
            return;
        }

        PlotData data = new PlotData(player.getUuid(), player.getName().getString(), pos, plotSize, world.getTime());
        manager.addPlot(data);

        int r = plotSize.getRadius();
        String sep = "sp.block.created_header";
        player.sendMessage(Text.translatable(sep), false);
        player.sendMessage(Text.translatable("sp.block.created_title").formatted(Formatting.YELLOW, Formatting.BOLD), false);
        player.sendMessage(Text.translatable(sep), false);
        player.sendMessage(Text.translatable("sp.block.created_size").formatted(Formatting.GRAY)
            .append(Text.translatable("sp.block.created_size_value", r, r).formatted(Formatting.AQUA)), false);
        player.sendMessage(Text.translatable("sp.block.created_tier").formatted(Formatting.GRAY)
            .append(Text.literal(plotSize.getDisplayName()).formatted(Formatting.AQUA)), false);
        player.sendMessage(Text.translatable("sp.block.created_hint"), false);
        player.sendMessage(Text.translatable(sep), false);
        // Action bar
        player.sendMessage(
            Text.translatable("sp.block.created_hud", plotSize.getDisplayName(), r, r)
                .formatted(Formatting.GREEN, Formatting.BOLD), true);

        world.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 0.6f, 1.2f);
        ModPackets.sendOpenPlotScreen(player, pos, data);
        ModPackets.sendShowPlotBorder(player, data);
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
            PlotManager manager   = PlotManager.getOrCreate((ServerWorld) world);
            PlotData data         = manager.getPlot(pos);
            if (data != null) {
                int opLevel   = cfg != null ? cfg.adminOpLevel : 2;
                boolean isOwner = data.getOwnerId().equals(player.getUuid());
                boolean isAdmin = player.hasPermissionLevel(opLevel);

                if (!isOwner && !isAdmin) {
                    player.sendMessage(Text.translatable("sp.block.not_owner"), false);
                    world.setBlockState(pos, state);
                    return state;
                }
                manager.removePlot(pos);
                ModPackets.sendHidePlotBorder((ServerPlayerEntity) player, pos);
                player.sendMessage(Text.translatable("sp.block.removed"), false);
            }
        }
        return super.onBreak(world, pos, state, player);
    }

    @Override
    public void appendTooltip(ItemStack stack, net.minecraft.item.Item.TooltipContext context,
                               List<Text> tooltip, TooltipType options) {
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
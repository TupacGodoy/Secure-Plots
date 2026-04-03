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
package com.zhilius.secureplots.item;

import com.zhilius.secureplots.command.SpCommand;
import com.zhilius.secureplots.config.SecurePlotsConfig;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotManager;
import com.zhilius.secureplots.plot.PlotSize;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

/**
 * Selection wand for creating plots by selecting an area.
 * Left-click: Set position 1
 * Right-click: Set position 2
 * Shift+right-click: Create plot at selection
 */
public class PlotSelectorItem extends Item {

    public PlotSelectorItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            if (player.isSneaking()) {
                // Shift+right-click: try to create plot from selection
                return executeCreatePlot(player);
            } else {
                // Right-click: set position 2
                return executeSetPos2(player);
            }
        }
        return TypedActionResult.success(user.getStackInHand(hand));
    }

    @Override
    public ActionResult useOnBlock(net.minecraft.item.ItemUsageContext context) {
        if (!context.getWorld().isClient && context.getPlayer() instanceof ServerPlayerEntity player) {
            if (player.isSneaking()) {
                executeCreatePlot(player);
            } else {
                executeSetPos2(player);
            }
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    private TypedActionResult<ItemStack> executeSetPos2(ServerPlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        SpCommand.SELECTION_POS2.put(player.getUuid(), pos);

        BlockPos pos1 = SpCommand.SELECTION_POS1.get(player.getUuid());
        if (pos1 != null) {
            player.sendMessage(Text.literal("§a✓ Position 2 set: §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
            player.sendMessage(Text.literal("§eSelection: §f" + pos1.getX() + "," + pos1.getY() + "," + pos1.getZ() +
                                          " §7→ §f" + pos.getX() + "," + pos.getY() + "," + pos.getZ()), false);

            int sizeX = Math.abs(pos.getX() - pos1.getX()) + 1;
            int sizeZ = Math.abs(pos.getZ() - pos1.getZ()) + 1;
            player.sendMessage(Text.literal("§7Selection size: §f" + sizeX + "x" + sizeZ), false);

            // Spawn particles
            spawnSelectionParticles(player.getWorld(), pos, 10);
            spawnSelectionOutlineParticles(player.getWorld(), pos1, pos);
        } else {
            player.sendMessage(Text.literal("§a✓ Position 2 set: §f" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()), false);
            player.sendMessage(Text.literal("§7Set position 1 first with left-click"), false);
            spawnSelectionParticles(player.getWorld(), pos, 10);
        }

        return TypedActionResult.success(player.getStackInHand(Hand.MAIN_HAND));
    }

    private TypedActionResult<ItemStack> executeCreatePlot(ServerPlayerEntity player) {
        BlockPos pos1 = SpCommand.SELECTION_POS1.get(player.getUuid());
        BlockPos pos2 = SpCommand.SELECTION_POS2.get(player.getUuid());

        if (pos1 == null || pos2 == null) {
            player.sendMessage(Text.literal("§c✗ Incomplete selection. Set both positions first."), false);
            player.sendMessage(Text.literal("§7Left-click: Set position 1"), false);
            player.sendMessage(Text.literal("§7Right-click: Set position 2"), false);
            return TypedActionResult.fail(player.getStackInHand(Hand.MAIN_HAND));
        }

        PlotManager manager = PlotManager.getOrCreate((ServerWorld) player.getWorld());

        // Calculate center and size from selection
        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        int centerX = (minX + maxX) / 2;
        int centerZ = (minZ + maxZ) / 2;
        int centerY = (Math.min(pos1.getY(), pos2.getY()) + Math.max(pos1.getY(), pos2.getY())) / 2;
        BlockPos center = new BlockPos(centerX, centerY, centerZ);

        // Calculate required radius
        int radiusX = maxX - minX + 1;
        int radiusZ = maxZ - minZ + 1;
        int requiredRadius = Math.max(radiusX, radiusZ);

        // Find best fitting tier
        PlotSize targetSize = getBestFittingTier(requiredRadius);
        if (targetSize == null) {
            player.sendMessage(Text.literal("§c✗ Selection too large for any tier"), false);
            return TypedActionResult.fail(player.getStackInHand(Hand.MAIN_HAND));
        }

        // Check if player can place here
        if (!manager.canPlace(center, targetSize)) {
            player.sendMessage(Text.literal("§c✗ Cannot place plot here — overlaps with another plot"), false);
            return TypedActionResult.fail(player.getStackInHand(Hand.MAIN_HAND));
        }

        // Create the plot
        long currentTick = player.getWorld().getTime();
        PlotData plot = new PlotData(player.getUuid(), player.getName().getString(), center, targetSize, currentTick);
        manager.addPlot(plot);

        player.sendMessage(Text.literal("§a✓ Plot created: §e\"" + plot.getPlotName() + "\""), false);
        player.sendMessage(Text.literal("§7Center: §f" + center.getX() + ", " + center.getY() + ", " + center.getZ()), false);
        player.sendMessage(Text.literal("§7Size: §f" + targetSize.getRadius() + "x" + targetSize.getRadius() +
                                      " (" + targetSize.getDisplayName() + ")"), false);

        // Place the plot block
        ((ServerWorld) player.getWorld()).setBlockState(center,
            com.zhilius.secureplots.block.ModBlocks.fromTier(targetSize.tier).getDefaultState());

        // Clear selection
        SpCommand.SELECTION_POS1.remove(player.getUuid());
        SpCommand.SELECTION_POS2.remove(player.getUuid());

        return TypedActionResult.success(player.getStackInHand(Hand.MAIN_HAND));
    }

    private PlotSize getBestFittingTier(int requiredRadius) {
        // Find the smallest tier that fits the selection
        for (int tier = 0; tier <= 4; tier++) {
            PlotSize size = PlotSize.fromTier(tier);
            if (size.getRadius() >= requiredRadius) {
                return size;
            }
        }
        return null;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("§e§lPlot Selector Wand").formatted(Formatting.BOLD));
        tooltip.add(Text.literal(""));
        tooltip.add(Text.literal("§7Left-click: §fSet position 1").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("§7Right-click: §fSet position 2").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("§7Shift+Right-click: §fCreate plot").formatted(Formatting.GRAY));
        tooltip.add(Text.literal(""));
        tooltip.add(Text.literal("§eTip: §7The plot center is calculated").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("§eTip: §7automatically from your selection").formatted(Formatting.GRAY));
    }

    /** Creates a named selector wand item stack. */
    public static ItemStack createWand() {
        ItemStack stack = new ItemStack(Items.WOODEN_AXE);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal("§e§lPlot Selector").formatted(Formatting.BOLD));
        stack.set(DataComponentTypes.LORE, new net.minecraft.component.type.LoreComponent(List.of(
            Text.literal("§7Left-click: Set position 1"),
            Text.literal("§7Right-click: Set position 2"),
            Text.literal("§7Shift+Right-click: Create plot")
        )));
        return stack;
    }

    /** Spawns particles at a single block position. */
    private void spawnSelectionParticles(net.minecraft.world.World world, BlockPos pos, int count) {
        if (world instanceof ServerWorld sw) {
            for (int i = 0; i < count; i++) {
                double x = pos.getX() + 0.5 + world.random.nextDouble() * 0.8 - 0.4;
                double y = pos.getY() + 0.5 + world.random.nextDouble() * 0.8 - 0.4;
                double z = pos.getZ() + 0.5 + world.random.nextDouble() * 0.8 - 0.4;
                sw.spawnParticles(ParticleTypes.END_ROD, x, y, z, 1, 0, 0, 0, 0);
            }
        }
    }

    /** Spawns particle outline around the selected area. */
    private void spawnSelectionOutlineParticles(net.minecraft.world.World world, BlockPos pos1, BlockPos pos2) {
        if (!(world instanceof ServerWorld sw)) return;

        int minX = Math.min(pos1.getX(), pos2.getX());
        int maxX = Math.max(pos1.getX(), pos2.getX());
        int minY = Math.min(pos1.getY(), pos2.getY());
        int maxY = Math.max(pos1.getY(), pos2.getY());
        int minZ = Math.min(pos1.getZ(), pos2.getZ());
        int maxZ = Math.max(pos1.getZ(), pos2.getZ());

        // Top and bottom edges
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                sw.spawnParticles(ParticleTypes.END_ROD, x + 0.5, minY + 0.5, z + 0.5, 1, 0, 0, 0, 0);
                sw.spawnParticles(ParticleTypes.END_ROD, x + 0.5, maxY + 0.5, z + 0.5, 1, 0, 0, 0, 0);
            }
        }

        // Vertical edges at corners
        for (int y = minY; y <= maxY; y++) {
            sw.spawnParticles(ParticleTypes.END_ROD, minX + 0.5, y + 0.5, minZ + 0.5, 1, 0, 0, 0, 0);
            sw.spawnParticles(ParticleTypes.END_ROD, minX + 0.5, y + 0.5, maxZ + 0.5, 1, 0, 0, 0, 0);
            sw.spawnParticles(ParticleTypes.END_ROD, maxX + 0.5, y + 0.5, minZ + 0.5, 1, 0, 0, 0, 0);
            sw.spawnParticles(ParticleTypes.END_ROD, maxX + 0.5, y + 0.5, maxZ + 0.5, 1, 0, 0, 0, 0);
        }
    }
}

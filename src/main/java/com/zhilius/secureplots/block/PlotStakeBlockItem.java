package com.zhilius.secureplots.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * BlockItem custom para la Estaca de Parcela.
 *
 * Sobreescribe la lógica de colocación para ignorar la cara clickeada:
 * siempre intenta colocar la estaca en el bloque objetivo (si es reemplazable)
 * o en la cara adyacente como fallback normal.
 *
 * Esto permite colocar estacas en cualquier posición X/Z sin necesidad
 * de que haya una superficie sólida debajo.
 */
public class PlotStakeBlockItem extends BlockItem {

    public PlotStakeBlockItem(Block block, Item.Settings settings) {
        super(block, settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos clicked = context.getBlockPos();

        // Primero intentar colocar directamente donde se clickeó
        // si ese bloque es reemplazable (aire, agua, hierba, etc.)
        if (world.getBlockState(clicked).isReplaceable()) {
            if (!world.isClient) {
                placeAt(context, clicked);
            }
            return ActionResult.SUCCESS;
        }

        // Fallback: cara adyacente (comportamiento estándar de BlockItem)
        BlockPos adjacent = clicked.offset(context.getSide());
        if (world.getBlockState(adjacent).isReplaceable()) {
            if (!world.isClient) {
                placeAt(context, adjacent);
            }
            return ActionResult.SUCCESS;
        }

        return ActionResult.FAIL;
    }

    private void placeAt(ItemUsageContext context, BlockPos pos) {
        World world = context.getWorld();
        BlockState state = getBlock().getDefaultState();
        world.setBlockState(pos, state);

        PlayerEntity player = context.getPlayer();
        ItemStack stack = context.getStack();

        getBlock().onPlaced(world, pos, state, player, stack);

        if (player != null && !player.isCreative()) {
            stack.decrement(1);
        }
    }
}

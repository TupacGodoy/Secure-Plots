package com.zhilius.secureplots.block;

import com.mojang.serialization.MapCodec;
import com.zhilius.secureplots.hologram.PlotHologram;
import com.zhilius.secureplots.blockentity.PlotBlockEntity;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotManager;
import com.zhilius.secureplots.plot.PlotSize;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;

public class PlotBlock extends BlockWithEntity {

    public PlotBlock(Settings settings) {
        super(settings);
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
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof PlotBlockEntity plotBE) {
                plotBE.openScreen((ServerPlayerEntity) player);

                // Mostrar holograma o chat al hacer clic derecho
                com.zhilius.secureplots.plot.PlotManager manager =
                    com.zhilius.secureplots.plot.PlotManager.getOrCreate((net.minecraft.server.world.ServerWorld) world);
                com.zhilius.secureplots.plot.PlotData data = manager.getPlot(pos);
                if (data != null) {
                    PlotHologram.spawn((net.minecraft.server.world.ServerWorld) world, pos, data, 200, player.getYaw());
                    com.zhilius.secureplots.network.ModPackets.sendShowPlotBorder((ServerPlayerEntity) player, data);
                    sendHologramAsChat(player, data);
                }
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, net.minecraft.entity.LivingEntity placer,
            net.minecraft.item.ItemStack stack) {
        if (!world.isClient && placer instanceof ServerPlayerEntity player) {
            PlotManager manager = PlotManager.getOrCreate((net.minecraft.server.world.ServerWorld) world);

            if (!manager.canPlace(pos, PlotSize.SMALL)) {
                world.breakBlock(pos, true, player);
                player.sendMessage(Text.literal("✗ No puedes colocar una protección aquí, está muy cerca de otra.")
                        .formatted(Formatting.RED), false);
                return;
            }

            UUID ownerId = player.getUuid();
            String ownerName = player.getName().getString();
            long tick = world.getTime();

            PlotData data = new PlotData(ownerId, ownerName, pos, PlotSize.SMALL, tick);
            manager.addPlot(data);

            player.sendMessage(Text.literal(""), false);
            player.sendMessage(Text.literal("═══════════════════════════").formatted(Formatting.GOLD), false);
            player.sendMessage(Text.literal("  🛡 ¡Protección colocada!").formatted(Formatting.YELLOW, Formatting.BOLD),
                    false);
            player.sendMessage(Text.literal("═══════════════════════════").formatted(Formatting.GOLD), false);
            player.sendMessage(Text.literal(""), false);
            player.sendMessage(
                    Text.literal("  ➤ Clic derecho al bloque para abrir el menú").formatted(Formatting.WHITE), false);
            player.sendMessage(
                    Text.literal("  ➤ Usa el Plano de Protección para ver los límites").formatted(Formatting.WHITE),
                    false);
            player.sendMessage(Text.literal("  ➤ Tamaño actual: 15x15").formatted(Formatting.AQUA), false);
            player.sendMessage(Text.literal("  ➤ Expira en: 25 días (sin rango)").formatted(Formatting.YELLOW), false);
            player.sendMessage(Text.literal(""), false);
            player.sendMessage(Text.literal("  Mejorala con cobblecoins y recursos!").formatted(Formatting.GREEN),
                    false);
            player.sendMessage(Text.literal("═══════════════════════════").formatted(Formatting.GOLD), false);

            // Mostrar holograma encima del bloque
            PlotHologram.spawn((net.minecraft.server.world.ServerWorld) world, pos, data, 300, placer.getYaw());
        }
    }

    /** Verifica si hay al menos 3 bloques de aire encima del bloque. */
    private boolean hasSpaceForHologram(World world, BlockPos pos) {
        for (int i = 1; i <= 3; i++) {
            if (!world.getBlockState(pos.up(i)).isAir()) return false;
        }
        return true;
    }

    /** Envía la info del plot como mensajes de chat cuando no hay espacio para el holograma. */
    private void sendHologramAsChat(PlayerEntity player, com.zhilius.secureplots.plot.PlotData data) {
        String name = (data.getPlotName() != null && !data.getPlotName().isBlank())
                ? data.getPlotName() : "Parcela Protegida";
        player.sendMessage(Text.literal("═══════════════════════════").formatted(Formatting.GOLD), false);
        player.sendMessage(Text.literal("  🛡 " + name).formatted(Formatting.YELLOW, Formatting.BOLD), false);
        player.sendMessage(Text.literal("  Dueño: ").formatted(Formatting.GRAY)
                .append(Text.literal(data.getOwnerName()).formatted(Formatting.WHITE)), false);
        player.sendMessage(Text.literal("  Tamaño: ").formatted(Formatting.GRAY)
                .append(Text.literal(data.getSize().radius + "x" + data.getSize().radius).formatted(Formatting.AQUA)), false);
        player.sendMessage(Text.literal("  Miembros: ").formatted(Formatting.GRAY)
                .append(Text.literal(String.valueOf(data.getMembers().size())).formatted(Formatting.GREEN)), false);
        player.sendMessage(Text.literal("═══════════════════════════").formatted(Formatting.GOLD), false);
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient) {
            PlotManager manager = PlotManager.getOrCreate((net.minecraft.server.world.ServerWorld) world);
            PlotData data = manager.getPlot(pos);
            if (data != null) {
                if (!data.getOwnerId().equals(player.getUuid()) && !player.hasPermissionLevel(2)) {
                    player.sendMessage(Text.literal("✗ No eres el dueño de esta protección.").formatted(Formatting.RED),
                            false);
                    world.setBlockState(pos, state);
                    return state;
                }
                manager.removePlot(pos);
                PlotHologram.remove((net.minecraft.server.world.ServerWorld) world, pos);
                player.sendMessage(Text.literal("✗ Protección eliminada.").formatted(Formatting.RED), false);
            }
        }
        return super.onBreak(world, pos, state, player);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
}
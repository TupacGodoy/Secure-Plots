package com.zhilius.secureplots.network;

import com.zhilius.secureplots.SecurePlots;
import com.zhilius.secureplots.network.ModPackets.OpenPlotScreenPayload;
import com.zhilius.secureplots.network.ModPackets.ShowPlotBorderPayload;
import com.zhilius.secureplots.network.ModPackets.UpdatePlotPayload;
import com.zhilius.secureplots.network.ModPackets.UpgradePlotPayload;
import com.zhilius.secureplots.plot.PlotData;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class ModPackets {

    public record OpenPlotScreenPayload(BlockPos pos, NbtCompound nbt) implements CustomPayload {
        public static final Id<OpenPlotScreenPayload> ID = new Id<>(
                Identifier.of(SecurePlots.MOD_ID, "open_plot_screen"));
        public static final PacketCodec<PacketByteBuf, OpenPlotScreenPayload> CODEC = PacketCodec.of(
                (value, buf) -> {
                    buf.writeBlockPos(value.pos());
                    buf.writeNbt(value.nbt());
                },
                buf -> new OpenPlotScreenPayload(buf.readBlockPos(), buf.readNbt()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record ShowPlotBorderPayload(NbtCompound nbt) implements CustomPayload {
        public static final Id<ShowPlotBorderPayload> ID = new Id<>(
                Identifier.of(SecurePlots.MOD_ID, "show_plot_border"));
        public static final PacketCodec<PacketByteBuf, ShowPlotBorderPayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeNbt(value.nbt()),
                buf -> new ShowPlotBorderPayload(buf.readNbt()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record UpdatePlotPayload(BlockPos pos, NbtCompound nbt) implements CustomPayload {
        public static final Id<UpdatePlotPayload> ID = new Id<>(Identifier.of(SecurePlots.MOD_ID, "update_plot"));
        public static final PacketCodec<PacketByteBuf, UpdatePlotPayload> CODEC = PacketCodec.of(
                (value, buf) -> {
                    buf.writeBlockPos(value.pos());
                    buf.writeNbt(value.nbt());
                },
                buf -> new UpdatePlotPayload(buf.readBlockPos(), buf.readNbt()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public record UpgradePlotPayload(BlockPos pos) implements CustomPayload {
        public static final Id<UpgradePlotPayload> ID = new Id<>(Identifier.of(SecurePlots.MOD_ID, "upgrade_plot"));
        public static final PacketCodec<PacketByteBuf, UpgradePlotPayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeBlockPos(value.pos()),
                buf -> new UpgradePlotPayload(buf.readBlockPos()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(OpenPlotScreenPayload.ID, OpenPlotScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShowPlotBorderPayload.ID, ShowPlotBorderPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdatePlotPayload.ID, UpdatePlotPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpgradePlotPayload.ID, UpgradePlotPayload.CODEC);
    }

    public static void sendOpenPlotScreen(ServerPlayerEntity player, BlockPos pos, PlotData data) {
        ServerPlayNetworking.send(player, new OpenPlotScreenPayload(pos, data.toNbt()));
    }

    public static void sendShowPlotBorder(ServerPlayerEntity player, PlotData data) {
        ServerPlayNetworking.send(player, new ShowPlotBorderPayload(data.toNbt()));
    }

    public static void registerServerHandlers() {
        registerPayloads();

        ServerPlayNetworking.registerGlobalReceiver(UpdatePlotPayload.ID, (payload, context) -> {
            BlockPos pos = payload.pos();
            NbtCompound nbt = payload.nbt();
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (player.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
                    var manager = com.zhilius.secureplots.plot.PlotManager.getOrCreate(serverWorld);
                    PlotData existing = manager.getPlot(pos);
                    if (existing != null && existing.getOwnerId().equals(player.getUuid())) {
                        PlotData updated = PlotData.fromNbt(nbt);
                        existing.setPlotName(updated.getPlotName());
                        for (var entry : updated.getMembers().entrySet()) {
                            existing.addMember(entry.getKey(), updated.getMemberName(entry.getKey()), entry.getValue());
                        }
                        manager.markDirty();
                    }
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpgradePlotPayload.ID, (payload, context) -> {
            BlockPos pos = payload.pos();
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (player.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld) {
                    var manager = com.zhilius.secureplots.plot.PlotManager.getOrCreate(serverWorld);
                    PlotData data = manager.getPlot(pos);
                    if (data == null || !data.getOwnerId().equals(player.getUuid()))
                        return;
                    var nextSize = data.getSize().next();
                    if (nextSize == null) {
                        player.sendMessage(net.minecraft.text.Text.literal("Esta proteccion ya esta al maximo nivel.")
                                .formatted(net.minecraft.util.Formatting.RED), false);
                        return;
                    }
                    data.setSize(nextSize);
                    manager.markDirty();
                    player.sendMessage(
                            net.minecraft.text.Text
                                    .literal("Proteccion mejorada a " + nextSize.displayName + " (" + nextSize.radius
                                            + "x" + nextSize.radius + ")!")
                                    .formatted(net.minecraft.util.Formatting.GREEN),
                            false);
                    sendOpenPlotScreen(player, pos, data);
                }
            });
        });
    }
}

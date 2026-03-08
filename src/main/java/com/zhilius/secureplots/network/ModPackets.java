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

    // ── Payloads existentes ───────────────────────────────────────────────────

    public record OpenPlotScreenPayload(BlockPos pos, NbtCompound nbt) implements CustomPayload {
        public static final Id<OpenPlotScreenPayload> ID = new Id<>(
                Identifier.of(SecurePlots.MOD_ID, "open_plot_screen"));
        public static final PacketCodec<PacketByteBuf, OpenPlotScreenPayload> CODEC = PacketCodec.of(
                (value, buf) -> { buf.writeBlockPos(value.pos()); buf.writeNbt(value.nbt()); },
                buf -> new OpenPlotScreenPayload(buf.readBlockPos(), buf.readNbt()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ShowPlotBorderPayload(NbtCompound nbt) implements CustomPayload {
        public static final Id<ShowPlotBorderPayload> ID = new Id<>(
                Identifier.of(SecurePlots.MOD_ID, "show_plot_border"));
        public static final PacketCodec<PacketByteBuf, ShowPlotBorderPayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeNbt(value.nbt()),
                buf -> new ShowPlotBorderPayload(buf.readNbt()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record HidePlotBorderPayload(BlockPos pos) implements CustomPayload {
        public static final Id<HidePlotBorderPayload> ID = new Id<>(
                Identifier.of(SecurePlots.MOD_ID, "hide_plot_border"));
        public static final PacketCodec<PacketByteBuf, HidePlotBorderPayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeBlockPos(value.pos()),
                buf -> new HidePlotBorderPayload(buf.readBlockPos()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record UpdatePlotPayload(BlockPos pos, NbtCompound nbt) implements CustomPayload {
        public static final Id<UpdatePlotPayload> ID = new Id<>(Identifier.of(SecurePlots.MOD_ID, "update_plot"));
        public static final PacketCodec<PacketByteBuf, UpdatePlotPayload> CODEC = PacketCodec.of(
                (value, buf) -> { buf.writeBlockPos(value.pos()); buf.writeNbt(value.nbt()); },
                buf -> new UpdatePlotPayload(buf.readBlockPos(), buf.readNbt()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record AddMemberPayload(BlockPos pos, String playerName) implements CustomPayload {
        public static final Id<AddMemberPayload> ID = new Id<>(Identifier.of(SecurePlots.MOD_ID, "add_member"));
        public static final PacketCodec<PacketByteBuf, AddMemberPayload> CODEC = PacketCodec.of(
                (value, buf) -> { buf.writeBlockPos(value.pos()); buf.writeString(value.playerName()); },
                buf -> new AddMemberPayload(buf.readBlockPos(), buf.readString()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record RemoveMemberPayload(BlockPos pos, String playerName) implements CustomPayload {
        public static final Id<RemoveMemberPayload> ID = new Id<>(Identifier.of(SecurePlots.MOD_ID, "remove_member"));
        public static final PacketCodec<PacketByteBuf, RemoveMemberPayload> CODEC = PacketCodec.of(
                (value, buf) -> { buf.writeBlockPos(value.pos()); buf.writeString(value.playerName()); },
                buf -> new RemoveMemberPayload(buf.readBlockPos(), buf.readString()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SetPermissionPayload(BlockPos pos, String memberUuid, String permission, boolean enabled) implements CustomPayload {
        public static final Id<SetPermissionPayload> ID = new Id<>(Identifier.of(SecurePlots.MOD_ID, "set_permission"));
        public static final PacketCodec<PacketByteBuf, SetPermissionPayload> CODEC = PacketCodec.of(
                (value, buf) -> { buf.writeBlockPos(value.pos()); buf.writeString(value.memberUuid()); buf.writeString(value.permission()); buf.writeBoolean(value.enabled()); },
                buf -> new SetPermissionPayload(buf.readBlockPos(), buf.readString(), buf.readString(), buf.readBoolean()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record UpgradePlotPayload(BlockPos pos) implements CustomPayload {
        public static final Id<UpgradePlotPayload> ID = new Id<>(Identifier.of(SecurePlots.MOD_ID, "upgrade_plot"));
        public static final PacketCodec<PacketByteBuf, UpgradePlotPayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeBlockPos(value.pos()),
                buf -> new UpgradePlotPayload(buf.readBlockPos()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ShowPlotInfoPayload(BlockPos pos, NbtCompound nbt) implements CustomPayload {
        public static final Id<ShowPlotInfoPayload> ID = new Id<>(
                Identifier.of(SecurePlots.MOD_ID, "show_plot_info"));
        public static final PacketCodec<PacketByteBuf, ShowPlotInfoPayload> CODEC = PacketCodec.of(
                (value, buf) -> { buf.writeBlockPos(value.pos()); buf.writeNbt(value.nbt()); },
                buf -> new ShowPlotInfoPayload(buf.readBlockPos(), buf.readNbt()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ── NUEVO: Estacas de Parcela ─────────────────────────────────────────────

    /**
     * S→C: Una estaca fue colocada correctamente. Actualiza la sesión de partículas.
     */
    public record StakeUpdatePayload(BlockPos stakePos, NbtCompound data) implements CustomPayload {
        public static final Id<StakeUpdatePayload> ID = new Id<>(
                Identifier.of(SecurePlots.MOD_ID, "stake_update"));
        public static final PacketCodec<PacketByteBuf, StakeUpdatePayload> CODEC = PacketCodec.of(
                (value, buf) -> { buf.writeBlockPos(value.stakePos()); buf.writeNbt(value.data()); },
                buf -> new StakeUpdatePayload(buf.readBlockPos(), buf.readNbt()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * S→C: Ángulo incorrecto — flash rojo en la sesión.
     */
    public record StakeAngleErrorPayload(BlockPos errorPos, String sessionId) implements CustomPayload {
        public static final Id<StakeAngleErrorPayload> ID = new Id<>(
                Identifier.of(SecurePlots.MOD_ID, "stake_angle_error"));
        public static final PacketCodec<PacketByteBuf, StakeAngleErrorPayload> CODEC = PacketCodec.of(
                (value, buf) -> { buf.writeBlockPos(value.errorPos()); buf.writeString(value.sessionId()); },
                buf -> new StakeAngleErrorPayload(buf.readBlockPos(), buf.readString()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * S→C: Sesión cancelada (estaca rota antes de completar).
     */
    public record StakeCancelledPayload(String sessionId) implements CustomPayload {
        public static final Id<StakeCancelledPayload> ID = new Id<>(
                Identifier.of(SecurePlots.MOD_ID, "stake_cancelled"));
        public static final PacketCodec<PacketByteBuf, StakeCancelledPayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeString(value.sessionId()),
                buf -> new StakeCancelledPayload(buf.readString()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * S→C: Abre el menú de configuración de altura (Y) de una subdivisión via estaca.
     */
    public record OpenStakeYMenuPayload(BlockPos stakePos, BlockPos plotCenter,
                                        String subName, boolean useY,
                                        int yMin, int yMax) implements CustomPayload {
        public static final Id<OpenStakeYMenuPayload> ID = new Id<>(
                Identifier.of(SecurePlots.MOD_ID, "open_stake_y_menu"));
        public static final PacketCodec<PacketByteBuf, OpenStakeYMenuPayload> CODEC = PacketCodec.of(
                (value, buf) -> {
                    buf.writeBlockPos(value.stakePos()); buf.writeBlockPos(value.plotCenter());
                    buf.writeString(value.subName()); buf.writeBoolean(value.useY());
                    buf.writeInt(value.yMin()); buf.writeInt(value.yMax());
                },
                buf -> new OpenStakeYMenuPayload(buf.readBlockPos(), buf.readBlockPos(),
                        buf.readString(), buf.readBoolean(), buf.readInt(), buf.readInt()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * C→S: El jugador confirmó la configuración de Y desde el menú de estaca.
     */
    public record StakeYConfigPayload(BlockPos stakePos, BlockPos plotCenter,
                                       String subName, boolean useY,
                                       int yMin, int yMax) implements CustomPayload {
        public static final Id<StakeYConfigPayload> ID = new Id<>(
                Identifier.of(SecurePlots.MOD_ID, "stake_y_config"));
        public static final PacketCodec<PacketByteBuf, StakeYConfigPayload> CODEC = PacketCodec.of(
                (value, buf) -> {
                    buf.writeBlockPos(value.stakePos()); buf.writeBlockPos(value.plotCenter());
                    buf.writeString(value.subName()); buf.writeBoolean(value.useY());
                    buf.writeInt(value.yMin()); buf.writeInt(value.yMax());
                },
                buf -> new StakeYConfigPayload(buf.readBlockPos(), buf.readBlockPos(),
                        buf.readString(), buf.readBoolean(), buf.readInt(), buf.readInt()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ── NUEVO: Subdivisiones ──────────────────────────────────────────────────

    /**
     * Servidor → Cliente: envía los datos de subdivisiones de una plot para renderizar.
     * El payload contiene el NBT completo de la PlotData (para no duplicar structs).
     */
    public record ShowSubdivisionsPayload(NbtCompound nbt) implements CustomPayload {
        public static final Id<ShowSubdivisionsPayload> ID = new Id<>(
                Identifier.of(SecurePlots.MOD_ID, "show_subdivisions"));
        public static final PacketCodec<PacketByteBuf, ShowSubdivisionsPayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeNbt(value.nbt()),
                buf -> new ShowSubdivisionsPayload(buf.readNbt()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /**
     * Servidor → Cliente: limpia las subdivisiones de una plot en pantalla.
     */
    public record HideSubdivisionsPayload(BlockPos plotCenter) implements CustomPayload {
        public static final Id<HideSubdivisionsPayload> ID = new Id<>(
                Identifier.of(SecurePlots.MOD_ID, "hide_subdivisions"));
        public static final PacketCodec<PacketByteBuf, HideSubdivisionsPayload> CODEC = PacketCodec.of(
                (value, buf) -> buf.writeBlockPos(value.plotCenter()),
                buf -> new HideSubdivisionsPayload(buf.readBlockPos()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    // ── Registro ──────────────────────────────────────────────────────────────

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(OpenPlotScreenPayload.ID,    OpenPlotScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShowPlotBorderPayload.ID,    ShowPlotBorderPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HidePlotBorderPayload.ID,    HidePlotBorderPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShowPlotInfoPayload.ID,      ShowPlotInfoPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShowSubdivisionsPayload.ID,  ShowSubdivisionsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HideSubdivisionsPayload.ID,  HideSubdivisionsPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StakeUpdatePayload.ID,       StakeUpdatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StakeAngleErrorPayload.ID,   StakeAngleErrorPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StakeCancelledPayload.ID,    StakeCancelledPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenStakeYMenuPayload.ID,    OpenStakeYMenuPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StakeYConfigPayload.ID,      StakeYConfigPayload.CODEC);

        PayloadTypeRegistry.playC2S().register(UpdatePlotPayload.ID,        UpdatePlotPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpgradePlotPayload.ID,       UpgradePlotPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AddMemberPayload.ID,         AddMemberPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SetPermissionPayload.ID,     SetPermissionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RemoveMemberPayload.ID,      RemoveMemberPayload.CODEC);
    }

    // ── Send helpers ──────────────────────────────────────────────────────────

    public static void sendShowPlotInfo(ServerPlayerEntity player, BlockPos pos, PlotData data) {
        ServerPlayNetworking.send(player, new ShowPlotInfoPayload(pos, data.toNbt()));
    }

    public static void sendOpenPlotScreen(ServerPlayerEntity player, BlockPos pos, PlotData data) {
        ServerPlayNetworking.send(player, new OpenPlotScreenPayload(pos, data.toNbt()));
    }

    public static void sendShowPlotBorder(ServerPlayerEntity player, PlotData data) {
        ServerPlayNetworking.send(player, new ShowPlotBorderPayload(data.toNbt()));
    }

    public static void sendHidePlotBorder(ServerPlayerEntity player, BlockPos pos) {
        ServerPlayNetworking.send(player, new HidePlotBorderPayload(pos));
    }

    public static void sendShowSubdivisions(ServerPlayerEntity player, PlotData data) {
        ServerPlayNetworking.send(player, new ShowSubdivisionsPayload(data.toNbt()));
    }

    public static void sendHideSubdivisions(ServerPlayerEntity player, BlockPos plotCenter) {
        ServerPlayNetworking.send(player, new HideSubdivisionsPayload(plotCenter));
    }

    public static void sendStakeUpdate(ServerPlayerEntity player, BlockPos stakePos,
                                        java.util.UUID sessionId, int index,
                                        boolean valid, BlockPos plotCenter) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("sessionId", sessionId.toString());
        nbt.putInt("index", index);
        nbt.putBoolean("valid", valid);
        nbt.putInt("plotCX", plotCenter.getX());
        nbt.putInt("plotCY", plotCenter.getY());
        nbt.putInt("plotCZ", plotCenter.getZ());
        ServerPlayNetworking.send(player, new StakeUpdatePayload(stakePos, nbt));
    }

    public static void sendStakeAngleError(ServerPlayerEntity player, BlockPos errorPos,
                                            java.util.UUID sessionId) {
        ServerPlayNetworking.send(player, new StakeAngleErrorPayload(errorPos,
                sessionId != null ? sessionId.toString() : ""));
    }

    public static void sendStakeCancelled(ServerPlayerEntity player, java.util.UUID sessionId) {
        ServerPlayNetworking.send(player, new StakeCancelledPayload(sessionId.toString()));
    }

    public static void sendOpenStakeYMenu(ServerPlayerEntity player, BlockPos stakePos,
                                           BlockPos plotCenter, String subName,
                                           boolean useY, int yMin, int yMax) {
        ServerPlayNetworking.send(player, new OpenStakeYMenuPayload(
                stakePos, plotCenter, subName, useY, yMin, yMax));
    }

    // ── Server handlers ───────────────────────────────────────────────────────

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
                    if (data == null || !data.getOwnerId().equals(player.getUuid())) return;
                    var nextSize = data.getSize().next();
                    if (nextSize == null) {
                        player.sendMessage(net.minecraft.text.Text.literal("Esta proteccion ya esta al maximo nivel.")
                                .formatted(net.minecraft.util.Formatting.RED), false);
                        return;
                    }
                    data.setSize(nextSize);
                    manager.markDirty();
                    net.minecraft.block.Block newBlock =
                            com.zhilius.secureplots.block.ModBlocks.fromTier(nextSize.tier);
                    serverWorld.setBlockState(pos, newBlock.getDefaultState());
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

        ServerPlayNetworking.registerGlobalReceiver(AddMemberPayload.ID, (payload, context) -> {
            BlockPos pos = payload.pos();
            String targetName = payload.playerName();
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (!(player.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld)) return;
                var manager = com.zhilius.secureplots.plot.PlotManager.getOrCreate(serverWorld);
                PlotData data = manager.getPlot(pos);
                if (data == null || !data.getOwnerId().equals(player.getUuid())) return;
                ServerPlayerEntity target = context.server().getPlayerManager().getPlayer(targetName);
                if (target == null) {
                    player.sendMessage(net.minecraft.text.Text.literal("✗ Jugador \"" + targetName + "\" no está en línea.")
                            .formatted(net.minecraft.util.Formatting.RED), false);
                    return;
                }
                if (target.getUuid().equals(player.getUuid())) {
                    player.sendMessage(net.minecraft.text.Text.literal("✗ No podés agregarte a vos mismo.")
                            .formatted(net.minecraft.util.Formatting.RED), false);
                    return;
                }
                if (data.getRoleOf(target.getUuid()) != PlotData.Role.VISITOR) {
                    player.sendMessage(net.minecraft.text.Text.literal("✗ " + targetName + " ya tiene acceso.")
                            .formatted(net.minecraft.util.Formatting.YELLOW), false);
                    return;
                }
                data.addMember(target.getUuid(), target.getName().getString(), PlotData.Role.MEMBER);
                manager.markDirty();
                player.sendMessage(net.minecraft.text.Text.literal("✔ " + targetName + " agregado.")
                        .formatted(net.minecraft.util.Formatting.GREEN), false);
                target.sendMessage(net.minecraft.text.Text.literal("✔ Fuiste agregado a " + data.getPlotName() + " de " + player.getName().getString())
                        .formatted(net.minecraft.util.Formatting.GREEN), false);
                sendOpenPlotScreen(player, pos, data);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RemoveMemberPayload.ID, (payload, context) -> {
            BlockPos pos = payload.pos();
            String targetName = payload.playerName();
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (!(player.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld)) return;
                var manager = com.zhilius.secureplots.plot.PlotManager.getOrCreate(serverWorld);
                PlotData data = manager.getPlot(pos);
                if (data == null || !data.getOwnerId().equals(player.getUuid())) return;
                java.util.UUID targetUuid = null;
                for (java.util.Map.Entry<java.util.UUID, PlotData.Role> entry : data.getMembers().entrySet()) {
                    if (data.getMemberName(entry.getKey()).equalsIgnoreCase(targetName)) {
                        targetUuid = entry.getKey();
                        break;
                    }
                }
                if (targetUuid == null) {
                    player.sendMessage(net.minecraft.text.Text.literal("✗ " + targetName + " no es miembro.")
                            .formatted(net.minecraft.util.Formatting.RED), false);
                    return;
                }
                data.removeMember(targetUuid);
                manager.markDirty();
                player.sendMessage(net.minecraft.text.Text.literal("✔ " + targetName + " eliminado.")
                        .formatted(net.minecraft.util.Formatting.GREEN), false);
                ServerPlayerEntity target = context.server().getPlayerManager().getPlayer(targetUuid);
                if (target != null) {
                    target.sendMessage(net.minecraft.text.Text.literal("✗ Te quitaron el acceso a " + data.getPlotName())
                            .formatted(net.minecraft.util.Formatting.RED), false);
                }
                sendOpenPlotScreen(player, pos, data);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SetPermissionPayload.ID, (payload, context) -> {
            BlockPos pos = payload.pos();
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (!(player.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld)) return;
                var manager = com.zhilius.secureplots.plot.PlotManager.getOrCreate(serverWorld);
                PlotData data = manager.getPlot(pos);
                if (data == null) return;
                boolean isAdmin = player.getCommandTags().contains("plot_admin");
                if (!data.getOwnerId().equals(player.getUuid()) && !isAdmin) return;
                try {
                    java.util.UUID memberUuid = java.util.UUID.fromString(payload.memberUuid());
                    PlotData.Permission perm = PlotData.Permission.valueOf(payload.permission());
                    data.setPermission(memberUuid, perm, payload.enabled());
                    manager.markDirty();
                    sendOpenPlotScreen(player, pos, data);
                } catch (Exception ignored) {}
            });
        });

        // ── Stake Y config ────────────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(StakeYConfigPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (!(player.getWorld() instanceof net.minecraft.server.world.ServerWorld serverWorld)) return;
                var manager = com.zhilius.secureplots.plot.PlotManager.getOrCreate(serverWorld);
                PlotData plot = manager.getPlot(payload.plotCenter());
                if (plot == null) return;
                if (!plot.getOwnerId().equals(player.getUuid())
                        && !plot.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_SUBDIVISIONS)
                        && !player.getCommandTags().contains("plot_admin")) return;
                com.zhilius.secureplots.plot.PlotSubdivision sub = plot.getSubdivision(payload.subName());
                if (sub == null) return;
                sub.useY = payload.useY();
                sub.yMin = payload.yMin();
                sub.yMax = payload.yMax();
                manager.markDirty();
                sendShowSubdivisions(player, plot);
                player.sendMessage(net.minecraft.text.Text.literal(
                        payload.useY()
                            ? "§a✔ Altura configurada: Y " + payload.yMin() + " → " + payload.yMax()
                            : "§a✔ Subdivisión sin límite de altura."), false);
            });
        });
    }
}

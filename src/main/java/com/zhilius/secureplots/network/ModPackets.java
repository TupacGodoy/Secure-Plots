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
package com.zhilius.secureplots.network;

import com.google.gson.Gson;
import com.zhilius.secureplots.SecurePlots;
import com.zhilius.secureplots.block.ModBlocks;
import com.zhilius.secureplots.config.BorderConfig;
import com.zhilius.secureplots.config.SecurePlotsConfig;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotManager;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;

public class ModPackets {

    // ── Payload definitions ───────────────────────────────────────────────────

    public record OpenPlotScreenPayload(BlockPos pos, NbtCompound nbt) implements CustomPayload {
        public static final Id<OpenPlotScreenPayload> ID = new Id<>(Identifier.of(SecurePlots.MOD_ID, "open_plot_screen"));
        public static final PacketCodec<PacketByteBuf, OpenPlotScreenPayload> CODEC = PacketCodec.of(
            (v, buf) -> { buf.writeBlockPos(v.pos()); buf.writeNbt(v.nbt()); },
            buf -> new OpenPlotScreenPayload(buf.readBlockPos(), buf.readNbt()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record ShowPlotBorderPayload(NbtCompound nbt) implements CustomPayload {
        public static final Id<ShowPlotBorderPayload> ID = new Id<>(Identifier.of(SecurePlots.MOD_ID, "show_plot_border"));
        public static final PacketCodec<PacketByteBuf, ShowPlotBorderPayload> CODEC = PacketCodec.of(
            (v, buf) -> buf.writeNbt(v.nbt()),
            buf -> new ShowPlotBorderPayload(buf.readNbt()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record HidePlotBorderPayload(BlockPos pos) implements CustomPayload {
        public static final Id<HidePlotBorderPayload> ID = new Id<>(Identifier.of(SecurePlots.MOD_ID, "hide_plot_border"));
        public static final PacketCodec<PacketByteBuf, HidePlotBorderPayload> CODEC = PacketCodec.of(
            (v, buf) -> buf.writeBlockPos(v.pos()),
            buf -> new HidePlotBorderPayload(buf.readBlockPos()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record UpdatePlotPayload(BlockPos pos, NbtCompound nbt) implements CustomPayload {
        public static final Id<UpdatePlotPayload> ID = new Id<>(Identifier.of(SecurePlots.MOD_ID, "update_plot"));
        public static final PacketCodec<PacketByteBuf, UpdatePlotPayload> CODEC = PacketCodec.of(
            (v, buf) -> { buf.writeBlockPos(v.pos()); buf.writeNbt(v.nbt()); },
            buf -> new UpdatePlotPayload(buf.readBlockPos(), buf.readNbt()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record AddMemberPayload(BlockPos pos, String playerName) implements CustomPayload {
        public static final Id<AddMemberPayload> ID = new Id<>(Identifier.of(SecurePlots.MOD_ID, "add_member"));
        public static final PacketCodec<PacketByteBuf, AddMemberPayload> CODEC = PacketCodec.of(
            (v, buf) -> { buf.writeBlockPos(v.pos()); buf.writeString(v.playerName()); },
            buf -> new AddMemberPayload(buf.readBlockPos(), buf.readString()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record RemoveMemberPayload(BlockPos pos, String playerName) implements CustomPayload {
        public static final Id<RemoveMemberPayload> ID = new Id<>(Identifier.of(SecurePlots.MOD_ID, "remove_member"));
        public static final PacketCodec<PacketByteBuf, RemoveMemberPayload> CODEC = PacketCodec.of(
            (v, buf) -> { buf.writeBlockPos(v.pos()); buf.writeString(v.playerName()); },
            buf -> new RemoveMemberPayload(buf.readBlockPos(), buf.readString()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record SetPermissionPayload(BlockPos pos, String memberUuid, String permission, boolean enabled) implements CustomPayload {
        public static final Id<SetPermissionPayload> ID = new Id<>(Identifier.of(SecurePlots.MOD_ID, "set_permission"));
        public static final PacketCodec<PacketByteBuf, SetPermissionPayload> CODEC = PacketCodec.of(
            (v, buf) -> { buf.writeBlockPos(v.pos()); buf.writeString(v.memberUuid()); buf.writeString(v.permission()); buf.writeBoolean(v.enabled()); },
            buf -> new SetPermissionPayload(buf.readBlockPos(), buf.readString(), buf.readString(), buf.readBoolean()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    public record UpgradePlotPayload(BlockPos pos) implements CustomPayload {
        public static final Id<UpgradePlotPayload> ID = new Id<>(Identifier.of(SecurePlots.MOD_ID, "upgrade_plot"));
        public static final PacketCodec<PacketByteBuf, UpgradePlotPayload> CODEC = PacketCodec.of(
            (v, buf) -> buf.writeBlockPos(v.pos()),
            buf -> new UpgradePlotPayload(buf.readBlockPos()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** C→S: owner requests a plot block by tier (creative mode). */
    public record GiveBlockPayload(int tier) implements CustomPayload {
        public static final Id<GiveBlockPayload> ID = new Id<>(Identifier.of(SecurePlots.MOD_ID, "give_block"));
        public static final PacketCodec<PacketByteBuf, GiveBlockPayload> CODEC = PacketCodec.of(
            (v, buf) -> buf.writeInt(v.tier()),
            buf -> new GiveBlockPayload(buf.readInt()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** S→C: close current screen and open chat pre-filled with a command. */
    public record OpenChatPayload(String prefill) implements CustomPayload {
        public static final Id<OpenChatPayload> ID = new Id<>(Identifier.of(SecurePlots.MOD_ID, "open_chat"));
        public static final PacketCodec<PacketByteBuf, OpenChatPayload> CODEC = PacketCodec.of(
            (v, buf) -> buf.writeString(v.prefill()),
            buf -> new OpenChatPayload(buf.readString()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }
    }

    /** S→C: syncs border visual config to the client on join. */
    public record SyncBorderConfigPayload(String configJson) implements CustomPayload {
        private static final Gson GSON = new Gson();
        public static final Id<SyncBorderConfigPayload> ID = new Id<>(Identifier.of(SecurePlots.MOD_ID, "sync_border_config"));
        public static final PacketCodec<PacketByteBuf, SyncBorderConfigPayload> CODEC = PacketCodec.of(
            (v, buf) -> buf.writeString(v.configJson()),
            buf -> new SyncBorderConfigPayload(buf.readString()));
        @Override public Id<? extends CustomPayload> getId() { return ID; }

        public BorderConfig toBorderConfig() {
            BorderConfig cfg = GSON.fromJson(configJson, BorderConfig.class);
            if (cfg == null) cfg = BorderConfig.createDefault();
            cfg.applyDefaults();
            return cfg;
        }
    }

    // ── Registration ──────────────────────────────────────────────────────────

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(OpenPlotScreenPayload.ID,   OpenPlotScreenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ShowPlotBorderPayload.ID,   ShowPlotBorderPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HidePlotBorderPayload.ID,   HidePlotBorderPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenChatPayload.ID,         OpenChatPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SyncBorderConfigPayload.ID, SyncBorderConfigPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdatePlotPayload.ID,       UpdatePlotPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(UpgradePlotPayload.ID,      UpgradePlotPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AddMemberPayload.ID,        AddMemberPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SetPermissionPayload.ID,    SetPermissionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(RemoveMemberPayload.ID,     RemoveMemberPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GiveBlockPayload.ID,        GiveBlockPayload.CODEC);
    }

    // ── Send helpers ──────────────────────────────────────────────────────────

    public static void sendSyncBorderConfig(ServerPlayerEntity player) {
        if (BorderConfig.INSTANCE == null) return;
        BorderConfig bc = BorderConfig.INSTANCE;
        if (SecurePlotsConfig.INSTANCE != null)
            bc.hologramEnabled = SecurePlotsConfig.INSTANCE.enableHologram;
        ServerPlayNetworking.send(player, new SyncBorderConfigPayload(new Gson().toJson(bc)));
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

    // ── Server-side handlers ──────────────────────────────────────────────────

    public static void registerServerHandlers() {
        registerPayloads();

        ServerPlayNetworking.registerGlobalReceiver(UpdatePlotPayload.ID, (payload, ctx) -> {
            BlockPos pos = payload.pos();
            NbtCompound nbt = payload.nbt();
            ServerPlayerEntity player = ctx.player();
            ctx.server().execute(() -> {
                if (!(player.getWorld() instanceof ServerWorld sw)) return;
                PlotManager manager = PlotManager.getOrCreate(sw);
                PlotData existing = manager.getPlot(pos);
                if (existing == null || !existing.getOwnerId().equals(player.getUuid())) return;
                PlotData updated = PlotData.fromNbt(nbt);
                existing.setPlotName(updated.getPlotName());
                for (Map.Entry<UUID, PlotData.Role> entry : updated.getMembers().entrySet())
                    existing.addMember(entry.getKey(), updated.getMemberName(entry.getKey()), entry.getValue());
                manager.markDirty();
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(UpgradePlotPayload.ID, (payload, ctx) -> {
            BlockPos pos = payload.pos();
            ServerPlayerEntity player = ctx.player();
            ctx.server().execute(() -> {
                if (!(player.getWorld() instanceof ServerWorld sw)) return;
                SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
                if (cfg != null && !cfg.enableUpgrades) return;

                PlotManager manager = PlotManager.getOrCreate(sw);
                PlotData data = manager.getPlot(pos);
                if (data == null || !data.getOwnerId().equals(player.getUuid())) return;

                var nextSize = data.getSize().next();
                if (nextSize == null) {
                    player.sendMessage(Text.translatable("sp.upgrade.max_level").formatted(Formatting.RED), false);
                    return;
                }
                data.setSize(nextSize);
                manager.markDirty();

                Block newBlock = ModBlocks.fromTier(nextSize.tier);
                sw.setBlockState(pos, newBlock.getDefaultState());
                player.sendMessage(
                    Text.translatable("sp.upgrade.success", nextSize.getDisplayName(), nextSize.getRadius(), nextSize.getRadius())
                        .formatted(Formatting.GREEN), false);
                sendOpenPlotScreen(player, pos, data);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(AddMemberPayload.ID, (payload, ctx) -> {
            BlockPos pos = payload.pos();
            String targetName = payload.playerName();
            ServerPlayerEntity player = ctx.player();
            ctx.server().execute(() -> {
                if (!(player.getWorld() instanceof ServerWorld sw)) return;
                PlotManager manager = PlotManager.getOrCreate(sw);
                PlotData data = manager.getPlot(pos);
                if (data == null || !data.getOwnerId().equals(player.getUuid())) return;

                ServerPlayerEntity target = ctx.server().getPlayerManager().getPlayer(targetName);
                if (target == null) {
                    player.sendMessage(Text.translatable("sp.add.player_not_found", targetName).formatted(Formatting.RED), false);
                    return;
                }
                if (target.getUuid().equals(player.getUuid())) {
                    player.sendMessage(Text.translatable("sp.add.self").formatted(Formatting.RED), false);
                    return;
                }
                if (data.getRoleOf(target.getUuid()) != PlotData.Role.VISITOR) {
                    player.sendMessage(Text.translatable("sp.member.already_has_access", targetName).formatted(Formatting.YELLOW), false);
                    return;
                }
                data.addMember(target.getUuid(), target.getName().getString(), PlotData.Role.MEMBER);
                manager.markDirty();
                player.sendMessage(Text.translatable("sp.member.added_sender", targetName).formatted(Formatting.GREEN), false);
                target.sendMessage(Text.translatable("sp.member.added_target", data.getPlotName(), player.getName().getString()).formatted(Formatting.GREEN), false);
                sendOpenPlotScreen(player, pos, data);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(RemoveMemberPayload.ID, (payload, ctx) -> {
            BlockPos pos = payload.pos();
            String targetName = payload.playerName();
            ServerPlayerEntity player = ctx.player();
            ctx.server().execute(() -> {
                if (!(player.getWorld() instanceof ServerWorld sw)) return;
                PlotManager manager = PlotManager.getOrCreate(sw);
                PlotData data = manager.getPlot(pos);
                if (data == null || !data.getOwnerId().equals(player.getUuid())) return;

                UUID targetUuid = data.getMembers().keySet().stream()
                    .filter(u -> data.getMemberName(u).equalsIgnoreCase(targetName))
                    .findFirst().orElse(null);

                if (targetUuid == null) {
                    player.sendMessage(Text.translatable("sp.member.not_member_simple", targetName).formatted(Formatting.RED), false);
                    return;
                }
                data.removeMember(targetUuid);
                manager.markDirty();
                player.sendMessage(Text.translatable("sp.member.removed_sender", targetName).formatted(Formatting.GREEN), false);
                ServerPlayerEntity target = ctx.server().getPlayerManager().getPlayer(targetUuid);
                if (target != null)
                    target.sendMessage(Text.translatable("sp.member.removed_target", data.getPlotName()).formatted(Formatting.RED), false);
                sendOpenPlotScreen(player, pos, data);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(SetPermissionPayload.ID, (payload, ctx) -> {
            BlockPos pos = payload.pos();
            ServerPlayerEntity player = ctx.player();
            ctx.server().execute(() -> {
                if (!(player.getWorld() instanceof ServerWorld sw)) return;
                PlotManager manager = PlotManager.getOrCreate(sw);
                PlotData data = manager.getPlot(pos);
                if (data == null) return;
                String adminTag = SecurePlotsConfig.INSTANCE != null ? SecurePlotsConfig.INSTANCE.adminTag : "plot_admin";
                boolean isAdmin = player.getCommandTags().contains(adminTag);
                if (!data.getOwnerId().equals(player.getUuid()) && !isAdmin) return;
                try {
                    UUID memberUuid = UUID.fromString(payload.memberUuid());
                    PlotData.Permission perm = PlotData.Permission.valueOf(payload.permission());
                    data.setPermission(memberUuid, perm, payload.enabled());
                    manager.markDirty();
                    sendOpenPlotScreen(player, pos, data);
                } catch (Exception ignored) {}
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(GiveBlockPayload.ID, (payload, ctx) -> {
            ServerPlayerEntity player = ctx.player();
            int tier = payload.tier();
            ctx.server().execute(() -> {
                int opLevel = SecurePlotsConfig.INSTANCE != null ? SecurePlotsConfig.INSTANCE.adminOpLevel : 2;
                if (!player.hasPermissionLevel(opLevel) && !player.isCreative()) {
                    player.sendMessage(Text.translatable("sp.give_block.no_permission").formatted(Formatting.RED), false);
                    return;
                }
                ItemStack stack = new ItemStack(ModBlocks.fromTier(tier).asItem(), 1);
                if (!player.giveItemStack(stack)) player.dropItem(stack, false);
                player.sendMessage(Text.translatable("sp.give_block.success").formatted(Formatting.GREEN), true);
            });
        });
    }
}
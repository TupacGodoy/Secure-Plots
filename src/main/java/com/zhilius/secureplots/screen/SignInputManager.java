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
package com.zhilius.secureplots.screen;

import com.zhilius.secureplots.config.SecurePlotsConfig;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotManager;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SignEditorOpenS2CPacket;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Opens a fake sign editor to collect text input from the player,
 * then processes it according to the pending action type.
 *
 * Particle and music input are handled via chat (/sp plot particle|music)
 * and do NOT go through this manager.
 */
public class SignInputManager {

    public enum InputType { RENAME, ADD_MEMBER, CREATE_GROUP, SET_ENTER_MESSAGE, SET_EXIT_MESSAGE }

    public record PendingInput(BlockPos fakePos, BlockPos plotPos, InputType type) {}

    private static final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    // ── Public entry points ───────────────────────────────────────────────────

    public static void openForRename(ServerPlayerEntity player, BlockPos plotPos) {
        open(player, plotPos, InputType.RENAME);
    }

    public static void openForAddMember(ServerPlayerEntity player, BlockPos plotPos) {
        open(player, plotPos, InputType.ADD_MEMBER);
    }

    public static void openForCreateGroup(ServerPlayerEntity player, BlockPos plotPos) {
        open(player, plotPos, InputType.CREATE_GROUP);
    }

    public static void openForEnterMessage(ServerPlayerEntity player, BlockPos plotPos) {
        open(player, plotPos, InputType.SET_ENTER_MESSAGE);
    }

    public static void openForExitMessage(ServerPlayerEntity player, BlockPos plotPos) {
        open(player, plotPos, InputType.SET_EXIT_MESSAGE);
    }

    // ── Core sign-open logic ──────────────────────────────────────────────────

    private static void open(ServerPlayerEntity player, BlockPos plotPos, InputType type) {
        BlockPos fakePos = new BlockPos(player.getBlockX(), player.getBlockY() + 2, player.getBlockZ());
        pending.put(player.getUuid(), new PendingInput(fakePos, plotPos, type));

        // 1. Place a fake sign block
        player.networkHandler.sendPacket(
            new BlockUpdateS2CPacket(fakePos, Blocks.OAK_WALL_SIGN.getDefaultState()));

        // 2. Send sign block entity data
        try {
            NbtCompound signNbt = new NbtCompound();
            signNbt.putString("id", "minecraft:sign");
            signNbt.putInt("x", fakePos.getX());
            signNbt.putInt("y", fakePos.getY());
            signNbt.putInt("z", fakePos.getZ());
            signNbt.put("front_text", emptySignFace());
            signNbt.put("back_text",  emptySignFace());
            signNbt.putBoolean("is_waxed", false);

            var ctor = BlockEntityUpdateS2CPacket.class.getDeclaredConstructor(
                BlockPos.class, BlockEntityType.class, NbtCompound.class);
            ctor.setAccessible(true);
            player.networkHandler.sendPacket(
                (Packet<?>) ctor.newInstance(fakePos, BlockEntityType.SIGN, signNbt));
        } catch (Exception e) {
            com.zhilius.secureplots.SecurePlots.LOGGER.error(
                "SecurePlots: failed to send sign BE packet: {}", e.getMessage());
        }

        // 3. Open the sign editor
        player.networkHandler.sendPacket(new SignEditorOpenS2CPacket(fakePos, true));
    }

    private static NbtCompound emptySignFace() {
        NbtCompound face = new NbtCompound();
        face.putString("color", "black");
        face.putBoolean("has_glowing_text", false);
        return face;
    }

    // ── Result handling ───────────────────────────────────────────────────────

    public static boolean hasPending(UUID uuid) {
        return pending.containsKey(uuid);
    }

    public static void handleSignUpdate(ServerPlayerEntity player, BlockPos pos, String[] lines) {
        UUID uuid = player.getUuid();
        PendingInput input = pending.remove(uuid);
        if (input == null) return;

        // If pos doesn't match, the player edited a real sign — restore pending and ignore
        if (!input.fakePos().equals(pos)) {
            pending.put(uuid, input);
            return;
        }

        // Remove the fake sign block
        player.networkHandler.sendPacket(
            new BlockUpdateS2CPacket(input.fakePos(), Blocks.AIR.getDefaultState()));

        // Read first non-blank line
        String text = "";
        for (String line : lines) {
            if (line != null && !line.isBlank()) { text = line.trim(); break; }
        }
        if (text.isEmpty()) return;

        switch (input.type()) {
            case RENAME            -> handleRename(player, input.plotPos(), text);
            case ADD_MEMBER        -> handleAddMember(player, input.plotPos(), text);
            case CREATE_GROUP      -> handleCreateGroup(player, input.plotPos(), text);
            case SET_ENTER_MESSAGE -> handleSetMessage(player, input.plotPos(), text, true);
            case SET_EXIT_MESSAGE  -> handleSetMessage(player, input.plotPos(), text, false);
        }
    }

    // ── Action handlers ───────────────────────────────────────────────────────

    private static void handleRename(ServerPlayerEntity player, BlockPos plotPos, String newName) {
        PlotData data = getPlot(player, plotPos);
        if (data == null) return;

        boolean isAdmin = isAdmin(player);
        if (!data.getOwnerId().equals(player.getUuid()) && !isAdmin) {
            player.sendMessage(Text.translatable("sp.error.not_owner"), false);
            reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.INFO);
            return;
        }
        if (newName.isEmpty() || newName.length() > 32) {
            player.sendMessage(Text.translatable("sp.rename.invalid_name"), false);
            reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.INFO);
            return;
        }

        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        PlotManager manager = PlotManager.getOrCreate(sw);

        for (PlotData p : manager.getPlayerPlots(data.getOwnerId())) {
            if (p != data && p.getPlotName().equalsIgnoreCase(newName)) {
                player.sendMessage(Text.translatable("sp.rename.duplicate"), false);
                reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.INFO);
                return;
            }
        }

        String old = data.getPlotName();
        data.setPlotName(newName);
        manager.markDirty();

        sw.playSound(null, player.getBlockPos(),
            SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 1f, 2f);
        player.sendMessage(
            Text.literal("✔ ").formatted(Formatting.GREEN)
                .append(Text.literal("\"" + old + "\"").formatted(Formatting.GRAY))
                .append(Text.literal(" → ").formatted(Formatting.GREEN))
                .append(Text.literal("\"" + newName + "\"").formatted(Formatting.YELLOW)),
            false);
        reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.INFO);
    }

    private static void handleAddMember(ServerPlayerEntity player, BlockPos plotPos, String targetName) {
        PlotData data = getPlot(player, plotPos);
        if (data == null) return;

        boolean isAdmin = isAdmin(player);
        if (!data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_MEMBERS) && !isAdmin) {
            reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.MEMBERS);
            return;
        }

        ServerPlayerEntity target = player.getServer().getPlayerManager().getPlayer(targetName);
        if (target == null) {
            player.sendMessage(Text.translatable("sp.add.player_not_found", targetName), false);
            reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.MEMBERS);
            return;
        }
        if (target.getUuid().equals(player.getUuid()) && !isAdmin) {
            player.sendMessage(Text.translatable("sp.add.self"), false);
            reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.MEMBERS);
            return;
        }
        if (data.getRoleOf(target.getUuid()) != PlotData.Role.VISITOR) {
            player.sendMessage(Text.translatable("sp.add.already_member", targetName), false);
            reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.MEMBERS);
            return;
        }

        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        PlotManager manager = PlotManager.getOrCreate(sw);

        data.addMember(target.getUuid(), target.getName().getString(), PlotData.Role.MEMBER);
        manager.markDirty();

        sw.playSound(null, player.getBlockPos(),
            SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.PLAYERS, 1f, 1.5f);
        player.sendMessage(Text.translatable("sp.member.added_sender", targetName), false);
        target.sendMessage(Text.translatable("sp.member.added_target",
            data.getPlotName(), player.getName().getString()), false);
        reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.MEMBERS);
    }

    private static void handleCreateGroup(ServerPlayerEntity player, BlockPos plotPos, String groupName) {
        PlotData data = getPlot(player, plotPos);
        if (data == null) return;

        boolean isAdmin = isAdmin(player);
        if (!data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS) && !isAdmin) {
            player.sendMessage(Text.translatable("sp.error.no_group_perm"), false);
            reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.MEMBERS);
            return;
        }
        if (groupName.isEmpty() || groupName.length() > 24) {
            player.sendMessage(Text.translatable("sp.group.invalid_name"), false);
            reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.MEMBERS);
            return;
        }
        if (data.getGroup(groupName) != null) {
            player.sendMessage(Text.translatable("sp.group.already_exists"), false);
            reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.MEMBERS);
            return;
        }

        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        PlotManager manager = PlotManager.getOrCreate(sw);

        data.getOrCreateGroup(groupName);
        manager.markDirty();
        player.sendMessage(Text.translatable("sp.group.created", groupName), false);
        reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.GLOBAL_PERMS);
    }

    private static void handleSetMessage(ServerPlayerEntity player, BlockPos plotPos, String msg, boolean isEnter) {
        PlotData data = getPlot(player, plotPos);
        if (data == null) return;

        if (!data.getOwnerId().equals(player.getUuid())) {
            player.sendMessage(Text.translatable("sp.error.not_owner"), false);
            reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.AMBIENT);
            return;
        }

        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        PlotManager manager = PlotManager.getOrCreate(sw);

        if (isEnter) data.setEnterMessage(msg);
        else         data.setExitMessage(msg);
        manager.markDirty();

        player.sendMessage(Text.translatable("sp.message.updated", msg), false);
        reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.AMBIENT);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns the PlotData for the given position, or null (sending an error message). */
    private static PlotData getPlot(ServerPlayerEntity player, BlockPos plotPos) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return null;
        PlotData data = PlotManager.getOrCreate(sw).getPlot(plotPos);
        if (data == null) player.sendMessage(Text.translatable("sp.error.not_in_plot"), false);
        return data;
    }

    private static boolean isAdmin(ServerPlayerEntity player) {
        String tag = SecurePlotsConfig.INSTANCE != null ? SecurePlotsConfig.INSTANCE.adminTag : "plot_admin";
        return player.getCommandTags().contains(tag);
    }

    private static void reopenMenu(ServerPlayerEntity player, BlockPos plotPos, PlotMenuHandler.MenuPage page) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        PlotData fresh = PlotManager.getOrCreate(sw).getPlot(plotPos);
        if (fresh == null) return;
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, inv, p) -> new PlotMenuHandler(syncId, inv, plotPos, fresh, page),
            Text.literal("🛡 " + fresh.getPlotName())
        ));
    }
}
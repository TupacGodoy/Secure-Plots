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
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
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

public class SignInputManager {

    public enum InputType { RENAME, ADD_MEMBER, CREATE_GROUP, SET_ENTER_MESSAGE, SET_EXIT_MESSAGE,
                            SET_PARTICLE, SET_MUSIC }

    public record PendingInput(BlockPos fakePos, BlockPos plotPos, InputType type) {}

    private static final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

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

    public static void openForParticle(ServerPlayerEntity player, BlockPos plotPos) {
        open(player, plotPos, InputType.SET_PARTICLE);
    }

    public static void openForMusic(ServerPlayerEntity player, BlockPos plotPos) {
        open(player, plotPos, InputType.SET_MUSIC);
    }

    private static void open(ServerPlayerEntity player, BlockPos plotPos, InputType type) {
        BlockPos fakePos = new BlockPos(player.getBlockX(), player.getBlockY() + 2, player.getBlockZ());
        pending.put(player.getUuid(), new PendingInput(fakePos, plotPos, type));

        player.networkHandler.sendPacket(
            new BlockUpdateS2CPacket(fakePos, Blocks.OAK_WALL_SIGN.getDefaultState()));

        try {
            NbtCompound signNbt = new NbtCompound();
            signNbt.putString("id", "minecraft:sign");
            signNbt.putInt("x", fakePos.getX());
            signNbt.putInt("y", fakePos.getY());
            signNbt.putInt("z", fakePos.getZ());
            NbtCompound front = new NbtCompound();
            front.putString("color", "black");
            front.putBoolean("has_glowing_text", false);
            signNbt.put("front_text", front);
            NbtCompound back = new NbtCompound();
            back.putString("color", "black");
            back.putBoolean("has_glowing_text", false);
            signNbt.put("back_text", back);
            signNbt.putBoolean("is_waxed", false);

            var pktClass = net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.class;
            var ctor = pktClass.getDeclaredConstructor(
                net.minecraft.util.math.BlockPos.class,
                net.minecraft.block.entity.BlockEntityType.class,
                NbtCompound.class);
            ctor.setAccessible(true);
            player.networkHandler.sendPacket(
                (Packet<?>) ctor.newInstance(
                    fakePos, net.minecraft.block.entity.BlockEntityType.SIGN, signNbt));
        } catch (Exception e) {
            com.zhilius.secureplots.SecurePlots.LOGGER.error("SecurePlots: failed to send sign BE packet: {}", e.getMessage());
        }

        player.networkHandler.sendPacket(new SignEditorOpenS2CPacket(fakePos, true));
    }

    public static boolean hasPending(UUID uuid) {
        return pending.containsKey(uuid);
    }

    public static void handleSignUpdate(ServerPlayerEntity player, BlockPos pos, String[] lines) {
        UUID uuid = player.getUuid();
        PendingInput input = pending.remove(uuid);
        if (input == null) return;
        if (!input.fakePos().equals(pos)) {
            pending.put(uuid, input);
            return;
        }

        player.networkHandler.sendPacket(
            new BlockUpdateS2CPacket(input.fakePos(), Blocks.AIR.getDefaultState()));

        String text = "";
        for (String line : lines) {
            if (line != null && !line.isBlank()) { text = line.trim(); break; }
        }
        if (text.isEmpty()) return;

        switch (input.type()) {
            case RENAME             -> handleRename(player, input.plotPos(), text);
            case ADD_MEMBER         -> handleAddMember(player, input.plotPos(), text);
            case CREATE_GROUP       -> handleCreateGroup(player, input.plotPos(), text);
            case SET_ENTER_MESSAGE  -> handleSetMessage(player, input.plotPos(), text, true);
            case SET_EXIT_MESSAGE   -> handleSetMessage(player, input.plotPos(), text, false);
            case SET_PARTICLE       -> handleSetParticle(player, input.plotPos(), text);
            case SET_MUSIC          -> handleSetMusic(player, input.plotPos(), text);
        }
    }

    // ── Rename ────────────────────────────────────────────────────────────────
    private static void handleRename(ServerPlayerEntity player, BlockPos plotPos, String newName) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        PlotManager manager = PlotManager.getOrCreate(sw);
        PlotData data = manager.getPlot(plotPos);
        if (data == null) {
            player.sendMessage(Text.literal("✗ Plot not found.").formatted(Formatting.RED), false);
            return;
        }
        boolean isAdmin = player.getCommandTags().contains(SecurePlotsConfig.INSTANCE != null ? SecurePlotsConfig.INSTANCE.adminTag : "plot_admin");
        if (!data.getOwnerId().equals(player.getUuid()) && !isAdmin) {
            player.sendMessage(Text.literal("✗ Only the owner can rename.").formatted(Formatting.RED), false);
            reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.INFO);
            return;
        }
        newName = newName.trim();
        if (newName.isEmpty() || newName.length() > 32) {
            player.sendMessage(Text.literal("✗ Invalid name (1-32 characters).").formatted(Formatting.RED), false);
            reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.INFO);
            return;
        }
        for (PlotData p : manager.getPlayerPlots(data.getOwnerId())) {
            if (p != data && p.getPlotName().equalsIgnoreCase(newName)) {
                player.sendMessage(Text.literal("✗ A plot with that name already exists.").formatted(Formatting.RED), false);
                reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.INFO);
                return;
            }
        }
        String old = data.getPlotName();
        data.setPlotName(newName);
        manager.markDirty();
        sw.playSound(null, player.getBlockPos(),
            SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
            SoundCategory.PLAYERS, 1f, 2f);
        player.sendMessage(
            Text.literal("✔ ").formatted(Formatting.GREEN)
                .append(Text.literal("\"" + old + "\"").formatted(Formatting.GRAY))
                .append(Text.literal(" → ").formatted(Formatting.GREEN))
                .append(Text.literal("\"" + newName + "\"").formatted(Formatting.YELLOW)),
            false);
        reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.INFO);
    }

    // ── Add Member ────────────────────────────────────────────────────────────
    private static void handleAddMember(ServerPlayerEntity player, BlockPos plotPos, String targetName) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        PlotManager manager = PlotManager.getOrCreate(sw);
        PlotData data = manager.getPlot(plotPos);
        if (data == null) {
            player.sendMessage(Text.literal("✗ Plot not found.").formatted(Formatting.RED), false);
            return;
        }
        boolean isAdmin = player.getCommandTags().contains(SecurePlotsConfig.INSTANCE != null ? SecurePlotsConfig.INSTANCE.adminTag : "plot_admin");
        if (!data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_MEMBERS) && !isAdmin) {
            reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.MEMBERS);
            return;
        }
        ServerPlayerEntity target = player.getServer().getPlayerManager().getPlayer(targetName);
        if (target == null) {
            player.sendMessage(Text.literal("✗ \"" + targetName + "\" is not online.").formatted(Formatting.RED), false);
            reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.MEMBERS);
            return;
        }
        if (target.getUuid().equals(player.getUuid()) && !isAdmin) {
            player.sendMessage(Text.literal("✗ You cannot add yourself.").formatted(Formatting.RED), false);
            reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.MEMBERS);
            return;
        }
        if (data.getRoleOf(target.getUuid()) != PlotData.Role.VISITOR) {
            player.sendMessage(Text.literal("✗ " + targetName + " already has access.").formatted(Formatting.YELLOW), false);
            reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.MEMBERS);
            return;
        }
        data.addMember(target.getUuid(), target.getName().getString(), PlotData.Role.MEMBER);
        manager.markDirty();
        sw.playSound(null, player.getBlockPos(),
            SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
            SoundCategory.PLAYERS, 1f, 1.5f);
        player.sendMessage(Text.literal("✔ " + targetName + " added as a member.").formatted(Formatting.GREEN), false);
        target.sendMessage(Text.literal("✔ You were added to \"" + data.getPlotName() + "\" by " + player.getName().getString()).formatted(Formatting.GREEN), false);
        reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.MEMBERS);
    }

    // ── Create Group ──────────────────────────────────────────────────────────
    private static void handleCreateGroup(ServerPlayerEntity player, BlockPos plotPos, String groupName) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        PlotManager manager = PlotManager.getOrCreate(sw);
        PlotData data = manager.getPlot(plotPos);
        if (data == null) {
            player.sendMessage(Text.literal("✗ Plot not found.").formatted(Formatting.RED), false);
            return;
        }
        boolean isAdmin = player.getCommandTags().contains(SecurePlotsConfig.INSTANCE != null ? SecurePlotsConfig.INSTANCE.adminTag : "plot_admin");
        if (!data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS) && !isAdmin) {
            player.sendMessage(Text.literal("✗ You do not have permission to create groups.").formatted(Formatting.RED), false);
            reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.MEMBERS);
            return;
        }
        groupName = groupName.trim();
        if (groupName.isEmpty() || groupName.length() > 24) {
            player.sendMessage(Text.literal("✗ Invalid group name (1-24 characters).").formatted(Formatting.RED), false);
            reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.MEMBERS);
            return;
        }
        if (data.getGroup(groupName) != null) {
            player.sendMessage(Text.literal("✗ Ya existe un grupo con ese nombre.").formatted(Formatting.YELLOW), false);
            reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.MEMBERS);
            return;
        }
        data.getOrCreateGroup(groupName);
        manager.markDirty();
        player.sendMessage(Text.literal("§a✔ Group §d\"" + groupName + "\" §acreated."), false);
        reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.GLOBAL_PERMS);
    }

    // ── Set Enter/Exit Message ────────────────────────────────────────────────
    private static void handleSetMessage(ServerPlayerEntity player, BlockPos plotPos, String msg, boolean isEnter) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        PlotManager manager = PlotManager.getOrCreate(sw);
        PlotData data = manager.getPlot(plotPos);
        if (data == null) {
            player.sendMessage(Text.literal("✗ Plot not found.").formatted(Formatting.RED), false);
            return;
        }
        if (!data.getOwnerId().equals(player.getUuid())) {
            player.sendMessage(Text.literal("✗ Only the owner can change messages.").formatted(Formatting.RED), false);
            reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.INFO);
            return;
        }
        msg = msg.trim();
        if (isEnter) data.setEnterMessage(msg);
        else         data.setExitMessage(msg);
        manager.markDirty();
        player.sendMessage(Text.literal("§a✔ Message updated: §f" + msg), false);
        reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.AMBIENT);
    }

    // ── Set Particle ──────────────────────────────────────────────────────────
    private static void handleSetParticle(ServerPlayerEntity player, BlockPos plotPos, String value) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        PlotManager manager = PlotManager.getOrCreate(sw);
        PlotData data = manager.getPlot(plotPos);
        if (data == null) { player.sendMessage(Text.literal("✗ Plot not found.").formatted(Formatting.RED), false); return; }
        boolean isAdmin = player.getCommandTags().contains(
            com.zhilius.secureplots.config.SecurePlotsConfig.INSTANCE != null
            ? com.zhilius.secureplots.config.SecurePlotsConfig.INSTANCE.adminTag : "plot_admin");
        boolean canEdit = data.getOwnerId().equals(player.getUuid())
            || data.getRoleOf(player.getUuid()) == PlotData.Role.ADMIN || isAdmin;
        if (!canEdit) { player.sendMessage(Text.literal("✗ Only the owner or admin can change this.").formatted(Formatting.RED), false); return; }
        data.setParticleEffect(value.trim());
        manager.markDirty();
        player.sendMessage(Text.literal("§a✔ Particle set to: §f" + value.trim()), false);
        reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.AMBIENT);
    }

    // ── Set Music ─────────────────────────────────────────────────────────────
    private static void handleSetMusic(ServerPlayerEntity player, BlockPos plotPos, String value) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        PlotManager manager = PlotManager.getOrCreate(sw);
        PlotData data = manager.getPlot(plotPos);
        if (data == null) { player.sendMessage(Text.literal("✗ Plot not found.").formatted(Formatting.RED), false); return; }
        boolean isAdmin = player.getCommandTags().contains(
            com.zhilius.secureplots.config.SecurePlotsConfig.INSTANCE != null
            ? com.zhilius.secureplots.config.SecurePlotsConfig.INSTANCE.adminTag : "plot_admin");
        boolean canEdit = data.getOwnerId().equals(player.getUuid())
            || data.getRoleOf(player.getUuid()) == PlotData.Role.ADMIN || isAdmin;
        if (!canEdit) { player.sendMessage(Text.literal("✗ Only the owner or admin can change this.").formatted(Formatting.RED), false); return; }
        data.setMusicSound(value.trim());
        manager.markDirty();
        player.sendMessage(Text.literal("§a✔ Music set to: §f" + value.trim()), false);
        reopenMenu(player, plotPos, PlotMenuHandler.MenuPage.AMBIENT);
    }

    private static void reopenMenu(ServerPlayerEntity player, BlockPos plotPos, PlotMenuHandler.MenuPage targetPage) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        PlotManager manager = PlotManager.getOrCreate(sw);
        PlotData fresh = manager.getPlot(plotPos);
        if (fresh == null) return;
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, inv, p) -> new PlotMenuHandler(syncId, inv, plotPos, fresh, targetPage),
            Text.literal("🛡 " + fresh.getPlotName())
        ));
    }
}

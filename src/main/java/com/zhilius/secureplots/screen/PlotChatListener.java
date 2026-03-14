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

import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotManager;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;

import java.util.List;
import java.util.UUID;

public class PlotChatListener {

    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            String text = message.getContent().getString().trim();
            UUID uuid = sender.getUuid();

            // Pending rename
            if (PendingRename.has(uuid)) {
                BlockPos plotPos = PendingRename.consume(uuid);
                handleRename(sender, plotPos, text);
                return false; // cancel the chat message
            }

            // Pending add member
            if (PendingAdd.has(uuid)) {
                BlockPos plotPos = PendingAdd.consume(uuid);
                handleAddMember(sender, plotPos, text);
                return false;
            }

            return true;
        });
    }

    private static void handleRename(ServerPlayerEntity player, BlockPos plotPos, String newName) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        PlotManager manager = PlotManager.getOrCreate(sw);
        PlotData data = manager.getPlot(plotPos);
        if (data == null || !data.getOwnerId().equals(player.getUuid())) {
            player.sendMessage(Text.literal("✗ Plot not found.").formatted(Formatting.RED), false);
            return;
        }
        newName = newName.trim();
        if (newName.isEmpty() || newName.length() > 32) {
            player.sendMessage(Text.literal("✗ Invalid name (1-32 characters).").formatted(Formatting.RED), false);
            return;
        }
        for (PlotData p : manager.getPlayerPlots(player.getUuid())) {
            if (p != data && p.getPlotName().equalsIgnoreCase(newName)) {
                player.sendMessage(Text.literal("✗ You already have a plot with that name.").formatted(Formatting.RED), false);
                return;
            }
        }
        String old = data.getPlotName();
        data.setPlotName(newName);
        manager.markDirty();
        player.sendMessage(
            Text.literal("✔ ").formatted(Formatting.GREEN)
                .append(Text.literal("\"" + old + "\"").formatted(Formatting.GRAY))
                .append(Text.literal(" → ").formatted(Formatting.GREEN))
                .append(Text.literal("\"" + newName + "\"").formatted(Formatting.YELLOW)),
            false);
        // Reabrir menú con datos actualizados
        reopenMenu(player, plotPos, manager);
    }

    private static void handleAddMember(ServerPlayerEntity player, BlockPos plotPos, String targetName) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        PlotManager manager = PlotManager.getOrCreate(sw);
        PlotData data = manager.getPlot(plotPos);
        if (data == null) {
            player.sendMessage(Text.literal("✗ Plot not found.").formatted(Formatting.RED), false);
            return;
        }
        PlotData.Role myRole = data.getRoleOf(player.getUuid());
        if (myRole != PlotData.Role.OWNER && myRole != PlotData.Role.ADMIN) return;

        ServerPlayerEntity target = player.getServer().getPlayerManager().getPlayer(targetName);
        if (target == null) {
            player.sendMessage(Text.literal("✗ \"" + targetName + "\" is not online.").formatted(Formatting.RED), false);
            reopenMenu(player, plotPos, manager);
            return;
        }
        if (target.getUuid().equals(player.getUuid())) {
            player.sendMessage(Text.literal("✗ You cannot add yourself.").formatted(Formatting.RED), false);
            reopenMenu(player, plotPos, manager);
            return;
        }
        if (data.getRoleOf(target.getUuid()) != PlotData.Role.VISITOR) {
            player.sendMessage(Text.literal("✗ " + targetName + " ya tiene acceso.").formatted(Formatting.YELLOW), false);
            reopenMenu(player, plotPos, manager);
            return;
        }
        data.addMember(target.getUuid(), target.getName().getString(), PlotData.Role.MEMBER);
        manager.markDirty();
        sw.playSound(null, player.getBlockPos(),
            net.minecraft.sound.SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
            net.minecraft.sound.SoundCategory.PLAYERS, 1f, 1.5f);
        player.sendMessage(Text.literal("✔ " + targetName + " added as a member.").formatted(Formatting.GREEN), false);
        target.sendMessage(Text.literal("✔ Fuiste agregado a \"" + data.getPlotName() + "\" de " + player.getName().getString()).formatted(Formatting.GREEN), false);
        reopenMenu(player, plotPos, manager);
    }

    private static void reopenMenu(ServerPlayerEntity player, BlockPos plotPos, PlotManager manager) {
        PlotData fresh = manager.getPlot(plotPos);
        if (fresh == null) return;
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, inv, p) -> new PlotMenuHandler(syncId, inv, plotPos, fresh, PlotMenuHandler.MenuPage.INFO),
            Text.literal("🛡 " + fresh.getPlotName())
        ));
    }
}

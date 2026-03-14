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
package com.zhilius.secureplots.hologram;

import com.zhilius.secureplots.SecurePlots;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotSize;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlotHologram {

    private static final String TAG = "sp_holo";
    private static final Map<BlockPos, UUID> active = new HashMap<>();
    private static final Map<UUID, int[]>    timers = new HashMap<>();

    public static void registerTicker() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof DisplayEntity.TextDisplayEntity disp) {
                Text name = disp.getCustomName();
                if (name != null && TAG.equals(name.getString())) {
                    world.getServer().execute(disp::discard);
                }
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            timers.entrySet().removeIf(e -> {
                e.getValue()[0]--;
                if (e.getValue()[0] <= 0) {
                    UUID id = e.getKey();
                    active.values().remove(id);
                    for (ServerWorld w : server.getWorlds()) {
                        net.minecraft.entity.Entity ent = w.getEntity(id);
                        if (ent != null) { ent.discard(); return true; }
                    }
                    return true;
                }
                return false;
            });
        });
    }

    public static void spawn(ServerWorld world, BlockPos blockPos, PlotData data,
                              int durationTicks, float playerYaw) {
        spawn(world, blockPos, data, durationTicks, playerYaw, null);
    }

    public static void spawn(ServerWorld world, BlockPos blockPos, PlotData data,
                              int durationTicks, float playerYaw, ServerPlayerEntity player) {
        // Remove previous hologram at this position
        UUID prev = active.remove(blockPos);
        if (prev != null) {
            timers.remove(prev);
            net.minecraft.entity.Entity e = world.getEntity(prev);
            if (e != null) e.discard();
        }

        // Spawn 1.5 blocks above the plot block (was 2.5)
        double x = blockPos.getX() + 0.5;
        double y = blockPos.getY() + 1.5;
        double z = blockPos.getZ() + 0.5;

        // Raycast: if blocks obstruct view from player to hologram, send chat instead
        if (player != null) {
            Vec3d playerEye = player.getEyePos();
            Vec3d holoPos   = new Vec3d(x, y, z);
            BlockHitResult hit = world.raycast(new RaycastContext(
                playerEye, holoPos,
                RaycastContext.ShapeType.VISUAL,
                RaycastContext.FluidHandling.NONE,
                player
            ));
            if (hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(blockPos)) {
                // Something is blocking the view — send info to chat instead
                sendToChat(player, data);
                return;
            }
        }

        DisplayEntity.TextDisplayEntity disp = EntityType.TEXT_DISPLAY.create(world);
        if (disp == null) {
            SecurePlots.LOGGER.error("[PlotHologram] TEXT_DISPLAY.create() null");
            return;
        }

        // Set via NBT — same as /summon
        NbtCompound nbt = new NbtCompound();
        NbtList rotation = new NbtList();
        rotation.add(NbtFloat.of(playerYaw + 180f));
        rotation.add(NbtFloat.of(0f));
        nbt.put("Rotation", rotation);
        nbt.putByte("billboard", (byte) 0); // FIXED
        nbt.putString("text", buildJson(data));
        nbt.putInt("background", 0xC0000000); // semi-transparent black background
        nbt.putInt("line_width", 200);
        nbt.putByte("text_opacity", (byte) 255);
        nbt.putByte("shadow", (byte) 0);
        nbt.putByte("see_through", (byte) 0);
        nbt.putFloat("scale", 1.0f);
        disp.readNbt(nbt);
        disp.setPos(x, y, z);

        world.spawnEntity(disp);
        SecurePlots.LOGGER.info("[PlotHologram] spawned uuid={} at {},{},{} yaw={}", disp.getUuid(), x, y, z, playerYaw);

        active.put(blockPos, disp.getUuid());
        timers.put(disp.getUuid(), new int[]{durationTicks});
    }

    public static void remove(ServerWorld world, BlockPos pos) {
        UUID id = active.remove(pos);
        if (id != null) {
            timers.remove(id);
            net.minecraft.entity.Entity e = world.getEntity(id);
            if (e != null) e.discard();
        }
    }

    public static boolean hasSpace(ServerWorld world, BlockPos blockPos) { return true; }

    public static void clearAll(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            world.getEntitiesByType(EntityType.TEXT_DISPLAY,
                    e -> { Text n = e.getCustomName(); return n != null && TAG.equals(n.getString()); }
            ).forEach(net.minecraft.entity.Entity::discard);
        }
        active.clear();
        timers.clear();
    }


    private static void sendToChat(ServerPlayerEntity player, PlotData data) {
        String tc = tierColorCode(data.getSize().tier);
        String name = (data.getPlotName() != null && !data.getPlotName().isBlank())
                ? data.getPlotName() : "Parcela Protegida";
        PlotSize next = data.getSize().next();
        String nextLine = (next != null)
                ? "\u00a77Siguiente: " + tierColorCode(next.tier) + "\u00a7l" + next.getDisplayName()
                : "\u00a76\u00a7lNivel M\u00e1ximo";

        player.sendMessage(net.minecraft.text.Text.literal(
            tc + "\u00a7l" + name.toUpperCase()), false);
        player.sendMessage(net.minecraft.text.Text.literal(
            "\u00a78- - - - - - - - - - - - -"), false);
        player.sendMessage(net.minecraft.text.Text.literal(
            "\u00a77Due\u00f1o:    \u00a7f" + data.getOwnerName()), false);
        player.sendMessage(net.minecraft.text.Text.literal(
            "\u00a77Nivel:    " + tc + "\u00a7l" + data.getSize().getDisplayName()), false);
        player.sendMessage(net.minecraft.text.Text.literal(
            "\u00a77Tama\u00f1o:   \u00a7b" + data.getSize().getRadius() + "x" + data.getSize().getRadius()), false);
        player.sendMessage(net.minecraft.text.Text.literal(
            "\u00a77Miembros: \u00a7a" + data.getMembers().size()), false);
        player.sendMessage(net.minecraft.text.Text.literal(
            "\u00a78- - - - - - - - - - - - -"), false);
        player.sendMessage(net.minecraft.text.Text.literal(nextLine), false);
    }

    private static String tierColorCode(int tier) {
        return switch (tier) {
            case 0 -> "\u00a76";  // Bronce - dorado
            case 1 -> "\u00a7e";  // Oro - amarillo
            case 2 -> "\u00a7a";  // Esmeralda - verde
            case 3 -> "\u00a7b";  // Diamante - aqua
            case 4 -> "\u00a75";  // Netherita - purpura
            default -> "\u00a7f";
        };
    }

    /** Measure string width in Minecraft font pixels (no color codes) */
    private static int mcWidth(String s) {
        String clean = s.replaceAll("§.", "");
        int w = 0;
        for (char ch : clean.toCharArray()) {
            if (ch == ' ') { w += 4; }
            else if (ch == 'i' || ch == '!' || ch == '.' || ch == ':' || ch == ';' || ch == '|') { w += 2; }
            else if (ch == 'l') { w += 3; }
            else if (ch == 'f' || ch == 'k' || ch == 't') { w += 5; }
            else { w += 6; }
        }
        return w;
    }

    private static String buildJson(PlotData data) {
        String name = (data.getPlotName() != null && !data.getPlotName().isBlank())
                ? data.getPlotName().toUpperCase() : "PARCELA PROTEGIDA";
        PlotSize next = data.getSize().next();
        String tc = tierColorCode(data.getSize().tier);

        // Arrow: thin vertical bar body + triangle tip (unicode)
        String ntc = next != null ? tierColorCode(next.tier) : "";
        String nextLine = (next != null)
                ? "\u00a7e\u2b06 \u00a77Siguiente: " + ntc + "\u00a7l" + next.getDisplayName()
                : "\u00a76\u00a7l\u2605 Nivel Maximo \u2605";

        // Measure each visible line in Minecraft font pixels
        // § codes are stripped inside mcWidth()
        String raw_name    = " " + name;
        String raw_dueno   = " Owner:    " + data.getOwnerName();
        String raw_nivel   = " Nivel:    " + data.getSize().getDisplayName();
        String raw_tamano  = " Size:     " + data.getSize().getRadius() + "x" + data.getSize().getRadius();
        String raw_members = " Miembros: " + data.getMembers().size();
        String raw_next    = next != null
                ? " ⬡ Next: " + next.getDisplayName()
                : " ★ Nivel Maximo ★";

        int maxPx = 0;
        for (String l : new String[]{raw_name, raw_dueno, raw_nivel, raw_tamano, raw_members, raw_next}) {
            maxPx = Math.max(maxPx, mcWidth(l));
        }

        // '-' = 6px wide in Minecraft font — use it for both border and divider
        int dashCount = maxPx / 6 + 6; // +3 dashes each side
        StringBuilder borderSB  = new StringBuilder();
        StringBuilder dividerSB = new StringBuilder();
        for (int i = 0; i < dashCount; i++) { borderSB.append("-"); dividerSB.append("-"); }
        String border  = tc  + "\u00a7l" + borderSB;
        String divider = "\u00a78" + dividerSB;
        String NL = "\n";

        String text =
            border + NL +
            divider + NL +
            tc + "\u00a7l " + name + NL +
            divider + NL +
            "\u00a77 Due\u00f1o:    \u00a7f" + data.getOwnerName() + NL +
            "\u00a77 Nivel:    " + tc + "\u00a7l" + data.getSize().getDisplayName() + NL +
            "\u00a77 Tama\u00f1o:   \u00a7b" + data.getSize().getRadius() + "x" + data.getSize().getRadius() + NL +
            "\u00a77 Miembros: \u00a7a" + data.getMembers().size() + NL +
            divider + NL +
            " " + nextLine + NL +
            border;

        text = text.replace("\"", "\\"");
        return "{\"text\":\"" + text + "\"}";
    }
}

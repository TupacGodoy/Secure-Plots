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
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side hologram spawning for plot blocks.
 * Note: visual rendering is handled client-side by {@code PlotHologramClient}.
 * This class handles spawning TextDisplayEntity holograms and their lifecycle.
 */
public class PlotHologram {

    private static final String TAG = "sp_holo";
    private static final Map<BlockPos, UUID> active = new HashMap<>();
    private static final Map<UUID, int[]>    timers = new HashMap<>();

    public static void registerTicker() {
        // Discard any leftover holograms from previous sessions on load
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof DisplayEntity.TextDisplayEntity disp) {
                Text name = disp.getCustomName();
                if (name != null && TAG.equals(name.getString()))
                    world.getServer().execute(disp::discard);
            }
        });

        // Tick down hologram timers and discard expired ones
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
                             int durationTicks, float playerYaw, ServerPlayerEntity player) {
        // Remove previous hologram at this position
        UUID prev = active.remove(blockPos);
        if (prev != null) {
            timers.remove(prev);
            net.minecraft.entity.Entity e = world.getEntity(prev);
            if (e != null) e.discard();
        }

        double x = blockPos.getX() + 0.5;
        double y = blockPos.getY() + 1.5;
        double z = blockPos.getZ() + 0.5;

        // If blocks obstruct the view from player to hologram, fall back to chat
        if (player != null) {
            BlockHitResult hit = world.raycast(new RaycastContext(
                player.getEyePos(), new Vec3d(x, y, z),
                RaycastContext.ShapeType.VISUAL,
                RaycastContext.FluidHandling.NONE,
                player));
            if (hit.getType() == HitResult.Type.BLOCK && !hit.getBlockPos().equals(blockPos)) {
                sendToChat(player, data);
                return;
            }
        }

        DisplayEntity.TextDisplayEntity disp = EntityType.TEXT_DISPLAY.create(world);
        if (disp == null) {
            SecurePlots.LOGGER.error("[PlotHologram] TEXT_DISPLAY.create() returned null");
            return;
        }

        NbtCompound nbt = new NbtCompound();
        NbtList rotation = new NbtList();
        rotation.add(NbtFloat.of(playerYaw + 180f));
        rotation.add(NbtFloat.of(0f));
        nbt.put("Rotation", rotation);
        nbt.putByte("billboard",    (byte) 0);   // FIXED
        nbt.putString("text",       buildJson(data));
        nbt.putInt("background",    0xC0000000); // semi-transparent black
        nbt.putInt("line_width",    200);
        nbt.putByte("text_opacity", (byte) 255);
        nbt.putByte("shadow",       (byte) 0);
        nbt.putByte("see_through",  (byte) 0);
        nbt.putFloat("scale",       1.0f);
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

    public static void clearAll(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            world.getEntitiesByType(EntityType.TEXT_DISPLAY,
                e -> { Text n = e.getCustomName(); return n != null && TAG.equals(n.getString()); }
            ).forEach(net.minecraft.entity.Entity::discard);
        }
        active.clear();
        timers.clear();
    }

    // ── Chat fallback (when hologram is obstructed) ───────────────────────────

    private static void sendToChat(ServerPlayerEntity player, PlotData data) {
        String tc   = tierColorCode(data.getSize().tier);
        String name = plotName(data);
        PlotSize next = data.getSize().next();
        String nextLine = next != null
            ? "\u00a77Next: " + tierColorCode(next.tier) + "\u00a7l" + next.getDisplayName()
            : "\u00a76\u00a7lMaximum Level";

        player.sendMessage(Text.literal(tc + "\u00a7l" + name.toUpperCase()), false);
        player.sendMessage(Text.literal("\u00a78- - - - - - - - - - - - -"), false);
        player.sendMessage(Text.literal("\u00a77Owner:   \u00a7f"  + data.getOwnerName()), false);
        player.sendMessage(Text.literal("\u00a77Tier:    " + tc + "\u00a7l" + data.getSize().getDisplayName()), false);
        player.sendMessage(Text.literal("\u00a77Size:    \u00a7b"  + data.getSize().getRadius() + "x" + data.getSize().getRadius()), false);
        player.sendMessage(Text.literal("\u00a77Members: \u00a7a"  + data.getMembers().size()), false);
        player.sendMessage(Text.literal("\u00a78- - - - - - - - - - - - -"), false);
        player.sendMessage(Text.literal(nextLine), false);
    }

    // ── Hologram JSON builder ─────────────────────────────────────────────────

    private static String buildJson(PlotData data) {
        String name   = plotName(data).toUpperCase();
        PlotSize next = data.getSize().next();
        String tc     = tierColorCode(data.getSize().tier);
        String ntc    = next != null ? tierColorCode(next.tier) : "";
        String nextLine = next != null
            ? "\u00a7e\u2b06 \u00a77Next: " + ntc + "\u00a7l" + next.getDisplayName()
            : "\u00a76\u00a7l\u2605 Maximum Level \u2605";

        // Calculate border width based on longest line
        String[] lines = {
            " " + name,
            " Owner:   " + data.getOwnerName(),
            " Tier:    " + data.getSize().getDisplayName(),
            " Size:    " + data.getSize().getRadius() + "x" + data.getSize().getRadius(),
            " Members: " + data.getMembers().size(),
            next != null ? " \u2b06 Next: " + next.getDisplayName() : " \u2605 Maximum Level \u2605"
        };
        int maxPx = 0;
        for (String l : lines) maxPx = Math.max(maxPx, mcWidth(l));

        int dashCount = maxPx / 6 + 6;
        String dashes = "-".repeat(dashCount);
        String border  = tc + "\u00a7l" + dashes;
        String divider = "\u00a78" + dashes;
        String NL = "\n";

        String text =
            border  + NL +
            divider + NL +
            tc + "\u00a7l " + name + NL +
            divider + NL +
            "\u00a77 Owner:   \u00a7f" + data.getOwnerName() + NL +
            "\u00a77 Tier:    " + tc + "\u00a7l" + data.getSize().getDisplayName() + NL +
            "\u00a77 Size:    \u00a7b" + data.getSize().getRadius() + "x" + data.getSize().getRadius() + NL +
            "\u00a77 Members: \u00a7a" + data.getMembers().size() + NL +
            divider + NL +
            " " + nextLine + NL +
            border;

        return "{\"text\":\"" + text.replace("\"", "\\") + "\"}";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns the plot name, falling back to a default if blank. */
    private static String plotName(PlotData data) {
        return (data.getPlotName() != null && !data.getPlotName().isBlank())
            ? data.getPlotName() : "Protected Plot";
    }

    private static String tierColorCode(int tier) {
        return switch (tier) {
            case 0 -> "\u00a76"; // Bronze  - gold
            case 1 -> "\u00a7e"; // Gold    - yellow
            case 2 -> "\u00a7a"; // Emerald - green
            case 3 -> "\u00a7b"; // Diamond - aqua
            case 4 -> "\u00a75"; // Netherite - purple
            default -> "\u00a7f";
        };
    }

    /** Estimates string width in Minecraft font pixels (strips color codes). */
    private static int mcWidth(String s) {
        String clean = s.replaceAll("§.", "");
        int w = 0;
        for (char ch : clean.toCharArray()) {
            w += switch (ch) {
                case ' '                              -> 4;
                case 'i', '!', '.', ':', ';', '|'    -> 2;
                case 'l'                              -> 3;
                case 'f', 'k', 't'                   -> 5;
                default                              -> 6;
            };
        }
        return w;
    }
}
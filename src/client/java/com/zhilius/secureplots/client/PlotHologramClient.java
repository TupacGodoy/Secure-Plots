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
package com.zhilius.secureplots.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotSize;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PlotHologramClient {

    private static final List<HologramDisplay> active = new ArrayList<>();

    private static class HologramDisplay {
        PlotData data;
        BlockPos pos;
        long     expiresAt;

        // Yaw captured on first render frame, then fixed forever
        float   fixedYaw    = 0f;
        boolean yawCaptured = false;

        HologramDisplay(PlotData data, BlockPos pos, long durationMs) {
            this.data      = data;
            this.pos       = pos;
            this.expiresAt = System.currentTimeMillis() + durationMs;
        }
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) return;

            long now = System.currentTimeMillis();
            Iterator<HologramDisplay> iter = active.iterator();
            while (iter.hasNext()) {
                HologramDisplay h = iter.next();
                if (h.expiresAt < now) { iter.remove(); continue; }
                renderHologram(context, h, mc);
            }
        });
    }

    public static void show(PlotData data, BlockPos pos, long durationMs) {
        for (HologramDisplay h : active) {
            if (h.pos.equals(pos)) {
                h.data        = data;
                h.expiresAt   = System.currentTimeMillis() + durationMs;
                h.yawCaptured = false; // re-capture orientation on next render
                return;
            }
        }
        active.add(new HologramDisplay(data, pos, durationMs));
    }

    public static void show(PlotData data, BlockPos pos, long durationMs, float yaw) {
        show(data, pos, durationMs); // yaw ignored — captured on first render
    }

    public static void hide(BlockPos pos) {
        active.removeIf(h -> h.pos.equals(pos));
    }

    public static void clear() {
        active.clear();
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    private static void renderHologram(WorldRenderContext context,
                                        HologramDisplay h, MinecraftClient mc) {
        Vec3d cam = context.camera().getPos();
        double hx = h.pos.getX() + 0.5;
        double hy = h.pos.getY() + 3.8; // 2 blocks above plot block
        double hz = h.pos.getZ() + 0.5;

        double dx = hx - cam.x;
        double dy = hy - cam.y;
        double dz = hz - cam.z;

        if (dx * dx + dy * dy + dz * dz > 24 * 24) return;

        // Capture yaw on first render — +PI so the front face looks at the player.
        // The -scale on X (needed to un-mirror text) flips winding again,
        // so both PIs cancel and the front stays correct.
        if (!h.yawCaptured) {
            h.fixedYaw    = (float) Math.atan2(-dx, -dz);
            h.yawCaptured = true;
        }

        String[] lines = buildLines(h.data);

        MatrixStack matrices = context.matrixStack();
        matrices.push();

        matrices.translate(dx, dy, dz);
        matrices.multiply(new Quaternionf().rotationY(h.fixedYaw));

        // scale(+x,-y,z) + atan2(dx,dz) = correct orientation all 4 cardinals, text not mirrored
        float scale = 0.025f;
        matrices.scale(scale, -scale, scale);

        TextRenderer tr = mc.textRenderer;

        int maxWidth = 0;
        for (String line : lines) maxWidth = Math.max(maxWidth, tr.getWidth(line));
        int panelW = maxWidth + 8;
        int panelH = lines.length * (tr.fontHeight + 1) + 6;
        int startX = -panelW / 2;
        int startY = -panelH / 2;

        Matrix4f mx = matrices.peek().getPositionMatrix();

        // Semi-transparent black background (same as original: 0xC0000000)
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        buf.vertex(mx, startX,          startY,          0).color(0f, 0f, 0f, 0.75f);
        buf.vertex(mx, startX + panelW, startY,          0).color(0f, 0f, 0f, 0.75f);
        buf.vertex(mx, startX + panelW, startY + panelH, 0).color(0f, 0f, 0f, 0.75f);
        buf.vertex(mx, startX,          startY + panelH, 0).color(0f, 0f, 0f, 0.75f);
        BufferRenderer.drawWithGlobalProgram(buf.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();

        // Draw text lines centered — no mirroring needed with scale(+x,...)
        int y = startY + 3;
        for (String line : lines) {
            int x = startX + (panelW - tr.getWidth(line)) / 2;
            tr.draw(line, x, y, 0xFFFFFFFF, false, mx,
                    mc.getBufferBuilders().getEntityVertexConsumers(),
                    TextRenderer.TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            y += tr.fontHeight + 1;
        }
        mc.getBufferBuilders().getEntityVertexConsumers().draw();

        RenderSystem.disableBlend();
        matrices.pop();
    }

    /**
     * Reproduces the exact layout from PlotHologram.buildJson():
     *
     *   §tc§l -------- (tier-colored border)
     *   §8-------- (gray divider)
     *   §tc§l NAME
     *   §8--------
     *   §7 Dueño:    §f owner
     *   §7 Nivel:    §tc§l tier
     *   §7 Tamaño:   §b RxR
     *   §7 Miembros: §a count
     *   §8--------
     *   §e⬆ §7Siguiente: §ntc§l nextTier  /  §6§l★ Nivel Maximo ★
     *   §tc§l --------
     */
    private static String[] buildLines(PlotData data) {
        String name = (data.getPlotName() != null && !data.getPlotName().isBlank())
                ? data.getPlotName().toUpperCase() : "PARCELA PROTEGIDA";

        String tc     = tierColor(data.getSize().tier);
        PlotSize next = data.getSize().next();
        String ntc    = next != null ? tierColor(next.tier) : "";

        String nextLine = (next != null)
                ? "§e\u2b06 §7Siguiente: " + ntc + "§l" + next.getDisplayName()
                : "§6§l\u2605 Nivel Maximo \u2605";

        // Build dash lines sized to the widest content line
        String[] content = {
            " " + name,
            " Dueño:    " + data.getOwnerName(),
            " Nivel:    " + data.getSize().getDisplayName(),
            " Tamaño:   " + data.getSize().getRadius() + "x" + data.getSize().getRadius(),
            " Miembros: " + data.getMembers().size(),
            next != null ? " Next: " + next.getDisplayName() : " Nivel Maximo"
        };
        int maxPx = 0;
        for (String s : content) maxPx = Math.max(maxPx, mcWidth(s));
        int dashCount = maxPx / 6 + 6;
        String dashes = "-".repeat(dashCount);
        String border  = tc + "§l" + dashes;
        String divider = "§8" + dashes;

        return new String[]{
            border,
            divider,
            tc + "§l " + name,
            divider,
            "§7 Dueño:    §f" + data.getOwnerName(),
            "§7 Nivel:    " + tc + "§l" + data.getSize().getDisplayName(),
            "§7 Tamaño:   §b" + data.getSize().getRadius() + "x" + data.getSize().getRadius(),
            "§7 Miembros: §a" + data.getMembers().size(),
            divider,
            " " + nextLine,
            border
        };
    }

    /** Approximate Minecraft font width in pixels (strips § color codes) */
    private static int mcWidth(String s) {
        String clean = s.replaceAll("§.", "");
        int w = 0;
        for (char ch : clean.toCharArray()) {
            if      (ch == ' ')                                              w += 4;
            else if ("i!.:;|".indexOf(ch) >= 0)                            w += 2;
            else if (ch == 'l')                                             w += 3;
            else if ("fkt".indexOf(ch) >= 0)                               w += 5;
            else                                                            w += 6;
        }
        return w;
    }

    private static String tierColor(int tier) {
        return switch (tier) {
            case 0 -> "§6";  // bronze
            case 1 -> "§e";  // gold
            case 2 -> "§a";  // emerald
            case 3 -> "§b";  // diamond
            case 4 -> "§5";  // netherite
            default -> "§f";
        };
    }
}

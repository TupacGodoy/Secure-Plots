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
import com.zhilius.secureplots.config.BorderConfig;
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
        long     createdAt;

        // Yaw captured on first render frame, then fixed forever
        float   fixedYaw    = 0f;
        boolean yawCaptured = false;

        HologramDisplay(PlotData data, BlockPos pos, long durationMs) {
            this.data      = data;
            this.pos       = pos;
            long now       = System.currentTimeMillis();
            this.createdAt = now;
            this.expiresAt = now + durationMs;
        }
    }

    // ── i18n ─────────────────────────────────────────────────────────────────

    private static boolean isSpanish() {
        String lang = MinecraftClient.getInstance().getLanguageManager().getLanguage();
        return lang != null && lang.startsWith("es");
    }

    private static String t(boolean es, String en, String esp) {
        return es ? esp : en;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) return;

            long now = System.currentTimeMillis();
            Iterator<HologramDisplay> iter = active.iterator();
            while (iter.hasNext()) {
                HologramDisplay h = iter.next();
                if (h.expiresAt < now) { iter.remove(); continue; }
                renderHologram(context, h, mc, now);
            }
        });
    }

    public static void show(PlotData data, BlockPos pos, long durationMs) {
        for (HologramDisplay h : active) {
            if (h.pos.equals(pos)) {
                h.data        = data;
                long now      = System.currentTimeMillis();
                h.createdAt   = now;
                h.expiresAt   = now + durationMs;
                h.yawCaptured = false;
                return;
            }
        }
        active.add(new HologramDisplay(data, pos, durationMs));
    }

    public static void show(PlotData data, BlockPos pos, long durationMs, float yaw) {
        show(data, pos, durationMs);
    }

    public static void hide(BlockPos pos) {
        active.removeIf(h -> h.pos.equals(pos));
    }

    public static void clear() {
        active.clear();
    }

    // ── Rendering ────────────────────────────────────────────────────────────

    private static void renderHologram(WorldRenderContext context,
                                        HologramDisplay h, MinecraftClient mc, long now) {
        BorderConfig cfg = PlotBorderRendererConfig.current;

        float maxDist = cfg.hologramMaxDistance;
        Vec3d cam = context.camera().getPos();

        // Float animation — bob up and down
        double floatOffset = 0.0;
        if (cfg.hologramFloat) {
            float ft = (now % cfg.hologramFloatCycleMs) / (float) cfg.hologramFloatCycleMs;
            floatOffset = cfg.hologramFloatAmplitude * Math.sin(ft * Math.PI * 2);
        }

        double dx = h.pos.getX() + 0.5 - cam.x;
        double dy = h.pos.getY() + cfg.hologramHeight + floatOffset - cam.y;
        double dz = h.pos.getZ() + 0.5 - cam.z;

        if (dx * dx + dy * dy + dz * dz > maxDist * maxDist) return;

        if (!h.yawCaptured) {
            h.fixedYaw    = (float) Math.atan2(-dx, -dz);
            h.yawCaptured = true;
        }

        // Fade in / fade out alpha multiplier
        float alpha = 1.0f;
        long elapsed = now - h.createdAt;
        long remaining = h.expiresAt - now;
        if (elapsed < cfg.hologramFadeInMs) {
            alpha = (float) elapsed / cfg.hologramFadeInMs;
        } else if (remaining < cfg.hologramFadeOutMs) {
            alpha = (float) remaining / cfg.hologramFadeOutMs;
        }
        alpha = Math.max(0f, Math.min(1f, alpha));

        // Calculate language once for all t() calls
        boolean es = isSpanish();
        String[] lines = buildLines(h.data, es);

        MatrixStack matrices = context.matrixStack();
        matrices.push();
        matrices.translate(dx, dy, dz);
        matrices.multiply(new Quaternionf().rotationY(h.fixedYaw));

        float scale = cfg.hologramScale;
        matrices.scale(scale, -scale, scale);

        TextRenderer tr = mc.textRenderer;

        // Calculate line widths once — reuse for centering
        int[] widths = new int[lines.length];
        int maxWidth = 0;
        for (int i = 0; i < lines.length; i++) {
            widths[i] = tr.getWidth(lines[i]);
            if (widths[i] > maxWidth) maxWidth = widths[i];
        }

        int panelW = maxWidth + cfg.hologramPaddingX;
        int panelH = lines.length * (tr.fontHeight + cfg.hologramLineSpacing) + cfg.hologramPaddingY;
        int startX = -panelW / 2;
        int startY = -panelH / 2;

        Matrix4f mx = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        float bgAlpha = cfg.hologramBackgroundOpacity * alpha;
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        // Front face
        buf.vertex(mx, startX,          startY,          0).color(0f, 0f, 0f, bgAlpha);
        buf.vertex(mx, startX + panelW, startY,          0).color(0f, 0f, 0f, bgAlpha);
        buf.vertex(mx, startX + panelW, startY + panelH, 0).color(0f, 0f, 0f, bgAlpha);
        buf.vertex(mx, startX,          startY + panelH, 0).color(0f, 0f, 0f, bgAlpha);
        // Back face (reversed winding — visible from behind without text)
        buf.vertex(mx, startX,          startY + panelH, 0).color(0f, 0f, 0f, bgAlpha);
        buf.vertex(mx, startX + panelW, startY + panelH, 0).color(0f, 0f, 0f, bgAlpha);
        buf.vertex(mx, startX + panelW, startY,          0).color(0f, 0f, 0f, bgAlpha);
        buf.vertex(mx, startX,          startY,          0).color(0f, 0f, 0f, bgAlpha);
        BufferRenderer.drawWithGlobalProgram(buf.end());

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();

        // Draw text lines — fade applied to text color alpha
        int textAlpha = (int)(255 * alpha) << 24 | 0x00FFFFFF;
        int y = startY + 3;
        VertexConsumerProvider.Immediate consumers = mc.getBufferBuilders().getEntityVertexConsumers();
        for (int i = 0; i < lines.length; i++) {
            int x = startX + (panelW - widths[i]) / 2;
            tr.draw(lines[i], x, y, textAlpha, false, mx,
                    consumers, TextRenderer.TextLayerType.NORMAL, 0,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE);
            y += tr.fontHeight + cfg.hologramLineSpacing;
        }
        consumers.draw();

        RenderSystem.disableBlend();
        matrices.pop();
    }

    private static String[] buildLines(PlotData data, boolean es) {
        String name = (data.getPlotName() != null && !data.getPlotName().isBlank())
                ? data.getPlotName().toUpperCase()
                : t(es, "PROTECTED PLOT", "PARCELA PROTEGIDA");

        String tc     = tierColor(data.getSize().tier);
        PlotSize next = data.getSize().next();
        String ntc    = next != null ? tierColor(next.tier) : "";
        String radius = data.getSize().getRadius() + "x" + data.getSize().getRadius();

        String nextLine = (next != null)
                ? "\u00a7e\u2b06 \u00a77" + t(es, "Next: ", "Siguiente: ") + ntc + "\u00a7l" + next.getDisplayName()
                : "\u00a76\u00a7l\u2605 " + t(es, "Max Level", "Nivel Maximo") + " \u2605";

        String ownerLabel   = t(es, "Owner:   ", "Due\u00f1o:   ");
        String tierLabel    = t(es, "Tier:    ", "Nivel:    ");
        String sizeLabel    = t(es, "Size:    ", "Tama\u00f1o:  ");
        String membersLabel = t(es, "Members: ", "Miembros: ");
        String nextLabel    = t(es, "Next: ",    "Siguiente: ");
        String maxLabel     = t(es, "Max Level", "Nivel Maximo");

        int maxPx = mcWidth(" " + name);
        maxPx = Math.max(maxPx, mcWidth(" " + ownerLabel   + data.getOwnerName()));
        maxPx = Math.max(maxPx, mcWidth(" " + tierLabel    + data.getSize().getDisplayName()));
        maxPx = Math.max(maxPx, mcWidth(" " + sizeLabel    + radius));
        maxPx = Math.max(maxPx, mcWidth(" " + membersLabel + data.getMembers().size()));
        maxPx = Math.max(maxPx, mcWidth(" " + (next != null ? nextLabel + next.getDisplayName() : maxLabel)));

        int dashCount = maxPx / 6 + 6;
        String dashes  = "-".repeat(dashCount);
        String border  = tc + "\u00a7l" + dashes;
        String divider = "\u00a78" + dashes;

        return new String[]{
            border,
            divider,
            tc + "\u00a7l " + name,
            divider,
            "\u00a77 " + ownerLabel   + "\u00a7f" + data.getOwnerName(),
            "\u00a77 " + tierLabel    + tc + "\u00a7l" + data.getSize().getDisplayName(),
            "\u00a77 " + sizeLabel    + "\u00a7b" + radius,
            "\u00a77 " + membersLabel + "\u00a7a" + data.getMembers().size(),
            divider,
            " " + nextLine,
            border
        };
    }

    /** Approximate Minecraft font width in pixels — manual loop, no regex. */
    private static int mcWidth(String s) {
        int w = 0;
        int len = s.length();
        for (int i = 0; i < len; i++) {
            char ch = s.charAt(i);
            if (ch == '\u00a7') { i++; continue; }
            if      (ch == ' ')                  w += 4;
            else if ("i!.:;|".indexOf(ch) >= 0) w += 2;
            else if (ch == 'l')                  w += 3;
            else if ("fkt".indexOf(ch) >= 0)     w += 5;
            else                                 w += 6;
        }
        return w;
    }

    private static String tierColor(int tier) {
        return switch (tier) {
            case 0 -> "\u00a76";  // bronze
            case 1 -> "\u00a7e";  // gold
            case 2 -> "\u00a7a";  // emerald
            case 3 -> "\u00a7b";  // diamond
            case 4 -> "\u00a75";  // netherite
            default -> "\u00a7f";
        };
    }
}

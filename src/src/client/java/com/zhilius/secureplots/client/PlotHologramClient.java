package com.zhilius.secureplots.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotSize;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.*;

public class PlotHologramClient {

    public record HologramEntry(PlotData data, BlockPos pos, long expiresAt) {}
    public static final List<HologramEntry> active = new ArrayList<>();

    // Captured each frame from WorldRenderEvents so HUD can project 3D->2D
    private static Matrix4f projMatrix  = null;
    private static Matrix4f modelMatrix = null;
    private static Vec3d    lastCamPos  = Vec3d.ZERO;

    // Fixed world position per hologram — set on show(), never updated
    private static final Map<BlockPos, Vec3d> fixedWorldPos = new HashMap<>();

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> {
            projMatrix  = new Matrix4f(ctx.projectionMatrix());
            Quaternionf rot = new Quaternionf(ctx.camera().getRotation()).conjugate();
            modelMatrix = new Matrix4f().rotate(rot);
            lastCamPos  = ctx.camera().getPos();
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (projMatrix == null || modelMatrix == null) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) return;

            long now = System.currentTimeMillis();
            active.removeIf(e -> {
                if (e.expiresAt() < now) {
                    fixedWorldPos.remove(e.pos());
                    return true;
                }
                return false;
            });

            for (HologramEntry entry : active) {
                BlockPos pos = entry.pos();

                // Use the fixed world position — hologram stays immobile in 3D space
                Vec3d fixed = fixedWorldPos.get(pos);
                if (fixed == null) continue;

                double wx = fixed.x - lastCamPos.x;
                double wy = fixed.y - lastCamPos.y;
                double wz = fixed.z - lastCamPos.z;

                float[] screen = project(wx, wy, wz, mc);
                if (screen == null) continue;

                drawPanel(drawContext, mc, screen[0], screen[1], entry.data(), 1.0f);
            }
        });
    }

    public static void show(PlotData data, BlockPos pos, long durationMs) {
        active.removeIf(e -> e.pos().equals(pos));
        active.add(new HologramEntry(data, pos, System.currentTimeMillis() + durationMs));
        // Lock fixed world position once — never moves again
        fixedWorldPos.put(pos, new Vec3d(pos.getX() + 0.5, pos.getY() + 2.5, pos.getZ() + 0.5));
    }

    public static void hide(BlockPos pos) {
        active.removeIf(e -> e.pos().equals(pos));
        fixedWorldPos.remove(pos);
    }

    public static void clear() {
        active.clear();
        fixedWorldPos.clear();
    }

    private static float[] project(double wx, double wy, double wz, MinecraftClient mc) {
        if (projMatrix == null || modelMatrix == null) return null;

        Vector4f v = new Vector4f((float)wx, (float)wy, (float)wz, 1f);
        modelMatrix.transform(v);
        projMatrix.transform(v);

        if (v.w <= 0) return null;

        float ndcX =  v.x / v.w;
        float ndcY = -v.y / v.w;

        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        float sx = (ndcX + 1f) / 2f * sw;
        float sy = (ndcY + 1f) / 2f * sh;

        if (sx < -300 || sx > sw+300 || sy < -300 || sy > sh+300) return null;

        return new float[]{sx, sy};
    }

    private static void drawPanel(DrawContext ctx, MinecraftClient mc,
                                   float cx, float cy, PlotData data, float alpha) {
        List<String> lines = buildLines(data);
        int lineH = mc.textRenderer.fontHeight + 2;
        int maxW = 0;
        for (String line : lines) {
            maxW = Math.max(maxW, mc.textRenderer.getWidth(line));
        }
        int padX = 8, padY = 6;
        int pw = maxW + padX * 2;
        int ph = lines.size() * lineH + padY * 2;
        int px = (int)(cx - pw / 2f);
        int py = (int)(cy - ph / 2f);

        // Panel: slightly more transparent (alpha 140 instead of 180)
        int a = (int)(alpha * 140);
        float[] bc = tierColor(data.getSize().tier);
        float pulse = 0.7f + 0.3f * (float) Math.sin(System.currentTimeMillis() * 0.003);
        int ba = (int)(alpha * pulse * 220);

        // Background
        ctx.fill(px, py, px+pw, py+ph, color(8, 8, 20, a));

        // Border: only TOP and BOTTOM lines, drawn INSIDE the black panel
        int br = (int)(bc[0]*255), bg2 = (int)(bc[1]*255), bb = (int)(bc[2]*255);
        ctx.fill(px + 2, py + 2,    px+pw - 2, py + 3,    color(br, bg2, bb, ba)); // top
        ctx.fill(px + 2, py+ph - 3, px+pw - 2, py+ph - 2, color(br, bg2, bb, ba)); // bottom

        // Text — single language (Spanish only)
        int ty = py + padY;
        for (String line : lines) {
            ctx.drawText(mc.textRenderer, line, px + padX, ty, 0xFFFFFF | (a << 24), false);
            ty += lineH;
        }
    }

    private static int color(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static List<String> buildLines(PlotData data) {
        List<String> lines = new ArrayList<>();
        String name = (data.getPlotName() != null && !data.getPlotName().isBlank())
                ? data.getPlotName().toUpperCase() : "PARCELA PROTEGIDA";
        lines.add("§6§l" + name);
        lines.add("§8────────────────");
        lines.add("§7Dueño:   §f" + data.getOwnerName());
        lines.add("§7Nivel:   §b" + data.getSize().displayName);
        lines.add("§7Tamaño:  §b" + data.getSize().radius + "×" + data.getSize().radius);
        String members = data.getMembers().isEmpty() ? "§7Ninguno" : "§a" + data.getMembers().size();
        lines.add("§7Miembros: " + members);
        lines.add("§8────────────────");
        PlotSize next = data.getSize().next();
        if (next != null) lines.add("§e⬆ §7Siguiente: §b" + next.displayName);
        else              lines.add("§6★ §eNivel Máximo");
        lines.add("§8────────────────");
        lines.add("§e» §7Usá el §6Plano §7para gestionar");
        return lines;
    }

    private static float[] tierColor(int tier) {
        return switch (tier) {
            case 0  -> new float[]{1.0f, 0.55f, 0.05f};
            case 1  -> new float[]{1.0f, 0.85f, 0.00f};
            case 2  -> new float[]{0.1f, 0.90f, 0.20f};
            case 3  -> new float[]{0.15f,0.95f, 1.00f};
            case 4  -> new float[]{0.6f, 0.20f, 0.80f};
            default -> new float[]{1.0f, 1.00f, 1.00f};
        };
    }
}

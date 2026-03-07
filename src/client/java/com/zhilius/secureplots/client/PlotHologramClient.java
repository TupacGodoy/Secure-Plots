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

    // Captured each frame from WorldRenderEvents so HUD can project 3D→2D
    private static Matrix4f projMatrix   = null;
    private static Matrix4f modelMatrix  = null;
    private static Vec3d    lastCamPos   = Vec3d.ZERO;

    public static void register() {
        // Capture matrices every frame
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> {
            projMatrix  = new Matrix4f(ctx.projectionMatrix());
            // Build view matrix from camera rotation
            Quaternionf rot = new Quaternionf(ctx.camera().getRotation()).conjugate();
            modelMatrix = new Matrix4f().rotate(rot);
            lastCamPos  = ctx.camera().getPos();
        });

        // Draw hologram as HUD — text and quads work perfectly here
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (projMatrix == null || modelMatrix == null) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.world == null) return;

            long now = System.currentTimeMillis();
            active.removeIf(e -> e.expiresAt() < now);

            for (HologramEntry entry : active) {
                BlockPos pos = entry.pos();
                double wx = pos.getX() + 0.5 - lastCamPos.x;
                double wy = pos.getY() + 2.5 - lastCamPos.y;
                double wz = pos.getZ() + 0.5 - lastCamPos.z;

                // Don't show if behind camera
                float[] screen = project(wx, wy, wz, mc);
                if (screen == null) continue;

                float alpha = 1.0f;
                drawPanel(drawContext, mc, screen[0], screen[1], entry.data(), alpha);
            }
        });
    }

    public static void show(PlotData data, BlockPos pos, long durationMs) {
        active.removeIf(e -> e.pos().equals(pos));
        active.add(new HologramEntry(data, pos, System.currentTimeMillis() + durationMs));
    }
    public static void hide(BlockPos pos) { active.removeIf(e -> e.pos().equals(pos)); }
    public static void clear() { active.clear(); }

    /** Projects a world-relative position to screen pixels. Returns null if behind camera. */
    private static float[] project(double wx, double wy, double wz, MinecraftClient mc) {
        if (projMatrix == null || modelMatrix == null) return null;

        Vector4f v = new Vector4f((float)wx, (float)wy, (float)wz, 1f);
        modelMatrix.transform(v);
        projMatrix.transform(v);

        if (v.w <= 0) return null; // behind camera

        float ndcX =  v.x / v.w;
        float ndcY = -v.y / v.w;

        // NDC to screen
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        float sx = (ndcX + 1f) / 2f * sw;
        float sy = (ndcY + 1f) / 2f * sh;

        // Clamp to screen
        if (sx < -300 || sx > sw+300 || sy < -300 || sy > sh+300) return null;

        return new float[]{sx, sy};
    }

    private static void drawPanel(DrawContext ctx, MinecraftClient mc,
                                   float cx, float cy, PlotData data, float alpha) {
        List<String[]> lines = buildLines(data); // each entry: [english, spanish]
        int lineH = mc.textRenderer.fontHeight + 2;
        int maxW = 0;
        for (String[] pair : lines) {
            String combined = pair[0] + "  " + pair[1];
            maxW = Math.max(maxW, mc.textRenderer.getWidth(combined));
        }
        int padX = 8, padY = 6;
        int pw = maxW + padX * 2;
        int ph = lines.size() * lineH + padY * 2;
        int px = (int)(cx - pw / 2f);
        int py = (int)(cy - ph / 2f);

        // Slightly transparent
        int a = (int)(alpha * 180);
        float[] bc = tierColor(data.getSize().tier);
        float pulse = 0.7f + 0.3f * (float) Math.sin(System.currentTimeMillis() * 0.003);
        int ba = (int)(alpha * pulse * 220);

        // Background
        ctx.fill(px, py, px+pw, py+ph, color(8, 8, 20, a));

        // Inner border (1px, tier color)
        int br = (int)(bc[0]*255), bg2 = (int)(bc[1]*255), bb = (int)(bc[2]*255);
        ctx.fill(px,      py,      px+pw,   py+1,    color(br,bg2,bb,ba));
        ctx.fill(px,      py+ph-1, px+pw,   py+ph,   color(br,bg2,bb,ba));
        ctx.fill(px,      py,      px+1,    py+ph,   color(br,bg2,bb,ba));
        ctx.fill(px+pw-1, py,      px+pw,   py+ph,   color(br,bg2,bb,ba));

        // Outer border made of dashes (─)
        int dash = mc.textRenderer.getWidth("-");
        int outerA = (int)(alpha * pulse * 160);
        int outerColor = color(br,bg2,bb,outerA);
        // top & bottom rows of dashes
        int ox = px - 2, oy = py - (mc.textRenderer.fontHeight + 1);
        int endX = px + pw + 2;
        for (int dx = ox; dx < endX; dx += dash + 1) {
            ctx.drawText(mc.textRenderer, "§r-", dx, oy, outerColor, false);
            ctx.drawText(mc.textRenderer, "§r-", dx, py + ph + 1, outerColor, false);
        }
        // left & right columns of dashes
        for (int dy = py; dy < py + ph; dy += mc.textRenderer.fontHeight + 1) {
            ctx.drawText(mc.textRenderer, "§r-", ox - dash, dy, outerColor, false);
            ctx.drawText(mc.textRenderer, "§r-", endX + 1,  dy, outerColor, false);
        }

        // Text: english (white) + spanish (gray)
        int ty = py + padY;
        for (String[] pair : lines) {
            int engW = mc.textRenderer.getWidth(pair[0]);
            ctx.drawText(mc.textRenderer, pair[0], px + padX, ty, 0xFFFFFF | (a << 24), false);
            ctx.drawText(mc.textRenderer, pair[1], px + padX + engW + 4, ty, 0xAAAAAA | (a << 24), false);
            ty += lineH;
        }
    }

    private static int color(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    // Returns pairs of [english, spanish] for each line
    private static List<String[]> buildLines(PlotData data) {
        List<String[]> lines = new ArrayList<>();
        String name = (data.getPlotName() != null && !data.getPlotName().isBlank())
                ? data.getPlotName().toUpperCase() : "PROTECTED PLOT";
        String nameSp = (data.getPlotName() != null && !data.getPlotName().isBlank())
                ? "" : "PARCELA PROTEGIDA";
        lines.add(new String[]{"§6§l" + name, "§6§l" + nameSp});
        lines.add(new String[]{"§8----------------", ""});
        lines.add(new String[]{"§7Owner: §f" + data.getOwnerName(), "§8Dueño"});
        lines.add(new String[]{"§7Level: §b" + data.getSize().displayName, "§8Nivel"});
        lines.add(new String[]{"§7Size: §b" + data.getSize().radius + "×" + data.getSize().radius, "§8Tamaño"});
        String mEng = data.getMembers().isEmpty() ? "§7None" : "§a" + data.getMembers().size();
        String mSp  = data.getMembers().isEmpty() ? "§8Ninguno" : "";
        lines.add(new String[]{"§7Members: " + mEng, mSp});
        lines.add(new String[]{"§8----------------", ""});
        PlotSize next = data.getSize().next();
        if (next != null) lines.add(new String[]{"§e⬆ §7Next: §b" + next.displayName, "§8Siguiente"});
        else              lines.add(new String[]{"§6★ §eMax Level", "§8Nivel Máximo"});
        lines.add(new String[]{"§8----------------", ""});
        lines.add(new String[]{"§e» §7Use the §6Blueprint §7to manage", "§8Usá el Plano"});
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

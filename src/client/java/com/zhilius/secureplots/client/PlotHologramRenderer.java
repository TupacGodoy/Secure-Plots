package com.zhilius.secureplots.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zhilius.secureplots.plot.PlotData;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public class PlotHologramRenderer {

    private static final double MAX_DISTANCE = 16.0;

    private static final Identifier PANEL_TEXTURE =
            Identifier.of("secure-plots", "textures/item/hologram_panel.png");

    private static final int PANEL_W = 180;
    private static final int PANEL_H = 90;
    private static final int HEADER_H = 16;
    private static final int TEXT_PADDING_X = 8;
    private static final int TEXT_PADDING_Y = 4;

    private static final int COLOR_TITLE  = 0xFFFF8200;
    private static final int COLOR_LABEL  = 0xFFAAAAAA;
    private static final int COLOR_VALUE  = 0xFFFFFFFF;
    private static final int COLOR_FOOTER = 0xFFFF8200;

    private static Matrix4f savedProjection = null;
    private static Matrix4f savedModelView  = null;

    public static BlockPos hudTargetPos  = null;
    public static PlotData hudTargetData = null;
    public static long     hudExpiresAt  = 0;

    public static void captureMatrices(WorldRenderContext ctx) {
        savedProjection = new Matrix4f(ctx.projectionMatrix());
        savedModelView  = new Matrix4f().rotation(ctx.camera().getRotation());
    }

    public static void registerHud() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (hudTargetData == null) return;
            if (System.currentTimeMillis() > hudExpiresAt) {
                hudTargetData = null;
                hudTargetPos  = null;
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            BlockPos pos = hudTargetPos;

            if (client.world != null) {
                for (int i = 1; i <= 3; i++) {
                    if (!client.world.getBlockState(pos.up(i)).isAir()) {
                        renderCornerHud(drawContext, client, hudTargetData);
                        return;
                    }
                }
            }

            Vec3d cam = client.gameRenderer.getCamera().getPos();
            double wx = pos.getX() + 0.5 - cam.x;
            double wy = pos.getY() + 2.5 - cam.y;
            double wz = pos.getZ() + 0.5 - cam.z;

            if (wx*wx + wy*wy + wz*wz > MAX_DISTANCE * MAX_DISTANCE) return;

            float[] screen = worldToScreen(client, wx, wy, wz);
            if (screen == null) return;

            int panelX = (int) screen[0] - PANEL_W / 2;
            int panelY = (int) screen[1] - PANEL_H / 2;

            renderPanel(drawContext, client, hudTargetData, panelX, panelY);
        });
    }

    private static void renderPanel(DrawContext drawContext, MinecraftClient client,
                                    PlotData data, int px, int py) {
        MatrixStack matrices = drawContext.getMatrices();
        matrices.push();

        // ── Textura con RenderSystem (MC 1.21.1 compatible) ──────────
        RenderSystem.setShaderTexture(0, PANEL_TEXTURE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f mat = matrices.peek().getPositionMatrix();
        BufferBuilder buf = Tessellator.getInstance().begin(
                VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        float x0 = px,            y0 = py;
        float x1 = px + PANEL_W,  y1 = py + PANEL_H;

        buf.vertex(mat, x0, y1, 0).texture(0f, 1f).color(255, 255, 255, 255);
        buf.vertex(mat, x1, y1, 0).texture(1f, 1f).color(255, 255, 255, 255);
        buf.vertex(mat, x1, y0, 0).texture(1f, 0f).color(255, 255, 255, 255);
        buf.vertex(mat, x0, y0, 0).texture(0f, 0f).color(255, 255, 255, 255);

        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();

        // ── Título ───────────────────────────────────────────────────
        String name = (data.getPlotName() != null && !data.getPlotName().isBlank())
                ? data.getPlotName().toUpperCase()
                : "PARCELA PROTEGIDA";

        int headerTextY = py + TEXT_PADDING_Y + 1;
        drawContext.drawText(client.textRenderer,
                Text.literal(name),
                px + TEXT_PADDING_X + 1, headerTextY + 1,
                0x80000000, false);
        drawContext.drawText(client.textRenderer,
                Text.literal("§l" + name),
                px + TEXT_PADDING_X, headerTextY,
                COLOR_TITLE, false);

        // ── Contenido ────────────────────────────────────────────────
        int contentY = py + HEADER_H + 8;
        int lh = 10;

        renderRow(drawContext, client, "DUEÑO",    data.getOwnerName(),
                px + TEXT_PADDING_X, contentY, lh);
        renderRow(drawContext, client, "TAMAÑO",
                data.getSize().radius + " x " + data.getSize().radius + " bloques",
                px + TEXT_PADDING_X, contentY + lh, lh);
        renderRow(drawContext, client, "MIEMBROS",
                String.valueOf(data.getMembers().size()),
                px + TEXT_PADDING_X, contentY + lh * 2, lh);

        // ── Footer ───────────────────────────────────────────────────
        int footerY = py + PANEL_H - 14;
        drawContext.fill(px + 4, footerY - 3, px + PANEL_W - 4, footerY - 2, 0x60FF8200);
        drawContext.drawText(client.textRenderer,
                Text.literal("▶  CLIC DERECHO → MENÚ"),
                px + TEXT_PADDING_X, footerY,
                COLOR_FOOTER, false);

        matrices.pop();
    }

    private static void renderRow(DrawContext ctx, MinecraftClient client,
                                   String label, String value, int x, int y, int lh) {
        String labelStr = label + ": ";
        int labelW = client.textRenderer.getWidth(labelStr);
        ctx.drawText(client.textRenderer, Text.literal(labelStr),  x,          y, COLOR_LABEL, false);
        ctx.drawText(client.textRenderer, Text.literal(value),     x + labelW, y, COLOR_VALUE, false);
    }

    private static void renderCornerHud(DrawContext drawContext, MinecraftClient client, PlotData data) {
        renderPanel(drawContext, client, data, 8, 8);
    }

    private static float[] worldToScreen(MinecraftClient client, double wx, double wy, double wz) {
        if (savedProjection == null || savedModelView == null) return null;

        Vector4f point = new Vector4f((float) wx, (float) wy, (float) wz, 1.0f);
        Matrix4f view = new Matrix4f()
                .rotation(client.gameRenderer.getCamera().getRotation())
                .invert();
        view.transform(point);
        savedProjection.transform(point);

        if (point.w <= 0) return null;

        float ndcX = point.x / point.w;
        float ndcY = point.y / point.w;

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        return new float[]{
                (ndcX + 1f) / 2f * screenW,
                (1f - ndcY) / 2f * screenH
        };
    }

    public static void render(WorldRenderContext ctx, BlockPos blockPos, PlotData data) {}

    public static List<Text> buildLines(PlotData data) {
        List<Text> lines = new ArrayList<>();
        String name = (data.getPlotName() != null && !data.getPlotName().isBlank())
                ? data.getPlotName() : "Parcela Protegida";
        lines.add(Text.literal("§6§l" + name));
        lines.add(Text.literal("§7Dueño: §f"    + data.getOwnerName()));
        lines.add(Text.literal("§7Tamaño: §b"   + data.getSize().radius + "x" + data.getSize().radius));
        lines.add(Text.literal("§7Miembros: §a" + data.getMembers().size()));
        lines.add(Text.literal("§e▶ §7Clic derecho para el menú"));
        return lines;
    }
}

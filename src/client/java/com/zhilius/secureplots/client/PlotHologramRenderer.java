package com.zhilius.secureplots.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zhilius.secureplots.plot.PlotData;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

public class PlotHologramRenderer {

    private static final double MAX_DISTANCE = 16.0;

    // Proyección guardada cada frame para usar en el HUD
    private static Matrix4f savedProjection = null;
    private static Matrix4f savedModelView = null;

    public static void captureMatrices(WorldRenderContext ctx) {
        // Guardar matrices de proyección y vista del frame actual
        savedProjection = new Matrix4f(ctx.projectionMatrix());

        // Construir la modelView: rotación de cámara + identidad de posición
        // (la posición se resta manualmente en el shader)
        savedModelView = new Matrix4f().rotation(ctx.camera().getRotation());
    }

    // HUD target (para mostrar en pantalla)
    public static BlockPos hudTargetPos = null;
    public static PlotData hudTargetData = null;
    public static long hudExpiresAt = 0;

    public static void registerHud() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (hudTargetData == null) return;
            if (System.currentTimeMillis() > hudExpiresAt) {
                hudTargetData = null;
                hudTargetPos = null;
                return;
            }

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            BlockPos pos = hudTargetPos;

            // Verificar espacio arriba
            if (client.world != null) {
                for (int i = 1; i <= 3; i++) {
                    if (!client.world.getBlockState(pos.up(i)).isAir()) {
                        // Sin espacio — mostrar en esquina en vez de centro
                        renderCornerHud(drawContext, client, hudTargetData);
                        return;
                    }
                }
            }

            // Proyectar posición del mundo a pantalla
            Vec3d cam = client.gameRenderer.getCamera().getPos();
            double wx = pos.getX() + 0.5 - cam.x;
            double wy = pos.getY() + 2.5 - cam.y;
            double wz = pos.getZ() + 0.5 - cam.z;

            // Distancia
            if (wx*wx + wy*wy + wz*wz > MAX_DISTANCE * MAX_DISTANCE) return;

            float[] screen = worldToScreen(client, wx, wy, wz);
            if (screen == null) return; // detrás de la cámara

            List<Text> lines = buildLines(hudTargetData);
            int lh = 10;
            int totalH = lines.size() * lh;
            int totalW = 0;
            for (Text t : lines) totalW = Math.max(totalW, client.textRenderer.getWidth(t));

            int sx = (int) screen[0] - totalW / 2;
            int sy = (int) screen[1] - totalH / 2;

            // Fondo
            drawContext.fill(sx - 4, sy - 4, sx + totalW + 4, sy + totalH + 4, 0xA0000000);

            for (int i = 0; i < lines.size(); i++) {
                drawContext.drawText(client.textRenderer, lines.get(i), sx, sy + i * lh, 0xFFFFFF, false);
            }
        });
    }

    private static void renderCornerHud(net.minecraft.client.gui.DrawContext drawContext, MinecraftClient client, PlotData data) {
        List<Text> lines = buildLines(data);
        int lh = 10;
        int x = 8, y = 8;
        int totalW = 0;
        for (Text t : lines) totalW = Math.max(totalW, client.textRenderer.getWidth(t));
        drawContext.fill(x - 2, y - 2, x + totalW + 2, y + lines.size() * lh + 2, 0xA0000000);
        for (int i = 0; i < lines.size(); i++) {
            drawContext.drawText(client.textRenderer, lines.get(i), x, y + i * lh, 0xFFFFFF, false);
        }
    }

    /** Convierte coordenadas de mundo (relativas a la cámara) a píxeles de pantalla. */
    private static float[] worldToScreen(MinecraftClient client, double wx, double wy, double wz) {
        if (savedProjection == null || savedModelView == null) return null;

        // Aplicar rotación de vista al punto
        Vector4f point = new Vector4f((float) wx, (float) wy, (float) wz, 1.0f);
        // Rotación de cámara inversa para pasar a espacio de vista
        Matrix4f view = new Matrix4f().rotation(client.gameRenderer.getCamera().getRotation()).invert();
        view.transform(point);

        // Proyección
        savedProjection.transform(point);

        if (point.w <= 0) return null; // detrás de la cámara

        // NDC a píxeles
        float ndcX = point.x / point.w;
        float ndcY = point.y / point.w;

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        float px = (ndcX + 1f) / 2f * screenW;
        float py = (1f - ndcY) / 2f * screenH;

        return new float[]{px, py};
    }

    // No-op: todo se maneja en el HUD
    public static void render(WorldRenderContext ctx, BlockPos blockPos, PlotData data) {}

    public static List<Text> buildLines(PlotData data) {
        List<Text> lines = new ArrayList<>();
        String name = (data.getPlotName() != null && !data.getPlotName().isBlank())
                ? data.getPlotName() : "Parcela Protegida";
        lines.add(Text.literal("§6§l" + name));
        lines.add(Text.literal("§7Dueño: §f" + data.getOwnerName()));
        lines.add(Text.literal("§7Tamaño: §b" + data.getSize().radius + "x" + data.getSize().radius));
        lines.add(Text.literal("§7Miembros: §a" + data.getMembers().size()));
        lines.add(Text.literal("§e▶ §7Clic derecho para el menú"));
        return lines;
    }
}

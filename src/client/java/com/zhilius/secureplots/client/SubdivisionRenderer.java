package com.zhilius.secureplots.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotSubdivision;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Renderiza las subdivisiones de una plot en el mundo:
 *
 *  - Las aristas del polígono como líneas de puntos (quads muy pequeños).
 *  - Vértices de la esquina como esferas de punto brillante.
 *  - Si useY == true: el volumen 3D (suelo + techo + paredes punteadas).
 *  - Si useY == false: columnas infinitas (misma altura que el border renderer).
 *
 * Color: cian/verde suave para diferenciar de los bordes de energía (que son del tier-color).
 */
public class SubdivisionRenderer {

    // Color de las subdivisiones: cian brillante
    private static final float SR = 0.20f;
    private static final float SG = 1.00f;
    private static final float SB = 0.85f;

    // Color de aristas activas (en construcción): amarillo
    private static final float AR = 1.00f;
    private static final float AG = 0.90f;
    private static final float AB = 0.10f;

    /** Altura de las columnas cuando useY == false (igual que el border renderer). */
    private static final double COLUMN_HEIGHT = 25.0;

    /** Separación entre puntos del dashed-line (en bloques). */
    private static final double DOT_SPACING = 0.8;

    /** Tamaño de cada punto. */
    private static final float DOT_W = 0.07f;

    public static void render(WorldRenderContext context, PlotData plotData, boolean isEditing, String activeSub) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        List<PlotSubdivision> subs = plotData.getSubdivisions();
        if (subs.isEmpty()) return;

        double camX = context.camera().getPos().x;
        double camY = context.camera().getPos().y;
        double camZ = context.camera().getPos().z;

        long time = System.currentTimeMillis();
        float pulse = 0.55f + 0.45f * (float) Math.sin((time % 1600) / 1600.0f * Math.PI * 2);

        MatrixStack matrices = context.matrixStack();
        matrices.push();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Matrix4f mx = matrices.peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();

        for (PlotSubdivision sub : subs) {
            if (sub.points.size() < 1) continue;

            boolean isActive = isEditing && sub.name != null && sub.name.equals(activeSub);

            float r = isActive ? AR : SR;
            float g = isActive ? AG : SG;
            float b = isActive ? AB : SB;
            float baseAlpha = isActive ? 0.95f * pulse : 0.70f * pulse;

            // ── Determinar rango Y ────────────────────────────────────────
            double yBase, yTop;
            double blockBaseY = plotData.getCenter().getY() - camY;

            if (sub.useY) {
                yBase = sub.yMin - camY;
                yTop  = sub.yMax - camY;
            } else {
                yBase = blockBaseY;
                yTop  = blockBaseY + COLUMN_HEIGHT;
            }

            List<int[]> pts = sub.points;
            int n = pts.size();

            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            // ── Aristas 2D (dashed columns) ───────────────────────────────
            for (int i = 0; i < n; i++) {
                int[] a = pts.get(i);
                int[] b2 = pts.get((i + 1) % n);
                // Último segmento solo si polígono cerrado (≥3 pts) o es arista activa
                if (i == n - 1 && n < 3 && !isActive) continue;

                double ax = a[0] + 0.5 - camX;
                double az = a[1] + 0.5 - camZ;
                double bx = b2[0] + 0.5 - camX;
                double bz = b2[1] + 0.5 - camZ;

                dashedColumn(buf, mx, ax, az, bx, bz, yBase, yTop, r, g, b, baseAlpha);
            }

            // ── Vértices (puntos más brillantes en las esquinas) ─────────
            for (int[] pt : pts) {
                double px = pt[0] + 0.5 - camX;
                double pz = pt[1] + 0.5 - camZ;
                vertexDot(buf, mx, px, pz, yBase, yTop, r, g, b, baseAlpha * 1.3f, isActive);
            }

            // ── Plano suelo y techo (si useY) ────────────────────────────
            if (sub.useY && n >= 3) {
                float faceAlpha = 0.10f * pulse;
                renderFilledPolygon(buf, mx, pts, yBase - camY + sub.yMin, r, g, b, faceAlpha, camX, camZ);
                renderFilledPolygon(buf, mx, pts, yTop  - camY + sub.yMax, r, g, b, faceAlpha, camX, camZ);
            }

            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        matrices.pop();
    }

    // ── Helpers de geometría ──────────────────────────────────────────────────

    /**
     * Dibuja una columna de puntos entre (ax, az) y (bx, bz), desde yBase hasta yTop.
     * Cada "punto" es un quad pequeño.
     */
    private static void dashedColumn(BufferBuilder buf, Matrix4f m,
                                     double ax, double az, double bx, double bz,
                                     double yBase, double yTop,
                                     float r, float g, float b, float a) {
        double dx = bx - ax, dz = bz - az;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 0.01) return;

        double ux = dx / len, uz = dz / len;

        // Punteado horizontal en cada nivel de Y
        for (double y = yBase; y <= yTop; y += DOT_SPACING * 0.8) {
            float ya = (float) y;
            for (double t = 0; t <= len; t += DOT_SPACING) {
                double cx = ax + ux * t;
                double cz = az + uz * t;
                float w = DOT_W;
                buf.vertex(m, (float)(cx - w), ya, (float)(cz - w)).color(r, g, b, a);
                buf.vertex(m, (float)(cx + w), ya, (float)(cz - w)).color(r, g, b, a);
                buf.vertex(m, (float)(cx + w), ya, (float)(cz + w)).color(r, g, b, a);
                buf.vertex(m, (float)(cx - w), ya, (float)(cz + w)).color(r, g, b, a);
            }
        }

        // Columnas verticales: un punto cada DOT_SPACING a lo largo del segmento
        for (double t = 0; t <= len; t += DOT_SPACING * 1.5) {
            double cx = ax + ux * t;
            double cz = az + uz * t;
            for (double y = yBase; y <= yTop; y += DOT_SPACING) {
                float ya = (float) y;
                float w = DOT_W * 0.7f;
                buf.vertex(m, (float)(cx - w), ya - w, (float) cz).color(r, g, b, a * 0.6f);
                buf.vertex(m, (float)(cx + w), ya - w, (float) cz).color(r, g, b, a * 0.6f);
                buf.vertex(m, (float)(cx + w), ya + w, (float) cz).color(r, g, b, a * 0.6f);
                buf.vertex(m, (float)(cx - w), ya + w, (float) cz).color(r, g, b, a * 0.6f);
            }
        }
    }

    /**
     * Renderiza el marcador de vértice: puntos grandes en la posición de esquina a lo largo
     * de toda la altura.
     */
    private static void vertexDot(BufferBuilder buf, Matrix4f m,
                                   double px, double pz, double yBase, double yTop,
                                   float r, float g, float b, float a, boolean isActive) {
        float w = isActive ? DOT_W * 2.5f : DOT_W * 1.8f;

        // Punto en la base
        double dy = yBase;
        buf.vertex(m, (float)(px - w), (float) dy, (float)(pz - w)).color(r, g, b, a);
        buf.vertex(m, (float)(px + w), (float) dy, (float)(pz - w)).color(r, g, b, a);
        buf.vertex(m, (float)(px + w), (float) dy, (float)(pz + w)).color(r, g, b, a);
        buf.vertex(m, (float)(px - w), (float) dy, (float)(pz + w)).color(r, g, b, a);

        // Punto en el tope
        dy = yTop;
        buf.vertex(m, (float)(px - w), (float) dy, (float)(pz - w)).color(r, g, b, a);
        buf.vertex(m, (float)(px + w), (float) dy, (float)(pz - w)).color(r, g, b, a);
        buf.vertex(m, (float)(px + w), (float) dy, (float)(pz + w)).color(r, g, b, a);
        buf.vertex(m, (float)(px - w), (float) dy, (float)(pz + w)).color(r, g, b, a);

        // Línea vertical central del vértice (columna de puntos densa)
        for (double y = yBase; y <= yTop; y += DOT_SPACING * 0.5) {
            float ww = w * 1.1f;
            buf.vertex(m, (float)(px - ww), (float) y, (float) pz).color(r, g, b, a);
            buf.vertex(m, (float)(px + ww), (float) y, (float) pz).color(r, g, b, a);
            buf.vertex(m, (float)(px + ww), (float)(y + 0.05), (float) pz).color(r, g, b, a);
            buf.vertex(m, (float)(px - ww), (float)(y + 0.05), (float) pz).color(r, g, b, a);
        }
    }

    /**
     * Rellena el polígono 2D (fan triangulation) en un plano Y dado.
     * Solo funciona bien para polígonos convexos o quasi-convexos.
     * Para polígonos cóncavos, se puede usar ear-clipping, pero por ahora fan es suficiente.
     */
    private static void renderFilledPolygon(BufferBuilder buf, Matrix4f m,
                                            List<int[]> pts, double y,
                                            float r, float g, float b, float a,
                                            double camX, double camZ) {
        int n = pts.size();
        if (n < 3) return;
        float fy = (float) y;

        // Fan desde el primer vértice
        double ox = pts.get(0)[0] + 0.5 - camX;
        double oz = pts.get(0)[1] + 0.5 - camZ;

        for (int i = 1; i < n - 1; i++) {
            double ax = pts.get(i)[0]     + 0.5 - camX;
            double az = pts.get(i)[1]     + 0.5 - camZ;
            double bx = pts.get(i + 1)[0] + 0.5 - camX;
            double bz = pts.get(i + 1)[1] + 0.5 - camZ;

            // Dos triángulos como quad (degenerate quad)
            buf.vertex(m, (float) ox, fy, (float) oz).color(r, g, b, a);
            buf.vertex(m, (float) ax, fy, (float) az).color(r, g, b, a);
            buf.vertex(m, (float) bx, fy, (float) bz).color(r, g, b, a);
            buf.vertex(m, (float) ox, fy, (float) oz).color(r, g, b, a); // degenerate 4th vert
        }
    }
}

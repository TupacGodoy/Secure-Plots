package com.zhilius.secureplots.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zhilius.secureplots.plot.PlotData;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;

import java.util.Random;

public class PlotBorderRenderer {

    private static final Random RAND = new Random();

    public static void render(WorldRenderContext context, PlotData data) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        BlockPos center = data.getCenter();
        int half = data.getSize().radius / 2;

        double camX = context.camera().getPos().x;
        double camY = context.camera().getPos().y;
        double camZ = context.camera().getPos().z;

        double minX = (center.getX() + 0.5) - half - camX;
        double maxX = (center.getX() + 0.5) + half - camX;
        double minZ = (center.getZ() + 0.5) - half - camZ;
        double maxZ = (center.getZ() + 0.5) + half - camZ;

        double baseY = center.getY() - camY;
        double topY  = baseY + 25;

        long time  = System.currentTimeMillis();
        float t     = (time % 2000) / 2000.0f;
        float pulse = 0.6f + 0.4f * (float) Math.sin(t * Math.PI * 2);

        // Azul energía fijo — ignora rol para mantener la estética
        // Core: azul eléctrico brillante
        float r = 0.05f, g = 0.45f, b = 1.0f;
        // Glow exterior: azul más frío
        float gr = 0.0f, gg = 0.2f, gb = 0.85f;
        // Núcleo blanco-azul
        float wr = 0.7f, wg = 0.9f, wb = 1.0f;

        MatrixStack matrices = context.matrixStack();
        matrices.push();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();

        Matrix4f matrix = matrices.peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();

        // ── 1. HALO EXTERIOR GRUESO (varias capas offset para dar volumen) ─
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            float ga = 0.25f * pulse;
            // Capas de desplazamiento para simular grosor real
            float[] offsets = { -0.12f, -0.08f, -0.04f, 0.04f, 0.08f, 0.12f };

            for (float dx : offsets) {
                for (float dz : offsets) {
                    // Solo los bordes del cuadrado de offset, no el relleno
                    if (Math.abs(dx) < 0.08f && Math.abs(dz) < 0.08f) continue;
                    pillar(buf, matrix, minX+dx, minZ+dz, baseY, topY, gr, gg, gb, ga * 0.6f);
                    pillar(buf, matrix, maxX+dx, minZ+dz, baseY, topY, gr, gg, gb, ga * 0.6f);
                    pillar(buf, matrix, minX+dx, maxZ+dz, baseY, topY, gr, gg, gb, ga * 0.6f);
                    pillar(buf, matrix, maxX+dx, maxZ+dz, baseY, topY, gr, gg, gb, ga * 0.6f);
                }
            }
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        // ── 2. CONTORNO PRINCIPAL GRUESO ──────────────────────────────────
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            float ea = 0.85f * pulse;

            // Cada arista se dibuja 3 veces con offsets para dar grosor
            float[] thick = { -0.03f, 0f, 0.03f };
            for (float d : thick) {
                float da = ea * (d == 0 ? 1f : 0.6f);
                // Pilares esq
                pillar(buf, matrix, minX+d, minZ,   baseY, topY, r, g, b, da);
                pillar(buf, matrix, minX,   minZ+d, baseY, topY, r, g, b, da);
                pillar(buf, matrix, maxX+d, minZ,   baseY, topY, r, g, b, da);
                pillar(buf, matrix, maxX,   minZ+d, baseY, topY, r, g, b, da);
                pillar(buf, matrix, minX+d, maxZ,   baseY, topY, r, g, b, da);
                pillar(buf, matrix, minX,   maxZ+d, baseY, topY, r, g, b, da);
                pillar(buf, matrix, maxX+d, maxZ,   baseY, topY, r, g, b, da);
                pillar(buf, matrix, maxX,   maxZ+d, baseY, topY, r, g, b, da);
                // Aros hor
                line(buf, matrix, minX, baseY+d, minZ, maxX, baseY+d, minZ, r, g, b, da);
                line(buf, matrix, maxX, baseY+d, minZ, maxX, baseY+d, maxZ, r, g, b, da);
                line(buf, matrix, maxX, baseY+d, maxZ, minX, baseY+d, maxZ, r, g, b, da);
                line(buf, matrix, minX, baseY+d, maxZ, minX, baseY+d, minZ, r, g, b, da);
                line(buf, matrix, minX, topY+d,  minZ, maxX, topY+d,  minZ, r, g, b, da);
                line(buf, matrix, maxX, topY+d,  minZ, maxX, topY+d,  maxZ, r, g, b, da);
                line(buf, matrix, maxX, topY+d,  maxZ, minX, topY+d,  maxZ, r, g, b, da);
                line(buf, matrix, minX, topY+d,  maxZ, minX, topY+d,  minZ, r, g, b, da);
            }
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        // ── 3. NÚCLEO BLANCO BRILLANTE en aristas ─────────────────────────
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            float wa = 0.95f * pulse;
            pillar(buf, matrix, minX, minZ, baseY, topY, wr, wg, wb, wa);
            pillar(buf, matrix, maxX, minZ, baseY, topY, wr, wg, wb, wa);
            pillar(buf, matrix, minX, maxZ, baseY, topY, wr, wg, wb, wa);
            pillar(buf, matrix, maxX, maxZ, baseY, topY, wr, wg, wb, wa);
            line(buf, matrix, minX, baseY, minZ, maxX, baseY, minZ, wr, wg, wb, wa * 0.7f);
            line(buf, matrix, maxX, baseY, minZ, maxX, baseY, maxZ, wr, wg, wb, wa * 0.7f);
            line(buf, matrix, maxX, baseY, maxZ, minX, baseY, maxZ, wr, wg, wb, wa * 0.7f);
            line(buf, matrix, minX, baseY, maxZ, minX, baseY, minZ, wr, wg, wb, wa * 0.7f);
            line(buf, matrix, minX, topY,  minZ, maxX, topY,  minZ, wr, wg, wb, wa * 0.7f);
            line(buf, matrix, maxX, topY,  minZ, maxX, topY,  maxZ, wr, wg, wb, wa * 0.7f);
            line(buf, matrix, maxX, topY,  maxZ, minX, topY,  maxZ, wr, wg, wb, wa * 0.7f);
            line(buf, matrix, minX, topY,  maxZ, minX, topY,  minZ, wr, wg, wb, wa * 0.7f);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        // ── 4. ENERGÍA VERTICAL DENSA en las 4 caras ──────────────────────
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            // Dos pasadas: azul glow + blanco-azul encima, más densas
            for (double x = minX; x <= maxX; x += 1.5) {
                float flick = 0.5f + 0.5f * (float) Math.sin(time * 0.004f + (float) x * 2.1f);
                float fa = 0.30f * pulse * flick;
                line(buf, matrix, x, baseY, minZ, x, topY, minZ, r,  g,  b,  fa);
                line(buf, matrix, x, baseY, maxZ, x, topY, maxZ, r,  g,  b,  fa);
                line(buf, matrix, x, baseY, minZ, x, topY, minZ, wr, wg, wb, fa * 0.35f);
                line(buf, matrix, x, baseY, maxZ, x, topY, maxZ, wr, wg, wb, fa * 0.35f);
            }
            for (double z = minZ; z <= maxZ; z += 1.5) {
                float flick = 0.5f + 0.5f * (float) Math.sin(time * 0.004f + (float) z * 1.8f + 1.5f);
                float fa = 0.30f * pulse * flick;
                line(buf, matrix, minX, baseY, z, minX, topY, z, r,  g,  b,  fa);
                line(buf, matrix, maxX, baseY, z, maxX, topY, z, r,  g,  b,  fa);
                line(buf, matrix, minX, baseY, z, minX, topY, z, wr, wg, wb, fa * 0.35f);
                line(buf, matrix, maxX, baseY, z, maxX, topY, z, wr, wg, wb, fa * 0.35f);
            }
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        // ── 5. RAYOS EN ESQUINAS (más gruesos, con 3 capas) ───────────────
        long boltSlot = time / 120;
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            // Cada esquina: rayo glow + rayo principal + núcleo blanco
            bolt(buf, matrix, minX, minZ, baseY, topY, gr, gg, gb, 0.55f, boltSlot*11L+1, 0.5);
            bolt(buf, matrix, minX, minZ, baseY, topY, r,  g,  b,  0.95f, boltSlot*11L+1, 0.3);
            bolt(buf, matrix, minX, minZ, baseY, topY, wr, wg, wb, 0.50f, boltSlot*11L+1, 0.1);

            bolt(buf, matrix, maxX, minZ, baseY, topY, gr, gg, gb, 0.55f, boltSlot*11L+2, 0.5);
            bolt(buf, matrix, maxX, minZ, baseY, topY, r,  g,  b,  0.95f, boltSlot*11L+2, 0.3);
            bolt(buf, matrix, maxX, minZ, baseY, topY, wr, wg, wb, 0.50f, boltSlot*11L+2, 0.1);

            bolt(buf, matrix, minX, maxZ, baseY, topY, gr, gg, gb, 0.55f, boltSlot*11L+3, 0.5);
            bolt(buf, matrix, minX, maxZ, baseY, topY, r,  g,  b,  0.95f, boltSlot*11L+3, 0.3);
            bolt(buf, matrix, minX, maxZ, baseY, topY, wr, wg, wb, 0.50f, boltSlot*11L+3, 0.1);

            bolt(buf, matrix, maxX, maxZ, baseY, topY, gr, gg, gb, 0.55f, boltSlot*11L+4, 0.5);
            bolt(buf, matrix, maxX, maxZ, baseY, topY, r,  g,  b,  0.95f, boltSlot*11L+4, 0.3);
            bolt(buf, matrix, maxX, maxZ, baseY, topY, wr, wg, wb, 0.50f, boltSlot*11L+4, 0.1);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        // ── 6. ANILLO VIAJERO DOBLE ───────────────────────────────────────
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
            double ringY  = baseY + (topY - baseY) * t;
            // Segundo anillo desfasado medio ciclo
            double ring2Y = baseY + (topY - baseY) * ((t + 0.5f) % 1.0f);
            float  ra = 0.95f * pulse;

            for (double ry : new double[]{ ringY, ring2Y }) {
                float alpha = (ry == ringY) ? ra : ra * 0.6f;
                line(buf, matrix, minX, ry, minZ, maxX, ry, minZ, r,  g,  b,  alpha);
                line(buf, matrix, maxX, ry, minZ, maxX, ry, maxZ, r,  g,  b,  alpha);
                line(buf, matrix, maxX, ry, maxZ, minX, ry, maxZ, r,  g,  b,  alpha);
                line(buf, matrix, minX, ry, maxZ, minX, ry, minZ, r,  g,  b,  alpha);
                line(buf, matrix, minX, ry, minZ, maxX, ry, minZ, wr, wg, wb, alpha * 0.5f);
                line(buf, matrix, maxX, ry, minZ, maxX, ry, maxZ, wr, wg, wb, alpha * 0.5f);
                line(buf, matrix, maxX, ry, maxZ, minX, ry, maxZ, wr, wg, wb, alpha * 0.5f);
                line(buf, matrix, minX, ry, maxZ, minX, ry, minZ, wr, wg, wb, alpha * 0.5f);
            }
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        matrices.pop();
    }

    /** Rayo zigzagueante en esquina vertical. spread controla el ancho del zigzag. */
    private static void bolt(BufferBuilder buf, Matrix4f matrix,
                              double cx, double cz, double baseY, double topY,
                              float r, float g, float b, float a, long seed, double spread) {
        RAND.setSeed(seed);
        int    segs = 16;
        double segH = (topY - baseY) / segs;

        double px = cx, py = baseY, pz = cz;
        for (int i = 0; i < segs; i++) {
            double nx = cx + (RAND.nextDouble() * 2 - 1) * spread;
            double ny = py + segH;
            double nz = cz + (RAND.nextDouble() * 2 - 1) * spread;
            if (i == segs - 1) { nx = cx; nz = cz; ny = topY; }
            buf.vertex(matrix, (float) px, (float) py, (float) pz).color(r, g, b, a);
            buf.vertex(matrix, (float) nx, (float) ny, (float) nz).color(r, g, b, a);
            px = nx; py = ny; pz = nz;
        }
    }

    /** Línea vertical (pilar). */
    private static void pillar(BufferBuilder buf, Matrix4f m,
                                double x, double z, double y1, double y2,
                                float r, float g, float b, float a) {
        buf.vertex(m, (float) x, (float) y1, (float) z).color(r, g, b, a);
        buf.vertex(m, (float) x, (float) y2, (float) z).color(r, g, b, a);
    }

    private static void line(BufferBuilder buf, Matrix4f m,
                              double x1, double y1, double z1,
                              double x2, double y2, double z2,
                              float r, float g, float b, float a) {
        buf.vertex(m, (float) x1, (float) y1, (float) z1).color(r, g, b, a);
        buf.vertex(m, (float) x2, (float) y2, (float) z2).color(r, g, b, a);
    }
}

package com.zhilius.secureplots.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotSize;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;

import java.util.Random;

public class PlotBorderRenderer {

    private static final Random RAND = new Random();

    // { r_core, g_core, b_core,  r_glow, g_glow, b_glow,  r_white, g_white, b_white }
    // tier 0=bronze  1=gold  2=emerald  3=diamond  4=netherite
    private static final float[][] TIER_COLORS = {
        { 1.00f, 0.55f, 0.05f,   0.70f, 0.28f, 0.00f,   1.00f, 0.88f, 0.55f }, // bronze
        { 1.00f, 0.85f, 0.00f,   0.80f, 0.50f, 0.00f,   1.00f, 0.97f, 0.65f }, // gold
        { 0.10f, 0.90f, 0.20f,   0.00f, 0.55f, 0.10f,   0.70f, 1.00f, 0.75f }, // emerald
        { 0.15f, 0.95f, 1.00f,   0.00f, 0.50f, 0.80f,   0.75f, 1.00f, 1.00f }, // diamond
        { 0.45f, 0.20f, 0.60f,   0.22f, 0.05f, 0.32f,   0.78f, 0.58f, 0.90f }, // netherite
    };

    public static void render(WorldRenderContext context, SecurePlotsClient.BorderDisplay display) {
        PlotData data = display.data;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        float halfF = display.effectiveRadiusF() / 2f;
        BlockPos center = data.getCenter();

        // Lerped tier for color blending
        float transP = display.transitionProgress();
        int fromTier = display.prevTier < 0 ? data.getSize().tier : display.prevTier;
        int toTier   = data.getSize().tier;
        // Clamp tiers
        fromTier = Math.max(0, Math.min(fromTier, TIER_COLORS.length - 1));
        toTier   = Math.max(0, Math.min(toTier,   TIER_COLORS.length - 1));

        double camX = context.camera().getPos().x;
        double camY = context.camera().getPos().y;
        double camZ = context.camera().getPos().z;

        double minX = (center.getX() + 0.5) - halfF - camX;
        double maxX = (center.getX() + 0.5) + halfF - camX;
        double minZ = (center.getZ() + 0.5) - halfF - camZ;
        double maxZ = (center.getZ() + 0.5) + halfF - camZ;

        double baseY = center.getY() - camY;
        double topY  = baseY + 25;

        int tier = toTier; // keep for reference
        float[] cf = TIER_COLORS[fromTier];
        float[] ct = TIER_COLORS[toTier];
        // Lerp all 9 color channels
        float r  = cf[0] + transP * (ct[0] - cf[0]);
        float g  = cf[1] + transP * (ct[1] - cf[1]);
        float b  = cf[2] + transP * (ct[2] - cf[2]);
        float gr = cf[3] + transP * (ct[3] - cf[3]);
        float gg = cf[4] + transP * (ct[4] - cf[4]);
        float gb = cf[5] + transP * (ct[5] - cf[5]);
        float wr = cf[6] + transP * (ct[6] - cf[6]);
        float wg = cf[7] + transP * (ct[7] - cf[7]);
        float wb = cf[8] + transP * (ct[8] - cf[8]);

        long  time  = System.currentTimeMillis();
        float t     = (time % 2000) / 2000.0f;
        float pulse = 0.6f + 0.4f * (float) Math.sin(t * Math.PI * 2);

        MatrixStack matrices = context.matrixStack();
        matrices.push();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Matrix4f mx = matrices.peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();

        // Grosor de cada "línea" como quad
        float W  = 0.06f;  // grosor core
        float WG = 0.13f;  // grosor glow

        // ── 1. HALO EXTERIOR (quads anchos, color glow) ───────────────────
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            float ga = 0.22f * pulse;
            // Pilares esquinas – quad en X y en Z
            quadPillarX(buf, mx, minX, minZ, baseY, topY, WG, gr, gg, gb, ga);
            quadPillarZ(buf, mx, minX, minZ, baseY, topY, WG, gr, gg, gb, ga);
            quadPillarX(buf, mx, maxX, minZ, baseY, topY, WG, gr, gg, gb, ga);
            quadPillarZ(buf, mx, maxX, minZ, baseY, topY, WG, gr, gg, gb, ga);
            quadPillarX(buf, mx, minX, maxZ, baseY, topY, WG, gr, gg, gb, ga);
            quadPillarZ(buf, mx, minX, maxZ, baseY, topY, WG, gr, gg, gb, ga);
            quadPillarX(buf, mx, maxX, maxZ, baseY, topY, WG, gr, gg, gb, ga);
            quadPillarZ(buf, mx, maxX, maxZ, baseY, topY, WG, gr, gg, gb, ga);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        // ── 2. ARISTAS CORE (quads medianos, color principal) ─────────────
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            float ea = 0.82f * pulse;
            // Pilares
            quadPillarX(buf, mx, minX, minZ, baseY, topY, W, r, g, b, ea);
            quadPillarZ(buf, mx, minX, minZ, baseY, topY, W, r, g, b, ea);
            quadPillarX(buf, mx, maxX, minZ, baseY, topY, W, r, g, b, ea);
            quadPillarZ(buf, mx, maxX, minZ, baseY, topY, W, r, g, b, ea);
            quadPillarX(buf, mx, minX, maxZ, baseY, topY, W, r, g, b, ea);
            quadPillarZ(buf, mx, minX, maxZ, baseY, topY, W, r, g, b, ea);
            quadPillarX(buf, mx, maxX, maxZ, baseY, topY, W, r, g, b, ea);
            quadPillarZ(buf, mx, maxX, maxZ, baseY, topY, W, r, g, b, ea);
            // Aros horizontal base y top
            quadBeamZ(buf, mx, minX, maxX, baseY, minZ, W, r, g, b, ea);
            quadBeamZ(buf, mx, minX, maxX, baseY, maxZ, W, r, g, b, ea);
            quadBeamX(buf, mx, minX, baseY, minZ, maxZ, W, r, g, b, ea);
            quadBeamX(buf, mx, maxX, baseY, minZ, maxZ, W, r, g, b, ea);
            quadBeamZ(buf, mx, minX, maxX, topY,  minZ, W, r, g, b, ea);
            quadBeamZ(buf, mx, minX, maxX, topY,  maxZ, W, r, g, b, ea);
            quadBeamX(buf, mx, minX, topY,  minZ, maxZ, W, r, g, b, ea);
            quadBeamX(buf, mx, maxX, topY,  minZ, maxZ, W, r, g, b, ea);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        // ── 3. NÚCLEO BLANCO (quads finos encima) ─────────────────────────
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            float wa = 0.95f * pulse;
            float WW = W * 0.4f;
            quadPillarX(buf, mx, minX, minZ, baseY, topY, WW, wr, wg, wb, wa);
            quadPillarZ(buf, mx, minX, minZ, baseY, topY, WW, wr, wg, wb, wa);
            quadPillarX(buf, mx, maxX, minZ, baseY, topY, WW, wr, wg, wb, wa);
            quadPillarZ(buf, mx, maxX, minZ, baseY, topY, WW, wr, wg, wb, wa);
            quadPillarX(buf, mx, minX, maxZ, baseY, topY, WW, wr, wg, wb, wa);
            quadPillarZ(buf, mx, minX, maxZ, baseY, topY, WW, wr, wg, wb, wa);
            quadPillarX(buf, mx, maxX, maxZ, baseY, topY, WW, wr, wg, wb, wa);
            quadPillarZ(buf, mx, maxX, maxZ, baseY, topY, WW, wr, wg, wb, wa);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        // ── 4. SCANLINES VERTICALES en las 4 caras ────────────────────────
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            float SW = 0.025f;
            for (double x = minX + 1.5; x < maxX; x += 1.5) {
                float flick = 0.45f + 0.55f * (float) Math.sin(time * 0.004f + (float) x * 2.1f);
                float fa = 0.28f * pulse * flick;
                quadPillarX(buf, mx, x, minZ, baseY, topY, SW, r, g, b, fa);
                quadPillarX(buf, mx, x, maxZ, baseY, topY, SW, r, g, b, fa);
            }
            for (double z = minZ + 1.5; z < maxZ; z += 1.5) {
                float flick = 0.45f + 0.55f * (float) Math.sin(time * 0.004f + (float) z * 1.8f + 1.5f);
                float fa = 0.28f * pulse * flick;
                quadPillarZ(buf, mx, minX, z, baseY, topY, SW, r, g, b, fa);
                quadPillarZ(buf, mx, maxX, z, baseY, topY, SW, r, g, b, fa);
            }
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        // ── 5. RAYOS EN ESQUINAS ──────────────────────────────────────────
        long boltSlot = time / 120;
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (int i = 0; i < 4; i++) {
                double cx = (i == 0 || i == 2) ? minX : maxX;
                double cz = (i == 0 || i == 1) ? minZ : maxZ;
                boltQuad(buf, mx, cx, cz, baseY, topY, gr, gg, gb, 0.50f, boltSlot*11L+(i*3+1), 0.48, 0.05f);
                boltQuad(buf, mx, cx, cz, baseY, topY, r,  g,  b,  0.90f, boltSlot*11L+(i*3+2), 0.26, 0.035f);
                boltQuad(buf, mx, cx, cz, baseY, topY, wr, wg, wb, 0.60f, boltSlot*11L+(i*3+3), 0.10, 0.015f);
            }
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        // ── 6. ANILLO VIAJERO DOBLE ───────────────────────────────────────
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            double ringY  = baseY + (topY - baseY) * t;
            double ring2Y = baseY + (topY - baseY) * ((t + 0.5f) % 1.0f);
            float  RW = W * 0.7f;
            for (int i = 0; i < 2; i++) {
                double ry    = (i == 0) ? ringY : ring2Y;
                float  alpha = (i == 0) ? 0.95f * pulse : 0.55f * pulse;
                quadBeamZ(buf, mx, minX, maxX, ry, minZ, RW, r, g, b, alpha);
                quadBeamZ(buf, mx, minX, maxX, ry, maxZ, RW, r, g, b, alpha);
                quadBeamX(buf, mx, minX, ry, minZ, maxZ, RW, r, g, b, alpha);
                quadBeamX(buf, mx, maxX, ry, minZ, maxZ, RW, r, g, b, alpha);
                // núcleo blanco del anillo
                quadBeamZ(buf, mx, minX, maxX, ry, minZ, RW*0.4f, wr, wg, wb, alpha * 0.6f);
                quadBeamZ(buf, mx, minX, maxX, ry, maxZ, RW*0.4f, wr, wg, wb, alpha * 0.6f);
                quadBeamX(buf, mx, minX, ry, minZ, maxZ, RW*0.4f, wr, wg, wb, alpha * 0.6f);
                quadBeamX(buf, mx, maxX, ry, minZ, maxZ, RW*0.4f, wr, wg, wb, alpha * 0.6f);
            }
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        // ── 7. ANILLO DE EXPANSIÓN (solo durante upgrade) ─────────────────
        float expandExtra = display.expandPulseRadius();
        if (expandExtra > 0.1f) {
            float[] ct2 = TIER_COLORS[toTier];
            float ep = expandExtra / 8f; // 0..1 normalized
            float pulseAlpha = ep * (1.0f - ep) * 2.8f; // peaks at midpoint
            float eMinX = (float)((center.getX() + 0.5) - halfF + expandExtra - camX);
            float eMaxX = (float)((center.getX() + 0.5) + halfF - expandExtra + camX * 0 - camX);
            // Recalc: the pulse ring is at halfF position (new border) expanding outward
            double pMinX = (center.getX() + 0.5) - halfF - camX;
            double pMaxX = (center.getX() + 0.5) + halfF - camX;
            double pMinZ = (center.getZ() + 0.5) - halfF - camZ;
            double pMaxZ = (center.getZ() + 0.5) + halfF - camZ;
            float PW = 0.18f + expandExtra * 0.04f;
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            // Full glowing ring at current border position
            quadBeamZ(buf, mx, pMinX, pMaxX, baseY + 2,  pMinZ, PW, ct2[0], ct2[1], ct2[2], pulseAlpha);
            quadBeamZ(buf, mx, pMinX, pMaxX, baseY + 2,  pMaxZ, PW, ct2[0], ct2[1], ct2[2], pulseAlpha);
            quadBeamX(buf, mx, pMinX, baseY + 2, pMinZ, pMaxZ, PW, ct2[0], ct2[1], ct2[2], pulseAlpha);
            quadBeamX(buf, mx, pMaxX, baseY + 2, pMinZ, pMaxZ, PW, ct2[0], ct2[1], ct2[2], pulseAlpha);
            quadBeamZ(buf, mx, pMinX, pMaxX, topY - 2,  pMinZ, PW, ct2[6], ct2[7], ct2[8], pulseAlpha * 0.7f);
            quadBeamZ(buf, mx, pMinX, pMaxX, topY - 2,  pMaxZ, PW, ct2[6], ct2[7], ct2[8], pulseAlpha * 0.7f);
            quadBeamX(buf, mx, pMinX, topY - 2, pMinZ, pMaxZ, PW, ct2[6], ct2[7], ct2[8], pulseAlpha * 0.7f);
            quadBeamX(buf, mx, pMaxX, topY - 2, pMinZ, pMaxZ, PW, ct2[6], ct2[7], ct2[8], pulseAlpha * 0.7f);
            // Vertical pillars at corners flashing
            quadPillarX(buf, mx, pMinX, pMinZ, baseY, topY, PW, ct2[0], ct2[1], ct2[2], pulseAlpha);
            quadPillarZ(buf, mx, pMinX, pMinZ, baseY, topY, PW, ct2[0], ct2[1], ct2[2], pulseAlpha);
            quadPillarX(buf, mx, pMaxX, pMinZ, baseY, topY, PW, ct2[0], ct2[1], ct2[2], pulseAlpha);
            quadPillarZ(buf, mx, pMaxX, pMinZ, baseY, topY, PW, ct2[0], ct2[1], ct2[2], pulseAlpha);
            quadPillarX(buf, mx, pMinX, pMaxZ, baseY, topY, PW, ct2[0], ct2[1], ct2[2], pulseAlpha);
            quadPillarZ(buf, mx, pMinX, pMaxZ, baseY, topY, PW, ct2[0], ct2[1], ct2[2], pulseAlpha);
            quadPillarX(buf, mx, pMaxX, pMaxZ, baseY, topY, PW, ct2[0], ct2[1], ct2[2], pulseAlpha);
            quadPillarZ(buf, mx, pMaxX, pMaxZ, baseY, topY, PW, ct2[0], ct2[1], ct2[2], pulseAlpha);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        matrices.pop();
    }

    // Pilar vertical en X (cara hacia Z)
    private static void quadPillarX(BufferBuilder buf, Matrix4f m,
                                     double x, double z, double y1, double y2,
                                     float w, float r, float g, float b, float a) {
        buf.vertex(m, (float)(x-w), (float)y1, (float)z).color(r,g,b,a);
        buf.vertex(m, (float)(x+w), (float)y1, (float)z).color(r,g,b,a);
        buf.vertex(m, (float)(x+w), (float)y2, (float)z).color(r,g,b,a);
        buf.vertex(m, (float)(x-w), (float)y2, (float)z).color(r,g,b,a);
    }

    // Pilar vertical en Z (cara hacia X)
    private static void quadPillarZ(BufferBuilder buf, Matrix4f m,
                                     double x, double z, double y1, double y2,
                                     float w, float r, float g, float b, float a) {
        buf.vertex(m, (float)x, (float)y1, (float)(z-w)).color(r,g,b,a);
        buf.vertex(m, (float)x, (float)y1, (float)(z+w)).color(r,g,b,a);
        buf.vertex(m, (float)x, (float)y2, (float)(z+w)).color(r,g,b,a);
        buf.vertex(m, (float)x, (float)y2, (float)(z-w)).color(r,g,b,a);
    }

    // Viga horizontal a lo largo de Z (aro superior/inferior)
    private static void quadBeamZ(BufferBuilder buf, Matrix4f m,
                                   double x1, double x2, double y, double z,
                                   float w, float r, float g, float b, float a) {
        buf.vertex(m, (float)x1, (float)(y-w), (float)z).color(r,g,b,a);
        buf.vertex(m, (float)x2, (float)(y-w), (float)z).color(r,g,b,a);
        buf.vertex(m, (float)x2, (float)(y+w), (float)z).color(r,g,b,a);
        buf.vertex(m, (float)x1, (float)(y+w), (float)z).color(r,g,b,a);
    }

    // Viga horizontal a lo largo de X
    private static void quadBeamX(BufferBuilder buf, Matrix4f m,
                                   double x, double y, double z1, double z2,
                                   float w, float r, float g, float b, float a) {
        buf.vertex(m, (float)x, (float)(y-w), (float)z1).color(r,g,b,a);
        buf.vertex(m, (float)x, (float)(y-w), (float)z2).color(r,g,b,a);
        buf.vertex(m, (float)x, (float)(y+w), (float)z2).color(r,g,b,a);
        buf.vertex(m, (float)x, (float)(y+w), (float)z1).color(r,g,b,a);
    }

    // Rayo zigzagueante como serie de quads
    private static void boltQuad(BufferBuilder buf, Matrix4f m,
                                  double cx, double cz, double baseY, double topY,
                                  float r, float g, float b, float a,
                                  long seed, double spread, float w) {
        RAND.setSeed(seed);
        int    segs = 14;
        double segH = (topY - baseY) / segs;
        double px = cx, py = baseY, pz = cz;
        for (int i = 0; i < segs; i++) {
            double nx = cx + (RAND.nextDouble() * 2 - 1) * spread;
            double ny = py + segH;
            double nz = cz + (RAND.nextDouble() * 2 - 1) * spread;
            if (i == segs - 1) { nx = cx; nz = cz; ny = topY; }
            // quad perpendicular al segmento
            buf.vertex(m, (float)(px-w), (float)py, (float)pz).color(r,g,b,a);
            buf.vertex(m, (float)(px+w), (float)py, (float)pz).color(r,g,b,a);
            buf.vertex(m, (float)(nx+w), (float)ny, (float)nz).color(r,g,b,a);
            buf.vertex(m, (float)(nx-w), (float)ny, (float)nz).color(r,g,b,a);
            px = nx; py = ny; pz = nz;
        }
    }
}

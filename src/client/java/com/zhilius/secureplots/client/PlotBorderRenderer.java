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
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;

public class PlotBorderRenderer {

    /** Fast deterministic pseudo-random in [-1, 1] — no Random instance needed. */
    private static double fastRand(long seed, int index) {
        long n = seed + index * 12345L;
        n = (n << 13) ^ n;
        n = n * (n * n * 15731L + 789221L) + 1376312589L;
        return (double)(n & 0x7fffffffL) / 1073741824.0 - 1.0;
    }

    public static void render(WorldRenderContext context, SecurePlotsClient.BorderDisplay display) {
        PlotData data = display.data;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        // Use live config from server
        BorderConfig cfg = PlotBorderRendererConfig.current;

        float halfF = display.effectiveRadiusF() / 2f;
        BlockPos center = data.getCenter();

        // Lerped tier for color blending
        float transP = display.transitionProgress();
        int fromTier = display.prevTier < 0 ? data.getSize().tier : display.prevTier;
        int toTier   = data.getSize().tier;
        int maxTier  = cfg.tierColors.size() - 1;
        fromTier = Math.max(0, Math.min(fromTier, maxTier));
        toTier   = Math.max(0, Math.min(toTier,   maxTier));

        double camX = context.camera().getPos().x;
        double camY = context.camera().getPos().y;
        double camZ = context.camera().getPos().z;

        double minX = (center.getX() + 0.5) - halfF - camX;
        double maxX = (center.getX() + 0.5) + halfF - camX;
        double minZ = (center.getZ() + 0.5) - halfF - camZ;
        double maxZ = (center.getZ() + 0.5) + halfF - camZ;

        double baseY = center.getY() - camY;
        double topY  = baseY + cfg.borderHeight;

        float[] cf = cfg.getTierColors(fromTier);
        float[] ct = cfg.getTierColors(toTier);

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
        float t     = (time % cfg.pulseCycleMs) / (float) cfg.pulseCycleMs;
        float pulse = cfg.pulseMin + cfg.pulseRange * (float) Math.sin(t * Math.PI * 2);

        // Pre-calculate alpha and width values for this frame
        float W  = cfg.edgeThickness;
        float WG = cfg.glowThickness;
        float SW = cfg.scanlineThickness;
        float ga = 0.22f * pulse;
        float ea = 0.82f * pulse;
        float wa = 0.95f * pulse;
        float WW = W * 0.4f;
        float RW = W * 0.7f;

        MatrixStack matrices = context.matrixStack();
        matrices.push();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Matrix4f mx = matrices.peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();

        // ── PASS 1: GLOW HALO + CORE EDGES + WHITE CORE + SCANLINES + TRAVELING RINGS ──
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);

            // 1. Outer glow halo
            for (int i = 0; i < 4; i++) {
                double cx = (i == 0 || i == 2) ? minX : maxX;
                double cz = (i == 0 || i == 1) ? minZ : maxZ;
                quadPillarX(buf, mx, cx, cz, baseY, topY, WG, gr, gg, gb, ga);
                quadPillarZ(buf, mx, cx, cz, baseY, topY, WG, gr, gg, gb, ga);
            }

            // 2. Core edges + white core overlay
            for (int i = 0; i < 4; i++) {
                double cx = (i == 0 || i == 2) ? minX : maxX;
                double cz = (i == 0 || i == 1) ? minZ : maxZ;
                quadPillarX(buf, mx, cx, cz, baseY, topY, W,  r,  g,  b,  ea);
                quadPillarZ(buf, mx, cx, cz, baseY, topY, W,  r,  g,  b,  ea);
                quadPillarX(buf, mx, cx, cz, baseY, topY, WW, wr, wg, wb, wa);
                quadPillarZ(buf, mx, cx, cz, baseY, topY, WW, wr, wg, wb, wa);
            }

            // 3. Horizontal rings at bottom and top
            quadBeamZ(buf, mx, minX, maxX, baseY, minZ, W, r, g, b, ea);
            quadBeamZ(buf, mx, minX, maxX, baseY, maxZ, W, r, g, b, ea);
            quadBeamX(buf, mx, minX, baseY, minZ, maxZ, W, r, g, b, ea);
            quadBeamX(buf, mx, maxX, baseY, minZ, maxZ, W, r, g, b, ea);
            quadBeamZ(buf, mx, minX, maxX, topY,  minZ, W, r, g, b, ea);
            quadBeamZ(buf, mx, minX, maxX, topY,  maxZ, W, r, g, b, ea);
            quadBeamX(buf, mx, minX, topY,  minZ, maxZ, W, r, g, b, ea);
            quadBeamX(buf, mx, maxX, topY,  minZ, maxZ, W, r, g, b, ea);

            // 4. Vertical scanlines
            float sp = cfg.scanlineSpacing;
            for (double x = minX + sp; x < maxX; x += sp) {
                float flick = 0.45f + 0.55f * (float) Math.sin(time * 0.004f + (float) x * 2.1f);
                float fa = 0.28f * pulse * flick;
                quadPillarX(buf, mx, x, minZ, baseY, topY, SW, r, g, b, fa);
                quadPillarX(buf, mx, x, maxZ, baseY, topY, SW, r, g, b, fa);
            }
            for (double z = minZ + sp; z < maxZ; z += sp) {
                float flick = 0.45f + 0.55f * (float) Math.sin(time * 0.004f + (float) z * 1.8f + 1.5f);
                float fa = 0.28f * pulse * flick;
                quadPillarZ(buf, mx, minX, z, baseY, topY, SW, r, g, b, fa);
                quadPillarZ(buf, mx, maxX, z, baseY, topY, SW, r, g, b, fa);
            }

            // 5. Double traveling rings
            double ringY  = baseY + (topY - baseY) * t;
            double ring2Y = baseY + (topY - baseY) * ((t + 0.5f) % 1.0f);
            for (int i = 0; i < 2; i++) {
                double ry    = (i == 0) ? ringY : ring2Y;
                float  alpha = (i == 0) ? 0.95f * pulse : 0.55f * pulse;
                quadBeamZ(buf, mx, minX, maxX, ry, minZ, RW,        r,  g,  b,  alpha);
                quadBeamZ(buf, mx, minX, maxX, ry, maxZ, RW,        r,  g,  b,  alpha);
                quadBeamX(buf, mx, minX, ry, minZ, maxZ, RW,        r,  g,  b,  alpha);
                quadBeamX(buf, mx, maxX, ry, minZ, maxZ, RW,        r,  g,  b,  alpha);
                quadBeamZ(buf, mx, minX, maxX, ry, minZ, RW * 0.4f, wr, wg, wb, alpha * 0.6f);
                quadBeamZ(buf, mx, minX, maxX, ry, maxZ, RW * 0.4f, wr, wg, wb, alpha * 0.6f);
                quadBeamX(buf, mx, minX, ry, minZ, maxZ, RW * 0.4f, wr, wg, wb, alpha * 0.6f);
                quadBeamX(buf, mx, maxX, ry, minZ, maxZ, RW * 0.4f, wr, wg, wb, alpha * 0.6f);
            }

            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        // ── PASS 2: CORNER LIGHTNING BOLTS ──
        {
            long boltSlot = time / cfg.boltFlickerMs;
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            for (int i = 0; i < 4; i++) {
                double cx = (i == 0 || i == 2) ? minX : maxX;
                double cz = (i == 0 || i == 1) ? minZ : maxZ;
                boltQuad(buf, mx, cx, cz, baseY, topY, gr, gg, gb, 0.50f, boltSlot * 11L + (i * 3 + 1), 0.48, 0.05f);
                boltQuad(buf, mx, cx, cz, baseY, topY, r,  g,  b,  0.90f, boltSlot * 11L + (i * 3 + 2), 0.26, 0.035f);
                boltQuad(buf, mx, cx, cz, baseY, topY, wr, wg, wb, 0.60f, boltSlot * 11L + (i * 3 + 3), 0.10, 0.015f);
            }
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        // ── PASS 3: EXPANSION RING (only during upgrade) ──
        float expandExtra = display.expandPulseRadius();
        if (expandExtra > 0.1f) {
            float[] ct2 = cfg.getTierColors(toTier);
            float ep = expandExtra / 8f;
            float pulseAlpha = ep * (1.0f - ep) * 2.8f;
            double pMinX = (center.getX() + 0.5) - halfF - camX;
            double pMaxX = (center.getX() + 0.5) + halfF - camX;
            double pMinZ = (center.getZ() + 0.5) - halfF - camZ;
            double pMaxZ = (center.getZ() + 0.5) + halfF - camZ;
            float PW   = 0.18f + expandExtra * 0.04f;
            float pa70 = pulseAlpha * 0.7f;

            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            quadBeamZ(buf, mx, pMinX, pMaxX, baseY + 2, pMinZ, PW, ct2[0], ct2[1], ct2[2], pulseAlpha);
            quadBeamZ(buf, mx, pMinX, pMaxX, baseY + 2, pMaxZ, PW, ct2[0], ct2[1], ct2[2], pulseAlpha);
            quadBeamX(buf, mx, pMinX, baseY + 2, pMinZ, pMaxZ, PW, ct2[0], ct2[1], ct2[2], pulseAlpha);
            quadBeamX(buf, mx, pMaxX, baseY + 2, pMinZ, pMaxZ, PW, ct2[0], ct2[1], ct2[2], pulseAlpha);
            quadBeamZ(buf, mx, pMinX, pMaxX, topY - 2, pMinZ, PW, ct2[6], ct2[7], ct2[8], pa70);
            quadBeamZ(buf, mx, pMinX, pMaxX, topY - 2, pMaxZ, PW, ct2[6], ct2[7], ct2[8], pa70);
            quadBeamX(buf, mx, pMinX, topY - 2, pMinZ, pMaxZ, PW, ct2[6], ct2[7], ct2[8], pa70);
            quadBeamX(buf, mx, pMaxX, topY - 2, pMinZ, pMaxZ, PW, ct2[6], ct2[7], ct2[8], pa70);
            for (int i = 0; i < 4; i++) {
                double cx = (i == 0 || i == 2) ? pMinX : pMaxX;
                double cz = (i == 0 || i == 1) ? pMinZ : pMaxZ;
                quadPillarX(buf, mx, cx, cz, baseY, topY, PW, ct2[0], ct2[1], ct2[2], pulseAlpha);
                quadPillarZ(buf, mx, cx, cz, baseY, topY, PW, ct2[0], ct2[1], ct2[2], pulseAlpha);
            }
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        matrices.pop();
    }

    /** Vertical quad pillar facing Z axis */
    private static void quadPillarX(BufferBuilder buf, Matrix4f m,
                                     double x, double z, double y1, double y2,
                                     float w, float r, float g, float b, float a) {
        buf.vertex(m, (float)(x-w), (float)y1, (float)z).color(r,g,b,a);
        buf.vertex(m, (float)(x+w), (float)y1, (float)z).color(r,g,b,a);
        buf.vertex(m, (float)(x+w), (float)y2, (float)z).color(r,g,b,a);
        buf.vertex(m, (float)(x-w), (float)y2, (float)z).color(r,g,b,a);
    }

    /** Vertical quad pillar facing X axis */
    private static void quadPillarZ(BufferBuilder buf, Matrix4f m,
                                     double x, double z, double y1, double y2,
                                     float w, float r, float g, float b, float a) {
        buf.vertex(m, (float)x, (float)y1, (float)(z-w)).color(r,g,b,a);
        buf.vertex(m, (float)x, (float)y1, (float)(z+w)).color(r,g,b,a);
        buf.vertex(m, (float)x, (float)y2, (float)(z+w)).color(r,g,b,a);
        buf.vertex(m, (float)x, (float)y2, (float)(z-w)).color(r,g,b,a);
    }

    /** Horizontal beam along Z axis */
    private static void quadBeamZ(BufferBuilder buf, Matrix4f m,
                                   double x1, double x2, double y, double z,
                                   float w, float r, float g, float b, float a) {
        buf.vertex(m, (float)x1, (float)(y-w), (float)z).color(r,g,b,a);
        buf.vertex(m, (float)x2, (float)(y-w), (float)z).color(r,g,b,a);
        buf.vertex(m, (float)x2, (float)(y+w), (float)z).color(r,g,b,a);
        buf.vertex(m, (float)x1, (float)(y+w), (float)z).color(r,g,b,a);
    }

    /** Horizontal beam along X axis */
    private static void quadBeamX(BufferBuilder buf, Matrix4f m,
                                   double x, double y, double z1, double z2,
                                   float w, float r, float g, float b, float a) {
        buf.vertex(m, (float)x, (float)(y-w), (float)z1).color(r,g,b,a);
        buf.vertex(m, (float)x, (float)(y-w), (float)z2).color(r,g,b,a);
        buf.vertex(m, (float)x, (float)(y+w), (float)z2).color(r,g,b,a);
        buf.vertex(m, (float)x, (float)(y+w), (float)z1).color(r,g,b,a);
    }

    /** Zigzag lightning bolt — 8 segments, deterministic random. */
    private static void boltQuad(BufferBuilder buf, Matrix4f m,
                                  double cx, double cz, double baseY, double topY,
                                  float r, float g, float b, float a,
                                  long seed, double spread, float w) {
        int    segs = 8;
        double segH = (topY - baseY) / segs;
        double px = cx, py = baseY, pz = cz;
        for (int i = 0; i < segs; i++) {
            double nx = cx + fastRand(seed, i * 2)     * spread;
            double ny = py + segH;
            double nz = cz + fastRand(seed, i * 2 + 1) * spread;
            if (i == segs - 1) { nx = cx; nz = cz; ny = topY; }
            buf.vertex(m, (float)(px-w), (float)py, (float)pz).color(r,g,b,a);
            buf.vertex(m, (float)(px+w), (float)py, (float)pz).color(r,g,b,a);
            buf.vertex(m, (float)(nx+w), (float)ny, (float)nz).color(r,g,b,a);
            buf.vertex(m, (float)(nx-w), (float)ny, (float)nz).color(r,g,b,a);
            px = nx; py = ny; pz = nz;
        }
    }
}

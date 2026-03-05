package com.zhilius.secureplots.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zhilius.secureplots.plot.PlotData;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;

public class PlotBorderRenderer {

        public static void render(WorldRenderContext context, PlotData data) {
                MinecraftClient client = MinecraftClient.getInstance();
                if (client.player == null)
                        return;

                BlockPos center = data.getCenter();
                int half = data.getSize().radius / 2;

                double camX = context.camera().getPos().x;
                double camY = context.camera().getPos().y;
                double camZ = context.camera().getPos().z;

                double minX = center.getX() - half - camX;
                double maxX = center.getX() + half + 1 - camX;
                double minZ = center.getZ() - half - camZ;
                double maxZ = center.getZ() + half + 1 - camZ;

                double baseY = center.getY() - camY;
                double topY = baseY + 25;

                MatrixStack matrices = context.matrixStack();
                matrices.push();

                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableCull();
                RenderSystem.disableDepthTest();

                PlotData.Role role = data.getRoleOf(client.player.getUuid());
                float r, g, b;
                switch (role) {
                        case OWNER -> {
                                r = 0.2f;
                                g = 0.8f;
                                b = 1.0f;
                        }
                        case ADMIN -> {
                                r = 1.0f;
                                g = 0.5f;
                                b = 0.0f;
                        }
                        case MEMBER -> {
                                r = 0.2f;
                                g = 1.0f;
                                b = 0.4f;
                        }
                        default -> {
                                r = 1.0f;
                                g = 0.2f;
                                b = 0.2f;
                        }
                }

                float alpha = 0.6f;
                Matrix4f matrix = matrices.peek().getPositionMatrix();

                Tessellator tessellator = Tessellator.getInstance();
                BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.DEBUG_LINES,
                                VertexFormats.POSITION_COLOR);

                drawLine(buffer, matrix, (float) minX, (float) baseY, (float) minZ, (float) minX, (float) topY,
                                (float) minZ, r, g, b, alpha);
                drawLine(buffer, matrix, (float) maxX, (float) baseY, (float) minZ, (float) maxX, (float) topY,
                                (float) minZ, r, g, b, alpha);
                drawLine(buffer, matrix, (float) minX, (float) baseY, (float) maxZ, (float) minX, (float) topY,
                                (float) maxZ, r, g, b, alpha);
                drawLine(buffer, matrix, (float) maxX, (float) baseY, (float) maxZ, (float) maxX, (float) topY,
                                (float) maxZ, r, g, b, alpha);

                drawLine(buffer, matrix, (float) minX, (float) baseY, (float) minZ, (float) maxX, (float) baseY,
                                (float) minZ, r, g, b, alpha);
                drawLine(buffer, matrix, (float) maxX, (float) baseY, (float) minZ, (float) maxX, (float) baseY,
                                (float) maxZ, r, g, b, alpha);
                drawLine(buffer, matrix, (float) maxX, (float) baseY, (float) maxZ, (float) minX, (float) baseY,
                                (float) maxZ, r, g, b, alpha);
                drawLine(buffer, matrix, (float) minX, (float) baseY, (float) maxZ, (float) minX, (float) baseY,
                                (float) minZ, r, g, b, alpha);

                drawLine(buffer, matrix, (float) minX, (float) topY, (float) minZ, (float) maxX, (float) topY,
                                (float) minZ, r, g, b, alpha);
                drawLine(buffer, matrix, (float) maxX, (float) topY, (float) minZ, (float) maxX, (float) topY,
                                (float) maxZ, r, g, b, alpha);
                drawLine(buffer, matrix, (float) maxX, (float) topY, (float) maxZ, (float) minX, (float) topY,
                                (float) maxZ, r, g, b, alpha);
                drawLine(buffer, matrix, (float) minX, (float) topY, (float) maxZ, (float) minX, (float) topY,
                                (float) minZ, r, g, b, alpha);

                BufferRenderer.drawWithGlobalProgram(buffer.end());

                RenderSystem.enableDepthTest();
                RenderSystem.enableCull();
                RenderSystem.disableBlend();

                matrices.pop();
        }

        private static void drawLine(BufferBuilder buf, Matrix4f matrix,
                        float x1, float y1, float z1,
                        float x2, float y2, float z2,
                        float r, float g, float b, float a) {
                buf.vertex(matrix, x1, y1, z1).color(r, g, b, a);
                buf.vertex(matrix, x2, y2, z2).color(r, g, b, a);
        }
}
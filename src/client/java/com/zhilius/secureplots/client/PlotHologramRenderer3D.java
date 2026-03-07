package com.zhilius.secureplots.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotSize;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PlotHologramRenderer3D {

    private static final float TEXT_SCALE = 0.025f;
    private static final int   PAD_X      = 10;
    private static final int   PAD_Y      = 8;

    public record HologramEntry(BlockPos pos, PlotData data, long expiresAt) {}
    private static final List<HologramEntry> active = new ArrayList<>();

    public static void add(BlockPos pos, PlotData data, int durationMs) {
        active.removeIf(e -> e.pos().equals(pos));
        active.add(new HologramEntry(pos, data, System.currentTimeMillis() + durationMs));
    }

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(PlotHologramRenderer3D::render);
    }

    private static void render(WorldRenderContext ctx) {
        if (active.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        long now = System.currentTimeMillis();
        Iterator<HologramEntry> iter = active.iterator();
        while (iter.hasNext()) {
            HologramEntry entry = iter.next();
            if (now > entry.expiresAt()) { iter.remove(); continue; }
            renderOne(ctx, client, entry);
        }
    }

    private static void renderOne(WorldRenderContext ctx, MinecraftClient client, HologramEntry entry) {
        Vec3d cam    = ctx.camera().getPos();
        BlockPos pos = entry.pos();

        double wx = pos.getX() + 0.5 - cam.x;
        double wy = pos.getY() + 2.2 - cam.y;
        double wz = pos.getZ() + 0.5 - cam.z;

        if (wx*wx + wy*wy + wz*wz > 24*24) return;

        TextRenderer font  = client.textRenderer;
        List<Text>   lines = buildLines(entry.data());

        int lineH    = font.fontHeight + 2;
        int maxW     = 0;
        for (Text t : lines) maxW = Math.max(maxW, font.getWidth(t));
        int totalH   = lines.size() * lineH;
        int panelPxW = maxW   + PAD_X * 2;
        int panelPxH = totalH + PAD_Y * 2;

        // Tamaño del panel en unidades de bloque
        float hw = panelPxW * TEXT_SCALE / 2f;  // mitad del ancho
        float hh = panelPxH * TEXT_SCALE / 2f;  // mitad del alto

        // Centro del panel en coords relativas a la cámara
        float cx = (float) wx;
        float cy = (float) wy;
        float cz = (float) wz;

        // El panel es un plano VERTICAL que mira hacia el NORTE (cara sur, normal +Z).
        // Vértices definidos en ejes del MUNDO:
        //   X = izquierda/derecha del mundo
        //   Y = arriba/abajo
        //   Z = fijo (la cara del panel)
        // Esto es exactamente como PlotBorderRenderer define sus quads — sin rotación.
        float x0 = cx - hw;
        float x1 = cx + hw;
        float y0 = cy - hh;
        float y1 = cy + hh;
        float z  = cz;   // el panel está en este plano Z del mundo

        MatrixStack matrices = ctx.matrixStack();
        Matrix4f mx = matrices.peek().getPositionMatrix();

        int light   = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        int overlay = OverlayTexture.DEFAULT_UV;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        // 1. Fondo negro translúcido usando shader de posición+color
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Tessellator tess = Tessellator.getInstance();
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            buf.vertex(mx, x0, y0, z).color(0, 0, 15, 160);
            buf.vertex(mx, x1, y0, z).color(0, 0, 15, 160);
            buf.vertex(mx, x1, y1, z).color(0, 0, 15, 160);
            buf.vertex(mx, x0, y1, z).color(0, 0, 15, 160);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        // 2. Bordes superior e inferior
        float[] bc = tierColor(entry.data().getSize().tier);
        float pulse = 0.7f + 0.3f * (float) Math.sin(System.currentTimeMillis() * 0.003);
        float bw = TEXT_SCALE * 1.2f;
        {
            BufferBuilder buf = tess.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
            float ba = pulse * 0.9f;
            buf.vertex(mx, x0+bw, y0+bw,    z).color(bc[0], bc[1], bc[2], ba);
            buf.vertex(mx, x1-bw, y0+bw,    z).color(bc[0], bc[1], bc[2], ba);
            buf.vertex(mx, x1-bw, y0+bw*2f, z).color(bc[0], bc[1], bc[2], ba);
            buf.vertex(mx, x0+bw, y0+bw*2f, z).color(bc[0], bc[1], bc[2], ba);

            buf.vertex(mx, x0+bw, y1-bw*2f, z).color(bc[0], bc[1], bc[2], ba);
            buf.vertex(mx, x1-bw, y1-bw*2f, z).color(bc[0], bc[1], bc[2], ba);
            buf.vertex(mx, x1-bw, y1-bw,    z).color(bc[0], bc[1], bc[2], ba);
            buf.vertex(mx, x0+bw, y1-bw,    z).color(bc[0], bc[1], bc[2], ba);
            BufferRenderer.drawWithGlobalProgram(buf.end());
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        // 3. Texto — usando la misma matrix del mundo, escalada al espacio del font
        // Construimos una matrix que: toma la posición del panel en el mundo,
        // y escala el espacio de píxeles del font a unidades de bloque.
        Matrix4f textMat = new Matrix4f(mx)
                .translate(cx, cy, z + 0.002f)
                .scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

        VertexConsumerProvider.Immediate immediate =
                client.getBufferBuilders().getEntityVertexConsumers();

        float originX = -panelPxW / 2f + PAD_X;
        float originY = -panelPxH / 2f + PAD_Y;

        for (int i = 0; i < lines.size(); i++) {
            Text  line = lines.get(i);
            float lx   = originX + (maxW - font.getWidth(line)) / 2f;
            float ly   = originY + i * lineH;
            font.draw(line, lx, ly, 0xFFFFFFFF, false,
                    textMat,
                    immediate,
                    TextRenderer.TextLayerType.NORMAL,
                    0x00000000, light);
        }

        immediate.draw();
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

    private static List<Text> buildLines(PlotData data) {
        List<Text> lines = new ArrayList<>();
        String name    = (data.getPlotName() != null && !data.getPlotName().isBlank())
                         ? data.getPlotName().toUpperCase() : "PARCELA PROTEGIDA";
        PlotSize next  = data.getSize().next();
        String nextStr = next != null ? "⬆ " + next.displayName : "★ Nivel Máximo";

        lines.add(Text.literal("§6§l" + name));
        lines.add(Text.literal("§8─────────────────"));
        lines.add(Text.literal("§7Dueño  §f"    + data.getOwnerName()));
        lines.add(Text.literal("§7Nivel  §b"    + data.getSize().displayName));
        lines.add(Text.literal("§7Miembros  §a" + data.getMembers().size()));
        lines.add(Text.literal("§8─────────────────"));
        lines.add(Text.literal("§eSiguiente: §b" + nextStr));
        lines.add(Text.literal("§8─────────────────"));
        lines.add(Text.literal("§e» §7Clic derecho para gestionar"));
        return lines;
    }
}

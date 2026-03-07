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
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PlotHologramRenderer3D {

    private static final Identifier PANEL_TEXTURE =
            Identifier.of("secure-plots", "textures/item/hologram_panel.png");

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

        // Medir texto
        int lineH    = font.fontHeight + 2;
        int maxW     = 0;
        for (Text t : lines) maxW = Math.max(maxW, font.getWidth(t));
        int totalH   = lines.size() * lineH;
        int panelPxW = maxW   + PAD_X * 2;
        int panelPxH = totalH + PAD_Y * 2;

        MatrixStack matrices = ctx.matrixStack();
        matrices.push();
        matrices.translate(wx, wy, wz);

        // Holograma fijo en el espacio 3D — sin rotación billboard

        // Todo en espacio font: escalar una sola vez
        matrices.scale(TEXT_SCALE, -TEXT_SCALE, TEXT_SCALE);

        float hw = panelPxW / 2f;
        float hh = panelPxH / 2f;

        int light   = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        int overlay = OverlayTexture.DEFAULT_UV;

        // ── Obtener el VertexConsumerProvider de entidades del cliente ───
        // Este es el mismo que usa Minecraft para nametags — se flushea
        // automáticamente al final del frame, igual que el texto
        VertexConsumerProvider.Immediate immediate =
                client.getBufferBuilders().getEntityVertexConsumers();

        // ── 1. Quad texturado ────────────────────────────────────────────
        {
            VertexConsumer vc = immediate.getBuffer(RenderLayer.getEntityTranslucentCull(PANEL_TEXTURE));
            MatrixStack.Entry e = matrices.peek();
            vc.vertex(e, -hw, -hh, 0f).color(255,255,255,230).texture(0f,0f).overlay(overlay).light(light).normal(e,0,0,1);
            vc.vertex(e,  hw, -hh, 0f).color(255,255,255,230).texture(1f,0f).overlay(overlay).light(light).normal(e,0,0,1);
            vc.vertex(e,  hw,  hh, 0f).color(255,255,255,230).texture(1f,1f).overlay(overlay).light(light).normal(e,0,0,1);
            vc.vertex(e, -hw,  hh, 0f).color(255,255,255,230).texture(0f,1f).overlay(overlay).light(light).normal(e,0,0,1);
        }

        // ── 2. Texto — mismo immediate, desplazado 0.5px en Z ───────────
        matrices.translate(0, 0, 0.5f);

        float originX = -panelPxW / 2f + PAD_X;
        float originY = -panelPxH / 2f + PAD_Y;

        for (int i = 0; i < lines.size(); i++) {
            Text  line = lines.get(i);
            float lx   = originX + (maxW - font.getWidth(line)) / 2f;
            float ly   = originY + i * lineH;
            font.draw(line, lx, ly, 0xFFFFFFFF, false,
                    matrices.peek().getPositionMatrix(),
                    immediate,
                    TextRenderer.TextLayerType.NORMAL,
                    0x00000000, light);
        }

        // Flushear el immediate para que texto y quad se dibujen juntos
        immediate.draw();

        matrices.pop();
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

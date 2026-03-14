package com.zhilius.secureplots.client;

import com.zhilius.secureplots.plot.PlotData;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

public class PlotInfoScreen extends Screen {

    private final PlotData data;

    // Panel fijo centrado — pequeño, solo info
    private static final int PW = 260;
    private static final int PH = 220;

    // Colores
    private static final int C_BG        = 0xF0080810;
    private static final int C_TITLE_BAR = 0xFF0B1830;
    private static final int C_BORDER    = 0xFF1E4A8A;
    private static final int C_DIVIDER   = 0xFF1E4A8A;

    public PlotInfoScreen(PlotData data) {
        super(Text.literal("Plot Info"));
        this.data = data;
    }

    private int px() { return (this.width  - PW) / 2; }
    private int py() { return (this.height - PH) / 2; }

    @Override
    protected void init() {
        int px = px(), py = py();

        // Botón cerrar
        addDrawableChild(ButtonWidget.builder(Text.literal("✕"), b -> this.close())
                .dimensions(px + PW - 20, py + 5, 16, 16).build());

        // Botón gestionar (si tiene acceso)
        assert this.client != null;
        PlotData.Role role = data.getRoleOf(this.client.player.getUuid());
        if (role != PlotData.Role.VISITOR) {
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("⚙  Manage  (Shift+click)"),
                    b -> this.close())
                    .dimensions(px + 10, py + PH - 30, PW - 20, 22).build());
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int px = px(), py = py();

        // Fondo oscuro detrás del panel
        ctx.fill(0, 0, this.width, this.height, 0x88000000);

        // Borde + panel
        ctx.fill(px - 2, py - 2, px + PW + 2, py + PH + 2, C_BORDER);
        ctx.fill(px, py, px + PW, py + PH, C_BG);

        // Título
        ctx.fill(px, py, px + PW, py + 26, C_TITLE_BAR);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("🛡  " + data.getPlotName()).formatted(Formatting.GOLD),
                px + PW / 2, py + 8, 0xFFFFFF);

        // Separador bajo título
        ctx.fill(px + 6, py + 26, px + PW - 6, py + 27, C_DIVIDER);

        // Contenido
        int x = px + 14;
        int y = py + 34;
        int gap = 18;

        assert this.client != null;
        long time = this.client.world != null ? this.client.world.getTime() : 0;

        drawRow(ctx, x, y, "Owner",   data.getOwnerName(),                Formatting.WHITE);  y += gap;
        drawRow(ctx, x, y, "Nivel",   data.getSize().getDisplayName(),          Formatting.AQUA);   y += gap;
        int sz = data.getSize().getRadius();
        drawRow(ctx, x, y, "Size",  sz + "x" + sz + " blocks",         Formatting.AQUA);   y += gap;
        drawRow(ctx, x, y, "Miembros", String.valueOf(data.getMembers().size()), Formatting.GREEN); y += gap;

        if (!data.hasRank()) {
            long days = data.getDaysRemaining(time);
            Formatting col = days < 5 ? Formatting.RED : days < 10 ? Formatting.YELLOW : Formatting.GREEN;
            drawRow(ctx, x, y, "Expires in", days + " days", col);
        } else {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("⚡  Permanent (Rank active)").formatted(Formatting.GREEN), x, y, 0xFFFFFF);
        }
        y += gap + 4;

        // Separador antes del hint
        ctx.fill(px + 6, y, px + PW - 6, y + 1, C_DIVIDER);
        y += 7;

        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("Shift+clic  ").formatted(Formatting.YELLOW)
                        .append(Text.literal("para gestionar").formatted(Formatting.DARK_GRAY)),
                px + PW / 2, y, 0xFFFFFF);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawRow(DrawContext ctx, int x, int y, String label, String value, Formatting valColor) {
        ctx.drawTextWithShadow(textRenderer,
                Text.literal(label + ": ").formatted(Formatting.GRAY)
                        .append(Text.literal(value).formatted(valColor)),
                x, y, 0xFFFFFF);
    }

    @Override
    public boolean shouldPause() { return false; }

    // Se cierra al hacer cualquier tecla o clic fuera del panel
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int px = px(), py = py();
        boolean insidePanel = mouseX >= px && mouseX <= px + PW && mouseY >= py && mouseY <= py + PH;
        if (!insidePanel) { this.close(); return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}

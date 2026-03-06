package com.zhilius.secureplots.client;

import com.zhilius.secureplots.config.SecurePlotsConfig;
import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotSize;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlotScreen extends Screen {

    private final BlockPos plotPos;
    private PlotData data;
    private PlotData.Role myRole;

    private static final int TAB_INFO    = 0;
    private static final int TAB_MEMBERS = 1;
    private static final int TAB_UPGRADE = 2;
    private int activeTab = TAB_INFO;

    private TextFieldWidget nameField;
    private TextFieldWidget addPlayerField;

    private static final int PW = 320;
    private static final int PH = 270;

    public PlotScreen(BlockPos plotPos, PlotData data) {
        super(Text.literal("Secure Plots"));
        this.plotPos = plotPos;
        this.data    = data;
    }

    private int px() { return (this.width  - PW) / 2; }
    private int py() { return (this.height - PH) / 2; }

    @Override
    protected void init() {
        assert this.client != null;
        myRole = data.getRoleOf(this.client.player.getUuid());

        int px = px(), py = py();
        int cy = py + 58; // content area starts here

        // Tabs
        addDrawableChild(ButtonWidget.builder(Text.literal("📋 Info"),
                b -> { activeTab = TAB_INFO; clearAndInit(); })
                .dimensions(px + 10, py + 28, 88, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("👥 Miembros"),
                b -> { activeTab = TAB_MEMBERS; clearAndInit(); })
                .dimensions(px + 116, py + 28, 88, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("⬆ Mejorar"),
                b -> { activeTab = TAB_UPGRADE; clearAndInit(); })
                .dimensions(px + 222, py + 28, 88, 20).build());

        // Close
        addDrawableChild(ButtonWidget.builder(Text.literal("✕"), b -> this.close())
                .dimensions(px + PW - 22, py + 4, 18, 18).build());

        // Tab-specific widgets
        if (activeTab == TAB_INFO)    initInfoTab(px, cy);
        if (activeTab == TAB_MEMBERS) initMembersTab(px, cy);
        if (activeTab == TAB_UPGRADE) initUpgradeTab(px, cy);
    }

    // ── INFO TAB ─────────────────────────────────────────────────────────────
    // Layout:
    //   cy+0   → label "Nombre:" + field (si owner) | solo texto si no
    //   cy+26  → Dueño
    //   cy+42  → Nivel
    //   cy+58  → Tamaño
    //   cy+74  → Tu rol
    //   cy+90  → Expira / Permanente
    //   cy+106 → Coords
    //   cy+130 → [Guardar] button (si owner, debajo del campo)
    private void initInfoTab(int px, int cy) {
        if (myRole == PlotData.Role.OWNER) {
            // Name field aligned to the right of the "Nombre:" label (label is at x+10, y+0)
            nameField = new TextFieldWidget(this.textRenderer, px + 75, cy, 180, 16, Text.empty());
            nameField.setText(data.getPlotName());
            nameField.setMaxLength(32);
            addDrawableChild(nameField);

            addDrawableChild(ButtonWidget.builder(Text.literal("Guardar"), b -> {
                data.setPlotName(nameField.getText());
                ClientPlayNetworking.send(new ModPackets.UpdatePlotPayload(plotPos, data.toNbt()));
                this.client.player.sendMessage(Text.literal("✓ Nombre guardado").formatted(Formatting.GREEN), true);
            }).dimensions(px + 260, cy - 1, 50, 18).build());
        }
    }

    // ── MEMBERS TAB ──────────────────────────────────────────────────────────
    // Layout:
    //   cy-10  → "Agregar jugador:" label
    //   cy+8   → [field] [+ Agregar]    ← widgets
    //   cy+32  → "Miembros (N):" label
    //   cy+48+ → list rows with [✕] buttons
    private void initMembersTab(int px, int cy) {
        if (myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN) {
            addPlayerField = new TextFieldWidget(this.textRenderer, px + 10, cy + 8, 155, 16, Text.empty());
            addPlayerField.setPlaceholder(Text.literal("Nombre del jugador...").formatted(Formatting.DARK_GRAY));
            addDrawableChild(addPlayerField);

            addDrawableChild(ButtonWidget.builder(Text.literal("+ Agregar"), b -> {
                String name = addPlayerField.getText().trim();
                if (!name.isEmpty()) {
                    ClientPlayNetworking.send(new ModPackets.AddMemberPayload(plotPos, name));
                    addPlayerField.setText("");
                }
            }).dimensions(px + 173, cy + 7, 80, 18).build());
        }

        // Remove buttons — one per member row
        List<Map.Entry<UUID, PlotData.Role>> members = new ArrayList<>(data.getMembers().entrySet());
        int rowY = cy + 48;
        for (Map.Entry<UUID, PlotData.Role> entry : members) {
            String memberName = data.getMemberName(entry.getKey());
            addDrawableChild(ButtonWidget.builder(Text.literal("✕"), b ->
                    ClientPlayNetworking.send(new ModPackets.RemoveMemberPayload(plotPos, memberName)))
                    .dimensions(px + PW - 38, rowY - 3, 18, 14).build());
            rowY += 16;
            if (rowY > py() + PH - 30) break;
        }
    }

    // ── UPGRADE TAB ──────────────────────────────────────────────────────────
    private void initUpgradeTab(int px, int cy) {
        if (myRole != PlotData.Role.OWNER) return;
        PlotSize next = data.getSize().next();
        if (next == null) return;

        addDrawableChild(ButtonWidget.builder(
                Text.literal("⬆ Confirmar mejora a " + next.displayName),
                b -> ClientPlayNetworking.send(new ModPackets.UpgradePlotPayload(plotPos)))
                .dimensions(px + 20, cy + 120, 280, 20).build());
    }

    // ── RENDER ───────────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);

        int px = px(), py = py();

        // Panel
        ctx.fill(px - 2, py - 2, px + PW + 2, py + PH + 2, 0xFF16213E);
        ctx.fill(px, py, px + PW, py + PH, 0xEE0D0D1A);
        // Title bar
        ctx.fill(px, py, px + PW, py + 24, 0xFF0F3460);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("🛡 " + data.getPlotName()).formatted(Formatting.GOLD),
                px + PW / 2, py + 7, 0xFFFFFF);

        int cy = py + 58;
        int x  = px + 10;

        if (activeTab == TAB_INFO)    renderInfoTab(ctx, x, cy);
        if (activeTab == TAB_MEMBERS) renderMembersTab(ctx, x, cy);
        if (activeTab == TAB_UPGRADE) renderUpgradeTab(ctx, x, cy);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderInfoTab(DrawContext ctx, int x, int y) {
        assert this.client != null;
        long time = this.client.world != null ? this.client.world.getTime() : 0;

        // Row 0: Nombre label (widget field sits at y+0 to the right)
        ctx.drawTextWithShadow(textRenderer, Text.literal("Nombre:").formatted(Formatting.GRAY), x, y + 4, 0xFFFFFF);
        if (myRole != PlotData.Role.OWNER) {
            ctx.drawTextWithShadow(textRenderer, Text.literal(data.getPlotName()).formatted(Formatting.GOLD), x + 65, y + 4, 0xFFFFFF);
        }

        // Row 1+
        ctx.drawTextWithShadow(textRenderer, Text.literal("Dueño: ").formatted(Formatting.GRAY)
                .append(Text.literal(data.getOwnerName()).formatted(Formatting.WHITE)), x, y + 26, 0xFFFFFF);

        ctx.drawTextWithShadow(textRenderer, Text.literal("Nivel: ").formatted(Formatting.GRAY)
                .append(Text.literal(data.getSize().displayName).formatted(Formatting.AQUA)), x, y + 42, 0xFFFFFF);

        int size = data.getSize().radius;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Tamaño: ").formatted(Formatting.GRAY)
                .append(Text.literal(size + "x" + size + " bloques").formatted(Formatting.AQUA)), x, y + 58, 0xFFFFFF);

        ctx.drawTextWithShadow(textRenderer, Text.literal("Tu rol: ").formatted(Formatting.GRAY)
                .append(Text.literal(myRole.name()).formatted(roleColor(myRole))), x, y + 74, 0xFFFFFF);

        if (!data.hasRank()) {
            long days = data.getDaysRemaining(time);
            Formatting col = days < 5 ? Formatting.RED : days < 10 ? Formatting.YELLOW : Formatting.GREEN;
            ctx.drawTextWithShadow(textRenderer, Text.literal("Expira en: ").formatted(Formatting.GRAY)
                    .append(Text.literal(days + " días").formatted(col)), x, y + 90, 0xFFFFFF);
        } else {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("⚡ Permanente (Rango activo)").formatted(Formatting.GREEN), x, y + 90, 0xFFFFFF);
        }

        BlockPos c = data.getCenter();
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Coords: " + c.getX() + ", " + c.getY() + ", " + c.getZ()).formatted(Formatting.DARK_GRAY),
                x, y + 106, 0xFFFFFF);
    }

    private void renderMembersTab(DrawContext ctx, int x, int y) {
        boolean canManage = myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;

        if (canManage) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("Agregar (debe estar en línea):").formatted(Formatting.GRAY), x, y - 10, 0xFFFFFF);
        }

        int memberCount = data.getMembers().size();
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Miembros (" + memberCount + "):").formatted(Formatting.GOLD), x, y + 32, 0xFFFFFF);

        if (data.getMembers().isEmpty()) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("Sin miembros todavía.").formatted(Formatting.DARK_GRAY), x, y + 48, 0xFFFFFF);
            return;
        }

        int rowY = y + 48;
        for (Map.Entry<UUID, PlotData.Role> entry : data.getMembers().entrySet()) {
            String name = data.getMemberName(entry.getKey());
            PlotData.Role role = entry.getValue();
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("• " + name + "  ").formatted(Formatting.WHITE)
                            .append(Text.literal("[" + role.name().toLowerCase() + "]").formatted(roleColor(role))),
                    x, rowY, 0xFFFFFF);
            rowY += 16;
            if (rowY > py() + PH - 30) {
                ctx.drawTextWithShadow(textRenderer, Text.literal("...").formatted(Formatting.GRAY), x, rowY, 0xFFFFFF);
                break;
            }
        }
    }

    private void renderUpgradeTab(DrawContext ctx, int x, int y) {
        PlotSize current = data.getSize();
        PlotSize next = current.next();

        ctx.drawTextWithShadow(textRenderer, Text.literal("Nivel actual: ").formatted(Formatting.GRAY)
                .append(Text.literal(current.displayName + " (" + current.radius + "x" + current.radius + ")").formatted(Formatting.AQUA)),
                x, y, 0xFFFFFF);

        if (next == null) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("⭐ ¡Máximo nivel alcanzado!").formatted(Formatting.GOLD), x, y + 20, 0xFFFFFF);
            return;
        }

        ctx.drawTextWithShadow(textRenderer, Text.literal("Siguiente: ").formatted(Formatting.GRAY)
                .append(Text.literal(next.displayName + " (" + next.radius + "x" + next.radius + ")").formatted(Formatting.GREEN)),
                x, y + 18, 0xFFFFFF);

        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        SecurePlotsConfig.UpgradeCost cost = cfg != null ? cfg.getUpgradeCost(current.tier) : null;

        if (cost != null) {
            ctx.drawTextWithShadow(textRenderer, Text.literal("Costo:").formatted(Formatting.YELLOW), x, y + 42, 0xFFFFFF);
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("  • " + cost.cobblecoins + " Cobblecoins").formatted(Formatting.WHITE), x, y + 56, 0xFFFFFF);
            int iy = y + 70;
            for (SecurePlotsConfig.UpgradeCost.ItemCost item : cost.items) {
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal("  • " + item.amount + "x " + item.itemId).formatted(Formatting.WHITE), x, iy, 0xFFFFFF);
                iy += 14;
            }
        }

        if (myRole != PlotData.Role.OWNER) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("Solo el dueño puede mejorar.").formatted(Formatting.RED), x, y + 130, 0xFFFFFF);
        }
    }

    private Formatting roleColor(PlotData.Role role) {
        return switch (role) {
            case OWNER   -> Formatting.GOLD;
            case ADMIN   -> Formatting.RED;
            case MEMBER  -> Formatting.GREEN;
            case VISITOR -> Formatting.GRAY;
        };
    }

    protected void clearAndInit() { clearChildren(); init(); }

    @Override public boolean shouldPause() { return false; }
}

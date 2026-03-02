package com.zhilius.secureplots.client;

import com.zhilius.secureplots.SecurePlots;
import com.zhilius.secureplots.config.SecurePlotsConfig;
import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotSize;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;

public class PlotScreen extends Screen {

    private final BlockPos plotPos;
    private final PlotData data;
    private PlotData.Role myRole;

    private static final int TAB_INFO = 0;
    private static final int TAB_MEMBERS = 1;
    private static final int TAB_UPGRADE = 2;
    private int activeTab = TAB_INFO;

    private TextFieldWidget nameField;
    private TextFieldWidget addPlayerField;

    // Colors
    private static final int COLOR_BG = 0xCC0D0D1A;
    private static final int COLOR_PANEL = 0xCC1A1A2E;
    private static final int COLOR_BORDER = 0xFF16213E;
    private static final int COLOR_ACCENT = 0xFF0F3460;
    private static final int COLOR_GOLD = 0xFFFFD700;
    private static final int COLOR_GREEN = 0xFF00FF7F;
    private static final int COLOR_RED = 0xFFFF4444;
    private static final int COLOR_BLUE = 0xFF4FC3F7;

    public PlotScreen(BlockPos plotPos, PlotData data) {
        super(Text.literal("Secure Plots"));
        this.plotPos = plotPos;
        this.data = data;
    }

    @Override
    protected void init() {
        assert this.client != null;
        UUID myId = this.client.player.getUuid();
        myRole = data.getRoleOf(myId);

        int w = this.width;
        int h = this.height;
        int panelW = 320;
        int panelH = 240;
        int panelX = (w - panelW) / 2;
        int panelY = (h - panelH) / 2;

        // Tab buttons
        addDrawableChild(ButtonWidget.builder(Text.literal("📋 Info"), b -> {
            activeTab = TAB_INFO;
            clearAndInit();
        }).dimensions(panelX + 10, panelY + 30, 90, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("👥 Miembros"), b -> {
            activeTab = TAB_MEMBERS;
            clearAndInit();
        }).dimensions(panelX + 110, panelY + 30, 90, 20).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("⬆ Mejorar"), b -> {
            activeTab = TAB_UPGRADE;
            clearAndInit();
        }).dimensions(panelX + 210, panelY + 30, 90, 20).build());

        // Close button
        addDrawableChild(ButtonWidget.builder(Text.literal("✕"), b -> this.close())
                .dimensions(panelX + panelW - 20, panelY + 5, 15, 15).build());

        // Tab content
        int contentY = panelY + 60;

        if (activeTab == TAB_INFO) {
            initInfoTab(panelX, contentY, panelW);
        } else if (activeTab == TAB_MEMBERS) {
            initMembersTab(panelX, contentY, panelW);
        } else if (activeTab == TAB_UPGRADE) {
            initUpgradeTab(panelX, contentY, panelW);
        }
    }

    private void initInfoTab(int panelX, int contentY, int panelW) {
        if (myRole == PlotData.Role.OWNER) {
            nameField = new TextFieldWidget(this.textRenderer, panelX + 80, contentY + 5, 180, 16, Text.empty());
            nameField.setText(data.getPlotName());
            nameField.setMaxLength(32);
            addDrawableChild(nameField);

            addDrawableChild(ButtonWidget.builder(Text.literal("Guardar"), b -> {
                data.setPlotName(nameField.getText());
                sendUpdatePacket();
                this.client.player.sendMessage(Text.literal("✓ Nombre guardado").formatted(Formatting.GREEN), true);
            }).dimensions(panelX + 265, contentY + 4, 45, 18).build());
        }
    }

    private void initMembersTab(int panelX, int contentY, int panelW) {
        if (myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN) {
            addPlayerField = new TextFieldWidget(this.textRenderer, panelX + 10, contentY, 150, 16, Text.empty());
            addPlayerField.setPlaceholder(Text.literal("Nombre del jugador...").formatted(Formatting.DARK_GRAY));
            addDrawableChild(addPlayerField);

            addDrawableChild(ButtonWidget.builder(Text.literal("+ Miembro"), b -> {
                // Would need to resolve player name to UUID via server
                this.client.player.sendMessage(Text.literal("Función próximamente...").formatted(Formatting.YELLOW),
                        true);
            }).dimensions(panelX + 170, contentY - 1, 80, 18).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("+ Admin"), b -> {
                this.client.player.sendMessage(Text.literal("Función próximamente...").formatted(Formatting.YELLOW),
                        true);
            }).dimensions(panelX + 255, contentY - 1, 55, 18).build());
        }
    }

    private void initUpgradeTab(int panelX, int contentY, int panelW) {
        PlotSize nextSize = data.getSize().next();
        if (nextSize == null)
            return;

        SecurePlotsConfig.UpgradeCost cost = SecurePlotsConfig.INSTANCE.getUpgradeCost(data.getSize().tier);
        if (cost == null)
            return;

        if (myRole == PlotData.Role.OWNER) {
            addDrawableChild(ButtonWidget.builder(
                    Text.literal("⬆ Mejorar a " + nextSize.displayName + " - " + cost.cobblecoins + " cobblecoins"),
                    b -> sendUpgradePacket()).dimensions(panelX + 20, contentY + 100, 280, 20).build());
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        int w = this.width;
        int h = this.height;
        int panelW = 320;
        int panelH = 240;
        int panelX = (w - panelW) / 2;
        int panelY = (h - panelH) / 2;

        // Background panel
        context.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, 0xFF16213E);
        context.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xEE0D0D1A);

        // Title bar
        context.fill(panelX, panelY, panelX + panelW, panelY + 25, 0xFF0F3460);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("🛡 " + data.getPlotName()).formatted(Formatting.GOLD),
                panelX + panelW / 2, panelY + 8, 0xFFFFFF);

        int contentY = panelY + 60;

        if (activeTab == TAB_INFO) {
            renderInfoTab(context, panelX + 10, contentY);
        } else if (activeTab == TAB_MEMBERS) {
            renderMembersTab(context, panelX + 10, contentY);
        } else if (activeTab == TAB_UPGRADE) {
            renderUpgradeTab(context, panelX + 10, contentY);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderInfoTab(DrawContext ctx, int x, int y) {
        assert this.client != null;
        long time = this.client.world != null ? this.client.world.getTime() : 0;

        if (myRole == PlotData.Role.OWNER) {
            ctx.drawTextWithShadow(textRenderer, Text.literal("Nombre:").formatted(Formatting.GRAY), x, y + 8,
                    0xFFFFFF);
        } else {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("Nombre: " + data.getPlotName()).formatted(Formatting.GOLD), x, y + 8, 0xFFFFFF);
        }

        ctx.drawTextWithShadow(textRenderer, Text.literal("Dueño: ").formatted(Formatting.GRAY)
                .append(Text.literal(data.getOwnerName()).formatted(Formatting.WHITE)), x, y + 32, 0xFFFFFF);

        ctx.drawTextWithShadow(textRenderer, Text.literal("Tamaño: ").formatted(Formatting.GRAY)
                .append(Text.literal(
                        data.getSize().displayName + " (" + data.getSize().radius + "x" + data.getSize().radius + ")")
                        .formatted(Formatting.AQUA)),
                x, y + 48, 0xFFFFFF);

        ctx.drawTextWithShadow(textRenderer, Text.literal("Tu rol: ").formatted(Formatting.GRAY)
                .append(Text.literal(myRole.name()).formatted(roleColor(myRole))), x, y + 64, 0xFFFFFF);

        if (!data.hasRank()) {
            long days = data.getDaysRemaining(time);
            Formatting color = days < 5 ? Formatting.RED : days < 10 ? Formatting.YELLOW : Formatting.GREEN;
            ctx.drawTextWithShadow(textRenderer, Text.literal("Expira en: ").formatted(Formatting.GRAY)
                    .append(Text.literal(days + " días").formatted(color)), x, y + 80, 0xFFFFFF);
        } else {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("⚡ Permanente (Rango activo)").formatted(Formatting.GREEN), x, y + 80, 0xFFFFFF);
        }

        BlockPos c = data.getCenter();
        ctx.drawTextWithShadow(textRenderer, Text.literal("Posición: " + c.getX() + ", " + c.getY() + ", " + c.getZ())
                .formatted(Formatting.DARK_GRAY), x, y + 96, 0xFFFFFF);
    }

    private void renderMembersTab(DrawContext ctx, int x, int y) {
        ctx.drawTextWithShadow(textRenderer, Text.literal("Miembros:").formatted(Formatting.GOLD), x, y - 15, 0xFFFFFF);

        int offsetY = y + 20;
        for (Map.Entry<UUID, PlotData.Role> entry : data.getMembers().entrySet()) {
            String name = data.getMemberName(entry.getKey());
            PlotData.Role role = entry.getValue();
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("• " + name + " - " + role.name()).formatted(roleColor(role)),
                    x, offsetY, 0xFFFFFF);
            offsetY += 14;
            if (offsetY > y + 120) {
                ctx.drawTextWithShadow(textRenderer, Text.literal("...").formatted(Formatting.GRAY), x, offsetY,
                        0xFFFFFF);
                break;
            }
        }

        if (data.getMembers().isEmpty()) {
            ctx.drawTextWithShadow(textRenderer, Text.literal("Sin miembros todavía.").formatted(Formatting.DARK_GRAY),
                    x, y + 20, 0xFFFFFF);
        }
    }

    private void renderUpgradeTab(DrawContext ctx, int x, int y) {
        PlotSize current = data.getSize();
        PlotSize next = current.next();

        ctx.drawTextWithShadow(textRenderer, Text.literal("Nivel actual: ").formatted(Formatting.GRAY)
                .append(Text.literal(current.displayName + " (" + current.radius + "x" + current.radius + ")")
                        .formatted(Formatting.AQUA)),
                x, y, 0xFFFFFF);

        if (next == null) {
            ctx.drawTextWithShadow(textRenderer, Text.literal("⭐ ¡Máximo nivel alcanzado!").formatted(Formatting.GOLD),
                    x, y + 20, 0xFFFFFF);
            return;
        }

        ctx.drawTextWithShadow(textRenderer, Text.literal("Siguiente: ").formatted(Formatting.GRAY)
                .append(Text.literal(next.displayName + " (" + next.radius + "x" + next.radius + ")")
                        .formatted(Formatting.GREEN)),
                x, y + 16, 0xFFFFFF);

        SecurePlotsConfig.UpgradeCost cost = SecurePlotsConfig.INSTANCE != null
                ? SecurePlotsConfig.INSTANCE.getUpgradeCost(current.tier)
                : null;

        if (cost != null) {
            ctx.drawTextWithShadow(textRenderer, Text.literal("Costo:").formatted(Formatting.YELLOW), x, y + 40,
                    0xFFFFFF);
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("  • " + cost.cobblecoins + " Cobblecoins").formatted(Formatting.WHITE), x, y + 56,
                    0xFFFFFF);
            int itemY = y + 72;
            for (SecurePlotsConfig.UpgradeCost.ItemCost item : cost.items) {
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal("  • " + item.amount + "x " + item.itemId).formatted(Formatting.WHITE), x, itemY,
                        0xFFFFFF);
                itemY += 14;
            }
        }
    }

    private Formatting roleColor(PlotData.Role role) {
        return switch (role) {
            case OWNER -> Formatting.GOLD;
            case ADMIN -> Formatting.RED;
            case MEMBER -> Formatting.GREEN;
            case VISITOR -> Formatting.GRAY;
        };
    }

    private void sendUpdatePacket() {
        ClientPlayNetworking.send(new ModPackets.UpdatePlotPayload(plotPos, data.toNbt()));
    }

    private void sendUpgradePacket() {
        ClientPlayNetworking.send(new ModPackets.UpgradePlotPayload(plotPos));
    }

    protected void clearAndInit() {
        clearChildren();
        init();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}

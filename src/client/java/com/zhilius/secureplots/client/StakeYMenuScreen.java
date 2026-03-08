package com.zhilius.secureplots.client;

import com.zhilius.secureplots.network.ModPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * Pantalla de configuración de altura (Y) para una subdivisión creada con estacas.
 *
 * Permite al jugador:
 *  - Activar/desactivar el límite de altura (useY toggle)
 *  - Ingresar yMin e yMax manualmente
 *  - Confirmar con "Guardar" o cancelar con "Cerrar"
 */
public class StakeYMenuScreen extends Screen {

    private final BlockPos stakePos;
    private final BlockPos plotCenter;
    private final String subName;

    private boolean useY;
    private int yMin;
    private int yMax;

    private TextFieldWidget yMinField;
    private TextFieldWidget yMaxField;
    private ButtonWidget toggleYButton;
    private ButtonWidget saveButton;

    private static final int PANEL_W = 220;
    private static final int PANEL_H = 160;

    public StakeYMenuScreen(BlockPos stakePos, BlockPos plotCenter, String subName,
                             boolean useY, int yMin, int yMax) {
        super(Text.literal("Configurar Altura — " + subName));
        this.stakePos   = stakePos;
        this.plotCenter = plotCenter;
        this.subName    = subName;
        this.useY       = useY;
        this.yMin       = yMin;
        this.yMax       = yMax;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;
        int top = cy - PANEL_H / 2;

        // Toggle useY button
        toggleYButton = ButtonWidget.builder(
                        Text.literal(useY ? "§aLímite de altura: §lACTIVADO" : "§7Límite de altura: §lDESACTIVADO"),
                        btn -> {
                            useY = !useY;
                            updateToggleLabel();
                            updateFieldStates();
                        })
                .dimensions(cx - 100, top + 30, 200, 20)
                .build();
        addDrawableChild(toggleYButton);

        // Y Min label + field
        yMinField = new TextFieldWidget(textRenderer, cx - 100, top + 65, 90, 18,
                Text.literal("Y mínima"));
        yMinField.setMaxLength(6);
        yMinField.setText(String.valueOf(yMin));
        yMinField.setChangedListener(s -> {
            try { yMin = Integer.parseInt(s.trim()); } catch (Exception ignored) {}
        });
        addDrawableChild(yMinField);

        // Y Max label + field
        yMaxField = new TextFieldWidget(textRenderer, cx + 10, top + 65, 90, 18,
                Text.literal("Y máxima"));
        yMaxField.setMaxLength(6);
        yMaxField.setText(String.valueOf(yMax));
        yMaxField.setChangedListener(s -> {
            try { yMax = Integer.parseInt(s.trim()); } catch (Exception ignored) {}
        });
        addDrawableChild(yMaxField);

        // Save button
        saveButton = ButtonWidget.builder(Text.literal("§a✔ Guardar"), btn -> save())
                .dimensions(cx - 110, top + 105, 100, 20)
                .build();
        addDrawableChild(saveButton);

        // Cancel button
        addDrawableChild(ButtonWidget.builder(Text.literal("§c✗ Cerrar"), btn -> close())
                .dimensions(cx + 10, top + 105, 100, 20)
                .build());

        updateFieldStates();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        int cx = width / 2;
        int cy = height / 2;
        int left = cx - PANEL_W / 2;
        int top  = cy - PANEL_H / 2;

        // Background panel
        context.fill(left, top, left + PANEL_W, top + PANEL_H, 0xCC111118);
        context.drawBorder(left, top, PANEL_W, PANEL_H, 0xFF9966FF);

        // Title
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§dEstaca de Parcela §8— §eAltura"),
                cx, top + 10, 0xFFFFFF);

        // Subtitle: subname
        context.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§7Subdivisión: §f" + subName),
                cx, top + 22, 0xFFFFFF);

        // Field labels
        if (useY) {
            context.drawTextWithShadow(textRenderer, Text.literal("§7Y mínima:"),
                    cx - 100, top + 55, 0xFFFFFF);
            context.drawTextWithShadow(textRenderer, Text.literal("§7Y máxima:"),
                    cx + 10, top + 55, 0xFFFFFF);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void updateToggleLabel() {
        toggleYButton.setMessage(Text.literal(
                useY ? "§aLímite de altura: §lACTIVADO" : "§7Límite de altura: §lDESACTIVADO"));
    }

    private void updateFieldStates() {
        yMinField.setEditable(useY);
        yMaxField.setEditable(useY);
        yMinField.setVisible(useY);
        yMaxField.setVisible(useY);
    }

    private void save() {
        // Validate
        if (useY && yMin >= yMax) {
            // Flash error — swap if needed
            int tmp = yMin; yMin = yMax; yMax = tmp;
            yMinField.setText(String.valueOf(yMin));
            yMaxField.setText(String.valueOf(yMax));
        }
        // Send to server
        ClientPlayNetworking.send(new ModPackets.StakeYConfigPayload(
                stakePos, plotCenter, subName, useY, yMin, yMax));
        close();
    }

    @Override
    public boolean shouldPause() { return false; }
}

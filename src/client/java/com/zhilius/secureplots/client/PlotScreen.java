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

import com.zhilius.secureplots.config.BorderConfig;
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

import java.util.*;

public class PlotScreen extends Screen {

    private final BlockPos plotPos;
    private PlotData data;
    private PlotData.Role myRole;

    private static final int TAB_INFO     = 0;
    private static final int TAB_MEMBERS  = 1;
    private static final int TAB_UPGRADE  = 2;
    private static final int TAB_AMBIENT  = 3;
    private static final int TAB_CREATIVE = 4;
    private int activeTab = TAB_INFO;

    private TextFieldWidget nameField;
    private TextFieldWidget addPlayerField;

    private UUID selectedMember = null;


    public PlotScreen(BlockPos plotPos, PlotData data) {
        super(Text.literal("Plot"));
        this.plotPos = plotPos;
        this.data    = data;
    }

    private static BorderConfig cfg() { BorderConfig c = PlotBorderRendererConfig.current; return c != null ? c : BorderConfig.createDefault(); }
    private int PW() { return cfg().screenPanelWidth; }
    private int PH() { return cfg().screenPanelHeight; }
    private int px() { return (this.width  - PW()) / 2; }
    private int py() { return (this.height - PH()) / 2; }

    // ── i18n ─────────────────────────────────────────────────────────────────

    private static boolean isSpanish() {
        String lang = MinecraftClient.getInstance().getLanguageManager().getLanguage();
        return lang != null && lang.startsWith("es");
    }

    private static String t(boolean es, String en, String esp) {
        return es ? esp : en;
    }

    // ── Init ─────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        assert this.client != null;
        myRole = data.getRoleOf(this.client.player.getUuid());
        boolean es = isSpanish();
        int px = px(), py = py();

        if (selectedMember != null) {
            initPermissionsSubScreen(px, py, es);
            return;
        }

        int tw = PW() / 5;
        addDrawableChild(ButtonWidget.builder(Text.literal(t(es, "Info",     "Info")),     b -> { activeTab = TAB_INFO;     selectedMember = null; clearAndInit(); }).dimensions(px,          py - 20, tw - 1, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(t(es, "Members",  "Miembros")), b -> { activeTab = TAB_MEMBERS;  selectedMember = null; clearAndInit(); }).dimensions(px + tw,     py - 20, tw - 1, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(t(es, "Upgrade",  "Mejorar")),  b -> { activeTab = TAB_UPGRADE;  selectedMember = null; clearAndInit(); }).dimensions(px + tw * 2, py - 20, tw - 1, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(t(es, "Ambient",  "Ambiente")), b -> { activeTab = TAB_AMBIENT;  selectedMember = null; clearAndInit(); }).dimensions(px + tw * 3, py - 20, tw - 1, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(t(es, "Creative", "Creativo")), b -> { activeTab = TAB_CREATIVE; selectedMember = null; clearAndInit(); }).dimensions(px + tw * 4, py - 20, tw,     20).build());

        int cy = py + 28;
        if (activeTab == TAB_INFO)     initInfoTab(px, cy, es);
        if (activeTab == TAB_MEMBERS)  initMembersTab(px, cy, es);
        if (activeTab == TAB_UPGRADE)  initUpgradeTab(px, cy, es);
        if (activeTab == TAB_AMBIENT)  initAmbientTab(px, cy, es);
        if (activeTab == TAB_CREATIVE) initCreativeTab(px, cy, es);
    }

    // ── PERMISSIONS SUB-SCREEN ───────────────────────────────────────────────
    private void initPermissionsSubScreen(int px, int py, boolean es) {
        boolean canManage = myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;
        addDrawableChild(ButtonWidget.builder(Text.literal("\u2190 " + t(es, "Back", "Volver")), b -> {
            selectedMember = null; activeTab = TAB_MEMBERS; clearAndInit();
        }).dimensions(px, py - 20, 80, 20).build());

        if (!canManage) return;

        int cy = py + 50;
        int btnW = PW() - 30;

        for (PlotData.Permission perm : PlotData.Permission.values()) {
            boolean has = data.hasPermission(selectedMember, perm);
            String label = (has ? "\u00a7a\u2714 " : "\u00a7c\u2718 ") + permLabel(perm, es) + " \u00a77\u2014 " + permDesc(perm, es);
            final PlotData.Permission p = perm;
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                boolean current = data.hasPermission(selectedMember, p);
                ClientPlayNetworking.send(new ModPackets.SetPermissionPayload(
                    plotPos, selectedMember.toString(), p.name(), !current));
            }).dimensions(px + 12, cy, btnW, 18).build());
            cy += 22;
        }

        cy += 8;
        String memberName = data.getMemberName(selectedMember);
        addDrawableChild(ButtonWidget.builder(
            Text.literal("\u00a7c\uD83D\uDDD1 " + t(es, "Remove ", "Quitar ") + memberName + t(es, " from plot", " de la parcela")), b ->
                ClientPlayNetworking.send(new ModPackets.RemoveMemberPayload(plotPos, memberName))
        ).dimensions(px + 12, cy, btnW, 18).build());
    }

    // ── INFO ─────────────────────────────────────────────────────────────────
    private void initInfoTab(int px, int cy, boolean es) {
        if (myRole == PlotData.Role.OWNER) {
            nameField = new TextFieldWidget(textRenderer, px + 72, cy + 2, PW() - 150, 16, Text.empty());
            nameField.setText(data.getPlotName());
            nameField.setMaxLength(cfg().screenMaxNameLength);
            addDrawableChild(nameField);

            addDrawableChild(ButtonWidget.builder(Text.literal(t(es, "Save", "Guardar")), b -> {
                data.setPlotName(nameField.getText());
                ClientPlayNetworking.send(new ModPackets.UpdatePlotPayload(plotPos, data.toNbt()));
                assert this.client != null;
                this.client.player.sendMessage(Text.literal("\u2713 " + t(es, "Saved", "Guardado")).formatted(Formatting.GREEN), true);
            }).dimensions(px + PW() - 74, cy + 1, 66, 18).build());
        }
    }

    // ── MEMBERS ──────────────────────────────────────────────────────────────
    private void initMembersTab(int px, int cy, boolean es) {
        boolean canManage = myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;
        if (canManage) {
            addPlayerField = new TextFieldWidget(textRenderer, px + 8, cy + 16, PW() - 100, 16, Text.empty());
            addPlayerField.setPlaceholder(Text.literal(t(es, "Player (online)...", "Jugador (en línea)...")).formatted(Formatting.DARK_GRAY));
            addDrawableChild(addPlayerField);

            addDrawableChild(ButtonWidget.builder(Text.literal("+ " + t(es, "Add", "Agregar")), b -> {
                String name = addPlayerField.getText().trim();
                if (!name.isEmpty()) {
                    ClientPlayNetworking.send(new ModPackets.AddMemberPayload(plotPos, name));
                    addPlayerField.setText("");
                }
            }).dimensions(px + PW() - 88, cy + 15, 84, 18).build());
        }

        int sepY = cy + (canManage ? 40 : 4);
        List<Map.Entry<UUID, PlotData.Role>> members = new ArrayList<>(data.getMembers().entrySet());
        int rowY = sepY + 20;

        for (Map.Entry<UUID, PlotData.Role> entry : members) {
            if (rowY + 18 > py() + PH() - 14) break;
            UUID uuid = entry.getKey();
            String memberName = data.getMemberName(uuid);

            if (canManage) {
                addDrawableChild(ButtonWidget.builder(
                    Text.literal("\u2699 " + memberName), b -> {
                        selectedMember = uuid;
                        clearAndInit();
                    }).dimensions(px + 8, rowY - 2, PW() - 40, 16).build());

                addDrawableChild(ButtonWidget.builder(Text.literal("\u2715"), b ->
                    ClientPlayNetworking.send(new ModPackets.RemoveMemberPayload(plotPos, memberName)))
                    .dimensions(px + PW() - 26, rowY - 2, 16, 14).build());
            }
            rowY += 18;
        }
    }

    // ── AMBIENT ──────────────────────────────────────────────────────────────
    private void initAmbientTab(int px, int cy, boolean es) {
        boolean canEdit = myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;
        if (!canEdit) return;

        int gap = 28;

        String pCurrent = data.getParticleEffect();
        addDrawableChild(ButtonWidget.builder(
            Text.literal("\u2728 " + t(es, "Set Particles", "Partículas") + "  \u00a78(" + (pCurrent.isEmpty() ? t(es, "none", "ninguna") : pCurrent) + ")"), b -> {
                assert this.client != null;
                this.client.setScreen(new net.minecraft.client.gui.screen.ChatScreen("/sp plot particle "));
        }).dimensions(px + 10, cy, PW() - 70, 20).build());
        if (!pCurrent.isEmpty()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7c\u2715 " + t(es, "Clear", "Quitar")), b -> {
                data.setParticleEffect("");
                ClientPlayNetworking.send(new ModPackets.UpdatePlotPayload(plotPos, data.toNbt()));
                clearAndInit();
            }).dimensions(px + PW() - 56, cy, 46, 20).build());
        }
        cy += gap;

        String mCurrent = data.getMusicSound();
        addDrawableChild(ButtonWidget.builder(
            Text.literal("\uD83C\uDFB5 " + t(es, "Set Music", "Música") + "  \u00a78(" + (mCurrent.isEmpty() ? t(es, "none", "ninguna") : mCurrent) + ")"), b -> {
                assert this.client != null;
                this.client.setScreen(new net.minecraft.client.gui.screen.ChatScreen("/sp plot music "));
        }).dimensions(px + 10, cy, PW() - 70, 20).build());
        if (!mCurrent.isEmpty()) {
            addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7c\u2715 " + t(es, "Clear", "Quitar")), b -> {
                data.setMusicSound("");
                ClientPlayNetworking.send(new ModPackets.UpdatePlotPayload(plotPos, data.toNbt()));
                clearAndInit();
            }).dimensions(px + PW() - 56, cy, 46, 20).build());
        }
    }

    // ── CREATIVE ─────────────────────────────────────────────────────────────
    private void initCreativeTab(int px, int cy, boolean es) {
        String[] tierNames  = { "Bronze", "Gold", "Emerald", "Diamond", "Netherite" };
        String[] tierColors = { "\u00a76", "\u00a7e", "\u00a7a", "\u00a7b", "\u00a77" };
        int btnW = PW() - 28;
        for (int i = 0; i < tierNames.length; i++) {
            final int tier = i;
            String label = tierColors[i] + "\u2B1B " + t(es, "Plot Block", "Bloque de Parcela") + " \u2014 " + tierNames[i];
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b ->
                ClientPlayNetworking.send(new ModPackets.GiveBlockPayload(tier))
            ).dimensions(px + 12, cy + i * 24, btnW, 20).build());
        }
    }

    // ── UPGRADE ──────────────────────────────────────────────────────────────
    private void initUpgradeTab(int px, int cy, boolean es) {
        if (myRole != PlotData.Role.OWNER) return;
        PlotSize next = data.getSize().next();
        if (next == null) return;
        addDrawableChild(ButtonWidget.builder(
                Text.literal(t(es, "Upgrade to ", "Mejorar a ") + next.getDisplayName() + "  \u25B6"),
                b -> ClientPlayNetworking.send(new ModPackets.UpgradePlotPayload(plotPos)))
                .dimensions(px + 14, py() + PH() - 32, PW() - 28, 20).build());
    }

    // ── RENDER ───────────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);
        boolean es = isSpanish();

        int px = px(), py = py();

        ctx.fill(px - 1, py - 1, px + PW() + 1, py + PH() + 1, cfg().screenColorBorderOuter);
        ctx.fill(px, py, px + PW(), py + PH(), cfg().screenColorBackground);
        ctx.fill(px + 1, py + 1, px + PW(), py + 2,        cfg().screenColorShadowDark);
        ctx.fill(px + 1, py + 1, px + 2,  py + PH(),       cfg().screenColorShadowDark);
        ctx.fill(px + 1, py + PH() - 2, px + PW() - 1, py + PH() - 1, cfg().screenColorShadowLight);
        ctx.fill(px + PW() - 2, py + 1,  px + PW() - 1, py + PH() - 1, cfg().screenColorShadowLight);
        ctx.fill(px, py, px + PW(), py + 24, cfg().screenColorTitleBar);
        ctx.fill(px, py, px + PW(), py + 23, cfg().screenColorTitleBarTop);

        String title = selectedMember != null
            ? "\u2699 " + t(es, "Permissions: ", "Permisos: ") + data.getMemberName(selectedMember)
            : "\uD83D\uDEE1 " + data.getPlotName();
        ctx.drawTextWithShadow(textRenderer, Text.literal(title).formatted(Formatting.YELLOW), px + 6, py + 7, 0xFFFFFF);
        ctx.fill(px + 4, py + 24, px + PW() - 4, py + 25, 0xFF8B8B8B);

        if (selectedMember == null) {
            int tw = PW() / 5;
            int tabX = px + activeTab * tw;
            ctx.fill(tabX, py - 20, tabX + tw - (activeTab == 4 ? 0 : 1), py, 0x55FFFFFF);
        }

        int cy = py + 28;
        int x  = px + 8;

        if (selectedMember != null) {
            renderPermissions(ctx, x, cy, es);
        } else {
            if (activeTab == TAB_INFO)     renderInfo(ctx, x, cy, es);
            if (activeTab == TAB_MEMBERS)  renderMembers(ctx, x, cy, es);
            if (activeTab == TAB_UPGRADE)  renderUpgrade(ctx, x, cy, es);
            if (activeTab == TAB_AMBIENT)  renderAmbient(ctx, x, cy, es);
            if (activeTab == TAB_CREATIVE) renderCreative(ctx, x, cy, es);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    // ── Render Permissions ───────────────────────────────────────────────────
    private void renderPermissions(DrawContext ctx, int x, int y, boolean es) {
        if (selectedMember == null) return;
        String name = data.getMemberName(selectedMember);
        PlotData.Role role = data.getRoleOf(selectedMember);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal(name + " \u00a78[" + role.name().toLowerCase() + "]"), x, y + 4, roleRgb(role));
        ctx.drawTextWithShadow(textRenderer,
            Text.literal(t(es, "Toggle individual permissions:", "Activar/desactivar permisos:")).formatted(Formatting.DARK_GRAY),
            x, y + 18, 0x555555);
    }

    // ── Render Info ──────────────────────────────────────────────────────────
    private void renderInfo(DrawContext ctx, int x, int y, boolean es) {
        assert this.client != null;
        long time = this.client.world != null ? this.client.world.getTime() : 0;

        ctx.drawTextWithShadow(textRenderer,
                Text.literal(t(es, "Name:", "Nombre:")).formatted(Formatting.DARK_GRAY), x, y + 6, 0x000000);
        if (myRole != PlotData.Role.OWNER) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal(data.getPlotName()).formatted(Formatting.BLACK), x + 66, y + 6, 0x111111);
        }

        int row = y + 26; int gap = cfg().screenRowSpacing;

        row(ctx, x, row, t(es, "Owner",     "Dueño"),      data.getOwnerName(),             0x333333, 0x000000); row += gap;
        row(ctx, x, row, t(es, "Tier",      "Nivel"),      data.getSize().getDisplayName(), 0x333333, 0x006688); row += gap;
        int sz = data.getSize().getRadius();
        row(ctx, x, row, t(es, "Size",      "Tamaño"),     sz + "x" + sz + t(es, " blocks", " bloques"), 0x333333, 0x006688); row += gap;
        row(ctx, x, row, t(es, "Your role", "Tu rol"),     myRole.name(),                   0x333333, roleRgb(myRole)); row += gap;

        if (!data.hasRank()) {
            long days = data.getDaysRemaining(time);
            int col = days < 5 ? 0xAA0000 : days < 10 ? 0xAA7700 : 0x007700;
            row(ctx, x, row, t(es, "Expires in", "Expira en"), days + t(es, " days", " días"), 0x333333, col);
        } else {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("\u26A1 " + t(es, "Permanent", "Permanente")).formatted(Formatting.DARK_GREEN), x, row, 0x005500);
        }
        row += gap;

        BlockPos c = data.getCenter();
        ctx.drawTextWithShadow(textRenderer,
                Text.literal(c.getX() + ", " + c.getY() + ", " + c.getZ()), x, row, 0x555555);
    }

    // ── Render Members ───────────────────────────────────────────────────────
    private void renderMembers(DrawContext ctx, int x, int y, boolean es) {
        boolean canManage = myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;
        if (canManage) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal(t(es, "Add member:", "Agregar miembro:")), x, y + 2, 0x333333);
        }

        int sepY = y + (canManage ? 36 : 4);
        ctx.fill(x - 2, sepY, x + PW() - 14, sepY + 1, 0xFF8B8B8B);
        ctx.fill(x - 2, sepY + 1, x + PW() - 14, sepY + 2, 0xFFFFFFFF);

        int memberCount = data.getMembers().size();
        ctx.drawTextWithShadow(textRenderer,
                Text.literal(t(es, "Members", "Miembros") + " (" + memberCount + ")  \u00a78\u2014 " + t(es, "click to view permissions", "click para ver permisos")),
                x, sepY + 4, 0x333333);

        if (data.getMembers().isEmpty()) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal(t(es, "None yet.", "Ninguno aún.")), x, sepY + 22, 0x777777);
        }
    }

    // ── Render Upgrade ───────────────────────────────────────────────────────
    private void renderUpgrade(DrawContext ctx, int x, int y, boolean es) {
        PlotSize cur = data.getSize();
        PlotSize next = cur.next();

        int row = y; int gap = cfg().screenRowSpacing;

        row(ctx, x, row, t(es, "Current tier", "Nivel actual"), cur.getDisplayName() + " (" + cur.getRadius() + "x" + cur.getRadius() + ")",
                0x333333, 0x006688); row += gap;

        if (next == null) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("\u2B50 " + t(es, "Maximum tier reached!", "¡Nivel máximo alcanzado!")), x, row, 0x886600);
            return;
        }

        row(ctx, x, row, t(es, "Next tier", "Siguiente nivel"), next.getDisplayName() + " (" + next.getRadius() + "x" + next.getRadius() + ")",
                0x333333, 0x005500); row += gap + 4;

        ctx.fill(x - 2, row, x + PW() - 14, row + 1, 0xFF8B8B8B);
        ctx.fill(x - 2, row + 1, x + PW() - 14, row + 2, 0xFFFFFFFF);
        row += 8;

        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        SecurePlotsConfig.UpgradeCost cost = cfg != null ? cfg.getUpgradeCost(cur.tier) : null;

        if (cost != null) {
            ctx.drawTextWithShadow(textRenderer, Text.literal(t(es, "Materials:", "Materiales:")), x, row, 0x333333);
            row += gap;
            if (cost.cobblecoins > 0) {
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal("  \u2022 " + cost.cobblecoins + " Cobblecoins"), x, row, 0x000000);
                row += gap;
            }
            for (SecurePlotsConfig.UpgradeCost.ItemCost item : cost.items) {
                String raw = item.itemId.contains(":") ? item.itemId.split(":")[1] : item.itemId;
                String name = Character.toUpperCase(raw.charAt(0)) + raw.substring(1).replace("_", " ");
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal("  \u2022 " + item.amount + "x " + name), x, row, 0x000000);
                row += gap;
            }
        }

        if (myRole != PlotData.Role.OWNER) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal(t(es, "Only the owner can upgrade.", "Solo el dueño puede mejorar.")), x, py() + PH() - 40, 0xAA0000);
        }
    }

    // ── Render Ambient ───────────────────────────────────────────────────────
    private void renderAmbient(DrawContext ctx, int x, int y, boolean es) {
        boolean canEdit = myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;
        int gap = 28;

        y += gap * 2 + 16;
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("\u00a77" + t(es, "Click a button to open chat and type the value.", "Haz click para abrir el chat e ingresar el valor.")),
            x, y, 0x555555);
        y += 12;
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("\u00a77" + t(es, "Autocomplete (Tab) shows all available options.", "Autocompletado (Tab) muestra las opciones disponibles.")),
            x, y, 0x555555);

        if (!canEdit) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal(t(es, "Only the owner or admin can edit these.", "Solo el dueño o admin puede editar esto.")).formatted(Formatting.RED),
                    x, y + 24, 0xAA0000);
        }
    }

    // ── Render Creative ──────────────────────────────────────────────────────
    private void renderCreative(DrawContext ctx, int x, int y, boolean es) {
        ctx.drawTextWithShadow(textRenderer,
            Text.literal(t(es, "Get plot blocks:", "Obtener bloques de parcela:")).formatted(Formatting.DARK_GRAY),
            x, y - 14, 0x555555);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal(t(es, "(OPs / creative mode only)", "(Solo OPs / modo creativo)")).formatted(Formatting.DARK_GRAY),
            x, y - 4, 0x888888);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────
    private void row(DrawContext ctx, int x, int y, String label, String value, int labelColor, int valColor) {
        ctx.drawTextWithShadow(textRenderer, Text.literal(label + ": "), x, y, labelColor);
        int offset = textRenderer.getWidth(label + ": ");
        ctx.drawTextWithShadow(textRenderer, Text.literal(value), x + offset, y, valColor);
    }

    private int roleRgb(PlotData.Role role) {
        return switch (role) {
            case OWNER   -> 0x886600;
            case ADMIN   -> 0xAA0000;
            case MEMBER  -> 0x005500;
            case VISITOR -> 0x555555;
        };
    }

    private static String permLabel(PlotData.Permission perm, boolean es) {
        return switch (perm) {
            case BUILD          -> t(es, "Build",              "Construir");
            case BREAK          -> t(es, "Break",              "Romper");
            case PLACE          -> t(es, "Place",              "Colocar");
            case INTERACT       -> t(es, "Interact",           "Interactuar");
            case CONTAINERS     -> t(es, "Containers",         "Contenedores");
            case USE_BEDS       -> t(es, "Beds",               "Camas");
            case USE_CRAFTING   -> t(es, "Crafting",           "Mesa de trabajo");
            case USE_ENCHANTING -> t(es, "Enchanting",         "Encantamiento");
            case USE_ANVIL      -> t(es, "Anvil",              "Yunque");
            case USE_FURNACE    -> t(es, "Furnace",            "Horno");
            case USE_BREWING    -> t(es, "Brewing",            "Pociones");
            case ATTACK_MOBS    -> t(es, "Attack Mobs",        "Atacar mobs");
            case ATTACK_ANIMALS -> t(es, "Attack Animals",     "Atacar animales");
            case PVP            -> t(es, "PvP",                "PvP");
            case RIDE_ENTITIES  -> t(es, "Ride Entities",      "Montar entidades");
            case INTERACT_MOBS  -> t(es, "Interact Mobs",      "Interactuar mobs");
            case LEASH_MOBS     -> t(es, "Leash Mobs",         "Atar mobs");
            case SHEAR_MOBS     -> t(es, "Shear",              "Esquilar");
            case MILK_MOBS      -> t(es, "Milk",               "Ordeñar");
            case CROP_TRAMPLING -> t(es, "Crop Trampling",     "Pisotear cultivos");
            case PICKUP_ITEMS   -> t(es, "Pick Up Items",      "Recoger ítems");
            case DROP_ITEMS     -> t(es, "Drop Items",         "Tirar ítems");
            case BREAK_CROPS    -> t(es, "Break Crops",        "Romper cultivos");
            case PLANT_SEEDS    -> t(es, "Plant Seeds",        "Plantar semillas");
            case USE_BONEMEAL   -> t(es, "Bonemeal",           "Hueso molido");
            case BREAK_DECOR    -> t(es, "Break Decor",        "Romper decoración");
            case DETONATE_TNT   -> t(es, "Detonate TNT",       "Detonar TNT");
            case GRIEFING       -> t(es, "Griefing",           "Griefing");
            case TP             -> t(es, "Teleport",           "Teletransporte");
            case FLY            -> t(es, "Fly",                "Volar");
            case ENTER          -> t(es, "Enter",              "Entrar");
            case CHAT           -> t(es, "Chat",               "Chat");
            case COMMAND_USE    -> t(es, "Commands",           "Comandos");
            case MANAGE_MEMBERS -> t(es, "Manage Members",     "Gestionar miembros");
            case MANAGE_PERMS   -> t(es, "Manage Permissions", "Gestionar permisos");
            case MANAGE_FLAGS   -> t(es, "Manage Flags",       "Gestionar flags");
            case MANAGE_GROUPS  -> t(es, "Manage Groups",      "Gestionar grupos");
        };
    }

    private static String permDesc(PlotData.Permission perm, boolean es) {
        return switch (perm) {
            case BUILD          -> t(es, "Place and break blocks",       "Colocar y romper bloques");
            case BREAK          -> t(es, "Break blocks only",            "Solo romper bloques");
            case PLACE          -> t(es, "Place blocks only",            "Solo colocar bloques");
            case INTERACT       -> t(es, "Levers, doors, buttons",       "Palancas, puertas, botones");
            case CONTAINERS     -> t(es, "Open chests and inventories",  "Abrir cofres e inventarios");
            case USE_BEDS       -> t(es, "Use beds to sleep",            "Usar camas para dormir");
            case USE_CRAFTING   -> t(es, "Use crafting tables",          "Usar mesas de trabajo");
            case USE_ENCHANTING -> t(es, "Use enchanting tables",        "Usar mesas de encantamiento");
            case USE_ANVIL      -> t(es, "Use anvils",                   "Usar yunques");
            case USE_FURNACE    -> t(es, "Furnaces and smokers",         "Hornos y ahumadores");
            case USE_BREWING    -> t(es, "Brewing stands",               "Soportes de pociones");
            case ATTACK_MOBS    -> t(es, "Attack hostile mobs",          "Atacar mobs hostiles");
            case ATTACK_ANIMALS -> t(es, "Attack passive animals",       "Atacar animales pasivos");
            case PVP            -> t(es, "Attack other players",         "Atacar otros jugadores");
            case RIDE_ENTITIES  -> t(es, "Ride horses, boats, etc.",     "Montar caballos, botes, etc.");
            case INTERACT_MOBS  -> t(es, "Trade, name mobs",             "Comerciar, nombrar mobs");
            case LEASH_MOBS     -> t(es, "Leash and unleash mobs",       "Atar y soltar mobs");
            case SHEAR_MOBS     -> t(es, "Shear sheep",                  "Esquilar ovejas");
            case MILK_MOBS      -> t(es, "Milk cows and goats",          "Ordeñar vacas y cabras");
            case CROP_TRAMPLING -> t(es, "Trample and destroy crops",    "Pisotear y destruir cultivos");
            case PICKUP_ITEMS   -> t(es, "Pick up items from ground",    "Recoger ítems del suelo");
            case DROP_ITEMS     -> t(es, "Drop items on ground",         "Tirar ítems al suelo");
            case BREAK_CROPS    -> t(es, "Break plants and crops",       "Romper plantas y cultivos");
            case PLANT_SEEDS    -> t(es, "Plant seeds and saplings",     "Plantar semillas y árboles");
            case USE_BONEMEAL   -> t(es, "Bonemeal on plants",           "Hueso molido en plantas");
            case BREAK_DECOR    -> t(es, "Break flowers and decor",      "Romper flores y decoración");
            case DETONATE_TNT   -> t(es, "Light and detonate TNT",       "Encender y detonar TNT");
            case GRIEFING       -> t(es, "Creeper/wither/etc. damage",   "Daño de Creeper/Wither/etc.");
            case TP             -> t(es, "Use /sp tp to come here",      "Usar /sp tp para llegar aquí");
            case FLY            -> t(es, "Fly inside the plot",          "Volar dentro de la parcela");
            case ENTER          -> t(es, "Enter the plot area",          "Entrar al área de la parcela");
            case CHAT           -> t(es, "Chat while in the plot",       "Chatear dentro de la parcela");
            case COMMAND_USE    -> t(es, "Use commands in the plot",     "Usar comandos en la parcela");
            case MANAGE_MEMBERS -> t(es, "Add and remove members",       "Agregar y quitar miembros");
            case MANAGE_PERMS   -> t(es, "Change member permissions",    "Cambiar permisos de miembros");
            case MANAGE_FLAGS   -> t(es, "Change global flags",          "Cambiar flags globales");
            case MANAGE_GROUPS  -> t(es, "Create and edit groups",       "Crear y editar grupos");
        };
    }

    protected void clearAndInit() { clearChildren(); init(); }
    @Override public boolean shouldPause() { return false; }
}
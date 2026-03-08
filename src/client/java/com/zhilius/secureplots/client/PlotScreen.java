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

import java.util.*;

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

    // If non-null, we're showing the permission sub-screen for this member
    private UUID selectedMember = null;

    private static final int PW = 300;
    private static final int PH = 230;

    public PlotScreen(BlockPos plotPos, PlotData data) {
        super(Text.literal("Parcela"));
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

        // If showing member permissions sub-screen, only add back button + perm toggles
        if (selectedMember != null) {
            initPermissionsSubScreen(px, py);
            return;
        }

        int tw = PW / 3;
        addDrawableChild(ButtonWidget.builder(Text.literal("Info"), b -> { activeTab = TAB_INFO; selectedMember = null; clearAndInit(); })
                .dimensions(px,        py - 20, tw - 1, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Miembros"), b -> { activeTab = TAB_MEMBERS; selectedMember = null; clearAndInit(); })
                .dimensions(px + tw,   py - 20, tw - 1, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Mejorar"), b -> { activeTab = TAB_UPGRADE; selectedMember = null; clearAndInit(); })
                .dimensions(px + tw*2, py - 20, tw,     20).build());

        int cy = py + 28;

        if (activeTab == TAB_INFO)    initInfoTab(px, cy);
        if (activeTab == TAB_MEMBERS) initMembersTab(px, cy);
        if (activeTab == TAB_UPGRADE) initUpgradeTab(px, cy);
    }

    // ── PERMISSIONS SUB-SCREEN ───────────────────────────────────────────────
    private void initPermissionsSubScreen(int px, int py) {
        boolean canManage = myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;
        // Back button
        addDrawableChild(ButtonWidget.builder(Text.literal("← Volver"), b -> {
            selectedMember = null; activeTab = TAB_MEMBERS; clearAndInit();
        }).dimensions(px, py - 20, 80, 20).build());

        if (!canManage) return;

        int cy = py + 50;
        int btnW = PW - 30;

        for (PlotData.Permission perm : PlotData.Permission.values()) {
            boolean has = data.hasPermission(selectedMember, perm);
            String label = (has ? "§a✔ " : "§c✗ ") + permLabel(perm) + " §7— " + permDesc(perm);
            final PlotData.Permission p = perm;
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                boolean current = data.hasPermission(selectedMember, p);
                ClientPlayNetworking.send(new ModPackets.SetPermissionPayload(
                    plotPos, selectedMember.toString(), p.name(), !current));
            }).dimensions(px + 12, cy, btnW, 18).build());
            cy += 22;
        }

        // Remove member button
        cy += 8;
        String memberName = data.getMemberName(selectedMember);
        addDrawableChild(ButtonWidget.builder(
            Text.literal("§c🗑 Eliminar a " + memberName + " de la plot"), b ->
                ClientPlayNetworking.send(new ModPackets.RemoveMemberPayload(plotPos, memberName))
        ).dimensions(px + 12, cy, btnW, 18).build());
    }

    // ── INFO ─────────────────────────────────────────────────────────────────
    private void initInfoTab(int px, int cy) {
        if (myRole == PlotData.Role.OWNER) {
            nameField = new TextFieldWidget(textRenderer, px + 72, cy + 2, PW - 150, 16, Text.empty());
            nameField.setText(data.getPlotName());
            nameField.setMaxLength(32);
            addDrawableChild(nameField);

            addDrawableChild(ButtonWidget.builder(Text.literal("Guardar"), b -> {
                data.setPlotName(nameField.getText());
                ClientPlayNetworking.send(new ModPackets.UpdatePlotPayload(plotPos, data.toNbt()));
                assert this.client != null;
                this.client.player.sendMessage(Text.literal("✓ Guardado").formatted(Formatting.GREEN), true);
            }).dimensions(px + PW - 74, cy + 1, 66, 18).build());
        }
    }

    // ── MIEMBROS ─────────────────────────────────────────────────────────────
    private void initMembersTab(int px, int cy) {
        boolean canManage = myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;
        if (canManage) {
            addPlayerField = new TextFieldWidget(textRenderer, px + 8, cy + 16, PW - 100, 16, Text.empty());
            addPlayerField.setPlaceholder(Text.literal("Jugador (online)...").formatted(Formatting.DARK_GRAY));
            addDrawableChild(addPlayerField);

            addDrawableChild(ButtonWidget.builder(Text.literal("+ Agregar"), b -> {
                String name = addPlayerField.getText().trim();
                if (!name.isEmpty()) {
                    ClientPlayNetworking.send(new ModPackets.AddMemberPayload(plotPos, name));
                    addPlayerField.setText("");
                }
            }).dimensions(px + PW - 88, cy + 15, 84, 18).build());
        }

        int sepY = cy + (canManage ? 40 : 4);
        List<Map.Entry<UUID, PlotData.Role>> members = new ArrayList<>(data.getMembers().entrySet());
        int rowY = sepY + 20;

        for (Map.Entry<UUID, PlotData.Role> entry : members) {
            if (rowY + 18 > py() + PH - 14) break;
            UUID uuid = entry.getKey();
            String memberName = data.getMemberName(uuid);

            // Click row → open permissions sub-screen
            if (canManage) {
                addDrawableChild(ButtonWidget.builder(
                    Text.literal("⚙ " + memberName), b -> {
                        selectedMember = uuid;
                        clearAndInit();
                    }).dimensions(px + 8, rowY - 2, PW - 40, 16).build());
            }

            // Quick remove (✕) button
            if (canManage) {
                addDrawableChild(ButtonWidget.builder(Text.literal("✕"), b ->
                    ClientPlayNetworking.send(new ModPackets.RemoveMemberPayload(plotPos, memberName)))
                    .dimensions(px + PW - 26, rowY - 2, 16, 14).build());
            }
            rowY += 18;
        }
    }

    // ── MEJORAR ──────────────────────────────────────────────────────────────
    private void initUpgradeTab(int px, int cy) {
        if (myRole != PlotData.Role.OWNER) return;
        PlotSize next = data.getSize().next();
        if (next == null) return;
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Mejorar a " + next.displayName + "  ▶"),
                b -> ClientPlayNetworking.send(new ModPackets.UpgradePlotPayload(plotPos)))
                .dimensions(px + 14, py() + PH - 32, PW - 28, 20).build());
    }

    // ── RENDER ───────────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);

        int px = px(), py = py();

        ctx.fill(px - 1, py - 1, px + PW + 1, py + PH + 1, 0xFF373737);
        ctx.fill(px, py, px + PW, py + PH, 0xFFC6C6C6);
        ctx.fill(px + 1, py + 1, px + PW, py + 2,        0xFF8B8B8B);
        ctx.fill(px + 1, py + 1, px + 2,  py + PH,       0xFF8B8B8B);
        ctx.fill(px + 1, py + PH - 2, px + PW - 1, py + PH - 1, 0xFFFFFFFF);
        ctx.fill(px + PW - 2, py + 1,  px + PW - 1, py + PH - 1, 0xFFFFFFFF);

        ctx.fill(px, py, px + PW, py + 24, 0xFF555555);
        ctx.fill(px, py, px + PW, py + 23, 0xFF666666);

        // Title
        String title = selectedMember != null
            ? "⚙ Permisos de " + data.getMemberName(selectedMember)
            : "🛡 " + data.getPlotName();
        ctx.drawTextWithShadow(textRenderer,
                Text.literal(title).formatted(Formatting.YELLOW),
                px + 6, py + 7, 0xFFFFFF);

        ctx.fill(px + 4, py + 24, px + PW - 4, py + 25, 0xFF8B8B8B);

        // Tab highlight (only when not in sub-screen)
        if (selectedMember == null) {
            int tw = PW / 3;
            int tabX = px + activeTab * tw;
            ctx.fill(tabX, py - 20, tabX + tw - (activeTab == 2 ? 0 : 1), py, 0x55FFFFFF);
        }

        int cy = py + 28;
        int x  = px + 8;

        if (selectedMember != null) {
            renderPermissions(ctx, x, cy);
        } else {
            if (activeTab == TAB_INFO)    renderInfo(ctx, x, cy);
            if (activeTab == TAB_MEMBERS) renderMembers(ctx, x, cy);
            if (activeTab == TAB_UPGRADE) renderUpgrade(ctx, x, cy);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    // ── Render Permissions ───────────────────────────────────────────────────
    private void renderPermissions(DrawContext ctx, int x, int y) {
        if (selectedMember == null) return;
        String name = data.getMemberName(selectedMember);
        PlotData.Role role = data.getRoleOf(selectedMember);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal(name + " §8[" + role.name().toLowerCase() + "]"), x, y + 4, roleRgb(role));
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("Activá o desactivá permisos individuales:").formatted(Formatting.DARK_GRAY),
            x, y + 18, 0x555555);
    }

    // ── Render Info ──────────────────────────────────────────────────────────
    private void renderInfo(DrawContext ctx, int x, int y) {
        assert this.client != null;
        long time = this.client.world != null ? this.client.world.getTime() : 0;

        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Nombre:").formatted(Formatting.DARK_GRAY), x, y + 6, 0x000000);
        if (myRole != PlotData.Role.OWNER) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal(data.getPlotName()).formatted(Formatting.BLACK), x + 66, y + 6, 0x111111);
        }

        int row = y + 26; int gap = 16;

        row(ctx, x, row, "Dueño",    data.getOwnerName(),               0x333333, 0x000000); row += gap;
        row(ctx, x, row, "Nivel",    data.getSize().displayName,        0x333333, 0x006688); row += gap;
        int sz = data.getSize().radius;
        row(ctx, x, row, "Tamaño",   sz + "x" + sz + " bloques",       0x333333, 0x006688); row += gap;
        row(ctx, x, row, "Tu rol",   myRole.name(),                     0x333333, roleRgb(myRole)); row += gap;

        if (!data.hasRank()) {
            long days = data.getDaysRemaining(time);
            int col = days < 5 ? 0xAA0000 : days < 10 ? 0xAA7700 : 0x007700;
            row(ctx, x, row, "Expira en", days + " días", 0x333333, col);
        } else {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("⚡ Permanente").formatted(Formatting.DARK_GREEN), x, row, 0x005500);
        }
        row += gap;

        BlockPos c = data.getCenter();
        ctx.drawTextWithShadow(textRenderer,
                Text.literal(c.getX() + ", " + c.getY() + ", " + c.getZ()), x, row, 0x555555);
    }

    // ── Render Miembros ──────────────────────────────────────────────────────
    private void renderMembers(DrawContext ctx, int x, int y) {
        boolean canManage = myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;
        if (canManage) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("Agregar miembro:"), x, y + 2, 0x333333);
        }

        int sepY = y + (canManage ? 36 : 4);
        ctx.fill(x - 2, sepY, x + PW - 14, sepY + 1, 0xFF8B8B8B);
        ctx.fill(x - 2, sepY + 1, x + PW - 14, sepY + 2, 0xFFFFFFFF);

        int memberCount = data.getMembers().size();
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Miembros (" + memberCount + ")  §8— clic para ver permisos"), x, sepY + 4, 0x333333);

        if (data.getMembers().isEmpty()) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("Ninguno todavía."), x, sepY + 22, 0x777777);
        }
    }

    // ── Render Mejorar ───────────────────────────────────────────────────────
    private void renderUpgrade(DrawContext ctx, int x, int y) {
        PlotSize cur = data.getSize();
        PlotSize next = cur.next();

        int row = y; int gap = 16;

        row(ctx, x, row, "Nivel actual", cur.displayName + " (" + cur.radius + "x" + cur.radius + ")",
                0x333333, 0x006688); row += gap;

        if (next == null) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("⭐ ¡Máximo nivel alcanzado!"), x, row, 0x886600);
            return;
        }

        row(ctx, x, row, "Siguiente", next.displayName + " (" + next.radius + "x" + next.radius + ")",
                0x333333, 0x005500); row += gap + 4;

        ctx.fill(x - 2, row, x + PW - 14, row + 1, 0xFF8B8B8B);
        ctx.fill(x - 2, row + 1, x + PW - 14, row + 2, 0xFFFFFFFF);
        row += 8;

        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        SecurePlotsConfig.UpgradeCost cost = cfg != null ? cfg.getUpgradeCost(cur.tier) : null;

        if (cost != null) {
            ctx.drawTextWithShadow(textRenderer, Text.literal("Materiales:"), x, row, 0x333333);
            row += gap;
            if (cost.cobblecoins > 0) {
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal("  • " + cost.cobblecoins + " Cobblecoins"), x, row, 0x000000);
                row += gap;
            }
            for (SecurePlotsConfig.UpgradeCost.ItemCost item : cost.items) {
                String raw = item.itemId.contains(":") ? item.itemId.split(":")[1] : item.itemId;
                String name = Character.toUpperCase(raw.charAt(0)) + raw.substring(1).replace("_", " ");
                ctx.drawTextWithShadow(textRenderer,
                        Text.literal("  • " + item.amount + "x " + name), x, row, 0x000000);
                row += gap;
            }
        }

        if (myRole != PlotData.Role.OWNER) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("Solo el dueño puede mejorar."), x, py() + PH - 40, 0xAA0000);
        }
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

    private static String permLabel(PlotData.Permission perm) {
        return switch (perm) {
            case BUILD          -> "Construir";
            case INTERACT       -> "Interactuar";
            case CONTAINERS     -> "Abrir cofres";
            case PVP            -> "PvP";
            case MANAGE_MEMBERS -> "Gestionar Miembros";
            case MANAGE_PERMS   -> "Gestionar Permisos";
            case MANAGE_FLAGS   -> "Gestionar Flags";
            case MANAGE_GROUPS  -> "Gestionar Grupos";
            case TP             -> "Teleportar";
            case FLY            -> "Volar";
            case ENTER          -> "Entrar";
        };
    }

    private static String permDesc(PlotData.Permission perm) {
        return switch (perm) {
            case BUILD          -> "Colocar y romper bloques";
            case INTERACT       -> "Usar palancas, puertas, etc.";
            case CONTAINERS     -> "Abrir cofres e inventarios";
            case PVP            -> "Atacar jugadores en la plot";
            case MANAGE_MEMBERS -> "Agregar y remover miembros";
            case MANAGE_PERMS   -> "Cambiar permisos de miembros";
            case MANAGE_FLAGS   -> "Cambiar flags globales";
            case MANAGE_GROUPS  -> "Crear y editar grupos";
            case TP             -> "Usar /sp tp para llegar aquí";
            case FLY            -> "Volar dentro de la parcela";
            case ENTER          -> "Entrar al área de la parcela";
        };
    }

    protected void clearAndInit() { clearChildren(); init(); }
    @Override public boolean shouldPause() { return false; }
}

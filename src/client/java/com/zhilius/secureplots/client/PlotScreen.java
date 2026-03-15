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
        super(Text.translatable("sp.screen.title"));
        this.plotPos = plotPos;
        this.data    = data;
    }

    private static BorderConfig cfg() { BorderConfig c = PlotBorderRendererConfig.current; return c != null ? c : BorderConfig.createDefault(); }
    private int PW() { return cfg().screenPanelWidth; }
    private int PH() { return cfg().screenPanelHeight; }
    private int px() { return (this.width  - PW()) / 2; }
    private int py() { return (this.height - PH()) / 2; }

    // ── Init ─────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        assert this.client != null;
        myRole = data.getRoleOf(this.client.player.getUuid());
        int px = px(), py = py();

        if (selectedMember != null) {
            initPermissionsSubScreen(px, py);
            return;
        }

        int tw = PW() / 5;
        addDrawableChild(ButtonWidget.builder(Text.translatable("sp.screen.tab.info"),     b -> { activeTab = TAB_INFO;     selectedMember = null; clearAndInit(); }).dimensions(px,          py - 20, tw - 1, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("sp.screen.tab.members"),  b -> { activeTab = TAB_MEMBERS;  selectedMember = null; clearAndInit(); }).dimensions(px + tw,     py - 20, tw - 1, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("sp.screen.tab.upgrade"),  b -> { activeTab = TAB_UPGRADE;  selectedMember = null; clearAndInit(); }).dimensions(px + tw * 2, py - 20, tw - 1, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("sp.screen.tab.ambient"),  b -> { activeTab = TAB_AMBIENT;  selectedMember = null; clearAndInit(); }).dimensions(px + tw * 3, py - 20, tw - 1, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("sp.screen.tab.creative"), b -> { activeTab = TAB_CREATIVE; selectedMember = null; clearAndInit(); }).dimensions(px + tw * 4, py - 20, tw,     20).build());

        int cy = py + 28;
        if (activeTab == TAB_INFO)     initInfoTab(px, cy);
        if (activeTab == TAB_MEMBERS)  initMembersTab(px, cy);
        if (activeTab == TAB_UPGRADE)  initUpgradeTab(px, cy);
        if (activeTab == TAB_AMBIENT)  initAmbientTab(px, cy);
        if (activeTab == TAB_CREATIVE) initCreativeTab(px, cy);
    }

    // ── PERMISSIONS SUB-SCREEN ───────────────────────────────────────────────
    private void initPermissionsSubScreen(int px, int py) {
        boolean canManage = myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;
        addDrawableChild(ButtonWidget.builder(Text.translatable("sp.screen.back"), b -> {
            selectedMember = null; activeTab = TAB_MEMBERS; clearAndInit();
        }).dimensions(px, py - 20, 80, 20).build());

        if (!canManage) return;

        int cy = py + 50;
        int btnW = PW() - 30;

        for (PlotData.Permission perm : PlotData.Permission.values()) {
            boolean has = data.hasPermission(selectedMember, perm);
            String label = (has ? "\u00a7a\u2714 " : "\u00a7c\u2718 ")
                + Text.translatable("sp.perm.label." + perm.name().toLowerCase()).getString()
                + " \u00a77\u2014 "
                + Text.translatable("sp.perm.desc." + perm.name().toLowerCase()).getString();
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
            Text.translatable("sp.screen.remove_from_plot", memberName), b ->
                ClientPlayNetworking.send(new ModPackets.RemoveMemberPayload(plotPos, memberName))
        ).dimensions(px + 12, cy, btnW, 18).build());
    }

    // ── INFO ─────────────────────────────────────────────────────────────────
    private void initInfoTab(int px, int cy) {
        if (myRole == PlotData.Role.OWNER) {
            nameField = new TextFieldWidget(textRenderer, px + 72, cy + 2, PW() - 150, 16, Text.empty());
            nameField.setText(data.getPlotName());
            nameField.setMaxLength(cfg().screenMaxNameLength);
            addDrawableChild(nameField);

            addDrawableChild(ButtonWidget.builder(Text.translatable("sp.screen.save"), b -> {
                data.setPlotName(nameField.getText());
                ClientPlayNetworking.send(new ModPackets.UpdatePlotPayload(plotPos, data.toNbt()));
                assert this.client != null;
                this.client.player.sendMessage(Text.translatable("sp.screen.saved").formatted(Formatting.GREEN), true);
            }).dimensions(px + PW() - 74, cy + 1, 66, 18).build());
        }
    }

    // ── MEMBERS ──────────────────────────────────────────────────────────────
    private void initMembersTab(int px, int cy) {
        boolean canManage = myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;
        if (canManage) {
            addPlayerField = new TextFieldWidget(textRenderer, px + 8, cy + 16, PW() - 100, 16, Text.empty());
            addPlayerField.setPlaceholder(Text.translatable("sp.screen.player_placeholder").formatted(Formatting.DARK_GRAY));
            addDrawableChild(addPlayerField);

            addDrawableChild(ButtonWidget.builder(Text.translatable("sp.screen.add_btn"), b -> {
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
    private void initAmbientTab(int px, int cy) {
        boolean canEdit = myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;
        if (!canEdit) return;

        int gap = 28;

        String pCurrent = data.getParticleEffect();
        String pLabel = "\u2728 " + Text.translatable("sp.screen.set_particles").getString()
            + "  \u00a78(" + (pCurrent.isEmpty()
                ? Text.translatable("sp.screen.none").getString()
                : pCurrent) + ")";
        addDrawableChild(ButtonWidget.builder(Text.literal(pLabel), b -> {
            assert this.client != null;
            this.client.setScreen(new net.minecraft.client.gui.screen.ChatScreen("/sp plot particle "));
        }).dimensions(px + 10, cy, PW() - 70, 20).build());
        if (!pCurrent.isEmpty()) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("sp.screen.clear_btn"), b -> {
                data.setParticleEffect("");
                ClientPlayNetworking.send(new ModPackets.UpdatePlotPayload(plotPos, data.toNbt()));
                clearAndInit();
            }).dimensions(px + PW() - 56, cy, 46, 20).build());
        }
        cy += gap;

        String mCurrent = data.getMusicSound();
        String mLabel = "\uD83C\uDFB5 " + Text.translatable("sp.screen.set_music").getString()
            + "  \u00a78(" + (mCurrent.isEmpty()
                ? Text.translatable("sp.screen.none").getString()
                : mCurrent) + ")";
        addDrawableChild(ButtonWidget.builder(Text.literal(mLabel), b -> {
            assert this.client != null;
            this.client.setScreen(new net.minecraft.client.gui.screen.ChatScreen("/sp plot music "));
        }).dimensions(px + 10, cy, PW() - 70, 20).build());
        if (!mCurrent.isEmpty()) {
            addDrawableChild(ButtonWidget.builder(Text.translatable("sp.screen.clear_btn"), b -> {
                data.setMusicSound("");
                ClientPlayNetworking.send(new ModPackets.UpdatePlotPayload(plotPos, data.toNbt()));
                clearAndInit();
            }).dimensions(px + PW() - 56, cy, 46, 20).build());
        }
    }

    // ── CREATIVE ─────────────────────────────────────────────────────────────
    private void initCreativeTab(int px, int cy) {
        String[] tierNames  = { "Bronze", "Gold", "Emerald", "Diamond", "Netherite" };
        String[] tierColors = { "\u00a76", "\u00a7e", "\u00a7a", "\u00a7b", "\u00a77" };
        int btnW = PW() - 28;
        String blockLabel = Text.translatable("sp.screen.plot_block").getString();
        for (int i = 0; i < tierNames.length; i++) {
            final int tier = i;
            String label = tierColors[i] + "\u2B1B " + blockLabel + " \u2014 " + tierNames[i];
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b ->
                ClientPlayNetworking.send(new ModPackets.GiveBlockPayload(tier))
            ).dimensions(px + 12, cy + i * 24, btnW, 20).build());
        }
    }

    // ── UPGRADE ──────────────────────────────────────────────────────────────
    private void initUpgradeTab(int px, int cy) {
        if (myRole != PlotData.Role.OWNER) return;
        PlotSize next = data.getSize().next();
        if (next == null) return;
        addDrawableChild(ButtonWidget.builder(
                Text.translatable("sp.screen.upgrade_btn", next.getDisplayName()),
                b -> ClientPlayNetworking.send(new ModPackets.UpgradePlotPayload(plotPos)))
                .dimensions(px + 14, py() + PH() - 32, PW() - 28, 20).build());
    }

    // ── RENDER ───────────────────────────────────────────────────────────────
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx, mouseX, mouseY, delta);

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
            ? "\u2699 " + Text.translatable("sp.screen.permissions_title").getString() + data.getMemberName(selectedMember)
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
            renderPermissions(ctx, x, cy);
        } else {
            if (activeTab == TAB_INFO)     renderInfo(ctx, x, cy);
            if (activeTab == TAB_MEMBERS)  renderMembers(ctx, x, cy);
            if (activeTab == TAB_UPGRADE)  renderUpgrade(ctx, x, cy);
            if (activeTab == TAB_AMBIENT)  renderAmbient(ctx, x, cy);
            if (activeTab == TAB_CREATIVE) renderCreative(ctx, x, cy);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    // ── Render Permissions ───────────────────────────────────────────────────
    private void renderPermissions(DrawContext ctx, int x, int y) {
        if (selectedMember == null) return;
        String name = data.getMemberName(selectedMember);
        PlotData.Role role = data.getRoleOf(selectedMember);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal(name + " \u00a78[" + role.name().toLowerCase() + "]"), x, y + 4, roleRgb(role));
        ctx.drawTextWithShadow(textRenderer,
            Text.translatable("sp.screen.toggle_perms").formatted(Formatting.DARK_GRAY),
            x, y + 18, 0x555555);
    }

    // ── Render Info ──────────────────────────────────────────────────────────
    private void renderInfo(DrawContext ctx, int x, int y) {
        assert this.client != null;
        long time = this.client.world != null ? this.client.world.getTime() : 0;

        ctx.drawTextWithShadow(textRenderer,
            Text.translatable("sp.screen.name_label").formatted(Formatting.DARK_GRAY), x, y + 6, 0x000000);
        if (myRole != PlotData.Role.OWNER) {
            ctx.drawTextWithShadow(textRenderer,
                Text.literal(data.getPlotName()).formatted(Formatting.BLACK), x + 66, y + 6, 0x111111);
        }

        int row = y + 26; int gap = cfg().screenRowSpacing;

        row(ctx, x, row, Text.translatable("sp.screen.owner").getString(),     data.getOwnerName(),             0x333333, 0x000000); row += gap;
        row(ctx, x, row, Text.translatable("sp.screen.tier").getString(),      data.getSize().getDisplayName(), 0x333333, 0x006688); row += gap;
        int sz = data.getSize().getRadius();
        row(ctx, x, row, Text.translatable("sp.screen.size").getString(),      sz + "x" + sz + " " + Text.translatable("sp.screen.blocks").getString(), 0x333333, 0x006688); row += gap;
        row(ctx, x, row, Text.translatable("sp.screen.your_role").getString(), myRole.name(), 0x333333, roleRgb(myRole)); row += gap;

        if (!data.hasRank()) {
            long days = data.getDaysRemaining(time);
            int col = days < 5 ? 0xAA0000 : days < 10 ? 0xAA7700 : 0x007700;
            row(ctx, x, row, Text.translatable("sp.screen.expires_in").getString(),
                days + " " + Text.translatable("sp.screen.days").getString(), 0x333333, col);
        } else {
            ctx.drawTextWithShadow(textRenderer,
                Text.literal("\u26A1 ").append(Text.translatable("sp.screen.permanent")).formatted(Formatting.DARK_GREEN), x, row, 0x005500);
        }
        row += gap;

        BlockPos c = data.getCenter();
        ctx.drawTextWithShadow(textRenderer,
            Text.literal(c.getX() + ", " + c.getY() + ", " + c.getZ()), x, row, 0x555555);
    }

    // ── Render Members ───────────────────────────────────────────────────────
    private void renderMembers(DrawContext ctx, int x, int y) {
        boolean canManage = myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;
        if (canManage) {
            ctx.drawTextWithShadow(textRenderer,
                Text.translatable("sp.screen.add_member_label"), x, y + 2, 0x333333);
        }

        int sepY = y + (canManage ? 36 : 4);
        ctx.fill(x - 2, sepY, x + PW() - 14, sepY + 1, 0xFF8B8B8B);
        ctx.fill(x - 2, sepY + 1, x + PW() - 14, sepY + 2, 0xFFFFFFFF);

        int memberCount = data.getMembers().size();
        ctx.drawTextWithShadow(textRenderer,
            Text.translatable("sp.screen.members_header", memberCount),
            x, sepY + 4, 0x333333);

        if (data.getMembers().isEmpty()) {
            ctx.drawTextWithShadow(textRenderer,
                Text.translatable("sp.screen.no_members"), x, sepY + 22, 0x777777);
        }
    }

    // ── Render Upgrade ───────────────────────────────────────────────────────
    private void renderUpgrade(DrawContext ctx, int x, int y) {
        PlotSize cur = data.getSize();
        PlotSize next = cur.next();

        int row = y; int gap = cfg().screenRowSpacing;

        row(ctx, x, row, Text.translatable("sp.screen.current_tier").getString(),
            cur.getDisplayName() + " (" + cur.getRadius() + "x" + cur.getRadius() + ")",
            0x333333, 0x006688); row += gap;

        if (next == null) {
            ctx.drawTextWithShadow(textRenderer,
                Text.literal("\u2B50 ").append(Text.translatable("sp.screen.max_tier")), x, row, 0x886600);
            return;
        }

        row(ctx, x, row, Text.translatable("sp.screen.next_tier").getString(),
            next.getDisplayName() + " (" + next.getRadius() + "x" + next.getRadius() + ")",
            0x333333, 0x005500); row += gap + 4;

        ctx.fill(x - 2, row, x + PW() - 14, row + 1, 0xFF8B8B8B);
        ctx.fill(x - 2, row + 1, x + PW() - 14, row + 2, 0xFFFFFFFF);
        row += 8;

        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        SecurePlotsConfig.UpgradeCost cost = cfg != null ? cfg.getUpgradeCost(cur.tier) : null;

        if (cost != null) {
            ctx.drawTextWithShadow(textRenderer, Text.translatable("sp.screen.materials"), x, row, 0x333333);
            row += gap;            for (SecurePlotsConfig.UpgradeCost.ItemCost item : cost.items) {
                String raw = item.itemId.contains(":") ? item.itemId.split(":")[1] : item.itemId;
                String name = Character.toUpperCase(raw.charAt(0)) + raw.substring(1).replace("_", " ");
                ctx.drawTextWithShadow(textRenderer,
                    Text.literal("  \u2022 " + item.amount + "x " + name), x, row, 0x000000);
                row += gap;
            }
        }

        if (myRole != PlotData.Role.OWNER) {
            ctx.drawTextWithShadow(textRenderer,
                Text.translatable("sp.screen.only_owner_upgrade"), x, py() + PH() - 40, 0xAA0000);
        }
    }

    // ── Render Ambient ───────────────────────────────────────────────────────
    private void renderAmbient(DrawContext ctx, int x, int y) {
        boolean canEdit = myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;
        int gap = 28;

        y += gap * 2 + 16;
        ctx.drawTextWithShadow(textRenderer, Text.translatable("sp.screen.ambient_hint1"), x, y, 0x555555);
        y += 12;
        ctx.drawTextWithShadow(textRenderer, Text.translatable("sp.screen.ambient_hint2"), x, y, 0x555555);

        if (!canEdit) {
            ctx.drawTextWithShadow(textRenderer,
                Text.translatable("sp.screen.only_owner_ambient").formatted(Formatting.RED),
                x, y + 24, 0xAA0000);
        }
    }

    // ── Render Creative ──────────────────────────────────────────────────────
    private void renderCreative(DrawContext ctx, int x, int y) {
        ctx.drawTextWithShadow(textRenderer,
            Text.translatable("sp.screen.get_blocks").formatted(Formatting.DARK_GRAY), x, y - 14, 0x555555);
        ctx.drawTextWithShadow(textRenderer,
            Text.translatable("sp.screen.creative_only").formatted(Formatting.DARK_GRAY), x, y - 4, 0x888888);
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

    protected void clearAndInit() { clearChildren(); init(); }
    @Override public boolean shouldPause() { return false; }
}

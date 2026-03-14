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
    private static final int TAB_AMBIENT = 3;
    private static final int TAB_CREATIVE = 4;
    private int activeTab = TAB_INFO;

    private TextFieldWidget nameField;
    private TextFieldWidget addPlayerField;
    private TextFieldWidget particleField;
    private TextFieldWidget musicField;

    private UUID selectedMember = null;

    private static final int PW = 320;
    private static final int PH = 240;

    public PlotScreen(BlockPos plotPos, PlotData data) {
        super(Text.literal("Plot"));
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

        if (selectedMember != null) {
            initPermissionsSubScreen(px, py);
            return;
        }

        int tw = PW / 5;
        addDrawableChild(ButtonWidget.builder(Text.literal("Info"), b -> { activeTab = TAB_INFO; selectedMember = null; clearAndInit(); })
                .dimensions(px,           py - 20, tw - 1, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Members"), b -> { activeTab = TAB_MEMBERS; selectedMember = null; clearAndInit(); })
                .dimensions(px + tw,      py - 20, tw - 1, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Upgrade"), b -> { activeTab = TAB_UPGRADE; selectedMember = null; clearAndInit(); })
                .dimensions(px + tw * 2,  py - 20, tw - 1, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Ambient"), b -> { activeTab = TAB_AMBIENT; selectedMember = null; clearAndInit(); })
                .dimensions(px + tw * 3,  py - 20, tw - 1, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Creative"), b -> { activeTab = TAB_CREATIVE; selectedMember = null; clearAndInit(); })
                .dimensions(px + tw * 4,  py - 20, tw,     20).build());

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
        addDrawableChild(ButtonWidget.builder(Text.literal("\u2190 Back"), b -> {
            selectedMember = null; activeTab = TAB_MEMBERS; clearAndInit();
        }).dimensions(px, py - 20, 80, 20).build());

        if (!canManage) return;

        int cy = py + 50;
        int btnW = PW - 30;

        for (PlotData.Permission perm : PlotData.Permission.values()) {
            boolean has = data.hasPermission(selectedMember, perm);
            String label = (has ? "\u00a7a\u2714 " : "\u00a7c\u2718 ") + permLabel(perm) + " \u00a77\u2014 " + permDesc(perm);
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
            Text.literal("\u00a7c\uD83D\uDDD1 Remove " + memberName + " from plot"), b ->
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

            addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> {
                data.setPlotName(nameField.getText());
                ClientPlayNetworking.send(new ModPackets.UpdatePlotPayload(plotPos, data.toNbt()));
                assert this.client != null;
                this.client.player.sendMessage(Text.literal("\u2713 Saved").formatted(Formatting.GREEN), true);
            }).dimensions(px + PW - 74, cy + 1, 66, 18).build());
        }
    }

    // ── MEMBERS ──────────────────────────────────────────────────────────────
    private void initMembersTab(int px, int cy) {
        boolean canManage = myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;
        if (canManage) {
            addPlayerField = new TextFieldWidget(textRenderer, px + 8, cy + 16, PW - 100, 16, Text.empty());
            addPlayerField.setPlaceholder(Text.literal("Player (online)...").formatted(Formatting.DARK_GRAY));
            addDrawableChild(addPlayerField);

            addDrawableChild(ButtonWidget.builder(Text.literal("+ Add"), b -> {
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

            if (canManage) {
                addDrawableChild(ButtonWidget.builder(
                    Text.literal("\u2699 " + memberName), b -> {
                        selectedMember = uuid;
                        clearAndInit();
                    }).dimensions(px + 8, rowY - 2, PW - 40, 16).build());
            }

            if (canManage) {
                addDrawableChild(ButtonWidget.builder(Text.literal("\u2715"), b ->
                    ClientPlayNetworking.send(new ModPackets.RemoveMemberPayload(plotPos, memberName)))
                    .dimensions(px + PW - 26, rowY - 2, 16, 14).build());
            }
            rowY += 18;
        }
    }

    // ── AMBIENT ──────────────────────────────────────────────────────────────
    private void initAmbientTab(int px, int cy) {
        boolean canEdit = myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;
        if (!canEdit) return;

        int labelW = 60;
        int fieldW = PW - labelW - 78;
        int btnW   = 56;
        int gap    = 26;

        // Particles
        particleField = new TextFieldWidget(textRenderer, px + labelW + 4, cy, fieldW, 16, Text.empty());
        particleField.setText(data.getParticleEffect());
        particleField.setMaxLength(128);
        particleField.setPlaceholder(Text.literal("minecraft:happy_villager").formatted(Formatting.DARK_GRAY));
        addDrawableChild(particleField);
        addDrawableChild(ButtonWidget.builder(Text.literal("Set"), b -> {
            data.setParticleEffect(particleField.getText().trim());
            ClientPlayNetworking.send(new ModPackets.UpdatePlotPayload(plotPos, data.toNbt()));
            assert this.client != null;
            this.client.player.sendMessage(Text.literal("\u2713 Particles saved").formatted(Formatting.GREEN), true);
        }).dimensions(px + labelW + 4 + fieldW + 4, cy, btnW, 16).build());
        cy += gap;

        // Music
        musicField = new TextFieldWidget(textRenderer, px + labelW + 4, cy, fieldW, 16, Text.empty());
        musicField.setText(data.getMusicSound());
        musicField.setMaxLength(128);
        musicField.setPlaceholder(Text.literal("minecraft:music.game").formatted(Formatting.DARK_GRAY));
        addDrawableChild(musicField);
        addDrawableChild(ButtonWidget.builder(Text.literal("Set"), b -> {
            data.setMusicSound(musicField.getText().trim());
            ClientPlayNetworking.send(new ModPackets.UpdatePlotPayload(plotPos, data.toNbt()));
            assert this.client != null;
            this.client.player.sendMessage(Text.literal("\u2713 Music saved").formatted(Formatting.GREEN), true);
        }).dimensions(px + labelW + 4 + fieldW + 4, cy, btnW, 16).build());
        cy += gap;

        // Weather buttons
        int wBtnW = (PW - 20) / 4 - 2;
        String[] weatherLabels = { "Clear", "Rain", "Thunder", "None" };
        String[] weatherValues = { "CLEAR", "RAIN",  "THUNDER", "" };
        for (int i = 0; i < weatherLabels.length; i++) {
            final String wv = weatherValues[i];
            boolean active = data.getWeatherType().equalsIgnoreCase(wv);
            String lbl = (active ? "\u00a7a" : "\u00a77") + weatherLabels[i];
            addDrawableChild(ButtonWidget.builder(Text.literal(lbl), b -> {
                data.setWeatherType(wv);
                ClientPlayNetworking.send(new ModPackets.UpdatePlotPayload(plotPos, data.toNbt()));
                clearAndInit();
            }).dimensions(px + 10 + i * (wBtnW + 2), cy, wBtnW, 16).build());
        }
        cy += gap;

        // Time buttons
        String[] timeLabels = { "Day", "Noon", "Dusk", "Night", "None" };
        long[]   timeValues = { 1000L, 6000L, 12500L, 18000L, -1L };
        int tBtnW = (PW - 20) / 5 - 2;
        for (int i = 0; i < timeLabels.length; i++) {
            final long tv = timeValues[i];
            boolean active = data.getPlotTime() == tv;
            String lbl = (active ? "\u00a7a" : "\u00a77") + timeLabels[i];
            addDrawableChild(ButtonWidget.builder(Text.literal(lbl), b -> {
                data.setPlotTime(tv);
                ClientPlayNetworking.send(new ModPackets.UpdatePlotPayload(plotPos, data.toNbt()));
                clearAndInit();
            }).dimensions(px + 10 + i * (tBtnW + 2), cy, tBtnW, 16).build());
        }
        cy += gap;

        // Clear All
        addDrawableChild(ButtonWidget.builder(Text.literal("\u00a7cClear All Ambient"), b -> {
            data.setParticleEffect("");
            data.setMusicSound("");
            data.setWeatherType("");
            data.setPlotTime(-1L);
            ClientPlayNetworking.send(new ModPackets.UpdatePlotPayload(plotPos, data.toNbt()));
            clearAndInit();
        }).dimensions(px + 10, cy, PW - 20, 16).build());
    }

    // ── CREATIVE ─────────────────────────────────────────────────────────────
    private void initCreativeTab(int px, int cy) {
        String[] tierNames  = { "Bronze", "Gold", "Emerald", "Diamond", "Netherite" };
        String[] tierColors = { "\u00a76", "\u00a7e", "\u00a7a", "\u00a7b", "\u00a77" };
        int btnW = PW - 28;
        for (int i = 0; i < tierNames.length; i++) {
            final int tier = i;
            String label = tierColors[i] + "\u2B1B Plot Block \u2014 " + tierNames[i];
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
                Text.literal("Upgrade to " + next.getDisplayName() + "  \u25B6"),
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

        String title = selectedMember != null
            ? "\u2699 Permissions: " + data.getMemberName(selectedMember)
            : "\uD83D\uDEE1 " + data.getPlotName();
        ctx.drawTextWithShadow(textRenderer,
                Text.literal(title).formatted(Formatting.YELLOW),
                px + 6, py + 7, 0xFFFFFF);

        ctx.fill(px + 4, py + 24, px + PW - 4, py + 25, 0xFF8B8B8B);

        if (selectedMember == null) {
            int tw = PW / 5;
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
            Text.literal("Toggle individual permissions:").formatted(Formatting.DARK_GRAY),
            x, y + 18, 0x555555);
    }

    // ── Render Info ──────────────────────────────────────────────────────────
    private void renderInfo(DrawContext ctx, int x, int y) {
        assert this.client != null;
        long time = this.client.world != null ? this.client.world.getTime() : 0;

        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Name:").formatted(Formatting.DARK_GRAY), x, y + 6, 0x000000);
        if (myRole != PlotData.Role.OWNER) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal(data.getPlotName()).formatted(Formatting.BLACK), x + 66, y + 6, 0x111111);
        }

        int row = y + 26; int gap = 16;

        row(ctx, x, row, "Owner",      data.getOwnerName(),                 0x333333, 0x000000); row += gap;
        row(ctx, x, row, "Tier",       data.getSize().getDisplayName(),     0x333333, 0x006688); row += gap;
        int sz = data.getSize().getRadius();
        row(ctx, x, row, "Size",       sz + "x" + sz + " blocks",          0x333333, 0x006688); row += gap;
        row(ctx, x, row, "Your role",  myRole.name(),                       0x333333, roleRgb(myRole)); row += gap;

        if (!data.hasRank()) {
            long days = data.getDaysRemaining(time);
            int col = days < 5 ? 0xAA0000 : days < 10 ? 0xAA7700 : 0x007700;
            row(ctx, x, row, "Expires in", days + " days", 0x333333, col);
        } else {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("\u26A1 Permanent").formatted(Formatting.DARK_GREEN), x, row, 0x005500);
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
                    Text.literal("Add member:"), x, y + 2, 0x333333);
        }

        int sepY = y + (canManage ? 36 : 4);
        ctx.fill(x - 2, sepY, x + PW - 14, sepY + 1, 0xFF8B8B8B);
        ctx.fill(x - 2, sepY + 1, x + PW - 14, sepY + 2, 0xFFFFFFFF);

        int memberCount = data.getMembers().size();
        ctx.drawTextWithShadow(textRenderer,
                Text.literal("Members (" + memberCount + ")  \u00a78\u2014 click to view permissions"), x, sepY + 4, 0x333333);

        if (data.getMembers().isEmpty()) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("None yet."), x, sepY + 22, 0x777777);
        }
    }

    // ── Render Upgrade ───────────────────────────────────────────────────────
    private void renderUpgrade(DrawContext ctx, int x, int y) {
        PlotSize cur = data.getSize();
        PlotSize next = cur.next();

        int row = y; int gap = 16;

        row(ctx, x, row, "Current tier", cur.getDisplayName() + " (" + cur.getRadius() + "x" + cur.getRadius() + ")",
                0x333333, 0x006688); row += gap;

        if (next == null) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("\u2B50 Maximum tier reached!"), x, row, 0x886600);
            return;
        }

        row(ctx, x, row, "Next tier", next.getDisplayName() + " (" + next.getRadius() + "x" + next.getRadius() + ")",
                0x333333, 0x005500); row += gap + 4;

        ctx.fill(x - 2, row, x + PW - 14, row + 1, 0xFF8B8B8B);
        ctx.fill(x - 2, row + 1, x + PW - 14, row + 2, 0xFFFFFFFF);
        row += 8;

        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        SecurePlotsConfig.UpgradeCost cost = cfg != null ? cfg.getUpgradeCost(cur.tier) : null;

        if (cost != null) {
            ctx.drawTextWithShadow(textRenderer, Text.literal("Materials:"), x, row, 0x333333);
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
                    Text.literal("Only the owner can upgrade."), x, py() + PH - 40, 0xAA0000);
        }
    }

    // ── Render Ambient ───────────────────────────────────────────────────────
    private void renderAmbient(DrawContext ctx, int x, int y) {
        boolean canEdit = myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;
        int gap = 26;
        int labelW = 60;

        ctx.drawTextWithShadow(textRenderer, Text.literal("Particles:").formatted(Formatting.DARK_GRAY), x, y + 4, 0x333333);
        y += gap;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Music:").formatted(Formatting.DARK_GRAY), x, y + 4, 0x333333);
        y += gap;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Weather:").formatted(Formatting.DARK_GRAY), x, y + 4, 0x333333);
        y += gap;
        ctx.drawTextWithShadow(textRenderer, Text.literal("Time:").formatted(Formatting.DARK_GRAY), x, y + 4, 0x333333);
        y += gap;

        if (!canEdit) {
            ctx.drawTextWithShadow(textRenderer,
                    Text.literal("Only the owner or admin can edit these.").formatted(Formatting.RED),
                    x, y + 4, 0xAA0000);
        }
    }

    // ── Render Creative ──────────────────────────────────────────────────────
    private void renderCreative(DrawContext ctx, int x, int y) {
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("Get plot blocks:").formatted(Formatting.DARK_GRAY),
            x, y - 14, 0x555555);
        ctx.drawTextWithShadow(textRenderer,
            Text.literal("(OPs / creative mode only)").formatted(Formatting.DARK_GRAY),
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

    private static String permLabel(PlotData.Permission perm) {
        return switch (perm) {
            case BUILD          -> "Build";
            case BREAK          -> "Break";
            case PLACE          -> "Place";
            case INTERACT       -> "Interact";
            case CONTAINERS     -> "Containers";
            case USE_BEDS       -> "Beds";
            case USE_CRAFTING   -> "Crafting";
            case USE_ENCHANTING -> "Enchanting";
            case USE_ANVIL      -> "Anvil";
            case USE_FURNACE    -> "Furnace";
            case USE_BREWING    -> "Brewing";
            case ATTACK_MOBS    -> "Attack Mobs";
            case ATTACK_ANIMALS -> "Attack Animals";
            case PVP            -> "PvP";
            case RIDE_ENTITIES  -> "Ride Entities";
            case INTERACT_MOBS  -> "Interact Mobs";
            case LEASH_MOBS     -> "Leash Mobs";
            case SHEAR_MOBS     -> "Shear";
            case MILK_MOBS      -> "Milk";
            case CROP_TRAMPLING -> "Crop Trampling";
            case PICKUP_ITEMS   -> "Pick Up Items";
            case DROP_ITEMS     -> "Drop Items";
            case BREAK_CROPS    -> "Break Crops";
            case PLANT_SEEDS    -> "Plant Seeds";
            case USE_BONEMEAL   -> "Bonemeal";
            case BREAK_DECOR    -> "Break Decor";
            case DETONATE_TNT   -> "Detonate TNT";
            case GRIEFING       -> "Griefing";
            case TP             -> "Teleport";
            case FLY            -> "Fly";
            case ENTER          -> "Enter";
            case CHAT           -> "Chat";
            case COMMAND_USE    -> "Commands";
            case MANAGE_MEMBERS -> "Manage Members";
            case MANAGE_PERMS   -> "Manage Permissions";
            case MANAGE_FLAGS   -> "Manage Flags";
            case MANAGE_GROUPS  -> "Manage Groups";
        };
    }

    private static String permDesc(PlotData.Permission perm) {
        return switch (perm) {
            case BUILD          -> "Place and break blocks";
            case BREAK          -> "Break blocks only";
            case PLACE          -> "Place blocks only";
            case INTERACT       -> "Levers, doors, buttons";
            case CONTAINERS     -> "Open chests and inventories";
            case USE_BEDS       -> "Use beds to sleep";
            case USE_CRAFTING   -> "Use crafting tables";
            case USE_ENCHANTING -> "Use enchanting tables";
            case USE_ANVIL      -> "Use anvils";
            case USE_FURNACE    -> "Furnaces and smokers";
            case USE_BREWING    -> "Brewing stands";
            case ATTACK_MOBS    -> "Attack hostile mobs";
            case ATTACK_ANIMALS -> "Attack passive animals";
            case PVP            -> "Attack other players";
            case RIDE_ENTITIES  -> "Ride horses, boats, etc.";
            case INTERACT_MOBS  -> "Trade, name mobs";
            case LEASH_MOBS     -> "Leash and unleash mobs";
            case SHEAR_MOBS     -> "Shear sheep";
            case MILK_MOBS      -> "Milk cows and goats";
            case CROP_TRAMPLING -> "Trample and destroy crops";
            case PICKUP_ITEMS   -> "Pick up items from ground";
            case DROP_ITEMS     -> "Drop items on ground";
            case BREAK_CROPS    -> "Break plants and crops";
            case PLANT_SEEDS    -> "Plant seeds and saplings";
            case USE_BONEMEAL   -> "Bonemeal on plants";
            case BREAK_DECOR    -> "Break flowers and decor";
            case DETONATE_TNT   -> "Light and detonate TNT";
            case GRIEFING       -> "Creeper/wither/etc. damage";
            case TP             -> "Use /sp tp to come here";
            case FLY            -> "Fly inside the plot";
            case ENTER          -> "Enter the plot area";
            case CHAT           -> "Chat while in the plot";
            case COMMAND_USE    -> "Use commands in the plot";
            case MANAGE_MEMBERS -> "Add and remove members";
            case MANAGE_PERMS   -> "Change member permissions";
            case MANAGE_FLAGS   -> "Change global flags";
            case MANAGE_GROUPS  -> "Create and edit groups";
        };
    }

    protected void clearAndInit() { clearChildren(); init(); }
    @Override public boolean shouldPause() { return false; }
}

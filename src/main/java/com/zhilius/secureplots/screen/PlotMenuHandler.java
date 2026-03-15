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
package com.zhilius.secureplots.screen;

import com.mojang.authlib.GameProfile;
import com.zhilius.secureplots.config.SecurePlotsConfig;
import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotManager;
import com.zhilius.secureplots.plot.PlotSize;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;

import java.util.*;

public class PlotMenuHandler extends GenericContainerScreenHandler {

    public enum MenuPage { INFO, MEMBERS, GLOBAL_PERMS, UPGRADE, AMBIENT }

    /** Actions that open a sign input screen. */
    public enum PendingAction { RENAME, ADD_MEMBER, CREATE_GROUP, SET_ENTER_MESSAGE, SET_EXIT_MESSAGE }

    private final BlockPos plotPos;
    private PlotData data;
    private final PlotData.Role myRole;
    private final ServerPlayerEntity player;
    private MenuPage page;
    private final SimpleInventory menuInv;

    private UUID viewingMemberUuid = null;
    private String viewingGroupName = null;
    private int permPage = 0;

    private static final int PERMS_PER_PAGE = 21;
    private static final int ROWS           = 6;
    private static final int SIZE           = ROWS * 9;

    private static final int SLOT_TAB_INFO         = 0;
    private static final int SLOT_TAB_MEMBERS      = 1;
    private static final int SLOT_TAB_GLOBAL_PERMS = 2;
    private static final int SLOT_TAB_UPGRADE      = 3;
    private static final int SLOT_TAB_AMBIENT      = 4;
    private static final int SLOT_CLOSE            = 8;
    private static final int SLOT_UPGRADE_BTN      = 49;

    // Content slots used across multiple pages
    private static final int[] CONTENT_SLOTS = {19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43};

    public PlotMenuHandler(int syncId, PlayerInventory playerInv, BlockPos plotPos, PlotData data, MenuPage page) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, new SimpleInventory(SIZE), ROWS);
        this.plotPos = plotPos;
        this.data    = data;
        this.page    = page;
        this.menuInv = (SimpleInventory) this.slots.get(0).inventory;
        this.player  = (ServerPlayerEntity) playerInv.player;

        boolean isAdmin = player.getCommandTags().contains(
            SecurePlotsConfig.INSTANCE != null ? SecurePlotsConfig.INSTANCE.adminTag : "plot_admin");
        this.myRole = isAdmin ? PlotData.Role.OWNER : data.getRoleOf(player.getUuid());

        buildMenu();
        playSound(SoundEvents.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
    }

    // ── Build ─────────────────────────────────────────────────────────────────

    public void buildMenu() {
        for (int i = 0; i < SIZE; i++) menuInv.setStack(i, ItemStack.EMPTY);
        fillBorder();
        buildTabs();
        switch (page) {
            case INFO         -> buildInfoPage();
            case MEMBERS      -> {
                if (viewingMemberUuid != null)   buildMemberPermsPage(viewingMemberUuid);
                else if (viewingGroupName != null) buildGroupPage(viewingGroupName);
                else                               buildMembersPage();
            }
            case GLOBAL_PERMS -> {
                if (viewingGroupName != null) buildGroupPage(viewingGroupName);
                else                          buildGlobalPermsPage();
            }
            case UPGRADE      -> buildUpgradePage();
            case AMBIENT      -> buildAmbientPage();
        }
        menuInv.setStack(SLOT_CLOSE, named(Items.BARRIER, "§c✕ Close"));
    }

    private void fillBorder() {
        ItemStack black = named(Items.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack gray  = named(Items.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 9; i++) menuInv.setStack(i, black.copy());
        for (int s : new int[]{9,17,18,26,27,35,36,44,45,46,47,48,50,51,52,53})
            menuInv.setStack(s, gray.copy());
    }

    private void buildTabs() {
        record Tab(int slot, MenuPage target, String icon, String label, String desc) {}
        Tab[] tabs = {
            new Tab(SLOT_TAB_INFO,         MenuPage.INFO,         "§e📋", "Info",         "View plot information"),
            new Tab(SLOT_TAB_MEMBERS,      MenuPage.MEMBERS,      "§e👥", "Members",      "Manage members and permissions"),
            new Tab(SLOT_TAB_GLOBAL_PERMS, MenuPage.GLOBAL_PERMS, "§e🌐", "Global Perms", "Global permissions and groups"),
            new Tab(SLOT_TAB_UPGRADE,      MenuPage.UPGRADE,      "§e⬆",  "Upgrade",      "Increase protection tier"),
            new Tab(SLOT_TAB_AMBIENT,      MenuPage.AMBIENT,      "§e✨", "Ambient",      "Particles, music, weather, time"),
        };
        for (Tab t : tabs) {
            var item = page == t.target ? Items.LIME_STAINED_GLASS_PANE : Items.WHITE_STAINED_GLASS_PANE;
            menuInv.setStack(t.slot, namedLore(item, t.icon + " " + t.label, "§7" + t.desc));
        }
    }

    private void buildInfoPage() {
        List<PlotData> owned = getOwnedPlots();
        int plotIndex = owned.indexOf(data) + 1;
        PlotSize size = data.getSize();

        menuInv.setStack(19, namedLore(Items.NAME_TAG,
            "§6" + data.getPlotName(), "§7ID: §f#" + plotIndex));
        menuInv.setStack(20, makePlayerHead(data.getOwnerId(), data.getOwnerName(),
            "§eOwner", "§f" + data.getOwnerName()));
        menuInv.setStack(21, namedLore(itemForSize(size),
            tierColor(size) + "Tier: " + size.getDisplayName(),
            "§7Size: §b" + size.getRadius() + "x" + size.getRadius() + " blocks"));
        menuInv.setStack(22, namedLore(Items.PAPER,
            "§eMembers",
            "§f" + data.getMembers().size() + " §7member(s)",
            "§f" + data.getGroups().size() + " §7group(s)"));

        BlockPos c = data.getCenter();
        menuInv.setStack(23, namedLore(Items.COMPASS,
            "§eLocation",
            "§7X: §f" + c.getX() + "  §7Y: §f" + c.getY() + "  §7Z: §f" + c.getZ()));
        menuInv.setStack(24, namedLore(Items.SHIELD,
            "§eYour role", roleColor(myRole) + myRole.name()));

        if (myRole == PlotData.Role.OWNER) {
            menuInv.setStack(29, namedLore(Items.ANVIL,
                "§6✏ Rename plot", "§7Click to change the name"));
        }

        boolean tpEnabled = data.hasFlag(PlotData.Flag.ALLOW_TP);
        if (tpEnabled || myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN) {
            menuInv.setStack(31, namedLore(Items.ENDER_PEARL,
                "§b✈ Teleport",
                tpEnabled ? "§7Public TP enabled" : "§7Admins/owner only",
                "§eClick to TP to this plot"));
        }

        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg != null && cfg.inactivityExpiry.enabled
                && player.getWorld() instanceof ServerWorld sw) {
            long daysInactive = data.getDaysInactive(sw.getTime());
            long maxDays = cfg.inactivityExpiry.baseDays + ((long) cfg.inactivityExpiry.daysPerTier * size.tier);
            long daysLeft = Math.max(0, maxDays - daysInactive);
            String color = daysLeft > 7 ? "§a" : daysLeft > 0 ? "§e" : "§c";
            menuInv.setStack(33, namedLore(Items.CLOCK,
                "§eOwner inactivity",
                "§7Days inactive: §f" + daysInactive,
                "§7Max days: §f" + maxDays,
                daysLeft > 0 ? color + daysLeft + " §7days until expiry" : "§c⚠ Protection expired!"));
        }
    }

    private void buildMembersPage() {
        boolean canManage = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_MEMBERS);
        if (canManage) {
            menuInv.setStack(10, namedLore(Items.EMERALD,
                "§a+ Add member", "§7Click to add a player", "§8Player must be online"));
        }

        List<PlotData.PermissionGroup> groups = data.getGroups();
        List<Map.Entry<UUID, PlotData.Role>> members = new ArrayList<>(data.getMembers().entrySet());
        int idx = 0;
        for (Map.Entry<UUID, PlotData.Role> entry : members) {
            if (idx >= CONTENT_SLOTS.length) break;
            String name = data.getMemberName(entry.getKey());
            PlotData.Role role = entry.getValue();
            List<String> lore = new ArrayList<>();
            lore.add(roleColor(role) + role.name());
            for (PlotData.PermissionGroup g : groups)
                if (g.members.contains(entry.getKey())) lore.add("§d[" + g.name + "]");
            if (canManage) { lore.add("§eClick: edit permissions"); lore.add("§cShift+Click: remove"); }
            menuInv.setStack(CONTENT_SLOTS[idx++], makePlayerHead(entry.getKey(), name,
                "§f" + name, lore.toArray(new String[0])));
        }

        if (members.isEmpty()) {
            menuInv.setStack(22, namedLore(Items.BARRIER,
                "§7No members yet", "§8Use the green button to add"));
        }
    }

    private void buildMemberPermsPage(UUID uuid) {
        String name = data.getMemberName(uuid);
        PlotData.Role role = data.getRoleOf(uuid);
        boolean canEdit = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_PERMS);

        menuInv.setStack(10, makePlayerHead(uuid, name,
            "§f" + name, roleColor(role) + role.name(), "§7Editing permissions"));
        menuInv.setStack(12, namedLore(Items.ARROW, "§7← Back", "§8Click to go back"));

        buildPermPageNav(PlotData.Permission.values().length, 14, 16);
        buildPermSlots(CONTENT_SLOTS, data.getPermsOf(uuid)::contains, canEdit);

        if (canEdit) {
            menuInv.setStack(16, namedLore(Items.EXPERIENCE_BOTTLE,
                "§eRole: " + roleColor(role) + role.name(),
                "§7Click to change role",
                "§8Cycles: MEMBER → ADMIN → MEMBER"));
        }

        boolean canManageGroups = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS);
        List<PlotData.PermissionGroup> allGroups = data.getGroups();
        int[] groupSlots = {37,38,39,40,41,42,43};
        for (int gi = 0; gi < allGroups.size() && gi < groupSlots.length; gi++) {
            PlotData.PermissionGroup g = allGroups.get(gi);
            boolean inGroup = g.members.contains(uuid);
            menuInv.setStack(groupSlots[gi], namedLore(
                inGroup ? Items.PURPLE_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE,
                (inGroup ? "§d✔ " : "§8✗ ") + "Group: §d" + g.name,
                inGroup ? "§7Member of this group" : "§8Not in this group",
                canManageGroups ? (inGroup ? "§cClick to remove from group" : "§aClick to add to group") : "§8No group permissions"
            ));
        }
    }

    private void buildGroupPage(String groupName) {
        PlotData.PermissionGroup group = data.getGroup(groupName);
        if (group == null) { viewingGroupName = null; buildGlobalPermsPage(); return; }

        boolean canEdit = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS);

        menuInv.setStack(10, namedLore(Items.WRITABLE_BOOK, "§d[Group] " + group.name,
            "§7" + group.members.size() + " member(s)",
            "§7" + group.permissions.size() + " permission(s)"));
        menuInv.setStack(12, namedLore(Items.ARROW, "§7← Back", "§8Click to go back"));
        if (canEdit) menuInv.setStack(14, namedLore(Items.TNT, "§cDelete group", "§7Click to delete this group"));

        int[] permSlots = {19,20,21,22,23,24,25, 28,29,30,31,32,33,34};
        buildPermPageNav(PlotData.Permission.values().length, 15, 17);
        buildPermSlots(permSlots, group.permissions::contains, canEdit);

        int[] memberSlots = {37,38,39,40,41,42,43};
        int idx = 0;
        for (UUID uuid : group.members) {
            if (idx >= memberSlots.length) break;
            String mName = data.getMemberName(uuid);
            menuInv.setStack(memberSlots[idx++], makePlayerHead(uuid, mName,
                "§f" + mName, canEdit ? "§cClick to remove from group" : ""));
        }
    }

    private void buildGlobalPermsPage() {
        boolean canEditFlags  = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_FLAGS);
        boolean canEditGroups = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS);

        menuInv.setStack(10, namedLore(Items.ORANGE_BANNER,
            "§e🌐 Global Perms",
            "§7Affect ALL players inside the plot.",
            canEditFlags ? "§eClick each to toggle" : "§8Only owner/admin can change"));

        PlotData.Flag[] flags = PlotData.Flag.values();
        int[] flagSlots = {19,20,21,22,23,24,25};
        for (int i = 0; i < flags.length && i < flagSlots.length; i++) {
            boolean on = data.hasFlag(flags[i]);
            menuInv.setStack(flagSlots[i], namedLore(
                on ? Items.LIME_CONCRETE : Items.RED_CONCRETE,
                (on ? "§a[ON] " : "§c[OFF] ") + flagLabel(flags[i]),
                "§7" + flagDesc(flags[i]),
                canEditFlags ? (on ? "§cClick to disable" : "§aClick to enable") : "§8No permissions"
            ));
        }

        menuInv.setStack(27, namedLore(Items.PURPLE_STAINED_GLASS_PANE,
            "§d━━━ Permission Groups ━━━",
            "§7Assign permissions to multiple members at once."));

        if (canEditGroups) {
            menuInv.setStack(28, namedLore(Items.BOOKSHELF,
                "§d+ Create group",
                "§7Click to create a permission group",
                "§8Groups apply permissions in bulk"));
        }

        List<PlotData.PermissionGroup> groups = data.getGroups();
        int[] groupSlots = {29,30,31,32,33,34};
        for (int i = 0; i < groups.size() && i < groupSlots.length; i++) {
            PlotData.PermissionGroup g = groups.get(i);
            menuInv.setStack(groupSlots[i], namedLore(Items.WRITABLE_BOOK,
                "§d[G] " + g.name,
                "§7" + g.members.size() + " member(s)",
                "§7" + g.permissions.size() + " permission(s)",
                canEditGroups ? "§eClick to edit" : "§8Read only"));
        }

        if (groups.isEmpty()) {
            menuInv.setStack(31, namedLore(Items.BARRIER,
                "§7No groups yet", "§8Use the purple button to create one"));
        }
    }

    private void buildUpgradePage() {
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg != null && !cfg.enableUpgrades) {
            menuInv.setStack(22, namedLore(Items.BARRIER,
                "§c✗ Upgrades are disabled",
                "§8This feature has been disabled by the server."));
            return;
        }

        PlotSize cur  = data.getSize();
        PlotSize next = cur.next();

        menuInv.setStack(19, namedLore(itemForSize(cur),
            tierColor(cur) + "Current tier: " + cur.getDisplayName(),
            "§7Size: §b" + cur.getRadius() + "x" + cur.getRadius() + " blocks"));

        if (next == null) {
            menuInv.setStack(22, namedLore(Items.NETHER_STAR,
                "§6⭐ Maximum tier reached!", "§7Your plot is at max tier"));
            return;
        }

        menuInv.setStack(21, namedLore(itemForSize(next),
            tierColor(next) + "Next tier: " + next.getDisplayName(),
            "§7Size: §b" + next.getRadius() + "x" + next.getRadius() + " blocks"));

        SecurePlotsConfig.UpgradeCost cost = cfg != null ? cfg.getUpgradeCost(cur.tier) : null;
        boolean canAfford = true;

        if (cost != null) {
            List<String> costLore = new ArrayList<>();
            costLore.add("§eYou need:");            for (SecurePlotsConfig.UpgradeCost.ItemCost ic : cost.items) {
                var item = Registries.ITEM.get(Identifier.of(ic.itemId));
                int has = countItem(player, item);
                boolean ok = has >= ic.amount;
                if (!ok) canAfford = false;
                costLore.add((ok ? "§a✔" : "§c✗") + " §7" + formatItemId(ic.itemId)
                    + ": " + (ok ? "§a" : "§c") + has + "§7/" + ic.amount);
            }
            menuInv.setStack(23, namedLore(Items.PAPER, "§eRequired materials",
                costLore.toArray(new String[0])));
        }

        if (myRole == PlotData.Role.OWNER) {
            if (canAfford) {
                ItemStack btn = namedLore(Items.ANVIL,
                    "§a⬆ Upgrade to " + next.getDisplayName(), "§7You have all the materials");
                btn.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                menuInv.setStack(SLOT_UPGRADE_BTN, btn);
            } else {
                menuInv.setStack(SLOT_UPGRADE_BTN, namedLore(Items.ANVIL, "§c✗ Cannot upgrade yet"));
            }
        } else {
            menuInv.setStack(SLOT_UPGRADE_BTN, namedLore(Items.BARRIER, "§cOnly the owner can upgrade"));
        }
    }

    private void buildAmbientPage() {
        boolean canEdit = myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        int curParticleCount = cfg != null ? Math.max(1, Math.min(cfg.particleCount, 5)) : 3;
        float curMusicVol    = cfg != null ? Math.max(0.1f, Math.min(cfg.musicVolume, 4.0f)) : 4.0f;

        String particle = data.getParticleEffect();
        menuInv.setStack(19, namedLore(Items.FIREWORK_STAR,
            "§e✨ Particles",
            particle.isEmpty() ? "§8None" : "§f" + particle,
            canEdit ? "§7Click to set (e.g. happy_villager)" : "§8Owner/admin only",
            canEdit && !particle.isEmpty() ? "§cRight-click to clear" : ""));

        if (!particle.isEmpty()) {
            menuInv.setStack(20, namedLore(Items.RED_STAINED_GLASS_PANE,
                "§c◀ Fewer particles",
                "§7Current: §e" + curParticleCount + " §7(min 1)",
                canEdit ? "§7Click to decrease" : "§8Owner/admin only"));
            menuInv.setStack(21, namedLore(Items.PAPER,
                "§e🌟 Entry burst: §f" + curParticleCount + "§7/5",
                "§7Particles on ENTERING the plot",
                "§8Continuous: §7" + (cfg != null ? cfg.ambientParticleCount : 2) + "§8/sec (config: ambientParticleCount)",
                "§8Max burst: 5"));
            menuInv.setStack(22, namedLore(Items.LIME_STAINED_GLASS_PANE,
                "§a▶ More particles",
                "§7Current: §e" + curParticleCount + " §7(max 5)",
                canEdit ? "§7Click to increase" : "§8Owner/admin only"));
        }

        String music = data.getMusicSound();
        menuInv.setStack(23, namedLore(Items.MUSIC_DISC_CAT,
            "§e🎵 Music",
            music.isEmpty() ? "§8None" : "§f" + music,
            canEdit ? "§7Click to set (e.g. minecraft:music.game)" : "§8Owner/admin only",
            canEdit && !music.isEmpty() ? "§cRight-click to clear" : ""));

        if (!music.isEmpty()) {
            int volPct = Math.round((curMusicVol / 4.0f) * 100);
            menuInv.setStack(24, namedLore(Items.ORANGE_STAINED_GLASS_PANE,
                "§6◀ Lower volume", "§7Current: §e" + volPct + "%",
                canEdit ? "§7Click to decrease (-10%)" : "§8Owner/admin only"));
            menuInv.setStack(25, namedLore(Items.JUKEBOX,
                "§e🔊 Volume: §f" + volPct + "%", "§7Plot music volume", "§8Range: 10% – 100%"));
            menuInv.setStack(26, namedLore(Items.LIME_STAINED_GLASS_PANE,
                "§a▶ Raise volume", "§7Current: §e" + volPct + "%",
                canEdit ? "§7Click to increase (+10%)" : "§8Owner/admin only"));
        }

        String em = data.getEnterMessage();
        String xm = data.getExitMessage();
        menuInv.setStack(37, namedLore(Items.GREEN_DYE,
            "§a💬 Enter message",
            em.isEmpty() ? "§8No message" : "§f" + em,
            canEdit ? "§7Click to edit  §8(use & for colors)" : "§8Owner/admin only",
            canEdit && !em.isEmpty() ? "§cRight-click to clear" : ""));
        menuInv.setStack(38, namedLore(Items.RED_DYE,
            "§c💬 Exit message",
            xm.isEmpty() ? "§8No message" : "§f" + xm,
            canEdit ? "§7Click to edit  §8(use & for colors)" : "§8Owner/admin only",
            canEdit && !xm.isEmpty() ? "§cRight-click to clear" : ""));

        menuInv.setStack(39, namedLore(Items.BOOK,
            "§e📖 Color & Format Codes",
            "§0&0 §fBlack  §8&8 §fDark Gray",
            "§1&1 §fDark Blue  §9&9 §fBlue",
            "§2&2 §fDark Green  §a&a §fGreen",
            "§3&3 §fDark Aqua  §b&b §fAqua",
            "§4&4 §fDark Red  §c&c §fRed",
            "§5&5 §fPurple  §d&d §fPink",
            "§6&6 §fGold  §e&e §fYellow",
            "§7&7 §fGray  §f&f §fWhite",
            "§l&l Bold  §o&o Italic  §n&n Underline  §r&r Reset",
            "§8Example: §r&6Hello &cWorld!"));
    }

    // ── Click Handler ─────────────────────────────────────────────────────────

    @Override
    public void onSlotClick(int slot, int button, SlotActionType actionType, PlayerEntity actor) {
        if (slot < 0 || slot >= SIZE) return;
        ItemStack clicked = menuInv.getStack(slot);
        if (clicked.isEmpty()) return;

        // Tabs
        if (slot == SLOT_TAB_INFO)         { navigateTo(MenuPage.INFO);         return; }
        if (slot == SLOT_TAB_MEMBERS)      { navigateTo(MenuPage.MEMBERS);      return; }
        if (slot == SLOT_TAB_GLOBAL_PERMS) { navigateTo(MenuPage.GLOBAL_PERMS); return; }
        if (slot == SLOT_TAB_UPGRADE)      { navigateTo(MenuPage.UPGRADE);      return; }
        if (slot == SLOT_TAB_AMBIENT)      { navigateTo(MenuPage.AMBIENT);      return; }
        if (slot == SLOT_CLOSE)            { player.closeHandledScreen(); return; }
        if (slot == SLOT_UPGRADE_BTN && myRole == PlotData.Role.OWNER) { handleUpgrade(); return; }

        switch (page) {
            case INFO         -> handleInfoClick(slot, clicked);
            case MEMBERS      -> handleMembersClick(slot, button, actionType, clicked);
            case GLOBAL_PERMS -> handleGlobalPermsClick(slot, clicked);
            case AMBIENT      -> handleAmbientClick(slot, button, clicked);
            default           -> {}
        }
    }

    private void navigateTo(MenuPage target) {
        page = target;
        viewingMemberUuid = null;
        viewingGroupName  = null;
        refreshMenu();
    }

    private void handleInfoClick(int slot, ItemStack clicked) {
        if (clicked.getItem() == Items.ANVIL && myRole == PlotData.Role.OWNER) {
            openSignForInput(PendingAction.RENAME);
        } else if (clicked.getItem() == Items.ENDER_PEARL) {
            handleTp();
        }
    }

    private void handleMembersClick(int slot, int button, SlotActionType actionType, ItemStack clicked) {
        if (viewingMemberUuid != null) { handleMemberPermsClick(slot, button, clicked); return; }
        if (viewingGroupName  != null) { handleGroupPageClick(slot, clicked);           return; }

        boolean canManage = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_MEMBERS);
        if (clicked.getItem() == Items.ARROW)    { viewingMemberUuid = null; viewingGroupName = null; refreshMenu(); return; }
        if (clicked.getItem() == Items.EMERALD && canManage) { openSignForInput(PendingAction.ADD_MEMBER);   return; }
        if (clicked.getItem() == Items.BOOKSHELF && canManage) { openSignForInput(PendingAction.CREATE_GROUP); return; }

        if (clicked.getItem() == Items.WRITABLE_BOOK) {
            String gn = stripColor(clicked).replace("[G] ", "").replace("[Group] ", "");
            if (data.getGroup(gn) != null) { viewingGroupName = gn; refreshMenu(); }
            return;
        }

        if (clicked.getItem() == Items.PLAYER_HEAD && canManage) {
            String memberName = stripColor(clicked);
            if (memberName.isEmpty()) return;
            if (button == 1 || actionType == SlotActionType.PICKUP_ALL) {
                removeMemberByName(memberName);
            } else {
                data.getMembers().keySet().stream()
                    .filter(u -> data.getMemberName(u).equalsIgnoreCase(memberName))
                    .findFirst()
                    .ifPresent(u -> { viewingMemberUuid = u; permPage = 0; refreshMenu(); });
            }
        }
    }

    private void handleGlobalPermsClick(int slot, ItemStack clicked) {
        if (viewingGroupName != null) { handleGroupPageClick(slot, clicked); return; }

        boolean canEditFlags = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_FLAGS);
        if (canEditFlags) {
            PlotData.Flag[] flags = PlotData.Flag.values();
            int[] flagSlots = {19,20,21,22,23,24,25};
            for (int i = 0; i < flags.length && i < flagSlots.length; i++) {
                if (slot == flagSlots[i]) {
                    final PlotData.Flag flag = flags[i];
                    withFreshPlot(fresh -> {
                        fresh.setFlag(flag, !fresh.hasFlag(flag));
                        playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.2f);
                    });
                    return;
                }
            }
        }

        boolean canEditGroups = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS);
        if (clicked.getItem() == Items.BOOKSHELF && canEditGroups) { openSignForInput(PendingAction.CREATE_GROUP); return; }
        if (clicked.getItem() == Items.WRITABLE_BOOK) {
            String gn = stripColor(clicked).replace("[G] ", "");
            if (data.getGroup(gn) != null) { viewingGroupName = gn; refreshMenu(); }
        }
    }

    private void handleAmbientClick(int slot, int button, ItemStack clicked) {
        boolean canEdit = myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;
        if (!canEdit) return;
        SecurePlotsConfig cfgA = SecurePlotsConfig.INSTANCE;

        switch (slot) {
            case 19 -> withFreshPlot(fresh -> {
                if (button == 1 && !fresh.getParticleEffect().isEmpty()) {
                    fresh.setParticleEffect("");
                    playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.0f);
                } else {
                    player.closeHandledScreen();
                    ServerPlayNetworking.send(player, new ModPackets.OpenChatPayload("/sp plot particle "));
                }
            });
            case 20 -> { if (cfgA != null && !data.getParticleEffect().isEmpty()) { cfgA.particleCount = Math.max(1, cfgA.particleCount - 1); SecurePlotsConfig.save(); playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 0.9f); refreshMenu(); } }
            case 22 -> { if (cfgA != null && !data.getParticleEffect().isEmpty()) { cfgA.particleCount = Math.min(5, cfgA.particleCount + 1); SecurePlotsConfig.save(); playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.1f); refreshMenu(); } }
            case 23 -> withFreshPlot(fresh -> {
                if (button == 1 && !fresh.getMusicSound().isEmpty()) {
                    fresh.setMusicSound("");
                    playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.0f);
                } else {
                    player.closeHandledScreen();
                    ServerPlayNetworking.send(player, new ModPackets.OpenChatPayload("/sp plot music "));
                }
            });
            case 24 -> { if (cfgA != null && !data.getMusicSound().isEmpty()) { cfgA.musicVolume = Math.max(0.1f, Math.round((cfgA.musicVolume - 0.4f) * 10f) / 10f); SecurePlotsConfig.save(); playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 0.9f); refreshMenu(); } }
            case 26 -> { if (cfgA != null && !data.getMusicSound().isEmpty()) { cfgA.musicVolume = Math.min(4.0f, Math.round((cfgA.musicVolume + 0.4f) * 10f) / 10f); SecurePlotsConfig.save(); playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.1f); refreshMenu(); } }
            case 37 -> { if (button == 1 && !data.getEnterMessage().isEmpty()) { withFreshPlot(f -> { f.setEnterMessage(""); playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.0f); }); } else { openSignForInput(PendingAction.SET_ENTER_MESSAGE); } }
            case 38 -> { if (button == 1 && !data.getExitMessage().isEmpty())  { withFreshPlot(f -> { f.setExitMessage("");  playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.0f); }); } else { openSignForInput(PendingAction.SET_EXIT_MESSAGE);  } }
        }
    }

    private void handleMemberPermsClick(int slot, int button, ItemStack clicked) {
        if (clicked.getItem() == Items.ARROW) { viewingMemberUuid = null; permPage = 0; refreshMenu(); return; }

        if (clicked.getItem() == Items.SPECTRAL_ARROW) {
            String label = getDisplayName(clicked);
            if (label.contains("Prev")) permPage = Math.max(0, permPage - 1); else permPage++;
            playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, label.contains("Prev") ? 0.8f : 1.2f);
            refreshMenu(); return;
        }

        boolean canEdit = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_PERMS);
        boolean canManageGroups = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS);

        if (clicked.getItem() == Items.EXPERIENCE_BOTTLE && canEdit) { cycleRole(viewingMemberUuid); return; }

        if (canManageGroups) {
            int[] groupSlots = {37,38,39,40,41,42,43};
            List<PlotData.PermissionGroup> allGroups = data.getGroups();
            for (int i = 0; i < allGroups.size() && i < groupSlots.length; i++) {
                if (slot == groupSlots[i]) {
                    final String groupName = allGroups.get(i).name;
                    withFreshPlot(fresh -> {
                        PlotData.PermissionGroup g = fresh.getGroup(groupName);
                        if (g == null) return;
                        if (g.members.contains(viewingMemberUuid)) g.members.remove(viewingMemberUuid);
                        else g.members.add(viewingMemberUuid);
                        playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.2f);
                    });
                    return;
                }
            }
        }

        if (!canEdit) return;
        PlotData.Permission[] allPerms = PlotData.Permission.values();
        int start = permPage * PERMS_PER_PAGE;
        int end   = Math.min(start + PERMS_PER_PAGE, allPerms.length);
        for (int si = 0; si < CONTENT_SLOTS.length && (start + si) < end; si++) {
            if (slot == CONTENT_SLOTS[si]) {
                final PlotData.Permission perm = allPerms[start + si];
                withFreshPlot(fresh -> {
                    fresh.setPermission(viewingMemberUuid, perm, !fresh.hasPermission(viewingMemberUuid, perm));
                    playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.2f);
                });
                return;
            }
        }
    }

    private void handleGroupPageClick(int slot, ItemStack clicked) {
        if (clicked.getItem() == Items.ARROW) { viewingGroupName = null; refreshMenu(); return; }

        boolean canEdit = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS);
        if (!canEdit) return;

        if (clicked.getItem() == Items.SPECTRAL_ARROW) {
            String s = getDisplayName(clicked);
            if (s.contains("Prev")) permPage = Math.max(0, permPage - 1); else permPage++;
            playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, s.contains("Prev") ? 0.8f : 1.2f);
            refreshMenu(); return;
        }

        if (clicked.getItem() == Items.TNT) {
            withFreshPlot(fresh -> {
                fresh.removeGroup(viewingGroupName);
                viewingGroupName = null;
                playSound(SoundEvents.ENTITY_ITEM_BREAK, 1f, 0.8f);
            });
            return;
        }

        int[] permSlots = {19,20,21,22,23,24,25, 28,29,30,31,32,33,34};
        PlotData.Permission[] perms = PlotData.Permission.values();
        int gStart = permPage * PERMS_PER_PAGE;
        for (int si = 0; si < permSlots.length; si++) {
            int pi = gStart + si;
            if (pi >= perms.length) break;
            if (slot == permSlots[si]) {
                final PlotData.Permission perm = perms[pi];
                withFreshPlot(fresh -> {
                    PlotData.PermissionGroup g = fresh.getGroup(viewingGroupName);
                    if (g == null) return;
                    if (g.permissions.contains(perm)) g.permissions.remove(perm); else g.permissions.add(perm);
                    playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.2f);
                });
                return;
            }
        }

        if (clicked.getItem() == Items.PLAYER_HEAD) {
            String mName = stripColor(clicked);
            withFreshPlot(fresh -> {
                PlotData.PermissionGroup g = fresh.getGroup(viewingGroupName);
                if (g != null) g.members.removeIf(u -> fresh.getMemberName(u).equalsIgnoreCase(mName));
            });
        }
    }

    // ── TP ────────────────────────────────────────────────────────────────────

    private void handleTp() {
        boolean tpEnabled = data.hasFlag(PlotData.Flag.ALLOW_TP);
        if (!tpEnabled && myRole != PlotData.Role.OWNER && myRole != PlotData.Role.ADMIN) {
            player.sendMessage(Text.translatable("sp.tp.not_allowed", data.getPlotName()), false);
            return;
        }
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        BlockPos c = data.getCenter();
        double tpY = sw.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, c).getY();
        player.closeHandledScreen();
        player.teleport(sw, c.getX() + 0.5, tpY, c.getZ() + 0.5,
            java.util.Set.of(), player.getYaw(), player.getPitch());
        player.sendMessage(Text.translatable("sp.tp.success", data.getPlotName()), false);
        sw.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
    }

    // ── Sign Input ────────────────────────────────────────────────────────────

    private void openSignForInput(PendingAction action) {
        player.closeHandledScreen();
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        sw.getServer().execute(() -> {
            switch (action) {
                case RENAME            -> SignInputManager.openForRename(player, plotPos);
                case ADD_MEMBER        -> SignInputManager.openForAddMember(player, plotPos);
                case CREATE_GROUP      -> SignInputManager.openForCreateGroup(player, plotPos);
                case SET_ENTER_MESSAGE -> SignInputManager.openForEnterMessage(player, plotPos);
                case SET_EXIT_MESSAGE  -> SignInputManager.openForExitMessage(player, plotPos);
            }
        });
    }

    // ── Upgrade ───────────────────────────────────────────────────────────────

    private void handleUpgrade() {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        PlotManager manager = PlotManager.getOrCreate(sw);
        PlotData fresh = manager.getPlot(plotPos);
        if (fresh == null) return;

        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg != null && !cfg.enableUpgrades) { playSound(SoundEvents.ENTITY_VILLAGER_NO, 1f, 1f); return; }

        PlotSize next = fresh.getSize().next();
        if (next == null) { playSound(SoundEvents.ENTITY_VILLAGER_NO, 1f, 1f); return; }

        SecurePlotsConfig.UpgradeCost cost = cfg != null ? cfg.getUpgradeCost(fresh.getSize().tier) : null;
        if (cost != null) {
            for (var ic : cost.items) {
                var item = Registries.ITEM.get(Identifier.of(ic.itemId));
                if (countItem(player, item) < ic.amount) { playSound(SoundEvents.ENTITY_VILLAGER_NO, 1f, 0.8f); refreshMenu(); return; }
            }
            for (var ic : cost.items)
                removeItem(player, Registries.ITEM.get(Identifier.of(ic.itemId)), ic.amount);
        }

        fresh.setSize(next);
        sw.setBlockState(plotPos, com.zhilius.secureplots.block.ModBlocks.fromTier(next.tier).getDefaultState());
        manager.markDirty();

        sw.playSound(null, plotPos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1f, 1f);
        sw.playSound(null, plotPos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.2f);
        spawnUpgradeParticles(sw, plotPos);

        player.closeHandledScreen();
        ModPackets.sendShowPlotBorder(player, fresh);
        player.sendMessage(Text.translatable("sp.upgrade.success",
            next.getDisplayName(), next.getRadius(), next.getRadius()), false);
    }

    private void spawnUpgradeParticles(ServerWorld sw, BlockPos pos) {
        Vec3d c = Vec3d.ofCenter(pos);
        for (int i = 0; i < 30; i++) {
            double a = i * (Math.PI * 2 / 30);
            sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                c.x + Math.cos(a) * 1.5, c.y + 0.5 + (i * 0.05), c.z + Math.sin(a) * 1.5, 1, 0, 0, 0, 0);
        }
        for (int i = 0; i < 15; i++) {
            sw.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                c.x + (Math.random() - 0.5) * 2, c.y + Math.random() * 2, c.z + (Math.random() - 0.5) * 2,
                1, 0, 0.1, 0, 0.1);
        }
        sw.spawnParticles(ParticleTypes.ENCHANT, c.x, c.y + 1, c.z, 40, 0.5, 0.5, 0.5, 0.5);
    }

    private void cycleRole(UUID uuid) {
        withFreshPlot(fresh -> {
            PlotData.Role next = fresh.getRoleOf(uuid) == PlotData.Role.MEMBER
                ? PlotData.Role.ADMIN : PlotData.Role.MEMBER;
            fresh.getMembers().put(uuid, next);
        });
    }

    private void removeMemberByName(String name) {
        withFreshPlot(fresh -> {
            fresh.getMembers().keySet().stream()
                .filter(u -> fresh.getMemberName(u).equalsIgnoreCase(name))
                .findFirst()
                .ifPresent(uuid -> {
                    fresh.removeMember(uuid);
                    playSound(SoundEvents.ENTITY_ITEM_BREAK, 1f, 0.8f);
                    player.sendMessage(Text.translatable("sp.member.removed_sender", name), false);
                });
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Loads a fresh copy of the plot, runs the action, marks dirty, updates data and refreshes the menu. */
    private void withFreshPlot(java.util.function.Consumer<PlotData> action) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        PlotManager manager = PlotManager.getOrCreate(sw);
        PlotData fresh = manager.getPlot(plotPos);
        if (fresh == null) return;
        action.accept(fresh);
        manager.markDirty();
        this.data = fresh;
        refreshMenu();
    }

    private void buildPermPageNav(int totalPerms, int prevSlot, int nextSlot) {
        int totalPages = (int) Math.ceil((double) totalPerms / PERMS_PER_PAGE);
        permPage = Math.max(0, Math.min(permPage, totalPages - 1));
        if (permPage > 0)
            menuInv.setStack(prevSlot, namedLore(Items.SPECTRAL_ARROW, "§e← Prev page", "§7Page " + permPage + "/" + totalPages));
        if (permPage < totalPages - 1)
            menuInv.setStack(nextSlot, namedLore(Items.SPECTRAL_ARROW, "§eNext page →", "§7Page " + (permPage + 2) + "/" + totalPages));
        menuInv.setStack(13, namedLore(Items.PAPER,
            "§7Page §e" + (permPage + 1) + "§7/§e" + totalPages,
            "§8" + totalPerms + " permissions total"));
    }

    private void buildPermSlots(int[] slots, java.util.function.Predicate<PlotData.Permission> hasPerm, boolean canEdit) {
        PlotData.Permission[] allPerms = PlotData.Permission.values();
        int start = permPage * PERMS_PER_PAGE;
        int end   = Math.min(start + PERMS_PER_PAGE, allPerms.length);
        int si = 0;
        for (int i = start; i < end && si < slots.length; i++, si++) {
            PlotData.Permission perm = allPerms[i];
            boolean has = hasPerm.test(perm);
            menuInv.setStack(slots[si], namedLore(
                has ? Items.LIME_DYE : Items.GRAY_DYE,
                (has ? "§a✔ " : "§c✗ ") + permLabel(perm),
                "§7" + permDesc(perm),
                canEdit ? (has ? "§cClick to disable" : "§aClick to enable") : "§8No edit permission"
            ));
        }
    }

    private void refreshMenu() { buildMenu(); sendContentUpdates(); }

    private void playSound(net.minecraft.sound.SoundEvent sound, float volume, float pitch) {
        if (player.getWorld() instanceof ServerWorld sw)
            sw.playSound(null, player.getBlockPos(), sound, SoundCategory.PLAYERS, volume, pitch);
    }

    private List<PlotData> getOwnedPlots() {
        if (!(player.getWorld() instanceof ServerWorld sw)) return List.of(data);
        return PlotManager.getOrCreate(sw).getPlayerPlots(player.getUuid());
    }

    private static String stripColor(ItemStack stack) {
        Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
        return name != null ? name.getString().replaceAll("§.", "").trim() : "";
    }

    private static String getDisplayName(ItemStack stack) {
        Text name = stack.get(DataComponentTypes.CUSTOM_NAME);
        return name != null ? name.getString() : "";
    }

    private static String formatItemId(String id) {
        String raw = id.contains(":") ? id.split(":")[1] : id;
        return Character.toUpperCase(raw.charAt(0)) + raw.substring(1).replace("_", " ");
    }

    // ── Labels ────────────────────────────────────────────────────────────────

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

    private static String flagLabel(PlotData.Flag flag) {
        return switch (flag) {
            case ALLOW_VISITOR_BUILD      -> "Visitors: Build";
            case ALLOW_VISITOR_INTERACT   -> "Visitors: Interact";
            case ALLOW_VISITOR_CONTAINERS -> "Visitors: Containers";
            case ALLOW_PVP                -> "Global PvP";
            case ALLOW_FLY                -> "Global Fly";
            case ALLOW_TP                 -> "Public TP";
            case GREETINGS                -> "Welcome Messages";
        };
    }

    private static String flagDesc(PlotData.Flag flag) {
        return switch (flag) {
            case ALLOW_VISITOR_BUILD      -> "Anyone can build here";
            case ALLOW_VISITOR_INTERACT   -> "Anyone can interact";
            case ALLOW_VISITOR_CONTAINERS -> "Anyone can open chests";
            case ALLOW_PVP                -> "PvP enabled for everyone";
            case ALLOW_FLY                -> "Everyone can fly here";
            case ALLOW_TP                 -> "Everyone can /sp tp here";
            case GREETINGS                -> "Show message on enter/exit";
        };
    }

    // ── Item Helpers ──────────────────────────────────────────────────────────

    private static ItemStack named(net.minecraft.item.Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).styled(s -> s.withItalic(false)));
        return stack;
    }

    private static ItemStack namedLore(net.minecraft.item.Item item, String name, String... loreLines) {
        ItemStack stack = named(item, name);
        List<Text> lore = new ArrayList<>();
        for (String line : loreLines)
            lore.add(Text.literal(line).styled(s -> s.withItalic(false)));
        stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return stack;
    }

    private ItemStack makePlayerHead(UUID uuid, String playerName, String displayName, String... loreLines) {
        ItemStack stack = namedLore(Items.PLAYER_HEAD, displayName, loreLines);
        try {
            ServerPlayerEntity online = player.getServer().getPlayerManager().getPlayer(uuid);
            GameProfile profile = online != null ? online.getGameProfile()
                : player.getServer().getUserCache() != null
                    ? player.getServer().getUserCache().getByUuid(uuid).orElse(new GameProfile(uuid, playerName))
                    : new GameProfile(uuid, playerName);
            stack.set(DataComponentTypes.PROFILE,
                new ProfileComponent(Optional.ofNullable(profile.getName()),
                    Optional.of(profile.getId()), profile.getProperties()));
        } catch (Exception ignored) {}
        return stack;
    }

    private static String tierColor(PlotSize size) {
        return switch (size) {
            case BRONZE    -> "§6";
            case GOLD      -> "§e";
            case EMERALD   -> "§a";
            case DIAMOND   -> "§b";
            case NETHERITE -> "§5";
        };
    }

    private static String roleColor(PlotData.Role role) {
        return switch (role) {
            case OWNER   -> "§6";
            case ADMIN   -> "§c";
            case MEMBER  -> "§a";
            case VISITOR -> "§7";
        };
    }

    private static net.minecraft.item.Item itemForSize(PlotSize size) {
        return switch (size) {
            case BRONZE    -> Items.COPPER_INGOT;
            case GOLD      -> Items.GOLD_INGOT;
            case EMERALD   -> Items.EMERALD;
            case DIAMOND   -> Items.DIAMOND;
            case NETHERITE -> Items.NETHERITE_INGOT;
        };
    }

    private static int countItem(PlayerEntity player, net.minecraft.item.Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.getItem() == item) count += s.getCount();
        }
        return count;
    }

    private static void removeItem(PlayerEntity player, net.minecraft.item.Item item, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().size() && remaining > 0; i++) {
            ItemStack s = player.getInventory().getStack(i);
            if (s.getItem() == item) {
                int take = Math.min(s.getCount(), remaining);
                s.decrement(take);
                remaining -= take;
            }
        }
    }

    @Override public boolean canUse(PlayerEntity player) { return true; }
    @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
}
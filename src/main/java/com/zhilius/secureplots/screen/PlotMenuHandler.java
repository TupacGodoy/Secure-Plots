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
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class PlotMenuHandler extends GenericContainerScreenHandler {

    public enum MenuPage { INFO, MEMBERS, GLOBAL_PERMS, UPGRADE, AMBIENT }
    public enum PendingAction { NONE, RENAME, ADD_MEMBER, CREATE_GROUP, SET_ENTER_MESSAGE, SET_EXIT_MESSAGE,
                                SET_PARTICLE, SET_MUSIC }

    private final BlockPos plotPos;
    private PlotData data;
    private PlotData.Role myRole;
    private final ServerPlayerEntity player;
    private MenuPage page;
    private final SimpleInventory menuInv;

    // Para sub-páginas de miembros (ver permisos de uno específico)
    private UUID viewingMemberUuid = null;
    // Para sub-página de grupos
    private String viewingGroupName = null;
    // Paginación de permisos de miembro
    private int permPage = 0;
    private static final int PERMS_PER_PAGE = 21;

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;

    // Tabs en fila superior
    private static final int SLOT_TAB_INFO         = 0;
    private static final int SLOT_TAB_MEMBERS      = 1;
    private static final int SLOT_TAB_GLOBAL_PERMS = 2;
    private static final int SLOT_TAB_UPGRADE      = 3;
    private static final int SLOT_TAB_AMBIENT      = 4;
    private static final int SLOT_CLOSE       = 8;
    private static final int SLOT_UPGRADE_BTN = 49;

    public PlotMenuHandler(int syncId, PlayerInventory playerInv, BlockPos plotPos, PlotData data, MenuPage page) {
        this(syncId, playerInv, plotPos, data, page, new SimpleInventory(SIZE));
    }

    private PlotMenuHandler(int syncId, PlayerInventory playerInv, BlockPos plotPos, PlotData data, MenuPage page, SimpleInventory inv) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, ROWS);
        this.plotPos = plotPos;
        this.data    = data;
        boolean isPlotAdmin = ((ServerPlayerEntity) playerInv.player).getCommandTags().contains(SecurePlotsConfig.INSTANCE != null ? SecurePlotsConfig.INSTANCE.adminTag : "plot_admin");
        this.myRole  = isPlotAdmin ? PlotData.Role.OWNER : data.getRoleOf(playerInv.player.getUuid());
        this.player  = (ServerPlayerEntity) playerInv.player;
        this.page    = page;
        this.menuInv = inv;
        buildMenu();
        playSound(SoundEvents.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
    }

    // ── Build ─────────────────────────────────────────────────────────────────
    public void buildMenu() {
        for (int i = 0; i < SIZE; i++) menuInv.setStack(i, ItemStack.EMPTY);
        fillBorder();
        buildTabs();
        switch (page) {
            case INFO    -> buildInfoPage();
            case MEMBERS -> {
                if (viewingMemberUuid != null) buildMemberPermsPage(viewingMemberUuid);
                else if (viewingGroupName != null) buildGroupPage(viewingGroupName);
                else buildMembersPage();
            }
            case GLOBAL_PERMS -> {
                if (viewingGroupName != null) buildGroupPage(viewingGroupName);
                else buildGlobalPermsPage();
            }
            case UPGRADE -> buildUpgradePage();
            case AMBIENT -> buildAmbientPage();
        }
        menuInv.setStack(SLOT_CLOSE, named(Items.BARRIER, "§c✕ Close"));
    }

    private void fillBorder() {
        for (int i = 0; i < 9; i++) menuInv.setStack(i, named(Items.BLACK_STAINED_GLASS_PANE, " "));
        int[] sides = {9,17, 18,26, 27,35, 36,44, 45,46,47,48,50,51,52,53};
        ItemStack gray = named(Items.GRAY_STAINED_GLASS_PANE, " ");
        for (int s : sides) menuInv.setStack(s, gray.copy());
    }

    private void buildTabs() {
        menuInv.setStack(SLOT_TAB_INFO,
            namedLore(page == MenuPage.INFO ? Items.LIME_STAINED_GLASS_PANE : Items.WHITE_STAINED_GLASS_PANE,
                "§e📋 Info", "§7View plot information"));
        menuInv.setStack(SLOT_TAB_MEMBERS,
            namedLore(page == MenuPage.MEMBERS ? Items.LIME_STAINED_GLASS_PANE : Items.WHITE_STAINED_GLASS_PANE,
                "§e👥 Members", "§7Manage members and permissions"));
        menuInv.setStack(SLOT_TAB_GLOBAL_PERMS,
            namedLore(page == MenuPage.GLOBAL_PERMS ? Items.LIME_STAINED_GLASS_PANE : Items.WHITE_STAINED_GLASS_PANE,
                "§e🌐 Global Perms", "§7Global permissions and groups"));
        menuInv.setStack(SLOT_TAB_UPGRADE,
            namedLore(page == MenuPage.UPGRADE ? Items.LIME_STAINED_GLASS_PANE : Items.WHITE_STAINED_GLASS_PANE,
                "§e⬆ Upgrade", "§7Increase protection tier"));
        menuInv.setStack(SLOT_TAB_AMBIENT,
            namedLore(page == MenuPage.AMBIENT ? Items.LIME_STAINED_GLASS_PANE : Items.WHITE_STAINED_GLASS_PANE,
                "§e✨ Ambient", "§7Particles, music, weather, time"));
    }
    private void buildInfoPage() {
        List<PlotData> owned = getOwnedPlots();
        int plotIndex = owned.indexOf(data) + 1;

        menuInv.setStack(19, namedLore(Items.NAME_TAG,
            "§6" + data.getPlotName(), "§7ID: §f#" + plotIndex));

        menuInv.setStack(20, makePlayerHeadFromServer(data.getOwnerId(), data.getOwnerName(),
            "§eOwner", "§f" + data.getOwnerName()));

        menuInv.setStack(21, namedLore(itemForSize(data.getSize()),
            tierColor(data.getSize()) + "Tier: " + data.getSize().getDisplayName(),
            "§7Size: §b" + data.getSize().getRadius() + "x" + data.getSize().getRadius() + " blocks"));

        menuInv.setStack(22, namedLore(Items.PAPER,
            "§eMembers",
            "§f" + data.getMembers().size() + " §7member(s)",
            "§f" + data.getGroups().size() + " §7grupo(s)"));

        BlockPos c = data.getCenter();
        menuInv.setStack(23, namedLore(Items.COMPASS,
            "§eLocation",
            "§7X: §f" + c.getX() + "  §7Y: §f" + c.getY() + "  §7Z: §f" + c.getZ()));

        menuInv.setStack(24, namedLore(Items.SHIELD,
            "§eYour role", roleColor(myRole) + myRole.name()));

        // Rename (owner)
        if (myRole == PlotData.Role.OWNER) {
            menuInv.setStack(29, namedLore(Items.ANVIL,
                "§6✏ Rename plot", "§7Click to change the name"));
        }

        // TP a la plot (si el flag está activo o es owner/admin)
        boolean tpEnabled = data.hasFlag(PlotData.Flag.ALLOW_TP);
        boolean canTp = tpEnabled || myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;
        if (canTp) {
            menuInv.setStack(31, namedLore(Items.ENDER_PEARL,
                "§b✈ Teleportarse",
                tpEnabled ? "§7Public TP enabled" : "§7Admins/owner only",
                "§eClick to TP to this plot"));
        }

        // Inactividad (si está habilitado)
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg != null && cfg.inactivityExpiry.enabled) {
            if (player.getWorld() instanceof ServerWorld sw) {
                long currentTick = sw.getTime();
                long daysInactive = data.getDaysInactive(currentTick);
                long maxDays = cfg.inactivityExpiry.baseDays + ((long) cfg.inactivityExpiry.daysPerTier * data.getSize().tier);
                long daysLeft = Math.max(0, maxDays - daysInactive);
                String statusColor = daysLeft > 7 ? "§a" : daysLeft > 0 ? "§e" : "§c";
                String expiryLine = daysLeft > 0
                    ? statusColor + daysLeft + " §7days until expiry"
                    : "§c⚠ Protection expired!";
                menuInv.setStack(33, namedLore(Items.CLOCK,
                    "§eOwner inactivity",
                    "§7Days inactive: §f" + daysInactive,
                    "§7Max days: §f" + maxDays,
                    expiryLine));
            }
        }
    }

    // ── MEMBERS PAGE ──────────────────────────────────────────────────────────
    private void buildMembersPage() {
        boolean canManage = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_MEMBERS);

        if (canManage) {
            menuInv.setStack(10, namedLore(Items.EMERALD,
                "§a+ Add member",
                "§7Clic para agregar un jugador",
                "§8El jugador debe estar online"));
        }

        // Miembros — sin grupos, usan todos los slots disponibles
        List<PlotData.PermissionGroup> groups = data.getGroups();
        List<Map.Entry<UUID, PlotData.Role>> memberList = new ArrayList<>(data.getMembers().entrySet());
        int[] slots = {19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43};
        int idx = 0;
        for (Map.Entry<UUID, PlotData.Role> entry : memberList) {
            if (idx >= slots.length) break;
            String name = data.getMemberName(entry.getKey());
            PlotData.Role role = entry.getValue();
            List<String> lore = new ArrayList<>();
            lore.add(roleColor(role) + role.name());
            // Mostrar grupos del miembro como info
            for (PlotData.PermissionGroup g : groups) {
                if (g.members.contains(entry.getKey())) lore.add("§d[" + g.name + "]");
            }
            if (canManage) {
                lore.add("§eClic: editar permisos");
                lore.add("§cShift+Clic: remover");
            }
            menuInv.setStack(slots[idx], makePlayerHeadFromServer(entry.getKey(), name,
                "§f" + name, lore.toArray(new String[0])));
            idx++;
        }

        if (memberList.isEmpty()) {
            menuInv.setStack(22, namedLore(Items.BARRIER,
                "§7No members yet",
                "§8Use the green button to add"));
        }
    }

    // ── MEMBER PERMS PAGE (sub-página) ────────────────────────────────────────
    private void buildMemberPermsPage(UUID uuid) {
        String name = data.getMemberName(uuid);
        PlotData.Role role = data.getRoleOf(uuid);
        boolean canEdit = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_PERMS);

        menuInv.setStack(10, makePlayerHeadFromServer(uuid, name,
            "§f" + name, roleColor(role) + role.name(), "§7Editando permisos"));

        menuInv.setStack(12, namedLore(Items.ARROW, "§7← Volver", "§8Clic para regresar"));

        // Paginación
        PlotData.Permission[] allPerms = PlotData.Permission.values();
        int totalPages = (int) Math.ceil((double) allPerms.length / PERMS_PER_PAGE);
        permPage = Math.max(0, Math.min(permPage, totalPages - 1));
        int start = permPage * PERMS_PER_PAGE;
        int end   = Math.min(start + PERMS_PER_PAGE, allPerms.length);

        // Botones de navegación de página
        if (permPage > 0)
            menuInv.setStack(14, namedLore(Items.SPECTRAL_ARROW, "§e← Prev page", "§7Page " + permPage + "/" + totalPages));
        if (permPage < totalPages - 1)
            menuInv.setStack(16, namedLore(Items.SPECTRAL_ARROW, "§eNext page →", "§7Page " + (permPage + 2) + "/" + totalPages));

        menuInv.setStack(13, namedLore(Items.PAPER, "§7Page §e" + (permPage + 1) + "§7/§e" + totalPages,
            "§8" + allPerms.length + " permisos en total"));

        int[] slots = {19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43};
        Set<PlotData.Permission> current = data.getPermsOf(uuid);
        int si = 0;
        for (int i = start; i < end && si < slots.length; i++, si++) {
            PlotData.Permission perm = allPerms[i];
            boolean has = current.contains(perm);
            menuInv.setStack(slots[si], namedLore(
                has ? Items.LIME_DYE : Items.GRAY_DYE,
                (has ? "§a✔ " : "§c✗ ") + permLabel(perm),
                "§7" + permDesc(perm),
                canEdit ? (has ? "§cClick to disable" : "§aClick to enable") : "§8No edit permission"
            ));
        }

        // Cambiar rol
        if (canEdit) {
            menuInv.setStack(16, namedLore(Items.EXPERIENCE_BOTTLE,
                "§eRol: " + roleColor(role) + role.name(),
                "§7Clic para cambiar el rol",
                "§8Cicla: MEMBER → ADMIN → MEMBER"));
        }

        // Grupos — mostrar grupos a los que pertenece y permitir agregar/quitar
        boolean canManageGroups = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS);
        List<PlotData.PermissionGroup> allGroups = data.getGroups();
        if (!allGroups.isEmpty()) {
            int[] groupSlots = {37, 38, 39, 40, 41, 42, 43};
            int gi = 0;
            for (PlotData.PermissionGroup g : allGroups) {
                if (gi >= groupSlots.length) break;
                boolean inGroup = g.members.contains(uuid);
                menuInv.setStack(groupSlots[gi], namedLore(
                    inGroup ? Items.PURPLE_STAINED_GLASS_PANE : Items.GRAY_STAINED_GLASS_PANE,
                    (inGroup ? "§d✔ " : "§8✗ ") + "Group: §d" + g.name,
                    inGroup ? "§7Miembro de este grupo" : "§8No pertenece a este grupo",
                    canManageGroups ? (inGroup ? "§cClic para quitar del grupo" : "§aClic para agregar al grupo") : "§8Sin permisos de grupos"
                ));
                gi++;
            }
        }
    }

    // ── GROUP PAGE (sub-página) ───────────────────────────────────────────────
    private void buildGroupPage(String groupName) {
        PlotData.PermissionGroup group = data.getGroup(groupName);
        if (group == null) { viewingGroupName = null; buildGlobalPermsPage(); return; }

        boolean canEdit = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS);

        menuInv.setStack(10, namedLore(Items.WRITABLE_BOOK, "§d[Grupo] " + group.name,
            "§7" + group.members.size() + " member(s)",
            "§7" + group.permissions.size() + " permiso(s)"));
        menuInv.setStack(12, namedLore(Items.ARROW, "§7← Volver", "§8Clic para regresar"));

        if (canEdit) {
            menuInv.setStack(14, namedLore(Items.TNT, "§cEliminar grupo", "§7Clic para borrar este grupo"));
        }

        // Permisos del grupo — paginated, all permissions shown
        PlotData.Permission[] perms = PlotData.Permission.values();
        int totalGroupPages = (int) Math.ceil((double) perms.length / PERMS_PER_PAGE);
        permPage = Math.max(0, Math.min(permPage, totalGroupPages - 1));
        int gStart = permPage * PERMS_PER_PAGE;
        int gEnd   = Math.min(gStart + PERMS_PER_PAGE, perms.length);

        if (permPage > 0)
            menuInv.setStack(15, namedLore(Items.SPECTRAL_ARROW, "§e← Prev page", "§7Page " + permPage + "/" + totalGroupPages));
        if (permPage < totalGroupPages - 1)
            menuInv.setStack(17, namedLore(Items.SPECTRAL_ARROW, "§eNext page →", "§7Page " + (permPage + 2) + "/" + totalGroupPages));
        menuInv.setStack(13, namedLore(Items.PAPER, "§7Page §e" + (permPage + 1) + "§7/§e" + totalGroupPages,
            "§8" + perms.length + " permissions total"));

        int[] gPermSlots = {19,20,21,22,23,24,25, 28,29,30,31,32,33,34};
        int si = 0;
        for (int i = gStart; i < gEnd && si < gPermSlots.length; i++, si++) {
            PlotData.Permission perm = perms[i];
            boolean has = group.permissions.contains(perm);
            menuInv.setStack(gPermSlots[si], namedLore(
                has ? Items.LIME_DYE : Items.GRAY_DYE,
                (has ? "§a✔ " : "§c✗ ") + permLabel(perm),
                "§7" + permDesc(perm),
                canEdit ? (has ? "§cClick to disable" : "§aClick to enable") : "§8No edit permission"
            ));
        }

        // Miembros del grupo (fila 4: slots 37-43, siempre visibles)
        int[] memberSlots = new int[]{37,38,39,40,41,42,43};
        int idx = 0;
        for (UUID uuid : group.members) {
            if (idx >= memberSlots.length) break;
            String mName = data.getMemberName(uuid);
            menuInv.setStack(memberSlots[idx], makePlayerHeadFromServer(uuid, mName,
                "§f" + mName,
                canEdit ? "§cClic para quitar del grupo" : ""));
            idx++;
        }
    }

    // ── GLOBAL PERMS PAGE (permisos globales + grupos) ────────────────────────
    private void buildGlobalPermsPage() {
        boolean canEditFlags  = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_FLAGS);
        boolean canEditGroups = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS);

        // Header
        menuInv.setStack(10, namedLore(Items.ORANGE_BANNER,
            "§e🌐 Global Perms",
            "§7Afectan a TODOS los jugadores dentro de la parcela.",
            canEditFlags ? "§eClick each to toggle" : "§8Only owner/admin can change"));

        // Flags — fila central
        PlotData.Flag[] flagValues = PlotData.Flag.values();
        int[] flagSlots = {19,20,21,22,23,24,25};
        for (int i = 0; i < flagValues.length && i < flagSlots.length; i++) {
            PlotData.Flag flag = flagValues[i];
            boolean on = data.hasFlag(flag);
            menuInv.setStack(flagSlots[i], namedLore(
                on ? Items.LIME_CONCRETE : Items.RED_CONCRETE,
                (on ? "§a[ON] " : "§c[OFF] ") + flagLabel(flag),
                "§7" + flagDesc(flag),
                canEditFlags ? (on ? "§cClic para desactivar" : "§aClic para activar") : "§8Sin permisos"
            ));
        }

        // Separador
        menuInv.setStack(27, namedLore(Items.PURPLE_STAINED_GLASS_PANE,
            "§d━━━ Permission Groups ━━━",
            "§7Assign permissions to multiple members at once."));

        // Botón crear grupo
        if (canEditGroups) {
            menuInv.setStack(28, namedLore(Items.BOOKSHELF,
                "§d+ Crear grupo",
                "§7Clic para crear un grupo de permisos",
                "§8Los grupos aplican permisos en bloque"));
        }

        // Grupos existentes
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
                "§7No groups yet",
                "§8Use the purple button to create one"));
        }
    }

    // ── UPGRADE PAGE ──────────────────────────────────────────────────────────
    private void buildUpgradePage() {
        PlotSize cur  = data.getSize();
        PlotSize next = cur.next();

        menuInv.setStack(19, namedLore(itemForSize(cur),
            tierColor(cur) + "Current tier: " + cur.getDisplayName(),
            "§7Size: §b" + cur.getRadius() + "x" + cur.getRadius() + " blocks"));

        if (next == null) {
            menuInv.setStack(22, namedLore(Items.NETHER_STAR,
                "§6⭐ Maximum tier reached!",
                "§7Your plot is at max tier"));
            return;
        }

        menuInv.setStack(21, namedLore(itemForSize(next),
            tierColor(next) + "Next tier: " + next.getDisplayName(),
            "§7Size: §b" + next.getRadius() + "x" + next.getRadius() + " blocks"));

        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        SecurePlotsConfig.UpgradeCost cost = cfg != null ? cfg.getUpgradeCost(cur.tier) : null;

        boolean canAfford = true;
        if (cost != null) {
            List<String> costLore = new ArrayList<>();
            costLore.add("§eYou need:");
            for (SecurePlotsConfig.UpgradeCost.ItemCost itemCost : cost.items) {
                net.minecraft.util.Identifier id = net.minecraft.util.Identifier.of(itemCost.itemId);
                net.minecraft.item.Item mc = net.minecraft.registry.Registries.ITEM.get(id);
                int has = countItem(player, mc);
                boolean ok = has >= itemCost.amount;
                if (!ok) canAfford = false;
                String raw = itemCost.itemId.contains(":") ? itemCost.itemId.split(":")[1] : itemCost.itemId;
                String itemName = Character.toUpperCase(raw.charAt(0)) + raw.substring(1).replace("_", " ");
                costLore.add((ok ? "§a✔" : "§c✗") + " §7" + itemName + ": " + (ok ? "§a" : "§c") + has + "§7/" + itemCost.amount);
            }
            menuInv.setStack(23, namedLoreDynamic(Items.PAPER, "§eRequired materials", costLore));
        }

        if (myRole == PlotData.Role.OWNER) {
            if (canAfford) {
                ItemStack btn = namedLore(Items.ANVIL, "§a⬆ Upgrade to " + next.getDisplayName(), "§7You have all the materials");
                btn.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                menuInv.setStack(SLOT_UPGRADE_BTN, btn);
            } else {
                menuInv.setStack(SLOT_UPGRADE_BTN, namedLore(Items.ANVIL, "§c✗ Cannot upgrade yet"));
            }
        } else {
            menuInv.setStack(SLOT_UPGRADE_BTN, namedLore(Items.BARRIER, "§cOnly the owner can upgrade"));
        }
    }

    // ── AMBIENT PAGE ──────────────────────────────────────────────────────────
    private void buildAmbientPage() {
        boolean canEdit = myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;
        com.zhilius.secureplots.config.SecurePlotsConfig cfg = com.zhilius.secureplots.config.SecurePlotsConfig.INSTANCE;
        int curParticleCount = (cfg != null) ? Math.max(1, Math.min(cfg.particleCount, 5)) : 3;
        float curMusicVol    = (cfg != null) ? Math.max(0.1f, Math.min(cfg.musicVolume, 4.0f)) : 4.0f;

        // ── Particles (slot 19) ───────────────────────────────────────────────
        String particle = data.getParticleEffect();
        menuInv.setStack(19, namedLore(Items.FIREWORK_STAR,
            "§e✨ Particles",
            particle.isEmpty() ? "§8None" : "§f" + particle,
            canEdit ? "§7Click to set (e.g. happy_villager)" : "§8Owner/admin only",
            canEdit && !particle.isEmpty() ? "§cRight-click to clear" : ""));

        // ── Particle count controls (slots 20-22) — solo si hay partícula activa ─
        boolean hasParticle = !particle.isEmpty();
        if (hasParticle) {
            // Decrease
            menuInv.setStack(20, namedLore(Items.RED_STAINED_GLASS_PANE,
                "§c◀ Menos partículas",
                "§7Actual: §e" + curParticleCount + " §7(mín 1)",
                canEdit ? "§7Click para reducir" : "§8Solo owner/admin"));
            // Current count display
            menuInv.setStack(21, namedLore(Items.PAPER,
                "§e🌟 Burst entrada: §f" + curParticleCount + "§7/5",
                "§7Partículas al ENTRAR a la plot",
                "§8Continuas: §7" + ((cfg != null) ? cfg.ambientParticleCount : 2) + "§8/seg (config: ambientParticleCount)",
                "§8Máximo burst: 5"));
            // Increase
            menuInv.setStack(22, namedLore(Items.LIME_STAINED_GLASS_PANE,
                "§a▶ Más partículas",
                "§7Actual: §e" + curParticleCount + " §7(máx 5)",
                canEdit ? "§7Click para aumentar" : "§8Solo owner/admin"));
        }

        // ── Music (slot 23) ───────────────────────────────────────────────────
        String music = data.getMusicSound();
        menuInv.setStack(23, namedLore(Items.MUSIC_DISC_CAT,
            "§e🎵 Music",
            music.isEmpty() ? "§8None" : "§f" + music,
            canEdit ? "§7Click to set (e.g. minecraft:music.game)" : "§8Owner/admin only",
            canEdit && !music.isEmpty() ? "§cRight-click to clear" : ""));

        // ── Music volume controls (slots 24-26) — solo si hay música activa ───
        boolean hasMusic = !music.isEmpty();
        if (hasMusic) {
            int volPct = Math.round((curMusicVol / 4.0f) * 100);
            // Decrease volume
            menuInv.setStack(24, namedLore(Items.ORANGE_STAINED_GLASS_PANE,
                "§6◀ Bajar volumen",
                "§7Actual: §e" + volPct + "%",
                canEdit ? "§7Click para bajar (-10%)" : "§8Solo owner/admin"));
            // Volume display
            menuInv.setStack(25, namedLore(Items.JUKEBOX,
                "§e🔊 Volumen: §f" + volPct + "%",
                "§7Volumen de la música de la plot",
                "§8Rango: 10% – 100%"));
            // Increase volume
            menuInv.setStack(26, namedLore(Items.LIME_STAINED_GLASS_PANE,
                "§a▶ Subir volumen",
                "§7Actual: §e" + volPct + "%",
                canEdit ? "§7Click para subir (+10%)" : "§8Solo owner/admin"));
        }


        // ── Enter/Exit message colors (slots 37-38) ─────────────────────────────
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

        // Color code reference book (slot 39)
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
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity actor) {
        if (slotIndex < 0 || slotIndex >= SIZE) return;
        ItemStack clicked = menuInv.getStack(slotIndex);
        if (clicked.isEmpty()) return;

        // Tabs
        if (slotIndex == SLOT_TAB_INFO)         { page = MenuPage.INFO;         viewingMemberUuid = null; viewingGroupName = null; refreshMenu(); return; }
        if (slotIndex == SLOT_TAB_MEMBERS)      { page = MenuPage.MEMBERS;      viewingMemberUuid = null; viewingGroupName = null; refreshMenu(); return; }
        if (slotIndex == SLOT_TAB_GLOBAL_PERMS) { page = MenuPage.GLOBAL_PERMS; viewingMemberUuid = null; viewingGroupName = null; refreshMenu(); return; }
        if (slotIndex == SLOT_TAB_UPGRADE)      { page = MenuPage.UPGRADE;      viewingMemberUuid = null; viewingGroupName = null; refreshMenu(); return; }
        if (slotIndex == SLOT_TAB_AMBIENT)      { page = MenuPage.AMBIENT;      viewingMemberUuid = null; viewingGroupName = null; refreshMenu(); return; }
        if (slotIndex == SLOT_CLOSE) { player.closeHandledScreen(); return; }

        // Botón upgrade
        if (slotIndex == SLOT_UPGRADE_BTN && myRole == PlotData.Role.OWNER) { handleUpgrade(); return; }

        // ── INFO page ──────────────────────────────────────────────────────────
        if (page == MenuPage.INFO) {
            // Renombrar
            if (clicked.getItem() == Items.ANVIL && myRole == PlotData.Role.OWNER) {
                openSignForInput(PendingAction.RENAME); return;
            }
            // TP
            if (clicked.getItem() == Items.ENDER_PEARL) {
                handleTp(); return;
            }
        }

        // ── MEMBERS page ──────────────────────────────────────────────────────
        if (page == MenuPage.MEMBERS) {
            if (viewingMemberUuid != null) {
                handleMemberPermsClick(slotIndex, button, clicked);
                return;
            }
            if (viewingGroupName != null) {
                handleGroupPageClick(slotIndex, button, clicked);
                return;
            }

            boolean canManage = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_MEMBERS);

            // Volver
            if (clicked.getItem() == Items.ARROW) {
                viewingMemberUuid = null; viewingGroupName = null; refreshMenu(); return;
            }
            // Agregar miembro
            if (clicked.getItem() == Items.EMERALD && canManage) {
                openSignForInput(PendingAction.ADD_MEMBER); return;
            }
            // Crear grupo
            if (clicked.getItem() == Items.BOOKSHELF && canManage) {
                openSignForInput(PendingAction.CREATE_GROUP); return;
            }
            // Clic en grupo
            if (clicked.getItem() == Items.WRITABLE_BOOK) {
                Text nameText = clicked.get(DataComponentTypes.CUSTOM_NAME);
                if (nameText != null) {
                    String gn = nameText.getString().replaceAll("§.", "").replace("[G] ", "").replace("[Grupo] ", "").trim();
                    if (data.getGroup(gn) != null) { viewingGroupName = gn; refreshMenu(); return; }
                }
            }
            // Clic en cabeza de miembro
            if (clicked.getItem() == Items.PLAYER_HEAD && canManage) {
                Text nameText = clicked.get(DataComponentTypes.CUSTOM_NAME);
                String memberName = nameText != null ? nameText.getString().replaceAll("§.", "").trim() : "";
                if (!memberName.isEmpty()) {
                    // Shift-click = remover, click normal = editar permisos
                    if (button == 1) { // right click / shift en algunos contextos
                        removeMemberByName(memberName);
                    } else if (actionType == SlotActionType.PICKUP_ALL) {
                        removeMemberByName(memberName);
                    } else {
                        // Buscar UUID y abrir sub-página
                        for (UUID uuid : data.getMembers().keySet()) {
                            if (data.getMemberName(uuid).equalsIgnoreCase(memberName)) {
                                viewingMemberUuid = uuid; permPage = 0; refreshMenu(); return;
                            }
                        }
                    }
                }
                return;
            }
        }

        // ── GLOBAL PERMS page ─────────────────────────────────────────────────
        if (page == MenuPage.GLOBAL_PERMS) {
            if (viewingGroupName != null) {
                handleGroupPageClick(slotIndex, button, clicked); return;
            }

            // Toggle flags
            boolean canEditFlags = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_FLAGS);
            if (canEditFlags) {
                PlotData.Flag[] flagValues = PlotData.Flag.values();
                int[] flagSlots = {19,20,21,22,23,24,25};
                for (int i = 0; i < flagValues.length && i < flagSlots.length; i++) {
                    if (slotIndex == flagSlots[i]) {
                        if (!(player.getWorld() instanceof ServerWorld sw)) return;
                        PlotManager manager = PlotManager.getOrCreate(sw);
                        PlotData fresh = manager.getPlot(plotPos);
                        if (fresh == null) return;
                        fresh.setFlag(flagValues[i], !fresh.hasFlag(flagValues[i]));
                        manager.markDirty();
                        this.data = fresh;
                        playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.2f);
                        refreshMenu();
                        return;
                    }
                }
            }

            // Crear grupo
            boolean canEditGroups = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS);
            if (clicked.getItem() == Items.BOOKSHELF && canEditGroups) {
                openSignForInput(PendingAction.CREATE_GROUP); return;
            }

            // Abrir grupo existente
            if (clicked.getItem() == Items.WRITABLE_BOOK) {
                Text nameText = clicked.get(DataComponentTypes.CUSTOM_NAME);
                if (nameText != null) {
                    String gn = nameText.getString().replaceAll("§.", "").replace("[G] ", "").trim();
                    if (data.getGroup(gn) != null) { viewingGroupName = gn; refreshMenu(); return; }
                }
            }
        }

        // ── AMBIENT page ──────────────────────────────────────────────────────
        if (page == MenuPage.AMBIENT) {
            boolean canEdit = myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;
            if (!canEdit) return;
            if (!(player.getWorld() instanceof ServerWorld sw)) return;
            PlotManager manager = PlotManager.getOrCreate(sw);
            PlotData fresh = manager.getPlot(plotPos);
            if (fresh == null) return;
            com.zhilius.secureplots.config.SecurePlotsConfig cfgA = com.zhilius.secureplots.config.SecurePlotsConfig.INSTANCE;

            // Particles - left click = open chat, right = clear
            if (slotIndex == 19) {
                if (button == 1 && !fresh.getParticleEffect().isEmpty()) {
                    fresh.setParticleEffect(""); manager.markDirty(); this.data = fresh;
                    playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.0f);
                    refreshMenu();
                } else {
                    player.closeHandledScreen();
                    ServerPlayNetworking.send(player, new ModPackets.OpenChatPayload("/sp plot particle "));
                }
                return;
            }

            // Particle count — decrease
            if (slotIndex == 20 && !fresh.getParticleEffect().isEmpty() && cfgA != null) {
                cfgA.particleCount = Math.max(1, cfgA.particleCount - 1);
                com.zhilius.secureplots.config.SecurePlotsConfig.save();
                playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 0.9f);
                refreshMenu(); return;
            }
            // Particle count — increase
            if (slotIndex == 22 && !fresh.getParticleEffect().isEmpty() && cfgA != null) {
                cfgA.particleCount = Math.min(5, cfgA.particleCount + 1);
                com.zhilius.secureplots.config.SecurePlotsConfig.save();
                playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.1f);
                refreshMenu(); return;
            }

            // Music - left click = open chat, right = clear
            if (slotIndex == 23) {
                if (button == 1 && !fresh.getMusicSound().isEmpty()) {
                    fresh.setMusicSound(""); manager.markDirty(); this.data = fresh;
                    playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.0f);
                    refreshMenu();
                } else {
                    player.closeHandledScreen();
                    ServerPlayNetworking.send(player, new ModPackets.OpenChatPayload("/sp plot music "));
                }
                return;
            }

            // Music volume — decrease
            if (slotIndex == 24 && !fresh.getMusicSound().isEmpty() && cfgA != null) {
                cfgA.musicVolume = Math.max(0.1f, Math.round((cfgA.musicVolume - 0.4f) * 10f) / 10f);
                com.zhilius.secureplots.config.SecurePlotsConfig.save();
                playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 0.9f);
                refreshMenu(); return;
            }
            // Music volume — increase
            if (slotIndex == 26 && !fresh.getMusicSound().isEmpty() && cfgA != null) {
                cfgA.musicVolume = Math.min(4.0f, Math.round((cfgA.musicVolume + 0.4f) * 10f) / 10f);
                com.zhilius.secureplots.config.SecurePlotsConfig.save();
                playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.1f);
                refreshMenu(); return;
            }


            // Enter message - left = edit, right = clear
            if (slotIndex == 37) {
                if (button == 1 && !fresh.getEnterMessage().isEmpty()) {
                    fresh.setEnterMessage(""); manager.markDirty(); this.data = fresh;
                    playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.0f);
                    refreshMenu();
                } else {
                    openSignForInput(PendingAction.SET_ENTER_MESSAGE);
                }
                return;
            }

            // Exit message - left = edit, right = clear
            if (slotIndex == 38) {
                if (button == 1 && !fresh.getExitMessage().isEmpty()) {
                    fresh.setExitMessage(""); manager.markDirty(); this.data = fresh;
                    playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.0f);
                    refreshMenu();
                } else {
                    openSignForInput(PendingAction.SET_EXIT_MESSAGE);
                }
                return;
            }
        }
    }

    private void handleMemberPermsClick(int slotIndex, int button, ItemStack clicked) {
        if (clicked.getItem() == Items.ARROW) {
            viewingMemberUuid = null; permPage = 0; refreshMenu(); return;
        }
        // Navegación de páginas
        if (clicked.getItem() == Items.SPECTRAL_ARROW) {
            Text nameText = clicked.get(DataComponentTypes.CUSTOM_NAME);
            String label = nameText != null ? nameText.getString() : "";
            if (label.contains("Prev") || label.contains("prev")) permPage = Math.max(0, permPage - 1);
            else permPage++;
            playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, label.contains("Prev") ? 0.8f : 1.2f);
            refreshMenu(); return;
        }

        boolean canEdit = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_PERMS);
        boolean canManageGroups = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS);

        // Cambiar rol
        if (clicked.getItem() == Items.EXPERIENCE_BOTTLE && canEdit) {
            cycleRole(viewingMemberUuid); return;
        }

        // Toggle grupo (fila inferior: slots 37-43)
        if (canManageGroups) {
            int[] groupSlots = {37, 38, 39, 40, 41, 42, 43};
            List<PlotData.PermissionGroup> allGroups = data.getGroups();
            for (int i = 0; i < allGroups.size() && i < groupSlots.length; i++) {
                if (slotIndex == groupSlots[i]) {
                    if (!(player.getWorld() instanceof ServerWorld sw)) return;
                    PlotManager manager = PlotManager.getOrCreate(sw);
                    PlotData fresh = manager.getPlot(plotPos);
                    if (fresh == null) return;
                    PlotData.PermissionGroup g = fresh.getGroup(allGroups.get(i).name);
                    if (g == null) return;
                    if (g.members.contains(viewingMemberUuid)) g.members.remove(viewingMemberUuid);
                    else g.members.add(viewingMemberUuid);
                    manager.markDirty();
                    this.data = fresh;
                    playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.2f);
                    refreshMenu(); return;
                }
            }
        }

        if (!canEdit) return;

        // Toggle permiso — con paginación
        PlotData.Permission[] allPerms = PlotData.Permission.values();
        int start = permPage * PERMS_PER_PAGE;
        int end   = Math.min(start + PERMS_PER_PAGE, allPerms.length);
        int[] slots = {19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43};
        int si = 0;
        for (int i = start; i < end && si < slots.length; i++, si++) {
            if (slotIndex == slots[si]) {
                if (!(player.getWorld() instanceof ServerWorld sw)) return;
                PlotManager manager = PlotManager.getOrCreate(sw);
                PlotData fresh = manager.getPlot(plotPos);
                if (fresh == null) return;
                boolean current = fresh.hasPermission(viewingMemberUuid, allPerms[i]);
                fresh.setPermission(viewingMemberUuid, allPerms[i], !current);
                manager.markDirty();
                this.data = fresh;
                playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.2f);
                refreshMenu(); return;
            }
        }
    }

    private void handleGroupPageClick(int slotIndex, int button, ItemStack clicked) {
        if (clicked.getItem() == Items.ARROW) {
            viewingGroupName = null; refreshMenu(); return;
        }
        boolean canEdit = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS);
        if (!canEdit) return;

        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        PlotManager manager = PlotManager.getOrCreate(sw);
        PlotData fresh = manager.getPlot(plotPos);
        if (fresh == null) return;

        PlotData.PermissionGroup group = fresh.getGroup(viewingGroupName);
        if (group == null) { viewingGroupName = null; refreshMenu(); return; }

        // Eliminar grupo
        if (clicked.getItem() == Items.TNT) {
            fresh.removeGroup(viewingGroupName);
            manager.markDirty();
            this.data = fresh;
            viewingGroupName = null;
            playSound(SoundEvents.ENTITY_ITEM_BREAK, 1f, 0.8f);
            refreshMenu(); return;
        }

        // Pagination for group perms
        if (clicked.getItem() == Items.SPECTRAL_ARROW) {
            Text lbl = clicked.get(DataComponentTypes.CUSTOM_NAME);
            String s = lbl != null ? lbl.getString() : "";
            if (s.contains("Prev")) permPage = Math.max(0, permPage - 1);
            else permPage++;
            playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.6f, s.contains("Prev") ? 0.8f : 1.2f);
            refreshMenu(); return;
        }

        // Toggle perm del grupo (paginated, 14 per page)
        PlotData.Permission[] perms = PlotData.Permission.values();
        int[] permSlots = {19,20,21,22,23,24,25, 28,29,30,31,32,33,34};
        int gStart = permPage * PERMS_PER_PAGE;
        for (int si = 0; si < permSlots.length; si++) {
            int pi = gStart + si;
            if (pi >= perms.length) break;
            if (slotIndex == permSlots[si]) {
                boolean has = group.permissions.contains(perms[pi]);
                if (has) group.permissions.remove(perms[pi]); else group.permissions.add(perms[pi]);
                manager.markDirty();
                this.data = fresh;
                playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.2f);
                refreshMenu(); return;
            }
        }

        // Quitar miembro del grupo
        if (clicked.getItem() == Items.PLAYER_HEAD) {
            Text nameText = clicked.get(DataComponentTypes.CUSTOM_NAME);
            String mName = nameText != null ? nameText.getString().replaceAll("§.", "").trim() : "";
            group.members.removeIf(u -> fresh.getMemberName(u).equalsIgnoreCase(mName));
            manager.markDirty();
            this.data = fresh;
            refreshMenu(); return;
        }
    }

    // ── TP ────────────────────────────────────────────────────────────────────
    private void handleTp() {
        boolean tpEnabled = data.hasFlag(PlotData.Flag.ALLOW_TP);
        boolean canTp = tpEnabled || myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;
        if (!canTp) {
            player.sendMessage(Text.literal("§c✗ TP is not enabled in this plot."), false);
            return;
        }
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        BlockPos c = data.getCenter();
        // Teleport encima del bloque central
        double tpY = sw.getTopPosition(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
            new BlockPos(c.getX(), c.getY(), c.getZ())).getY();
        player.closeHandledScreen();
        player.teleport(sw, c.getX() + 0.5, tpY, c.getZ() + 0.5,
            java.util.Set.of(), player.getYaw(), player.getPitch());
        player.sendMessage(Text.literal("§a✔ Teleportado a §e" + data.getPlotName()), false);
        sw.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
    }

    // ── Sign Input ────────────────────────────────────────────────────────────
    private void openSignForInput(PendingAction action) {
        player.closeHandledScreen();
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        sw.getServer().execute(() -> {
            switch (action) {
                case RENAME             -> SignInputManager.openForRename(player, plotPos);
                case ADD_MEMBER         -> SignInputManager.openForAddMember(player, plotPos);
                case CREATE_GROUP       -> SignInputManager.openForCreateGroup(player, plotPos);
                case SET_ENTER_MESSAGE  -> SignInputManager.openForEnterMessage(player, plotPos);
                case SET_EXIT_MESSAGE   -> SignInputManager.openForExitMessage(player, plotPos);
                case SET_PARTICLE       -> SignInputManager.openForParticle(player, plotPos);
                case SET_MUSIC          -> SignInputManager.openForMusic(player, plotPos);
                default                 -> {}
            }
        });
    }

    // ── Upgrade ───────────────────────────────────────────────────────────────
    private void handleUpgrade() {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        PlotManager manager = PlotManager.getOrCreate(sw);
        PlotData fresh = manager.getPlot(plotPos);
        if (fresh == null) return;

        PlotSize next = fresh.getSize().next();
        if (next == null) { playSound(SoundEvents.ENTITY_VILLAGER_NO, 1f, 1f); return; }

        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        SecurePlotsConfig.UpgradeCost cost = cfg != null ? cfg.getUpgradeCost(fresh.getSize().tier) : null;

        if (cost != null) {
            for (SecurePlotsConfig.UpgradeCost.ItemCost ic : cost.items) {
                net.minecraft.util.Identifier id = net.minecraft.util.Identifier.of(ic.itemId);
                net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(id);
                if (countItem(player, item) < ic.amount) {
                    playSound(SoundEvents.ENTITY_VILLAGER_NO, 1f, 0.8f);
                    refreshMenu(); return;
                }
            }
            for (SecurePlotsConfig.UpgradeCost.ItemCost ic : cost.items) {
                net.minecraft.util.Identifier id = net.minecraft.util.Identifier.of(ic.itemId);
                net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(id);
                removeItem(player, item, ic.amount);
            }
        }

        fresh.setSize(next);
        net.minecraft.block.Block newBlock = com.zhilius.secureplots.block.ModBlocks.fromTier(next.tier);
        sw.setBlockState(plotPos, newBlock.getDefaultState());
        manager.markDirty();

        sw.playSound(null, plotPos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1f, 1f);
        sw.playSound(null, plotPos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.2f);
        spawnUpgradeParticles(sw, plotPos);

        player.closeHandledScreen();
        com.zhilius.secureplots.network.ModPackets.sendShowPlotBorder(player, fresh);
        player.sendMessage(Text.literal("§a✔ Plot upgraded to §e" + next.getDisplayName() + "§a!"), false);
    }

    private void spawnUpgradeParticles(ServerWorld sw, BlockPos pos) {
        Vec3d center = Vec3d.ofCenter(pos);
        for (int i = 0; i < 30; i++) {
            double angle = i * (Math.PI * 2 / 30);
            sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                center.x + Math.cos(angle) * 1.5, center.y + 0.5 + (i * 0.05), center.z + Math.sin(angle) * 1.5,
                1, 0, 0, 0, 0);
        }
        for (int i = 0; i < 15; i++) {
            sw.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                center.x + (Math.random() - 0.5) * 2, center.y + Math.random() * 2, center.z + (Math.random() - 0.5) * 2,
                1, 0, 0.1, 0, 0.1);
        }
        sw.spawnParticles(ParticleTypes.ENCHANT, center.x, center.y + 1, center.z, 40, 0.5, 0.5, 0.5, 0.5);
    }

    private void cycleRole(UUID uuid) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        PlotManager manager = PlotManager.getOrCreate(sw);
        PlotData fresh = manager.getPlot(plotPos);
        if (fresh == null) return;
        PlotData.Role current = fresh.getRoleOf(uuid);
        PlotData.Role next = current == PlotData.Role.MEMBER ? PlotData.Role.ADMIN : PlotData.Role.MEMBER;
        fresh.getMembers().put(uuid, next);
        // Reset perms to role default
        fresh.getMembers().put(uuid, next);
        manager.markDirty();
        this.data = fresh;
        refreshMenu();
    }

    private void removeMemberByName(String name) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        PlotManager manager = PlotManager.getOrCreate(sw);
        PlotData fresh = manager.getPlot(plotPos);
        if (fresh == null) return;
        UUID target = null;
        for (UUID uuid : fresh.getMembers().keySet()) {
            if (fresh.getMemberName(uuid).equalsIgnoreCase(name)) { target = uuid; break; }
        }
        if (target == null) return;
        fresh.removeMember(target);
        manager.markDirty();
        this.data = fresh;
        playSound(SoundEvents.ENTITY_ITEM_BREAK, 1f, 0.8f);
        player.sendMessage(Text.literal("§a✔ " + name + " eliminado."), false);
        refreshMenu();
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

    // ── Labels de permisos / flags ────────────────────────────────────────────
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
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).styled(s -> s.withItalic(false)));
        List<Text> lore = new ArrayList<>();
        for (String line : loreLines)
            lore.add(Text.literal(line).styled(s -> s.withItalic(false)));
        stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return stack;
    }

    private static ItemStack namedLoreDynamic(net.minecraft.item.Item item, String name, List<String> loreLines) {
        return namedLore(item, name, loreLines.toArray(new String[0]));
    }

    private ItemStack makePlayerHeadFromServer(UUID uuid, String playerName, String displayName, String... loreLines) {
        ItemStack stack = namedLore(Items.PLAYER_HEAD, displayName, loreLines);
        try {
            ServerPlayerEntity onlinePlayer = player.getServer().getPlayerManager().getPlayer(uuid);
            GameProfile profile;
            if (onlinePlayer != null) {
                profile = onlinePlayer.getGameProfile();
            } else {
                var userCache = player.getServer().getUserCache();
                profile = (userCache != null) ? userCache.getByUuid(uuid).orElse(new GameProfile(uuid, playerName))
                                              : new GameProfile(uuid, playerName);
            }
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

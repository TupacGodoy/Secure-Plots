package com.zhilius.secureplots.screen;

import com.mojang.authlib.GameProfile;
import com.zhilius.secureplots.config.SecurePlotsConfig;
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
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

public class PlotMenuHandler extends GenericContainerScreenHandler {

    public enum MenuPage { INFO, MEMBERS, GLOBAL_PERMS, UPGRADE }
    public enum PendingAction { NONE, RENAME, ADD_MEMBER, CREATE_GROUP }

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

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;

    // Tabs en fila superior
    private static final int SLOT_TAB_INFO         = 0;
    private static final int SLOT_TAB_MEMBERS      = 1;
    private static final int SLOT_TAB_GLOBAL_PERMS = 2;
    private static final int SLOT_TAB_UPGRADE      = 3;
    private static final int SLOT_CLOSE       = 8;
    private static final int SLOT_UPGRADE_BTN = 49;

    public PlotMenuHandler(int syncId, PlayerInventory playerInv, BlockPos plotPos, PlotData data, MenuPage page) {
        this(syncId, playerInv, plotPos, data, page, new SimpleInventory(SIZE));
    }

    private PlotMenuHandler(int syncId, PlayerInventory playerInv, BlockPos plotPos, PlotData data, MenuPage page, SimpleInventory inv) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, ROWS);
        this.plotPos = plotPos;
        this.data    = data;
        boolean isPlotAdmin = ((ServerPlayerEntity) playerInv.player).getCommandTags().contains("plot_admin");
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
            case GLOBAL_PERMS -> buildGlobalPermsPage();
            case UPGRADE -> buildUpgradePage();
        }
        menuInv.setStack(SLOT_CLOSE, named(Items.BARRIER, "§c✕ Cerrar"));
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
                "§e📋 Info", "§7Ver información de la parcela"));
        menuInv.setStack(SLOT_TAB_MEMBERS,
            namedLore(page == MenuPage.MEMBERS ? Items.LIME_STAINED_GLASS_PANE : Items.WHITE_STAINED_GLASS_PANE,
                "§e👥 Miembros", "§7Gestionar miembros y sus permisos"));
        menuInv.setStack(SLOT_TAB_GLOBAL_PERMS,
            namedLore(page == MenuPage.GLOBAL_PERMS ? Items.LIME_STAINED_GLASS_PANE : Items.WHITE_STAINED_GLASS_PANE,
                "§e🌐 Permisos Globales", "§7Permisos globales y grupos"));
        menuInv.setStack(SLOT_TAB_UPGRADE,
            namedLore(page == MenuPage.UPGRADE ? Items.LIME_STAINED_GLASS_PANE : Items.WHITE_STAINED_GLASS_PANE,
                "§e⬆ Mejorar", "§7Subir el nivel de protección"));
    }

    // ── INFO PAGE ─────────────────────────────────────────────────────────────
    private void buildInfoPage() {
        List<PlotData> owned = getOwnedPlots();
        int plotIndex = owned.indexOf(data) + 1;

        menuInv.setStack(19, namedLore(Items.NAME_TAG,
            "§6" + data.getPlotName(), "§7ID: §f#" + plotIndex));

        menuInv.setStack(20, makePlayerHeadFromServer(data.getOwnerId(), data.getOwnerName(),
            "§eDueño", "§f" + data.getOwnerName()));

        menuInv.setStack(21, namedLore(itemForSize(data.getSize()),
            tierColor(data.getSize()) + "Nivel: " + data.getSize().displayName,
            "§7Tamaño: §b" + data.getSize().radius + "x" + data.getSize().radius + " bloques"));

        menuInv.setStack(22, namedLore(Items.PAPER,
            "§eIntegrantes",
            "§f" + data.getMembers().size() + " §7miembro(s)",
            "§f" + data.getGroups().size() + " §7grupo(s)"));

        BlockPos c = data.getCenter();
        menuInv.setStack(23, namedLore(Items.COMPASS,
            "§eUbicación",
            "§7X: §f" + c.getX() + "  §7Y: §f" + c.getY() + "  §7Z: §f" + c.getZ()));

        menuInv.setStack(24, namedLore(Items.SHIELD,
            "§eTu rol", roleColor(myRole) + myRole.name()));

        // Renombrar (owner)
        if (myRole == PlotData.Role.OWNER) {
            menuInv.setStack(29, namedLore(Items.ANVIL,
                "§6✏ Renombrar parcela", "§7Clic para cambiar el nombre"));
        }

        // TP a la plot (si el flag está activo o es owner/admin)
        boolean tpEnabled = data.hasFlag(PlotData.Flag.ALLOW_TP);
        boolean canTp = tpEnabled || myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;
        if (canTp) {
            menuInv.setStack(31, namedLore(Items.ENDER_PEARL,
                "§b✈ Teleportarse",
                tpEnabled ? "§7TP público habilitado" : "§7Solo para admins/owner",
                "§eClic para tp a esta parcela"));
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
                    ? statusColor + daysLeft + " §7días para expirar"
                    : "§c⚠ ¡Protección expirada!";
                menuInv.setStack(33, namedLore(Items.CLOCK,
                    "§eInactividad del dueño",
                    "§7Días inactivo: §f" + daysInactive,
                    "§7Máx. días: §f" + maxDays,
                    expiryLine));
            }
        }
    }

    // ── MEMBERS PAGE ──────────────────────────────────────────────────────────
    private void buildMembersPage() {
        boolean canManage = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_MEMBERS);

        if (canManage) {
            menuInv.setStack(10, namedLore(Items.EMERALD,
                "§a+ Agregar miembro",
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
                "§7Sin miembros todavía",
                "§8Usá el botón verde para agregar"));
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

        // Mostrar toggle por cada permiso (excepto OWNER-only)
        PlotData.Permission[] perms = PlotData.Permission.values();
        int[] slots = {19,20,21,22,23,24,25, 28,29,30,31,32,33,34};
        Set<PlotData.Permission> current = data.getPermsOf(uuid);
        for (int i = 0; i < perms.length && i < slots.length; i++) {
            PlotData.Permission perm = perms[i];
            boolean has = current.contains(perm);
            menuInv.setStack(slots[i], namedLore(
                has ? Items.LIME_DYE : Items.GRAY_DYE,
                (has ? "§a✔ " : "§c✗ ") + permLabel(perm),
                "§7" + permDesc(perm),
                canEdit ? (has ? "§cClic para desactivar" : "§aClic para activar") : "§8Sin permisos de edición"
            ));
        }

        // Cambiar rol
        if (canEdit) {
            menuInv.setStack(16, namedLore(Items.EXPERIENCE_BOTTLE,
                "§eRol: " + roleColor(role) + role.name(),
                "§7Clic para cambiar el rol",
                "§8Cicla: MEMBER → ADMIN → MEMBER"));
        }
    }

    // ── GROUP PAGE (sub-página) ───────────────────────────────────────────────
    private void buildGroupPage(String groupName) {
        PlotData.PermissionGroup group = data.getGroup(groupName);
        if (group == null) { viewingGroupName = null; buildGlobalPermsPage(); return; }

        boolean canEdit = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_GROUPS);

        menuInv.setStack(10, namedLore(Items.WRITABLE_BOOK, "§d[Grupo] " + group.name,
            "§7" + group.members.size() + " miembro(s)",
            "§7" + group.permissions.size() + " permiso(s)"));
        menuInv.setStack(12, namedLore(Items.ARROW, "§7← Volver", "§8Clic para regresar"));

        if (canEdit) {
            menuInv.setStack(14, namedLore(Items.TNT, "§cEliminar grupo", "§7Clic para borrar este grupo"));
        }

        // Permisos del grupo
        PlotData.Permission[] perms = PlotData.Permission.values();
        int[] permSlots = {19,20,21,22,23,24,25};
        for (int i = 0; i < perms.length && i < permSlots.length; i++) {
            PlotData.Permission perm = perms[i];
            boolean has = group.permissions.contains(perm);
            menuInv.setStack(permSlots[i], namedLore(
                has ? Items.LIME_DYE : Items.GRAY_DYE,
                (has ? "§a✔ " : "§c✗ ") + permLabel(perm),
                "§7" + permDesc(perm),
                canEdit ? (has ? "§cClic para desactivar" : "§aClic para activar") : "§8Sin permisos de edición"
            ));
        }

        // Miembros del grupo
        int[] memberSlots = {28,29,30,31,32,33,34};
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
            "§e🌐 Permisos Globales",
            "§7Afectan a TODOS los jugadores dentro de la parcela.",
            canEditFlags ? "§eClic en cada uno para activar/desactivar" : "§8Solo el dueño/admin puede cambiar"));

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
            "§d━━━ Grupos de Permisos ━━━",
            "§7Asignan permisos a varios miembros a la vez."));

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
                "§7" + g.members.size() + " miembro(s)",
                "§7" + g.permissions.size() + " permiso(s)",
                canEditGroups ? "§eClic para editar" : "§8Solo lectura"));
        }

        if (groups.isEmpty()) {
            menuInv.setStack(31, namedLore(Items.BARRIER,
                "§7Sin grupos todavía",
                "§8Usá el botón morado para crear uno"));
        }
    }

    // ── UPGRADE PAGE ──────────────────────────────────────────────────────────
    private void buildUpgradePage() {
        PlotSize cur  = data.getSize();
        PlotSize next = cur.next();

        menuInv.setStack(19, namedLore(itemForSize(cur),
            tierColor(cur) + "Nivel actual: " + cur.displayName,
            "§7Tamaño: §b" + cur.radius + "x" + cur.radius + " bloques"));

        if (next == null) {
            menuInv.setStack(22, namedLore(Items.NETHER_STAR,
                "§6⭐ ¡Nivel máximo alcanzado!",
                "§7Tu protección está al máximo"));
            return;
        }

        menuInv.setStack(21, namedLore(itemForSize(next),
            tierColor(next) + "Siguiente: " + next.displayName,
            "§7Tamaño: §b" + next.radius + "x" + next.radius + " bloques"));

        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        SecurePlotsConfig.UpgradeCost cost = cfg != null ? cfg.getUpgradeCost(cur.tier) : null;

        boolean canAfford = true;
        if (cost != null) {
            List<String> costLore = new ArrayList<>();
            costLore.add("§eNecesitás:");
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
            menuInv.setStack(23, namedLoreDynamic(Items.PAPER, "§eMateriales requeridos", costLore));
        }

        if (myRole == PlotData.Role.OWNER) {
            if (canAfford) {
                ItemStack btn = namedLore(Items.ANVIL, "§a⬆ Mejorar a " + next.displayName, "§7Tenés todos los materiales");
                btn.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                menuInv.setStack(SLOT_UPGRADE_BTN, btn);
            } else {
                menuInv.setStack(SLOT_UPGRADE_BTN, namedLore(Items.ANVIL, "§c✗ No podés mejorar todavía"));
            }
        } else {
            menuInv.setStack(SLOT_UPGRADE_BTN, namedLore(Items.BARRIER, "§cSolo el dueño puede mejorar"));
        }
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
                                viewingMemberUuid = uuid; refreshMenu(); return;
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
    }

    private void handleMemberPermsClick(int slotIndex, int button, ItemStack clicked) {
        if (clicked.getItem() == Items.ARROW) {
            viewingMemberUuid = null; refreshMenu(); return;
        }
        boolean canEdit = data.hasPermission(player.getUuid(), PlotData.Permission.MANAGE_PERMS);
        if (!canEdit) return;

        // Cambiar rol
        if (clicked.getItem() == Items.EXPERIENCE_BOTTLE) {
            cycleRole(viewingMemberUuid); return;
        }

        // Toggle permiso
        PlotData.Permission[] perms = PlotData.Permission.values();
        int[] slots = {19,20,21,22,23,24,25, 28,29,30,31,32,33,34};
        for (int i = 0; i < perms.length && i < slots.length; i++) {
            if (slotIndex == slots[i]) {
                if (!(player.getWorld() instanceof ServerWorld sw)) return;
                PlotManager manager = PlotManager.getOrCreate(sw);
                PlotData fresh = manager.getPlot(plotPos);
                if (fresh == null) return;
                boolean current = fresh.hasPermission(viewingMemberUuid, perms[i]);
                fresh.setPermission(viewingMemberUuid, perms[i], !current);
                manager.markDirty();
                this.data = fresh;
                playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.2f);
                refreshMenu();
                return;
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

        // Toggle perm del grupo
        PlotData.Permission[] perms = PlotData.Permission.values();
        int[] permSlots = {19,20,21,22,23,24,25};
        for (int i = 0; i < perms.length && i < permSlots.length; i++) {
            if (slotIndex == permSlots[i]) {
                boolean has = group.permissions.contains(perms[i]);
                if (has) group.permissions.remove(perms[i]); else group.permissions.add(perms[i]);
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
            player.sendMessage(Text.literal("§c✗ El TP no está habilitado en esta parcela."), false);
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
                case RENAME       -> SignInputManager.openForRename(player, plotPos);
                case ADD_MEMBER   -> SignInputManager.openForAddMember(player, plotPos);
                case CREATE_GROUP -> SignInputManager.openForCreateGroup(player, plotPos);
                default           -> {}
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
        player.sendMessage(Text.literal("§a✔ ¡Protección mejorada a §e" + next.displayName + "§a!"), false);
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
            case BUILD          -> "Construir";
            case INTERACT       -> "Interactuar";
            case CONTAINERS     -> "Contenedores";
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
            case INTERACT       -> "Palancas, puertas, botones";
            case CONTAINERS     -> "Abrir cofres e inventarios";
            case PVP            -> "Atacar a otros jugadores";
            case MANAGE_MEMBERS -> "Agregar y remover miembros";
            case MANAGE_PERMS   -> "Cambiar permisos de miembros";
            case MANAGE_FLAGS   -> "Cambiar flags globales";
            case MANAGE_GROUPS  -> "Crear y editar grupos";
            case TP             -> "Usar /sp tp para llegar aquí";
            case FLY            -> "Volar dentro de la parcela";
            case ENTER          -> "Entrar al área de la parcela";
        };
    }

    private static String flagLabel(PlotData.Flag flag) {
        return switch (flag) {
            case ALLOW_VISITOR_BUILD      -> "Visitantes: Construir";
            case ALLOW_VISITOR_INTERACT   -> "Visitantes: Interactuar";
            case ALLOW_VISITOR_CONTAINERS -> "Visitantes: Contenedores";
            case ALLOW_PVP                -> "PvP Global";
            case ALLOW_FLY                -> "Volar Global";
            case ALLOW_TP                 -> "TP Público";
            case GREETINGS                -> "Mensajes de Bienvenida";
        };
    }

    private static String flagDesc(PlotData.Flag flag) {
        return switch (flag) {
            case ALLOW_VISITOR_BUILD      -> "Cualquiera puede construir aquí";
            case ALLOW_VISITOR_INTERACT   -> "Cualquiera puede interactuar";
            case ALLOW_VISITOR_CONTAINERS -> "Cualquiera puede abrir cofres";
            case ALLOW_PVP                -> "PvP habilitado para todos";
            case ALLOW_FLY                -> "Todos pueden volar aquí";
            case ALLOW_TP                 -> "Todos pueden /sp tp a esta plot";
            case GREETINGS                -> "Mostrar mensaje al entrar/salir";
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

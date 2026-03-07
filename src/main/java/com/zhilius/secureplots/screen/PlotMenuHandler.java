package com.zhilius.secureplots.screen;

import com.mojang.authlib.GameProfile;
import com.zhilius.secureplots.config.SecurePlotsConfig;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotManager;
import com.zhilius.secureplots.plot.PlotSize;
import com.zhilius.secureplots.screen.SignInputManager;
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
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PlotMenuHandler extends GenericContainerScreenHandler {

    public enum MenuPage { INFO, MEMBERS, UPGRADE }
    public enum PendingAction { NONE, RENAME, ADD_MEMBER }

    private final BlockPos plotPos;
    private PlotData data;
    private PlotData.Role myRole;
    private final ServerPlayerEntity player;
    private MenuPage page;
    private final SimpleInventory menuInv;

    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;

    private static final int SLOT_TAB_INFO    = 0;
    private static final int SLOT_TAB_MEMBERS = 1;
    private static final int SLOT_TAB_UPGRADE = 2;
    private static final int SLOT_CLOSE       = 8;
    private static final int SLOT_UPGRADE_BTN = 49;

    public PlotMenuHandler(int syncId, PlayerInventory playerInv, BlockPos plotPos, PlotData data, MenuPage page) {
        this(syncId, playerInv, plotPos, data, page, new SimpleInventory(SIZE));
    }

    private PlotMenuHandler(int syncId, PlayerInventory playerInv, BlockPos plotPos, PlotData data, MenuPage page, SimpleInventory inv) {
        super(ScreenHandlerType.GENERIC_9X6, syncId, playerInv, inv, ROWS);
        this.plotPos = plotPos;
        this.data    = data;
        // plot_admin tag overrides role check — treat as OWNER for menu purposes
        boolean isPlotAdmin = ((ServerPlayerEntity) playerInv.player).getCommandTags().contains("plot_admin");
        this.myRole  = isPlotAdmin ? PlotData.Role.OWNER : data.getRoleOf(playerInv.player.getUuid());
        this.player  = (ServerPlayerEntity) playerInv.player;
        this.page    = page;
        this.menuInv = inv;
        buildMenu();
        // Sonido al abrir
        playSound(SoundEvents.BLOCK_CHEST_OPEN, 0.5f, 1.2f);
    }

    // ── Build ─────────────────────────────────────────────────────────────────
    public void buildMenu() {
        for (int i = 0; i < SIZE; i++) menuInv.setStack(i, ItemStack.EMPTY);
        fillBorder();
        buildTabs();
        switch (page) {
            case INFO    -> buildInfoPage();
            case MEMBERS -> buildMembersPage();
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
                "§e👥 Miembros", "§7Gestionar acceso"));
        menuInv.setStack(SLOT_TAB_UPGRADE,
            namedLore(page == MenuPage.UPGRADE ? Items.LIME_STAINED_GLASS_PANE : Items.WHITE_STAINED_GLASS_PANE,
                "§e⬆ Mejorar", "§7Subir el nivel de protección"));
    }

    // ── INFO PAGE ─────────────────────────────────────────────────────────────
    private void buildInfoPage() {
        List<PlotData> owned = getOwnedPlots();
        int plotIndex = owned.indexOf(data) + 1;

        // Name tag: nombre del plot + ID
        menuInv.setStack(19, namedLore(Items.NAME_TAG,
            "§6" + data.getPlotName(),
            "§7ID: §f#" + plotIndex));

        // Cabeza con skin real del dueño
        menuInv.setStack(20, makePlayerHeadFromServer(data.getOwnerId(), data.getOwnerName(),
            "§eDueño", "§f" + data.getOwnerName()));

        // Nivel
        menuInv.setStack(21, namedLore(itemForSize(data.getSize()),
            tierColor(data.getSize()) + "Nivel: " + data.getSize().displayName,
            "§7Tamaño: §b" + data.getSize().radius + "x" + data.getSize().radius + " bloques"));

        // Miembros (solo info)
        menuInv.setStack(22, namedLore(Items.PAPER,
            "§eIntegrantes",
            "§f" + data.getMembers().size() + " §7miembro(s)"));

        // Coordenadas
        BlockPos c = data.getCenter();
        menuInv.setStack(23, namedLore(Items.COMPASS,
            "§eUbicación",
            "§7X: §f" + c.getX() + "  §7Y: §f" + c.getY() + "  §7Z: §f" + c.getZ()));

        // Tu rol
        menuInv.setStack(24, namedLore(Items.SHIELD,
            "§eTu rol", roleColor(myRole) + myRole.name()));

        // Yunque = renombrar (solo owner)
        if (myRole == PlotData.Role.OWNER) {
            menuInv.setStack(29, namedLore(Items.ANVIL,
                "§6✏ Renombrar parcela",
                "§7Clic para cambiar el nombre"));
        }
    }

    // ── MEMBERS PAGE ─────────────────────────────────────────────────────────
    private void buildMembersPage() {
        boolean canManage = myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN;

        // Botón agregar (solo en esta sección)
        if (canManage) {
            menuInv.setStack(19, namedLore(Items.EMERALD,
                "§a+ Agregar miembro",
                "§7Clic para agregar un jugador",
                "§8El jugador debe estar online"));
        }

        List<Map.Entry<UUID, PlotData.Role>> members = new ArrayList<>(data.getMembers().entrySet());
        int[] slots = {20,21,22,23,24,25,28,29,30,31,32,33,34,37,38};
        int idx = 0;
        for (Map.Entry<UUID, PlotData.Role> entry : members) {
            if (idx >= slots.length) break;
            String name = data.getMemberName(entry.getKey());
            PlotData.Role role = entry.getValue();
            List<String> lore = new ArrayList<>();
            lore.add(roleColor(role) + role.name());
            if (canManage) lore.add("§c🗑 Clic para remover");
            menuInv.setStack(slots[idx], makePlayerHeadFromServer(entry.getKey(), name,
                "§f" + name, lore.toArray(new String[0])));
            idx++;
        }

        if (members.isEmpty()) {
            menuInv.setStack(22, namedLore(Items.BARRIER,
                "§7Sin miembros todavía",
                "§8Usá el botón verde para agregar"));
        }
    }

    // ── UPGRADE PAGE ─────────────────────────────────────────────────────────
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

        // Costo en PAPEL con checkmarks de materiales
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
                String check = ok ? "§a✔" : "§c✗";
                String amountColor = ok ? "§a" : "§c";
                costLore.add(check + " §7" + itemName + ": " + amountColor + has + "§7/" + itemCost.amount);
            }
            menuInv.setStack(23, namedLoreDynamic(Items.PAPER, "§eMateriales requeridos", costLore));
        }

        // Botón mejorar: yunque encantado si puede, normal si no
        if (myRole == PlotData.Role.OWNER) {
            if (canAfford) {
                ItemStack btn = namedLore(Items.ANVIL,
                    "§a⬆ Mejorar a " + next.displayName,
                    "§7Tenés todos los materiales");
                btn.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                menuInv.setStack(SLOT_UPGRADE_BTN, btn);
            } else {
                menuInv.setStack(SLOT_UPGRADE_BTN, namedLore(Items.ANVIL,
                    "§c✗ No podés mejorar todavía"));
            }
        } else {
            menuInv.setStack(SLOT_UPGRADE_BTN, namedLore(Items.BARRIER,
                "§cSolo el dueño puede mejorar"));
        }
    }

    // ── Click Handler ─────────────────────────────────────────────────────────
    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity actor) {
        if (slotIndex < 0 || slotIndex >= SIZE) return;

        ItemStack clicked = menuInv.getStack(slotIndex);
        if (clicked.isEmpty()) return;

        if (slotIndex == SLOT_TAB_INFO)    { page = MenuPage.INFO;    refreshMenu(); return; }
        if (slotIndex == SLOT_TAB_MEMBERS) { page = MenuPage.MEMBERS; refreshMenu(); return; }
        if (slotIndex == SLOT_TAB_UPGRADE) { page = MenuPage.UPGRADE; refreshMenu(); return; }
        if (slotIndex == SLOT_CLOSE)       { player.closeHandledScreen(); return; }

        // Botón mejorar
        if (slotIndex == SLOT_UPGRADE_BTN && myRole == PlotData.Role.OWNER) {
            handleUpgrade(); return;
        }

        // Yunque → renombrar (abrir cartel)
        if (clicked.getItem() == Items.ANVIL && page == MenuPage.INFO && myRole == PlotData.Role.OWNER) {
            openSignForInput(PendingAction.RENAME);
            return;
        }

        // Esmeralda → agregar miembro (abrir cartel) — SOLO en tab miembros
        if (clicked.getItem() == Items.EMERALD && page == MenuPage.MEMBERS &&
                (myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN)) {
            openSignForInput(PendingAction.ADD_MEMBER);
            return;
        }

        // Clic en cabeza → remover miembro
        if (page == MenuPage.MEMBERS && clicked.getItem() == Items.PLAYER_HEAD &&
                (myRole == PlotData.Role.OWNER || myRole == PlotData.Role.ADMIN)) {
            Text nameText = clicked.get(DataComponentTypes.CUSTOM_NAME);
            String memberName = nameText != null ? nameText.getString().replaceAll("§.", "").trim() : "";
            if (!memberName.isEmpty()) removeMemberByName(memberName);
            return;
        }
    }

    // ── Sign Input UI ─────────────────────────────────────────────────────────
    private void openSignForInput(PendingAction action) {
        player.closeHandledScreen();
        // Single tick delay so client processes the close before the sign editor open
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        sw.getServer().execute(() -> {
            if (action == PendingAction.RENAME) {
                SignInputManager.openForRename(player, plotPos);
            } else {
                SignInputManager.openForAddMember(player, plotPos);
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
        if (next == null) {
            playSound(SoundEvents.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        SecurePlotsConfig.UpgradeCost cost = cfg != null ? cfg.getUpgradeCost(fresh.getSize().tier) : null;

        if (cost != null) {
            for (SecurePlotsConfig.UpgradeCost.ItemCost itemCost : cost.items) {
                net.minecraft.util.Identifier id = net.minecraft.util.Identifier.of(itemCost.itemId);
                net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(id);
                if (countItem(player, item) < itemCost.amount) {
                    // Sonido "no podés"
                    playSound(SoundEvents.ENTITY_VILLAGER_NO, 1f, 0.8f);
                    refreshMenu();
                    return;
                }
            }
            for (SecurePlotsConfig.UpgradeCost.ItemCost itemCost : cost.items) {
                net.minecraft.util.Identifier id = net.minecraft.util.Identifier.of(itemCost.itemId);
                net.minecraft.item.Item item = net.minecraft.registry.Registries.ITEM.get(id);
                removeItem(player, item, itemCost.amount);
            }
        }

        PlotSize oldSize = fresh.getSize();
        fresh.setSize(next);

        net.minecraft.block.Block newBlock = com.zhilius.secureplots.block.ModBlocks.fromTier(next.tier);
        sw.setBlockState(plotPos, newBlock.getDefaultState());

        manager.markDirty();

        // Sonido de mejora
        sw.playSound(null, plotPos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1f, 1f);
        sw.playSound(null, plotPos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.PLAYERS, 0.8f, 1.2f);

        // Partículas en el bloque
        spawnUpgradeParticles(sw, plotPos);

        // Cerrar menú para ver las partículas
        player.closeHandledScreen();
        // Enviar nuevo borde al cliente — esto dispara la transición animada
        com.zhilius.secureplots.network.ModPackets.sendShowPlotBorder(player, fresh);
        player.sendMessage(Text.literal("§a✔ ¡Protección mejorada a §e" + next.displayName + "§a!"), false);
    }

    private void spawnUpgradeParticles(ServerWorld sw, BlockPos pos) {
        Vec3d center = Vec3d.ofCenter(pos);
        // Espiral de partículas happy_villager + totem
        for (int i = 0; i < 30; i++) {
            double angle = i * (Math.PI * 2 / 30);
            double r = 1.5;
            sw.spawnParticles(ParticleTypes.HAPPY_VILLAGER,
                center.x + Math.cos(angle) * r,
                center.y + 0.5 + (i * 0.05),
                center.z + Math.sin(angle) * r,
                1, 0, 0, 0, 0);
        }
        for (int i = 0; i < 15; i++) {
            sw.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING,
                center.x + (Math.random() - 0.5) * 2,
                center.y + Math.random() * 2,
                center.z + (Math.random() - 0.5) * 2,
                1, 0, 0.1, 0, 0.1);
        }
        sw.spawnParticles(ParticleTypes.ENCHANT,
            center.x, center.y + 1, center.z,
            40, 0.5, 0.5, 0.5, 0.5);
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

        // Sonido al eliminar miembro
        playSound(SoundEvents.ENTITY_ITEM_BREAK, 1f, 0.8f);

        player.sendMessage(Text.literal("§a✔ " + name + " eliminado."), false);
        refreshMenu();
    }

    private void refreshMenu() {
        buildMenu();
        this.sendContentUpdates();
    }

    private void playSound(net.minecraft.sound.SoundEvent sound, float volume, float pitch) {
        if (player.getWorld() instanceof ServerWorld sw) {
            sw.playSound(null, player.getBlockPos(), sound, SoundCategory.PLAYERS, volume, pitch);
        }
    }

    private List<PlotData> getOwnedPlots() {
        if (!(player.getWorld() instanceof ServerWorld sw)) return List.of(data);
        return PlotManager.getOrCreate(sw).getPlayerPlots(player.getUuid());
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

    /**
     * Crea una cabeza de jugador con la skin real obtenida del servidor.
     * Usa el GameProfile cacheado del servidor si el jugador está online,
     * o construye uno con UUID para que el cliente lo resuelva.
     */
    private ItemStack makePlayerHeadFromServer(UUID uuid, String playerName, String displayName, String... loreLines) {
        ItemStack stack = namedLore(Items.PLAYER_HEAD, displayName, loreLines);
        try {
            // Intentar obtener el perfil real con texturas del servidor
            ServerPlayerEntity onlinePlayer = player.getServer().getPlayerManager().getPlayer(uuid);
            GameProfile profile;
            if (onlinePlayer != null) {
                profile = onlinePlayer.getGameProfile();
            } else {
                // Jugador offline: buscar en UserCache
                var userCache = player.getServer().getUserCache();
                if (userCache != null) {
                    var cached = userCache.getByUuid(uuid);
                    profile = cached.orElse(new GameProfile(uuid, playerName));
                } else {
                    profile = new GameProfile(uuid, playerName);
                }
            }
            stack.set(DataComponentTypes.PROFILE,
                new ProfileComponent(
                    Optional.ofNullable(profile.getName()),
                    Optional.of(profile.getId()),
                    profile.getProperties()
                ));
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

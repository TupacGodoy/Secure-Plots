package com.zhilius.secureplots.item;
import com.zhilius.secureplots.config.SecurePlotsConfig;

import com.zhilius.secureplots.network.ModPackets;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotManager;
import com.zhilius.secureplots.screen.PlotMenuHandler;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.item.Item.TooltipContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PlotblueprintItem extends Item {

    public PlotblueprintItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            PlotManager manager = PlotManager.getOrCreate((ServerWorld) world);
            PlotData atPos = manager.getPlotAt(player.getBlockPos());
            boolean isAdmin = player.getCommandTags().contains(SecurePlotsConfig.INSTANCE != null ? SecurePlotsConfig.INSTANCE.adminTag : "plot_admin");

            if (player.isSneaking()) {
                // Shift+click: mostrar rayos del borde
                if (atPos != null && (atPos.getRoleOf(player.getUuid()) != PlotData.Role.VISITOR || isAdmin)) {
                    ModPackets.sendShowPlotBorder(player, atPos);
                } else {
                    player.sendMessage(Text.literal("✗ No estás dentro de una protección tuya.").formatted(Formatting.RED), false);
                }
            } else {
                if (atPos != null && (atPos.getRoleOf(player.getUuid()) != PlotData.Role.VISITOR || isAdmin)) {
                    // Dentro de una plot propia → abrir menú
                    BlockPos plotPos = atPos.getCenter();
                    player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                            (syncId, inv, p) -> new PlotMenuHandler(syncId, inv, plotPos, atPos, PlotMenuHandler.MenuPage.INFO),
                            Text.literal("🛡 " + atPos.getPlotName())
                    ));
                } else {
                    // Fuera de protección → abrir menú de selección con lista de plots + TP
                    List<PlotData> playerPlots = manager.getPlayerPlots(player.getUuid());
                    if (playerPlots.isEmpty()) {
                        player.sendMessage(Text.literal("✗ No tenés protecciones colocadas.").formatted(Formatting.RED), false);
                    } else {
                        openPlotListMenu(player, playerPlots, manager);
                    }
                }
            }
        }
        return TypedActionResult.success(user.getStackInHand(hand));
    }

    /**
     * Abre un menú inventario con la lista de plots del jugador.
     * Cada ítem muestra nombre, coordenadas y permite hacer TP si el flag está activo.
     */
    private static void openPlotListMenu(ServerPlayerEntity player, List<PlotData> plots, PlotManager manager) {
        int rows = Math.min(6, (int) Math.ceil((plots.size() + 9) / 9.0) + 1);
        rows = Math.max(rows, 2);
        int size = rows * 9;
        SimpleInventory inv = new SimpleInventory(size);

        // Header
        for (int i = 0; i < 9; i++)
            inv.setStack(i, named(Items.BLACK_STAINED_GLASS_PANE, " "));
        inv.setStack(4, namedLore(Items.MAP, "§e🗺 Tus Parcelas",
            "§7Clic: Teleportarte",
            "§7Shift+Clic: Abrir menú de la parcela"));
        inv.setStack(8, named(Items.BARRIER, "§c✕ Cerrar"));

        // Plot entries
        for (int i = 0; i < plots.size() && i < size - 9; i++) {
            PlotData p = plots.get(i);
            BlockPos c = p.getCenter();
            boolean tpOn = p.hasFlag(PlotData.Flag.ALLOW_TP);
            String tpStatus = tpOn ? "§aTP público: ON" : "§7TP público: OFF";

            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("§7X: §f" + c.getX() + "  §7Z: §f" + c.getZ()).styled(s -> s.withItalic(false)));
            lore.add(Text.literal("§7Nivel: §b" + p.getSize().getDisplayName()).styled(s -> s.withItalic(false)));
            lore.add(Text.literal(tpStatus).styled(s -> s.withItalic(false)));
            lore.add(Text.literal("§eClic: Teleportarte aquí").styled(s -> s.withItalic(false)));
            lore.add(Text.literal("§7Shift+Clic: Abrir menú").styled(s -> s.withItalic(false)));

            ItemStack plotItem = new ItemStack(itemForTier(p.getSize().tier));
            plotItem.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(tierColor(p.getSize().tier) + p.getPlotName()).styled(s -> s.withItalic(false)));
            plotItem.set(DataComponentTypes.LORE, new LoreComponent(lore));
            inv.setStack(9 + i, plotItem);
        }

        final int invRows = rows;
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, p) -> new PlotListMenuHandler(syncId, playerInv, inv, plots, manager, invRows),
            Text.literal("§e🗺 Mis Parcelas")
        ));
    }

    // ── Inner handler for plot list menu ──────────────────────────────────────
    public static class PlotListMenuHandler extends GenericContainerScreenHandler {
        private final List<PlotData> plots;
        private final PlotManager manager;
        private final ServerPlayerEntity player;

        public PlotListMenuHandler(int syncId, PlayerInventory playerInv, SimpleInventory inv,
                                   List<PlotData> plots, PlotManager manager, int rows) {
            super(rows == 2 ? ScreenHandlerType.GENERIC_9X2 :
                  rows == 3 ? ScreenHandlerType.GENERIC_9X3 :
                  rows == 4 ? ScreenHandlerType.GENERIC_9X4 :
                  rows == 5 ? ScreenHandlerType.GENERIC_9X5 : ScreenHandlerType.GENERIC_9X6,
                  syncId, playerInv, inv, rows);
            this.plots = plots;
            this.manager = manager;
            this.player = (ServerPlayerEntity) playerInv.player;
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity actor) {
            int size = slots.size() - 36; // subtract player inv slots
            if (slotIndex < 0 || slotIndex >= size) return;

            // Cerrar
            if (slotIndex == 8) { player.closeHandledScreen(); return; }

            // Plot slots start at index 9
            int plotIdx = slotIndex - 9;
            if (plotIdx < 0 || plotIdx >= plots.size()) return;

            PlotData plot = plots.get(plotIdx);

            // Shift+click → abrir menú de la plot
            if (actionType == SlotActionType.PICKUP_ALL || button == 1) {
                player.closeHandledScreen();
                if (!(player.getWorld() instanceof ServerWorld sw)) return;
                PlotData fresh = manager.getPlot(plot.getCenter());
                if (fresh == null) return;
                sw.getServer().execute(() ->
                    player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                        (syncId, inv, p) -> new PlotMenuHandler(syncId, inv, fresh.getCenter(), fresh, PlotMenuHandler.MenuPage.INFO),
                        Text.literal("🛡 " + fresh.getPlotName())
                    ))
                );
                return;
            }

            // Click normal → TP
            if (!(player.getWorld() instanceof ServerWorld sw)) return;
            BlockPos c = plot.getCenter();
            double tpY = sw.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                new BlockPos(c.getX(), c.getY(), c.getZ())).getY();
            player.closeHandledScreen();
            player.teleport(sw, c.getX() + 0.5, tpY, c.getZ() + 0.5,
                Set.of(), player.getYaw(), player.getPitch());
            player.sendMessage(Text.literal("§a✔ Teleportado a §e" + plot.getPlotName()), false);
            sw.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
        }

        @Override public boolean canUse(PlayerEntity player) { return true; }
        @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private static ItemStack named(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).styled(s -> s.withItalic(false)));
        return stack;
    }

    private static ItemStack namedLore(Item item, String name, String... loreLines) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).styled(s -> s.withItalic(false)));
        List<Text> lore = new ArrayList<>();
        for (String line : loreLines)
            lore.add(Text.literal(line).styled(s -> s.withItalic(false)));
        stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return stack;
    }

    private static Item itemForTier(int tier) {
        return switch (tier) {
            case 0 -> Items.COPPER_INGOT;
            case 1 -> Items.GOLD_INGOT;
            case 2 -> Items.EMERALD;
            case 3 -> Items.DIAMOND;
            case 4 -> Items.NETHERITE_INGOT;
            default -> Items.PAPER;
        };
    }

    private static String tierColor(int tier) {
        return switch (tier) {
            case 0 -> "§6";
            case 1 -> "§e";
            case 2 -> "§a";
            case 3 -> "§b";
            case 4 -> "§5";
            default -> "§f";
        };
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.literal("Clic: abre el menú / lista de parcelas").formatted(Formatting.GRAY));
        tooltip.add(Text.literal("Shift+Clic: muestra los límites de la protección").formatted(Formatting.DARK_GRAY));
    }
}

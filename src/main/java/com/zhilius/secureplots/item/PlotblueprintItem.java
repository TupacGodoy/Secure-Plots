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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PlotblueprintItem extends Item {

    public PlotblueprintItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        // Skip off-hand if main hand also has a blueprint (avoid double action)
        if (hand == Hand.OFF_HAND) {
            ItemStack main = user.getMainHandStack();
            if (!main.isEmpty() && main.getItem() instanceof PlotblueprintItem)
                return TypedActionResult.pass(user.getStackInHand(hand));
        }

        if (!world.isClient && user instanceof ServerPlayerEntity player) {
            PlotManager manager = PlotManager.getOrCreate((ServerWorld) world);
            PlotData atPos    = manager.getPlotAt(player.getBlockPos());
            boolean isAdmin   = isAdmin(player);

            if (player.isSneaking()) {
                // Shift+click: show plot border
                if (atPos != null && (atPos.getRoleOf(player.getUuid()) != PlotData.Role.VISITOR || isAdmin)) {
                    ModPackets.sendShowPlotBorder(player, atPos);
                } else {
                    player.sendMessage(Text.translatable("sp.block.not_owner"), false);
                }
            } else {
                // Normal click: open plot menu if inside own plot, otherwise open plot list
                if (atPos != null && (atPos.getRoleOf(player.getUuid()) != PlotData.Role.VISITOR || isAdmin)) {
                    BlockPos plotPos = atPos.getCenter();
                    player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                        (syncId, inv, p) -> new PlotMenuHandler(syncId, inv, plotPos, atPos, PlotMenuHandler.MenuPage.INFO),
                        Text.literal("🛡 " + atPos.getPlotName())
                    ));
                } else {
                    List<PlotData> playerPlots = manager.getPlayerPlots(player.getUuid());
                    if (playerPlots.isEmpty()) {
                        player.sendMessage(Text.translatable("sp.error.no_plots"), false);
                    } else {
                        openPlotListMenu(player, playerPlots, manager);
                    }
                }
            }
        }
        return TypedActionResult.success(user.getStackInHand(hand));
    }

    /** Opens an inventory menu listing all the player's plots with TP and menu options. */
    private static void openPlotListMenu(ServerPlayerEntity player, List<PlotData> plots, PlotManager manager) {
        int rows = Math.max(2, Math.min(6, (int) Math.ceil((plots.size() + 9) / 9.0) + 1));
        int size = rows * 9;
        SimpleInventory inv = new SimpleInventory(size);

        // Header row
        for (int i = 0; i < 9; i++) inv.setStack(i, named(Items.BLACK_STAINED_GLASS_PANE, " "));
        inv.setStack(4, namedLore(Items.MAP, "§e🗺 Your Plots",
            "§7Click: Teleport here",
            "§7Right-click: Open plot menu"));
        inv.setStack(8, named(Items.BARRIER, "§c✕ Close"));

        // Plot entries
        for (int i = 0; i < plots.size() && i < size - 9; i++) {
            PlotData p  = plots.get(i);
            BlockPos c  = p.getCenter();
            boolean tpOn = p.hasFlag(PlotData.Flag.ALLOW_TP);

            List<Text> lore = new ArrayList<>();
            lore.add(Text.literal("§7X: §f" + c.getX() + "  §7Z: §f" + c.getZ()).styled(s -> s.withItalic(false)));
            lore.add(Text.translatable("sp.blueprint.lore_tier", p.getSize().getDisplayName()).styled(s -> s.withItalic(false)));
            lore.add(Text.literal(tpOn ? "§aPublic TP: ON" : "§7Public TP: OFF").styled(s -> s.withItalic(false)));
            lore.add(Text.translatable("sp.blueprint.lore_click").styled(s -> s.withItalic(false)));
            lore.add(Text.translatable("sp.blueprint.lore_rightclick").styled(s -> s.withItalic(false)));

            ItemStack plotItem = new ItemStack(itemForTier(p.getSize().tier));
            plotItem.set(DataComponentTypes.CUSTOM_NAME,
                Text.literal(tierColor(p.getSize().tier) + p.getPlotName()).styled(s -> s.withItalic(false)));
            plotItem.set(DataComponentTypes.LORE, new LoreComponent(lore));
            inv.setStack(9 + i, plotItem);
        }

        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, playerInv, p) -> new PlotListMenuHandler(syncId, playerInv, inv, plots, manager, rows),
            Text.literal("§e🗺 My Plots")
        ));
    }

    // ── Plot list menu handler ────────────────────────────────────────────────

    public static class PlotListMenuHandler extends GenericContainerScreenHandler {
        private final List<PlotData>      plots;
        private final PlotManager         manager;
        private final ServerPlayerEntity  player;

        public PlotListMenuHandler(int syncId, PlayerInventory playerInv, SimpleInventory inv,
                                   List<PlotData> plots, PlotManager manager, int rows) {
            super(rowsToType(rows), syncId, playerInv, inv, rows);
            this.plots   = plots;
            this.manager = manager;
            this.player  = (ServerPlayerEntity) playerInv.player;
        }

        private static ScreenHandlerType<GenericContainerScreenHandler> rowsToType(int rows) {
            return switch (rows) {
                case 2  -> ScreenHandlerType.GENERIC_9X2;
                case 3  -> ScreenHandlerType.GENERIC_9X3;
                case 4  -> ScreenHandlerType.GENERIC_9X4;
                case 5  -> ScreenHandlerType.GENERIC_9X5;
                default -> ScreenHandlerType.GENERIC_9X6;
            };
        }

        @Override
        public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity actor) {
            int size = slots.size() - 36;
            if (slotIndex < 0 || slotIndex >= size) return;

            if (slotIndex == 8) { player.closeHandledScreen(); return; }

            int plotIdx = slotIndex - 9;
            if (plotIdx < 0 || plotIdx >= plots.size()) return;

            PlotData plot = plots.get(plotIdx);

            // Right-click or double-click → open plot menu
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

            // Left-click → teleport
            if (!(player.getWorld() instanceof ServerWorld sw)) return;
            BlockPos c  = plot.getCenter();
            double tpY  = sw.getTopPosition(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, c).getY();
            player.closeHandledScreen();
            player.teleport(sw, c.getX() + 0.5, tpY, c.getZ() + 0.5,
                Set.of(), player.getYaw(), player.getPitch());
            player.sendMessage(Text.translatable("sp.tp.success", plot.getPlotName()), false);
            sw.playSound(null, player.getBlockPos(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 1f);
        }

        @Override public boolean canUse(PlayerEntity player)               { return true; }
        @Override public ItemStack quickMove(PlayerEntity player, int slot) { return ItemStack.EMPTY; }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean isAdmin(ServerPlayerEntity player) {
        String tag = SecurePlotsConfig.INSTANCE != null ? SecurePlotsConfig.INSTANCE.adminTag : "plot_admin";
        return player.getCommandTags().contains(tag);
    }

    private static ItemStack named(Item item, String name) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name).styled(s -> s.withItalic(false)));
        return stack;
    }

    private static ItemStack namedLore(Item item, String name, String... loreLines) {
        ItemStack stack = named(item, name);
        List<Text> lore = new ArrayList<>();
        for (String line : loreLines)
            lore.add(Text.literal(line).styled(s -> s.withItalic(false)));
        stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
        return stack;
    }

    private static Item itemForTier(int tier) {
        return switch (tier) {
            case 0  -> Items.COPPER_INGOT;
            case 1  -> Items.GOLD_INGOT;
            case 2  -> Items.EMERALD;
            case 3  -> Items.DIAMOND;
            case 4  -> Items.NETHERITE_INGOT;
            default -> Items.PAPER;
        };
    }

    private static String tierColor(int tier) {
        return switch (tier) {
            case 0  -> "§6";
            case 1  -> "§e";
            case 2  -> "§a";
            case 3  -> "§b";
            case 4  -> "§5";
            default -> "§f";
        };
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("sp.blueprint.tooltip_click").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("sp.blueprint.tooltip_shift").formatted(Formatting.DARK_GRAY));
    }
}
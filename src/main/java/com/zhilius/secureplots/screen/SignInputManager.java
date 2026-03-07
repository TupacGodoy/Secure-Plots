package com.zhilius.secureplots.screen;

import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotManager;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SignEditorOpenS2CPacket;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sign input via fake client-side packets only.
 * 1. BlockUpdateS2CPacket     → tells client there's an oak_wall_sign at fakePos
 * 2. BlockEntityUpdateS2CPacket → gives the client a valid SignBlockEntity NBT
 * 3. SignEditorOpenS2CPacket  → opens the sign editor
 * The mixin cancels the UpdateSignC2SPacket response before server validation.
 */
public class SignInputManager {

    public enum InputType { RENAME, ADD_MEMBER }

    public record PendingInput(BlockPos fakePos, BlockPos plotPos, InputType type) {}

    private static final Map<UUID, PendingInput> pending = new ConcurrentHashMap<>();

    public static void openForRename(ServerPlayerEntity player, BlockPos plotPos) {
        open(player, plotPos, InputType.RENAME);
    }

    public static void openForAddMember(ServerPlayerEntity player, BlockPos plotPos) {
        open(player, plotPos, InputType.ADD_MEMBER);
    }

    private static void open(ServerPlayerEntity player, BlockPos plotPos, InputType type) {
        // Use a position at player's feet Y + 2 so it's always within range
        // and predictably close to the player
        BlockPos fakePos = new BlockPos(player.getBlockX(), player.getBlockY() + 2, player.getBlockZ());

        pending.put(player.getUuid(), new PendingInput(fakePos, plotPos, type));

        // 1. Send block state (oak_wall_sign facing north — client needs this)
        player.networkHandler.sendPacket(
            new BlockUpdateS2CPacket(fakePos, Blocks.OAK_WALL_SIGN.getDefaultState()));

        // 2. Send SignBlockEntity NBT via reflection (constructor is private in 1.21)
        try {
            NbtCompound signNbt = new NbtCompound();
            signNbt.putString("id", "minecraft:sign");
            signNbt.putInt("x", fakePos.getX());
            signNbt.putInt("y", fakePos.getY());
            signNbt.putInt("z", fakePos.getZ());
            NbtCompound front = new NbtCompound();
            front.putString("color", "black");
            front.putBoolean("has_glowing_text", false);
            signNbt.put("front_text", front);
            NbtCompound back = new NbtCompound();
            back.putString("color", "black");
            back.putBoolean("has_glowing_text", false);
            signNbt.put("back_text", back);
            signNbt.putBoolean("is_waxed", false);

            var pktClass = net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.class;
            var ctor = pktClass.getDeclaredConstructor(
                net.minecraft.util.math.BlockPos.class,
                net.minecraft.block.entity.BlockEntityType.class,
                NbtCompound.class);
            ctor.setAccessible(true);
            player.networkHandler.sendPacket(
                (Packet<?>) ctor.newInstance(
                    fakePos, net.minecraft.block.entity.BlockEntityType.SIGN, signNbt));
        } catch (Exception e) {
            com.zhilius.secureplots.SecurePlots.LOGGER.error("SecurePlots: failed to send sign BE packet: {}", e.getMessage());
        }

        // 3. Open the sign editor UI on the client
        player.networkHandler.sendPacket(new SignEditorOpenS2CPacket(fakePos, true));
    }

    public static boolean hasPending(UUID uuid) {
        return pending.containsKey(uuid);
    }

    /**
     * Called from UpdateSignMixin before server-side validation.
     */
    public static void handleSignUpdate(ServerPlayerEntity player, BlockPos pos, String[] lines) {
        UUID uuid = player.getUuid();
        PendingInput input = pending.remove(uuid);
        if (input == null) return;
        if (!input.fakePos().equals(pos)) {
            pending.put(uuid, input); // not ours
            return;
        }

        // Restore air to the client at fakePos (clean up the fake block)
        player.networkHandler.sendPacket(
            new BlockUpdateS2CPacket(input.fakePos(), Blocks.AIR.getDefaultState()));

        // First non-empty line = input text
        String text = "";
        for (String line : lines) {
            if (line != null && !line.isBlank()) { text = line.trim(); break; }
        }

        if (text.isEmpty()) return; // cancelled

        switch (input.type()) {
            case RENAME     -> handleRename(player, input.plotPos(), text);
            case ADD_MEMBER -> handleAddMember(player, input.plotPos(), text);
        }
    }

    // ── Rename ────────────────────────────────────────────────────────────────
    private static void handleRename(ServerPlayerEntity player, BlockPos plotPos, String newName) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        PlotManager manager = PlotManager.getOrCreate(sw);
        PlotData data = manager.getPlot(plotPos);
        if (data == null) {
            player.sendMessage(Text.literal("✗ Protección no encontrada.").formatted(Formatting.RED), false);
            return;
        }
        boolean isAdmin = player.getCommandTags().contains("plot_admin");
        if (!data.getOwnerId().equals(player.getUuid()) && !isAdmin) {
            player.sendMessage(Text.literal("✗ Solo el dueño puede renombrar.").formatted(Formatting.RED), false);
            reopenMenu(player, plotPos);
            return;
        }
        newName = newName.trim();
        if (newName.isEmpty() || newName.length() > 32) {
            player.sendMessage(Text.literal("✗ Nombre inválido (1-32 caracteres).").formatted(Formatting.RED), false);
            reopenMenu(player, plotPos);
            return;
        }
        for (PlotData p : manager.getPlayerPlots(data.getOwnerId())) {
            if (p != data && p.getPlotName().equalsIgnoreCase(newName)) {
                player.sendMessage(Text.literal("✗ Ya existe una protección con ese nombre.").formatted(Formatting.RED), false);
                reopenMenu(player, plotPos);
                return;
            }
        }
        String old = data.getPlotName();
        data.setPlotName(newName);
        manager.markDirty();
        sw.playSound(null, player.getBlockPos(),
            SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(),
            SoundCategory.PLAYERS, 1f, 2f);
        player.sendMessage(
            Text.literal("✔ ").formatted(Formatting.GREEN)
                .append(Text.literal("\"" + old + "\"").formatted(Formatting.GRAY))
                .append(Text.literal(" → ").formatted(Formatting.GREEN))
                .append(Text.literal("\"" + newName + "\"").formatted(Formatting.YELLOW)),
            false);
        reopenMenu(player, plotPos);
    }

    // ── Add Member ────────────────────────────────────────────────────────────
    private static void handleAddMember(ServerPlayerEntity player, BlockPos plotPos, String targetName) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        PlotManager manager = PlotManager.getOrCreate(sw);
        PlotData data = manager.getPlot(plotPos);
        if (data == null) {
            player.sendMessage(Text.literal("✗ Protección no encontrada.").formatted(Formatting.RED), false);
            return;
        }
        boolean isAdmin = player.getCommandTags().contains("plot_admin");
        PlotData.Role myRole = data.getRoleOf(player.getUuid());
        if (myRole != PlotData.Role.OWNER && myRole != PlotData.Role.ADMIN && !isAdmin) {
            reopenMenu(player, plotPos);
            return;
        }
        ServerPlayerEntity target = player.getServer().getPlayerManager().getPlayer(targetName);
        if (target == null) {
            player.sendMessage(Text.literal("✗ \"" + targetName + "\" no está online.").formatted(Formatting.RED), false);
            reopenMenu(player, plotPos);
            return;
        }
        if (target.getUuid().equals(player.getUuid()) && !isAdmin) {
            player.sendMessage(Text.literal("✗ No podés agregarte a vos mismo.").formatted(Formatting.RED), false);
            reopenMenu(player, plotPos);
            return;
        }
        if (data.getRoleOf(target.getUuid()) != PlotData.Role.VISITOR) {
            player.sendMessage(Text.literal("✗ " + targetName + " ya tiene acceso.").formatted(Formatting.YELLOW), false);
            reopenMenu(player, plotPos);
            return;
        }
        data.addMember(target.getUuid(), target.getName().getString(), PlotData.Role.MEMBER);
        manager.markDirty();
        sw.playSound(null, player.getBlockPos(),
            SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
            SoundCategory.PLAYERS, 1f, 1.5f);
        player.sendMessage(Text.literal("✔ " + targetName + " agregado como miembro.").formatted(Formatting.GREEN), false);
        target.sendMessage(Text.literal("✔ Fuiste agregado a \"" + data.getPlotName() + "\" de " + player.getName().getString()).formatted(Formatting.GREEN), false);
        reopenMenu(player, plotPos);
    }

    private static void reopenMenu(ServerPlayerEntity player, BlockPos plotPos) {
        if (!(player.getWorld() instanceof ServerWorld sw)) return;
        PlotManager manager = PlotManager.getOrCreate(sw);
        PlotData fresh = manager.getPlot(plotPos);
        if (fresh == null) return;
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
            (syncId, inv, p) -> new PlotMenuHandler(syncId, inv, plotPos, fresh, PlotMenuHandler.MenuPage.INFO),
            Text.literal("🛡 " + fresh.getPlotName())
        ));
    }
}

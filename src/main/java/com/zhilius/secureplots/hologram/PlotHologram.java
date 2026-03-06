package com.zhilius.secureplots.hologram;

import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotSize;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class PlotHologram {

    // Bloques de alto que necesita el holograma para visualizarse completo
    private static final int HOLOGRAM_HEIGHT = 4;

    private static final Map<UUID, UUID> active = new HashMap<>();       // posKey → entity UUID
    private static final Map<UUID, Integer> tickMap = new HashMap<>();   // posKey → ticks remaining
    private static final Map<UUID, BlockPos> blockPosMap = new HashMap<>(); // posKey → block pos
    private static final Map<UUID, ServerWorld> worldMap = new HashMap<>();  // posKey → world

    public static void registerTicker() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            List<UUID> toRemove = new ArrayList<>();

            for (Map.Entry<UUID, Integer> e : tickMap.entrySet()) {
                UUID posKey = e.getKey();
                int remaining = e.getValue() - 1;

                if (remaining <= 0) {
                    toRemove.add(posKey);
                    discardEntity(server, posKey);
                    continue;
                }

                // Check every tick if a block was placed in the hologram space
                ServerWorld w = worldMap.get(posKey);
                BlockPos bp = blockPosMap.get(posKey);
                if (w != null && bp != null && isBlocked(w, bp)) {
                    toRemove.add(posKey);
                    discardEntity(server, posKey);
                    continue;
                }

                e.setValue(remaining);
            }

            for (UUID key : toRemove) {
                tickMap.remove(key);
                blockPosMap.remove(key);
                worldMap.remove(key);
                active.remove(key);
            }
        });
    }

    private static void discardEntity(net.minecraft.server.MinecraftServer server, UUID posKey) {
        UUID entityId = active.get(posKey);
        if (entityId == null) return;
        for (ServerWorld w : server.getWorlds()) {
            var entity = w.getEntity(entityId);
            if (entity != null) { entity.discard(); break; }
        }
    }

    /** Returns true if any block in the hologram space is opaque */
    private static boolean isBlocked(ServerWorld world, BlockPos blockPos) {
        for (int i = 1; i <= HOLOGRAM_HEIGHT; i++) {
            if (world.getBlockState(blockPos.up(i)).isOpaque()) return true;
        }
        return false;
    }

    /** Returns true if there is enough clear space above blockPos for the hologram */
    public static boolean hasSpace(ServerWorld world, BlockPos blockPos) {
        return !isBlocked(world, blockPos);
    }

    public static void remove(ServerWorld world, BlockPos pos) {
        UUID posKey = posKey(pos);
        UUID entityId = active.get(posKey);
        if (entityId != null) {
            var entity = world.getEntity(entityId);
            if (entity != null) entity.discard();
        }
        active.remove(posKey);
        tickMap.remove(posKey);
        blockPosMap.remove(posKey);
        worldMap.remove(posKey);
    }

    public static void spawn(ServerWorld world, BlockPos blockPos, PlotData data, int durationTicks, float playerYaw) {
        UUID posKey = posKey(blockPos);

        // Remove previous hologram at this position
        UUID oldId = active.get(posKey);
        if (oldId != null) {
            var old = world.getEntity(oldId);
            if (old != null) old.discard();
            active.remove(posKey);
            tickMap.remove(posKey);
            blockPosMap.remove(posKey);
            worldMap.remove(posKey);
        }

        // Don't spawn if space is blocked
        if (!hasSpace(world, blockPos)) return;

        double x = blockPos.getX() + 0.5;
        double y = blockPos.getY() + 1.5;
        double z = blockPos.getZ() + 0.5;

        DisplayEntity.TextDisplayEntity text = new DisplayEntity.TextDisplayEntity(
                net.minecraft.entity.EntityType.TEXT_DISPLAY, world);
        text.setPosition(x, y, z);
        text.setYaw(playerYaw + 180f);

        NbtCompound nbt = text.writeNbt(new NbtCompound());

        String textJson = Text.Serialization.toJsonString(buildText(data), world.getRegistryManager());
        nbt.putString("text", textJson);

        nbt.putInt("background", 0x80404040);
        nbt.putByte("default_background", (byte) 0);
        nbt.putByte("text_opacity", (byte) -1);
        nbt.putByte("shadow", (byte) 1);
        nbt.putByte("see_through", (byte) 0);
        nbt.putString("alignment", "center");
        nbt.putString("billboard", "center");
        nbt.putInt("line_width", 400);

        NbtList scale = new NbtList();
        scale.add(net.minecraft.nbt.NbtFloat.of(0.6f));
        scale.add(net.minecraft.nbt.NbtFloat.of(0.6f));
        scale.add(net.minecraft.nbt.NbtFloat.of(0.6f));
        nbt.put("scale", scale);

        text.readNbt(nbt);
        world.spawnEntity(text);

        active.put(posKey, text.getUuid());
        tickMap.put(posKey, durationTicks);
        blockPosMap.put(posKey, blockPos);
        worldMap.put(posKey, world);
    }

    private static UUID posKey(BlockPos pos) {
        return UUID.nameUUIDFromBytes((pos.getX() + "," + pos.getY() + "," + pos.getZ()).getBytes());
    }

    private static Text buildText(PlotData data) {
        String name = (data.getPlotName() != null && !data.getPlotName().isBlank())
                ? data.getPlotName().toUpperCase()
                : "PARCELA PROTEGIDA";

        int size = data.getSize().radius;
        PlotSize next = data.getSize().next();
        String nextLine = (next != null)
                ? "§e⬆ §7Siguiente: §b" + next.displayName
                : "§6★ §eNivel Máximo §6★";

        String membersStr = data.getMembers().isEmpty() ? "§7Ninguno" : "§a" + data.getMembers().size();

        return Text.literal(
                "§8▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔\n" +
                "§6§l" + name + "\n" +
                "§8───────────────\n" +
                "§7Dueño: §f" + data.getOwnerName() + "\n" +
                "§7Nivel: §b" + data.getSize().displayName + "\n" +
                "§7Tamaño: §b" + size + "x" + size + " bloques\n" +
                "§7Miembros: " + membersStr + "\n" +
                "§8───────────────\n" +
                nextLine + "\n" +
                "§8───────────────\n" +
                "§e» §7Usá el §6Plano §7para gestionar\n" +
                "§8▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁");
    }
}

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

    private static final Map<UUID, UUID> active = new HashMap<>(); // blockKey → entity UUID

    public static void registerTicker() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            Map<UUID, Integer> ticks = tickMap;
            List<UUID> toRemove = new ArrayList<>();
            for (Map.Entry<UUID, Integer> e : ticks.entrySet()) {
                int remaining = e.getValue() - 1;
                if (remaining <= 0) {
                    toRemove.add(e.getKey());
                    UUID entityId = active.get(e.getKey());
                    if (entityId != null) {
                        for (ServerWorld w : server.getWorlds()) {
                            var entity = w.getEntity(entityId);
                            if (entity != null) {
                                entity.discard();
                                break;
                            }
                        }
                        active.remove(e.getKey());
                    }
                } else {
                    e.setValue(remaining);
                }
            }
            toRemove.forEach(tickMap::remove);
        });
    }

    private static final Map<UUID, Integer> tickMap = new HashMap<>();

    public static void remove(ServerWorld world, BlockPos pos) {
        UUID posKey = UUID.nameUUIDFromBytes(
                (pos.getX() + "," + pos.getY() + "," + pos.getZ()).getBytes());
        UUID entityId = active.get(posKey);
        if (entityId != null) {
            var entity = world.getEntity(entityId);
            if (entity != null)
                entity.discard();
            active.remove(posKey);
            tickMap.remove(posKey);
        }
    }

    public static void spawn(ServerWorld world, BlockPos blockPos, PlotData data, int durationTicks, float playerYaw) {
        // Clave única por posición del bloque
        UUID posKey = UUID.nameUUIDFromBytes(
                (blockPos.getX() + "," + blockPos.getY() + "," + blockPos.getZ()).getBytes());

        // Eliminar holograma anterior en esta posición si existe
        UUID oldId = active.get(posKey);
        if (oldId != null) {
            var old = world.getEntity(oldId);
            if (old != null)
                old.discard();
            active.remove(posKey);
            tickMap.remove(posKey);
        }

        double x = blockPos.getX() + 0.5;
        double y = blockPos.getY() + 2.0;
        double z = blockPos.getZ() + 0.5;

        // Solo TextDisplayEntity — sin panel, sin item
        DisplayEntity.TextDisplayEntity text = new DisplayEntity.TextDisplayEntity(
                net.minecraft.entity.EntityType.TEXT_DISPLAY, world);
        text.setPosition(x, y, z);
        // Orientar hacia donde miraba el jugador (yaw + 180 para que la cara frontal
        // quede de frente)
        text.setYaw(playerYaw + 180f);

        NbtCompound nbt = text.writeNbt(new NbtCompound());

        String textJson = Text.Serialization.toJsonString(buildText(data), world.getRegistryManager());
        nbt.putString("text", textJson);

        // Fondo semitransparente oscuro (estilo vanilla nametag)
        nbt.putInt("background", 0x80404040);
        nbt.putByte("default_background", (byte) 0);
        nbt.putByte("text_opacity", (byte) -1);
        nbt.putByte("shadow", (byte) 1);
        nbt.putByte("see_through", (byte) 0);
        nbt.putString("alignment", "center");
        nbt.putString("billboard", "fixed"); // siempre mira al jugador en todos los ejes
        nbt.putInt("line_width", 200);

        NbtList scale = new NbtList();
        scale.add(net.minecraft.nbt.NbtFloat.of(1.0f));
        scale.add(net.minecraft.nbt.NbtFloat.of(1.0f));
        scale.add(net.minecraft.nbt.NbtFloat.of(1.0f));
        nbt.put("scale", scale);

        text.readNbt(nbt);
        world.spawnEntity(text);

        active.put(posKey, text.getUuid());
        tickMap.put(posKey, durationTicks);
    }

    private static Text buildText(PlotData data) {
        String name = (data.getPlotName() != null && !data.getPlotName().isBlank())
                ? data.getPlotName().toUpperCase()
                : "PARCELA PROTEGIDA";

        PlotSize next = data.getSize().next();
        String nextLine = (next != null)
                ? "§e⬆ §7Siguiente: §b" + next.displayName
                : "§6★ §eNivel Máximo §6★";

        return Text.literal(
                "§8▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔▔\n" +
                        "§6§l" + name + "\n" +
                        "§8───────────────\n" +
                        "§7Dueño  §f" + data.getOwnerName() + "\n" +
                        "§7Nivel  §b" + data.getSize().displayName + "\n" +
                        "§7Miembros  §a" + data.getMembers().size() + "\n" +
                        "§8───────────────\n" +
                        nextLine + "\n" +
                        "§8───────────────\n" +
                        "§e» §7Clic derecho para gestionar\n" +
                        "§8▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁");
    }
}

package com.zhilius.secureplots.hologram;

import com.zhilius.secureplots.SecurePlots;
import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotSize;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlotHologram {

    private static final String TAG = "sp_holo";
    private static final Map<BlockPos, UUID> active = new HashMap<>();
    private static final Map<UUID, int[]>    timers = new HashMap<>();

    public static void registerTicker() {
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof DisplayEntity.TextDisplayEntity disp) {
                Text name = disp.getCustomName();
                if (name != null && TAG.equals(name.getString())) {
                    world.getServer().execute(disp::discard);
                }
            }
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            timers.entrySet().removeIf(e -> {
                e.getValue()[0]--;
                if (e.getValue()[0] <= 0) {
                    UUID id = e.getKey();
                    active.values().remove(id);
                    for (ServerWorld w : server.getWorlds()) {
                        net.minecraft.entity.Entity ent = w.getEntity(id);
                        if (ent != null) { ent.discard(); return true; }
                    }
                    return true;
                }
                return false;
            });
        });
    }

    public static void spawn(ServerWorld world, BlockPos blockPos, PlotData data,
                              int durationTicks, float playerYaw) {
        // Remove previous hologram at this position
        UUID prev = active.remove(blockPos);
        if (prev != null) {
            timers.remove(prev);
            net.minecraft.entity.Entity e = world.getEntity(prev);
            if (e != null) e.discard();
        }

        // Spawn 2.5 blocks above the plot block
        double x = blockPos.getX() + 0.5;
        double y = blockPos.getY() + 2.5;
        double z = blockPos.getZ() + 0.5;

        DisplayEntity.TextDisplayEntity disp = EntityType.TEXT_DISPLAY.create(world);
        if (disp == null) {
            SecurePlots.LOGGER.error("[PlotHologram] TEXT_DISPLAY.create() null");
            return;
        }

        // Set via NBT — same as /summon
        NbtCompound nbt = new NbtCompound();
        NbtList rotation = new NbtList();
        rotation.add(NbtFloat.of(playerYaw + 180f));
        rotation.add(NbtFloat.of(0f));
        nbt.put("Rotation", rotation);
        nbt.putByte("billboard", (byte) 0); // FIXED
        nbt.putString("text", buildJson(data));
        nbt.putInt("background", 0xC0000000); // semi-transparent black background
        nbt.putInt("line_width", 200);
        nbt.putByte("text_opacity", (byte) 255);
        nbt.putByte("shadow", (byte) 0);
        nbt.putByte("see_through", (byte) 0);
        nbt.putFloat("scale", 1.0f);
        disp.readNbt(nbt);
        disp.setPos(x, y, z);

        world.spawnEntity(disp);
        SecurePlots.LOGGER.info("[PlotHologram] spawned uuid={} at {},{},{} yaw={}", disp.getUuid(), x, y, z, playerYaw);

        active.put(blockPos, disp.getUuid());
        timers.put(disp.getUuid(), new int[]{durationTicks});
    }

    public static void remove(ServerWorld world, BlockPos pos) {
        UUID id = active.remove(pos);
        if (id != null) {
            timers.remove(id);
            net.minecraft.entity.Entity e = world.getEntity(id);
            if (e != null) e.discard();
        }
    }

    public static boolean hasSpace(ServerWorld world, BlockPos blockPos) { return true; }

    public static void clearAll(MinecraftServer server) {
        for (ServerWorld world : server.getWorlds()) {
            world.getEntitiesByType(EntityType.TEXT_DISPLAY,
                    e -> { Text n = e.getCustomName(); return n != null && TAG.equals(n.getString()); }
            ).forEach(net.minecraft.entity.Entity::discard);
        }
        active.clear();
        timers.clear();
    }


    private static String tierColorCode(int tier) {
        return switch (tier) {
            case 0 -> "\u00a76";  // Bronce - dorado
            case 1 -> "\u00a7e";  // Oro - amarillo
            case 2 -> "\u00a7a";  // Esmeralda - verde
            case 3 -> "\u00a7b";  // Diamante - aqua
            case 4 -> "\u00a75";  // Netherita - purpura
            default -> "\u00a7f";
        };
    }

    private static String buildJson(PlotData data) {
        String name = (data.getPlotName() != null && !data.getPlotName().isBlank())
                ? data.getPlotName().toUpperCase() : "PARCELA PROTEGIDA";
        PlotSize next = data.getSize().next();
        String tc = tierColorCode(data.getSize().tier);

        String nextLine = (next != null)
                ? "\u00a77 Siguiente: " + tierColorCode(next.tier) + "\u00a7l" + next.displayName
                : "\u00a76\u00a7l \u2605 Nivel Maximo \u2605";

        // Top/bottom border: tier color + bold dashes (mimics the HUD panel borders)
        String border  = tc + "\u00a7l\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550";
        // Subdivider under name: dark gray thin line
        String divider = "\u00a78\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500";
        String NL = "\\n";

        String text =
            border + NL +
            tc + "\u00a7l " + name + NL +
            divider + NL +
            "\u00a77 Due\u00f1o:    \u00a7f" + data.getOwnerName() + NL +
            "\u00a77 Nivel:    " + tc + "\u00a7l" + data.getSize().displayName + NL +
            "\u00a77 Tama\u00f1o:   \u00a7b" + data.getSize().radius + "x" + data.getSize().radius + NL +
            "\u00a77 Miembros: \u00a7a" + data.getMembers().size() + NL +
            divider + NL +
            nextLine + NL +
            border;

        text = text.replace("\"", "\\\"");
        return "{\"text\":\"" + text + "\"}";
    }
}

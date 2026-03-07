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
        nbt.putInt("background", 0x7F000000); // semi-transparent black background
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
            case 0 -> "§6";  // bronze/gold
            case 1 -> "§e";  // gold/yellow
            case 2 -> "§a";  // emerald/green
            case 3 -> "§b";  // diamond/aqua
            case 4 -> "§5";  // netherite/purple
            default -> "§f";
        };
    }

    private static String buildJson(PlotData data) {
        String name = (data.getPlotName() != null && !data.getPlotName().isBlank())
                ? data.getPlotName().toUpperCase() : "PARCELA PROTEGIDA";
        PlotSize next  = data.getSize().next();
        String nextStr = next != null ? "⬆ " + next.displayName : "★ Nivel Máximo";
        String tc = tierColorCode(data.getSize().tier);

        // Build text with tier-colored top/bottom border lines inside the panel
        String text =
            tc + "§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n" +
            "§6§l " + name + "\n" +
            "§7 Dueño:    §f" + data.getOwnerName() + "\n" +
            "§7 Nivel:    §b" + data.getSize().displayName + "\n" +
            "§7 Tamaño:   §b" + data.getSize().radius + "×" + data.getSize().radius + "\n" +
            "§7 Miembros: §a" + data.getMembers().size() + "\n" +
            "§8 ─────────────────\n" +
            "§e " + nextStr + "\n" +
            tc + "§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";

        // Escape backslashes then quotes for JSON string value
        text = text.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"text\":\"" + text + "\"}";
    }
}

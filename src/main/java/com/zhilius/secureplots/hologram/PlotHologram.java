package com.zhilius.secureplots.hologram;

import com.zhilius.secureplots.plot.PlotData;
import com.zhilius.secureplots.plot.PlotSize;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlotHologram {

    private static final int HOLOGRAM_HEIGHT = 4;
    private static final String PERSISTENT_KEY = "secureplots_holos";

    private static final Map<UUID, UUID>        active      = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer>     tickMap     = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockPos>    blockPosMap = new ConcurrentHashMap<>();
    private static final Map<UUID, ServerWorld> worldMap    = new ConcurrentHashMap<>();

    // ── PersistentState ───────────────────────────────────────────────────────
    static class HologramState extends PersistentState {
        final Set<UUID> entityUuids = new HashSet<>();

        static HologramState load(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
            HologramState s = new HologramState();
            NbtList list = nbt.getList("uuids", 8);
            for (int i = 0; i < list.size(); i++) {
                try { s.entityUuids.add(UUID.fromString(list.getString(i))); }
                catch (Exception ignored) {}
            }
            return s;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
            NbtList list = new NbtList();
            for (UUID u : entityUuids) list.add(NbtString.of(u.toString()));
            nbt.put("uuids", list);
            return nbt;
        }
    }

    private static HologramState getState(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
            new PersistentState.Type<>(HologramState::new,
                (nbt, reg) -> HologramState.load(nbt, reg), null),
            PERSISTENT_KEY);
    }

    // ── Ticker & cleanup ──────────────────────────────────────────────────────
    private static final Set<ServerWorld> pendingCleanup = ConcurrentHashMap.newKeySet();

    public static void registerTicker() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            // Run cleanup in the ticker — entities are fully loaded by now
            if (!pendingCleanup.isEmpty()) {
                for (ServerWorld w : new ArrayList<>(pendingCleanup)) {
                    cleanupPersistedEntities(w);
                }
                pendingCleanup.clear();
            }

            List<UUID> toRemove = new ArrayList<>();
            for (UUID posKey : new ArrayList<>(tickMap.keySet())) {
                int remaining = tickMap.getOrDefault(posKey, 0) - 1;
                if (remaining <= 0) {
                    toRemove.add(posKey);
                    discardEntity(server, posKey);
                    continue;
                }
                ServerWorld w = worldMap.get(posKey);
                BlockPos bp = blockPosMap.get(posKey);
                if (w != null && bp != null && isBlocked(w, bp)) {
                    toRemove.add(posKey);
                    discardEntity(server, posKey);
                    continue;
                }
                tickMap.put(posKey, remaining);
            }
            for (UUID key : toRemove) {
                tickMap.remove(key);
                blockPosMap.remove(key);
                worldMap.remove(key);
                active.remove(key);
            }
        });

        // Queue world for cleanup — actual sweep happens in the next ticker call
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents.LOAD.register((server, world) -> {
            pendingCleanup.add(world);
        });
    }

    private static void cleanupPersistedEntities(ServerWorld world) {
        try {
            HologramState state = getState(world);
            // Kill tracked UUIDs first
            for (UUID entityUuid : new ArrayList<>(state.entityUuids)) {
                try {
                    var entity = world.getEntity(entityUuid);
                    if (entity != null) entity.discard();
                } catch (Exception ignored) {}
            }
            // Sweep stray TextDisplay entities (collect first, then discard)
            List<net.minecraft.entity.Entity> stray = new ArrayList<>();
            try {
                for (net.minecraft.entity.Entity e : world.iterateEntities()) {
                    if (e instanceof DisplayEntity.TextDisplayEntity) stray.add(e);
                }
            } catch (Exception ignored) {}
            for (net.minecraft.entity.Entity e : stray) {
                try { e.discard(); } catch (Exception ignored) {}
            }
            state.entityUuids.clear();
            state.markDirty();
        } catch (Exception e) {
            com.zhilius.secureplots.SecurePlots.LOGGER.error("SecurePlots: error cleaning holos in {}: {}", world.getRegistryKey().getValue(), e.getMessage());
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────
    public static void clearAll(net.minecraft.server.MinecraftServer server) {
        for (UUID posKey : new ArrayList<>(active.keySet())) discardEntity(server, posKey);
        active.clear(); tickMap.clear(); blockPosMap.clear(); worldMap.clear();
    }

    public static void remove(ServerWorld world, BlockPos pos) {
        UUID posKey = posKey(pos);
        UUID entityId = active.remove(posKey);
        if (entityId != null) {
            var entity = world.getEntity(entityId);
            if (entity != null) entity.discard();
            HologramState state = getState(world);
            state.entityUuids.remove(entityId);
            state.markDirty();
        }
        tickMap.remove(posKey); blockPosMap.remove(posKey); worldMap.remove(posKey);
    }

    public static void spawn(ServerWorld world, BlockPos blockPos, PlotData data, int durationTicks, float playerYaw) {
        UUID posKey = posKey(blockPos);
        UUID oldId = active.remove(posKey);
        tickMap.remove(posKey); blockPosMap.remove(posKey); worldMap.remove(posKey);
        if (oldId != null) {
            var old = world.getEntity(oldId);
            if (old != null) old.discard();
            HologramState st = getState(world);
            st.entityUuids.remove(oldId);
            st.markDirty();
        }
        if (!hasSpace(world, blockPos)) return;

        double x = blockPos.getX() + 0.5, y = blockPos.getY() + 1.5, z = blockPos.getZ() + 0.5;
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
        HologramState state = getState(world);
        state.entityUuids.add(text.getUuid());
        state.markDirty();
    }

    public static boolean hasSpace(ServerWorld world, BlockPos blockPos) {
        return !isBlocked(world, blockPos);
    }

    private static void discardEntity(net.minecraft.server.MinecraftServer server, UUID posKey) {
        UUID entityId = active.get(posKey);
        if (entityId == null) return;
        for (ServerWorld w : server.getWorlds()) {
            var entity = w.getEntity(entityId);
            if (entity != null) {
                entity.discard();
                HologramState state = getState(w);
                state.entityUuids.remove(entityId);
                state.markDirty();
                return;
            }
        }
    }

    private static boolean isBlocked(ServerWorld world, BlockPos blockPos) {
        for (int i = 1; i <= HOLOGRAM_HEIGHT; i++)
            if (!world.getBlockState(blockPos.up(i)).isAir()) return true;
        return false;
    }

    private static UUID posKey(BlockPos pos) {
        return UUID.nameUUIDFromBytes((pos.getX() + "," + pos.getY() + "," + pos.getZ()).getBytes());
    }

    private static Text buildText(PlotData data) {
        String name = (data.getPlotName() != null && !data.getPlotName().isBlank())
                ? data.getPlotName().toUpperCase() : "PARCELA PROTEGIDA";
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

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
 * You should be received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.zhilius.secureplots.plot;

import com.zhilius.secureplots.config.SecurePlotsConfig;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.*;

/**
 * Manages protected areas - global protection zones independent of player plots.
 * These areas can be defined via config or commands and protect regions from
 * unauthorized player interaction.
 */
public class ProtectedAreaManager extends PersistentState {

    private static final String KEY = "secure_plots_protected_areas";

    private final List<SecurePlotsConfig.ProtectedArea> areas = new ArrayList<>();
    private boolean dirty = false;

    public static ProtectedAreaManager getOrCreate(ServerWorld world) {
        PersistentStateManager manager = world.getPersistentStateManager();
        return manager.getOrCreate(new PersistentState.Type<>(
            ProtectedAreaManager::new,
            (nbt, registries) -> {
                ProtectedAreaManager pam = new ProtectedAreaManager();
                pam.readNbt(nbt);
                return pam;
            },
            null), KEY);
    }

    /**
     * Adds a new protected area.
     */
    public void addArea(SecurePlotsConfig.ProtectedArea area) {
        areas.add(area);
        markDirty();
    }

    /**
     * Removes a protected area by name.
     * @return true if removed, false if not found
     */
    public boolean removeArea(String name) {
        boolean removed = areas.removeIf(a -> a.name.equalsIgnoreCase(name));
        if (removed) markDirty();
        return removed;
    }

    /**
     * Gets a protected area by name.
     */
    public SecurePlotsConfig.ProtectedArea getArea(String name) {
        for (SecurePlotsConfig.ProtectedArea area : areas) {
            if (area.name.equalsIgnoreCase(name)) return area;
        }
        return null;
    }

    /**
     * Checks if a position is inside any enabled protected area.
     * @return the area if found, null otherwise
     */
    public SecurePlotsConfig.ProtectedArea getAreaAt(BlockPos pos, String dimension) {
        for (SecurePlotsConfig.ProtectedArea area : areas) {
            if (!area.enabled) continue;
            if (!area.dimension.equals(dimension)) continue;
            if (area.contains(pos)) return area;
        }
        return null;
    }

    /**
     * Checks if a player is allowed to interact in a protected area.
     * Checks both player name and permission groups.
     */
    public boolean isPlayerAllowed(SecurePlotsConfig.ProtectedArea area, ServerPlayerEntity player) {
        if (!area.enabled) return true; // Area disabled, allow all

        String playerName = player.getName().getString().toLowerCase();

        // Check if player name is in allowed list
        boolean nameAllowed = area.allowedPlayers.stream().anyMatch(p -> p.equalsIgnoreCase(playerName));
        if (nameAllowed) return true;

        // Check if player has any allowed group (LuckPerms integration)
        if (!area.allowedGroups.isEmpty()) {
            try {
                Class<?> lpProviderClass = Class.forName("net.luckperms.api.LuckPermsProvider");
                Object luckPerms = lpProviderClass.getMethod("get").invoke(null);
                if (luckPerms != null) {
                    Object userManager = luckPerms.getClass().getMethod("getUserManager").invoke(luckPerms);
                    Object user = userManager.getClass().getMethod("getUser", java.util.UUID.class).invoke(userManager, player.getUuid());
                    if (user != null) {
                        Object nodes = user.getClass().getMethod("getNodes").invoke(user);
                        for (String groupName : area.allowedGroups) {
                            String targetKey = "group." + groupName;
                            boolean hasGroup = (boolean) nodes.getClass().getMethod("stream").invoke(nodes)
                                .getClass().getMethod("anyMatch", java.util.function.Predicate.class)
                                .invoke(nodes, (java.util.function.Predicate<Object>) n -> {
                                    try {
                                        return (boolean) n.getClass().getMethod("getKey").invoke(n).equals(targetKey);
                                    } catch (Exception e) {
                                        return false;
                                    }
                                });
                            if (hasGroup) return true;
                        }
                    }
                }
            } catch (Exception e) {
                // LuckPerms not available or error, continue with name check
            }
        }

        return false;
    }

    /**
     * Checks if a player can break blocks at a position.
     */
    public boolean canBreak(ServerPlayerEntity player, BlockPos pos, String dimension) {
        SecurePlotsConfig.ProtectedArea area = getAreaAt(pos, dimension);
        if (area == null || !area.protectBreak) return true;
        return isPlayerAllowed(area, player);
    }

    /**
     * Checks if a player can place blocks at a position.
     */
    public boolean canPlace(ServerPlayerEntity player, BlockPos pos, String dimension) {
        SecurePlotsConfig.ProtectedArea area = getAreaAt(pos, dimension);
        if (area == null || !area.protectPlace) return true;
        return isPlayerAllowed(area, player);
    }

    /**
     * Checks if a player can interact at a position.
     */
    public boolean canInteract(ServerPlayerEntity player, BlockPos pos, String dimension) {
        SecurePlotsConfig.ProtectedArea area = getAreaAt(pos, dimension);
        if (area == null || !area.protectInteract) return true;
        return isPlayerAllowed(area, player);
    }

    /**
     * Checks if a player can access containers at a position.
     */
    public boolean canAccessContainers(ServerPlayerEntity player, BlockPos pos, String dimension) {
        SecurePlotsConfig.ProtectedArea area = getAreaAt(pos, dimension);
        if (area == null || !area.protectContainers) return true;
        return isPlayerAllowed(area, player);
    }

    /**
     * Checks if a player can enter the area (for requireAuth areas).
     */
    public boolean canEnter(ServerPlayerEntity player, BlockPos pos, String dimension) {
        SecurePlotsConfig.ProtectedArea area = getAreaAt(pos, dimension);
        if (area == null || !area.requireAuth) return true;
        return isPlayerAllowed(area, player);
    }

    /**
     * Checks if entities are protected at a position.
     */
    public boolean isEntityProtected(BlockPos pos, String dimension) {
        SecurePlotsConfig.ProtectedArea area = getAreaAt(pos, dimension);
        return area != null && area.protectEntities && area.enabled;
    }

    /**
     * Checks if explosions are blocked at a position.
     */
    public boolean isExplosionProtected(BlockPos pos, String dimension) {
        SecurePlotsConfig.ProtectedArea area = getAreaAt(pos, dimension);
        return area != null && area.protectExplosions && area.enabled;
    }

    /**
     * Checks if liquids are protected at a position.
     */
    public boolean isLiquidProtected(BlockPos pos, String dimension) {
        SecurePlotsConfig.ProtectedArea area = getAreaAt(pos, dimension);
        return area != null && area.protectLiquids && area.enabled;
    }

    /**
     * Checks and removes expired temporary areas.
     * @return true if any areas were removed
     */
    public boolean cleanupExpiredAreas() {
        long now = System.currentTimeMillis();
        boolean changed = areas.removeIf(area -> area.isTemporary && area.expiryTime > 0 && now > area.expiryTime);
        if (changed) markDirty();
        return changed;
    }

    /**
     * Gets all areas that a player is currently inside (for enter/exit tracking).
     */
    public List<SecurePlotsConfig.ProtectedArea> getAreasForPlayer(ServerPlayerEntity player) {
        BlockPos pos = player.getBlockPos();
        String dimension = player.getWorld().getRegistryKey().getValue().toString();
        return getAreasAt(pos, dimension);
    }

    /**
     * Gets all protected areas.
     */
    public List<SecurePlotsConfig.ProtectedArea> getAllAreas() {
        return new ArrayList<>(areas);
    }

    /**
     * Gets all areas that contain a position (could be multiple overlapping areas).
     */
    public List<SecurePlotsConfig.ProtectedArea> getAreasAt(BlockPos pos, String dimension) {
        List<SecurePlotsConfig.ProtectedArea> result = new ArrayList<>();
        for (SecurePlotsConfig.ProtectedArea area : areas) {
            if (!area.enabled) continue;
            if (!area.dimension.equals(dimension)) continue;
            if (area.contains(pos)) result.add(area);
        }
        return result;
    }

    @Override
    public void markDirty() {
        dirty = true;
        super.markDirty();
    }

    private void readNbt(NbtCompound nbt) {
        areas.clear();
        NbtList list = nbt.getList("areas", 10);
        for (int i = 0; i < list.size(); i++) {
            areas.add(areaFromNbt(list.getCompound(i)));
        }
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtList list = new NbtList();
        for (SecurePlotsConfig.ProtectedArea area : areas) {
            list.add(areaToNbt(area));
        }
        nbt.put("areas", list);
        return nbt;
    }

    private NbtCompound areaToNbt(SecurePlotsConfig.ProtectedArea area) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("name", area.name);
        nbt.putInt("x1", area.x1);
        nbt.putInt("y1", area.y1);
        nbt.putInt("z1", area.z1);
        nbt.putInt("x2", area.x2);
        nbt.putInt("y2", area.y2);
        nbt.putInt("z2", area.z2);
        nbt.putBoolean("requireAuth", area.requireAuth);
        nbt.putBoolean("protectBreak", area.protectBreak);
        nbt.putBoolean("protectPlace", area.protectPlace);
        nbt.putBoolean("protectInteract", area.protectInteract);
        nbt.putBoolean("protectContainers", area.protectContainers);
        nbt.putBoolean("protectEntities", area.protectEntities);
        nbt.putBoolean("protectExplosions", area.protectExplosions);
        nbt.putBoolean("protectLiquids", area.protectLiquids);
        nbt.putBoolean("enabled", area.enabled);
        nbt.putBoolean("showNotifications", area.showNotifications);
        nbt.putBoolean("isTemporary", area.isTemporary);
        nbt.putLong("expiryTime", area.expiryTime);
        nbt.putString("dimension", area.dimension);

        NbtList allowedList = new NbtList();
        for (String player : area.allowedPlayers) {
            allowedList.add(NbtString.of(player));
        }
        nbt.put("allowedPlayers", allowedList);

        NbtList groupList = new NbtList();
        for (String group : area.allowedGroups) {
            groupList.add(NbtString.of(group));
        }
        nbt.put("allowedGroups", groupList);

        return nbt;
    }

    private SecurePlotsConfig.ProtectedArea areaFromNbt(NbtCompound nbt) {
        SecurePlotsConfig.ProtectedArea area = new SecurePlotsConfig.ProtectedArea();
        area.name = nbt.getString("name");
        area.x1 = nbt.getInt("x1");
        area.y1 = nbt.getInt("y1");
        area.z1 = nbt.getInt("z1");
        area.x2 = nbt.getInt("x2");
        area.y2 = nbt.getInt("y2");
        area.z2 = nbt.getInt("z2");
        area.requireAuth = nbt.getBoolean("requireAuth");
        area.protectBreak = nbt.getBoolean("protectBreak");
        area.protectPlace = nbt.getBoolean("protectPlace");
        area.protectInteract = nbt.getBoolean("protectInteract");
        area.protectContainers = nbt.getBoolean("protectContainers");
        area.protectEntities = nbt.getBoolean("protectEntities");
        area.protectExplosions = nbt.getBoolean("protectExplosions");
        area.protectLiquids = nbt.getBoolean("protectLiquids");
        area.enabled = nbt.getBoolean("enabled");
        area.showNotifications = nbt.getBoolean("showNotifications");
        area.isTemporary = nbt.getBoolean("isTemporary");
        area.expiryTime = nbt.getLong("expiryTime");
        area.dimension = nbt.contains("dimension") ? nbt.getString("dimension") : "minecraft:overworld";

        NbtList allowedList = nbt.getList("allowedPlayers", 8);
        for (int i = 0; i < allowedList.size(); i++) {
            area.allowedPlayers.add(allowedList.getString(i));
        }

        NbtList groupList = nbt.getList("allowedGroups", 8);
        for (int i = 0; i < groupList.size(); i++) {
            area.allowedGroups.add(groupList.getString(i));
        }

        return area;
    }
}

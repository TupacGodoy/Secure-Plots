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
package com.zhilius.secureplots.plot;

import com.zhilius.secureplots.config.SecurePlotsConfig;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class PlotData {

    public enum Role { OWNER, ADMIN, MEMBER, VISITOR }

    /**
     * Individual permissions assignable per member or group.
     * Also used as global flags (for VISITOR and MEMBER by default).
     */
    public enum Permission {
        // Construction
        BUILD, BREAK, PLACE,
        // Interaction
        INTERACT, CONTAINERS, USE_BEDS, USE_CRAFTING, USE_ENCHANTING,
        USE_ANVIL, USE_FURNACE, USE_BREWING,
        // Entities
        ATTACK_MOBS, ATTACK_ANIMALS, PVP, RIDE_ENTITIES, INTERACT_MOBS,
        LEASH_MOBS, SHEAR_MOBS, MILK_MOBS,
        // Nature
        CROP_TRAMPLING, PICKUP_ITEMS, DROP_ITEMS, BREAK_CROPS, PLANT_SEEDS,
        USE_BONEMEAL, BREAK_DECOR,
        // Explosives
        DETONATE_TNT, GRIEFING,
        // Misc
        TP, FLY, ENTER, CHAT, COMMAND_USE,
        // Admin / management
        MANAGE_MEMBERS, MANAGE_PERMS, MANAGE_FLAGS, MANAGE_GROUPS
    }

    // ── Global flags — affect EVERYONE (including non-members) when enabled ───
    public enum Flag {
        ALLOW_VISITOR_BUILD, ALLOW_VISITOR_INTERACT, ALLOW_VISITOR_CONTAINERS,
        ALLOW_PVP, ALLOW_FLY, ALLOW_TP, GREETINGS
    }

    // ── Permission group ──────────────────────────────────────────────────────
    public static class PermissionGroup {
        public String name;
        public Set<Permission>  permissions = new HashSet<>();
        public List<UUID>       members     = new ArrayList<>();

        public PermissionGroup() {}
        public PermissionGroup(String name) { this.name = name; }

        public NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putString("name", name);
            NbtList permsNbt = new NbtList();
            for (Permission p : permissions) permsNbt.add(NbtString.of(p.name()));
            nbt.put("perms", permsNbt);
            NbtList membersNbt = new NbtList();
            for (UUID u : members) membersNbt.add(NbtString.of(u.toString()));
            nbt.put("members", membersNbt);
            return nbt;
        }

        public static PermissionGroup fromNbt(NbtCompound nbt) {
            PermissionGroup g = new PermissionGroup(nbt.getString("name"));
            NbtList permsNbt = nbt.getList("perms", 8);
            for (int i = 0; i < permsNbt.size(); i++)
                try { g.permissions.add(Permission.valueOf(permsNbt.getString(i))); } catch (Exception ignored) {}
            NbtList membersNbt = nbt.getList("members", 8);
            for (int i = 0; i < membersNbt.size(); i++)
                try { g.members.add(UUID.fromString(membersNbt.getString(i))); } catch (Exception ignored) {}
            return g;
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────
    private UUID    ownerId;
    private String  ownerName;
    private BlockPos center;
    private PlotSize size;
    private boolean  hasRank;
    private long     placedAtTick;
    private long     lastOwnerSeenTick;

    private final Map<UUID, Role>            members     = new HashMap<>();
    private final Map<UUID, String>          memberNames = new HashMap<>();
    private final Map<UUID, Set<Permission>> memberPerms = new HashMap<>();
    private final Set<Flag>                  flags       = new HashSet<>();
    private final List<PermissionGroup>      groups      = new ArrayList<>();

    private String plotName;
    private String enterMessage  = "";
    private String exitMessage   = "";
    private String particleEffect = "";
    private String weatherType    = "";
    private long   plotTime       = -1L;
    private String musicSound     = "";

    // ── Constructors ──────────────────────────────────────────────────────────
    public PlotData(UUID ownerId, String ownerName, BlockPos center, PlotSize size, long currentTick) {
        this.ownerId           = ownerId;
        this.ownerName         = ownerName;
        this.center            = center;
        this.size              = size;
        this.placedAtTick      = currentTick;
        this.lastOwnerSeenTick = currentTick;
        this.plotName          = ownerName + "'s Plot";
        loadDefaultFlags();
    }

    public PlotData() {}

    // ── Getters / Setters ─────────────────────────────────────────────────────
    public UUID      getOwnerId()                    { return ownerId; }
    public String    getOwnerName()                  { return ownerName; }
    public BlockPos  getCenter()                     { return center; }
    public PlotSize  getSize()                       { return size; }
    public void      setSize(PlotSize size)          { this.size = size; }
    public boolean   hasRank()                       { return hasRank; }
    public void      setHasRank(boolean hasRank)     { this.hasRank = hasRank; }
    public String    getPlotName()                   { return plotName; }
    public void      setPlotName(String name)        { this.plotName = name; }
    public long      getPlacedAtTick()               { return placedAtTick; }
    public long      getLastOwnerSeenTick()          { return lastOwnerSeenTick; }
    public void      setLastOwnerSeenTick(long tick) { this.lastOwnerSeenTick = tick; }
    public Map<UUID, Role>       getMembers()        { return members; }
    public List<PermissionGroup> getGroups()         { return groups; }
    public Set<Flag>             getFlags()          { return flags; }

    // Ambient effect getters/setters — all null-safe
    public String getEnterMessage()          { return enterMessage   != null ? enterMessage   : ""; }
    public void   setEnterMessage(String m)  { this.enterMessage   = m != null ? m : ""; }
    public String getExitMessage()           { return exitMessage    != null ? exitMessage    : ""; }
    public void   setExitMessage(String m)   { this.exitMessage    = m != null ? m : ""; }
    public String getParticleEffect()        { return particleEffect != null ? particleEffect : ""; }
    public void   setParticleEffect(String p){ this.particleEffect = p != null ? p : ""; }
    public String getWeatherType()           { return weatherType    != null ? weatherType    : ""; }
    public void   setWeatherType(String w)   { this.weatherType    = w != null ? w.toUpperCase() : ""; }
    public long   getPlotTime()              { return plotTime; }
    public void   setPlotTime(long t)        { this.plotTime = t; }
    public String getMusicSound()            { return musicSound     != null ? musicSound     : ""; }
    public void   setMusicSound(String s)    { this.musicSound     = s != null ? s : ""; }

    // ── Flags ─────────────────────────────────────────────────────────────────
    public boolean hasFlag(Flag flag)                    { return flags.contains(flag); }
    public void    setFlag(Flag flag, boolean enabled)   { if (enabled) flags.add(flag); else flags.remove(flag); }

    private void loadDefaultFlags() {
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg != null && cfg.defaultFlags != null) {
            for (String name : cfg.defaultFlags)
                try { flags.add(Flag.valueOf(name)); } catch (Exception ignored) {}
        } else {
            flags.add(Flag.ALLOW_TP);
            flags.add(Flag.GREETINGS);
        }
    }

    // ── Members ───────────────────────────────────────────────────────────────
    public void addMember(UUID uuid, String name, Role role) {
        members.put(uuid, role);
        memberNames.put(uuid, name);
        memberPerms.put(uuid, defaultPermsFor(role));
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
        memberNames.remove(uuid);
        memberPerms.remove(uuid);
        for (PermissionGroup g : groups) g.members.remove(uuid);
    }

    public Role getRoleOf(UUID uuid) {
        if (uuid.equals(ownerId)) return Role.OWNER;
        return members.getOrDefault(uuid, Role.VISITOR);
    }

    public String getMemberName(UUID uuid) {
        return memberNames.getOrDefault(uuid, uuid.toString().substring(0, 8));
    }

    // ── Default permissions by role ───────────────────────────────────────────
    public static Set<Permission> defaultPermsFor(Role role) {
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg != null && cfg.roleDefaults != null) {
            if (role == Role.OWNER) return EnumSet.allOf(Permission.class);
            List<String> names = switch (role) {
                case ADMIN   -> cfg.roleDefaults.admin;
                case MEMBER  -> cfg.roleDefaults.member;
                case VISITOR -> cfg.roleDefaults.visitor;
                default      -> List.of();
            };
            Set<Permission> perms = new HashSet<>();
            for (String name : names)
                try { perms.add(Permission.valueOf(name)); } catch (Exception ignored) {}
            return perms;
        }
        // Hardcoded fallback (used only before config loads)
        return switch (role) {
            case OWNER   -> EnumSet.allOf(Permission.class);
            case ADMIN   -> EnumSet.of(
                Permission.BUILD, Permission.BREAK, Permission.PLACE,
                Permission.INTERACT, Permission.CONTAINERS, Permission.USE_BEDS,
                Permission.USE_CRAFTING, Permission.USE_ENCHANTING, Permission.USE_ANVIL,
                Permission.USE_FURNACE, Permission.USE_BREWING, Permission.ATTACK_MOBS,
                Permission.ATTACK_ANIMALS, Permission.PVP, Permission.RIDE_ENTITIES,
                Permission.INTERACT_MOBS, Permission.LEASH_MOBS, Permission.SHEAR_MOBS,
                Permission.MILK_MOBS, Permission.CROP_TRAMPLING, Permission.PICKUP_ITEMS,
                Permission.DROP_ITEMS, Permission.BREAK_CROPS, Permission.PLANT_SEEDS,
                Permission.USE_BONEMEAL, Permission.BREAK_DECOR, Permission.FLY,
                Permission.TP, Permission.ENTER, Permission.CHAT,
                Permission.MANAGE_MEMBERS, Permission.MANAGE_PERMS,
                Permission.MANAGE_FLAGS, Permission.MANAGE_GROUPS);
            case MEMBER  -> EnumSet.of(
                Permission.BUILD, Permission.BREAK, Permission.PLACE,
                Permission.INTERACT, Permission.CONTAINERS, Permission.USE_BEDS,
                Permission.USE_CRAFTING, Permission.USE_ENCHANTING, Permission.USE_ANVIL,
                Permission.USE_FURNACE, Permission.USE_BREWING, Permission.ATTACK_MOBS,
                Permission.ATTACK_ANIMALS, Permission.RIDE_ENTITIES, Permission.INTERACT_MOBS,
                Permission.LEASH_MOBS, Permission.SHEAR_MOBS, Permission.MILK_MOBS,
                Permission.PICKUP_ITEMS, Permission.DROP_ITEMS, Permission.PLANT_SEEDS,
                Permission.USE_BONEMEAL, Permission.TP, Permission.ENTER, Permission.CHAT);
            case VISITOR -> EnumSet.of(Permission.INTERACT, Permission.ENTER);
        };
    }

    // ── Effective permissions ─────────────────────────────────────────────────
    public Set<Permission> getPermsOf(UUID uuid) {
        if (uuid.equals(ownerId)) return EnumSet.allOf(Permission.class);

        Set<Permission> perms = new HashSet<>(memberPerms.getOrDefault(uuid, defaultPermsFor(getRoleOf(uuid))));

        for (PermissionGroup g : groups)
            if (g.members.contains(uuid)) perms.addAll(g.permissions);

        if (hasFlag(Flag.ALLOW_VISITOR_BUILD))      perms.add(Permission.BUILD);
        if (hasFlag(Flag.ALLOW_VISITOR_INTERACT))   perms.add(Permission.INTERACT);
        if (hasFlag(Flag.ALLOW_VISITOR_CONTAINERS)) perms.add(Permission.CONTAINERS);
        if (hasFlag(Flag.ALLOW_PVP))                perms.add(Permission.PVP);
        if (hasFlag(Flag.ALLOW_FLY))                perms.add(Permission.FLY);
        if (hasFlag(Flag.ALLOW_TP))                 perms.add(Permission.TP);

        return perms;
    }

    public void setPermission(UUID uuid, Permission perm, boolean enabled) {
        Set<Permission> perms = memberPerms.computeIfAbsent(uuid,
            k -> new HashSet<>(defaultPermsFor(getRoleOf(k))));
        if (enabled) perms.add(perm); else perms.remove(perm);
    }

    public boolean hasPermission(UUID uuid, Permission perm) {
        if (uuid.equals(ownerId)) return true;
        return getPermsOf(uuid).contains(perm);
    }

    public boolean canBuild(UUID uuid)    { return hasPermission(uuid, Permission.BUILD); }
    public boolean canInteract(UUID uuid) { return hasPermission(uuid, Permission.INTERACT); }
    public boolean canManage(UUID uuid)   { return hasPermission(uuid, Permission.MANAGE_MEMBERS); }

    // ── Groups ────────────────────────────────────────────────────────────────
    public PermissionGroup getGroup(String name) {
        for (PermissionGroup g : groups)
            if (g.name.equalsIgnoreCase(name)) return g;
        return null;
    }

    public PermissionGroup getOrCreateGroup(String name) {
        PermissionGroup g = getGroup(name);
        if (g == null) { g = new PermissionGroup(name); groups.add(g); }
        return g;
    }

    public boolean removeGroup(String name) {
        return groups.removeIf(g -> g.name.equalsIgnoreCase(name));
    }

    // ── Inactivity expiry ─────────────────────────────────────────────────────
    public boolean isExpired(long currentTick) {
        if (hasRank) return false;
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg == null || !cfg.inactivityExpiry.enabled) return false;
        return getDaysInactive(currentTick) > maxInactiveDays(cfg);
    }

    public long getDaysInactive(long currentTick) {
        return (currentTick - lastOwnerSeenTick) / 24000L;
    }

    public long getDaysRemaining(long currentTick) {
        if (hasRank) return Long.MAX_VALUE;
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg == null || !cfg.inactivityExpiry.enabled) return Long.MAX_VALUE;
        return Math.max(0, maxInactiveDays(cfg) - getDaysInactive(currentTick));
    }

    private long maxInactiveDays(SecurePlotsConfig cfg) {
        return cfg.inactivityExpiry.baseDays + ((long) cfg.inactivityExpiry.daysPerTier * size.tier);
    }

    // ── NBT Serialization ─────────────────────────────────────────────────────
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("ownerId",    ownerId.toString());
        nbt.putString("ownerName",  ownerName);
        nbt.putInt("cx",            center.getX());
        nbt.putInt("cy",            center.getY());
        nbt.putInt("cz",            center.getZ());
        nbt.putInt("sizeTier",      size.tier);
        nbt.putBoolean("hasRank",   hasRank);
        nbt.putLong("placedAt",     placedAtTick);
        nbt.putLong("lastOwnerSeen",lastOwnerSeenTick);
        nbt.putString("plotName",       plotName);
        nbt.putString("enterMessage",   getEnterMessage());
        nbt.putString("exitMessage",    getExitMessage());
        nbt.putString("particleEffect", getParticleEffect());
        nbt.putString("weatherType",    getWeatherType());
        nbt.putLong("plotTime",         plotTime);
        nbt.putString("musicSound",     getMusicSound());

        // Members
        NbtList membersList = new NbtList();
        for (Map.Entry<UUID, Role> entry : members.entrySet()) {
            UUID uuid = entry.getKey();
            NbtCompound m = new NbtCompound();
            m.putString("uuid", uuid.toString());
            m.putString("role", entry.getValue().name());
            m.putString("name", memberNames.getOrDefault(uuid, "Unknown"));
            Set<Permission> perms = memberPerms.getOrDefault(uuid, defaultPermsFor(entry.getValue()));
            StringBuilder sb = new StringBuilder();
            for (Permission p : perms) { if (sb.length() > 0) sb.append(","); sb.append(p.name()); }
            m.putString("perms", sb.toString());
            membersList.add(m);
        }
        nbt.put("members", membersList);

        // Flags
        NbtList flagsList = new NbtList();
        for (Flag f : flags) flagsList.add(NbtString.of(f.name()));
        nbt.put("flags", flagsList);

        // Groups
        NbtList groupsList = new NbtList();
        for (PermissionGroup g : groups) groupsList.add(g.toNbt());
        nbt.put("groups", groupsList);

        return nbt;
    }

    public static PlotData fromNbt(NbtCompound nbt) {
        PlotData data = new PlotData();
        data.ownerId           = UUID.fromString(nbt.getString("ownerId"));
        data.ownerName         = nbt.getString("ownerName");
        data.center            = new BlockPos(nbt.getInt("cx"), nbt.getInt("cy"), nbt.getInt("cz"));
        data.size              = PlotSize.fromTier(nbt.getInt("sizeTier"));
        data.hasRank           = nbt.getBoolean("hasRank");
        data.placedAtTick      = nbt.getLong("placedAt");
        data.lastOwnerSeenTick = nbt.contains("lastOwnerSeen") ? nbt.getLong("lastOwnerSeen") : data.placedAtTick;
        data.plotName          = nbt.getString("plotName");
        data.enterMessage      = nbt.contains("enterMessage")   ? nbt.getString("enterMessage")   : "";
        data.exitMessage       = nbt.contains("exitMessage")    ? nbt.getString("exitMessage")    : "";
        data.particleEffect    = nbt.contains("particleEffect") ? nbt.getString("particleEffect") : "";
        data.weatherType       = nbt.contains("weatherType")    ? nbt.getString("weatherType")    : "";
        data.plotTime          = nbt.contains("plotTime")       ? nbt.getLong("plotTime")         : -1L;
        data.musicSound        = nbt.contains("musicSound")     ? nbt.getString("musicSound")     : "";

        // Members
        NbtList membersList = nbt.getList("members", 10);
        for (int i = 0; i < membersList.size(); i++) {
            NbtCompound m    = membersList.getCompound(i);
            UUID uuid        = UUID.fromString(m.getString("uuid"));
            Role role        = Role.valueOf(m.getString("role"));
            String name      = m.getString("name");
            data.members.put(uuid, role);
            data.memberNames.put(uuid, name);
            String permsStr = m.getString("perms");
            if (permsStr != null && !permsStr.isEmpty()) {
                Set<Permission> perms = new HashSet<>();
                for (String ps : permsStr.split(","))
                    try { perms.add(Permission.valueOf(ps.trim())); } catch (Exception ignored) {}
                data.memberPerms.put(uuid, perms);
            } else {
                data.memberPerms.put(uuid, defaultPermsFor(role));
            }
        }

        // Flags
        if (nbt.contains("flags")) {
            NbtList flagsList = nbt.getList("flags", 8);
            for (int i = 0; i < flagsList.size(); i++)
                try { data.flags.add(Flag.valueOf(flagsList.getString(i))); } catch (Exception ignored) {}
        } else {
            // Backwards compat: load defaults from config
            data.loadDefaultFlags();
        }

        // Groups
        if (nbt.contains("groups")) {
            NbtList groupsList = nbt.getList("groups", 10);
            for (int i = 0; i < groupsList.size(); i++)
                data.groups.add(PermissionGroup.fromNbt(groupsList.getCompound(i)));
        }

        return data;
    }
}
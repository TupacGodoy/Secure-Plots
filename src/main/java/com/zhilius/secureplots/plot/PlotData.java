package com.zhilius.secureplots.plot;

import com.zhilius.secureplots.config.SecurePlotsConfig;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class PlotData {

    public enum Role {
        OWNER, ADMIN, MEMBER, VISITOR
    }

    public enum Permission {
        BUILD,
        INTERACT,
        CONTAINERS,
        PVP,
        MANAGE_MEMBERS,
        MANAGE_PERMS,
        MANAGE_FLAGS,
        MANAGE_GROUPS,
        MANAGE_SUBDIVISIONS, // nuevo: crear/editar subdivisiones
        TP,
        FLY,
        ENTER
    }

    public enum Flag {
        ALLOW_VISITOR_BUILD,
        ALLOW_VISITOR_INTERACT,
        ALLOW_VISITOR_CONTAINERS,
        ALLOW_PVP,
        ALLOW_FLY,
        ALLOW_TP,
        GREETINGS
    }

    // ── Grupo de permisos ─────────────────────────────────────────────────────
    public static class PermissionGroup {
        public String name;
        public Set<Permission> permissions = new HashSet<>();
        public List<UUID> members = new ArrayList<>();

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
            for (int i = 0; i < permsNbt.size(); i++) {
                try { g.permissions.add(Permission.valueOf(permsNbt.getString(i))); } catch (Exception ignored) {}
            }
            NbtList membersNbt = nbt.getList("members", 8);
            for (int i = 0; i < membersNbt.size(); i++) {
                try { g.members.add(UUID.fromString(membersNbt.getString(i))); } catch (Exception ignored) {}
            }
            return g;
        }
    }

    // ── Campos principales ────────────────────────────────────────────────────
    private UUID ownerId;
    private String ownerName;
    private BlockPos center;
    private PlotSize size;
    private boolean hasRank;

    private long placedAtTick;
    private long lastOwnerSeenTick;

    private Map<UUID, Role>             members     = new HashMap<>();
    private Map<UUID, String>           memberNames = new HashMap<>();
    private Map<UUID, Set<Permission>>  memberPerms = new HashMap<>();

    private Set<Flag>             flags  = new HashSet<>();
    private List<PermissionGroup> groups = new ArrayList<>();

    /** Subdivisiones de esta plot */
    private List<PlotSubdivision> subdivisions = new ArrayList<>();

    private String plotName;

    // ── Constructores ─────────────────────────────────────────────────────────
    public PlotData(UUID ownerId, String ownerName, BlockPos center, PlotSize size, long currentTick) {
        this.ownerId  = ownerId;
        this.ownerName = ownerName;
        this.center   = center;
        this.size     = size;
        this.placedAtTick = currentTick;
        this.lastOwnerSeenTick = currentTick;
        this.plotName = ownerName + "'s Plot";
        this.hasRank  = false;
        this.flags.add(Flag.ALLOW_TP);
        this.flags.add(Flag.GREETINGS);
    }

    public PlotData() {}

    // ── Getters / Setters ─────────────────────────────────────────────────────
    public UUID getOwnerId()                    { return ownerId; }
    public String getOwnerName()                { return ownerName; }
    public BlockPos getCenter()                 { return center; }
    public PlotSize getSize()                   { return size; }
    public void setSize(PlotSize size)          { this.size = size; }
    public boolean hasRank()                    { return hasRank; }
    public void setHasRank(boolean hasRank)     { this.hasRank = hasRank; }
    public String getPlotName()                 { return plotName; }
    public void setPlotName(String plotName)    { this.plotName = plotName; }
    public long getPlacedAtTick()               { return placedAtTick; }
    public long getLastOwnerSeenTick()          { return lastOwnerSeenTick; }
    public void setLastOwnerSeenTick(long tick) { this.lastOwnerSeenTick = tick; }
    public Map<UUID, Role> getMembers()         { return members; }
    public List<PermissionGroup> getGroups()    { return groups; }
    public Set<Flag> getFlags()                 { return flags; }
    public List<PlotSubdivision> getSubdivisions() { return subdivisions; }

    // ── Flags ─────────────────────────────────────────────────────────────────
    public boolean hasFlag(Flag flag) { return flags.contains(flag); }
    public void setFlag(Flag flag, boolean enabled) {
        if (enabled) flags.add(flag); else flags.remove(flag);
    }

    // ── Miembros ──────────────────────────────────────────────────────────────
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

    public static Set<Permission> defaultPermsFor(Role role) {
        Set<Permission> perms = new HashSet<>();
        switch (role) {
            case OWNER -> { perms.addAll(Arrays.asList(Permission.values())); }
            case ADMIN -> {
                perms.add(Permission.BUILD); perms.add(Permission.INTERACT);
                perms.add(Permission.CONTAINERS); perms.add(Permission.PVP);
                perms.add(Permission.MANAGE_MEMBERS); perms.add(Permission.MANAGE_PERMS);
                perms.add(Permission.TP); perms.add(Permission.ENTER);
            }
            case MEMBER -> {
                perms.add(Permission.BUILD); perms.add(Permission.INTERACT);
                perms.add(Permission.CONTAINERS); perms.add(Permission.TP);
                perms.add(Permission.ENTER);
            }
            case VISITOR -> {
                perms.add(Permission.INTERACT); perms.add(Permission.ENTER);
            }
        }
        return perms;
    }

    public Set<Permission> getPermsOf(UUID uuid) {
        if (uuid.equals(ownerId)) return EnumSet.allOf(Permission.class);
        Set<Permission> perms = new HashSet<>(memberPerms.getOrDefault(uuid, defaultPermsFor(getRoleOf(uuid))));
        for (PermissionGroup g : groups) {
            if (g.members.contains(uuid)) perms.addAll(g.permissions);
        }
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

    /**
     * Resuelve el permiso efectivo para un jugador en un BlockPos dado,
     * teniendo en cuenta subdivisiones (que sobreescriben la plot principal).
     */
    public boolean hasPermissionAt(UUID uuid, Permission perm, BlockPos pos) {
        if (uuid.equals(ownerId)) return true;
        // Buscar si hay subdivisión que contenga el pos
        for (PlotSubdivision sub : subdivisions) {
            if (sub.isValid() && sub.contains(pos)) {
                // La subdivisión sobreescribe: primero intenta el permiso de sub,
                // luego cae al de la plot si no hay override específico
                if (sub.memberPerms.containsKey(uuid)) {
                    return sub.hasPermission(uuid, perm);
                }
                // Si hay flags de sub, aplicar
                if (perm == Permission.BUILD      && sub.hasFlag(Flag.ALLOW_VISITOR_BUILD))      return true;
                if (perm == Permission.INTERACT   && sub.hasFlag(Flag.ALLOW_VISITOR_INTERACT))   return true;
                if (perm == Permission.CONTAINERS && sub.hasFlag(Flag.ALLOW_VISITOR_CONTAINERS)) return true;
                if (perm == Permission.PVP        && sub.hasFlag(Flag.ALLOW_PVP))               return true;
                if (perm == Permission.FLY        && sub.hasFlag(Flag.ALLOW_FLY))               return true;
                // Si la subdivisión no tiene override para este jugador, usa permiso de la plot
                return hasPermission(uuid, perm);
            }
        }
        // Sin subdivisión activa → permiso normal de la plot
        return hasPermission(uuid, perm);
    }

    public boolean canBuild(UUID uuid)    { return hasPermission(uuid, Permission.BUILD); }
    public boolean canInteract(UUID uuid) { return hasPermission(uuid, Permission.INTERACT); }
    public boolean canManage(UUID uuid)   { return hasPermission(uuid, Permission.MANAGE_MEMBERS); }

    public boolean canBuildAt(UUID uuid, BlockPos pos)    { return hasPermissionAt(uuid, Permission.BUILD, pos); }
    public boolean canInteractAt(UUID uuid, BlockPos pos) { return hasPermissionAt(uuid, Permission.INTERACT, pos); }

    // ── Grupos ────────────────────────────────────────────────────────────────
    public PermissionGroup getGroup(String name) {
        for (PermissionGroup g : groups) {
            if (g.name.equalsIgnoreCase(name)) return g;
        }
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

    // ── Subdivisiones ─────────────────────────────────────────────────────────
    public PlotSubdivision getSubdivision(String name) {
        for (PlotSubdivision s : subdivisions) {
            if (s.name != null && s.name.equalsIgnoreCase(name)) return s;
        }
        return null;
    }

    public PlotSubdivision getOrCreateSubdivision(String name) {
        PlotSubdivision s = getSubdivision(name);
        if (s == null) { s = new PlotSubdivision(name); subdivisions.add(s); }
        return s;
    }

    public boolean removeSubdivision(String name) {
        return subdivisions.removeIf(s -> s.name != null && s.name.equalsIgnoreCase(name));
    }

    /** Retorna la primera subdivisión que contenga este BlockPos, o null. */
    public PlotSubdivision getSubdivisionAt(BlockPos pos) {
        for (PlotSubdivision s : subdivisions) {
            if (s.isValid() && s.contains(pos)) return s;
        }
        return null;
    }

    // ── Expiración por inactividad ────────────────────────────────────────────
    public boolean isExpired(long currentTick) {
        if (hasRank) return false;
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg == null || !cfg.inactivityExpiry.enabled) return false;
        long inactiveDays = (currentTick - lastOwnerSeenTick) / 24000L;
        long maxDays = cfg.inactivityExpiry.baseDays + ((long) cfg.inactivityExpiry.daysPerTier * size.tier);
        return inactiveDays > maxDays;
    }

    public long getDaysInactive(long currentTick) {
        return (currentTick - lastOwnerSeenTick) / 24000L;
    }

    public long getDaysRemaining(long currentTick) {
        if (hasRank) return Long.MAX_VALUE;
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        if (cfg == null || !cfg.inactivityExpiry.enabled) return Long.MAX_VALUE;
        long inactiveDays = (currentTick - lastOwnerSeenTick) / 24000L;
        long maxDays = cfg.inactivityExpiry.baseDays + ((long) cfg.inactivityExpiry.daysPerTier * size.tier);
        return Math.max(0, maxDays - inactiveDays);
    }

    public String getMemberName(UUID uuid) {
        return memberNames.getOrDefault(uuid, uuid.toString().substring(0, 8));
    }

    // ── NBT ───────────────────────────────────────────────────────────────────
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("ownerId",   ownerId.toString());
        nbt.putString("ownerName", ownerName);
        nbt.putInt("cx", center.getX());
        nbt.putInt("cy", center.getY());
        nbt.putInt("cz", center.getZ());
        nbt.putInt("sizeTier", size.tier);
        nbt.putBoolean("hasRank", hasRank);
        nbt.putLong("placedAt",        placedAtTick);
        nbt.putLong("lastOwnerSeen",   lastOwnerSeenTick);
        nbt.putString("plotName",      plotName);

        NbtList membersList = new NbtList();
        for (Map.Entry<UUID, Role> entry : members.entrySet()) {
            NbtCompound m = new NbtCompound();
            m.putString("uuid", entry.getKey().toString());
            m.putString("role", entry.getValue().name());
            m.putString("name", memberNames.getOrDefault(entry.getKey(), "Unknown"));
            Set<Permission> perms = memberPerms.getOrDefault(entry.getKey(), defaultPermsFor(entry.getValue()));
            StringBuilder sb = new StringBuilder();
            for (Permission p : perms) { if (sb.length() > 0) sb.append(","); sb.append(p.name()); }
            m.putString("perms", sb.toString());
            membersList.add(m);
        }
        nbt.put("members", membersList);

        NbtList flagsList = new NbtList();
        for (Flag f : flags) flagsList.add(NbtString.of(f.name()));
        nbt.put("flags", flagsList);

        NbtList groupsList = new NbtList();
        for (PermissionGroup g : groups) groupsList.add(g.toNbt());
        nbt.put("groups", groupsList);

        // Subdivisiones
        NbtList subList = new NbtList();
        for (PlotSubdivision s : subdivisions) subList.add(s.toNbt());
        nbt.put("subdivisions", subList);

        return nbt;
    }

    public static PlotData fromNbt(NbtCompound nbt) {
        PlotData data = new PlotData();
        data.ownerId    = UUID.fromString(nbt.getString("ownerId"));
        data.ownerName  = nbt.getString("ownerName");
        data.center     = new BlockPos(nbt.getInt("cx"), nbt.getInt("cy"), nbt.getInt("cz"));
        data.size       = PlotSize.fromTier(nbt.getInt("sizeTier"));
        data.hasRank    = nbt.getBoolean("hasRank");
        data.placedAtTick = nbt.getLong("placedAt");
        data.lastOwnerSeenTick = nbt.contains("lastOwnerSeen") ? nbt.getLong("lastOwnerSeen") : data.placedAtTick;
        data.plotName   = nbt.getString("plotName");

        NbtList membersList = nbt.getList("members", 10);
        for (int i = 0; i < membersList.size(); i++) {
            NbtCompound m = membersList.getCompound(i);
            UUID uuid  = UUID.fromString(m.getString("uuid"));
            Role role  = Role.valueOf(m.getString("role"));
            String name = m.getString("name");
            data.members.put(uuid, role);
            data.memberNames.put(uuid, name);
            String permsStr = m.getString("perms");
            if (permsStr != null && !permsStr.isEmpty()) {
                Set<Permission> perms = new HashSet<>();
                for (String ps : permsStr.split(",")) {
                    try { perms.add(Permission.valueOf(ps.trim())); } catch (Exception ignored) {}
                }
                data.memberPerms.put(uuid, perms);
            } else {
                data.memberPerms.put(uuid, defaultPermsFor(role));
            }
        }

        if (nbt.contains("flags")) {
            NbtList flagsList = nbt.getList("flags", 8);
            for (int i = 0; i < flagsList.size(); i++) {
                try { data.flags.add(Flag.valueOf(flagsList.getString(i))); } catch (Exception ignored) {}
            }
        } else {
            data.flags.add(Flag.ALLOW_TP);
            data.flags.add(Flag.GREETINGS);
        }

        if (nbt.contains("groups")) {
            NbtList groupsList = nbt.getList("groups", 10);
            for (int i = 0; i < groupsList.size(); i++) {
                data.groups.add(PermissionGroup.fromNbt(groupsList.getCompound(i)));
            }
        }

        if (nbt.contains("subdivisions")) {
            NbtList subList = nbt.getList("subdivisions", 10);
            for (int i = 0; i < subList.size(); i++) {
                data.subdivisions.add(PlotSubdivision.fromNbt(subList.getCompound(i)));
            }
        }

        return data;
    }
}

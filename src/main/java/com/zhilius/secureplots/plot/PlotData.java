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

    /**
     * Permisos individuales que se pueden asignar por miembro o grupo.
     * También se usan como flags globales (para VISITOR y MEMBER por defecto).
     */
    public enum Permission {
        // Construcción
        BUILD,          // colocar/romper bloques
        // Interacción
        INTERACT,       // palancas, puertas, botones
        CONTAINERS,     // cofres, baúles, etc.
        // PvP
        PVP,            // atacar jugadores dentro de la plot
        // Admin / gestión
        MANAGE_MEMBERS, // agregar/remover miembros (equiv. ADMIN)
        MANAGE_PERMS,   // cambiar permisos de miembros
        MANAGE_FLAGS,   // cambiar flags globales
        MANAGE_GROUPS,  // crear/editar grupos de la plot
        // Teleport
        TP,             // teletransportarse a la plot (si está habilitado)
        // Misc
        FLY,            // volar dentro de la plot (si el servidor lo soporta)
        ENTER           // entrar al área de la plot
    }

    // ── Flags globales ────────────────────────────────────────────────────────
    // Afectan a TODOS (incluso a no-miembros) si están activados
    public enum Flag {
        ALLOW_VISITOR_BUILD,     // no-miembros pueden construir
        ALLOW_VISITOR_INTERACT,  // no-miembros pueden interactuar
        ALLOW_VISITOR_CONTAINERS,// no-miembros pueden abrir contenedores
        ALLOW_PVP,               // pvp habilitado en la plot
        ALLOW_FLY,               // volar habilitado en la plot
        ALLOW_TP,                // se puede teleportar a la plot (/sp tp)
        GREETINGS                // mostrar mensaje de bienvenida al entrar
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

    private Map<UUID, Role> members = new HashMap<>();
    private Map<UUID, String> memberNames = new HashMap<>();
    private Map<UUID, Set<Permission>> memberPerms = new HashMap<>();

    // Flags globales activas (por defecto vacío = todas OFF)
    private Set<Flag> flags = new HashSet<>();

    // Grupos de permisos personalizados
    private List<PermissionGroup> groups = new ArrayList<>();

    private String plotName;

    // ── Constructores ─────────────────────────────────────────────────────────
    public PlotData(UUID ownerId, String ownerName, BlockPos center, PlotSize size, long currentTick) {
        this.ownerId = ownerId;
        this.ownerName = ownerName;
        this.center = center;
        this.size = size;
        this.placedAtTick = currentTick;
        this.lastOwnerSeenTick = currentTick;
        this.plotName = ownerName + "'s Plot";
        this.hasRank = false;
        // Flags por defecto
        this.flags.add(Flag.ALLOW_TP);
        this.flags.add(Flag.GREETINGS);
    }

    public PlotData() {}

    // ── Getters / Setters básicos ─────────────────────────────────────────────
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

    // ── Flags globales ────────────────────────────────────────────────────────
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
        // Quitar de grupos también
        for (PermissionGroup g : groups) g.members.remove(uuid);
    }

    public Role getRoleOf(UUID uuid) {
        if (uuid.equals(ownerId)) return Role.OWNER;
        return members.getOrDefault(uuid, Role.VISITOR);
    }

    // ── Permisos por defecto según rol ────────────────────────────────────────
    public static Set<Permission> defaultPermsFor(Role role) {
        Set<Permission> perms = new HashSet<>();
        switch (role) {
            case OWNER -> {
                perms.addAll(Arrays.asList(Permission.values()));
            }
            case ADMIN -> {
                perms.add(Permission.BUILD);
                perms.add(Permission.INTERACT);
                perms.add(Permission.CONTAINERS);
                perms.add(Permission.PVP);
                perms.add(Permission.MANAGE_MEMBERS);
                perms.add(Permission.MANAGE_PERMS);
                perms.add(Permission.TP);
                perms.add(Permission.ENTER);
            }
            case MEMBER -> {
                perms.add(Permission.BUILD);
                perms.add(Permission.INTERACT);
                perms.add(Permission.CONTAINERS);
                perms.add(Permission.TP);
                perms.add(Permission.ENTER);
            }
            case VISITOR -> {
                perms.add(Permission.INTERACT);
                perms.add(Permission.ENTER);
            }
        }
        return perms;
    }

    // ── Resolver permisos efectivos de un jugador ─────────────────────────────
    public Set<Permission> getPermsOf(UUID uuid) {
        if (uuid.equals(ownerId)) return EnumSet.allOf(Permission.class);

        // Permisos base según rol o asignación individual
        Set<Permission> perms = new HashSet<>(memberPerms.getOrDefault(uuid, defaultPermsFor(getRoleOf(uuid))));

        // Añadir permisos de grupos
        for (PermissionGroup g : groups) {
            if (g.members.contains(uuid)) {
                perms.addAll(g.permissions);
            }
        }

        // Flags globales que aplican permisos a todos
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

    public boolean canBuild(UUID uuid)     { return hasPermission(uuid, Permission.BUILD); }
    public boolean canInteract(UUID uuid)  { return hasPermission(uuid, Permission.INTERACT); }
    public boolean canManage(UUID uuid)    { return hasPermission(uuid, Permission.MANAGE_MEMBERS); }

    // ── Grupos ────────────────────────────────────────────────────────────────
    public PermissionGroup getGroup(String name) {
        for (PermissionGroup g : groups) {
            if (g.name.equalsIgnoreCase(name)) return g;
        }
        return null;
    }

    public PermissionGroup getOrCreateGroup(String name) {
        PermissionGroup g = getGroup(name);
        if (g == null) {
            g = new PermissionGroup(name);
            groups.add(g);
        }
        return g;
    }

    public boolean removeGroup(String name) {
        return groups.removeIf(g -> g.name.equalsIgnoreCase(name));
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

    // ── NBT Serialization ─────────────────────────────────────────────────────
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("ownerId", ownerId.toString());
        nbt.putString("ownerName", ownerName);
        nbt.putInt("cx", center.getX());
        nbt.putInt("cy", center.getY());
        nbt.putInt("cz", center.getZ());
        nbt.putInt("sizeTier", size.tier);
        nbt.putBoolean("hasRank", hasRank);
        nbt.putLong("placedAt", placedAtTick);
        nbt.putLong("lastOwnerSeen", lastOwnerSeenTick);
        nbt.putString("plotName", plotName);

        // Miembros
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

        // Flags
        NbtList flagsList = new NbtList();
        for (Flag f : flags) flagsList.add(NbtString.of(f.name()));
        nbt.put("flags", flagsList);

        // Grupos
        NbtList groupsList = new NbtList();
        for (PermissionGroup g : groups) groupsList.add(g.toNbt());
        nbt.put("groups", groupsList);

        return nbt;
    }

    public static PlotData fromNbt(NbtCompound nbt) {
        PlotData data = new PlotData();
        data.ownerId = UUID.fromString(nbt.getString("ownerId"));
        data.ownerName = nbt.getString("ownerName");
        data.center = new BlockPos(nbt.getInt("cx"), nbt.getInt("cy"), nbt.getInt("cz"));
        data.size = PlotSize.fromTier(nbt.getInt("sizeTier"));
        data.hasRank = nbt.getBoolean("hasRank");
        data.placedAtTick = nbt.getLong("placedAt");
        data.lastOwnerSeenTick = nbt.contains("lastOwnerSeen") ? nbt.getLong("lastOwnerSeen") : data.placedAtTick;
        data.plotName = nbt.getString("plotName");

        // Miembros
        NbtList membersList = nbt.getList("members", 10);
        for (int i = 0; i < membersList.size(); i++) {
            NbtCompound m = membersList.getCompound(i);
            UUID uuid = UUID.fromString(m.getString("uuid"));
            Role role = Role.valueOf(m.getString("role"));
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

        // Flags
        if (nbt.contains("flags")) {
            NbtList flagsList = nbt.getList("flags", 8);
            for (int i = 0; i < flagsList.size(); i++) {
                try { data.flags.add(Flag.valueOf(flagsList.getString(i))); } catch (Exception ignored) {}
            }
        } else {
            // Retrocompatibilidad: flags por defecto
            data.flags.add(Flag.ALLOW_TP);
            data.flags.add(Flag.GREETINGS);
        }

        // Grupos
        if (nbt.contains("groups")) {
            NbtList groupsList = nbt.getList("groups", 10);
            for (int i = 0; i < groupsList.size(); i++) {
                data.groups.add(PermissionGroup.fromNbt(groupsList.getCompound(i)));
            }
        }

        return data;
    }
}

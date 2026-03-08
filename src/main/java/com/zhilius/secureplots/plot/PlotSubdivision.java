package com.zhilius.secureplots.plot;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

import java.util.*;

/**
 * Una subdivisión dentro de una PlotData.
 *
 * La forma se define como un polígono de puntos (x, z) en coordenadas de mundo.
 * Si useY == true, la subdivisión ocupa solo el rango vertical [yMin, yMax].
 * Si useY == false, ocupa toda la altura (igual que los rayos de energía del borde).
 *
 * Los permisos sobreescriben los de la plot principal para los jugadores que
 * estén dentro de esta subdivisión.
 */
public class PlotSubdivision {

    public String name;

    /** Puntos del polígono en orden. Mínimo 3 para forma cerrada. */
    public List<int[]> points = new ArrayList<>(); // cada int[]: {x, z}

    /** Si true, restringe la altura al rango [yMin, yMax]. */
    public boolean useY = false;
    public int yMin = 0;
    public int yMax = 255;

    // Permisos de esta subdivisión (sobreescriben los de la plot para quien esté aquí)
    public Map<UUID, Set<PlotData.Permission>> memberPerms = new HashMap<>();
    public Set<PlotData.Flag>                 flags        = new HashSet<>();

    public PlotSubdivision() {}

    public PlotSubdivision(String name) {
        this.name = name;
    }

    // ── Puntos ────────────────────────────────────────────────────────────────

    public void addPoint(int x, int z) {
        points.add(new int[]{x, z});
    }

    public void removeLastPoint() {
        if (!points.isEmpty()) points.remove(points.size() - 1);
    }

    public void clearPoints() {
        points.clear();
    }

    /** Retorna true si los puntos forman un polígono cerrado válido (≥3 puntos). */
    public boolean isValid() {
        return points.size() >= 3;
    }

    // ── Contención ────────────────────────────────────────────────────────────

    /**
     * Devuelve true si el BlockPos está dentro de esta subdivisión.
     * Usa ray-casting para el polígono 2D, y chequeo de Y si useY == true.
     */
    public boolean contains(net.minecraft.util.math.BlockPos pos) {
        if (points.size() < 3) return false;
        if (useY && (pos.getY() < yMin || pos.getY() > yMax)) return false;
        return pointInPolygon(pos.getX(), pos.getZ());
    }

    /**
     * Ray-casting algorithm para polígono 2D.
     * Devuelve true si (px, pz) está dentro del polígono definido por {@code points}.
     */
    private boolean pointInPolygon(int px, int pz) {
        int n = points.size();
        boolean inside = false;
        int j = n - 1;
        for (int i = 0; i < n; i++) {
            int xi = points.get(i)[0], zi = points.get(i)[1];
            int xj = points.get(j)[0], zj = points.get(j)[1];
            if (((zi > pz) != (zj > pz)) &&
                (px < (long)(xj - xi) * (pz - zi) / (zj - zi) + xi)) {
                inside = !inside;
            }
            j = i;
        }
        return inside;
    }

    // ── Permisos en la subdivisión ────────────────────────────────────────────

    public boolean hasPermission(UUID uuid, PlotData.Permission perm) {
        Set<PlotData.Permission> perms = memberPerms.get(uuid);
        if (perms == null) return false;
        return perms.contains(perm);
    }

    public void setPermission(UUID uuid, PlotData.Permission perm, boolean enabled) {
        Set<PlotData.Permission> perms = memberPerms.computeIfAbsent(uuid, k -> new HashSet<>());
        if (enabled) perms.add(perm); else perms.remove(perm);
    }

    public boolean hasFlag(PlotData.Flag flag) { return flags.contains(flag); }
    public void setFlag(PlotData.Flag flag, boolean enabled) {
        if (enabled) flags.add(flag); else flags.remove(flag);
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("name", name != null ? name : "");
        nbt.putBoolean("useY", useY);
        nbt.putInt("yMin", yMin);
        nbt.putInt("yMax", yMax);

        NbtList ptList = new NbtList();
        for (int[] pt : points) {
            NbtCompound p = new NbtCompound();
            p.putInt("x", pt[0]);
            p.putInt("z", pt[1]);
            ptList.add(p);
        }
        nbt.put("points", ptList);

        NbtList permsNbt = new NbtList();
        for (Map.Entry<UUID, Set<PlotData.Permission>> e : memberPerms.entrySet()) {
            NbtCompound entry = new NbtCompound();
            entry.putString("uuid", e.getKey().toString());
            StringBuilder sb = new StringBuilder();
            for (PlotData.Permission p : e.getValue()) {
                if (sb.length() > 0) sb.append(",");
                sb.append(p.name());
            }
            entry.putString("perms", sb.toString());
            permsNbt.add(entry);
        }
        nbt.put("memberPerms", permsNbt);

        NbtList flagsNbt = new NbtList();
        for (PlotData.Flag f : flags) flagsNbt.add(net.minecraft.nbt.NbtString.of(f.name()));
        nbt.put("flags", flagsNbt);

        return nbt;
    }

    public static PlotSubdivision fromNbt(NbtCompound nbt) {
        PlotSubdivision sub = new PlotSubdivision();
        sub.name  = nbt.getString("name");
        sub.useY  = nbt.getBoolean("useY");
        sub.yMin  = nbt.getInt("yMin");
        sub.yMax  = nbt.getInt("yMax");

        NbtList ptList = nbt.getList("points", 10);
        for (int i = 0; i < ptList.size(); i++) {
            NbtCompound p = ptList.getCompound(i);
            sub.points.add(new int[]{p.getInt("x"), p.getInt("z")});
        }

        NbtList permsNbt = nbt.getList("memberPerms", 10);
        for (int i = 0; i < permsNbt.size(); i++) {
            NbtCompound entry = permsNbt.getCompound(i);
            try {
                UUID uuid = UUID.fromString(entry.getString("uuid"));
                Set<PlotData.Permission> perms = new HashSet<>();
                String permsStr = entry.getString("perms");
                if (!permsStr.isEmpty()) {
                    for (String ps : permsStr.split(",")) {
                        try { perms.add(PlotData.Permission.valueOf(ps.trim())); } catch (Exception ignored) {}
                    }
                }
                sub.memberPerms.put(uuid, perms);
            } catch (Exception ignored) {}
        }

        NbtList flagsNbt = nbt.getList("flags", 8);
        for (int i = 0; i < flagsNbt.size(); i++) {
            try { sub.flags.add(PlotData.Flag.valueOf(flagsNbt.getString(i))); } catch (Exception ignored) {}
        }

        return sub;
    }
}

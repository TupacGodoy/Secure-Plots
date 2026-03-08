package com.zhilius.secureplots.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.util.*;

public class SecurePlotsConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "secure_plots.json");

    public static SecurePlotsConfig INSTANCE;

    // ── Upgrade costs ─────────────────────────────────────────────────────────
    public List<UpgradeCost> upgradeCosts = new ArrayList<>();

    // ── Limits ───────────────────────────────────────────────────────────────
    /** Max plots per player (0 = unlimited) */
    public int maxPlotsPerPlayer = 3;

    /** How many blocks outside the border members can still reach-interact */
    public int memberReachBonus = 5;

    /** Max subdivisions per plot (0 = unlimited) */
    public int maxSubdivisionsPerPlot = 10;

    /** Max points per subdivision polygon (0 = unlimited, min effective: 3) */
    public int maxSubdivisionPoints = 32;

    // ── Inactivity expiry ─────────────────────────────────────────────────────
    public InactivityExpiry inactivityExpiry = new InactivityExpiry();

    public static class InactivityExpiry {
        public boolean enabled  = false;
        public int baseDays     = 45;
        public int daysPerTier  = 5;
    }

    // ── Economy ───────────────────────────────────────────────────────────────
    public String cobblescoinsItemId = "cobbleverse:cobblecoin";

    // ── Subdivision tool ──────────────────────────────────────────────────────
    public SubdivisionToolConfig subdivisionTool = new SubdivisionToolConfig();

    public static class SubdivisionToolConfig {
        /**
         * Ítem base que se usa para crafting de la herramienta de subdivisiones.
         * Por defecto: "minecraft:stick" — puede cambiarse a cualquier ítem.
         */
        public String recipeBaseItem = "minecraft:stick";

        /**
         * Si true, la herramienta solo se puede usar dentro de una plot donde
         * el jugador tenga MANAGE_SUBDIVISIONS. Si false, cualquier miembro puede verla.
         */
        public boolean requireManagePermission = true;
    }

    // ── Blocked zones (no se pueden colocar plots) ────────────────────────────
    /**
     * Lista de zonas bloqueadas donde no se puede colocar ninguna protección.
     * Cada zona puede definirse de distintas formas:
     *
     *  type "circle":  bloquea un radio circular alrededor de (centerX, centerZ)
     *  type "rect":    bloquea un rectángulo definido por (minX, minZ, maxX, maxZ)
     *  type "structure": igual que "circle" pero el label es semántico (para documentar
     *                    que proviene de una estructura del mundo — el admin pone coords manualmente)
     *
     * Ejemplo de config:
     * {
     *   "type": "circle",
     *   "label": "Spawn",
     *   "centerX": 0, "centerZ": 0, "radius": 100
     * }
     * {
     *   "type": "rect",
     *   "label": "Event Arena",
     *   "minX": -200, "minZ": -200, "maxX": 200, "maxZ": 200
     * }
     * {
     *   "type": "structure",
     *   "label": "village:my_village",
     *   "centerX": 512, "centerZ": -256, "radius": 64
     * }
     */
    public List<BlockedZone> blockedZones = new ArrayList<>();

    public static class BlockedZone {
        /** "circle", "rect" o "structure" */
        public String type   = "circle";
        public String label  = "";

        // Para tipo circle / structure
        public int centerX = 0;
        public int centerZ = 0;
        public int radius  = 100;

        // Para tipo rect
        public int minX = 0;
        public int minZ = 0;
        public int maxX = 0;
        public int maxZ = 0;

        /**
         * Retorna true si (x, z) está dentro de esta zona bloqueada.
         */
        public boolean contains(int x, int z) {
            switch (type) {
                case "circle":
                case "structure": {
                    long dx = x - centerX, dz = z - centerZ;
                    return dx * dx + dz * dz <= (long) radius * radius;
                }
                case "rect": {
                    return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
                }
                default: return false;
            }
        }

        /**
         * Retorna true si el área de la plot (centro ± half) toca esta zona bloqueada.
         * Usamos el método conservador: si algún punto del borde de la plot queda en la zona.
         */
        public boolean overlapsPlot(int plotCenterX, int plotCenterZ, int plotHalfSize) {
            // Clamp al rectángulo más cercano del círculo/rect
            int nearX = Math.max(plotCenterX - plotHalfSize, Math.min(plotCenterX + plotHalfSize, clampedX(plotCenterX)));
            int nearZ = Math.max(plotCenterZ - plotHalfSize, Math.min(plotCenterZ + plotHalfSize, clampedZ(plotCenterZ)));
            return contains(nearX, nearZ)
                || contains(plotCenterX - plotHalfSize, plotCenterZ - plotHalfSize)
                || contains(plotCenterX + plotHalfSize, plotCenterZ - plotHalfSize)
                || contains(plotCenterX - plotHalfSize, plotCenterZ + plotHalfSize)
                || contains(plotCenterX + plotHalfSize, plotCenterZ + plotHalfSize)
                || contains(plotCenterX, plotCenterZ);
        }

        private int clampedX(int refX) {
            return switch (type) {
                case "rect" -> Math.max(minX, Math.min(maxX, refX));
                default -> centerX;
            };
        }

        private int clampedZ(int refZ) {
            return switch (type) {
                case "rect" -> Math.max(minZ, Math.min(maxZ, refZ));
                default -> centerZ;
            };
        }
    }

    // ── Upgrade costs ─────────────────────────────────────────────────────────
    public static class UpgradeCost {
        public int fromTier;
        public int toTier;
        public int cobblecoins;
        public List<ItemCost> items = new ArrayList<>();

        public static class ItemCost {
            public String itemId;
            public int amount;
            public ItemCost(String itemId, int amount) {
                this.itemId = itemId; this.amount = amount;
            }
        }
    }

    // ── Load / Save ───────────────────────────────────────────────────────────
    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (Reader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, SecurePlotsConfig.class);
                if (INSTANCE == null) INSTANCE = createDefault();
            } catch (IOException e) {
                e.printStackTrace();
                INSTANCE = createDefault();
            }
        } else {
            INSTANCE = createDefault();
            save();
        }
    }

    public static void save() {
        try (Writer writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static SecurePlotsConfig createDefault() {
        SecurePlotsConfig config = new SecurePlotsConfig();

        UpgradeCost u1 = new UpgradeCost();
        u1.fromTier = 0; u1.toTier = 1; u1.cobblecoins = 0;
        u1.items.add(new UpgradeCost.ItemCost("minecraft:gold_block", 15));
        config.upgradeCosts.add(u1);

        UpgradeCost u2 = new UpgradeCost();
        u2.fromTier = 1; u2.toTier = 2; u2.cobblecoins = 0;
        u2.items.add(new UpgradeCost.ItemCost("minecraft:emerald_block", 10));
        config.upgradeCosts.add(u2);

        UpgradeCost u3 = new UpgradeCost();
        u3.fromTier = 2; u3.toTier = 3; u3.cobblecoins = 0;
        u3.items.add(new UpgradeCost.ItemCost("minecraft:diamond", 64));
        config.upgradeCosts.add(u3);

        UpgradeCost u4 = new UpgradeCost();
        u4.fromTier = 3; u4.toTier = 4; u4.cobblecoins = 0;
        u4.items.add(new UpgradeCost.ItemCost("minecraft:netherite_block", 1));
        config.upgradeCosts.add(u4);

        // Zona bloqueada de ejemplo: spawn
        BlockedZone spawn = new BlockedZone();
        spawn.type = "circle"; spawn.label = "Spawn"; spawn.centerX = 0; spawn.centerZ = 0; spawn.radius = 50;
        config.blockedZones.add(spawn);

        return config;
    }

    public UpgradeCost getUpgradeCost(int fromTier) {
        for (UpgradeCost cost : upgradeCosts) {
            if (cost.fromTier == fromTier) return cost;
        }
        return null;
    }

    /**
     * Retorna true si la posición (centerX, centerZ) con el radio dado NO puede
     * colocarse por chocar con alguna zona bloqueada.
     */
    public boolean isBlockedByZone(int centerX, int centerZ, int halfSize) {
        for (BlockedZone zone : blockedZones) {
            if (zone.overlapsPlot(centerX, centerZ, halfSize)) return true;
        }
        return false;
    }
}

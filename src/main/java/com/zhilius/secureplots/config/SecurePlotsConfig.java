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

    // ── General ────────────────────────────────────────────────────────────────

    /** Máximo de parcelas por jugador (0 = ilimitado). */
    public int maxPlotsPerPlayer = 3;

    /** Buffer mínimo en bloques entre parcelas para evitar solapamiento. */
    public int plotBuffer = 15;

    /**
     * Tag de comando que otorga permisos de administrador sobre todas las parcelas.
     * Se asigna con: /tag <jugador> add <valor>
     */
    public String adminTag = "plot_admin";

    /**
     * ID del ítem de Cobblecoins (integración con mod cobbleverse).
     * Si no se usa ese mod, este campo se ignora.
     */
    public String cobblescoinsItemId = "cobbleverse:cobblecoin";

    /**
     * Prefijos de estructuras bloqueadas: no se puede colocar una parcela
     * sobre estructuras cuyos IDs comiencen con alguno de estos prefijos.
     */
    public List<String> blockedStructurePrefixes = new ArrayList<>(
            Arrays.asList("cobbleverse:", "legendarymonuments:")
    );

    // ── Inactividad ────────────────────────────────────────────────────────────

    /** Configuración del sistema de expiración por inactividad del dueño. */
    public InactivityExpiry inactivityExpiry = new InactivityExpiry();

    public static class InactivityExpiry {
        /** Si está en true, las parcelas expiran cuando el dueño es inactivo. */
        public boolean enabled = false;
        /** Días base de inactividad antes de expirar. */
        public int baseDays = 45;
        /** Días extra de gracia por cada nivel de mejora. */
        public int daysPerTier = 5;
    }

    // ── Tiers de parcela ───────────────────────────────────────────────────────

    /**
     * Configuración de cada tier de parcela (0 = Bronce, 4 = Netherita).
     * Permite cambiar radio, nombre, luminosidad, dureza y resistencia.
     */
    public List<TierConfig> tiers = new ArrayList<>();

    public static class TierConfig {
        /** Número de tier (0-4). */
        public int tier;
        /** Nombre visible en el menú y comandos. */
        public String displayName;
        /** Radio de la parcela en bloques (el área total es radio×radio). */
        public int radius;
        /** Luminosidad del bloque de parcela (0-15). */
        public int luminance;
        /** Dureza del bloque (tiempo de minado). */
        public float hardness;
        /** Resistencia a explosiones del bloque. */
        public float blastResistance;

        public TierConfig() {}

        public TierConfig(int tier, String displayName, int radius, int luminance,
                          float hardness, float blastResistance) {
            this.tier = tier;
            this.displayName = displayName;
            this.radius = radius;
            this.luminance = luminance;
            this.hardness = hardness;
            this.blastResistance = blastResistance;
        }
    }

    // ── Costos de mejora ───────────────────────────────────────────────────────

    /** Costos para subir de tier. Soporta ítems de cualquier mod. */
    public List<UpgradeCost> upgradeCosts = new ArrayList<>();

    public static class UpgradeCost {
        public int fromTier;
        public int toTier;
        public int cobblecoins;
        public List<ItemCost> items = new ArrayList<>();

        public static class ItemCost {
            public String itemId;
            public int amount;

            public ItemCost() {}

            public ItemCost(String itemId, int amount) {
                this.itemId = itemId;
                this.amount = amount;
            }
        }
    }

    // ── Permisos por defecto por rol ───────────────────────────────────────────

    /**
     * Permisos que se asignan automáticamente a un miembro según su rol.
     * Permisos válidos: BUILD, INTERACT, CONTAINERS, PVP,
     *   MANAGE_MEMBERS, MANAGE_PERMS, MANAGE_FLAGS, MANAGE_GROUPS,
     *   TP, FLY, ENTER
     */
    public RoleDefaults roleDefaults = new RoleDefaults();

    public static class RoleDefaults {
        public List<String> admin = new ArrayList<>(Arrays.asList(
                "BUILD", "INTERACT", "CONTAINERS", "PVP",
                "MANAGE_MEMBERS", "MANAGE_PERMS", "TP", "ENTER"
        ));
        public List<String> member = new ArrayList<>(Arrays.asList(
                "BUILD", "INTERACT", "CONTAINERS", "TP", "ENTER"
        ));
        public List<String> visitor = new ArrayList<>(Arrays.asList(
                "INTERACT", "ENTER"
        ));
    }

    // ── Flags por defecto en parcelas nuevas ───────────────────────────────────

    /**
     * Flags globales activadas al crear una parcela nueva.
     * Flags válidas: ALLOW_VISITOR_BUILD, ALLOW_VISITOR_INTERACT,
     *   ALLOW_VISITOR_CONTAINERS, ALLOW_PVP, ALLOW_FLY, ALLOW_TP, GREETINGS
     */
    public List<String> defaultFlags = new ArrayList<>(Arrays.asList(
            "ALLOW_TP", "GREETINGS"
    ));

    // ── Carga / Guardado ───────────────────────────────────────────────────────

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (Reader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, SecurePlotsConfig.class);
                // Retrocompatibilidad: rellenar campos nuevos si faltan
                if (INSTANCE.tiers == null || INSTANCE.tiers.isEmpty()) {
                    INSTANCE.tiers = createDefaultTiers();
                }
                if (INSTANCE.roleDefaults == null) {
                    INSTANCE.roleDefaults = new RoleDefaults();
                }
                if (INSTANCE.defaultFlags == null || INSTANCE.defaultFlags.isEmpty()) {
                    INSTANCE.defaultFlags = new ArrayList<>(Arrays.asList("ALLOW_TP", "GREETINGS"));
                }
                if (INSTANCE.blockedStructurePrefixes == null) {
                    INSTANCE.blockedStructurePrefixes = new ArrayList<>(
                            Arrays.asList("cobbleverse:", "legendarymonuments:"));
                }
                if (INSTANCE.adminTag == null || INSTANCE.adminTag.isEmpty()) {
                    INSTANCE.adminTag = "plot_admin";
                }
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
        config.tiers = createDefaultTiers();

        // Bronce (0) → Oro (1)
        UpgradeCost u1 = new UpgradeCost();
        u1.fromTier = 0; u1.toTier = 1; u1.cobblecoins = 0;
        u1.items.add(new UpgradeCost.ItemCost("minecraft:gold_block", 15));
        config.upgradeCosts.add(u1);

        // Oro (1) → Esmeralda (2)
        UpgradeCost u2 = new UpgradeCost();
        u2.fromTier = 1; u2.toTier = 2; u2.cobblecoins = 0;
        u2.items.add(new UpgradeCost.ItemCost("minecraft:emerald_block", 10));
        config.upgradeCosts.add(u2);

        // Esmeralda (2) → Diamante (3)
        UpgradeCost u3 = new UpgradeCost();
        u3.fromTier = 2; u3.toTier = 3; u3.cobblecoins = 0;
        u3.items.add(new UpgradeCost.ItemCost("minecraft:diamond", 64));
        config.upgradeCosts.add(u3);

        // Diamante (3) → Netherita (4)
        UpgradeCost u4 = new UpgradeCost();
        u4.fromTier = 3; u4.toTier = 4; u4.cobblecoins = 0;
        u4.items.add(new UpgradeCost.ItemCost("minecraft:netherite_block", 1));
        config.upgradeCosts.add(u4);

        return config;
    }

    private static List<TierConfig> createDefaultTiers() {
        List<TierConfig> list = new ArrayList<>();
        list.add(new TierConfig(0, "Bronce",    15,  4, 50f, 1200f));
        list.add(new TierConfig(1, "Oro",       30,  5, 50f, 1200f));
        list.add(new TierConfig(2, "Esmeralda", 50,  6, 50f, 1200f));
        list.add(new TierConfig(3, "Diamante",  75,  7, 50f, 1200f));
        list.add(new TierConfig(4, "Netherita", 100, 8, 50f, 1200f));
        return list;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Obtiene el costo de mejora desde un tier específico. */
    public UpgradeCost getUpgradeCost(int fromTier) {
        for (UpgradeCost cost : upgradeCosts) {
            if (cost.fromTier == fromTier) return cost;
        }
        return null;
    }

    /** Obtiene la configuración de un tier. Si no existe, devuelve valores seguros. */
    public TierConfig getTierConfig(int tier) {
        for (TierConfig t : tiers) {
            if (t.tier == tier) return t;
        }
        return new TierConfig(tier, "Tier " + tier, 15 + tier * 10, 4 + tier, 50f, 1200f);
    }
}

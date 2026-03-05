package com.zhilius.secureplots.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.util.*;
import java.util.LinkedHashMap;

public class SecurePlotsConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "secure_plots.json");

    public static SecurePlotsConfig INSTANCE;

    // Upgrade costs per tier (tier 0->1, 1->2, etc.)
    // Each entry: list of "item_id:amount"
    public List<UpgradeCost> upgradeCosts = new ArrayList<>();

    // Max plots per player (0 = unlimited)
    public int maxPlotsPerPlayer = 3;

    /**
     * Tamaños de plot por tier (en bloques, e.g. 15 = 15x15).
     * Los administradores pueden editar estos valores en secure_plots.json para personalizar
     * el tamaño de cada tier sin necesidad de recompilar el mod.
     *
     * Valores por defecto:
     *   tier 0 (Bronce)    = 15x15
     *   tier 1 (Oro)       = 30x30
     *   tier 2 (Esmeralda) = 50x50
     *   tier 3 (Diamante)  = 75x75
     *   tier 4 (Netherita) = 100x100
     */
    public Map<Integer, Integer> plotSizes = new LinkedHashMap<>();

    // Cobblecoins mod item ID
    public String cobblescoinsItemId = "cobbleverse:cobblecoin";

    public static class UpgradeCost {
        public int fromTier;
        public int toTier;
        public int cobblecoins;
        public List<ItemCost> items = new ArrayList<>();

        public static class ItemCost {
            public String itemId;
            public int amount;

            public ItemCost(String itemId, int amount) {
                this.itemId = itemId;
                this.amount = amount;
            }
        }
    }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (Reader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, SecurePlotsConfig.class);
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

        // Tamaños por defecto de cada tier (editables por el admin en secure_plots.json)
        config.plotSizes.put(0, 15);   // Bronce:    15x15
        config.plotSizes.put(1, 30);   // Oro:       30x30
        config.plotSizes.put(2, 50);   // Esmeralda: 50x50
        config.plotSizes.put(3, 75);   // Diamante:  75x75
        config.plotSizes.put(4, 100);  // Netherita: 100x100

        // Tier 0 (Bronce 15x15) -> Tier 1 (Oro 30x30)
        UpgradeCost u1 = new UpgradeCost();
        u1.fromTier = 0; u1.toTier = 1; u1.cobblecoins = 500;
        u1.items.add(new UpgradeCost.ItemCost("minecraft:gold_ingot", 32));
        config.upgradeCosts.add(u1);

        // Tier 1 (Oro 30x30) -> Tier 2 (Esmeralda 50x50)
        UpgradeCost u2 = new UpgradeCost();
        u2.fromTier = 1; u2.toTier = 2; u2.cobblecoins = 1500;
        u2.items.add(new UpgradeCost.ItemCost("minecraft:emerald", 32));
        u2.items.add(new UpgradeCost.ItemCost("minecraft:gold_ingot", 64));
        config.upgradeCosts.add(u2);

        // Tier 2 (Esmeralda 50x50) -> Tier 3 (Diamante 75x75)
        UpgradeCost u3 = new UpgradeCost();
        u3.fromTier = 2; u3.toTier = 3; u3.cobblecoins = 3000;
        u3.items.add(new UpgradeCost.ItemCost("minecraft:diamond", 16));
        u3.items.add(new UpgradeCost.ItemCost("minecraft:emerald", 64));
        config.upgradeCosts.add(u3);

        // Tier 3 (Diamante 75x75) -> Tier 4 (Netherita 100x100)
        UpgradeCost u4 = new UpgradeCost();
        u4.fromTier = 3; u4.toTier = 4; u4.cobblecoins = 6000;
        u4.items.add(new UpgradeCost.ItemCost("minecraft:diamond", 32));
        u4.items.add(new UpgradeCost.ItemCost("minecraft:netherite_scrap", 8));
        config.upgradeCosts.add(u4);

        return config;
    }

    public UpgradeCost getUpgradeCost(int fromTier) {
        for (UpgradeCost cost : upgradeCosts) {
            if (cost.fromTier == fromTier) return cost;
        }
        return null;
    }

    /**
     * Devuelve el tamaño configurado para el tier dado.
     * Si no está en el mapa, devuelve -1 (se usará el valor por defecto del enum).
     */
    public int getPlotSize(int tier) {
        return plotSizes.getOrDefault(tier, -1);
    }
}

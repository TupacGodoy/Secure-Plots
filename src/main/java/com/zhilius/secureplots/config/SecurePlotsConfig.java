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

    // Upgrade costs per tier (tier 0->1, 1->2, etc.)
    // Each entry: list of "item_id:amount"
    public List<UpgradeCost> upgradeCosts = new ArrayList<>();

    // Max plots per player (0 = unlimited)
    public int maxPlotsPerPlayer = 3;

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

        // Bronze (tier 0, 15x15) → Gold (tier 1, 30x30): 15 bloques de oro
        UpgradeCost u1 = new UpgradeCost();
        u1.fromTier = 0; u1.toTier = 1; u1.cobblecoins = 0;
        u1.items.add(new UpgradeCost.ItemCost("minecraft:gold_block", 15));
        config.upgradeCosts.add(u1);

        // Gold (tier 1, 30x30) → Emerald (tier 2, 50x50): 10 bloques de esmeralda
        UpgradeCost u2 = new UpgradeCost();
        u2.fromTier = 1; u2.toTier = 2; u2.cobblecoins = 0;
        u2.items.add(new UpgradeCost.ItemCost("minecraft:emerald_block", 10));
        config.upgradeCosts.add(u2);

        // Emerald (tier 2, 50x50) → Diamond (tier 3, 75x75): 64 diamantes
        UpgradeCost u3 = new UpgradeCost();
        u3.fromTier = 2; u3.toTier = 3; u3.cobblecoins = 0;
        u3.items.add(new UpgradeCost.ItemCost("minecraft:diamond", 64));
        config.upgradeCosts.add(u3);

        // Diamond (tier 3, 75x75) → Netherite (tier 4, 100x100): 1 bloque de netherite
        UpgradeCost u4 = new UpgradeCost();
        u4.fromTier = 3; u4.toTier = 4; u4.cobblecoins = 0;
        u4.items.add(new UpgradeCost.ItemCost("minecraft:netherite_block", 1));
        config.upgradeCosts.add(u4);

        return config;
    }

    public UpgradeCost getUpgradeCost(int fromTier) {
        for (UpgradeCost cost : upgradeCosts) {
            if (cost.fromTier == fromTier) return cost;
        }
        return null;
    }
}

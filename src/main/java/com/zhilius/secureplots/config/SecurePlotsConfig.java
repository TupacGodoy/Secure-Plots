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
package com.zhilius.secureplots.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.util.*;

public class SecurePlotsConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(
        FabricLoader.getInstance().getConfigDir().toFile(), "secure_plots.json");

    public static SecurePlotsConfig INSTANCE;

    // ── Feature toggles ───────────────────────────────────────────────────────

    /** Master switch: enable/disable the entire plot protection system. */
    public boolean enableProtection = true;

    /** Allow players to fly inside plots that have the FLY flag/permission. */
    public boolean enableFlyInPlots = true;

    /** Show plot name + owner in the action bar when entering a plot. */
    public boolean enableEnterHud = true;

    /** Show enter/exit messages (GREETINGS flag) as title overlays. */
    public boolean enableGreetingMessages = true;

    /** Spawn ambient particles when entering a plot. */
    public boolean enablePlotParticles = true;

    /**
     * Number of particles in the entry burst (1–5).
     * Max: 5
     */
    public int particleCount = 3;

    /**
     * Continuous ambient particles spawned while the player is INSIDE the plot (1–5).
     * Spawned every ambientInterval ticks. Keep low to avoid TPS impact.
     */
    public int ambientParticleCount = 2;

    /**
     * How often (in ticks) to check if a player entered or left a plot.
     * 10 = every half second. Lower = more precise but more CPU usage.
     */
    public int checkInterval = 10;

    /**
     * How often (in ticks) to spawn continuous ambient particles inside a plot.
     * 20 = once per second. Higher = less TPS impact.
     */
    public int ambientInterval = 20;

    /** Play ambient music when entering a plot. */
    public boolean enablePlotMusic = true;

    /**
     * Plot music volume (0.1 – 4.0). Default: 4.0
     */
    public float musicVolume = 4.0f;

    /** Apply weather override when entering a plot. */
    public boolean enablePlotWeather = true;

    /** Apply time override when entering a plot. */
    public boolean enablePlotTime = true;

    /** Allow /sp tp teleportation to plots. */
    public boolean enablePlotTeleport = true;

    /** Show hologram over plot blocks. */
    public boolean enableHologram = true;

    /** Enable PvP control per-plot (ALLOW_PVP flag). */
    public boolean enablePvpControl = true;

    /** Allow plot upgrades (players can upgrade tier). */
    public boolean enableUpgrades = true;

    /** Allow custom permission groups inside plots. */
    public boolean enablePermissionGroups = true;

    /** Enable inactivity expiry system. */
    public boolean enableInactivityExpiry = false;

    /** If true, plot blocks are unbreakable by non-owners (ignores hardness). */
    public boolean plotBlocksUnbreakable = true;

    /** Allow players to place plots inside other plots (overrides area check). */
    public boolean allowNestedPlots = false;

    /** Minimum OP level required to use /sp admin commands (0–4). */
    public int adminOpLevel = 2;

    // ── General ───────────────────────────────────────────────────────────────

    /** Maximum plots per player (0 = unlimited). */
    public int maxPlotsPerPlayer = 3;

    /** Minimum buffer in blocks between plots to prevent overlap. */
    public int plotBuffer = 15;

    /**
     * Command tag that grants admin permissions over all plots.
     * Assign with: /tag <player> add <value>
     */
    public String adminTag = "plot_admin";

    /**
     * Blocked structure prefixes: plots cannot be placed over structures
     * whose IDs begin with any of these prefixes.
     */
    public List<String> blockedStructurePrefixes = new ArrayList<>(
        Arrays.asList("legendarymonuments:"));

    // ── Inactivity expiry ─────────────────────────────────────────────────────

    /** Configuration for the owner inactivity expiry system. */
    public InactivityExpiry inactivityExpiry = new InactivityExpiry();

    public static class InactivityExpiry {
        /** If true, plots expire when the owner is inactive. */
        public boolean enabled = false;
        /** Base inactivity days before a plot expires. */
        public int baseDays = 45;
        /** Extra grace days per upgrade tier. */
        public int daysPerTier = 5;
    }

    // ── Plot tiers ────────────────────────────────────────────────────────────

    /**
     * Configuration for each plot tier (0 = Bronze, 4 = Netherite).
     * Allows changing radius, display name, luminance, hardness and blast resistance.
     */
    public List<TierConfig> tiers = new ArrayList<>();

    public static class TierConfig {
        /** Tier number (0–4). */
        public int tier;
        /** Display name shown in menus and commands. */
        public String displayName;
        /** Plot radius in blocks (total area = radius × radius). */
        public int radius;
        /** Block luminance (0–15). */
        public int luminance;
        /** Block hardness (mining time). */
        public float hardness;
        /** Block blast resistance. */
        public float blastResistance;

        public TierConfig() {}

        public TierConfig(int tier, String displayName, int radius, int luminance,
                          float hardness, float blastResistance) {
            this.tier            = tier;
            this.displayName     = displayName;
            this.radius          = radius;
            this.luminance       = luminance;
            this.hardness        = hardness;
            this.blastResistance = blastResistance;
        }
    }

    // ── Upgrade costs ─────────────────────────────────────────────────────────

    /**
     * Costs to upgrade from one tier to the next.
     * Supports items from any mod.
     * Optionally add a "cobblecoins" field (int) if using cobbleverse economy.
     */
    public List<UpgradeCost> upgradeCosts = new ArrayList<>();

    public static class UpgradeCost {
        public int fromTier;
        public int toTier;
        public List<ItemCost> items = new ArrayList<>();

        public static class ItemCost {
            public String itemId;
            public int    amount;

            public ItemCost() {}
            public ItemCost(String itemId, int amount) {
                this.itemId  = itemId;
                this.amount  = amount;
            }
        }
    }

    // ── Crafting recipes ──────────────────────────────────────────────────────

    /**
     * Crafting recipes for plot blocks and the blueprint item.
     * Pattern is a 3×3 grid of strings (3 rows, each char maps to a key).
     * Use " " (space) for empty slots.
     * Each key maps to any mod item ID.
     * If disabled=true the recipe is not registered (item only obtainable in creative
     * or via /sp creative).
     */
    public List<CraftingRecipe> craftingRecipes = new ArrayList<>();

    public static class CraftingRecipe {
        /** Result item ID, e.g. "secure-plots:bronze_plot_block". */
        public String result;
        /** Crafting pattern: exactly 3 strings of 3 characters. */
        public String[] pattern;
        /** Key map: char → item ID. Each key is a character from the pattern. */
        public Map<String, String> key = new LinkedHashMap<>();
        /** If true, the recipe is not registered. */
        public boolean disabled = false;

        public CraftingRecipe() {}
        public CraftingRecipe(String result, String[] pattern, Map<String, String> key) {
            this.result  = result;
            this.pattern = pattern;
            this.key     = key;
        }
    }

    // ── Default permissions by role ───────────────────────────────────────────

    /**
     * Permissions automatically assigned to a member based on their role.
     * Valid permissions: BUILD, BREAK, PLACE, INTERACT, CONTAINERS, USE_BEDS,
     *   USE_CRAFTING, USE_ENCHANTING, USE_ANVIL, USE_FURNACE, USE_BREWING,
     *   ATTACK_MOBS, ATTACK_ANIMALS, PVP, RIDE_ENTITIES, INTERACT_MOBS,
     *   LEASH_MOBS, SHEAR_MOBS, MILK_MOBS, CROP_TRAMPLING, PICKUP_ITEMS,
     *   DROP_ITEMS, BREAK_CROPS, PLANT_SEEDS, USE_BONEMEAL, BREAK_DECOR,
     *   DETONATE_TNT, GRIEFING, TP, FLY, ENTER, CHAT, COMMAND_USE,
     *   MANAGE_MEMBERS, MANAGE_PERMS, MANAGE_FLAGS, MANAGE_GROUPS
     */
    public RoleDefaults roleDefaults = new RoleDefaults();

    public static class RoleDefaults {
        public List<String> admin = new ArrayList<>(Arrays.asList(
            "BUILD", "INTERACT", "CONTAINERS", "PVP",
            "MANAGE_MEMBERS", "MANAGE_PERMS", "TP", "ENTER"));
        public List<String> member = new ArrayList<>(Arrays.asList(
            "BUILD", "INTERACT", "CONTAINERS", "TP", "ENTER"));
        public List<String> visitor = new ArrayList<>(Arrays.asList(
            "INTERACT", "ENTER"));
    }

    // ── Default flags for new plots ───────────────────────────────────────────

    /**
     * Global flags enabled when a new plot is created.
     * Valid flags: ALLOW_VISITOR_BUILD, ALLOW_VISITOR_INTERACT,
     *   ALLOW_VISITOR_CONTAINERS, ALLOW_PVP, ALLOW_FLY, ALLOW_TP, GREETINGS
     */
    public List<String> defaultFlags = new ArrayList<>(Arrays.asList("ALLOW_TP", "GREETINGS"));

    // ── Load / Save ───────────────────────────────────────────────────────────

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (Reader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, SecurePlotsConfig.class);
                applyBackwardsCompat(INSTANCE);
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

    /** Fills in missing fields when loading an older config file. */
    private static void applyBackwardsCompat(SecurePlotsConfig c) {
        if (c.tiers == null || c.tiers.isEmpty())
            c.tiers = createDefaultTiers();
        if (c.roleDefaults == null)
            c.roleDefaults = new RoleDefaults();
        if (c.defaultFlags == null || c.defaultFlags.isEmpty())
            c.defaultFlags = new ArrayList<>(Arrays.asList("ALLOW_TP", "GREETINGS"));
        if (c.blockedStructurePrefixes == null)
            c.blockedStructurePrefixes = new ArrayList<>(Arrays.asList("legendarymonuments:"));
        if (c.adminTag == null || c.adminTag.isEmpty())
            c.adminTag = "plot_admin";
        if (c.particleCount     <= 0 || c.particleCount > 5)  c.particleCount = 3;
        if (c.ambientParticleCount <= 0 || c.ambientParticleCount > 5) c.ambientParticleCount = 2;
        if (c.musicVolume       <= 0f || c.musicVolume > 4f)  c.musicVolume = 4.0f;
        if (c.checkInterval     <= 0) c.checkInterval  = 10;
        if (c.ambientInterval   <= 0) c.ambientInterval = 20;
        if (c.craftingRecipes == null || c.craftingRecipes.isEmpty())
            c.craftingRecipes = createDefault().craftingRecipes;
    }

    // ── Default config ────────────────────────────────────────────────────────

    private static SecurePlotsConfig createDefault() {
        SecurePlotsConfig cfg = new SecurePlotsConfig();
        cfg.tiers = createDefaultTiers();

        // Bronze (0) → Gold (1)
        UpgradeCost u1 = new UpgradeCost();
        u1.fromTier = 0; u1.toTier = 1;
        u1.items.add(new UpgradeCost.ItemCost("minecraft:gold_block", 15));
        cfg.upgradeCosts.add(u1);

        // Gold (1) → Emerald (2)
        UpgradeCost u2 = new UpgradeCost();
        u2.fromTier = 1; u2.toTier = 2;
        u2.items.add(new UpgradeCost.ItemCost("minecraft:emerald_block", 10));
        cfg.upgradeCosts.add(u2);

        // Emerald (2) → Diamond (3)
        UpgradeCost u3 = new UpgradeCost();
        u3.fromTier = 2; u3.toTier = 3;
        u3.items.add(new UpgradeCost.ItemCost("minecraft:diamond", 64));
        cfg.upgradeCosts.add(u3);

        // Diamond (3) → Netherite (4)
        UpgradeCost u4 = new UpgradeCost();
        u4.fromTier = 3; u4.toTier = 4;
        u4.items.add(new UpgradeCost.ItemCost("minecraft:netherite_block", 1));
        cfg.upgradeCosts.add(u4);

        // Crafting recipes
        Map<String,String> bronzeKey = new LinkedHashMap<>();
        bronzeKey.put("C", "minecraft:copper_block");
        bronzeKey.put("B", "minecraft:redstone_block");
        bronzeKey.put("H", "minecraft:heart_of_the_sea");
        cfg.craftingRecipes.add(new CraftingRecipe(
            "secure-plots:bronze_plot_block",
            new String[]{"CBC", "BHB", "CBC"},
            bronzeKey));

        Map<String,String> blueprintKey = new LinkedHashMap<>();
        blueprintKey.put("S", "minecraft:amethyst_shard");
        blueprintKey.put("P", "minecraft:paper");
        blueprintKey.put("C", "minecraft:compass");
        cfg.craftingRecipes.add(new CraftingRecipe(
            "secure-plots:plot_blueprint",
            new String[]{"SPS", "PCP", "SPS"},
            blueprintKey));

        return cfg;
    }

    private static List<TierConfig> createDefaultTiers() {
        return new ArrayList<>(Arrays.asList(
            new TierConfig(0, "Bronze",    15,  4, 50f, 1200f),
            new TierConfig(1, "Gold",      30,  5, 50f, 1200f),
            new TierConfig(2, "Emerald",   50,  6, 50f, 1200f),
            new TierConfig(3, "Diamond",   75,  7, 50f, 1200f),
            new TierConfig(4, "Netherite", 100, 8, 50f, 1200f)
        ));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns the upgrade cost from a specific tier, or null if not configured. */
    public UpgradeCost getUpgradeCost(int fromTier) {
        for (UpgradeCost cost : upgradeCosts)
            if (cost.fromTier == fromTier) return cost;
        return null;
    }

    /** Returns the config for a tier, falling back to safe values if not found. */
    public TierConfig getTierConfig(int tier) {
        for (TierConfig t : tiers)
            if (t.tier == tier) return t;
        return new TierConfig(tier, "Tier " + tier, 15 + tier * 10, 4 + tier, 50f, 1200f);
    }
}
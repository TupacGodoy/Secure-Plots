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
import net.minecraft.server.network.ServerPlayerEntity;

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

    /** Number of particles in the entry burst (1–5). */
    public int particleCount = 3;

    /** Continuous ambient particles spawned while inside a plot (1–5). */
    public int ambientParticleCount = 2;

    /** How often (in ticks) to check if a player entered or left a plot. */
    public int checkInterval = 10;

    /** How often (in ticks) to spawn continuous ambient particles inside a plot. */
    public int ambientInterval = 20;

    /** Play ambient music when entering a plot. */
    public boolean enablePlotMusic = true;

    /** Plot music volume (0.1 – 4.0). */
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

    /**
     * Default maximum plots per player (0 = unlimited).
     * Can be overridden per rank via rankPerks.
     */
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

    // ── Rank-based perks ──────────────────────────────────────────────────────

    /**
     * Per-rank plot feature configuration.
     *
     * Each entry maps a command tag (assigned with /tag <player> add <tag>)
     * to a set of plot feature permissions and limits.
     *
     * If a player has multiple rank tags, the BEST value of each field wins
     * (highest maxPlots, most permissive booleans).
     *
     * If a player has no rank tags, the global defaults apply.
     * The adminTag always bypasses all rank restrictions.
     *
     * Example — add three ranks:
     *   /tag Steve add vip
     *   /tag Alex add mvp
     *
     * Field descriptions:
     *   tag                — the /tag value to match
     *   maxPlots           — max plots this rank can own (0 = use global default)
     *   maxTier            — highest plot tier this rank can place (0–4)
     *   canRename          — can rename their plot
     *   canSetMusic        — can set plot music
     *   canSetParticles    — can set plot particles
     *   canSetWeather      — can set plot weather override
     *   canSetTime         — can set plot time override
     *   canSetEnterExit    — can set enter/exit messages
     *   canTp              — can use /sp tp
     *   canFly             — can enable fly in their plot
     *   canUpgrade         — can upgrade their plot tier
     *   canGroups          — can create permission groups
     *   hasRankProtection  — plot is immune to inactivity expiry
     */
    public List<RankPerks> rankPerks = new ArrayList<>();

    public static class RankPerks {
        /** The command tag assigned with /tag <player> add <this value>. */
        public String  tag                = "";

        /** Maximum plots this rank can own. 0 = use global maxPlotsPerPlayer. */
        public int     maxPlots           = 0;

        /** Highest plot tier (0–4) this rank can place. Default: 4 (all tiers). */
        public int     maxTier            = 4;

        /** Can rename their plot. */
        public boolean canRename          = true;

        /** Can set ambient music on their plot. */
        public boolean canSetMusic        = true;

        /** Can set ambient particles on their plot. */
        public boolean canSetParticles    = true;

        /** Can set a weather override on their plot. */
        public boolean canSetWeather      = true;

        /** Can set a time override on their plot. */
        public boolean canSetTime         = true;

        /** Can set enter/exit messages on their plot. */
        public boolean canSetEnterExit    = true;

        /** Can use /sp tp to teleport to their own plot. */
        public boolean canTp              = true;

        /** Can enable the fly flag on their plot. */
        public boolean canFly             = true;

        /** Can upgrade their plot to a higher tier. */
        public boolean canUpgrade         = true;

        /** Can create and manage permission groups. */
        public boolean canGroups          = true;

        /** Plot is immune to inactivity expiry. */
        public boolean hasRankProtection  = false;

        public RankPerks() {}

        public RankPerks(String tag) { this.tag = tag; }
    }

    // ── Rank resolution ───────────────────────────────────────────────────────

    /**
     * Resolved perks for a player, merging all their rank tags.
     * Best-value wins: highest maxPlots, most permissive booleans.
     */
    public static class ResolvedPerks {
        public int     maxPlots          = 0;   // 0 = use global default
        public int     maxTier           = 4;
        public boolean canRename         = true;
        public boolean canSetMusic       = true;
        public boolean canSetParticles   = true;
        public boolean canSetWeather     = true;
        public boolean canSetTime        = true;
        public boolean canSetEnterExit   = true;
        public boolean canTp             = true;
        public boolean canFly            = true;
        public boolean canUpgrade        = true;
        public boolean canGroups         = true;
        public boolean hasRankProtection = false;
        public boolean hasAnyRank        = false;
    }

    /**
     * Resolves the effective perks for a player by merging all matching rank tags.
     * Admin players get full perks regardless.
     *
     * @param player the server player to resolve perks for
     * @return resolved perks (never null)
     */
    public ResolvedPerks resolvePerks(ServerPlayerEntity player) {
        SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
        ResolvedPerks result = new ResolvedPerks();

        // Admin bypasses everything
        if (player.getCommandTags().contains(adminTag)
                || player.hasPermissionLevel(adminOpLevel)) {
            result.maxPlots          = 0; // unlimited
            result.maxTier           = 4;
            result.canRename         = true;
            result.canSetMusic       = true;
            result.canSetParticles   = true;
            result.canSetWeather     = true;
            result.canSetTime        = true;
            result.canSetEnterExit   = true;
            result.canTp             = true;
            result.canFly            = true;
            result.canUpgrade        = true;
            result.canGroups         = true;
            result.hasRankProtection = true;
            result.hasAnyRank        = true;
            return result;
        }

        if (cfg == null || cfg.rankPerks == null || cfg.rankPerks.isEmpty())
            return result; // use defaults

        Set<String> playerTags = player.getCommandTags();

        for (RankPerks rank : cfg.rankPerks) {
            if (rank.tag == null || !playerTags.contains(rank.tag)) continue;
            result.hasAnyRank = true;

            // Best-value merge: take the most permissive value of each field
            if (rank.maxPlots > result.maxPlots)    result.maxPlots  = rank.maxPlots;
            if (rank.maxTier  > result.maxTier)     result.maxTier   = rank.maxTier;
            result.canRename        |= rank.canRename;
            result.canSetMusic      |= rank.canSetMusic;
            result.canSetParticles  |= rank.canSetParticles;
            result.canSetWeather    |= rank.canSetWeather;
            result.canSetTime       |= rank.canSetTime;
            result.canSetEnterExit  |= rank.canSetEnterExit;
            result.canTp            |= rank.canTp;
            result.canFly           |= rank.canFly;
            result.canUpgrade       |= rank.canUpgrade;
            result.canGroups        |= rank.canGroups;
            result.hasRankProtection|= rank.hasRankProtection;
        }

        if (!result.hasAnyRank) {
            // No rank tags — apply global feature toggles as defaults
            result.canRename        = true;
            result.canSetMusic      = cfg.enablePlotMusic;
            result.canSetParticles  = cfg.enablePlotParticles;
            result.canSetWeather    = cfg.enablePlotWeather;
            result.canSetTime       = cfg.enablePlotTime;
            result.canTp            = cfg.enablePlotTeleport;
            result.canFly           = cfg.enableFlyInPlots;
            result.canUpgrade       = cfg.enableUpgrades;
            result.canGroups        = cfg.enablePermissionGroups;
        }

        return result;
    }

    /**
     * Returns the effective max plots for a player,
     * considering their rank perks and the global default.
     */
    public int getMaxPlotsFor(ServerPlayerEntity player) {
        ResolvedPerks perks = resolvePerks(player);
        if (perks.maxPlots > 0) return perks.maxPlots;
        return maxPlotsPerPlayer; // global default (0 = unlimited)
    }

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
     */
    public List<TierConfig> tiers = new ArrayList<>();

    public static class TierConfig {
        public int    tier;
        public String displayName;
        public int    radius;
        public int    luminance;
        public float  hardness;
        public float  blastResistance;

        public TierConfig() {}
        public TierConfig(int tier, String displayName, int radius, int luminance,
                          float hardness, float blastResistance) {
            this.tier = tier; this.displayName = displayName; this.radius = radius;
            this.luminance = luminance; this.hardness = hardness; this.blastResistance = blastResistance;
        }
    }

    // ── Upgrade costs ─────────────────────────────────────────────────────────

    public List<UpgradeCost> upgradeCosts = new ArrayList<>();

    public static class UpgradeCost {
        public int fromTier;
        public int toTier;
        public List<ItemCost> items = new ArrayList<>();

        public static class ItemCost {
            public String itemId;
            public int    amount;
            public ItemCost() {}
            public ItemCost(String itemId, int amount) { this.itemId = itemId; this.amount = amount; }
        }
    }

    // ── Crafting recipes ──────────────────────────────────────────────────────

    public List<CraftingRecipe> craftingRecipes = new ArrayList<>();

    public static class CraftingRecipe {
        public String result;
        public String[] pattern;
        public Map<String, String> key = new LinkedHashMap<>();
        public boolean disabled = false;
        public CraftingRecipe() {}
        public CraftingRecipe(String result, String[] pattern, Map<String, String> key) {
            this.result = result; this.pattern = pattern; this.key = key;
        }
    }

    // ── Default permissions by role ───────────────────────────────────────────

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

    private static void applyBackwardsCompat(SecurePlotsConfig c) {
        if (c.tiers == null || c.tiers.isEmpty())                  c.tiers = createDefaultTiers();
        if (c.roleDefaults == null)                                 c.roleDefaults = new RoleDefaults();
        if (c.defaultFlags == null || c.defaultFlags.isEmpty())     c.defaultFlags = new ArrayList<>(Arrays.asList("ALLOW_TP", "GREETINGS"));
        if (c.blockedStructurePrefixes == null)                     c.blockedStructurePrefixes = new ArrayList<>(Arrays.asList("legendarymonuments:"));
        if (c.adminTag == null || c.adminTag.isEmpty())             c.adminTag = "plot_admin";
        if (c.rankPerks == null)                                    c.rankPerks = new ArrayList<>();
        if (c.particleCount <= 0 || c.particleCount > 5)           c.particleCount = 3;
        if (c.ambientParticleCount <= 0 || c.ambientParticleCount > 5) c.ambientParticleCount = 2;
        if (c.musicVolume <= 0f || c.musicVolume > 4f)             c.musicVolume = 4.0f;
        if (c.checkInterval <= 0)                                   c.checkInterval = 10;
        if (c.ambientInterval <= 0)                                 c.ambientInterval = 20;
        if (c.craftingRecipes == null || c.craftingRecipes.isEmpty()) c.craftingRecipes = createDefault().craftingRecipes;
    }

    // ── Default config ────────────────────────────────────────────────────────

    private static SecurePlotsConfig createDefault() {
        SecurePlotsConfig cfg = new SecurePlotsConfig();
        cfg.tiers = createDefaultTiers();

        // Example rank: VIP gets 5 plots, all features
        RankPerks vip = new RankPerks("vip");
        vip.maxPlots = 5; vip.maxTier = 4;
        cfg.rankPerks.add(vip);

        // Example rank: MVP gets 10 plots, all features + rank protection
        RankPerks mvp = new RankPerks("mvp");
        mvp.maxPlots = 10; mvp.maxTier = 4; mvp.hasRankProtection = true;
        cfg.rankPerks.add(mvp);

        // Example rank: basic gets 2 plots, limited features
        RankPerks basic = new RankPerks("basic");
        basic.maxPlots = 2; basic.maxTier = 1;
        basic.canSetMusic = false; basic.canSetParticles = false;
        basic.canSetWeather = false; basic.canSetTime = false;
        basic.canFly = false; basic.canUpgrade = false; basic.canGroups = false;
        cfg.rankPerks.add(basic);

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

        Map<String,String> bronzeKey = new LinkedHashMap<>();
        bronzeKey.put("C", "minecraft:copper_block");
        bronzeKey.put("B", "minecraft:redstone_block");
        bronzeKey.put("H", "minecraft:heart_of_the_sea");
        cfg.craftingRecipes.add(new CraftingRecipe("secure-plots:bronze_plot_block",
            new String[]{"CBC","BHB","CBC"}, bronzeKey));

        Map<String,String> blueprintKey = new LinkedHashMap<>();
        blueprintKey.put("S", "minecraft:amethyst_shard");
        blueprintKey.put("P", "minecraft:paper");
        blueprintKey.put("C", "minecraft:compass");
        cfg.craftingRecipes.add(new CraftingRecipe("secure-plots:plot_blueprint",
            new String[]{"SPS","PCP","SPS"}, blueprintKey));

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

    public UpgradeCost getUpgradeCost(int fromTier) {
        for (UpgradeCost cost : upgradeCosts) if (cost.fromTier == fromTier) return cost;
        return null;
    }

    public TierConfig getTierConfig(int tier) {
        for (TierConfig t : tiers) if (t.tier == tier) return t;
        return new TierConfig(tier, "Tier " + tier, 15 + tier * 10, 4 + tier, 50f, 1200f);
    }
}
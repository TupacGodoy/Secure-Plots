/*
 * SecurePlots - A Fabric mod for Minecraft 1.21.1
 * Copyright (C) 2025 TupacGodoy
 * GPL-3.0-only
 */
package com.zhilius.secureplots.recipe;

import com.zhilius.secureplots.SecurePlots;
import com.zhilius.secureplots.config.SecurePlotsConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;

import java.util.*;

/**
 * Registers configurable crafting recipes from secure_plots.json.
 * Uses SERVER_STARTING so recipes are available before any player joins.
 * Injects into RecipeManager via reflection — no access widener needed.
 */
public class DynamicRecipeManager {

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            SecurePlotsConfig cfg = SecurePlotsConfig.INSTANCE;
            if (cfg == null || cfg.craftingRecipes == null || cfg.craftingRecipes.isEmpty()) return;

            net.minecraft.recipe.RecipeManager rm = server.getRecipeManager();

            // Snapshot current recipes into a mutable map (Identifier → RecipeEntry)
            Map<Identifier, RecipeEntry<?>> all = new HashMap<>();
            for (RecipeEntry<?> entry : rm.values()) {
                all.put(entry.id(), entry);
            }

            int added = 0;
            for (SecurePlotsConfig.CraftingRecipe cfgR : cfg.craftingRecipes) {
                if (cfgR.disabled) continue;
                if (cfgR.result == null || cfgR.pattern == null || cfgR.key == null) {
                    SecurePlots.LOGGER.warn("[SecurePlots] Skipping recipe with null fields");
                    continue;
                }
                try {
                    RecipeEntry<ShapedRecipe> entry = buildShaped(cfgR);
                    if (entry != null) {
                        all.put(entry.id(), entry);
                        added++;
                    }
                } catch (Exception e) {
                    SecurePlots.LOGGER.warn("[SecurePlots] Failed to build recipe '{}': {}",
                        cfgR.result, e.getMessage());
                }
            }

            if (added > 0) {
                injectRecipes(rm, all);
                SecurePlots.LOGGER.info("[SecurePlots] Loaded {} configurable crafting recipe(s).", added);
            }
        });
    }

    /**
     * Injects the recipe map into RecipeManager via reflection.
     * Searches for the first Map field in RecipeManager — works with
     * Yarn 1.21.1+build.3 without hardcoding the field name.
     */
    @SuppressWarnings("unchecked")
    private static void injectRecipes(net.minecraft.recipe.RecipeManager rm,
                                      Map<Identifier, RecipeEntry<?>> recipes) {
        try {
            java.lang.reflect.Field target = null;
            for (java.lang.reflect.Field f : net.minecraft.recipe.RecipeManager.class.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(f.getType())) {
                    target = f;
                    break;
                }
            }
            if (target == null) {
                SecurePlots.LOGGER.warn("[SecurePlots] Could not find recipe map field in RecipeManager");
                return;
            }
            target.setAccessible(true);
            Object existing = target.get(rm);
            if (existing instanceof Map<?,?> map) {
                ((Map<Identifier, RecipeEntry<?>>) map).clear();
                ((Map<Identifier, RecipeEntry<?>>) map).putAll(recipes);
            }
        } catch (Exception e) {
            SecurePlots.LOGGER.error("[SecurePlots] Failed to inject recipes: {}", e.getMessage());
        }
    }

    private static RecipeEntry<ShapedRecipe> buildShaped(SecurePlotsConfig.CraftingRecipe cfg) {
        // Resolve result item
        Identifier resultId = Identifier.tryParse(cfg.result);
        if (resultId == null) {
            SecurePlots.LOGGER.warn("[SecurePlots] Invalid result ID: '{}'", cfg.result);
            return null;
        }
        Item resultItem = Registries.ITEM.get(resultId);
        if (resultItem == null || resultItem == Items.AIR) {
            SecurePlots.LOGGER.warn("[SecurePlots] Result item not found: '{}'", cfg.result);
            return null;
        }

        // Resolve key map char → Ingredient
        Map<Character, Ingredient> ingredients = new HashMap<>();
        for (Map.Entry<String, String> e : cfg.key.entrySet()) {
            if (e.getKey() == null || e.getKey().isEmpty()) continue;
            char ch = e.getKey().charAt(0);
            Identifier itemId = Identifier.tryParse(e.getValue());
            if (itemId == null) {
                SecurePlots.LOGGER.warn("[SecurePlots] Invalid key '{}' in recipe '{}'",
                    e.getValue(), cfg.result);
                return null;
            }
            Item item = Registries.ITEM.get(itemId);
            if (item == null || item == Items.AIR) {
                SecurePlots.LOGGER.warn("[SecurePlots] Key item not found: '{}' in recipe '{}'",
                    e.getValue(), cfg.result);
                return null;
            }
            ingredients.put(ch, Ingredient.ofItems(item));
        }

        // Validate pattern
        if (cfg.pattern.length == 0 || cfg.pattern.length > 3) {
            SecurePlots.LOGGER.warn("[SecurePlots] Pattern must have 1-3 rows in recipe '{}'", cfg.result);
            return null;
        }
        int width  = cfg.pattern[0].length();
        int height = cfg.pattern.length;
        if (width == 0 || width > 3) {
            SecurePlots.LOGGER.warn("[SecurePlots] Pattern rows must be 1-3 chars wide in recipe '{}'", cfg.result);
            return null;
        }

        // Flatten pattern into ingredient list
        DefaultedList<Ingredient> flat = DefaultedList.ofSize(width * height, Ingredient.EMPTY);
        for (int row = 0; row < height; row++) {
            String line = cfg.pattern[row];
            for (int col = 0; col < Math.min(line.length(), width); col++) {
                char ch = line.charAt(col);
                if (ch == ' ') continue;
                Ingredient ing = ingredients.get(ch);
                if (ing == null) {
                    SecurePlots.LOGGER.warn("[SecurePlots] Char '{}' has no key in recipe '{}'",
                        ch, cfg.result);
                    return null;
                }
                flat.set(row * width + col, ing);
            }
        }

        // Yarn 1.21.1+build.3 ShapedRecipe constructor:
        // ShapedRecipe(String group, CraftingRecipeCategory category, RawShapedRecipe raw,
        //              ItemStack result, boolean showNotification)
        RawShapedRecipe raw = new RawShapedRecipe(width, height, flat, Optional.empty());
        ShapedRecipe shaped = new ShapedRecipe(
            "",
            net.minecraft.recipe.book.CraftingRecipeCategory.MISC,
            raw,
            new ItemStack(resultItem),
            false
        );

        // Yarn 1.21.1: RecipeEntry<R>(Identifier id, R recipe)
        Identifier recipeId = Identifier.of(SecurePlots.MOD_ID, resultId.getPath());
        return new RecipeEntry<>(recipeId, shaped);
    }
}

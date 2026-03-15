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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Visual configuration for the plot border renderer.
 * Stored in secure_plots_client.json on the server and synced to all clients on join.
 */
public class BorderConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File(
        FabricLoader.getInstance().getConfigDir().toFile(), "secure_plots_client.json");

    public static BorderConfig INSTANCE;

    // ── Border thickness ──────────────────────────────────────────────────────

    /** Core edge thickness. */
    public float edgeThickness = 0.06f;

    /** Glow halo thickness. */
    public float glowThickness = 0.13f;

    /** Scanline thickness. */
    public float scanlineThickness = 0.025f;

    /** Spacing between scanlines in blocks. */
    public float scanlineSpacing = 1.5f;

    // ── Animation ─────────────────────────────────────────────────────────────

    /** Border height above the plot block in blocks. */
    public float borderHeight = 25f;

    /** Pulse cycle duration in milliseconds (lower = faster pulse). */
    public int pulseCycleMs = 2000;

    /** Minimum pulse brightness (0.0 – 1.0). */
    public float pulseMin = 0.6f;

    /** Pulse variation range (0.0 – 1.0). pulseMin + pulseRange should be <= 1.0. */
    public float pulseRange = 0.4f;

    /** Lightning bolt flicker interval in milliseconds. */
    public int boltFlickerMs = 120;

    // ── Hologram ──────────────────────────────────────────────────────────────

    /** Whether to show the floating hologram above plot blocks. Synced from SecurePlotsConfig.enableHologram. */
    public boolean hologramEnabled = true;

    /** Hologram height above the plot block in blocks. */
    public float hologramHeight = 3.0f;

    /** Maximum distance in blocks to render the hologram. */
    public float hologramMaxDistance = 24f;

    /** Text scale of the hologram. Higher = larger. */
    public float hologramScale = 0.025f;

    /** Background opacity (0.0 = fully transparent, 1.0 = fully opaque). */
    public float hologramBackgroundOpacity = 0.75f;

    /** Horizontal padding of the panel in font pixels. */
    public int hologramPaddingX = 8;

    /** Vertical padding of the panel in font pixels. */
    public int hologramPaddingY = 6;

    /** Line spacing in font pixels. */
    public int hologramLineSpacing = 1;

    /** Duration of the fade-in animation in milliseconds. */
    public int hologramFadeInMs = 400;

    /** Duration of the fade-out animation in milliseconds (before expiry). */
    public int hologramFadeOutMs = 600;

    /** Whether the hologram floats up and down. */
    public boolean hologramFloat = true;

    /** Float amplitude in blocks (how far it moves up and down). */
    public float hologramFloatAmplitude = 0.1f;

    /** Float cycle duration in milliseconds. */
    public int hologramFloatCycleMs = 3000;

    // ── Plot screen UI ────────────────────────────────────────────────────────

    /** Width of the plot management screen panel in pixels. */
    public int screenPanelWidth = 320;

    /** Height of the plot management screen panel in pixels. */
    public int screenPanelHeight = 240;

    /** Row spacing between info lines in pixels. */
    public int screenRowSpacing = 16;

    /** Maximum length of a plot name. */
    public int screenMaxNameLength = 32;

    // Panel colors (ARGB hex)
    public int screenColorBorderOuter  = 0xFF373737;
    public int screenColorBackground   = 0xFFC6C6C6;
    public int screenColorTitleBar     = 0xFF555555;
    public int screenColorTitleBarTop  = 0xFF666666;
    public int screenColorShadowDark   = 0xFF8B8B8B;
    public int screenColorShadowLight  = 0xFFFFFFFF;

    // ── Hologram text labels ──────────────────────────────────────────────────

    /** Label shown when the plot has no name. */
    public String hologramDefaultName = "PROTECTED PLOT";

    /** Label for the owner field. */
    public String hologramLabelOwner = "Owner:   ";

    /** Label for the tier field. */
    public String hologramLabelTier = "Tier:    ";

    /** Label for the size field. */
    public String hologramLabelSize = "Size:    ";

    /** Label for the members field. */
    public String hologramLabelMembers = "Members: ";

    /** Label for the next tier field. */
    public String hologramLabelNext = "Next: ";

    /** Label shown when the plot is at max level. */
    public String hologramLabelMaxLevel = "\u00a76\u00a7l\u2605 Max Level \u2605";

    // ── Tier colors ───────────────────────────────────────────────────────────

    /**
     * Colors for each tier (0=Bronze … 4=Netherite).
     * Each entry has 9 float values in [0.0, 1.0]:
     * r_core, g_core, b_core, r_glow, g_glow, b_glow, r_white, g_white, b_white
     */
    public List<TierColors> tierColors = new ArrayList<>();

    public static class TierColors {
        public int   tier;
        public float coreR,  coreG,  coreB;
        public float glowR,  glowG,  glowB;
        public float whiteR, whiteG, whiteB;

        public TierColors() {}

        public TierColors(int tier,
                          float coreR,  float coreG,  float coreB,
                          float glowR,  float glowG,  float glowB,
                          float whiteR, float whiteG, float whiteB) {
            this.tier   = tier;
            this.coreR  = coreR;  this.coreG  = coreG;  this.coreB  = coreB;
            this.glowR  = glowR;  this.glowG  = glowG;  this.glowB  = glowB;
            this.whiteR = whiteR; this.whiteG = whiteG; this.whiteB = whiteB;
        }

        public float[] toArray() {
            return new float[]{ coreR, coreG, coreB, glowR, glowG, glowB, whiteR, whiteG, whiteB };
        }
    }

    // ── Load / Save ───────────────────────────────────────────────────────────

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (Reader reader = new FileReader(CONFIG_FILE)) {
                INSTANCE = GSON.fromJson(reader, BorderConfig.class);
                if (INSTANCE == null) INSTANCE = createDefault();
                INSTANCE.applyDefaults();
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

    // ── Defaults ──────────────────────────────────────────────────────────────

    public static BorderConfig createDefault() {
        BorderConfig c = new BorderConfig();
        c.tierColors = createDefaultTierColors();
        return c;
    }

    public static List<TierColors> createDefaultTierColors() {
        return new ArrayList<>(Arrays.asList(
            new TierColors(0, 1.00f,0.55f,0.05f, 0.70f,0.28f,0.00f, 1.00f,0.88f,0.55f), // Bronze
            new TierColors(1, 1.00f,0.85f,0.00f, 0.80f,0.50f,0.00f, 1.00f,0.97f,0.65f), // Gold
            new TierColors(2, 0.10f,0.90f,0.20f, 0.00f,0.55f,0.10f, 0.70f,1.00f,0.75f), // Emerald
            new TierColors(3, 0.15f,0.95f,1.00f, 0.00f,0.50f,0.80f, 0.75f,1.00f,1.00f), // Diamond
            new TierColors(4, 0.45f,0.20f,0.60f, 0.22f,0.05f,0.32f, 0.78f,0.58f,0.90f)  // Netherite
        ));
    }

    /** Applies safe default values for any field that is invalid or out of range. */
    public void applyDefaults() {
        if (edgeThickness      <= 0)           edgeThickness      = 0.06f;
        if (glowThickness      <= 0)           glowThickness      = 0.13f;
        if (scanlineThickness  <= 0)           scanlineThickness  = 0.025f;
        if (scanlineSpacing    <= 0)           scanlineSpacing    = 1.5f;
        if (borderHeight       <= 0)           borderHeight       = 25f;
        if (pulseCycleMs       <= 0)           pulseCycleMs       = 2000;
        if (pulseMin    < 0 || pulseMin > 1)   pulseMin           = 0.6f;
        if (pulseRange  < 0 || pulseRange > 1) pulseRange         = 0.4f;
        if (boltFlickerMs      <= 0)           boltFlickerMs      = 120;
        if (hologramHeight     <= 0)           hologramHeight     = 3.0f;
        if (hologramMaxDistance<= 0)           hologramMaxDistance= 24f;
        if (hologramScale      <= 0)           hologramScale      = 0.025f;
        if (hologramBackgroundOpacity < 0 || hologramBackgroundOpacity > 1)
                                               hologramBackgroundOpacity = 0.75f;
        if (hologramPaddingX   <= 0)           hologramPaddingX   = 8;
        if (hologramPaddingY   <= 0)           hologramPaddingY   = 6;
        if (hologramLineSpacing < 0)           hologramLineSpacing= 1;
        if (hologramFadeInMs   <= 0)           hologramFadeInMs   = 400;
        if (hologramFadeOutMs  <= 0)           hologramFadeOutMs  = 600;
        if (hologramFloatAmplitude < 0)        hologramFloatAmplitude = 0.1f;
        if (hologramFloatCycleMs   <= 0)       hologramFloatCycleMs   = 3000;
        if (screenPanelWidth   <= 0)           screenPanelWidth   = 320;
        if (screenPanelHeight  <= 0)           screenPanelHeight  = 240;
        if (screenRowSpacing   <= 0)           screenRowSpacing   = 16;
        if (screenMaxNameLength<= 0)           screenMaxNameLength= 32;
        if (hologramDefaultName == null || hologramDefaultName.isBlank())
                                               hologramDefaultName = "PROTECTED PLOT";
        if (hologramLabelOwner   == null) hologramLabelOwner   = "Owner:   ";
        if (hologramLabelTier    == null) hologramLabelTier    = "Tier:    ";
        if (hologramLabelSize    == null) hologramLabelSize    = "Size:    ";
        if (hologramLabelMembers == null) hologramLabelMembers = "Members: ";
        if (hologramLabelNext    == null) hologramLabelNext    = "Next: ";
        if (hologramLabelMaxLevel== null) hologramLabelMaxLevel= "\u00a76\u00a7l\u2605 Max Level \u2605";
        if (tierColors == null || tierColors.isEmpty())
                                               tierColors = createDefaultTierColors();
    }

    public float[] getTierColors(int tier) {
        for (TierColors tc : tierColors)
            if (tc.tier == tier) return tc.toArray();
        float[][] defaults = {
            { 1.00f,0.55f,0.05f, 0.70f,0.28f,0.00f, 1.00f,0.88f,0.55f },
            { 1.00f,0.85f,0.00f, 0.80f,0.50f,0.00f, 1.00f,0.97f,0.65f },
            { 0.10f,0.90f,0.20f, 0.00f,0.55f,0.10f, 0.70f,1.00f,0.75f },
            { 0.15f,0.95f,1.00f, 0.00f,0.50f,0.80f, 0.75f,1.00f,1.00f },
            { 0.45f,0.20f,0.60f, 0.22f,0.05f,0.32f, 0.78f,0.58f,0.90f },
        };
        return defaults[Math.max(0, Math.min(tier, defaults.length - 1))];
    }
}
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
package com.zhilius.secureplots.plot;

import com.zhilius.secureplots.config.SecurePlotsConfig;

public enum PlotSize {
    BRONZE  (0, "bronze_plot_block"),
    GOLD    (1, "gold_plot_block"),
    EMERALD (2, "emerald_plot_block"),
    DIAMOND (3, "diamond_plot_block"),
    NETHERITE(4, "netherite_plot_block");

    public final int tier;
    public final String blockId;

    PlotSize(int tier, String blockId) {
        this.tier    = tier;
        this.blockId = blockId;
    }

    /** Radio en bloques (leído del config; tiene fallback si el config no está cargado). */
    public int getRadius() {
        if (SecurePlotsConfig.INSTANCE != null) {
            return SecurePlotsConfig.INSTANCE.getTierConfig(tier).radius;
        }
        // Fallback durante el arranque antes de cargar el config
        return switch (tier) { case 0 -> 15; case 1 -> 30; case 2 -> 50; case 3 -> 75; default -> 100; };
    }

    /** Nombre visible (leído del config). */
    public String getDisplayName() {
        if (SecurePlotsConfig.INSTANCE != null) {
            return SecurePlotsConfig.INSTANCE.getTierConfig(tier).displayName;
        }
        return switch (tier) {
            case 0 -> "Bronce"; case 1 -> "Oro"; case 2 -> "Esmeralda";
            case 3 -> "Diamante"; default -> "Netherita";
        };
    }

    /**
     * Compatibilidad: devuelve el radio directamente.
     * Usar getRadius() es preferible, pero este campo público se mantiene
     * para que el código existente que accede a .radius no rompa.
     */
    public int radius() { return getRadius(); }

    /** @deprecated Usar getDisplayName() */
    @Deprecated
    public String displayName() { return getDisplayName(); }

    public PlotSize next() {
        PlotSize[] values = values();
        if (this.tier + 1 < values.length) return values[this.tier + 1];
        return null;
    }

    public static PlotSize fromTier(int tier) {
        for (PlotSize size : values()) {
            if (size.tier == tier) return size;
        }
        return BRONZE;
    }
}

package com.zhilius.secureplots.plot;

import com.zhilius.secureplots.config.SecurePlotsConfig;

public enum PlotSize {
    // Tier 0 - Bronze  : 15x15  por defecto
    BRONZE(15, "Bronce", 0, "bronze_plot_block"),
    // Tier 1 - Gold    : 30x30  por defecto
    GOLD(30, "Oro", 1, "gold_plot_block"),
    // Tier 2 - Emerald : 50x50  por defecto
    EMERALD(50, "Esmeralda", 2, "emerald_plot_block"),
    // Tier 3 - Diamond : 75x75  por defecto
    DIAMOND(75, "Diamante", 3, "diamond_plot_block"),
    // Tier 4 - Netherite: 100x100 por defecto
    NETHERITE(100, "Netherita", 4, "netherite_plot_block");

    /** Tamaño por defecto (hardcoded). El tamaño real en juego se obtiene con {@link #getRadius()}. */
    public final int defaultRadius;
    public final String displayName;
    public final int tier;
    public final String blockId;

    PlotSize(int defaultRadius, String displayName, int tier, String blockId) {
        this.defaultRadius = defaultRadius;
        this.displayName = displayName;
        this.tier = tier;
        this.blockId = blockId;
    }

    /**
     * Devuelve el radio real del plot para este tier.
     * Si el administrador configuró un tamaño personalizado en secure_plots.json, se usa ese valor;
     * de lo contrario se usa el valor por defecto del enum.
     */
    public int getRadius() {
        if (SecurePlotsConfig.INSTANCE != null) {
            int configured = SecurePlotsConfig.INSTANCE.getPlotSize(this.tier);
            if (configured > 0) return configured;
        }
        return defaultRadius;
    }

    /**
     * @deprecated Usar {@link #getRadius()} para respetar la configuración del admin.
     * Este campo se mantiene por compatibilidad.
     */
    @Deprecated
    public int getRadiusLegacy() {
        return defaultRadius;
    }

    public PlotSize next() {
        PlotSize[] values = values();
        if (this.tier + 1 < values.length) {
            return values[this.tier + 1];
        }
        return null;
    }

    public static PlotSize fromTier(int tier) {
        for (PlotSize size : values()) {
            if (size.tier == tier) return size;
        }
        return BRONZE;
    }
}

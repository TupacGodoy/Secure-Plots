package com.zhilius.secureplots.plot;

public enum PlotSize {
    SMALL(15, "Pequeño", 0, "bronze_plot_block"),
    MEDIUM(30, "Mediano", 1, "iron_plot_block"),
    LARGE(50, "Grande", 2, "gold_plot_block"),
    XLARGE(70, "Extra Grande", 3, "diamond_plot_block"),
    HUGE(100, "Enorme", 4, "netherite_plot_block"),
    MASSIVE(200, "Masivo", 5, "quantum_plot_block");

    public final int radius;
    public final String displayName;
    public final int tier;
    public final String blockId;

    PlotSize(int radius, String displayName, int tier, String blockId) {
        this.radius = radius;
        this.displayName = displayName;
        this.tier = tier;
        this.blockId = blockId;
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
        return SMALL;
    }
}

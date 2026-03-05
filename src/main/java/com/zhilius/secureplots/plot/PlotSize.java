package com.zhilius.secureplots.plot;

public enum PlotSize {
    BRONZE(15,  "Bronce",    0, "bronze_plot_block"),
    GOLD  (30,  "Oro",       1, "gold_plot_block"),
    EMERALD(50, "Esmeralda", 2, "emerald_plot_block"),
    DIAMOND(75, "Diamante",  3, "diamond_plot_block"),
    NETHERITE(100, "Netherita", 4, "netherite_plot_block");

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
        return BRONZE;
    }
}

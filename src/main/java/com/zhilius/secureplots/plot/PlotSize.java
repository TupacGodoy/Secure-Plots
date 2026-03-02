package com.zhilius.secureplots.plot;

public enum PlotSize {
    SMALL(15, "Pequeño", 0),
    MEDIUM(30, "Mediano", 1),
    LARGE(50, "Grande", 2),
    XLARGE(70, "Extra Grande", 3),
    HUGE(100, "Enorme", 4),
    MASSIVE(200, "Masivo", 5);

    public final int radius;
    public final String displayName;
    public final int tier;

    PlotSize(int radius, String displayName, int tier) {
        this.radius = radius;
        this.displayName = displayName;
        this.tier = tier;
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

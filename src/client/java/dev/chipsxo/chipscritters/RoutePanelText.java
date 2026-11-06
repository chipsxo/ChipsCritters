package dev.chipsxo.chipscritters;

/** Shared text and measured fitting for the live panel and HUD editor preview. */
final class RoutePanelText {
    private RoutePanelText() { }

    static String modeName(ChipsConfig.Mode mode) {
        return switch (mode) {
            case MONEY -> "Money";
            case UNIQUE -> "Unique";
            case FULL_CLEAR -> "Full Clear";
        };
    }

    static String line(String biome, String counter, ChipsConfig.Mode mode, String status) {
        return biome + " · Stop " + counter + " · " + modeName(mode) + " · " + status;
    }

    // Keep all fields, the panel bounds and other rows intact; fit only this row.
    static float lineScale(int measuredWidth, int availableWidth) {
        return measuredWidth <= 0 ? 1f : Math.min(1f, Math.max(1, availableWidth) / (float) measuredWidth);
    }
}

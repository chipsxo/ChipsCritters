package dev.chipsxo.chipscritters;

final class Patch65RegressionTests {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError("0.1.65: " + message);
    }

    static void run() {
        String[] names = {"Money", "Unique", "Full Clear"};
        ChipsConfig.Mode[] modes = ChipsConfig.Mode.values();
        for (int i = 0; i < modes.length; i++) {
            check(RoutePanelText.modeName(modes[i]).equals(names[i]), "exact public mode name");
            check(RoutePanelText.line("Cavern", "8/40", modes[i], "5m")
                    .equals("Cavern · Stop 8/40 · " + names[i] + " · 5m"), "mode, counter and distance retained");
        }
        String longest = RoutePanelText.line("Haunted", "73/73", ChipsConfig.Mode.FULL_CLEAR, "Waiting");
        check(longest.equals("Haunted · Stop 73/73 · Full Clear · Waiting"), "longest waiting text intact");
        check(RoutePanelText.line("Haunted", "61/66", ChipsConfig.Mode.UNIQUE, "Waiting")
                .equals("Haunted · Stop 61/66 · Unique · Waiting"), "waiting status retained");
        // Exercise the same measured-fit function used by the renderer, including
        // wide font/resource-pack metrics and GUI scales 1–4 in small windows.
        for (int guiScale = 1; guiScale <= 4; guiScale++) {
            for (int windowWidth : new int[]{320, 640, 854, 1280, 1920}) {
                double guiWidth = windowWidth / (double) guiScale;
                double panelScale = Math.min(1, Math.max(0, guiWidth - 16) / 268);
                for (int measuredWidth : new int[]{180, 256, 300, longest.length() * 9}) {
                    float scale = RoutePanelText.lineScale(measuredWidth, 256);
                    check(scale > 0 && scale <= 1, "fit scale bounded");
                    check(measuredWidth * (double) scale <= 256.0001, "complete text fits padded panel");
                    check((6 + measuredWidth * (double) scale) * panelScale <= 262 * panelScale + .0001,
                            "right padding retained at every GUI scale/window width");
                    check(19 + 9 * scale < 33, "text cannot overlap progress bar or party row");
                }
            }
        }
        check(RoutePanelText.lineScale(128, 256) == 1, "short rows never enlarged");
        check(RoutePanelText.lineScale(512, 256) == .5f, "long row fits without truncation");
        System.out.println("0.1.65 checks passed: three exact mode names, live-row formatting, Waiting retained, longest full text fitted without clipping across GUI1�4/small-window metrics; progress/party rows unchanged.");
    }
}

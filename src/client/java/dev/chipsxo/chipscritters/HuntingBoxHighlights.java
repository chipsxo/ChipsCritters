package dev.chipsxo.chipscritters;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

public final class HuntingBoxHighlights {
    private static AbstractContainerScreen<?> activeBox;
    public static boolean isOpen() { return activeBox != null; }
    private HuntingBoxHighlights() { }
    public static boolean matchesTitle(String title) {
        return title != null && title.replaceAll("\\u00a7.", "").strip().matches("(?:\\(\\d+/\\d+\\) )?Hunting Box");
    }
    private static int rows(AbstractContainerScreen<?> screen) {
        if(screen.getMenu() instanceof net.minecraft.world.inventory.ChestMenu chest) return chest.getRowCount();
        return Math.max(1,Math.min(6,(screen.getMenu().slots.size()-36)/9));
    }
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            String title=screen.getTitle().getString();
            if(title.contains("Hunting")) ChipsCrittersClient.LOGGER.info("HuntingBox init: title={}, titleMatch={}, screenClass={}, enabled={}", title,matchesTitle(title),screen.getClass().getName(),ChipsConfig.get().huntingBoxColors);
            if (!(screen instanceof AbstractContainerScreen<?> box) || !matchesTitle(title)) return;
            java.util.Map<Integer,String> logged = new java.util.HashMap<>();
            boolean[] drawLogged = {false};
            activeBox = box;
            ScreenEvents.remove(screen).register(closed -> { if (activeBox == closed) activeBox = null; });
            BazaarPriceService.refreshIfDue();
            ScreenEvents.afterExtract(screen).register((current, graphics, mouseX, mouseY, delta) -> {
                if (!drawLogged[0]) { drawLogged[0]=true; ChipsCrittersClient.LOGGER.info("HuntingBox afterExtract fired: title={}, slots={}",title,box.getMenu().slots.size()); }
                BazaarPriceService.refreshIfDue();
                int left = (current.width - 176) / 2;
                int top = (current.height - (rows(box) * 18 + 114)) / 2;
                int slots = rows(box) * 9;
                for (int index = 0; index < slots; index++) {
                    var slot = box.getMenu().slots.get(index);
                    var assessment = ShardTooltip.assess(slot.getItem());
                    int decision = assessment.decision();
                    String detail=assessment.toString();
                    if(!detail.equals(logged.put(index,detail))) ChipsCrittersClient.LOGGER.info("HuntingBox slot {} titleMatch=true drawEvent=true {}",index,detail);
                    if (decision == 0) continue;
                    int x = left + slot.x, y = top + slot.y;
                    int color = decision > 0 ? 0xEE00FF80 : 0xEEFF3030;
                    graphics.fill(x - 1, y - 1, x + 17, y, color);
                    graphics.fill(x - 1, y + 16, x + 17, y + 17, color);
                    graphics.fill(x - 1, y, x, y + 16, color);
                    graphics.fill(x + 16, y, x + 17, y + 16, color);
                    // Corner marker stays outside the item, its rarity ring and count.
                    if (decision > 0) {
                        graphics.fill(x + 3, y - 3, x + 4, y - 2, color);
                        graphics.fill(x + 2, y - 2, x + 5, y - 1, color);
                        graphics.fill(x + 1, y - 1, x + 6, y, color);
                    } else {
                        for (int pixel = 0; pixel < 3; pixel++) {
                            graphics.fill(x + 2 + pixel, y - 3 + pixel, x + 3 + pixel, y - 2 + pixel, color);
                            graphics.fill(x + 4 - pixel, y - 3 + pixel, x + 5 - pixel, y - 2 + pixel, color);
                        }
                    }
                }
            });
        });
    }
}

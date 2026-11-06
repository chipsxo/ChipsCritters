package dev.chipsxo.chipscritters;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

/** Short flashing screen popup after all four shared biome claims are marked done. */
public final class WarpSafariReminder {
	private static final int PANEL_WIDTH = 360;
	private static final int PANEL_HEIGHT = 54;
	private static long visibleUntil;

	private WarpSafariReminder() { }

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(ChipsCrittersClient.MOD_ID, "warp_safari_reminder"), WarpSafariReminder::render);
	}

	public static void show() {
		if (!ChipsCrittersClient.isInCritterSafari()) return;
		visibleUntil = System.currentTimeMillis() + ChipsConfig.get().warpReminderSeconds * 1000L;
	}

	public static void dismiss() { visibleUntil = 0L; }

	private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		if (client.player == null || !ChipsCrittersClient.isInCritterSafari()
				|| !ChipsConfig.get().partyLeaderWarpReminder || now >= visibleUntil) return;
		int screenWidth = client.getWindow().getGuiScaledWidth();
		int panelWidth = Math.max(1, Math.min(PANEL_WIDTH, screenWidth - 12));
		int x = (screenWidth - panelWidth) / 2;
		int y = 42;
		graphics.fill(x, y, x + panelWidth, y + PANEL_HEIGHT, 0xE5000000);
		graphics.fill(x, y, x + panelWidth, y + 2, 0xFFFFC844);
		graphics.centeredText(client.font, "Party route complete", screenWidth / 2, y + 7, 0xFFFFD96A);
		int warpColor = ((now / 350L) & 1L) == 0L ? 0xFFFFFF55 : 0xFFFFFFFF;
		graphics.centeredText(client.font, "Next run: /warp safari", screenWidth / 2, y + 22, warpColor);
		graphics.centeredText(client.font, "Press " + ChipsCrittersClient.dismissReminderBinding() + " to dismiss", screenWidth / 2, y + 38, 0xFFCCCCCC);
	}
}

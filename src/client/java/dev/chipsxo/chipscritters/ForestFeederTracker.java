package dev.chipsxo.chipscritters;

import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Tracks local Forest bait counts and the server's Birdfeeder/capture messages. */
public final class ForestFeederTracker {
	private static final ForestBirdRunState BIRDS = new ForestBirdRunState();
	private static int knownFeederFeed = -1;
	private static int previousTotalFeed = -1;
	private static Screen currentScreen;

	private ForestFeederTracker() { }

	public static void reset() {
		knownFeederFeed = -1;
		previousTotalFeed = -1;
	}

	public static void newRunStarted() {
		reset();
		BIRDS.resetForNewRun();
	}

	public static void screenOpened(Screen screen) {
		currentScreen = screen;
	}

	public static void screenRemoved(Screen screen) {
		if (currentScreen == screen) currentScreen = null;
	}

	public static void tick(Minecraft client) {
		if (!RouteController.waitingForForestBird() || client.player == null) return;
		if (BIRDS.feedExhausted()) {
			RouteController.forestFeedExhausted(BIRDS.missingBirdsText());
			return;
		}

		int playerFeed = countPlayerFeed(client);
		if (currentScreen instanceof AbstractContainerScreen<?> screen && isBirdfeederTitle(screen.getTitle().getString())) {
			knownFeederFeed = countFeederSlots(screen);
		}

		// -1 means we have not inspected the Birdfeeder UI yet. Do not guess that its stored bait is gone.
		int totalFeed = knownFeederFeed < 0 ? -1 : playerFeed + knownFeederFeed;
		if (totalFeed >= 0 && previousTotalFeed > 0 && totalFeed == 0) {
			markExhausted(client);
		}
		if (knownFeederFeed == 0 && playerFeed == 0) {
			markExhausted(client);
		}
		if (totalFeed >= 0) previousTotalFeed = totalFeed;
	}

	public static void onGameMessage(String rawMessage) {
		if (rawMessage == null) return;
		String message = rawMessage.replaceAll("§.", "").trim();
		ForestBirdRunState.CaptureUpdate capture = BIRDS.recordCapture(message);
		if (capture != null && RouteController.active() && RouteController.biome() == Biome.FOREST && BIRDS.finishAllBirdsOnce())
            RouteController.forestAllBirdsCaught();
        if (capture != null && BIRDS.completionIssued() && (capture.newlyCaught() || capture.resolvedFinalFeed())) {
			Minecraft client = Minecraft.getInstance();
			if (client.player != null) client.player.sendOverlayMessage(Component.literal(
					"[ChipsCritters] " + BIRDS.missingBirdsText()));
		}
		if (!RouteController.waitingForForestBird()) return;
		if (looksLikeEmptyFeederMessage(message)) {
			Minecraft client = Minecraft.getInstance();
			if (client.player != null && countPlayerFeed(client) > 0) return;
			if (knownFeederFeed > 0) return;
			markExhausted(client);
		}
	}

	public static boolean feedExhausted() {
		return BIRDS.feedExhausted();
	}

	public static boolean allBirdsFinished() { return BIRDS.completionIssued() && BIRDS.allBirdsCaught(); }
	public static String missingBirdsText() { return BIRDS.missingBirdsText(); }
	public static boolean finalCatchPending() { return BIRDS.finalFeedCatchPending(); }

	private static int countPlayerFeed(Minecraft client) {
		int total = 0;
		for (int slot = 0; slot <= 40; slot++) total += feedCount(client.player.getInventory().getItem(slot));
		if (client.player.containerMenu != null) total += feedCount(client.player.containerMenu.getCarried());
		return total;
	}

	private static int countFeederSlots(AbstractContainerScreen<?> screen) {
		var slots = screen.getMenu().slots;
		// Container menus append the player's 36 inventory/hotbar slots after the feeder's own slots.
		int feederSlotCount = Math.max(0, slots.size() - 36);
		int total = 0;
		for (int index = 0; index < feederSlotCount; index++) total += feedCount(slots.get(index).getItem());
		return total;
	}

	private static int feedCount(ItemStack stack) {
		if (stack == null || stack.isEmpty()) return 0;
		String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
		return name.contains("yogi berry") || name.contains("wriggleworm") || name.contains("bag of seeds")
				? stack.getCount() : 0;
	}

	private static boolean isBirdfeederTitle(String title) {
		String normalized = title.toLowerCase(Locale.ROOT).replace(" ", "");
		return normalized.contains("birdfeeder");
	}

	private static boolean looksLikeEmptyFeederMessage(String message) {
		String normalized = message.toLowerCase(Locale.ROOT);
		boolean namesFeeder = normalized.contains("birdfeeder") || normalized.contains("bird feeder");
		boolean namesFeed = normalized.contains("feed") || normalized.contains("bait") || normalized.contains("seed")
				|| normalized.contains("yogi berry") || normalized.contains("wriggleworm") || normalized.contains("bag of seeds");
		boolean saysEmpty = normalized.contains("empty") || normalized.contains("no more") || normalized.contains("out of")
				|| normalized.contains("ran out") || normalized.contains("run out") || normalized.contains("depleted")
				|| normalized.contains("don't have") || normalized.contains("do not have") || normalized.contains("none left");
		return (namesFeeder || namesFeed) && saysEmpty;
	}

	private static void markExhausted(Minecraft client) {
		if (!BIRDS.markFeedExhausted()) return;
		RouteController.forestFeedExhausted(BIRDS.missingBirdsText());
	}
}

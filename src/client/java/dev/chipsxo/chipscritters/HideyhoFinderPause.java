package dev.chipsxo.chipscritters;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;

/** Pauses route guidance while SkyHanni's server-signaled Hideyho search is underway. */
public final class HideyhoFinderPause {
	private static final long MAX_PAUSE_MILLIS = 5 * 60 * 1000L;
	private static final Pattern NAV_STOP = Pattern.compile("^/?(?:shnav|shnavall)\\s+stop(?:\\s.*)?$", Pattern.CASE_INSENSITIVE);
	private static boolean active;
	private static long startedAt;

	private HideyhoFinderPause() { }

	public static boolean active() { return active; }
    static void beginManualSearch(){active=false;startedAt=0;cancelSkyHanniNavigation();}

	public static void onGameMessage(String text) {
		String normalized = stripFormatting(text).toLowerCase(Locale.ROOT);
        if(normalized.startsWith("[mob] hideyho: no peeking!"))HideyhoSearch.newRound();
        if(HideyhoSearch.active()){
            if(HideyhoSearchSignals.found(text)){RouteController.hideyhoFound();if(HideyhoSearchSignals.reward(text))RouteController.hideyhoRelocated();cancelSkyHanniNavigation();active=false;startedAt=0;}
            return;
        }
		if(normalized.matches("^capture! you found the (?:sparkling )?hideyho,.*!$"))RouteController.hideyhoRelocated();
        if (normalized.contains("hideyho: no peeking!")) {
			active = true;
			startedAt = System.currentTimeMillis();
			return;
		}
		if (normalized.contains("hideyho: aah! you found me!") || normalized.contains("found hideyho!")) {
			RouteController.hideyhoFound();
			cancelSkyHanniNavigation();
			resume();
			return;
		}
		if (normalized.contains("could not find any hidden hideyho")) {
			resume();
		}
	}

	public static void onCommand(String command) {
		if (command != null && NAV_STOP.matcher(command.trim()).matches()) resume();
	}

	public static void tick() {
		if (active && System.currentTimeMillis() - startedAt >= MAX_PAUSE_MILLIS) resume();
	}

	public static void reset() { resume(); }

	private static void resume() {
		if (!active) return;
		active = false;
		startedAt = 0L;
		RouteController.onFinderSearchEnded();
		RouteController.onFinderResume();
	}

	private static void cancelSkyHanniNavigation() {
		Minecraft client = Minecraft.getInstance();
		if (client.getConnection() == null) return;
		var dispatcher = ClientCommands.getActiveDispatcher();
		if (dispatcher == null || dispatcher.getRoot().getChild("shnav") == null) return;
		if (!(client.getConnection().getSuggestionsProvider() instanceof FabricClientCommandSource source)) return;
		try {
			dispatcher.execute("shnav stop", source);
		} catch (CommandSyntaxException | RuntimeException exception) {
			ChipsCrittersClient.LOGGER.debug("Could not stop SkyHanni navigation after Hideyho was found", exception);
		}
	}

	private static String stripFormatting(String text) {
		return text == null ? "" : text.replaceAll("§.", "");
	}
}

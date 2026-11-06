package dev.chipsxo.chipscritters;

import java.util.List;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Local route history and persistent personal bests. */
public final class RunHistory {
	public static final class Entry {
		public long completedAt;
		public String biome = "";
		public long durationMillis;
		public int stopCount;
		public String mode = "";
	}

	private static final PersistentRunHistory STORE = new PersistentRunHistory(
			FabricLoader.getInstance().getConfigDir().resolve("chipscritters-history.json"));

	private RunHistory() { }

	/** Returns true only when this real run establishes a new best for its biome and mode. */
	public static synchronized boolean record(Biome biome, long durationMillis, int stopCount, ChipsConfig.Mode mode) {
		return STORE.record(biome, Math.max(0L, durationMillis), stopCount, mode);
	}

	public static synchronized String summary() {
		List<Entry> runs = STORE.recentEntries(Integer.MAX_VALUE);
		if (runs.isEmpty()) return "Route history: no completed routes";
		Entry last = runs.getFirst();
		return "Route history: " + STORE.completedRunCount() + " completed  •  last " + displayBiome(last.biome) + " " + duration(last.durationMillis);
	}

	public static long archivedFastestBest(Biome biome){return STORE.archivedFastestBest(biome);}
    public static String savedModeLabel(String value){return "FASTEST".equals(value)?"Fastest (retired)":modeLabel(parseMode(value));}

    public static synchronized int completedRunCount() { return STORE.completedRunCount(); }

	public static synchronized long personalBestMillis(Biome biome, ChipsConfig.Mode mode) {
		return STORE.personalBestMillis(biome, mode);
	}

	public static synchronized List<Entry> recentEntries(int limit) { return STORE.recentEntries(limit); }

	public static synchronized void printRecent() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
		client.player.sendSystemMessage(Component.literal("[ChipsCritters] " + STORE.completedRunCount() + " completed route runs (most recent first):"));
		for (Entry entry : STORE.recentEntries(8)) {
			client.player.sendSystemMessage(Component.literal("• " + displayBiome(entry.biome) + " — " + duration(entry.durationMillis) + " — " + entry.stopCount + " stops — " + savedModeLabel(entry.mode)));
		}
	}

	public static synchronized void printStats() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
		client.player.sendSystemMessage(Component.literal("[ChipsCritters] Personal bests by biome and mode:"));
		for (Biome biome : Biome.values()) {
			StringBuilder line = new StringBuilder(biome.displayName()).append(": ");
			boolean found = false;
			for (ChipsConfig.Mode mode : ChipsConfig.Mode.values()) {
				long best = personalBestMillis(biome, mode);
				if (best < 0L) continue;
				if (found) line.append("  •  ");
				line.append(modeLabel(mode)).append(' ').append(duration(best));
				found = true;
			}
			client.player.sendSystemMessage(Component.literal("• " + line + (found ? "" : "no runs yet")+(archivedFastestBest(biome)<0?"":"  •  Fastest (retired) "+duration(archivedFastestBest(biome)))));
		}
	}

	public static String formatDuration(long millis) { return duration(millis); }

	public static String modeLabel(ChipsConfig.Mode mode) {
		return switch (mode) {
			case MONEY -> "Money";
			case UNIQUE -> "Unique";
			case FULL_CLEAR -> "Full Clear";
		};
	}

	private static ChipsConfig.Mode parseMode(String value) {
		try { return ChipsConfig.Mode.valueOf(value); }
		catch (IllegalArgumentException | NullPointerException ignored) { return ChipsConfig.Mode.FULL_CLEAR; }
	}

	private static String displayBiome(String biomeName) {
		Biome biome = Biome.parse(biomeName);
		return biome == null ? biomeName : biome.displayName();
	}

	private static String duration(long millis) {
		long totalSeconds = Math.max(0L, millis) / 1000L;
		return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60);
	}
}

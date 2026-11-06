package dev.chipsxo.chipscritters;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Version-stable history store with a migration path from the original runs-only JSON. */
final class PersistentRunHistory {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int MAX_RECENT_RUNS = 30;
	private final Path file;
	private final Path backup;
	private final Path temp;
	private Store store;

	private static final class Store {
		List<RunHistory.Entry> runs = new ArrayList<>();
		Map<String, RunHistory.Entry> personalBests = new LinkedHashMap<>();
	}

	PersistentRunHistory(Path file) {
		this.file = file;
		this.backup = file.resolveSibling(file.getFileName() + ".bak");
		this.temp = file.resolveSibling(file.getFileName() + ".tmp");
		this.store = load();
	}

	synchronized boolean record(Biome biome, long durationMillis, int stopCount, ChipsConfig.Mode mode) {
		long previousBest = personalBestMillis(biome, mode);
		RunHistory.Entry entry = new RunHistory.Entry();
		entry.completedAt = System.currentTimeMillis();
		entry.biome = biome.name();
		entry.durationMillis = Math.max(0L, durationMillis);
		entry.stopCount = Math.max(0, stopCount);
		entry.mode = mode.name();
		store.runs.add(entry);
		while (store.runs.size() > MAX_RECENT_RUNS) store.runs.removeFirst();
		boolean newBest = previousBest < 0L || entry.durationMillis < previousBest;
		if (newBest) store.personalBests.put(key(biome, mode), copy(entry));
		save();
		return newBest;
	}

	synchronized long archivedFastestBest(Biome biome) {
        RunHistory.Entry entry=store.personalBests.get(biome.name()+"/FASTEST");
        return valid(entry)?entry.durationMillis:-1;
    }

    synchronized int completedRunCount() { return store.runs.size(); }

	synchronized long personalBestMillis(Biome biome, ChipsConfig.Mode mode) {
		RunHistory.Entry best = store.personalBests.get(key(biome, mode));
		return best == null ? -1L : Math.max(0L, best.durationMillis);
	}

	synchronized List<RunHistory.Entry> recentEntries(int limit) {
		List<RunHistory.Entry> recent = new ArrayList<>();
		int count = Math.max(0, limit);
		for (int i = store.runs.size() - 1; i >= 0 && recent.size() < count; i--) recent.add(copy(store.runs.get(i)));
		return List.copyOf(recent);
	}

	private Store load() {
		Store loaded = read(file);
		if (loaded == null) loaded = read(backup);
		if (loaded == null) loaded = read(temp);
		if (loaded == null) {
			if (Files.exists(file) || Files.exists(backup)) ChipsCrittersClient.LOGGER.warn("Route history and its backup were unreadable; starting with an empty history.");
			return new Store();
		}
		if (loaded.runs == null) loaded.runs = new ArrayList<>();
		if (loaded.personalBests == null) loaded.personalBests = new LinkedHashMap<>();
		boolean migrated = false;
		for (RunHistory.Entry entry : loaded.runs) {
			if (!valid(entry)) continue;
			Biome biome = Biome.parse(entry.biome);
			ChipsConfig.Mode mode = parseMode(entry.mode);
			if (biome == null || (mode == null && !"FASTEST".equals(entry.mode))) continue;
			String key = mode == null ? biome.name()+"/FASTEST" : key(biome, mode);
			RunHistory.Entry existing = loaded.personalBests.get(key);
			if (!valid(existing) || entry.durationMillis < existing.durationMillis) {
				loaded.personalBests.put(key, copy(entry));
				migrated = true;
			}
		}
		if (migrated) saveStore(loaded);
		return loaded;
	}

	private Store read(Path source) {
		try {
			if (!Files.exists(source)) return null;
			Store parsed = GSON.fromJson(Files.readString(source), Store.class);
			return parsed == null ? null : parsed;
		} catch (Exception exception) {
			return null;
		}
	}

	private void save() { saveStore(store); }

	private void saveStore(Store value) {
		try {
			Files.createDirectories(file.getParent());
			byte[] bytes = GSON.toJson(value).getBytes(java.nio.charset.StandardCharsets.UTF_8);
			try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
				ByteBuffer buffer = ByteBuffer.wrap(bytes);
				while (buffer.hasRemaining()) channel.write(buffer);
				channel.force(true);
			}
			if (Files.exists(file) && read(file) != null) Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
			try {
				Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException ignored) {
				// Keep the previous valid file as .bak before replacing it on filesystems without atomic rename.
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException exception) {
			ChipsCrittersClient.LOGGER.warn("Could not safely save route history", exception);
			try { Files.deleteIfExists(temp); } catch (IOException ignored) { }
		}
	}

	private static boolean valid(RunHistory.Entry entry) {
		return entry != null && entry.biome != null && entry.mode != null && entry.durationMillis >= 0L;
	}

	private static RunHistory.Entry copy(RunHistory.Entry source) {
		RunHistory.Entry entry = new RunHistory.Entry();
		entry.completedAt = source.completedAt;
		entry.biome = source.biome;
		entry.durationMillis = source.durationMillis;
		entry.stopCount = source.stopCount;
		entry.mode = source.mode;
		return entry;
	}

	private static String key(Biome biome, ChipsConfig.Mode mode) { return biome.name() + "/" + mode.name(); }

	private static ChipsConfig.Mode parseMode(String value) {
		try { return ChipsConfig.Mode.valueOf(value); }
		catch (IllegalArgumentException | NullPointerException ignored) { return null; }
	}
}

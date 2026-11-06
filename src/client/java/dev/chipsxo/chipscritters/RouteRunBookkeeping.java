package dev.chipsxo.chipscritters;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Keeps one real route clock and at most one saved completion per biome and mode in a Safari run. */
final class RouteRunBookkeeping {
	private record RouteKey(Biome biome, ChipsConfig.Mode mode) { }
	private final Map<Biome, Long> startedAt = new HashMap<>();
	private final Set<RouteKey> completed = new HashSet<>();
    private final Set<Biome> disqualified=new HashSet<>();
    synchronized void disqualify(Biome biome){disqualified.add(biome);}
    synchronized boolean eligible(Biome biome){return !disqualified.contains(biome);}

	synchronized long startOrResume(Biome biome, long nowMillis, long existingRunStartMillis) {
		long candidate = existingRunStartMillis > 0L ? existingRunStartMillis : nowMillis;
		return startedAt.computeIfAbsent(biome, ignored -> candidate);
	}

	synchronized long startedAt(Biome biome) { return startedAt.getOrDefault(biome, 0L); }

	synchronized boolean markCompleted(Biome biome, ChipsConfig.Mode mode, boolean eligible) {
		return eligible && !disqualified.contains(biome) && completed.add(new RouteKey(biome, mode));
	}

	synchronized void newRun() {
		disqualified.clear();
		startedAt.clear();
		completed.clear();
	}
}

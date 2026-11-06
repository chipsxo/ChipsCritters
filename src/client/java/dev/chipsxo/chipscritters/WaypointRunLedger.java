package dev.chipsxo.chipscritters;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.phys.Vec3;

/** In-memory per-lobby memory for stops already completed or skipped in this run. */
public final class WaypointRunLedger {
	private static final double MERGE_RADIUS_SQUARED = 1.5 * 1.5;
	private static final Map<Biome, Set<Vec3>> SATISFIED = new EnumMap<>(Biome.class);
	private static final RunCaptureState CAPTURES = new RunCaptureState();
	private static long stateVersion;
	private static boolean hideyhoFound;
	private static boolean hideyhoSearchEnded;
	static final HauntedFinish HAUNTED = new HauntedFinish();
    static final HauntedUniqueState HAUNTED_UNIQUE=new HauntedUniqueState();

	private WaypointRunLedger() { }

	public static synchronized void onLobbyChanged(String nextLobbyKey) {
		if (CAPTURES.onLobbyChanged(nextLobbyKey)) {
			SATISFIED.clear();
			hideyhoFound = false;
			hideyhoSearchEnded = false;
		HAUNTED.reset();HAUNTED_UNIQUE.reset();
			stateVersion++;
		}
	}

	public static synchronized void clearRun() {
		SATISFIED.clear();
		hideyhoFound = false;
		hideyhoSearchEnded = false;
		HAUNTED.reset();HAUNTED_UNIQUE.reset();
		CAPTURES.resetAll();
		stateVersion++;
	}

	public static synchronized void newRunStarted() {
		SATISFIED.clear();
		hideyhoFound = false;
		hideyhoSearchEnded = false;
		HAUNTED.reset();HAUNTED_UNIQUE.reset();
		CAPTURES.newRunStarted();
		stateVersion++;
	}

	public static synchronized int restoreBiome(Biome biome,boolean resetCaptures){
        int count=SATISFIED.getOrDefault(biome,Set.of()).size();SATISFIED.remove(biome);stateVersion++;
        if(resetCaptures){
            if(biome==Biome.CAVERN)CAPTURES.resetCavernCaptures();
            if(biome==Biome.ICY)CAPTURES.resetIcyCaptures();
            if(biome==Biome.HAUNTED){long start=HAUNTED.started;HAUNTED.reset();HAUNTED_UNIQUE.reset();HAUNTED.started=start;hideyhoFound=false;hideyhoSearchEnded=false;}
        }
        return count;
    }
	public static synchronized void markSatisfied(Biome biome, Vec3 position) {
		if (biome == null || position == null) return;
		SATISFIED.computeIfAbsent(biome, ignored -> new HashSet<>()).add(position);
	}

	public static synchronized boolean isSatisfied(Biome biome, Vec3 position) {
		if (biome == null || position == null) return false;
		for (Vec3 reached : SATISFIED.getOrDefault(biome, Set.of())) {
			if (sameRunStop(biome, reached, position)) return true;
		}
		return false;
	}

	public static synchronized List<Vec3> withoutSatisfied(Biome biome, List<Vec3> stops) {
		List<Vec3> remaining = new ArrayList<>(stops.size());
		for (Vec3 stop : stops) {
			if (hideyhoSearchEnded && SafariData.isHideyhoWaypoint(biome, stop)) continue;
            if(biome==Biome.HAUNTED&&HideyhoSearch.STATE.found&&SafariData.pureHideyhoSearch(stop))continue;
			if (!isSatisfied(biome, stop)) remaining.add(stop);
		}
		return List.copyOf(remaining);
	}

	public static synchronized boolean hideyhoFound() {
		if (hideyhoFound) return false;
		hideyhoFound = true;
		hideyhoSearchEnded = true;
		stateVersion++;
		return true;
	}

	public static synchronized boolean endHideyhoSearch() {
		if (hideyhoSearchEnded) return false;
		hideyhoSearchEnded = true;
		stateVersion++;
		return true;
	}

	public static synchronized boolean hideyhoSearchEnded() { return hideyhoSearchEnded; }

	public static synchronized void markSnoozleCaught() { CAPTURES.markSnoozleCaught(); stateVersion++; }
	public static synchronized boolean snoozleCaught() { return CAPTURES.snoozleCaught(); }
	public static synchronized void markTroodonCaught() { CAPTURES.markTroodonCaught(); stateVersion++; }
	public static synchronized boolean troodonCaught() { return CAPTURES.troodonCaught(); }
	public static synchronized void markIcyWumpaSpawned() { CAPTURES.markIcyWumpaSpawned(); stateVersion++; }
	public static synchronized boolean icyWumpaSpawned() { return CAPTURES.icyWumpaSpawned(); }
	public static synchronized long stateVersion() { return stateVersion; }

	static boolean sameRunStop(Biome biome, Vec3 first, Vec3 second) {
		if (SafariData.isSnoozleWaypoint(biome, first) || SafariData.isSnoozleWaypoint(biome, second)
                || (biome==Biome.HAUNTED && (SafariData.GIMMIEGOLD.equals(first)||SafariData.GIMMIEGOLD.equals(second)))
                || SafariData.isHideonwallLocation(biome,first) || SafariData.isHideonwallLocation(biome,second)) {
			return first.distanceToSqr(second) < 0.01;
		}
		if (first.distanceToSqr(second) <= MERGE_RADIUS_SQUARED) return true;
		// Floor checks can be a couple of Y blocks above their reachable route stop.
		return SafariData.isFloorDropAlias(biome, first, second)
				|| SafariData.isFloorDropAlias(biome, second, first);
	}
}

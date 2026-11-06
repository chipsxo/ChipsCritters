package dev.chipsxo.chipscritters;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Deterministic fixed-waypoint response to the exact Icy Wumpa spawn message. */
public final class IcyWumpaRoute {
	public record SpawnResult(boolean outOfScope, boolean alreadyReached, boolean changed,
			List<Vec3> stops, int nextStop) { }

	private IcyWumpaRoute() { }

	public static SpawnResult onSpawn(boolean routeActive, Biome currentBiome, boolean insideIcy,
			boolean alreadyReached, List<Vec3> currentStops, int currentNextStop, Vec3 wumpa, boolean fixedEnd) {
		if (!routeActive || currentBiome != Biome.ICY || !insideIcy) {
			return new SpawnResult(true, false, false, currentStops, currentNextStop);
		}
		int index = -1;
		for (int i = 0; i < currentStops.size(); i++) {
			if (currentStops.get(i).distanceToSqr(wumpa) < 0.01) { index = i; break; }
		}
		if (alreadyReached || index >= 0 && index < currentNextStop) {
			return new SpawnResult(false, true, false, currentStops, currentNextStop);
		}
		int prefixSize = Math.max(0, Math.min(currentNextStop, currentStops.size()));
		List<Vec3> updated = new ArrayList<>(currentStops.subList(0, prefixSize));
		updated.add(wumpa);
		if (!fixedEnd && index >= 0) {
			for (int i = Math.max(prefixSize, index + 1); i < currentStops.size(); i++) updated.add(currentStops.get(i));
		}
		return new SpawnResult(false, false, true, List.copyOf(updated), prefixSize);
	}
}

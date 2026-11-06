package dev.chipsxo.chipscritters;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Pure capture-message and route-filter rules shared by runtime and regression checks. */
public final class IcyCaptureRules {
	public record Point(int x, int y, int z) { }
	public record FilteredStops<T>(List<T> stops, int removed) { }

	private static final List<Point> TROODON_WAYPOINTS = List.of(
			new Point(-103, 79, -93), new Point(-108, 89, -25), new Point(-130, 78, -60));

	private IcyCaptureRules() { }

	public static List<Point> troodonWaypoints() { return TROODON_WAYPOINTS; }

	public static boolean isTroodonWaypoint(Point point) {
		return TROODON_WAYPOINTS.contains(point);
	}

	public static boolean isLocalCaptureMessage(String rawMessage, String critterName) {
		if (rawMessage == null || critterName == null || critterName.isBlank()) return false;
		String message = rawMessage.replaceAll("§.", "");
		if (Pattern.compile("(?i)\\bLOOT SHARE!").matcher(message).find()) return false;
		String escapedName = Pattern.quote(critterName.trim());
		Pattern capture = Pattern.compile("(?i)\\bCAPTURE!\\s*You caught\\s+(?:a\\s+)?(?:SPARKLING\\s+)?"
				+ escapedName + "\\b");
		return capture.matcher(message).find();
	}

	public static boolean shouldFinishWumpa(boolean activeIcyRoute, int remainingStops, String message) {
		// Catching the Wumpa ends the Icy route even when ordinary stops remain.
		return activeIcyRoute && isLocalCaptureMessage(message, "Wumpa");
	}

	public static boolean shouldClearTroodons(boolean activeIcyRoute, boolean uniqueMode, String message) {
		return activeIcyRoute && uniqueMode && isLocalCaptureMessage(message, "Troodon");
	}

	public static boolean keepTroodonWaypoints(boolean uniqueMode, boolean caughtThisRun) {
		return !uniqueMode || !caughtThisRun;
	}

	/** Preserves completed prefix stops while filtering matching stops from the current and future route. */
	public static <T> FilteredStops<T> removeRemaining(List<T> route, int nextIndex, boolean remove,
			Predicate<T> predicate) {
		Objects.requireNonNull(route, "route");
		Objects.requireNonNull(predicate, "predicate");
		int firstPending = Math.max(0, Math.min(nextIndex, route.size()));
		List<T> updated = new ArrayList<>(route.size());
		updated.addAll(route.subList(0, firstPending));
		int removed = 0;
		for (int index = firstPending; index < route.size(); index++) {
			T stop = route.get(index);
			if (remove && predicate.test(stop)) removed++;
			else updated.add(stop);
		}
		return new FilteredStops<>(List.copyOf(updated), removed);
	}
}

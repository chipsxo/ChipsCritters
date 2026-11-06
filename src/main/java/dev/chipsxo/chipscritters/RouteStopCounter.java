package dev.chipsxo.chipscritters;

/** Formats the live target stop using the current route list and cursor. */
public final class RouteStopCounter {
	private RouteStopCounter() { }

	public static String text(boolean active, int nextStop, int stopCount) {
		if (!active || stopCount <= 0) return "";
		long current = Math.min(Math.max(0L, (long)nextStop) + 1L, (long)stopCount);
		return current + "/" + stopCount;
	}
}

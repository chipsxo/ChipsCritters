package dev.chipsxo.chipscritters;

/** Isolates route-mutation rules while Hideyho search navigation is active. */
public final class HideyhoWaypointPolicy {
	private HideyhoWaypointPolicy() { }

	public static boolean allowCurrentHideyhoStop(boolean finderPaused, boolean currentIsHideyho) {
		return !finderPaused || currentIsHideyho;
	}

	public static boolean allowPassedStopCollection(boolean finderPaused, boolean hauntedSearchPortion) {
		return !finderPaused && !hauntedSearchPortion;
	}

	public static boolean allowAutoSkip(boolean finderPaused, boolean hauntedSearchPortion) {
		return !finderPaused && !hauntedSearchPortion;
	}

	public static boolean deferRelayClaim(boolean finderPaused, boolean hauntedSearchPortion) {
		return finderPaused || hauntedSearchPortion;
	}

	/** During Finder pause a search marker can advance locally, without a claim or ledger write. */
	public static boolean advanceSearchStopWithoutClaim(boolean finderPaused, boolean currentIsHideyho) {
		return finderPaused && currentIsHideyho;
	}
}

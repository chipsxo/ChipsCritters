package dev.chipsxo.chipscritters;

/** Catch flags survive route restarts and disconnects, and reset on a newly identified safari lobby. */
public final class RunCaptureState {
	private static final String NEW_RUN_START_PREFIX = "NEW RUN START!";
	private String lobbyKey = "";
	private boolean snoozleCaught;
	private boolean troodonCaught;
	private boolean icyWumpaSpawned;

	public static boolean isNewRunStartMessage(String rawMessage) {
		if (rawMessage == null) return false;
		String message = rawMessage.replaceAll("§.", "").trim();
		return message.startsWith(NEW_RUN_START_PREFIX)
				|| message.startsWith("HEAD START! You started with ")
				|| message.equals("[NPC] Safari Manager: I already saw your ticket, so you're free to go.");
	}

	/** Empty lobby updates are transient disconnect/rejoin state and must not erase the run. */
	public synchronized boolean onLobbyChanged(String nextLobbyKey) {
		String normalized = nextLobbyKey == null ? "" : nextLobbyKey.trim();
		if (normalized.isEmpty() || normalized.equals(lobbyKey)) return false;
		boolean newRun = !lobbyKey.isEmpty();
		if (newRun) resetFlags();
		lobbyKey = normalized;
		return newRun;
	}

	public synchronized void resetAll() {
		lobbyKey = "";
		resetFlags();
	}

	/** Starts a fresh Safari run without requiring the lobby identifier to change. */
	public synchronized void newRunStarted() { resetFlags(); }

	private void resetFlags() {
		snoozleCaught = false;
		troodonCaught = false;
		icyWumpaSpawned = false;
	}

	public synchronized void resetCavernCaptures(){snoozleCaught=false;}
    public synchronized void resetIcyCaptures(){troodonCaught=false;icyWumpaSpawned=false;}
	public synchronized void markSnoozleCaught() { snoozleCaught = true; }
	public synchronized boolean snoozleCaught() { return snoozleCaught; }
	public synchronized void markTroodonCaught() { troodonCaught = true; }
	public synchronized boolean troodonCaught() { return troodonCaught; }
	public synchronized void markIcyWumpaSpawned() { icyWumpaSpawned = true; }
	public synchronized boolean icyWumpaSpawned() { return icyWumpaSpawned; }
}

package dev.chipsxo.chipscritters;

import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Tracks the player's own Forest bird captures and one-time feed-exhaustion completion per run. */
public final class ForestBirdRunState {
	public record CaptureUpdate(String bird, boolean newlyCaught, boolean resolvedFinalFeed) { }

	private enum Bird { BLUEBIRD("Bluebird"), PARAKEET("Parakeet"), MACAW("Macaw");
		private final String displayName;
		Bird(String displayName) { this.displayName = displayName; }
	}

	private static final Pattern PERSONAL_CAPTURE = Pattern.compile(
			"(?i)^CAPTURE!\\s*You caught\\s+(?:a\\s+)?(?:SPARKLING\\s+)?(Bluebird|Parakeet|Macaw)\\b.*$");
	private final Set<Bird> caught = EnumSet.noneOf(Bird.class);
	private boolean feedExhausted;
	private boolean completionIssued;
	private boolean finalFeedCatchPending;

	public CaptureUpdate recordCapture(String rawMessage) {
		if (rawMessage == null) return null;
		String message = rawMessage.replaceAll("§.", "").trim();
		Matcher matcher = PERSONAL_CAPTURE.matcher(message);
		if (!matcher.matches()) return null;
		Bird bird = Bird.valueOf(matcher.group(1).toUpperCase(java.util.Locale.ROOT));
		boolean newlyCaught = caught.add(bird);
		boolean resolvedFinalFeed = finalFeedCatchPending;
		finalFeedCatchPending = false;
		return new CaptureUpdate(bird.displayName, newlyCaught, resolvedFinalFeed);
	}

	/** Returns true once when feed is gone and Forest should finish. */
	public boolean markFeedExhausted() {
		feedExhausted = true;
		if (completionIssued) return false;
		completionIssued = true;
		finalFeedCatchPending = true;
		return true;
	}

	public boolean allBirdsCaught() { return caught.size() == Bird.values().length; }
    public boolean finishAllBirdsOnce() {
        if (!allBirdsCaught() || completionIssued) return false;
        completionIssued = true;
        finalFeedCatchPending = false;
        return true;
    }
    public boolean feedExhausted() { return feedExhausted; }
	public boolean completionIssued() { return completionIssued; }
	public boolean finalFeedCatchPending() { return finalFeedCatchPending; }

	public String missingBirdsText() {
		StringBuilder missing = new StringBuilder();
		for (Bird bird : Bird.values()) {
			if (caught.contains(bird)) continue;
			if (missing.length() > 0) missing.append(", ");
			missing.append(bird.displayName);
		}
		return missing.length() == 0 ? "All birds caught" : "Missing birds: " + missing;
	}

	public void resetForNewRun() {
		caught.clear();
		feedExhausted = false;
		completionIssued = false;
		finalFeedCatchPending = false;
	}
}

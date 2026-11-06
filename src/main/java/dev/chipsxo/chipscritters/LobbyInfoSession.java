package dev.chipsxo.chipscritters;

import java.util.ArrayDeque;
import java.util.Deque;

/** Tracks locraw truth, bounded automatic probes, and which exact replies to suppress. */
public final class LobbyInfoSession {
	public record Accepted(LobbyInfoRules.Info info, boolean hideReply) { }
	private record ExpectedReply(boolean userRequested, long requestedAtMillis) { }

	private static final long REQUEST_COOLDOWN_MILLIS = 3_000L;
	private static final long REPLY_EXPIRY_MILLIS = 30_000L;
	private static final int MAX_AUTOMATIC_ATTEMPTS = 3;
	private final Deque<ExpectedReply> expectedReplies = new ArrayDeque<>();
	private LobbyInfoRules.Info info;
	private boolean confirmed;
	private int automaticAttempts;
	private long lastAutomaticRequestMillis;

	public void resetForWorldChange() {
		reset(true);
	}

	public void resetForDisconnect() {
		reset(false);
	}

	private void reset(boolean keepCooldown) {
		info = null;
		confirmed = false;
		automaticAttempts = 0;
		if (!keepCooldown) lastAutomaticRequestMillis = 0L;
		expectedReplies.clear(); // Requests from an old world must never affect visibility in a new one.
	}

	public boolean requestDue(long nowMillis, boolean hypixel, boolean connected) {
		if (!hypixel || !connected || confirmed || automaticAttempts >= MAX_AUTOMATIC_ATTEMPTS) return false;
		return lastAutomaticRequestMillis == 0L || nowMillis - lastAutomaticRequestMillis >= REQUEST_COOLDOWN_MILLIS;
	}

	public void automaticRequestSent(long nowMillis) {
		automaticAttempts++;
		lastAutomaticRequestMillis = nowMillis;
		addExpectedReply(new ExpectedReply(false, nowMillis));
	}

	public void manualRequestSent(long nowMillis) {
		// Only /sr locraw (or its long alias) calls this. SEND_COMMAND cannot
		// reliably distinguish a human from another mod.
		expectedReplies.clear();
		addExpectedReply(new ExpectedReply(true, nowMillis));
	}

	/** Returns null for anything other than a whole-message locraw JSON object. */
	public Accepted accept(String rawMessage) {
		return accept(rawMessage, System.currentTimeMillis());
	}

	public Accepted accept(String rawMessage, long nowMillis) {
		LobbyInfoRules.Info parsed = LobbyInfoRules.parse(rawMessage);
		if (parsed == null) return null;
		info = parsed;
		confirmed = true;
		while (!expectedReplies.isEmpty() && nowMillis - expectedReplies.peekFirst().requestedAtMillis() > REPLY_EXPIRY_MILLIS) {
			expectedReplies.removeFirst();
		}
		ExpectedReply expected = expectedReplies.pollFirst();
        expectedReplies.clear(); // Valid truth ends the probe batch; no stale visibility entries.
		return new Accepted(parsed, expected == null || !expected.userRequested());
	}

	private void addExpectedReply(ExpectedReply reply) {
		expectedReplies.addLast(reply);
		while (expectedReplies.size() > 16) expectedReplies.removeFirst();
	}

	public boolean confirmed() { return confirmed; }
	public LobbyInfoRules.Info info() { return info; }
	public boolean inCritterSafari() { return confirmed && LobbyInfoRules.isCritterSafari(info); }
	public int automaticAttempts() { return automaticAttempts; }
}

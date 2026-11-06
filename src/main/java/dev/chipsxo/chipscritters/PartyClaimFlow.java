package dev.chipsxo.chipscritters;

/** Small deterministic state rules for the per-lobby [CC] claim handshake. */
public final class PartyClaimFlow {
	public enum EntryAction { NONE, CLAIM, JOIN }
	public record PartyMessage(String action, Biome biome) { }
		private static final java.util.regex.Pattern MESSAGE=java.util.regex.Pattern.compile("\\[CC]\\s+(CLAIM|JOIN|DONE)\\s+(ICY|HAUNTED|FOREST|CAVERN)",java.util.regex.Pattern.CASE_INSENSITIVE);
	public static PartyMessage parseMessage(String text) {
		if(text==null)return null;
		var m=MESSAGE.matcher(text);
		return m.find()?new PartyMessage(m.group(1).toUpperCase(java.util.Locale.ROOT),Biome.parse(m.group(2))):null;
	}
	public static boolean shouldSendDone(boolean enabled,boolean connected,boolean inParty){return enabled&&connected&&inParty;}
	public record Claim(String owner, boolean done) { }

	private PartyClaimFlow() { }

	public static EntryAction entryAction(boolean actualBiomeEntry, boolean alreadyEntered, String localPlayer, Claim current) {
		if (!actualBiomeEntry || alreadyEntered) return EntryAction.NONE;
		if (current == null) return EntryAction.CLAIM;
		return current.owner().equalsIgnoreCase(localPlayer) ? EntryAction.NONE : EntryAction.JOIN;
	}

	/** The first received CLAIM owns the biome; later CLAIMs cannot replace that owner. */
	public static Claim receiveClaim(Claim current, String sender) {
		if (current == null || current.done()) return new Claim(sender, false);
		if (current.owner().equalsIgnoreCase("party") && sender != null) return new Claim(sender, false);
		return current;
	}

	/** DONE updates completion while retaining the original claimer. */
	public static Claim receiveDone(Claim current, String sender) {
		return new Claim(current == null ? sender : current.owner(), true);
	}
}

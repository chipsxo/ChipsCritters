package dev.chipsxo.chipscritters;

import com.mojang.authlib.GameProfile;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public final class ClaimsManager {
	private record Claim(String owner, boolean done) { }
	private static final PartyDoneLedger DONE_SENT = new PartyDoneLedger();
	private static final Map<Biome, Claim> CLAIMS = new EnumMap<>(Biome.class);
	private static final Map<Biome, Set<String>> JOINERS = new EnumMap<>(Biome.class);
	private static final Set<Biome> ENTERED_THIS_LOBBY = EnumSet.noneOf(Biome.class);
	
	private static final Set<Biome> LOCAL_DONE = EnumSet.noneOf(Biome.class);
	private enum PartyPresence { UNKNOWN, PRESENT, ABSENT }
	private static PartyPresence partyPresence = PartyPresence.UNKNOWN;
	private static long modPartyCommandUntilMillis;
	private static final long MOD_PARTY_REPLY_WINDOW_MILLIS = 3500L;
	private static final Pattern NO_PARTY_PATTERN = Pattern.compile("(?i)^you are not in a party right now[.!]?$");
	private static boolean completionReminderShown;
	private static String lobbyKey = "";

	private ClaimsManager() { }

	public static void receive(Component message, GameProfile sender) {
		if (!ChipsCrittersClient.isInCritterSafari() || !ChipsConfig.get().partyClaims) return;
		PartyClaimFlow.PartyMessage parsed=PartyClaimFlow.parseMessage(message.getString());
		if(parsed==null) return;
		partyPresence = PartyPresence.PRESENT;
		Biome biome=parsed.biome();
		if (biome == null) return;
		String owner = sender == null ? "party" : sender.name();
		String action = parsed.action();
		if (action.equals("JOIN")) {
			if (sender != null) JOINERS.computeIfAbsent(biome, ignored -> new HashSet<>()).add(owner);
			Minecraft client = Minecraft.getInstance();
			if (sender != null && client.player != null) client.player.sendOverlayMessage(Component.literal(
					"[ChipsCritters] " + owner + " joined " + biome.displayName() + "."));
			return;
		}
		Claim current = CLAIMS.get(biome);
		if (action.equals("DONE")) {
			PartyClaimFlow.Claim updated = PartyClaimFlow.receiveDone(toFlowClaim(current), owner);
			CLAIMS.put(biome, fromFlowClaim(updated));
			maybeShowCompletionReminder();
		} else {
			PartyClaimFlow.Claim updated = PartyClaimFlow.receiveClaim(toFlowClaim(current), owner);
			if (current == null || current.done) {
				CLAIMS.put(biome, fromFlowClaim(updated));
				completionReminderShown = false;
			} else if (updated != null && !updated.owner().equals(current.owner)) {
				CLAIMS.put(biome, fromFlowClaim(updated));
			}
		}
	}

	/** Called only on a real gate/biome-area entry, never when a hotspot message merely loads a route. */
	public static void enterBiome(Biome biome) {
		if (!ChipsCrittersClient.isInCritterSafari() || biome == null || !ENTERED_THIS_LOBBY.add(biome)) return;
		Minecraft client = Minecraft.getInstance();
		String owner = client.player == null ? "you" : client.player.getGameProfile().name();
		Claim current = CLAIMS.get(biome);
		PartyClaimFlow.EntryAction action = PartyClaimFlow.entryAction(true, false, owner, toFlowClaim(current));
		if (action != PartyClaimFlow.EntryAction.NONE && ChipsConfig.get().partyClaims) {
			if (partyPresence == PartyPresence.PRESENT) sendEntryClaim(biome, owner);
			else if (partyPresence == PartyPresence.UNKNOWN) {
				// Send the claim immediately; the server rejects pc locally when solo.
				// A short-lived response filter records that solo state and hides its notice.
				sendEntryClaim(biome, owner);
			}
		}
		if (client.player != null) client.player.sendOverlayMessage(Component.literal(action == PartyClaimFlow.EntryAction.JOIN
				? "[ChipsCritters] Joining " + current.owner + " in " + biome.displayName() + "."
				: "[ChipsCritters] Claimed " + biome.displayName() + " by entering its gate."));
	}

	/** Returns true only for the no-party notice caused by our own party-list probe. */
	public static boolean handleSystemMessage(String rawMessage) {
		String text = rawMessage == null ? "" : rawMessage.replaceAll("§.", "").trim();
		long now = System.currentTimeMillis();
		if (now > modPartyCommandUntilMillis) return false;
		if (NO_PARTY_PATTERN.matcher(text).matches()) {
			partyPresence = PartyPresence.ABSENT;
			return true;
		}
		return false;
	}

	private static void sendEntryClaim(Biome biome, String owner) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.getConnection() == null) return;
		Claim current = CLAIMS.get(biome);
		PartyClaimFlow.EntryAction action = PartyClaimFlow.entryAction(true, false, owner, toFlowClaim(current));
		if (action == PartyClaimFlow.EntryAction.NONE) return;
		modPartyCommandUntilMillis = System.currentTimeMillis() + MOD_PARTY_REPLY_WINDOW_MILLIS;
		partyPresence=PartyPresence.PRESENT;
		client.getConnection().sendCommand("pc [CC] " + action.name() + " " + biome.name());
		if (action == PartyClaimFlow.EntryAction.JOIN) JOINERS.computeIfAbsent(biome, ignored -> new HashSet<>()).add(owner);
	}

	public static void done(Biome biome) { done(biome, false); }
	public static void done(Biome biome, boolean clear) {
		if (!ChipsCrittersClient.isInCritterSafari()) return;
		Minecraft client = Minecraft.getInstance();
		String owner = client.player == null ? "you" : client.player.getGameProfile().name();
		Claim current = CLAIMS.get(biome);
		if (current == null || !current.done) {
			CLAIMS.put(biome, fromFlowClaim(PartyClaimFlow.receiveDone(toFlowClaim(current), owner)));
		}
		LOCAL_DONE.add(biome);
		if (DONE_SENT.shouldSend(biome, ChipsConfig.get().partyClaims, client.getConnection() != null,
				partyPresence == PartyPresence.PRESENT)) {
			modPartyCommandUntilMillis = System.currentTimeMillis() + MOD_PARTY_REPLY_WINDOW_MILLIS;
			client.getConnection().sendCommand("pc [CC] DONE " + biome.name() + " - " + biome.displayName() + (clear ? " Biome is clear" : " Biome is complete"));
		}
		maybeShowCompletionReminder();
	}

	public static String unclaimedText() {
		StringBuilder result = new StringBuilder();
		for (Biome biome : Biome.values()) {
			Claim claim = CLAIMS.get(biome);
			if (claim == null && !ENTERED_THIS_LOBBY.contains(biome)) {
				if (result.length() > 0) result.append("  ");
				result.append(biome.displayName());
			}
		}
		return result.length() == 0 ? "none" : result.toString();
	}

	public static String partySummaryText() {
		int claimed = 0;
		int done = 0;
		for (Biome biome : Biome.values()) {
			Claim claim = CLAIMS.get(biome);
			if (claim != null || ENTERED_THIS_LOBBY.contains(biome)) claimed++;
			if ((claim != null && claim.done) || LOCAL_DONE.contains(biome)) done++;
		}
		return "Party: " + claimed + "/4 claimed  •  " + done + "/4 done";
	}

	public static String biomeRosterText() {
		StringBuilder result = new StringBuilder("Biomes: ");
		for (Biome biome : Biome.values()) {
			if (result.length() > 8) result.append(" | ");
			Claim claim = CLAIMS.get(biome);
			boolean localOnly = claim == null && ENTERED_THIS_LOBBY.contains(biome);
			result.append(biome.displayName()).append(": ").append(claim == null ? (localOnly ? "you" : "unclaimed")
					: claim.owner + (claim.done || LOCAL_DONE.contains(biome) ? " done" : ""));
			Set<String> joined = JOINERS.get(biome);
			if (joined != null && !joined.isEmpty()) result.append(" (+").append(String.join(",", joined)).append(")");
		}
		return result.toString();
	}

	public static String nearestUnfinished(Vec3 position) {
		Biome nearest = nearestUnfinishedBiome(position);
		return nearest == null ? "no claimed unfinished biome" : nearest.displayName() + " (" + entryOwner(nearest) + ")";
	}

	public static Biome nearestUnfinishedBiome(Vec3 position) {
		Minecraft client = Minecraft.getInstance();
		String localName = client.player == null ? "" : client.player.getGameProfile().name();
		Biome nearest = null;
		double best = Double.POSITIVE_INFINITY;
		for (Map.Entry<Biome, Claim> entry : CLAIMS.entrySet()) {
			Claim claim = entry.getValue();
			if (claim.done || claim.owner.equalsIgnoreCase(localName)) continue;
			double distance = position.distanceTo(entry.getKey().gate());
			if (client.level != null) {
				for (AbstractClientPlayer teammate : client.level.players()) {
					if (teammate.getGameProfile().name().equalsIgnoreCase(claim.owner)) {
						distance = position.distanceTo(teammate.position());
						break;
					}
				}
			}
			if (distance < best) {
				best = distance;
				nearest = entry.getKey();
			}
		}
		return nearest;
	}

	public static boolean isClaimedByOther(Biome biome) {
		Claim claim = CLAIMS.get(biome);
		if (claim == null) return false;
		Minecraft client = Minecraft.getInstance();
		String localName = client.player == null ? "" : client.player.getGameProfile().name();
		return !claim.owner.equalsIgnoreCase(localName);
	}

	public static Biome nearestUnclaimedBiome(Vec3 position, Biome excluded) {
		Biome nearest = null;
		double best = Double.POSITIVE_INFINITY;
		for (Biome biome : Biome.values()) {
			if (biome == excluded || CLAIMS.containsKey(biome) || ENTERED_THIS_LOBBY.contains(biome)) continue;
			double distance = SafariGraph.get().travelDistance(position, biome.gate());
			if (distance < best) {
				best = distance;
				nearest = biome;
			}
		}
		return nearest;
	}

	public static String ownerOf(Biome biome) { return entryOwner(biome); }

	public static int doneBiomeCount() {
		int count = 0;
		for (Biome biome : Biome.values()) {
			Claim claim = CLAIMS.get(biome);
			if ((claim != null && claim.done) || LOCAL_DONE.contains(biome)) count++;
		}
		return count;
	}

	public static void onNewRunStarted() {
		DONE_SENT.newRun();
		CLAIMS.clear(); JOINERS.clear(); ENTERED_THIS_LOBBY.clear(); LOCAL_DONE.clear();
		completionReminderShown = false;
	}

	public static void onLobbyChanged(String nextLobbyKey) {
		String normalized = nextLobbyKey == null ? "" : nextLobbyKey;
		if (normalized.isEmpty()) return;
		if (normalized.equals(lobbyKey)) return;
		if (normalized.isEmpty() || !lobbyKey.isEmpty()) resetState();
		lobbyKey = normalized;
	}

	public static void reset() {
		resetState();
		lobbyKey = "";
	}

	private static void resetState() {
		CLAIMS.clear(); JOINERS.clear(); ENTERED_THIS_LOBBY.clear(); DONE_SENT.clear();
		LOCAL_DONE.clear(); completionReminderShown = false;
		partyPresence = PartyPresence.UNKNOWN; modPartyCommandUntilMillis = 0L;
	}

	private static PartyClaimFlow.Claim toFlowClaim(Claim claim) {
		return claim == null ? null : new PartyClaimFlow.Claim(claim.owner, claim.done);
	}

	private static Claim fromFlowClaim(PartyClaimFlow.Claim claim) {
		return claim == null ? null : new Claim(claim.owner(), claim.done());
	}

	public static void partyLeaderReminderEnabled() {
		completionReminderShown = false;
		maybeShowCompletionReminder();
	}

	private static void maybeShowCompletionReminder() {
		if (completionReminderShown || !ChipsConfig.get().partyLeaderWarpReminder) return;
		for (Biome biome : Biome.values()) {
			Claim claim = CLAIMS.get(biome);
			if (claim == null || !claim.done) return;
		}
		completionReminderShown = true;
		WarpSafariReminder.show();
	}

	private static String entryOwner(Biome biome) {
		Claim claim = CLAIMS.get(biome);
		return claim == null ? (ENTERED_THIS_LOBBY.contains(biome) ? "you" : "unclaimed") : claim.owner;
	}
}

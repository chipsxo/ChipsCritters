package dev.chipsxo.chipscritters;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.phys.Vec3;

/** Optional HTTPS sync for route-stop claims. It never uses Minecraft chat. */
public final class WaypointRelay {
	private static final String DEFAULT_ENDPOINT = "";
	private static String endpoint(ChipsConfig c) { return normalizeEndpoint(c.relayUrl == null || c.relayUrl.isBlank() ? DEFAULT_ENDPOINT : c.relayUrl); }
	private static final Gson GSON = new GsonBuilder().create();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private static final String ORIGIN = UUID.randomUUID().toString();
	private static final AtomicLong CURSOR = new AtomicLong();
	private static volatile long generation;
	private static volatile long runSequenceFloor;
	private static final ConcurrentLinkedQueue<ClaimEvent> INBOX = new ConcurrentLinkedQueue<>();
	private static final Set<Biome> LOCAL_REPLAY=java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static int restoreLocal(Biome biome){LOCAL_REPLAY.add(biome);var previous=CLAIMED.remove(biome);return previous==null?0:previous.size();}
    static boolean replayLocal(Biome biome){return LOCAL_REPLAY.contains(biome);}
	private static final Map<Biome, Set<String>> CLAIMED = new EnumMap<>(Biome.class);
	private static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(task -> {
		Thread thread = new Thread(task, "ChipsCritters-waypoint-relay");
		thread.setDaemon(true);
		return thread;
	});
	private static volatile String state = "off";
	private static volatile boolean enabledForSession;
	private static ScheduledFuture<?> pollTask;
	private static String activeEndpoint = "";
	private static String activeRoom = "";
	private static volatile String lobbyRoom = "";

	private WaypointRelay() { }

	public static synchronized void updateConfiguration() {
		ChipsConfig config = ChipsConfig.get();
		String endpoint = endpoint(config);
		if (enabledForSession && config.relayEnabled && endpoint.equals(activeEndpoint) && lobbyRoom.equals(activeRoom) && ChipsCrittersClient.isInCritterSafari()) return;
		long session = ++generation;
		if (pollTask != null) pollTask.cancel(false);
		pollTask = null;
		enabledForSession = false;
		if (!config.relayEnabled) { CLAIMED.clear(); state = "off"; return; }
		if (endpoint.isBlank()) { CLAIMED.clear(); state = "no relay endpoint configured"; return; }
		if (!ChipsCrittersClient.isInCritterSafari()) { state = "waiting for Critter Safari"; return; }
		if (!validEndpoint(endpoint)) { CLAIMED.clear(); state = "needs HTTPS endpoint"; return; }
		String room = lobbyRoom;
		if (room.isBlank()) { state = "waiting for private lobby info"; return; }
		if (!room.equals(activeRoom) || !endpoint.equals(activeEndpoint)) CLAIMED.clear();
		activeEndpoint = endpoint;
		activeRoom = room;
		CURSOR.set(0L);
		INBOX.clear();
		enabledForSession = true;
		state = "connecting";
		var player=net.minecraft.client.Minecraft.getInstance().player;
		if (!config.relayPrivacyNoticeShown && player!=null) {
			player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[ChipsCritters] Relay shares a hashed lobby ID, random client ID, biome and waypoint coordinates. Turn it off under /sr → Tools → Relay."));
			config.relayPrivacyNoticeShown=true;
			config.save();
		}
		pollTask = EXECUTOR.scheduleWithFixedDelay(() -> poll(endpoint, room, session), 0L, 2L, TimeUnit.SECONDS);
	}

	public static synchronized void setLobbyInfo(String server, String gametype, String mode, String map) {
		String normalizedMode = mode == null ? "" : mode.trim().toLowerCase(java.util.Locale.ROOT);
		String normalizedGametype = gametype == null ? "" : gametype.trim().toLowerCase(java.util.Locale.ROOT);
		String normalizedServer = server == null ? "" : server.trim().toLowerCase(java.util.Locale.ROOT);
		boolean safari = normalizedGametype.equals("skyblock") && normalizedMode.equals("safari");
		boolean validServerId = normalizedServer.matches("[a-z0-9_-]{1,64}");
		// Hypixel's lobby server ID is the common identifier shared by everyone in this instance.
		// Mode/map strings can vary between locraw responses, so they are used only to confirm Safari.
		String room = safari && validServerId
				? hashLobby("chipscritters:lobby:v1|" + normalizedServer + "|critter_safari") : "";
		if (!room.equals(lobbyRoom)) {
			lobbyRoom = room;
			runSequenceFloor = 0L;
			CLAIMED.clear();
			CURSOR.set(0L);
			INBOX.clear();
			WaypointRunLedger.onLobbyChanged(room);
			ClaimsManager.onLobbyChanged(room);
		}
		updateConfiguration();
	}

	public static synchronized void clearLobbyInfo() {
		generation++;
		lobbyRoom = "";
		WaypointRunLedger.onLobbyChanged("");
		ClaimsManager.onLobbyChanged("");
		if (pollTask != null) pollTask.cancel(false);
		pollTask = null;
		enabledForSession = false;
		CLAIMED.clear();
		CURSOR.set(0L);
		INBOX.clear();
		state = ChipsConfig.get().relayEnabled ? "waiting for private lobby info" : "off";
	}

	static void clearReplayForNewRun(){LOCAL_REPLAY.clear();}
	public static synchronized void newRunStarted() {
        clearReplayForNewRun();
        long floor = CURSOR.get();
        stop();
        CLAIMED.clear();
        INBOX.clear();
        runSequenceFloor = floor;
        updateConfiguration();
    }
    public static synchronized void stop() {
		generation++;
		if (pollTask != null) pollTask.cancel(false);
		pollTask = null;
		enabledForSession = false;
		INBOX.clear();
		state = "off";
	}

	public static String statusText() { return endpoint(ChipsConfig.get()).isBlank() ? "no relay endpoint configured" : state; }
	public static boolean enabled() { return enabledForSession; }
	public static String lobbySessionKey() { return lobbyRoom; }

	private static volatile boolean searchClaimsPaused;
    static void pauseSearchClaims(boolean value){searchClaimsPaused=value;}
	public static void waypointReached(Biome biome, Vec3 position) {
        if(searchClaimsPaused)return;
		if (!enabledForSession || LOCAL_REPLAY.contains(biome)) return;
		long session = generation;
		CLAIMED.computeIfAbsent(biome, key -> new HashSet<>()).add(waypointKey(position));
		String endpoint = activeEndpoint;
		ClaimRequest payload = new ClaimRequest(activeRoom, ORIGIN, biome.name(), round(position.x), round(position.y), round(position.z));
		EXECUTOR.execute(() -> sendClaim(endpoint, payload, session, 0));
	}

	/** Drained on the Minecraft client thread so route state is never mutated from network threads. */
	public static void tick() {
		if (!ChipsCrittersClient.isInCritterSafari()) {
			if (enabledForSession) stop();
			INBOX.clear();
			return;
		}
		ClaimEvent event;
		while ((event = INBOX.poll()) != null) {
			if (event.sessionGeneration != generation || !enabledForSession) continue;
			Biome biome = Biome.parse(event.biome);
			if (biome == null || !Double.isFinite(event.x) || !Double.isFinite(event.y) || !Double.isFinite(event.z)) continue;
			if(LOCAL_REPLAY.contains(biome))continue;
			Vec3 position = new Vec3(event.x, event.y, event.z);
			CLAIMED.computeIfAbsent(biome, key -> new HashSet<>()).add(waypointKey(position));
			RouteController.onRelayWaypointClaim(biome, position);
		}
	}

	public static List<Vec3> filterAlreadyClaimed(Biome biome, List<Vec3> planned) {
		if (LOCAL_REPLAY.contains(biome))return planned;
		if (!enabledForSession || lobbyRoom.isBlank() || !lobbyRoom.equals(activeRoom)) return planned;
		Set<String> claimed = CLAIMED.get(biome);
		if (claimed == null || claimed.isEmpty() || planned.isEmpty()) return planned;
		List<Vec3> remaining = new ArrayList<>(planned.size());
		for (int index = 0; index < planned.size(); index++) {
			Vec3 stop = planned.get(index);
			boolean gate = index == 0 && stop.distanceToSqr(biome.gate()) <= 4.0;
			boolean cavernConnector = biome == Biome.CAVERN && SafariData.isCavernWaterfallConnector(stop);
			if ((biome==Biome.HAUNTED && SafariData.GIMMIEGOLD.equals(stop)) || gate || cavernConnector || !claimed.contains(waypointKey(stop))) remaining.add(stop);
		}
		return List.copyOf(remaining);
	}

	public static boolean validEndpoint(String endpoint) {
		try {
			URI uri = URI.create(endpoint);
			String scheme = uri.getScheme();
			if (scheme == null || uri.getHost() == null) return false;
			boolean loopback = "localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
			return ("https".equalsIgnoreCase(scheme) || (loopback && "http".equalsIgnoreCase(scheme)))
					&& uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null;
		} catch (IllegalArgumentException exception) { return false; }
	}

	private static String hashLobby(String identity) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(digest, 0, 16);
		} catch (java.security.NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
	}

	private static String normalizeEndpoint(String endpoint) {
		if (endpoint == null) return "";
		String normalized = endpoint.trim();
		while (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
		return normalized;
	}

	private static double round(double value) { return Math.rint(value * 10.0) / 10.0; }

	private static String waypointKey(Vec3 position) {
		return String.format(java.util.Locale.ROOT, "%.1f,%.1f,%.1f", round(position.x), round(position.y), round(position.z));
	}

	private static void sendClaim(String endpoint, ClaimRequest payload, long session, int attempt) {
        if(searchClaimsPaused){if(session==generation && enabledForSession)EXECUTOR.schedule(()->sendClaim(endpoint,payload,session,attempt),1,TimeUnit.SECONDS);return;}
		if (session != generation || !enabledForSession || LOCAL_REPLAY.contains(Biome.parse(payload.biome))) return;
		try {
			HttpResponse<String> response = HTTP.send(request(endpoint, "/v1/claim", payload), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (session == generation && enabledForSession && endpoint.equals(activeEndpoint) && payload.room.equals(activeRoom)) {
				if (response.statusCode() == 200 || response.statusCode() == 201) state = "connected";
				else { state = "relay error " + response.statusCode(); retryClaim(endpoint, payload, session, attempt); }
			}
		} catch (Exception exception) {
			if (session == generation) { state = "offline; retrying"; retryClaim(endpoint, payload, session, attempt); }
		}
	}

	private static void retryClaim(String endpoint, ClaimRequest payload, long session, int attempt) {
		if (attempt < 3) EXECUTOR.schedule(() -> sendClaim(endpoint, payload, session, attempt + 1),
				2L << attempt, TimeUnit.SECONDS);
	}

	private static void poll(String endpoint, String room, long session) {
		if (!enabledForSession || session != generation || !endpoint.equals(activeEndpoint) || !room.equals(activeRoom)) return;
		try {
			SyncRequest payload = new SyncRequest(room, ORIGIN, CURSOR.get());
			HttpResponse<String> response = HTTP.send(request(endpoint, "/v1/sync", payload), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
			if (session != generation) return;
			if (response.statusCode() != 200) { state = "relay error " + response.statusCode(); return; }
			SyncResponse result = GSON.fromJson(response.body(), SyncResponse.class);
			if (result == null) { state = "invalid relay response"; return; }
			synchronized (WaypointRelay.class) {
				if (session != generation || !enabledForSession || !room.equals(activeRoom)) return;
				CURSOR.accumulateAndGet(Math.max(0L, result.latestSequence), Math::max);
				if (result.events != null) for (ClaimEvent event : result.events) if (event != null && event.sequence > runSequenceFloor && !ORIGIN.equals(event.origin)) {
					event.sessionGeneration = session;
					INBOX.add(event);
				}
				state = "connected";
			}
		} catch (Exception exception) { if (session == generation) state = "offline; retrying"; }
	}

	private static HttpRequest request(String endpoint, String path, Object payload) {
		String url = endpoint + path;
		return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8))
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.header("Cache-Control", "no-store")
				.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload), StandardCharsets.UTF_8)).build();
	}

	private static final class ClaimRequest {
		final String room;
		final String origin;
		final String biome;
		final double x, y, z;
		ClaimRequest(String room, String origin, String biome, double x, double y, double z) {
			this.room = room; this.origin = origin; this.biome = biome; this.x = x; this.y = y; this.z = z;
		}
	}

	private static final class SyncRequest {
		final String room;
		final String origin;
		final long since;
		SyncRequest(String room, String origin, long since) { this.room = room; this.origin = origin; this.since = since; }
	}

	private static final class ClaimEvent {
		transient long sessionGeneration;
		long sequence;
		String origin;
		String biome;
		double x, y, z;
	}

	private static final class SyncResponse {
		long latestSequence;
		List<ClaimEvent> events;
	}
}

package dev.chipsxo.chipscritters;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.phys.Vec3;

public final class ChipsConfig {
	public enum Mode { MONEY, UNIQUE, FULL_CLEAR }
	public enum CompanionSetting { AUTO, ON, OFF }
	public static final class ModeProfile {
		public double detourBlocks = 5.0;
		public boolean oneWaypointAtATime = true;
		public boolean troodonLabels = true;
		public boolean showDistanceLabels;
		public boolean hideReachedWaypoints = true;
		public boolean showWaypointSource;
	}
	private static final class StopExport {
		public Map<String, List<double[]>> learnedStops = new LinkedHashMap<>();
		public Map<String, String> learnedStopLabels = new LinkedHashMap<>();
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("chipscritters.json");
	private static final Path STOP_EXPORT_FILE = FabricLoader.getInstance().getConfigDir().resolve("chipscritters-waypoints.json");
	private static ChipsConfig instance;

	public Mode mode = Mode.MONEY;
	public CompanionSetting companion = CompanionSetting.AUTO;
	public boolean partyClaims = true;
	public boolean partyLeaderWarpReminder = false;
	public boolean routeRendering = true;
	public boolean waypointRendering = true;
	public boolean troodonLabels = true;
	public boolean oneWaypointAtATime = true;
	public boolean nearWaypointPing = true;
	public double pingVolume = 0.8;
	public int arrivalRadius = 4;
	public boolean showHudTimer = true;
	public boolean showRouteProgress = true;
	public boolean showRouteHints = true;
	public boolean showWaypointPanel = true;
	public boolean showPartyStatus = true;
	public boolean showWaypointSource;
	public boolean showDistanceLabels;
	public boolean hideReachedWaypoints = true;
	public double ribbonOpacity = 0.9;
	public double ribbonThickness = 0.12;
	public int routeColorPreset;
	public int warpReminderSeconds = 4;
	public boolean relayEnabled = false;
	public boolean relayPrivacyNoticeShown;
	public String relayUrl = "";
	public int hudX = -1;
	public int hudY = -1;
	private int waypointLayoutVersion;
	public int waypointHudX = -1;
	public int waypointHudY = -1;
	public double waypointHudScale = 1.0;
	public double detourBlocks = 5.0;
	public Map<String, ModeProfile> modeProfiles = new LinkedHashMap<>();
	public boolean routeUpdatesEnabled = true;
	public boolean routeUpdateNoticeShown;
	public String routeDataUrl = ReleaseLinks.ROUTES;
    private int routeEndpointDefaultsVersion;
	public boolean snapHudToGrid = true;
	public double bazaarFeePercent = 1.0;
	public boolean huntingBoxColors = true;
	public boolean safariLoadingTips = true;
    public double airshipBannerPosition = -1; // Local vertical fraction; default keeps automatic 0.1.62 placement.
    public boolean uniqueHideonwallRoutes = true;
	public double minimumShardAdvantagePercent = 0.0;
    public double minimumShardAdvantageCoins = ShardProfitMinimum.DEFAULT_COINS;
	public String morningDewProductId = "";
	public Map<String, String> shardProductIds = new LinkedHashMap<>();
	public Map<String, List<double[]>> learnedStops = new LinkedHashMap<>();
	public Map<String, String> learnedStopLabels = new LinkedHashMap<>();

	public static ChipsConfig get() {
		if (instance == null) instance = load();
		return instance;
	}

	public static void reload() {
		instance = load();
	}

	private static ChipsConfig load() {
		try {
			if (Files.exists(FILE)) {
				String raw=Files.readString(FILE);
                var json=com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
                boolean retiredFastest=RetiredModeMigration.migrate(json);
                ShardProfitMinimum.applyDefault(json);
                ChipsConfig loaded = GSON.fromJson(json, ChipsConfig.class);
                if(retiredFastest)ChipsCrittersClient.LOGGER.info("Retired Fastest selection migrated to Money; archived FASTEST profile/history/PBs kept separately.");
				if (loaded != null) {
					if (loaded.mode == null) loaded.mode = Mode.MONEY;
					if (loaded.companion == null) loaded.companion = CompanionSetting.AUTO;
					if (loaded.shardProductIds == null) loaded.shardProductIds = new LinkedHashMap<>();
					if (loaded.learnedStops == null) loaded.learnedStops = new LinkedHashMap<>();
					if (loaded.learnedStopLabels == null) loaded.learnedStopLabels = new LinkedHashMap<>();
					if (loaded.modeProfiles == null) loaded.modeProfiles = new LinkedHashMap<>();
					if (loaded.waypointLayoutVersion < 1) {
						loaded.waypointHudX = loaded.waypointHudY = -1;
						loaded.waypointLayoutVersion = 1;
					}
					if(loaded.routeEndpointDefaultsVersion<1){if(loaded.routeDataUrl==null||loaded.routeDataUrl.isBlank())loaded.routeDataUrl=ReleaseLinks.ROUTES;loaded.routeEndpointDefaultsVersion=1;}
                    loaded.clampSettings();
					ModeProfile profile = loaded.modeProfiles.get(loaded.mode.name());
					if (profile == null) loaded.modeProfiles.put(loaded.mode.name(), loaded.currentProfile());
					else loaded.applyProfile(profile);
					loaded.save();
					return loaded;
				}
			}
		} catch (Exception exception) {
			ChipsCrittersClient.LOGGER.error("Could not read ChipsCritters config", exception);
		}
		ChipsConfig fresh = new ChipsConfig();
		fresh.waypointLayoutVersion = 1;fresh.routeEndpointDefaultsVersion=1;
		fresh.save();
		return fresh;
	}

	public void save() {
		try {
			if (modeProfiles == null) modeProfiles = new LinkedHashMap<>();
			if (mode != null) modeProfiles.put(mode.name(), currentProfile());
			clampSettings();
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, GSON.toJson(this));
		} catch (IOException exception) {
			ChipsCrittersClient.LOGGER.error("Could not save ChipsCritters config", exception);
		}
	}

	public void switchMode(Mode nextMode) {
		if (nextMode == null || nextMode == mode) return;
		if (modeProfiles == null) modeProfiles = new LinkedHashMap<>();
		modeProfiles.put(mode.name(), currentProfile());
		mode = nextMode;
		ModeProfile next = modeProfiles.computeIfAbsent(nextMode.name(), key -> new ModeProfile());
		applyProfile(next);
		save();
	}

	private ModeProfile currentProfile() {
		ModeProfile profile = new ModeProfile();
		profile.detourBlocks = detourBlocks;
		profile.oneWaypointAtATime = oneWaypointAtATime;
		profile.troodonLabels = troodonLabels;
		profile.showDistanceLabels = showDistanceLabels;
		profile.hideReachedWaypoints = hideReachedWaypoints;
		profile.showWaypointSource = showWaypointSource;
		return profile;
	}

	private void applyProfile(ModeProfile profile) {
		if (profile == null) return;
		detourBlocks = profile.detourBlocks;
		oneWaypointAtATime = profile.oneWaypointAtATime;
		troodonLabels = profile.troodonLabels;
		showDistanceLabels = profile.showDistanceLabels;
		hideReachedWaypoints = profile.hideReachedWaypoints;
		showWaypointSource = profile.showWaypointSource;
		clampSettings();
	}

	private void clampSettings() {
        minimumShardAdvantageCoins=ShardProfitMinimum.sanitize(minimumShardAdvantageCoins);
        airshipBannerPosition=BannerPosition.sanitize(airshipBannerPosition);
		if (!Double.isFinite(detourBlocks)) detourBlocks = 5.0;
		if (!Double.isFinite(pingVolume)) pingVolume = 0.8;
		if (!Double.isFinite(ribbonOpacity)) ribbonOpacity = 0.9;
		if (!Double.isFinite(ribbonThickness)) ribbonThickness = 0.12;
		if (!Double.isFinite(waypointHudScale)) waypointHudScale = 1.0;
		detourBlocks = Math.max(0.0, Math.min(20.0, detourBlocks));
		pingVolume = Math.max(0.0, Math.min(1.0, pingVolume));
		ribbonOpacity = Math.max(0.15, Math.min(1.0, ribbonOpacity));
		ribbonThickness = Math.max(0.05, Math.min(0.3, ribbonThickness));
		waypointHudScale = Math.max(0.6, Math.min(1.5, waypointHudScale));
		arrivalRadius = Math.max(1, Math.min(8, arrivalRadius));
		routeColorPreset = Math.floorMod(routeColorPreset, 4);
		warpReminderSeconds = Math.max(2, Math.min(10, warpReminderSeconds));
	}

	public boolean companionMode() {
		return switch (companion) {
			case ON -> true;
			case OFF -> false;
			case AUTO -> FabricLoader.getInstance().isModLoaded("crittermod");
		};
	}

	public List<Vec3> learnedStops(Biome biome) {
		List<double[]> saved = learnedStops.get(biome.name());
		if (saved == null) return List.of();
		List<Vec3> result = new ArrayList<>();
		for (double[] point : saved) {
			if (point != null && point.length == 3) result.add(new Vec3(point[0], point[1], point[2]));
		}
		return result;
	}

	public void addLearnedStop(Biome biome, Vec3 position) {
		addLearnedStop(biome, position, null);
	}

	public void addLearnedStop(Biome biome, Vec3 position, String label) {
		double[] point = rounded(position);
		learnedStops.computeIfAbsent(biome.name(), key -> new ArrayList<>()).add(point);
		if (label != null && !label.isBlank()) learnedStopLabels.put(stopKey(biome, point), label.trim());
		save();
	}

	public boolean renameLearnedStop(Biome biome, Vec3 position, String label) {
		double[] point = rounded(position);
		boolean exists = learnedStops(biome).stream().anyMatch(saved -> saved.distanceTo(new Vec3(point[0], point[1], point[2])) < 0.01);
		if (!exists) return false;
		String key = stopKey(biome, point);
		if (label == null || label.isBlank()) learnedStopLabels.remove(key);
		else learnedStopLabels.put(key, label.trim());
		save();
		return true;
	}

	public boolean removeLearnedStop(Biome biome, Vec3 position) {
		List<double[]> saved = learnedStops.get(biome.name());
		if (saved == null) return false;
		double[] point = rounded(position);
		boolean removed = saved.removeIf(candidate -> candidate != null && candidate.length == 3
				&& Math.abs(candidate[0] - point[0]) < 0.01 && Math.abs(candidate[1] - point[1]) < 0.01
				&& Math.abs(candidate[2] - point[2]) < 0.01);
		if (removed) {
			learnedStopLabels.remove(stopKey(biome, point));
			if (saved.isEmpty()) learnedStops.remove(biome.name());
			save();
		}
		return removed;
	}

	public boolean isLearnedStop(Biome biome, Vec3 position) {
		return learnedStops(biome).stream().anyMatch(point -> point.distanceToSqr(position) < 0.01);
	}

	public String exportLearnedStops() throws IOException {
		StopExport data = new StopExport();
		data.learnedStops.putAll(learnedStops);
		data.learnedStopLabels.putAll(learnedStopLabels);
		Files.createDirectories(STOP_EXPORT_FILE.getParent());
		Files.writeString(STOP_EXPORT_FILE, GSON.toJson(data));
		return STOP_EXPORT_FILE.toString();
	}

	public int importLearnedStops() throws IOException {
		if (!Files.exists(STOP_EXPORT_FILE)) throw new IOException("Waypoint export file was not found");
		StopExport data = GSON.fromJson(Files.readString(STOP_EXPORT_FILE), StopExport.class);
		if (data == null || data.learnedStops == null) return 0;
		if (data.learnedStopLabels == null) data.learnedStopLabels = new LinkedHashMap<>();
		int added = 0;
		for (Map.Entry<String, List<double[]>> entry : data.learnedStops.entrySet()) {
			Biome biome;
			try { biome = Biome.valueOf(entry.getKey()); } catch (Exception ignored) { continue; }
			if (entry.getValue() == null) continue;
			List<double[]> target = learnedStops.computeIfAbsent(biome.name(), key -> new ArrayList<>());
			for (double[] raw : entry.getValue()) {
				if (raw == null || raw.length != 3 || !Double.isFinite(raw[0]) || !Double.isFinite(raw[1]) || !Double.isFinite(raw[2])
						|| Math.abs(raw[0]) > 30_000_000.0 || Math.abs(raw[1]) > 30_000_000.0 || Math.abs(raw[2]) > 30_000_000.0) continue;
				double[] point = rounded(new Vec3(raw[0], raw[1], raw[2]));
				if (target.stream().noneMatch(existing -> existing != null && existing.length == 3
						&& Math.abs(existing[0] - point[0]) < 0.01 && Math.abs(existing[1] - point[1]) < 0.01
						&& Math.abs(existing[2] - point[2]) < 0.01)) {
					target.add(point);
					added++;
				}
				String key = stopKey(biome, point);
				String label = data.learnedStopLabels.get(key);
				if (label != null && !label.isBlank()) learnedStopLabels.putIfAbsent(key, label);
			}
		}
		save();
		return added;
	}

	public static void resetAll() {
		instance = new ChipsConfig();
		instance.save();
	}

	public String learnedStopLabel(Biome biome, Vec3 position) {
		return learnedStopLabels.get(stopKey(biome, rounded(position)));
	}

	private static double[] rounded(Vec3 position) {
		return new double[]{Math.rint(position.x * 10.0) / 10.0, Math.rint(position.y * 10.0) / 10.0, Math.rint(position.z * 10.0) / 10.0};
	}

	private static String stopKey(Biome biome, double[] point) {
		return String.format(Locale.ROOT, "%s:%.1f,%.1f,%.1f", biome.name(), point[0], point[1], point[2]);
	}
}

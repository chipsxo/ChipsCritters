package dev.chipsxo.chipscritters;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.phys.Vec3;

public final class SafariData {
	public static final double ICY_OCEAN_ADVANCE_Y = 45.0;
	public static final double CAVERN_WATERFALL_RESUME_Y = 82.0;
	public record CavernLadder(String name, Vec3 bottom, Vec3 top, boolean twoWay, Vec3 before) { }
	public record RouteTemplate(List<Vec3> stops, List<Vec3> houseSweep, List<Vec3> floorDropChecks,
			List<Vec3> hideyhoLocations, List<Vec3> hideonwallLocations, List<Vec3> gazerBeds,
			Vec3 ritualPoint, boolean fixedEnd, Vec3 cavernWaterfallEntry,
			List<Vec3> cavernWaterfallTopStops, List<Vec3> cavernWestPlateauStops,
			List<CavernLadder> cavernLadders) { }
	private static final Vec3 FOREST_TREE_JUMP = new Vec3(0.0, 90.0, 8.0);
	private static final Vec3 FOREST_TREE_JUMP_OFF = new Vec3(2.0, 90.0, 8.0);
	public static final Vec3 FOREST_TREE_AFTER = new Vec3(10,90,10);
	public static final Vec3 FOREST_DROP_AFTER_TREE = new Vec3(-5,78,11);
	public static final Vec3 FOREST_FLUFFLING_EXIT = new Vec3(21,52,56);
	public static final Vec3 FOREST_FLUFFLING_NEXT = new Vec3(7,50,60);
	public static final Vec3 CAVERN_DROP_START=new Vec3(-94,82,55), CAVERN_DROP_END=new Vec3(-86,60,50);
	public static final Vec3 CAVERN_AFTER_DROP=new Vec3(-106,59,37);
    public static final Vec3 CAVERN_LADDER_CAVE = new Vec3(-88,61,42);
	public static final List<Vec3> FOREST_HILL_FEET = List.of(
			new Vec3(25,52,54), new Vec3(24,53,51), new Vec3(21,55,51), new Vec3(25,57,49));
	private static final Vec3 ICY_WUMPA = new Vec3(-110.0, 80.0, -78.0);
	private static final List<Vec3> ICY_TROODONS = IcyCaptureRules.troodonWaypoints().stream()
			.map(point -> new Vec3(point.x(), point.y(), point.z())).toList();
	private static final Vec3 ICY_OCEAN_DIVE = new Vec3(-69.0, 30.0, -47.0);
	public static final Vec3 HAUNTED_ENTRANCE = new Vec3(2,78,-80);
	public static final Vec3 HAUNTED_ENTRANCE_DOOR = new Vec3(1,77,-70);
	public static final Vec3 GIMMIEGOLD = new Vec3(-2,59,-54);
	static boolean forestWaterColumn(Vec3 p) {
		return p.x >= -27.5 && p.x <= -23 && p.z >= 34 && p.z <= 36 && p.y >= 65 && p.y <= 96;
	}
	static boolean isPaintingVineEdge(Vec3 a,Vec3 b) {return a.x==18.5 && b.x==18.5 && a.z==-71.5 && b.z==-71.5 && Math.min(a.y,b.y)>=77 && Math.max(a.y,b.y)<=80;}
	static boolean isForestWaterEdge(Vec3 a,Vec3 b) { return forestWaterColumn(a) && forestWaterColumn(b); }
	public static final Vec3 CAVERN_POND_STOP = new Vec3(-76.0, 71.0, 68.0);
	public static final Vec3 CAVERN_WATERFALL_ENTRY = new Vec3(-77.0, 72.0, 67.0);
	public static final Vec3 CAVERN_WATERFALL_EXIT = new Vec3(-76.0, CAVERN_WATERFALL_RESUME_Y, 67.0);
	private static final List<Vec3> CAVERN_WATERFALL_STEPS = List.of(
			new Vec3(-77.0, 76.0, 67.0), CAVERN_WATERFALL_EXIT);
	private static final Vec3 GEMZIE_PODIUM = new Vec3(-135.0, 61.0, 49.0);
	private static final List<Vec3> CAVERN_SNOOZLES = List.of(
			new Vec3(-121.0, 39.0, 73.0), new Vec3(-113.0, 39.0, 85.0),
			new Vec3(-69.0, 39.0, 66.0), new Vec3(-96.0, 40.0, 19.0), new Vec3(-95.0, 40.0, 41.0));
	private record UnsafeDrop(Biome biome, Vec3 from, Vec3 to) { }
	// Empty until the user supplies confirmed unsafe drop coordinates.
	private static final List<UnsafeDrop> UNSAFE_DIRECT_DROPS = List.of();
	private static final Map<Biome, List<Vec3>> AREA_POINTS = new EnumMap<>(Biome.class);
	private static Map<Biome, RouteTemplate> routes;

	private SafariData() { }

	public static RouteTemplate route(Biome biome) {
		if (routes == null) routes = loadRoutes();
		return routes.getOrDefault(biome, new RouteTemplate(List.of(biome.gate()), List.of(), List.of(), List.of(), List.of(), List.of(), null, false,
				null, List.of(), List.of(), List.of()));
	}

	public static List<Vec3> cavernWaterfallPathFrom(Vec3 start) {
		List<Vec3> path = new ArrayList<>(CAVERN_WATERFALL_STEPS.size() + 1);
		path.add(start);
		int firstRemainingStep = cavernWaterfallStepIndex(start.y);
		for (int i = firstRemainingStep; i < CAVERN_WATERFALL_STEPS.size(); i++) {
			path.add(CAVERN_WATERFALL_STEPS.get(i));
		}
		return List.copyOf(path);
	}

	public static List<Vec3> cavernWaterfallSteps() {
		return CAVERN_WATERFALL_STEPS;
	}

	public static int cavernWaterfallStepIndex(double playerY) {
		return playerY < CAVERN_WATERFALL_STEPS.getFirst().y - 0.75 ? 0 : CAVERN_WATERFALL_STEPS.size() - 1;
	}

	public static List<Vec3> cavernWaterfallRouteStops() {
		List<Vec3> stops = new ArrayList<>(CAVERN_WATERFALL_STEPS.size() + 1);
		stops.add(CAVERN_WATERFALL_ENTRY);
		stops.addAll(CAVERN_WATERFALL_STEPS);
		return List.copyOf(stops);
	}

	public static String waypointInstruction(Biome biome, Vec3 position) {
		if (biome == Biome.HAUNTED && position.equals(HAUNTED_ENTRANCE)) return "ENTRANCE ROOM";
		if (isGazerBedWaypoint(biome, position)) return "GAZER BED";
		if (biome == Biome.CAVERN && position.distanceToSqr(new Vec3(-111,80,36)) < 0.01) return "SCRAPPY";
		if (biome == Biome.FOREST && position.equals(new Vec3(-39,86,58))) return "JUMP";
		if (biome == Biome.FOREST && position.equals(new Vec3(-35,68,60))) return "DROP DOWN";
		if (biome == Biome.HAUNTED && position.equals(GIMMIEGOLD)) return "GIMMIEGOLD";
		if (biome == Biome.FOREST && position.equals(FOREST_TREE_JUMP)) return "JUMP ON TREE";
		if (biome == Biome.FOREST && position.equals(FOREST_DROP_AFTER_TREE)) return "DROP DOWN";
		if (biome == Biome.FOREST && position.equals(FOREST_FLUFFLING_NEXT)) return "DROP DOWN";
		if (biome == Biome.FOREST && isForestTreeJumpOff(position)) return "JUMP ON TREE";
		if (biome == Biome.CAVERN && position.equals(CAVERN_DROP_START)) return "DROP DOWN";
		if (biome == Biome.CAVERN && position.distanceToSqr(CAVERN_POND_STOP) < 0.01) return "GO TO CAVERNFISH POND";
		if (biome == Biome.CAVERN && position.distanceToSqr(CAVERN_WATERFALL_ENTRY) < 0.01) return "WATERFALL BASE";
		if (biome == Biome.CAVERN) for (CavernLadder climb : cavernLadders()) {
			if (climb.bottom().equals(position) || climb.top().equals(position)) return climb.name();
		}
		if (isSnoozleWaypoint(biome, position)) return "SNOOZLE";
		if (isTroodonWaypoint(biome, position)) return "TROODON";
		if (isWumpaWaypoint(biome, position)) return "WUMPA";
		if (isOceanWaypoint(biome, position)) return "DIVE BELOW Y=45";
		if (isGemzieWaypoint(biome, position)) return "PLACE 3 GEMS FOR GEMZIE";
		if (biome == Biome.HAUNTED) {
			RouteTemplate haunted = route(Biome.HAUNTED);
			if (matches(haunted.hideyhoLocations(), position)) return "HIDEYHO SEARCH";
			if (matches(haunted.hideonwallLocations(), position)) {
				return position.distanceToSqr(new Vec3(17.0, 80.0, -74.0)) < 0.01
						? "CLIMB VINE TO HIDEONWALL" : "HIDEONWALL PAINTING";
			}
			if (matches(haunted.gazerBeds(), position)) return "GAZER BED — GAZER SHARD";
			boolean ritual = haunted.ritualPoint() != null && position.distanceToSqr(haunted.ritualPoint()) <= 16.0;
			if (ritual) return "RITUAL CAVE — 4 INCENSE REQUIRED";
		}
		return null;
	}

	public static boolean isRitualWaypoint(Biome biome, Vec3 position) {
		if (biome != Biome.HAUNTED) return false;
		Vec3 ritualPoint = route(Biome.HAUNTED).ritualPoint();
		return ritualPoint != null && position.distanceToSqr(ritualPoint) <= 16.0;
	}

	public static boolean isTroodonWaypoint(Biome biome, Vec3 position) {
		return biome == Biome.ICY && ICY_TROODONS.stream().anyMatch(point -> position.distanceToSqr(point) < 0.01);
	}

	public static boolean isWumpaWaypoint(Biome biome, Vec3 position) {
		return biome == Biome.ICY && position.distanceToSqr(ICY_WUMPA) < 0.01;
	}

	public static Vec3 icyWumpaWaypoint() { return ICY_WUMPA; }

	public static boolean isOceanWaypoint(Biome biome, Vec3 position) {
		return biome == Biome.ICY && position.distanceToSqr(ICY_OCEAN_DIVE) < 0.01;
	}

	public static boolean isGemzieWaypoint(Biome biome, Vec3 position) {
		return biome == Biome.CAVERN && position.distanceToSqr(GEMZIE_PODIUM) < 0.01;
	}

	public static boolean isSnoozleWaypoint(Biome biome, Vec3 position) {
		return biome == Biome.CAVERN && CAVERN_SNOOZLES.stream().anyMatch(point -> point.distanceToSqr(position) < 0.01);
	}

	public static List<Vec3> snoozleWaypoints() { return CAVERN_SNOOZLES; }

	public static List<CavernLadder> cavernLadders() { return route(Biome.CAVERN).cavernLadders(); }

	public static boolean isCavernLadderLeg(Vec3 from, Vec3 to) {
		return cavernLadders().stream().anyMatch(ladder ->
				(ladder.bottom().distanceToSqr(from) < 0.01 && ladder.top().distanceToSqr(to) < 0.01)
				|| (ladder.twoWay() && ladder.top().distanceToSqr(from) < 0.01 && ladder.bottom().distanceToSqr(to) < 0.01));
	}

	public static boolean isCavernLadderBottom(Vec3 position) {
		return cavernLadders().stream().anyMatch(ladder -> ladder.bottom().distanceToSqr(position) < 0.01);
	}

	public static boolean isFloorDropWaypoint(Biome biome, Vec3 position) {
		return floorDropTarget(biome, position) != null;
	}

	/** Returns the merged reward location represented by this route waypoint, if any. */
	public static Vec3 floorDropTarget(Biome biome, Vec3 position) {
		List<Vec3> drops = route(biome).floorDropChecks();
		for (Vec3 drop : drops) if (position.distanceToSqr(drop) < 0.01) return drop;
		for (Vec3 drop : drops) if (isFloorDropAlias(biome, position, drop)) return drop;
		return null;
	}

	/** A floor-drop stop and its route waypoint count as one stop when they are the same floor location. */
	public static boolean isFloorDropAlias(Biome biome, Vec3 waypoint, Vec3 drop) {
		if (waypoint.distanceToSqr(drop) < 0.01) return true;
		if (biome == Biome.FOREST && SafariTerrain.floor(biome,waypoint).equals(SafariTerrain.floor(biome,drop))) return true;
		double horizontal = Math.hypot(waypoint.x - drop.x, waypoint.z - drop.z);
		double vertical = waypoint.y - drop.y;
		return horizontal <= 2.5 && vertical >= 1.5 && vertical <= 2.5;
	}

	public static boolean isGazerBedWaypoint(Biome biome, Vec3 position) {
		return biome == Biome.HAUNTED && matches(route(Biome.HAUNTED).gazerBeds(), position);
	}

	public static int gazerBedIndex(Biome biome, Vec3 position) {
		if (biome != Biome.HAUNTED) return -1;
		List<Vec3> beds = route(Biome.HAUNTED).gazerBeds();
		for (int i = 0; i < beds.size(); i++) if (beds.get(i).distanceToSqr(position) < 0.01) return i;
		return -1;
	}

	public static boolean isCavernWaterfallConnector(Vec3 position) {
		return position.distanceToSqr(CAVERN_WATERFALL_EXIT) < 0.01
				|| CAVERN_WATERFALL_STEPS.stream().anyMatch(step -> step.distanceToSqr(position) < 0.01);
	}

	public static Vec3 forestTreeJumpOff() { return FOREST_TREE_JUMP_OFF; }
	public static Vec3 forestTreeStop() { return FOREST_TREE_JUMP; }


	public static boolean isForestTreeJumpOff(Vec3 position) { return position.distanceToSqr(FOREST_TREE_JUMP_OFF) < 0.01; }
	public static boolean isCavernCaveEntryLeg(Vec3 from,Vec3 to) { return Math.abs(from.x+92)<0.6 && Math.abs(from.z-42)<0.6 && from.y>=71 && from.y<=73
				&& Math.abs(to.x+88)<0.6 && Math.abs(to.z-42)<0.6 && to.y>=60 && to.y<=62; }
	public static boolean isForestTreeStop(Vec3 position) { return position.distanceToSqr(FOREST_TREE_JUMP) < 0.01; }
	public static boolean isCavernOpenDropEdge(Vec3 from, Vec3 to) {
        return (Math.abs(from.x+94.5)<.3 && Math.abs(to.x+94.5)<.3 && from.z>=37.4 && from.z<=38.6 && to.z>=37.4 && to.z<=38.6 && Math.min(from.y,to.y)>=74 && Math.max(from.y,to.y)<=81)
            || Math.abs(from.x + 88.5)<.25 && Math.abs(to.x + 88.5)<.25
            && Math.abs(from.z - 42.5)<.25 && Math.abs(to.z - 42.5)<.25
            && Math.max(from.y,to.y) <= 71 && Math.min(from.y,to.y) >= 61;
    }
    public static boolean isHauntedWaterExitEdge(Vec3 from, Vec3 to) {
        return Math.abs(from.x - 38.5) < .6 && Math.abs(to.x - 38.5) < .6
            && Math.abs(from.z + 15.5) < .6 && Math.abs(to.z + 15.5) < .6
            && Math.min(from.y,to.y) >= 50 && Math.max(from.y,to.y) <= 63;
    }
    public static boolean isForestShortcutLeg(Vec3 from, Vec3 to) {
        return (from.distanceToSqr(new Vec3(-28.5,90,69.5)) <= 9 && to.distanceToSqr(new Vec3(-38.5,86,58.5)) < 4)
            || (from.distanceToSqr(new Vec3(-38.5,86,58.5)) <= 9 && to.distanceToSqr(new Vec3(-34.5,68,60.5)) < 4);
    }

    public static boolean isForestTreeConnectorLeg(Vec3 from, Vec3 to) {
		return FOREST_TREE_JUMP_OFF.distanceToSqr(from) < 0.01 && FOREST_TREE_JUMP.distanceToSqr(to) < 0.01;
	}

	/** Direct ribbon shortcuts stay inside the lower route or the waterfall-top group. */
	public static boolean sameCavernRoutePhase(Vec3 from, Vec3 to) {
		return cavernRoutePhase(from) == cavernRoutePhase(to);
	}

	public static boolean isUnsafeDirectDrop(Biome biome, Vec3 from, Vec3 to) {
		return UNSAFE_DIRECT_DROPS.stream().anyMatch(drop -> drop.biome == biome
				&& drop.from.distanceToSqr(from) < 1.0 && drop.to.distanceToSqr(to) < 1.0);
	}

	private static int cavernRoutePhase(Vec3 point) {
		if (CAVERN_WATERFALL_EXIT.distanceToSqr(point) <= 12.25
				|| CAVERN_WATERFALL_STEPS.stream().anyMatch(step -> step.distanceToSqr(point) <= 12.25)) return 1;
		List<Vec3> stops = route(Biome.CAVERN).stops();
		int waterfallIndex = -1;
		for (int i = 0; i < stops.size(); i++) {
			if (stops.get(i).distanceToSqr(CAVERN_WATERFALL_ENTRY) < 0.01) {
				waterfallIndex = i;
				break;
			}
		}
		int nearestIndex = 0;
		double nearest = Double.POSITIVE_INFINITY;
		for (int i = 0; i < stops.size(); i++) {
			double distance = stops.get(i).distanceToSqr(point);
			if (distance < nearest) {
				nearest = distance;
				nearestIndex = i;
			}
		}
		return waterfallIndex >= 0 && nearestIndex > waterfallIndex ? 1 : 0;
	}

	public static boolean isHideyhoWaypoint(Biome biome, Vec3 position) {
		return biome == Biome.HAUNTED && route(Biome.HAUNTED).hideyhoLocations().stream()
				.anyMatch(point -> point.distanceToSqr(position) < 0.01);
	}

	static boolean pureHideyhoSearch(Vec3 p){
        return isHideyhoWaypoint(Biome.HAUNTED,p)&&!isFloorDropWaypoint(Biome.HAUNTED,p)&&!route(Biome.HAUNTED).houseSweep().contains(p)&&!isHideonwallLocation(Biome.HAUNTED,p)&&!isGazerBedWaypoint(Biome.HAUNTED,p)&&!isRitualWaypoint(Biome.HAUNTED,p)&&!p.equals(GIMMIEGOLD);
    }
    public static boolean isHideonwallWaypoint(Biome biome, Vec3 position) {
		return isHideonwallLocation(biome, position)
				&& position.distanceToSqr(new Vec3(17.0, 80.0, -74.0)) >= 0.01;
 	}

	public static boolean isHideonwallLocation(Biome biome, Vec3 position) {
		return biome == Biome.HAUNTED && matches(route(Biome.HAUNTED).hideonwallLocations(), position);
	}

	public static boolean isWithinBiomeArea(Biome biome, Vec3 position, double maxDistance) {
		double maxDistanceSquared = maxDistance * maxDistance;
		if (areaPoints(biome).stream().anyMatch(point -> point.distanceToSqr(position) <= maxDistanceSquared)) return true;
		return ChipsConfig.get().learnedStops(biome).stream().anyMatch(point -> point.distanceToSqr(position) <= maxDistanceSquared);
	}

	public static boolean isWithinAnyBiomeArea(Vec3 position, double maxDistance) {
		for (Biome biome : Biome.values()) if (isWithinBiomeArea(biome, position, maxDistance)) return true;
		return false;
	}

	public static Biome nearestBiomeArea(Vec3 position, double maxDistance) {
		Biome nearest = null;
		double bestSquared = maxDistance * maxDistance;
		for (Biome biome : Biome.values()) {
			double distanceSquared = areaPoints(biome).stream().mapToDouble(point -> point.distanceToSqr(position))
					.min().orElse(Double.POSITIVE_INFINITY);
			if (distanceSquared < bestSquared) {
				bestSquared = distanceSquared;
				nearest = biome;
			}
		}
		return nearest;
	}

	private static List<Vec3> areaPoints(Biome biome) {
		List<Vec3> cached = AREA_POINTS.get(biome);
		if (cached != null) return cached;
		RouteTemplate route = route(biome);
		List<Vec3> points = new ArrayList<>();
		points.add(biome.gate());
		points.addAll(route.stops());
		points.addAll(route.houseSweep());
		points.addAll(route.floorDropChecks());
		points.addAll(route.hideyhoLocations());
		points.addAll(route.hideonwallLocations());
		points.addAll(route.gazerBeds());
		if (route.ritualPoint() != null) points.add(route.ritualPoint());
		List<Vec3> immutable = List.copyOf(points);
		AREA_POINTS.put(biome, immutable);
		return immutable;
	}

	private static boolean matches(List<Vec3> points, Vec3 position) {
		return points.stream().anyMatch(point -> point.distanceToSqr(position) < 0.01);
	}

	public static synchronized void reloadRouteData() { routes=null; AREA_POINTS.clear(); }
	private static Map<Biome, RouteTemplate> loadRoutes() {
		Map<Biome, RouteTemplate> result = new EnumMap<>(Biome.class);
		try (InputStream input = ChipsCrittersClient.class.getResourceAsStream("/data/chipscritters/routes.json")) {
			if (input == null) throw new IllegalStateException("routes.json was not found");
			JsonObject root = RouteDataUpdates.routeRoot();
			if(root==null) root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
			for (Biome biome : Biome.values()) {
				JsonObject route = root.getAsJsonObject(biome.name());
				if (route == null) continue;
				List<Vec3> stops = readPoints(route.getAsJsonArray("stops"));
				// Generic Haunted house checkpoints are traversal waypoints, not critter locations.
				List<Vec3> houseSweep = biome == Biome.HAUNTED && route.has("houseSweep")
						? readPoints(route.getAsJsonArray("houseSweep")) : List.of();
				List<Vec3> floorDropChecks = route.has("floorDropChecks")
						? readPoints(route.getAsJsonArray("floorDropChecks")) : List.of();
				List<Vec3> hideyhoLocations = biome == Biome.HAUNTED && route.has("hideyhoLocations")
						? readPoints(route.getAsJsonArray("hideyhoLocations")) : List.of();
				List<Vec3> hideonwallLocations = biome == Biome.HAUNTED && route.has("hideonwallLocations")
						? readPoints(route.getAsJsonArray("hideonwallLocations")) : List.of();
				List<Vec3> gazerBeds = biome == Biome.HAUNTED && route.has("gazerBeds")
						? readPoints(route.getAsJsonArray("gazerBeds")) : List.of();
				Vec3 ritualPoint = biome == Biome.HAUNTED && route.has("ritualPoint")
						? readPoint(route.getAsJsonArray("ritualPoint")) : null;
				if (stops.isEmpty() || stops.getFirst().distanceTo(biome.gate()) > 2.0) stops.addFirst(biome.gate());
				Vec3 cavernWaterfallEntry = biome == Biome.CAVERN && route.has("cavernWaterfallEntry")
						? readPoint(route.getAsJsonArray("cavernWaterfallEntry")) : null;
				List<Vec3> cavernWaterfallTopStops = biome == Biome.CAVERN && route.has("cavernWaterfallTopStops")
						? readPoints(route.getAsJsonArray("cavernWaterfallTopStops")) : List.of();
				List<Vec3> cavernWestPlateauStops = biome == Biome.CAVERN && route.has("cavernWestPlateauStops")
						? readPoints(route.getAsJsonArray("cavernWestPlateauStops")) : List.of();
				List<CavernLadder> cavernLadders = new ArrayList<>();
				if (biome == Biome.CAVERN && route.has("cavernLadders")) {
					for (JsonElement element : route.getAsJsonArray("cavernLadders")) {
						JsonObject ladder = element.getAsJsonObject();
						Vec3 bottom = readPoint(ladder.getAsJsonArray("bottom"));
						Vec3 top = readPoint(ladder.getAsJsonArray("top"));
						if (bottom != null && top != null) cavernLadders.add(new CavernLadder(
								ladder.has("name") ? ladder.get("name").getAsString() : "CLIMB LADDER",
								bottom, top, ladder.has("twoWay") && ladder.get("twoWay").getAsBoolean(), readPoint(ladder.getAsJsonArray("before"))));
					}
				}
				result.put(biome, new RouteTemplate(List.copyOf(stops), List.copyOf(houseSweep), List.copyOf(floorDropChecks),
						List.copyOf(hideyhoLocations), List.copyOf(hideonwallLocations), List.copyOf(gazerBeds), ritualPoint,
						route.has("fixedEnd") && route.get("fixedEnd").getAsBoolean(), cavernWaterfallEntry,
						List.copyOf(cavernWaterfallTopStops), List.copyOf(cavernWestPlateauStops), List.copyOf(cavernLadders)));
			}
		} catch (Exception exception) {
			ChipsCrittersClient.LOGGER.error("Could not load ChipsCritters route data", exception);
		}
		return result;
	}

	private static List<Vec3> readPoints(JsonArray array) {
		List<Vec3> points = new ArrayList<>();
		if (array == null) return points;
		for (JsonElement element : array) {
			JsonArray point = element.getAsJsonArray();
			if (point.size() == 3) points.add(new Vec3(point.get(0).getAsDouble(), point.get(1).getAsDouble(), point.get(2).getAsDouble()));
		}
		return points;
	}

	private static Vec3 readPoint(JsonArray point) {
		return point == null || point.size() != 3 ? null
				: new Vec3(point.get(0).getAsDouble(), point.get(1).getAsDouble(), point.get(2).getAsDouble());
	}
}

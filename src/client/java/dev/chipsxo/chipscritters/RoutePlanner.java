package dev.chipsxo.chipscritters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import net.minecraft.world.phys.Vec3;

public final class RoutePlanner {
	public record PlanResult(List<Vec3> stops, long templateNanos, long collectNanos, long searchNanos, long totalNanos) {
		public PlanResult { stops = List.copyOf(stops); }
	}
	public record Input(Biome biome, ChipsConfig.Mode mode, double detourBlocks, List<Vec3> learnedStops,
			boolean snoozleCaught, boolean troodonCaught, boolean uniquePaintings) {
        public Input(Biome biome,ChipsConfig.Mode mode,double detourBlocks,List<Vec3> learnedStops,boolean snoozleCaught,boolean troodonCaught){this(biome,mode,detourBlocks,learnedStops,snoozleCaught,troodonCaught,true);}
		public Input {
			learnedStops = List.copyOf(learnedStops);
		}
	}
	private static final Vec3 HAUNTED_LEFT_AROUND_FIRST_STOP = new Vec3(-39.0, 67.0, -40.0);
	private static final int OPTIMIZATION_TRIALS = 96;
	private static final int MAX_2_OPT_PASSES = 50;
	private static final int MAX_EXACT_FREE_STOPS = 16;

	private RoutePlanner() { }

	public static List<Vec3> plan(Biome biome, ChipsConfig config) {
		return plan(snapshot(biome, config));
	}

	public static Input snapshot(Biome biome, ChipsConfig config) {
		return new Input(biome, config.mode, config.detourBlocks, config.learnedStops(biome),
				WaypointRunLedger.snoozleCaught(), WaypointRunLedger.troodonCaught(), config.uniqueHideonwallRoutes);
	}

	public static List<Vec3> plan(Input input) {
		return planMeasured(input).stops();
	}

	public static PlanResult planMeasured(Input input) {
		long totalStarted = System.nanoTime();
		long templateStarted = System.nanoTime();
		Biome biome = input.biome();
		SafariData.RouteTemplate template = SafariData.route(biome);
		long templateNanos = System.nanoTime() - templateStarted;
		long collectStarted = System.nanoTime();
		List<Vec3> stops = new ArrayList<>(template.stops().stream().filter(p->biome!=Biome.HAUNTED||!p.equals(SafariData.GIMMIEGOLD)).toList());
		if (biome == Biome.CAVERN) {
			// Cavern's sequence is authored in route data and includes its full floor-drop
			// sweep. Do not reorder it or append optional stops around its connectors.
			if (input.mode() == ChipsConfig.Mode.UNIQUE && input.snoozleCaught()) {
				stops.removeIf(stop -> SafariData.isSnoozleWaypoint(biome, stop));
			}
			List<Vec3> result = deduplicateExact(stops);
			return new PlanResult(result, templateNanos, System.nanoTime() - collectStarted, 0L,
					System.nanoTime() - totalStarted);
		}
		if (biome == Biome.ICY && !IcyCaptureRules.keepTroodonWaypoints(
				input.mode() == ChipsConfig.Mode.UNIQUE, input.troodonCaught())) {
			stops.removeIf(stop -> SafariData.isTroodonWaypoint(biome, stop));
		}
		List<Vec3> additions = input.learnedStops();
		addDistinct(stops, template.houseSweep(), template.fixedEnd());
		// Keep each floor-drop target, but merge checks represented by a
		// route waypoint at the same floor location instead of adding a second stop.
		for (Vec3 drop : template.floorDropChecks()) {
			boolean alreadyCovered = stops.stream().anyMatch(stop -> SafariData.isFloorDropAlias(biome, stop, drop)
					&& !(SafariData.isSnoozleWaypoint(biome, stop) && !samePoint(stop, drop)));
			if (!alreadyCovered) addExactDistinct(stops, List.of(drop), template.fixedEnd());
		}
		if (biome == Biome.HAUNTED) {
			if (input.mode() == ChipsConfig.Mode.MONEY) addExactDistinct(stops, template.gazerBeds(), template.fixedEnd());
			// All route modes visit the known painting sites; the wall target is
			// rendered at the painting and navigation ends at reachable floor nearby.
			addExactDistinct(stops, template.hideonwallLocations(), template.fixedEnd());
			if (template.ritualPoint() != null) addDistinctWithin(stops, List.of(template.ritualPoint()), template.fixedEnd(), 4.0);
			if (input.mode() == ChipsConfig.Mode.UNIQUE || input.mode() == ChipsConfig.Mode.FULL_CLEAR) {
				// These are fixed search targets, not live critter detection. Skip candidates covered by existing route stops.
				addDistinctWithin(stops, template.hideyhoLocations(), template.fixedEnd(), 4.0);
				if (input.mode() == ChipsConfig.Mode.UNIQUE) {
					Vec3 bed = bestGazerBed(stops, template.gazerBeds(), template.fixedEnd(), SafariGraph.get());
					if (bed != null) addDistinct(stops, List.of(bed), template.fixedEnd());
				} else {
					addDistinct(stops, template.gazerBeds(), template.fixedEnd());
				}
			}
		}
		if (input.mode() == ChipsConfig.Mode.FULL_CLEAR || input.mode() == ChipsConfig.Mode.UNIQUE) {
			addDistinct(stops, additions, template.fixedEnd());
		} else if (input.mode() == ChipsConfig.Mode.MONEY) {
			for (Vec3 extra : additions) {
				double nearest = template.stops().stream().mapToDouble(point -> point.distanceTo(extra)).min().orElse(Double.POSITIVE_INFINITY);
				if (nearest <= input.detourBlocks()) addDistinct(stops, List.of(extra), template.fixedEnd());
			}
		}
		if (stops.isEmpty() || stops.getFirst().distanceTo(biome.gate()) > 2.0) stops.addFirst(biome.gate());
		if (biome == Biome.HAUNTED && stops.contains(new Vec3(10,77,-75)) && stops.contains(new Vec3(11,77,-75))) stops.remove(new Vec3(11,77,-75));
        stops = deduplicateExact(stops);
        if(biome==Biome.HAUNTED&&input.mode()==ChipsConfig.Mode.UNIQUE){
            if(!input.uniquePaintings())stops=stops.stream().filter(p->!SafariData.isHideonwallLocation(biome,p)).toList();
        }
        if(biome==Biome.HAUNTED)stops=stops.stream().filter(p->!SafariData.GIMMIEGOLD.equals(p)).toList();
		long collectNanos = System.nanoTime() - collectStarted;
		long searchStarted = System.nanoTime();
		if (biome == Biome.FOREST) {
			// The bundled sweep is the solved directed graph tour. Only custom stops require a new order.
			List<Vec3> result = stops.equals(template.stops()) ? template.stops()
					: optimize(biome, stops, template.fixedEnd(), SafariGraph.get(), null);
			result=pinForestTreeSequence(result,template.fixedEnd(),SafariGraph.get());
			return new PlanResult(result, templateNanos, collectNanos, System.nanoTime() - searchStarted,
					System.nanoTime() - totalStarted);
		}
		Vec3 pinnedSecond = switch (biome) {
			case HAUNTED -> HAUNTED_LEFT_AROUND_FIRST_STOP;
			case ICY -> firstStopBelowY(stops, SafariData.ICY_OCEAN_ADVANCE_Y);
			default -> null;
		};
		List<Vec3> result = optimize(biome, stops, template.fixedEnd(), SafariGraph.get(), pinnedSecond);
		if (biome == Biome.HAUNTED) {
            result = AtticRouteOrder.optimize(result, SafariGraph.get());
            if(input.mode()==ChipsConfig.Mode.UNIQUE)result=AtticRouteOrder.uniqueShortSweep(result,SafariGraph.get());
            result = pinHauntedEntrance(result);
            result = HauntedRoomOrder.paintingRoom(HauntedRoomOrder.order(result));
            var withGold=new ArrayList<>(result);withGold.removeIf(SafariData.GIMMIEGOLD::equals);withGold.add(SafariData.GIMMIEGOLD);result=List.copyOf(withGold);
        }
		return new PlanResult(result, templateNanos, collectNanos, System.nanoTime() - searchStarted,
				System.nanoTime() - totalStarted);
	}

	private static List<Vec3> pinHauntedEntrance(List<Vec3> route) {
        var result = new ArrayList<>(route);
        if(!result.remove(SafariData.HAUNTED_ENTRANCE)) return route;
        int lastEastBed=-1;
        for(int i=0;i<result.size();i++) if(SafariData.isGazerBedWaypoint(Biome.HAUNTED,result.get(i)) && result.get(i).x>0) lastEastBed=i;
        if(lastEastBed<0) return route;
        result.add(lastEastBed+1,SafariData.HAUNTED_ENTRANCE);
        return List.copyOf(result);
    }
    private static List<Vec3> pinForestTreeSequence(List<Vec3> route,boolean fixedEnd,SafariGraph graph) {
		List<Vec3> chain=List.of(SafariData.forestTreeStop(),SafariData.FOREST_TREE_AFTER,SafariData.FOREST_DROP_AFTER_TREE);
		List<Vec3> base=new ArrayList<>(route);
		for(Vec3 p:chain) base.removeIf(p::equals);
		if(base.size()<2) return route;
		List<Vec3> best=null;double bestCost=Double.POSITIVE_INFINITY;
		int end=fixedEnd?base.size()-1:base.size();
		for(int at=1;at<=end;at++) {
			List<Vec3> candidate=new ArrayList<>(base);
			candidate.addAll(at,chain);
			double cost=orderedRouteCost(Biome.FOREST,candidate,graph);
			if(cost<bestCost){bestCost=cost;best=candidate;}
		}
		return best==null?route:List.copyOf(best);
	}

	static List<Vec3> deduplicateExact(List<Vec3> points) {
		List<Vec3> result = new ArrayList<>(points.size());
		for (Vec3 point : points) if (result.stream().noneMatch(existing -> samePoint(existing, point))) result.add(point);
		return List.copyOf(result);
	}

	/** Reorders only the remaining waypoints from the current player position. */
	public static List<Vec3> optimizeRemaining(Biome biome, List<Vec3> pending, Vec3 origin, boolean gateIsFirst) {
		// Initial route order was optimized once. Relay claims, rejoin, and captures
		// now remove stops without running another full search on the client thread.
		return List.copyOf(pending);
	}

	/** Exceptional relocation replan; graph-only, called on the planner executor. Upper house stays before underground; Gold stays last. */
    static List<Vec3> reorderHaunted(List<Vec3> pending,Vec3 origin){
        var upper=new ArrayList<Vec3>();var lower=new ArrayList<Vec3>();boolean gold=pending.contains(SafariData.GIMMIEGOLD);
        for(Vec3 p:pending)if(!p.equals(SafariData.GIMMIEGOLD)){if(p.y>60)upper.add(p);else lower.add(p);}
        Vec3 ritual=pending.stream().filter(p->SafariData.isRitualWaypoint(Biome.HAUNTED,p)).findFirst().orElse(null);
        if(ritual!=null){lower.remove(ritual);upper.remove(ritual);}
        var result=new ArrayList<Vec3>();Vec3 from=origin;
        for(var group:List.of(upper,lower)){
            if(group==lower&&ritual!=null){result.add(ritual);from=ritual;}
            if(group.isEmpty())continue;
            var input=new ArrayList<Vec3>();input.add(from);input.addAll(group);
            var order=optimize(Biome.HAUNTED,input,false,SafariGraph.get(),null);
            if(orderedRouteCost(Biome.HAUNTED,input,SafariGraph.get())<orderedRouteCost(Biome.HAUNTED,order,SafariGraph.get()))order=List.copyOf(input);
            if(group==upper){order=AtticRouteOrder.optimize(order,SafariGraph.get());order=HauntedRoomOrder.paintingRoom(order);}
            result.addAll(order.subList(1,order.size()));from=result.getLast();
        }
        if(gold)result.add(SafariData.GIMMIEGOLD);
        return List.copyOf(result);
    }

    static List<Vec3> searchOrder(List<Vec3> pending,Vec3 origin){
        if(pending.isEmpty())return List.of();var input=new ArrayList<Vec3>();input.add(origin);input.addAll(pending);
        return List.copyOf(optimize(Biome.HAUNTED,input,false,SafariGraph.get(),null).subList(1,input.size()));
    }
    private static boolean samePoint(Vec3 first, Vec3 second) {
		return first.distanceToSqr(second) < 0.01;
	}

	private static Vec3 bestGazerBed(List<Vec3> stops, List<Vec3> beds, boolean fixedEnd, SafariGraph graph) {
		if (beds.isEmpty() || stops.isEmpty()) return null;
		Vec3 bestBed = null;
		double bestRouteCost = Double.POSITIVE_INFINITY;
		for (Vec3 bed : beds) {
			List<Vec3> candidateStops = new ArrayList<>(stops);
			addDistinct(candidateStops, List.of(bed), fixedEnd);
			List<Vec3> ordered = optimize(Biome.HAUNTED, candidateStops, fixedEnd, graph, HAUNTED_LEFT_AROUND_FIRST_STOP);
			double routeCost = orderedRouteCost(Biome.HAUNTED, ordered, graph);
			if (routeCost < bestRouteCost) {
				bestRouteCost = routeCost;
				bestBed = bed;
			}
		}
		return bestBed;
	}

	private static double orderedRouteCost(Biome biome, List<Vec3> stops, SafariGraph graph) {
		double cost = 0.0;
		for (int i = 1; i < stops.size(); i++) cost += graph.travelDistance(biome, stops.get(i - 1), stops.get(i));
		return cost;
	}

	private static Vec3 firstStopBelowY(List<Vec3> stops, double yLevel) {
		for (int i = 1; i < stops.size(); i++) {
			if (stops.get(i).y < yLevel) return stops.get(i);
		}
		return null;
	}

	private static void addDistinct(List<Vec3> target, List<Vec3> points, boolean fixedEnd) {
		for (Vec3 point : points) {
			if (target.stream().noneMatch(existing -> existing.distanceToSqr(point) < 1.0)) {
				target.add(fixedEnd ? target.size() - 1 : target.size(), point);
			}
		}
	}

	private static void addExactDistinct(List<Vec3> target, List<Vec3> points, boolean fixedEnd) {
		for (Vec3 point : points) {
			if (target.stream().noneMatch(existing -> samePoint(existing, point))) {
				target.add(fixedEnd ? target.size() - 1 : target.size(), point);
			}
		}
	}

	private static void addDistinctWithin(List<Vec3> target, List<Vec3> points, boolean fixedEnd, double radius) {
		double radiusSquared = radius * radius;
		for (Vec3 point : points) {
			if (target.stream().noneMatch(existing -> existing.distanceToSqr(point) <= radiusSquared)) {
				target.add(fixedEnd ? target.size() - 1 : target.size(), point);
			}
		}
	}

	/** Multi-start nearest-neighbor plus 2-opt where a biome permits reordering. Gate and required openings stay pinned; authored Cavern order is fixed and Forest ends at the Birdfeeder. */
	private static List<Vec3> optimize(Biome biome, List<Vec3> points, boolean fixedEnd, SafariGraph graph, Vec3 pinnedSecond) {
		if (points.size() < 3) return List.copyOf(points);
		int count = points.size();
		int pinnedSecondIndex = findPoint(points, pinnedSecond, fixedEnd ? count - 1 : count);
		int pinnedThirdIndex = -1;
		double[][] distance = new double[count][count];
		for (int i = 0; i < count; i++) {
			for (int j = i + 1; j < count; j++) {
				distance[i][j] = distance[j][i] = graph.travelDistance(biome, points.get(i), points.get(j));
			}
		}
		List<Integer> bestOrder = null;
		double bestCost = Double.POSITIVE_INFINITY;
		int lockedPrefix = pinnedThirdIndex > 0 ? 3 : pinnedSecondIndex > 0 ? 2 : 1;
		int freeEnd = fixedEnd ? count - 1 : count;
		int freeCount = freeEnd - lockedPrefix;
		if (freeCount >= 0 && freeCount <= MAX_EXACT_FREE_STOPS) {
			List<Integer> exact = exactOrder(distance, count, fixedEnd, lockedPrefix, freeCount, pinnedSecondIndex, pinnedThirdIndex);
			if (exact != null) {
				List<Vec3> result = new ArrayList<>(count);
				for (int index : exact) result.add(points.get(index));
				return List.copyOf(result);
			}
		}
		for (int trial = 0; trial < OPTIMIZATION_TRIALS; trial++) {
			Random random = new Random(0x434849505343L + trial);
			List<Integer> order = nearestNeighborOrder(distance, count, fixedEnd, pinnedSecondIndex, pinnedThirdIndex, trial, random);
			improveTwoOpt(order, distance, fixedEnd, lockedPrefix);
			double cost = routeCost(order, distance);
			if (cost < bestCost) {
				bestCost = cost;
				bestOrder = order;
			}
		}

		List<Vec3> result = new ArrayList<>(count);
		for (int index : bestOrder) result.add(points.get(index));
		return List.copyOf(result);
	}

	/** Exact shortest path for typical partial routes; larger routes use the multi-start heuristic below. */
	private static List<Integer> exactOrder(double[][] distance, int count, boolean fixedEnd, int lockedPrefix, int freeCount,
			int pinnedSecondIndex, int pinnedThirdIndex) {
		int freeEnd = fixedEnd ? count - 1 : count;
		if (freeCount != freeEnd - lockedPrefix || freeCount > MAX_EXACT_FREE_STOPS) return null;
		List<Integer> prefix = new ArrayList<>(lockedPrefix);
		prefix.add(0);
		if (pinnedSecondIndex >= 0) prefix.add(pinnedSecondIndex);
		if (pinnedThirdIndex >= 0) prefix.add(pinnedThirdIndex);
		List<Integer> freePoints = new ArrayList<>(freeCount);
		for (int i = 1; i < freeEnd; i++) if (i != pinnedSecondIndex && i != pinnedThirdIndex) freePoints.add(i);
		if (prefix.size() != lockedPrefix || freePoints.size() != freeCount) return null;
		if (freeCount == 0) {
			List<Integer> order = new ArrayList<>(count);
			order.addAll(prefix);
			if (fixedEnd) order.add(count - 1);
			return order;
		}

		int stateCount = 1 << freeCount;
		int tableSize = stateCount * freeCount;
		double[] costs = new double[tableSize];
		java.util.Arrays.fill(costs, Double.POSITIVE_INFINITY);
		short[] previous = new short[tableSize];
		java.util.Arrays.fill(previous, (short) -1);
		int start = prefix.getLast();
		for (int last = 0; last < freeCount; last++) {
			int point = freePoints.get(last);
			costs[(1 << last) * freeCount + last] = distance[start][point];
		}

		for (int mask = 1; mask < stateCount; mask++) {
			for (int last = 0; last < freeCount; last++) {
				if ((mask & (1 << last)) == 0) continue;
				double cost = costs[mask * freeCount + last];
				if (!Double.isFinite(cost)) continue;
				int remaining = (stateCount - 1) ^ mask;
				for (int next = 0; next < freeCount; next++) {
					int bit = 1 << next;
					if ((remaining & bit) == 0) continue;
					int nextMask = mask | bit;
					int slot = nextMask * freeCount + next;
					double candidate = cost + distance[freePoints.get(last)][freePoints.get(next)];
					if (candidate < costs[slot]) {
						costs[slot] = candidate;
						previous[slot] = (short) last;
					}
				}
			}
		}

		int fullMask = stateCount - 1;
		int bestLast = -1;
		double bestCost = Double.POSITIVE_INFINITY;
		for (int last = 0; last < freeCount; last++) {
			double candidate = costs[fullMask * freeCount + last];
			if (fixedEnd) candidate += distance[freePoints.get(last)][count - 1];
			if (candidate < bestCost) {
				bestCost = candidate;
				bestLast = last;
			}
		}
		if (bestLast < 0) return null;

		int[] reversed = new int[freeCount];
		int mask = fullMask;
		int cursor = bestLast;
		for (int position = freeCount - 1; position >= 0; position--) {
				reversed[position] = freePoints.get(cursor);
			int slot = mask * freeCount + cursor;
			int prior = previous[slot];
			mask ^= 1 << cursor;
			cursor = prior;
		}

		List<Integer> order = new ArrayList<>(count);
		order.addAll(prefix);
		for (int point : reversed) order.add(point);
		if (fixedEnd) order.add(count - 1);
		return order;
	}

	private static int findPoint(List<Vec3> points, Vec3 target, int exclusiveEnd) {
		if (target == null) return -1;
		for (int i = 1; i < Math.min(points.size(), exclusiveEnd); i++) {
			if (points.get(i).distanceToSqr(target) < 1.0) return i;
		}
		return -1;
	}

	private static List<Integer> nearestNeighborOrder(double[][] distance, int count, boolean fixedEnd,
			int pinnedSecondIndex, int pinnedThirdIndex, int trial, Random random) {
		List<Integer> order = new ArrayList<>();
		boolean[] used = new boolean[count];
		order.add(0);
		used[0] = true;
		if (pinnedSecondIndex >= 0) {
			order.add(pinnedSecondIndex);
			used[pinnedSecondIndex] = true;
		}
		if (pinnedThirdIndex >= 0) {
			order.add(pinnedThirdIndex);
			used[pinnedThirdIndex] = true;
		}
		int end = fixedEnd ? count - 1 : -1;
		while (order.size() < count - (fixedEnd ? 1 : 0)) {
			int last = order.getLast();
			List<Integer> candidates = new ArrayList<>();
			for (int i = 1; i < count; i++) {
				if (!used[i] && i != end) candidates.add(i);
			}
			candidates.sort(Comparator.comparingDouble(index -> distance[last][index]));
			if (candidates.isEmpty()) break;
			int poolSize = Math.min(3, candidates.size());
			int choice = trial == 0 ? 0 : random.nextInt(poolSize);
			int next = candidates.get(choice);
			used[next] = true;
			order.add(next);
		}
		if (fixedEnd) order.add(end);
		return order;
	}

	private static void improveTwoOpt(List<Integer> order, double[][] distance, boolean fixedEnd, int lockedPrefix) {
		boolean improved = true;
		int passes = 0;
		while (improved && passes++ < MAX_2_OPT_PASSES) {
			improved = false;
			for (int i = lockedPrefix; i < order.size() - 1; i++) {
				int lastReversal = fixedEnd ? order.size() - 2 : order.size() - 1;
				for (int k = i + 1; k <= lastReversal; k++) {
					int before = order.get(i - 1);
					int first = order.get(i);
					int last = order.get(k);
					double oldCost = distance[before][first];
					double newCost = distance[before][last];
					for (int edge = i; edge < k; edge++) {
						int from = order.get(edge);
						int to = order.get(edge + 1);
						oldCost += distance[from][to];
						newCost += distance[to][from];
					}
					if (k + 1 < order.size()) {
						int after = order.get(k + 1);
						oldCost += distance[last][after];
						newCost += distance[first][after];
					}
					if (newCost + 0.01 < oldCost) {
						Collections.reverse(order.subList(i, k + 1));
						improved = true;
					}
				}
			}
		}
	}

	private static double routeCost(List<Integer> order, double[][] distance) {
		double cost = 0.0;
		for (int i = 1; i < order.size(); i++) cost += distance[order.get(i - 1)][order.get(i)];
		return cost;
	}
}

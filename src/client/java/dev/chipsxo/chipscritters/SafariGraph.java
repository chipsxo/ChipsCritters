package dev.chipsxo.chipscritters;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiPredicate;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Walkable topology only. The bundled derivative contains no node names or tags. */
public final class SafariGraph {
	public record RouteLineIssue(int stopNumber, Vec3 from, Vec3 to, String reason) { }
	public record RouteLineResult(List<Vec3> points, Set<Integer> gaps, List<RouteLineIssue> issues) { }
	private static final Vec3 SAFARI_LANDING = new Vec3(-50.0, 69.0, 22.0);
	private static final double LANDING_APPROACH_RADIUS = 32.0;
	private record Node(Vec3 position, Map<Integer, Double> neighbors) { }
	private record QueueEntry(int id, double distance) { }
	private record Vec3Edge(Vec3 first, Vec3 second) { }
	private record NearestKey(Biome biome, Vec3 position, double maxDistance) { }
	private record SourceKey(Biome biome, int node) { }
	private record DirectLegKey(Biome biome, int stopNumber, Vec3 target) { }
	private record DirectDecision(boolean direct, int consecutiveClearChecks) { }
	private final Map<Integer, Node> nodes = new HashMap<>();
	private final Map<NearestKey, Integer> nearestNodeCache = new ConcurrentHashMap<>();
	private final Map<SourceKey, Map<Integer, Double>> distanceCache = new ConcurrentHashMap<>();
	private final Map<DirectLegKey, DirectDecision> directDecisions = new ConcurrentHashMap<>();
	private static SafariGraph instance;

	private SafariGraph() { load(); }

	public static SafariGraph get() {
		if (instance == null) instance = new SafariGraph();
		return instance;
	}

	private boolean alternateJoin;
    private boolean coinCosting;
	void requestAlternateJoin() { alternateJoin=true; }
	public void resetDirectRouteChoices() { directDecisions.clear(); forceMappedRepair=false; alternateJoin=false; }

	private void load() {
		try (InputStream input = ChipsCrittersClient.class.getResourceAsStream("/data/chipscritters/safari_graph.json")) {
			if (input == null) throw new IllegalStateException("safari_graph.json was not found");
			JsonObject root = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("nodes");
			for (Map.Entry<String, com.google.gson.JsonElement> entry : root.entrySet()) {
				int id = Integer.parseInt(entry.getKey());
				JsonObject node = entry.getValue().getAsJsonObject();
				var position = node.getAsJsonArray("p");
				Map<Integer, Double> neighbors = new HashMap<>();
				JsonObject neighborObject = node.getAsJsonObject("n");
				if (neighborObject != null) {
					for (Map.Entry<String, com.google.gson.JsonElement> neighbor : neighborObject.entrySet()) {
						neighbors.put(Integer.parseInt(neighbor.getKey()), neighbor.getValue().getAsDouble());
					}
				}
				nodes.put(id, new Node(new Vec3(position.get(0).getAsDouble(), position.get(1).getAsDouble(), position.get(2).getAsDouble()), neighbors));
			}
			ChipsCrittersClient.LOGGER.info("Loaded {} tag-free safari path nodes", nodes.size());
		} catch (Exception exception) {
			ChipsCrittersClient.LOGGER.error("Could not load safari walk graph", exception);
		}
	}

	public int nearestNode(Vec3 point, double maxDistance) {
		return nearestNode(null, point, maxDistance);
	}

	public int nearestNode(Biome biome, Vec3 point, double maxDistance) {
		return nearestNodeCache.computeIfAbsent(new NearestKey(biome, point, maxDistance), this::findNearestNode);
	}

	private int findNearestNode(NearestKey key) {
		int bestId = -1;
		double bestSquared = key.maxDistance * key.maxDistance;
		for (Map.Entry<Integer, Node> entry : nodes.entrySet()) {
			if (!routeNodeAllowed(key.biome, entry.getValue().position)) continue;
			double squared = entry.getValue().position.distanceToSqr(key.position);
			if (squared < bestSquared) {
				bestSquared = squared;
				bestId = entry.getKey();
			}
		}
		return bestId;
	}

	public double travelDistance(Vec3 from, Vec3 to) {
		return travelDistance(null, from, to);
	}

	public double travelDistance(Biome biome, Vec3 from, Vec3 to) {
		int start = nearestNode(biome, from, 18.0);
		int goal = nearestNode(biome, to, 18.0);
		if (start < 0 || goal < 0) return from.distanceTo(to);
		if (start == goal) return from.distanceTo(to);
		return distancesFrom(biome, start).getOrDefault(goal, from.distanceTo(to));
	}

	public List<Vec3> auditMappedPath(Biome biome,Vec3 from,Vec3 to){return pathBetween(biome,from,to,null);}
	public List<Vec3> pathBetween(Vec3 from, Vec3 to) {
		return pathBetween(null, from, to, null);
	}

	private List<Vec3> pathBetween(Biome biome, Vec3 from, Vec3 to, BiPredicate<Vec3, Vec3> edgeAllowed) {
		if (from.distanceToSqr(to) < 1.0E-4) return List.of(from);
		int start = nearestNode(biome, from, 18.0);
		int goal = nearestNode(biome, to, 18.0);
		if (start < 0 || goal < 0) return List.of();
		if (edgeAllowed != null && (alternateJoin || biome==null)) {
			alternateJoin=false;
			var repaired=joinCorridor(biome,from,to,edgeAllowed);
			if(!repaired.isEmpty()) return repaired;
		}
		return pathVia(biome,from,to,start,goal,edgeAllowed);
	}

	// One multi-source search: a nearest graph point can be behind a wall at either end.
	private List<Vec3> joinCorridor(Biome biome,Vec3 from,Vec3 to,BiPredicate<Vec3,Vec3> allowed) {
		var prev=new HashMap<Integer,Integer>();
		var cost=new HashMap<Integer,Double>();
		var ends=new HashMap<Integer,Double>();
		var queue=new PriorityQueue<QueueEntry>((a,b)->Double.compare(a.distance,b.distance));
		for(var endpoint:List.of(from,to)) {
			var nearby=nodes.entrySet().stream().filter(e->routeNodeAllowed(biome,e.getValue().position)
				&& endpoint.distanceToSqr(e.getValue().position)<=144)
				.sorted(java.util.Comparator.comparingDouble(e->endpoint.distanceToSqr(e.getValue().position))).limit(6).toList();
			for(var e:nearby) {
				Vec3 n=e.getValue().position;
				if(!allowed.test(endpoint,n) || !RouteRenderer.supportedJoin(Minecraft.getInstance().level,endpoint,n))continue;
				double d=endpoint.distanceTo(n);
				if(endpoint==from){cost.put(e.getKey(),d);queue.add(new QueueEntry(e.getKey(),d));}
				else ends.put(e.getKey(),d);
			}
		}
		int goal=-1;double best=Double.POSITIVE_INFINITY;
		while(!queue.isEmpty() && !ends.isEmpty()){
			var cur=queue.poll();
			if(cur.distance>=best)break;
			if(cur.distance>cost.get(cur.id))continue;
			double end=cur.distance+ends.getOrDefault(cur.id,Double.POSITIVE_INFINITY);
			if(end<best){best=end;goal=cur.id;}
			var n=nodes.get(cur.id);
			for(var e:n.neighbors.entrySet()){
				var next=nodes.get(e.getKey()); double d=cur.distance+e.getValue();
				if(next==null || !routeNodeAllowed(biome,next.position) || d>=cost.getOrDefault(e.getKey(),Double.POSITIVE_INFINITY)
					|| !allowed.test(n.position,next.position))continue;
				cost.put(e.getKey(),d);prev.put(e.getKey(),cur.id);queue.add(new QueueEntry(e.getKey(),d));
			}
		}
		if(goal<0)return List.of();
		var rev=new ArrayList<Vec3>();for(Integer id=goal;id!=null;id=prev.get(id))rev.add(nodes.get(id).position);
		Collections.reverse(rev);return withEndpoints(from,rev,to);
	}

	private List<Vec3> pathVia(Biome biome,Vec3 from,Vec3 to,int start,int goal,BiPredicate<Vec3,Vec3> edgeAllowed) {
		if (start == goal) {
			List<Vec3> direct = withEndpoints(from, List.of(nodes.get(start).position), to);
			return pathWithinBiome(biome, direct) && pathEdgesAllowed(direct, edgeAllowed) ? direct : List.of();
		}

		Map<Integer, Integer> previous = new HashMap<>();
		Map<Integer, Double> distances = edgeAllowed == null
				? distancesFrom(biome, start, previous)
				: distancesFromAvoiding(biome, start, goal, previous, edgeAllowed);
		if (!distances.containsKey(goal)) return List.of();
		List<Vec3> reversed = new ArrayList<>();
		int cursor = goal;
		reversed.add(nodes.get(cursor).position);
		while (cursor != start) {
			Integer prior = previous.get(cursor);
			if (prior == null) return List.of();
			cursor = prior;
			reversed.add(nodes.get(cursor).position);
		}
		Collections.reverse(reversed);
		List<Vec3> complete = withEndpoints(from, reversed, to);
		return pathWithinBiome(biome, complete) && pathEdgesAllowed(complete, edgeAllowed) ? complete : List.of();
	}

	private static boolean pathWithinBiome(Biome biome, List<Vec3> path) {
		if (biome == null) return true;
		for (Vec3 point : path) if (!routeNodeAllowed(biome, point)) return false;
		return true;
	}

	private static boolean routeNodeAllowed(Biome biome, Vec3 point) {
		if (biome == null) return true;
		// The bundled topology has no biome tags. Keep graph nodes, anchors and manual
		// connectors inside disjoint mapped corridors so a neighboring biome cannot
		// become a shortcut or a rejoin target.
		return switch (biome) {
			case CAVERN -> point.x >= -155.0 && point.x <= -60.0
					&& point.y >= 30.0 && point.y <= 110.0
					&& point.z >= 10.0 && point.z <= 95.0
					&& (point.y <= 82.0 || point.x <= -64.0);
			case ICY -> point.x >= -145.0 && point.x <= -50.0
					&& point.y >= 18.0 && point.y <= 130.0
					&& point.z >= -120.0 && point.z <= -5.0;
			case HAUNTED -> point.x >= -50.0 && point.x <= 65.0
					&& point.y >= 35.0 && point.y <= 100.0
					&& point.z >= -112.0 && point.z <= -1.0;
			case FOREST -> point.x >= -45.0 && point.x <= 50.0
					&& point.y >= 35.0 && point.y <= 130.0
					&& point.z >= 0.0 && point.z <= 100.0;
		};
	}

	private static boolean pathEdgesAllowed(List<Vec3> path, BiPredicate<Vec3, Vec3> edgeAllowed) {
		if (edgeAllowed == null) return true;
		for (int i = 0; i + 1 < path.size(); i++) {
			if (!edgeAllowed.test(path.get(i), path.get(i + 1))) return false;
		}
		return true;
	}

	private Map<Integer, Double> distancesFromAvoiding(Biome biome, int source, int target, Map<Integer, Integer> previous,
			BiPredicate<Vec3, Vec3> edgeAllowed) {
		Map<Integer, Double> distances = new HashMap<>();
		PriorityQueue<QueueEntry> queue = new PriorityQueue<>((a, b) -> Double.compare(a.distance, b.distance));
		distances.put(source, 0.0);
		queue.add(new QueueEntry(source, 0.0));
		while (!queue.isEmpty()) {
			QueueEntry current = queue.poll();
			if (current.distance > distances.getOrDefault(current.id, Double.POSITIVE_INFINITY)) continue;
			if (current.id == target) break;
			Node node = nodes.get(current.id);
			if (node == null) continue;
			for (Map.Entry<Integer, Double> edge : node.neighbors.entrySet()) {
				Node neighbor = nodes.get(edge.getKey());
				if (neighbor == null || !routeNodeAllowed(biome, neighbor.position)
						|| !edgeAllowed.test(node.position, neighbor.position)) continue;
				double candidate = current.distance + edge.getValue();
				if (candidate < distances.getOrDefault(edge.getKey(), Double.POSITIVE_INFINITY)) {
					distances.put(edge.getKey(), candidate);
					previous.put(edge.getKey(), current.id);
					queue.add(new QueueEntry(edge.getKey(), candidate));
				}
			}
		}
		return distances;
	}

	private static List<Vec3> withEndpoints(Vec3 from, List<Vec3> path, Vec3 to) {
		List<Vec3> complete = new ArrayList<>(path.size() + 2);
		appendDistinct(complete, from);
		for (Vec3 point : path) appendDistinct(complete, point);
		appendDistinct(complete, to);
		return List.copyOf(complete);
	}

	private static List<Vec3> joined(List<Vec3> a,List<Vec3> b) {
		if(a.isEmpty() || b.isEmpty()) return List.of();
		List<Vec3> path=new ArrayList<>(a);
		for(Vec3 point:b) appendDistinct(path,point);
		return path;
	}

	private static void appendDistinct(List<Vec3> points, Vec3 point) {
		if (points.isEmpty() || points.getLast().distanceToSqr(point) > 0.01) points.add(point);
	}

	public List<Vec3> routeLine(List<Vec3> stops) {
		return routeLineFrom(new Vec3(-50, 69, 22), stops); // Shared landing; route stop #1 remains the gate.
	}

	public List<Vec3> routeLineFrom(Vec3 anchor, List<Vec3> stops) {
		return routeLineFrom(null, anchor, stops);
	}

	public List<Vec3> routeLineFrom(Biome biome, Vec3 anchor, List<Vec3> stops) {
		return routeLineResultFrom(biome, anchor, stops, 0).points();
	}

	public RouteLineResult routeLineResultFrom(Biome biome, Vec3 anchor, List<Vec3> stops, int firstStopNumber) {
        return routeLineResultFrom(biome,anchor,stops,firstStopNumber,false,null);
    }
    RouteLineResult coinCostRoute(Vec3 sourceIdentity,Vec3 anchor,Vec3 target){
        boolean repair=forceMappedRepair,alternate=alternateJoin,prior=coinCosting;coinCosting=true;
        try{return routeLineResultFrom(Biome.HAUNTED,anchor,List.of(target),0,true,sourceIdentity);}
        finally{forceMappedRepair=repair;alternateJoin=alternate;coinCosting=prior;}
    }
    private RouteLineResult routeLineResultFrom(Biome biome,Vec3 anchor,List<Vec3> stops,int firstStopNumber,boolean costing,Vec3 sourceIdentity){
		if (stops.isEmpty()) return new RouteLineResult(List.of(), Set.of(), List.of());
		var level = Minecraft.getInstance().level;
		List<Vec3> anchors = new ArrayList<>(stops.size() + 1);
		anchors.add(anchor);
		for (Vec3 stop : stops) anchors.add(navigationTarget(biome, stop).add(0.5, 0.0, 0.5));
		Map<Vec3Edge, Boolean> clearEdges = new HashMap<>();
		BiPredicate<Vec3, Vec3> edgeAllowed = biome == null && level == null ? null : (from, to) -> {
			if (!routeNodeAllowed(biome, from) || !routeNodeAllowed(biome, to)) return false;
			if (level == null) return true;
			Vec3Edge key = orderedEdge(from, to);
			return clearEdges.computeIfAbsent(key, ignored -> RouteRenderer.graphEdgeClear(level, key.first, key.second));
		};
		List<Vec3> line = new ArrayList<>();
		Set<Integer> gaps = new java.util.HashSet<>();
		List<RouteLineIssue> issues = new ArrayList<>();
		for (int i = 0; i + 1 < anchors.size(); i++) {
			Vec3 from = anchors.get(i);
			Vec3 to = anchors.get(i + 1);
			boolean gate = biome != null && stops.get(i).equals(biome.gate());
			boolean viaGate = !costing && biome != null && i == 0 && RouteController.needsLandingGateGuide() && !gate;
			var painting = SafariTerrain.painting(biome, stops.get(i));
			List<Vec3> segment;
			BiPredicate<Vec3, Vec3> approach = (a,b) -> level == null || RouteRenderer.graphEdgeClear(level,a,b);
			List<Vec3> recorded=costing?RecordedLegPaths.costRoute(biome,sourceIdentity,stops.get(i),from):RecordedLegPaths.route(biome,i==0?RouteController.legOrigin(stops.get(i)):stops.get(i-1),stops.get(i),from);
            if (!recorded.isEmpty()) segment=recorded;
            else if (gate) { segment=pathBetween(null,from,to,approach); if(segment.isEmpty())segment=joinCorridor(null,from,to,approach); }
			else if (viaGate) {
				Vec3 gateFeet = navigationTarget(biome,biome.gate()).add(.5,0,.5);
				List<Vec3> boatPath=pathBetween(null,from,gateFeet,approach);
				if(boatPath.isEmpty())boatPath=joinCorridor(null,from,gateFeet,approach);
				segment = joined(boatPath, routeSegment(biome,gateFeet,to,edgeAllowed,firstStopNumber+i));
			} else if (painting != null) {
				var tail = new ArrayList<Vec3>();
				Vec3 attach=painting.anchor();
				if(stops.get(i).equals(new Vec3(17,80,-74)) && from.y<79.5) {
					attach=new Vec3(17,78,-67); // mapped lower-side entry to the painting vine
					tail.add(attach);
					for (Vec3 floor : List.of(new Vec3(17,77,-67),new Vec3(17,77,-70),new Vec3(18,77,-70),new Vec3(18,77,-72)))
						tail.add(floor.add(.5,0,.5));
					tail.add(new Vec3(18.5,79,-71.5)); // cached vine block
					tail.add(new Vec3(18.5,80,-71.5));
				} else tail.add(attach);
				for (var cell : painting.path()) appendDistinct(tail,cell.add(.5,0,.5));
				appendDistinct(tail,to);
				List<Vec3> head=pathBetween(biome,from,attach,edgeAllowed);
                if(biome==Biome.HAUNTED && stops.get(i).equals(new Vec3(6,79,-83))){
                    Vec3 door=SafariData.HAUNTED_ENTRANCE_DOOR.add(.5,0,.5);
                    List<Vec3> before=pathBetween(biome,from,door,edgeAllowed);if(before.isEmpty())before=joinCorridor(biome,from,door,edgeAllowed);
                    List<Vec3> inside=pathBetween(biome,door,attach,edgeAllowed);if(inside.isEmpty())inside=joinCorridor(biome,door,attach,edgeAllowed);
                    head=joined(before,inside);
                }
				if(head.isEmpty()) head=joinCorridor(biome,from,attach,edgeAllowed);
				segment = pathEdgesAllowed(tail,edgeAllowed) ? joined(head,tail) : List.of();
                if(!stops.get(i).equals(new Vec3(17,80,-74)) && level!=null) {
                    List<Vec3> before=segment;
                    segment=PaintingPathJoin.join(from,tail,segment,(a,b)->RouteRenderer.walkableDirectEdgeClear(level,a,b,biome));
                    if(!segment.equals(before))ChipsCrittersClient.LOGGER.debug("Painting suffix join: target={} from={} oldPoints={} newPoints={}",stops.get(i),from,before.size(),segment.size());
                }
				if(segment.isEmpty()) segment=routeSegment(biome,from,to,edgeAllowed,firstStopNumber+i);
			} else segment = routeSegment(biome,from,to,edgeAllowed,firstStopNumber+i);
			if (segment.isEmpty()) {
                // A blocked far edge must not erase the safe mapped prefix at the player.
                // This is mapped topology, never an unvalidated direct fallback.
                List<Vec3> raw=pathBetween(gate?null:biome,from,to,null);
                List<Vec3> prefix=new ArrayList<>();
                if(!raw.isEmpty())prefix.add(raw.getFirst());
                for(int edge=1;edge<raw.size();edge++){
                    Vec3 a=raw.get(edge-1),b=raw.get(edge);
                    if(level!=null && (!RouteRenderer.graphEdgeClear(level,a,b)
                       || (edge==1 && !RouteRenderer.supportedJoin(level,a,b) && b.y>=a.y)))break;
                    prefix.add(b);
                }
                for(Vec3 point:prefix)appendDistinct(line,point);
                if(!prefix.isEmpty() && prefix.getLast().distanceToSqr(to)<.01)continue;
                boolean waitingChunks=level!=null && (!level.hasChunkAt(BlockPos.containing(from))||!level.hasChunkAt(BlockPos.containing(to)));
                ChipsCrittersClient.LOGGER.debug("Route path missing: biome={}, gateApproach={}, from={}, to={}, waitingChunks={}",biome,gate||viaGate,from,to,waitingChunks);
                if (line.isEmpty()) line.add(from);
				gaps.add(line.size() - 1);
				line.add(to);
				issues.add(new RouteLineIssue(firstStopNumber + i, from, to,
						waitingChunks ? "waiting for route chunks" : "no biome-safe mapped path; direct path failed clearance or footing checks"));
				continue;
			}
			for (Vec3 point : segment) {
				if (line.isEmpty() || line.getLast().distanceToSqr(point) > 0.01) line.add(point);
			}
		}
		return new RouteLineResult(List.copyOf(line), Set.copyOf(gaps), List.copyOf(issues));
	}

	/** The route ends at the reachable floor beside a drop or wall painting. */
	public Vec3 navigationTarget(Biome biome, Vec3 stop) {
		return biome == null ? stop : SafariTerrain.standing(Minecraft.getInstance().level, biome, stop);
	}

	private boolean forceMappedRepair;
    public void requestMappedRepair(){forceMappedRepair=true;requestAlternateJoin();}
	private List<Vec3> routeSegment(Biome biome, Vec3 from, Vec3 to, BiPredicate<Vec3, Vec3> edgeAllowed, int stopNumber) {
		if (!routeNodeAllowed(biome, from) || !routeNodeAllowed(biome, to)) return List.of();
		if (biome == Biome.HAUNTED && to.distanceToSqr(new Vec3(2.5,77,-79.5)) < 1) {
            Vec3 door=SafariData.HAUNTED_ENTRANCE_DOOR.add(.5,0,.5);
            List<Vec3> tail=pathBetween(biome,door,to,edgeAllowed);
            if(tail.isEmpty()) tail=joinCorridor(biome,door,to,edgeAllowed);
            List<Vec3> head=pathBetween(biome,from,door,edgeAllowed);
            if(head.isEmpty()) head=joinCorridor(biome,from,door,edgeAllowed);
            return joined(head,tail);
        }
        if (biome == Biome.CAVERN || biome == Biome.HAUNTED) {
            List<Vec3> approachPath = VerifiedApproaches.from(biome, from, to);
            if (!approachPath.isEmpty() && pathEdgesAllowed(approachPath, edgeAllowed)) return approachPath;
        }
        if (biome == Biome.CAVERN && SafariData.isCavernCaveEntryLeg(from,to)) {
			List<Vec3> mapped=pathBetween(biome,from,to,edgeAllowed);
			return mapped.isEmpty() ? joinCorridor(biome,from,to,edgeAllowed) : mapped;
		}
		if (biome == Biome.CAVERN && SafariData.isCavernLadderLeg(from, to)) {
			return edgeAllowed == null || edgeAllowed.test(from, to) ? List.of(from, to) : List.of();
		}
		if (biome == Biome.FOREST && SafariData.isForestShortcutLeg(from, to)) return List.of(from, to);
		if (biome == Biome.FOREST && SafariData.isForestTreeConnectorLeg(from, to)) return List.of(from, to);
		// Distance is not a reason to reject a direct route: prove each sampled
		// point is walkable, in loaded chunks, and inside the same route phase.
		var level = Minecraft.getInstance().level;
		boolean samePhase = biome != Biome.CAVERN || SafariData.sameCavernRoutePhase(from, to);
		boolean clear = (coinCosting || !forceMappedRepair) && level != null && samePhase && RouteRenderer.walkableDirectEdgeClear(level, from, to, biome);
		if (coinCosting?clear:preferDirectForLeg(new DirectLegKey(biome, stopNumber, to), clear)) return List.of(from, to);
		if(!coinCosting)forceMappedRepair=false;
		List<Vec3> mapped=pathBetween(biome,from,to,edgeAllowed);
		return mapped.isEmpty() ? joinCorridor(biome,from,to,edgeAllowed) : mapped;
	}

	private boolean preferDirectForLeg(DirectLegKey key, boolean clear) {
		DirectDecision previous = directDecisions.get(key);
		if (previous == null) {
			directDecisions.put(key, new DirectDecision(clear, clear ? 1 : 0));
			return clear;
		}
		if (previous.direct() && !clear) {
			// Fall back immediately when movement or a chunk update invalidates a direct leg.
			directDecisions.put(key, new DirectDecision(false, 0));
			return false;
		}
		if (!previous.direct() && clear) {
			int confirmations = previous.consecutiveClearChecks() + 1;
			boolean promote = confirmations >= 2;
			directDecisions.put(key, new DirectDecision(promote, promote ? confirmations : confirmations));
			return promote;
		}
		if (!clear) directDecisions.put(key, new DirectDecision(false, 0));
		return previous.direct() && clear;
	}

	/** Named hill steps on this directed graph leg, in travel order. */
	public List<Vec3> forestHillSteps(Vec3 from, Vec3 to) {
		List<Vec3> steps = new ArrayList<>();
		for (Vec3 point : pathBetween(Biome.FOREST, from, to, null)) {
			for (Vec3 feet : SafariData.FOREST_HILL_FEET) {
				if (feet.add(0.5, 0, 0.5).equals(point)) appendDistinct(steps, feet);
			}
		}
		return steps.size() >= 2 ? List.copyOf(steps) : List.of();
	}

	private static boolean near(Vec3 point, Vec3 target, double radius) {
		return point.distanceToSqr(target) <= radius * radius;
	}

	private static Vec3Edge orderedEdge(Vec3 first, Vec3 second) {
		int comparison = Double.compare(first.x, second.x);
		if (comparison == 0) comparison = Double.compare(first.y, second.y);
		if (comparison == 0) comparison = Double.compare(first.z, second.z);
		return comparison <= 0 ? new Vec3Edge(first, second) : new Vec3Edge(second, first);
	}

	private Map<Integer, Double> distancesFrom(int source) {
		return distancesFrom(null, source);
	}

	private Map<Integer, Double> distancesFrom(Biome biome, int source) {
		return distanceCache.computeIfAbsent(new SourceKey(biome, source), key -> distancesFrom(key.biome, key.node, new HashMap<>()));
	}

	private Map<Integer, Double> distancesFrom(Biome biome, int source, Map<Integer, Integer> previous) {
		Map<Integer, Double> distances = new HashMap<>();
		PriorityQueue<QueueEntry> queue = new PriorityQueue<>((a, b) -> Double.compare(a.distance, b.distance));
		distances.put(source, 0.0);
		queue.add(new QueueEntry(source, 0.0));
		while (!queue.isEmpty()) {
			QueueEntry current = queue.poll();
			if (current.distance > distances.getOrDefault(current.id, Double.POSITIVE_INFINITY)) continue;
			Node node = nodes.get(current.id);
			if (node == null) continue;
			for (Map.Entry<Integer, Double> edge : node.neighbors.entrySet()) {
				Node neighbor = nodes.get(edge.getKey());
				if (neighbor == null || !routeNodeAllowed(biome, neighbor.position)) continue;
				double candidate = current.distance + edge.getValue();
				if (candidate < distances.getOrDefault(edge.getKey(), Double.POSITIVE_INFINITY)) {
					distances.put(edge.getKey(), candidate);
					previous.put(edge.getKey(), current.id);
					queue.add(new QueueEntry(edge.getKey(), candidate));
				}
			}
		}
		return distances;
	}
}

package dev.chipsxo.chipscritters;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

public final class RouteRenderer {
	private static final int ACTIVE_GOLD = ARGB.colorFromFloat(1.0f, 1.0f, 0.82f, 0.02f);
	private static final int TREE_JUMP_COLOR = ARGB.colorFromFloat(1.0f, 0.18f, 1.0f, 0.35f);
	private static final int FLOOR_DROP_COLOR = ARGB.colorFromFloat(1.0f, 1.0f, 0.82f, 0.02f);
	private static final int RITUAL_COLOR = ARGB.colorFromFloat(1.0f, 0.82f, 0.2f, 1.0f);
	private static final int HIDEONWALL_COLOR = ARGB.colorFromFloat(1.0f, 1.0f, 0.12f, 0.72f);
	private static final int GAZER_COLOR = ARGB.colorFromFloat(1.0f, 0.78f, 0.2f, 1.0f);
	private static final int OCEAN_COLOR = ARGB.colorFromFloat(1.0f, 0.0f, 0.85f, 1.0f);
	private static final int GEMZIE_COLOR = ARGB.colorFromFloat(1.0f, 0.78f, 0.1f, 1.0f);
	private static final int WATERFALL_EXIT_COLOR = ARGB.colorFromFloat(1.0f, 0.15f, 0.9f, 1.0f);
	private static final int WATERFALL_STEP_COLOR = ARGB.colorFromFloat(1.0f, 0.05f, 0.95f, 1.0f);
	private static final int WATERFALL_ACTIVE_STEP_COLOR = ARGB.colorFromFloat(1.0f, 1.0f, 0.78f, 0.05f);
	private static final int WATERFALL_RIBBON_COLOR = ARGB.colorFromFloat(1.0f, 0.0f, 0.68f, 1.0f);
	private static final int WATERFALL_RIBBON_HIGHLIGHT = ARGB.colorFromFloat(1.0f, 1.0f, 0.96f, 0.24f);
	private static final double ACTIVE_MARKER_HALF = 0.5;
	private static final double NEXT_MARKER_HALF = 0.5;
	private static final double WAYPOINT_MARKER_OPACITY = 0.25;
	private static final double FEET_LEVEL_RIBBON_OFFSET = 0.2;
	private static final double ROUTE_RIBBON_HALF_THICKNESS = 0.018;
	private static final double MAX_RENDERED_RIBBON_HALF_WIDTH = 0.345;
	
	private static final double MAX_RIBBON_CLEARANCE_LIFT = 1.5;
	private static final double RIBBON_CLEARANCE_LIFT_STEP = 0.1;
	private static final double MAX_CORNER_TRIM = 0.7;
	private static final int CORNER_CURVE_STEPS = 6;
	private static List<Vec3> cachedLine = List.of();
	private static List<Vec3> cachedRenderLine = List.of();
	private static ClientLevel cachedLevel;
	private static double[] segmentLifts = new double[0];
	private static double[] nodeLifts = new double[0];
	private static double cachedRibbonThickness = Double.NaN;
	private static int cacheAge;
	private record GraphEdgeKey(ClientLevel level, Vec3 first, Vec3 second) { }
	private record GraphEdgeValue(boolean clear, long tick) { }
	private static final Map<GraphEdgeKey, GraphEdgeValue> GRAPH_EDGE_CACHE = new LinkedHashMap<>(256, 0.75f, true);
	private static long graphCacheTick;

	private RouteRenderer() { }

	public static boolean hasSafeStart(){return segmentLifts.length>0 && segmentLifts[0]>=0;}
 static boolean recordingClear(List<Vec3> points){
  var c=Minecraft.getInstance();if(c==null||c.level==null)return false;
  for(int i=1;i<points.size();i++)if(!segmentIsClear(c,points.get(i-1).add(0,.2,0),points.get(i).add(0,.2,0),Math.min(MAX_RENDERED_RIBBON_HALF_WIDTH,ChipsConfig.get().ribbonThickness)))return false;
  return true;
 }
    static boolean dropRibbonClear(Vec3 a,Vec3 b){
        var c=Minecraft.getInstance();return c.level!=null&&loaded(c,a,b)&&segmentIsClear(c,a.add(0,.2,0),b.add(0,.2,0),Math.min(MAX_RENDERED_RIBBON_HALF_WIDTH,ChipsConfig.get().ribbonThickness));
    }
    static boolean dropBodyClear(Vec3 a,Vec3 b){
        var c=Minecraft.getInstance();if(c.level==null||!loaded(c,a,b))return false;
        int n=Math.max(1,(int)Math.ceil(a.distanceTo(b)/.2));
        for(int i=0;i<=n;i++){var p=a.lerp(b,(double)i/n);if(!c.level.noCollision(new AABB(p.x-.3,p.y+.05,p.z-.3,p.x+.3,p.y+1.7,p.z+.3)))return false;}
        return dropRibbonClear(a,b);
    }
 private static boolean loaded(Minecraft c,Vec3 a,Vec3 b){int n=Math.max(1,(int)Math.ceil(a.distanceTo(b)*4));for(int i=0;i<=n;i++)if(!c.level.hasChunkAt(BlockPos.containing(a.lerp(b,(double)i/n))))return false;return true;}
 public static void register() {
		LevelRenderEvents.BEFORE_TRANSLUCENT_TERRAIN.register(RouteRenderer::render);
		LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(RouteRenderer::renderOceanWaypointOverlay);
	}

	private static List<Vec3> sourceLine() { return DevMode.preview().isEmpty()?RouteController.line():DevMode.preview(); }
    private static List<Vec3> terrainSamples(List<Vec3> source) {
        if(source.size()<2)return source;
        var result=new java.util.ArrayList<Vec3>();result.add(source.getFirst());
        for(int i=1;i<source.size();i++){Vec3 a=source.get(i-1),b=source.get(i);int count=isUnapprovedVerticalSegment(a,b)?1:Math.max(1,(int)Math.ceil(a.distanceTo(b)/.25));for(int n=1;n<=count;n++)result.add(a.lerp(b,(double)n/count));}
        return List.copyOf(result);
    }
    public static void tick(Minecraft client) {
		graphCacheTick++;
		if (GRAPH_EDGE_CACHE.size() > 4096) invalidateGraphEdgeCache();
		if (!ChipsCrittersClient.isInCritterSafari()) {
			cachedLine = List.of();
			cachedRenderLine = List.of();
			cachedLevel = client.level;
			segmentLifts = new double[0];
			nodeLifts = new double[0];
			cacheAge = 0;
			return;
		}
		if ((HideyhoFinderPause.active()&&!HideyhoSearch.active())) return;
		List<Vec3> line = sourceLine();
		if (client.level == null || line.isEmpty()) {
			cachedLine = line;
			cachedRenderLine = line;
			cachedLevel = client.level;
			segmentLifts = new double[0];
			nodeLifts = new double[0];
			cachedRibbonThickness = ChipsConfig.get().ribbonThickness;
			cacheAge = 0;
			return;
		}
		double ribbonThickness = Math.min(MAX_RENDERED_RIBBON_HALF_WIDTH, ChipsConfig.get().ribbonThickness);
		if (RouteController.waitingForCavernWaterfall()) ribbonThickness = Math.min(MAX_RENDERED_RIBBON_HALF_WIDTH, ribbonThickness * 1.15);
		boolean changed = line != cachedLine || client.level != cachedLevel
				|| Double.compare(ribbonThickness, cachedRibbonThickness) != 0 || ++cacheAge >= 20;
		if (!changed) return;
		cacheAge = 0;
		cachedLine = line;
		cachedLevel = client.level;
		cachedRibbonThickness = ribbonThickness;
		boolean recorded=RecordedLegPaths.current() || !DevMode.preview().isEmpty();
        if(RouteController.dropGuidance()&&DevMode.preview().isEmpty()){
            cachedRenderLine=line;segmentLifts=new double[Math.max(0,line.size()-1)];nodeLifts=new double[line.size()];
            for(int i=0;i<segmentLifts.length;i++)if(!dropRibbonClear(line.get(i),line.get(i+1))){segmentLifts[i]=-1;RouteController.noteLineGap(i,line.get(i),line.get(i+1),"fixed drop ribbon obstructed or chunks not loaded; record this connector");}
            return;
        }
		List<Vec3> renderLine = recorded || RouteController.hasLineGaps() ? line : terrainSamples(smoothRouteLine(client, line, ribbonThickness));
		cachedRenderLine = renderLine;
		segmentLifts = new double[Math.max(0, renderLine.size() - 1)];
		java.util.Arrays.fill(segmentLifts, -1.0);
		nodeLifts = new double[renderLine.size()];
		for (int i = 0; i < segmentLifts.length; i++) {
			double lift = recorded ? (segmentIsClear(client,renderLine.get(i).add(0,FEET_LEVEL_RIBBON_OFFSET,0),renderLine.get(i+1).add(0,FEET_LEVEL_RIBBON_OFFSET,0),ribbonThickness)?0:-1) : findSegmentLift(client, renderLine.get(i), renderLine.get(i + 1), ribbonThickness);
			segmentLifts[i] = lift;
			if (lift >= 0.0) {
				nodeLifts[i] = Math.max(nodeLifts[i], lift);
				nodeLifts[i + 1] = Math.max(nodeLifts[i + 1], lift);
			}
		}
		for (int i = 0; i < segmentLifts.length; i++) {
			if (renderLine == line && RouteController.isLineGap(i)) {
				segmentLifts[i] = -1.0;
				continue;
			}
			if (!recorded && !RouteController.ladderClimbing() && !RouteController.waitingForCavernWaterfall() && !SafariData.isForestWaterEdge(renderLine.get(i),renderLine.get(i+1)) && isUnapprovedVerticalSegment(renderLine.get(i), renderLine.get(i + 1))) {
				segmentLifts[i] = -1.0;
				RouteController.noteLineGap(i, renderLine.get(i), renderLine.get(i + 1), "near-vertical segment is not a fixed ladder connector");
				continue;
			}
			if (segmentLifts[i] < 0.0) {
                if(!loaded(client,renderLine.get(i),renderLine.get(i+1))){RouteController.onRouteChunksChanged();continue;}
                if(!recorded) RouteController.requestMappedClipRepair();
				RouteController.noteLineGap(i, renderLine.get(i), renderLine.get(i+1), "terrain/ribbon collision did not clear within 1.5 blocks, or chunk unavailable");
				continue;
			}
			Vec3 from = renderLine.get(i).add(0.0, FEET_LEVEL_RIBBON_OFFSET + nodeLifts[i], 0.0);
			Vec3 to = renderLine.get(i + 1).add(0.0, FEET_LEVEL_RIBBON_OFFSET + nodeLifts[i + 1], 0.0);
			if (!segmentIsClear(client, from, to, ribbonThickness)) {
				segmentLifts[i] = -1.0;
				RouteController.noteLineGap(i,from,to,"shared segment height obstructed");
			}
		}
	}

	private static List<Vec3> smoothRouteLine(Minecraft client, List<Vec3> source, double ribbonThickness) {
		if (source.size() < 3 || source.stream().anyMatch(SafariData::forestWaterColumn)) return source;
		if (RouteController.hasLineGaps()) return source;
		List<Vec3> smoothed = new java.util.ArrayList<>(source.size() * 2);
		smoothed.add(source.getFirst());
		for (int i = 1; i + 1 < source.size(); i++) {
			Vec3 previous = source.get(i - 1);
			Vec3 corner = source.get(i);
			Vec3 next = source.get(i + 1);
			double incomingLength = previous.distanceTo(corner);
			double outgoingLength = corner.distanceTo(next);
			if (incomingLength < 0.8 || outgoingLength < 0.8) {
				appendDistinct(smoothed, corner);
				continue;
			}
			Vec3 incoming = corner.subtract(previous).scale(1.0 / incomingLength);
			Vec3 outgoing = next.subtract(corner).scale(1.0 / outgoingLength);
			if (incoming.dot(outgoing) > 0.96) {
				appendDistinct(smoothed, corner);
				continue;
			}
			double trim = Math.min(MAX_CORNER_TRIM, Math.min(incomingLength, outgoingLength) * 0.25);
			Vec3 entry = corner.subtract(incoming.scale(trim));
			Vec3 exit = corner.add(outgoing.scale(trim));
			List<Vec3> curve = new java.util.ArrayList<>(CORNER_CURVE_STEPS + 1);
			for (int step = 0; step <= CORNER_CURVE_STEPS; step++) {
				double t = step / (double) CORNER_CURVE_STEPS;
				double oneMinus = 1.0 - t;
				curve.add(entry.scale(oneMinus * oneMinus).add(corner.scale(2.0 * oneMinus * t)).add(exit.scale(t * t)));
			}
			boolean clear = walkableCurveSegment(client, smoothed.getLast(), entry, ribbonThickness);
			for (int step = 1; clear && step < curve.size(); step++) {
				clear = walkableCurveSegment(client, curve.get(step - 1), curve.get(step), ribbonThickness);
			}
			if (!clear) {
				appendDistinct(smoothed, corner);
				continue;
			}
			appendDistinct(smoothed, entry);
			for (int step = 1; step < curve.size(); step++) appendDistinct(smoothed, curve.get(step));
		}
		appendDistinct(smoothed, source.getLast());
		return List.copyOf(smoothed);
	}

	private static boolean segmentLiftExists(Minecraft client, Vec3 from, Vec3 to, double ribbonThickness) {
		int liftSteps = (int) Math.round(MAX_RIBBON_CLEARANCE_LIFT / RIBBON_CLEARANCE_LIFT_STEP);
		for (int step = 0; step <= liftSteps; step++) {
			double lift = step * RIBBON_CLEARANCE_LIFT_STEP;
			if (segmentIsClear(client,
					from.add(0.0, FEET_LEVEL_RIBBON_OFFSET + lift, 0.0),
					to.add(0.0, FEET_LEVEL_RIBBON_OFFSET + lift, 0.0), ribbonThickness)) return true;
		}
		return false;
	}

	private static boolean walkableCurveSegment(Minecraft client, Vec3 from, Vec3 to, double width) {
		return segmentLiftExists(client, from, to, width)
				&& (client.level == null || walkableDirectEdgeClear(client.level, from, to, RouteController.biome()));
	}

	private static void appendDistinct(List<Vec3> points, Vec3 point) {
		if (points.isEmpty() || points.getLast().distanceToSqr(point) > 0.01) points.add(point);
	}

	private static void render(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context) {
		if (!ChipsCrittersClient.isInCritterSafari() || (HideyhoFinderPause.active()&&!HideyhoSearch.active())
				|| (!RouteController.active() && RouteController.assistBiome() == null && DevMode.preview().isEmpty())) return;
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) return;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);
		var collector = context.submitNodeCollector();
		ChipsConfig config = ChipsConfig.get();
		int ribbonColor = routeColor(config, false);
		int ribbonHighlight = routeColor(config, true);
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 liveFeet = client.player.getPosition(partial).add(0, FEET_LEVEL_RIBBON_OFFSET, 0);
		var cameraState = context.levelState().cameraRenderState;
		var look = cameraState.orientation.transform(new org.joml.Vector3f(0, 0, -1));
		double pixelsPerSlope = Math.max(Math.abs(cameraState.projectionMatrix.m11()) * client.getWindow().getHeight(),
				Math.abs(cameraState.projectionMatrix.m00()) * client.getWindow().getWidth()) / 2.0;
		RibbonView view = new RibbonView(camera, new Vec3(look.x, look.y, look.z),
				(client.player.isSwimming() || client.player.isCrouching() ? 5.0 : 8.0) * Math.sqrt(config.ribbonThickness / 0.12) / Math.max(1, pixelsPerSlope));
		collector.submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> {
			if (!config.routeRendering && !config.waypointRendering && DevMode.preview().isEmpty()) return;
			if (config.routeRendering || !DevMode.preview().isEmpty()) {
				var line = cachedRenderLine;
				var sourceLine = sourceLine();
				boolean waterfallRoute = RouteController.waitingForCavernWaterfall();
				boolean lowPose = client.player.isSwimming() || client.player.isCrouching();
				double visibleRibbonDistance = lowPose ? 176.0 : 112.0;
				int activeRibbonColor = waterfallRoute ? withOpacity(WATERFALL_RIBBON_COLOR, config.ribbonOpacity) : ribbonColor;
				int activeRibbonHighlight = waterfallRoute ? withOpacity(WATERFALL_RIBBON_HIGHLIGHT, Math.min(1.0, config.ribbonOpacity + 0.1)) : ribbonHighlight;
				double activeRibbonThickness = Math.min(MAX_RENDERED_RIBBON_HALF_WIDTH,
					waterfallRoute ? Math.max(config.ribbonThickness * 1.15, 0.045) : config.ribbonThickness);
				double routeDistance = 0.0;
				double motionPhase = System.nanoTime() * 0.000000001 * 1.6;
				boolean current = cachedLine == sourceLine && cachedLevel == client.level && !line.isEmpty();
				boolean hasHead = current && line.size() >= 2 && segmentLifts.length > 0 && segmentLifts[0] >= 0;
				Vec3 target = RouteController.displayTarget();
				Vec3 join = null;
				int firstSegment = 0;
				if (hasHead && DevMode.preview().isEmpty()) {
					// Advance along the contiguous start as the player passes a corner between path rebuilds.
					double budget = liveFeet.distanceTo(line.getFirst()) + 1;
					double best = Double.POSITIVE_INFINITY;
					for (int i = 0; i < segmentLifts.length && segmentLifts[i] >= 0 && budget > 0; i++) {
						Vec3 start = line.get(i).add(0, FEET_LEVEL_RIBBON_OFFSET + nodeLifts[i], 0);
						Vec3 end = line.get(i + 1).add(0, FEET_LEVEL_RIBBON_OFFSET + nodeLifts[i + 1], 0);
						double distance = closestPointOnSegment(liveFeet, start, end).distanceToSqr(liveFeet);
						if (distance < best) { best = distance; firstSegment = i; }
						budget -= start.distanceTo(end);
					}
					Vec3 a = line.get(firstSegment).add(0, FEET_LEVEL_RIBBON_OFFSET + nodeLifts[firstSegment], 0);
					Vec3 b = line.get(firstSegment + 1).add(0, FEET_LEVEL_RIBBON_OFFSET + nodeLifts[firstSegment + 1], 0);
					Vec3 closest = closestPointOnSegment(liveFeet, a, b);
					double left = closest.distanceTo(b);
					join = left <= 0.8 ? b : closest.lerp(b, 0.8 / left);
					if (liveFeet.distanceTo(join) > 8 || !segmentIsClear(client, liveFeet, join, activeRibbonThickness)) join = null;
				}
				// Keep a short, collision-clipped heading at the feet while a leg is rebuilding or obstructed.
				Vec3 lead = join;
				if (lead == null && target != null && !RouteController.paused() && !RouteController.waitingForForestBird()) {
					Vec3 guide=RecordedLegPaths.guide(liveFeet);
					Vec3 direction = (guide!=null?guide.add(0,FEET_LEVEL_RIBBON_OFFSET,0):SafariGraph.get().navigationTarget(RouteController.biome(),target).add(0.5, FEET_LEVEL_RIBBON_OFFSET, 0.5)).subtract(liveFeet);
					lead = liveFeet;
					for (double d = 0.10; d <= Math.min(1.25, direction.length()); d += 0.10) {
						Vec3 candidate = liveFeet.add(direction.normalize().scale(d));
						if (!segmentIsClear(client, lead, candidate, activeRibbonThickness)) break;
						lead = candidate;
					}
				}
				if (lead != null) drawTwistedRibbon(pose, buffer, liveFeet, lead, null, null, 0,
						motionPhase, view, activeRibbonThickness, visibleRibbonDistance, activeRibbonColor, activeRibbonHighlight);
				for (int i = firstSegment; i + 1 < line.size(); i++) {
					if (cachedLine != sourceLine || cachedLevel != client.level || i >= segmentLifts.length || segmentLifts[i] < 0.0) {
						routeDistance += line.get(i).distanceTo(line.get(i + 1));
						continue;
					}
					Vec3 from = line.get(i).add(0, FEET_LEVEL_RIBBON_OFFSET + nodeLifts[i], 0);
					Vec3 to = line.get(i + 1).add(0, FEET_LEVEL_RIBBON_OFFSET + nodeLifts[i + 1], 0);
					if (i == firstSegment && join != null) from = join;
					drawTwistedRibbon(pose, buffer, from, to,
						i > 0 ? line.get(i - 1).add(0, FEET_LEVEL_RIBBON_OFFSET + nodeLifts[i - 1], 0) : null,
						i + 2 < line.size() ? line.get(i + 2).add(0, FEET_LEVEL_RIBBON_OFFSET + nodeLifts[i + 2], 0) : null,
						routeDistance, motionPhase, view, activeRibbonThickness, visibleRibbonDistance,
						activeRibbonColor, activeRibbonHighlight);
					routeDistance += from.distanceTo(to);
				}
			}
			if (config.waypointRendering) {
				if (RouteController.active() && !config.hideReachedWaypoints) {
					for (Vec3 reached : RouteController.reachedStops()) {
						Vec3 marker = markerCenter(SafariGraph.get().navigationTarget(RouteController.biome(),reached));
						if (marker.distanceToSqr(camera) <= 90.0 * 90.0) drawCube(pose, buffer, marker, 0.5, withOpacity(0xFF7E8991, WAYPOINT_MARKER_OPACITY));
					}
				}
				if (RouteController.active()) {
					List<Vec3> visibleStops = RouteController.visibleStops();
					for (int i = 0; i < visibleStops.size(); i++) {
						Vec3 routeStop = visibleStops.get(i);

						if (SafariData.isOceanWaypoint(RouteController.biome(), routeStop)) continue;
						String instruction = RouteController.dropGuidance()?RouteController.dropInstruction():SafariData.waypointInstruction(RouteController.biome(), routeStop);
						boolean wumpa = SafariData.isWumpaWaypoint(RouteController.biome(), routeStop);
						boolean treeJump = "JUMP ON TREE".equals(instruction);
						boolean hideonwallPainting = SafariData.isHideonwallWaypoint(RouteController.biome(), routeStop);
						boolean gazer = SafariData.isGazerBedWaypoint(RouteController.biome(), routeStop);
						boolean snoozle = SafariData.isSnoozleWaypoint(RouteController.biome(), routeStop);
						boolean gemzie = SafariData.isGemzieWaypoint(RouteController.biome(), routeStop);
						boolean ritual = SafariData.isRitualWaypoint(RouteController.biome(), routeStop);
						boolean floorDrop = SafariData.isFloorDropWaypoint(RouteController.biome(), routeStop);
						Vec3 displayStop = SafariGraph.get().navigationTarget(RouteController.biome(), routeStop);
						Vec3 stop = markerCenter(displayStop);
						if (stop.distanceToSqr(camera) > 90.0 * 90.0) continue;
						double half = i == 0 ? ACTIVE_MARKER_HALF : NEXT_MARKER_HALF;
						int color = RouteController.biome() == Biome.CAVERN && routeStop.equals(SafariData.CAVERN_POND_STOP) ? 0xFF39D9FF
								: hideonwallPainting ? HIDEONWALL_COLOR
								: wumpa ? TREE_JUMP_COLOR
								: snoozle ? ARGB.colorFromFloat(1.0f, 0.15f, 1.0f, 0.2f)
								: treeJump ? TREE_JUMP_COLOR
								: gazer ? GAZER_COLOR
								: gemzie ? GEMZIE_COLOR
								: ritual && floorDrop ? ARGB.colorFromFloat(1.0f, 1.0f, 0.15f, 0.85f)
								: ritual ? RITUAL_COLOR
								: floorDrop ? FLOOR_DROP_COLOR
								: i == 0 ? ACTIVE_GOLD : ribbonColor;
						drawCube(pose, buffer, stop, half, withOpacity(color, WAYPOINT_MARKER_OPACITY));
						if (hideonwallPainting && !routeStop.equals(displayStop)) {
							// Indicate the painting face from the safe standing side; never put the cue inside a wall.
							Vec3 face=markerCenter(routeStop), eye=displayStop.add(.5,1.4,.5);
							for(int step=0;step<=12;step++){
								Vec3 cue=face.lerp(eye,step/12.0);
								if(!client.level.noCollision(AABB.ofSize(cue,.21,.21,.21))) continue;
								drawCube(pose,buffer,cue,.1,withOpacity(HIDEONWALL_COLOR,.8)); break;
							}
						}
					}
					if (RouteController.ladderClimbing() && RouteController.ladderTop() != null) {
						Vec3 top = markerCenter(RouteController.ladderTop());
						if (top.distanceToSqr(camera) <= 90.0 * 90.0) drawCube(pose, buffer, top, 0.5, withOpacity(ACTIVE_GOLD, WAYPOINT_MARKER_OPACITY));
					}
					if (RouteController.waitingForCavernWaterfall()) {
						List<Vec3> waterfallStops = SafariData.cavernWaterfallRouteStops();
					int activeWaterfallStep = SafariData.cavernWaterfallStepIndex(client.player.getY()) + 1;
						for (int i = 0; i < waterfallStops.size(); i++) {
							Vec3 marker = markerCenter(waterfallStops.get(i));
							if (marker.distanceToSqr(camera) <= 90.0 * 90.0) {
								int markerColor = i == waterfallStops.size() - 1 ? WATERFALL_EXIT_COLOR
										: i == activeWaterfallStep ? WATERFALL_ACTIVE_STEP_COLOR : WATERFALL_STEP_COLOR;
								drawCube(pose, buffer, marker, 0.5, withOpacity(markerColor, WAYPOINT_MARKER_OPACITY));
							}
						}
					}
				} else if (RouteController.assistBiome() != null) {
					Vec3 gate = markerCenter(RouteController.assistBiome().gate());
					if (gate.distanceToSqr(camera) <= 90.0 * 90.0 && isAirAt(client, gate.add(0.0, 0.75, 0.0))) drawCube(pose, buffer, gate, ACTIVE_MARKER_HALF, withOpacity(ACTIVE_GOLD, WAYPOINT_MARKER_OPACITY));
				}
			}
		});
		if (config.waypointRendering && RouteController.active()) {
			if (!config.hideReachedWaypoints) {
				List<Vec3> reachedStops = RouteController.reachedStops();
				for (int i = 0; i < reachedStops.size(); i++) {

					Vec3 marker = SafariGraph.get().navigationTarget(RouteController.biome(),reachedStops.get(i)).add(.5, 1.25, .5);
					if (marker.distanceToSqr(camera) > 90.0 * 90.0) continue;
					collector.submitNameTag(poseStack, marker, 0,
							Component.literal("✓ " + (i + 1) + " reached").withStyle(ChatFormatting.GRAY),
							false, 0x00F000F0, context.levelState().cameraRenderState);
				}
			}
            if(RouteController.dropGuidance()&&!RouteController.paused()&&RouteController.biome()==Biome.CAVERN){
                Vec3 drop=SafariData.CAVERN_DROP_START.add(.5,1.6,.5);
                if(drop.distanceToSqr(camera)<=120*120){
                    var text=Component.literal("DROP DOWN").withStyle(ChatFormatting.GOLD);
                    poseStack.pushPose();poseStack.translate(drop.x,drop.y,drop.z);poseStack.mulPose(context.levelState().cameraRenderState.orientation);poseStack.scale(.025F,-.025F,.025F);
                    collector.submitText(poseStack,-client.font.width(text)/2F,0,text.getVisualOrderText(),false,net.minecraft.client.gui.Font.DisplayMode.NORMAL,0x00F000F0,0xFFFFD05A,0xD0182028,0);
                    poseStack.popPose();
                }
            }
            List<Vec3> visibleStops = RouteController.visibleStops();
			for (int i = 0; i < visibleStops.size(); i++) {
				Vec3 routeStop = visibleStops.get(i);
                if(RouteController.dropGuidance())continue; // One compact drop label; detail stays in the arrow panel.

				if (SafariData.isOceanWaypoint(RouteController.biome(), routeStop)) continue;
				String instruction = RouteController.dropGuidance()?RouteController.dropInstruction():SafariData.waypointInstruction(RouteController.biome(), routeStop);
				Vec3 displayStop = SafariGraph.get().navigationTarget(RouteController.biome(),routeStop);
				Vec3 stop = displayStop.add(0.5, 1.25, 0.5);
				if (stop.distanceToSqr(camera) > 90.0 * 90.0 || (instruction == null && !isAirAt(client, stop))) continue;
				int globalStop = RouteController.nextStop() + i;
				boolean gazer = SafariData.isGazerBedWaypoint(RouteController.biome(), routeStop);
				String label = gazer ? gazerRouteLabel(RouteController.stops(), globalStop)
						: instruction != null ? instruction : config.troodonLabels ? config.learnedStopLabel(RouteController.biome(), routeStop) : null;
				String markerText = RouteController.dropGuidance()?RouteController.dropInstruction():RouteController.ladderGuidance()?RouteController.climbInstruction():Integer.toString(RouteController.biome()==Biome.HAUNTED?RouteController.displayStopNumber(i):globalStop+1) + (label == null ? "" : " " + label);
				if (RouteController.ladderGuidance()) markerText = RouteController.climbInstruction();
				else if (RouteController.hillGuidance()) markerText = RouteController.hillInstruction();
                else if (RouteController.routeLineGap() && i == 0) markerText += " · LINE GAP — FOLLOW CUBE";
				
				if (config.showDistanceLabels && client.player != null) markerText += String.format(java.util.Locale.ROOT, " · %.0fm", client.player.position().distanceTo(routeStop));
				if (config.showWaypointSource && RouteController.biome() != null) {
					markerText += config.isLearnedStop(RouteController.biome(), routeStop) ? " · LEARNED" : " · ROUTE";
				}
				boolean critterLabel = SafariData.isTroodonWaypoint(RouteController.biome(), routeStop)
						|| SafariData.isSnoozleWaypoint(RouteController.biome(), routeStop)
						|| SafariData.isWumpaWaypoint(RouteController.biome(), routeStop)
						|| gazer || (label != null && instruction == null);
				collector.submitNameTag(poseStack, stop, 0,
						styledWaypointTag(markerText, gazer ? label : instruction, critterLabel, i == 0),
						false, 0x00F000F0, context.levelState().cameraRenderState);
			}
			if (RouteController.ladderClimbing() && RouteController.ladderTop() != null) {
				Vec3 top = RouteController.ladderTop().add(0.5, 1.25, 0.5);
				if (top.distanceToSqr(camera) <= 90.0 * 90.0) {
					collector.submitNameTag(poseStack, top, 0,
							Component.literal(RouteController.climbInstruction() + " · TOP").withStyle(ChatFormatting.BOLD).withStyle(ChatFormatting.GOLD),
							false, 0x00F000F0, context.levelState().cameraRenderState);
				}
			}
			if (RouteController.waitingForCavernWaterfall()) {
				List<Vec3> waterfallStops = SafariData.cavernWaterfallRouteStops();
				int activeWaterfallStep = SafariData.cavernWaterfallStepIndex(client.player.getY());
				for (int i = 0; i < waterfallStops.size(); i++) {
				Vec3 marker = waterfallStops.get(i).add(0.0, 1.25, 0.0);
					if (marker.distanceToSqr(camera) <= 90.0 * 90.0) {
					String label = i == 0 ? "WATERFALL START" : i == waterfallStops.size() - 1
							? "↑ WATERFALL EXIT · Y=82" : "↑ WATERFALL " + i + "/" + (waterfallStops.size() - 1);
						ChatFormatting color = i == waterfallStops.size() - 1 || i == activeWaterfallStep ? ChatFormatting.GOLD : ChatFormatting.AQUA;
						collector.submitNameTag(poseStack, marker, 0,
								Component.literal(label).withStyle(ChatFormatting.BOLD).withStyle(color),
								false, 0x00F000F0, context.levelState().cameraRenderState);
					}
				}
			}
		} else if (config.waypointRendering && RouteController.assistBiome() != null) {
			Vec3 gate = RouteController.assistBiome().gate().add(0, 1.55, 0);
			if (gate.distanceToSqr(camera) <= 90.0 * 90.0 && isAirAt(client, gate)) {
				collector.submitNameTag(poseStack, gate, 0, styledWaypointTag("1", null, false, true), false, 0x00F000F0, context.levelState().cameraRenderState);
			}
		}
		poseStack.popPose();
	}

	private static void renderOceanWaypointOverlay(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context) {
		if (!ChipsCrittersClient.isInCritterSafari() || (HideyhoFinderPause.active()&&!HideyhoSearch.active())
				|| !ChipsConfig.get().waypointRendering || !RouteController.active() || RouteController.biome() != Biome.ICY) return;
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.player == null) return;
		Vec3 target = RouteController.visibleStops().stream()
				.filter(point -> SafariData.isOceanWaypoint(Biome.ICY, point)).findFirst().orElse(null);
		if (target == null) return;

		Vec3 marker = markerCenter(oceanSurfaceMarker(client, target));
		Vec3 camera = context.levelState().cameraRenderState.pos;
		if (marker.distanceToSqr(camera) > 90.0 * 90.0) return;
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		poseStack.translate(-camera.x, -camera.y, -camera.z);
		var collector = context.submitNodeCollector();
		collector.submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(),
				(pose, buffer) -> drawCube(pose, buffer, marker, ACTIVE_MARKER_HALF, withOpacity(OCEAN_COLOR, WAYPOINT_MARKER_OPACITY)));
		collector.submitNameTag(poseStack, marker.add(0.0, 0.75, 0.0), 0,
				styledWaypointTag((RouteController.nextStop() + 1) + " DIVE BELOW Y=45", "DIVE BELOW Y=45", false, true),
				false, 0x00F000F0, context.levelState().cameraRenderState);
		poseStack.popPose();
	}

	private static Vec3 oceanSurfaceMarker(Minecraft client, Vec3 target) {
		int x = BlockPos.containing(target).getX();
		int z = BlockPos.containing(target).getZ();
		int startY = BlockPos.containing(target).getY();
		int scanLimit = Math.min(client.level.getMaxY(), startY + 128);
		int highestFluidY = Integer.MIN_VALUE;
		for (int y = startY; y < scanLimit; y++) {
			if (!client.level.getBlockState(new BlockPos(x, y, z)).getFluidState().isEmpty()) highestFluidY = y;
		}
		return highestFluidY == Integer.MIN_VALUE ? target
				: new Vec3(target.x, highestFluidY + 1.0, target.z);
	}

	private static Vec3 markerCenter(Vec3 floorPosition) {
		return floorPosition.add(0.5, 0.5, 0.5);
	}

	private static String gazerRouteLabel(List<Vec3> routeStops, int routeIndex) {
		int total = (int) routeStops.stream().filter(point -> SafariData.isGazerBedWaypoint(Biome.HAUNTED, point)).count();
		int order = 0;
		for (int i = 0; i <= routeIndex && i < routeStops.size(); i++) {
			if (SafariData.isGazerBedWaypoint(Biome.HAUNTED, routeStops.get(i))) order++;
		}
		return "GAZER G" + order + "/" + total + " — SHARD";
	}

	private static Component styledWaypointTag(String text, String instruction, boolean critterLabel, boolean active) {
		ChatFormatting color;
		if ("HIDEONWALL PAINTING".equals(instruction)) color = ChatFormatting.LIGHT_PURPLE;
		else if (instruction != null && instruction.startsWith("GAZER G")) color = ChatFormatting.AQUA;
		else if (critterLabel || "JUMP ON TREE".equals(instruction) || "TROODON".equals(instruction) || "WUMPA".equals(instruction)
				|| "CLIMB THE VINES".equals(instruction) || "SCRAPPY".equals(instruction) || "JUMP".equals(instruction) || "SNOOZLE".equals(instruction) || "CLIMB LADDER".equals(instruction)
				|| "HIDEYHO SEARCH".equals(instruction) || "CLIMB VINE TO HIDEONWALL".equals(instruction)
				|| "GAZER BED — GAZER SHARD".equals(instruction)) color = ChatFormatting.GREEN;
		else if (instruction != null && instruction.contains("GEMZIE")) color = ChatFormatting.LIGHT_PURPLE;
		else if (instruction != null && instruction.startsWith("DIVE BELOW")) color = ChatFormatting.AQUA;
		else if (instruction != null && instruction.contains("RITUAL")) color = ChatFormatting.LIGHT_PURPLE;
		else color = active ? ChatFormatting.GOLD : ChatFormatting.WHITE;
		return Component.literal(text).withStyle(ChatFormatting.BOLD).withStyle(color);
	}

	private static double findSegmentLift(Minecraft client, Vec3 from, Vec3 to, double ribbonHalfWidth) {
		int liftSteps = (int) Math.round(MAX_RIBBON_CLEARANCE_LIFT / RIBBON_CLEARANCE_LIFT_STEP);
		for (int step = 0; step <= liftSteps; step++) {
			double lift = step * RIBBON_CLEARANCE_LIFT_STEP;
			Vec3 liftedFrom = from.add(0.0, FEET_LEVEL_RIBBON_OFFSET + lift, 0.0);
			Vec3 liftedTo = to.add(0.0, FEET_LEVEL_RIBBON_OFFSET + lift, 0.0);
			if (segmentIsClear(client, liftedFrom, liftedTo, ribbonHalfWidth)) return lift;
		}
		return -1.0;
	}

	static synchronized boolean graphEdgeClear(ClientLevel level, Vec3 from, Vec3 to) {
		Minecraft client = Minecraft.getInstance();
		if (client.level != level) return false;
		if (isUnapprovedVerticalSegment(from, to) && !SafariData.isForestWaterEdge(from,to) && !SafariData.isPaintingVineEdge(from,to)) return false;
		Vec3 first = comparePoint(from, to) <= 0 ? from : to;
		Vec3 second = first == from ? to : from;
		GraphEdgeKey key = new GraphEdgeKey(level, first, second);
		GraphEdgeValue cached = GRAPH_EDGE_CACHE.get(key);
		if (cached != null && graphCacheTick - cached.tick() <= 40) return cached.clear();
		int liftSteps = (int) Math.round(MAX_RIBBON_CLEARANCE_LIFT / RIBBON_CLEARANCE_LIFT_STEP);
		for (int step = 0; step <= liftSteps; step++) {
			double lift = step * RIBBON_CLEARANCE_LIFT_STEP;
			Vec3 liftedFrom = from.add(0.0, FEET_LEVEL_RIBBON_OFFSET + lift, 0.0);
			Vec3 liftedTo = to.add(0.0, FEET_LEVEL_RIBBON_OFFSET + lift, 0.0);
			if (segmentIsClear(client, liftedFrom, liftedTo, ChipsConfig.get().ribbonThickness, true)) {
				GRAPH_EDGE_CACHE.put(key, new GraphEdgeValue(true, graphCacheTick));
				return true;
			}
		}
		GRAPH_EDGE_CACHE.put(key, new GraphEdgeValue(false, graphCacheTick));
		return false;
	}

	private static int comparePoint(Vec3 first, Vec3 second) {
		int comparison = Double.compare(first.x, second.x);
		if (comparison == 0) comparison = Double.compare(first.y, second.y);
		if (comparison == 0) comparison = Double.compare(first.z, second.z);
		return comparison;
	}

	public static synchronized void invalidateGraphEdgeCache() { GRAPH_EDGE_CACHE.clear(); }

	static boolean isUnapprovedVerticalSegment(Vec3 from, Vec3 to) {
		double horizontal = Math.hypot(to.x - from.x, to.z - from.z);
		return horizontal < 0.65 && Math.abs(to.y - from.y) > 1.25
				&& !SafariData.isCavernLadderLeg(from, to) && !SafariData.isPaintingVineEdge(from,to) && !SafariData.isHauntedWaterExitEdge(from,to) && !SafariData.isCavernOpenDropEdge(from,to);
	}

	// Graph coordinates may sit a block above the walking surface. For short off-graph
	// joins, follow continuous loaded support instead of connecting across open air.
	static boolean supportedJoin(ClientLevel level,Vec3 from,Vec3 to) {
		if(level==null || from.distanceToSqr(to)<.01) return true;
		int count=Math.max(1,(int)Math.ceil(from.distanceTo(to)*3));
		double previous=Double.NaN;
		for(int i=0;i<=count;i++){
			Vec3 p=from.lerp(to,(double)i/count);
			var cell=BlockPos.containing(p.x,p.y+.25,p.z);
			if(!level.hasChunkAt(cell))return false;
			double surface=Double.NaN;
			for(int dy=0;dy<=3;dy++){
				var q=cell.below(dy);var state=level.getBlockState(q);
				if(!state.getFluidState().isEmpty() || state.is(net.minecraft.tags.BlockTags.CLIMBABLE)){surface=q.getY();break;}
				var shape=state.getCollisionShape(level,q);
				if(shape.isEmpty())continue;
				double top=q.getY()+shape.max(net.minecraft.core.Direction.Axis.Y);
				if(top>p.y+1.05 || top<p.y-2.1) return false;
				surface=top; break;
			}
			if(!Double.isFinite(surface) || (Double.isFinite(previous) && Math.abs(surface-previous)>1.01))return false;
			previous=surface;
		}
		return true;
	}

	static boolean walkableDirectEdgeClear(ClientLevel level, Vec3 from, Vec3 to, Biome biome) {
		if (level == null || biome == null || isUnapprovedVerticalSegment(from, to)) return false;
		double length = from.distanceTo(to);
		int samples = Math.max(1, (int) Math.ceil(length * 3.0));
		int previousSupportY = Integer.MIN_VALUE;
		boolean descendingDrop = to.y < from.y && !SafariData.isUnsafeDirectDrop(biome, from, to);
		boolean supportedAtEnd = false;
		for (int i = 0; i <= samples; i++) {
			Vec3 point = from.lerp(to, i / (double) samples);
			if (!SafariData.isWithinBiomeArea(biome, point, 18.0)) return false;
			BlockPos centerFloor = BlockPos.containing(point.x, point.y - 0.05, point.z);
			if (!level.hasChunkAt(centerFloor)) return false;
			for (double dx : new double[]{-0.28, 0.0, 0.28}) {
				for (double dz : new double[]{-0.28, 0.0, 0.28}) {
					BlockPos feet = BlockPos.containing(point.x + dx, point.y + 0.1, point.z + dz);
					BlockPos body = BlockPos.containing(point.x + dx, point.y + 0.9, point.z + dz);
					BlockPos head = BlockPos.containing(point.x + dx, point.y + 1.1, point.z + dz);
					BlockPos top = BlockPos.containing(point.x + dx, point.y + 1.7, point.z + dz);
					BlockPos floor = BlockPos.containing(point.x + dx, point.y - 0.05, point.z + dz);
					if (!level.hasChunkAt(feet) || !level.hasChunkAt(body) || !level.hasChunkAt(head)
							|| !level.hasChunkAt(top) || !level.hasChunkAt(floor)) return false;
					if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
							|| !level.getBlockState(body).getCollisionShape(level, body).isEmpty()
							|| !level.getBlockState(head).getCollisionShape(level, head).isEmpty()
							|| !level.getBlockState(top).getCollisionShape(level, top).isEmpty()) return false;
					boolean floorSupported = !level.getBlockState(floor).getCollisionShape(level, floor).isEmpty();
					if (i == samples && dx == 0.0 && dz == 0.0) supportedAtEnd = floorSupported || level.getFluidState(feet).is(net.minecraft.tags.FluidTags.WATER);
					if (!floorSupported && !descendingDrop && !level.getFluidState(feet).is(net.minecraft.tags.FluidTags.WATER)) return false;
				}
			}
			if (level.getFluidState(BlockPos.containing(point)).is(net.minecraft.tags.FluidTags.WATER)) previousSupportY = Integer.MIN_VALUE;
			boolean centerSupported = !level.getBlockState(centerFloor).getCollisionShape(level, centerFloor).isEmpty();
			if (centerSupported) {
				if (previousSupportY != Integer.MIN_VALUE && centerFloor.getY() - previousSupportY > 1
						&& !hasStairOrSlabSupport(level, point)) return false;
				previousSupportY = centerFloor.getY();
			}
		}
		return supportedAtEnd;
	}

	private static boolean hasStairOrSlabSupport(ClientLevel level, Vec3 point) {
		BlockPos floor = BlockPos.containing(point.x, point.y - 0.05, point.z);
		if (!level.hasChunkAt(floor)) return false;
		String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(level.getBlockState(floor).getBlock()).getPath();
		return path.endsWith("_stairs") || path.endsWith("_slab");
	}

	private static boolean segmentIsClear(Minecraft client, Vec3 from, Vec3 to, double ribbonHalfWidth) {
		return segmentIsClear(client, from, to, ribbonHalfWidth, false);
	}

	private static boolean segmentIsClear(Minecraft client, Vec3 from, Vec3 to, double ribbonHalfWidth, boolean allowUnloaded) {
		int samples = Math.max(1, (int) Math.ceil(from.distanceTo(to) * 4.0));
		Vec3 tangent = to.subtract(from).normalize();
		Vec3 side = new Vec3(-tangent.z, 0.0, tangent.x);
		if (side.lengthSqr() < 1.0E-6) side = new Vec3(1.0, 0.0, 0.0);
		else side = side.normalize();
		Vec3 up = side.cross(tangent).normalize();
		double clearance = Math.max(ribbonHalfWidth, 0.105) + 0.025;
		for (int i = 0; i <= samples; i++) {
			Vec3 point = from.lerp(to, i / (double) samples);
			if (!isAirAt(client, point, allowUnloaded)) return false;
			for (int sideSign = -1; sideSign <= 1; sideSign += 2) {
				for (int upSign = -1; upSign <= 1; upSign += 2) {
					Vec3 offset = side.scale(sideSign * clearance).add(up.scale(upSign * clearance));
					if (!isAirAt(client, point.add(offset), allowUnloaded)) return false;
				}
				if (!isAirAt(client, point.add(side.scale(sideSign * clearance)), allowUnloaded)
						|| !isAirAt(client, point.add(up.scale(sideSign * clearance)), allowUnloaded)) return false;
			}
		}
		return true;
	}

	private static void drawTwistedRibbon(PoseStack.Pose pose, VertexConsumer consumer, Vec3 from, Vec3 to,
			Vec3 previous, Vec3 nextPoint, double distanceAtStart, double motionPhase, RibbonView view,
			double ribbonHalfWidth, double visibleRibbonDistance, int routeColor, int highlightColor) {
		Vec3 delta = to.subtract(from);
		double length = delta.length();
		if (length < 1.0E-4) return;
		Vec3 forward = delta.scale(1.0 / length);
		Vec3 startTangent = previous == null ? forward : to.subtract(previous).normalize();
		Vec3 endTangent = nextPoint == null ? forward : nextPoint.subtract(from).normalize();
		if (startTangent.dot(forward) < 0.0) startTangent = forward;
		if (endTangent.dot(forward) < 0.0) endTangent = forward;
		int steps = Math.max(1, (int) Math.ceil(length / 0.32));
		for (int step = 0; step < steps; step++) {
			double t0 = step / (double) steps;
			double t1 = (step + 1) / (double) steps;
			double d0 = distanceAtStart + length * t0;
			double d1 = distanceAtStart + length * t1;
			Vec3 center0 = from.lerp(to, t0);
			Vec3 center1 = from.lerp(to, t1);
			if (center0.distanceToSqr(view.camera()) > visibleRibbonDistance * visibleRibbonDistance
					&& center1.distanceToSqr(view.camera()) > visibleRibbonDistance * visibleRibbonDistance) continue;
			Vec3 tangent0 = interpolatedTangent(startTangent, endTangent, t0, forward);
			Vec3 tangent1 = interpolatedTangent(startTangent, endTangent, t1, forward);
			Vec3[] ring0 = ribbonRing(center0, tangent0, d0, motionPhase, view.halfWidth(center0, ribbonHalfWidth));
			Vec3[] ring1 = ribbonRing(center1, tangent1, d1, motionPhase, view.halfWidth(center1, ribbonHalfWidth));
			for (int edge = 0; edge < 4; edge++) {
				int next = (edge + 1) % 4;
				drawQuad(pose, consumer, ring0[edge], ring0[next], ring1[next], ring1[edge], edge == 1 ? highlightColor : routeColor);
			}
		}
	}

	record RibbonView(Vec3 camera, Vec3 forward, double pixelSlope) {
		double halfWidth(Vec3 point, double requested) {
			Vec3 offset = point.subtract(camera);
			double depth = Math.max(0, offset.dot(forward));
			// Use camera-space depth and the current projection, including off-axis foreshortening.
			return Math.min(requested, pixelSlope * depth * depth / Math.max(0.001, depth + offset.length()));
		}
	}

	private static Vec3 closestPointOnSegment(Vec3 point, Vec3 start, Vec3 end) {
		Vec3 span = end.subtract(start);
		double lengthSquared = span.lengthSqr();
		if (lengthSquared < 1.0E-8) return start;
		double t = Math.max(0.0, Math.min(1.0, point.subtract(start).dot(span) / lengthSquared));
		return start.add(span.scale(t));
	}

	private static Vec3 interpolatedTangent(Vec3 start, Vec3 end, double amount, Vec3 fallback) {
		Vec3 tangent = start.lerp(end, amount);
		return tangent.lengthSqr() < 1.0E-6 ? fallback : tangent.normalize();
	}

	private static Vec3[] ribbonRing(Vec3 center, Vec3 tangent, double distance, double motionPhase, double ribbonHalfWidth) {
		Vec3 side = new Vec3(-tangent.z, 0.0, tangent.x);
		if (side.lengthSqr() < 1.0E-6) side = new Vec3(1.0, 0.0, 0.0);
		else side = side.normalize();
		Vec3 up = side.cross(tangent).normalize();
		double angle = distance * 0.75 + motionPhase;
		Vec3 widthAxis = side.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
		Vec3 thicknessAxis = tangent.cross(widthAxis).normalize();
		Vec3 width = widthAxis.scale(ribbonHalfWidth);
		Vec3 thickness = thicknessAxis.scale(Math.min(ROUTE_RIBBON_HALF_THICKNESS, ribbonHalfWidth * 0.35));
		return new Vec3[]{
				center.add(width).add(thickness),
				center.subtract(width).add(thickness),
				center.subtract(width).subtract(thickness),
				center.add(width).subtract(thickness)
		};
	}

	private static void drawQuad(PoseStack.Pose pose, VertexConsumer consumer, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
		consumer.addVertex(pose, (float) a.x, (float) a.y, (float) a.z).setColor(color);
		consumer.addVertex(pose, (float) b.x, (float) b.y, (float) b.z).setColor(color);
		consumer.addVertex(pose, (float) c.x, (float) c.y, (float) c.z).setColor(color);
		consumer.addVertex(pose, (float) d.x, (float) d.y, (float) d.z).setColor(color);
	}

	private static int routeColor(ChipsConfig config, boolean highlight) {
		float red;
		float green;
		float blue;
		if (highlight) {
			switch (Math.floorMod(config.routeColorPreset, 4)) {
				case 1 -> { red = 0.65f; green = 0.95f; blue = 1.0f; }
				case 2 -> { red = 1.0f; green = 0.62f; blue = 1.0f; }
				case 3 -> { red = 0.74f; green = 1.0f; blue = 0.82f; }
				default -> { red = 1.0f; green = 0.42f; blue = 0.26f; }
			}
		} else {
			switch (Math.floorMod(config.routeColorPreset, 4)) {
				case 1 -> { red = 0.0f; green = 0.66f; blue = 1.0f; }
				case 2 -> { red = 0.62f; green = 0.12f; blue = 1.0f; }
				case 3 -> { red = 0.08f; green = 0.92f; blue = 0.28f; }
				default -> { red = 1.0f; green = 0.0f; blue = 0.02f; }
			}
		}
		float alpha = (float) Math.min(1.0, highlight ? config.ribbonOpacity + 0.1 : config.ribbonOpacity);
		return ARGB.colorFromFloat(alpha, red, green, blue);
	}

	private static int withOpacity(int argb, double opacity) {
		int alpha = (argb >>> 24) & 0xFF;
		float red = ((argb >>> 16) & 0xFF) / 255.0f;
		float green = ((argb >>> 8) & 0xFF) / 255.0f;
		float blue = (argb & 0xFF) / 255.0f;
		return ARGB.colorFromFloat((float) (alpha / 255.0 * Math.max(0.0, Math.min(1.0, opacity))), red, green, blue);
	}

	private static boolean isAirAt(Minecraft client, Vec3 point) {
		return isAirAt(client, point, false);
	}

	private static boolean isAirAt(Minecraft client, Vec3 point, boolean allowUnloaded) {
		BlockPos pos = BlockPos.containing(point.x, point.y, point.z);
		if (!client.level.hasChunkAt(pos)) return allowUnloaded;
		var shape = client.level.getBlockState(pos).getCollisionShape(client.level, pos);
		if (shape.isEmpty()) return true;
		if (shape == net.minecraft.world.phys.shapes.Shapes.block()) return false;
		Vec3 local = point.subtract(pos.getX(), pos.getY(), pos.getZ());
		for (AABB box : shape.toAabbs()) if (box.contains(local)) return false;
		return true;
	}

	private static void drawCube(PoseStack.Pose pose, VertexConsumer consumer, Vec3 center, double half, int color) {
		AABB box = new AABB(center.x - half, center.y - half, center.z - half, center.x + half, center.y + half, center.z + half);
		var level = Minecraft.getInstance().level;
		if (level == null || !level.hasChunkAt(BlockPos.containing(center)) || !level.noCollision(box.deflate(.001))) return;
		float[][] corners = {
				{(float)box.minX,(float)box.minY,(float)box.minZ}, {(float)box.maxX,(float)box.minY,(float)box.minZ},
				{(float)box.maxX,(float)box.maxY,(float)box.minZ}, {(float)box.minX,(float)box.maxY,(float)box.minZ},
				{(float)box.minX,(float)box.minY,(float)box.maxZ}, {(float)box.maxX,(float)box.minY,(float)box.maxZ},
				{(float)box.maxX,(float)box.maxY,(float)box.maxZ}, {(float)box.minX,(float)box.maxY,(float)box.maxZ}
		};
		int[][] faces = {{0,1,2,3},{5,4,7,6},{4,0,3,7},{1,5,6,2},{3,2,6,7},{4,5,1,0}};
		for (int[] face : faces) {
			for (int corner : face) {
				float[] vertex = corners[corner];
				consumer.addVertex(pose, vertex[0], vertex[1], vertex[2]).setColor(color);
			}
		}
	}
}

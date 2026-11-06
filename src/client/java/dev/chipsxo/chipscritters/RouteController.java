package dev.chipsxo.chipscritters;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

public final class RouteController {
	public static final int MAX_VISIBLE_STOPS_AHEAD = 2;
	private static final double ROUTE_LINE_REANCHOR_DISTANCE = 2.5;
	private static Biome biome;
	private static List<Vec3> stops = List.of();
 private static List<Vec3> runOrder = List.of();
	private static List<Vec3> line = List.of();
	private static Set<Integer> lineGaps = Set.of();
	private static Vec3 lineAnchor;
	private static int nextStop;
	private static boolean active;
	private static boolean gateVisited;
	private static boolean landingTransfer;
 private static Vec3 backReviewTarget;
 private static boolean backReviewLeft;
 private static boolean descentRepairAttempted;
 private static int dropStage;
    static final DoomspiralCountdown DOOM_COUNTDOWN=new DoomspiralCountdown();
    static final DoomspiralDings DOOM_DINGS=new DoomspiralDings();
    private static List<Vec3> dropFlight=List.of();
    private static Vec3 dropApproachAnchor;private static long dropBuiltAt;
    private static boolean skipManualHideyhoRewardReplan;
    private static long hauntedReplanAt;
    private static long hauntedReplanDeadline;
    private static long hauntedReplanSerial;
    private static boolean hauntedReplanBusy;
    private static Vec3 hauntedSettlePosition;
    private static String hauntedReplanReason;
 private static final Set<Vec3> manuallySkipped=new HashSet<>();
 private static final Set<Vec3> protectedPaintingsNoted=new HashSet<>();
	private static boolean waitingForForestBird;
	private static boolean waitingForCavernWaterfall;
	private enum LadderStage { NONE, APPROACH_BOTTOM, CLIMB_TO_TOP }
	private static LadderStage ladderStage = LadderStage.NONE;
	private static SafariData.CavernLadder activeLadder;
	private static Vec3 hillLegTarget;
	private static List<Vec3> hillSteps = List.of();
	private static int hillStep;
	private static boolean pendingHideyhoRefresh;
	private enum ForestJumpStage { NONE, APPROACH_JUMP_OFF, JUMP_TO_TREE }
	private static ForestJumpStage forestJumpStage = ForestJumpStage.NONE;
	private static boolean paused;
	private static Biome assistBiome;
	private static int lastPingedStop = -1;
	private static int lastSkippedStop = -1;
	private static Biome lastSkippedBiome;
	private static long skipCompletionDeadline;
	private static long startedAtMillis;
	private static final RouteRunBookkeeping RUN_TIMES = new RouteRunBookkeeping();
	private static int runStopCount;
	private static int outsideBiomeTicks;
	private static final int OUTSIDE_BIOME_CLEAR_TICKS = 40;
	private static final double BIOME_AREA_MARGIN = 64.0;
	private static final Set<String> LOGGED_LINE_GAPS = new HashSet<>();
	private static boolean routeLineGap;
 private static boolean routeLineWaitingChunks;
	private static boolean buildingRoute;
	private static long routeBuildGeneration;
	private static final ConcurrentLinkedQueue<BuildCompletion> BUILD_COMPLETIONS = new ConcurrentLinkedQueue<>();
	private static PendingLineBuild pendingLineBuild;
	private static long routeSnapshotNanos;
	private static long routeClaimNanos;
	private static boolean routePathRecheckPending;
	private static long lastChunkLineRefreshMillis;
	private static long lineGapDetectedMillis;
	private static long lineGapClearSince;
	private static long lastLineGapRecoveryMillis;
	private static int lineGapRetryCount;
	private static Vec3 gapTarget;
	private static long lastObservedGap;
	private record RelayWaypointClaim(Biome biome, Vec3 position) { }
	private static final List<RelayWaypointClaim> DEFERRED_RELAY_CLAIMS = new ArrayList<>();
	private record BuildCompletion(long generation, Biome biome, ClientLevel level, String lobbyKey, long runStateVersion,
			RoutePlanner.Input input, RoutePlanService.Result result, Throwable error) { }
	private static final class PendingLineBuild {
		private final Biome biome;
		private final List<Vec3> targets;
		private final int firstStopNumber;
		private final List<Vec3> points = new java.util.ArrayList<>();
		private final Set<Integer> gaps = new HashSet<>();
		private final List<SafariGraph.RouteLineIssue> issues = new java.util.ArrayList<>();
		private Vec3 anchor;
		private int index;
		private PendingLineBuild(Biome biome, Vec3 anchor, List<Vec3> targets, int firstStopNumber) {
			this.biome = biome;
			this.anchor = anchor;
			this.targets = List.copyOf(targets);
			this.firstStopNumber = firstStopNumber;
		}
	}

	private RouteController() { }

	public static void startFromHotspot(Biome selected) {
		start(selected, false);
	}

	public static void startFromGate(Biome selected) {
		start(selected, true);
	}

	public static void startFromArea(Biome selected) {
		start(selected, true);
	}

	public static void claimHotspot(Biome selected) {
		// A HOTSPOT announcement selects the route; it is not a biome entry event.
	}

	public static boolean needsLandingGateGuide() { return active && landingTransfer; }
	private static boolean landingApproach(Vec3 player) {
		return player.distanceToSqr(new Vec3(-50,69,22)) <= 48*48;
	}

	private static void start(Biome selected, boolean actualBiomeEntry) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || !ChipsCrittersClient.isInCritterSafari()) return;
		DevMode.routeChanged();
		long claimStarted = System.nanoTime();
		if (actualBiomeEntry) ClaimsManager.enterBiome(selected);
		if (selected == Biome.HAUNTED && WaypointRunLedger.HAUNTED.finished && !WaypointRunLedger.HAUNTED.replay) { if (active && biome != selected) stop(); return; }
		routeClaimNanos = System.nanoTime() - claimStarted;
		manuallySkipped.clear();protectedPaintingsNoted.clear();backReviewTarget=null; dropStage=0;dropFlight=List.of();dropApproachAnchor=null;descentRepairAttempted=false;hauntedReplanSerial++;hauntedReplanAt=0;hauntedReplanBusy=false;
        long generation = ++routeBuildGeneration;
		biome = selected;
		landingTransfer = !actualBiomeEntry && landingApproach(client.player.position());
		ChipsCrittersClient.LOGGER.info("Route load {} from={} landingTransfer={} gate={}",selected,client.player.position(),landingTransfer,selected.gate());
		forestJumpStage = ForestJumpStage.NONE;
		SafariGraph.get().resetDirectRouteChoices();
		lineGapDetectedMillis = 0L;
		lineGapRetryCount = 0;
		pendingLineBuild = null;
		ForestFeederTracker.reset();
		assistBiome = null;
		stops = List.of(selected.gate());
		nextStop = 0;
		lineAnchor = null;
		line = List.of();
		lineGaps = Set.of();
		gateVisited = WaypointRunLedger.isSatisfied(selected, selected.gate());
		waitingForForestBird = false;
		waitingForCavernWaterfall = false;
		ladderStage = LadderStage.NONE;
		activeLadder = null;
		hillLegTarget = null;
		hillSteps = List.of();
		pendingHideyhoRefresh = false;
		forestJumpStage = ForestJumpStage.NONE;
		routeLineGap = false; routeLineWaitingChunks=false;
		paused = false;
		lastPingedStop = -1;
		lastSkippedStop = -1;
		lastSkippedBiome = null;
		skipCompletionDeadline = 0L;
		long existingHauntedStart = biome == Biome.HAUNTED ? WaypointRunLedger.HAUNTED.started : 0L;
		startedAtMillis = RUN_TIMES.startOrResume(selected, System.currentTimeMillis(), existingHauntedStart);
		if (biome == Biome.HAUNTED && WaypointRunLedger.HAUNTED.started == 0L) WaypointRunLedger.HAUNTED.started = startedAtMillis;
		runStopCount = 0;
		buildingRoute = true;
		active = true;
		if(landingTransfer) line=buildRouteLine(client.player.position(),List.of(selected.gate()));
		outsideBiomeTicks = 0;
		WaypointRelay.updateConfiguration();
		long snapshotStarted = System.nanoTime();
		RoutePlanner.Input input = RoutePlanner.snapshot(selected, ChipsConfig.get());
		routeSnapshotNanos = System.nanoTime() - snapshotStarted;
		ClientLevel expectedLevel = client.level;
		String expectedLobby = WaypointRelay.lobbySessionKey();
		long expectedRunState = WaypointRunLedger.stateVersion();
		java.util.concurrent.CompletableFuture<RoutePlanService.Result> future = RoutePlanService.plan(input);
		future.whenComplete((result, error) -> client.execute(() -> BUILD_COMPLETIONS.add(new BuildCompletion(
				generation, selected, expectedLevel, expectedLobby, expectedRunState, input, result, error))));
		client.player.sendOverlayMessage(Component.literal("[ChipsCritters] " + selected.displayName() + " — Building route…"));
	}

	private static void applyPendingBuilds(Minecraft client) {
		BuildCompletion completion;
		while ((completion = BUILD_COMPLETIONS.poll()) != null) {
			if (completion.generation() != routeBuildGeneration || !active || !buildingRoute || biome != completion.biome()) continue;
			if (completion.error() != null || completion.result() == null) {
				buildingRoute = false;
				active = false;
				if (client.player != null) client.player.sendOverlayMessage(Component.literal("[ChipsCritters] Route build failed; use /sr refresh to try again."));
				ChipsCrittersClient.LOGGER.error("Route plan failed for {}", completion.biome(), completion.error());
				continue;
			}
			if (client.player == null || client.level != completion.level()) continue;
			Biome playerArea = SafariData.nearestBiomeArea(client.player.position(), 16.0);
			if (playerArea != null && playerArea != completion.biome() && !landingApproach(client.player.position())) {
				buildingRoute = false;
				active = false;
				continue;
			}
			boolean staleSnapshot = !completion.lobbyKey().equals(WaypointRelay.lobbySessionKey())
					|| completion.runStateVersion() != WaypointRunLedger.stateVersion()
					|| completion.input().mode() != ChipsConfig.get().mode;
			if (staleSnapshot) {
				buildingRoute = false;
				start(completion.biome(), false);
				continue;
			}
			long clientApplyStarted = System.nanoTime();
			long ledgerStarted = System.nanoTime();
			List<Vec3> planned = completion.result().stops();
            runOrder = List.copyOf(planned);
            if(completion.biome()==Biome.HAUNTED)WaypointRunLedger.HAUNTED.rememberTotal(hauntedTotalKey(completion.input().mode(),completion.input().uniquePaintings()),planned.size());
			List<Vec3> unvisited = WaypointRunLedger.withoutSatisfied(completion.biome(), planned);
			long ledgerNanos = System.nanoTime() - ledgerStarted;
			long relayStarted = System.nanoTime();
			List<Vec3> remaining = WaypointRelay.filterAlreadyClaimed(completion.biome(), unvisited);
			long relayNanos = System.nanoTime() - relayStarted;
			if(completion.biome()==Biome.HAUNTED)ChipsCrittersClient.LOGGER.info("Haunted Hideonwall coverage: mode={}, planned={}, afterLedger={}, afterRelay={}",completion.input().mode(),planned.stream().filter(p->SafariData.isHideonwallLocation(Biome.HAUNTED,p)).toList(),unvisited.stream().filter(p->SafariData.isHideonwallLocation(Biome.HAUNTED,p)).toList(),remaining.stream().filter(p->SafariData.isHideonwallLocation(Biome.HAUNTED,p)).toList());
            boolean relayClearedAllStops = ChipsConfig.get().relayEnabled && unvisited.size() > 1 && remaining.size() == 1;
			if (completion.biome() == Biome.ICY && WaypointRunLedger.icyWumpaSpawned()
					&& !WaypointRunLedger.isSatisfied(Biome.ICY, SafariData.icyWumpaWaypoint())) {
				remaining = List.of(SafariData.icyWumpaWaypoint());
			}
			if (biome == Biome.HAUNTED && WaypointRunLedger.HAUNTED.doom && !WaypointRunLedger.HAUNTED.replay) remaining = WaypointRunLedger.HAUNTED.gold ? List.of() : List.of(SafariData.GIMMIEGOLD);
			gateVisited = WaypointRunLedger.isSatisfied(completion.biome(), completion.biome().gate())
					|| (!remaining.isEmpty() && remaining.getFirst().distanceToSqr(completion.biome().gate()) > 4.0);
			if(biome==Biome.HAUNTED&&ChipsConfig.get().mode==ChipsConfig.Mode.UNIQUE)remaining=uniquePending(remaining);
            stops = List.copyOf(remaining);
            nextStop = 0;
			lineAnchor = client.player.position();
			long lineStarted = System.nanoTime();
			line = stops.isEmpty() ? List.of() : buildRouteLine(lineAnchor, upcomingStops(0));
			long lineNanos = System.nanoTime() - lineStarted;
			buildingRoute = false;UniqueGoldPlacement.invalidateOrder();
			runStopCount = stops.size();
			resumeLadderIfNeeded();
			resumeForestTreeIfNeeded(client.player.position());
			long applyNanos = System.nanoTime() - clientApplyStarted;
			ChipsCrittersClient.LOGGER.info("Route start timings {} mode={}: claim={}ms snapshot={}ms planner={}ms cacheHit={} ledgerFilter={}ms relayFilter={}ms initialLine={}ms clientApply={}ms",
					completion.biome(), completion.input().mode(), ms(routeClaimNanos), ms(routeSnapshotNanos),
					ms(completion.result().plannerNanos()), completion.result().cacheHit(), ms(ledgerNanos), ms(relayNanos),
					ms(lineNanos), ms(applyNanos));
			client.player.sendOverlayMessage(Component.literal(
					"[ChipsCritters] " + completion.biome().displayName() + " route loaded (" + displayStopCount() + " total stops; " + stops.size() + " available)."));
			if (relayClearedAllStops) client.player.sendOverlayMessage(Component.literal("[ChipsCritters] Relay marks every other "
					+ completion.biome().displayName() + " stop claimed in this lobby. Check the route before continuing."));
			if (stops.isEmpty() && completion.biome() != Biome.FOREST) complete(client, client.player.position(), "All shared route stops are already complete");
		}
	}

	private static double ms(long nanos) { return Math.round(nanos / 1_000_000.0 * 100.0) / 100.0; }

	public static void restoreClaims(java.util.Set<Biome> scope,boolean resetCaptures){
        if(HideyhoSearch.active()){commandFeedback("Use /sr find stop before restoring normal route claims.");return;}
        if(!ChipsCrittersClient.isInCritterSafari()){commandFeedback("Enter Critter Safari before restoring claims.");return;}
        int restored=0;for(Biome b:scope){
            int count=WaypointRunLedger.restoreBiome(b,resetCaptures)+WaypointRelay.restoreLocal(b);
            if(b==biome && nextStop>0)count=Math.max(1,count);
            if(count>0||resetCaptures){RUN_TIMES.disqualify(b);if(b==Biome.HAUNTED)WaypointRunLedger.HAUNTED.replay=true;}
            if(resetCaptures && b==Biome.FOREST)ForestFeederTracker.newRunStarted();restored+=count;
        }
        DEFERRED_RELAY_CLAIMS.removeIf(c->scope.contains(c.biome()));
        Biome resume=scope.contains(biome)?biome:SafariData.nearestBiomeArea(Minecraft.getInstance().player.position(),12);
        if(resume!=null && scope.contains(resume))start(resume,false);
        commandFeedback("Restored local waypoint state for "+scope+" ("+restored+" entries). "+(restored>0||resetCaptures?"PB disabled for restored biomes this run. ":"")+"Timer kept. Relay claims/sharing ignored locally for these biomes until a new run; no reset/claim sent to the relay. Captures "+(resetCaptures?"reset by your choice.":"preserved; catch-based exclusions remain."));
    }
	public static void restart() {
        if(HideyhoSearch.active()){commandFeedback("Use /sr find stop to return to the normal route before this command.");return;}
		if (Minecraft.getInstance().player == null) return;
		if (biome == null) {
			Minecraft.getInstance().player.sendSystemMessage(Component.literal("[ChipsCritters] No biome route is loaded yet."));
			return;
		}
		start(biome, false);
	}

	public static void onNewSafariRunStarted() {
        HideyhoSearch.newRound();DOOM_COUNTDOWN.reset();DOOM_DINGS.reset();
		reset();
		RUN_TIMES.newRun();
	}

	public static void stop() {
        HideyhoSearch.silentStop();UniqueGoldPlacement.reset();DOOM_COUNTDOWN.reset();DOOM_DINGS.reset();
        DevMode.routeChanged();
		routeBuildGeneration++;hauntedReplanSerial++;hauntedReplanAt=0;hauntedReplanBusy=false;
		buildingRoute = false;
		pendingLineBuild = null;
		active = false;
		waitingForForestBird = false;
		waitingForCavernWaterfall = false;
		ladderStage = LadderStage.NONE;
		activeLadder = null;
		hillLegTarget = null;
		hillSteps = List.of();
		pendingHideyhoRefresh = false;
		forestJumpStage = ForestJumpStage.NONE;
		paused = false;
		lastPingedStop = -1;
		lastSkippedStop = -1;
		lastSkippedBiome = null;
		skipCompletionDeadline = 0L;
		ForestFeederTracker.reset();
		assistBiome = null;
		line = List.of();
		lineGaps = Set.of();
		routeLineGap = false; routeLineWaitingChunks=false;
		lineAnchor = null;
		outsideBiomeTicks = 0;
		lineGapDetectedMillis = 0L;
		lineGapRetryCount = 0;
	}

	public static void reset() {
        HideyhoSearch.silentStop();UniqueGoldPlacement.reset();DOOM_COUNTDOWN.reset();DOOM_DINGS.reset();skipManualHideyhoRewardReplan=false;
        DevMode.routeChanged();
        manuallySkipped.clear();protectedPaintingsNoted.clear();backReviewTarget=null;dropStage=0;dropFlight=List.of();dropApproachAnchor=null;descentRepairAttempted=false;landingTransfer=false; routePathRecheckPending=false; lastMappedClipRepair=0; gapTarget=null; runOrder=List.of();
		routeBuildGeneration++;hauntedReplanSerial++;hauntedReplanAt=0;hauntedReplanBusy=false;
		buildingRoute = false;
		pendingLineBuild = null;
		active = false;
		biome = null;
		stops = List.of();
		line = List.of();
		lineGaps = Set.of();
		routeLineGap = false; routeLineWaitingChunks=false;
		lineAnchor = null;
		nextStop = 0;
		gateVisited = false;
		waitingForForestBird = false;
		waitingForCavernWaterfall = false;
		ladderStage = LadderStage.NONE;
		activeLadder = null;
		hillLegTarget = null;
		hillSteps = List.of();
		pendingHideyhoRefresh = false;
		forestJumpStage = ForestJumpStage.NONE;
		paused = false;
		lastPingedStop = -1;
		lastSkippedStop = -1;
		lastSkippedBiome = null;
		skipCompletionDeadline = 0L;
		ForestFeederTracker.reset();
		assistBiome = null;
		startedAtMillis = 0L;
		runStopCount = 0;
		outsideBiomeTicks = 0;
		DEFERRED_RELAY_CLAIMS.clear();
		LOGGED_LINE_GAPS.clear();
		routeLineGap = false; routeLineWaitingChunks=false;
		lineGapDetectedMillis = 0L;
		lineGapRetryCount = 0;
	}

	public static void commandFeedback(String message) {
        var player=Minecraft.getInstance().player;
        if(player!=null) player.sendSystemMessage(Component.literal("[ChipsCritters] " + message));
    }

	public static void pause() {
        if(HideyhoSearch.active()){HideyhoSearch.pause(true);commandFeedback("Hideyho search paused.");return;}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
		if (!active || paused) { commandFeedback(!active ? "No active route to pause." : "Route is already paused."); return; }
		paused = true;
		line = List.of();
		lineGaps = Set.of();
		routeLineGap = false; routeLineWaitingChunks=false;
		lineAnchor = null;
		client.player.sendOverlayMessage(Component.literal("[ChipsCritters] Route paused. Use /chipscritters resume to continue."));
	}

	public static void restoreAfterRecording(boolean refresh){paused=false;if(refresh && active)refreshVisibleRouteLine();}
	public static void resume() {
        if(HideyhoSearch.active()){HideyhoSearch.pause(false);commandFeedback("Hideyho search resumed.");return;}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
		if (!active || !paused) { commandFeedback(!active ? "No active route to resume." : "Route is already running."); return; }
		paused = false;
		refreshVisibleRouteLine();
		client.player.sendOverlayMessage(Component.literal("[ChipsCritters] Route resumed."));
	}

	private static void cancelHauntedReplan(){hauntedReplanSerial++;hauntedReplanAt=0;hauntedReplanBusy=false;}
    public static void skipCurrentStop() {
        if(HideyhoSearch.active()){HideyhoSearch.skip();return;}
        cancelHauntedReplan();
        DevMode.routeChanged();dropStage=0;dropFlight=List.of();dropApproachAnchor=null;descentRepairAttempted=false;
		
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
        if (!active) { commandFeedback("No active route to skip."); return; }
        if (buildingRoute) { commandFeedback("Route is building; try skip once it is ready."); return; }
        if (waitingForForestBird()) { commandFeedback("Waiting for Birdfeeder completion; use stop to abandon the route."); return; }
        if (nextStop >= stops.size()) { commandFeedback("No remaining stop to skip. Use undo during the final-skip countdown."); return; }
        if(goldLocked(stops.get(nextStop))){commandFeedback("Catch Doomspiral before the final Gimmiegold stop.");return;}
        ladderStage = LadderStage.NONE;
        activeLadder = null;
        hillLegTarget = null;
        hillSteps = List.of();
        forestJumpStage = ForestJumpStage.NONE;
		pendingLineBuild=null;
        lastSkippedStop = nextStop;
		lastSkippedBiome = biome;
		if(manuallySkipped.add(stops.get(nextStop)))runStopCount=Math.max(0,runStopCount-1);
        nextStop=RouteCursor.next(nextStop,stops.size());
        backReviewTarget=null;
		lastPingedStop = -1;
		
		waitingForCavernWaterfall = false;
		if (nextStop >= stops.size()) {
			clearRouteLine();
			lineAnchor = null;
			skipCompletionDeadline = System.currentTimeMillis() + 5000L;
			client.player.sendOverlayMessage(Component.literal("[ChipsCritters] Final stop skipped. Completes in 5s; use /chipscritters undo to restore it."));
			return;
		}
		if(!paused)refreshVisibleRouteLine();
		client.player.sendOverlayMessage(Component.literal("[ChipsCritters] Skipped to stop " + stopCounterText() + ". Use /chipscritters undo to restore the skipped stop."));
	}

	public static void skipToNumber(int number) {
        if(HideyhoSearch.active()){commandFeedback("Use /sr find stop to return to the normal route before this command.");return;}
        var client=Minecraft.getInstance();
        if(!active||client.player==null){commandFeedback("No active route to skip.");return;}
        if(buildingRoute){commandFeedback("Route is building; try again once ready.");return;}
        if(waitingForForestBird()){commandFeedback("Waiting for Birdfeeder completion.");return;}
        int target=NumberedRouteJump.index(number,displayStopNumber(0),nextStop,stops.size(),displayStopCount());
        if(target<0){commandFeedback("Waypoint unavailable. Choose an available number from 1 to "+displayStopCount()+"; cleared/removed stops are not selectable.");return;}
        if(goldLocked(stops.get(target))){commandFeedback("Catch Doomspiral before the final Gimmiegold stop.");return;}
        if(target==nextStop){commandFeedback("Already on stop "+stopCounterText()+".");return;}
        DevMode.routeChanged();
        if(target>nextStop)for(int i=nextStop;i<target;i++)if(manuallySkipped.add(stops.get(i)))runStopCount=Math.max(0,runStopCount-1);
        cancelHauntedReplan();RUN_TIMES.disqualify(biome);
        nextStop=target;lastSkippedStop=-1;lastSkippedBiome=null;skipCompletionDeadline=0;
        backReviewTarget=stops.get(target);backReviewLeft=false;
        dropStage=0;dropFlight=List.of();dropApproachAnchor=null;descentRepairAttempted=false;ladderStage=LadderStage.NONE;activeLadder=null;
        hillLegTarget=null;hillSteps=List.of();forestJumpStage=ForestJumpStage.NONE;waitingForCavernWaterfall=false;
        pendingLineBuild=null;clearRouteLine();SafariGraph.get().resetDirectRouteChoices();lastPingedStop=-1;
        if(!paused)refreshVisibleRouteLine();
        commandFeedback("Now on stop "+stopCounterText()+". Bypassed stops were not collected; PB disabled for this biome this run.");
    }

    public static void undoSkippedStop() {
        if(HideyhoSearch.active()){commandFeedback("Use /sr find stop to return to the normal route before this command.");return;}
        cancelHauntedReplan();
        DevMode.routeChanged();dropStage=0;dropFlight=List.of();dropApproachAnchor=null;descentRepairAttempted=false;
		Minecraft client = Minecraft.getInstance();
		if (!active || client.player == null || lastSkippedStop < 0 || lastSkippedBiome != biome
				|| nextStop != lastSkippedStop + 1 || lastSkippedStop >= stops.size()) { commandFeedback("No recent skip to undo."); return; }
		nextStop = lastSkippedStop;
        backReviewTarget=null;
        pendingLineBuild=null; ladderStage=LadderStage.NONE;activeLadder=null;hillLegTarget=null;hillSteps=List.of();forestJumpStage=ForestJumpStage.NONE;
		skipCompletionDeadline = 0L;
		lastSkippedStop = -1;
		lastSkippedBiome = null;
		if(manuallySkipped.remove(stops.get(nextStop)))runStopCount++;
		waitingForCavernWaterfall = false;
		skipCompletionDeadline = 0L;
		lastPingedStop = -1;
		if(!paused)refreshVisibleRouteLine();
		client.player.sendOverlayMessage(Component.literal("[ChipsCritters] Restored stop "+stopCounterText()+"."));
	}

	public static void previousStop() {
        if(HideyhoSearch.active()){commandFeedback("Use /sr find stop to return to the normal route before this command.");return;}
        cancelHauntedReplan();
        DevMode.routeChanged();dropStage=0;dropFlight=List.of();dropApproachAnchor=null;descentRepairAttempted=false;
		Minecraft client = Minecraft.getInstance();
		if (!active || client.player == null || nextStop <= 0 || waitingForForestBird()) { commandFeedback("No previous pending stop available."); return; }
		if(buildingRoute){commandFeedback("Route is building; try back once ready.");return;}
		nextStop=RouteCursor.back(nextStop,stops.size());
        if(manuallySkipped.remove(stops.get(nextStop)))runStopCount++;
        backReviewTarget=stops.get(nextStop);backReviewLeft=false;pendingLineBuild=null;clearRouteLine();SafariGraph.get().resetDirectRouteChoices();
		ladderStage = LadderStage.NONE; activeLadder = null; hillLegTarget = null; hillSteps = List.of(); forestJumpStage = ForestJumpStage.NONE;
		lastSkippedStop = -1;
		lastSkippedBiome = null;
		lastPingedStop = -1;
		waitingForCavernWaterfall = false;
		skipCompletionDeadline = 0L;
		if(!paused)refreshVisibleRouteLine();
		client.player.sendOverlayMessage(Component.literal("[ChipsCritters] Back to stop " + stopCounterText() + "."));
	}

	public static void recalculateRoute() {
        if(HideyhoSearch.active()){commandFeedback("Use /sr find stop to return to the normal route before this command.");return;}
		Minecraft client = Minecraft.getInstance();
		if (!active || client.player == null || paused || waitingForCavernWaterfall || ladderStage != LadderStage.NONE
				|| forestJumpStage != ForestJumpStage.NONE || nextStop >= stops.size()) { commandFeedback("Refresh unavailable: no pending route, paused, or following a fixed connector. Use resume or rejoin."); return; }
		lineAnchor = client.player.position();
		rebuildVisibleLineNow();
		client.player.sendOverlayMessage(Component.literal("[ChipsCritters] Route line recalculated from your position."));
	}

	public static void rejoinNearestRemainingStop() { rejoin(false); }

	private static void rejoin(boolean automatic) {
        if(HideyhoSearch.active()){HideyhoSearch.refresh();if(!automatic)commandFeedback("Hideyho search line refreshed.");return;}
		Minecraft client=Minecraft.getInstance();
		if (!active || client.player==null || displayTarget()==null) { if (!automatic) commandFeedback("No active waypoint to rejoin."); return; }
		if (paused) { if(!automatic) commandFeedback("Route is paused; use /sr resume before rejoining."); return; }
		if (!automatic) { cancelHauntedReplan();descentRepairAttempted=false;lineGapRetryCount=0; lineGapDetectedMillis=0; }
		SafariGraph.get().resetDirectRouteChoices();
		RouteRenderer.invalidateGraphEdgeCache();
		SafariGraph.get().requestAlternateJoin();
		lineAnchor=client.player.position();
		refreshVisibleRouteLine();
		if (!automatic) commandFeedback("Route rejoined from your position.");
		ChipsCrittersClient.LOGGER.info("Route rejoin {} stop={} from={} target={} reason=line gap or manual rejoin",biome,displayStopNumber(0),lineAnchor,displayTarget());
	}

	public static void tick(Minecraft client) {
		if (!ChipsCrittersClient.isInCritterSafari()) {
			if (active || buildingRoute || assistBiome != null) reset();
			return;
		}
		tickDoomCountdown(client);
        if(HideyhoSearch.active()){HideyhoSearch.tick(client);return;}
        if(DOOM_COUNTDOWN.active()){pendingLineBuild=null;clearRouteLine();return;}
        applyPendingBuilds(client);
		if(landingTransfer && client.player!=null && biome!=null && client.player.position().distanceTo(SafariGraph.get().navigationTarget(biome,biome.gate()).add(.5,0,.5))<=ChipsConfig.get().arrivalRadius){
            landingTransfer=false;resumeLadderIfNeeded();resumeForestTreeIfNeeded(client.player.position());
            ChipsCrittersClient.LOGGER.info("Landing transfer reached {} gate",biome);
        }
        if(active&&!paused&&client.player!=null&&tickDrop(client))return;
        processPendingLineBuild();
        tickHauntedChanges(client);
        if(hauntedReplanBusy)return;
        if (buildingRoute) return;

		tickLineGapRecovery(client);
		boolean finderPaused = HideyhoFinderPause.active();
		boolean hideyhoSearch = inHideyhoSearchPortion();
		if (!HideyhoWaypointPolicy.allowCurrentHideyhoStop(finderPaused, hideyhoSearch)) return;
		if (pendingHideyhoRefresh && active && !paused && !finderPaused) {
			pendingHideyhoRefresh = false;
			rebuildVisibleLineNow();
		}
		if (!finderPaused && !inHideyhoSearchPortion()) applyDeferredRelayClaims();
		if (routePathRecheckPending && !(isCavernDescent() && !routeLineWaitingChunks && !line.isEmpty() && hasSafeLineStart()) && !hillGuidance() && forestJumpStage == ForestJumpStage.NONE && active && !paused && !waitingForCavernWaterfall
				&& ladderStage == LadderStage.NONE && pendingLineBuild == null && client.player != null
				&& System.currentTimeMillis() - lastChunkLineRefreshMillis >= 300L) {
			routePathRecheckPending = false;
			lastChunkLineRefreshMillis = System.currentTimeMillis();
			lineAnchor = client.player.position();
			line = buildRouteLine(lineAnchor, upcomingStops(nextStop));
		}
		if (client.player != null && ((active && biome != null && (!landingTransfer && (gateVisited || !landingApproach(client.player.position()))) && !SafariData.isWithinBiomeArea(biome, client.player.position(), BIOME_AREA_MARGIN))
				|| (!active && assistBiome != null && !SafariData.isWithinAnyBiomeArea(client.player.position(), BIOME_AREA_MARGIN)))) {
			outsideBiomeTicks++;
			if (outsideBiomeTicks >= OUTSIDE_BIOME_CLEAR_TICKS) {
				Biome cleared = active ? biome : null;
				stop();
				if (cleared != null) client.player.sendOverlayMessage(Component.literal("[ChipsCritters] " + cleared.displayName() + " route cleared after leaving the biome."));
				outsideBiomeTicks = 0;
				return;
			}
		} else {
			outsideBiomeTicks = 0;
		}
		if (active && skipCompletionDeadline > 0L) {
			if (client.player != null && System.currentTimeMillis() >= skipCompletionDeadline) {
				skipCompletionDeadline = 0L;
				complete(client, client.player.position(), "Route complete; final stop skipped", false);
			}
			return;
		}
		if (paused) return;
		if (waitingForCavernWaterfall && client.player != null) {
			Vec3 waterfallExit = SafariData.CAVERN_WATERFALL_EXIT;
			double exitRadius = Math.max(2.0, ChipsConfig.get().arrivalRadius);
			Vec3 upperTarget = nextStop < stops.size() ? stops.get(nextStop) : waterfallExit;
			boolean reachedUpperLevel = client.player.position().y >= SafariData.CAVERN_WATERFALL_RESUME_Y - 0.5;
			if (reachedUpperLevel && (client.player.position().distanceTo(waterfallExit) <= exitRadius
					|| client.player.position().distanceTo(upperTarget) < client.player.position().distanceTo(waterfallExit))) {
				waitingForCavernWaterfall = false;
				lineAnchor = client.player.position();
				line = buildRouteLine(lineAnchor, upcomingStops(nextStop));
				client.player.sendOverlayMessage(Component.literal("[ChipsCritters] Waterfall ascent complete. Continue to stop " + stopCounterText() + "."));
			} else {
				// Keep the waterfall route anchored to the player so the ascent guidance moves with you.
				Vec3 playerPosition = client.player.position();
				if (lineAnchor == null || playerPosition.distanceToSqr(lineAnchor) >= 1.0) {
					lineAnchor = playerPosition;
					pendingLineBuild = null;
					line = waterfallGuide(playerPosition);
				}
				return;
			}
		}
		if (active && client.player != null) resumeLadderIfNeeded();
		if (active && client.player != null && ladderStage != LadderStage.NONE) {
			tickLadder(client, client.player.position());
			return;
		}
		if (active && client.player != null && forestJumpStage != ForestJumpStage.NONE) {
			tickForestTreeJump(client, client.player.position());
			return;
		}
		if (!active || client.player == null || biome == null || waitingForForestBird || nextStop >= stops.size()) return;
        if(goldLocked(stops.get(nextStop))||waitingForHauntedUnlock()){clearRouteLine();return;}
		Vec3 player = client.player.position();
		// The Fluffling exit now uses the shorter verified drop route.
		if (beginForestTreeJumpIfNeeded(client, player)) return;
		boolean currentHideyhoStop = biome == Biome.HAUNTED && SafariData.isHideyhoWaypoint(biome, stops.get(nextStop));
		boolean hideyhoSearchNow = finderPaused || inHideyhoSearchPortion();
		if (HideyhoWaypointPolicy.allowPassedStopCollection(finderPaused, hideyhoSearchNow))
			markPassedFutureStops(player, ChipsConfig.get().arrivalRadius);
		if (nextStop >= stops.size()) return;
		Vec3 target = stops.get(nextStop);
		Vec3 reachableTarget = SafariGraph.get().navigationTarget(biome, target);
		Vec3 arrivalPoint = reachableTarget.add(0.5, 0.0, 0.5);
		ChipsConfig config = ChipsConfig.get();
		double distanceToTarget = player.distanceTo(arrivalPoint);
		if (config.nearWaypointPing && nextStop != lastPingedStop && distanceToTarget <= Math.max(config.arrivalRadius + 1.0, 6.0)) {
			lastPingedStop = nextStop;
			if (config.pingVolume > 0.0) client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, (float) config.pingVolume, 1.45F);
		}
		// A newly selected stop is eligible immediately, even if the player is already
		// standing within its radius after the previous stop advanced.
		boolean reachedTarget = distanceToTarget <= config.arrivalRadius;
        
        if(biome==Biome.HAUNTED && target.equals(new Vec3(6,79,-83)))reachedTarget=distanceToTarget<=1.25 && Math.abs(player.y-arrivalPoint.y)<=1;

        if(target.equals(backReviewTarget)){
            if(!reachedTarget)backReviewLeft=true;
            if(!backReviewLeft)reachedTarget=false;
        }
		boolean reachedOceanDepth = biome == Biome.ICY
				&& SafariData.isOceanWaypoint(biome, target)
				&& player.y < SafariData.ICY_OCEAN_ADVANCE_Y;
		if (reachedTarget || reachedOceanDepth) {
            if(biome==Biome.HAUNTED)ChipsCrittersClient.LOGGER.info("Haunted stop cleared: target={}, reason=current target arrival, player={}, distance={}",target,player,distanceToTarget);
            RecordedLegPaths.clearSelection();
			backReviewTarget=null;descentRepairAttempted=false;
            String reachedCounter=stopCounterText();
            int reached = nextStop;
            if(SafariData.isHideonwallLocation(biome,target))ChipsCrittersClient.LOGGER.info("Hideonwall current stop reached: target={}, standing={}, player={}",target,arrivalPoint,player);
			if (HideyhoWaypointPolicy.advanceSearchStopWithoutClaim(finderPaused, currentHideyhoStop)) {
                ChipsCrittersClient.LOGGER.info("Haunted stop cleared: target={}, reason=current Hideyho search advance during finder; no ledger/relay claim",target);
				List<Vec3> updated = new ArrayList<>(stops);
				updated.remove(reached);
				stops = List.copyOf(updated);
				runStopCount = Math.max(0, runStopCount - 1);
				lastPingedStop = -1;
				lineAnchor = player;
				clearRouteLine();
				return;
			}
			lastSkippedStop = -1;
			lastSkippedBiome = null;
			if (biome == Biome.HAUNTED && target.equals(SafariData.GIMMIEGOLD)) {
                WaypointRunLedger.HAUNTED.gold = true;
                if(!WaypointRunLedger.HAUNTED.doom){ChipsCrittersClient.LOGGER.info("Unique early Gimmiegold reached without personal catch; counts done under existing reach rule; resume remaining stops");scheduleHauntedReplan("early Gimmiegold reached");}
            }
			else if (biome != Biome.CAVERN || !SafariData.isCavernWaterfallConnector(stops.get(reached))) {
				if(!WaypointRunLedger.isSatisfied(biome,stops.get(reached)))WaypointRelay.waypointReached(biome, stops.get(reached));
			}
			WaypointRunLedger.markSatisfied(biome, stops.get(reached));
			nextStop++;
			lastPingedStop = -1;
			if (reached == 0) { gateVisited = true; landingTransfer=false; }
			if (nextStop >= stops.size()) {
				if (biome == Biome.FOREST) {
					waitingForForestBird = true;
					clearRouteLine();
					lineAnchor = null;
					client.player.sendOverlayMessage(Component.literal("[ChipsCritters] Forest stops complete. Use all Birdfeeder feed, then catch the bird from the final feed."));
				} else {
					complete(client, player);
				}
			} else {
				if (beginForestTreeJump(client, player)) return;
				resumeLadderIfNeeded();
				if (ladderGuidance()) return;
				if (biome == Biome.CAVERN && !RecordedLegPaths.hasTarget(biome,stops.get(nextStop)) && stops.get(reached).distanceToSqr(SafariData.CAVERN_WATERFALL_ENTRY) < 0.01) {
					waitingForCavernWaterfall = true;
					lineAnchor = player;
					pendingLineBuild = null;
					line = waterfallGuide(player);
					client.player.sendOverlayMessage(Component.literal("[ChipsCritters] Lower Cavern sweep complete. Follow the numbered waterfall route to the Y=82 exit."));
					return;
				}
				boolean advancedByDepth = reachedOceanDepth && !reachedTarget;
				lineAnchor = player;
				line = buildRouteLine(lineAnchor, upcomingStops(nextStop));
				String progress = advancedByDepth
						? "Below Y=45; advancing to stop " + stopCounterText()
						: "Reached stop " + reachedCounter + ". Next: " + stopCounterText();
					client.player.sendOverlayMessage(Component.literal("[ChipsCritters] " + progress));
			}
			return;
		}

		// Once the gate has been reached, a distant detour re-joins at the nearest later stop.
		if (HideyhoWaypointPolicy.allowAutoSkip(finderPaused, hideyhoSearchNow)
				&& biome != Biome.CAVERN && biome != Biome.FOREST && biome != Biome.HAUNTED && gateVisited && nextStop > 0 && !line.isEmpty()) {
			double pathDistance = line.stream().mapToDouble(point -> point.distanceTo(player)).min().orElse(Double.POSITIVE_INFINITY);
			if (pathDistance > 12.0) {
				int bestIndex = nextStop;
				double bestDistance = Double.POSITIVE_INFINITY;
				for (int i = nextStop; i < stops.size(); i++) {
					double distance = player.distanceTo(stops.get(i));
					if (distance < bestDistance) {
						bestDistance = distance;
						bestIndex = i;
					}
				}
				if (bestIndex > nextStop) {
					nextStop = bestIndex;
					lineAnchor = player;
					line = buildRouteLine(lineAnchor, upcomingStops(nextStop));
					client.player.sendOverlayMessage(Component.literal("[ChipsCritters] Rejoining at stop " + (nextStop + 1) + "; earlier stops were left behind."));
				}
			}
		}

		if (active && pendingLineBuild == null && !(isCavernDescent() && !line.isEmpty())
				&& (lineAnchor == null || line.isEmpty() || player.distanceToSqr(lineAnchor) >= ROUTE_LINE_REANCHOR_DISTANCE * ROUTE_LINE_REANCHOR_DISTANCE)) {
			lineAnchor = player;
			line = buildRouteLine(lineAnchor, upcomingStops(nextStop));
		}
	}

	private static List<Vec3> waterfallGuide(Vec3 player) {
		lineGaps = Set.of();
		routeLineGap = false; routeLineWaitingChunks=false;
		var points = new java.util.ArrayList<Vec3>();
		points.add(player);
		var steps = SafariData.cavernWaterfallSteps();
		for (int i = SafariData.cavernWaterfallStepIndex(player.y); i < steps.size(); i++)
			points.add(steps.get(i).add(0.5, 0, 0.5));
		return List.copyOf(points);
	}

	static boolean passedStopAllowed(Biome b,Vec3 stop){
        if(SafariData.isHideonwallLocation(b,stop) || (b==Biome.HAUNTED && SafariData.GIMMIEGOLD.equals(stop)))return false;
        return b!=Biome.HAUNTED || (!SafariData.isHideyhoWaypoint(b,stop) && !SafariData.isGazerBedWaypoint(b,stop) && !SafariData.route(b).houseSweep().contains(stop) && SafariData.isFloorDropWaypoint(b,stop));
    }
	private static void markPassedFutureStops(Vec3 player, double arrivalRadius) {
		if (!active || biome == null || nextStop + 1 >= stops.size()
				|| !HideyhoWaypointPolicy.allowPassedStopCollection(HideyhoFinderPause.active(), inHideyhoSearchPortion())) return;
		double radius = Math.max(0.5, arrivalRadius);
		List<Vec3> updated = new java.util.ArrayList<>(stops);
		boolean changed = false;
		for (int index = nextStop + 1; index < updated.size();) {
			Vec3 stop = updated.get(index);
            if(!passedStopAllowed(biome,stop) && player.distanceTo(SafariGraph.get().navigationTarget(biome,stop).add(.5,0,.5))<=radius && protectedPaintingsNoted.add(stop))ChipsCrittersClient.LOGGER.info("Preserved future Hideonwall {} near player {}; current stop is {}",stop,player,stops.get(nextStop));
			if (goldLocked(stop) || !passedStopAllowed(biome,stop) || (biome == Biome.FOREST && (SafariData.isForestTreeStop(stop) || index == updated.size() - 1))
					|| (biome == Biome.CAVERN && (stop.equals(SafariData.CAVERN_POND_STOP) || stop.equals(SafariData.CAVERN_WATERFALL_ENTRY)))) {
				index++;
				continue;
			}
			Vec3 target = SafariGraph.get().navigationTarget(biome, stop).add(0.5, 0.0, 0.5);
			if (player.distanceTo(target) <= radius) {
				WaypointRunLedger.markSatisfied(biome, stop);
                if(biome==Biome.HAUNTED)ChipsCrittersClient.LOGGER.info("Haunted stop cleared: target={}, reason=approved floor-drop pass, player={}",stop,player);
				if (biome != Biome.CAVERN || !SafariData.isCavernWaterfallConnector(stop)) WaypointRelay.waypointReached(biome, stop);
				updated.remove(index);
				runStopCount = Math.max(0, runStopCount - 1);
				changed = true;
			} else index++;
		}
		if (changed) {
			stops = List.copyOf(updated);
			lastPingedStop = -1;
			refreshVisibleRouteLine();
		}
	}

	private static void resumeLadderIfNeeded() {
        if(landingTransfer) return;
        if(nextStop<stops.size() && RecordedLegPaths.hasTarget(biome,stops.get(nextStop))) return;
		if (biome != Biome.CAVERN || !gateVisited || nextStop >= stops.size() || ladderGuidance()) return;
		Vec3 goal = stops.get(nextStop);
		for (SafariData.CavernLadder climb : SafariData.cavernLadders()) {
			if (!goal.equals(climb.before()) || WaypointRunLedger.isSatisfied(biome, climb.top())) continue;
			Vec3 player = Minecraft.getInstance().player.position();
			Vec3 bottom = climb.bottom().add(0.5, 0, 0.5);
			Vec3 top = climb.top().add(0.5, 0, 0.5);
			if (withinLadderReachArea(player, climb.top())) {
				WaypointRunLedger.markSatisfied(biome, climb.top());
				continue;
			}
			double fraction = (player.y - bottom.y) / (top.y - bottom.y);
			boolean midClimb = fraction > 0 && fraction < 1 && bottom.lerp(top, fraction).distanceTo(player) <= 1.2;
			activeLadder = climb;
			ladderStage = midClimb ? LadderStage.CLIMB_TO_TOP : LadderStage.APPROACH_BOTTOM;
			refreshVisibleRouteLine();
			return;
		}
	}

	private static boolean beginForestTreeJumpIfNeeded(Minecraft client, Vec3 player) {
        if(nextStop<stops.size() && RecordedLegPaths.hasTarget(biome,stops.get(nextStop))) return false;
		return biome == Biome.FOREST && SafariData.isForestTreeStop(stops.get(nextStop))
				&& player.distanceTo(SafariData.forestTreeStop().add(0.5, 0, 0.5)) > 1.5
				&& beginForestTreeJump(client, player);
	}

	private static boolean followHill(Vec3 player) {
        if(nextStop<stops.size() && RecordedLegPaths.hasTarget(biome,stops.get(nextStop))) return false;
		Vec3 goal = stops.get(nextStop);
		if (!goal.equals(hillLegTarget)) {
			hillLegTarget = goal;
			hillSteps = SafariGraph.get().forestHillSteps(player, goal.add(0.5, 0, 0.5));
			hillStep = 0;
			if (hillGuidance()) refreshVisibleRouteLine();
		}
		if (!hillGuidance()) return false;
		Vec3 current = hillSteps.get(hillStep).add(0.5, 0, 0.5);
		if (player.distanceTo(current) <= 1.35) {
			hillStep++;
			refreshVisibleRouteLine();
		} else if (lineAnchor == null || player.distanceToSqr(lineAnchor) > 4.0) refreshVisibleRouteLine();
		return hillGuidance();
	}

	public static boolean hillGuidance() { if(HideyhoSearch.active())return false;
		return active && biome == Biome.FOREST && nextStop < stops.size() && stops.get(nextStop).equals(hillLegTarget)
				&& hillStep < hillSteps.size();
	}

	public static String hillInstruction() {
		return hillSteps.getLast().y < hillSteps.getFirst().y ? "DESCEND THE HILL" : "CLIMB THE HILL";
	}

	private static void resumeForestTreeIfNeeded(Vec3 player) {
        if(nextStop<stops.size() && RecordedLegPaths.hasTarget(biome,stops.get(nextStop))) return;
		if (biome == Biome.FOREST && nextStop < stops.size()
				&& SafariData.isForestTreeStop(stops.get(nextStop))
				&& player.distanceTo(SafariData.forestTreeStop().add(0.5, 0.0, 0.5)) > 1.5) {
			beginForestTreeJump(Minecraft.getInstance(), player);
		}
	}

	private static boolean beginForestTreeJump(Minecraft client, Vec3 player) {
        if(nextStop<stops.size() && RecordedLegPaths.hasTarget(biome,stops.get(nextStop))) return false;
		if (biome != Biome.FOREST || forestJumpStage != ForestJumpStage.NONE || nextStop >= stops.size()
				|| !SafariData.isForestTreeStop(stops.get(nextStop))) return false;
		forestJumpStage = ForestJumpStage.APPROACH_JUMP_OFF;
		lineAnchor = player;
		line = buildRouteLine(player, List.of(SafariData.forestTreeJumpOff()));
		client.player.sendOverlayMessage(Component.literal("[ChipsCritters] Follow the mapped route to the tree jump-off at (2, 90, 8)."));
		return true;
	}

	private static void tickForestTreeJump(Minecraft client, Vec3 player) {
		Vec3 jumpOff = SafariData.forestTreeJumpOff();
		Vec3 tree = SafariData.forestTreeStop();
		if (forestJumpStage == ForestJumpStage.APPROACH_JUMP_OFF
				&& player.distanceTo(jumpOff.add(0.5, 0.0, 0.5)) <= Math.max(1.5, Math.min(3.0, ChipsConfig.get().arrivalRadius))) {
			forestJumpStage = ForestJumpStage.JUMP_TO_TREE;
			pendingLineBuild = null;
			line = List.of(player, tree.add(0.5, 0, 0.5));
			lineGaps = Set.of();
			routeLineGap = false; routeLineWaitingChunks=false;
			lineAnchor = jumpOff;
			client.player.sendOverlayMessage(Component.literal("[ChipsCritters] JUMP ON TREE from (2, 90, 8) to (0, 90, 8)."));
			return;
		}
		if (forestJumpStage == ForestJumpStage.JUMP_TO_TREE
				&& player.distanceTo(tree.add(0.5, 0.0, 0.5)) <= 1.5) {
			forestJumpStage = ForestJumpStage.NONE;
			lineAnchor = player;
			line = buildRouteLine(player, upcomingStops(nextStop));
			client.player.sendOverlayMessage(Component.literal("[ChipsCritters] Tree reached. Continue the upper Forest route."));
		}
	}

	private static List<Vec3> climbLine(Vec3 player){
        Vec3 top=SafariTerrain.floor(Biome.CAVERN,activeLadder.top()).add(.5,0,.5);
        Vec3 bottom=SafariTerrain.floor(Biome.CAVERN,activeLadder.bottom()).add(.5,0,.5);
        // Stay on the observed ladder/vine column until its open exit, never cut diagonally through rock.
        Vec3 column=new Vec3(bottom.x,Math.min(player.y,top.y),bottom.z);
        Vec3 upper=new Vec3(bottom.x,top.y,bottom.z);
        return List.of(player,column,upper,top);
    }
    private static void tickLadder(Minecraft client, Vec3 player) {
		if (activeLadder == null) {
			ladderStage = LadderStage.NONE;
			return;
		}
		if (ladderStage == LadderStage.APPROACH_BOTTOM) {
			Vec3 bottom = activeLadder.bottom().add(0.5, 0.0, 0.5);
			if (!withinLadderReachArea(player, activeLadder.bottom()) && (lineAnchor == null || player.distanceToSqr(lineAnchor) > 4)) refreshVisibleRouteLine();
			if (withinLadderReachArea(player, activeLadder.bottom())) {
				ladderStage = LadderStage.CLIMB_TO_TOP;
				pendingLineBuild = null;
				line = climbLine(player);
				lineGaps = Set.of();
				routeLineGap = false; routeLineWaitingChunks=false;
				lineAnchor = activeLadder.bottom();
				client.player.sendOverlayMessage(Component.literal("[ChipsCritters] " + climbInstruction() + " to Y=" + (int) activeLadder.top().y + "."));
			}
			return;
		}
		if (ladderStage == LadderStage.CLIMB_TO_TOP
				&& withinLadderReachArea(player, activeLadder.top())) {
			WaypointRunLedger.markSatisfied(Biome.CAVERN, activeLadder.top());
			ladderStage = LadderStage.NONE;
			resumeLadderIfNeeded();
			if (ladderGuidance()) return;
			lineAnchor = player;
			line = buildRouteLine(player, upcomingStops(nextStop));
			client.player.sendOverlayMessage(Component.literal("[ChipsCritters] Ladder climbed. Continue to stop "
					+ stopCounterText() + "."));
		}
	}

	public static void snoozleCaptured() {
		Minecraft client = Minecraft.getInstance();
		if (!active || biome != Biome.CAVERN || ChipsConfig.get().mode != ChipsConfig.Mode.UNIQUE
				|| client.player == null || WaypointRunLedger.snoozleCaught()) return;
		WaypointRunLedger.markSnoozleCaught();
		List<Vec3> updated = new java.util.ArrayList<>(stops.size());
		int removed = 0;
		for (int index = 0; index < stops.size(); index++) {
			Vec3 stop = stops.get(index);
			if (index >= nextStop && SafariData.isSnoozleWaypoint(biome, stop)) removed++;
			else updated.add(stop);
		}
		if (removed == 0) return;
		stops = List.copyOf(updated);
		runStopCount = Math.max(0, runStopCount - removed);
		lastPingedStop = -1;
		lastSkippedStop = -1;
		lastSkippedBiome = null;
		if (nextStop >= stops.size()) {
			complete(client, client.player.position(), "Snoozle caught; Cavern route complete");
			return;
		}
		// Cavern optimization keeps its fixed phases; rebuilding from here changes only the line origin.
		lineAnchor = client.player.position();
		refreshVisibleRouteLine();
		client.player.sendOverlayMessage(Component.literal("[ChipsCritters] Snoozle caught; removed " + removed + " remaining Snoozle waypoint" + (removed == 1 ? "" : "s") + "."));
	}

	public static void wumpaSpawned() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
		boolean insideIcy = SafariData.nearestBiomeArea(client.player.position(), 16.0) == Biome.ICY;
		Vec3 wumpa = SafariData.icyWumpaWaypoint();
		boolean fixedEnd = SafariData.route(Biome.ICY).fixedEnd()
				&& SafariData.isWumpaWaypoint(Biome.ICY, SafariData.route(Biome.ICY).stops().getLast());
		IcyWumpaRoute.SpawnResult result = IcyWumpaRoute.onSpawn(active, biome, insideIcy,
				WaypointRunLedger.isSatisfied(Biome.ICY, wumpa), stops, nextStop, wumpa, fixedEnd);
		if (result.outOfScope()) {
			client.player.sendOverlayMessage(Component.literal("[ChipsCritters] Wumpa spawn noticed; no active Icy route here."));
			return;
		}
		if (result.alreadyReached()) {
			client.player.sendOverlayMessage(Component.literal("[ChipsCritters] Wumpa waypoint was already reached; route unchanged."));
			return;
		}
		if (WaypointRunLedger.icyWumpaSpawned()) return;
		if (!result.changed()) return;
		WaypointRunLedger.markIcyWumpaSpawned();
		runStopCount = Math.max(0, runStopCount - (stops.size() - result.stops().size()));
		stops = result.stops();
		nextStop = result.nextStop();
		waitingForCavernWaterfall = false;
		lineAnchor = client.player.position();
		line = buildRouteLine(lineAnchor, upcomingStops(nextStop));
		client.player.sendOverlayMessage(Component.literal(fixedEnd
				? "[ChipsCritters] Wumpa spawned. Go to the WUMPA waypoint; this fixed-end route finishes there."
				: "[ChipsCritters] Wumpa spawned. It is next; remaining route continues after it."));
	}

	public static void wumpaCaptured() {
		Minecraft client = Minecraft.getInstance();
		if (!active || biome != Biome.ICY || client.player == null) return;
		WaypointRunLedger.markSatisfied(Biome.ICY, SafariData.icyWumpaWaypoint());
		complete(client, client.player.position(), "Wumpa caught; Icy route complete");
	}

	public static void troodonCaptured() {
		Minecraft client = Minecraft.getInstance();
		if (!active || biome != Biome.ICY || client.player == null
				|| ChipsConfig.get().mode != ChipsConfig.Mode.UNIQUE || WaypointRunLedger.troodonCaught()) return;
		WaypointRunLedger.markTroodonCaught();
		IcyCaptureRules.FilteredStops<Vec3> filtered = IcyCaptureRules.removeRemaining(stops, nextStop, true,
				stop -> SafariData.isTroodonWaypoint(Biome.ICY, stop));
		if (filtered.removed() == 0) return;
		stops = filtered.stops();
		runStopCount = Math.max(0, runStopCount - filtered.removed());
		lastPingedStop = -1;
		lastSkippedStop = -1;
		lastSkippedBiome = null;
		if (nextStop >= stops.size()) {
			complete(client, client.player.position(), "Troodon caught; Icy route complete");
			return;
		}
		lineAnchor = client.player.position();
		line = buildRouteLine(lineAnchor, upcomingStops(nextStop));
		client.player.sendOverlayMessage(Component.literal("[ChipsCritters] Troodon caught; removed " + filtered.removed()
				+ " remaining Troodon waypoint" + (filtered.removed() == 1 ? "" : "s") + " for this run."));
	}

	public static void recordCurrentPosition() {
		recordCurrentPosition(null);
	}

	public static void recordCurrentPosition(String label) {
		Minecraft client = Minecraft.getInstance();
		if (!active || biome == null || client.player == null) {
			if (client.player != null) client.player.sendSystemMessage(Component.literal("[ChipsCritters] Start a biome route before recording a stop."));
			return;
		}
		ChipsConfig.get().addLearnedStop(biome, client.player.position(), label);
		String labelText = label == null ? "" : " labelled " + label;
		client.player.sendSystemMessage(Component.literal("[ChipsCritters] Saved a fixed " + biome.displayName() + " stop" + labelText + ". Restart the route to include it."));
		client.player.sendSystemMessage(Component.literal("[ChipsCritters] This is a manually recorded coordinate; no critter or floor drop is tracked."));
		if (!gateVisited) {
			client.player.sendSystemMessage(Component.literal("[ChipsCritters] Stop #1 remains the biome gate."));
		}
	}

	public static Biome biome() { return HideyhoSearch.active()?Biome.HAUNTED:biome; }
	public static boolean waitingForForestBird() { if(HideyhoSearch.active())return false; return active && biome == Biome.FOREST && waitingForForestBird; }
	public static boolean waitingForCavernWaterfall() { if(HideyhoSearch.active())return false; return active && biome == Biome.CAVERN && waitingForCavernWaterfall; }
	public static boolean paused() { return HideyhoSearch.active()?HideyhoSearch.paused():active && paused; }
	public static boolean finishingSkip() { if(HideyhoSearch.active())return false; return active && skipCompletionDeadline > 0L; }
	public static int completedStopCount() { if(HideyhoSearch.active())return HideyhoSearch.STATE.checked.size();return active ? biome==Biome.HAUNTED?Math.max(0,displayStopCount()-remainingStopCount()):Math.max(0,nextStop) : 0; }
    static String hauntedTotalKey(ChipsConfig.Mode mode,boolean paintings){return mode==ChipsConfig.Mode.UNIQUE&&!paintings?"UNIQUE_PAINTINGS_OFF":mode.name();}
    public static int displayStopCount(){if(HideyhoSearch.active())return 20;return active&&biome==Biome.HAUNTED?WaypointRunLedger.HAUNTED.total(hauntedTotalKey(ChipsConfig.get().mode,ChipsConfig.get().uniqueHideonwallRoutes),stops.size()):stops.size();}
    public static int displayStopNumber(int offset){return (int)Math.min((long)displayStopCount(),Math.max(0L,(long)completedStopCount())+1+Math.max(0,offset));}
    static boolean goldLocked(Vec3 target){return biome==Biome.HAUNTED && SafariData.GIMMIEGOLD.equals(target) && !WaypointRunLedger.HAUNTED.doom && !(WaypointRunLedger.HAUNTED_UNIQUE.earlyGold && ChipsConfig.get().mode==ChipsConfig.Mode.UNIQUE);}
	private static boolean ritualLocked(Vec3 target){return active&&biome==Biome.HAUNTED&&(SafariData.isRitualWaypoint(biome,target)||(target.y<=60&&!target.equals(SafariData.GIMMIEGOLD)))&&!WaypointRunLedger.HAUNTED.doom&&!WaypointRunLedger.HAUNTED_UNIQUE.doomRoute&&ChipsConfig.get().mode==ChipsConfig.Mode.UNIQUE;}
    public static boolean waitingForHauntedUnlock(){if(HideyhoSearch.active())return false;return nextStop<stops.size()&&ritualLocked(stops.get(nextStop));}
    public static int remainingStopCount() { if(HideyhoSearch.active())return HideyhoSearch.stops().size();return active ? Math.max(0, stops.size() - nextStop) : 0; }
	public static String stopCounterText() {
        if(HideyhoSearch.active())return HideyhoSearch.stops().size()+"/20 left";
		return biome==Biome.HAUNTED?RouteStopCounter.text(active,completedStopCount(),displayStopCount()):RouteStopCounter.text(active,nextStop,stops.size());
	}
	public static long elapsedMillis() {
		return active && startedAtMillis > 0L ? Math.max(0L, System.currentTimeMillis() - startedAtMillis) : 0L;
	}

	private static List<Vec3> buildRouteLine(Vec3 anchor, List<Vec3> targets) {
		pendingLineBuild = targets.isEmpty() ? null : new PendingLineBuild(biome, anchor, targets, displayStopNumber(0));
		line = List.of();
		lineGaps = Set.of();
		// Keep recovery armed while a replacement line is built; a transient empty
		// line must not clear the active gap timer or warning.
		return line;
	}

	/** Builds at most one visible route leg per client tick. */
	private static void processPendingLineBuild() {
		PendingLineBuild build = pendingLineBuild;
		if (build == null) return;
		if (!active || build.biome != biome || build.index >= build.targets.size()) {
			pendingLineBuild = null;
			return;
		}
		Vec3 target = build.targets.get(build.index);
		long started = System.nanoTime();
		SafariGraph.RouteLineResult result = SafariGraph.get().routeLineResultFrom(
				build.biome, build.anchor, List.of(target), build.firstStopNumber + build.index);
		long elapsed = System.nanoTime() - started;
		if (elapsed >= 2_000_000L) ChipsCrittersClient.LOGGER.debug("Route line leg budget {} stop={} elapsed={}ms",
				build.biome, build.firstStopNumber + build.index, ms(elapsed));
		int offset = Math.max(0, build.points.size() - 1);
		for (int gap : result.gaps()) build.gaps.add(offset + gap);
		for (Vec3 point : result.points()) {
			if (build.points.isEmpty() || build.points.getLast().distanceToSqr(point) > 0.01) build.points.add(point);
		}
		build.issues.addAll(result.issues());
		build.anchor = SafariGraph.get().navigationTarget(build.biome, target).add(0.5, 0.0, 0.5);
		build.index++;
		if (build.index >= build.targets.size()) {
			line = List.copyOf(build.points);
			lineGaps = Set.copyOf(build.gaps);
			routeLineGap = !build.issues.isEmpty();
            routeLineWaitingChunks=routeLineGap && build.issues.stream().allMatch(issue->issue.reason().equals("waiting for route chunks"));
			updateLineGapClock();
			for (SafariGraph.RouteLineIssue issue : build.issues) logLineGap(issue.stopNumber(), issue.from(), issue.to(), issue.reason());
			pendingLineBuild = null;
		} else {
			line = List.copyOf(build.points);
			lineGaps = Set.copyOf(build.gaps);
			routeLineGap = !build.issues.isEmpty();
            routeLineWaitingChunks=routeLineGap && build.issues.stream().allMatch(issue->issue.reason().equals("waiting for route chunks"));
			updateLineGapClock();
		}
	}

	private static void updateLineGapClock() {
		if (routeLineGap) {
			lastObservedGap=System.currentTimeMillis();
			if (lineGapDetectedMillis == 0L) lineGapDetectedMillis = System.currentTimeMillis();
			lineGapClearSince = 0L;
		} else if (pendingLineBuild == null && lineGapDetectedMillis > 0L) {
			long now=System.currentTimeMillis();
			if(lineGapClearSince==0L) lineGapClearSince=now;
			else if(now-lineGapClearSince>=5000L) { lineGapDetectedMillis=0L; lineGapRetryCount=0; lineGapClearSince=0L; }
		}
	}

	private static void logLineGap(int stopNumber, Vec3 from, Vec3 to, String reason) {
		var livePlayer = Minecraft.getInstance().player;
		String context = " player=" + (livePlayer == null ? "unavailable" : livePlayer.position()) + " target=" + displayTarget();
		String key = biome + ":" + stopNumber + ":" + net.minecraft.core.BlockPos.containing(to) + ":" + reason;
		if(reason.equals("waiting for route chunks"))return;
        if (LOGGED_LINE_GAPS.add(key)) ChipsCrittersClient.LOGGER.warn(
				"ChipsCritters route line gap: biome={}, stop={}, from=({}, {}, {}), to=({}, {}, {}), reason={}",
				biome, stopNumber, from.x, from.y, from.z, to.x, to.y, to.z, reason + context);
	}

	private static long lastMappedClipRepair;
    public static void requestMappedClipRepair(){
        if(HideyhoSearch.active()){HideyhoSearch.repair();return;}
        if(System.currentTimeMillis()-lastMappedClipRepair<1000 || RecordedLegPaths.current())return;
        if(isCavernDescent()){if(descentRepairAttempted)return;descentRepairAttempted=true;}
        lastMappedClipRepair=System.currentTimeMillis();SafariGraph.get().requestMappedRepair();routePathRecheckPending=true;
    }
    public static void noteLineGap(int segmentIndex, Vec3 from, Vec3 to, String reason) {
        if(HideyhoSearch.active()){HideyhoSearch.noteGap(segmentIndex,from,to,reason);return;}
        if(!DevMode.preview().isEmpty())return;
		routeLineGap = true; routeLineWaitingChunks=false;
		lineGapClearSince=0L;
		lastObservedGap=System.currentTimeMillis();
		if (lineGapDetectedMillis == 0L) lineGapDetectedMillis = System.currentTimeMillis();
		
		logLineGap(displayStopNumber(0), from, to, reason);
	}

	private static void tickLineGapRecovery(Minecraft client) {
		if (!java.util.Objects.equals(gapTarget,displayTarget())) {gapTarget=displayTarget(); lineGapRetryCount=0; lineGapDetectedMillis=0;}
		if (dropGuidance() || !active || paused || !routeLineGap || client.player == null || pendingLineBuild != null || displayTarget()==null) {
			if (!routeLineGap && pendingLineBuild == null) updateLineGapClock();
			return;
		}
		long now = System.currentTimeMillis();
		if (lineGapDetectedMillis == 0L) lineGapDetectedMillis = now;
		if (isCavernDescent() && (descentRepairAttempted || !client.player.onGround()))return;
        if (now - lineGapDetectedMillis >= 1_000L
				&& (lineGapRetryCount==0 || now-lastLineGapRecoveryMillis>=10_000L)) {
			if(isCavernDescent())descentRepairAttempted=true;
            lineGapRetryCount++;
			lastLineGapRecoveryMillis=now;
			rejoin(true);
		}
	}

	public static boolean rejoinSuggested() { return routeLineGap && pendingLineBuild==null && lineGapRetryCount >= 3; }
	public static boolean isLineGap(int segmentIndex) { return HideyhoSearch.active()?HideyhoSearch.gap(segmentIndex):lineGaps.contains(segmentIndex); }
	public static String dropInstruction(){return dropStage==1?"GO TO DROP EDGE":"DROP DOWN";}
    public static boolean dropGuidance(){if(HideyhoSearch.active())return false;return active&&dropStage>0;}
    public static boolean isCavernDescent(){return dropGuidance();}
    private static boolean tickDrop(Minecraft client){
        if(biome!=Biome.CAVERN||nextStop>=stops.size()||!stops.get(nextStop).equals(SafariData.CAVERN_AFTER_DROP)){dropStage=0;return false;}
        if(WaypointRunLedger.isSatisfied(Biome.CAVERN,SafariData.CAVERN_DROP_END)){dropStage=0;return false;}
        Vec3 player=client.player.position(),end=SafariData.CAVERN_DROP_END.add(.5,0,.5);
        if(dropStage==0&&client.player.onGround()&&player.y<=62&&player.distanceTo(end)<12){WaypointRunLedger.markSatisfied(Biome.CAVERN,SafariData.CAVERN_DROP_END);refreshVisibleRouteLine();return false;}
        if(dropStage==0){dropStage=1;dropFlight=List.of();dropApproachAnchor=null;buildDropLine(client);}
        if(dropStage==1){
            if(player.distanceTo(SafariData.CAVERN_DROP_START.add(.5,0,.5))<=1.25){
                dropStage=2;pendingLineBuild=null;line=dropFlight;lineGaps=Set.of();routeLineGap=dropFlight.isEmpty();
                ChipsCrittersClient.LOGGER.info("Cavern fixed drop descent locked: from={} to={} samples={}; no rebuilds during fall",player,end,line.size());
            }else if(System.currentTimeMillis()-dropBuiltAt>600&&(dropApproachAnchor==null||player.distanceToSqr(dropApproachAnchor)>4||routePathRecheckPending)){buildDropLine(client);}
        }
        if(dropStage==2&&(client.player.onGround()||client.player.isInWater())&&player.distanceTo(end)<=Math.max(2,ChipsConfig.get().arrivalRadius)){
            dropStage=0;dropFlight=List.of();dropApproachAnchor=null;WaypointRunLedger.markSatisfied(Biome.CAVERN,SafariData.CAVERN_DROP_END);refreshVisibleRouteLine();return false;
        }return true;
    }
    private static void buildDropLine(Minecraft client){
        pendingLineBuild=null;clearRouteLine();dropFlight=List.of();routePathRecheckPending=false;dropBuiltAt=System.currentTimeMillis();lineAnchor=dropApproachAnchor=client.player.position();
        Vec3 edge=SafariData.CAVERN_DROP_START.add(.5,0,.5),end=SafariData.CAVERN_DROP_END.add(.5,0,.5);
        boolean direct=RouteRenderer.walkableDirectEdgeClear(client.level,lineAnchor,edge,Biome.CAVERN);
        var mapped=direct?null:SafariGraph.get().routeLineResultFrom(Biome.CAVERN,lineAnchor,List.of(SafariData.CAVERN_DROP_START),displayStopNumber(0));
        var approach=DropRibbonPath.approach(direct?List.of(lineAnchor,edge):mapped.points(),RouteRenderer::dropRibbonClear);
        boolean joined=!approach.isEmpty()&&approach.getLast().distanceTo(edge)<1.6&&(mapped==null||mapped.gaps().isEmpty());
        if(!joined){line=approach;routeLineGap=true;logLineGap(displayStopNumber(0),lineAnchor,edge,"fixed drop approach not fully clear in loaded terrain; /sr record next");return;}
        dropFlight=DropRibbonPath.flight(approach.getLast(),end,RouteRenderer::dropBodyClear);
        line=DropRibbonPath.combine(approach,dropFlight);routeLineGap=dropFlight.isEmpty();
        if(routeLineGap)logLineGap(displayStopNumber(0),approach.getLast(),end,"fixed drop has no collision-clear descent in loaded terrain; /sr record next");
        else ChipsCrittersClient.LOGGER.info("Cavern fixed drop line verified: player={} edge={} end={} approach={} descent={} directApproach={}",lineAnchor,edge,end,approach.size(),dropFlight.size(),direct);
    }

	public static boolean hasLineGaps() { return HideyhoSearch.active()?HideyhoSearch.gaps():routeLineGap; }
	public static boolean routeLineGap() { return HideyhoSearch.active()?HideyhoSearch.gaps():routeLineGap; }
 public static boolean waitingForRouteChunks(){return routeLineWaitingChunks;}

	private static void rebuildVisibleLineNow() {
		refreshVisibleRouteLine();
		processPendingLineBuild();
	}

	static List<Vec3> uniquePending(List<Vec3> pending){
        var state=WaypointRunLedger.HAUNTED;var unique=WaypointRunLedger.HAUNTED_UNIQUE;var result=new ArrayList<Vec3>();
        for(Vec3 p:pending)if((!unique.doomRoute||p.y<=60||SafariData.isRitualWaypoint(Biome.HAUNTED,p))&&(!state.gold||!p.equals(SafariData.GIMMIEGOLD)))result.add(p);
        // Coin enables cost-based insertion later on the client tick; restart must not force Gold first.
        return List.copyOf(result);
    }
    public static void uniqueOptionsChanged(){
        if(active&&biome==Biome.HAUNTED&&ChipsConfig.get().mode==ChipsConfig.Mode.UNIQUE)start(Biome.HAUNTED,false);
    }
    public static void hideyhoRelocated(){if(HideyhoSearch.STATE.found)return;if(skipManualHideyhoRewardReplan){skipManualHideyhoRewardReplan=false;return;}if(active&&biome==Biome.HAUNTED)scheduleHauntedReplan("Hideyho reward/relocation");}
    private static void scheduleHauntedReplan(String reason){
        hauntedReplanAt=System.currentTimeMillis()+400;hauntedReplanDeadline=System.currentTimeMillis()+1200;hauntedSettlePosition=null;hauntedReplanReason=reason;hauntedReplanSerial++;hauntedReplanBusy=false;
    }
    private static void tickHauntedChanges(Minecraft client){
        if(!active||biome!=Biome.HAUNTED||buildingRoute||client.player==null)return;
        var state=WaypointRunLedger.HAUNTED;var unique=WaypointRunLedger.HAUNTED_UNIQUE;
        boolean eligible=ChipsConfig.get().mode==ChipsConfig.Mode.UNIQUE;
        if(eligible&&!state.gold&&!unique.earlyGold&&HauntedInventory.count(client,"SHINING_COIN","Shining Coin")>=1){
            unique.earlyGold=true;UniqueGoldPlacement.invalidateOrder();
            ChipsCrittersClient.LOGGER.info("Unique Shining Coin verified: compare remaining Gimmiegold insertion slots using real directed routed paths; no front priority");
        }
        if(eligible&&unique.earlyGold&&!state.gold&&!state.doom&&!paused&&!HideyhoFinderPause.active()&&!HideyhoSearch.active()){
            int at=Math.min(nextStop,stops.size());var pending=List.copyOf(stops.subList(at,stops.size()));
            var placed=UniqueGoldPlacement.tick(pending,client.player.position());
            if(!placed.equals(pending)){var changed=new ArrayList<>(stops.subList(0,at));changed.addAll(placed);stops=List.copyOf(changed);pendingLineBuild=null;clearRouteLine();if(!paused)refreshVisibleRouteLine();}
        }
        if(eligible&&!state.doom&&!unique.doomRoute&&!(unique.earlyGold&&!state.gold)&&!HideyhoFinderPause.active()
            &&unique.canRouteDoom(ChipsConfig.get().uniqueHideonwallRoutes,HauntedInventory.count(client,"SOOTHING_INCENSE","Soothing Incense"))){
            unique.doomRoute=true;
            var pending=new ArrayList<Vec3>();
            for(Vec3 p:stops.subList(Math.min(nextStop,stops.size()),stops.size()))if(p.y<=60&&!p.equals(SafariData.GIMMIEGOLD)&&!SafariData.isRitualWaypoint(biome,p))pending.add(p);
            Vec3 ritual=stops.subList(Math.min(nextStop,stops.size()),stops.size()).stream().filter(p->SafariData.isRitualWaypoint(biome,p)).findFirst().orElse(null);if(ritual!=null)pending.addFirst(ritual);
            if(!state.gold)pending.add(SafariData.GIMMIEGOLD);
            var updated=new ArrayList<>(stops.subList(0,Math.min(nextStop,stops.size())));updated.addAll(pending);stops=List.copyOf(updated);
            ChipsCrittersClient.LOGGER.info("Unique Doomspiral unlocked: own house catches={}, Hideonwall required={}, own Soothing Incense >=3; retain underground drops then ritual",unique.caught,ChipsConfig.get().uniqueHideonwallRoutes);
            scheduleHauntedReplan("all required house catches plus three incense");
        }
        if(eligible&&!state.doom&&!unique.doomRoute&&nextStop<stops.size()&&SafariData.isRitualWaypoint(biome,stops.get(nextStop))){
            boolean later=stops.subList(nextStop+1,stops.size()).stream().anyMatch(p->!p.equals(SafariData.GIMMIEGOLD));
            if(later){var changed=new ArrayList<>(stops);Vec3 ritual=changed.remove(nextStop);int at=changed.size();for(int j=nextStop;j<changed.size();j++)if(changed.get(j).y<=60){at=j;break;}changed.add(at,ritual);stops=List.copyOf(changed);pendingLineBuild=null;clearRouteLine();}
            else { /* The visible ritual cube remains, but arrival waits for inventory/capture unlock. */ }
        }
        if(hauntedReplanAt==0||hauntedReplanBusy||HideyhoFinderPause.active()||paused)return;
        Vec3 player=client.player.position();
        if(hauntedSettlePosition==null||hauntedSettlePosition.distanceToSqr(player)>2.25){hauntedSettlePosition=player;hauntedReplanAt=System.currentTimeMillis()+300;}
        if(System.currentTimeMillis()<Math.min(hauntedReplanAt,hauntedReplanDeadline))return;
        hauntedReplanAt=0;hauntedReplanBusy=true;long serial=hauntedReplanSerial,generation=routeBuildGeneration,run=WaypointRunLedger.stateVersion();
        var level=client.level;String lobby=WaypointRelay.lobbySessionKey();var mode=ChipsConfig.get().mode;
        var prefix=List.copyOf(stops.subList(0,Math.min(nextStop,stops.size())));var pending=List.copyOf(stops.subList(Math.min(nextStop,stops.size()),stops.size()));String reason=hauntedReplanReason;
        RoutePlanService.reorderHaunted(pending,player).whenComplete((order,error)->client.execute(()->{
            if(serial!=hauntedReplanSerial)return;hauntedReplanBusy=false;
            if(error!=null){ChipsCrittersClient.LOGGER.warn("Haunted relocation replan failed; keeping remaining order",error);return;}
            if(!active||biome!=Biome.HAUNTED||routeBuildGeneration!=generation||client.level!=level||client.player==null||!lobby.equals(WaypointRelay.lobbySessionKey())||run!=WaypointRunLedger.stateVersion()||mode!=ChipsConfig.get().mode)return;
            // Keep any ledger/relay claims received during the background calculation removed.
            var safe=WaypointRelay.filterAlreadyClaimed(biome,WaypointRunLedger.withoutSatisfied(biome,order));
            if(mode==ChipsConfig.Mode.UNIQUE)safe=uniquePending(safe);
            var result=new ArrayList<>(prefix);result.addAll(safe);stops=List.copyOf(result);nextStop=prefix.size();
            lastSkippedStop=-1;lastSkippedBiome=null;backReviewTarget=null;pendingLineBuild=null;clearRouteLine();SafariGraph.get().resetDirectRouteChoices();
            if(!paused)refreshVisibleRouteLine();ChipsCrittersClient.LOGGER.info("Haunted relocation replan applied: reason={} origin={} now={} pending={}",reason,player,client.player.position(),safe);
        }));
    }

    public static void onFinderResume() {
		if (!pendingHideyhoRefresh) refreshVisibleRouteLine();
	}

	public static void hideyhoFound() {
        if(HideyhoSearch.active()){HideyhoSearch.stop(true,true);return;}
        if(HideyhoSearch.STATE.found)return;
		if (!active || biome != Biome.HAUNTED || !WaypointRunLedger.hideyhoFound()) return;
		pendingHideyhoRefresh = true;
		removeHideyhoSearchStops();scheduleHauntedReplan("Hideyho search ended");
	}

	public static void onFinderSearchEnded() {
		if (!active || biome != Biome.HAUNTED || !WaypointRunLedger.endHideyhoSearch()) return;
		pendingHideyhoRefresh = true;
		removeHideyhoSearchStops();scheduleHauntedReplan("Hideyho search ended");
	}

	private static void removeHideyhoSearchStops() {
		Minecraft client = Minecraft.getInstance();
		if (!active || biome != Biome.HAUNTED) return;
		if (client.player == null) return;
		if (stops.isEmpty()) { complete(client, client.player.position()); return; }
		int oldNextStop = Math.min(nextStop, stops.size());
		int updatedNextStop = 0;
		int removed = 0;
		int removedFuture = 0;
		List<Vec3> updatedStops = new java.util.ArrayList<>(stops.size());
		for (int i = 0; i < stops.size(); i++) {
			Vec3 stop = stops.get(i);
			if (SafariData.isHideyhoWaypoint(biome, stop)) {
				removed++;
				if (i >= oldNextStop) removedFuture++;
				continue;
			}
			if (i < oldNextStop) updatedNextStop++;
			updatedStops.add(stop);
		}
		if (removed == 0) return;
		nextStop = Math.min(updatedNextStop, updatedStops.size());
		runStopCount = Math.max(0, runStopCount - removedFuture);
		lastPingedStop = -1;
		lastSkippedStop = -1;
		lastSkippedBiome = null;
		skipCompletionDeadline = 0L;
		if (nextStop >= updatedStops.size()) {
			stops = List.copyOf(updatedStops);
			complete(client, client.player.position());
			return;
		}
		List<Vec3> prefix = new java.util.ArrayList<>(updatedStops.subList(0, nextStop));
		List<Vec3> pending = new java.util.ArrayList<>(updatedStops.subList(nextStop, updatedStops.size()));
		if (!pending.isEmpty()) {
			pending = new java.util.ArrayList<>(RoutePlanner.optimizeRemaining(biome, pending, client.player.position(), !gateVisited && nextStop == 0));
		}
		prefix.addAll(pending);
		stops = List.copyOf(prefix);
		lineAnchor = client.player.position();
		pendingLineBuild = null;
		clearRouteLine();
	}

	public static void hauntedCapture(String message) {
        if(IcyCaptureRules.isLocalCaptureMessage(message,"Doomspiral")){DOOM_COUNTDOWN.reset();DOOM_DINGS.reset();}
		var state = WaypointRunLedger.HAUNTED;
		WaypointRunLedger.HAUNTED_UNIQUE.capture(message);
        if (!state.capture(message) || !active || biome != Biome.HAUNTED) return;
        if(!state.doom){
            if(state.gold){
                WaypointRunLedger.markSatisfied(biome,SafariData.GIMMIEGOLD);
                int before=nextStop;var updated=new ArrayList<Vec3>();for(int i=0;i<stops.size();i++)if(!stops.get(i).equals(SafariData.GIMMIEGOLD))updated.add(stops.get(i));else if(i<before)nextStop--;
                stops=List.copyOf(updated);scheduleHauntedReplan("personal early Gimmiegold catch");
            }
            return;
        }
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
		if (state.gold) { complete(client,client.player.position(),"Haunted route complete"); return; }
		routeBuildGeneration++;hauntedReplanSerial++;hauntedReplanAt=0;hauntedReplanBusy=false;
		buildingRoute = false;
		pendingHideyhoRefresh = false;
		paused = false;
		skipCompletionDeadline = 0;
		lastSkippedStop = lastPingedStop = -1;
		lineAnchor = client.player.position();
		stops = List.of(SafariData.GIMMIEGOLD);
		nextStop = 0;
		clearRouteLine();
		line = buildRouteLine(lineAnchor,stops);
	}

	public static void onRelayWaypointClaim(Biome claimedBiome, Vec3 position) {
        if(HideyhoSearch.active()){var c=new RelayWaypointClaim(claimedBiome,position);if(!DEFERRED_RELAY_CLAIMS.contains(c))DEFERRED_RELAY_CLAIMS.add(c);return;}
		Minecraft client = Minecraft.getInstance();
		if (!active || biome != claimedBiome || client.player == null || stops.isEmpty()) return;
        if(claimedBiome==Biome.HAUNTED && SafariData.GIMMIEGOLD.equals(position))return;
		if (claimedBiome == Biome.HAUNTED
				&& HideyhoWaypointPolicy.deferRelayClaim(HideyhoFinderPause.active(), inHideyhoSearchPortion())) {
			RelayWaypointClaim claim = new RelayWaypointClaim(claimedBiome, position);
			if (!DEFERRED_RELAY_CLAIMS.contains(claim)) DEFERRED_RELAY_CLAIMS.add(claim);
			return;
		}
		if (claimedBiome == Biome.HAUNTED && WaypointRunLedger.HAUNTED.doom) return;
		int matchedIndex = -1;
		for (int index = nextStop; index < stops.size(); index++) {
			if (claimedBiome == Biome.CAVERN && SafariData.isCavernWaterfallConnector(stops.get(index))) continue;
			if (stops.get(index).distanceToSqr(position) <= 0.02) { matchedIndex = index; break; }
		}
		if (matchedIndex < 0) return;
		if (matchedIndex == 0 && !gateVisited) return;
		WaypointRunLedger.markSatisfied(claimedBiome, stops.get(matchedIndex));
        if(claimedBiome==Biome.HAUNTED)ChipsCrittersClient.LOGGER.info("Haunted stop cleared: target={}, reason=exact incoming relay claim",stops.get(matchedIndex));
		int sharedNumber=biome==Biome.HAUNTED?displayStopNumber(Math.max(0,matchedIndex-nextStop)):matchedIndex+1;
        int beforeCount = stops.size();
		List<Vec3> updated = new java.util.ArrayList<>(stops);
		updated.remove(matchedIndex);
		runStopCount = Math.max(0, runStopCount - 1);
		lastSkippedStop = -1;
		lastSkippedBiome = null;
		lastPingedStop = -1;
		List<Vec3> prefix = new java.util.ArrayList<>(updated.subList(0, Math.min(nextStop, updated.size())));
		List<Vec3> pending = new java.util.ArrayList<>(updated.subList(Math.min(nextStop, updated.size()), updated.size()));
		Vec3 routeOrigin = waitingForCavernWaterfall ? SafariData.CAVERN_WATERFALL_EXIT : client.player.position();
		if (!pending.isEmpty()) pending = new java.util.ArrayList<>(RoutePlanner.optimizeRemaining(biome, pending, routeOrigin, !gateVisited));
		prefix.addAll(pending);
		stops = List.copyOf(prefix);
		if (nextStop >= stops.size()) {
			if (biome == Biome.FOREST) {
				waitingForForestBird = true;
				line = List.of();
				lineGaps = Set.of();
				routeLineGap = false; routeLineWaitingChunks=false;
				lineAnchor = null;
				client.player.sendOverlayMessage(Component.literal("[ChipsCritters] Forest stops are complete. Use the Birdfeeder and catch the final bird."));
			} else {
				complete(client, client.player.position(), "Shared route stops complete");
			}
			return;
		}
		if (!paused && !waitingForCavernWaterfall) refreshVisibleRouteLine();
		int stopNumber = sharedNumber;
		client.player.sendOverlayMessage(Component.literal("[ChipsCritters] A teammate reached shared stop " + stopNumber + "; now on " + stopCounterText() + "."));
	}

	private static boolean inHideyhoSearchPortion() {
		return active && biome == Biome.HAUNTED && !WaypointRunLedger.hideyhoSearchEnded()
				&& nextStop >= 0 && nextStop < stops.size() && SafariData.isHideyhoWaypoint(biome, stops.get(nextStop));
	}

	private static void applyDeferredRelayClaims() {
		if (DEFERRED_RELAY_CLAIMS.isEmpty() || HideyhoFinderPause.active() || inHideyhoSearchPortion()) return;
		List<RelayWaypointClaim> queued = List.copyOf(DEFERRED_RELAY_CLAIMS);
		DEFERRED_RELAY_CLAIMS.clear();
		for (RelayWaypointClaim claim : queued) onRelayWaypointClaim(claim.biome(), claim.position());
	}

	public static void printStatus() {
		commandFeedback(RouteDataUpdates.status()+ (DevMode.enabled()?" [DEV]":" [PUBLIC]"));
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
		ChipsConfig config = ChipsConfig.get();
		String routeStatus;
		if (waitingForForestBird()) {
			routeStatus = "Forest Birdfeeder";
		} else if (waitingForCavernWaterfall()) {
			routeStatus = "At waterfall — follow the ascent route to Y=" + (int) SafariData.CAVERN_WATERFALL_RESUME_Y;
		} else if (finishingSkip()) {
			routeStatus = "Final stop skipped — undo window open";
		} else if (active && biome != null && nextStop < stops.size()) {
			Vec3 target = stops.get(nextStop);
			String instruction = SafariData.waypointInstruction(biome, target);
			routeStatus = biome.displayName() + " stop " + stopCounterText() + " (run identity " + (runOrder.indexOf(target)+1) + "/" + runOrder.size() + ")"
					+ " at " + String.format(java.util.Locale.ROOT, "(%.1f, %.1f, %.1f)", target.x, target.y, target.z)
					+ (instruction == null ? "" : " — " + instruction);
		} else if (assistBiome != null) {
			routeStatus = "assist at " + assistBiome.displayName() + " gate";
		} else {
			routeStatus = biome == null ? "none" : "inactive (" + biome.displayName() + ")";
		}
		client.player.sendSystemMessage(Component.literal("[ChipsCritters] Mode: " + modeName(config.mode)
				+ " | Route line: " + (config.routeRendering ? "ON" : "OFF")
				+ " | Waypoints: " + (config.waypointRendering ? "ON" : "OFF")));
		client.player.sendSystemMessage(Component.literal("[ChipsCritters] Route: " + routeStatus));
        if(active)commandFeedback("Player feet: "+client.player.position()+" | navigation target: "+displayTarget());
		client.player.sendSystemMessage(Component.literal("[ChipsCritters] Hideyho Finder: "
				+ (HideyhoFinderPause.active() ? "PAUSED" : "inactive") + " | Party biomes done: " + ClaimsManager.doneBiomeCount() + "/4"));
		client.player.sendSystemMessage(Component.literal("[ChipsCritters] " + ClaimsManager.biomeRosterText()));
	}

	private static String modeName(ChipsConfig.Mode mode) {
		return switch (mode) {
			case MONEY -> "Money";
			case UNIQUE -> "Unique";
			case FULL_CLEAR -> "Full Clear (Solo)";
		};
	}

	public static void forestAllBirdsCaught() {
        Minecraft client = Minecraft.getInstance();
        if (active && biome == Biome.FOREST && client.player != null)
            complete(client, client.player.position(), "Forest clear - All birds caught");
    }
    public static void forestFeedExhausted(String missingBirds) {
		Minecraft client = Minecraft.getInstance();
		if (!waitingForForestBird() || client.player == null) return;
		complete(client, client.player.position(), "Forest done — " + missingBirds);
	}
	public static List<Vec3> stops() { return HideyhoSearch.active()?HideyhoSearch.stops():stops; }
    public static Vec3 legOrigin(Vec3 target) {
        if(HideyhoSearch.active())return HideyhoSearch.origin();
        if (activeLadder!=null && ladderStage==LadderStage.CLIMB_TO_TOP && activeLadder.top().equals(target)) return activeLadder.bottom();
        int at=stops.indexOf(target);
        if(at>0)return stops.get(at-1);
        int original=runOrder.indexOf(target);
        if(original>0)return runOrder.get(original-1);
        return biome==null || (target!=null && target.equals(biome.gate()))?new Vec3(-50,69,22):biome.gate();
    }
    public static boolean activeRecordedLeg(){if(HideyhoSearch.active())return false;return active && nextStop<stops.size() && RecordedLegPaths.matches(biome,legOrigin(stops.get(nextStop)),stops.get(nextStop));}
    public static boolean hasSafeLineStart(){return RouteRenderer.hasSafeStart();}

	public static List<Vec3> reachedStops() {
        if(HideyhoSearch.active())return List.of();
		if (!active || paused || ChipsConfig.get().hideReachedWaypoints || nextStop <= 0) return List.of();
		return stops.subList(0, Math.min(nextStop, stops.size()));
	}
	public static List<Vec3> visibleStops() {
        if(HideyhoSearch.active())return HideyhoSearch.target()==null||HideyhoSearch.paused()?List.of():List.of(HideyhoSearch.target());
        if(DOOM_COUNTDOWN.active())return List.of();
        if(dropGuidance())return List.of(displayTarget());
		if (!active || paused || waitingForCavernWaterfall) return List.of();
		if (hillGuidance()) return List.of(hillSteps.get(hillStep));
		if (ladderStage != LadderStage.NONE && activeLadder != null) return List.of(activeLadder.bottom());
		if (forestJumpStage == ForestJumpStage.APPROACH_JUMP_OFF) return List.of(SafariData.forestTreeJumpOff());
		if (forestJumpStage == ForestJumpStage.JUMP_TO_TREE) return List.of(SafariData.forestTreeStop());
		return upcomingStops(nextStop);
	}
	public static String climbInstruction() { return activeLadder.name(); }

	public static Vec3 displayTarget() {
        if(HideyhoSearch.active())return HideyhoSearch.target();
        if(DOOM_COUNTDOWN.active())return null;
        if(dropGuidance())return dropStage==1?SafariData.CAVERN_DROP_START:SafariData.CAVERN_DROP_END;
		if (hillGuidance()) return hillSteps.get(hillStep);
		if (waitingForCavernWaterfall() && Minecraft.getInstance().player != null)
			return SafariData.cavernWaterfallSteps().get(SafariData.cavernWaterfallStepIndex(Minecraft.getInstance().player.getY()));
		if (active && ladderStage == LadderStage.APPROACH_BOTTOM && activeLadder != null) return activeLadder.bottom();
		if (active && ladderStage == LadderStage.CLIMB_TO_TOP && activeLadder != null) return activeLadder.top();
		if (active && forestJumpStage == ForestJumpStage.APPROACH_JUMP_OFF) return SafariData.forestTreeJumpOff();
		if (active && forestJumpStage == ForestJumpStage.JUMP_TO_TREE) return SafariData.forestTreeStop();
		return active && nextStop < stops.size() && !goldLocked(stops.get(nextStop)) && !ritualLocked(stops.get(nextStop)) ? stops.get(nextStop) : null;
	}
	public static boolean withinLadderReachArea(Vec3 player, Vec3 ladderBlock) {
		boolean realLadder=SafariData.cavernLadders().stream().anyMatch(l->l.name().equals("CLIMB LADDER")&&(l.bottom().equals(ladderBlock)||l.top().equals(ladderBlock)));
        return LadderReachRule.reached(player.x, player.y, player.z, ladderBlock.x, ladderBlock.y, ladderBlock.z,realLadder?2.5:1.5);
	}
	public static boolean ladderGuidance() { if(HideyhoSearch.active())return false; return active && ladderStage != LadderStage.NONE && activeLadder != null; }
	public static boolean ladderClimbing() { if(HideyhoSearch.active())return false; return active && ladderStage == LadderStage.CLIMB_TO_TOP && activeLadder != null; }
	public static Vec3 ladderTop() { return activeLadder == null ? null : activeLadder.top(); }
	public static Vec3 ladderBottom() { return activeLadder == null ? null : activeLadder.bottom(); }
	public static List<Vec3> line() { return HideyhoSearch.active()?HideyhoSearch.line():line; }
	public static int nextStop() { return HideyhoSearch.active()?0:Math.max(0,Math.min(nextStop,stops.size())); }
	public static boolean active() { return HideyhoSearch.active()||active; }
	public static boolean buildingRoute() { return HideyhoSearch.active()?HideyhoSearch.building():active && buildingRoute; }
	public static boolean forestJumpGuidance() { if(HideyhoSearch.active())return false; return active && biome == Biome.FOREST && forestJumpStage != ForestJumpStage.NONE; }
	public static boolean gateVisited() { return gateVisited; }
	public static Biome assistBiome() { return assistBiome; }
	public static double distanceToNext(Vec3 position) {
		Vec3 target = displayTarget();
		return (HideyhoSearch.active()||active) && !paused() && !waitingForCavernWaterfall() && target != null
				? position.distanceTo(SafariGraph.get().navigationTarget(biome(), target).add(0.5, 0.0, 0.5)) : Double.NaN;
	}

	public static void refreshVisibleRouteLine() {
        if(HideyhoSearch.active()){HideyhoSearch.refresh();return;}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
		if (!active || paused) { commandFeedback(!active ? "No active route to pause." : "Route is already paused."); return; }
		if(dropGuidance() && !RecordedLegPaths.hasTarget(biome,displayTarget())){if(dropStage==1)buildDropLine(client);return;}
        if(displayTarget()!=null && RecordedLegPaths.hasTarget(biome,displayTarget())){
            lineAnchor=client.player.position();line=buildRouteLine(lineAnchor,List.of(displayTarget()));return;
        }
        if (waitingForCavernWaterfall) {
			pendingLineBuild=null; lineGaps=Set.of(); routeLineGap=false;
			lineAnchor=client.player.position(); line=waterfallGuide(lineAnchor); return;
		}
		if(nextStop>=stops.size()) return;
		if (ladderGuidance() || hillGuidance()) {
			lineAnchor = client.player.position();
			if (ladderClimbing() || (hillGuidance() && hillStep > 0)) {
				pendingLineBuild = null;
				lineGaps = Set.of();
				routeLineGap = false; routeLineWaitingChunks=false;
				line = ladderClimbing()?climbLine(lineAnchor):List.of(lineAnchor, displayTarget().add(0.5, 0, 0.5));
			} else line = buildRouteLine(lineAnchor, List.of(displayTarget()));
			return;
		}
		if (forestJumpStage == ForestJumpStage.JUMP_TO_TREE) {
			pendingLineBuild = null;
			line = List.of(client.player.position(), SafariData.forestTreeStop().add(0.5, 0, 0.5));
			lineAnchor = SafariData.forestTreeJumpOff();
			return;
		}
		if (forestJumpStage == ForestJumpStage.APPROACH_JUMP_OFF) {
			lineAnchor = client.player.position();
			line = buildRouteLine(lineAnchor, List.of(SafariData.forestTreeJumpOff()));
			return;
		}
		lineAnchor = client.player.position();
		line = buildRouteLine(lineAnchor, upcomingStops(nextStop));
	}

	public static void onRouteChunksChanged() { routePathRecheckPending = true;UniqueGoldPlacement.chunksChanged(); }

	public static void clearManualFindRewardGuard(){skipManualHideyhoRewardReplan=false;}
    public static void resumeAfterSearch(boolean found){
        if(found){
            skipManualHideyhoRewardReplan=true;
            if(active&&biome==Biome.HAUNTED){
                var kept=new ArrayList<Vec3>();int cursor=0,removed=0;
                for(int i=0;i<stops.size();i++){Vec3 p=stops.get(i);if(SafariData.pureHideyhoSearch(p)){if(i>=nextStop)removed++;continue;}if(i<nextStop)cursor++;kept.add(p);}
                stops=List.copyOf(kept);nextStop=cursor;runStopCount=Math.max(0,runStopCount-removed);
            }
        }
        pendingLineBuild=null;clearRouteLine();lineAnchor=null;lineGapDetectedMillis=0;lineGapRetryCount=0;SafariGraph.get().resetDirectRouteChoices();
        if(active&&!paused)refreshVisibleRouteLine();
    }
    public static void doomGameMessage(String message){
        if(!ChipsCrittersClient.isInCritterSafari())return;
        if(DOOM_COUNTDOWN.message(message,System.currentTimeMillis()))ChipsCrittersClient.LOGGER.info("Doomspiral readiness signal: {}; delay={}ms (earliest confirmed)",message,DoomspiralCountdown.READY_DELAY_MILLIS);
    }
    private static void tickDoomCountdown(Minecraft client){
        long now=System.currentTimeMillis();boolean enabled=ChipsConfig.get().nearWaypointPing&&ChipsConfig.get().pingVolume>0;
        int dings=DOOM_DINGS.tick(now,DOOM_COUNTDOWN.active(),DOOM_COUNTDOWN.notifyOnce(now),enabled);
        if(dings>0&&client.player!=null)client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP,(float)ChipsConfig.get().pingVolume,1.45F);
    }
    private static void clearRouteLine() {
		line = List.of();
		lineGaps = Set.of();
		routeLineGap = false; routeLineWaitingChunks=false;
	}

	private static void complete(Minecraft client, Vec3 player) {
		complete(client, player, "Route complete");
	}

	private static void complete(Minecraft client, Vec3 player, String completionText) {
		complete(client, player, completionText, true);
	}

	private static void complete(Minecraft client, Vec3 player, String completionText, boolean recordEligible) {
        DevMode.routeChanged();
		if (!active) return;
        boolean saveEligible=recordEligible && manuallySkipped.isEmpty() && RUN_TIMES.eligible(biome);
        if(biome==Biome.HAUNTED && WaypointRunLedger.HAUNTED.replay)WaypointRunLedger.HAUNTED.finishOnce();
		if (biome == Biome.HAUNTED && recordEligible && !WaypointRunLedger.HAUNTED.replay && !WaypointRunLedger.HAUNTED.finishOnce()) {
			clearRouteLine();
			skipCompletionDeadline = 0;
			return;
		}
		routeBuildGeneration++;hauntedReplanSerial++;hauntedReplanAt=0;hauntedReplanBusy=false;
		buildingRoute = false;
		pendingLineBuild = null;
		active = false;
		waitingForForestBird = false;
		waitingForCavernWaterfall = false;
		paused = false;
		skipCompletionDeadline = 0L;
		ClaimsManager.done(biome, biome == Biome.FOREST && completionText.startsWith("Forest clear"));
		ChipsCrittersClient.markRunFinished();
		boolean newBest = false;
		if (RUN_TIMES.markCompleted(biome, ChipsConfig.get().mode, saveEligible) && startedAtMillis > 0L) {
			newBest = RunHistory.record(biome, System.currentTimeMillis() - startedAtMillis, runStopCount, ChipsConfig.get().mode);
		}
		assistBiome = ClaimsManager.nearestUnfinishedBiome(player);
		line = assistBiome == null ? List.of() : SafariGraph.get().routeLineFrom(assistBiome, player, List.of(assistBiome.gate()));
		lineAnchor = assistBiome == null ? null : player;
		String assist = assistBiome == null ? "no claimed teammate has an unfinished biome" : "nearest teammate " + ClaimsManager.ownerOf(assistBiome) + " — " + assistBiome.displayName() + " gate";
		String bestNotice = newBest ? " New personal best for " + biome.displayName() + " "
				+ RunHistory.modeLabel(ChipsConfig.get().mode) + ": "
				+ RunHistory.formatDuration(System.currentTimeMillis() - startedAtMillis) + "." : "";
		client.player.sendOverlayMessage(Component.literal("[ChipsCritters] " + completionText + "." + bestNotice + " Assist: " + assist));
	}

	private static List<Vec3> upcomingStops(int fromIndex) {
		int visibleCount = ChipsConfig.get().oneWaypointAtATime ? 1 : MAX_VISIBLE_STOPS_AHEAD;
		int end = Math.min(stops.size(), fromIndex + visibleCount);
		return fromIndex >= end ? List.of() : stops.subList(fromIndex, end).stream().filter(p->!goldLocked(p)&&!ritualLocked(p)).toList();
	}
}

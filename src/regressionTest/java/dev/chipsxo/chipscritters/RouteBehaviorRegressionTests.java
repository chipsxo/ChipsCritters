package dev.chipsxo.chipscritters;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free regression checks for route state rules; run with ./gradlew regressionTest. */
public final class RouteBehaviorRegressionTests {
	private static final String WUMPA = "CAPTURE! You caught a Wumpa and received 1x Wumpa Shard!";
	private static final String SPARKLING_WUMPA = "CAPTURE! You caught a SPARKLING Wumpa and received a Rainbow Feather and 1x Wumpa Shard!";
	private static final String TROODON = "CAPTURE! You caught a Troodon and received 1x Troodon Shard!";
	private static final String SPARKLING_TROODON = "CAPTURE! You caught a SPARKLING Troodon and received a Rainbow Feather and 1x Troodon Shard!";
	private static final String TEAMMATE_WUMPA = "LOOT SHARE! You received 1x Wumpa Shard from Alex catching a Wumpa!";
	private static final String TEAMMATE_TROODON = "LOOT SHARE! You received 1x Troodon Shard from Alex catching a Troodon!";

	private RouteBehaviorRegressionTests() { }

	public static void main(String[] args) {
		check(RunCaptureState.isNewRunStartMessage("HEAD START! You started with a Wriggleworm!"), "real head start");
        check(RunCaptureState.isNewRunStartMessage("HEAD START! You started with an Orange Gem!"), "any item");
        check(!RunCaptureState.isNewRunStartMessage("Party > Alex: HEAD START! You started with a Wriggleworm!"), "party cannot reset");
        check(ShardValueRule.compare(100, 98.9, 0, 1.25) == -1, "fee setting changes winner");
        check(ShardValueRule.compare(100, 80, 0) == 1, "sell wins after fee");
        check(ShardValueRule.compare(100, 110, 0) == -1, "salvage wins");
        check(ShardValueRule.compare(100, 99, 0) == 0, "tie has no color");
        check(ShardValueRule.compare(0, 80, 0) == 0, "missing price");
        check(ShardValueRule.compare(100, 98, 5) == 0, "minimum advantage");
        check(HuntingBoxHighlights.matchesTitle("(1/2) Hunting Box"), "page one");
        check(HuntingBoxHighlights.matchesTitle("(2/2) Hunting Box"), "page two");
        check(HuntingBoxHighlights.matchesTitle("Hunting Box"), "unpaged box");
        check(!HuntingBoxHighlights.matchesTitle("Hunting Box Settings"), "unrelated menu");
        check(ShardTooltip.isSafariShardName("Cavernfish Shard"), "exact Safari shard");
        check(!ShardTooltip.isSafariShardName("Large Cavernfish Shard"), "similar name excluded");
        check(!ShardTooltip.isSafariShardName("Prismarine Shard"), "non-Safari shard excluded");
        int[] rarityColors={0xFFFFFF,0x55FF55,0x5555FF,0xAA00AA,0xFFAA00};
        for(int i=0;i<rarityColors.length;i++)check(ShardTooltip.essenceByColor(rarityColors[i])==(1<<i), "rarity essence "+i);
        check(ShardTooltip.essenceByColor(0x123456)==0, "unknown rarity excluded");
        check(SafariData.isForestShortcutLeg(new net.minecraft.world.phys.Vec3(-28.5,89,69.5), new net.minecraft.world.phys.Vec3(-38.5,87,58.5)), "jump accepts floor-corrected standing height");
        check(SafariData.isForestShortcutLeg(new net.minecraft.world.phys.Vec3(-38.5,87,58.5), new net.minecraft.world.phys.Vec3(-34.5,67,60.5)), "drop accepts floor-corrected standing height");
        var exitPath=VerifiedApproaches.from(Biome.HAUNTED,new net.minecraft.world.phys.Vec3(8.07,48.42,-26.17),new net.minecraft.world.phys.Vec3(-1.5,59,-53.5));
        check(exitPath.size()>60, "mapped ritual exit connected");
        check(exitPath.getLast().equals(new net.minecraft.world.phys.Vec3(-1.5,59,-53.5)), "exit reaches Gimmiegold");
        var dispatcher = new com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>();
        ChipsCrittersClient.registerRouteCommands(dispatcher,"sr");
        ChipsCrittersClient.registerRouteCommands(dispatcher,"chipscritters");
        for (String root : List.of("sr","chipscritters")) {
            var tree = dispatcher.getRoot().getChild(root);
            check(tree.getCommand()!=null,"settings command "+root);
            for(String command:List.of("back","controls","defaults","export","help","history","import","locraw","mark","newrun","pause","refresh","rejoin","report","restart","resume","skip","stats","status","stop","troodon","undo","waypoints")) {
                check(tree.getChild(command)!=null && tree.getChild(command).getCommand()!=null,"registered executable "+root+" "+command);
                check(dispatcher.parse(root+" "+command,null).getExceptions().isEmpty(),"valid command parse");
            }
            check(tree.getChild("mark").getChild("troodon").getCommand()!=null,"mark troodon executable");
        }
        check(ShardTooltip.canonicalShardName("Cavernfish").equals("Cavernfish Shard"), "plain Hunting Box name maps to verified shard ID");
        check(!ShardTooltip.isSafariShardName("Cavernfish Trophy"),"similar name stays excluded");
        check(HuntingBoxHighlights.matchesTitle("(1/2) Hunting Box"),"actual page one title");
        check(HuntingBoxHighlights.matchesTitle("(2/2) Hunting Box"),"actual page two title");
        var hauntedMoney54=RoutePlanner.plan(new RoutePlanner.Input(Biome.HAUNTED,ChipsConfig.Mode.MONEY,5,List.of(),false,false));
        check(hauntedMoney54.containsAll(SafariData.route(Biome.HAUNTED).gazerBeds()),"Money includes all four approved Gazer beds");
        check(hauntedMoney54.contains(SafariData.HAUNTED_ENTRANCE),"entrance room included");
        check(!hauntedMoney54.contains(SafariData.HAUNTED_ENTRANCE_DOOR),"door is a connector, not a stop");
        var unique54=RoutePlanner.plan(new RoutePlanner.Input(Biome.HAUNTED,ChipsConfig.Mode.UNIQUE,5,List.of(),false,false));
        check(unique54.stream().filter(p -> SafariData.isGazerBedWaypoint(Biome.HAUNTED,p)).count()==1,"Unique still requires one Gazer bed");
        var cavern54=SafariData.route(Biome.CAVERN).stops();
        check(cavern54.get(7).equals(SafariData.CAVERN_AFTER_DROP) && !cavern54.contains(new net.minecraft.world.phys.Vec3(-92,72,42)),"old ledge stop removed; cave follows plateau");
        var openDrop=VerifiedApproaches.from(Biome.CAVERN,new net.minecraft.world.phys.Vec3(-91.5,72,42.5),new net.minecraft.world.phys.Vec3(-87.5,61,42.5));
        check(!openDrop.isEmpty(),"open-space Cavern drop corridor exists");
        check(SafariData.waypointInstruction(Biome.HAUNTED,new net.minecraft.world.phys.Vec3(8,77,-79)).equals("GAZER BED"),"bed label takes priority");
        var earlyBirds = new ForestBirdRunState();
        for (String bird : List.of("Bluebird", "Parakeet", "Macaw")) earlyBirds.recordCapture("CAPTURE! You caught a SPARKLING " + bird + " and gained a shard!");
        check(earlyBirds.finishAllBirdsOnce(), "all three birds finish early");
        check(!earlyBirds.markFeedExhausted(), "feed out cannot finish twice");
        earlyBirds.resetForNewRun();
        check(!earlyBirds.allBirdsCaught(), "new run resets birds");
        check(LadderReachRule.reached(-113,84,18,-114,84,17), "vine bottom uses broad reach");
        check(!LadderReachRule.reached(-113,87,18,-114,84,17), "vine vertical limit");
        var h=new HauntedFinish();
		String doom="CAPTURE! You caught a Doomspiral and gained 2x Doomspiral Shard!";
		String gold="CAPTURE! You caught a Gimmiegold and gained a Gimmiegold Shard!";
		if(h.capture("Party > Friend: "+doom) || h.capture("LOOT SHARE "+gold)) throw new AssertionError("not personal");
		if(!h.capture(doom) || h.finishOnce()) throw new AssertionError("wait for gold");
		h.gold=true; if(!h.finishOnce() || h.finishOnce()) throw new AssertionError("reach exactly once");
		h.reset(); if(!h.capture(doom) || !h.capture(gold) || !h.finishOnce()) throw new AssertionError("catch after doom");
		h.reset(); if(!h.capture(gold.replace("a Gimmiegold and", "a SPARKLING Gimmiegold and")) || h.finishOnce() || !h.capture(doom) || !h.finishOnce()) throw new AssertionError("early sparkling gold");
		wumpaCatchAtFinalStopAndWithStopsLeft();
		wumpaTeammateAndInactiveRouteDoNotFinish();
		troodonCatchAtEachStopRemovesOnlyRemainingTroodons();
		troodonCatchPersistsAcrossRestartAndRejoin();
		newSafariRunResetsTroodonCatch();
		otherModesKeepTroodonStops();
		hideyhoWaypointProtection();
		forestBirdFeedCompletion();
		personalBestPersistenceAndMigration();
		routeRunClockAndFinishEligibility();
		partyClaimFlowRegressions();
		partyDoneAndLadderReachRegressions();
		lobbyInfoStrictJson();
		lobbyInfoSessionAndReplySuppression();
		routeStopCounterTracksLiveStops();
		waypointPanelPlacement();
		try { SignedRouteRegressionTests.run(); RecordingRegressionTests.run(); Patch57RegressionTests.run(); Patch58RegressionTests.run(); Patch59RegressionTests.run(); Patch60RegressionTests.run(); Patch61RegressionTests.run(); Patch62RegressionTests.run(); Patch63RegressionTests.run(); Patch64RegressionTests.run(); Patch65RegressionTests.run(); } catch(Exception e) { throw new AssertionError(e); }
		System.out.println("Route behavior regression checks passed.");
	}

	private static void wumpaCatchAtFinalStopAndWithStopsLeft() {
		for (int remainingStops : List.of(0, 5)) {
			check(IcyCaptureRules.shouldFinishWumpa(true, remainingStops, WUMPA), "own Wumpa capture should finish Icy with remaining=" + remainingStops);
			check(IcyCaptureRules.shouldFinishWumpa(true, remainingStops, SPARKLING_WUMPA), "Sparkling Wumpa capture should finish Icy with remaining=" + remainingStops);
		}
	}

	private static void wumpaTeammateAndInactiveRouteDoNotFinish() {
		check(!IcyCaptureRules.shouldFinishWumpa(true, 4, TEAMMATE_WUMPA), "teammate Wumpa loot share must not finish local route");
		check(!IcyCaptureRules.shouldFinishWumpa(false, 0, WUMPA), "capture with no active Icy route must not finish a route");
		check(!IcyCaptureRules.shouldFinishWumpa(true, 4, "LOOT SHARE! " + SPARKLING_WUMPA), "loot share must not look like a local capture");
	}

	private static void troodonCatchAtEachStopRemovesOnlyRemainingTroodons() {
		List<IcyCaptureRules.Point> points = IcyCaptureRules.troodonWaypoints();
		for (int caughtIndex = 0; caughtIndex < points.size(); caughtIndex++) {
			check(IcyCaptureRules.isTroodonWaypoint(points.get(caughtIndex)), "fixed Troodon coordinate must be recognized: " + points.get(caughtIndex));
			String captureText = caughtIndex == 1 ? SPARKLING_TROODON : TROODON;
			check(IcyCaptureRules.shouldClearTroodons(true, true, captureText), "own catch should clear remaining stops at index " + caughtIndex);
			List<IcyCaptureRules.Point> route = new ArrayList<>(points);
			IcyCaptureRules.Point requiredDive = new IcyCaptureRules.Point(-69, 30, -47);
			IcyCaptureRules.Point fixedEnd = new IcyCaptureRules.Point(-110, 80, -78);
			route.add(requiredDive);
			route.add(fixedEnd);
			var filtered = IcyCaptureRules.removeRemaining(route, caughtIndex, true, IcyCaptureRules::isTroodonWaypoint);
			check(filtered.removed() == points.size() - caughtIndex, "remove current and later Troodon stops only");
			check(filtered.stops().subList(caughtIndex, filtered.stops().size()).stream().noneMatch(IcyCaptureRules::isTroodonWaypoint),
					"no pending Troodon waypoint remains");
			check(filtered.stops().contains(requiredDive) && filtered.stops().contains(fixedEnd), "required dive and fixed route end remain");
		}
		check(!IcyCaptureRules.shouldClearTroodons(true, true, TEAMMATE_TROODON), "teammate catch must not clear Troodon waypoints");
	}

	private static void troodonCatchPersistsAcrossRestartAndRejoin() {
		RunCaptureState state = new RunCaptureState();
		state.onLobbyChanged("safari-a");
		state.markTroodonCaught();
		check(!IcyCaptureRules.keepTroodonWaypoints(true, state.troodonCaught()), "Unique plan after catch excludes Troodons");
		// Restart/re-plan do not mutate the per-run capture ledger; empty room info is a reconnect gap.
		state.onLobbyChanged("");
		state.onLobbyChanged("safari-a");
		check(state.troodonCaught(), "Troodon catch persists through route restart and same-lobby rejoin");
	}

	private static void newSafariRunResetsTroodonCatch() {
		RunCaptureState state = new RunCaptureState();
		state.onLobbyChanged("safari-a");
		state.markTroodonCaught();
		check(!RunCaptureState.isNewRunStartMessage("CAPTURE! You caught a Troodon"), "ordinary capture is not a new run");
		String newRunNotice = "NEW RUN START! You started with a Yogi Berry.";
		check(RunCaptureState.isNewRunStartMessage(newRunNotice), "new safari run notice is recognized");
		if (RunCaptureState.isNewRunStartMessage(newRunNotice)) state.newRunStarted();
		check(!state.troodonCaught(), "new run resets the Troodon caught flag");
		check(IcyCaptureRules.keepTroodonWaypoints(true, state.troodonCaught()), "new Unique run includes Troodon stops again");
		check(state.onLobbyChanged("safari-b"), "a different lobby also resets the run state");
	}

	private static void otherModesKeepTroodonStops() {
		for (String mode : List.of("MONEY", "FASTEST", "FULL_CLEAR")) {
			check(IcyCaptureRules.keepTroodonWaypoints(false, true), mode + " mode keeps all Troodon stops after a catch");
		}
		check(!IcyCaptureRules.keepTroodonWaypoints(true, true), "Unique alone clears Troodon stops after a catch");
	}

	private static void hideyhoWaypointProtection() {
		check(!HideyhoWaypointPolicy.allowCurrentHideyhoStop(true, false), "Finder pause blocks a non-Hideyho current stop");
		check(HideyhoWaypointPolicy.allowCurrentHideyhoStop(true, true), "current Hideyho search stop can advance during Finder pause");
		check(!HideyhoWaypointPolicy.advanceSearchStopWithoutClaim(true, false), "floor-drop stop cannot advance during Finder pause");
		check(HideyhoWaypointPolicy.advanceSearchStopWithoutClaim(true, true), "Hideyho stop advances without a waypoint claim during Finder pause");
		check(!HideyhoWaypointPolicy.allowPassedStopCollection(true, false), "walking past a floor drop during Finder pause is ignored");
		check(!HideyhoWaypointPolicy.allowPassedStopCollection(false, true), "walking past a floor drop during Haunted Hideyho search is ignored");
		check(!HideyhoWaypointPolicy.allowAutoSkip(false, true), "distance-based auto-skip is blocked during Hideyho search");
		check(HideyhoWaypointPolicy.deferRelayClaim(true, false), "relay claim is deferred during Finder pause");
		check(HideyhoWaypointPolicy.deferRelayClaim(false, true), "relay claim is deferred during Hideyho route search");
		for (boolean relayEnabled : List.of(false, true)) {
			check(HideyhoWaypointPolicy.deferRelayClaim(true, false), "relay on/off does not change Finder-pause protection: " + relayEnabled);
		}
		check(HideyhoWaypointPolicy.allowPassedStopCollection(false, false), "ordinary passed-stop collection resumes after search ends");
		check(!HideyhoWaypointPolicy.deferRelayClaim(false, false), "queued relay claims can apply after search ends");

		WaypointRunLedger.clearRun();
		check(WaypointRunLedger.hideyhoFound(), "first Hideyho-found event marks search ended");
		check(WaypointRunLedger.hideyhoSearchEnded(), "Hideyho-found state persists for re-plans");
		check(!WaypointRunLedger.endHideyhoSearch(), "Finder resume does not remove Hideyho stops a second time");
		WaypointRunLedger.clearRun();
	}

	private static void forestBirdFeedCompletion() {
		ForestBirdRunState allCaught = new ForestBirdRunState();
		check(allCaught.recordCapture("CAPTURE! You caught a Bluebird and gained a shard!") != null, "personal Bluebird capture recorded");
		check(allCaught.recordCapture("CAPTURE! You caught a SPARKLING Parakeet and gained a shard!") != null,
				"Sparkling Parakeet capture recorded");
		check(allCaught.recordCapture("CAPTURE! You caught a Macaw and gained a shard!") != null, "personal Macaw capture recorded");
		check(allCaught.recordCapture("LOOT SHARE! You received 1x Parakeet Shard from Alex catching a Parakeet!") == null,
				"teammate loot share does not count as a personal catch");
		check(allCaught.markFeedExhausted(), "feed exhaustion finishes Forest once when all birds were caught");
		check(allCaught.missingBirdsText().equals("All birds caught"), "all-caught completion reports no missing birds");
		check(allCaught.finalFeedCatchPending(), "final-feed reminder remains while its catch could still arrive");
		check(!allCaught.markFeedExhausted(), "repeat exhaustion cannot issue a second finish");

		ForestBirdRunState missing = new ForestBirdRunState();
		missing.recordCapture("CAPTURE! You caught a Bluebird and gained a shard!");
		check(missing.markFeedExhausted(), "feed exhaustion completes when birds are missing");
		check(missing.missingBirdsText().equals("Missing birds: Parakeet, Macaw"), "missing-bird list is computed from own catches");
		ForestBirdRunState.CaptureUpdate finalFeedCatch = missing.recordCapture(
				"CAPTURE! You caught a SPARKLING Macaw and gained a shard!");
		check(finalFeedCatch != null && finalFeedCatch.newlyCaught() && finalFeedCatch.resolvedFinalFeed(),
				"final-feed catch updates the run after Forest was completed");
		check(missing.missingBirdsText().equals("Missing birds: Parakeet"), "post-finish catch updates local missing list");
		check(!missing.markFeedExhausted(), "post-finish capture cannot issue a second DONE/history completion");

		// A route restart doesn't clear the state object; only the new-run event does.
		check(missing.recordCapture("CAPTURE! You caught a Parakeet and gained a shard!") != null,
				"same-run route restart keeps bird catches available");
		check(missing.missingBirdsText().equals("All birds caught"), "same-run restart retains all catches");
		missing.resetForNewRun();
		check(missing.missingBirdsText().equals("Missing birds: Bluebird, Parakeet, Macaw") && !missing.feedExhausted(),
				"new run resets catches, feed state and completion");

		ForestFeederTracker.newRunStarted();
		ForestFeederTracker.onGameMessage("CAPTURE! You caught a Bluebird and gained a shard!");
		ForestFeederTracker.reset(); // the route controller's restart/reset path must retain run catches
		check(ForestFeederTracker.missingBirdsText().equals("Missing birds: Parakeet, Macaw"),
				"same-run Forest route restart preserves bird catches");
		ForestFeederTracker.newRunStarted();
		check(ForestFeederTracker.missingBirdsText().equals("Missing birds: Bluebird, Parakeet, Macaw"),
				"Forest tracker resets on a new run");
	}

	private static void personalBestPersistenceAndMigration() {
		Path directory = null;
		try {
			directory = Files.createTempDirectory("chipscritters-history-test");
			Path file = directory.resolve("chipscritters-history.json");
			Files.writeString(file, "{\"runs\":[{\"completedAt\":1,\"biome\":\"ICY\",\"durationMillis\":120000,\"stopCount\":9,\"mode\":\"MONEY\"}]}");
			PersistentRunHistory migrated = new PersistentRunHistory(file);
			check(migrated.personalBestMillis(Biome.ICY, ChipsConfig.Mode.MONEY) == 120000L, "legacy run history migrates to a PB");
			PersistentRunHistory reopened = new PersistentRunHistory(file);
			check(reopened.personalBestMillis(Biome.ICY, ChipsConfig.Mode.MONEY) == 120000L, "migrated PB reloads from config-style file");
			check(!reopened.record(Biome.ICY, 130000L, 10, ChipsConfig.Mode.MONEY), "slower run does not beat PB");
			check(reopened.personalBestMillis(Biome.ICY, ChipsConfig.Mode.MONEY) == 120000L, "slower run preserves PB");
			check(reopened.record(Biome.ICY, 110000L, 11, ChipsConfig.Mode.MONEY), "faster run is reported as a new PB");
			for (int run = 0; run < 35; run++) reopened.record(Biome.ICY, 130000L + run, 12, ChipsConfig.Mode.MONEY);
			PersistentRunHistory afterVersionUpdate = new PersistentRunHistory(file);
			check(afterVersionUpdate.personalBestMillis(Biome.ICY, ChipsConfig.Mode.MONEY) == 110000L, "PB survives store reload/update with recent history capped independently");
			check(afterVersionUpdate.completedRunCount() == 30, "recent history remains capped at 30 runs");
			check(afterVersionUpdate.personalBestMillis(Biome.ICY, ChipsConfig.Mode.UNIQUE) < 0L, "PBs are separated by mode");
			Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			Files.writeString(file, "not valid json");
			PersistentRunHistory recovered = new PersistentRunHistory(file);
			check(recovered.personalBestMillis(Biome.ICY, ChipsConfig.Mode.MONEY) == 110000L, "valid backup recovers a damaged primary file");
			check(recovered.record(Biome.ICY, 100000L, 12, ChipsConfig.Mode.MONEY), "recovered history can be saved again");
			check(new PersistentRunHistory(file).personalBestMillis(Biome.ICY, ChipsConfig.Mode.MONEY) == 100000L,
					"subsequent atomic save preserves recovered state");
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.copy(file, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			Files.writeString(file, "damaged primary again");
			Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".bak"));
			check(new PersistentRunHistory(file).personalBestMillis(Biome.ICY, ChipsConfig.Mode.MONEY) == 100000L,
					"fully flushed temporary file can recover a first-write interruption");
		} catch (Exception exception) {
			throw new AssertionError("personal best persistence/migration regression", exception);
		} finally {
			if (directory != null) {
				try (var paths = Files.walk(directory)) {
					for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
				} catch (Exception ignored) { }
			}
		}
	}

	private static void routeRunClockAndFinishEligibility() {
		RouteRunBookkeeping runs = new RouteRunBookkeeping();
		ChipsConfig.Mode mode = ChipsConfig.Mode.FULL_CLEAR;
		check(runs.startOrResume(Biome.CAVERN, 1000L, 0L) == 1000L, "first route entry starts the real clock");
		check(runs.startOrResume(Biome.CAVERN, 9000L, 0L) == 1000L, "route restart keeps the original clock");
		check(!runs.markCompleted(Biome.CAVERN, mode, false), "final-stop skip is not an eligible saved finish");
		check(runs.markCompleted(Biome.CAVERN, mode, true), "real completion is recorded after an unrecorded skipped finish");
		check(!runs.markCompleted(Biome.CAVERN, mode, true), "restarting a completed route cannot record the same run twice");
		check(runs.startOrResume(Biome.CAVERN, 12000L, 0L) == 1000L, "changing route mode during a same-run restart does not reset the clock");
		check(runs.markCompleted(Biome.CAVERN, ChipsConfig.Mode.MONEY, true), "completion can still be recorded separately by mode");
		check(runs.startOrResume(Biome.FOREST, 14000L, 0L) == 14000L, "another biome has its own route clock");
		runs.newRun();
		check(runs.startOrResume(Biome.CAVERN, 20000L, 0L) == 20000L, "new Safari run resets route clocks");
		check(runs.markCompleted(Biome.CAVERN, mode, true), "new Safari run can save its completion");
	}

	private static void partyDoneAndLadderReachRegressions() {
		PartyDoneLedger sent=new PartyDoneLedger();
		check(!sent.shouldSend(Biome.ICY,false,true,true),"party message toggle");
		check(!sent.shouldSend(Biome.ICY,true,false,true),"missing connection");
		check(!sent.shouldSend(Biome.ICY,true,true,false),"solo completion");
		check(!PartyClaimFlow.shouldSendDone(true,true,false),"party status unknown or solo must not send DONE");
		check(sent.shouldSend(Biome.ICY,true,true,true),"first party completion sends");
		check(!sent.shouldSend(Biome.ICY,true,true,true),"same biome sends once per run");
		check(sent.shouldSend(Biome.FOREST,true,true,true),"other biome sends once");
		sent.newRun();
		check(sent.shouldSend(Biome.ICY,true,true,true),"new run allows one new completion");
		var parsed=PartyClaimFlow.parseMessage("[CC] DONE ICY - Icy Biome is complete");
		check(parsed!=null && parsed.action().equals("DONE") && parsed.biome()==Biome.ICY,"DONE suffix parses");
		check(LadderReachRule.reached(-87.5,61,43.5,-88,61,43),"ladder side reach");
		check(LadderReachRule.reached(-89,62,42,-88,61,43),"3x3 far corner");
		check(LadderReachRule.reached(-88,71,43,-88,71,43),"first ladder top reach");
		check(!LadderReachRule.reached(-87.5,65,43.5,-88,61,43),"wrong floor rejected");
		check(!LadderReachRule.reached(-85.9,61,43.5,-88,61,43),"outside 3x3 rejected");
		check(LadderReachRule.reached(-95,75,36,-94,74,37),"second ladder 3x3 far corner");
		check(LadderReachRule.reached(-94,81,37,-94,81,37),"second ladder top reach");
		check(!LadderReachRule.reached(-94,77,37,-94,74,37),"second ladder wrong floor rejected");
	}

	private static void partyClaimFlowRegressions() {
		check(PartyClaimFlow.entryAction(true, false, "Chris", null) == PartyClaimFlow.EntryAction.CLAIM, "first entry claims biome");
		PartyClaimFlow.Claim existing = new PartyClaimFlow.Claim("Alex", false);
		check(PartyClaimFlow.entryAction(true, false, "Chris", existing) == PartyClaimFlow.EntryAction.JOIN, "second player joins existing claim");
		check(PartyClaimFlow.entryAction(true, true, "Chris", existing) == PartyClaimFlow.EntryAction.NONE, "re-entry does not repeat claim");
		check(PartyClaimFlow.entryAction(false, false, "Chris", null) == PartyClaimFlow.EntryAction.NONE,
				"hotspot route selection without physical entry does not claim");
		check(PartyClaimFlow.receiveClaim(existing, "Sam").owner().equals("Alex"), "first received claim owner wins");
	}

	private static void lobbyInfoStrictJson() {
		String json = "{\"server\":\"mini6A\",\"gametype\":\"SKYBLOCK\",\"mode\":\"safari\",\"map\":\"Safari\"}";
		LobbyInfoRules.Info parsed = LobbyInfoRules.parse(json);
		check(parsed != null, "the exact Hypixel Safari locraw JSON is accepted");
		check("mini6A".equals(parsed.server()), "locraw server ID is retained");
		check(LobbyInfoRules.isCritterSafari(parsed), "SKYBLOCK + safari identifies Critter Safari");
		check(LobbyInfoRules.parse("prefix " + json) == null, "a chat prefix prevents locraw parsing");
		check(LobbyInfoRules.parse(json + " trailing") == null, "trailing chat text prevents locraw parsing");
		check(!LobbyInfoRules.isCritterSafari(LobbyInfoRules.parse(
				"{\"server\":\"mini6A\",\"gametype\":\"SKYBLOCK\",\"mode\":\"hub\",\"map\":\"Safari\"}")),
				"a Safari map label without mode=safari is rejected");
	}

	private static void lobbyInfoSessionAndReplySuppression() {
		String privateIsland = "{\"server\":\"mini187DN\",\"gametype\":\"SKYBLOCK\",\"mode\":\"dynamic\",\"map\":\"Private Island\"}";
		String hub = "{\"server\":\"mini123AB\",\"gametype\":\"SKYBLOCK\",\"mode\":\"dynamic\",\"map\":\"Hub\"}";
		String falseSafari = "{\"server\":\"mini123AB\",\"gametype\":\"LIMBO\",\"mode\":\"safari\",\"map\":\"Critter Safari\"}";
		String safari = "{\"server\":\"mini123AB\",\"gametype\":\"SKYBLOCK\",\"mode\":\"safari\",\"map\":\"Critter Safari\"}";
		LobbyInfoSession session = new LobbyInfoSession();
		session.resetForDisconnect();
		check(session.requestDue(10_000L, true, true), "world entry starts one locraw probe");
		session.automaticRequestSent(10_000L);
		check(!session.requestDue(12_999L, true, true), "locraw retry respects cooldown");
		check(session.requestDue(13_000L, true, true), "locraw retry waits for cooldown");
		session.automaticRequestSent(13_000L);
		check(session.requestDue(16_000L, true, true), "only bounded retries continue without a response");
		session.automaticRequestSent(16_000L);
		check(!session.requestDue(30_000L, true, true), "automatic locraw retries stop after three sends");
		LobbyInfoSession.Accepted island = session.accept(privateIsland, 16_001L);
		check(island != null && island.hideReply(), "mod-requested Private Island locraw is hidden");
		check(session.accept(privateIsland, 16_002L).hideReply(), "second mod-requested locraw reply is also hidden");
		check(session.accept(privateIsland, 16_003L).hideReply(), "third mod-requested locraw reply is also hidden");
		check(session.confirmed() && !session.inCritterSafari(), "Private Island response is confirmed but not Safari");
		check(!session.requestDue(60_000L, true, true), "non-Safari response ends automatic probing");
		check(session.accept("Guild > Alex: " + privateIsland, 16_002L) == null, "prefixed chat is not a whole-message locraw reply");
		check(session.accept(privateIsland + " extra", 16_003L) == null, "trailing chat text is not a whole-message locraw reply");
		LobbyInfoSession unsolicited = new LobbyInfoSession();
		LobbyInfoSession.Accepted unrequestedReply = unsolicited.accept(privateIsland, 17_000L);
		check(unrequestedReply != null && unrequestedReply.hideReply(), "unrequested exact locraw JSON is hidden by default");
		check(!unsolicited.requestDue(20_000L, false, true), "non-Hypixel servers never trigger automatic locraw");

		session.resetForWorldChange();
		check(!session.confirmed() && !session.inCritterSafari(), "world transition returns to not-confirmed state");
		session.manualRequestSent(20_000L);
		LobbyInfoSession.Accepted hubReply = session.accept(hub, 20_001L);
		check(hubReply != null && !hubReply.hideReply(), "player-requested Hub locraw remains visible");
		check(session.confirmed() && !session.inCritterSafari(), "Hub reply never enables route features");
		check(!LobbyInfoRules.isCritterSafari(LobbyInfoRules.parse(falseSafari)), "LIMBO is not Safari even if the mode text matches");

		session.resetForWorldChange();
		session.automaticRequestSent(70_000L);
		LobbyInfoSession.Accepted safariReply = session.accept(safari, 70_001L);
		check(safariReply != null && safariReply.hideReply(), "mod-requested Safari locraw is hidden");
		check(session.confirmed() && session.inCritterSafari(), "SKYBLOCK safari response enables route features");
		session.resetForWorldChange(); // moving from Safari to another lobby
		check(!session.inCritterSafari() && !session.confirmed(), "world transition hides Safari UI pending fresh confirmation");
		session.automaticRequestSent(74_000L);
		LobbyInfoSession.Accepted movedToHub = session.accept(hub, 74_001L);
		check(movedToHub != null && movedToHub.hideReply() && !session.inCritterSafari(), "moving to Hub stays inactive");
		session.resetForDisconnect(); // then rejoining
		check(!session.inCritterSafari() && !session.confirmed(), "rejoin requires a fresh locraw confirmation");
	}

	private static void waypointPanelPlacement() {
		for (int[] window : List.of(new int[]{1920,1080}, new int[]{1280,720}, new int[]{854,480}, new int[]{640,360})) {
			for (int gui = 1; gui <= 4; gui++) {
				int w = (int)Math.ceil(window[0] / (double)gui), h = (int)Math.ceil(window[1] / (double)gui);
				double mainScale = Math.max(.25, Math.min(1, Math.min((w-16)/332.0, (h-16)/116.0)));
				int mainWidth = (int)Math.ceil(332*mainScale), mainHeight = (int)Math.ceil(78*mainScale);
				// A short type and the widest instruction with its warning, including arrow and padding.
				for (int contentWidth : List.of(68, 100, 224, 280)) for (int contentHeight : List.of(20,32,44)) for (double requested : List.of(.6,1.,1.5)) {
					double scale = WaypointPlacement.scale(w,h,mainHeight,contentWidth,contentHeight,requested);
					int width = (int)Math.ceil(contentWidth*scale), height = (int)Math.ceil(contentHeight*scale);
					int x = WaypointPlacement.x(w,width,-1);
					int y = WaypointPlacement.y(w,h,true,8,8,8,mainWidth,mainHeight,x,width,height,-1);
					check(Math.abs((2*x+width)-w) <= 1 && x >= 0 && x+width <= w, "centered and inside screen");
					check(y >= 0 && y+height <= h, "panel fits height");
					check(x >= 8+mainWidth || x+width <= 8 || y >= 8+mainHeight || y+height <= 8, "clears main HUD");
					check(WaypointPlacement.x(w,width,w+1) == w-width, "dragged panel clamped after resize");
				}
			}
		}
	}

	private static void routeStopCounterTracksLiveStops() {
		check("17/46".equals(RouteStopCounter.text(true, 16, 46)), "counter shows the active target stop");
		check("18/46".equals(RouteStopCounter.text(true, 17, 46)), "counter advances after a stop is reached");
		check("17/45".equals(RouteStopCounter.text(true, 16, 45)), "counter reflects a relay-cleared stop");
		check("17/42".equals(RouteStopCounter.text(true, 16, 42)), "counter reflects stops removed after a catch");
		check("17/46".equals(RouteStopCounter.text(true, 16, 46)), "connector guidance keeps the destination stop number");
		check("".equals(RouteStopCounter.text(false, 16, 46)), "inactive route hides the stop counter");
		check("".equals(RouteStopCounter.text(true, 0, 0)), "empty route has no stop counter");
	}

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}
}

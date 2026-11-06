package dev.chipsxo.chipscritters;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import com.mojang.brigadier.CommandDispatcher;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;

public final class ChipsCrittersClient implements ClientModInitializer {
	public static final String MOD_ID = "chipscritters";
	public static final Logger LOGGER = LogUtils.getLogger();
	private static final Pattern HOTSPOT = Pattern.compile("HOTSPOT! Your Hunting Hotspot is the (Icy|Cavern|Forest|Haunted) Biome!", Pattern.CASE_INSENSITIVE);
	private static final String WUMPA_SPAWN_MESSAGE = "You hear the sound of massive footsteps echoing through the Icy Biome... What could it be?";
	private static final double BIOME_GATE_ENTRY_RADIUS = 20.0;
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "controls"));
	private static KeyMapping recordStopKey;
	private static KeyMapping warpKey;
	private static KeyMapping dismissReminderKey;
	private static boolean settingsScreenRequested;
	private static java.util.function.Supplier<net.minecraft.client.gui.screens.Screen> pendingCommandScreen;
	private static Biome currentGateArea;
	private static Biome pendingHotspot;
	private static long pendingHotspotExpires;
	private static Biome currentBiomeArea;
	private static boolean wasInCritterSafari;
	private static final LobbyInfoSession LOBBY_INFO = new LobbyInfoSession();
    private static final java.util.Map<Component,Boolean> LOCRAW_VISIBILITY=new java.util.IdentityHashMap<>();
	private static boolean sendingAutomaticLocraw;
	private static long lastNewRunMessageMillis;
	private static ClientLevel observedLevel;
	private static String lastSafariServer = "";
	private static boolean runFinished;

	@Override
	public void onInitializeClient() {
		ChipsConfig.get();
		RouteDataUpdates.initialize();
		RecordedLegPaths.reload();
		SafariGraph.get();
		for (Biome biome : Biome.values()) SafariData.route(biome);
		registerKeys();
		registerChatAndCommands();
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			ForestFeederTracker.screenOpened(screen);
			ScreenEvents.remove(screen).register(ForestFeederTracker::screenRemoved);
		});
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.level != observedLevel) {
				clearSafariFeatures();
				LOBBY_INFO.resetForWorldChange();
				observedLevel = client.level;
				if (client.level != null) requestLobbyInfo();
			}
			requestLobbyInfo(); // bounded: one request plus at most two cooldown retries per world
			boolean inCritterSafari = isInCritterSafari(client);
			if (inCritterSafari && !wasInCritterSafari) {
				RouteDataUpdates.check();
				RoutePlanService.prewarm(ChipsConfig.get());
				WaypointRelay.updateConfiguration();
			}
			if (!inCritterSafari && wasInCritterSafari) {
				clearSafariFeatures();
			}
			wasInCritterSafari = inCritterSafari;
			if (inCritterSafari) HideyhoFinderPause.tick();
			else HideyhoFinderPause.reset();
			updateGateEntry(client);
            if(DevMode.enabled()) RouteRecorder.tick();
            RouteDataUpdates.notice();
            if (pendingHotspot != null && isInCritterSafari()) {
                Biome selected=pendingHotspot;pendingHotspot=null;
                if(System.currentTimeMillis()<pendingHotspotExpires && !RouteController.active()) RouteController.startFromHotspot(selected);
            }
			if (settingsScreenRequested) {
				settingsScreenRequested = false;
				client.setScreenAndShow(new ChipsSettingsScreen(null));
			}
			if (pendingCommandScreen != null) { var factory = pendingCommandScreen; pendingCommandScreen = null; client.setScreenAndShow(factory.get()); }
			while (recordStopKey.consumeClick()) RouteController.recordCurrentPosition();
			while (warpKey.consumeClick()) {
				if (client.getConnection() != null) {
					SafariLoadingTips.onCommand("warp safari");
					client.getConnection().sendCommand("warp safari");
				}
			}
			while (dismissReminderKey.consumeClick()) WarpSafariReminder.dismiss();
			RouteController.tick(client);
            SafariLoadingTips.tick();
			ForestFeederTracker.tick(client);
			WaypointRelay.tick();
			RouteRenderer.tick(client);
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			clearSafariFeatures();
			LOBBY_INFO.resetForWorldChange();
			currentGateArea = null;
			currentBiomeArea = null;
			wasInCritterSafari = false;
			observedLevel = client.level;
			requestLobbyInfo();
			var server = client.getCurrentServer();
			if (server != null && server.ip != null && server.ip.toLowerCase(java.util.Locale.ROOT).contains("hypixel.net")) {
				BazaarPriceService.refreshIfDue();
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            SafariLoadingTips.onDisconnect();
			clearSafariFeatures();
			LOBBY_INFO.resetForDisconnect();
			currentGateArea = null;
			currentBiomeArea = null;
			wasInCritterSafari = false;
			observedLevel = null;
			HideyhoFinderPause.reset();
			RouteController.reset();
		});
		RouteHud.register();
		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
			RouteRenderer.invalidateGraphEdgeCache();
			RouteController.onRouteChunksChanged();
		});
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> {
			RouteRenderer.invalidateGraphEdgeCache();
			RouteController.onRouteChunksChanged();
		});
		WarpSafariReminder.register();
		RouteRenderer.register();
		ShardTooltip.register();
		HuntingBoxHighlights.register();
		SafariLoadingTips.register();
		LOGGER.info("ChipsCritters initialized. Fixed-coordinate route assistance only.");
	}

	public static boolean isOnHypixel(Minecraft client) {
		var server = client.getCurrentServer();
		return server != null && server.ip != null
				&& server.ip.toLowerCase(java.util.Locale.ROOT).contains("hypixel.net");
	}

	public static boolean isInCritterSafari() {
		return isInCritterSafari(Minecraft.getInstance());
	}

	private static boolean isInCritterSafari(Minecraft client) {
		return client.player != null && client.level != null && isOnHypixel(client) && LOBBY_INFO.inCritterSafari();
	}

	public static boolean knownOutsideSafari(){return LOBBY_INFO.confirmed() && !LOBBY_INFO.inCritterSafari();}
	private static void updateGateEntry(Minecraft client) {
		if (client.player == null || !isInCritterSafari(client)) {
			currentGateArea = null;
			currentBiomeArea = null;
			return;
		}
		if(HideyhoSearch.active()){currentGateArea=Biome.nearestGateWithin(client.player.position(),BIOME_GATE_ENTRY_RADIUS);currentBiomeArea=SafariData.nearestBiomeArea(client.player.position(),12);return;}
        Biome entered = Biome.nearestGateWithin(client.player.position(), BIOME_GATE_ENTRY_RADIUS);
		if (entered != currentGateArea) {
			currentGateArea = entered;
			if (entered != null) {
				ClaimsManager.enterBiome(entered);
				if (!RouteController.active() || entered != RouteController.biome()) RouteController.startFromGate(entered);
			}
		}
		Biome area = SafariData.nearestBiomeArea(client.player.position(), 12.0);
		if (area != currentBiomeArea) {
			currentBiomeArea = area;
			if (area != null) {
				ClaimsManager.enterBiome(area);
				if (!RouteController.active() || area != RouteController.biome()) RouteController.startFromArea(area);
			}
		}
	}

	private static void registerKeys() {
		recordStopKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.chipscritters.record_stop", InputConstants.Type.KEYSYM, InputConstants.KEY_SEMICOLON, CATEGORY));
		warpKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.chipscritters.warp_safari", InputConstants.Type.MOUSE, InputConstants.MOUSE_BUTTON_4, CATEGORY));
		dismissReminderKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.chipscritters.dismiss_warp_reminder", InputConstants.Type.KEYSYM, InputConstants.KEY_K, CATEGORY));
	}

	private static void registerChatAndCommands() {
        var observe=Identifier.fromNamespaceAndPath(MOD_ID,"locraw_observe");
        var hide=Identifier.fromNamespaceAndPath(MOD_ID,"locraw_hide_late");
        ClientReceiveMessageEvents.ALLOW_GAME.addPhaseOrdering(observe,net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
        ClientReceiveMessageEvents.ALLOW_GAME.addPhaseOrdering(net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE,hide);
        ClientReceiveMessageEvents.ALLOW_GAME.register(observe,(message,overlay)->{
            if(!overlay&&isOnHypixel(Minecraft.getInstance())){var accepted=readLobbyInfo(message.getString());if(accepted!=null)LOCRAW_VISIBILITY.put(message,accepted.hideReply());}
            return true; // Observe only: other mods still receive the original JSON.
        });
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            SafariLoadingTips.onGameMessage(message.getString());
            return LOCRAW_VISIBILITY.containsKey(message)||!ClaimsManager.handleSystemMessage(message.getString());
        });
        ClientReceiveMessageEvents.ALLOW_GAME.register(hide,(message,overlay)->!Boolean.TRUE.equals(LOCRAW_VISIBILITY.remove(message)));
        ClientReceiveMessageEvents.GAME_CANCELED.register((message,overlay)->LOCRAW_VISIBILITY.remove(message));
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			String text = message.getString();
			if (isOnHypixel(Minecraft.getInstance())) {
                checkNewRunMessage(text);
                var queuedHotspot=HOTSPOT.matcher(text);
                if(queuedHotspot.find()) {
                    pendingHotspot=Biome.parse(queuedHotspot.group(1));pendingHotspotExpires=System.currentTimeMillis()+30000L;
                    LOGGER.info("Hotspot received: biome={}, safariConfirmed={}, player={}; retaining until confirmed",pendingHotspot,isInCritterSafari(),Minecraft.getInstance().player==null?"none":Minecraft.getInstance().player.position());
                }
            }
			if (!isInCritterSafari()) return;
			RouteController.doomGameMessage(text);
            checkWumpaMessage(text, true);
			HideyhoFinderPause.onGameMessage(text);
			if (!overlay && isInCritterSafari()) RouteController.hauntedCapture(text);
			checkSnoozleCapture(text);
			checkTroodonCapture(text);
			checkWumpaMessage(text, false);
			Matcher matcher = HOTSPOT.matcher(text);
			if (matcher.find()) {
				if (runFinished && System.currentTimeMillis() - lastNewRunMessageMillis >= 15000L) resetSafariRun("hotspot after finish");
				Biome hotspotBiome = Biome.parse(matcher.group(1));
				if (hotspotBiome != null) {
					Minecraft client = Minecraft.getInstance();
					Biome gateBiome = client.player == null ? null
							: Biome.nearestGateWithin(client.player.position(), BIOME_GATE_ENTRY_RADIUS);
					if (gateBiome != null) {
						if (!RouteController.active() || RouteController.biome() != gateBiome) RouteController.startFromGate(gateBiome);
						// The gate route already claimed this biome; a hotspot announcement must
						// not re-run claimed-hotspot redirection while the player is at its gate.
					} else if (!RouteController.active()) {
						RouteController.startFromHotspot(hotspotBiome);
					} else if (RouteController.biome() == hotspotBiome) {
						RouteController.claimHotspot(hotspotBiome);
					}
					// Do not let a delayed server hotspot announcement replace a route already
					// selected by entering a biome gate. A nearby gate takes precedence over
					// the hotspot label in case the server's announcement arrives first.
				}
			}
			ForestFeederTracker.onGameMessage(text);
			ClaimsManager.receive(message, null);
		});
		ClientSendMessageEvents.COMMAND.register(command -> {
			HideyhoFinderPause.onCommand(command);
			SafariLoadingTips.onCommand(command);
			String normalized = command == null ? "" : command.trim().replaceFirst("^/", "");
            // SEND_COMMAND also sees other mods. Only our explicit /sr locraw command opts into visible JSON.
		});
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, boundChatType, timestamp) -> {
			if (!isInCritterSafari()) return;
			String text = message.getString();
			HideyhoFinderPause.onGameMessage(text);
			checkSnoozleCapture(text);
			checkTroodonCapture(text);
			checkWumpaMessage(text, false);
			ForestFeederTracker.onGameMessage(text);
			ClaimsManager.receive(message, sender);
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
			registerRouteCommands(dispatcher, "chipscritters");
			registerRouteCommands(dispatcher, "sr");
		});
	}

	private static void checkSnoozleCapture(String message) {
		if (IcyCaptureRules.isLocalCaptureMessage(message, "Snoozle")) {
			RouteController.snoozleCaptured();
		}
	}

	private static void checkNewRunMessage(String rawMessage) {
		if (!RunCaptureState.isNewRunStartMessage(rawMessage)) return;
		long now = System.currentTimeMillis();
		if (now - lastNewRunMessageMillis < 15000L) return;
		resetSafariRun("game message: " + rawMessage);
	}

	public static void markRunFinished() { runFinished = ClaimsManager.doneBiomeCount() == Biome.values().length; }

	private static void resetSafariRun(String reason) {
		lastNewRunMessageMillis = System.currentTimeMillis();
		runFinished = false;
		RouteController.onNewSafariRunStarted();
		RouteDataUpdates.beginRun();
		WaypointRunLedger.newRunStarted();
		WaypointRelay.newRunStarted();
		ClaimsManager.onNewRunStarted();
		ForestFeederTracker.newRunStarted();
		HideyhoFinderPause.reset();
		WarpSafariReminder.dismiss();
		currentGateArea = null;
		currentBiomeArea = null;
		LOGGER.info("ChipsCritters new Safari run reset: {}", reason);
	}

	private static void checkTroodonCapture(String message) {
		if (IcyCaptureRules.shouldClearTroodons(RouteController.active() && RouteController.biome() == Biome.ICY,
				ChipsConfig.get().mode == ChipsConfig.Mode.UNIQUE, message)) {
			RouteController.troodonCaptured();
		}
	}

	private static void checkWumpaMessage(String rawMessage, boolean allowSpawn) {
		if (rawMessage == null) return;
		String message = rawMessage.replaceAll("§.", "").trim();
		if (allowSpawn && WUMPA_SPAWN_MESSAGE.equals(message)) RouteController.wumpaSpawned();
		if (IcyCaptureRules.shouldFinishWumpa(RouteController.active() && RouteController.biome() == Biome.ICY,
				RouteController.remainingStopCount(), message)) {
			RouteController.wumpaCaptured();
		}
	}

	public static void requestLobbyInfo() {
		Minecraft client = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		if (!LOBBY_INFO.requestDue(now, isOnHypixel(client), client.getConnection() != null)) return;
		LOBBY_INFO.automaticRequestSent(now);
		sendingAutomaticLocraw = true;
		try {
			client.getConnection().sendCommand("locraw");
		} finally {
			sendingAutomaticLocraw = false;
		}
	}

	private static LobbyInfoSession.Accepted readLobbyInfo(String text) {
		LobbyInfoSession.Accepted accepted = LOBBY_INFO.accept(text);
		if (accepted == null) return null;
		LobbyInfoRules.Info info = accepted.info();
		if (LobbyInfoRules.isCritterSafari(info) && isOnHypixel(Minecraft.getInstance())) {
			if (!info.server().equals(lastSafariServer)) {
				if (System.currentTimeMillis() - lastNewRunMessageMillis >= 15000L)
					resetSafariRun(lastSafariServer.isEmpty() ? "confirmed Safari entry" : "Safari server changed");
				lastSafariServer = info.server();
			}
			WaypointRelay.setLobbyInfo(info.server(), info.gametype(), info.mode(), info.map());
		} else {
			clearSafariFeatures();
		}
		return accepted;
	}

	private static void clearSafariFeatures() {
		WaypointRelay.stop();
		WaypointRelay.clearLobbyInfo();
		RouteController.reset();
		ForestFeederTracker.reset();
		HideyhoFinderPause.reset();
		WarpSafariReminder.dismiss();
		currentGateArea = null;
		currentBiomeArea = null;
		wasInCritterSafari = false;
	}

	static void registerRouteCommands(CommandDispatcher<FabricClientCommandSource> dispatcher, String command) {
		var root = ClientCommands.literal(command)
				.executes(context -> {
					settingsScreenRequested = true;
					return 1;
				})
				.then(ClientCommands.literal("newrun").executes(context -> { resetSafariRun("manual /sr newrun"); sendMessage("New Safari run state cleared; personal bests are retained."); return 1; }))
				.then(ClientCommands.literal("restart").executes(context -> { RouteController.restart(); return 1; }))
				.then(ClientCommands.literal("stop").executes(context -> { RouteController.stop(); sendMessage("Route stopped."); return 1; }))
				.then(ClientCommands.literal("pause").executes(context -> { RouteController.pause(); return 1; }))
				.then(ClientCommands.literal("resume").executes(context -> { RouteController.resume(); return 1; }))
				.then(ClientCommands.literal("find")
                    .then(ClientCommands.literal("hideyho").executes(context->{HideyhoSearch.start();return 1;}))
                    .then(ClientCommands.literal("stop").executes(context->{HideyhoSearch.stop(false,true);return 1;})))
                .then(ClientCommands.literal("skip").executes(context -> { RouteController.skipCurrentStop(); return 1; })
                    .then(ClientCommands.argument("number",com.mojang.brigadier.arguments.IntegerArgumentType.integer()).executes(context->{RouteController.skipToNumber(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context,"number"));return 1;})))
				.then(ClientCommands.literal("undo").executes(context -> { RouteController.undoSkippedStop(); return 1; }))
				.then(ClientCommands.literal("back").executes(context -> { RouteController.previousStop(); return 1; }))
				.then(ClientCommands.literal("refresh").executes(context -> { RouteController.recalculateRoute(); return 1; })
                    .then(ClientCommands.literal("claims").executes(context->{requestClaimsRestore(false);return 1;})
                        .then(ClientCommands.literal("all").executes(context->{requestClaimsRestore(true);return 1;}))))
				.then(ClientCommands.literal("rejoin").executes(context -> { RouteController.rejoinNearestRemainingStop(); return 1; }))
				.then(ClientCommands.literal("status").executes(context -> { RouteController.printStatus(); return 1; }))
				.then(ClientCommands.literal("history").executes(context -> { RunHistory.printRecent(); return 1; }))
				.then(ClientCommands.literal("stats").executes(context -> { RunHistory.printStats(); return 1; }))
				.then(ClientCommands.literal("help").executes(context -> { printHelp(); return 1; }))
				.then(ClientCommands.literal("report").executes(context -> { ChipsBugReport.printSummary(); return 1; }))
				.then(ClientCommands.literal("waypoints").executes(context -> { pendingCommandScreen = () -> new LearnedStopsScreen(null); return 1; }))
				.then(ClientCommands.literal("export").executes(context -> {
					try { sendMessage("Exported learned waypoints to " + ChipsConfig.get().exportLearnedStops()); }
					catch (java.io.IOException exception) { sendMessage("Waypoint export failed: " + exception.getMessage()); }
					return 1;
				}))
				.then(ClientCommands.literal("import").executes(context -> {
					try { sendMessage("Imported " + ChipsConfig.get().importLearnedStops() + " learned waypoints (matching coordinates were merged). Restart the route to use them."); }
					catch (java.io.IOException exception) { sendMessage("Waypoint import failed: " + exception.getMessage()); }
					return 1;
				}))
				.then(ClientCommands.literal("defaults").executes(context -> { pendingCommandScreen = () -> new ConfirmResetScreen(null); return 1; }))
				.then(ClientCommands.literal("controls").executes(context -> { pendingCommandScreen = () -> new net.minecraft.client.gui.screens.options.controls.ControlsScreen(null, Minecraft.getInstance().options); return 1; }))
                .then(ClientCommands.literal("locraw").executes(context -> {
                    var connection = Minecraft.getInstance().getConnection();
                    if (connection == null) { sendMessage("Not connected to a server."); return 0; }
                    LOBBY_INFO.manualRequestSent(System.currentTimeMillis());
                    connection.sendCommand("locraw");
                    sendMessage("Requested lobby information."); return 1;
                }))
                .then(ClientCommands.literal("troodon").executes(context -> { RouteController.recordCurrentPosition("Troodon"); return 1; }))
                .then(ClientCommands.literal("mark").executes(context -> { RouteController.recordCurrentPosition(); return 1; })
						.then(ClientCommands.literal("troodon").executes(context -> { RouteController.recordCurrentPosition("Troodon"); return 1; })));
        if(DevMode.enabled()) root.then(RouteRecorder.command());
        dispatcher.register(root);
	}

	public static Biome claimsRestoreBiome(){var c=Minecraft.getInstance();if(c.player==null)return null;return RouteController.active()?RouteController.biome():SafariData.nearestBiomeArea(c.player.position(),12);}
    private static void requestClaimsRestore(boolean all){if(!isInCritterSafari()){sendMessage("Enter Critter Safari before restoring claims.");return;}Biome b=claimsRestoreBiome();pendingCommandScreen=()->new ConfirmClaimsScreen(null,b,all);}
	private static void printHelp() {
        sendMessage("/sr find hideyho searches 20 fixed Haunted spots; /sr find stop returns to your route. No waypoint/relay claims during search.");
        sendMessage("/sr refresh claims [all] opens confirmation to restore local waypoints; PBs are disabled when stops are restored.");
        if(DevMode.enabled()) sendMessage("Dev: /sr record next; start <biome> <from> <to>; stop, cancel, save, list, delete <id>, export <dataVersion>.");
		sendMessage("Commands: /sr opens settings; /sr controls, locraw, newrun, help; /sr pause, resume, skip [number], undo, back, refresh, rejoin, restart, stop, status. /chipscritters also works.");
		sendMessage("Data tools: /sr waypoints, export, import, defaults, history, stats, report, mark [troodon], troodon.");
		sendMessage("Modes: Money, Unique, Full Clear (solo). Fastest is retired; its old bests remain archived. Full Clear visits every configured stop.");
		sendMessage("Gate entry announces [CC] CLAIM <BIOME>. A claimed biome stays on its own route; enter another biome gate to switch.");
		sendMessage("Route line and waypoint markers have separate display toggles. Waypoint relay uses HTTPS and is separate from chat.");
	}

	public static void printHelpFromMenu() { printHelp(); }

	private static void sendMessage(String message) {
		if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.sendSystemMessage(Component.literal("[ChipsCritters] " + message));
	}

	public static String dismissReminderBinding() {
		return dismissReminderKey == null ? "K" : dismissReminderKey.getTranslatedKeyMessage().getString();
	}

}

package dev.chipsxo.chipscritters;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

public final class RouteHud {
	public static final int PANEL_WIDTH = 268;
	public static final int PANEL_HEIGHT = 78;
	private static final int EDGE_MARGIN = 8;
	private static final int PANEL_BACKGROUND = 0xC017232E;
	private static final int TITLE_COLOR = 0xFF7DE8C3;
	private static final int BODY_COLOR = 0xFFFFFFFF;
	private static final int MUTED_COLOR = 0xFFE8F0F5;
	private static final int SECONDARY_COLOR = 0xFFA6B7C4;
	private static final int WARNING_COLOR = 0xFFFF5555;
	private static final int PREVIEW_INSTRUCTION_COLOR = 0xFF50E6D2;
	private static final int PREVIEW_WARNING_COLOR = WARNING_COLOR;
	private record WaypointText(String counter, String instruction, String warning, int color, double arrow) { }
	public record WaypointLayout(String counter, String instruction, String warning, int color, double arrow, int width, int height, double scale) { }

	private RouteHud() { }

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(ChipsCrittersClient.MOD_ID, "route_hud"), RouteHud::render);
	}

	private static void render(GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
        if(client.gui.screen() instanceof ChipsHudPositionScreen)return;
		if (client.player == null || client.level == null || !ChipsCrittersClient.isInCritterSafari()
				|| (!RouteController.active() && !RouteController.DOOM_COUNTDOWN.active() && RouteController.assistBiome() == null && !ForestFeederTracker.finalCatchPending() && !(RouteController.biome() == Biome.FOREST && ForestFeederTracker.allBirdsFinished()))) return;
		ChipsConfig config = ChipsConfig.get();
		int screenWidth = client.getWindow().getGuiScaledWidth();
		int screenHeight = client.getWindow().getGuiScaledHeight();
		int x = positionX(config, screenWidth);
		int y = positionY(config, screenWidth, screenHeight);
		double mainScale = mainScale(screenWidth, screenHeight);
		if (!HideyhoFinderPause.active()||HideyhoSearch.active()) renderMain(graphics, client, x, y, mainScale, routeText(client));
		if (config.showWaypointPanel && (RouteController.active()||RouteController.DOOM_COUNTDOWN.active())) {
			WaypointText waypoint = waypointText(client, deltaTracker.getGameTimeDeltaPartialTick(false));
			WaypointLayout layout = layoutWaypoint(client, waypoint, screenWidth, screenHeight, config.waypointHudScale);
			int waypointX = waypointPositionX(config, screenWidth, layout);
			int waypointY = waypointPositionY(config, screenWidth, screenHeight, layout);
			renderWaypointPanel(graphics, client, waypointX, waypointY, layout);
		}
	}

	public static void renderPreview(GuiGraphicsExtractor graphics, Minecraft client, int x, int y) {
		renderMain(graphics, client, x, y, mainScale(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight()),
				"Forest  •  Stop 12/35  •  10m");
	}

	public static void renderWaypointPreview(GuiGraphicsExtractor graphics, Minecraft client, int x, int y) {
		renderWaypointPanel(graphics, client, x, y, waypointPreviewLayout(client));
	}

	static TipPlacement.Rect tipObstacle(Minecraft c){
        if(!ChipsConfig.get().showWaypointPanel||!RouteController.active())return null;
        var layout=layoutWaypoint(c,waypointText(c,0),c.getWindow().getGuiScaledWidth(),c.getWindow().getGuiScaledHeight(),ChipsConfig.get().waypointHudScale);
        return new TipPlacement.Rect(waypointPositionX(ChipsConfig.get(),c.getWindow().getGuiScaledWidth(),layout),waypointPositionY(ChipsConfig.get(),c.getWindow().getGuiScaledWidth(),c.getWindow().getGuiScaledHeight(),layout),(int)Math.ceil(layout.width()*layout.scale()),(int)Math.ceil(layout.height()*layout.scale()));
    }
    public static WaypointLayout waypointPreviewLayout(Minecraft client) {
		WaypointText preview = new WaypointText("17/46 · ΔY +5", "CLIMB LADDER", "LINE GAP — FOLLOW CUBE", PREVIEW_INSTRUCTION_COLOR, Math.PI / 4);
		return layoutWaypoint(client, preview, client.getWindow().getGuiScaledWidth(),
				client.getWindow().getGuiScaledHeight(), ChipsConfig.get().waypointHudScale);
	}

	public static int waypointPositionX(ChipsConfig config, int screenWidth, int screenHeight) {
		WaypointText preview = new WaypointText("17/46 · ΔY +5", "CLIMB LADDER", "LINE GAP — FOLLOW CUBE", PREVIEW_INSTRUCTION_COLOR, Math.PI / 4);
		return waypointPositionX(config, screenWidth,
				layoutWaypoint(Minecraft.getInstance(), preview, screenWidth, screenHeight, config.waypointHudScale));
	}

	public static int waypointPositionY(ChipsConfig config, int screenWidth, int screenHeight) {
		WaypointText preview = new WaypointText("17/46 · ΔY +5", "CLIMB LADDER", "LINE GAP — FOLLOW CUBE", PREVIEW_INSTRUCTION_COLOR, Math.PI / 4);
		return waypointPositionY(config, screenWidth, screenHeight,
				layoutWaypoint(Minecraft.getInstance(), preview, screenWidth, screenHeight, config.waypointHudScale));
	}

	public static int positionX(ChipsConfig config, int screenWidth) {
		int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		int width = renderedMainWidth(screenWidth, screenHeight);
		int maxX = Math.max(0, screenWidth - width);
		int defaultX = Math.min(EDGE_MARGIN, maxX);
		return config.hudX < 0 ? defaultX : Math.max(0, Math.min(config.hudX, maxX));
	}

	public static int positionY(ChipsConfig config, int screenWidth, int screenHeight) {
		int height = renderedMainHeight(screenWidth, screenHeight);
		int maxY = Math.max(0, screenHeight - height);
		return config.hudY < 0 ? Math.min(EDGE_MARGIN, maxY) : Math.max(0, Math.min(config.hudY, maxY));
	}

	public static int renderedMainWidth(int screenWidth, int screenHeight) {
		return (int) Math.ceil(PANEL_WIDTH * mainScale(screenWidth, screenHeight));
	}

	public static int renderedMainHeight(int screenWidth, int screenHeight) {
		return (int) Math.ceil(PANEL_HEIGHT * mainScale(screenWidth, screenHeight));
	}

	private static double mainScale(int screenWidth, int screenHeight) {
		return Math.max(0.25, Math.min(1.0, Math.min((screenWidth - EDGE_MARGIN * 2.0) / PANEL_WIDTH,
				(screenHeight - EDGE_MARGIN * 2.0) / (PANEL_HEIGHT + 6.0 + 32.0))));
	}

	private static String routeText(Minecraft client) {
		if (!RouteController.active() && RouteController.biome() == Biome.FOREST && ForestFeederTracker.allBirdsFinished()) return "Forest clear - All birds caught";
		if (ForestFeederTracker.finalCatchPending()) return "Forest done · " + ForestFeederTracker.missingBirdsText();
		if (RouteController.buildingRoute() && RouteController.biome() != null) {
			return RouteController.biome().displayName() + "  •  Building route…";
		}
		if (RouteController.waitingForForestBird()) {
			return ForestFeederTracker.feedExhausted()
					? "Forest  •  Birdfeeder empty — catch final bird"
					: "Forest  •  Birdfeeder — use remaining feed, then catch final bird";
		}
		if (RouteController.waitingForCavernWaterfall()) {
			int step = SafariData.cavernWaterfallStepIndex(client.player.getY()) + 1;
			return "Cavern  •  Waterfall route  " + step + "/" + SafariData.cavernWaterfallSteps().size() + "  •  Exit Y=82";
		}
		if (RouteController.paused()) return "Route paused  •  /sr resume";
		if (RouteController.finishingSkip()) return "Final stop skipped  •  /sr undo to restore";
		if (RouteController.active() && RouteController.biome() != null && RouteController.nextStop() < RouteController.stops().size()) {
			int shown = Math.min(RouteController.nextStop() + 1, RouteController.stops().size());
			double distance = RouteController.distanceToNext(client.player.position());
			Vec3 target = RouteController.displayTarget();
			
			return RouteController.biome().displayName() + "  •  Stop " + RouteController.stopCounterText()
					+ (Double.isFinite(distance)?String.format(java.util.Locale.ROOT, "  •  %.0fm",distance):"  •  Waiting");
		}
		if (RouteController.assistBiome() != null) {
			return "Assist " + ClaimsManager.ownerOf(RouteController.assistBiome()) + " at " + RouteController.assistBiome().displayName() + " gate";
		}
		return "Safari route active";
	}

	private static WaypointText waypointText(Minecraft client, float partial) {
		String instruction = "";
		if (RouteController.active() && RouteController.biome() != null) {
			if(RouteController.biome()==Biome.HAUNTED&&WaypointRunLedger.HAUNTED_UNIQUE.doomRoute&&!WaypointRunLedger.HAUNTED.doom&&(RouteController.displayTarget()==null))instruction="CATCH DOOMSPIRAL";
            else if(RouteController.waitingForHauntedUnlock())instruction="HOUSE CATCHES + 3 INCENSE TO CONTINUE";
            else if (RouteController.dropGuidance())instruction=RouteController.dropInstruction();
			else if (RouteController.ladderGuidance()) instruction = RouteController.climbInstruction();
			else if (RouteController.hillGuidance()) instruction = RouteController.hillInstruction();
			else if (RouteController.forestJumpGuidance()) instruction = "JUMP ON TREE";
			else if (RouteController.waitingForCavernWaterfall()) instruction = "FOLLOW WATERFALL UP";
			else if (RouteController.displayTarget() != null) {
				String hint = SafariData.waypointInstruction(RouteController.biome(), RouteController.displayTarget());
				if (hint != null && (ChipsConfig.get().showRouteHints
						|| RouteController.displayTarget().equals(SafariData.CAVERN_POND_STOP)
						|| RouteController.displayTarget().equals(SafariData.CAVERN_LADDER_CAVE))) instruction = hint;
			}
		}
		String warning = RouteController.routeLineGap() && !RouteController.waitingForRouteChunks() && !RouteController.hasSafeLineStart() ? "NO SAFE PATH - FOLLOW ARROW" : "";
		Vec3 target = RouteController.displayTarget();
		double arrow = Double.NaN;
		String vertical = "";
		if (HideyhoFinderPause.active()) instruction = "Hideyho Finder active";
		else if (RouteController.paused()) instruction = "Route paused";
		else if (RouteController.biome()==Biome.HAUNTED && target==null && !WaypointRunLedger.HAUNTED.doom) instruction="Catch Doomspiral";
		else if (RouteController.buildingRoute()) instruction = "Building route…";
		else if (RouteController.waitingForForestBird()) instruction = "Birdfeeder";
		else if (target != null) {
			if (instruction.isBlank()) instruction = SafariData.isFloorDropWaypoint(RouteController.biome(), target) ? "Floor drop" : "Waypoint";
			Vec3 recordedGuide=RecordedLegPaths.guide(client.player.getPosition(partial));
			Vec3 offset = (recordedGuide!=null?recordedGuide:SafariGraph.get().navigationTarget(RouteController.biome(),target).add(0.5, 0, 0.5)).subtract(client.player.getPosition(partial));
			vertical = "ΔY " + (Math.abs(offset.y) < .5 ? "0" : (offset.y > 0 ? "+" : "−") + Math.round(Math.abs(offset.y)));
			if (Math.abs(offset.y) > 1 && Math.abs(offset.y) > Math.hypot(offset.x, offset.z) * 1.35) {
				arrow = offset.y > 0 ? 0 : Math.PI;
			} else arrow = Math.atan2(-offset.x, offset.z) - Math.toRadians(client.player.getViewYRot(partial));
		} else instruction = RouteController.biome() == Biome.HAUNTED ? "Catch Doomspiral" : "Waiting for waypoint";
		if (RouteController.biome() == Biome.HAUNTED && target != null && target.equals(SafariData.GIMMIEGOLD)
                && Math.abs(client.player.getX() - 38.5) < 2 && Math.abs(client.player.getZ() + 15.5) < 2
                && client.player.getY() >= 49 && client.player.getY() < 63) instruction = "SWIM UP - CAVE EXIT";
        if(HideyhoSearch.active())instruction="HIDEYHO SEARCH · "+HideyhoSearch.stops().size()+" LEFT";
        else if(RouteController.DOOM_COUNTDOWN.active())instruction=RouteController.DOOM_COUNTDOWN.text(System.currentTimeMillis());
        String counter = RouteController.stopCounterText();
		String header = counter.isBlank() ? vertical : vertical.isBlank() ? counter : counter + " · " + vertical;
		if (instruction.isBlank()) instruction = "Waypoint";
		return new WaypointText(header, instruction, warning, waypointColor(instruction), arrow);
	}

	private static int waypointColor(String instruction) {
		if (instruction.contains("CAVE BY LADDER")) return 0xFFFF654D;
		if (instruction.contains("POND")) return 0xFF39D9FF;
		if (instruction.contains("VINES") || instruction.contains("HILL")) return 0xFFA4EF67;
		if (instruction.contains("LADDER") || instruction.contains("DIVE")) return 0xFF54E6D7;
		if (instruction.contains("TREE")) return 0xFF8CFF69;
		if (instruction.contains("HIDEYHO")) return 0xFFD889FF;
		if (instruction.contains("HIDEONWALL") || instruction.contains("PAINTING")) return 0xFFFFB4E5;
		if (instruction.contains("GAZER")) return 0xFFFF88C8;
		if (instruction.contains("GEMZIE") || instruction.contains("GEM")) return 0xFFFFD95A;
		if (instruction.contains("SNOOZLE") || instruction.contains("TROODON") || instruction.contains("WUMPA")) return 0xFFFFB347;
		if (instruction.contains("WATERFALL")) return 0xFF57E5FF;
		if (instruction.contains("DROP DOWN")) return 0xFFFF744D;
		return BODY_COLOR;
	}

	private static void renderMain(GuiGraphicsExtractor graphics, Minecraft client, int x, int y, double scale, String current) {
		ChipsConfig config = ChipsConfig.get();
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale((float) scale, (float) scale);
		UiTheme.card(graphics,0,0,PANEL_WIDTH,PANEL_HEIGHT,PANEL_BACKGROUND);
        graphics.fill(0,4,2,PANEL_HEIGHT-4,UiTheme.ACCENT);
		graphics.text(client.font, "ChipsCritters", 6, 5, TITLE_COLOR, true);
		graphics.text(client.font, fitText(client, current, PANEL_WIDTH - 12), 6, 19, BODY_COLOR, true);
		if (config.showRouteProgress && RouteController.active() && !RouteController.stops().isEmpty()) {
			int left = 6;
			int right = PANEL_WIDTH - 6;
			graphics.fill(left, 33, right, 36, UiTheme.BORDER);
			double ratio = Math.min(1.0, RouteController.completedStopCount() / (double) Math.max(1,RouteController.displayStopCount()));
			graphics.fill(left, 33, left + (int) ((right - left) * ratio), 36, TITLE_COLOR);
		}
		if (config.showPartyStatus) graphics.text(client.font, fitText(client, ClaimsManager.partySummaryText(), PANEL_WIDTH - 12), 6, 42, MUTED_COLOR, true);
		String lower = ForestFeederTracker.finalCatchPending()
				? "Final-feed bird may still be catchable"
				: config.showHudTimer && RouteController.active()
				? "Route time " + formatTimer(RouteController.elapsedMillis())
				: config.companionMode() ? "Companion mode active" : RunHistory.summary();
		graphics.text(client.font, fitText(client, lower, PANEL_WIDTH - 12), 6, 56, SECONDARY_COLOR, true);
		if (!config.showPartyStatus && config.companionMode()) graphics.text(client.font, "Companion mode active", 6, 67, SECONDARY_COLOR, true);
		graphics.pose().popMatrix();
	}

	private static WaypointLayout layoutWaypoint(Minecraft client, WaypointText text, int screenWidth, int screenHeight, double requestedScale) {
		String counter = text.counter();
		String instruction = text.instruction();
		String warning = text.warning();
		int maxTextWidth = Math.max(24, screenWidth - EDGE_MARGIN * 2 - 28);
		counter = fitText(client, counter, maxTextWidth - 14);
		instruction = fitText(client, instruction, maxTextWidth);
		warning = fitText(client, warning, maxTextWidth);
		int contentWidth = Math.max(client.font.width(counter) + 30,
				Math.max(client.font.width(instruction), client.font.width(warning)));
		int width = Math.max(0, contentWidth) + 12;
		boolean hasCounter = !counter.isBlank();
		boolean hasInstruction = !instruction.isBlank();
		boolean hasWarning = !warning.isBlank();
		int height = 8 + (hasCounter ? 12 : 0) + (hasInstruction ? 12 : 0) + (hasWarning ? 12 : 0);
		if (width <= 12 || height <= 0) return new WaypointLayout("", "", "", text.color(), text.arrow(), 0, 0, 1.0);
		double scale = WaypointPlacement.scale(screenWidth, screenHeight, renderedMainHeight(screenWidth, screenHeight),
				width, height, requestedScale);
		return new WaypointLayout(counter, instruction, warning, text.color(), text.arrow(), width, height, scale);
	}

	private static int waypointPositionX(ChipsConfig config, int screenWidth, WaypointLayout layout) {
		int renderedWidth = (int) Math.ceil(layout.width() * layout.scale());
		return WaypointPlacement.x(screenWidth, renderedWidth, config.waypointHudX);
	}

	private static int waypointPositionY(ChipsConfig config, int screenWidth, int screenHeight, WaypointLayout layout) {
		int renderedHeight = (int) Math.ceil(layout.height() * layout.scale());
		int mainY = positionY(config, screenWidth, screenHeight);
		int mainX = positionX(config, screenWidth);
		int wpX = waypointPositionX(config, screenWidth, layout);
		var world = Minecraft.getInstance().level;
		boolean sidebar = world != null && world.getScoreboard().getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR) != null;
		return WaypointPlacement.y(screenWidth, screenHeight, sidebar, EDGE_MARGIN, mainX, mainY, renderedMainWidth(screenWidth, screenHeight),
				renderedMainHeight(screenWidth, screenHeight), wpX, (int) Math.ceil(layout.width() * layout.scale()),
				renderedHeight, config.waypointHudY);
	}

	private static void renderWaypointPanel(GuiGraphicsExtractor graphics, Minecraft client, int x, int y, WaypointLayout layout) {
		if (layout.width() <= 0 || layout.height() <= 0) return;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale((float) layout.scale(), (float) layout.scale());
		UiTheme.card(graphics,0,0,layout.width(),layout.height(),PANEL_BACKGROUND);
        graphics.fill(4,0,layout.width()-4,1,UiTheme.ACCENT);
		if (Double.isFinite(layout.arrow())) {
			graphics.pose().pushMatrix();
			graphics.pose().translate((layout.width()-client.font.width(layout.counter()))/2f-10f, 9f);
			graphics.pose().rotate((float) layout.arrow());
			graphics.fill(-1, -4, 1, 5, layout.color());
			for (int row = 0; row < 4; row++) graphics.fill(-row - 1, row - 5, row + 1, row - 4, layout.color());
			graphics.pose().popMatrix();
		}
		int row = 4;
		if (!layout.counter().isBlank()) {
			graphics.text(client.font, layout.counter(), WaypointPlacement.centerText(layout.width(),client.font.width(layout.counter())), row, SECONDARY_COLOR, true);
			row += 12;
		}
		if (!layout.instruction().isBlank()) {
			graphics.text(client.font, layout.instruction(), WaypointPlacement.centerText(layout.width(),client.font.width(layout.instruction())), row, layout.color(), true);
			row += 12;
		}
		if (!layout.warning().isBlank()) graphics.text(client.font, layout.warning(), WaypointPlacement.centerText(layout.width(),client.font.width(layout.warning())), row, WARNING_COLOR, true);
		graphics.pose().popMatrix();
	}

	private static String fitText(Minecraft client, String value, int maxWidth) {
		if (value == null || value.isBlank() || maxWidth <= 0) return "";
		if (client.font.width(value) <= maxWidth) return value;
		String ellipsis = "…";
		int end = value.length();
		while (end > 0 && client.font.width(value.substring(0, end) + ellipsis) > maxWidth) end--;
		return end <= 0 ? ellipsis : value.substring(0, end).stripTrailing() + ellipsis;
	}

	private static String formatTimer(long elapsedMillis) {
		long totalSeconds = elapsedMillis / 1000L;
		long totalMinutes = totalSeconds / 60L;
		return totalMinutes >= 60L
				? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", totalMinutes / 60L, totalMinutes % 60L, totalSeconds % 60L)
				: String.format(java.util.Locale.ROOT, "%02d:%02d", totalMinutes, totalSeconds % 60L);
	}
}

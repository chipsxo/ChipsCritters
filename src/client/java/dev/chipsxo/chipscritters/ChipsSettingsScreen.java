package dev.chipsxo.chipscritters;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.network.chat.Component;

/** Compact, paged settings UI for route, display, and local data controls. */
public final class ChipsSettingsScreen extends ChipsScreen {
	private final Screen parent;
	private final int page;
    private RouteModeCounts modeCounts;private long lastCountRefresh;
    private final java.util.List<CountRow> countRows=new java.util.ArrayList<>();
    private record CountRow(Button button,ChipsConfig.Mode mode,boolean first){}

	public ChipsSettingsScreen(Screen parent) { this(parent, 0); }

	private ChipsSettingsScreen(Screen parent, int page) {
		super(Component.literal(switch(page){case 0->"Routes";case 1->"Display & sound";case 2->"Tools";case 3->"Value & tips";case 4->"Route data";case 5->"Unique options";case 6->"Money options";case 7->"Full Clear options";default->"Route modes";}));
		this.parent = parent;
		this.page = page;
	}

	@Override
	protected void init() {
		int center = width / 2;
		int x = center - 120;
		int panelHeight = page == 1 ? 270 : page == 0 ? 232 : page == 2 ? 214 : 174;
		int y = Math.max(40, Math.min(height / 2 - 110, height - panelHeight - 10));
		ChipsConfig config = ChipsConfig.get();
        countRows.clear();if(page>=5)modeCounts=new RouteModeCounts(config);
		addRenderableWidget(UiButton.builder(Component.literal("Routes"), button -> openPage(0)).bounds(x, y - 35, 57, 18).build());
		addRenderableWidget(UiButton.builder(Component.literal("Display"), button -> openPage(1)).bounds(x + 61, y - 35, 57, 18).build());
		addRenderableWidget(UiButton.builder(Component.literal("Tools"), button -> openPage(2)).bounds(x + 122, y - 35, 57, 18).build());
		
        if(page==8)initModeList(config,x,y);
        else if(page==5||page==6||page==7)initModeOptions(config,x,y);
        else if (page == 0) initRoutes(config, x, y);
		else if (page == 1) initDisplay(config, x, y);
		else if (page == 2) initTools(x, y);
		else if(page==3) initMarket(config,x,y);
		else initRouteData(config,x,y);
	}

    private void addCounts(ChipsConfig.Mode mode,int x,int y){
        for(int row=0;row<2;row++){boolean first=row==0;var button=addRenderableWidget(UiButton.builder(Component.literal(modeCounts.row(mode,first)),b->{}).bounds(x,y+row*26,240,22).build());button.active=false;countRows.add(new CountRow(button,mode,first));}
    }
    private void initModeList(ChipsConfig config,int x,int y){
        int i=0;for(var mode:ChipsConfig.Mode.values()){
            int options=mode==ChipsConfig.Mode.MONEY?6:mode==ChipsConfig.Mode.UNIQUE?5:7, rowY=y+i++*86;
            addRenderableWidget(UiButton.builder(Component.literal(label(mode)+(mode==config.mode?" · ACTIVE":"")+" — options"),b->{config.switchMode(mode);openPage(options);}).bounds(x,rowY,240,22).build());
            addCounts(mode,x,rowY+26);
        }
        addRenderableWidget(UiButton.builder(Component.literal("Back"),b->openPage(0)).bounds(x,y+280,240,22).build());
    }
    private void initModeOptions(ChipsConfig config,int x,int y){
        addCounts(config.mode,x,y);y+=52;
        addToggle(x,y,240,"One waypoint at a time",config.oneWaypointAtATime,()->{config.oneWaypointAtATime=!config.oneWaypointAtATime;RouteController.refreshVisibleRouteLine();});
        addToggle(x,y+26,240,"Hide reached waypoints",config.hideReachedWaypoints,()->config.hideReachedWaypoints=!config.hideReachedWaypoints);
        addToggle(x,y+52,240,"Waypoint source labels",config.showWaypointSource,()->config.showWaypointSource=!config.showWaypointSource);
        addToggle(x,y+78,240,"Troodon labels",config.troodonLabels,()->config.troodonLabels=!config.troodonLabels);
        addToggle(x,y+104,240,"Waypoint distances",config.showDistanceLabels,()->config.showDistanceLabels=!config.showDistanceLabels);
        if(page==5)addToggle(x,y+130,240,"Hideonwall routes",config.uniqueHideonwallRoutes,()->{config.uniqueHideonwallRoutes=!config.uniqueHideonwallRoutes;RoutePlanService.clear();RouteController.uniqueOptionsChanged();});
        if(page==6)addSlider(x,y+130,240,config.detourBlocks/20,()->"Extra-stop detour: "+String.format(java.util.Locale.ROOT,"%.1f",config.detourBlocks)+" blocks",v->config.detourBlocks=Math.rint(v*40)/2);
        addRenderableWidget(UiButton.builder(Component.literal("Back"),b->openPage(8)).bounds(x,y+190,240,22).build());
    }
    @Override protected boolean tabSelected(String tab){return tab.equals(switch(page){case 0,5,6,7,8->"Routes";case 1->"Display";default->"Tools";});}
    private void initRoutes(ChipsConfig config, int x, int y) {
		addRenderableWidget(UiButton.builder(Component.literal("Route modes & stop counts →"),button->openPage(8)).bounds(x,y,240,22).build());
        addToggle(x, y + 19, 240, "Incoming [CC] tracking", config.partyClaims, () -> config.partyClaims = !config.partyClaims);
		addToggle(x, y + 38, 240, "PL /warp safari popup", config.partyLeaderWarpReminder, () -> {
			config.partyLeaderWarpReminder = !config.partyLeaderWarpReminder;
			if (config.partyLeaderWarpReminder) ClaimsManager.partyLeaderReminderEnabled();
		});
		addToggle(x, y + 57, 240, "Route line", config.routeRendering, () -> config.routeRendering = !config.routeRendering);
		addToggle(x, y + 76, 240, "Waypoint markers", config.waypointRendering, () -> config.waypointRendering = !config.waypointRendering);
		addToggle(x, y + 114, 240, "Near-waypoint ding", config.nearWaypointPing, () -> config.nearWaypointPing = !config.nearWaypointPing);
		addSlider(x, y + 133, 240, Math.max(0.0, Math.min(1.0, config.pingVolume)),
				() -> "Ding volume: " + Math.round(config.pingVolume * 100) + "%",
				value -> config.pingVolume = value);
		addSlider(x, y + 152, 240, (config.arrivalRadius - 1.0) / 7.0,
				() -> "Waypoint arrival radius: " + config.arrivalRadius + " blocks",
				value -> config.arrivalRadius = (int) Math.round(1.0 + value * 7.0));
		addRenderableWidget(UiButton.builder(Component.literal("Move HUD"), button -> this.minecraft.setScreenAndShow(new ChipsHudPositionScreen(this)))
				.bounds(x, y + 190, 118, 18).build());
		addRenderableWidget(UiButton.builder(Component.literal("Keybinds"), button -> this.minecraft.setScreenAndShow(new ControlsScreen(this, this.minecraft.options)))
				.bounds(x + 122, y + 190, 118, 18).build());
		addRenderableWidget(UiButton.builder(Component.literal("Done"), button -> onClose()).bounds(x, y + 214, 240, 18).build());
	}

	private void initDisplay(ChipsConfig config, int x, int y) {
		addToggle(x, y + 76, 240, "Show route hints", config.showRouteHints, () -> config.showRouteHints = !config.showRouteHints);
		addToggle(x, y + 95, 240, "Waypoint instruction panel", config.showWaypointPanel, () -> config.showWaypointPanel = !config.showWaypointPanel);
		addToggle(x, y + 114, 240, "HUD progress bar", config.showRouteProgress, () -> config.showRouteProgress = !config.showRouteProgress);
		addToggle(x, y + 133, 240, "HUD route timer", config.showHudTimer, () -> config.showHudTimer = !config.showHudTimer);
		addToggle(x, y + 152, 240, "HUD party status", config.showPartyStatus, () -> config.showPartyStatus = !config.showPartyStatus);
		addRenderableWidget(UiButton.builder(Component.literal("Ribbon color: " + colorName(config.routeColorPreset)), button -> {
			config.routeColorPreset = (config.routeColorPreset + 1) % 4;
			config.save();
			openPage(1);
		}).bounds(x, y + 171, 240, 18).build());
		addSlider(x, y + 190, 240, (config.ribbonOpacity - 0.15) / 0.85,
				() -> "Ribbon opacity: " + Math.round(config.ribbonOpacity * 100) + "%",
				value -> config.ribbonOpacity = 0.15 + value * 0.85);
		addSlider(x, y + 209, 240, (config.ribbonThickness - 0.05) / 0.25,
				() -> "Ribbon width: " + String.format(java.util.Locale.ROOT, "%.2f", config.ribbonThickness),
				value -> config.ribbonThickness = 0.05 + value * 0.25);
		addSlider(x, y + 228, 240, (config.warpReminderSeconds - 2.0) / 8.0,
				() -> "Warp popup: " + config.warpReminderSeconds + " seconds",
				value -> config.warpReminderSeconds = (int) Math.round(2.0 + value * 8.0));
		addRenderableWidget(UiButton.builder(Component.literal("Done"), button -> onClose()).bounds(x, y + 251, 240, 18).build());
	}

	private void initRouteData(ChipsConfig config,int x,int y) {
        addToggle(x,y,240,"Signed route updates",config.routeUpdatesEnabled,()->config.routeUpdatesEnabled=!config.routeUpdatesEnabled);
        addRenderableWidget(UiButton.builder(Component.literal("Check route data status"),b->RouteController.commandFeedback(RouteDataUpdates.status())).bounds(x,y+22,240,18).build());
        addRenderableWidget(UiButton.builder(Component.literal("Done"),b->openPage(2)).bounds(x,y+48,240,18).build());
    }
    private void initMarket(ChipsConfig config, int x, int y) {
		addSlider(x, y + 57, 240, config.bazaarFeePercent / 2.0, () -> "Bazaar fee: " + String.format(java.util.Locale.ROOT, "%.2f%%", config.bazaarFeePercent), value -> config.bazaarFeePercent = Math.round(value * 200.0) / 100.0);
		addToggle(x, y, 240, "Hunting Box slot colors", config.huntingBoxColors, () -> config.huntingBoxColors = !config.huntingBoxColors);
		addToggle(x, y + 19, 240, "Airship cutscene tips", config.safariLoadingTips, () -> config.safariLoadingTips = !config.safariLoadingTips);
		addSlider(x, y + 38, 240, config.minimumShardAdvantagePercent / 25.0, () -> "Minimum advantage: " + Math.round(config.minimumShardAdvantagePercent) + "%", value -> config.minimumShardAdvantagePercent = value * 25.0);
        addSlider(x,y+76,240,config.minimumShardAdvantageCoins/10000.0,()->"Minimum profit: "+String.format(java.util.Locale.US,"%,.0f",config.minimumShardAdvantageCoins)+" coins",v->config.minimumShardAdvantageCoins=Math.round(v*10000));
        addRenderableWidget(UiButton.builder(Component.literal("Reset minimum profit: 1,000 coins"),b->{config.minimumShardAdvantageCoins=ShardProfitMinimum.DEFAULT_COINS;config.save();openPage(3);}).bounds(x,y+95,240,18).build());
        addRenderableWidget(UiButton.builder(Component.literal("Done"), button -> openPage(2)).bounds(x,y+122,240,18).build());
	}

	private void initTools(int x, int y) {
		addRenderableWidget(UiButton.builder(Component.literal("Manage learned waypoints"), button ->
				this.minecraft.setScreenAndShow(new LearnedStopsScreen(this))).bounds(x, y, 240, 18).build());
		addRenderableWidget(UiButton.builder(Component.literal("Relay settings"), button ->
				this.minecraft.setScreenAndShow(new RelaySettingsScreen(this))).bounds(x, y + 19, 240, 18).build());
		addRenderableWidget(UiButton.builder(Component.literal("Export learned waypoints"), button -> {
			try { message("Exported to " + ChipsConfig.get().exportLearnedStops()); }
			catch (java.io.IOException exception) { message("Export failed: " + exception.getMessage()); }
		}).bounds(x, y + 38, 240, 18).build());
		addRenderableWidget(UiButton.builder(Component.literal("Import learned waypoints"), button -> {
			try { message("Imported " + ChipsConfig.get().importLearnedStops() + " new waypoints. Restart the route to use them."); }
			catch (java.io.IOException exception) { message("Import failed: " + exception.getMessage()); }
		}).bounds(x, y + 57, 240, 18).build());
		addRenderableWidget(UiButton.builder(Component.literal("Personal bests and history"), button ->
				this.minecraft.setScreenAndShow(new PersonalBestsScreen(this)))
				.bounds(x, y + 76, 240, 18).build());
		addRenderableWidget(UiButton.builder(Component.literal("Bug report summary"), button -> ChipsBugReport.printSummary())
				.bounds(x, y + 95, 240, 18).build());
		addRenderableWidget(UiButton.builder(Component.literal("Bag colors and airship tips"), button -> openPage(3))
				.bounds(x, y + 114, 240, 18).build());
		addRenderableWidget(UiButton.builder(Component.literal("Reset settings and learned stops…"), button ->
				this.minecraft.setScreenAndShow(new ConfirmResetScreen(this))).bounds(x, y + 133, 240, 18).build());
		addRenderableWidget(UiButton.builder(Component.literal("Done"), button -> onClose()).bounds(x, y + 194, 240, 18).build());
        addRenderableWidget(UiButton.builder(Component.literal("Restore reached waypoints…"),b->minecraft.setScreenAndShow(new ConfirmClaimsScreen(this,ChipsCrittersClient.claimsRestoreBiome(),false))).bounds(x,y+171,240,18).build());
        addRenderableWidget(UiButton.builder(Component.literal("Route data updates"), button -> openPage(4)).bounds(x,y+152,240,18).build());
	}

	private void addToggle(int x, int y, int width, String title, boolean value, Runnable change) {
		addRenderableWidget(UiButton.builder(Component.literal(title + ": " + onOff(value)), button -> {
			change.run();
			ChipsConfig.get().save();
			if (title.startsWith("Hide reached")) RouteController.refreshVisibleRouteLine();
			openPage(page);
		}).bounds(x, y, width, 18).build());
	}

	private void addSlider(int x, int y, int width, double initial, java.util.function.Supplier<String> label,
			java.util.function.DoubleConsumer apply) {
		addRenderableWidget(new UiSlider(x, y, width, 18, Component.literal(label.get()), Math.max(0.0, Math.min(1.0, initial))) {
			@Override protected void updateMessage() { setMessage(Component.literal(label.get())); }
			@Override protected void applyValue() { apply.accept(value); ChipsConfig.get().save(); updateMessage(); }
		});
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if(!countRows.isEmpty()&&!modeCounts.matches(ChipsConfig.get())&&System.currentTimeMillis()-lastCountRefresh>200){modeCounts=new RouteModeCounts(ChipsConfig.get());lastCountRefresh=System.currentTimeMillis();}
        for(var row:countRows)row.button().setMessage(Component.literal(modeCounts.row(row.mode(),row.first())));
        super.extractRenderState(graphics,mouseX,mouseY,delta);
        if(page==7)bodyNote(graphics,"Solo sweep: all configured route, learned and search stops. Saved display options above are specific to Full Clear.",212,UiTheme.MUTED);
        if(page==5)bodyNote(graphics,"With Hideonwall routes OFF, Hideonwall is also excluded from the house-catch requirement. Uncollected stops return when enabled again.",212,UiTheme.MUTED);
	}

	@Override
	public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(parent); }

	private void openPage(int nextPage) {
		this.minecraft.setScreenAndShow(new ChipsSettingsScreen(parent, nextPage));
	}

	private void message(String text) {
		if (minecraft != null && minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal("[ChipsCritters] " + text));
	}

	private static String colorName(int value) {
		return switch (Math.floorMod(value, 4)) { case 0 -> "Red"; case 1 -> "Cyan"; case 2 -> "Violet"; default -> "Green"; };
	}

	private static String label(ChipsConfig.Mode mode) {
		return switch (mode) {
			case MONEY -> "Money";
			case UNIQUE -> "Unique";
			case FULL_CLEAR -> "Full Clear (Solo)";
		};
	}

	private static String formatTimer(long elapsedMillis) {
		long totalSeconds = elapsedMillis / 1000L;
		long totalMinutes = totalSeconds / 60L;
		return totalMinutes >= 60L
				? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", totalMinutes / 60L, totalMinutes % 60L, totalSeconds % 60L)
				: String.format(java.util.Locale.ROOT, "%02d:%02d", totalMinutes, totalSeconds % 60L);
	}

	private static String onOff(boolean value) { return value ? "ON" : "OFF"; }
}

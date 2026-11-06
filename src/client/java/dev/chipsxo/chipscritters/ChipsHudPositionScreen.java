package dev.chipsxo.chipscritters;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class ChipsHudPositionScreen extends ChipsScreen {
	private final Screen parent;
	private int hudX;
	private int hudY;
	private int waypointX;
	private int waypointY;
	private int dragOffsetX;
	private int dragOffsetY;
	private boolean draggingMain;
	private boolean draggingWaypoint;
	private boolean waypointMoved;
    private int bannerY;private boolean draggingBanner,bannerMoved;

	public ChipsHudPositionScreen(Screen parent) {
		super(Component.literal("Move ChipsCritters HUD"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		ChipsConfig config = ChipsConfig.get();
        bannerY=SafariLoadingTips.previewY(Minecraft.getInstance(),width,height);draggingBanner=draggingMain=draggingWaypoint=false;bannerMoved=false;
		hudX = RouteHud.positionX(config, this.width);
		hudY = RouteHud.positionY(config, this.width, this.height);
		waypointX = RouteHud.waypointPositionX(config, this.width, this.height);
		waypointY = RouteHud.waypointPositionY(config, this.width, this.height);
		this.addRenderableWidget(UiButton.builder(Component.literal("Waypoint panel size: " + Math.round(config.waypointHudScale * 100) + "%"), button -> {
			config.waypointHudScale = config.waypointHudScale >= 1.5 ? 0.6 : Math.min(1.5, config.waypointHudScale + 0.1);
			config.save();
			this.minecraft.setScreenAndShow(new ChipsHudPositionScreen(parent));
		}).bounds(this.width / 2 - 100, this.height - 54, 200, 18).build());
		this.addRenderableWidget(UiButton.builder(Component.literal("Reset to center"), button -> {
			config.waypointHudX = config.waypointHudY = -1;
            config.airshipBannerPosition=-1;
			config.save();
			this.minecraft.setScreenAndShow(new ChipsHudPositionScreen(parent));
		}).bounds(this.width / 2 - 100, this.height - 76, 200, 18).build());
		this.addRenderableWidget(UiButton.builder(Component.literal("Snap: " + (config.snapHudToGrid ? "ON" : "OFF") + " (Shift bypass)"), button -> {
            config.snapHudToGrid = !config.snapHudToGrid;
            config.save();
            button.setMessage(Component.literal("Snap: " + (config.snapHudToGrid ? "ON" : "OFF") + " (Shift bypass)"));
        }).bounds(this.width / 2 - 100, this.height - 98, 200, 18).build());
        this.addRenderableWidget(UiButton.builder(Component.literal("Done"), button -> this.onClose())
				.bounds(this.width / 2 - 100, this.height - 30, 200, 18).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (ChipsConfig.get().snapHudToGrid) {
            for (int gx = 0; gx < width; gx += 8) graphics.fill(gx, 0, gx + 1, height, 0x102F515B);
            for (int gy = 0; gy < height; gy += 8) graphics.fill(0, gy, width, gy + 1, 0x102F515B);
            graphics.fill(width / 2, 0, width / 2 + 1, height, 0x887DE8C3);
            graphics.fill(0, height / 2, width, height / 2 + 1, 0x887DE8C3);
        }

		graphics.centeredText(this.font, "HUD LAYOUT · Drag a panel", this.width / 2, 18, 0xFFFFFFFF);
		graphics.centeredText(this.font, "Drag airship tips up/down · Reset restores its default", this.width / 2, 32, UiTheme.MUTED);
		Minecraft client = Minecraft.getInstance();
        SafariLoadingTips.renderPreview(graphics,client,width,height,bannerY);
        UiTheme.line(graphics,font,"AIRSHIP TIPS · DRAG UP/DOWN",4,Math.max(2,bannerY-10),width-8,UiTheme.ACCENT);
		RouteHud.renderPreview(graphics, client, hudX, hudY);
        UiTheme.line(graphics,font,"ROUTE · DRAG",hudX+4,Math.max(2,hudY-10),120,UiTheme.ACCENT);
		RouteHud.renderWaypointPreview(graphics, client, waypointX, waypointY);
        UiTheme.line(graphics,font,"WAYPOINT · DRAG",waypointX+4,Math.max(2,waypointY-10),120,UiTheme.ACCENT);
        graphics.fill(Math.max(0,width-118),Math.max(0,bannerY-12),width,Math.max(0,bannerY-12)+12,0xEE17232E);
        graphics.text(font,"AIRSHIP · DRAG",Math.max(2,width-112),Math.max(0,bannerY-12)+2,UiTheme.ACCENT,false);
        super.extractRenderState(graphics,mouseX,mouseY,delta);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		for(var child:children())if(child instanceof net.minecraft.client.gui.components.AbstractWidget w&&w.visible&&w.isMouseOver(event.x(),event.y()))return super.mouseClicked(event,doubleClick);
        if(event.button()==0&&event.x()>=Math.max(0,width-118)&&event.y()>=Math.max(0,bannerY-12)&&event.y()<=Math.max(0,bannerY-12)+12){draggingBanner=true;dragOffsetY=(int)event.y()-bannerY;return true;}
        if (event.button() == 0 && containsWaypoint(event.x(), event.y())) {
			draggingWaypoint = true;
			dragOffsetX = (int) event.x() - waypointX;
			dragOffsetY = (int) event.y() - waypointY;
			return true;
		}
		if (event.button() == 0 && containsMain(event.x(), event.y())) {
			draggingMain = true;
			dragOffsetX = (int) event.x() - hudX;
			dragOffsetY = (int) event.y() - hudY;
			return true;
		}
        int bh=SafariLoadingTips.previewHeight(Minecraft.getInstance(),width);
        if(event.button()==0&&event.y()>=bannerY&&event.y()<=bannerY+bh){draggingBanner=true;dragOffsetY=(int)event.y()-bannerY;return true;}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if(draggingBanner&&event.button()==0){int bh=SafariLoadingTips.previewHeight(Minecraft.getInstance(),width);bannerY=clamp((int)event.y()-dragOffsetY,0,Math.max(0,height-bh));if(snapEnabled())bannerY=snap(bannerY,bh,height,waypointY,waypointHeight());bannerMoved=true;return true;}

		if (draggingMain && event.button() == 0) {
			hudX = clamp((int) event.x() - dragOffsetX, 0, Math.max(0, this.width - RouteHud.renderedMainWidth(this.width, this.height)));
			hudY = clamp((int) event.y() - dragOffsetY, 0, Math.max(0, this.height - RouteHud.renderedMainHeight(this.width, this.height)));
            if (snapEnabled()) {
                hudX = snap(hudX, RouteHud.renderedMainWidth(width, height), width, waypointX, waypointWidth());
                hudY = snap(hudY, RouteHud.renderedMainHeight(width, height), height, waypointY, waypointHeight());
            }
			return true;
		}
		if (draggingWaypoint && event.button() == 0) {
			RouteHud.WaypointLayout layout = RouteHud.waypointPreviewLayout(Minecraft.getInstance());
			int width = (int) Math.ceil(layout.width() * layout.scale());
			int height = (int) Math.ceil(layout.height() * layout.scale());
			waypointMoved = true;
			waypointX = clamp((int) event.x() - dragOffsetX, 0, Math.max(0, this.width - width));
			waypointY = clamp((int) event.y() - dragOffsetY, 0, Math.max(0, this.height - height));
            if (snapEnabled()) {
                waypointX = snap(waypointX, width, this.width, hudX, RouteHud.renderedMainWidth(this.width,this.height));
                waypointY = snap(waypointY, height, this.height, hudY, RouteHud.renderedMainHeight(this.width,this.height));
            }
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 0 && (draggingMain || draggingWaypoint || draggingBanner)) {
			draggingBanner=false;draggingMain = false;
			draggingWaypoint = false;
			savePosition();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public void onClose() {
		savePosition();
		if (this.minecraft != null) this.minecraft.setScreenAndShow(this.parent);
	}

	private boolean containsMain(double x, double y) {
		return x >= hudX && x <= hudX + RouteHud.renderedMainWidth(this.width, this.height)
				&& y >= hudY && y <= hudY + RouteHud.renderedMainHeight(this.width, this.height);
	}

	private boolean containsWaypoint(double x, double y) {
		RouteHud.WaypointLayout layout = RouteHud.waypointPreviewLayout(Minecraft.getInstance());
		int panelWidth = (int) Math.ceil(layout.width() * layout.scale());
		int panelHeight = (int) Math.ceil(layout.height() * layout.scale());
		return x >= waypointX && x <= waypointX + panelWidth && y >= waypointY && y <= waypointY + panelHeight;
	}

	private void savePosition() {
		ChipsConfig config = ChipsConfig.get();
		if(bannerMoved)config.airshipBannerPosition=BannerPosition.fraction(bannerY,height,Math.min(height,SafariLoadingTips.previewHeight(Minecraft.getInstance(),width)));
		config.hudX = hudX;
		config.hudY = hudY;
		if (waypointMoved) {
			config.waypointHudX = waypointX;
			config.waypointHudY = waypointY;
		}
		config.save();
	}

	private boolean snapEnabled() {
        var window = Minecraft.getInstance().getWindow();
        return ChipsConfig.get().snapHudToGrid
            && !com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, 340)
            && !com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, 344);
    }
    private int waypointWidth() { var layout=RouteHud.waypointPreviewLayout(Minecraft.getInstance()); return (int)Math.ceil(layout.width()*layout.scale()); }
    private int waypointHeight() { var layout=RouteHud.waypointPreviewLayout(Minecraft.getInstance()); return (int)Math.ceil(layout.height()*layout.scale()); }
    private static int snap(int value, int panelSize, int screenSize, int otherStart, int otherSize) {
        int result = (int) Math.round(value / 8.0) * 8;
        int center = (screenSize - panelSize) / 2;
        for (int aligned : new int[]{otherStart, otherStart + otherSize - panelSize, otherStart + (otherSize - panelSize) / 2})
            if (Math.abs(value - aligned) <= 5) result = aligned;
        if (Math.abs(value - center) <= 5) result = center;
        return clamp(result, 0, Math.max(0, screenSize - panelSize));
    }
    private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, max));
	}
}

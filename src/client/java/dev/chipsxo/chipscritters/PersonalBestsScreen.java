package dev.chipsxo.chipscritters;

import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Read-only local summary of completed runs and best times. */
public final class PersonalBestsScreen extends ChipsScreen {
	private final Screen parent;
    private boolean archived;

	public PersonalBestsScreen(Screen parent) {
		super(Component.literal("ChipsCritters — Personal Bests"));
		this.parent = parent;
	}

	@Override
	protected void init() {
        addRenderableWidget(UiButton.builder(Component.literal("Show active / archived Fastest bests"),b->archived=!archived).bounds(Math.max(4,width/2-120),height-52,Math.min(240,width-8),18).build());
		addRenderableWidget(UiButton.builder(Component.literal("Done"), button -> onClose())
				.bounds(width / 2 - 100, height - 30, 200, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics,mouseX,mouseY,delta);
        int x=contentLeft(),w=contentWidth(),y=contentY(30);
        graphics.enableScissor(x,contentTop(),x+w,contentBottom());
        String[] modes=archived?new String[]{"FASTEST"}:java.util.Arrays.stream(ChipsConfig.Mode.values()).map(Enum::name).toArray(String[]::new);
        for(Biome b:Biome.values()){
            UiTheme.card(graphics,x,y,w,46,UiTheme.ROW);UiTheme.line(graphics,font,b.displayName(),x+8,y+7,w-16,biomeColor(b));
            int col=Math.max(1,(w-16)/modes.length);
            for(int i=0;i<modes.length;i++){
                String m=modes[i];long best=m.equals("FASTEST")?RunHistory.archivedFastestBest(b):RunHistory.personalBestMillis(b,ChipsConfig.Mode.valueOf(m));
                String label=m.equals("FASTEST")?"Retired":m.equals("FULL_CLEAR")?"Full":m.equals("UNIQUE")?"Unique":"Money";
                UiTheme.line(graphics,font,label+" "+(best<0?"—":duration(best)),x+8+i*col,y+27,col-4,UiTheme.TEXT);
            }y+=52;
        }
        UiTheme.line(graphics,font,"RECENT RUNS · "+RunHistory.completedRunCount()+" saved",x,y+6,w,UiTheme.ACCENT);y+=24;
        for(var entry:RunHistory.recentEntries(30)){
            String line=entry.biome+" · "+RunHistory.savedModeLabel(entry.mode)+" · "+duration(entry.durationMillis)+" · "+entry.stopCount+" stops";
            UiTheme.line(graphics,font,line,x+4,y,w-8,UiTheme.MUTED);y+=18;
        }
        virtualContentHeight(y-contentY(0)+8);graphics.disableScissor();
	}

	@Override protected String subtitle(){return "Local bests by biome and mode · Scroll for history";}
    @Override
	public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(parent); }

	private static ChipsConfig.Mode parseMode(String value) {
		try { return ChipsConfig.Mode.valueOf(value); }
		catch (IllegalArgumentException | NullPointerException ignored) { return ChipsConfig.Mode.FULL_CLEAR; }
	}

	private static String modeLabel(ChipsConfig.Mode mode) {
		return switch (mode) {
			case MONEY -> "Money";
			case UNIQUE -> "Unique";
			case FULL_CLEAR -> "Full Clear";
		};
	}

	private static String duration(long millis) {
		long seconds = Math.max(0L, millis / 1000L);
		return String.format(Locale.ROOT, "%02d:%02d", seconds / 60L, seconds % 60L);
	}

	private static int biomeColor(Biome biome) {
		return switch (biome) {
			case ICY -> 0xFF70D7FF;
			case HAUNTED -> 0xFFD27EFF;
			case FOREST -> 0xFF8CFF6E;
			case CAVERN -> 0xFFFFB65C;
		};
	}
}

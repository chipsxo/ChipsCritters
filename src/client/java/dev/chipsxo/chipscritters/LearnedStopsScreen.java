package dev.chipsxo.chipscritters;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** Local editor for user-recorded fixed coordinates. */
public final class LearnedStopsScreen extends ChipsScreen {
	private static final int PAGE_SIZE = 6;
	private final Screen parent;
	private final Biome biome;
	private final int page;
	private final int selectedIndex;
	private EditBox labelInput;

	public LearnedStopsScreen(Screen parent) { this(parent, Biome.ICY, 0, -1); }

	private LearnedStopsScreen(Screen parent, Biome biome, int page, int selectedIndex) {
		super(Component.literal("ChipsCritters — Learned Waypoints"));
		this.parent = parent;
		this.biome = biome;
		this.page = page;
		this.selectedIndex = selectedIndex;
	}

	@Override
	protected void init() {
		ChipsConfig config = ChipsConfig.get();
		List<Vec3> stops = config.learnedStops(biome);
		int pages = Math.max(1, (stops.size() + PAGE_SIZE - 1) / PAGE_SIZE);
		int safePage = Math.min(page, pages - 1);
		int center = width / 2;
		int top = height / 2 - 136;
		addRenderableWidget(UiButton.builder(Component.literal("‹ " + biome.displayName() + " ›"), button ->
				this.minecraft.setScreenAndShow(new LearnedStopsScreen(parent, nextBiome(biome), 0, -1)))
				.bounds(center - 110, top, 220, 20).build());

		int first = safePage * PAGE_SIZE;
		int last = Math.min(stops.size(), first + PAGE_SIZE);
		for (int index = first; index < last; index++) {
			int selected = index;
			Vec3 point = stops.get(index);
			String label = config.learnedStopLabel(biome, point);
			String row = String.format(java.util.Locale.ROOT, "%d  (%.1f, %.1f, %.1f)%s%s", index + 1,
					point.x, point.y, point.z, label == null ? "" : "  —  ", label == null ? "" : label);
			addRenderableWidget(UiButton.builder(Component.literal(row), button ->
					this.minecraft.setScreenAndShow(new LearnedStopsScreen(parent, biome, safePage, selected)))
					.bounds(center - 185, top + 25 + (index - first) * 22, 370, 20).build());
		}

		if (selectedIndex >= 0 && selectedIndex < stops.size()) {
			Vec3 point = stops.get(selectedIndex);
			labelInput = new EditBox(font, center - 185, top + 162, 370, 20, Component.literal("Waypoint label"));
			labelInput.setMaxLength(32);
			String currentLabel = config.learnedStopLabel(biome, point);
			labelInput.setValue(currentLabel == null ? "" : currentLabel);
			addRenderableWidget(labelInput);
			addRenderableWidget(UiButton.builder(Component.literal("Save name"), button -> {
				if (ChipsConfig.get().renameLearnedStop(biome, point, labelInput.getValue())) {
					this.minecraft.setScreenAndShow(new LearnedStopsScreen(parent, biome, safePage, selectedIndex));
				}
			}).bounds(center - 185, top + 186, 180, 20).build());
			addRenderableWidget(UiButton.builder(Component.literal("Delete waypoint"), button -> {
				ChipsConfig.get().removeLearnedStop(biome, point);
				this.minecraft.setScreenAndShow(new LearnedStopsScreen(parent, biome, safePage, -1));
			}).bounds(center + 5, top + 186, 180, 20).build());
		}

		if (safePage > 0) addRenderableWidget(UiButton.builder(Component.literal("Previous page"), button ->
				this.minecraft.setScreenAndShow(new LearnedStopsScreen(parent, biome, safePage - 1, -1)))
				.bounds(center - 185, top + 212, 120, 20).build());
		if (safePage + 1 < pages) addRenderableWidget(UiButton.builder(Component.literal("Next page"), button ->
				this.minecraft.setScreenAndShow(new LearnedStopsScreen(parent, biome, safePage + 1, -1)))
				.bounds(center - 60, top + 212, 120, 20).build());
		addRenderableWidget(UiButton.builder(Component.literal("Done"), button -> onClose())
				.bounds(center + 65, top + 212, 120, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics,mouseX,mouseY,delta);
	}

	@Override
	public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(parent); }

	@Override protected String subtitle(){return "Fixed local coordinates · Select a waypoint to rename or delete";}
    private static Biome nextBiome(Biome current) {
		Biome[] values = Biome.values();
		return values[(current.ordinal() + 1) % values.length];
	}
}

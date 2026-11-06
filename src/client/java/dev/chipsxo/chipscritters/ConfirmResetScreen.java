package dev.chipsxo.chipscritters;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Confirmation gate for clearing settings and manually learned waypoints. */
public final class ConfirmResetScreen extends ChipsScreen {
	private final Screen parent;

	public ConfirmResetScreen(Screen parent) {
		super(Component.literal("Reset ChipsCritters?"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int center = width / 2;
		int top = height / 2 - 20;
		addRenderableWidget(UiButton.builder(Component.literal("Reset settings and learned waypoints"), button -> {
			ChipsConfig.resetAll();
			WaypointRelay.updateConfiguration();
			RouteController.reset();
			if (minecraft != null) minecraft.setScreenAndShow(new ChipsSettingsScreen(parent));
		}).bounds(center - 155, top, 310, 22).build());
		addRenderableWidget(UiButton.builder(Component.literal("Cancel"), button -> onClose())
				.bounds(center - 60, top + 28, 120, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics,mouseX,mouseY,delta);
	}

	@Override protected String subtitle(){return "Clears settings and learned coordinates. Saved history and personal bests are kept.";}
    @Override
	public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(parent); }
}

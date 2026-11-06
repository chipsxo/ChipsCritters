package dev.chipsxo.chipscritters;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Optional same-lobby relay configuration; all sharing stays outside Minecraft chat. */
public final class RelaySettingsScreen extends ChipsScreen {
	private final Screen parent;
	private EditBox endpointInput;
	private Button toggleButton;

	public RelaySettingsScreen(Screen parent) {
		super(Component.literal("ChipsCritters — Waypoint Relay"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int center = width / 2;
		int top = height / 2 - 65;
		ChipsConfig config = ChipsConfig.get();
		endpointInput = new EditBox(font, center - 170, top, 340, 20, Component.literal("Relay HTTPS endpoint"));
		endpointInput.setMaxLength(200);
		endpointInput.setValue(config.relayUrl == null ? "" : config.relayUrl);
		addRenderableWidget(endpointInput);
		addRenderableWidget(UiButton.builder(Component.literal("Save settings"), button -> saveSettings())
				.bounds(center - 170, top + 31, 165, 20).build());
		toggleButton = UiButton.builder(Component.literal(enabledLabel(config.relayEnabled)), button -> toggleRelay())
				.bounds(center + 5, top + 31, 165, 20).build();
		addRenderableWidget(toggleButton);
		addRenderableWidget(UiButton.builder(Component.literal("Back"), button -> onClose())
				.bounds(center - 60, top + 61, 120, 20).build());
	}

	@Override protected String subtitle(){return "Optional HTTPS · same Safari lobby only";}
    private void saveSettings() {
		ChipsConfig config = ChipsConfig.get();
		config.relayUrl = endpointInput.getValue().trim();
		config.save();
		if (config.relayEnabled) WaypointRelay.updateConfiguration();
		message("Saved. Relay status: " + WaypointRelay.statusText());
	}

	private void toggleRelay() {
		ChipsConfig config = ChipsConfig.get();
		config.relayUrl = endpointInput.getValue().trim();
		config.relayEnabled = !config.relayEnabled;
		config.save();
		WaypointRelay.updateConfiguration();
		toggleButton.setMessage(Component.literal(enabledLabel(config.relayEnabled)));
		message("Waypoint relay " + (WaypointRelay.enabled() ? "enabled" : "not connected") + ": " + WaypointRelay.statusText());
	}

	private void message(String text) {
		if (minecraft != null && minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal("[ChipsCritters] " + text));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics,mouseX,mouseY,delta);
        bodyNote(graphics,"Status: "+WaypointRelay.statusText(),88,UiTheme.ACCENT);
        bodyNote(graphics,"Same-lobby sync. Shared: hashed lobby ID, random client ID, biome and waypoint coordinates. No endpoint means off. Party chat is not used for waypoint relay.",112,UiTheme.MUTED);
	}

	@Override
	public void onClose() { if (minecraft != null) minecraft.setScreenAndShow(parent); }

	private static String enabledLabel(boolean enabled) { return enabled ? "Disconnect relay" : "Connect relay"; }
}

package dev.chipsxo.chipscritters;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Prints a shareable diagnostic summary without player, lobby, or server identity. */
public final class ChipsBugReport {
	private ChipsBugReport() { }

	public static void printSummary() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;
        client.player.sendSystemMessage(Component.literal("[ChipsCritters] Report issues: "+ReleaseLinks.ISSUES).withStyle(style->style.withUnderlined(true).withClickEvent(new net.minecraft.network.chat.ClickEvent.OpenUrl(java.net.URI.create(ReleaseLinks.ISSUES)))));
        client.player.sendSystemMessage(Component.literal("Before posting latest.log, review it and remove personal information or credentials. Include PUBLIC/DEV, mode, biome, stop coordinates and what happened."));
		String version = FabricLoader.getInstance().getModContainer(ChipsCrittersClient.MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
		String biome = RouteController.biome() == null ? "none" : RouteController.biome().name();
		String state = RouteController.paused() ? "paused" : RouteController.active() ? "active" : "inactive";
		client.player.sendSystemMessage(Component.literal("[ChipsCritters report] Mod " + version + " | Minecraft "
				+ SharedConstants.getCurrentVersion().name() + " | Mode " + ChipsConfig.get().mode + " | Biome " + biome + " | Route " + state));
		client.player.sendSystemMessage(Component.literal("[ChipsCritters report] Stop " + RouteController.nextStop() + "/"
				+ RouteController.stops().size() + " | Route line " + ChipsConfig.get().routeRendering
				+ " | Waypoints " + ChipsConfig.get().waypointRendering + " | Relay "
				+ WaypointRelay.statusText() + " | No player name, coordinates, lobby ID, or server address included."));
	}
}

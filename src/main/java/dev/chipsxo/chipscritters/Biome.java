package dev.chipsxo.chipscritters;

import java.util.Locale;
import net.minecraft.world.phys.Vec3;

public enum Biome {
	ICY("Icy", new Vec3(-76, 67, -30)),
	HAUNTED("Haunted", new Vec3(-27, 67, -24)),
	FOREST("Forest", new Vec3(-23, 67, 30)),
	CAVERN("Cavern", new Vec3(-80, 65, 31));

	private final String displayName;
	private final Vec3 gate;

	Biome(String displayName, Vec3 gate) {
		this.displayName = displayName;
		this.gate = gate;
	}

	public String displayName() {
		return displayName;
	}

	public Vec3 gate() {
		return gate;
	}

	public static Biome parse(String text) {
		if (text == null) return null;
		String normalized = text.trim().toUpperCase(Locale.ROOT);
		for (Biome biome : values()) {
			if (biome.name().equals(normalized) || biome.displayName.equalsIgnoreCase(text.trim())) return biome;
		}
		return null;
	}

	public static Biome nearestGateWithin(Vec3 position, double radius) {
		Biome nearest = null;
		double bestDistance = radius * radius;
		for (Biome biome : values()) {
			double distance = position.distanceToSqr(biome.gate);
			if (distance <= bestDistance) {
				bestDistance = distance;
				nearest = biome;
			}
		}
		return nearest;
	}
}

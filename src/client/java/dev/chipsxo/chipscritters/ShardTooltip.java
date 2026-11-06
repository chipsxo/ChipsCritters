package dev.chipsxo.chipscritters;

import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

public final class ShardTooltip {
	private record Shard(String key, String displayName) { }
	private static final List<Shard> SHARDS = List.of(
			new Shard("Cavernfish", "Cavernfish"), new Shard("Flitter", "Flitter"), new Shard("Shyworm", "Shyworm"), new Shard("Driftling", "Driftling"), new Shard("Chuckwalla", "Chuckwalla"), new Shard("Rockmite", "Rockmite"), new Shard("Scrappy", "Scrappy"), new Shard("Gemzie", "Gemzie"), new Shard("Snoozle", "Snoozle"),
			new Shard("Hideonfloor", "Hideonfloor"), new Shard("Fluffling", "Fluffling"), new Shard("Treefrog", "Treefrog"), new Shard("Foxtrot", "Foxtrot"), new Shard("Woodchucker", "Woodchucker"), new Shard("Honeybug", "Honeybug"), new Shard("Bluebird", "Bluebird"), new Shard("Parakeet", "Parakeet"), new Shard("Macaw", "Macaw"),
			new Shard("Hideonwall", "Hideonwall"), new Shard("Solsnatcher", "Solsnatcher"), new Shard("Areita", "Areita"), new Shard("Bloodbat", "Bloodbat"), new Shard("Hideyho", "Hideyho"), new Shard("Litterbug", "Litterbug"), new Shard("Gimmiegold", "Gimmiegold"), new Shard("Duplico", "Duplico"), new Shard("Gazer", "Gazer"), new Shard("Doomspiral", "Doomspiral"),
			new Shard("Mantis Shrimp", "Mantis Shrimp"), new Shard("Shuddersquid", "Shuddersquid"), new Shard("Polaris", "Polaris"), new Shard("Strongarm", "Strongarm"), new Shard("Billygoat", "Billygoat"), new Shard("Nozzlenose", "Nozzlenose"), new Shard("Tepid", "Tepid"), new Shard("Troodon", "Troodon"), new Shard("Wumpa", "Wumpa")
	);

	private ShardTooltip() { }

	public static void register() {
		ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> appendValue(stack, lines));
	}

	private static void appendValue(ItemStack stack, List<Component> lines) {
		if (!HuntingBoxHighlights.isOpen()) return;
		int decision = slotDecision(stack);
		if (decision == 0) return;
		String id = BazaarPriceService.verifiedId(canonicalShardName(stack.getHoverName().getString()));
        var price = BazaarPriceService.price(id);
        Integer nameColor = firstNameColor(stack.getHoverName());
        if (price == null || nameColor == null) return;
        double sell = price.lowestSellOffer * (1 - ChipsConfig.get().bazaarFeePercent / 100.0);
        double salvage = BazaarPriceService.salvageValue(essenceByColor(nameColor), BazaarPriceService.verifiedId("Morning Dew"));
        lines.add(Component.literal(String.format(Locale.US, "ChipsCritters: sell %.1f | salvage %.1f coins", sell, salvage))
				.withStyle(decision > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
	}

    public record Assessment(int decision, String reason, String name, String productId, Integer color) { }
    static String canonicalShardName(String name) {
        if (name == null) return null;
        String clean = name.replaceAll("\\u00a7.", "").strip();
        for (Shard shard : SHARDS) if (clean.equals(shard.displayName) || clean.equals(shard.displayName + " Shard")) return shard.displayName + " Shard";
        return null;
    }
    public static int slotDecision(ItemStack stack) { return assess(stack).decision(); }
    public static Assessment assess(ItemStack stack) {
        String raw = stack.getHoverName().getString();
        String name = canonicalShardName(raw);
        Integer color = firstNameColor(stack.getHoverName());
        if (!ChipsConfig.get().huntingBoxColors) return new Assessment(0,"setting disabled",raw,null,color);
        if (stack.isEmpty()) return new Assessment(0,"empty slot",raw,null,color);
        if (name == null) return new Assessment(0,"not an exact Safari shard name",raw,null,color);
        int essence = color == null ? 0 : essenceByColor(color);
        if (essence == 0) return new Assessment(0,"missing or unsupported rarity",raw,null,color);
        String id = BazaarPriceService.verifiedId(name);
        String dew = BazaarPriceService.verifiedId("Morning Dew");
        if (id == null || dew == null) return new Assessment(0,"product mapping unavailable; snapshot="+BazaarPriceService.fetchedAt()+" refreshing="+BazaarPriceService.refreshing(),raw,id,color);
        var price = BazaarPriceService.price(id);
        double salvage = BazaarPriceService.salvageValue(essence,dew);
        if (price == null || price.lowestSellOffer <= 0 || salvage <= 0) return new Assessment(0,"missing or stale shard/Morning Dew price",raw,id,color);
        var config = ChipsConfig.get();
        int decision = ShardValueRule.compare(price.lowestSellOffer,salvage,config.minimumShardAdvantagePercent,config.bazaarFeePercent,config.minimumShardAdvantageCoins);
        return new Assessment(decision,decision==0 ? "tie or below minimum percentage/coin difference" : decision>0 ? "sell" : "salvage",raw,id,color);
    }
    static boolean isSafariShardName(String name) { return canonicalShardName(name) != null; }
    private static Integer firstNameColor(Component component) {
        String raw = component.getString();
        var legacy = java.util.regex.Pattern.compile("\\u00a7([fa956])",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(raw);
        if (legacy.find()) return switch(legacy.group(1).toLowerCase(Locale.ROOT)) {
            case "f" -> 0xFFFFFF; case "a" -> 0x55FF55; case "9" -> 0x5555FF; case "5" -> 0xAA00AA; case "6" -> 0xFFAA00; default -> null;
        };
        return component.<Integer>visit((style,text) -> text.isBlank() || style.getColor()==null
            ? java.util.Optional.empty() : java.util.Optional.of(style.getColor().getValue() & 0xFFFFFF),net.minecraft.network.chat.Style.EMPTY).orElse(null);
    }

	static int essenceByColor(int color) {
		return switch (color) {
			case 0xFFFFFF -> 1;
			case 0x55FF55 -> 2;
			case 0x5555FF -> 4;
			case 0xAA00AA -> 8;
			case 0xFFAA00 -> 16;
			default -> 0;
		};
	}
}

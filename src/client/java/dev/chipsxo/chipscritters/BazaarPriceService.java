package dev.chipsxo.chipscritters;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BazaarPriceService {
	public static final class ProductPrice {
		public double lowestSellOffer;
		public double highestBuyOrder;
	}
	private static final class Cache {
		public int schema = 2;
		public Map<String, String> names = new HashMap<>();
		public long fetchedAt;
		public Map<String, ProductPrice> products = new HashMap<>();
	}

	private static final long REFRESH_INTERVAL_MS = Duration.ofMinutes(10).toMillis();
	private static final URI ENDPOINT = URI.create("https://api.hypixel.net/v2/skyblock/bazaar");
	private static final Path CACHE_FILE = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("chipscritters-bazaar.json");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
	private static final AtomicBoolean REFRESHING = new AtomicBoolean(false);
	private static volatile long lastAttemptMillis;
	private static volatile Cache cache = loadCache();

	private BazaarPriceService() { }

	public static void refreshIfDue() {
		ChipsConfig config = ChipsConfig.get();
		
		if (System.currentTimeMillis() - cache.fetchedAt >= REFRESH_INTERVAL_MS) refresh(false);
	}

	public static void refresh(boolean force) {
		ChipsConfig config = ChipsConfig.get();
		Set<String> requestedIds = configuredIds(config);
		if ((!force && System.currentTimeMillis() - cache.fetchedAt < REFRESH_INTERVAL_MS)) return;
		if (!force && System.currentTimeMillis() - lastAttemptMillis < 60000L) return;
		if (!REFRESHING.compareAndSet(false, true)) return;
		lastAttemptMillis = System.currentTimeMillis();

		CompletableFuture.runAsync(() -> {
			try {
				HttpRequest request = HttpRequest.newBuilder(ENDPOINT).timeout(Duration.ofSeconds(20)).header("Accept", "application/json").GET().build();
				HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() < 200 || response.statusCode() >= 300) throw new IOException("HTTP " + response.statusCode());
				JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
				if (!root.has("success") || !root.get("success").getAsBoolean()) throw new IOException("Hypixel Bazaar response was unsuccessful");
				JsonObject products = root.getAsJsonObject("products");
				Cache next = new Cache();
				next.fetchedAt = root.has("lastUpdated") ? root.get("lastUpdated").getAsLong() : 0L;
				for (String productId : products.keySet()) {
					JsonObject product = products.has(productId) ? products.getAsJsonObject(productId) : null;
					if (product == null) continue; // Unknown IDs are discarded, never guessed from a shard name.
					ProductPrice price = new ProductPrice();
					price.lowestSellOffer = firstPrice(product.getAsJsonArray("buy_summary"));
					price.highestBuyOrder = firstPrice(product.getAsJsonArray("sell_summary"));
					if (price.lowestSellOffer > 0.0 || price.highestBuyOrder > 0.0) next.products.put(productId, price);
				}
                try (var mappingStream = BazaarPriceService.class.getResourceAsStream("/data/chipscritters/shard_products.json")) {
                    if (mappingStream == null) throw new IOException("Missing verified shard products");
                    JsonObject mapping = JsonParser.parseReader(new java.io.InputStreamReader(mappingStream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    for (var entry : mapping.entrySet()) {
                        String id = entry.getValue().getAsString();
                        if (next.products.containsKey(id)) next.names.put(entry.getKey(), id);
                    }
                }
				cache = next;
				saveCache(next);
				ChipsCrittersClient.LOGGER.info("Updated ChipsCritters Bazaar snapshot with {} verified product IDs", next.products.size());
			} catch (Exception exception) {
				ChipsCrittersClient.LOGGER.warn("Could not refresh Hypixel Bazaar prices", exception);
			} finally {
				REFRESHING.set(false);
			}
		});
	}

	private static Set<String> configuredIds(ChipsConfig config) {
		Set<String> ids = new HashSet<>();
		if (config.morningDewProductId != null && !config.morningDewProductId.isBlank()) ids.add(config.morningDewProductId.trim());
		if (config.shardProductIds != null) {
			for (String id : config.shardProductIds.values()) if (id != null && !id.isBlank()) ids.add(id.trim());
		}
		return ids;
	}

	private static double firstPrice(com.google.gson.JsonArray orders) {
		if (orders == null || orders.isEmpty()) return 0.0;
		JsonObject first = orders.get(0).getAsJsonObject();
		return first.has("pricePerUnit") ? first.get("pricePerUnit").getAsDouble() : 0.0;
	}

	public static ProductPrice price(String productId) {
		return productId == null || System.currentTimeMillis() - cache.fetchedAt > Duration.ofMinutes(20).toMillis() ? null : cache.products.get(productId);
	}

	public static String verifiedId(String exactName) { return cache.names.get(exactName); }

	public static long fetchedAt() { return cache.fetchedAt; }
	public static boolean refreshing() { return REFRESHING.get(); }

	public static double salvageValue(int essence, String morningDewId) {
		ProductPrice dew = price(morningDewId);
		return dew == null || dew.lowestSellOffer <= 0.0 ? -1.0 : (essence / 2500.0) * dew.lowestSellOffer;
	}

	private static Cache loadCache() {
		try {
			if (Files.exists(CACHE_FILE)) {
				Cache loaded = GSON.fromJson(Files.readString(CACHE_FILE), Cache.class);
				if (loaded != null && loaded.schema == 2 && loaded.names != null && !loaded.names.isEmpty()) {
					if (loaded.products == null) loaded.products = new HashMap<>();
					return loaded;
				}
			}
		} catch (Exception exception) {
			ChipsCrittersClient.LOGGER.warn("Could not load Bazaar cache", exception);
		}
		return new Cache();
	}

	private static void saveCache(Cache value) {
		try {
			Files.createDirectories(CACHE_FILE.getParent());
			Files.writeString(CACHE_FILE, GSON.toJson(value));
		} catch (IOException exception) {
			ChipsCrittersClient.LOGGER.warn("Could not save Bazaar cache", exception);
		}
	}
}

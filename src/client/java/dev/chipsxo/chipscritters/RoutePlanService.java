package dev.chipsxo.chipscritters;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.world.phys.Vec3;

/** Computes graph-only route orders away from the client thread and caches immutable results. */
public final class RoutePlanService {
	public record Result(List<Vec3> stops, long plannerNanos, boolean cacheHit) {
		public Result { stops = List.copyOf(stops); }
	}
	private static final int MAX_CACHE_ENTRIES = 48;
	private static final ConcurrentMap<RoutePlanner.Input, CompletableFuture<Result>> CACHE = new ConcurrentHashMap<>();
	private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "ChipsCritters-route-planner");
		thread.setDaemon(true);
		return thread;
	});

	private RoutePlanService() { }

	public static CompletableFuture<Result> plan(RoutePlanner.Input input) {
		CompletableFuture<Result> existing = CACHE.get(input);
		if (existing != null) return existing.thenApply(result -> new Result(result.stops(), result.plannerNanos(), true));
		if (CACHE.size() >= MAX_CACHE_ENTRIES) CACHE.clear();
		return CACHE.computeIfAbsent(input, key -> CompletableFuture.supplyAsync(() -> {
			RoutePlanner.PlanResult planned = RoutePlanner.planMeasured(key);
			ChipsCrittersClient.LOGGER.info("Route planner timings {} mode={}: template={}ms collect={}ms search={}ms total={}ms stops={}",
					key.biome(), key.mode(), ms(planned.templateNanos()), ms(planned.collectNanos()),
					ms(planned.searchNanos()), ms(planned.totalNanos()), planned.stops().size());
			return new Result(planned.stops(), planned.totalNanos(), false);
		}, EXECUTOR).whenComplete((result, error) -> {
			if (error != null) CACHE.remove(key);
		}));
	}

	private static double ms(long nanos) { return Math.round(nanos / 1_000_000.0 * 100.0) / 100.0; }

	/** Queue all route variants on Safari entry so the first gate claim normally hits the cache. */
	public static void prewarm(ChipsConfig config) {
		boolean snoozleCaught = WaypointRunLedger.snoozleCaught();
		boolean troodonCaught = WaypointRunLedger.troodonCaught();
		List<RoutePlanner.Input> requests = new ArrayList<>(Biome.values().length * ChipsConfig.Mode.values().length);
		for (Biome biome : Biome.values()) {
			List<Vec3> learned = List.copyOf(config.learnedStops(biome));
			for (ChipsConfig.Mode mode : ChipsConfig.Mode.values()) {
				ChipsConfig.ModeProfile profile = config.modeProfiles.get(mode.name());
				double detour = profile == null ? 5.0 : profile.detourBlocks;
				requests.add(new RoutePlanner.Input(biome, mode, detour, learned, snoozleCaught, troodonCaught, config.uniqueHideonwallRoutes));
			}
		}
		for (RoutePlanner.Input request : requests) plan(request);
	}

	public static CompletableFuture<List<Vec3>> reorderHaunted(List<Vec3> pending,Vec3 origin){
        List<Vec3> snapshot=List.copyOf(pending);return CompletableFuture.supplyAsync(()->RoutePlanner.reorderHaunted(snapshot,origin),EXECUTOR);
    }
    static CompletableFuture<List<Vec3>> search(List<Vec3> pending,Vec3 origin){var copy=List.copyOf(pending);return CompletableFuture.supplyAsync(()->RoutePlanner.searchOrder(copy,origin),EXECUTOR);}
    public static void clear() { CACHE.clear(); }
}

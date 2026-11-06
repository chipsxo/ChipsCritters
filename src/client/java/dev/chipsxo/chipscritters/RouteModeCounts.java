package dev.chipsxo.chipscritters;
import java.util.*;
import java.util.concurrent.CompletableFuture;
/** Same immutable planner inputs and totals as route loading; no screen-thread optimization. */
final class RouteModeCounts {
 private List<RoutePlanner.Input> inputs;
 private final Map<ChipsConfig.Mode,Map<Biome,CompletableFuture<RoutePlanService.Result>>> plans=new EnumMap<>(ChipsConfig.Mode.class);
 static List<RoutePlanner.Input> snapshot(ChipsConfig config){
  var result=new ArrayList<RoutePlanner.Input>();for(var mode:ChipsConfig.Mode.values()){
   var profile=config.modeProfiles.get(mode.name());double detour=mode==config.mode?config.detourBlocks:profile==null?5:profile.detourBlocks;
   for(var biome:Biome.values())result.add(new RoutePlanner.Input(biome,mode,detour,List.copyOf(config.learnedStops(biome)),WaypointRunLedger.snoozleCaught(),WaypointRunLedger.troodonCaught(),config.uniqueHideonwallRoutes));
  }return List.copyOf(result);
 }
 RouteModeCounts(ChipsConfig config){inputs=snapshot(config);for(var input:inputs)plans.computeIfAbsent(input.mode(),m->new EnumMap<>(Biome.class)).put(input.biome(),RoutePlanService.plan(input));}
 boolean matches(ChipsConfig config){return inputs.equals(snapshot(config));}
 String count(ChipsConfig.Mode mode,Biome biome){
  if(!HideyhoSearch.active()&&RouteController.active()&&!RouteController.buildingRoute()&&ChipsConfig.get().mode==mode&&RouteController.biome()==biome)return Integer.toString(RouteController.displayStopCount());
  var future=plans.get(mode).get(biome);if(!future.isDone())return "…";if(future.isCompletedExceptionally())return "?";
  return Integer.toString(future.join().stops().size());
 }
 String row(ChipsConfig.Mode mode,boolean first){return first?"Cavern "+count(mode,Biome.CAVERN)+" · Forest "+count(mode,Biome.FOREST):"Haunted "+count(mode,Biome.HAUNTED)+" · Icy "+count(mode,Biome.ICY);}
}

package dev.chipsxo.chipscritters;
import java.util.*;import net.minecraft.client.Minecraft;import net.minecraft.world.phys.Vec3;
/** One real router leg per client tick, cached for this route context. No world access off-thread. */
final class UniqueGoldPlacement {
 record Leg(Vec3 from,Vec3 to){}
 private static final Map<Leg,Double> costs=new HashMap<>();
 private static List<Vec3> requested=List.of(),applied=List.of();private static Vec3 origin;
 private static final ArrayDeque<Leg> queue=new ArrayDeque<>();private static long lastFailure;
 private static Object level;private static String lobby="";
 static void reset(){costs.clear();requested=applied=List.of();origin=null;queue.clear();lastFailure=0;level=null;}
 static void chunksChanged(){costs.entrySet().removeIf(e->!Double.isFinite(e.getValue()));lastFailure=0;applied=requested=List.of();queue.clear();}
 static void invalidateOrder(){applied=List.of();requested=List.of();queue.clear();}
 static List<Vec3> tick(List<Vec3> pending,Vec3 player){
  if(!pending.contains(SafariData.GIMMIEGOLD))return pending;
  var c=Minecraft.getInstance();if(c.level!=level||!WaypointRelay.lobbySessionKey().equals(lobby)){reset();level=c.level;lobby=WaypointRelay.lobbySessionKey();}
  if(pending.equals(applied))return pending;
  if(!pending.equals(requested)||origin==null){requested=List.copyOf(pending);origin=player;queue.clear();enqueue(pending,player);}
  if(!queue.isEmpty()){var leg=queue.removeFirst();costs.put(leg,measure(leg.from,leg.to));return pending;}
  if(player.distanceToSqr(origin)>16){origin=player;enqueue(pending,origin);return pending;}
  var result=GoldInsertionPlanner.choose(pending,origin,SafariData.GIMMIEGOLD,p->SafariData.isRitualWaypoint(Biome.HAUNTED,p),p->p.equals(Biome.HAUNTED.gate()),UniqueGoldPlacement::cost);
  if(result.best()==null){if(System.currentTimeMillis()-lastFailure>10000){lastFailure=System.currentTimeMillis();ChipsCrittersClient.LOGGER.info("Unique coin insertion waiting: no complete collision-checked routed candidate; keep pending Gold, origin={}",origin);}return pending;}
  applied=result.order();requested=applied;
  var best=result.best();var second=result.runnerUp();
  ChipsCrittersClient.LOGGER.info("Unique coin Gimmiegold placement: fixed={} before={} after={} addedEstimatedSeconds={} nextBestEstimatedSeconds={} savingEstimatedSeconds={} origin={} candidatesBeforeDoom=true",SafariData.GIMMIEGOLD,best.before(),best.after(),best.added(),second==null?"none":second.added(),second==null?"n/a":second.added()-best.added(),origin);
  return applied;
 }
 private static void enqueue(List<Vec3> pending,Vec3 player){
  var base=GoldInsertionPlanner.base(pending,SafariData.GIMMIEGOLD);int end=base.size();for(int i=0;i<base.size();i++)if(SafariData.isRitualWaypoint(Biome.HAUNTED,base.get(i))){end=i;break;}
  var legs=new LinkedHashSet<Leg>();
  // Stable stop-to-stop costs first; origin costs last so walking during planning doesn't invalidate all work.
  for(int i=1;i<=end;i++){var before=base.get(i-1);Vec3 after=i<base.size()?base.get(i):null;legs.add(new Leg(before,SafariData.GIMMIEGOLD));if(after!=null){legs.add(new Leg(SafariData.GIMMIEGOLD,after));legs.add(new Leg(before,after));}}
  if(base.isEmpty()||!base.getFirst().equals(Biome.HAUNTED.gate())){legs.add(new Leg(player,SafariData.GIMMIEGOLD));if(!base.isEmpty()){legs.add(new Leg(SafariData.GIMMIEGOLD,base.getFirst()));legs.add(new Leg(player,base.getFirst()));}}
  for(var leg:legs)if(!costs.containsKey(leg))queue.addLast(leg);
 }
 static double measure(Vec3 from,Vec3 to){
  var c=Minecraft.getInstance();Vec3 anchor=from.equals(origin)?from:SafariGraph.get().navigationTarget(Biome.HAUNTED,from).add(.5,0,.5);
  var line=SafariGraph.get().coinCostRoute(from,anchor,to);
  if(!line.gaps().isEmpty()||!line.issues().isEmpty()||line.points().isEmpty())return Double.POSITIVE_INFINITY;
  return GoldTravelTime.seconds(line.points());
 }
 private static double cost(Vec3 a,Vec3 b){return costs.getOrDefault(new Leg(a,b),Double.POSITIVE_INFINITY);}
}

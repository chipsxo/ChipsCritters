package dev.chipsxo.chipscritters;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.world.phys.Vec3;

public final class RecordedLegPaths {
 record Leg(Biome biome,Vec3 from,Vec3 to,List<Vec3> points){}
 private static volatile List<Leg> legs=List.of();
 private static List<Leg> local=List.of();
 private RecordedLegPaths(){}
 static Vec3 point(JsonElement e){var a=e.getAsJsonArray();return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
 static JsonArray point(Vec3 p){var a=new JsonArray();a.add(p.x);a.add(p.y);a.add(p.z);return a;}
 static List<Leg> parse(JsonArray array){var result=new ArrayList<Leg>();for(var e:array){var o=e.getAsJsonObject();var points=new ArrayList<Vec3>();for(var p:o.getAsJsonArray("points"))points.add(point(p));result.add(new Leg(Biome.valueOf(o.get("biome").getAsString()),point(o.get("from")),point(o.get("to")),List.copyOf(points)));}return List.copyOf(result);}
 public static synchronized void reload(){
  local=List.of();
  if(DevMode.enabled())try{Path file=RouteDataUpdates.folder().resolve("recorded-legs.json");if(Files.exists(file))local=parse(JsonParser.parseString(Files.readString(file)).getAsJsonArray());}catch(Exception e){ChipsCrittersClient.LOGGER.warn("Local recordings unavailable: {}",e.getMessage());}
  var all=new ArrayList<>(parse(RouteDataUpdates.paths()));
  for(var l:local){all.removeIf(old->same(old,l));all.add(l);}legs=List.copyOf(all);
 }
 private static boolean same(Leg a,Leg b){return a.biome==b.biome&&a.from.equals(b.from)&&a.to.equals(b.to);}
 static JsonArray json(List<Leg> data){var result=new JsonArray();for(var leg:data){var o=new JsonObject();o.addProperty("biome",leg.biome.name());o.add("from",point(leg.from));o.add("to",point(leg.to));var points=new JsonArray();for(var p:leg.points)points.add(point(p));o.add("points",points);result.add(o);}return result;}
 static synchronized void save(Leg leg)throws Exception {
  if(!DevMode.enabled())throw new IllegalStateException("dev build required");
  var changed=new ArrayList<>(local);changed.removeIf(old->same(old,leg));changed.add(leg);
  var prior=local;local=List.copyOf(changed);
  try{var payload=RouteDataUpdates.exportPayload(RouteDataUpdates.version()+1);SignedRouteData.validate(payload,RouteDataUpdates.MOD_VERSION);RouteDataUpdates.atomicWrite(RouteDataUpdates.folder().resolve("recorded-legs.json"),json(local).toString(),false);}
  catch(Exception e){local=prior;throw e;}
  reload();
 }
 public static JsonArray exportPaths(){var combined=new ArrayList<>(parse(RouteDataUpdates.paths()));for(var l:local){combined.removeIf(old->same(old,l));combined.add(l);}return json(combined);}
 private static Leg selected;
 private static final Set<String> logged=new HashSet<>();
 static boolean endpointsMatch(Leg l,Biome b,Vec3 origin,Vec3 target){return b==l.biome && origin!=null && target!=null && l.from.distanceToSqr(origin)<.01 && l.to.distanceToSqr(target)<.01;}
 public static boolean matches(Biome b,Vec3 origin,Vec3 target){return legs.stream().anyMatch(l->endpointsMatch(l,b,origin,target));}
 public static boolean hasTarget(Biome b,Vec3 target){var player=net.minecraft.client.Minecraft.getInstance().player;return RouteController.active() && b==RouteController.biome() && player!=null && !route(b,RouteController.legOrigin(target),target,player.position()).isEmpty();}
 private static void log(Leg l,String reason){String key=l.biome+":"+l.from+":"+l.to+":"+reason;if(logged.add(key))ChipsCrittersClient.LOGGER.info("Recording {} from={} to={} reason={}",l.biome,l.from,l.to,reason);}
 public static void clearSelection(){if(selected!=null)log(selected,"finished or route context changed");selected=null;logged.clear();}
 static int nearest(List<Vec3> points,Vec3 player){int best=0;for(int i=1;i<points.size();i++)if(points.get(i).distanceToSqr(player)<points.get(best).distanceToSqr(player))best=i;return best;}
 static List<Vec3> suffix(Leg leg,Vec3 player){int at=nearest(leg.points,player);if(leg.points.get(at).distanceToSqr(player)>64)return List.of();if(at<leg.points.size()-1&&leg.points.get(at).distanceToSqr(player)<.75*.75)at++;var result=new ArrayList<Vec3>();result.add(player);result.addAll(leg.points.subList(at,leg.points.size()));return List.copyOf(result);}
 public static List<Vec3> route(Biome biome,Vec3 origin,Vec3 target,Vec3 player){
  if(!RouteController.active() || biome!=RouteController.biome() || !java.util.Objects.equals(target,RouteController.displayTarget()))return List.of();
  for(var l:legs){
   if(!endpointsMatch(l,biome,origin,target)){if(l.biome==biome&&l.to.equals(target))log(l,"ignored: source endpoint differs");continue;}
   var points=suffix(l,player);
   if(points.isEmpty()){log(l,"ignored: player farther than 8 blocks from recorded leg; using mapped path");continue;}
   if(!RouteRenderer.recordingClear(points)){log(l,"ignored: blocked or unloaded recording; using mapped path");continue;}
   selected=l;log(l,"selected: both endpoint coordinates match");return points;
  }
  selected=null;return List.of();
 }
 static List<Vec3> costRoute(Biome biome,Vec3 origin,Vec3 target,Vec3 player){
  for(var leg:legs)if(endpointsMatch(leg,biome,origin,target)){var points=suffix(leg,player);if(!points.isEmpty()&&RouteRenderer.recordingClear(points))return points;}return List.of();
 }
 public static boolean current(){return selected!=null && RouteController.active() && endpointsMatch(selected,RouteController.biome(),RouteController.legOrigin(RouteController.displayTarget()),RouteController.displayTarget());}
 public static Vec3 guide(Vec3 player){if(!current())return null;var points=suffix(selected,player);return points.size()<2?null:points.get(1);}
 static List<Leg> localLegs(){return local;}
 static synchronized void delete(int id)throws Exception{if(id<1||id>local.size())throw new IllegalArgumentException("Unknown local recording id; use /sr record list");var changed=new ArrayList<>(local);changed.remove(id-1);RouteDataUpdates.atomicWrite(RouteDataUpdates.folder().resolve("recorded-legs.json"),json(changed).toString(),false);clearSelection();reload();}
}

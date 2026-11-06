package dev.chipsxo.chipscritters;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

public final class RouteRecorder {
 private static boolean recording,ground,climbing,water,ownedPause,pendingNext;
 private static Object level;
 private static Biome biome;
 private static Vec3 from,to;
 private static Vec3 previousTick,previousDelta=Vec3.ZERO;
 private static final ArrayList<Vec3> points=new ArrayList<>();
 private static List<Vec3> preview=List.of();
 private RouteRecorder(){}
 public static List<Vec3> preview(){
  var c=Minecraft.getInstance();
  return DevMode.enabled() && c.level==level && ChipsCrittersClient.isInCritterSafari() && (!RouteController.active() || RouteController.biome()==biome)?preview:List.of();
 }
 private static void restore(boolean refresh){if(ownedPause){ownedPause=false;RouteController.restoreAfterRecording(refresh);}}
 public static void routeChanged(){pendingNext=false;recording=false;points.clear();preview=List.of();restore(false);}

 private static void feedback(String message){RouteController.commandFeedback(message);}
 public static void start(String name,int first,int last){
  var c=Minecraft.getInstance();
  if(!DevMode.enabled()||!ChipsCrittersClient.isInCritterSafari()||c.player==null){feedback("Recording requires the dev build in Critter Safari.");return;}
  if(recording||!preview.isEmpty()){feedback("Save or cancel the current recording first.");return;}
  biome=Biome.parse(name);if(biome==null){feedback("Biome must be Icy, Cavern, Forest or Haunted.");return;}
  var stops=RouteController.active()&&RouteController.biome()==biome?RouteController.stops():SafariData.route(biome).stops();
  if(first<0||last<1||first>stops.size()||last>stops.size()||first==last){feedback("Use valid 1-based stop numbers; from 0 means Safari landing.");return;}
  from=first==0?new Vec3(-50,69,22):stops.get(first-1);to=stops.get(last-1);
  Vec3 start=first==0?from:SafariGraph.get().navigationTarget(biome,from).add(.5,0,.5);
  if(c.player.position().distanceTo(start)>8){feedback("From "+start+" is "+String.format(java.util.Locale.ROOT,"%.1f",c.player.position().distanceTo(start))+" blocks away; stand within 8, or use /sr record next.");return;}
  begin(c);
 }
 private static void begin(Minecraft c){
  level=c.level;points.clear();recording=true;ground=c.player.onGround();climbing=c.player.onClimbable();water=c.player.isInWater();sample(c.player.position());previousTick=c.player.position();previousDelta=Vec3.ZERO;
  ownedPause=RouteController.active()&&!RouteController.paused();if(ownedPause)RouteController.pause();
  feedback("Recording feet: "+biome+" "+from+" -> "+to+". Walk the intended path, then /sr record stop.");
 }
 private static void sample(Vec3 p){if(points.isEmpty()||points.getLast().distanceToSqr(p)>.0004){points.add(p);preview=List.copyOf(points);}}
 public static void tick(){if(pendingNext && !RouteController.buildingRoute()){pendingNext=false;next();}if(!recording)return;var c=Minecraft.getInstance();if(c.level!=level||c.player==null||!ChipsCrittersClient.isInCritterSafari()){cancel();return;}
  var p=c.player;Vec3 last=points.getLast();boolean event=p.onGround()!=ground||p.onClimbable()!=climbing||p.isInWater()!=water;
  Vec3 delta=p.position().subtract(previousTick);
  if(previousDelta.lengthSqr()>.0001 && delta.lengthSqr()>.0001 && previousDelta.normalize().dot(delta.normalize())<.85) sample(previousTick);
  if(event||!p.onGround()||p.onClimbable()||p.isInWater()||last.distanceToSqr(p.position())>=.5*.5||Math.abs(last.y-p.getY())>=.1)sample(p.position());
  previousTick=p.position();previousDelta=delta;
  ground=p.onGround();climbing=p.onClimbable();water=p.isInWater();
  if(points.size()>=12000){recording=false;feedback("Recording limit reached; preview retained.");}
 }
 public static void stop(){if(!recording){feedback("No recording is running.");return;}if(Minecraft.getInstance().player==null){cancel();return;}sample(Minecraft.getInstance().player.position());recording=false;feedback("Preview ready: "+points.size()+" foot samples. /sr record save or /sr record cancel.");}
 public static void cancel(){pendingNext=false;recording=false;points.clear();preview=List.of();restore(true);feedback("Recording cancelled; saved paths retained.");}
 public static void save(){if(recording||points.size()<2){feedback("Stop a recording with at least two samples before saving.");return;}
  Vec3 end=SafariGraph.get().navigationTarget(biome,to).add(.5,0,.5);
  if(points.getLast().distanceTo(end)>Math.max(1.5,ChipsConfig.get().arrivalRadius)){feedback("Recording ends at "+points.getLast()+", "+String.format(java.util.Locale.ROOT,"%.1f",points.getLast().distanceTo(end))+" blocks from target "+end+". Reach the cube, then record the full leg again.");return;}
  try{RecordedLegPaths.save(new RecordedLegPaths.Leg(biome,from,to,List.copyOf(points)));preview=List.of();points.clear();restore(true);if(RouteController.active()&&!RouteController.paused())RouteController.refreshVisibleRouteLine();feedback("Recorded leg saved in config/chipscritters-routes/recorded-legs.json. Route restored; the saved leg only applies to its matching endpoints.");}catch(Exception e){feedback("Recording not saved: "+e.getMessage());}
 }
 public static void export(int version){try{if(version<=RouteDataUpdates.version())throw new IllegalArgumentException("data version must increase");RouteDataUpdates.atomicWrite(RouteDataUpdates.folder().resolve("routes-export.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(RouteDataUpdates.exportPayload(version)),false);feedback("Exported routes-export.json (data v"+version+"). Sign it locally before publishing.");}catch(Exception e){feedback("Export failed: "+e.getMessage());}}
 public static void next(){
  var c=Minecraft.getInstance();
  if(!DevMode.enabled()){feedback("Recording requires the DEV build.");return;}
  if(c.player==null){feedback("Recording requires a connected player.");return;}
  var state=RecordNextState.check(RouteController.active(),ChipsCrittersClient.isInCritterSafari(),RouteController.buildingRoute(),RouteController.displayTarget()!=null);
  switch(state){
   case NO_ROUTE -> {feedback("Start a Safari route before /sr record next.");return;}
   case WAIT_SAFARI -> {feedback("Waiting for confirmed Critter Safari lobby information.");return;}
   case WAIT_BUILD -> {pendingNext=true;feedback("Route is building; recording will begin at its next waypoint when ready.");return;}
   case NO_WAYPOINT -> {feedback("Route is active but waiting for a catch or completion; there is no waypoint to record.");return;}
   default -> {}
  }
  if(recording||!preview.isEmpty()){feedback("Save or cancel the current recording first.");return;}
  biome=RouteController.biome();to=RouteController.displayTarget();from=RouteController.legOrigin(to);
  feedback("Record next: feet "+c.player.position()+" -> "+to+" (leg source "+from+").");begin(c);
 }
 public static void list(){var list=RecordedLegPaths.localLegs();if(list.isEmpty())feedback("No local recordings saved.");for(int i=0;i<list.size();i++){var l=list.get(i);feedback((i+1)+": "+l.biome()+" "+l.from()+" -> "+l.to()+" ("+l.points().size()+" samples; modes with this exact leg)");}}
 public static void delete(int id){try{RecordedLegPaths.delete(id);feedback("Deleted local recording "+id+".");if(RouteController.active()&&!RouteController.paused())RouteController.refreshVisibleRouteLine();}catch(Exception e){feedback("Delete failed: "+e.getMessage());}}
 private static int usage(String text){feedback("Usage: /sr record "+text);return 1;}
 public static LiteralArgumentBuilder<FabricClientCommandSource> command(){return ClientCommands.literal("record")
  .executes(c->usage("next | start <biome> <from> <to> | stop | cancel | save | list | delete <id> | export <dataVersion>"))
  .then(ClientCommands.literal("next").executes(c->{next();return 1;}))
  .then(ClientCommands.literal("list").executes(c->{list();return 1;}))
  .then(ClientCommands.literal("delete").executes(c->usage("delete <id>")).then(ClientCommands.argument("id",IntegerArgumentType.integer(1)).executes(c->{delete(IntegerArgumentType.getInteger(c,"id"));return 1;})))
  .then(ClientCommands.literal("start").executes(c->usage("start <biome> <from> <to>"))
   .then(ClientCommands.argument("biome",StringArgumentType.word()).executes(c->usage("start <biome> <from> <to>"))
    .then(ClientCommands.argument("from",IntegerArgumentType.integer(0)).executes(c->usage("start <biome> <from> <to>"))
     .then(ClientCommands.argument("to",IntegerArgumentType.integer(1)).executes(c->{start(StringArgumentType.getString(c,"biome"),IntegerArgumentType.getInteger(c,"from"),IntegerArgumentType.getInteger(c,"to"));return 1;})))))
  .then(ClientCommands.literal("stop").executes(c->{stop();return 1;})).then(ClientCommands.literal("cancel").executes(c->{cancel();return 1;}))
  .then(ClientCommands.literal("save").executes(c->{save();return 1;}))
  .then(ClientCommands.literal("export").executes(c->usage("export <dataVersion>")).then(ClientCommands.argument("version",IntegerArgumentType.integer(1)).executes(c->{export(IntegerArgumentType.getInteger(c,"version"));return 1;})));}
}

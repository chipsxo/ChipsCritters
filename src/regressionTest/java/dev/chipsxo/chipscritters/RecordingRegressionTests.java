package dev.chipsxo.chipscritters;
import java.util.*;
import net.minecraft.world.phys.Vec3;
final class RecordingRegressionTests {
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static void field(Class<?> c,String n,Object v)throws Exception{var f=c.getDeclaredField(n);f.setAccessible(true);f.set(null,v);}
 static Object field(Class<?> c,String n)throws Exception{var f=c.getDeclaredField(n);f.setAccessible(true);return f.get(null);}
 static void run()throws Exception{
  int legs=0;
  var unrelated=new RecordedLegPaths.Leg(Biome.CAVERN,new Vec3(-101,81,60),new Vec3(-92,72,42),List.of(new Vec3(-100,82,60),new Vec3(-99.5,82,60)));
  for(var b:Biome.values())for(var m:ChipsConfig.Mode.values()){
   var stops=RoutePlanner.plan(new RoutePlanner.Input(b,m,5,List.of(),false,false));Vec3 from=new Vec3(-50,69,22),logicalFrom=b.gate();
   for(var target:stops){
    Vec3 to=SafariTerrain.floor(b,target).add(.5,0,.5);
    var path=SafariGraph.get().auditMappedPath(target.equals(b.gate())?null:b,from,to);
    check(!path.isEmpty(),"mapped topology guidance "+b+" "+m+" "+from+" -> "+to);
    check(!RecordedLegPaths.endpointsMatch(unrelated,b,logicalFrom,target),"unrelated recording cannot replace another leg");
    from=to;logicalFrom=target;legs++;
   }
  }
  var a=new Vec3(-101,81,60);var z=new Vec3(-92,72,42);
  check(RecordedLegPaths.endpointsMatch(unrelated,Biome.CAVERN,a,z),"both logical endpoints match");
  check(!RecordedLegPaths.endpointsMatch(unrelated,Biome.CAVERN,new Vec3(-100,81,60),z),"same target, different origin ignored after skip/replan");
  check(!RecordedLegPaths.endpointsMatch(unrelated,Biome.FOREST,a,z),"another biome ignored");
  check(RecordedLegPaths.suffix(unrelated,new Vec3(-79,65,31)).isEmpty(),"far-away rejoin cannot attach to unrelated recording");
  check(RecordedLegPaths.suffix(unrelated,new Vec3(-100,82,60)).getFirst().equals(new Vec3(-100,82,60)),"recorded suffix connected to current player");
  // Exercise the actual cleanup used by new runs, restarts, skips, undo and biome changes.
  field(RouteRecorder.class,"recording",true);field(RouteRecorder.class,"ownedPause",true);
  field(RouteRecorder.class,"preview",unrelated.points());field(RouteController.class,"paused",true);
  field(RouteController.class,"startedAtMillis",123456L);
  RouteRecorder.routeChanged();
  check(!(Boolean)field(RouteRecorder.class,"recording"),"recording stopped on lifecycle change");
  check(((List<?>)field(RouteRecorder.class,"preview")).isEmpty(),"unsaved preview cleared on lifecycle change");
  check(!(Boolean)field(RouteController.class,"paused"),"recorder-owned pause restored");
  check((Long)field(RouteController.class,"startedAtMillis")==123456L,"real route timer retained");
  field(RouteRecorder.class,"ownedPause",false);field(RouteController.class,"paused",true);RouteRecorder.routeChanged();
  check((Boolean)field(RouteController.class,"paused"),"preexisting user pause preserved");
  field(RouteController.class,"paused",false);field(RouteController.class,"startedAtMillis",0L);
  var command=RouteRecorder.command().build();
  check(command.getChild("next").getCommand()!=null&&command.getChild("list").getCommand()!=null,"next/list commands registered");
  for(String missing:List.of("start","export","delete"))check(command.getChild(missing).getCommand()!=null,"usage feedback "+missing);
  System.out.println("Recording regression checks passed: "+legs+" biome/mode topology legs; unrelated recording isolation and draft cleanup.");
 }
}

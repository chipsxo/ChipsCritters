package dev.chipsxo.chipscritters;
import java.util.*;
import java.nio.file.*;
import com.google.gson.*;
import net.minecraft.world.phys.Vec3;
public final class Route56Audit {
 static Vec3 center(Biome b,Vec3 p){return SafariTerrain.floor(b,p).add(.5,0,.5);}
 static JsonArray xyz(Vec3 p){return RecordedLegPaths.point(p);}
 static void leg(JsonArray all,Biome b,ChipsConfig.Mode mode,int stop,Vec3 a,Vec3 z,String kind){
  var o=new JsonObject();o.addProperty("biome",b.name());o.addProperty("mode",mode.name());o.addProperty("stop",stop);o.addProperty("kind",kind);o.add("from",xyz(a));o.add("to",xyz(z));
  var path=VerifiedApproaches.from(b,a,z);if(path.isEmpty())path=SafariGraph.get().auditMappedPath(kind.equals("LANDING")?null:b,a,z);
  if(kind.equals("CLIMB"))path=List.of(a,new Vec3(a.x,z.y,a.z),z);
  if(kind.equals("WATERFALL")||SafariData.isForestShortcutLeg(a,z))path=List.of(a,z);
  var points=new JsonArray();for(var p:path)points.add(xyz(p));o.add("path",points);all.add(o);
 }
 public static void main(String[] args)throws Exception{
  var all=new JsonArray();for(var b:Biome.values())for(var m:ChipsConfig.Mode.values()){
   var stops=RoutePlanner.plan(new RoutePlanner.Input(b,m,5,List.of(),false,false));Vec3 from=new Vec3(-50,69,22);
   for(int i=0;i<stops.size();i++){
    var target=stops.get(i);
    if(b==Biome.CAVERN)for(var c:SafariData.cavernLadders())if(c.before().equals(target)){
     leg(all,b,m,i+1,from,center(b,c.bottom()),"CLIMB APPROACH");leg(all,b,m,i+1,center(b,c.bottom()),center(b,c.top()),"CLIMB");from=center(b,c.top());
    }
    leg(all,b,m,i+1,from,center(b,target),i==0?"LANDING":"STOP");from=center(b,target);
    if(b==Biome.CAVERN&&SafariData.isCavernWaterfallConnector(target)){var end=SafariData.CAVERN_WATERFALL_EXIT.add(.5,0,.5);leg(all,b,m,i+1,from,end,"WATERFALL");from=end;}
   }
  }
  for(var m:ChipsConfig.Mode.values())leg(all,Biome.HAUNTED,m,0,new Vec3(8.07,48.42,-26.17),new Vec3(3.5,60,-54.5),"DOOMSPIRAL TO GIMMIEGOLD");
  leg(all,Biome.CAVERN,ChipsConfig.Mode.MONEY,2,new Vec3(-81.8368759711142,64,33.28141678224722),new Vec3(-88.5,61,43.5),"CLIP LADDER APPROACH");
  leg(all,Biome.CAVERN,ChipsConfig.Mode.MONEY,3,new Vec3(-112.38376147400136,81,25.533184351040514),new Vec3(-113.5,84,17.5),"CLIP VINES APPROACH");
  leg(all,Biome.CAVERN,ChipsConfig.Mode.MONEY,4,new Vec3(-98.69255748143605,95,21.07677041265056),new Vec3(-126.5,81,34.5),"CLIP FLOOR DROP AFTER PASSED STOP");
  Files.createDirectories(Path.of("work"));
  Files.writeString(Path.of("work/audit56-map.json"),new GsonBuilder().setPrettyPrinting().create().toJson(all));System.out.println("Audited "+all.size()+" plan/connector legs; topology only, terrain checked separately.");
 }
}

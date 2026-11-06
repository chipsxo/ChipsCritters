package dev.chipsxo.chipscritters;
import java.util.*;import net.minecraft.world.phys.Vec3;
final class Patch62RegressionTests {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError("0.1.62: "+message);}
 static void run()throws Exception{
  var d=new DoomspiralDings();var c=new DoomspiralCountdown();long t=10000;c.message(DoomspiralCountdown.SUMMON,t);
  check(d.tick(t,true,c.notifyOnce(t),true)==0,"no sound before ready");
  check(d.tick(t+6000,true,c.notifyOnce(t+6000),true)==1,"first ding exactly once at ready");
  check(d.tick(t+6100,true,c.notifyOnce(t+6100),true)==0,"pair has quick distinct spacing");
  check(d.tick(t+6130,true,c.notifyOnce(t+6130),true)==1,"second ding at 130ms");
  check(d.tick(t+6200,true,c.notifyOnce(t+6200),true)==0,"no third ding");
  c.message(DoomspiralCountdown.SUMMON,t+8000);check(d.tick(t+8000,true,c.notifyOnce(t+8000),true)==0,"duplicate summon cannot replay pair");
  d.reset();check(d.tick(t,true,true,false)==0&&d.tick(t+500,true,false,true)==0,"toggle/zero volume suppress whole pair");
  d.tick(t,true,true,true);check(d.tick(t+140,false,false,true)==0,"capture/lobby cancellation prevents delayed ding");
  d.tick(t,true,true,true);check(d.tick(t+140,true,false,false)==0,"volume/toggle checked again for second ding");
  for(int gui=1;gui<=4;gui++)for(int[] size:List.of(new int[]{1920,1080},new int[]{1280,720},new int[]{800,600},new int[]{640,480})){
   int w=Math.max(320,size[0]/gui),h=Math.max(240,size[1]/gui);
   for(double chat:new double[]{.2,.4,.6})for(int barHeight:new int[]{22,32,42}){
    var obstacles=List.of(new TipPlacement.Rect(0,(int)(h*(1-chat)),w/2,(int)(h*chat)),new TipPlacement.Rect((w-140)/2,10,140,35),new TipPlacement.Rect(0,h-80,w,40));
    var bar=TipPlacement.bar(w,h,barHeight,obstacles);
    if(bar!=null)check(bar.x()==0&&bar.w()==w&&bar.y()>=0&&bar.y()+bar.h()<=h&&obstacles.stream().noneMatch(bar::overlaps),"edge-to-edge clear bar GUI "+gui);
   }
   var ordinary=TipPlacement.bar(w,h,24,List.of(new TipPlacement.Rect(0,h-85,w,85)));check(ordinary!=null&&ordinary.w()==w,"ordinary cutscene bar always fits");
  }
  check(TipPlacement.bar(320,240,30,List.of(new TipPlacement.Rect(0,0,320,240)))==null,"fullscreen chat impossible placement suppressed");
  Vec3 edge=SafariData.CAVERN_DROP_START.add(.5,0,.5),end=SafariData.CAVERN_DROP_END.add(.5,0,.5);
  java.util.function.BiPredicate<Vec3,Vec3> rock=(a,b)->List.of(a,b,a.lerp(b,.5)).stream().noneMatch(p->p.x>-90&&p.x<-88&&p.y>66&&p.y<73);
  check(!rock.test(edge.lerp(end,.55),edge.lerp(end,.65)),"synthetic rock matches logged diagonal obstruction region");
  var flight=DropRibbonPath.flight(edge,end,rock);check(!flight.isEmpty()&&flight.getFirst().equals(edge)&&flight.getLast().equals(end),"clear descent keeps approved endpoints");
  for(int i=1;i<flight.size();i++)check(rock.test(flight.get(i-1),flight.get(i))&&flight.get(i).y<=flight.get(i-1).y,"descent monotonic and collision checked");
  check(DropRibbonPath.flight(edge,end,(a,b)->false).isEmpty(),"never invent a blocked fallback");
  var approach=DropRibbonPath.approach(List.of(new Vec3(-102,82,58),edge),(a,b)->true);
  var full=DropRibbonPath.combine(approach,flight);check(full.getFirst().equals(approach.getFirst())&&full.contains(edge)&&full.getLast().equals(end),"one continuous player-edge-descent polyline");
  var lifted=DropRibbonPath.approach(List.of(new Vec3(0,0,0),new Vec3(1,0,0)),(a,b)->a.y>=.3&&b.y>=.3);
  check(!lifted.isEmpty()&&lifted.stream().allMatch(p->p.y>=.3&&p.y<=1.5),"quarter-block samples lift locally above floor");
  check(DropRibbonPath.approach(List.of(Vec3.ZERO,new Vec3(1,0,0)),(a,b)->false).isEmpty(),"unclear approach requires recording, not through-wall line");
  for(var mode:ChipsConfig.Mode.values()){
   for(var biome:Biome.values()){
    int count=RoutePlanner.plan(new RoutePlanner.Input(biome,mode,5,List.of(),false,false,true)).size();
    int expected=biome==Biome.CAVERN?40:biome==Biome.FOREST?45:biome==Biome.ICY?27:mode==ChipsConfig.Mode.MONEY?58:mode==ChipsConfig.Mode.UNIQUE?66:73;
    check(count==expected,"same planner totals as HUD "+biome+" "+mode);
   }
  }
  check(RoutePlanner.plan(new RoutePlanner.Input(Biome.HAUNTED,ChipsConfig.Mode.UNIQUE,5,List.of(),false,false,false)).size()==58,"Hideonwall off updates Unique total");
  System.out.println("0.1.62 checks passed: one two-ding pair/toggle/reset; full-width tip bars at GUI1-4; logged-region synthetic drop rock, local sample lifts and no blocked fallback; continuous approved-endpoint connector; all mode totals incl Hideonwall toggle. Patch60/61 ensure unchanged normal orders and working messages/search.");
 }
}

package dev.chipsxo.chipscritters;
import java.util.*;import net.minecraft.world.phys.Vec3;
final class Patch63RegressionTests {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError("0.1.63: "+message);}
 static void run()throws Exception{
  check(BannerPosition.sanitize(-1)==-1&&BannerPosition.sanitize(Double.NaN)==-1,"missing/invalid position uses unchanged automatic placement");
  for(int gui=1;gui<=4;gui++)for(int[] pixels:List.of(new int[]{1920,1080},new int[]{1280,720},new int[]{800,600},new int[]{640,480})){
   int h=Math.max(240,pixels[1]/gui);for(int bh:new int[]{22,32,52,300})for(double f:new double[]{0,.25,.5,.99,1}){
    int boundedHeight=Math.min(h,bh),y=BannerPosition.y(f,h,boundedHeight);
    check(y>=0&&y+boundedHeight<=h,"resized/scaled banner remains on screen");
    if(h>boundedHeight)check(Math.abs(BannerPosition.fraction(y,h,boundedHeight)-f)<=1.0/(h-boundedHeight),"drag position round trip");
   }
  }
  check(BannerPosition.sanitize(3)==1&&BannerPosition.sanitize(-2)==-1,"config position bounds");
  check(Math.abs(GoldTravelTime.seconds(List.of(Vec3.ZERO,new Vec3(4.3,0,0)))-1)<.00001,"reference routed walk time");
  check(Math.abs(GoldTravelTime.seconds(List.of(Vec3.ZERO,new Vec3(0,2.35,0)))-1)<.00001,"reference climb cost includes height");
  check(GoldTravelTime.seconds(List.of(new Vec3(0,22,0),Vec3.ZERO))<22/GoldTravelTime.WALK_BLOCKS_PER_SECOND,"directed free fall differs from walking/climbing");
  Vec3 origin=new Vec3(0,0,0),a=new Vec3(1,0,0),b=new Vec3(2,0,0),doom=new Vec3(3,0,0),gold=new Vec3(4,0,0);
  record Leg(Vec3 from,Vec3 to){}var cost=new HashMap<Leg,Double>();
  cost.put(new Leg(origin,a),10.0);cost.put(new Leg(a,b),10.0);cost.put(new Leg(b,doom),10.0);
  cost.put(new Leg(origin,gold),30.0);cost.put(new Leg(gold,a),30.0); // Front priority is expensive.
  cost.put(new Leg(a,gold),8.0);cost.put(new Leg(gold,b),9.0);
  cost.put(new Leg(b,gold),12.0);cost.put(new Leg(gold,doom),12.0);
  var input=List.of(a,b,doom,gold);var result=GoldInsertionPlanner.choose(input,origin,gold,doom::equals,p->false,(x,y)->cost.getOrDefault(new Leg(x,y),Double.POSITIVE_INFINITY));
  check(result.order().equals(List.of(a,gold,b,doom)),"choose cheapest insertion, not first or after Doom");
  check(result.best().before().equals(a)&&result.best().after().equals(b)&&result.best().added()==7&&result.runnerUp().added()==14,"added routed cost and next-best margin");
  check(result.order().size()==input.size()&&Collections.frequency(result.order(),gold)==1,"one existing Gold and stable count");
  cost.put(new Leg(gold,b),Double.POSITIVE_INFINITY);var blocked=GoldInsertionPlanner.choose(input,origin,gold,doom::equals,p->false,(x,y)->cost.getOrDefault(new Leg(x,y),Double.POSITIVE_INFINITY));
  check(blocked.order().equals(List.of(a,b,gold,doom)),"blocked or one-way-unreachable exit rejected; next valid slot used");
  var cleared=GoldInsertionPlanner.choose(List.of(b,doom,gold),a,gold,doom::equals,p->false,(x,y)->cost.getOrDefault(new Leg(x,y),Double.POSITIVE_INFINITY));
  check(cleared.order().equals(List.of(b,gold,doom))&&!cleared.order().contains(a),"claims/cleared stops do not return");
  var none=GoldInsertionPlanner.choose(input,origin,gold,doom::equals,p->false,(x,y)->Double.POSITIVE_INFINITY);
  check(none.best()==null&&none.order().equals(input),"no invented direct fallback for missing paths");
  var removed=GoldInsertionPlanner.choose(List.of(a,b,doom),origin,gold,doom::equals,p->false,(x,y)->1);
  check(removed.order().equals(List.of(a,b,doom))&&removed.best()==null,"never restore a Gold stop absent from pending route");
  var gated=GoldInsertionPlanner.choose(List.of(a,b,doom,gold),origin,gold,doom::equals,a::equals,(x,y)->1);
  check(gated.order().getFirst().equals(a),"unreached gate remains first");
  for(boolean paintings:new boolean[]{true,false}){var pending=RoutePlanner.plan(new RoutePlanner.Input(Biome.HAUNTED,ChipsConfig.Mode.UNIQUE,5,List.of(),false,false,paintings));var placed=GoldInsertionPlanner.choose(pending,Biome.HAUNTED.gate(),SafariData.GIMMIEGOLD,p->SafariData.isRitualWaypoint(Biome.HAUNTED,p),p->p.equals(Biome.HAUNTED.gate()),(x,y)->1);
   check(placed.order().size()==(paintings?66:58),"Unique total unchanged with coin");check(GoldInsertionPlanner.base(placed.order(),SafariData.GIMMIEGOLD).equals(GoldInsertionPlanner.base(pending,SafariData.GIMMIEGOLD)),"all other order unchanged");}
  var totals=new HauntedFinish();totals.rememberTotal(RouteController.hauntedTotalKey(ChipsConfig.Mode.UNIQUE,true),66);totals.rememberTotal(RouteController.hauntedTotalKey(ChipsConfig.Mode.UNIQUE,false),58);
  check(totals.total(RouteController.hauntedTotalKey(ChipsConfig.Mode.UNIQUE,false),58)==58&&totals.total(RouteController.hauntedTotalKey(ChipsConfig.Mode.UNIQUE,true),66)==66,"Hideonwall toggle retains separate 66/58 HUD totals");
  System.out.println("0.1.63 checks passed: banner fractional persistence/clamping at GUI1-4 and resized windows; actual-cost insertion contract, runner-up margin, directed/blocked rejection, cleared-stop preservation, gate and Doom bounds, one Gold and unchanged 66/58 totals. Earlier suites preserve all no-coin/Money/Full Clear routes and timing.");
 }
}

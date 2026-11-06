package dev.chipsxo.chipscritters;
import java.util.*;import net.minecraft.world.phys.Vec3;
final class Patch58RegressionTests {
 static void check(boolean c,String m){if(!c)throw new AssertionError(m);}
 static void run()throws Exception{
  String real="-----------------------------\n[MVP+] chipsxo entered Critter Safari!\n-----------------------------";
  var tips=new SafariTripTips();check(tips.begin(real,1000),"real logged entry");
  check(tips.immediateVisibility(1050,true,true,true).equals("show"),"immediate unknown-lobby visibility without locraw");
  check(!SafariTripTips.ownEntry("[VIP] Friend entered Critter Safari!","chipsxo"),"other entry cannot arm local trip");
  check(tips.immediateVisibility(1100,false,true,true).equals("setting off"),"setting respected");
  check(!tips.begin("[VIP] Friend entered Critter Safari!",1400),"burst clock kept");
  for(var l:SafariData.cavernLadders())if(l.name().equals("CLIMB LADDER"))for(var p:List.of(l.bottom(),l.top())){
   check(RouteController.withinLadderReachArea(p.add(2.9,0,2.9),p),"expanded 5x5 bounds");
   check(!RouteController.withinLadderReachArea(p.add(3.1,0,.5),p),"outside 5x5");
   check(!RouteController.withinLadderReachArea(p.add(.5,-2.1,.5),p),"other floor rejected");
  }
  var cave=SafariData.route(Biome.CAVERN).stops();check(cave.size()==40 && cave.get(7).equals(SafariData.CAVERN_AFTER_DROP),"41 stops, no counted drop guide");
  check(!cave.contains(SafariData.CAVERN_DROP_START)&&!cave.contains(SafariData.CAVERN_DROP_END),"drop guides uncounted");
  var painting=SafariTerrain.painting(Biome.HAUNTED,new Vec3(6,79,-83));check(painting.stand().equals(new Vec3(6,79,-84)),"adjacent reachable painting face");
  check(new Vec3(10.3178,79.1661,-77.9298).distanceTo(painting.stand().add(.5,0,.5))>1.25,"logged early arrival now rejected");
  for(var house:SafariData.route(Biome.HAUNTED).houseSweep())check(!RouteController.passedStopAllowed(Biome.HAUNTED,house),"house cannot bulk-clear");
  for(var mode:ChipsConfig.Mode.values()){
   var p=RoutePlanner.plan(new RoutePlanner.Input(Biome.HAUNTED,mode,5,List.of(),false,false));check(p.getLast().equals(SafariData.GIMMIEGOLD),"gold last "+mode);check(p.stream().filter(SafariData.GIMMIEGOLD::equals).count()==1,"gold only once");
   int paint=p.indexOf(new Vec3(6,79,-83));for(var b:SafariData.route(Biome.HAUNTED).gazerBeds())if(b.x>0&&p.contains(b))check(paint<p.indexOf(b),"painting before east beds");
  }
  var state=new HauntedFinish();state.rememberTotal("MONEY",58);check(state.total("MONEY",1)==58,"total kept after Doomspiral pending list shrinks");check(!state.finishOnce(),"cannot finish before Doom and Gold");state.doom=true;check(!state.finishOnce(),"must still get Gold");state.gold=true;check(state.finishOnce()&&!state.finishOnce(),"one real finish");
  var times=new RouteRunBookkeeping();long start=times.startOrResume(Biome.HAUNTED,1000,0);times.disqualify(Biome.HAUNTED);check(times.startOrResume(Biome.HAUNTED,9999,0)==start,"restore keeps original clock");check(!times.markCompleted(Biome.HAUNTED,ChipsConfig.Mode.MONEY,true),"restored stops cannot make PB");
  WaypointRunLedger.newRunStarted();WaypointRunLedger.markSnoozleCaught();WaypointRunLedger.markTroodonCaught();WaypointRunLedger.markSatisfied(Biome.CAVERN,cave.getFirst());
  var confirmation=new ConfirmationGate();confirmation.cancel();check(!confirmation.confirm(()->WaypointRunLedger.restoreBiome(Biome.CAVERN,false)),"cancel cannot apply restoration");check(WaypointRunLedger.isSatisfied(Biome.CAVERN,cave.getFirst()),"cancel/construct confirmation changes nothing");
  check(WaypointRunLedger.restoreBiome(Biome.CAVERN,false)==1 && WaypointRunLedger.snoozleCaught()&&WaypointRunLedger.troodonCaught(),"scoped restore preserves captures by default");
  WaypointRunLedger.restoreBiome(Biome.CAVERN,true);check(!WaypointRunLedger.snoozleCaught()&&WaypointRunLedger.troodonCaught(),"explicit catch reset only scoped biome");
  WaypointRelay.restoreLocal(Biome.CAVERN);check(WaypointRelay.replayLocal(Biome.CAVERN)&&WaypointRelay.filterAlreadyClaimed(Biome.CAVERN,cave).equals(cave),"server claims cannot immediately reclaim restored stops");
  var commands=new com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>();for(String root:List.of("sr","chipscritters")){ChipsCrittersClient.registerRouteCommands(commands,root);for(String c:List.of("refresh","refresh claims","refresh claims all","skip","back","undo"))check(commands.parse(root+" "+c,null).getExceptions().isEmpty(),"command remains valid "+c);}
  var confirmed=new ConfirmationGate();int[] applications={0};check(confirmed.confirm(()->applications[0]++),"explicit confirmation applies");check(!confirmed.confirm(()->applications[0]++) && applications[0]==1,"confirmation applies once");
  WaypointRunLedger.newRunStarted();WaypointRelay.clearReplayForNewRun();
  var old=new java.util.HashMap<String,Object>();for(String n:List.of("active","biome","stops","nextStop","dropStage"))old.put(n,RecordingRegressionTests.field(RouteController.class,n));
  try{RecordingRegressionTests.field(RouteController.class,"active",true);RecordingRegressionTests.field(RouteController.class,"biome",Biome.HAUNTED);RecordingRegressionTests.field(RouteController.class,"stops",List.of(SafariData.GIMMIEGOLD));RecordingRegressionTests.field(RouteController.class,"nextStop",0);RecordingRegressionTests.field(RouteController.class,"dropStage",0);
   WaypointRunLedger.HAUNTED.rememberTotal("MONEY",58);
   check(WaypointRunLedger.HAUNTED.total("MONEY",1)==58 && RouteStopCounter.text(true,57,58).equals("58/58"),"full total kept for final single stop");
   check(RouteController.goldLocked(SafariData.GIMMIEGOLD) && RouteController.displayTarget()==null && !WaypointRunLedger.HAUNTED.finishOnce(),"Gold remains incomplete and hidden before Doom");
   WaypointRunLedger.HAUNTED.doom=true;check(RouteController.displayTarget().equals(SafariData.GIMMIEGOLD),"Doom unlocks final target");
  }finally{for(var e:old.entrySet())RecordingRegressionTests.field(RouteController.class,e.getKey(),e.getValue());WaypointRunLedger.newRunStarted();}

  System.out.println("0.1.58 checks passed: prompt tips, 5x5 ladders, 41 Cavern stops, painting face/current-only, final Gold count, confirmed claim restore/timer/PB/relay/capture scope.");
 }
}

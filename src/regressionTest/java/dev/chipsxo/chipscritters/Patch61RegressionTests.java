package dev.chipsxo.chipscritters;
import java.util.*;
import net.minecraft.world.phys.Vec3;
final class Patch61RegressionTests {
 static void check(boolean ok,String s){if(!ok)throw new AssertionError("0.1.61: "+s);}
 static void run()throws Exception{
  var countdown=new DoomspiralCountdown();long t=10000;
  check(countdown.message(DoomspiralCountdown.SUMMON,t),"real summon starts timer");
  check(countdown.text(t).equals("DOOMSPIRAL READY IN 6..."),"six-second initial text");
  check(!countdown.message("Something stirs in the Haunted Biome...",t+3000),"stirs does not restart countdown");
  check(!countdown.ready(t+5999)&&countdown.ready(t+6000),"earliest confirmed threshold");
  check(countdown.notifyOnce(t+6000)&&!countdown.notifyOnce(t+8000),"one ready sound");
  countdown.reset();countdown.message(DoomspiralCountdown.SUMMON,t);
  countdown.message(DoomspiralCountdown.SUMMON,t+1000);
  check(countdown.ready(t+6000),"duplicate summon does not restart timer");
  countdown.reset();countdown.message(DoomspiralCountdown.SUMMON,t);
  check(countdown.message("You threw a Masterful Critter Capsule at the Doomspiral!",t+5700)&&countdown.ready(t+5700),"real early throw proves ready");
  check(countdown.text(t+5700).equals("CATCH DOOMSPIRAL NOW"),"ready prompt");
  countdown.reset();check(!countdown.active(),"new run/capture reset");
  check(!countdown.message("Friend: "+DoomspiralCountdown.SUMMON,t),"typed summon rejected");
  check(!countdown.message("Party > Friend: You threw a Masterful Critter Capsule at the Doomspiral!",t),"typed throw rejected");
  check(IcyCaptureRules.isLocalCaptureMessage("§a§lCAPTURE! §7You caught a §6Doomspiral§7 and gained a §6Doomspiral Shard§7!","Doomspiral"),"real own capture accepted");
  check(!IcyCaptureRules.isLocalCaptureMessage("LOOT SHARE! Friend caught a Doomspiral!","Doomspiral"),"teammate not own capture");
  var points=SafariData.route(Biome.HAUNTED).hideyhoLocations();check(points.size()==20&&new HashSet<>(points).size()==20,"exact twenty fixed search points");
  var state=new HideyhoSearchState<Vec3>();state.begin(points);var first=state.target();state.reached();
  check(state.checked.size()==1&&!state.remaining().contains(first),"only current search target checked");
  state.stop(false);state.begin(points);check(state.remaining().size()==19&&!state.remaining().contains(first),"stop/restart skips already checked");
  var reverse=new ArrayList<>(state.remaining());Collections.reverse(reverse);state.order(reverse);
  check(state.target().equals(reverse.getFirst()),"optimized order applied without new locations");
  state.stop(true);check(state.found&&!state.active,"found ends overlay");state.reset();state.begin(points);check(state.remaining().size()==20&&!state.found,"new round resets search");
  check(HideyhoSearchSignals.found("[MOB] Hideyho: Aah! You found me!"),"real found line");
  check(HideyhoSearchSignals.reward("CAPTURE! You found the SPARKLING Hideyho, and gained a Hideyho Shard!"),"sparkling personal reward");
  check(!HideyhoSearchSignals.found("Party > Friend: [MOB] Hideyho: Aah! You found me!"),"typed found rejected");
  for(var p:points)if(SafariData.route(Biome.HAUNTED).floorDropChecks().contains(p))check(!SafariData.pureHideyhoSearch(p),"overlapping floor drop retained after found");
  Object savedStops=RecordingRegressionTests.field(RouteController.class,"stops"),savedNext=RecordingRegressionTests.field(RouteController.class,"nextStop"),savedActive=RecordingRegressionTests.field(RouteController.class,"active");
  try{
   var normal=List.of(new Vec3(1,2,3),new Vec3(4,5,6));RecordingRegressionTests.field(RouteController.class,"stops",normal);RecordingRegressionTests.field(RouteController.class,"nextStop",1);RecordingRegressionTests.field(RouteController.class,"active",true);
   HideyhoSearch.STATE.begin(points);HideyhoSearch.STATE.reached();
   check(RouteController.biome()==Biome.HAUNTED&&RouteController.active(),"overlay owns displayed route");
   check(RecordingRegressionTests.field(RouteController.class,"stops")==normal&&(int)RecordingRegressionTests.field(RouteController.class,"nextStop")==1,"normal cursor/order untouched by search checks");
   HideyhoSearch.STATE.stop(false);check(RouteController.stops()==normal&&RouteController.nextStop()==1,"normal display returns unchanged");
  }finally{HideyhoSearch.STATE.reset();RecordingRegressionTests.field(RouteController.class,"stops",savedStops);RecordingRegressionTests.field(RouteController.class,"nextStop",savedNext);RecordingRegressionTests.field(RouteController.class,"active",savedActive);}
  for(int gui=1;gui<=4;gui++)for(int[] pixels:List.of(new int[]{1920,1080},new int[]{1280,720},new int[]{800,600},new int[]{640,480})){
   int w=Math.max(320,pixels[0]/gui),h=Math.max(240,pixels[1]/gui);
   for(double fraction:new double[]{.2,.4,.6}){
    var chat=new TipPlacement.Rect(0,(int)(h*(1-fraction)),(int)(w*.55),(int)(h*fraction));
    var arrow=new TipPlacement.Rect((w-160)/2,10,160,38);var bar=new TipPlacement.Rect(0,h-80,w,40);
    var place=TipPlacement.place(w,h,Math.min(280,w-24),30,List.of(chat,arrow,bar));
    if(place!=null)check(!place.overlaps(chat)&&!place.overlaps(arrow)&&!place.overlaps(bar)&&place.x()>=0&&place.x()+place.w()<=w&&place.y()+place.h()<=h,"clear tip placement GUI "+gui);
   }
  }
  check(TipPlacement.place(320,240,280,30,List.of(new TipPlacement.Rect(0,0,320,240)))==null,"fullscreen obstruction safely suppresses instead of overlap");
  System.out.println("0.1.61 checks passed: real Doomspiral sequence/6-second estimate/early throw; twenty-point isolated Hideyho search, checked restart, trusted found messages, overlapping floors preserved; normal cursor unchanged; GUI1-4 tip obstacle placement. Patch60 verifies all normal route orders.");
 }
}

package dev.chipsxo.chipscritters;
import java.util.*;import net.minecraft.world.phys.Vec3;import com.google.gson.*;
final class Patch57RegressionTests {
 static void check(boolean x,String why){if(!x)throw new AssertionError(why);}
 static void run()throws Exception{
  for(String text:List.of("[MVP+] chipsxo entered Critter Safari!","[VIP] Friend entered Critter Safari!","[MVP++] Teammate entered Critter Safari!","NoRank entered Critter Safari!","[YOUTUBE] Friend entered Critter Safari!","-----------------------------\n[MVP+] chipsxo entered Critter Safari!\n-----------------------------"))check(SafariTripTips.entry(text),"server entry "+text);
  for(String text:List.of("Party > [MVP+] Alex: chipsxo entered Critter Safari!","[MVP+] Alex: [MVP+] chipsxo entered Critter Safari!","Guild > Alex: Friend entered Critter Safari!","From Friend: chipsxo entered Critter Safari!","[NPC] Alex: chipsxo entered Critter Safari!","chipsxo entered Hub!"))check(!SafariTripTips.entry(text),"typed or unrelated chat rejected");
  for(boolean safari:new boolean[]{false,true})for(boolean build:new boolean[]{false,true})for(boolean target:new boolean[]{false,true})check(RecordNextState.check(true,safari,build,target)!=RecordNextState.State.NO_ROUTE,"active routes never get inactive message");
  check(RecordNextState.check(true,true,true,false)==RecordNextState.State.WAIT_BUILD,"building queues recording without a target");
  check(RecordNextState.check(true,true,false,true)==RecordNextState.State.READY,"normal and connector targets record");
  check(RecordNextState.check(false,true,false,false)==RecordNextState.State.NO_ROUTE,"inactive route message truthful");
  var trip=new SafariTripTips();check(trip.begin("[MVP+] chipsxo entered Critter Safari!",1000),"trip starts");
  check(!trip.begin("[VIP] Friend entered Critter Safari!",5000),"party burst does not restart");check(trip.tipStep(7000)==1,"tip rotates six seconds from first entry");
  check(trip.active(9000),"burst still showing");trip.end();check(!trip.active(9001),"landing hides");check(!trip.begin("Friend entered Critter Safari!",10000),"once per trip");trip.newTrip();check(trip.begin("Friend entered Critter Safari!",20000),"next trip resets");
  check(SafariTripTips.landingMessage("[NPC] Safari Manager: I already saw your ticket, so you're free to go."),"observed landing message");
  check(!SafariTripTips.landingMessage("Party > Alex: HOTSPOT! Your Hunting Hotspot is the Forest Biome!"),"typed landing rejected");
  for(int size:new int[]{0,1,42})for(int cursor:new int[]{Integer.MIN_VALUE,-99,-1,0,1,42,Integer.MAX_VALUE}){
   int n=RouteCursor.next(cursor,size),b=RouteCursor.back(cursor,size);check(n>=0&&n<=size&&b>=0&&b<=size,"bounded cursor");
   String text=RouteStopCounter.text(true,cursor,size);check(!text.contains("-"),"nonnegative counter "+text);
  }
  check(RouteCursor.next(7,42)==8 && RouteCursor.back(8,42)==7,"exactly one route waypoint in normal and connector navigation");
  for(int gui=1;gui<=4;gui++)for(int window:new int[]{640,854,1280,1920})for(int textWidth:new int[]{30,80,180,300}){
   int screen=window/gui,panel=Math.min(screen-16,textWidth+42),fitted=Math.min(textWidth,panel-16);
   int x=WaypointPlacement.centerText(panel,fitted);check(Math.abs((x+fitted/2.0)-panel/2.0)<=.5,"text centered at GUI "+gui);
   int px=WaypointPlacement.x(screen,panel,-1);check(px>=0 && px+panel<=screen,"centered panel fits");
  }
  JsonObject baseline;try(var in=Patch57RegressionTests.class.getResourceAsStream("/routes56-preserved.json")){baseline=JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();}
  for(var b:List.of(Biome.CAVERN,Biome.FOREST))for(var mode:ChipsConfig.Mode.values()){
   var p=RoutePlanner.plan(new RoutePlanner.Input(b,mode,5,List.of(),false,false));var expected=baseline.getAsJsonArray(b+"/"+mode).deepCopy();if(b==Biome.CAVERN){for(int i=expected.size()-1;i>=0;i--)if((RecordedLegPaths.point(expected.get(i)).equals(new Vec3(-92,72,42))||RecordedLegPaths.point(expected.get(i)).equals(SafariData.CAVERN_LADDER_CAVE)))expected.remove(i);}check(p.size()==expected.size(),"preserved size "+b+mode);
   for(int i=0;i<p.size();i++)check(p.get(i).equals(RecordedLegPaths.point(expected.get(i))),"preserved order "+b+mode+" stop "+i);
  }
  for(var mode:List.of(ChipsConfig.Mode.MONEY,ChipsConfig.Mode.UNIQUE,ChipsConfig.Mode.FULL_CLEAR)){
   var p=RoutePlanner.plan(new RoutePlanner.Input(Biome.HAUNTED,mode,5,List.of(),false,false));for(var painting:SafariData.route(Biome.HAUNTED).hideonwallLocations()){
    check(p.contains(painting),"all eight paintings "+mode+painting);check(!RouteController.passedStopAllowed(Biome.HAUNTED,painting),"future painting protected");
    check(!WaypointRunLedger.sameRunStop(Biome.HAUNTED,painting,painting.add(0,-2,0)),"painting not aliased to floor/room");
   }
   check(p.indexOf(new Vec3(5,78,-64))<p.indexOf(new Vec3(7,78,-68)),"near room before far room "+mode);
  }
  check(SafariData.GIMMIEGOLD.equals(new Vec3(-2,59,-54)),"exact Gimmiegold coordinate");check(SafariTerrain.floor(Biome.HAUNTED,SafariData.GIMMIEGOLD).equals(SafariData.GIMMIEGOLD),"no displaced gold floor anchor");
  check(SafariData.CAVERN_DROP_START.equals(new Vec3(-94,82,55)) && SafariData.CAVERN_DROP_END.equals(new Vec3(-86,60,50)),"Chris fixed direct-drop coordinates");
  check(!RouteRenderer.isUnapprovedVerticalSegment(new Vec3(-94.5,77,37.5),new Vec3(-94.5,75.5,38)),"known open drop accepted, not a ladder stage");
  var oldState=new java.util.HashMap<String,Object>();
  for(String name:List.of("active","biome","stops","nextStop","lastMappedClipRepair","descentRepairAttempted","routePathRecheckPending","dropStage"))oldState.put(name,RecordingRegressionTests.field(RouteController.class,name));
  try{
   RecordingRegressionTests.field(RouteController.class,"active",true);RecordingRegressionTests.field(RouteController.class,"biome",Biome.CAVERN);
   RecordingRegressionTests.field(RouteController.class,"stops",List.of(SafariData.CAVERN_LADDER_CAVE));RecordingRegressionTests.field(RouteController.class,"nextStop",0);
   RecordingRegressionTests.field(RouteController.class,"dropStage",2);RecordingRegressionTests.field(RouteController.class,"lastMappedClipRepair",0L);RecordingRegressionTests.field(RouteController.class,"descentRepairAttempted",false);
   RouteController.requestMappedClipRepair();check((Boolean)RecordingRegressionTests.field(RouteController.class,"routePathRecheckPending"),"one descent repair allowed");
   RecordingRegressionTests.field(RouteController.class,"routePathRecheckPending",false);RecordingRegressionTests.field(RouteController.class,"lastMappedClipRepair",0L);
   RouteController.requestMappedClipRepair();check(!(Boolean)RecordingRegressionTests.field(RouteController.class,"routePathRecheckPending"),"blocked descent cannot rebuild in a loop");
  }finally{for(var entry:oldState.entrySet())RecordingRegressionTests.field(RouteController.class,entry.getKey(),entry.getValue());SafariGraph.get().resetDirectRouteChoices();}
  System.out.println("0.1.57 checks passed: preserved orders, eight paintings, room constraint, nonnegative cursor, GUI 1-4 math, trip messages/bursts and descent.");
 }
}

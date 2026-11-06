package dev.chipsxo.chipscritters;
import java.util.*;import java.nio.file.*;import net.minecraft.world.phys.Vec3;
final class Patch60RegressionTests {
 static void check(boolean ok,String text){if(!ok)throw new AssertionError("0.1.60: "+text);}
 static void run()throws Exception{
  String real="-----------------------------\n[MVP+] chipsxo entered Critter Safari!\n-----------------------------";
  check(SafariTripTips.ownEntry(real,"chipsxo"),"real own entry accepted");
  for(String s:List.of("[VIP] chipsxo entered Critter Safari!","chipsxo entered Critter Safari!","[MVP++] chipsxo entered Critter Safari!"))check(SafariTripTips.ownEntry(s,"chipsxo"),"rank/no rank own entry");
  check(!SafariTripTips.ownEntry("[MVP+] Friend entered Critter Safari!","chipsxo"),"party entry cannot start local tips");
  check(!SafariTripTips.ownEntry("Party > Friend: chipsxo entered Critter Safari!","chipsxo"),"typed text rejected");
  var trip=new SafariTripTips();long messageTime=10000;check(trip.begin(real,messageTime),"begin trip");
  check(trip.immediateVisibility(messageTime+16,true,true,true).equals("show"),"first frame eligible without camera, hidden-HUD, locraw or Safari confirmation");
  check(!trip.begin(real,messageTime+50)&&trip.elapsed(messageTime+60)==60,"announcement burst does not reset clock");
  check(trip.immediateVisibility(messageTime+20,false,true,true).equals("setting off"),"suppression reason");
  check(SafariTripTips.landingMessage("[NPC] Safari Manager: §fI already saw your ticket, so you're free to go."),"real landing ends trip");
  trip.end();check(!trip.immediateVisibility(messageTime+4000,true,true,true).equals("show"),"nothing after landing");
  trip.newTrip();trip.begin(real,messageTime);check(!trip.active(messageTime+60001),"timeout");
  check(SafariData.GIMMIEGOLD.equals(new Vec3(-2,59,-54)),"new exact Gold identity");
  check(SafariTerrain.floor(Biome.HAUNTED,SafariData.GIMMIEGOLD).equals(SafariData.GIMMIEGOLD),"Gold floor anchor uses new point");
  var cave=SafariData.route(Biome.CAVERN);check(cave.stops().size()==40&&!cave.stops().contains(SafariData.CAVERN_LADDER_CAVE),"travel-only Cavern stop removed, count 40");
  check(cave.stops().get(7).equals(SafariData.CAVERN_AFTER_DROP),"next real target is unchanged after removed cave guide");
  check(SafariData.CAVERN_DROP_START.equals(new Vec3(-94,82,55))&&SafariData.CAVERN_DROP_END.equals(new Vec3(-86,60,50)),"drop endpoints kept");
  check(SafariData.waypointInstruction(Biome.CAVERN,SafariData.CAVERN_LADDER_CAVE)==null,"removed stop has no misleading DROP DOWN label");
  Object prior=RecordingRegressionTests.field(RouteController.class,"dropStage");try{
   RecordingRegressionTests.field(RouteController.class,"dropStage",1);check(RouteController.dropInstruction().equals("GO TO DROP EDGE"),"approach with positive deltaY is not called drop");
   RecordingRegressionTests.field(RouteController.class,"dropStage",2);check(RouteController.dropInstruction().equals("DROP DOWN"),"actual fall label");
  }finally{RecordingRegressionTests.field(RouteController.class,"dropStage",prior);}
  for(int gui=1;gui<=4;gui++)for(int[] size:List.of(new int[]{1920,1080},new int[]{1280,720},new int[]{800,600},new int[]{640,480})){
   int w=Math.max(240,size[0]/gui),h=Math.max(160,size[1]/gui),top=UiViewport.top(h,false),bottom=h-40,content=14*26;
   check(UiViewport.panelWidth(w)<=w-8,"panel fits GUI "+gui);
   double sx=UiViewport.horizontalScale(w-40,370);check(370*sx<=w-40,"wide relay/learned rows fit GUI "+gui);
   for(int row=0;row<14;row++){
    int natural=row*26,scroll=UiViewport.clampScroll(natural,content,bottom-top);
    check(UiViewport.visible(top+natural-scroll,22,top,bottom),"every setting reachable without shrinking text at GUI "+gui);
   }
   int confirmTop=UiViewport.top(h,true);check(bottom-confirmTop>=20,"confirmation controls remain reachable in small windows");
  }
  for(Class<?> screen:List.of(ChipsSettingsScreen.class,ChipsHudPositionScreen.class,PersonalBestsScreen.class,ConfirmClaimsScreen.class,ConfirmResetScreen.class,RelaySettingsScreen.class,LearnedStopsScreen.class))check(screen.getSuperclass().equals(ChipsScreen.class),"shared UI shell "+screen.getSimpleName());
  var baseline=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(Patch60RegressionTests.class.getResourceAsStream("/routes59-preserved.json")),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
  for(var b:Biome.values())for(var m:ChipsConfig.Mode.values()){
   var expected=new ArrayList<Vec3>();for(var el:baseline.getAsJsonArray(b+"/"+m)){var p=RecordedLegPaths.point(el);if(b==Biome.CAVERN&&p.equals(SafariData.CAVERN_LADDER_CAVE))continue;expected.add(b==Biome.HAUNTED&&p.equals(new Vec3(3,60,-55))?SafariData.GIMMIEGOLD:p);}
   check(RoutePlanner.plan(new RoutePlanner.Input(b,m,5,List.of(),false,false)).equals(expected),"only authorized coordinate/removal changes "+b+"/"+m);
  }
  System.out.println("0.1.60 checks passed: own-entry immediate tips, landing/timeout suppression, new Gold point, 40 Cavern stops with fixed drop, GUI 1-4 scroll/fit and shared screen design; other route orders unchanged.");
 }
}

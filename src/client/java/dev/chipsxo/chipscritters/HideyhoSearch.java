package dev.chipsxo.chipscritters;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
/** Temporary guidance overlay; it never edits normal stops, their cursor, clock or reached ledger. */
final class HideyhoSearch {
 static final HideyhoSearchState<Vec3> STATE=new HideyhoSearchState<>();
 private static List<Vec3> line=List.of();private static Set<Integer> gaps=Set.of();
 private static long serial,lastRepair;
    private static final Set<String> loggedGaps=new HashSet<>();private static boolean building,paused;private static Vec3 anchor,origin;
 static boolean active(){return STATE.active;}
 static boolean paused(){return paused;}
 static boolean building(){return building;}
 static Vec3 target(){return building?null:STATE.target();}
 static List<Vec3> stops(){return STATE.remaining();}
 static List<Vec3> line(){return line;}
 static boolean gap(int at){return gaps.contains(at);}
 static boolean gaps(){return !gaps.isEmpty();}
 static Vec3 origin(){return origin;}
 static void start(){
  var c=Minecraft.getInstance();if(!ChipsCrittersClient.isInCritterSafari()||c.player==null){RouteController.commandFeedback("Enter Critter Safari before /sr find hideyho.");return;}
  if(STATE.found){RouteController.commandFeedback("Hideyho was already found this round. Start a new Hideyho round to search again.");return;}
  STATE.begin(SafariData.route(Biome.HAUNTED).hideyhoLocations());
  if(!active()){silentStop();RouteController.resumeAfterSearch(false);RouteController.commandFeedback("All 20 Hideyho spots are already checked this round.");return;}
  WaypointRelay.pauseSearchClaims(true);RecordedLegPaths.clearSelection();HideyhoFinderPause.beginManualSearch();paused=false;building=true;line=List.of();gaps=Set.of();anchor=origin=c.player.position();long version=++serial;var level=c.level;String lobby=WaypointRelay.lobbySessionKey();
  RoutePlanService.search(stops(),origin).whenComplete((order,error)->c.execute(()->{
   if(version!=serial||!active())return;building=false;
   if(error!=null){RouteController.commandFeedback("Hideyho search planning failed; normal route kept.");stop(false,true);return;}
   if(c.level!=level||!lobby.equals(WaypointRelay.lobbySessionKey())||!ChipsCrittersClient.isInCritterSafari()){silentStop();return;}
   STATE.order(order);refresh();
  }));
  RouteController.commandFeedback("HIDEYHO SEARCH · "+stops().size()+" spots left. /sr find stop returns to your normal route.");
 }
 static void tick(Minecraft c){
  if(!active()||building||paused||c.player==null)return;
  if(target()==null){stop(false,true);RouteController.commandFeedback("All 20 Hideyho spots checked; Hideyho not found.");return;}
  Vec3 player=c.player.position(),goal=SafariGraph.get().navigationTarget(Biome.HAUNTED,target()).add(.5,0,.5);
  if(Math.abs(player.y-goal.y)<=2&&player.distanceTo(goal)<=2){
   origin=target();ChipsCrittersClient.LOGGER.info("Hideyho manual search checked: {}; no route ledger/relay claim",target());STATE.reached();refresh();return;
  }
  if(anchor==null||player.distanceToSqr(anchor)>6.25)refresh();
 }
 static void noteGap(int at,Vec3 from,Vec3 to,String reason){
        var updated=new HashSet<>(gaps);updated.add(at);gaps=Set.copyOf(updated);
        if(loggedGaps.add(target()+":"+reason))ChipsCrittersClient.LOGGER.info("Hideyho search line gap: target={} from={} to={} reason={}",target(),from,to,reason);
    }
    static void repair(){if(System.currentTimeMillis()-lastRepair<1000)return;lastRepair=System.currentTimeMillis();SafariGraph.get().requestMappedRepair();refresh();}
    static void refresh(){var c=Minecraft.getInstance();if(!active()||paused||building||c.player==null)return;
  if(target()==null){line=List.of();gaps=Set.of();return;}anchor=c.player.position();
  var result=SafariGraph.get().routeLineResultFrom(Biome.HAUNTED,anchor,List.of(target()),STATE.checked.size()+1);line=result.points();gaps=result.gaps();
 }
 static void skip(){if(!active())return;if(building){RouteController.commandFeedback("Hideyho search is building.");return;}STATE.reached();refresh();RouteController.commandFeedback("HIDEYHO SEARCH · "+stops().size()+" spots left; no route claim sent.");}
 static void pause(boolean value){paused=value;if(value)line=List.of();else refresh();}
 static void stop(boolean found,boolean feedback){
  if(!active()){if(feedback)RouteController.commandFeedback("No active Hideyho search.");return;}
  STATE.stop(found);WaypointRelay.pauseSearchClaims(false);serial++;building=paused=false;line=List.of();gaps=Set.of();
  RouteController.resumeAfterSearch(found);if(feedback)RouteController.commandFeedback(found?"Hideyho found; normal route resumed.":"Hideyho search stopped; normal route resumed.");
 }
 static void silentStop(){WaypointRelay.pauseSearchClaims(false);STATE.stop(false);serial++;building=paused=false;line=List.of();gaps=Set.of();}
 static void newRound(){silentStop();STATE.reset();RouteController.clearManualFindRewardGuard();}
}

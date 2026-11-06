package dev.chipsxo.chipscritters;
import java.util.*;
import net.minecraft.world.phys.Vec3;
final class HauntedRoomOrder {
 static final Vec3 ROOM=new Vec3(7,78,-68);
 static double length(List<Vec3> route){double n=0;for(int i=1;i<route.size();i++)n+=SafariGraph.get().travelDistance(Biome.HAUNTED,route.get(i-1),route.get(i));return n;}
 static List<Vec3> paintingRoom(List<Vec3> route){
  Vec3 painting=new Vec3(6,79,-83);if(!route.contains(painting))return route;
  var group=new ArrayList<Vec3>();if(route.contains(SafariData.HAUNTED_ENTRANCE))group.add(SafariData.HAUNTED_ENTRANCE);group.add(painting);
  for(Vec3 p:route)if(SafariData.isGazerBedWaypoint(Biome.HAUNTED,p)&&p.x>0)group.add(p);
  for(Vec3 p:route)if(SafariData.isHideyhoWaypoint(Biome.HAUNTED,p)&&p.y>=76&&p.y<=81&&p.distanceTo(painting)<=7&&!group.contains(p)&&!p.equals(ROOM))group.add(p);
  var result=new ArrayList<>(route);int first=route.indexOf(group.getFirst());result.removeAll(group);
  int after=result.indexOf(ROOM);int at=after>=0?after+1:Math.max(1,Math.min(first,result.size()));
  result.addAll(at,group);return List.copyOf(result);
 }
 static List<Vec3> order(List<Vec3> route){
  int at=route.indexOf(ROOM);if(at<1)return route;
  Vec3 previous=route.get(at-1);
  // Existing east ground-floor house rooms only; preserve the separate entrance/door and attic groups.
  var houses=SafariData.route(Biome.HAUNTED).houseSweep();
  Vec3 candidate=route.subList(at+1,route.size()).stream().filter(p->houses.contains(p)&&p.y==78&&p.x>0&&!p.equals(SafariData.HAUNTED_ENTRANCE))
   .min(Comparator.comparingDouble(p->SafariGraph.get().travelDistance(Biome.HAUNTED,previous,p))).orElse(null);
  if(candidate==null)return route;
  var moved=new ArrayList<>(route);moved.remove(candidate);moved.add(at,candidate);
  var swapped=new ArrayList<>(route);Collections.swap(swapped,at,route.indexOf(candidate));
  List<Vec3> best=length(moved)<=length(swapped)?moved:swapped;
  // If the requested local change creates a longer detour, retain the known route and report it.
  // Chris selected closest-room-first; choose the least additional travel under that constraint.
  ChipsCrittersClient.LOGGER.info("Haunted room order: previous={}, beforeRoom={}, room={}, oldLength={}, newLength={}",previous,candidate,ROOM,length(route),length(best));
  return List.copyOf(best);
 }
}

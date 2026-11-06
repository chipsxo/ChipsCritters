package dev.chipsxo.chipscritters;
import java.util.*;
import java.util.function.BiPredicate;
import net.minecraft.world.phys.Vec3;
/** Geometry restricted to Chris's fixed Cavern DROP connector, never route-stop planning. */
final class DropRibbonPath {
 static List<Vec3> flight(Vec3 start,Vec3 end,BiPredicate<Vec3,Vec3> clear){
  int n=Math.max(2,(int)Math.ceil(start.distanceTo(end)/.20));
  // Front-load lateral travel rather than cutting diagonally through the rock face.
  for(double power:new double[]{2,3,4,6,1}){
   var points=new ArrayList<Vec3>();points.add(start);boolean valid=true;
   for(int i=1;i<=n;i++){double t=(double)i/n,u=1-Math.pow(1-t,power);var p=new Vec3(start.x+(end.x-start.x)*u,start.y+(end.y-start.y)*t,start.z+(end.z-start.z)*u);
    if(!clear.test(points.getLast(),p)){valid=false;break;}points.add(p);}
   if(valid)return List.copyOf(points);
  }return List.of();
 }
 static List<Vec3> approach(List<Vec3> source,BiPredicate<Vec3,Vec3> clear){
  if(source.size()<2)return source;var sampled=new ArrayList<Vec3>();sampled.add(source.getFirst());
  for(int i=1;i<source.size();i++){var a=source.get(i-1);var b=source.get(i);int n=Math.max(1,(int)Math.ceil(a.distanceTo(b)/.25));for(int j=1;j<=n;j++)sampled.add(a.lerp(b,(double)j/n));}
  // Correct local samples, then validate the actual joined edges; do not share a segment's
  // maximum lift with both neighbours, which can push a clear edge into an overhang.
  var result=new ArrayList<Vec3>();
  for(var p:sampled){Vec3 choice=null;for(int k=0;k<=15;k++){var q=p.add(0,k*.1,0);if(clear.test(q,q)&&(result.isEmpty()||clear.test(result.getLast(),q))){choice=q;break;}}
   if(choice==null)return List.of();result.add(choice);}
  return List.copyOf(result);
 }
 static List<Vec3> combine(List<Vec3> approach,List<Vec3> flight){
  if(approach.isEmpty())return List.of();if(flight.isEmpty())return approach;
  if(approach.getLast().distanceToSqr(flight.getFirst())>.0001)return List.of();
  var points=new ArrayList<>(approach);points.addAll(flight.subList(1,flight.size()));return List.copyOf(points);
 }
}

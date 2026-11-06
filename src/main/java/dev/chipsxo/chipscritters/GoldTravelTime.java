package dev.chipsxo.chipscritters;
import java.util.*;import net.minecraft.world.phys.Vec3;
/** Coin-insertion cost model only. Reference rates are estimates, not calibrated server timings. */
final class GoldTravelTime {
 static final double WALK_BLOCKS_PER_SECOND=4.3,CLIMB_BLOCKS_PER_SECOND=2.35;
 static double seconds(List<Vec3> points){
  double total=0;for(int i=1;i<points.size();){var a=points.get(i-1);var b=points.get(i);double dy=b.y-a.y,h=Math.hypot(b.x-a.x,b.z-a.z);
   if(dy<0&&-dy>h){double vertical=0,horizontal=0;do{a=points.get(i-1);b=points.get(i);dy=b.y-a.y;h=Math.hypot(b.x-a.x,b.z-a.z);if(!(dy<0&&-dy>h))break;vertical-=dy;horizontal+=h;i++;}while(i<points.size());total+=Math.max(fallSeconds(vertical),horizontal/WALK_BLOCKS_PER_SECOND);}
   else{total+=dy>h?Math.max(dy/CLIMB_BLOCKS_PER_SECOND,h/WALK_BLOCKS_PER_SECOND):a.distanceTo(b)/WALK_BLOCKS_PER_SECOND;i++;}
  }return total;
 }
 static double fallSeconds(double distance){double y=0,velocity=0;int ticks=0;while(y<distance&&ticks<1000){velocity=(velocity+.08)*.98;y+=velocity;ticks++;}return ticks/20.0;}
 static double length(List<Vec3> points){double n=0;for(int i=1;i<points.size();i++)n+=points.get(i-1).distanceTo(points.get(i));return n;}
}

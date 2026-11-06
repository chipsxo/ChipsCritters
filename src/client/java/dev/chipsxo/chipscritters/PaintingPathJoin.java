package dev.chipsxo.chipscritters;

import java.util.*;
import java.util.function.*;
import net.minecraft.world.phys.Vec3;

/** Joins an already-entered room at its nearest reachable forward suffix, without replaying the entrance. */
final class PaintingPathJoin {
    static List<Vec3> join(Vec3 from, List<Vec3> tail, List<Vec3> mapped,
                          BiPredicate<Vec3,Vec3> walkable) {
        List<Vec3> best = mapped;
        double cost = length(best);
        for (int at=0; at<tail.size(); at++) {
            Vec3 target=tail.get(at);
            if (!walkable.test(from,target)) continue;
            boolean safe=true;
            for(int j=at+1;j<tail.size();j++)if(!walkable.test(tail.get(j-1),tail.get(j))){safe=false;break;}
            if(!safe)continue;
            var candidate=new ArrayList<Vec3>();candidate.add(from);candidate.addAll(tail.subList(at,tail.size()));
            if(length(candidate)<cost){best=List.copyOf(candidate);cost=length(candidate);}
        }
        if(best.isEmpty())return best;
        best=eraseLoops(best);
        // Remove only shortcuts proven walkable, never a collision-only diagonal through a wall/gap.
        var result=new ArrayList<Vec3>();int at=0;result.add(best.getFirst());
        while(at<best.size()-1){
            int next=at+1;
            for(int j=best.size()-1;j>next;j--)if(walkable.test(best.get(at),best.get(j))){next=j;break;}
            if(result.getLast().distanceToSqr(best.get(next))>.0001)result.add(best.get(next));at=next;
        }
        return List.copyOf(result);
    }
    /** Erasing a closed loop uses existing validated segments; it never invents a wall-crossing chord. */
    static List<Vec3> eraseLoops(List<Vec3> input){
        var path=new ArrayList<Vec3>();
        for(Vec3 point:input){
            int repeat=-1;for(int i=0;i<path.size();i++)if(path.get(i).distanceToSqr(point)<1e-10){repeat=i;break;}
            if(repeat>=0)path.subList(repeat+1,path.size()).clear();else path.add(point);
        }
        for(int pass=0;pass<input.size();pass++){
            boolean cut=false;
            outer:for(int i=0;i+1<path.size();i++)for(int j=i+2;j+1<path.size();j++){
                Vec3 a=path.get(i),b=path.get(i+1),c=path.get(j),d=path.get(j+1);
                double dx=b.x-a.x,dz=b.z-a.z,ex=d.x-c.x,ez=d.z-c.z,det=dx*ez-dz*ex;
                if(Math.abs(det)<1e-9)continue;
                double t=((c.x-a.x)*ez-(c.z-a.z)*ex)/det;
                double u=((c.x-a.x)*dz-(c.z-a.z)*dx)/det;
                if(t<0||t>1||u<0||u>1)continue;
                Vec3 hit=a.lerp(b,t);if(hit.distanceToSqr(c.lerp(d,u))>1e-10)continue;
                var shortened=new ArrayList<>(path.subList(0,i+1));shortened.add(hit);shortened.addAll(path.subList(j+1,path.size()));
                path=shortened;cut=true;break outer;
            }
            if(!cut)break;
        }
        return List.copyOf(path);
    }
    static double length(List<Vec3> p){if(p.isEmpty())return Double.POSITIVE_INFINITY;double n=0;for(int i=1;i<p.size();i++)n+=p.get(i-1).distanceTo(p.get(i));return n;}
}

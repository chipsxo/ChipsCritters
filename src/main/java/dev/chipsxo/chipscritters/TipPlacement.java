package dev.chipsxo.chipscritters;
import java.util.*;
final class TipPlacement {
 record Rect(int x,int y,int w,int h){boolean overlaps(Rect b){return b!=null&&x<b.x+b.w&&x+w>b.x&&y<b.y+b.h&&y+h>b.y;}}
 static Rect bar(int sw,int sh,int h,List<Rect> avoid){
  int preferred=Math.max(0,sh/4);var ys=new ArrayList<Integer>();ys.add(preferred);ys.add(8);
  for(var rect:avoid){ys.add(rect.y-h-4);ys.add(rect.y+rect.h+4);}
  return ys.stream().distinct().filter(y->y>=6&&y+h<=sh-6).map(y->new Rect(0,y,sw,h))
   .filter(r->avoid.stream().noneMatch(r::overlaps)).min(Comparator.comparingInt(r->Math.abs(r.y-preferred))).orElse(null);
 }
 static Rect place(int sw,int sh,int w,int h,List<Rect> avoid){
  for(int y:new int[]{sh/4,sh/6,sh/3,8})for(int x:new int[]{(sw-w)/2,sw-w-8,8}){
   Rect candidate=new Rect(Math.max(6,x),Math.max(6,y),w,h);
   if(candidate.x+w>sw-6||candidate.y+h>sh-6)continue;
   if(avoid.stream().noneMatch(candidate::overlaps))return candidate;
  }return null;
 }
}

package dev.chipsxo.chipscritters;
final class RouteCursor {
 private RouteCursor(){}
 static int clamp(int index,int count){return Math.max(0,Math.min(index,Math.max(0,count)));}
 static int next(int index,int count){return (int)Math.min(Math.max(0L,count),(long)clamp(index,count)+1);}
 static int back(int index,int count){return Math.max(0,clamp(index,count)-1);}
}

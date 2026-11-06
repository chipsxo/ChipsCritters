package dev.chipsxo.chipscritters;
/** Logical GUI units; scroll preserves readable control heights at small windows/scales. */
final class UiViewport {
 static int panelWidth(int width){return Math.max(80,Math.min(520,width-16));}
 static int clampScroll(int value,int content,int visible){return Math.max(0,Math.min(value,Math.max(0,content-visible)));}
 static int clampX(int x,int w,int screen){return Math.max(4,Math.min(x,Math.max(4,screen-w-4)));}
 static boolean visible(int y,int h,int top,int bottom){return y>=top&&y+h<=bottom;}
 static double horizontalScale(int width,int span){return Math.min(1,Math.max(1,width)/(double)Math.max(1,span));}
 static int top(int height,boolean confirmation){return Math.min(confirmation?106:76,Math.max(60,height-80));}
}

package dev.chipsxo.chipscritters;
/** Local vertical fraction of the usable screen; -1 preserves the existing automatic placement. */
final class BannerPosition {
 static double sanitize(double value){return !Double.isFinite(value)||value<0?-1:Math.min(1,value);}
 static int y(double fraction,int screenHeight,int barHeight){return (int)Math.round(Math.max(0,screenHeight-barHeight)*Math.max(0,sanitize(fraction)));}
 static double fraction(int y,int screenHeight,int barHeight){int available=Math.max(0,screenHeight-barHeight);return available==0?0:Math.max(0,Math.min(1,(double)y/available));}
}

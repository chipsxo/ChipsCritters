package dev.chipsxo.chipscritters;
/** Three-block-wide approach around a fixed ladder cell, limited to two blocks in height. */
public final class LadderReachRule {
 private LadderReachRule(){}
 public static boolean reached(double playerX,double playerY,double playerZ,double blockX,double blockY,double blockZ){
  return reached(playerX,playerY,playerZ,blockX,blockY,blockZ,1.5);
 }
 public static boolean reached(double playerX,double playerY,double playerZ,double blockX,double blockY,double blockZ,double halfWidth){
  return Math.abs(playerX-(blockX+.5))<=halfWidth && Math.abs(playerZ-(blockZ+.5))<=halfWidth
   && Math.abs(playerY-blockY)<=2.0;
 }
}

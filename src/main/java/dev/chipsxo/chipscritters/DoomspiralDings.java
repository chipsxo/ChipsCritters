package dev.chipsxo.chipscritters;
/** Client-ticked pair, no sleeping or background sound calls. */
final class DoomspiralDings {
 static final long SECOND_DELAY_MILLIS=130;
 private long secondAt=-1;
 int tick(long now,boolean active,boolean readyOnce,boolean enabled){
  if(!active){reset();return 0;}
  if(readyOnce){secondAt=enabled?now+SECOND_DELAY_MILLIS:-1;return enabled?1:0;}
  if(secondAt>=0&&now>=secondAt){secondAt=-1;return enabled?1:0;}return 0;
 }
 void reset(){secondAt=-1;}
}

package dev.chipsxo.chipscritters;
import java.util.regex.Pattern;
/** Six-second countdown requested for the release candidate; own throw proves readiness sooner. */
final class DoomspiralCountdown {
 static final long READY_DELAY_MILLIS=6000;
 static final String SUMMON="Your ritual summoned a Doomspiral into this world. Stay still.";
 private static final Pattern THROW=Pattern.compile("^You threw (?:a|an) .+ at the Doomspiral!$");
 private long started=-1;private boolean proven,notified;
 boolean message(String raw,long now){if(raw==null)return false;String text=raw.replaceAll("§.","").strip();
  if(text.equals(SUMMON)){if(started<0){started=now;proven=notified=false;}return true;}
  if(THROW.matcher(text).matches()){if(started<0)started=now;proven=true;return true;}
  return false;
 }
 boolean active(){return started>=0;}
 boolean ready(long now){return active()&&(proven||now-started>=READY_DELAY_MILLIS);}
 String text(long now){return !active()?"":ready(now)?"CATCH DOOMSPIRAL NOW":"DOOMSPIRAL READY IN "+Math.max(1,(READY_DELAY_MILLIS-(now-started)+999)/1000)+"...";}
 boolean notifyOnce(long now){if(!ready(now)||notified)return false;notified=true;return true;}
 void reset(){started=-1;proven=notified=false;}
}

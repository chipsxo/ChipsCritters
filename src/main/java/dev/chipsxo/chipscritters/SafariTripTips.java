package dev.chipsxo.chipscritters;
import java.util.regex.Pattern;
/** Recognizes only the server's whole Safari-entry announcement, never ordinary player chat. */
final class SafariTripTips {
 private static final Pattern ENTRY=Pattern.compile("^(?:\\[[^\\]\\r\\n:]{1,30}\\]\\s*)*[A-Za-z0-9_]{1,16} entered Critter Safari!$");
 private long started=-1;private boolean finished;private String endedReason="inactive / no own entry";
 static boolean entry(String raw){
  if(raw==null)return false;boolean found=false;
  for(String line:raw.replaceAll("§.","").replace("\\n","\n").strip().split("\\R")){
   line=line.strip();if(line.isEmpty()||line.matches("-{3,}"))continue;
   if(!ENTRY.matcher(line).matches())return false;found=true;
  }return found;
 }
 boolean begin(String text,long now){if(finished||started>=0||!entry(text))return false;started=now;return true;}
 void end(){end("ended");}
 void end(String reason){if(started>=0){started=-1;finished=true;endedReason=reason;}}
 void newTrip(){started=-1;finished=false;endedReason="inactive / no own entry";}
 boolean active(long now){if(started>=0&&now-started>60000)end("timeout");return started>=0;}
 static boolean ownEntry(String raw,String name){
  if(name==null||!entry(raw))return false;
  String wanted=(name+" entered Critter Safari!").toLowerCase(java.util.Locale.ROOT);
  return raw.replaceAll("§.","").replace("\\n","\n").lines().map(line->line.strip().toLowerCase(java.util.Locale.ROOT)).anyMatch(line->line.equals(wanted)||line.endsWith(" "+wanted));
 }
 String immediateVisibility(long now,boolean enabled,boolean hypixel,boolean player){
  if(!active(now))return endedReason;
  if(!enabled)return "setting off";
  if(!hypixel){end();return "not Hypixel";}
  if(!player)return "player unavailable";
  return "show";
 }
 long elapsed(long now){return started<0?0:Math.max(0,now-started);}
 int tipStep(long now){return started<0?0:(int)Math.max(0,(now-started)/6000);}
 static boolean landingMessage(String text){if(text==null)return false;text=text.replaceAll("§.","").strip();return text.startsWith("HOTSPOT! Your Hunting Hotspot is the ")||text.equals("[NPC] Safari Manager: I already saw your ticket, so you're free to go.")||text.equals("[NPC] Safari Manager: Looks good to me. Have fun out there!");}
}

package dev.chipsxo.chipscritters;
import java.util.Locale;
final class HideyhoSearchSignals {
 static boolean found(String raw){if(raw==null)return false;String s=raw.replaceAll("§.","").strip().toLowerCase(Locale.ROOT);
  return s.equals("[mob] hideyho: aah! you found me!")||s.equals("[skyhanni] found hideyho!")||reward(raw);
 }
 static boolean reward(String raw){return raw!=null&&raw.replaceAll("§.","").strip().matches("(?i)^CAPTURE! You found the (?:SPARKLING )?Hideyho,.*!$");}
}

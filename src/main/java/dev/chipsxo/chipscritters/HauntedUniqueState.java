package dev.chipsxo.chipscritters;

import java.util.*;
import java.util.regex.Pattern;

/** Only whole personal capture messages count; relay claims and loot share never call capture. */
final class HauntedUniqueState {
    static final Set<String> HOUSE=Set.of("areita","bloodbat","duplico","gazer","litterbug","hideonwall");
    private static final Pattern OWN=Pattern.compile("^CAPTURE! You caught (?:a|an) (?:SPARKLING )?(Areita|Bloodbat|Duplico|Gazer|Litterbug|Hideonwall) and (?:gained|received) .+!$",Pattern.CASE_INSENSITIVE);
    final Set<String> caught=new HashSet<>();
    boolean earlyGold, doomRoute;
    boolean capture(String raw){if(raw==null)return false;var m=OWN.matcher(raw.replaceAll("§.","").strip());return m.matches()&&caught.add(m.group(1).toLowerCase(Locale.ROOT));}
    boolean houseDone(boolean paintings){return HOUSE.stream().filter(n->paintings||!n.equals("hideonwall")).allMatch(caught::contains);}
    boolean canRouteDoom(boolean paintings,int incense){return incense>=3&&houseDone(paintings);}
    void reset(){caught.clear();earlyGold=doomRoute=false;}
}

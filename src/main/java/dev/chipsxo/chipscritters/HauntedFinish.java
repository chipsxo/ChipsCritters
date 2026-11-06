package dev.chipsxo.chipscritters;

import java.util.regex.Pattern;

/** Personal catches; deliberately excludes player chat and loot-share notices. */
final class HauntedFinish {
    private static final Pattern OWN = Pattern.compile("^CAPTURE! You caught (?:a|an) (?:SPARKLING )?(Doomspiral|Gimmiegold) and (?:gained|received) [^\\r\\n]*\\b\\1 Shard!(?:\\s.*)?$",Pattern.CASE_INSENSITIVE);
    boolean doom, gold, finished, replay;
    long started;
    private final java.util.Map<String,Integer> totals=new java.util.HashMap<>();
    void rememberTotal(String mode,int count){totals.merge(mode,Math.max(0,count),Math::max);}
    int total(String mode,int fallback){return Math.max(Math.max(0,fallback),totals.getOrDefault(mode,0));}
    boolean capture(String message) {
        if (finished || message == null) return false;
        var match=OWN.matcher(message.replaceAll("§.","").strip());
        if (!match.matches()) return false;
        if (match.group(1).equalsIgnoreCase("Doomspiral")) { if(doom)return false; doom=true; }
        else { if(gold)return false; gold=true; }
        return true;
    }
    boolean finishOnce() {
        if(finished || !doom || !gold)return false;
        finished=true; return true;
    }
    void reset(){started=0;doom=gold=finished=replay=false;totals.clear();}
}

package dev.chipsxo.chipscritters;
import java.util.EnumSet;
import java.util.Set;
/** One party DONE decision per biome for each Safari run. */
final class PartyDoneLedger {
 private final Set<Biome> sent=EnumSet.noneOf(Biome.class);
 boolean shouldSend(Biome biome,boolean enabled,boolean connected,boolean inParty) {
  return PartyClaimFlow.shouldSendDone(enabled,connected,inParty) && biome!=null && sent.add(biome);
 }
 void newRun(){sent.clear();}
 void clear(){sent.clear();}
}

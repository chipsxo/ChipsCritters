package dev.chipsxo.chipscritters;
import java.util.*;
/** Search checks are deliberately separate from route reached/relay/capture ledgers. */
final class HideyhoSearchState<T> {
 final Set<T> checked=new HashSet<>();private List<T> pending=List.of();boolean active,found;
 void begin(List<T> points){pending=points.stream().filter(p->!checked.contains(p)).distinct().toList();active=!pending.isEmpty();}
 List<T> remaining(){return pending;}
 void order(List<T> points){pending=points.stream().filter(p->pending.contains(p)&&!checked.contains(p)).distinct().toList();}
 T target(){return active&&!pending.isEmpty()?pending.getFirst():null;}
 void reached(){if(target()==null)return;checked.add(target());pending=List.copyOf(pending.subList(1,pending.size()));}
 void stop(boolean found){this.found|=found;active=false;pending=List.of();}
 void reset(){checked.clear();pending=List.of();active=found=false;}
}

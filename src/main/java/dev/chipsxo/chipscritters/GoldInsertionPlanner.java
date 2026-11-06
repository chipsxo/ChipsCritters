package dev.chipsxo.chipscritters;
import java.util.*;import java.util.function.*;import net.minecraft.world.phys.Vec3;
/** Inserts exactly one fixed Gold identity without reordering any other pending stops. */
final class GoldInsertionPlanner {
 record Option(int index,Vec3 before,Vec3 after,double added){}
 record Result(List<Vec3> order,Option best,Option runnerUp){}
 static List<Vec3> base(List<Vec3> pending,Vec3 gold){return pending.stream().filter(p->!p.equals(gold)).toList();}
 static Result choose(List<Vec3> pending,Vec3 origin,Vec3 gold,Predicate<Vec3> doom,Predicate<Vec3> gate,ToDoubleBiFunction<Vec3,Vec3> routed){
  if(!pending.contains(gold))return new Result(List.copyOf(pending),null,null);
  var base=base(pending,gold);var options=new ArrayList<Option>();int end=base.size();for(int i=0;i<base.size();i++)if(doom.test(base.get(i))){end=i;break;}
  for(int i=0;i<=end;i++){
   if(i==0&&!base.isEmpty()&&gate.test(base.getFirst()))continue;
   Vec3 before=i==0?origin:base.get(i-1),after=i<base.size()?base.get(i):null;
   double in=routed.applyAsDouble(before,gold),out=after==null?0:routed.applyAsDouble(gold,after),removed=after==null?0:routed.applyAsDouble(before,after);
   if(!Double.isFinite(in)||!Double.isFinite(out)||!Double.isFinite(removed))continue;
   options.add(new Option(i,before,after,in+out-removed));
  }
  options.sort(Comparator.comparingDouble(Option::added).thenComparingInt(Option::index));
  if(options.isEmpty())return new Result(List.copyOf(pending),null,null);
  var best=options.getFirst();var order=new ArrayList<>(base);order.add(best.index,gold);
  return new Result(List.copyOf(order),best,options.size()>1?options.get(1):null);
 }
}

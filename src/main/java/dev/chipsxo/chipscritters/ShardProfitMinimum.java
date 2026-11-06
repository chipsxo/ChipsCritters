package dev.chipsxo.chipscritters;
/** Absolute per-shard value difference, separate from the existing percentage filter. */
public final class ShardProfitMinimum {
 public static final double DEFAULT_COINS=1000;
 private ShardProfitMinimum(){}
 public static double sanitize(double value){return Double.isFinite(value)&&value>=0?value:DEFAULT_COINS;}
 public static boolean applyDefault(com.google.gson.JsonObject config){
  var value=config.get("minimumShardAdvantageCoins");
  if(value!=null&&value.isJsonPrimitive()&&value.getAsJsonPrimitive().isNumber()){
   double n=value.getAsDouble();if(Double.isFinite(n)&&n>=0)return false;
  }config.addProperty("minimumShardAdvantageCoins",DEFAULT_COINS);return true;
 }
 public static boolean meets(double sell,double salvage,double minimum){double difference=Math.abs(sell-salvage);return difference+1.0e-7>=sanitize(minimum);}
}

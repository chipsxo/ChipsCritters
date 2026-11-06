package dev.chipsxo.chipscritters;

/** Positive means sell, negative salvage, zero means no reliable advantage. */
public final class ShardValueRule {
    private ShardValueRule() { }
    public static int compare(double offer, double salvage, double minimumPercent) {
        if (!Double.isFinite(offer) || !Double.isFinite(salvage) || offer <= 0 || salvage <= 0) return 0;
        return compare(offer, salvage, minimumPercent, 1.0);
    }
    public static int compare(double offer, double salvage, double minimumPercent, double feePercent) {
        if (!Double.isFinite(offer) || !Double.isFinite(salvage) || offer <= 0 || salvage <= 0 || !Double.isFinite(feePercent)) return 0;
        double sell = offer * (1.0 - Math.max(0, Math.min(100, feePercent)) / 100.0);
        if (sell <= 0) return -1;
        double advantage = Math.abs(sell - salvage) / Math.min(sell, salvage) * 100.0;
        if (Math.abs(sell - salvage) < 0.000001 || advantage < Math.max(0, minimumPercent)) return 0;
        return sell > salvage ? 1 : -1;
    }
    public static int compare(double offer,double salvage,double minimumPercent,double feePercent,double minimumCoins){
        int decision=compare(offer,salvage,minimumPercent,feePercent);
        if(decision==0)return 0;
        double sell=offer*(1.0-Math.max(0,Math.min(100,feePercent))/100.0);
        return ShardProfitMinimum.meets(sell,salvage,minimumCoins)?decision:0;
    }
}

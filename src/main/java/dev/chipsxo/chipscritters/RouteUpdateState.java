package dev.chipsxo.chipscritters;
/** Immutable-run staging policy shared by production and regression checks. */
public final class RouteUpdateState {
 private SignedRouteData.Verified active,pending;
 public synchronized SignedRouteData.Verified active(){return active;}
 public synchronized SignedRouteData.Verified pending(){return pending;}
 public synchronized int highestVersion(){return Math.max(active==null?0:active.version(),pending==null?0:pending.version());}
 public synchronized void loadCache(SignedRouteData.Verified data){active=data;}
 public synchronized void stage(SignedRouteData.Verified data){if(data.version()<=highestVersion())throw new IllegalArgumentException("older data version");pending=data;}
 public synchronized boolean beginRun(boolean routeActive){if(routeActive||pending==null)return false;active=pending;pending=null;return true;}
}

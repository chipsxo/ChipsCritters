package dev.chipsxo.chipscritters;
public final class DevMode {
 private DevMode(){}
 public static java.util.List<net.minecraft.world.phys.Vec3> preview(){return enabled()?RouteRecorder.preview():java.util.List.of();}
 public static void routeChanged(){if(enabled())RouteRecorder.routeChanged();RecordedLegPaths.clearSelection();}
 public static boolean enabled(){return DevMode.class.getResource("/chipscritters-dev.marker")!=null;}
}

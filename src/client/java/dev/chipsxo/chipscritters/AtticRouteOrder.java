package dev.chipsxo.chipscritters;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Exhaustively orders the small attic group as a single visit, keeping every other stop in order. */
final class AtticRouteOrder {
    private AtticRouteOrder() { }
    static List<Vec3> uniqueShortSweep(List<Vec3> route,SafariGraph graph){
        var attic=route.stream().filter(p->attic(p)&&SafariData.route(Biome.HAUNTED).houseSweep().contains(p)).toList();if(attic.size()<2)return route;
        int first=route.indexOf(attic.getFirst());var base=new ArrayList<>(route);base.removeAll(attic);
        Vec3 before=base.get(Math.max(0,first-1)),after=base.get(Math.min(first,base.size()-1));
        Vec3 kept=attic.stream().min(java.util.Comparator.comparingDouble(p->distance(graph,before,p)+distance(graph,p,after))).orElse(attic.getFirst());
        base.add(Math.min(first,base.size()),kept);
        ChipsCrittersClient.LOGGER.info("Unique short attic: kept={}, removed={}, old={} new={}",kept,attic.stream().filter(p->!p.equals(kept)).toList(),length(graph,route),length(graph,base));
        return List.copyOf(base);
    }
    static boolean attic(Vec3 p){return p.y>=85&&p.y<=88&&p.x>=-16&&p.x<=16&&p.z>=-85&&p.z<=-50;}
    static List<Vec3> optimize(List<Vec3> route, SafariGraph graph) {
        List<Vec3> attic = route.stream().filter(p -> p.y >= 85 && p.y <= 88 && p.x >= -16 && p.x <= 16 && p.z >= -85 && p.z <= -50).toList();
        if (attic.size() < 2 || attic.size() > 8) return route;
        List<Vec3> base = new ArrayList<>(route);
        base.removeAll(attic);
        List<List<Vec3>> permutations = new ArrayList<>();
        permute(new ArrayList<>(attic), 0, permutations);
        double best = Double.POSITIVE_INFINITY;
        List<Vec3> chosen = route;
        for (int at = 2; at < base.size(); at++) {
            Vec3 before = base.get(at - 1), after = base.get(at);
            // Entry and exit stay on the house's upper floor, avoiding an underground detour.
            if (before.y < 77 || after.y < 77) continue;
            for (List<Vec3> order : permutations) {
                double cost = distance(graph, before, order.getFirst()) + distance(graph, order.getLast(), after) - distance(graph, before, after);
                for (int j = 1; j < order.size(); j++) cost += distance(graph, order.get(j - 1), order.get(j));
                if (cost < best) {
                    best = cost;
                    var candidate = new ArrayList<>(base);
                    candidate.addAll(at, order);
                    chosen = List.copyOf(candidate);
                }
            }
        }
        ChipsCrittersClient.LOGGER.info("Haunted attic grouped: old={} blocks, new={} blocks, attic={}", length(graph, route), length(graph, chosen), chosen.stream().filter(attic::contains).toList());
        return chosen;
    }
    static double length(SafariGraph graph, List<Vec3> points) { double total=0; for(int i=1;i<points.size();i++) total+=distance(graph,points.get(i-1),points.get(i)); return total; }
    private static double distance(SafariGraph graph, Vec3 a, Vec3 b) { return graph.travelDistance(Biome.HAUNTED, a, b); }
    private static void permute(ArrayList<Vec3> points, int at, List<List<Vec3>> output) {
        if (at == points.size()) { output.add(List.copyOf(points)); return; }
        for (int j = at; j < points.size(); j++) {
            java.util.Collections.swap(points, at, j);
            permute(points, at + 1, output);
            java.util.Collections.swap(points, at, j);
        }
    }
}

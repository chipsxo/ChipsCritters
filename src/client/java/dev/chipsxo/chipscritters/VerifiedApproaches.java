package dev.chipsxo.chipscritters;
import java.util.*;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.phys.Vec3;
final class VerifiedApproaches {
    private record Corridor(Biome biome, List<Vec3> points) { }
    private static final java.util.Set<String> LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final List<Corridor> PATHS = load();
    private static List<Corridor> load() {
        try (var stream = VerifiedApproaches.class.getResourceAsStream("/data/chipscritters/verified_approaches.json")) {
            var paths = new ArrayList<Corridor>();
            for(var entry:JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonArray()) {
                var points=new ArrayList<Vec3>();
                for(var point:entry.getAsJsonObject().getAsJsonArray("path")) {
                    var xyz=point.getAsJsonArray();
                    Vec3 next = new Vec3(xyz.get(0).getAsDouble()+.5,xyz.get(1).getAsDouble(),xyz.get(2).getAsDouble()+.5);
                    if (!points.isEmpty()) {
                        Vec3 prior = points.getLast();
                        int subdivisions = (int)Math.ceil(prior.distanceTo(next) / 2.0);
                        for(int part=1;part<subdivisions;part++) points.add(prior.lerp(next,(double)part/subdivisions));
                    }
                    points.add(next);
                }
                paths.add(new Corridor(Biome.valueOf(entry.getAsJsonObject().get("biome").getAsString()), List.copyOf(points)));
            }
            return List.copyOf(paths);
        } catch(Exception e) { throw new IllegalStateException("Invalid verified approaches",e); }
    }
    static List<Vec3> from(Biome biome,Vec3 from,Vec3 to) {
        for(var corridor:PATHS) {
            if (corridor.biome() != biome) continue;
            var path = corridor.points();
            if(path.isEmpty() || path.getLast().distanceToSqr(to)>1) continue;
            int nearest=0;
            for(int i=1;i<path.size();i++) if(path.get(i).distanceToSqr(from)<path.get(nearest).distanceToSqr(from)) nearest=i;
            if(path.get(nearest).distanceToSqr(from)>16) continue;
            var result=new ArrayList<Vec3>(); result.add(from);
            result.addAll(path.subList(nearest,path.size())); result.add(to);
            String key=biome+":"+path.getFirst()+":"+path.getLast();
            if(LOGGED.add(key)) ChipsCrittersClient.LOGGER.info("Fixed route approach selected: biome={}, from={}, target={}, guideStart={}, reason=cached open-space path; corrected standing-height anchors",biome,from,to,path.getFirst());
            return List.copyOf(result);
        }
        return List.of();
    }
}

package dev.chipsxo.chipscritters;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.*;

final class SafariTerrain {
	record Painting(Vec3 stand, Vec3 anchor, List<Vec3> path) {}
	private static final Map<Biome,Map<Vec3,Vec3>> floors = new EnumMap<>(Biome.class);
	private static final Map<Vec3,Painting> paintings = new HashMap<>();
	static {
		try (var in = SafariTerrain.class.getResourceAsStream("/data/chipscritters/terrain.json");
				var reader = new InputStreamReader(Objects.requireNonNull(in), StandardCharsets.UTF_8)) {
			var root = JsonParser.parseReader(reader).getAsJsonObject();
			root.getAsJsonObject("corrections").entrySet().forEach(entry -> {
				Map<Vec3,Vec3> map = new HashMap<>();
				for (var pair : entry.getValue().getAsJsonArray()) {
					var ab = pair.getAsJsonArray(); map.put(point(ab.get(0)),point(ab.get(1)));
				}
				floors.put(Biome.valueOf(entry.getKey()), Map.copyOf(map));
			});
			for (var item : root.getAsJsonArray("paintings")) {
				var obj=item.getAsJsonObject();
				var path=new ArrayList<Vec3>();
				for(var step:obj.getAsJsonArray("path")) path.add(point(step));
				paintings.put(point(obj.get("paint")),new Painting(point(obj.get("stand")),point(obj.get("anchor")),List.copyOf(path)));
			}
		} catch (IOException e) { throw new UncheckedIOException(e); }
	}
	private static Vec3 point(JsonElement e) {
		var p=e.getAsJsonArray(); return new Vec3(p.get(0).getAsDouble(),p.get(1).getAsDouble(),p.get(2).getAsDouble());
	}
	static Painting painting(Biome b,Vec3 id) {return b==Biome.HAUNTED ? paintings.get(id) : null;}
	static Vec3 floor(Biome b,Vec3 id) {
		if(b==Biome.CAVERN){
            if(id.equals(new Vec3(-88,61,43)))return new Vec3(-89,61,43);
            if(id.equals(new Vec3(-88,71,43)))return new Vec3(-89,71,43);
            if(id.equals(new Vec3(-94,74,37)))return new Vec3(-95,74,37);
            if(id.equals(new Vec3(-94,81,37)))return new Vec3(-95,81,37);
            if(id.equals(new Vec3(-112,88,17)))return new Vec3(-113,88,17);
        }
        var painting=painting(b,id);
		return painting!=null ? painting.stand : floors.getOrDefault(b,Map.of()).getOrDefault(id,id);
	}
	static Vec3 standing(ClientLevel level,Biome b,Vec3 id) {
		Vec3 base=floor(b,id);
		if(level==null) return base;
		BlockPos block=BlockPos.containing(base.x+.5,base.y,base.z+.5);
		if(!level.hasChunkAt(block)) return base;
		AABB body=new AABB(base.x+.18,base.y+.001,base.z+.18,base.x+.82,base.y+1.8,base.z+.82);
		if(b==Biome.HAUNTED && id.equals(SafariData.GIMMIEGOLD)){
            // Keep the authored XYZ; place its cube on the loaded supporting surface, including seagrass/water.
            for(int down=1;down<=2;down++){
                var support=block.below(down);if(!level.hasChunkAt(support))break;
                var shape=level.getBlockState(support).getCollisionShape(level,support);
                if(shape.isEmpty())continue;
                double top=support.getY()+shape.max(net.minecraft.core.Direction.Axis.Y);
                if(top<=base.y && top>=base.y-2 && level.noCollision(body.move(0,top-base.y,0)))return new Vec3(base.x,top,base.z);
                break;
            }
        }
        if(level.noCollision(body)) {
			var support=block.below(); var shape=level.getBlockState(support).getCollisionShape(level,support);
			if(!shape.isEmpty() && !level.getBlockState(block).is(net.minecraft.tags.BlockTags.CLIMBABLE)){
				double top=support.getY()+shape.max(net.minecraft.core.Direction.Axis.Y);
				if(top<base.y && base.y-top<1 && level.noCollision(body.move(0,top-base.y,0)))return new Vec3(base.x,top,base.z);
			}
			return base;
		}
		// Only lift through contiguous supporting material. A ceiling over open feet is a different floor.
		for(int up=0;up<=3;up++) {
			BlockPos cell=block.above(up);
			var shape=level.getBlockState(cell).getCollisionShape(level,cell);
			if(shape.isEmpty()) break;
			double top=cell.getY()+shape.max(net.minecraft.core.Direction.Axis.Y);
			if(top-base.y>3.001) break;
			if(level.noCollision(body.move(0,top-base.y,0))) return new Vec3(base.x,top,base.z);
		}
		return base;
	}
	private SafariTerrain() {}
}

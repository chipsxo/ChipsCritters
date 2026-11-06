package dev.chipsxo.chipscritters;
import com.google.gson.*;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/** Strict data-only signed package. This class performs no IO or Minecraft access. */
public final class SignedRouteData {
 public static final int SCHEMA=1;
 public static final String PUBLIC_KEY="MCowBQYDK2VwAyEA12UUQQvOl3Ok2K7qCTety0xAApKJs386wp57h1GEFyU=";
 public record Verified(int version, JsonObject payload) { }
 private SignedRouteData(){}
 public static Verified verify(String envelope,String key,String modVersion,int currentVersion) throws Exception {
  if(envelope.length()>8_000_000)throw new IllegalArgumentException("package too large");
  JsonObject outer=parse(envelope).getAsJsonObject(); exact(outer,Set.of("payload","signature"));
  byte[] bytes=Base64.getDecoder().decode(outer.get("payload").getAsString());
  if(bytes.length>5_000_000)throw new IllegalArgumentException("payload too large");
  var pub=KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key)));
  var verifier=Signature.getInstance("Ed25519");verifier.initVerify(pub);verifier.update(bytes);
  if(!verifier.verify(Base64.getDecoder().decode(outer.get("signature").getAsString())))throw new IllegalArgumentException("bad signature");
  JsonObject data=parse(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
  validate(data,modVersion);
  int version=integer(data.get("dataVersion"));
  if(version<=currentVersion)throw new IllegalArgumentException("older or equal data version");
  return new Verified(version,data.deepCopy());
 }
 public static void validate(JsonObject data,String modVersion) {
  exact(data,Set.of("schema","dataVersion","minModVersion","routes","recordedPaths"));
  if(integer(data.get("schema"))!=SCHEMA || integer(data.get("dataVersion"))<1)throw new IllegalArgumentException("invalid schema/version");
  String min=data.get("minModVersion").getAsString();
  if(!min.matches("[0-9]{1,4}\\.[0-9]{1,4}\\.[0-9]{1,4}") || compareVersion(min,modVersion)>0)throw new IllegalArgumentException("minimum mod version unsupported");
  JsonObject routes=data.getAsJsonObject("routes");exact(routes,Set.of("ICY","FOREST","CAVERN","HAUNTED"));
  Set<String> fields=Set.of("fixedEnd","stops","floorDropChecks","houseSweep","hideyhoLocations","hideonwallLocations","gazerBeds","ritualPoint","cavernWaterfallEntry","cavernWaterfallTopStops","cavernWestPlateauStops","cavernLadders");
  for(var biome:routes.entrySet()) {
   var route=biome.getValue().getAsJsonObject();
   if(!fields.containsAll(route.keySet()) || !route.has("stops") || !route.has("fixedEnd") || !route.get("fixedEnd").getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("unknown route field");
   for(var field:route.entrySet()) {
    if(field.getKey().equals("fixedEnd"))continue;
    if(field.getKey().equals("cavernLadders")) {
     if(field.getValue().getAsJsonArray().size()>32)throw new IllegalArgumentException("too many climbs");
     for(var v:field.getValue().getAsJsonArray()) {
      var c=v.getAsJsonObject();exact(c,Set.of("name","bottom","top","twoWay","before"));
      if(!Set.of("CLIMB LADDER","CLIMB THE VINES").contains(c.get("name").getAsString()) || !c.get("twoWay").getAsJsonPrimitive().isBoolean())throw new IllegalArgumentException("invalid connector");
      for(String k:List.of("bottom","top","before"))point(c.get(k),biome.getKey());
     }
    } else if(Set.of("ritualPoint","cavernWaterfallEntry").contains(field.getKey()))point(field.getValue(),biome.getKey());
    else points(field.getValue(),biome.getKey(),512);
   }
   if(route.getAsJsonArray("stops").isEmpty())throw new IllegalArgumentException("empty route");
  }
  var legs=data.getAsJsonArray("recordedPaths");if(legs.size()>512)throw new IllegalArgumentException("too many legs");
  Set<String> seen=new HashSet<>();
  for(var v:legs){var leg=v.getAsJsonObject();exact(leg,Set.of("biome","from","to","points"));String biome=leg.get("biome").getAsString();
   if(!routes.has(biome))throw new IllegalArgumentException("invalid biome");point(leg.get("from"),biome);point(leg.get("to"),biome);points(leg.get("points"),biome,12000);
   if(leg.getAsJsonArray("points").size()<2)throw new IllegalArgumentException("leg requires two points");
   var positions=leg.getAsJsonArray("points");
   if(distance(positions.get(0),leg.get("from"))>256 || distance(positions.get(positions.size()-1),leg.get("to"))>10)throw new IllegalArgumentException("recorded endpoints do not match leg");
   for(int i=1;i<positions.size();i++)if(distance(positions.get(i-1),positions.get(i))>4)throw new IllegalArgumentException("recording has a discontinuity");
   if(!seen.add(biome+leg.get("from")+leg.get("to")))throw new IllegalArgumentException("duplicate leg");
  }
 }
 private static JsonElement parse(String text){
  int depth=0;boolean quoted=false,escape=false;
  for(char c:text.toCharArray()){
   if(escape){escape=false;continue;}if(quoted&&c=='\\'){escape=true;continue;}if(c=='"'){quoted=!quoted;continue;}if(quoted)continue;
   if(c=='{'||c=='['){if(++depth>32)throw new IllegalArgumentException("JSON nesting limit");}else if(c=='}'||c==']'){if(--depth<0)throw new IllegalArgumentException("JSON nesting");}
  }if(depth!=0||quoted)throw new IllegalArgumentException("invalid JSON");return JsonParser.parseString(text);
 }
 private static void points(JsonElement e,String biome,int max){var a=e.getAsJsonArray();if(a.size()>max)throw new IllegalArgumentException("too many points");for(var p:a)point(p,biome);}
 private static void point(JsonElement e,String biome){var a=e.getAsJsonArray();if(a.size()!=3)throw new IllegalArgumentException("invalid XYZ");double[] p=new double[3];for(int i=0;i<3;i++){if(!a.get(i).getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("coordinate is not numeric");p[i]=a.get(i).getAsDouble();if(!Double.isFinite(p[i]))throw new IllegalArgumentException("nonfinite XYZ");}
  double minX=-60,maxX=70,minZ=-120,maxZ=35,minY=30,maxY=145;
  switch(biome){case "CAVERN"->{minX=-160;maxX=-45;minZ=0;maxZ=100;minY=25;}case "ICY"->{minX=-150;maxX=-45;minZ=-130;maxZ=25;minY=10;}case "FOREST"->{maxX=55;minZ=0;maxZ=110;}case "HAUNTED"->{}default->throw new IllegalArgumentException("biome");}
  if(p[0]<minX||p[0]>maxX||p[1]<minY||p[1]>maxY||p[2]<minZ||p[2]>maxZ)throw new IllegalArgumentException("coordinate outside "+biome);
 }
 private static double distance(JsonElement a,JsonElement b){double d=0;for(int i=0;i<3;i++){double v=a.getAsJsonArray().get(i).getAsDouble()-b.getAsJsonArray().get(i).getAsDouble();d+=v*v;}return Math.sqrt(d);}
 private static int integer(JsonElement e){if(!e.getAsJsonPrimitive().isNumber() || e.getAsDouble()!=e.getAsInt())throw new IllegalArgumentException("integer required");return e.getAsInt();}
 private static void exact(JsonObject o,Set<String> fields){if(!o.keySet().equals(fields))throw new IllegalArgumentException("unknown or missing fields");}
 private static int compareVersion(String a,String b){String[] x=a.split("\\."),y=b.split("\\.");for(int i=0;i<3;i++){int c=Integer.compare(Integer.parseInt(x[i]),Integer.parseInt(y[i]));if(c!=0)return c;}return 0;}
}

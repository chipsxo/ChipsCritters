package dev.chipsxo.chipscritters;
import com.google.gson.*;
import java.security.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

final class SignedRouteRegressionTests {
 static void check(boolean condition,String reason){if(!condition)throw new AssertionError(reason);}
 interface Action{void run()throws Exception;}
 static void reject(Action action,String reason)throws Exception{try{action.run();}catch(Exception expected){return;}throw new AssertionError("accepted "+reason);}
 static String envelope(JsonObject payload,PrivateKey privateKey)throws Exception{
  byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);var signature=Signature.getInstance("Ed25519");signature.initSign(privateKey);signature.update(bytes);
  var out=new JsonObject();out.addProperty("payload",Base64.getEncoder().encodeToString(bytes));out.addProperty("signature",Base64.getEncoder().encodeToString(signature.sign()));return out.toString();
 }
 static void run()throws Exception{
  var pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();String pub=Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
  JsonObject payload=new JsonObject();payload.addProperty("schema",1);payload.addProperty("dataVersion",1);payload.addProperty("minModVersion","0.1.55");
  try(var in=SignedRouteRegressionTests.class.getResourceAsStream("/data/chipscritters/routes.json")){payload.add("routes",JsonParser.parseReader(new java.io.InputStreamReader(in,StandardCharsets.UTF_8)));}
  payload.add("recordedPaths",new JsonArray());
  String signed=envelope(payload,pair.getPrivate());
  var valid=SignedRouteData.verify(signed,pub,"0.1.55",0);check(valid.version()==1,"valid signed route accepted");
  var broken=JsonParser.parseString(signed).getAsJsonObject();broken.addProperty("signature",Base64.getEncoder().encodeToString(new byte[64]));
  reject(()->SignedRouteData.verify(broken.toString(),pub,"0.1.55",0),"bad signature");
  reject(()->SignedRouteData.verify(signed,pub,"0.1.55",1),"old version");
  var unknown=payload.deepCopy();unknown.addProperty("command","/warp safari");reject(()->SignedRouteData.verify(envelope(unknown,pair.getPrivate()),pub,"0.1.55",0),"command field");
  var coords=payload.deepCopy();coords.getAsJsonObject("routes").getAsJsonObject("CAVERN").getAsJsonArray("stops").set(0,JsonParser.parseString("[999,70,999]"));reject(()->SignedRouteData.verify(envelope(coords,pair.getPrivate()),pub,"0.1.55",0),"outside biome");
  var future=payload.deepCopy();future.addProperty("minModVersion","9.0.0");reject(()->SignedRouteData.verify(envelope(future,pair.getPrivate()),pub,"0.1.55",0),"future mod");
  var invalidSchema=payload.deepCopy();invalidSchema.addProperty("schema",2);reject(()->SignedRouteData.verify(envelope(invalidSchema,pair.getPrivate()),pub,"0.1.55",0),"unknown schema");
  var url=payload.deepCopy();url.getAsJsonObject("routes").getAsJsonObject("CAVERN").getAsJsonArray("cavernLadders").get(0).getAsJsonObject().addProperty("name","https://example.com");reject(()->SignedRouteData.verify(envelope(url,pair.getPrivate()),pub,"0.1.55",0),"URL inside data");
  var state=new RouteUpdateState();state.loadCache(valid);
  var newer=payload.deepCopy();newer.addProperty("dataVersion",2);var candidate=SignedRouteData.verify(envelope(newer,pair.getPrivate()),pub,"0.1.55",1);
  state.stage(candidate);check(!state.beginRun(true)&&state.active().version()==1,"update arriving during active route stays staged");
  // A simulated offline fetch returns no package: the same last good snapshot remains untouched.
  try{throw new java.io.IOException("offline transport");}catch(java.io.IOException expected){check(state.active().version()==1,"offline retains last good");}
  check(state.beginRun(false)&&state.active().version()==2,"next-run boundary promotes signed data");
  reject(()->state.stage(valid),"downgrade after activation");
  var leg=new JsonObject();leg.addProperty("biome","CAVERN");leg.add("from",JsonParser.parseString("[-92,72,42]"));leg.add("to",JsonParser.parseString("[-88,61,42]"));
  var positions=new JsonArray();for(int i=0;i<=11;i++)positions.add(JsonParser.parseString("[-89,"+(72-i)+",42]"));leg.add("points",positions);
  var recording=payload.deepCopy();recording.getAsJsonArray("recordedPaths").add(leg);SignedRouteData.validate(recording,"0.1.55");
  var parsed=RecordedLegPaths.parse(recording.getAsJsonArray("recordedPaths"));check(parsed.getFirst().points().size()==12,"recorded foot samples retained without smoothing");
  check(!DevMode.enabled(),"public regression classpath has no dev marker");
  var dispatcher=new com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>();ChipsCrittersClient.registerRouteCommands(dispatcher,"sr");check(dispatcher.getRoot().getChild("sr").getChild("record")==null,"public command tree excludes recorder");
  var limited=new LimitedRouteBody(4);boolean[] cancelled={false};
  limited.onSubscribe(new java.util.concurrent.Flow.Subscription(){public void request(long n){}public void cancel(){cancelled[0]=true;}});
  limited.onNext(List.of(java.nio.ByteBuffer.wrap(new byte[5])));
  check(cancelled[0],"oversized response subscription cancelled before buffering");
  reject(()->limited.getBody().toCompletableFuture().get(),"oversized body completion");
  System.out.println("Signed route data regression checks passed.");
 }
}

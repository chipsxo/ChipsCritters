package dev.chipsxo.chipscritters;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;

/** Data updates are staged by a worker and only activated at a new-run boundary. */
public final class RouteDataUpdates {
 public static final String MOD_VERSION="0.1.65";
 private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
 static Path folder(){return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("chipscritters-routes");}
 private static final RouteUpdateState DATA=new RouteUpdateState();
 private static volatile String pendingEnvelope;
 private static volatile String state="route updates not configured";
 private static volatile boolean busy;
 private static long lastCheck;
 private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
 private RouteDataUpdates(){}
 public static void initialize(){
  for(String name:new String[]{"last-good.json","last-good.backup.json"})try{Path p=folder().resolve(name);if(Files.exists(p)){if(Files.size(p)>8_000_000)throw new java.io.IOException("oversized route cache");DATA.loadCache(SignedRouteData.verify(Files.readString(p),SignedRouteData.PUBLIC_KEY,MOD_VERSION,0));break;}}catch(Exception e){ChipsCrittersClient.LOGGER.warn("Route cache rejected: {}",e.getMessage());}
  check();
 }
 public static JsonObject routeRoot(){return DATA.active()==null?null:DATA.active().payload().getAsJsonObject("routes").deepCopy();}
 public static JsonArray paths(){return DATA.active()==null?new JsonArray():DATA.active().payload().getAsJsonArray("recordedPaths").deepCopy();}
 public static int version(){return DATA.active()==null?0:DATA.active().version();}
 public static String status(){return "route data v"+version()+" ("+(DATA.active()==null?"bundled":"signed")+") - "+(ChipsConfig.get().routeDataUrl.isBlank()?"route updates not configured":state);}
 public static synchronized void beginRun(){
  if(RouteController.active() || DATA.pending()==null || !ChipsConfig.get().routeUpdatesEnabled) return;
  try{atomicWrite(folder().resolve("last-good.json"),pendingEnvelope,true);}catch(Exception e){state="activation delayed: "+e.getMessage();return;}
  if(!DATA.beginRun(false))return;pendingEnvelope=null;state="signed route data activated for new run";
  SafariData.reloadRouteData();RecordedLegPaths.reload();RoutePlanService.clear();
 }
 public static synchronized void check(){
  var config=ChipsConfig.get();
  if(config.routeDataUrl.isBlank()){state="route updates not configured";return;}
  if(!config.routeUpdatesEnabled){state="disabled";return;}
  if(RouteController.active()||busy||System.currentTimeMillis()-lastCheck<600000L)return;
  URI uri;
  try{uri=URI.create(config.routeDataUrl);if(!"https".equals(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null || !uri.getPath().endsWith(".json"))throw new IllegalArgumentException();}
  catch(Exception e){state="invalid HTTPS route URL";return;}
  lastCheck=System.currentTimeMillis();busy=true;state="checking";
  CompletableFuture.runAsync(()->{
   try{
    var req=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(12)).header("Accept","application/json").GET().build();
    var response=HTTP.send(req,info->new LimitedRouteBody(8_000_000));
    
    byte[] bytes=response.body();
    String envelope=new String(bytes,StandardCharsets.UTF_8);
    var verified=RouteUpdateResponse.verify(response.statusCode(),envelope,SignedRouteData.PUBLIC_KEY,MOD_VERSION,DATA.highestVersion());
    atomicWrite(folder().resolve("pending-signed.json"),envelope,false);
    synchronized(RouteDataUpdates.class){pendingEnvelope=envelope;DATA.stage(verified);}state="v"+verified.version()+" ready for next run";
   }catch(Exception e){state="kept last good data: "+e.getMessage();ChipsCrittersClient.LOGGER.warn("Route update rejected/unavailable: {}",e.getMessage());}
   finally{busy=false;}
  });
 }
 static void atomicWrite(Path target,String text,boolean backup) throws java.io.IOException {
  if(!target.normalize().getParent().equals(folder().normalize()))throw new java.io.IOException("route file must stay in mod config folder");
  Files.createDirectories(folder());Path temp=Files.createTempFile(folder(),"routes-",".tmp");
  try{Files.writeString(temp,text,StandardCharsets.UTF_8);try(var channel=java.nio.channels.FileChannel.open(temp,StandardOpenOption.WRITE)){channel.force(true);}
   if(backup&&Files.exists(target))Files.copy(target,folder().resolve("last-good.backup.json"),StandardCopyOption.REPLACE_EXISTING);
   try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
  }finally{Files.deleteIfExists(temp);}
 }
 static JsonObject exportPayload(int dataVersion){
  JsonObject root=new JsonObject();root.addProperty("schema",1);root.addProperty("dataVersion",dataVersion);root.addProperty("minModVersion",MOD_VERSION);
  JsonObject routes=routeRoot();
  if(routes==null)try(var stream=RouteDataUpdates.class.getResourceAsStream("/data/chipscritters/routes.json")){routes=JsonParser.parseReader(new java.io.InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();}catch(Exception e){throw new IllegalStateException(e);}
  root.add("routes",routes);root.add("recordedPaths",RecordedLegPaths.exportPaths());SignedRouteData.validate(root,MOD_VERSION);return root;
 }
 public static void notice(){var c=ChipsConfig.get();if(!c.routeDataUrl.isBlank()&&!c.routeUpdateNoticeShown&&net.minecraft.client.Minecraft.getInstance().player!=null){
  RouteController.commandFeedback("Route updates download signed data from "+c.routeDataUrl+". They take effect next run; disable under Tools > Route data.");c.routeUpdateNoticeShown=true;c.save();}}
}

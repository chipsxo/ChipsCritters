package dev.chipsxo.chipscritters;

import java.nio.file.*;
import java.util.*;
import net.minecraft.world.phys.Vec3;

final class Patch59RegressionTests {
    static void check(boolean value,String message){if(!value)throw new AssertionError("0.1.59: "+message);}
    static void run() throws Exception {
        // Reproduce an inside-room /back: the old stitch replayed the entrance, crossing the room twice.
        Vec3 player=new Vec3(5,79,-84),target=new Vec3(6.5,79,-83.5);
        var tail=List.of(new Vec3(2.5,79,-80.5),new Vec3(4.5,79,-83.5),target);
        var old=List.of(player,new Vec3(1.5,79,-69.5),tail.getFirst(),tail.get(1),target);
        var direct=PaintingPathJoin.join(player,tail,old,(a,b)->true);
        check(direct.equals(List.of(player,target)),"inside-room suffix is a clean direct segment without entrance reversal");
        var blocked=PaintingPathJoin.join(player,tail,old,(a,b)->false);
        check(blocked.equals(PaintingPathJoin.eraseLoops(old)),"blocked shortcuts can only erase loops along existing validated segments");
        var noLoop=List.of(player,new Vec3(1.5,79,-69.5),target);
        check(PaintingPathJoin.join(player,List.of(target),noLoop,(x,y)->false).equals(noLoop),"cannot invent a wall-crossing chord");
        var brokenTail=PaintingPathJoin.join(player,tail,List.of(),(a,b)->a.equals(player)&&b.equals(tail.getFirst()));
        check(brokenTail.isEmpty(),"no unchecked blocked suffix appended after a valid head");
        Vec3 a=new Vec3(0,78,0),b=new Vec3(2,78,2),c=new Vec3(0,78,2),d=new Vec3(2,78,0);
        check(PaintingPathJoin.eraseLoops(List.of(a,b,c,d)).equals(List.of(a,new Vec3(1,78,1),d)),"stitched crossing loop removed along its existing safe segments");
        check(PaintingPathJoin.eraseLoops(List.of(a,b,c,b,d)).equals(List.of(a,b,d)),"repeated-anchor reversal erased without a new chord");

        var trip=new SafariTripTips();long now=1000;
        check(trip.begin("-----------------------------\n[MVP+] chipsxo entered Critter Safari!\n-----------------------------",now),"real entry block accepted");
        check(trip.immediateVisibility(now+50,true,true,true).equals("show"),"own entry displays with no camera/locraw wait");
        check(!trip.begin("Alex entered Critter Safari!",now+100),"burst does not restart");
        trip.end();check(!trip.immediateVisibility(now+600,true,true,true).equals("show"),"landing ends tips");
        check(!SafariTripTips.ownEntry("[VIP] Alex entered Critter Safari!","chipsxo"),"party entry does not start local trip");
        check(!SafariTripTips.entry("Guild > [MVP+] Alex: chipsxo entered Critter Safari!"),"typed announcement rejected");

        check(NumberedRouteJump.index(33,33,31,57,58)==31,"HUD 33/58 maps to same cursor despite filtered 57 list");
        check(NumberedRouteJump.index(40,33,31,57,58)==38,"numbered skip destination");
        check(NumberedRouteJump.index(0,33,31,57,58)<0&&NumberedRouteJump.index(59,33,31,57,58)<0,"out-of-range jumps rejected");
        var book=new RouteRunBookkeeping();book.startOrResume(Biome.HAUNTED,1000,0);book.disqualify(Biome.HAUNTED);
        check(!book.markCompleted(Biome.HAUNTED,ChipsConfig.Mode.UNIQUE,true),"number skip disqualifies PB");

        check(ChipsConfig.Mode.values().length==3,"Fastest removed from selectable modes");
        var config=com.google.gson.JsonParser.parseString("{\"mode\":\"FASTEST\",\"hudX\":77,\"modeProfiles\":{\"FASTEST\":{\"detourBlocks\":9},\"MONEY\":{\"detourBlocks\":4}}}").getAsJsonObject();
        check(RetiredModeMigration.migrate(config)&&config.get("mode").getAsString().equals("MONEY"),"selection migrates to Money");
        check(config.get("hudX").getAsInt()==77&&config.getAsJsonObject("modeProfiles").getAsJsonObject("FASTEST").get("detourBlocks").getAsInt()==9,"positions and legacy profile survive");
        check(!RetiredModeMigration.migrate(config),"migration idempotent");
        Path history=Files.createTempDirectory("chipscritters-retired-").resolve("history.json");
        Files.writeString(history,"{\"runs\":[{\"biome\":\"HAUNTED\",\"mode\":\"FASTEST\",\"durationMillis\":5000,\"stopCount\":39}],\"personalBests\":{\"HAUNTED/MONEY\":{\"biome\":\"HAUNTED\",\"mode\":\"MONEY\",\"durationMillis\":9000}}}");
        var store=new PersistentRunHistory(history);
        check(store.archivedFastestBest(Biome.HAUNTED)==5000,"old runs-only Fastest PB migrated to archive");
        check(store.personalBestMillis(Biome.HAUNTED,ChipsConfig.Mode.MONEY)==9000,"shorter retired run cannot overwrite Money PB");
        check(new PersistentRunHistory(history).archivedFastestBest(Biome.HAUNTED)==5000,"archived best survives reload");

        var unique=new HauntedUniqueState();
        for(String name:List.of("Areita","Bloodbat","Duplico","Gazer","Litterbug")){
            check(unique.capture("§a§lCAPTURE! §7You caught a §aSPARKLING "+name+"§7 and gained 2x "+name+" Shard!"),"own sparkling "+name);
        }
        check(unique.canRouteDoom(false,3),"Hideonwall disabled does not block house completion");
        check(!unique.canRouteDoom(true,3)&&!unique.canRouteDoom(false,2),"painting-on requirement and three-incense threshold");
        check(!unique.capture("LOOT SHARE! You caught a Hideonwall and gained a Hideonwall Shard!"),"teammate/loot-share cannot satisfy house");
        check(!unique.capture("Party > Alex: CAPTURE! You caught a Hideonwall and gained a Hideonwall Shard!"),"player chat cannot satisfy house");
        check(unique.capture("CAPTURE! You caught a Hideonwall and gained a Hideonwall Shard!")&&unique.canRouteDoom(true,3),"all six own catches unlock at three");
        check(HauntedInventory.matches("§6Shining Coin","SHINING_COIN","SHINING_COIN","Shining Coin"),"verified coin name/id");
        check(!HauntedInventory.matches("Shining Coin","FAKE_COIN","SHINING_COIN","Shining Coin"),"renamed counterfeit does not match");
        check(HauntedInventory.matches("Soothing Incense","SOOTHING_INCENSE","SOOTHING_INCENSE","Soothing Incense"),"verified incense name/id");

        WaypointRunLedger.newRunStarted();var state=WaypointRunLedger.HAUNTED;var u=WaypointRunLedger.HAUNTED_UNIQUE;
        u.earlyGold=true;
        var room=new Vec3(7,78,-68);var ritual=SafariData.route(Biome.HAUNTED).ritualPoint();
        check(RouteController.uniquePending(List.of(room,SafariData.GIMMIEGOLD)).equals(List.of(room,SafariData.GIMMIEGOLD)),"restart preserves order pending cost-based coin placement");
        state.gold=true;WaypointRunLedger.markSatisfied(Biome.HAUNTED,SafariData.GIMMIEGOLD);
        check(!RouteController.uniquePending(List.of(room,SafariData.GIMMIEGOLD)).contains(SafariData.GIMMIEGOLD),"reached/caught early Gold never returns on restart");
        check(!state.finishOnce(),"early Gold does not finish before Doom");
        check(state.capture("CAPTURE! You caught a Doomspiral and gained 2x Doomspiral Shard!")&&state.finishOnce()&&!state.finishOnce(),"Doom after early Gold finishes exactly once");
        var sparkling=new HauntedFinish();check(sparkling.capture("CAPTURE! You caught a SPARKLING Gimmiegold and received a Rainbow Feather and 2x Gimmiegold Shard!"),"personal sparkling Gold with extra loot");
        check(!sparkling.capture("LOOT SHARE! CAPTURE! You caught a Doomspiral and gained a Doomspiral Shard!"),"loot share cannot complete Haunted");
        WaypointRunLedger.newRunStarted();check(!u.earlyGold&&u.caught.isEmpty()&&!state.gold&&!state.doom,"new run resets Unique early/catch state");

        var without=RoutePlanner.plan(new RoutePlanner.Input(Biome.HAUNTED,ChipsConfig.Mode.UNIQUE,5,List.of(),false,false,false));
        var with=RoutePlanner.plan(new RoutePlanner.Input(Biome.HAUNTED,ChipsConfig.Mode.UNIQUE,5,List.of(),false,false,true));
        check(without.stream().noneMatch(p->SafariData.isHideonwallLocation(Biome.HAUNTED,p)),"Unique toggle removes all eight paintings");
        check(with.stream().filter(p->SafariData.isHideonwallLocation(Biome.HAUNTED,p)).count()==8,"Unique toggle restores fixed set");
        check(with.stream().filter(AtticRouteOrder::attic).count()==1,"Unique attic reduced to one waypoint");
        var money=RoutePlanner.plan(new RoutePlanner.Input(Biome.HAUNTED,ChipsConfig.Mode.MONEY,5,List.of(),false,false));
        check(money.size()==58&&money.getLast().equals(SafariData.GIMMIEGOLD),"Money total/order kept with Gold from start");
        check(SafariData.route(Biome.HAUNTED).stops().contains(SafariData.GIMMIEGOLD),"Gold is in stored Haunted data group");
        var order=RoutePlanner.reorderHaunted(List.of(new Vec3(17,71,-56),room,ritual,SafariData.GIMMIEGOLD),new Vec3(-20,77,-54));
        check(order.size()==4&&order.containsAll(List.of(room,ritual))&&order.getLast().equals(SafariData.GIMMIEGOLD),"relocation planner keeps all pending identities and fixed Gold end");
        check(order.indexOf(room)<order.indexOf(ritual),"house phase stays before underground ritual");
        Vec3 lower=new Vec3(7,49,-24);var withLower=RoutePlanner.reorderHaunted(List.of(lower,room,ritual,SafariData.GIMMIEGOLD),new Vec3(-20,77,-54));
        check(withLower.indexOf(room)<withLower.indexOf(ritual)&&withLower.indexOf(ritual)<withLower.indexOf(lower),"ritual entrance precedes underground floor drops, never strands player below then returns to entrance");
        var dispatcher=new com.mojang.brigadier.CommandDispatcher<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource>();
        for(String root:List.of("sr","chipscritters")){
            ChipsCrittersClient.registerRouteCommands(dispatcher,root);
            for(String text:List.of("skip","skip 1","skip 33","skip 0","skip -1","skip 9999","back","undo","refresh claims all")){
                var parsed=dispatcher.parse(root+" "+text,null);
                check(parsed.getExceptions().isEmpty()&&!parsed.getReader().canRead(),"complete command parses "+text);
            }
        }
        WaypointRunLedger.newRunStarted();
        System.out.println("0.1.59 checks passed: painting suffix safety, cutscene-only tips, consistent numbered jumps, retired profiles/PBs, own Unique catches/inventory IDs, early Gold/restarts/reset, attic reduction and relocation order.");
    }
}

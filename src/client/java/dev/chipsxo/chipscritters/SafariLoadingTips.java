package dev.chipsxo.chipscritters;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class SafariLoadingTips {
    
    private static final SafariTripTips TRIP=new SafariTripTips();
    private static int firstTip;
    private static boolean wasSafari;
    private static String lastVisibility="inactive";
    private static boolean renderedThisTrip;
    private static boolean lastPlacementBlocked;
    public static void onDisconnect(){TRIP.newTrip();wasSafari=false;renderedThisTrip=false;lastVisibility="inactive";}
    private static final List<String> TIPS = List.of(
        "2,500 Salvage Essence buys one Morning Dew.",
        
        "Use /sr rejoin if you get off the route.",
        "Press your warp key to go back to Safari quickly.",
        "Open /chipscritters to change your route mode and display settings.");
    private SafariLoadingTips() { }
    public static void onCommand(String command) {
        if (command != null && command.trim().replaceFirst("^/", "").matches("(?i)warp\\s+.*")) {if(TRIP.active(System.currentTimeMillis()))ChipsCrittersClient.LOGGER.info("Airship tips: ended by another warp at {}",System.currentTimeMillis());TRIP.end("another warp");}
        if (command != null && command.trim().replaceFirst("^/", "").matches("(?i)warp\\s+safari"))
            {TRIP.newTrip();}
    }
    public static void onGameMessage(String text){
        if(!ChipsCrittersClient.isOnHypixel(net.minecraft.client.Minecraft.getInstance()))return;
        if(text.contains("entered Critter Safari") && !SafariTripTips.entry(text))ChipsCrittersClient.LOGGER.info("Airship tips: rejected announcement-like player/non-server message");
        var client=net.minecraft.client.Minecraft.getInstance();
        if(SafariTripTips.entry(text)&&!SafariTripTips.ownEntry(text,client.getUser().getName()))ChipsCrittersClient.LOGGER.info("Airship tips: suppressed party/other player's entry; messageTime={}",System.currentTimeMillis());
        if(SafariTripTips.ownEntry(text,client.getUser().getName())){
            if(TRIP.begin(text,System.currentTimeMillis())){
                firstTip=ThreadLocalRandom.current().nextInt(TIPS.size());lastVisibility="entry received";renderedThisTrip=false;
                ChipsCrittersClient.LOGGER.info("Airship tips: OWN entry accepted at messageTime={}; no camera or locraw requirement",System.currentTimeMillis());
            }else ChipsCrittersClient.LOGGER.debug("Airship tips: repeated entry ignored; trip already active/finished");
        }
        if(SafariTripTips.landingMessage(text)){if(TRIP.active(System.currentTimeMillis()))ChipsCrittersClient.LOGGER.info("Airship tips: ended by confirmed landing/hotspot message: {}",text);TRIP.end();}
    }
    public static void tick(){
        boolean safari=ChipsCrittersClient.isInCritterSafari();
        if(safari&&TRIP.active(System.currentTimeMillis())){ChipsCrittersClient.LOGGER.info("Airship tips: ended on confirmed Safari landing at {}",System.currentTimeMillis());TRIP.end();}
        if(wasSafari&&!safari&&!TRIP.active(System.currentTimeMillis()))TRIP.newTrip();wasSafari=safari;
        reportVisibility();
    }
    private static String reportVisibility(){
        var c=net.minecraft.client.Minecraft.getInstance();
        String reason=TRIP.immediateVisibility(System.currentTimeMillis(),ChipsConfig.get().safariLoadingTips,ChipsCrittersClient.isOnHypixel(c),c.player!=null);
        if(!reason.equals(lastVisibility)){
            ChipsCrittersClient.LOGGER.info("Airship tips: {}; spectator={} hudHidden={} safari={} previousLobbyKnownElsewhere={}",reason,c.player!=null&&c.player.isSpectator(),c.gui.hud.isHidden(),ChipsCrittersClient.isInCritterSafari(),ChipsCrittersClient.knownOutsideSafari());lastVisibility=reason;
        }
        return reason;
    }
    private static void renderTrip(net.minecraft.client.gui.GuiGraphicsExtractor graphics,net.minecraft.client.DeltaTracker delta){
        var c=net.minecraft.client.Minecraft.getInstance();long now=System.currentTimeMillis();
        if(!reportVisibility().equals("show"))return;
        if(c.gui.screen() instanceof ChipsHudPositionScreen)return;
        int w=c.getWindow().getGuiScaledWidth(),h=c.getWindow().getGuiScaledHeight();
        var lines=c.font.split(net.minecraft.network.chat.Component.literal(TIPS.get((firstTip+TRIP.tipStep(now))%TIPS.size())),Math.max(40,w-32));
        int th=lines.size()*10+12;
        var avoid=new java.util.ArrayList<TipPlacement.Rect>();
        boolean focused=c.gui.hud.getChat().isChatFocused();
        int chatWidth=(int)Math.ceil(net.minecraft.client.gui.components.ChatComponent.getWidth(c.options.chatWidth().get())*c.options.chatScale().get())+12;
        int chatHeight=(int)Math.ceil(net.minecraft.client.gui.components.ChatComponent.getHeight((focused?c.options.chatHeightFocused():c.options.chatHeightUnfocused()).get())*c.options.chatScale().get())+18;
        avoid.add(new TipPlacement.Rect(0,Math.max(0,h-40-chatHeight),Math.min(w,chatWidth),chatHeight+40));
        avoid.add(new TipPlacement.Rect(0,h-80,w,40));
        var arrow=RouteHud.tipObstacle(c);if(arrow!=null)avoid.add(arrow);
        var place=ChipsConfig.get().airshipBannerPosition<0?TipPlacement.bar(w,h,th,avoid):new TipPlacement.Rect(0,BannerPosition.y(ChipsConfig.get().airshipBannerPosition,h,th),w,Math.min(th,h));
        if(place==null){if(!lastPlacementBlocked){ChipsCrittersClient.LOGGER.info("Airship tip temporarily suppressed: no clear space around expanded chat/action bar/arrow");lastPlacementBlocked=true;}return;}
        lastPlacementBlocked=false;drawBar(graphics,c,place,lines);
        if(!renderedThisTrip){renderedThisTrip=true;ChipsCrittersClient.LOGGER.info("Airship tips: firstRenderTime={}, after {}ms; unconditional HUD lane (hotbar independent)",now,TRIP.elapsed(now));}
    }
    static String previewTip(){return TIPS.stream().max(java.util.Comparator.comparingInt(String::length)).orElse("Airship tip preview");}
    static int previewHeight(net.minecraft.client.Minecraft c,int width){return c.font.split(net.minecraft.network.chat.Component.literal(previewTip()),Math.max(40,width-32)).size()*10+12;}
    static int previewY(net.minecraft.client.Minecraft c,int width,int height){
        int barHeight=previewHeight(c,width);double saved=ChipsConfig.get().airshipBannerPosition;
        if(saved>=0)return BannerPosition.y(saved,height,barHeight);
        boolean focused=c.gui.hud.getChat().isChatFocused();
        int chatHeight=(int)Math.ceil(net.minecraft.client.gui.components.ChatComponent.getHeight((focused?c.options.chatHeightFocused():c.options.chatHeightUnfocused()).get())*c.options.chatScale().get())+18;
        var avoid=new java.util.ArrayList<TipPlacement.Rect>();avoid.add(new TipPlacement.Rect(0,Math.max(0,height-40-chatHeight),width,chatHeight+40));avoid.add(new TipPlacement.Rect(0,height-80,width,40));
        // Editor always shows the arrow preview; do not hide the banner preview if no clear automatic lane exists.
        var arrow=RouteHud.tipObstacle(c);if(arrow!=null)avoid.add(arrow);
        var automatic=TipPlacement.bar(width,height,barHeight,avoid);return automatic==null?Math.max(0,Math.min(height-barHeight,height/4)):automatic.y();
    }
    static void renderPreview(net.minecraft.client.gui.GuiGraphicsExtractor g,net.minecraft.client.Minecraft c,int width,int height,int y){
        var lines=c.font.split(net.minecraft.network.chat.Component.literal(previewTip()),Math.max(40,width-32));int barHeight=Math.min(height,lines.size()*10+12);
        drawBar(g,c,new TipPlacement.Rect(0,Math.max(0,Math.min(y,height-barHeight)),width,barHeight),lines);
    }
    private static void drawBar(net.minecraft.client.gui.GuiGraphicsExtractor g,net.minecraft.client.Minecraft c,TipPlacement.Rect place,java.util.List<net.minecraft.util.FormattedCharSequence> lines){
        g.enableScissor(0,place.y(),place.w(),place.y()+place.h());g.fill(0,place.y(),place.w(),place.y()+place.h(),0xDC17232E);g.fill(0,place.y(),place.w(),place.y()+1,UiTheme.ACCENT);
        int y=place.y()+5;for(var line:lines){g.text(c.font,line,(place.w()-c.font.width(line))/2,y,UiTheme.TEXT,false);y+=10;}g.disableScissor();
    }
    public static void register() {
        net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath(ChipsCrittersClient.MOD_ID,"airship_tips"),SafariLoadingTips::renderTrip);
    }
}

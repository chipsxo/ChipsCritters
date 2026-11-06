package dev.chipsxo.chipscritters;
import java.util.*;import net.minecraft.client.gui.screens.Screen;import net.minecraft.client.gui.GuiGraphicsExtractor;import net.minecraft.client.gui.components.Button;import net.minecraft.network.chat.Component;
/** Nothing is changed until the explicit Restore button is pressed. */
public final class ConfirmClaimsScreen extends ChipsScreen {
 private final ConfirmationGate confirmation=new ConfirmationGate();
 private final Screen parent;private final Biome biome;private boolean all,captures;
 public ConfirmClaimsScreen(Screen parent,Biome biome,boolean all){super(Component.literal("Restore reached waypoints?"));this.parent=parent;this.biome=biome;this.all=all;}
 @Override protected void init(){int buttonWidth=Math.min(300,Math.max(80,width-16));int x=(width-buttonWidth)/2,y=Math.max(55,height/2-35);
  addRenderableWidget(UiButton.builder(Component.literal("Scope: "+(all?"all biomes":biome==null?"no biome selected":biome.displayName())),b->{all=!all;b.setMessage(Component.literal("Scope: "+(all?"all biomes":biome==null?"no biome selected":biome.displayName())));}).bounds(x,y,buttonWidth,20).build());
  addRenderableWidget(UiButton.builder(Component.literal("Reset catch state: OFF"),b->{captures=!captures;b.setMessage(Component.literal("Reset catch state: "+(captures?"ON":"OFF")));}).bounds(x,y+24,buttonWidth,20).build());
  addRenderableWidget(UiButton.builder(Component.literal("Restore waypoints (confirm)"),b->{if(!all&&biome==null){RouteController.commandFeedback("Select all biomes or enter a biome first.");return;}confirmation.confirm(()->RouteController.restoreClaims(all?EnumSet.allOf(Biome.class):EnumSet.of(biome),captures));onClose();}).bounds(x,y+52,buttonWidth,20).build());
  addRenderableWidget(UiButton.builder(Component.literal("Cancel"),b->onClose()).bounds(x,y+78,buttonWidth,20).build());
 }
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){
        super.extractRenderState(g,mx,my,d);
	}
 @Override protected String subtitle(){return "Timer kept. Restored runs cannot earn PBs. Relay claims stay local for this scope; catches are kept unless reset is ON.";}
 @Override public void onClose(){confirmation.cancel();if(minecraft!=null)minecraft.setScreenAndShow(parent);}
}

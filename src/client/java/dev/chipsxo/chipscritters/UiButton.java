package dev.chipsxo.chipscritters;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
final class UiButton extends Button {
 boolean selected;private String tooltipText="";
 UiButton(int x,int y,int w,int h,Component text,OnPress action){super(x,y,w,h,text,action,DEFAULT_NARRATION);}
 public static Builder builder(Component text,OnPress action){return new Builder(text,action);}
 public static class Builder extends Button.Builder {
  private final Component text;private final OnPress action;private int x,y,w=120,h=20;
  Builder(Component text,OnPress action){super(text,action);this.text=text;this.action=action;}
  @Override public Builder bounds(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;return this;}
  @Override public Button build(){return new UiButton(x,y,w,h,text,action);}
 }
 @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
  var font=Minecraft.getInstance().font;boolean hot=isHoveredOrFocused();int x=getX(),y=getY(),w=getWidth(),h=getHeight();
  UiTheme.card(g,x,y,w,h,hot?UiTheme.HOVER:UiTheme.ROW);
  if(selected||isFocused())g.fill(x+2,y+h-2,x+w-2,y+h,UiTheme.ACCENT);
  String full=getMessage().getString();int color=!active?0xFF667B89:UiTheme.TEXT;
  boolean toggle=full.endsWith(": ON")||full.endsWith(": OFF");
  if(toggle){int split=full.lastIndexOf(':');String value=full.substring(split+2);UiTheme.line(g,font,full.substring(0,split),x+8,y+(h-8)/2,w-48,color);g.text(font,value,x+w-8-font.width(value),y+(h-8)/2,value.equals("ON")?UiTheme.ACCENT:UiTheme.MUTED,false);}
  else{String shown=UiTheme.fit(font,full,w-12);int c=full.toLowerCase(java.util.Locale.ROOT).contains("confirm")?UiTheme.WARNING:color;g.text(font,shown,x+(w-font.width(shown))/2,y+(h-8)/2,c,false);}
  if(!full.equals(tooltipText)){setTooltip(Tooltip.create(getMessage()));tooltipText=full;}
 }
}

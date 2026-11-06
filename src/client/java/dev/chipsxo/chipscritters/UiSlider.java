package dev.chipsxo.chipscritters;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
abstract class UiSlider extends AbstractSliderButton {
 UiSlider(int x,int y,int w,int h,Component text,double value){super(x,y,w,h,text,value);}
 @Override public void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
  int x=getX(),y=getY(),w=getWidth(),h=getHeight();var f=Minecraft.getInstance().font;
  UiTheme.card(g,x,y,w,h,isHoveredOrFocused()?UiTheme.HOVER:UiTheme.ROW);
  g.fill(x+4,y+h-3,x+w-4,y+h-2,UiTheme.BORDER);g.fill(x+4,y+h-3,x+4+(int)((w-8)*value),y+h-2,UiTheme.ACCENT);
  String text=UiTheme.fit(f,getMessage().getString(),w-16);g.text(f,text,x+(w-f.width(text))/2,y+(h-8)/2,UiTheme.TEXT,false);
 }
}

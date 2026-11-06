package dev.chipsxo.chipscritters;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
final class UiTheme {
 static final int BG=0xF0111A23,PANEL=0xDC17232E,ROW=0xFF1B2B37,HOVER=0xFF273F4C,BORDER=0xFF314654;
 static final int TEXT=0xFFE8F0F5,MUTED=0xFFA6B7C4,ACCENT=0xFF7DE8C3,WARNING=0xFFFFCE78,DANGER=0xFFFF8585;
 static void card(GuiGraphicsExtractor g,int x,int y,int w,int h,int color){
  if(w<4||h<4)return;g.fill(x+2,y,x+w-2,y+h,color);g.fill(x,y+2,x+w,y+h-2,color);
 }
 static String fit(Font f,String s,int width){if(s==null||width<=0)return "";if(f.width(s)<=width)return s;String out=f.plainSubstrByWidth(s,Math.max(0,width-f.width("…")));return out+"…";}
 static void line(GuiGraphicsExtractor g,Font f,String text,int x,int y,int width,int color){g.text(f,fit(f,text,width),x,y,color,false);}
}

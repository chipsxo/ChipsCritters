package dev.chipsxo.chipscritters;
import java.util.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
/** Shared shell and responsive widget viewport. Settings/actions retain their existing callbacks. */
abstract class ChipsScreen extends Screen {
 private record Natural(AbstractWidget widget,int x,int y,int width,int height,boolean tab,boolean footer){}
 private final List<Natural> natural=new ArrayList<>();private Object first;private int widgetCount,scroll,contentHeight;
 protected ChipsScreen(Component title){super(title);}
 protected boolean canvas(){return this instanceof ChipsHudPositionScreen;}
 protected int contentTop(){return UiViewport.top(height,this instanceof ConfirmClaimsScreen||this instanceof ConfirmResetScreen);}
 protected int contentBottom(){return height-40;}
 protected int contentY(int y){return contentTop()+y-scroll;}
 private int panelWidth(){return Math.min(UiViewport.panelWidth(width),this instanceof PersonalBestsScreen?520:(this instanceof LearnedStopsScreen||this instanceof RelaySettingsScreen)?424:360);}
 protected int contentWidth(){return panelWidth()-24;}
 protected int contentLeft(){return (width-panelWidth())/2+12;}
 protected void virtualContentHeight(int h){contentHeight=Math.max(contentHeight,h);}
 protected void bodyNote(GuiGraphicsExtractor g,String value,int offset,int color){
  int y=contentY(offset),x=contentLeft();g.enableScissor(x,contentTop(),x+contentWidth(),contentBottom());
  for(var line:font.split(Component.literal(value),contentWidth()-8)){g.text(font,line,x+4,y,color,false);y+=11;}
  virtualContentHeight(offset+y-contentY(offset)+10);g.disableScissor();
 }
 protected String subtitle(){return "Local preferences · /sr opens this menu";}
 protected boolean tabSelected(String name){return false;}
 private void layout(){
  var widgets=children().stream().filter(x->x instanceof AbstractWidget).map(x->(AbstractWidget)x).toList();
  if(widgets.isEmpty())return;
  if(first!=widgets.getFirst()||widgetCount!=widgets.size()){
   natural.clear();first=widgets.getFirst();widgetCount=widgets.size();scroll=0;contentHeight=0;
   var rows=widgets.stream().filter(w->!footer(w)&&!tab(w)).map(AbstractWidget::getY).distinct().sorted().toList();
   for(var w:widgets){int y=canvas()?w.getY():Math.max(0,rows.indexOf(w.getY()))*26;natural.add(new Natural(w,w.getX(),y,w.getWidth(),canvas()?w.getHeight():Math.max(22,w.getHeight()),tab(w),footer(w)));}
  }
  int span=natural.stream().filter(n->!n.tab&&!n.footer).mapToInt(n->n.x+n.width).max().orElse(width/2+120)-natural.stream().filter(n->!n.tab&&!n.footer).mapToInt(n->n.x).min().orElse(width/2-120);
  double sx=UiViewport.horizontalScale(contentWidth(),span);int minX=natural.stream().filter(n->!n.tab&&!n.footer).mapToInt(n->n.x).min().orElse(width/2-120);
  int tabIndex=0;int tabCount=(int)natural.stream().filter(Natural::tab).count();
  for(var n:natural){var w=n.widget;
   if(n.tab){int tw=Math.max(20,contentWidth()/Math.max(1,tabCount));w.setX(contentLeft()+tabIndex++*tw);w.setY(48);w.setWidth(tw-3);if(w instanceof UiButton b)b.selected=tabSelected(w.getMessage().getString());}
   else if(n.footer){w.setWidth(Math.min(180,contentWidth()));w.setX((width-w.getWidth())/2);w.setY(height-30);}
   else if(!canvas()){
    w.setX(contentLeft()+(int)Math.round((n.x-minX)*sx)+(int)((contentWidth()-span*sx)/2));w.setWidth(Math.max(24,(int)Math.round(n.width*sx)));
    w.setHeight(n.height);w.setY(contentY(n.y));contentHeight=Math.max(contentHeight,n.y+n.height+8);
    w.visible=UiViewport.visible(w.getY(),w.getHeight(),contentTop(),contentBottom());
   }else{w.setWidth(Math.min(w.getWidth(),width-16));w.setX(UiViewport.clampX(w.getX(),w.getWidth(),width));}
  }
 }
 private boolean footer(AbstractWidget w){String s=w.getMessage().getString();return s.equals("Done")||s.equals("Back")||s.equals("Cancel");}
 private boolean tab(AbstractWidget w){return this instanceof ChipsSettingsScreen&&Set.of("Routes","Display","Tools","Unique").contains(w.getMessage().getString());}
 @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){
  if(canvas())return;g.fill(0,0,width,height,0x78080D12);int pw=panelWidth();UiTheme.card(g,(width-pw)/2,8,pw,height-16,UiTheme.BG);
 }
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
  layout();super.extractRenderState(g,mx,my,delta);
  for(var n:natural)if(n.widget instanceof EditBox e&&e.visible){int x=e.getX(),y=e.getY(),w=e.getWidth(),h=e.getHeight(),c=e.isFocused()?UiTheme.ACCENT:UiTheme.BORDER;g.fill(x-1,y-1,x+w+1,y,c);g.fill(x-1,y+h,x+w+1,y+h+1,c);g.fill(x-1,y,x,y+h,c);g.fill(x+w,y,x+w+1,y+h,c);}
  if(canvas())return;
  int x=contentLeft();UiTheme.line(g,font,"CHIPSCRITTERS",x,19,contentWidth()-60,UiTheme.ACCENT);
  g.text(font,"0.1.65",width-contentLeft()-font.width("0.1.65"),19,UiTheme.MUTED,false);
  String timer=RouteController.active()?RunHistory.formatDuration(RouteController.elapsedMillis()):"";
  UiTheme.line(g,font,getTitle().getString().replace("ChipsCritters — ",""),x,33,contentWidth()-font.width(timer)-8,UiTheme.TEXT);
  if(!timer.isBlank())g.text(font,timer,width-contentLeft()-font.width(timer),33,UiTheme.ACCENT,false);
  if(!(this instanceof ChipsSettingsScreen)){int y=50;for(var line:font.split(Component.literal(subtitle()),contentWidth())){if(y>=contentTop()-8)break;g.text(font,line,x,y,UiTheme.MUTED,false);y+=10;}}
  if(contentHeight>contentBottom()-contentTop()){
   int bh=contentBottom()-contentTop(),thumb=Math.max(12,bh*bh/contentHeight),max=Math.max(1,contentHeight-bh);
   int sy=contentTop()+scroll*(bh-thumb)/max;g.fill(width-contentLeft()+4,contentTop(),width-contentLeft()+6,contentBottom(),UiTheme.ROW);g.fill(width-contentLeft()+4,sy,width-contentLeft()+6,sy+thumb,UiTheme.ACCENT);
   UiTheme.line(g,font,"Scroll for more",x,height-16,contentWidth()/2,UiTheme.MUTED);
  }
 }
 @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
  if(canvas())return super.mouseScrolled(mx,my,horizontal,vertical);
  int next=UiViewport.clampScroll(scroll-(int)Math.round(vertical*24),contentHeight,contentBottom()-contentTop());
  if(next!=scroll){scroll=next;layout();return true;}return super.mouseScrolled(mx,my,horizontal,vertical);
 }
 @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event){
  if(!canvas()&&(event.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN||event.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP)){
   int direction=event.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN?1:-1;
   scroll=UiViewport.clampScroll(scroll+direction*Math.max(24,contentBottom()-contentTop()-20),contentHeight,contentBottom()-contentTop());layout();return true;
  }
  return super.keyPressed(event);
 }
}

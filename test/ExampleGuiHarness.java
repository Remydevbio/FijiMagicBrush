import ij.*;import ij.gui.*;import ij.io.*;import ij.plugin.PlugIn;import ij.process.*;
import java.awt.*;import java.awt.event.*;import java.awt.geom.*;import java.nio.file.*;

/** Native GUI checks on the supplied multichannel image, explicitly channel 2. */
public class ExampleGuiHarness implements PlugIn {
 static final String ROOT="/home/rbonnav/Documents/Codex/2026-09-13-brushtoolFiji";Robot robot;ImagePlus imp;ImageCanvas canvas;StringBuilder out=new StringBuilder();int passed;
 public void run(String arg){new Thread(()->{try{test();}catch(Throwable e){out.append("FAIL "+e+"\n");e.printStackTrace();}finally{try{Files.write(Paths.get(ROOT,"build/example-gui-results.txt"),out.toString().getBytes());}catch(Exception e){e.printStackTrace();}}},"example GUI verification").start();}
 void ui(Runnable r)throws Exception{EventQueue.invokeAndWait(r);}void idle()throws Exception{robot.waitForIdle();Thread.sleep(180);}void ok(boolean b,String m){if(!b)throw new AssertionError(m);out.append("PASS "+m+"\n");passed++;}
 Point screen(int x,int y){Point p=canvas.getLocationOnScreen();int sx=canvas.screenX(x),sy=canvas.screenY(y);for(int d=-5;d<=5;d++)if(canvas.offScreenX(sx+d)==x){sx+=d;break;}for(int d=-5;d<=5;d++)if(canvas.offScreenY(sy+d)==y){sy+=d;break;}return new Point(p.x+sx,p.y+sy);}
 void click(int x,int y)throws Exception{Point p=screen(x,y);robot.mouseMove(p.x,p.y);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);for(int i=0;i<150;i++){Thread.sleep(30);final boolean[] done={false};ui(()->done[0]=Intensity_Selection_Tools.instance.gesture==null);if(done[0])return;}throw new AssertionError("worker timeout");}
 Area area(){return imp.getRoi()==null?new Area():Intensity_Selection_Tools.area(imp.getRoi());}void samePixels(Area a,String m){Area b=area();int n=0;Rectangle u=a.getBounds().union(b.getBounds());for(int y=u.y;y<u.y+u.height;y++)for(int x=u.x;x<u.x+u.width;x++)if(a.contains(x+.5,y+.5)!=b.contains(x+.5,y+.5))n++;ok(n==0,m+" (different pixels="+n+")");}
 void clear()throws Exception{robot.keyPress(KeyEvent.VK_CONTROL);robot.keyPress(KeyEvent.VK_Z);robot.keyRelease(KeyEvent.VK_Z);robot.keyRelease(KeyEvent.VK_CONTROL);idle();}
 void test()throws Exception{
  robot=new Robot();robot.setAutoDelay(30);String dir=ROOT+"/test-images/example multichannel/";
  ui(()->{imp=IJ.openImage(dir+"example_multichannel.tif");imp.setC(2);imp.show();canvas=imp.getCanvas();imp.getWindow().setLocation(20,40);new Intensity_Selection_Tools().run("");});Thread.sleep(600);
  ui(()->{Toolbar.getInstance().setTool("Intensity Smart Brush Tool");imp.getWindow().toFront();canvas.requestFocus();Intensity_Selection_Tools.instance.settings.channel=0;Intensity_Selection_Tools.instance.settings.diameter=149;Intensity_Selection_Tools.instance.settings.sigma=2;Intensity_Selection_Tools.instance.settings.sensitivity=3;Intensity_Selection_Tools.instance.settings.absolute=false;});idle();
  click(648,349);Area baseline=area();ok(imp.getC()==2,"channel 2 is active");ok(!baseline.isEmpty(),"channel-2 brush creates a selection");
  Area overlap=new Area(baseline);try{overlap.intersect(Intensity_Selection_Tools.area(new RoiDecoder(dir+"example_multichannel.roi").getRoi()));}catch(Exception e){throw new RuntimeException(e);}ok(!overlap.isEmpty(),"channel-2 selection overlaps supplied rough ROI");
  Rectangle view=new Rectangle(canvas.getSrcRect()),window=imp.getWindow().getBounds();robot.keyPress(KeyEvent.VK_ALT);click(648,349);robot.keyRelease(KeyEvent.VK_ALT);ok(canvas.getSrcRect().equals(view),"Alt-click does not pan or change viewport");ok(imp.getWindow().getBounds().equals(window),"Alt-click does not move image window");
  clear();ui(()->{imp.getProcessor().setMinAndMax(50,100);imp.updateAndDraw();});idle();click(648,349);samePixels(baseline,"brightness/LUT display range does not change channel-2 mask");clear();
  for(int i=0;i<3;i++)ui(()->canvas.zoomIn(canvas.screenX(648),canvas.screenY(349)));idle();click(648,349);samePixels(baseline,"high zoom keeps exact channel-2 mask");
  out.append("Completed "+passed+" supplied-image GUI assertions at magnification "+canvas.getMagnification()+"\n");
 }
}

import ij.*;import ij.gui.*;import ij.plugin.PlugIn;
import java.awt.*;import java.awt.event.*;import java.awt.geom.*;import java.nio.file.*;import java.util.concurrent.*;import javax.imageio.ImageIO;
/** Runs inside real Fiji. Setup uses APIs; tested gestures use native Robot events. */
public class GuiHarness implements PlugIn {
 static final String ROOT="/home/rbonnav/Documents/Codex/2026-09-13-brushtoolFiji";
 Robot robot;ImagePlus imp;ImageCanvas canvas;StringBuilder report=new StringBuilder();int passed;
 public void run(String arg){new Thread(()->{try{runTests();}catch(Throwable e){report.append("FAIL "+e+"\n");e.printStackTrace();}finally{try{Files.write(Paths.get(ROOT,"build/gui-results.txt"),report.toString().getBytes());}catch(Exception e){e.printStackTrace();}}},"GUI verification").start();}
 void ui(Runnable r)throws Exception{EventQueue.invokeAndWait(r);}
 void waitIdle()throws Exception{robot.waitForIdle();Thread.sleep(160);}
 void done()throws Exception{for(int i=0;i<150;i++){Thread.sleep(40);final boolean[] done={false};ui(()->done[0]=Intensity_Selection_Tools.instance.gesture==null);if(done[0])return;}throw new AssertionError("worker did not finish");}
 void ok(boolean b,String m){if(!b)throw new AssertionError(m);report.append("PASS "+m+"\n");passed++;}
 Point screen(int x,int y){Point p=canvas.getLocationOnScreen();int sx=canvas.screenX(x),sy=canvas.screenY(y);for(int d=-3;d<=3;d++)if(canvas.offScreenX(sx+d)==x){sx+=d;break;}for(int d=-3;d<=3;d++)if(canvas.offScreenY(sy+d)==y){sy+=d;break;}return new Point(p.x+sx,p.y+sy);}
 void move(int x,int y){Point p=screen(x,y);robot.mouseMove(p.x,p.y);}
 void press(int x,int y){move(x,y);robot.mousePress(1024);}
 void release(){robot.mouseRelease(1024);}
 void click(int x,int y)throws Exception{press(x,y);release();done();}
 void key(int...keys){for(int k:keys)robot.keyPress(k);for(int i=keys.length-1;i>=0;i--)robot.keyRelease(keys[i]);}
 Area roi(){return imp.getRoi()==null?new Area():Intensity_Selection_Tools.area(imp.getRoi());}
 void same(Area a,String m){Area diff=new Area(a);diff.exclusiveOr(roi());ok(diff.isEmpty(),m);}
 void samePixels(Area a,String m){Area b=roi();int differences=0;for(int y=0;y<imp.getHeight();y++)for(int x=0;x<imp.getWidth();x++)if(a.contains(x+.5,y+.5)!=b.contains(x+.5,y+.5))differences++;ok(differences==0,m+" (different source pixels="+differences+")");}
 void runTests()throws Exception{
  robot=new Robot();robot.setAutoDelay(25);
  ui(()->{imp=Synthetic.make(8);imp.show();canvas=imp.getCanvas();imp.getWindow().setLocation(20,40);new Intensity_Selection_Tools().run("");});Thread.sleep(500);
  ui(()->{Toolbar.getInstance().setTool("Intensity Smart Brush Tool");imp.getWindow().toFront();canvas.requestFocus();Intensity_Selection_Tools.instance.settings.sigma=0;Intensity_Selection_Tools.instance.settings.absolute=true;Intensity_Selection_Tools.instance.settings.tolerance=10;});waitIdle();
  ok(Intensity_Selection_Tools.instance.active(),"plugin tool is selected");click(125,190);ok(roi().contains(125,190),"smart seed selected");ok(!roi().contains(165,190),"dark hole excluded");ok(!roi().contains(60,190),"background excluded");ok(roi().getBounds2D().getWidth()<=149.1,"bounded footprint");
  Area firstDab=roi();click(190,190);ok(roi().contains(125,190),"inside-ROI dab preserves existing selection");ok(roi().getBounds2D().getMaxX()>firstDab.getBounds2D().getMaxX(),"inside-ROI dab unions and expands selection");
  Area a=roi();report.append("History before undo: "+Intensity_Selection_Tools.instance.history.canUndo()+"\n");key(17,90);waitIdle();same(firstDab,"Ctrl+Z restores previous complete ROI");key(17,89);waitIdle();same(a,"Ctrl+Y restores full gesture");
  robot.keyPress(16);click(365,190);robot.keyRelease(16);ok(roi().contains(125,190)&&roi().contains(365,190),"Shift adds disconnected region");
  robot.keyPress(17);click(365,190);robot.keyRelease(17);ok(roi().contains(125,190)&&!roi().contains(365,190),"Ctrl subtracts (Alt is reserved by this window manager)");
  a=roi();press(330,190);move(360,190);Thread.sleep(180);key(27);release();waitIdle();same(a,"Escape restores starting ROI");
  double d=Intensity_Selection_Tools.instance.settings.diameter,m=canvas.getMagnification();move(120,190);robot.keyPress(KeyEvent.VK_Q);robot.mouseWheel(-2);robot.keyRelease(KeyEvent.VK_Q);waitIdle();ok(Intensity_Selection_Tools.instance.settings.diameter>d,"Q wheel resizes");ok(canvas.getMagnification()==m,"Q wheel does not zoom");
  a=roi();robot.keyPress(32);press(100,100);move(150,150);robot.keyRelease(32);move(200,200);release();waitIdle();same(a,"Space before drag and release mid-pan never paint");
  a=roi();press(340,180);move(365,190);Thread.sleep(120);robot.keyPress(32);move(380,200);robot.keyRelease(32);move(390,210);release();waitIdle();same(a,"Space during stroke cancels and prevents joining");
  ui(()->{Toolbar.getInstance().setTool("Selection Brush Tool");Intensity_Selection_Tools.instance.settings.diameter=30;});waitIdle();press(60,80);move(400,80);release();done();ok(roi().contains(230,80),"fast brush drag has no gaps");
  key(17,90);waitIdle();same(a,"one undo removes entire fast stroke");
  ui(()->{Toolbar.getInstance().setTool("Intensity Smart Brush Tool");Intensity_Selection_Tools.instance.settings.diameter=120;Intensity_Selection_Tools.instance.settings.sigma=0;Intensity_Selection_Tools.instance.settings.absolute=true;Intensity_Selection_Tools.instance.settings.tolerance=10;imp.deleteRoi();Intensity_Selection_Tools.instance.expected=null;Intensity_Selection_Tools.instance.history.reset(null);});waitIdle();
  click(125,190);report.append("source seed A="+Intensity_Selection_Tools.instance.lastInputX+","+Intensity_Selection_Tools.instance.lastInputY+"\n");Area mask1x=roi();key(17,90);waitIdle();
  ui(()->{canvas.zoomIn(canvas.screenX(125),canvas.screenY(190));canvas.requestFocus();});waitIdle();click(125,190);report.append("source seed B="+Intensity_Selection_Tools.instance.lastInputX+","+Intensity_Selection_Tools.instance.lastInputY+"\n");samePixels(mask1x,"same source-pixel mask at adjacent zoom levels");key(17,90);waitIdle();
  ui(()->{Toolbar.getInstance().setTool("Selection Brush Tool");Intensity_Selection_Tools.instance.settings.diameter=30;});waitIdle();
  ui(()->{canvas.zoomIn(200,100);canvas.requestFocus();});waitIdle();click(180,150);ok(Math.abs(roi().getBounds2D().getWidth()-30)<.01,"zoom preserves image-coordinate brush diameter");
  a=roi();m=canvas.getMagnification();move(180,150);robot.keyPress(17);robot.mouseWheel(-1);robot.keyRelease(17);waitIdle();ok(canvas.getMagnification()>m,"ordinary Ctrl wheel retains native zoom");same(a,"zoom preserves ROI coordinates");
  waitIdle();ok(Math.abs(Intensity_Selection_Tools.instance.cursor.getFloatBounds().width-Intensity_Selection_Tools.instance.settings.diameter)<.01,"cursor image diameter remains stable after zoom");
  double oldDiameter=Intensity_Selection_Tools.instance.settings.diameter;robot.keyPress(KeyEvent.VK_Q);waitIdle();
  ui(()->Toolkit.getDefaultToolkit().getSystemEventQueue().postEvent(new MouseWheelEvent(canvas,MouseEvent.MOUSE_WHEEL,System.currentTimeMillis(),0,50,50,50,50,0,false,MouseWheelEvent.WHEEL_UNIT_SCROLL,3,0,.25)));waitIdle();robot.keyRelease(KeyEvent.VK_Q);
  ok(Intensity_Selection_Tools.instance.settings.diameter<oldDiameter,"fractional trackpad wheel event resizes");
  Area focusBefore=roi();press(180,150);move(190,150);Thread.sleep(100);
  Frame fieldFrame=new Frame("Selection tools focus test");TextField field=new TextField();
  ui(()->{fieldFrame.add(field);fieldFrame.setBounds(50,50,250,100);fieldFrame.setVisible(true);fieldFrame.toFront();field.requestFocus();});waitIdle();release();key(KeyEvent.VK_Q);waitIdle();
  ok(field.getText().equals("q"),"text field receives Q normally");ok(!Intensity_Selection_Tools.instance.resize&&!Intensity_Selection_Tools.instance.space,"focus loss clears held keys");same(focusBefore,"focus loss cancels preview");ui(()->{fieldFrame.dispose();imp.getWindow().toFront();canvas.requestFocus();});waitIdle();
  ui(()->{while(canvas.getMagnification()>1)canvas.zoomOut(100,100);});waitIdle();
  for(int type:new int[]{16,32,24}){
   ui(()->{imp=Synthetic.make(type);imp.show();canvas=imp.getCanvas();imp.getWindow().setLocation(20,40);imp.getWindow().toFront();canvas.requestFocus();Toolbar.getInstance().setTool("Intensity Smart Brush Tool");Intensity_Selection_Tools.instance.settings.diameter=149;Intensity_Selection_Tools.instance.settings.absolute=false;Intensity_Selection_Tools.instance.settings.sigma=0;});waitIdle();click(125,190);ok(roi().contains(125,190)&&!roi().contains(165,190),"native raw smart selection type "+type);
  }
  ui(()->{imp=IJ.openImage(ROOT+"/test-images/composite.tif");imp.show();canvas=imp.getCanvas();imp.getWindow().setLocation(20,40);imp.getWindow().toFront();canvas.requestFocus();});waitIdle();click(125,190);ok(!roi().contains(165,190),"composite active bright channel respects hole");
  ui(()->imp.setC(2));waitIdle();click(125,190);ok(roi().contains(165,190),"composite active blank channel changes sampling");
  Area before=roi();press(250,200);move(270,200);Thread.sleep(100);ui(()->imp.setC(1));release();waitIdle();same(before,"plane/channel switch cancels pending result");
  ui(()->{imp.getWindow().toFront();canvas.requestFocus();});waitIdle();
  ui(()->{canvas.setSourceRect(new Rectangle(50,50,256,192));canvas.requestFocus();});waitIdle();
  Area panBefore=roi();Rectangle viewBefore=new Rectangle(canvas.getSrcRect());
  robot.keyPress(32);press(150,130);move(120,110);release();robot.keyRelease(32);waitIdle();
  ok(canvas.getSrcRect().x>viewBefore.x&&canvas.getSrcRect().y>viewBefore.y,"Space drag actually pans a cropped viewport");same(panBefore,"actual pan preserves original-coordinate ROI");
  Rectangle bounds=imp.getWindow().getBounds();ImageIO.write(robot.createScreenCapture(bounds),"png",Paths.get(ROOT,"build/fiji-verified.png").toFile());
  report.append("Completed "+passed+" native GUI assertions in Fiji / ImageJ "+IJ.getVersion()+"; display transform "+canvas.getGraphicsConfiguration().getDefaultTransform()+"\n");
 }
}

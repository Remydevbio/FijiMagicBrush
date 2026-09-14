import ij.*;
import ij.gui.*;
import ij.plugin.PlugIn;
import ij.plugin.tool.PlugInTool;
import ij.process.ImageProcessor;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.concurrent.*;
import javax.swing.Timer;

/** ImageJ extension: intercept only events owned by the selected plugin tool. */
public class Intensity_Selection_Tools implements PlugIn {
    static Controller instance;
    public void run(String arg){EventQueue.invokeLater(()->{
        if("stop".equals(arg)){if(instance!=null){instance.close();instance=null;}return;}
        if(instance==null)instance=new Controller();
        Toolbar.addPlugInTool(new ModeTool(false));Toolbar.addPlugInTool(new ModeTool(true));
    });}
    static class ModeTool extends PlugInTool {
        final boolean smart;ModeTool(boolean smart){this.smart=smart;}
        public String getToolName(){return smart?"Intensity Smart Brush Tool":"Selection Brush Tool";}
        public String getToolIcon(){return smart?"C037T5f16W":"C037T5f16B";}
        public void showOptionsDialog(){if(instance!=null)instance.options();}
    }
    static class Controller extends EventQueue implements CommandListener, KeyEventDispatcher {
        final SelectionEngine.Settings settings=new SelectionEngine.Settings();
        final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Intensity selection");t.setDaemon(true);return t;});
        final SelectionHistory history=new SelectionHistory(); final Timer timer;
        ImagePlus image;ImageCanvas canvas;Roi expected,cursor;Overlay cursorOverlay;
        int plane,resizeKey=KeyEvent.VK_Q;boolean space,resize,held,pan,blocked,closed,updating;
        int px,py,lastInputX,lastInputY;Point pointer=new Point();Gesture gesture;long serial;volatile double lastLatency;double cursorMag;
        volatile RoiDiagnostics.Counts lastRoiDiagnostics;
        Controller(){Toolkit.getDefaultToolkit().getSystemEventQueue().push(this);Executer.addCommandListener(this);KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);timer=new Timer(60,e->check());timer.start();}
        boolean active(){return Toolbar.getPlugInTool() instanceof ModeTool;}
        boolean smart(){return active()&&((ModeTool)Toolbar.getPlugInTool()).smart;}
        void check(){
            if(closed)return;
            if(!active()){cancel();clearCursor();resetKeys();return;}
            ImagePlus now=WindowManager.getCurrentImage();
            if(now!=image||(now!=null&&now.getCurrentSlice()!=plane)){
                cancel();clearCursor();resetKeys();image=now;canvas=now==null?null:now.getCanvas();plane=now==null?0:now.getCurrentSlice();expected=now==null?null:now.getRoi();history.reset(expected);
            }
            if(image!=null&&image.getRoi()!=expected&&!updating){cancel();expected=image.getRoi();history.reset(expected);}
            if(cursor!=null&&canvas!=null&&cursorMag!=canvas.getMagnification())drawCursor();
        }
        protected void dispatchEvent(AWTEvent event){
            try{
                if(!closed&&active()){
                    check();
                    if(event instanceof WindowEvent&&event.getID()==WindowEvent.WINDOW_LOST_FOCUS){cancel();resetKeys();clearCursor();}
                    if(event instanceof FocusEvent&&event.getID()==FocusEvent.FOCUS_LOST&&event.getSource()==canvas){cancel();resetKeys();clearCursor();}

                    if(event instanceof MouseEvent&&event.getSource()==canvas&&mouse((MouseEvent)event))return;
                }
            }catch(RuntimeException ex){cancel();resetKeys();IJ.handleException(ex);}
            super.dispatchEvent(event);
        }
        public boolean dispatchKeyEvent(KeyEvent e){if(closed||!active())return false;check();return key(e);}
        boolean key(KeyEvent e){
            if(e.getID()==KeyEvent.KEY_RELEASED){if(e.getKeyCode()==resizeKey){resize=false;return e.getSource()==canvas;}if(e.getKeyCode()==KeyEvent.VK_SPACE){space=false;drawCursor();return e.getSource()==canvas;}}
            if(e.getSource()!=canvas)return false; // text fields and other application windows retain all keys
            if(e.getID()==KeyEvent.KEY_TYPED)return e.getKeyChar()==' '||Character.toUpperCase(e.getKeyChar())==resizeKey;
            boolean down=e.getID()==KeyEvent.KEY_PRESSED;
            if(e.getKeyCode()==KeyEvent.VK_SPACE){if(down&&!space){cancel();space=true;resize=false;pan=held;blocked=held;px=pointer.x;py=pointer.y;}drawCursor();return true;}
            if(e.getKeyCode()==resizeKey&&!e.isControlDown()&&!e.isMetaDown()){resize=down;return true;}
            if(down&&e.getKeyCode()==KeyEvent.VK_ESCAPE){cancel();blocked=held;return true;}
            if(down&&(e.isControlDown()||e.isMetaDown())&&(e.getKeyCode()==KeyEvent.VK_Z||e.getKeyCode()==KeyEvent.VK_Y)){
                boolean redo=e.getKeyCode()==KeyEvent.VK_Y||e.isShiftDown();return undo(redo);
            }
            return false;
        }
        boolean mouse(MouseEvent e){
            pointer=e.getPoint();
            if(e instanceof MouseWheelEvent){
                if(space)return true;
                if(resize){cancel();blocked=held;settings.diameter=SelectionEngine.resized(settings.diameter,((MouseWheelEvent)e).getPreciseWheelRotation());drawCursor();return true;}
                cancel();blocked=held;return false;
            }
            switch(e.getID()){
                case MouseEvent.MOUSE_PRESSED:
                    if(e.getButton()!=MouseEvent.BUTTON1)return false;
                    e.consume(); // Alt belongs to this tool; never propagate it to ImageJ.
                    canvas.requestFocusInWindow();held=true;blocked=false;pan=space;px=e.getX();py=e.getY();
                    if(!space)start(e);drawCursor();return true;
                case MouseEvent.MOUSE_DRAGGED:
                    e.consume();
                    if(!held)return true;
                    if(pan||space){
                        if(space){Rectangle r=new Rectangle(canvas.getSrcRect());double m=canvas.getMagnification();r.x=Math.max(0,Math.min(image.getWidth()-r.width,r.x+(int)Math.round((px-e.getX())/m)));r.y=Math.max(0,Math.min(image.getHeight()-r.height,r.y+(int)Math.round((py-e.getY())/m)));canvas.setSourceRect(r);canvas.repaint();}
                        px=e.getX();py=e.getY();drawCursor();return true;
                    }
                    if(!blocked&&gesture!=null)gesture.offer(point(e));drawCursor();return true;
                case MouseEvent.MOUSE_RELEASED:
                    if(e.getButton()!=MouseEvent.BUTTON1)return false;
                    e.consume();
                    if(gesture!=null){gesture.offer(point(e));gesture.finish();}held=false;pan=false;blocked=false;drawCursor();return true;
                case MouseEvent.MOUSE_CLICKED:return e.getButton()==MouseEvent.BUTTON1;
                case MouseEvent.MOUSE_MOVED:drawCursor();return false;
                case MouseEvent.MOUSE_EXITED:clearCursor();return false;
                default:return false;
            }
        }
        Point2D.Double point(MouseEvent e){
            // ImageJ's integer conversion identifies the source pixel under the
            // pointer consistently at every magnification. Work from its center.
            lastInputX=canvas.offScreenX(e.getX());lastInputY=canvas.offScreenY(e.getY());
            return new Point2D.Double(lastInputX+.5,lastInputY+.5);
        }
        void start(MouseEvent e){
            cancel();Roi before=SelectionHistory.copy(image.getRoi());
            if(before!=null&&!before.isArea()){IJ.showStatus("Intensity tools require an area selection; clear the line/point ROI first.");blocked=true;return;}
            int c=settings.channel==0?image.getC():settings.channel;
            if(c>image.getNChannels()){IJ.showStatus("Configured channel is absent in this image");blocked=true;return;}
            ImageProcessor ip=image.getStack().getProcessor(image.getStackIndex(c,image.getZ(),image.getT()));
            Point2D.Double seed=point(e);
            // An unmodified stroke begun inside the selected area continues it.
            // A stroke outside still has the established replace behavior.
            int op=(e.isAltDown()||e.isControlDown())?-1:e.isShiftDown()?1:before!=null&&before.contains((int)Math.floor(seed.x),(int)Math.floor(seed.y))?1:0;
            gesture=new Gesture(++serial,image,plane,before,op,smart(),canvas.getMagnification(),settings.copy(),pixels(ip,c));
            gesture.offer(seed);
        }
        SelectionEngine.Pixels pixels(ImageProcessor ip,int c){
            return new SelectionEngine.Pixels(ip,false);
        }
        void apply(Roi roi){if(image==null)return;updating=true;if(roi==null)image.deleteRoi();else{image.setRoi(roi);}expected=image.getRoi();updating=false;}
        void cancel(){Gesture g=gesture;if(g!=null){g.cancelled=true;gesture=null;serial++;if(image==g.imp&&image.getRoi()==expected)apply(SelectionHistory.copy(g.before));} }
        void resetKeys(){space=resize=held=pan=blocked=false;}
        boolean undo(boolean redo){cancel();check();if(image==null||!(redo?history.canRedo():history.canUndo()))return false;apply(redo?history.redo():history.undo());return true;}
        public String commandExecuting(String command){
            if(!active())return command;
            if(!"Undo".equals(command)){
                Runnable release=()->{cancel();clearCursor();if(image!=null)history.reset(image.getRoi());};
                try{if(EventQueue.isDispatchThread())release.run();else EventQueue.invokeAndWait(release);}catch(Exception ex){IJ.handleException(ex);}
                return command;
            }
            final boolean[] done={false};try{if(EventQueue.isDispatchThread())done[0]=undo(false);else EventQueue.invokeAndWait(()->done[0]=undo(false));}catch(Exception ex){IJ.handleException(ex);}return done[0]?null:command;
        }
        void clearCursor(){if(cursorOverlay!=null&&cursor!=null)cursorOverlay.remove(cursor);if(canvas!=null)canvas.repaint();cursor=null;cursorOverlay=null;}
        void drawCursor(){
            clearCursor();if(canvas==null||image==null)return;
            if(space){canvas.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));IJ.showStatus("Pan — selection gesture cancelled; release mouse before painting again");return;}
            canvas.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));double m=canvas.getMagnification(),d=SelectionEngine.effectiveDiameter(settings,m);cursorMag=m;
            cursor=new OvalRoi(canvas.offScreenXD(pointer.x)-d/2,canvas.offScreenYD(pointer.y)-d/2,d,d);cursor.setStrokeColor(Color.CYAN);cursor.setStrokeWidth(1);cursor.setPosition(image.getC(),image.getZ(),image.getT());
            Overlay o=image.getOverlay();if(o==null){o=new Overlay();image.setOverlay(o);}cursorOverlay=o;o.add(cursor);canvas.repaint();
            IJ.showStatus((smart()?"Smart":"Brush")+String.format(" | %.1f image px / %.1f screen px | %s size | %s | raw channel | %s+wheel resize | sigma %.1f, sensitivity %.2f | %.1f ms",d,d*m,settings.adaptiveDiameter?"adaptive":"fixed",gesture==null?"inside continues / outside replaces / Shift add / Ctrl or Alt subtract":gesture.op<0?"subtract":gesture.op>0?"add":"replace",KeyEvent.getKeyText(resizeKey),settings.sigma,settings.sensitivity,lastLatency));
        }
        void options(){
            cancel();clearCursor();resetKeys();GenericDialog gd=new GenericDialog("Intensity selection tools");
            gd.addNumericField("Diameter at 100% zoom (pixels, 3–256)",settings.diameter,1);
            gd.addCheckbox("Adaptive diameter (constant displayed size, QuPath-style)",settings.adaptiveDiameter);
            gd.addNumericField("Channel (0 = active)",settings.channel,0);
            gd.addNumericField("Gaussian sigma (processing pixels, 0–8)",settings.sigma,1);
            gd.addNumericField("Sensitivity (local SD / value)",settings.sensitivity,2);
            gd.addCheckbox("Use absolute tolerance instead of local SD",settings.absolute);
            gd.addNumericField("Absolute tolerance (raw channel units)",settings.tolerance,2);
            gd.addCheckbox("Log internal mask versus final ROI diagnostics",settings.roiDiagnostics);
            gd.addStringField("Resize key (single letter)",KeyEvent.getKeyText(resizeKey),2);gd.showDialog();if(gd.wasCanceled())return;
            double d=gd.getNextNumber();boolean adaptive=gd.getNextBoolean();double ch=gd.getNextNumber(),sigma=gd.getNextNumber(),sens=gd.getNextNumber();boolean absolute=gd.getNextBoolean();double tol=gd.getNextNumber();boolean diagnostics=gd.getNextBoolean();String key=gd.getNextString().trim().toUpperCase();
            if(!Double.isFinite(d)||d<3||d>256||!Double.isFinite(ch)||ch<0||ch!=Math.floor(ch)||!Double.isFinite(sigma)||sigma<0||sigma>8||!Double.isFinite(sens)||sens<=0||!Double.isFinite(tol)||tol<0||!key.matches("[A-W]")||key.equals("Z")){IJ.error("Invalid settings","Use the documented ranges; resize key must be A–W (Space and undo keys are reserved).");return;}
            settings.diameter=d;settings.adaptiveDiameter=adaptive;settings.channel=(int)ch;settings.sigma=sigma;settings.sensitivity=sens;settings.absolute=absolute;settings.tolerance=tol;settings.roiDiagnostics=diagnostics;resizeKey=key.charAt(0);drawCursor();
        }
        void close(){cancel();clearCursor();timer.stop();Executer.removeCommandListener(this);KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);worker.shutdownNow();closed=true;pop();}
        class Gesture {
            final long id;final ImagePlus imp;final int plane,op;final Roi before;final boolean smart;final double m;final SelectionEngine.Settings s;final SelectionEngine.Pixels pixels;
            volatile boolean cancelled;boolean running,finished;Point2D.Double pending,last;Area painted=new Area();
            Gesture(long id,ImagePlus imp,int plane,Roi before,int op,boolean smart,double m,SelectionEngine.Settings s,SelectionEngine.Pixels pixels){this.id=id;this.imp=imp;this.plane=plane;this.before=before;this.op=op;this.smart=smart;this.m=m;this.s=s;this.pixels=pixels;}
            synchronized void offer(Point2D.Double p){pending=p;if(!running){running=true;worker.execute(this::drain);}}
            synchronized void finish(){finished=true;if(!running){running=true;worker.execute(this::drain);}}
            void drain(){try{
                while(!cancelled){Point2D.Double target;boolean done;synchronized(this){target=pending;pending=null;done=finished;if(target==null){running=false;}}
                    if(target==null){if(done)publish(true);return;}
                    long t=System.nanoTime();double d=SelectionEngine.effectiveDiameter(s,m),spacing=Math.max(.25,d/8);int steps=last==null?1:Math.max(1,(int)Math.ceil(last.distance(target)/spacing));Point2D.Double from=last==null?target:last;
                    for(int i=1;i<=steps&&!cancelled;i++){double x=from.x+(target.x-from.x)*i/steps,y=from.y+(target.y-from.y)*i/steps;Area a=smart?SelectionEngine.smart(pixels,x,y,m,s,()->cancelled):SelectionEngine.brush(x,y,d,pixels.ip.getWidth(),pixels.ip.getHeight());painted.add(a);}
                    last=target;lastLatency=(System.nanoTime()-t)/1e6;publish(false);
                }
            }catch(Throwable ex){EventQueue.invokeLater(()->{if(gesture==this){cancel();IJ.handleException(ex);}});}}
            void publish(boolean commit){
                Area result=new Area(painted);if(op!=0&&before!=null){Area base=area(before);if(op>0)base.add(result);else base.subtract(result);result=base;}else if(op<0)result=new Area();
                final Roi roi=RoiDiagnostics.toRoi(result,imp.getWidth(),imp.getHeight());
                if(s.roiDiagnostics){RoiDiagnostics.Counts c=RoiDiagnostics.compare(result,roi,imp.getWidth(),imp.getHeight());lastRoiDiagnostics=c;IJ.log("Brush ROI conversion diagnostic: internal mask pixels="+c.internalPixels+", ROI pixels="+c.roiPixels+", extra="+c.extraPixels+", missing="+c.missingPixels);}
                if(roi!=null){int[] pos=imp.convertIndexToPosition(plane);roi.setPosition(pos[0],pos[1],pos[2]);}
                EventQueue.invokeLater(()->{
                    if(cancelled||gesture!=this||serial!=id||!active()||WindowManager.getCurrentImage()!=imp||imp.getCurrentSlice()!=plane)return;
                    apply(roi);if(commit){history.commit(roi);gesture=null;}drawCursor();
                });
            }
        }
    }
    static Area area(Roi roi){ShapeRoi s=roi instanceof ShapeRoi?(ShapeRoi)roi:new ShapeRoi(roi);Rectangle b=s.getBounds();return new Area(AffineTransform.getTranslateInstance(b.x,b.y).createTransformedShape(s.getShape()));}
}

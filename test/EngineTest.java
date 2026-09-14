import ij.process.*;
import ij.gui.*;
import java.awt.geom.*;
public class EngineTest {
 static int tests;
 static void ok(boolean b,String message){tests++;if(!b)throw new AssertionError(message);}
 static SelectionEngine.Settings settings(){SelectionEngine.Settings s=new SelectionEngine.Settings();s.diameter=101;s.sigma=0;s.absolute=true;s.tolerance=1;return s;}
 public static void main(String[] args){
  for(ImageProcessor ip:new ImageProcessor[]{new ByteProcessor(160,160),new ShortProcessor(160,160),new FloatProcessor(160,160),new ColorProcessor(160,160)}){
   for(int y=30;y<130;y++)for(int x=30;x<130;x++)ip.setf(x,y,ip instanceof ColorProcessor?0xffffff:200);
   for(int y=65;y<80;y++)for(int x=65;x<80;x++)ip.setf(x,y,0);
   SelectionEngine.Settings s=settings();SelectionEngine.Pixels p=new SelectionEngine.Pixels(ip,false);
   Area a=SelectionEngine.smart(p,55.5,55.5,1,s,()->false);
   ok(a.contains(55.5,55.5),"seed "+ip);ok(!a.contains(70.5,70.5),"hole "+ip);ok(!a.contains(20,20),"background");ok(a.getBounds2D().getWidth()<=101.0001,"bounded");
   Area z=SelectionEngine.smart(p,55.5,55.5,2,s,()->false);a.exclusiveOr(z);ok(a.isEmpty(),"zoom-invariant exact source mask");
  }
  FloatProcessor f=new FloatProcessor(101,101);f.setf(50,50,Float.NaN);ok(SelectionEngine.smart(new SelectionEngine.Pixels(f,false),50.5,50.5,1,settings(),()->false).isEmpty(),"NaN seed");
  FloatProcessor raw=new FloatProcessor(160,160);for(int i=0;i<25600;i++)raw.setf(i,100000+i%160);SelectionEngine.Pixels p=new SelectionEngine.Pixels(raw,false);Area a=SelectionEngine.smart(p,80.5,80.5,1,settings(),()->false);raw.setMinAndMax(100000,100020);Area b=SelectionEngine.smart(new SelectionEngine.Pixels(raw,false),80.5,80.5,1,settings(),()->false);a.exclusiveOr(b);ok(a.isEmpty(),"raw display independence");
  ok(SelectionEngine.resized(3,100)==3,"minimum");ok(SelectionEngine.resized(256,-100)==256,"maximum");ok(SelectionEngine.resized(50,.1)<50,"trackpad fractional");
  ByteProcessor contrast=new ByteProcessor(201,201);for(int y=0;y<201;y++)for(int x=0;x<201;x++)contrast.set(x,y,x<100?25:220);
  SelectionEngine.Settings invariant=settings();invariant.diameter=149;invariant.tolerance=5;
  Area reference=null;for(double zoom:new double[]{.25,.5,1,2,4,8}){Area mask=SelectionEngine.smart(new SelectionEngine.Pixels(contrast,false),80.5,100.5,zoom,invariant,()->false);ok(!mask.contains(120.5,100.5),"large contrast rejected at "+zoom+"x");if(reference==null)reference=mask;else{Area diff=new Area(reference);diff.exclusiveOr(mask);ok(diff.isEmpty(),"identical mask at "+zoom+"x");}}
  SelectionEngine.Settings adaptive=settings();adaptive.diameter=80;adaptive.adaptiveDiameter=true;adaptive.tolerance=255;
  ok(SelectionEngine.effectiveDiameter(adaptive,.25)==320,"adaptive zoom-out image diameter");
  ok(SelectionEngine.effectiveDiameter(adaptive,1)==80,"adaptive 100% image diameter");
  ok(SelectionEngine.effectiveDiameter(adaptive,4)==20,"adaptive zoom-in image diameter");
  for(double zoom:new double[]{.25,1,4}){
   Area mask=SelectionEngine.smart(new SelectionEngine.Pixels(new ByteProcessor(500,500),false),250.5,250.5,zoom,adaptive,()->false);
   double screenWidth=mask.getBounds2D().getWidth()*zoom;
   ok(Math.abs(screenWidth-adaptive.diameter)<=2*zoom,"adaptive displayed footprint at "+zoom+"x");
  }
  adaptive.tolerance=5;for(double zoom:new double[]{.25,1,4}){Area mask=SelectionEngine.smart(new SelectionEngine.Pixels(contrast,false),80.5,100.5,zoom,adaptive,()->false);ok(!mask.contains(120.5,100.5),"adaptive raw contrast rejected at "+zoom+"x");}
  Roi r=new ShapeRoi(new Ellipse2D.Double(100,200,30,40));ok(Intensity_Selection_Tools.area(r).contains(115,220),"ROI global transform");
  SelectionHistory h=new SelectionHistory();h.reset(null);h.commit(r);ok(h.undo()==null,"undo empty");ok(h.redo().contains(115,220),"redo");
  ByteProcessor islands=new ByteProcessor(101,101);islands.set(50,50,200);islands.set(51,51,200);
  Area connected=SelectionEngine.smart(new SelectionEngine.Pixels(islands,false),50.5,50.5,1,settings(),()->false);
  ok(connected.contains(50.5,50.5)&&!connected.contains(51.5,51.5),"four-connectivity rejects diagonal island");
  islands.set(51,50,200);connected=SelectionEngine.smart(new SelectionEngine.Pixels(islands,false),50.5,50.5,1,settings(),()->false);
  ok(connected.contains(51.5,51.5),"one-pixel bridge connects at native resolution");
  byte[] original=((byte[])islands.getPixels()).clone();SelectionEngine.smart(new SelectionEngine.Pixels(islands,false),50.5,50.5,.25,settings(),()->false);ok(java.util.Arrays.equals(original,(byte[])islands.getPixels()),"sampling leaves pixels unchanged");
  Area edge=SelectionEngine.smart(new SelectionEngine.Pixels(islands,false),.5,.5,.25,settings(),()->false);ok(edge.getBounds2D().getMinX()>=-1e-9&&edge.getBounds2D().getMinY()>=-1e-9,"edge clipping");
  SelectionEngine.Settings defaults=new SelectionEngine.Settings();Area smooth=SelectionEngine.smart(new SelectionEngine.Pixels(Synthetic.make(8).getProcessor(),false),125,190,1,defaults,()->false);ok(smooth.contains(125,190)&&!smooth.contains(165,190),"default smoothing follows raw intensity");
  FloatProcessor large=new FloatProcessor(4096,4096);SelectionEngine.Settings s=settings();s.sigma=4;s.absolute=false;s.diameter=149;
  for(int i=0;i<10;i++)SelectionEngine.smart(new SelectionEngine.Pixels(large,false),2000,2000,.25,s,()->false);
  long t=System.nanoTime();for(int i=0;i<30;i++)SelectionEngine.smart(new SelectionEngine.Pixels(large,false),2000,2000,.25,s,()->false);
  System.out.printf("PASS %d assertions; 4096² image, 149-image-pixel smart dab: mean %.2f ms (30 dabs)%n",tests,(System.nanoTime()-t)/30e6);
 }
}

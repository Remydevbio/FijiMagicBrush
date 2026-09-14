import ij.*;
import ij.gui.*;
import ij.io.*;
import ij.process.*;
import java.awt.*;
import java.awt.geom.*;

/** Headless regression against the supplied biological channel-2 fixture. */
public class ExampleChannelTest {
    static int count(Area a, Rectangle b){int n=0;for(int y=Math.max(0,b.y);y<b.y+b.height;y++)for(int x=Math.max(0,b.x);x<b.x+b.width;x++)if(a.contains(x+.5,y+.5))n++;return n;}
    public static void main(String[] args)throws Exception{
        String root="/home/rbonnav/Documents/Codex/2026-09-13-brushtoolFiji/test-images/example multichannel/";
        ImagePlus imp=IJ.openImage(root+"example_multichannel.tif");
        if(imp.getNChannels()<2)throw new AssertionError("fixture has no channel 2");
        ImageProcessor ch2=imp.getStack().getProcessor(imp.getStackIndex(2,1,1));
        Roi rough=new RoiDecoder(root+"example_multichannel.roi").getRoi();
        SelectionEngine.Settings s=new SelectionEngine.Settings();s.diameter=149;s.sigma=2;s.sensitivity=3;
        Area best=null;int bx=0,by=0,bestInside=0;
        Rectangle rb=rough.getBounds();
        for(int y=rb.y+20;y<rb.y+rb.height;y+=35)for(int x=rb.x+20;x<rb.x+rb.width;x+=35){
            if(!rough.contains(x,y))continue;
            Area a=SelectionEngine.smart(new SelectionEngine.Pixels(ch2,false),x+.5,y+.5,1,s,()->false);
            Area overlap=new Area(a);overlap.intersect(Intensity_Selection_Tools.area(rough));int inside=count(overlap,rb);
            if(inside>bestInside){best=a;bestInside=inside;bx=x;by=y;}
        }
        if(best==null||bestInside<20)throw new AssertionError("no useful channel-2 selection found inside reference ROI");
        Area high=SelectionEngine.smart(new SelectionEngine.Pixels(ch2,false),bx+.5,by+.5,8,s,()->false);
        Area diff=new Area(best);diff.exclusiveOr(high);
        if(!diff.isEmpty())throw new AssertionError("channel-2 source mask changes at 8x");
        // Display changes cannot affect raw channel access.
        ch2.setMinAndMax(ch2.getMin()+10,Math.max(ch2.getMin()+11,ch2.getMax()-10));
        Area displayChanged=SelectionEngine.smart(new SelectionEngine.Pixels(ch2,false),bx+.5,by+.5,.25,s,()->false);
        diff=new Area(best);diff.exclusiveOr(displayChanged);
        if(!diff.isEmpty())throw new AssertionError("channel-2 mask changed with display range");
        System.out.printf("PASS supplied channel 2: seed=(%d,%d), selected=%d pixels, reference overlap=%d pixels, exact masks at 0.25x/1x/8x%n",bx,by,count(best,best.getBounds()),bestInside);
    }
}

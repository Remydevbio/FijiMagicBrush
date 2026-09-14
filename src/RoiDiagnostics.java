import ij.gui.Roi;
import ij.plugin.filter.ThresholdToSelection;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import java.awt.Rectangle;
import java.awt.geom.Area;

/** Development diagnostics for the internal selection to ImageJ ROI boundary. */
public final class RoiDiagnostics {
    public static final class Counts {
        public int internalPixels,roiPixels,extraPixels,missingPixels;
    }
    public static Counts compare(Area internal,Roi roi,int width,int height){
        Counts c=new Counts();
        Rectangle b=internal.getBounds();
        if(roi!=null)b=b.union(roi.getBounds());
        b=b.intersection(new Rectangle(0,0,width,height));
        for(int y=b.y;y<b.y+b.height;y++)for(int x=b.x;x<b.x+b.width;x++){
            boolean selected=internal.contains(x+.5,y+.5),represented=roi!=null&&roi.contains(x,y);
            if(selected)c.internalPixels++;
            if(represented)c.roiPixels++;
            if(represented&&!selected)c.extraPixels++;
            if(selected&&!represented)c.missingPixels++;
        }
        return c;
    }
    /** Rasterize at source-pixel centers, then use ImageJ's topology-aware converter. */
    public static Roi toRoi(Area internal,int width,int height){
        Rectangle b=internal.getBounds().intersection(new Rectangle(0,0,width,height));
        if(b.isEmpty())return null;
        ByteProcessor mask=new ByteProcessor(b.width,b.height);
        for(int y=0;y<b.height;y++)for(int x=0;x<b.width;x++)if(internal.contains(b.x+x+.5,b.y+y+.5))mask.set(x,y,255);
        mask.setThreshold(255,255,ImageProcessor.NO_LUT_UPDATE);
        Roi roi=new ThresholdToSelection().convert(mask);
        if(roi!=null){Rectangle local=roi.getBounds();roi.setLocation(b.x+local.x,b.y+local.y);}
        return roi;
    }
}

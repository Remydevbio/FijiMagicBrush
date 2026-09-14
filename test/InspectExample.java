import ij.*;
import ij.gui.*;
import ij.io.*;
import ij.process.*;

public class InspectExample {
    public static void main(String[] args) throws Exception {
        String root="/home/rbonnav/Documents/Codex/2026-09-13-brushtoolFiji/test-images/example multichannel/";
        ImagePlus imp=IJ.openImage(root+"example_multichannel.tif");
        System.out.printf("%dx%d C=%d Z=%d T=%d bit=%d%n",imp.getWidth(),imp.getHeight(),imp.getNChannels(),imp.getNSlices(),imp.getNFrames(),imp.getBitDepth());
        for(int c=1;c<=imp.getNChannels();c++){
            ImageProcessor ip=imp.getStack().getProcessor(imp.getStackIndex(c,1,1));
            System.out.printf("C%d min=%.3f max=%.3f mean=%.3f%n",c,ip.getStatistics().min,ip.getStatistics().max,ip.getStatistics().mean);
        }
        Roi roi=new RoiDecoder(root+"example_multichannel.roi").getRoi();
        System.out.println("ROI "+roi.getTypeAsString()+" "+roi.getBounds());
    }
}

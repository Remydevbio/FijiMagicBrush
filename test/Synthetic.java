import ij.*;import ij.io.*;import ij.process.*;
public class Synthetic {
 public static ImagePlus make(int type){int w=512,h=384;ImageProcessor p=type==16?new ShortProcessor(w,h):type==32?new FloatProcessor(w,h):type==24?new ColorProcessor(w,h):new ByteProcessor(w,h);
 for(int y=0;y<h;y++)for(int x=0;x<w;x++){double v=20+x*30.0/w;if((x-165)*(x-165)+(y-190)*(y-190)<90*90||(x-365)*(x-365)+(y-190)*(y-190)<60*60)v=210;if(x>=245&&x<=310&&Math.abs(y-190)<2)v=210;if((x-165)*(x-165)+(y-190)*(y-190)<25*25)v=15;if(type==16)v*=200;if(type==32)v=v*123.456-2000;if(type==24){int k=(int)v;p.set(x,y,(k<<16)|(k<<8)|k);}else p.setf(x,y,(float)v);}
 ImagePlus imp=new ImagePlus("Synthetic "+type,p);imp.resetDisplayRange();return imp;}
 public static void main(String[] args){new java.io.File("test-images").mkdirs();for(int t:new int[]{8,16,32,24})new FileSaver(make(t)).saveAsTiff("test-images/synthetic-"+t+".tif");ImageStack st=new ImageStack(512,384);st.addSlice(make(16).getProcessor());st.addSlice(new ShortProcessor(512,384));ImagePlus imp=new ImagePlus("Composite",st);imp.setDimensions(2,1,1);new FileSaver(new CompositeImage(imp,CompositeImage.COMPOSITE)).saveAsTiff("test-images/composite.tif");}
}

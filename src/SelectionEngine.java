import ij.process.ImageProcessor;
import ij.process.ColorProcessor;
import java.awt.geom.*;
import java.awt.image.IndexColorModel;
import java.util.function.BooleanSupplier;

/** Local, read-only sampling and geometry. No ImageJ window or event state. */
public final class SelectionEngine {
    public static final class Settings {
        /** Diameter and sigma are always expressed in source-image pixels. */
        public double diameter=149, sensitivity=2, sigma=4, tolerance=10;
        public boolean absolute=false;
        public int channel=0;
        public Settings copy(){Settings s=new Settings();s.diameter=diameter;s.sensitivity=sensitivity;s.sigma=sigma;s.tolerance=tolerance;s.absolute=absolute;s.channel=channel;return s;}
    }
    public static final class Pixels {
        final ImageProcessor ip; final boolean rendered, rgb; final double min,max; final IndexColorModel lut;
        public Pixels(ImageProcessor ip, boolean rendered){this(ip,rendered,rendered?ip.getMin():0,rendered?ip.getMax():1,ip.getColorModel() instanceof IndexColorModel?(IndexColorModel)ip.getColorModel():null);}
        public Pixels(ImageProcessor ip,boolean rendered,double min,double max,IndexColorModel lut){this.ip=ip;this.rendered=rendered;rgb=ip instanceof ColorProcessor;this.min=min;this.max=max;this.lut=lut;}
        double[] at(double x,double y){
            int ix=(int)Math.floor(x),iy=(int)Math.floor(y);
            if(ix<0||iy<0||ix>=ip.getWidth()||iy>=ip.getHeight())return null;
            if(rgb){int v=ip.get(ix,iy);return new double[]{(v>>16)&255,(v>>8)&255,v&255};}
            double v=ip.getf(ix,iy);if(!Double.isFinite(v))return null;
            if(!rendered)return new double[]{v};
            int k=(int)Math.round(Math.max(0,Math.min(255,(v-min)*255/Math.max(1e-30,max-min))));
            return lut==null?new double[]{k,k,k}:new double[]{lut.getRed(k),lut.getGreen(k),lut.getBlue(k)};
        }
    }
    public static Area brush(double x,double y,double diameter,int width,int height){
        Area a=new Area(new Ellipse2D.Double(x-diameter/2,y-diameter/2,diameter,diameter));
        a.intersect(new Area(new Rectangle2D.Double(0,0,width,height)));return a;
    }
    public static double resized(double d,double wheel){return Math.max(3,Math.min(256,d*Math.exp(-wheel*Math.log(1.12))));}
    public static Area smart(Pixels p,double x,double y,double ignoredMagnification,Settings s,BooleanSupplier cancelled){
        // Deliberately ignore display magnification. Each grid cell maps to exactly
        // one source pixel, so zoom/LUT/brightness cannot change the mask.
        double diameter=s.diameter;
        int n=Math.max(3,(int)Math.ceil(diameter));if(n%2==0)n++;
        double x0=Math.floor(x)-n/2,y0=Math.floor(y)-n/2;
        int channels=p.rgb||p.rendered?3:1, count=n*n,seed=(n/2)*n+n/2;
        double[][] v=new double[channels][count]; boolean[] valid=new boolean[count];
        for(int j=0;j<n;j++){
            if(cancelled.getAsBoolean())return new Area();
            for(int i=0;i<n;i++){int k=j*n+i;double[] sample=p.at(x0+i+.5,y0+j+.5);valid[k]=sample!=null;if(sample!=null)for(int c=0;c<channels;c++)v[c][k]=sample[c];}
        }
        if(!valid[seed])return new Area();
        if(s.sigma>0)for(int c=0;c<channels;c++)v[c]=blur(v[c],valid,n,s.sigma);
        double[] tol=new double[channels];
        for(int c=0;c<channels;c++){
            double mean=0,m2=0;int num=0;
            for(int k=0;k<count;k++)if(valid[k]){num++;double d=v[c][k]-mean;mean+=d/num;m2+=d*(v[c][k]-mean);}
            tol[c]=s.absolute?s.tolerance:Math.sqrt(m2/Math.max(1,num))/s.sensitivity;
        }
        boolean[] seen=new boolean[count],selected=new boolean[count];int[] queue=new int[count];int head=0,tail=0;queue[tail++]=seed;seen[seed]=true;
        while(head<tail){
            if((head&1023)==0&&cancelled.getAsBoolean())return new Area();
            int k=queue[head++],i=k%n,j=k/n;
            double dx=i+.5-n/2.0,dy=j+.5-n/2.0;
            if(!valid[k]||dx*dx+dy*dy>diameter*diameter/4)continue;
            boolean ok=true;for(int c=0;c<channels;c++)if(Math.abs(v[c][k]-v[c][seed])>tol[c]+1e-10)ok=false;
            if(!ok)continue; selected[k]=true;
            if(i>0&&!seen[k-1]){seen[k-1]=true;queue[tail++]=k-1;}
            if(i<n-1&&!seen[k+1]){seen[k+1]=true;queue[tail++]=k+1;}
            if(j>0&&!seen[k-n]){seen[k-n]=true;queue[tail++]=k-n;}
            if(j<n-1&&!seen[k+n]){seen[k+n]=true;queue[tail++]=k+n;}
        }
        // Run rectangles form a nonzero-winding path; Area preserves holes and disjoint pieces.
        Path2D path=new Path2D.Double();
        for(int j=0;j<n;j++)for(int i=0;i<n;){if(!selected[j*n+i]){i++;continue;}int start=i;while(i<n&&selected[j*n+i])i++;path.append(new Rectangle2D.Double(x0+start,y0+j,i-start,1),false);}
        Area a=new Area(path);a.intersect(brush(x,y,diameter,p.ip.getWidth(),p.ip.getHeight()));return a;
    }
    private static double[] blur(double[] a,boolean[] valid,int n,double sigma){
        int r=(int)Math.ceil(sigma*2);double[] kernel=new double[2*r+1];for(int k=-r;k<=r;k++)kernel[k+r]=Math.exp(-k*k/(2*sigma*sigma));
        double[] b=new double[a.length],out=new double[a.length];
        for(int pass=0;pass<2;pass++){
            double[] input=pass==0?a:b,result=pass==0?b:out;
            for(int y=0;y<n;y++)for(int x=0;x<n;x++){double sum=0,weight=0;for(int k=-r;k<=r;k++){int xx=pass==0?reflect(x+k,n):x,yy=pass==0?y:reflect(y+k,n);int q=yy*n+xx;if(valid[q]){sum+=input[q]*kernel[k+r];weight+=kernel[k+r];}}result[y*n+x]=weight==0?0:sum/weight;}
        }return out;
    }
    private static int reflect(int x,int n){while(x<0||x>=n)x=x<0?-x:2*n-x-2;return x;}
}

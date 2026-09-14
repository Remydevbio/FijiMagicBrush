import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.imageio.ImageIO;
public class DesktopProbe {
 public static void main(String[] a) throws Exception {
  Robot r=new Robot();r.setAutoDelay(120);
  if(a[0].equals("shot")) ImageIO.write(r.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())),"png",new File(a[1]));
  if(a[0].equals("click")){r.mouseMove(Integer.parseInt(a[1]),Integer.parseInt(a[2]));r.mousePress(1024);r.mouseRelease(1024);}
  if(a[0].equals("key")){for(int i=1;i<a.length;i++)r.keyPress(Integer.parseInt(a[i]));for(int i=a.length-1;i>0;i--)r.keyRelease(Integer.parseInt(a[i]));}
  if(a[0].equals("text")){for(char c:a[1].toCharArray()){int k=KeyEvent.getExtendedKeyCodeForChar(c);boolean shift=Character.isUpperCase(c)||c=='_';if(c=='_')k=KeyEvent.VK_MINUS;if(shift)r.keyPress(16);r.keyPress(k);r.keyRelease(k);if(shift)r.keyRelease(16);}}
  if(a[0].equals("wheel")){r.mouseMove(Integer.parseInt(a[1]),Integer.parseInt(a[2]));r.mouseWheel(Integer.parseInt(a[3]));}
  if(a[0].equals("drag")){if(a.length>5)r.keyPress(Integer.parseInt(a[5]));r.mouseMove(Integer.parseInt(a[1]),Integer.parseInt(a[2]));r.mousePress(1024);for(int i=0;i<=20;i++){r.mouseMove(Integer.parseInt(a[1])+(Integer.parseInt(a[3])-Integer.parseInt(a[1]))*i/20,Integer.parseInt(a[2])+(Integer.parseInt(a[4])-Integer.parseInt(a[2]))*i/20);}r.mouseRelease(1024);if(a.length>5)r.keyRelease(Integer.parseInt(a[5]));}
 }
}

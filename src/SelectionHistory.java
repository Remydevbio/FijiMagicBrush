import ij.gui.Roi;
import java.util.ArrayList;
/** Bounded gesture history; null (no ROI) is a real state. */
public final class SelectionHistory {
    private final ArrayList<Roi> states=new ArrayList<>();private int index;
    public static Roi copy(Roi r){return r==null?null:(Roi)r.clone();}
    public void reset(Roi r){states.clear();states.add(copy(r));index=0;}
    public void commit(Roi r){while(states.size()>index+1)states.remove(states.size()-1);states.add(copy(r));index++;if(states.size()>31){states.remove(0);index--;}}
    public boolean canUndo(){return index>0;} public boolean canRedo(){return index+1<states.size();}
    public Roi undo(){return copy(states.get(--index));} public Roi redo(){return copy(states.get(++index));}
}

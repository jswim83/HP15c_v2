import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.geom.Ellipse2D;
import java.util.HashMap;


public class SevenSegmentDisplay extends Canvas {

    private Segment[] segments;
    private RadixMark radixMark;
    private Annunciator annunciator;
    private int padding;
    private int displayHeight;
    private int displayWidth;
    private boolean active;

    public SevenSegmentDisplay(int segmentHeight, int segmentWidth, int padding, Color backgroundColor, Color segmentColor){
        if(padding < 2 * segmentWidth) {
            padding = 2 * segmentWidth;
            //System.out.println("not enough padding to display decimal place, padding set to minimum " + padding) ;
        }
        this.segments = new Segment[7];
        this.padding = padding;
        this.displayHeight = 2 * segmentHeight + 2 * padding;
        this.displayWidth = segmentHeight + 2 * padding;
        this.setPreferredSize(new Dimension(displayWidth, displayHeight));
        this.setBackground(backgroundColor);
        segments[0] = new Segment(segmentHeight, segmentWidth, false, padding, padding, backgroundColor, segmentColor);
        segments[1] = new Segment(segmentHeight, segmentWidth, true, padding, padding, backgroundColor, segmentColor);
        segments[2] = new Segment(segmentHeight, segmentWidth, true, padding + segmentHeight - segmentWidth, padding, backgroundColor, segmentColor);
        segments[3] = new Segment(segmentHeight - segmentWidth, segmentWidth, false, padding + segmentWidth / 2, segmentHeight - segmentWidth / 2 + padding, backgroundColor, segmentColor);
        segments[4] = new Segment(segmentHeight, segmentWidth, true, padding, segmentHeight + padding, backgroundColor, segmentColor);
        segments[5] = new Segment(segmentHeight, segmentWidth, true, padding + segmentHeight - segmentWidth, segmentHeight + padding, backgroundColor, segmentColor);
        segments[6] = new Segment(segmentHeight, segmentWidth, false, padding, 2 * segmentHeight + padding - segmentWidth, backgroundColor, segmentColor);
        this.radixMark = new RadixMark(segmentHeight + segmentWidth + padding,2 * segmentHeight + padding - segmentWidth, segmentWidth, backgroundColor, segmentColor);
        this.annunciator = new Annunciator(padding, 2 * segmentHeight + 12 * padding / 7, 3 * segmentWidth / 2, backgroundColor, segmentColor);
    }

    @Override
    public void paint(Graphics g) {
        super.paint(g);
        if(!active)
            return;
        for(int i = 0; i < 7; i++)
        this.segments[i].paintComponent(g);
        this.radixMark.paintComponent(g);
        this.annunciator.paintComponent(g);
    }

    public void setDigit(Digit digit){
        active = true;
        for(int i = 0; i < 7; i++)
            this.segments[i].setActive(digit.getDigit()[i]);
        this.radixMark.setState(digit.getRadixMark());
        repaint();
    }

    public void setRadixMark(RadixMark.State state){
        active = true;
        this.radixMark.setState(state);
        repaint();
    }

    public void setAnnunciatorState(Annunciator.State state){
        active = true;
        this.annunciator.setState(state);
        repaint();
    }

    public void clear(){
        active = false;
        repaint();
    }
}

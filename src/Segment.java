import javax.swing.*;
import java.awt.*;
import java.io.Serializable;

public class Segment extends JComponent implements Serializable {

    private boolean active;
    private Color offColor;
    private Color onColor;
    private Polygon segment;

    public Segment(int longEdge, int shortEdge, boolean vertical, int x, int y, Color offColor, Color onColor){
        this.offColor = offColor;
        this.onColor = onColor;
        this.active = false;
        segment = new Polygon(new int[6], new int[6], 0);
        var leg = shortEdge / 2;
        if(vertical) {
            //top three points
            segment.addPoint(x, y + leg);
            segment.addPoint(x + leg, y);
            segment.addPoint(x + shortEdge, y + leg);
            //bottom three points
            segment.addPoint(x + shortEdge, y + longEdge - leg);
            segment.addPoint(x + leg, y + longEdge);
            segment.addPoint(x, y + longEdge - leg);
        }
        else {
            //left three points
            segment.addPoint(x + leg, y + shortEdge);
            segment.addPoint(x, y + leg);
            segment.addPoint(x + leg, y);
            //right three points
            segment.addPoint(x + longEdge - leg, y);
            segment.addPoint(x + longEdge, y + leg);
            segment.addPoint(x + longEdge - leg, y + shortEdge);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        var g2d = (Graphics2D)g;
        if(active) {
            g2d.setColor(onColor);
            g2d.draw(segment);
            g2d.fill(segment);
        }
    }

    public void setActive(boolean active){
        this.active = active;
    }
}

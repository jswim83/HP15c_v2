import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;

public class RadixMark extends JComponent {

    public static enum State{
        OFF, RADIX, DIGIT_GROUP;
    }
    //0 = off, 1 = radix, 2 = digit group
    private State state;
    private Color offColor;
    private Color onColor;
    private Ellipse2D radix;
    private Polygon digitGroup;

    public RadixMark(int x, int y, int size, Color offColor, Color onColor){
        this.offColor = offColor;
        this.onColor = onColor;
        this.state = State.OFF;
        radix =  new Ellipse2D.Double(x, y, size, size);
        digitGroup = new Polygon(new int[4], new int[4], 0);
        digitGroup.addPoint(x, y + size);
        digitGroup.addPoint(x + size, y + size);
        digitGroup.addPoint(x + size / 4, y + 2 * size);
        digitGroup.addPoint(x - size / 4, y + 2 * size);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        var g2d = (Graphics2D)g;
        switch(state) {
            case OFF:
                break;
            case RADIX:
                g2d.setColor(onColor);
                g2d.draw(radix);
                g2d.fill(radix);
                break;
            case DIGIT_GROUP:
                g2d.setColor(onColor);
                g2d.draw(radix);
                g2d.fill(radix);
                g2d.draw(digitGroup);
                g2d.fill(digitGroup);
                break;
        }
    }

    public void setState(State state){
        this.state = state;
    }
}

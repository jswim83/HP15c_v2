import javax.swing.*;
import java.awt.*;

public class Annunciator extends JComponent {

    private State state;
    private Color offColor;
    private Color onColor;
    private int x;
    private int y;
    private int size;

    public enum State {
        OFF(""),
        DEG(""),
        RAD("  RAD"),
        GRAD("GRAD"),
        PROGRAM("PGRM"),
        COMPLEX("C");
        public String string;
        State(String string){
            this.string = string;
        }
    }

    public Annunciator(int x, int y, int size, Color offColor, Color onColor) {
        this.state = State.OFF;
        this.x = x;
        this.y = y;
        this.size = size;
        this.offColor = offColor;
        this.onColor = onColor;
        this.state = state;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        var g2d = (Graphics2D)g;
        g2d.setFont(g2d.getFont().deriveFont((float)size));
        switch(state) {
            case OFF:
                break;
            case RAD:
                g2d.setColor(onColor);
                g2d.drawString(State.RAD.string, x, y);
                break;
            case GRAD:
                g2d.setColor(onColor);
                g2d.drawString(State.GRAD.string, x, y);
                break;
            case PROGRAM:
                g2d.setColor(onColor);
                g2d.drawString(State.PROGRAM.string, x, y);
                break;
            case COMPLEX:
                g2d.setColor(onColor);
                g2d.drawString(State.COMPLEX.string, x, y);
                break;
        }
        g2d.drawString(state.string, x, y);
    }

    public void setState(State state) {
        this.state = state;
    }
}

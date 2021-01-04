import javax.swing.*;
import java.awt.*;

public class Annunciator extends JComponent {

    private State state;
    private Color offColor;
    private Color onColor;
    private int x;
    private int y;

    public enum State {
        OFF(""),
        RAD("RAD"),
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
        this.offColor = offColor;
        this.onColor = onColor;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        var g2d = (Graphics2D)g;
        g2d.drawString(state.string, x, y);
    }

    public void setState(State state) {
        this.state = state;
    }
}

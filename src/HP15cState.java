import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Stack;

public class HP15cState implements Serializable {

    public double[] stack;
    public HP15c.Input[] programMemory;
    public double[] registers;
    public boolean programMode;
    public int programIndex;
    public HP15c.DisplayMode displayMode;
    public HP15c.AngleMode angleMode;
    public int precision;

    public HP15cState(double[] stack, HP15c.Input[] programMemory, double[] registers, boolean programMode,
                      int programIndex, HP15c.DisplayMode displayMode, HP15c.AngleMode angleMode, int precision) {
        this.stack = stack;
        this.programMemory = programMemory;
        this.registers = registers;
        this.programMode = programMode;
        this.programIndex = programIndex;
        this.displayMode = displayMode;
        this.angleMode = angleMode;
        this.precision = precision;
    }
}

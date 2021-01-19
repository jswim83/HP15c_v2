import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Stack;

public class HP15cState implements Serializable {

    protected double[] stack;
    protected HP15c.Input[] programMemory;
    protected double[] registers;
    protected boolean programMode;
    protected int programIndex;
    protected HP15c.DisplayMode displayMode;
    protected HP15c.AngleMode angleMode;
    protected int precision;

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

    public void setStack(double[] stack) {
        this.stack = stack;
    }

    public void setProgramMemory(HP15c.Input[] programMemory) {
        this.programMemory = programMemory;
    }

    public void setRegisters(double[] registers) {
        this.registers = registers;
    }

    public void setProgramMode(boolean programMode) {
        this.programMode = programMode;
    }

    public void setProgramIndex(int programIndex) {
        this.programIndex = programIndex;
    }

    public void setDisplayMode(HP15c.DisplayMode displayMode) {
        this.displayMode = displayMode;
    }

    public void setAngleMode(HP15c.AngleMode angleMode) {
        this.angleMode = angleMode;
    }

    public void setPrecision(int precision) {
        this.precision = precision;
    }
}

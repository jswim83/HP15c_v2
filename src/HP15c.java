import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.*;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Stack;
import java.util.stream.Collectors;

public class HP15c extends JFrame implements Serializable {

    private final HP15cDisplay display;
    private transient KeyListener mainListener;
    private String xRegisterString = "0";
    private String waitingInputString = "";
    private volatile WaitingInput waitingInput;
    private final LinkedList<Character> commandBuffer;
    private double[] stack;
    private double[][] complexStack;
    private Input[] programMemory = new Input[448];
    private final Stack<Integer> programStack = new Stack<>();
    private double[] registers = new double[67];
    private static final HashMap<String, Input> operationMap = new HashMap<>(20);
    private static final HashMap<String, Input> labelMap = new HashMap<>(15);
    private static final HashMap<String, Integer> registerMap = new HashMap<>(21);
    private boolean programMode;
    private volatile boolean programExecution;
    private int programIndex;
    private boolean stackLift = true;
    private LastEntry lastEntry = LastEntry.DIGIT;
    private DisplayMode displayMode = DisplayMode.FIX;
    private AngleMode angleMode = AngleMode.RAD;
    private int precision;
    private final DecimalFormat displayFormatter;
    private final DecimalFormat fixFormatter;
    private final DecimalFormat scientificFormatter;
    private boolean awaitingInput;

    public static final int XREG = 0;
    public static final int YREG = 1;
    public static final int ZREG = 2;
    public static final int TREG = 3;

    private enum LastEntry {
        DIGIT, COMMAND
    }

    public enum DisplayMode implements Serializable {
        FIX, SCI, ENG
    }

    public enum AngleMode implements Serializable {
        DEG, RAD, GRAD
    }

    public static final String[] fixPatterns = {
            "#,##0.", "#,##0.0", "#,##0.00", "#,##0.000", "#,##0.0000",
            "#,##0.00000", "#,##0.000000", "#,##0.0000000", "#,##0.00000000", "#,##0.000000000"
    };
    public static final String[] scientificPatterns = {
            "0.E00", "0.0E00", "0.00E00", "0.000E00", "0.0000E00", "0.00000E00", "0.000000E00",
    };

    public static abstract class Input implements Serializable {
        public final String dummy = "dummy";
        public final String code;
        public final String name;

        public Input(String code, String name) {
            this.code = code;
            this.name = name;
        }

        public abstract void input();

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Input))
                return false;
            var toCompare = (Input) obj;
            return this.code.equals(toCompare.code);
        }
    }

    private abstract class WaitingInput extends Input implements Serializable {
        public final int waitingLength;

        public WaitingInput(String code, String name, int length) {
            super(code, name);
            this.waitingLength = length;
        }
    }

    private abstract class ValuedInput extends Input implements Serializable {
        public String address;


        public ValuedInput(String code, String address, String name) {
            super(code, name);
            this.address = address;
        }
    }

    public HP15c() {
        setupOperations();
        this.display = new HP15cDisplay();
        this.setUpMainListener();
        this.commandBuffer = new LinkedList<>();
        this.stack = new double[4];
        this.precision = 4;
        this.fixFormatter = new DecimalFormat(fixPatterns[precision]);
        this.scientificFormatter = new DecimalFormat(scientificPatterns[precision > 6 ? 6 : precision]);
        this.displayFormatter = this.fixFormatter;
        this.setTitle("HP 15c");
        this.setDefaultCloseOperation(EXIT_ON_CLOSE);
        this.addKeyListener(mainListener);
        this.add(display);
        this.pack();
        this.setVisible(true);
        display.drawBuffer(displayFormatter.format(Double.parseDouble(xRegisterString)));
        display.drawAngleAnnunciator(Annunciator.State.OFF);
    }

    public HP15c(String state) {
        this();
        try {
            var fileIn = new FileInputStream("States/" + state + ".ser");
            var objectIn = new ObjectInputStream(fileIn);
            this.loadState((HP15cState) objectIn.readObject());
            fileIn.close();
            objectIn.close();
        } catch (Exception exception) {
            System.out.println("no default state found, creating a new one");
            try {
                var fileOut = new FileOutputStream("States/default.ser");
                var objectOut = new ObjectOutputStream(fileOut);
                objectOut.writeObject(this.getHP15cState());
                fileOut.close();
                objectOut.close();
            } catch (Exception exception2) {
                exception2.printStackTrace();
            }
        }
    }

    public static void main(String... args) {
        if (args.length == 0)
            SwingUtilities.invokeLater(() -> new HP15c("default"));
        else
            SwingUtilities.invokeLater(() -> new HP15c(args[0]));
    }

    public void loadState(HP15cState state) {
        this.stack = state.stack;
        this.programMemory = state.programMemory;
        this.registers = state.registers;
        this.programMode = state.programMode;
        this.programIndex = state.programIndex;
        this.displayMode = state.displayMode;
        this.angleMode = state.angleMode;
        this.precision = state.precision;
    }

    public void setUpMainListener() {
        this.mainListener = new KeyListener() {
            @Override
            public void keyReleased(KeyEvent e) {
                var inputCode = e.getKeyCode();
                var inputChar = e.getKeyChar();
                var inputString = String.valueOf(e.getKeyCode());
                if (awaitingInput) {
                    waitingInputString = waitingInputString + inputChar;
                    if (waitingInput.equals(operationMap.get("sto")) || waitingInput.equals(operationMap.get("rcl"))) {
                        if (registerMap.containsKey(waitingInputString)) {
                            waitingInput.input();
                            waitingInput = null;
                            waitingInputString = "";
                        }
                    }
                    else if (waitingInputString.length() == waitingInput.waitingLength) {
                        waitingInput.input();
                        waitingInput = null;
                        waitingInputString = "";
                        return;
                    }
                }
                //if entering a single character command
                if (operationMap.containsKey(inputString)) {
                    if (programMode) {
                        switch (inputString) {
                            case "8":       //backspace
                                operationMap.get(inputString).input();
                                break;
                            case "32":      //space
                                shiftProgramMemoryForward();
                                operationMap.get(inputString).input();
                                break;
                            default:
                                shiftProgramMemoryForward();
                                programMemory[++programIndex] = operationMap.get(inputString);
                                display.drawProgram(programIndex, operationMap.get(inputString));
                                break;
                        }
                    }
                    else {
                        operationMap.get(inputString).input();
                        commandBuffer.clear();
                    }
                    return;
                }
                //if entering a multi-character command
                if (Character.isAlphabetic(inputChar)) {
                    lastEntry = LastEntry.COMMAND;
                    commandBuffer.add(inputChar);
                    display.updateCommandDisplay(commandBuffer);
                    var commandString = charLLtoString(commandBuffer);
                    if (operationMap.containsKey(commandString)) {
                        if (programMode) {
                            switch (commandString) {
                                case "pr":
                                    operationMap.get(commandString).input();
                                    break;
                                case "lbl":
                                case "sto":
                                case "rcl":
                                case "fix":
                                case "sci":
                                case "eng":
                                case "gto":
                                case "gsb":
                                    shiftProgramMemoryForward();
                                    operationMap.get(commandString).input();
                                    break;
                                default:
                                    shiftProgramMemoryForward();
                                    programMemory[++programIndex] = operationMap.get(commandString);
                                    display.drawProgram(programIndex, operationMap.get(commandString));
                                    break;
                            }
                        }
                        else
                            operationMap.get(commandString).input();
                        if (!awaitingInput) {
                            commandBuffer.clear();
                            display.updateCommandDisplay(commandBuffer);
                        }
                        return;
                    }
                    lastEntry = LastEntry.COMMAND;
                }
            }

            @Override
            public void keyTyped(KeyEvent e) {

            }

            @Override
            public void keyPressed(KeyEvent e) {

            }
        };
    }

    private String charLLtoString(LinkedList<Character> characterList) {
        return characterList.stream().map(String::valueOf).collect(Collectors.joining());
    }

    private String formatDisplay() {
        var xReg = stack[XREG];
        if ((xReg >= 1e100 || xReg <= -1e100) || (xReg >= -1e-100 && xReg <= 1e-100) && xReg != 0) {
            return "error";
        }
        switch (displayMode) {
            case FIX:
                if (((xReg > -1e-1 && xReg < 1e-1) || (xReg <= -1e10 || xReg >= 1e10)) && stack[XREG] != 0.0) {
                    scientificFormatter.applyPattern(scientificPatterns[precision]);
                    return scientificFormatter.format(xReg);
                }
                else {
                    fixFormatter.applyPattern(fixPatterns[precision]);
                    return fixFormatter.format(stack[XREG]);
                }
            case SCI:
                scientificFormatter.applyPattern(scientificPatterns[precision]);
                return scientificFormatter.format(xReg);
            case ENG:
                return scientificFormatter.format(xReg);//fix this
        }
        return null;
    }

    private void printStack() {
        //System.out.println("T: " + stack[TREG]);
        //System.out.println("Z: " + stack[ZREG]);
        //System.out.println("Y: " + stack[YREG]);
        //System.out.println("X: " + stack[XREG]);
        //System.out.println();
    }

    private void liftStack() {
        stack[TREG] = stack[ZREG];
        stack[ZREG] = stack[YREG];
        stack[YREG] = stack[XREG];
        stackLift = false;
    }

    private void inputDigit(String digit) {
        lastEntry = LastEntry.DIGIT;
        if ((xRegisterString.contains(".") ? xRegisterString.length() - 1 : xRegisterString.length()) > 9)
            return;
        if (stackLift) {
            liftStack();
            xRegisterString = digit;
        }
        else {
            xRegisterString = xRegisterString + digit;
        }
        stack[XREG] = Double.parseDouble(xRegisterString);
        if (!programExecution)
            display.drawBuffer(xRegisterString);
    }

    private void executeProgram(HP15c.Input label) {
        programExecution = true;
        var labelIndex = getLabelIndex(label);
        if (labelIndex > -1) {
            programIndex = labelIndex;
            while (programMemory[programIndex] != null && programExecution) {
                programMemory[programIndex++].input();
            }
            var disp = displayFormatter.format(stack[XREG]);
            display.drawBuffer(disp);
        }
    }

    private int getLabelIndex(HP15c.Input label) {
        var index = programIndex;
        var numberChecked = 0;
        while (numberChecked < programMemory.length) {
            if (programMemory[index % programMemory.length] == null)
                ;
            else if (programMemory[index % programMemory.length].equals(label))
                return index % programMemory.length;
            index++;
            numberChecked++;
        }
        return -1;
    }

    private void shiftProgramMemoryForward() {
        var index = programIndex + 1;
        Input currentInput;
        if (programMemory[index] != null)
            currentInput = programMemory[index];
        else
            return;
        Input tempInput;
        while (programMemory[index] != null && index < programMemory.length - 1) {
            tempInput = programMemory[index + 1];
            programMemory[index + 1] = currentInput;
            currentInput = tempInput;
            index++;
        }
    }

    private void shiftProgramMemoryBackward() {
        var index = programIndex;
        if (index == 0)
            return;
        while (programMemory[index + 1] != null && index < programMemory.length - 1) {
            programMemory[index] = programMemory[index + 1];
            index++;
        }
        programMemory[index] = null;
    }

    private void solve(HP15c.Input label) {
        var guess = stack[XREG];
        stack[TREG] = guess;
        stack[ZREG] = guess;
        stack[YREG] = guess;
        executeProgram(label);
        var f = stack[XREG];
        var mag = guess == 0 ? 1 : guess;
        var d0 = Math.random() * 1e-5 * mag;
        var d1 = Math.random() * 1e-5 * mag;
        var delta = d0 + d1;
        stack[XREG] = guess - d0;
        executeProgram(label);
        var x0 = stack[XREG];
        stack[XREG] = guess + d1;
        executeProgram(label);
        var x1 = stack[XREG];
        var fPrime = (x1 - x0) / delta;
        stack[XREG] = guess - (f / fPrime);
        if (stack[XREG] == guess) {
            programExecution = false;
            commandBuffer.clear();
            display.updateCommandDisplay(commandBuffer);
            return;
        }
        solve(label);
    }

    public HP15cState getHP15cState() {
        return new HP15cState(stack, programMemory, registers, programMode, programIndex, displayMode, angleMode, precision);
    }

    private void setupOperations() {
        operationMap.put(String.valueOf(KeyEvent.VK_0), new Input("00", "0") {
            @Override
            public void input() {
                if (!programMode)
                    inputDigit("0");
                else {
                    programMemory[++programIndex] = this;
                    display.drawProgram(programIndex, this);
                }
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_1), new Input("01", "1") {
            @Override
            public void input() {
                if (!programMode)
                    inputDigit("1");
                else {
                    programMemory[++programIndex] = this;
                    display.drawProgram(programIndex, this);
                }
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_2), new Input("02", "2") {
            @Override
            public void input() {
                if (!programMode)
                    inputDigit("2");
                else {
                    programMemory[++programIndex] = this;
                    display.drawProgram(programIndex, this);
                }
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_3), new Input("03", "3") {
            @Override
            public void input() {
                if (!programMode)
                    inputDigit("3");
                else {
                    programMemory[++programIndex] = this;
                    display.drawProgram(programIndex, this);
                }
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_4), new Input("04", "4") {
            @Override
            public void input() {
                if (!programMode)
                    inputDigit("4");
                else {
                    programMemory[++programIndex] = this;
                    display.drawProgram(programIndex, this);
                }
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_5), new Input("05", "5") {
            @Override
            public void input() {
                if (!programMode)
                    inputDigit("5");
                else {
                    programMemory[++programIndex] = this;
                    display.drawProgram(programIndex, this);
                }
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_6), new Input("06", "6") {
            @Override
            public void input() {
                if (!programMode)
                    inputDigit("6");
                else {
                    programMemory[++programIndex] = this;
                    display.drawProgram(programIndex, this);
                }
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_7), new Input("07", "7") {
            @Override
            public void input() {
                if (!programMode)
                    inputDigit("7");
                else {
                    programMemory[++programIndex] = this;
                    display.drawProgram(programIndex, this);
                }
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_8), new Input("08", "8") {
            @Override
            public void input() {
                if (!programMode)
                    inputDigit("8");
                else {
                    programMemory[++programIndex] = this;
                    display.drawProgram(programIndex, this);
                }
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_9), new Input("09", "9") {
            @Override
            public void input() {
                if (!programMode)
                    inputDigit("9");
                else {
                    programMemory[++programIndex] = this;
                    display.drawProgram(programIndex, this);
                }
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_ENTER), new Input("16", "ENTER") {
            @Override
            public void input() {
                lastEntry = LastEntry.COMMAND;
                liftStack();
                xRegisterString = "";
                if (!programExecution)
                    display.drawBuffer(formatDisplay());
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_BACK_SPACE), new Input("35", "BACKSPACE") {
            @Override
            public void input() {
                if (!programMode) {
                    if (lastEntry == LastEntry.DIGIT) {
                        switch (xRegisterString.length()) {
                            case 0:
                                break;
                            case 1:
                                xRegisterString = "";
                                stack[XREG] = 0.0;
                                break;
                            case 2:
                                if (xRegisterString.charAt(0) == '-') {
                                    xRegisterString = "";
                                    stack[XREG] = 0.0;
                                }
                                else {
                                    xRegisterString = xRegisterString.substring(0, 1);
                                    stack[XREG] = Double.parseDouble(xRegisterString);
                                }
                                break;
                            default:
                                xRegisterString = xRegisterString.substring(0, xRegisterString.length() - 1);
                                stack[XREG] = Double.parseDouble(xRegisterString);
                                break;
                        }
                        display.drawBuffer(xRegisterString);
                    }
                    else {
                        if (commandBuffer.isEmpty()) {
                            stack[XREG] = 0.0;
                            display.drawBuffer(formatDisplay());
                        }
                        else
                            commandBuffer.clear();
                        display.updateCommandDisplay(commandBuffer);
                    }
                }
                else {
                    if (commandBuffer.isEmpty()) {
                        if (programIndex > 0) {
                            shiftProgramMemoryBackward();
                            display.drawProgram(--programIndex, programMemory[programIndex]);
                        }
                    }
                    else {
                        commandBuffer.clear();
                        display.updateCommandDisplay(commandBuffer);
                    }
                }
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_SPACE), new Input("00", "COMMAND ENTER") {
            @Override
            public void input() {
                if (!commandBuffer.isEmpty()) {
                    if (programMode) {
                        var operation = operationMap.get(display.getCommand());
                        programMemory[++programIndex] = operation;
                        display.drawProgram(programIndex, operation);
                    }
                    else
                        operationMap.get(display.getCommand()).input();
                    commandBuffer.clear();
                    display.updateCommandDisplay(commandBuffer);
                }
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_PERIOD), new Input("48", "DECIMAL") {
            @Override
            public void input() {
                lastEntry = LastEntry.DIGIT;
                if (xRegisterString.contains("."))
                    return;
                /*if ((xRegisterString.contains(".") ? xRegisterString.length() : xRegisterString.length() - 1) > 9)
                    return;*/
                if (stackLift) {
                    liftStack();
                    xRegisterString = ".";
                    stack[XREG] = 0.;
                }
                else {
                    xRegisterString = xRegisterString + ".";
                    stack[XREG] = Double.parseDouble(xRegisterString);
                }
                if (!programExecution)
                    display.drawBuffer(xRegisterString);
            }
        });

        operationMap.put("settings", new Input("", "SETTINGS") {
            @Override
            public void input() {
                if (programMode)
                    return;
                SwingUtilities.invokeLater(() -> new SettingsWindow(HP15c.this));
            }
        });

        operationMap.put("debug", new Input("", "DEBUG") {
            @Override
            public void input() {
                if (programMode)
                    return;
                SwingUtilities.invokeLater(() -> new HP15cDebugWindow(HP15c.this));
            }
        });

        operationMap.put("clx", new Input("35", "CLX") {
            @Override
            public void input() {
                xRegisterString = "";
                stack[XREG] = 0.0;
                if (!programExecution)
                    display.drawBuffer(formatDisplay());
            }
        });

        operationMap.put("chs", new Input("16", "CHS") {
            @Override
            public void input() {
                if (!programMode) {
                    if (!xRegisterString.isEmpty()) {
                        if (xRegisterString.charAt(0) != '-') {
                            xRegisterString = "-" + xRegisterString;
                        }
                        else {
                            xRegisterString = xRegisterString.substring(1);
                        }
                        stack[XREG] *= -1.0;
                        if (!programExecution)
                            display.drawBuffer(xRegisterString);
                    }
                    else {
                        stack[XREG] *= -1.0;
                        if (!programExecution)
                            display.drawBuffer(formatDisplay());
                    }
                }
                else {
                    programMemory[++programIndex] = this;
                    display.drawProgram(programIndex, this);
                }
            }
        });

        operationMap.put("abs", new Input("16", "ABS") {
            @Override
            public void input() {
                stack[XREG] = Math.abs(stack[XREG]);
                xRegisterString = "";
                stackLift = true;
                if (!programExecution)
                    display.drawBuffer(formatDisplay());
            }
        });

        operationMap.put("sto", new WaitingInput("44", "STO", 3) {
            @Override
            public void input() {
                display.setCommandPropt("sto");
                if (waitingInput == null) {
                    awaitingInput = true;
                    waitingInput = this;
                    return;
                }
                var address = waitingInputString;
                if (registerMap.containsKey(address)) {
                    if (programMode) {
                        if (address.equals("ind")) {
                            programMemory[++programIndex] = new ValuedInput("44", address, "STO ind") {
                                @Override
                                public void input() {
                                    var index = (int) registers[registerMap.get("irg")];
                                    if (index < 0 || index > 65)
                                        return;//error out
                                    else
                                        registers[index] = stack[XREG];
                                    stackLift = true;
                                }
                            };
                        }
                        else {
                            programMemory[++programIndex] = new ValuedInput("44", address, "STO " + address) {
                                @Override
                                public void input() {
                                    registers[registerMap.get(address)] = stack[XREG];
                                    stackLift = true;
                                }
                            };
                        }
                        display.drawProgram(programIndex, programMemory[programIndex]);
                    }
                    else {
                        if (address.equals("ind")) {
                            var index = (int) registers[registerMap.get("irg")];
                            if (index < 0 || index > 65)
                                return;//error out
                            else
                                registers[index] = stack[XREG];
                        }
                        else
                            registers[registerMap.get(address)] = stack[XREG];
                        stackLift = true;
                        display.drawBuffer(formatDisplay());
                    }
                    display.setCommandPropt(null);
                    awaitingInput = false;
                    commandBuffer.clear();
                    display.updateCommandDisplay(commandBuffer);
                }
                if (address.length() > 3 || address.contains("\b")) {
                    if (programMode)
                        display.drawProgram(programIndex, programMemory[programIndex]);
                    else {
                        stackLift = true;
                        display.drawBuffer(formatDisplay());
                    }
                    display.setCommandPropt(null);
                    awaitingInput = false;
                    commandBuffer.clear();
                    display.updateCommandDisplay(commandBuffer);
                }
            }
        });

        operationMap.put("rcl", new WaitingInput("45", "RCL", 3) {
            @Override
            public void input() {
                display.setCommandPropt("rcl");
                if (waitingInput == null) {
                    awaitingInput = true;
                    waitingInput = this;
                    return;
                }
                var address = waitingInputString;
                if (registerMap.containsKey(address)) {
                    if (programMode) {
                        programMemory[++programIndex] = new ValuedInput("44", address, "RCl " + address) {
                            @Override
                            public void input() {
                                stack[XREG] = registers[registerMap.get(address)];
                            }
                        };
                        display.drawProgram(programIndex, programMemory[programIndex]);
                    }
                    else {
                        if (address.equals("ind")) {
                            var index = (int) registers[registerMap.get("irg")];
                            if (index < 0 || index > 65)
                                return;//error out
                            else {
                                liftStack();
                                stack[XREG] = registers[index];
                            }
                        }
                        else {
                            liftStack();
                            stack[XREG] = registers[registerMap.get(address)];
                        }
                        stackLift = true;
                        display.drawBuffer(formatDisplay());
                    }
                    display.setCommandPropt(null);
                    awaitingInput = false;
                    commandBuffer.clear();
                    display.updateCommandDisplay(commandBuffer);

                }
                if (address.length() > 3 || address.contains("\b")) {
                    if (programMode)
                        display.drawProgram(programIndex, programMemory[programIndex]);
                    else {
                        stackLift = true;
                        display.drawBuffer(formatDisplay());
                    }
                    display.setCommandPropt(null);
                    awaitingInput = false;
                    commandBuffer.clear();
                    display.updateCommandDisplay(commandBuffer);
                }
            }
        });

        operationMap.put("rd", new Input("33", "RD") {
            @Override
            public void input() {
                var temp = stack[XREG];
                stack[XREG] = stack[YREG];
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                stack[TREG] = temp;
                xRegisterString = "";
                if (!programExecution)
                    display.drawBuffer(formatDisplay());
                //printStack();
            }
        });

        operationMap.put("ru", new Input("33", "RU") {
            @Override
            public void input() {
                var temp = stack[TREG];
                stack[TREG] = stack[ZREG];
                stack[ZREG] = stack[YREG];
                stack[YREG] = stack[XREG];
                stack[XREG] = temp;
                xRegisterString = "";
                if (!programExecution)
                    display.drawBuffer(formatDisplay());
            }
        });

        operationMap.put("plus", new Input("40", "PLUS") {
            @Override
            public void input() {
                stack[XREG] = stack[YREG] + stack[XREG];
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay());
                    printStack();
                }
            }
        });

        operationMap.put("sub", new Input("30", "SUB") {
            @Override
            public void input() {
                stack[XREG] = stack[YREG] - stack[XREG];
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay());
                    printStack();
                }
            }
        });

        operationMap.put("mult", new Input("20", "MULT") {
            @Override
            public void input() {
                stack[XREG] = stack[YREG] * stack[XREG];
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay());
                    printStack();
                }
            }
        });

        operationMap.put("div", new Input("10", "DIV") {
            @Override
            public void input() {
                stack[XREG] = stack[YREG] / stack[XREG];
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay());
                    printStack();
                }
            }
        });

        operationMap.put("sqt", new Input("11", "SQT") {
            @Override
            public void input() {
                stack[XREG] = Math.sqrt(stack[XREG]);
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay());
                    printStack();
                }
            }
        });

        operationMap.put("exp", new Input("12", "EXP") {
            @Override
            public void input() {
                stack[XREG] = Math.exp(stack[XREG]);
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay());
                    printStack();
                }
            }
        });

        operationMap.put("alog", new Input("13", "ALOG") {
            @Override
            public void input() {
                stack[XREG] = Math.pow(10.0, stack[XREG]);
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay());
                    printStack();
                }
            }
        });

        operationMap.put("yx", new Input("14", "YX") {
            @Override
            public void input() {
                stack[XREG] = Math.pow(stack[YREG], stack[XREG]);
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay());
                    printStack();
                }
            }
        });

        operationMap.put("inv", new Input("15", "INV") {
            @Override
            public void input() {
                stack[XREG] = 1.0 / stack[XREG];
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay());
                    printStack();
                }
            }
        });

        operationMap.put("sqr", new Input("11", "SQR") {
            @Override
            public void input() {
                stack[XREG] = stack[XREG] * stack[XREG];
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay());
                    printStack();
                }
            }
        });

        operationMap.put("ln", new Input("12", "LN") {
            @Override
            public void input() {
                stack[XREG] = Math.log(stack[XREG]);
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay());
                    printStack();
                }
            }
        });

        operationMap.put("log", new Input("13", "LOG") {
            @Override
            public void input() {
                stack[XREG] = Math.log10(stack[XREG]);
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay());
                    printStack();
                }
            }
        });

        operationMap.put("per", new Input("14", "PER") {
            @Override
            public void input() {
                stack[XREG] = stack[YREG] * .01 * stack[XREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay());
                    printStack();
                }
            }
        });

        operationMap.put("pch", new Input("15", "PCH") {
            @Override
            public void input() {
                stack[XREG] = (stack[XREG] - stack[YREG]) * 100 / stack[YREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay());
                    printStack();
                }
            }
        });

        operationMap.put("sin", new Input("23", "SIN") {
            @Override
            public void input() {
                switch (angleMode) {
                    case DEG:
                        stack[XREG] = Math.sin(stack[XREG] * Math.PI / 180);
                        break;
                    case RAD:
                        stack[XREG] = Math.sin(stack[XREG]);
                        break;
                    case GRAD:
                        stack[XREG] = Math.sin(stack[XREG] / 200 * Math.PI);
                        break;
                }
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay());
                    printStack();
                }
            }
        });

        operationMap.put("cos", new Input("24", "COS") {
            @Override
            public void input() {
                switch (angleMode) {
                    case DEG:
                        stack[XREG] = Math.cos(stack[XREG] * Math.PI / 180);
                        break;
                    case RAD:
                        stack[XREG] = Math.cos(stack[XREG]);
                        break;
                    case GRAD:
                        stack[XREG] = Math.cos(stack[XREG] / 200 * Math.PI);
                        break;
                }
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay());
                    printStack();
                }
            }
        });

        operationMap.put("tan", new Input("25", "TAN") {
            @Override
            public void input() {
                switch (angleMode) {
                    case DEG:
                        stack[XREG] = Math.tan(stack[XREG] * Math.PI / 180);
                        break;
                    case RAD:
                        stack[XREG] = Math.tan(stack[XREG]);
                        break;
                    case GRAD:
                        stack[XREG] = Math.tan(stack[XREG] / 200 * Math.PI);
                        break;
                }
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay());
                    printStack();
                }
            }
        });

        operationMap.put("hsin", new Input("23", "HSIN") {
            @Override
            public void input() {
                switch (angleMode) {
                    case DEG:
                        stack[XREG] = Math.sinh(stack[XREG] * Math.PI / 180);
                        break;
                    case RAD:
                        stack[XREG] = Math.sinh(stack[XREG]);
                        break;
                    case GRAD:
                        stack[XREG] = Math.sinh(stack[XREG] / 200 * Math.PI);
                        break;
                }
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay());
                    printStack();
                }
            }
        });

        operationMap.put("hcos", new Input("24", "HCOS") {
            @Override
            public void input() {
                switch (angleMode) {
                    case DEG:
                        stack[XREG] = Math.cosh(stack[XREG] * Math.PI / 180);
                        break;
                    case RAD:
                        stack[XREG] = Math.cosh(stack[XREG]);
                        break;
                    case GRAD:
                        stack[XREG] = Math.cosh(stack[XREG] / 200 * Math.PI);
                        break;
                }
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay());
                    printStack();
                }
            }
        });

        operationMap.put("htan", new Input("25", "HTAN") {
            @Override
            public void input() {
                switch (angleMode) {
                    case DEG:
                        stack[XREG] = Math.tanh(stack[XREG] * Math.PI / 180);
                        break;
                    case RAD:
                        stack[XREG] = Math.tanh(stack[XREG]);
                        break;
                    case GRAD:
                        stack[XREG] = Math.tanh(stack[XREG] / 200 * Math.PI);
                        break;
                }
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay());
                    printStack();
                }
            }
        });

        operationMap.put("int", new Input("44", "INT") {
            @Override
            public void input() {
                stack[XREG] = Math.floor(stack[XREG]);
                xRegisterString = "";
                stackLift = true;
                if (!programExecution)
                    display.drawBuffer(formatDisplay());
            }
        });

        operationMap.put("frac", new Input("44", "FRAC") {
            @Override
            public void input() {
                stack[XREG] = stack[XREG] - Math.floor(stack[XREG]);
                xRegisterString = "";
                stackLift = true;
                if (!programExecution)
                    display.drawBuffer(formatDisplay());
            }
        });

        operationMap.put("fix", new Input("17", "FIX") {
            @Override
            public void input() {
                removeKeyListener(mainListener);
                addKeyListener(new KeyListener() {
                    @Override
                    public void keyReleased(KeyEvent e) {
                        var inputChar = e.getKeyChar();
                        if (!Character.isDigit(inputChar))
                            return;
                        if (programMode) {
                            programMemory[++programIndex] = new ValuedInput("17", String.valueOf(inputChar), String.valueOf(inputChar)) {
                                @Override
                                public void input() {
                                    displayMode = DisplayMode.FIX;
                                    precision = Integer.parseInt(address);
                                }
                            };
                            display.drawProgram(programIndex, programMemory[programIndex]);
                        }
                        else {
                            displayMode = DisplayMode.FIX;
                            precision = Character.getNumericValue(inputChar);
                            display.drawBuffer(formatDisplay());
                        }
                        removeKeyListener(getKeyListeners()[0]);
                        addKeyListener(mainListener);
                    }

                    @Override
                    public void keyPressed(KeyEvent e) {

                    }

                    @Override
                    public void keyTyped(KeyEvent e) {

                    }
                });
            }
        });

        operationMap.put("sci", new Input("18", "SCI") {
            @Override
            public void input() {
                removeKeyListener(mainListener);
                addKeyListener(new KeyListener() {
                    @Override
                    public void keyReleased(KeyEvent e) {
                        var inputChar = e.getKeyChar();
                        if (!Character.isDigit(inputChar))
                            return;
                        if (programMode) {
                            programMemory[++programIndex] = new ValuedInput("17", String.valueOf(inputChar), String.valueOf(inputChar)) {
                                @Override
                                public void input() {
                                    displayMode = DisplayMode.SCI;
                                    precision = Integer.parseInt(address);
                                }
                            };
                            display.drawProgram(programIndex, programMemory[programIndex]);
                        }
                        else {
                            displayMode = DisplayMode.SCI;
                            var tempPrecision = Character.getNumericValue(inputChar);
                            if (tempPrecision > 6)
                                tempPrecision = 6;
                            precision = tempPrecision;
                            display.drawBuffer(formatDisplay());
                        }
                        removeKeyListener(getKeyListeners()[0]);
                        addKeyListener(mainListener);
                    }

                    @Override
                    public void keyPressed(KeyEvent e) {

                    }

                    @Override
                    public void keyTyped(KeyEvent e) {

                    }
                });
            }
        });

        operationMap.put("eng", new Input("19", "ENG") {
            @Override
            public void input() {
                removeKeyListener(mainListener);
                addKeyListener(new KeyListener() {
                    @Override
                    public void keyReleased(KeyEvent e) {
                        var inputChar = e.getKeyChar();
                        if (!Character.isDigit(inputChar))
                            return;
                        if (programMode) {
                            programMemory[++programIndex] = new ValuedInput("17", String.valueOf(inputChar), String.valueOf(inputChar)) {
                                @Override
                                public void input() {
                                    displayMode = DisplayMode.ENG;
                                    precision = Integer.parseInt(address);
                                }
                            };
                            display.drawProgram(programIndex, programMemory[programIndex]);
                        }
                        else {
                            displayMode = DisplayMode.ENG;
                            precision = Character.getNumericValue(inputChar);
                            display.drawBuffer(formatDisplay());
                        }
                        removeKeyListener(getKeyListeners()[0]);
                        addKeyListener(mainListener);
                    }

                    @Override
                    public void keyPressed(KeyEvent e) {
                    }

                    @Override
                    public void keyTyped(KeyEvent e) {

                    }
                });
            }
        });

        operationMap.put("deg", new Input("17", "DEG") {
            @Override
            public void input() {
                angleMode = AngleMode.DEG;
                display.drawAngleAnnunciator(Annunciator.State.DEG);
            }
        });

        operationMap.put("rad", new Input("18", "RAD") {
            @Override
            public void input() {
                angleMode = AngleMode.RAD;
                display.drawAngleAnnunciator(Annunciator.State.RAD);
            }
        });

        operationMap.put("grad", new Input("19", "GRAD") {
            @Override
            public void input() {
                angleMode = AngleMode.GRAD;
                display.drawAngleAnnunciator(Annunciator.State.GRAD);
            }
        });

        //advanced functions
        operationMap.put("solve", new WaitingInput("10", "SOLVE", 1) {
            @Override
            public void input() {
                display.setCommandPropt("solve");
                if (waitingInput == null) {
                    awaitingInput = true;
                    waitingInput = this;
                    return;
                }
                var address = waitingInputString;
                if (labelMap.containsKey(address)) {
                    if (programMode) {
                        ;//do stuff
                    }
                    else {
                        if (getLabelIndex(labelMap.get(address)) == -1) {
                            System.out.println("label not found");
                            return;
                        }
                        solve(labelMap.get(address));
                        display.drawBuffer(formatDisplay());
                    }
                }
                display.setCommandPropt(null);
                awaitingInput = false;
                commandBuffer.clear();
                display.updateCommandDisplay(commandBuffer);
            }
        });

        //program operations
        operationMap.put("pr", new Input("50", "PR") {
            @Override
            public void input() {
                programMode = !programMode;
                if (programMode)
                    display.drawProgram(programIndex, programMemory[programIndex]);
                else
                    display.drawBuffer(displayFormatter.format(stack[XREG]));
                display.drawProgramAnnunciator(programMode);
            }
        });

        operationMap.put("lbl", new WaitingInput("31", "LBL", 1) {
            @Override
            public void input() {
                display.setCommandPropt("lbl");
                if (waitingInput == null) {
                    awaitingInput = true;
                    waitingInput = this;
                    return;
                }
                var address = waitingInputString;
                if (!programMode)
                    return;
                if (labelMap.containsKey(address)) {
                    programMemory[++programIndex] = labelMap.get(address);
                    display.drawProgram(programIndex, labelMap.get(address));
                }
                awaitingInput = false;
                commandBuffer.clear();
                display.updateCommandDisplay(commandBuffer);
            }
        });

        operationMap.put("rtn", new Input("42", "RTN") {
            @Override
            public void input() {
                if (programExecution) {
                    if (programStack.isEmpty()) {
                        programExecution = false;
                        stackLift = true;
                        programIndex = 0;
                    }
                    else {
                        stackLift = true;
                        programIndex = programStack.pop();
                    }
                }
            }
        });

        operationMap.put("gto", new ValuedInput("32", "", "GTO") {
            @Override
            public void input() {
                address = "";
                display.setCommandPropt("gto");
                awaitingInput = true;
                removeKeyListener(mainListener);
                addKeyListener(new KeyListener() {
                    @Override
                    public void keyReleased(KeyEvent e) {
                        var inputString = String.valueOf(e.getKeyCode());
                        var inputChars = String.valueOf(e.getKeyChar());
                        address = address + inputChars;
                        if (address.charAt(0) == 'n') {
                            if (programMode) {
                                removeKeyListener(getKeyListeners()[0]);
                                addKeyListener(mainListener);
                                awaitingInput = false;
                                commandBuffer.clear();
                                display.updateCommandDisplay(commandBuffer);
                                return;
                            }
                            display.setCommandPropt("gto nnn");
                            if (address.length() == 4) {
                                try {
                                    var index = Integer.parseInt(address.substring(1));
                                    if (programMemory[index] != null)
                                        programIndex = index;
                                    else
                                        display.drawBuffer("error");
                                } catch (NumberFormatException ignored) {
                                }
                                removeKeyListener(getKeyListeners()[0]);
                                addKeyListener(mainListener);
                                awaitingInput = false;
                                commandBuffer.clear();
                                display.updateCommandDisplay(commandBuffer);
                            }
                        }
                        else {
                            if (programMode) {
                                programMemory[++programIndex] = new ValuedInput("32", inputString, "GTO " + address) {
                                    @Override
                                    public void input() {
                                        stackLift = true;
                                        if (labelMap.containsKey(address)) {
                                            var labelIndex = getLabelIndex(labelMap.get(address));
                                            if (labelIndex > 0)
                                                programIndex = labelIndex;
                                        }
                                    }
                                };
                                display.drawProgram(programIndex, programMemory[programIndex]);
                            }
                            else {
                                if (labelMap.containsKey(inputString)) {
                                    var labelIndex = getLabelIndex(labelMap.get(inputString));
                                    if (labelIndex > 0) {
                                        programIndex = labelIndex;
                                    }
                                    else
                                        display.drawBuffer("error");
                                }
                                else
                                    display.drawBuffer("error");
                            }
                            removeKeyListener(getKeyListeners()[0]);
                            addKeyListener(mainListener);
                            awaitingInput = false;
                            commandBuffer.clear();
                            display.updateCommandDisplay(commandBuffer);
                        }
                    }

                    @Override
                    public void keyPressed(KeyEvent e) {

                    }

                    @Override
                    public void keyTyped(KeyEvent e) {

                    }
                });
            }
        });

        operationMap.put("gsb", new Input("42", "GSB") {
            @Override
            public void input() {
                display.setCommandPropt("gsb");
                awaitingInput = true;
                removeKeyListener(mainListener);
                addKeyListener(new KeyListener() {
                    @Override
                    public void keyReleased(KeyEvent e) {
                        var inputString = String.valueOf(e.getKeyCode());
                        if (programMode) {
                            programMemory[++programIndex] = new ValuedInput("32", inputString, "GSB " + labelMap.get(inputString).name) {
                                @Override
                                public void input() {
                                    stackLift = true;
                                    if (labelMap.containsKey(address)) {
                                        var labelIndex = getLabelIndex(labelMap.get(address));
                                        if (labelIndex > 0) {
                                            stackLift = true;
                                            programStack.push(programIndex);
                                            programIndex = labelIndex;
                                        }
                                    }
                                }
                            };
                            display.drawProgram(programIndex, programMemory[programIndex]);
                        }
                        else {
                            if (labelMap.containsKey(inputString)) {
                                var labelIndex = getLabelIndex(labelMap.get(inputString));
                                if (labelIndex > 0) {
                                    stackLift = true;
                                    programStack.push(programIndex);
                                    executeProgram(labelMap.get(inputString));
                                }
                                else
                                    display.drawBuffer("error");
                            }
                            else
                                display.drawBuffer("error");
                        }
                        removeKeyListener(getKeyListeners()[0]);
                        addKeyListener(mainListener);
                        awaitingInput = false;
                        commandBuffer.clear();
                        display.updateCommandDisplay(commandBuffer);
                    }

                    @Override
                    public void keyPressed(KeyEvent e) {

                    }

                    @Override
                    public void keyTyped(KeyEvent e) {

                    }
                });
            }
        });


        labelMap.put("a", new Input("42,21,11", "LBL A") {
            @Override
            public void input() {

            }
        });
        labelMap.put("b", new Input("42,21,12", "LBL B") {
            @Override
            public void input() {

            }
        });
        labelMap.put("c", new Input("42,21,13", "LBL C") {
            @Override
            public void input() {

            }
        });
        labelMap.put("d", new Input("42,21,14", "LBL D") {
            @Override
            public void input() {

            }
        });
        labelMap.put("e", new Input("42,21,15", "LBL E") {
            @Override
            public void input() {

            }
        });
        labelMap.put("0", new Input("42,21, 0", "LBL 0") {
            @Override
            public void input() {

            }
        });
        labelMap.put("1", new Input("42,21, 1", "LBL 1") {
            @Override
            public void input() {

            }
        });
        labelMap.put("2", new Input("42,21, 2", "LBL 2") {
            @Override
            public void input() {

            }
        });
        labelMap.put("3", new Input("42,21, 3", "LBL 3") {
            @Override
            public void input() {

            }
        });
        labelMap.put("4", new Input("42,21, 4", "LBL 4") {
            @Override
            public void input() {

            }
        });
        labelMap.put("5", new Input("42,21, 5", "LBL 5") {
            @Override
            public void input() {

            }
        });
        labelMap.put("6", new Input("42,21, 6", "LBL 6") {
            @Override
            public void input() {

            }
        });
        labelMap.put("7", new Input("42,21, 7", "LBL 7") {
            @Override
            public void input() {

            }
        });
        labelMap.put("8", new Input("42,21, 8", "LBL 8") {
            @Override
            public void input() {

            }
        });
        labelMap.put("9", new Input("42,21, 9", "LBL 9") {
            @Override
            public void input() {

            }
        });

        operationMap.put("fa", new Input("11", "A") {
            @Override
            public void input() {
                programExecution = true;
                executeProgram(labelMap.get("a"));
            }
        });
        operationMap.put("fb", new Input("12", "B") {
            @Override
            public void input() {
                programExecution = true;
                executeProgram(labelMap.get("b"));
            }
        });
        operationMap.put("fc", new Input("13", "C") {
            @Override
            public void input() {
                programExecution = true;
                executeProgram(labelMap.get("c"));
            }
        });
        operationMap.put("fd", new Input("14", "D") {
            @Override
            public void input() {
                programExecution = true;
                executeProgram(labelMap.get("d"));
            }
        });
        operationMap.put("fe", new Input("15", "E") {
            @Override
            public void input() {
                programExecution = true;
                executeProgram(labelMap.get("e"));
            }
        });

        registerMap.put("0", 0);
        registerMap.put("1", 1);
        registerMap.put("2", 2);
        registerMap.put("3", 3);
        registerMap.put("4", 4);
        registerMap.put("5", 5);
        registerMap.put("6", 6);
        registerMap.put("7", 7);
        registerMap.put("8", 8);
        registerMap.put("9", 9);
        registerMap.put(".0", 10);
        registerMap.put(".1", 11);
        registerMap.put(".2", 12);
        registerMap.put(".3", 13);
        registerMap.put(".4", 14);
        registerMap.put(".5", 15);
        registerMap.put(".6", 16);
        registerMap.put(".7", 17);
        registerMap.put(".8", 18);
        registerMap.put(".9", 19);
        registerMap.put("irg", 66);
        registerMap.put("ind", null);
    }
}

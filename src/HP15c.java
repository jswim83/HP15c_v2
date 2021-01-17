import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Stack;
import java.util.stream.Collectors;

public class HP15c extends JFrame implements KeyListener {

    private final HP15cDisplay display;
    private String xRegisterString = "0";
    private final LinkedList<Character> commandBuffer;
    private final double[] stack;
    private final Input[] programMemory = new Input[448];
    private final Stack<Integer> programStack = new Stack<>();
    private final double[] registers = new double[67];
    private static final HashMap<String, Input> operationMap = new HashMap<>(20);
    private static final HashMap<String, Input> labelMap = new HashMap<>(15);
    private static final HashMap<String, Integer> registerMap = new HashMap<>(21);
    private boolean programMode;
    private boolean programExecution;
    private int programIndex;
    private boolean stackLift = true;
    private LastEntry lastEntry = LastEntry.DIGIT;
    private DisplayMode displayMode = DisplayMode.FIX;
    private AngleMode angleMode = AngleMode.RAD;
    private int precision;
    private DecimalFormat displayFormatter;
    private DecimalFormat fixFormatter;
    private DecimalFormat scientificFormatter;

    private static final int XREG = 0;
    private static final int YREG = 1;
    private static final int ZREG = 2;
    private static final int TREG = 3;

    private enum LastEntry {
        DIGIT, COMMAND;
    }

    private enum DisplayMode {
        FIX, SCI, ENG;
    }

    public enum AngleMode {
        DEG, RAD, GRAD;
    }

    private static final String[] fixPatterns = {
            "#,##0.", "#,##0.0", "#,##0.00", "#,##0.000", "#,##0.0000",
            "#,##0.00000", "#,##0.000000", "#,##0.0000000", "#,##0.00000000", "#,##0.000000000"
    };
    private static final String[] scientificPatterns = {
            "0.E00", "0.0E00", "0.00E00", "0.000E00", "0.0000E00", "0.00000E00",
            "0.000000E00", "0.0000000E00", "0.00000000E00", "0.000000000E00",
    };

    public abstract class Input {
        public String code;

        public Input(String code) {
            this.code = code;
        }

        public abstract void input();
    }

    private abstract class ValuedInput extends Input {
        public String address;

        public ValuedInput(String code, String address) {
            super(code);
            this.address = address;
        }
    }

    public HP15c() {
        setupOperations();
        this.display = new HP15cDisplay();
        this.commandBuffer = new LinkedList<>();
        this.stack = new double[4];
        this.precision = 4;
        this.fixFormatter = new DecimalFormat(fixPatterns[precision]);
        this.scientificFormatter = new DecimalFormat(scientificPatterns[precision]);
        this.displayFormatter = this.fixFormatter;
        this.setDefaultCloseOperation(EXIT_ON_CLOSE);
        this.addKeyListener(this);
        this.add(display);
        this.pack();
        this.setVisible(true);
        display.drawBuffer(displayFormatter.format(Double.parseDouble(xRegisterString)), angleMode);
        display.drawAngleAnnunciator(Annunciator.State.OFF);
    }

    public static void main(String... args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                new HP15c();
            }
        });
    }

    @Override
    public void keyReleased(KeyEvent e) {
        var inputCode = e.getKeyCode();
        var inputChar = e.getKeyChar();
        var inputString = String.valueOf(inputCode);
        //if entering a single character command
        if (operationMap.containsKey(inputString)) {
            if (programMode) {
                switch (inputString) {
                    case "8":       //backspace
                    case "32":      //space
                        operationMap.get(inputString).input();
                        break;
                    default:
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
                        case "lbl":
                        case "sto":
                        case "rcl":
                            operationMap.get(commandString).input();
                            break;
                        default:
                            programMemory[++programIndex] = operationMap.get(commandString);
                            display.drawProgram(programIndex, operationMap.get(commandString));
                            break;
                    }
                }
                else
                    operationMap.get(commandString).input();
                commandBuffer.clear();
                display.updateCommandDisplay(commandBuffer);
                return;
            }
            lastEntry = LastEntry.COMMAND;
            return;
        }
        System.out.println("invalid key");
    }

    private String charLLtoString(LinkedList<Character> characterList) {
        return characterList.stream().map(String::valueOf).collect(Collectors.joining());
    }

    private String formatDisplay() {
        var xReg = stack[XREG];
        switch (displayMode) {
            case FIX:
                if (((xReg > -1e-1 && xReg < 1e-1) || (xReg <= -1e10 && xReg >= 1e10)) && stack[XREG] != 0.0) {
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

    private String formatScientificNotation() {
        return null;
    }

    private void printStack() {
        System.out.println("T: " + stack[TREG]);
        System.out.println("Z: " + stack[ZREG]);
        System.out.println("Y: " + stack[YREG]);
        System.out.println("X: " + stack[XREG]);
        System.out.println();
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
            display.drawBuffer(xRegisterString, angleMode);
    }

    private void executeProgram(HP15c.Input label) {
        var labelIndex = getLabelIndex(label);
        if (labelIndex != null) {
            programIndex = labelIndex;
            while (programMemory[programIndex] != null && programExecution) {
                programMemory[programIndex++].input();
            }
            display.drawBuffer(displayFormatter.format(stack[XREG]), angleMode);
        }
    }

    private Integer getLabelIndex(HP15c.Input label) {
        var index = programIndex;
        var numberChecked = 0;
        while (numberChecked < programMemory.length) {
            if (programMemory[index++] == label)
                return index;
            numberChecked++;
        }
        return null;
    }

    private void setupOperations() {
        operationMap.put(String.valueOf(KeyEvent.VK_0), new Input("00") {
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

        operationMap.put(String.valueOf(KeyEvent.VK_1), new Input("01") {
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

        operationMap.put(String.valueOf(KeyEvent.VK_2), new Input("02") {
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

        operationMap.put(String.valueOf(KeyEvent.VK_3), new Input("03") {
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

        operationMap.put(String.valueOf(KeyEvent.VK_4), new Input("04") {
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

        operationMap.put(String.valueOf(KeyEvent.VK_5), new Input("05") {
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

        operationMap.put(String.valueOf(KeyEvent.VK_6), new Input("06") {
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

        operationMap.put(String.valueOf(KeyEvent.VK_7), new Input("07") {
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

        operationMap.put(String.valueOf(KeyEvent.VK_8), new Input("08") {
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

        operationMap.put(String.valueOf(KeyEvent.VK_9), new Input("09") {
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

        operationMap.put(String.valueOf(KeyEvent.VK_ENTER), new Input("16") {
            @Override
            public void input() {
                lastEntry = LastEntry.COMMAND;
                liftStack();
                xRegisterString = "";
                if (!programExecution)
                    display.drawBuffer(formatDisplay(), angleMode);
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_BACK_SPACE), new Input("35") {
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
                        display.drawBuffer(xRegisterString, angleMode);
                    }
                    else {
                        if (commandBuffer.isEmpty()) {
                            stack[XREG] = 0.0;
                            display.drawBuffer(formatDisplay(), angleMode);
                        }
                        else
                            commandBuffer.clear();
                        display.updateCommandDisplay(commandBuffer);
                    }
                }
                else {
                    if (commandBuffer.isEmpty()) {
                        if (programIndex > 0) {
                            programMemory[programIndex--] = null;
                            //shift down all subsequent instructions
                            display.drawProgram(programIndex, programMemory[programIndex]);
                        }
                    }
                    else {
                        commandBuffer.clear();
                        display.updateCommandDisplay(commandBuffer);
                    }
                }
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_SPACE), new Input("00") {
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

        operationMap.put(String.valueOf(KeyEvent.VK_PERIOD), new Input("48") {
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
                    display.drawBuffer(xRegisterString, angleMode);
            }
        });

        operationMap.put("clx", new Input("35") {
            @Override
            public void input() {
                xRegisterString = "";
                stack[XREG] = 0.0;
                if (!programExecution)
                    display.drawBuffer(formatDisplay(), angleMode);
            }
        });

        operationMap.put("chs", new Input("16") {
            @Override
            public void input() {
                if (!programMode) {
                    if (!xRegisterString.isEmpty()) {
                        if (xRegisterString.charAt(0) != '-') {
                            xRegisterString = "-" + xRegisterString;
                        }
                        else {
                            xRegisterString = xRegisterString.substring(1, xRegisterString.length());
                        }
                        stack[XREG] *= -1.0;
                        if (!programExecution)
                            display.drawBuffer(xRegisterString, angleMode);
                    }
                    else {
                        stack[XREG] *= -1.0;
                        if (!programExecution)
                            display.drawBuffer(formatDisplay(), angleMode);
                    }
                }
                else {
                    programMemory[++programIndex] = this;
                    display.drawProgram(programIndex, this);
                }
            }
        });

        operationMap.put("sto", new ValuedInput("44", "") {
            @Override
            public void input() {
                address = "";
                removeKeyListener(HP15c.this);
                addKeyListener(new KeyListener() {
                    @Override
                    public void keyReleased(KeyEvent e) {
                        var inputString = String.valueOf(e.getKeyChar());
                        address = address + inputString;
                        if (registerMap.containsKey(address)) {
                            if (programMode) {
                                programMemory[++programIndex] = new ValuedInput("44", address) {
                                    @Override
                                    public void input() {
                                        registers[registerMap.get(address)] = stack[XREG];
                                    }
                                };
                                display.drawProgram(programIndex, programMemory[programIndex]);
                            }
                            else {
                                registers[registerMap.get(address)] = stack[XREG];
                                stackLift = true;
                                display.drawBuffer(formatDisplay(), angleMode);
                            }
                            removeKeyListener(getKeyListeners()[0]);
                            addKeyListener(HP15c.this);
                        }
                        else if (address.length() == 1 && address.charAt(0) == '.')
                            ;
                        else {
                            if (programMode)
                                display.drawProgram(programIndex, programMemory[programIndex]);
                            else {
                                stackLift = true;
                                display.drawBuffer(formatDisplay(), angleMode);
                            }
                            removeKeyListener(getKeyListeners()[0]);
                            addKeyListener(HP15c.this);
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

        operationMap.put("rcl", new ValuedInput("45", "") {
            @Override
            public void input() {
                address = "";
                removeKeyListener(HP15c.this);
                addKeyListener(new KeyListener() {
                    @Override
                    public void keyReleased(KeyEvent e) {
                        var inputString = String.valueOf(e.getKeyChar());
                        address = address + inputString;
                        if (registerMap.containsKey(address)) {
                            if (programMode) {
                                programMemory[++programIndex] = new ValuedInput("44", address) {
                                    @Override
                                    public void input() {
                                        stack[XREG] = registers[registerMap.get(address)];
                                    }
                                };
                                display.drawProgram(programIndex, programMemory[programIndex]);
                            }
                            else {
                                liftStack();
                                stack[XREG] = registers[registerMap.get(address)];
                                stackLift = true;
                                display.drawBuffer(formatDisplay(), angleMode);
                            }
                            removeKeyListener(getKeyListeners()[0]);
                            addKeyListener(HP15c.this);
                        }
                        else if (address.length() == 1 && address.charAt(0) == '.')
                            ;
                        else {
                            if (programMode)
                                display.drawProgram(programIndex, programMemory[programIndex]);
                            else {
                                stackLift = true;
                                display.drawBuffer(formatDisplay(), angleMode);
                            }
                            removeKeyListener(getKeyListeners()[0]);
                            addKeyListener(HP15c.this);
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

        operationMap.put("rd", new Input("33") {
            @Override
            public void input() {
                var temp = stack[XREG];
                stack[XREG] = stack[YREG];
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                stack[TREG] = temp;
                xRegisterString = "";
                if (!programExecution)
                    display.drawBuffer(formatDisplay(), angleMode);
                printStack();
            }
        });

        operationMap.put("ru", new Input("33") {
            @Override
            public void input() {
                var temp = stack[TREG];
                stack[TREG] = stack[ZREG];
                stack[ZREG] = stack[YREG];
                stack[YREG] = stack[XREG];
                stack[XREG] = temp;
                xRegisterString = "";
                if (!programExecution)
                    display.drawBuffer(formatDisplay(), angleMode);
                printStack();
            }
        });

        operationMap.put("plus", new Input("40") {
            @Override
            public void input() {
                stack[XREG] = stack[YREG] + stack[XREG];
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay(), angleMode);
                    printStack();
                }
            }
        });

        operationMap.put("sub", new Input("30") {
            @Override
            public void input() {
                stack[XREG] = stack[YREG] - stack[XREG];
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay(), angleMode);
                    printStack();
                }
            }
        });

        operationMap.put("mult", new Input("20") {
            @Override
            public void input() {
                stack[XREG] = stack[YREG] * stack[XREG];
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay(), angleMode);
                    printStack();
                }
            }
        });

        operationMap.put("div", new Input("10") {
            @Override
            public void input() {
                stack[XREG] = stack[YREG] / stack[XREG];
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay(), angleMode);
                    printStack();
                }
            }
        });

        operationMap.put("sqt", new Input("11") {
            @Override
            public void input() {
                stack[XREG] = Math.sqrt(stack[XREG]);
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay(), angleMode);
                    printStack();
                }
            }
        });

        operationMap.put("exp", new Input("12") {
            @Override
            public void input() {
                stack[XREG] = Math.exp(stack[XREG]);
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay(), angleMode);
                    printStack();
                }
            }
        });

        operationMap.put("alog", new Input("13") {
            @Override
            public void input() {
                stack[XREG] = Math.pow(10.0, stack[XREG]);
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay(), angleMode);
                    printStack();
                }
            }
        });

        operationMap.put("yx", new Input("14") {
            @Override
            public void input() {
                stack[XREG] = Math.pow(stack[YREG], stack[XREG]);
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay(), angleMode);
                    printStack();
                }
            }
        });

        operationMap.put("inv", new Input("15") {
            @Override
            public void input() {
                stack[XREG] = 1.0 / stack[XREG];
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay(), angleMode);
                    printStack();
                }
            }
        });

        operationMap.put("sqr", new Input("11") {
            @Override
            public void input() {
                stack[XREG] = stack[XREG] * stack[XREG];
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay(), angleMode);
                    printStack();
                }
            }
        });

        operationMap.put("ln", new Input("12") {
            @Override
            public void input() {
                stack[XREG] = Math.log(stack[XREG]);
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay(), angleMode);
                    printStack();
                }
            }
        });

        operationMap.put("log", new Input("13") {
            @Override
            public void input() {
                stack[XREG] = Math.log10(stack[XREG]);
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay(), angleMode);
                    printStack();
                }
            }
        });

        operationMap.put("per", new Input("14") {
            @Override
            public void input() {
                stack[XREG] = stack[YREG] * .01 * stack[XREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay(), angleMode);
                    printStack();
                }
            }
        });

        operationMap.put("pch", new Input("15") {
            @Override
            public void input() {
                stack[XREG] = (stack[XREG] - stack[YREG]) * 100 / stack[YREG];
                xRegisterString = "";
                stackLift = true;
                if (!programExecution) {
                    display.drawBuffer(formatDisplay(), angleMode);
                    printStack();
                }
            }
        });

        operationMap.put("sin", new Input("23") {
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
                    display.drawBuffer(formatDisplay(), angleMode);
                    printStack();
                }
            }
        });

        operationMap.put("cos", new Input("24") {
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
                    display.drawBuffer(formatDisplay(), angleMode);
                    printStack();
                }
            }
        });

        operationMap.put("tan", new Input("25") {
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
                    display.drawBuffer(formatDisplay(), angleMode);
                    printStack();
                }
            }
        });

        operationMap.put("hsin", new Input("23") {
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
                    display.drawBuffer(formatDisplay(), angleMode);
                    printStack();
                }
            }
        });

        operationMap.put("hcos", new Input("24") {
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
                    display.drawBuffer(formatDisplay(), angleMode);
                    printStack();
                }
            }
        });

        operationMap.put("htan", new Input("25") {
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
                    display.drawBuffer(formatDisplay(), angleMode);
                    printStack();
                }
            }
        });

        operationMap.put("fix", new Input("17") {
            @Override
            public void input() {
                removeKeyListener(HP15c.this);
                addKeyListener(new KeyListener() {
                    @Override
                    public void keyReleased(KeyEvent e) {
                        System.out.println("inner listener");
                        var inputChar = e.getKeyChar();
                        if (programMode) {
                            programMemory[++programIndex] = new ValuedInput("17", String.valueOf(inputChar)) {
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
                            display.drawBuffer(formatDisplay(), angleMode);
                        }
                        removeKeyListener(getKeyListeners()[0]);
                        addKeyListener(HP15c.this);
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

        operationMap.put("sci", new Input("18") {
            @Override
            public void input() {
                removeKeyListener(HP15c.this);
                addKeyListener(new KeyListener() {
                    @Override
                    public void keyReleased(KeyEvent e) {
                        System.out.println("inner listener");
                        var inputChar = e.getKeyChar();
                        if (programMode) {
                            programMemory[++programIndex] = new ValuedInput("17", String.valueOf(inputChar)) {
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
                            precision = Character.getNumericValue(inputChar);
                            display.drawBuffer(formatDisplay(), angleMode);
                        }
                        removeKeyListener(getKeyListeners()[0]);
                        addKeyListener(HP15c.this);
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

        operationMap.put("eng", new Input("19") {
            @Override
            public void input() {
                removeKeyListener(HP15c.this);
                addKeyListener(new KeyListener() {
                    @Override
                    public void keyReleased(KeyEvent e) {
                        System.out.println("inner listener");
                        var inputChar = e.getKeyChar();
                        if (programMode) {
                            programMemory[++programIndex] = new ValuedInput("17", String.valueOf(inputChar)) {
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
                            display.drawBuffer(formatDisplay(), angleMode);
                        }
                        removeKeyListener(getKeyListeners()[0]);
                        addKeyListener(HP15c.this);
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

        operationMap.put("deg", new Input("17") {
            @Override
            public void input() {
                angleMode = AngleMode.DEG;
                display.drawAngleAnnunciator(Annunciator.State.DEG);
            }
        });

        operationMap.put("rad", new Input("17") {
            @Override
            public void input() {
                angleMode = AngleMode.RAD;
                display.drawAngleAnnunciator(Annunciator.State.RAD);
            }
        });

        operationMap.put("grad", new Input("17") {
            @Override
            public void input() {
                angleMode = AngleMode.GRAD;
                display.drawAngleAnnunciator(Annunciator.State.GRAD);
            }
        });


        //program operations
        operationMap.put("pr", new Input("50") {
            @Override
            public void input() {
                programMode = !programMode;
                programIndex = 0;
                if (programMode)
                    display.drawProgram(programIndex, programMemory[programIndex]);
                else
                    display.drawBuffer(displayFormatter.format(stack[XREG]), angleMode);
                display.drawProgramAnnunciator(programMode);
            }
        });

        operationMap.put("lbl", new Input("31") {
            @Override
            public void input() {
                if (!programMode)
                    return;
                removeKeyListener(HP15c.this);
                addKeyListener(new KeyListener() {
                    @Override
                    public void keyReleased(KeyEvent e) {
                        System.out.println("inner listener");
                        var inputString = String.valueOf(e.getKeyCode());
                        if (labelMap.containsKey(inputString)) {
                            programMemory[++programIndex] = labelMap.get(inputString);
                            display.drawProgram(programIndex, labelMap.get(inputString));
                            System.out.println(labelMap.get(inputString).code);
                        }
                        removeKeyListener(getKeyListeners()[0]);
                        addKeyListener(HP15c.this);
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

        operationMap.put("rtn", new Input("42") {
            @Override
            public void input() {
                if (!programMode || programExecution)
                    return;
                if (programStack.isEmpty()) {
                    programExecution = false;
                    programIndex = 0;
                }
                else {
                    programIndex = programStack.pop();
                }
            }
        });

        labelMap.put(String.valueOf(KeyEvent.VK_A), new Input("424611") {
            @Override
            public void input() {

            }
        });
        labelMap.put(String.valueOf(KeyEvent.VK_B), new Input("424612") {
            @Override
            public void input() {

            }
        });
        labelMap.put(String.valueOf(KeyEvent.VK_C), new Input("424613") {
            @Override
            public void input() {

            }
        });
        labelMap.put(String.valueOf(KeyEvent.VK_D), new Input("424614") {
            @Override
            public void input() {

            }
        });
        labelMap.put(String.valueOf(KeyEvent.VK_E), new Input("424615") {
            @Override
            public void input() {

            }
        });
        labelMap.put(String.valueOf(KeyEvent.VK_0), new Input("4246,00") {
            @Override
            public void input() {

            }
        });
        labelMap.put(String.valueOf(KeyEvent.VK_1), new Input("4246,01") {
            @Override
            public void input() {

            }
        });
        labelMap.put(String.valueOf(KeyEvent.VK_2), new Input("4246,02") {
            @Override
            public void input() {

            }
        });
        labelMap.put(String.valueOf(KeyEvent.VK_3), new Input("4246,03") {
            @Override
            public void input() {

            }
        });
        labelMap.put(String.valueOf(KeyEvent.VK_4), new Input("4246,04") {
            @Override
            public void input() {

            }
        });
        labelMap.put(String.valueOf(KeyEvent.VK_5), new Input("4246,05") {
            @Override
            public void input() {

            }
        });
        labelMap.put(String.valueOf(KeyEvent.VK_6), new Input("4246,06") {
            @Override
            public void input() {

            }
        });
        labelMap.put(String.valueOf(KeyEvent.VK_7), new Input("4246,07") {
            @Override
            public void input() {

            }
        });
        labelMap.put(String.valueOf(KeyEvent.VK_8), new Input("4246,08") {
            @Override
            public void input() {

            }
        });
        labelMap.put(String.valueOf(KeyEvent.VK_9), new Input("4246,09") {
            @Override
            public void input() {

            }
        });

        operationMap.put("fa", new Input("00") {
            @Override
            public void input() {
                programExecution = true;
                executeProgram(labelMap.get(KeyEvent.VK_A));
            }
        });
        operationMap.put("fb", new Input("00") {
            @Override
            public void input() {
                programExecution = true;
                executeProgram(labelMap.get(KeyEvent.VK_B));
            }
        });
        operationMap.put("fc", new Input("00") {
            @Override
            public void input() {
                programExecution = true;
                executeProgram(labelMap.get(KeyEvent.VK_C));
            }
        });
        operationMap.put("fd", new Input("00") {
            @Override
            public void input() {
                programExecution = true;
                executeProgram(labelMap.get(KeyEvent.VK_D));
            }
        });
        operationMap.put("fe", new Input("00") {
            @Override
            public void input() {
                programExecution = true;
                executeProgram(labelMap.get(KeyEvent.VK_E));
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
        registerMap.put("i", 20);


    }

    @Override
    public void keyPressed(KeyEvent e) {
    }

    @Override
    public void keyTyped(KeyEvent e) {

    }
}

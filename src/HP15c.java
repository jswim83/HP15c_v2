import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Optional;
import java.util.Stack;
import java.util.stream.Collectors;

public class HP15c extends JFrame implements KeyListener {

    private final HP15cDisplay display;
    private String xRegisterString = "0";
    private final LinkedList<Character> commandBuffer;
    private final double[] stack;
    private final Input[] programMemory = new Input[440];
    private final Stack<Integer> programStack = new Stack<>();
    private final HashMap<String, Input> operationMap = new HashMap<>(20);
    private final HashMap<String, Input> labelMap = new HashMap<>(15);
    private boolean programMode;
    private boolean programExecution;
    private int programIndex;
    private volatile boolean labeling;
    private boolean stackLift = true;
    private boolean erred;
    private LastEntry lastEntry = LastEntry.DIGIT;
    private DisplayMode displayMode;
    private int precision;
    private DecimalFormat displayFormatter;

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

    public abstract class Input {
        public String code;

        public Input(String code) {
            this.code = code;
        }

        public abstract void input();
    }

    private static String radixEntry = "#,##0.";
    private static String[] fixPatterns = {
            "#,##0.", "#,##0.0", "#,##0.00", "#,##0.000", "#,##0.0000",
            "#,##0.00000", "#,##0.000000", "#,##0.0000000", "#,##0.00000000", "#,##0.000000000"
    };

    public HP15c() {
        setupOperations();
        this.display = new HP15cDisplay();
        this.commandBuffer = new LinkedList<>();
        this.stack = new double[4];
        this.displayMode = DisplayMode.FIX;
        this.precision = 2;
        this.displayFormatter = new DecimalFormat(fixPatterns[precision]);
        this.setDefaultCloseOperation(EXIT_ON_CLOSE);
        this.addKeyListener(this);
        this.add(display);
        this.pack();
        this.setVisible(true);
        display.drawBuffer(displayFormatter.format(Double.parseDouble(xRegisterString)));
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
        System.out.println("Typed: " + inputChar);
        System.out.println("key code " + inputCode);
        //if labeling
        if (labeling) {
            if (labelMap.containsKey(inputString)) {
                programMemory[++programIndex] = labelMap.get(inputString);
                display.drawProgram(programIndex, labelMap.get(inputString));
                System.out.println(labelMap.get(inputString).code);
                labeling = false;
                return;
            }
            else
                labeling = false;
        }
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
                lastEntry = Character.isDigit(inputChar) ? LastEntry.DIGIT : LastEntry.COMMAND;
                operationMap.get(inputString).input();
                commandBuffer.clear();
            }
            return;
        }
        //if entering a multi-character command
        if (Character.isAlphabetic(inputChar)) {
            commandBuffer.add(inputChar);
            lastEntry = LastEntry.COMMAND;
            display.updateCommandDisplay(commandBuffer);
            var commandString = charLLtoString(commandBuffer);
            if (operationMap.containsKey(commandString)) {
                if(programMode) {
                    switch (commandString){
                        case "pr":
                        case "lbl":
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
            }
            return;
        }
        System.out.println("invalid key");
    }

    private String charLLtoString(LinkedList<Character> characterList) {
        return characterList.stream().map(String::valueOf).collect(Collectors.joining());
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

    private void executeProgram(HP15c.Input label){
        var labelIndex = getLabelIndex(label);
        System.out.println("label at: " + labelIndex);
        if(labelIndex != null){
            programIndex = labelIndex;
            while(programMemory[programIndex] != null && programExecution){
                programMemory[programIndex++].input();
            }
            display.drawBuffer(displayFormatter.format(stack[XREG]));
        }
        else
            System.out.println("no such label exists");
    }

    private Integer getLabelIndex(HP15c.Input label){
        var index = programIndex;
        var numberChecked = 0;
        while (numberChecked < 440){
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
                if (!programMode) {
                    lastEntry = LastEntry.DIGIT;
                    if (xRegisterString.length() > 9)
                        return;
                    if (stackLift) {
                        liftStack();
                        xRegisterString = "0";
                    }
                    else {
                        xRegisterString = xRegisterString + "0";
                    }
                    stack[XREG] = Double.parseDouble(xRegisterString);
                    if (!programExecution)
                        display.drawBuffer(xRegisterString);
                }
                else {
                    programMemory[++programIndex] = this;
                    display.drawProgram(programIndex, this);
                }
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_1), new Input("01") {
            @Override
            public void input() {
                if (!programMode) {
                    lastEntry = LastEntry.DIGIT;
                    if (xRegisterString.length() > 9)
                        return;
                    if (stackLift) {
                        liftStack();
                        xRegisterString = "1";
                    }
                    else {
                        xRegisterString = xRegisterString + "1";
                    }
                    stack[XREG] = Double.parseDouble(xRegisterString);
                    if (!programExecution)
                        display.drawBuffer(xRegisterString);
                }
                else {
                    programMemory[++programIndex] = this;
                    display.drawProgram(programIndex, this);
                }
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_2), new Input("02") {
            @Override
            public void input() {
                if (!programMode) {
                    lastEntry = LastEntry.DIGIT;
                    if (xRegisterString.length() > 9)
                        return;
                    if (stackLift) {
                        liftStack();
                        xRegisterString = "2";
                    }
                    else {
                        xRegisterString = xRegisterString + "2";
                    }
                    stack[XREG] = Double.parseDouble(xRegisterString);
                    if (!programExecution)
                        display.drawBuffer(xRegisterString);
                }
                else {
                    programMemory[++programIndex] = this;
                    display.drawProgram(programIndex, this);
                }
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_3), new Input("03") {
            @Override
            public void input() {
                lastEntry = LastEntry.DIGIT;
                if (xRegisterString.length() > 9)
                    return;
                if (stackLift) {
                    liftStack();
                    xRegisterString = "3";
                }
                else {
                    xRegisterString = xRegisterString + "3";
                }
                stack[XREG] = Double.parseDouble(xRegisterString);
                if (!programExecution)
                    display.drawBuffer(xRegisterString);
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_4), new Input("04") {
            @Override
            public void input() {
                lastEntry = LastEntry.DIGIT;
                if (xRegisterString.length() > 9)
                    return;
                if (stackLift) {
                    liftStack();
                    xRegisterString = "4";
                }
                else {
                    xRegisterString = xRegisterString + "4";
                }
                stack[XREG] = Double.parseDouble(xRegisterString);
                if (!programExecution)
                    display.drawBuffer(xRegisterString);
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_5), new Input("05") {
            @Override
            public void input() {
                lastEntry = LastEntry.DIGIT;
                if (xRegisterString.length() > 9)
                    return;
                if (stackLift) {
                    liftStack();
                    xRegisterString = "5";
                }
                else {
                    xRegisterString = xRegisterString + "5";
                }
                stack[XREG] = Double.parseDouble(xRegisterString);
                if (!programExecution)
                    display.drawBuffer(xRegisterString);
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_6), new Input("06") {
            @Override
            public void input() {
                lastEntry = LastEntry.DIGIT;
                if (xRegisterString.length() > 9)
                    return;
                if (stackLift) {
                    liftStack();
                    xRegisterString = "6";
                }
                else {
                    xRegisterString = xRegisterString + "6";
                }
                stack[XREG] = Double.parseDouble(xRegisterString);
                if (!programExecution)
                    display.drawBuffer(xRegisterString);
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_7), new Input("07") {
            @Override
            public void input() {
                lastEntry = LastEntry.DIGIT;
                if (xRegisterString.length() > 9)
                    return;
                if (stackLift) {
                    liftStack();
                    xRegisterString = "7";
                }
                else {
                    xRegisterString = xRegisterString + "7";
                }
                stack[XREG] = Double.parseDouble(xRegisterString);
                if (!programExecution)
                    display.drawBuffer(xRegisterString);
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_8), new Input("08") {
            @Override
            public void input() {
                lastEntry = LastEntry.DIGIT;
                if (xRegisterString.length() > 9)
                    return;
                if (stackLift) {
                    liftStack();
                    xRegisterString = "8";
                }
                else {
                    xRegisterString = xRegisterString + "8";
                }
                stack[XREG] = Double.parseDouble(xRegisterString);
                if (!programExecution)
                    display.drawBuffer(xRegisterString);
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_9), new Input("09") {
            @Override
            public void input() {
                lastEntry = LastEntry.DIGIT;
                if (xRegisterString.length() > 9)
                    return;
                if (stackLift) {
                    liftStack();
                    xRegisterString = "9";
                }
                else {
                    xRegisterString = xRegisterString + "9";
                }
                stack[XREG] = Double.parseDouble(xRegisterString);
                if (!programExecution)
                    display.drawBuffer(xRegisterString);
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_ENTER), new Input("16") {
            @Override
            public void input() {
                liftStack();
                xRegisterString = "";
                if (!programExecution)
                    display.drawBuffer(displayFormatter.format(stack[XREG]));
            }
        });

        operationMap.put(String.valueOf(KeyEvent.VK_BACK_SPACE), new Input("14") {
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
                        if (commandBuffer.isEmpty())
                            ;
                        else
                            commandBuffer.clear();
                        display.updateCommandDisplay(commandBuffer);
                    }
                }
                else {
                    if(commandBuffer.isEmpty()) {
                        if(programIndex > 0) {
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

        operationMap.put(String.valueOf(KeyEvent.VK_PERIOD), new Input("34") {
            @Override
            public void input() {
                lastEntry = LastEntry.DIGIT;
                if(xRegisterString.contains("."))
                    return;
                if ((xRegisterString.contains(".") ? xRegisterString.length() : xRegisterString.length() - 1) > 9)
                    return;
                if (stackLift) {
                    liftStack();
                    xRegisterString = "0.";
                }
                else {
                    xRegisterString = xRegisterString + ".";
                }
                stack[XREG] = Double.parseDouble(xRegisterString);
                if (!programExecution)
                    display.drawBuffer(xRegisterString);
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
                if(!programExecution) {
                    display.drawBuffer(displayFormatter.format(stack[XREG]));
                    printStack();
                }
            }
        });

        operationMap.put("sub", new Input("40") {
            @Override
            public void input() {
                stack[XREG] = stack[YREG] - stack[XREG];
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if(!programExecution) {
                    display.drawBuffer(displayFormatter.format(stack[XREG]));
                    printStack();
                }
            }
        });

        operationMap.put("mult", new Input("40") {
            @Override
            public void input() {
                stack[XREG] = stack[YREG] * stack[XREG];
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if(!programExecution) {
                    display.drawBuffer(displayFormatter.format(stack[XREG]));
                    printStack();
                }
            }
        });

        operationMap.put("div", new Input("40") {
            @Override
            public void input() {
                stack[XREG] = stack[YREG] / stack[XREG];
                stack[YREG] = stack[ZREG];
                stack[ZREG] = stack[TREG];
                xRegisterString = "";
                stackLift = true;
                if(!programExecution) {
                    display.drawBuffer(displayFormatter.format(stack[XREG]));
                    printStack();
                }
            }
        });

        //program operations
        operationMap.put("pr", new Input("50") {
            @Override
            public void input() {
                programMode = !programMode;
                programIndex = 0;
                if(programMode)
                    display.drawProgram(programIndex, programMemory[programIndex]);
                else
                    display.drawBuffer(displayFormatter.format(stack[XREG]));
                System.out.println("program mode: " + programMode);
            }
        });

        operationMap.put("lbl", new Input("") {
            @Override
            public void input() {
                labeling = true;
                System.out.println("labeling: " + labeling);
            }
        });

        operationMap.put("rtn", new Input("42") {
            @Override
            public void input() {
                if(programStack.isEmpty()){
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

    }
/*
    public void square(){
        if(digitBuffer.size() != 0) {
            if(digitEntry)
                pushStack();
        }
        stack[XREG] = stack[XREG] * stack[XREG];
        stack[YREG] = stack[ZREG];
        stack[ZREG] = stack[TREG];
        stackLift = true;
        printStack();
    }

    public void squareRoot(){
        if(digitBuffer.size() != 0) {
            if(digitEntry)
                pushStack();
        }
        stack[XREG] = Math.sqrt(stack[XREG]);
        stack[YREG] = stack[ZREG];
        stack[ZREG] = stack[TREG];
        stackLift = true;
        printStack();
    }

    public void inverse(){
        if(digitBuffer.size() != 0) {
            if(digitEntry)
                pushStack();
        }
        stack[XREG] = 1 / stack[XREG];
        stack[YREG] = stack[ZREG];
        stack[ZREG] = stack[TREG];
        stackLift = true;
        printStack();
    }

    public void power(){
        if(digitBuffer.size() != 0) {
            if(digitEntry)
                pushStack();
        }
        stack[XREG] = Math.pow(stack[YREG], stack[XREG]);
        stack[YREG] = stack[ZREG];
        stack[ZREG] = stack[TREG];
        stackLift = true;
        printStack();
    }

    public void exponential(){
        if(digitBuffer.size() != 0) {
            if(digitEntry)
                pushStack();
        }
        stack[XREG] = Math.exp(stack[XREG]);
        stack[YREG] = stack[ZREG];
        stack[ZREG] = stack[TREG];
        stackLift = true;
        printStack();
    }

    public void ln(){
        if(digitBuffer.size() != 0) {
            if(digitEntry)
                pushStack();
        }
        stack[XREG] = Math.log(stack[XREG]);
        stack[YREG] = stack[ZREG];
        stack[ZREG] = stack[TREG];
        stackLift = true;
        printStack();
    }

    public void antilog(){
        if(digitBuffer.size() != 0) {
            if(digitEntry)
                pushStack();
        }
        stack[XREG] = Math.pow(10.0, stack[XREG]);
        stack[YREG] = stack[ZREG];
        stack[ZREG] = stack[TREG];
        stackLift = true;
        printStack();
    }

    public void log(){
        if(digitBuffer.size() != 0) {
            if(digitEntry)
                pushStack();
        }
        stack[XREG] = Math.log10(stack[XREG]);
        stack[YREG] = stack[ZREG];
        stack[ZREG] = stack[TREG];
        stackLift = true;
        printStack();
    }
*/

    @Override
    public void keyPressed(KeyEvent e) {
    }

    @Override
    public void keyTyped(KeyEvent e) {

    }
}
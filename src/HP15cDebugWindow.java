import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DecimalFormat;

public class HP15cDebugWindow extends JFrame {

    private transient HP15c target;
    private HP15cState state;
    private JTextArea registersText;
    private DecimalFormat fixFormatter;
    private DecimalFormat scientificFormatter;

    public HP15cDebugWindow(HP15c target) {
        this.target = target;
        this.state = target.getHP15cState();
        this.fixFormatter = new DecimalFormat(HP15c.fixPatterns[9]);
        this.scientificFormatter = new DecimalFormat(HP15c.scientificPatterns[6]);
        this.init();
        this.setSize(800, 600);
        this.setTitle("HP15c Debugger");
        this.setVisible(true);
    }

    protected void init() {
        var mainPanel = new JPanel(new GridLayout(1, 3));
        var leftPanel = new JPanel(new GridLayout(2, 1));

        var stackText = new JTextArea(stackToString());
        stackText.setEditable(false);
        stackText.setFont(new Font("Courier New", Font.PLAIN, 16));
        leftPanel.add(stackText);
        var saveButton = new JButton("Save Registers");
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                state.registers = parseRegisters();
                target.loadState(state);
            }
        });
        leftPanel.add(saveButton);

        var programMemoryText = new JTextArea(programMemoryToString());
        programMemoryText.setEditable(false);
        programMemoryText.setFont(new Font("Courier New", Font.PLAIN, 16));
        var programMemoryScroller = new JScrollPane(programMemoryText);
        programMemoryScroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        programMemoryScroller.setWheelScrollingEnabled(true);

        registersText = new JTextArea(registersToString());
        registersText.setEditable(true);
        registersText.setFont(new Font("Courier New", Font.PLAIN, 16));
        var registersScroller = new JScrollPane(registersText);
        registersScroller.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        registersScroller.setWheelScrollingEnabled(true);

        mainPanel.add(leftPanel);
        mainPanel.add(programMemoryScroller);
        mainPanel.add(registersScroller);
        this.pack();
        this.add(mainPanel);
    }

    private String stackToString() {
        var stackBuilder = new StringBuilder();
        stackBuilder.append("T: ");
        stackBuilder.append(formatDisplay(state.stack[HP15c.TREG]) + "\n");
        stackBuilder.append("Z: ");
        stackBuilder.append(formatDisplay(state.stack[HP15c.ZREG]) + "\n");
        stackBuilder.append("Y: ");
        stackBuilder.append(formatDisplay(state.stack[HP15c.YREG]) + "\n");
        stackBuilder.append("X: ");
        stackBuilder.append(formatDisplay(state.stack[HP15c.XREG]) + "\n");
        return stackBuilder.toString();
    }

    private String programMemoryToString() {
        var memoryBuilder = new StringBuilder();
        memoryBuilder.append("Program Memory" + "\n");
        for (int i = 0; i < state.programMemory.length; i++) {
            if (state.programMemory[i] != null) {
                memoryBuilder.append(HP15cDisplay.getProgramStepFormatter().format(i) + ": ");
                memoryBuilder.append(state.programMemory[i].name);
                if (i != state.programMemory.length - 1)
                    memoryBuilder.append("\n");
            }
        }
        return memoryBuilder.toString();
    }

    public String registersToString() {
        var registerBuilder = new StringBuilder();
        registerBuilder.append("Registers" + "\n");
        registerBuilder.append("I: ");
        registerBuilder.append(formatDisplay(state.registers[66]) + "\n");
        for (int i = 0; i < state.registers.length - 1; i++) {
            registerBuilder.append(i + ": ");
            registerBuilder.append(formatDisplay(state.registers[i]) + "\n");
        }
        return registerBuilder.toString();
    }

    private double[] parseRegisters(){
        var rawText = registersText.getText();
        var newRegisters = new double[67];
        var registerIndex = 66;
        var startIndex = 10;
        var endIndex = 10;
        try {
            while (endIndex < rawText.length()) {
                while (rawText.charAt(endIndex) != '\n')
                    endIndex++;
                while (rawText.charAt(startIndex) != ':')
                    startIndex++;
                startIndex++;
                newRegisters[registerIndex++ % 67] = Double.parseDouble(rawText.substring(startIndex, endIndex));
                startIndex = ++endIndex;
            }
        }
        catch (Exception exception){
            System.out.println("you broken the register format, no changes saved");
        }
        return newRegisters;
    }

    private String formatDisplay(double number) {
        if (((number > -1e-1 && number < 1e-1) || (number <= -1e10 || number >= 1e10)) && number != 0.0) {
            return scientificFormatter.format(number);
        }
        else {
            return fixFormatter.format(number);
        }
    }
}

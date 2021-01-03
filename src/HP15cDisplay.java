import javax.swing.*;
import java.awt.*;
import java.awt.font.TextAttribute;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.Map;

public class HP15cDisplay extends JPanel {

    private SevenSegmentDisplay[] displays;
    private JLabel[] commandPropts;
    private int currentLabel = 1;

    private static DecimalFormat programStepFormatter = new DecimalFormat("000");
    private static Color highlightColor = new Color(136, 206, 255);
    private static String[][] commandList = {
            /*A*/       {"alog"},
            /*B*/       {},
            /*C*/       {"chs", "clx", "cos"},
            /*D*/       {"div"},
            /*E*/       {"exp"},
            /*F*/       {},
            /*G*/       {},
            /*H*/       {"hcos", "hsin", "htan"},
            /*I*/       {"inv"},
            /*J*/       {},
            /*K*/       {},
            /*L*/       {"lbl", "ln", "log"},
            /*M*/       {"mult"},
            /*N*/       {},
            /*O*/       {},
            /*P*/       {"pch", "per", "plus", "pow", "pr"},
            /*Q*/       {},
            /*R*/       {"rcl", "rd", "ru"},
            /*S*/       {"sin", "sto", "sub", "sqr", "sqt"},
            /*T*/       {"tan"},
            /*U*/       {},
            /*V*/       {},
            /*W*/       {},
            /*X*/       {},
            /*Y*/       {},
            /*Z*/       {},
    };

    public HP15cDisplay() {
        super(new BorderLayout());
        this.displays = new SevenSegmentDisplay[11];
        this.commandPropts = new JLabel[10];
        var segmentHeight = 72;
        var segmentWidth = 12;
        var padding = 0;
        var segmentPanel = new JPanel(new GridLayout());
        for (int i = 0; i < displays.length; i++) {
            displays[i] = new SevenSegmentDisplay(segmentHeight, segmentWidth, padding, Color.LIGHT_GRAY, Color.DARK_GRAY);
            segmentPanel.add(displays[i]);
        }
        var commandPanelOuter = new JPanel();
        var commandPanelInner = new JPanel(new GridLayout());
        var textDim = new Dimension(
                11 * (int) displays[0].getPreferredSize().getWidth() / 10,
                (int) displays[0].getPreferredSize().getHeight() / 4);
        var commandFont = new Font(Font.DIALOG_INPUT, 1, (int) displays[0].getPreferredSize().getHeight() / 10);
        var commandInputFont = new Font(Font.DIALOG_INPUT, 1, (int) displays[0].getPreferredSize().getHeight() / 10);
        Map<TextAttribute, Object> attributes = (Map<TextAttribute, Object>) commandInputFont.getAttributes();
        attributes.put(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
        for (int i = 0; i < commandPropts.length; i++) {
            commandPropts[i] = new JLabel();
            commandPropts[i].setPreferredSize(textDim);
            commandPropts[i].setFont(commandFont);
            commandPropts[i].setOpaque(true);
            commandPanelInner.add(commandPropts[i]);
        }
        //commandPropts[0].setBackground(new Color(192, 214, 228));
        commandPropts[0].setFont(commandInputFont.deriveFont(attributes));
        commandPanelOuter.add(new JPanel());
        commandPanelOuter.add(commandPanelInner);
        this.add(segmentPanel, BorderLayout.CENTER);
        this.add(commandPanelOuter, BorderLayout.SOUTH);
        revalidate();
        repaint();
    }

    public void drawBuffer(String buffer) {
        clear();
        if (buffer.isEmpty())
            return;
        var bufferIndex = 0;
        var displayIndex = 1;
        if (buffer.charAt(bufferIndex) == '-') {
            displays[0].setDigit(Digit.MINUS);
            bufferIndex++;
        }
        while (bufferIndex < buffer.length() && displayIndex < 11) {
            var currentChar = buffer.charAt(bufferIndex);
            switch (currentChar) {
                case '.':
                    displays[displayIndex - 1].setRadixMark(RadixMark.State.RADIX);
                    break;
                case ',':
                    displays[displayIndex - 1].setRadixMark(RadixMark.State.DIGIT_GROUP);
                    break;
                case 'E':
                    displayIndex = buffer.charAt(bufferIndex + 1) == '-' ? 8 : 9;
                    break;
                case '-':
                    displays[displayIndex++].setDigit(Digit.MINUS);
                    break;
                default:
                    displays[displayIndex++].setDigit(new Digit(currentChar));
                    break;
            }
            bufferIndex++;
        }
    }

    public void drawProgram(int programIndex, HP15c.Input input) {
        clear();
        var programIndexString = programStepFormatter.format((double) programIndex);
        for (int i = 0; i < 3; i++)
            displays[i].setDigit(new Digit(programIndexString.charAt(i)));
        var displayIndex = 10;
        if (input != null) {
            for (int j = input.code.length() - 1; j > -1; j--)
                displays[displayIndex--].setDigit(new Digit(input.code.charAt(j)));
        }
    }

    public void clear() {
        for (int i = 0; i < 11; i++) {
            displays[i].clear();
        }
    }

    public String updateCommandDisplay(LinkedList<Character> command) {
        for (int k = 1; k < commandPropts.length; k++) {
            commandPropts[k].setText(null);
            commandPropts[k].setBackground(null);
        }
        if (command.size() < 1) {
            commandPropts[0].setText(null);
            return null;
        }
        var i = 0;
        var commandChars = new char[command.size()];
        for (char c : command)
            commandChars[i++] = c;
        var commandString = new String(commandChars);
        commandPropts[0].setText(commandString);
        int letter = Character.getNumericValue(command.peek()) - 10;
        String[] matchingCommands = commandList[letter];
        i = 1;
        for (int j = 0; j < commandPropts.length && j < matchingCommands.length; j++) {
            commandPropts[1].setBackground(highlightColor);
            if (matchingCommands[j].equals(commandString))
                return commandString;
            if (matchingCommands[j].contains(commandString))
                commandPropts[i++].setText(matchingCommands[j]);
        }
        return null;
    }

    public String getCommand() {
        var command = commandPropts[currentLabel].getText();
        commandPropts[currentLabel].setBackground(null);
        currentLabel = 1;
        return command;
    }

    public void rightCommand() {
        if (commandPropts[currentLabel + 1].getText() == null)
            return;
        commandPropts[currentLabel].setBackground(null);
        currentLabel++;
        commandPropts[currentLabel].setBackground(highlightColor);
        repaint();
    }

    public void leftCommand() {
        if (currentLabel == 1)
            return;
        commandPropts[currentLabel].setBackground(null);
        currentLabel--;
        commandPropts[currentLabel].setBackground(highlightColor);
        repaint();
    }
}
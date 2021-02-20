import javax.swing.*;
import java.awt.*;
import java.awt.font.TextAttribute;
import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.Map;

public class HP15cDisplay extends JPanel {

    private final SevenSegmentDisplay[] displays;
    private final JLabel[] commandPropts;
    private int currentLabel = 1;

    private static final DecimalFormat programStepFormatter = new DecimalFormat("000");
    private static final Color highlightColor = new Color(193, 207, 212);
    private static final String[][] commandList = {
            /*A*/       {"alog"},
            /*B*/       {},
            /*C*/       {"chs", "clx", "cos"},
            /*D*/       {"debug", "deg", "div"},
            /*E*/       {"eng", "exp"},
            /*F*/       {"fix"},
            /*G*/       {"grad"},
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
            /*R*/       {"rad", "rcl", "rd", "rtn", "ru"},
            /*S*/       {"sci", "settings", "sin", "solve", "sto", "sub", "sqr", "sqt"},
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
        var padding = 24;
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
        commandPropts[0].setFont(commandInputFont.deriveFont(attributes));
        commandPropts[0].setBackground(highlightColor);
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
        else
            displays[0].setDigit(Digit.BLANK);
        while (displayIndex < displays.length) {
            if (bufferIndex < buffer.length()) {
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
                    case 'e':
                        displays[displayIndex++].setDigit(Digit.E);
                        break;
                    case 'r':
                        displays[displayIndex++].setDigit(Digit.R);
                        break;
                    case 'o':
                        displays[displayIndex++].setDigit(Digit.O);
                        break;
                    default:
                        displays[displayIndex++].setDigit(new Digit(currentChar));
                        break;
                }
                bufferIndex++;
            }
            else
                displays[displayIndex++].setDigit(Digit.BLANK);
        }
    }

    public void drawProgram(int programIndex, HP15c.ProgramStep programStep) {
        this.clear();
        var programIndexString = programStepFormatter.format((double) programIndex);
        for (int i = 0; i < 3; i++)
            displays[i].setDigit(new Digit(programIndexString.charAt(i)));
        displays[3].setDigit(Digit.MINUS);
        var displayIndex = 10;
        if (programStep != null) {
            char currentChar;
            var digitGroup = false;
            for (int j = programStep.input.length() - 1; j > -1; j--) {
                currentChar = programStep.input.charAt(j);

                switch (currentChar){
                    case ',':
                        digitGroup = true;
                        break;
                    case ' ':
                        displays[displayIndex--].setDigit(Digit.BLANK);
                        break;
                    default:
                        Digit currentDigit;
                        if(digitGroup){
                            currentDigit = new Digit(currentChar, RadixMark.State.DIGIT_GROUP);
                            digitGroup = false;
                        }
                        else
                            currentDigit = new Digit(currentChar);
                        displays[displayIndex--].setDigit(currentDigit);
                        break;
                }
            }
        }
    }

    public void updateCommandDisplay(String commandString) {
        clearCommandPrompts();
        if (commandString.equals(""))
            return;
        commandPropts[0].setText(commandString);
        int letter = Character.getNumericValue(commandString.charAt(0)) - 10;
        if (letter < 0 || letter > 26)
            return;
        String[] matchingCommands = commandList[letter];
        var i = 1;
        for (int j = 0; j < commandPropts.length && j < matchingCommands.length; j++) {
            if (matchingCommands[j].equals(commandString))
                return;
            if (matchingCommands[j].contains(commandString))
                commandPropts[i++].setText(matchingCommands[j]);
        }
    }

    public void clearCommandPrompts(){
        for (int i = 0; i < commandPropts.length; i++) {
            commandPropts[i].setText(null);
        }
    }

    public void setCommandPropt(String command){
        commandPropts[0].setText(command);
        repaint();
    }

    public void drawAngleAnnunciator(Annunciator.State state) {
        displays[7].setAnnunciatorState(state);
    }

    public void drawProgramAnnunciator(boolean on){
        if(on)
            displays[10].setAnnunciatorState(Annunciator.State.PROGRAM);
        else
            displays[10].setAnnunciatorState(Annunciator.State.OFF);
    }

    public void drawComplexAnnunciator(boolean on){
        if(on)
            displays[9].setAnnunciatorState(Annunciator.State.COMPLEX);
        else
            displays[9].setAnnunciatorState(Annunciator.State.OFF);
    }

    public void clear(){
        for(int i = 0; i < displays.length; i++)
            displays[i].setDigit(Digit.BLANK);
    }

    public String getCommand() {
        var command = commandPropts[currentLabel].getText();
        commandPropts[currentLabel].setBackground(null);
        currentLabel = 1;
        return command;
    }

    public static DecimalFormat getProgramStepFormatter() {
        return programStepFormatter;
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
public class Digit {

    private static boolean[][] digits = {
            {true, true, true, false, true, true, true},        //0
            {false, false, true, false, false, true, false},    //1
            {true, false, true, true, true, false, true},       //2
            {true, false, true, true, false, true, true},       //3
            {false, true, true, true, false, true, false},      //4
            {true, true, false, true, false, true, true},       //5
            {true, true, false, true, true, true, true},        //6
            {true, false, true, false, false, true, false},     //7
            {true, true, true, true, true, true, true},         //8
            {true, true, true, true, false, true, false},       //9
            {false, false, false, true, false, false, false},   //-
            {true, true, false, true, true, false, true},        //E
            {true, true, false, false, true, false, false}      //R
    };

    public static Digit ZERO = new Digit('0', RadixMark.State.OFF);
    public static Digit MINUS = new Digit('-', 10);
    public static Digit E = new Digit('E', 11);
    public static Digit R = new Digit('R', 12);
    public static Digit O = new Digit('O', 0);

    private char digitChar;
    private boolean[] digit;
    private RadixMark.State radixMark;

    public Digit(char digit, RadixMark.State radixMark){
        this.digitChar = digit;
        this.digit = digits[Character.getNumericValue(digit)];
        this.radixMark = radixMark;
    }

    private Digit(char digit, int index){
        this.digitChar = digit;
        this.digit = digits[index];
        this.radixMark = RadixMark.State.OFF;
    }

    @Override
    public boolean equals(Object d){
        return this.digitChar == ((Digit)d).digitChar;
    }

    public char getDigitChar() {
        return digitChar;
    }

    public boolean[] getDigit() {
        return digit;
    }

    public RadixMark.State getRadixMark() {
        return radixMark;
    }

    public void setRadixMark(RadixMark.State radixMark){
        this.radixMark = radixMark;
    }
}

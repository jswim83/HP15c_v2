import java.util.LinkedList;

public class DigitList extends LinkedList<Digit> {

    private boolean decimal;
    private int decimalIndex;

    public DigitList(boolean decimal){
        super();
        this.decimal = decimal;
    }

    @Override
    public boolean add(Digit digit){
        if(!decimal)
            decimalIndex++;
        return super.add(digit);
    }

    @Override
    public void addLast(Digit digit) {
        if(!decimal)
            decimalIndex++;
        super.addLast(digit);
    }

    @Override
    public Digit removeLast() {
        if(!decimal)
            decimalIndex--;
        return super.removeLast();
    }

    @Override
    public void clear() {
        this.decimal = false;
        this.decimalIndex = 0;
        super.clear();
    }

    public boolean isDecimal() {
        return decimal;
    }

    public void setDecimal(boolean decimal) {
        this.decimal = decimal;
    }

    public int getDecimalIndex() {
        return decimalIndex;
    }
}

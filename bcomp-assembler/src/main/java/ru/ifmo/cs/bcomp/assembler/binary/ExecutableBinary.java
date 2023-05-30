package ru.ifmo.cs.bcomp.assembler.binary;

public class ExecutableBinary {

    private final byte[] data;

    public ExecutableBinary(byte[] src) {
        this.data = src;
    }

    public String asWordDirective() {
        var sb = new StringBuilder("BIN: WORD ");

        for (int i = 0; i < data.length; i += 2)
            sb.append("0x")
                    .append(toHexWord(asWord(data[i], data[i + 1])))
                    .append(", ");
        sb.delete(sb.length() - 2, sb.length());

        return sb.toString();
    }

    public String asStrings() {
        var sb = new StringBuilder();
        for (int i = 0; i < data.length; i += 2)
            sb.append(toHexWord(asWord(data[i], data[i + 1]))).append("\n");
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    private int asWord(short hi, short lo) {
        return (0xFF & hi) << 8 | (0xFF & lo);
    }

    private String toHexWord(int x) {
        var base = String.format("%04X", x);
        return base.substring(base.length() - 4);
    }
}

package ru.m210projects;

public class ChipsetFeature {

    private int registerIndex;
    private int device;
    private int function;
    private int mask;
    private int value;

    public ChipsetFeature(int reg, int device, int function, int mask, int value) {
        this.registerIndex = reg;
        this.device = device;
        this.function = function;
        this.mask = mask;
        this.value = value;
    }

    public ChipsetFeature(byte[] data) {
        int ptr = 0;

        this.registerIndex = data[ptr++] & 0xFF;
        this.device = ((data[ptr] & 0xF8) >> 3);
        this.function = data[ptr++] & 0x07;
        this.mask = data[ptr++] & 0xFF;
        ptr++;
        this.value = data[ptr++] & 0xFF;
        ptr++;
        ptr++;
    }

    public int write(byte[] data, int ptr, boolean isEnd) {
        data[ptr++] = (byte) registerIndex;
        data[ptr++] = (byte) (((device << 3) & 0xF8) | (function & 0x07));
        data[ptr++] = (byte) mask;
        data[ptr++] = 0; // Always 0 ?
        data[ptr++] = (byte) value; // Value
        data[ptr++] = 0; // Always 0 ?
        data[ptr++] = (byte) (isEnd ? 0x36 : 0x02);

        return ptr;
    }

    public static int getSize() {
        return 7;
    }

    @Override
    public String toString() {
        return "ChipsetFeature { \n" +
                "\tregisterIndex=0x" + Integer.toHexString(registerIndex) + ",\n" +
                "\tdevice=0x" + Integer.toHexString(device) + ",\n" +
                "\tfunction=0x" + Integer.toHexString(function) + ",\n" +
                "\tmask=0x" + Integer.toHexString(mask) + ",\n" +
                "\tvalue=0x" + Integer.toHexString(value) + ",\n" +
                " }";
    }
}

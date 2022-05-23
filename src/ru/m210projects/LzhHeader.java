package ru.m210projects;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class LzhHeader {

    public final int headerSize;
    public final int headerSum;

    public final String method;
    public final int compressedSize;
    public final int originalSize;
    public final int time;
    public final int fileType;
    public final int attribute;
    public final int level;
    public final int filenameLen;
    public final String filename;

    public LzhHeader(byte[] data, int offset) {
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        bb.position(offset);

        this.headerSize = bb.get() & 0xFF;
        this.headerSum = bb.get() & 0xFF;
        byte[] id = new byte[5];
        bb.get(id);
        this.method = new String(id);

        this.compressedSize = bb.getInt();
        this.originalSize = bb.getInt();
        this.time = bb.getShort() & 0xFFFF;
        this.fileType = bb.getShort() & 0xFFFF;
        this.attribute = bb.get() & 0xFF;
        this.level = bb.get() & 0xFF;
        this.filenameLen = bb.get() & 0xFF;
        byte[] name = new byte[filenameLen];
        bb.get(name);
        this.filename = new String(name);
    }

}

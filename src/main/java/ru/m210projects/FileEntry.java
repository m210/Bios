package ru.m210projects;

public class FileEntry {
    String name;                // name of this file (incl NULL terminator)
    int nameLen;                // length of filename (minus NULL terminator)

    int size;                    // size of this file
    int compSize;                // the compressed size this file had
    int type;                    // type ID/magic of this file
    int crc;                    // the crc this file had
    boolean crcOK;                // TRUE if the file passed CRC on decompression, FALSE if not

    byte[] data;                // the decompressed data of this file
    int offset;                // special/fixed offset of this file in the image, or 0 if none.
    int flags;                    // special flags for this file (used to identify boot & decompression blocks)
}

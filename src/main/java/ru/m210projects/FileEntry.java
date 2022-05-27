package ru.m210projects;

public class FileEntry {
    String name;                 // name of this file (incl NULL terminator)

    boolean modified = true;
    int size;                    // size of this file
    int compSize;                // the compressed size this file had
    int type;                    // type ID/magic of this file
    int crc;                     // the crc this file had

    byte[] data;                 // the decompressed data of this file
    byte[] compressedData;
    int offset;                  // special/fixed offset of this file in the image, or 0 if none.
    int flags;                   // special flags for this file (used to identify boot & decompression blocks)
}

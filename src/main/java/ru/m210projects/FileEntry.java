package ru.m210projects;

import java.util.Arrays;
import java.util.Objects;

public class FileEntry {
    String name;                 // name of this file (incl NULL terminator)

    boolean modified = false;
    int size;                    // size of this file
    int compSize;                // the compressed size this file had
    int type;                    // type ID/magic of this file
    int crc;                     // the crc this file had

    byte[] data;                 // the decompressed data of this file
    byte[] compressedData;
    int offset;                  // special/fixed offset of this file in the image, or 0 if none.
    int flags;                   // special flags for this file (used to identify boot & decompression blocks)

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileEntry fileEntry = (FileEntry) o;
        return modified == fileEntry.modified && size == fileEntry.size && compSize == fileEntry.compSize && type == fileEntry.type && crc == fileEntry.crc && offset == fileEntry.offset && flags == fileEntry.flags && Objects.equals(name, fileEntry.name) && Arrays.equals(data, fileEntry.data) && Arrays.equals(compressedData, fileEntry.compressedData);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name, modified, size, compSize, type, crc, offset, flags);
        result = 31 * result + Arrays.hashCode(data);
        result = 31 * result + Arrays.hashCode(compressedData);
        return result;
    }
}

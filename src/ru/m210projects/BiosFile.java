package ru.m210projects;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import jp.gr.java_conf.dangan.util.lha.LhaChecksum;
import jp.gr.java_conf.dangan.util.lha.LhaHeader;
import jp.gr.java_conf.dangan.util.lha.LhaInputStream;

public class BiosFile {

    public enum BiosLayout {
        LAYOUT_UNKNOWN,
        LAYOUT_1_1_1,
        LAYOUT_2_1_1,
        LAYOUT_2_2_2
    }

    public enum BIOSVersion {
        VerUnknown,
        Ver451PG,
        Ver600PG,
        Ver60
    }

    public static final int TYPEID_DECOMPBLOCK = 0x01;
    public static final int TYPEID_BOOTBLOCK = 0x02;

    boolean modified;                // has this image been modified?
    String fname;                    // full path/name of image loaded
    BiosLayout layout;               // type of layout of the file table
    byte[] imageData;                // the loaded image
    int imageSize;                   // size of the loaded image (in bytes)
    List<FileEntry> fileTable;       // uncompressed data of all files
    int tableOffset;                 // offset of dynamic file table from start of image
    int maxTableSize;                // maximum compressed size allowed in the file table

    public BiosFile(String filename) throws IOException {
        try (RandomAccessFile fp = new RandomAccessFile(new File(filename), "r")) {
            this.imageSize = (int) fp.length();

            fp.seek(this.imageSize - 16);

            int _0xEA = fp.read();
            fp.seek(this.imageSize - 11);

            byte[] _mrb = new byte[5];
            fp.read(_mrb);

            if ((_0xEA != 0xEA) || (!new String(_mrb).equals("*MRB*")) || ((this.imageSize % 1024) != 0) || (this.imageSize > (1024 * 1024))) {
                System.err.println("This image does not appear to be a valid Award BIOS image.");
                //return;
            }

            // looks okay from here...
            this.fname = filename;

            this.imageData = new byte[this.imageSize];

            fp.seek(0);
            fp.read(this.imageData);
            fp.close();

            // scan for the boot and decompression blocks, and extract them
            System.out.println("Scanning for Boot Block...");

            int ptr = 0;
            byte[] bootBlockData = null;
            int bootBlockSize = 0;

            for (int count = this.imageSize; count >= 0; count--, ptr++) {
                if (!memcmp(imageData, ptr, "Award BootBlock Bios", 20)) {
                    bootBlockSize = this.imageSize - ptr;
                    bootBlockData = new byte[bootBlockSize];

                    System.arraycopy(this.imageData, ptr, bootBlockData, 0, bootBlockSize);
                    break;
                }
            }

            if (bootBlockData == null) {
                System.err.println("Unable to locate the Boot Block within the BIOS Image!");
                System.err.println("The editor will still be able to modify this image, but this component will be unaccessable.  Re-flashing with a saved version of this BIOS is NOT RECOMMENDED!");
                return;
            }

            // next, decompression block...
            System.out.println("Scanning for Decompression Block...");

            ptr = 0;
            byte[] decompBlockData = null;
            int decompBlockSize = 0;

            for (int count = this.imageSize; count >= 0; count--, ptr++) {
                if (!memcmp(imageData, ptr, "= Award Decompression Bios =", 28)) {
                    decompBlockSize = this.imageSize - ptr;
                    decompBlockData = new byte[decompBlockSize];

                    System.arraycopy(this.imageData, ptr, decompBlockData, 0, decompBlockSize);
                    break;
                }
            }

            if (decompBlockData == null) {
                System.err.println("Unable to locate the Decompression Block within the BIOS Image!");
                System.err.println("The editor will still be able to modify this image, but this component will be unaccessable.  unaccessable.  Re-flashing with a saved version of this BIOS is NOT RECOMMENDED!");
                return;
            }

            // load the file table
            this.layout = BiosLayout.LAYOUT_UNKNOWN;
            int fileCount = 0;
            this.tableOffset = 0xDEADBEEF;

            System.out.println("Parsing File Table...");

            // first, determine the offset of the file table
            for (ptr = 0; ptr < this.imageSize; ptr++) {
                if (!memcmp(imageData, ptr + 2, "-lh", 3)) {
                    this.tableOffset = ptr;
                    break;
                }
            }

            if (this.tableOffset == 0xDEADBEEF) {
                System.err.println("Unable to locate a file table within the BIOS image!\n" +
                        "It is possible that this version of the editor simply does not support this type.\n\n" +
                        "Please check the homepage listed under Help.About and see if a new version is\n" +
                        "available for download.");
                return;
            }

            // next, determine the total size of the file table and file count, and try to determine the layout
            ptr = this.tableOffset;
            boolean done = false;
            while (!done) {
                LzhHeader lzhhdr = new LzhHeader(this.imageData, ptr);

                if ((lzhhdr.headerSize == 0) || (lzhhdr.headerSize == 0xFF)) {
                    done = true;
                } else {
                    LhaChecksum crc = new LhaChecksum();
                    crc.update(this.imageData, ptr + 2, lzhhdr.headerSize);
                    if (crc.getValue() != lzhhdr.headerSum) {
                        System.err.println("BIOS Image Checksum failed!\n\nThis BIOS Image may be corrupted or damaged.  The editor will still continue to load\n" +
                                "the image, but certain components may not be editable.");
                    }

                    // advance to next file
                    fileCount++;
                    ptr += (2 + lzhhdr.headerSize + lzhhdr.compressedSize);

                    // see how many bytes are needed to get to the next file, and adjust the type if necessary...
                    if (fileCount == 1) {
                        // first file... could be anything...
                        if (!memcmp(imageData, ptr + 4, "-lh", 3)) {
                            this.layout = BiosLayout.LAYOUT_2_2_2;
                            ptr += 2;
                        } else if (!memcmp(imageData, ptr + 3, "-lh", 3)) {
                            this.layout = BiosLayout.LAYOUT_1_1_1;
                            ptr++;
                        }
                    } else {
                        // next file, so we have some constraints to work with.
                        if (!memcmp(imageData, ptr + 4, "-lh", 3)) {
                            if (this.layout == BiosLayout.LAYOUT_2_2_2) {
                                // continue with 2_2_2...
                                ptr += 2;
                            } else {
                                // uh-oh, this is a new one!
                                this.layout = BiosLayout.LAYOUT_UNKNOWN;
                            }
                        } else if (!memcmp(imageData, ptr + 3, "-lh", 3)) {
                            if (this.layout == BiosLayout.LAYOUT_2_2_2) {
                                if (fileCount == 2) {
                                    // ok, we can switch here.
                                    this.layout = BiosLayout.LAYOUT_2_1_1;
                                    ptr++;
                                } else {
                                    // hmm... don't know this one either!
                                    this.layout = BiosLayout.LAYOUT_UNKNOWN;
                                }
                            } else if (this.layout == BiosLayout.LAYOUT_2_1_1) {
                                // no problems...
                                ptr++;
                            } else if (this.layout == BiosLayout.LAYOUT_1_1_1) {
                                // no problems here either...
                                ptr++;
                            }
                        } else {
                            switch (this.layout) {
                                case LAYOUT_2_2_2:
                                    if ((this.imageData[ptr + 2] == (byte) 0xFF) || (this.imageData[ptr + 2] == 0x00)) {
                                        // ok, end of file table.
                                        ptr += 2;
                                    } else {
                                        // not good!
                                        this.layout = BiosLayout.LAYOUT_UNKNOWN;
                                    }
                                    break;
                                case LAYOUT_2_1_1:
                                case LAYOUT_1_1_1:
                                    if ((this.imageData[ptr + 1] == (byte) 0xFF) || (this.imageData[ptr + 1] == 0x00)) {
                                        // ok, end of file table.
                                        ptr++;
                                    } else {
                                        // not good!
                                        this.layout = BiosLayout.LAYOUT_UNKNOWN;
                                    }
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                }
            }

            // check for a valid layout
            if (this.layout == BiosLayout.LAYOUT_UNKNOWN) {
                System.err.println("Unable to determine the layout of the file table within the BIOS Image!\n" +
                        "It is possible that this version of the editor simply does not support this type.\n\n" +
                        "Please check the homepage listed under Help.About and see if a new version is\n" +
                        "available for download.");
                return;
            }

            // allocate our file table space...
            System.out.println("Loading File Table...");

            this.fileTable = new ArrayList<>();

            // decompress and load the file table into memory...

            int curFile = 0;
            ptr = this.tableOffset;
            done = false;
            while (!done) {
                LzhHeader lzhhdr = new LzhHeader(this.imageData, ptr);

                if ((lzhhdr.headerSize == 0) || (lzhhdr.headerSize == 0xFF)) {
                    done = true;
                } else {
                    // fill out fileentry for this file
                    FileEntry fe = new FileEntry();
                    fileTable.add(fe);

                    biosWriteEntry(fe, lzhhdr, ptr);

                    // advance to next file
                    ptr += (2 + lzhhdr.headerSize + lzhhdr.compressedSize);

                    // skip past extra data
                    switch (this.layout) {
                        case LAYOUT_2_2_2:
                            ptr += 2;
                            break;
                        case LAYOUT_2_1_1:
                            if (curFile == 0) {
                                ptr += 2;
                            } else {
                                ptr++;
                            }
                            break;
                        case LAYOUT_1_1_1:
                            ptr++;
                            break;
                        default:
                            break;
                    }

                    curFile++;
                }
            }

            // calculate available table space
            this.maxTableSize = (this.imageSize - this.tableOffset) - (decompBlockSize + bootBlockSize);

            // scan for fixed-offset components
            System.out.println("Scanning for fixed components...");

            // continue until we hit the end of the image
            while (ptr < (this.imageSize - 6)) {
                if (!memcmp(imageData, ptr + 2, "-lh", 3) && (this.imageData[ptr + 6] == '-')) {
                    // found something... maybe...
                    LzhHeader lzhhdr = new LzhHeader(this.imageData, ptr);

                    if ((lzhhdr.headerSize != 0) && (lzhhdr.headerSize != 0xFF)) {
                        // looks somewhat okay -- check the checksum
                        LhaChecksum crc = new LhaChecksum();
                        crc.update(this.imageData, ptr + 2, lzhhdr.headerSize);
                        if (crc.getValue() == lzhhdr.headerSum) {
                            // we found something!  add it to our table
                            FileEntry fe = new FileEntry();
                            fileTable.add(fe);
                            biosWriteEntry(fe, lzhhdr, ptr);

                            // if this offset is less than our maximum table size, then adjust the size appropriately...
                            // (note: the dynamic file table cannot exceed the space occupied by any fixed components)
                            if (this.maxTableSize > fe.offset) {
                                this.maxTableSize = fe.offset;
                            }

                            // advance pointer past this file
                            ptr += (2 + lzhhdr.headerSize + lzhhdr.compressedSize);
                        }
                    }
                }

                ptr++;
            }

            // insert the decompression and boot blocks
            if (decompBlockData != null) {
                FileEntry fe = new FileEntry();
                fileTable.add(fe);

                String name = "decomp_blk.bin";
                fe.nameLen = name.length();
                fe.name = name;

                fe.size = decompBlockSize;
                fe.compSize = 0;
                fe.type = TYPEID_DECOMPBLOCK;
                fe.crc = 0;
                fe.crcOK = true;
                fe.data = new byte[fe.size];
                fe.offset = 0;
                fe.flags = 1; //FEFLAGS_DECOMP_BLOCK;

                System.arraycopy(decompBlockData, 0, fe.data, 0, decompBlockSize);
            }

            if (bootBlockData != null) {
                FileEntry fe = new FileEntry();
                fileTable.add(fe);

                String name = "boot_blk.bin";
                fe.nameLen = name.length();
                fe.name = name;
                fe.size = bootBlockSize;
                fe.compSize = 0;
                fe.type = TYPEID_BOOTBLOCK;
                fe.crc = 0;
                fe.crcOK = true;
                fe.data = new byte[fe.size];
                fe.offset = 0;
                fe.flags = 2; //FEFLAGS_BOOT_BLOCK;

                System.arraycopy(bootBlockData, 0, fe.data, 0, bootBlockSize);
            }
        }
    }

    private boolean memcmp(byte[] data, int ptr, String ref, int length) {
        return !ref.equalsIgnoreCase(new String(data, ptr, length));
    }

    private void biosWriteEntry(FileEntry fe, LzhHeader lzhhdr, int offset) {
        fe.nameLen = lzhhdr.filenameLen;
        fe.name = lzhhdr.filename;
        fe.size = lzhhdr.originalSize;
        fe.compSize = lzhhdr.compressedSize;
        fe.type = lzhhdr.fileType;
        fe.data = new byte[fe.size];
        fe.offset = offset;
        fe.flags = 0;

        // decompress file
        try (LhaInputStream lis = new LhaInputStream(new ByteArrayInputStream(this.imageData, offset, fe.size + lzhhdr.headerSize))) {
            LhaHeader header = lis.getNextEntry();
            if (header == null) {
                throw new IOException("Header is null");
            }
            lis.read(fe.data);
            fe.crc = header.getCRC();
            fe.crcOK = true;
        } catch (IOException e) {
            e.printStackTrace();

            fe.crcOK = false;
            System.err.println("Error extracting component!\n\nThis BIOS Image may be corrupted or damaged.  The editor will still continue to load\n" +
                    "the image, but certain components may not be editable.");
        }
    }

    public FileEntry scanForID(int id) {
        for (FileEntry fe : fileTable) {
            if (fe.type == id) {
                return fe;
            }
        }
        return null;
    }

    public BIOSVersion getVersion() {
        FileEntry fe = scanForID(0x5000);
        if (fe == null) {
            return BIOSVersion.VerUnknown;
        }

        // get the bios's version
        int sptr = 0x1E060;
        int len = (fe.data[sptr++] & 0xFF) - 1;

        while (len-- != 0) {
            if (!memcmp(fe.data, sptr, "v4.51PG", 7)) {
                return BIOSVersion.Ver451PG;
            } else if (!memcmp(fe.data, sptr, "v6.00PG", 7)) {
                return BIOSVersion.Ver600PG;
            } else if (!memcmp(fe.data, sptr, "v6.0", 4)) {
                return BIOSVersion.Ver60;
            } else {
                sptr++;
            }
        }

        return BIOSVersion.VerUnknown;
    }
}

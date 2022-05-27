package ru.m210projects;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jp.gr.java_conf.dangan.io.LittleEndian;
import jp.gr.java_conf.dangan.util.lha.*;

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
    private BIOSVersion version = null;

    boolean modified;                // has this image been modified?
    String fname;                    // full path/name of image loaded
    BiosLayout layout;               // type of layout of the file table
    byte[] imageData;                // the loaded image
    int imageSize;                   // size of the loaded image (in bytes)
    List<FileEntry> fileTable;       // uncompressed data of all files
    int tableOffset;                 // offset of dynamic file table from start of image
    int maxTableSize;                // maximum compressed size allowed in the file table

    public int getSum(int pos, int st) {
        int csum2 = st;
        int count = pos;

        int s = 0;
        while (count-- != 0) {
            int ch = imageData[s++] & 0xFF;
            csum2 += pos + ch;
        }

        return csum2 & 0xFF;
    }

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
                return;
            }

            // looks okay from here...
            this.fname = filename;

            this.imageData = new byte[this.imageSize];

            fp.seek(0);
            fp.read(this.imageData);
            fp.close();

            // scan for the boot and decompression blocks, and extract them
            System.out.println("Scanning for Boot Block...");

            int ptr = getIndexOf(imageData, 0, "Award BootBlock Bios");
            byte[] bootBlockData = null;
            int bootBlockSize = 0;

            if (ptr != -1) {
                bootBlockSize = this.imageSize - ptr;
                bootBlockData = new byte[bootBlockSize];
                System.arraycopy(this.imageData, ptr, bootBlockData, 0, bootBlockSize);
            }

            if (bootBlockData == null) {
                System.err.println("Unable to locate the Boot Block within the BIOS Image!");
                System.err.println("The editor will still be able to modify this image, but this component will be unaccessable.  Re-flashing with a saved version of this BIOS is NOT RECOMMENDED!");
                return;
            }

            // next, decompression block...
            System.out.println("Scanning for Decompression Block...");
            ptr = getIndexOf(imageData, 0, "= Award Decompression Bios =");
            byte[] decompBlockData = null;
            int decompBlockSize = 0;
            if (ptr != -1) {
                decompBlockSize = this.imageSize - ptr - bootBlockSize;
                decompBlockData = new byte[decompBlockSize];
                System.arraycopy(this.imageData, ptr, decompBlockData, 0, decompBlockSize);
            }

            if (decompBlockData == null) {
                System.err.println("Unable to locate the Decompression Block within the BIOS Image!");
                System.err.println("The editor will still be able to modify this image, but this component will be unaccessable.  unaccessable.  Re-flashing with a saved version of this BIOS is NOT RECOMMENDED!");
                return;
            }

//            int pos = ((imageSize - (decompBlockSize + bootBlockSize)) & 0xFFFFF000) + 0xFFE;
//
//            int csum1 = 0x00;
//            int csum2 = 0xD8;
//            int count = pos;
//
//            int s = 0;
//            while (count-- != 0) {
//                int ch = imageData[s++];
//                csum1 += ch;
//                csum2 += pos + ch;
//            }
//
//            System.out.println(Integer.toHexString((csum1) & 0xFF));
//            System.out.println(Integer.toHexString((csum2) & 0xFF));

            // load the file table
            this.layout = BiosLayout.LAYOUT_UNKNOWN;
            int fileCount = 0;
            this.tableOffset = 0xDEADBEEF;

            System.out.println("Parsing File Table...");

            // first, determine the offset of the file table
            ptr = getIndexOf(imageData, 2, "-lh");
            if (ptr != -1) {
                this.tableOffset = ptr;
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
            while (true) {
                BiosLhaHeader header = null;
                try {
                    header = new BiosLhaHeader(Arrays.copyOfRange(imageData, ptr, ptr + 255));
                } catch (Exception e) {
                    if (e.getMessage().equals("unknown header level \"-1\"."))
                        break;

                    e.printStackTrace();
                    break;
                }

                if(header.getPath().equals("awdflash.exe")) {
                    break;
                }

                // advance to next file
                fileCount++;
                int offs = ptr;
                ptr += (2 + header.getSize() + header.getCompressedSize());

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
            while (true) {
                BiosLhaHeader header = null;
                try {
                    header = new BiosLhaHeader(Arrays.copyOfRange(imageData, ptr, ptr + 255));
                } catch (Exception e) {
                    break;
                }


                // fill out fileentry for this file
                FileEntry fe = new FileEntry();
                fileTable.add(fe);

                biosWriteEntry(fe, header, ptr);
                fe.offset = 0;

                // advance to next file
                ptr += (2 + header.getSize() + header.getCompressedSize());

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

            // calculate available table space
            this.maxTableSize = (this.imageSize - this.tableOffset) - (decompBlockSize + bootBlockSize);

            // scan for fixed-offset components
            System.out.println("Scanning for fixed components...");

            // continue until we hit the end of the image
            while (ptr < (this.imageSize - 6)) {

                if (!memcmp(imageData, ptr + 2, "-lh", 3) && (this.imageData[ptr + 6] == '-')) {
                    // found something... maybe...
                    BiosLhaHeader header = null;
                    try {
                        header = new BiosLhaHeader(Arrays.copyOfRange(imageData, ptr, ptr + 255));
                    } catch (Exception e) {
                        continue;
                    }
                    int offs = ptr;

                    // we found something!  add it to our table
                    FileEntry fe = new FileEntry();
                    fileTable.add(fe);
                    biosWriteEntry(fe, header, ptr);

                    // if this offset is less than our maximum table size, then adjust the size appropriately...
                    // (note: the dynamic file table cannot exceed the space occupied by any fixed components)
                    if (this.maxTableSize > fe.offset) {
                        this.maxTableSize = fe.offset;
                    }

                    // advance pointer past this file
                    ptr += (2 + header.getSize() + header.getCompressedSize());

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
                }

                ptr++;
            }

            // insert the decompression and boot blocks
            if (decompBlockData != null) {
                FileEntry fe = new FileEntry();
                fileTable.add(fe);

                String name = "decomp_blk.bin";
                fe.name = name;
                fe.size = decompBlockSize;
                fe.compSize = 0;
                fe.type = TYPEID_DECOMPBLOCK;
                fe.crc = 0;
                fe.data = new byte[fe.size];
                fe.offset = 0;
                fe.flags = 1; //FEFLAGS_DECOMP_BLOCK;

                System.arraycopy(decompBlockData, 0, fe.data, 0, decompBlockSize);
            }

            if (bootBlockData != null) {
                FileEntry fe = new FileEntry();
                fileTable.add(fe);

                String name = "boot_blk.bin";
                fe.name = name;
                fe.size = bootBlockSize;
                fe.compSize = 0;
                fe.type = TYPEID_BOOTBLOCK;
                fe.crc = 0;
                fe.data = new byte[fe.size];
                fe.offset = 0;
                fe.flags = 2; //FEFLAGS_BOOT_BLOCK;

                System.arraycopy(bootBlockData, 0, fe.data, 0, bootBlockSize);
            }

            this.version = getVersion();
        }
    }

    public void saveFile(String fileName) throws IOException {
        System.out.println("Saving Image...");
        ByteBuffer bb = ByteBuffer.allocate(imageSize).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < imageSize; i++) {
            bb.put(i, (byte) 0xFF);
        }

        System.out.println("Writing components...");
        // iterate through all files with no fixed offset and no special flags, compress them, and write them
        int t = 0;
        for (FileEntry fe : fileTable) {
            if ((fe.offset == 0) && (fe.flags == 0)) {

                if(fe.name.equals("AWARDEPA.BIN")) {
                    fe.modified = false;
                }



                biosWriteComponent(fe, bb, t++);
            }
        }

        // write the decompression and boot blocks...
        System.out.println("Writing boot/decomp blocks...");

        FileEntry fe = scanForID(TYPEID_DECOMPBLOCK);
        int decompSize = ((fe == null) ? (0) : (fe.size));

        fe = scanForID(TYPEID_BOOTBLOCK);
        int bootSize = ((fe == null) ? (0) : (fe.size));

        bb.position(imageSize - (decompSize + bootSize));

        // write the blocks
        fe = scanForID(TYPEID_DECOMPBLOCK);
        if (fe != null) {
            bb.put(fe.data, 0, fe.size);
        }

        fe = scanForID(TYPEID_BOOTBLOCK);
        if (fe != null) {
            bb.put(fe.data, 0, fe.size);
        }

        // now write components which have a fixed offset
        System.out.println("Writing fixed components...");
        for (FileEntry f : fileTable) {
            if (f.offset != 0) {
                bb.position(f.offset);
                biosWriteComponent(f, bb, -1);
            }
        }

        // finally, if the BIOS is version 6.00PG, update the internal checksum in the decompression block...
        if (getVersion() == BIOSVersion.Ver600PG) {
            fe = scanForID(TYPEID_DECOMPBLOCK);
            if (fe != null) {
                // re-open the file in read-only mode
                bb.rewind();

                // calculate the position of the checksum bytes
                int pos = ((imageSize - (decompSize + bootSize)) & 0xFFFFF000) + 0xFFE;

                // calculate the checksum
                int csum1 = 0x00;
                int csum2 = 0xD8;
                int count = pos;

                while (count-- != 0) {
                    int ch = bb.get() & 0xFF;
                    csum1 += ch;
                    csum2 += pos + ch;
                }

                // seek to the checksum position
                bb.position(pos);

                // write the checksum bytes
                bb.put((byte) csum1);
                bb.put((byte) csum2);
            }
        }

        bb.rewind();
        try (FileOutputStream out = new FileOutputStream(fileName)) {
            while (bb.hasRemaining()) {
                out.write(bb.get());
            }
        }
    }

    public static boolean memcmp(byte[] data, int ptr, String ref, int length) {
        return !ref.equalsIgnoreCase(new String(data, ptr, length));
    }

    private void biosWriteComponent(FileEntry fe, ByteBuffer fp, int fileIdx) throws IOException {
        int csum = 0x00;
        if(!fe.modified) {
            fp.put(fe.compressedData);

            // calculate checksum over LZH header and compressed data
            int cptr = 2 + (fe.compressedData[0] & 0xFF);
            int usedsize = LittleEndian.readInt(fe.compressedData, 7);
            while (usedsize-- != 0) {
                csum += fe.compressedData[cptr++];
            }
        } else {
            try (ByteArrayOutputStream compressedDataStream = new ByteArrayOutputStream()) {
                try (LhaOutputStream out = new LhaOutputStream(compressedDataStream)) {
                    BiosLhaHeader header = new BiosLhaHeader(fe.name, fe.type);
                    out.putNextEntry(header);
                    out.write(fe.data);
                }

                byte[] compressedData = compressedDataStream.toByteArray();
                fp.put(compressedData);

                // calculate checksum over LZH header and compressed data
                int cptr = 2 + (compressedData[0] & 0xFF);
                int usedsize = LittleEndian.readInt(compressedData, 7);
                while (usedsize-- != 0) {
                    csum += compressedData[cptr++];
                }
            }
        }

        int ebcount = 2;
        // write extra bytes, depending on the layout
        if (fileIdx != -1) {
            switch (layout) {
                case LAYOUT_2_2_2:
                    ebcount = 2;
                    break;
                case LAYOUT_2_1_1:
                    if (fileIdx == 0)
                        ebcount = 2;
                    else
                        ebcount = 1;
                    break;
                case LAYOUT_1_1_1:
                    ebcount = 1;
                    break;
                default:
                    break;
            }
        }

        if (ebcount > 0) {
            fp.put((byte) 0x00);
            if (ebcount > 1) {
                fp.put((byte) csum);
            }
        }
    }

    private void biosWriteEntry(FileEntry fe, BiosLhaHeader lzhhdr, int offset) {
        fe.name = lzhhdr.getPath();
        fe.size = (int) lzhhdr.getOriginalSize();
        fe.compSize = (int) lzhhdr.getCompressedSize();
        fe.type = lzhhdr.getFileType();
        fe.data = new byte[fe.size];
        fe.compressedData = new byte[2 + fe.compSize + lzhhdr.getSize()];
        fe.offset = offset;
        fe.flags = 0;

        System.arraycopy(imageData, offset, fe.compressedData, 0, fe.compressedData.length);

        // decompress file
        try (LhaInputStream lis = new LhaInputStream(new ByteArrayInputStream(this.imageData, offset, 2 + fe.size + lzhhdr.getSize()))) {
            LhaHeader header = lis.getNextEntry();
            if (header == null) {
                throw new IOException("Error extracting component!\n\nThis BIOS Image may be corrupted or damaged.  The editor will still continue to load\n" +
                        "the image, but certain components may not be editable.");
            }

            lis.read(fe.data);

            CRC16 crc16 = new CRC16();
            crc16.update(fe.data);
            if (crc16.getValue() != header.getCRC()) {
                throw new IOException("CRC check failed!\n\nThis BIOS Image may be corrupted or damaged.  The editor will still continue to load\n" +
                        "the image, but certain components may not be editable.\n");
            }

            fe.crc = header.getCRC();
        } catch (IOException e) {
            e.printStackTrace();
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

    public static int getIndexOf(byte[] data, int offset, String text) {
        int ptr = 0;
        for (int count = data.length - 1; count >= 0; count--, ptr++) {
            if (!memcmp(data, ptr + offset, text, text.length())) {
                return ptr;
            }
        }
        return -1;
    }

    public BIOSVersion getVersion() {
        if (version != null) {
            return version;
        }

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

    public List<String> calcChecksum() {
        FileEntry fe = scanForID(TYPEID_DECOMPBLOCK);
        int decompBlockSize = fe.size;
        fe = scanForID(TYPEID_BOOTBLOCK);
        int bootBlockSize = fe.size;

        int pos = ((imageSize - (decompBlockSize + bootBlockSize)) & 0xFFFFF000) + 0xFFE;

        int csum1 = 0x00;
        int csum2 = 0xD8;
        int count = pos;

        int s = 0;
        while (count-- != 0) {
            int ch = imageData[s++];
            csum1 += ch;
            csum2 += pos + ch;
        }

        return List.of("0x" + Integer.toHexString(csum1 & 0xFF), "0x" + Integer.toHexString(csum2 & 0xFF));
    }
}

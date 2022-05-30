package ru.m210projects;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class Main {

	public static void main(String[] arg) throws IOException {
		//biosOpenFile("6via3801.bin");
		biosOpenFile("MY6VIA82P.BIN");
	}
	
	
	public static void biosOpenFile(String filename) throws IOException {
		BiosFile biosData = new BiosFile(filename);

		System.out.println(biosData.getVersion());
		System.out.println(biosData.calcChecksum());


		System.out.println(biosData.changeModule("original.tmp", 0x5000));
		System.out.println(biosData.changeModule("CPUCODE.BIN", 0x4001));
//		FileEntry fe1 = biosData.scanForID(0x5000);
//		fe1.modified = true;

		for(FileEntry fe : biosData.fileTable) {
			System.err.println(fe.name + " 0x" +  Integer.toHexString(fe.type));
		}

		biosData.saveFile("test.bin");
	}

}

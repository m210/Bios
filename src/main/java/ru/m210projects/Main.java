package ru.m210projects;

import java.io.*;

public class Main {

	public static void main(String[] arg) throws IOException {
		//biosOpenFile("6via3801.bin");
		biosOpenFile("test.bin");
	}
	
	
	public static void biosOpenFile(String filename) throws IOException {
		BiosFile biosData = new BiosFile(filename);

		//System.out.println(biosData.getVersion());

		for(FileEntry fe : biosData.fileTable) {
			System.err.println(fe.name + " 0x" +  Integer.toHexString(fe.type));
		}

		System.out.println(biosData.getVersion());
		System.out.println(biosData.calcChecksum());

		/*
		FileEntry original = biosData.scanForID(0x5000);
		byte[] origianlData = original.data;

		int ptr = 0x10E0D;
		
		// Register Table
		for(int i = 0; i < 23; i++) {
			origianlData[ptr++] = 2; // Register index
			origianlData[ptr++] = 9; // Device (5 bit) | Function (3 bit)
			origianlData[ptr++] = (byte) 0xFF; // Mask
			origianlData[ptr++] = 0; // Always 0 ?
			origianlData[ptr++] = (byte) 0x3F; // Value
			origianlData[ptr++] = 0; // Always 0 ?
			System.err.println(Integer.toHexString(origianlData[ptr++])); //2 or 36 (end)
			System.err.println();
		}
		*/

//		biosData.layout = BiosFile.BiosLayout.LAYOUT_1_1_1;
//		FileEntry original = biosData.scanForID(0x5000);
//		original.offset = 0x20000;
//
//		biosData.saveFile("test.bin");
	}

}

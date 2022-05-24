package ru.m210projects;

import jp.gr.java_conf.dangan.util.lha.LhaHeader;
import jp.gr.java_conf.dangan.util.lha.LhaOutputStream;

import java.io.*;

public class Main {
	
	public static void main(String[] arg) throws IOException {
		biosOpenFile("6via82p03.bin");
		//biosOpenFile("test.bin");
		
		/*
		FileInputStream fis = new FileInputStream("test.lha");
		//byte[] data = new byte[64];
		//fis.read(data);
		//new LzhHeader(data, 0);
		
		LhaInputStream in = new LhaInputStream(fis);
		LhaHeader header = in.getNextEntry();
		
		
		
		/*
		int i = 0;
		byte[] data = new byte[(int) header.getOriginalSize()];
		while(i < header.getOriginalSize()) {
			data[i++] = (byte) in.read();
		}
		
		CRC16 crc = new CRC16();
		crc.update(data);
		System.err.println(crc.getValue() + " " + header.getCRC());
		*/
	}
	
	
	public static void biosOpenFile(String filename) throws IOException {
		BiosFile biosData = new BiosFile(filename);

		System.out.println(biosData.getVersion());

		for(FileEntry fe : biosData.fileTable) {
			System.err.println(fe.name + " 0x" +  Integer.toHexString(fe.type));


//			if(fe.name.equals("awardeyt.rom")) {
//				LhaOutputStream out = new LhaOutputStream(new FileOutputStream("test.lha"));
//				LhaHeader header = new BiosLhaHeader(fe.name, fe.type);
//
//				/*
//				header.setCRC(fe.crc);
//				header.setCompressedSize(fe.compSize);
//				header.setOriginalSize(fe.size);
//
//				byte[] headerData = header.getBytes();
//				new LzhHeader(headerData, 0);
//				*/
//
//
//				out.putNextEntry(header);
//				out.write(fe.data);
//				out.close();
//			}


			/*
			FileOutputStream out = new FileOutputStream(fe.name);
			out.write(fe.data);
			out.close();
			
			/*
			String name = h.fileName.replaceFirst("[.][^.]+$", ".lha");
			Path file = Files.createFile(Paths.get(name));
			Files.write(file, h.getData(), StandardOpenOption.TRUNCATE_EXISTING);
			*/
		}
		
		biosData.saveFile("test.bin");
	}

}

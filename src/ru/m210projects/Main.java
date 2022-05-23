package ru.m210projects;

import java.io.*;

public class Main {
	
	public static void main(String[] arg) throws IOException {
		biosOpenFile("6via82p03.bin");
	}
	
	public static void biosOpenFile(String filename) throws IOException {
		BiosFile biosData = new BiosFile(filename);

		System.out.println(biosData.getVersion());

		for(FileEntry fe : biosData.fileTable) {
			System.err.println(fe.name);

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
	}

}

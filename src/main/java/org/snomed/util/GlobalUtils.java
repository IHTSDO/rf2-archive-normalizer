package org.snomed.util;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.snomed.ApplicationException;

public class GlobalUtils {
	
	public static final String LINE_DELIMITER = "\r\n";
	public static final String QUOTE = "\"";
	public static final String BETA_PREFIX = "x";
	
	public static void print(String msg) {
		print(msg, true);
	}
	
	public static void print(String msg, boolean newLine) {
		if (newLine) {
			System.out.println(msg);
		} else {
			System.out.print(msg);
		}
	}
	
	public static PrintWriter prepareFileToWrite(File outputFile) throws ApplicationException {
		try {
			OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(outputFile, true), StandardCharsets.UTF_8);
			BufferedWriter bw = new BufferedWriter(osw);
			return new PrintWriter(bw);
		} catch (FileNotFoundException e) {
			throw new ApplicationException ("Unable to prepare " + outputFile + " for writing.", e);
		}
	}
	
	public static synchronized void writeToFile(PrintWriter out, Collection<? extends Object> lines) {
		for (Object line : lines) {
			out.print(line.toString() + LINE_DELIMITER);
		}
	}

	public static File ensureFileExists(String fileName) throws IOException {
		File file = new File(fileName);
		if (!file.exists()) {
			if (file.getParentFile() != null) {
				file.getParentFile().mkdirs();
			} 
			file.createNewFile();
		}
		return file;
	}
	
	/**
	 * @return an array of 3 elements containing:  The path, the filename, the file extension (if it exists) or empty strings
	 */
	public static String[] deconstructFilename(File file) {
		String[] parts = new String[] {"","",""};
		
		if (file== null) {
			return parts;
		}
		parts[0] = file.getAbsolutePath().substring(0, file.getAbsolutePath().lastIndexOf(File.separator));
		if (file.getName().lastIndexOf(".") > 0) {
			parts[1] = file.getName().substring(0, file.getName().lastIndexOf("."));
			parts[2] = file.getName().substring(file.getName().lastIndexOf(".") + 1);
		} else {
			parts[1] = file.getName();
		}
		
		return parts;
	}
	
	/**
	 * Creates a new file, appending a incrementing integer if a file of the same
	 * name already exists
	 */
	public static File createUniqueFile(File file) {
		int increment = 0;
		while (file.exists()) {
			String[] fileNameParts = deconstructFilename(file);
			String proposedName = fileNameParts[0] + File.separator + fileNameParts[1] + "_" + (++increment) + fileNameParts[2] ;
			file = new File(proposedName);
		}
		return file;
	}
	
	public static void unzipFlat(File archive, File targetDir, String[] matchArray) throws ApplicationException {

		if (!targetDir.exists() || !targetDir.isDirectory()) {
			throw new ApplicationException(targetDir + " is not a viable directory in which to extract archive");
		}
		try {
			ZipInputStream zis = new ZipInputStream(new FileInputStream(archive));
			ZipEntry ze = zis.getNextEntry();
			try {
				while (ze != null) {
					if (!ze.isDirectory()) {
						Path p = Paths.get(ze.getName());
						String extractedFilename = p.getFileName().toString();
						for (String matchStr : matchArray) {
							if (matchStr == null || extractedFilename.contains(matchStr)) {
								//If the filename is a beta file with x prefix, remove the prefix
								if (extractedFilename.startsWith(BETA_PREFIX)) {
									extractedFilename = extractedFilename.substring(1);
								}
								File extractedFile = new File(targetDir, extractedFilename);
								print(".", false);
								Files.copy(zis, extractedFile.toPath());
							}
						}
					}
					ze = zis.getNextEntry();
				}
			} finally {
				zis.closeEntry();
				zis.close();
			}
		} catch (IOException e) {
			throw new ApplicationException("Failed to expand archive " + archive.getName(), e);
		}
	}
	
	public static void delete(File f) {
		try {
			if (f != null && f.exists()) {
				if (f.isDirectory()) {
					for (File c : f.listFiles())
						delete(c);
				}
				if (!f.delete()) {
					print("Failed to delete file: " + f);
				}
			}
		} catch (Exception e) {
			print ("Exception while deleting " + f + e.getMessage());
		}
	}
	

	public static void createArchive(File exportLocation) throws ApplicationException {
		try {
			if (exportLocation.listFiles().length == 0) {
				print ("No files found to create archive at: " + exportLocation);
				return;
			}
			// The zip filename will be the name of the first thing in the zip location
			String zipFileName = exportLocation.listFiles()[0].getName() + ".zip";
			int fileNameModifier = 1;
			while (new File(zipFileName).exists()) {
				zipFileName = exportLocation.listFiles()[0].getName() + "_" + fileNameModifier++ + ".zip";
			}
			ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFileName));
			String rootLocation = exportLocation.getAbsolutePath() + File.separator;
			print("Creating archive : " + zipFileName + " from files found in " + rootLocation);
			addDir(rootLocation, exportLocation, out);
			out.close();
		} catch (IOException e) {
			throw new ApplicationException("Failed to create revised archive from " + exportLocation, e);
		}
	}

	public static void addDir(String rootLocation, File dirObj, ZipOutputStream out) throws IOException {
		File[] files = dirObj.listFiles();
		byte[] tmpBuf = new byte[1024];

		for (int i = 0; i < files.length; i++) {
			if (files[i].isDirectory()) {
				addDir(rootLocation, files[i], out);
				continue;
			}
			FileInputStream in = new FileInputStream(files[i].getAbsolutePath());
			String relativePath = files[i].getAbsolutePath().substring(rootLocation.length());
			print(" Adding: " + relativePath);
			out.putNextEntry(new ZipEntry(relativePath));
			int len;
			while ((len = in.read(tmpBuf)) > 0) {
				out.write(tmpBuf, 0, len);
			}
			out.closeEntry();
			in.close();
		}
	}
}

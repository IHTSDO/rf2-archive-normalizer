package org.snomed.util;

import java.io.File;
import java.io.IOException;

import org.apache.commons.validator.routines.checkdigit.VerhoeffCheckDigit;
import org.snomed.ApplicationException;
import org.snomed.rf2.normalizer.SnomedConstants;

public class SnomedUtils implements SnomedConstants{
	
	private static VerhoeffCheckDigit verhoeffCheck = new VerhoeffCheckDigit();

	public static String isValid(String sctId, PartionIdentifier partitionIdentifier) {
		String errorMsg=null;
		int partitionNumber = Integer.valueOf("" + sctId.charAt(sctId.length() -2));
		if ( partitionNumber != partitionIdentifier.ordinal()) {
			errorMsg = sctId + " does not exist in partition " + partitionIdentifier.toString();
		}
		if (!verhoeffCheck.isValid(sctId)) {
			errorMsg = sctId + " does not exhibit a valid check digit";
		}
		return errorMsg;
	}

	public static void isValid(String sctId, PartionIdentifier partitionIdentifier,
			boolean errorIfInvalid) throws ApplicationException {
		String errMsg = isValid(sctId,partitionIdentifier);
		if (errorIfInvalid && errMsg != null) {
			throw new ApplicationException(errMsg);
		}
	}

	public static DefinitionStatus translateDefnStatus(String defnStatusSctId) {
		switch (defnStatusSctId) {
			case SCTID_PRIMITIVE : return DefinitionStatus.PRIMITIVE;
			case SCTID_FULLY_DEFINED: return DefinitionStatus.FULLY_DEFINED;
			default:
		}
		return null;
	}
	
	public static String translateDefnStatus(DefinitionStatus defn) {
		switch (defn) {
			case PRIMITIVE: return SCTID_PRIMITIVE;
			case FULLY_DEFINED: return SCTID_FULLY_DEFINED;
			default:
		}
		return null;
	}
	
	public static String[] deconstructFSN(String fsn) {
		String[] elements = new String[2];
		int cutPoint = fsn.lastIndexOf(SEMANTIC_TAG_START);
		elements[0] = fsn.substring(0, cutPoint).trim();
		elements[1] = fsn.substring(cutPoint);
		return elements;
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
	
	public static boolean isExtensionNamespace(String sctId) {
		char extensionFlag = sctId.charAt(sctId.length() - 3);
		return extensionFlag == '1';
	}

	public static File ensureFileExists(String fileName) throws ApplicationException {
		File file = new File(fileName);
		try {
			if (!file.exists()) {
				if (file.getParentFile() != null) {
					file.getParentFile().mkdirs();
				}
				file.createNewFile();
			}
		} catch (IOException e) {
			throw new ApplicationException("Failed to create file " + fileName,e);
		}
		return file;
	}
}

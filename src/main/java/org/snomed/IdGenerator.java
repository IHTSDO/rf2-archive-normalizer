package org.snomed;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.apache.commons.validator.routines.checkdigit.VerhoeffCheckDigit;
import org.snomed.rf2.normalizer.SnomedConstants;
import org.snomed.util.SnomedUtils;

public class IdGenerator implements SnomedConstants {
	private String generatorName;
	private String fileName;
	private BufferedReader availableSctIds;
	private int dummySequence = 100;
	private boolean useDummySequence = false;
	int idsAssigned = 0;
	private String namespace = "";
	private boolean isExtension = false;
	
	public static IdGenerator initiateIdGenerator(String generatorName, String sctidFilename) throws ApplicationException {
		if (sctidFilename.toLowerCase().equals("dummy")) {
			return new IdGenerator(generatorName);
		}
		
		File sctIdFile = new File (sctidFilename);
		try {
			if (sctIdFile.canRead()) {
				return new IdGenerator(generatorName, sctIdFile);
			}
		} catch (Exception e) {}
		
		throw new ApplicationException("Unable to read sctids from " + sctidFilename);
	}
	private IdGenerator(String generatorName, File sctidFile) throws FileNotFoundException {
		this.generatorName = generatorName;
		fileName = sctidFile.getAbsolutePath();
		availableSctIds = new BufferedReader(new FileReader(sctidFile));
	}
	private IdGenerator(String generatorName) {
		this.generatorName = generatorName;
		useDummySequence = true;
	}
	
	public String getSCTID(PartionIdentifier partitionIdentifier) throws ApplicationException {
		if (useDummySequence) {
			idsAssigned++;
			return getDummySCTID(partitionIdentifier);
		}
		
		String sctId;
		try {
			sctId = availableSctIds.readLine();
		} catch (IOException e) {
			throw new RuntimeException("Unable to recover SCTID from file " + fileName, e);
		}
		
		if (sctId == null || sctId.isEmpty()) {
			//Report switch to use dummy strategy
			useDummySequence = true;
			System.out.println(generatorName + " run out of Ids at " + idsAssigned + " switching to dummy...");
			return getSCTID(partitionIdentifier); 
		}
		//Check the SCTID is valid, and belongs to the correct partition
		SnomedUtils.isValid(sctId, partitionIdentifier, true);  //throw exception if not valid
		idsAssigned++;
		return sctId;
	}
	
	private String getDummySCTID(PartionIdentifier partitionIdentifier) throws ApplicationException  {
		try {
			String sctIdBase = ++dummySequence + namespace + (isExtension?"1":"0") + partitionIdentifier.ordinal();
			String checkDigit = new VerhoeffCheckDigit().calculate(sctIdBase);
			return sctIdBase + checkDigit;
		} catch (CheckDigitException e) {
			throw new ApplicationException ("Failed to generate dummy sctid",e);
		}
	}
	
	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}	
	
	public String finish() {
		try {
			if (!useDummySequence) {
				availableSctIds.close();
			}
		} catch (Exception e){}
		return generatorName + " id generator supplied " + idsAssigned + " sctids.";
	}
	
	public void isExtension(boolean b) {
		isExtension = b;
	}
}

package org.snomed.rf2.normalizer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.snomedboot.ComponentStore;
import org.ihtsdo.otf.snomedboot.ReleaseImportException;
import org.ihtsdo.otf.snomedboot.ReleaseImporter;
import org.ihtsdo.otf.snomedboot.domain.Concept;
import org.ihtsdo.otf.snomedboot.domain.Description;
import org.ihtsdo.otf.snomedboot.domain.Relationship;
import org.ihtsdo.otf.snomedboot.factory.LoadingProfile;
import org.ihtsdo.otf.snomedboot.factory.implementation.standard.ComponentFactoryImpl;
import org.snomed.ApplicationException;
import org.snomed.IdGenerator;
import org.snomed.util.GlobalUtils;
import org.snomed.util.SnomedUtils;

import com.google.common.io.Files;

public class Rf2ArchiveNormalizer implements SnomedConstants {

	File deltaArchive;
	File releaseLocation;
	File revisedDeltaRoot;
	File revisedDeltaLocation;
	File relationshipSctIds;
	String outputDirName = "output";
	Set<Rf2File> filesProcessed = new HashSet<Rf2File>();
	List<String[]> inactivationIndicators = new ArrayList<String[]>();
	List<String[]> langRefsetStorage = new ArrayList<String[]>();
	
	Map<Long, ? extends Concept> conceptMap;
	Map<Long, List<ReportDetail>> report = new HashMap<Long, List<ReportDetail>>();
	Map<String, String> replacedIds = new HashMap<String, String>();
	Map<Long, Object> existingComponents;
	IdGenerator relIdGenerator;
	IdGenerator descIdGenerator;

	String[] targetEffectiveTimes;
	Long maxTargetEffectiveTime;
	String edition = "INT";
	String languageCode = "en";
	
	public static void main(String args[]) throws Exception{
		Rf2ArchiveNormalizer app = new Rf2ArchiveNormalizer();
		Timestamp startTime = new Timestamp(System.currentTimeMillis());
		System.out.println ("Started at " +  startTime);
		try{
			app.init(args);
			app.importPreviousRelease();
			app.generateComponentMap();
			app.processDelta();
			app.outputReport();
			GlobalUtils.createArchive(app.revisedDeltaRoot);
		} finally {
			Timestamp now = new Timestamp(System.currentTimeMillis());
			System.out.println ("\nTime now " + now);
			System.out.println ("Time taken: " + timeDiff (startTime, now));
			app.cleanUp();
		}
	}

	private void generateComponentMap() {
		//Work through all concepts and add their relationships and descriptions to a map so that 
		//we can look up to see a) if they exist and b) what values might have changed
		print ("Generating component map");
		existingComponents = new HashMap<Long, Object>();
		for (Concept c : conceptMap.values()) {
			existingComponents.put(c.getId(), c);
			
			for (Description d : c.getDescriptions()) {
				existingComponents.put(new Long(d.getId()), d);
			}
			
			for (Relationship r : c.getRelationships()) {
				existingComponents.put(new Long(r.getId()), r);
			}
		}
	}

	private void init(String[] args) throws SQLException, ClassNotFoundException, ApplicationException {
		if (args.length < 6) {
			print ("Usage deltaProcessor [-p previousRelease] [-r relationshipSCTIDs file] [-d descriptionSCTIDs file] [-a deltaArchive] [-t effectiveTime]");
			System.exit(-1);
		}
		
		relIdGenerator = IdGenerator.initiateIdGenerator("Relationship","dummy");
		descIdGenerator = IdGenerator.initiateIdGenerator("Description", "dummy");
		
		for (int i=0; i < args.length; i++) {
			if (args[i].equals("-p")) {
				releaseLocation = new File (args[i+1]);
				if (!releaseLocation.isDirectory()) {
					throw new ApplicationException("Could not read from directory " + args[i+1]);
				}
			} else if (args[i].equals("-r")) {
				relIdGenerator = IdGenerator.initiateIdGenerator("Relationship", args[i+1]);
			} else if (args[i].equals("-d")) {
				descIdGenerator = IdGenerator.initiateIdGenerator("Description", args[i+1]);
			} else if (args[i].equals("-t")) {
				maxTargetEffectiveTime = new Long(args[i+1]);
			} else if (args[i].equals("-a")) {
				deltaArchive = new File (args[i+1]);
				if (!deltaArchive.canRead()) {
					throw new ApplicationException("Could not read from delta archive " + args[i+1]);
				}
			}
		}
		
		if (deltaArchive == null) {
			throw new ApplicationException("Did not receive a deltaArchive parameter (-a) on command line");
		}
		
		revisedDeltaRoot = Files.createTempDir();
		revisedDeltaLocation = new File (revisedDeltaRoot, "SnomedCT_" + edition + "_" + maxTargetEffectiveTime);
		
		revisedDeltaRoot = new File (outputDirName);
		int increment = 0;
		while (revisedDeltaRoot.exists()) {
			String proposedOutputDirName = outputDirName + "_" + (++increment) ;
			revisedDeltaRoot = new File(proposedOutputDirName);
		}
		outputDirName = revisedDeltaRoot.getName();
		revisedDeltaLocation = new File (outputDirName + File.separator + "SnomedCT_RF2Release_" + edition +"_" + maxTargetEffectiveTime);
		print ("Outputting data to " + revisedDeltaLocation);
	}
	

	private void processDelta() throws ApplicationException, FileNotFoundException, IOException {
		try {
			ZipInputStream zis = new ZipInputStream(new FileInputStream(deltaArchive));
			ZipEntry ze = zis.getNextEntry();
			try {
				while (ze != null) {
					if (!ze.isDirectory()) {
						Path p = Paths.get(ze.getName());
						String fileName = p.getFileName().toString();
						Rf2File rf2File = SnomedRf2File.getRf2File(fileName, FileType.DELTA);
						filesProcessed.add(rf2File);
						if (rf2File != null) {
							processRf2Delta(zis, rf2File, fileName);
						} else {
							print ("Skipping unrecognised file: " + fileName);
						}
					}
					ze = zis.getNextEntry();
				}
			} finally {
				zis.closeEntry();
				zis.close();
			}
		} catch (IOException e) {
			throw new ApplicationException("Failed to expand archive " + deltaArchive.getName(), e);
		}
		
		//TODO If original input also included inactivation indicators, then we'll have to merge the two files
		outputInactivationIndicators();
		
		//We need to process the lang refset afterwards, once we know what all the SCTIDs have been mapped to
		outputLangRefset();
		
		//Output headers for any files that we haven't processed
		SnomedRf2File.outputHeaders(revisedDeltaLocation, filesProcessed, edition, FileType.DELTA, languageCode, maxTargetEffectiveTime.toString());
	}

	private void processRf2Delta(InputStream is, Rf2File rf2File, String fileName) throws IOException, ApplicationException {
		//Not putting this in a try resource block otherwise it will close the stream on completion and we've got more to read!
		print ("Processing " + fileName);
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		String line;
		boolean isHeader = true;
		String outputFile = SnomedRf2File.getOutputFile(revisedDeltaLocation, rf2File, edition, FileType.DELTA, languageCode, maxTargetEffectiveTime.toString());
		SnomedUtils.ensureFileExists(outputFile);
		try(	OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(outputFile, true), StandardCharsets.UTF_8);
				BufferedWriter bw = new BufferedWriter(osw);
				PrintWriter out = new PrintWriter(bw))  {
			while ((line = br.readLine()) != null) {
				if (!isHeader) {
					String[] lineItems = line.split(FIELD_DELIMITER);
					//Ensure that the effectiveTime is set to null for an import delta
					lineItems[IDX_EFFECTIVETIME] = "";
					switch (rf2File) {
						//TODO We're processing the description file prior to the stated relationship file because of 
						//alphabetic order, but if this changes, we'll need to store the file and process it last to ensure
						//that all the descriptions are available for reporting.
						case CONCEPT : processConcept(lineItems, rf2File, fileName, out);
							break;
						case DESCRIPTION : processDescription(lineItems, out);
							break; 
						case STATED_RELATIONSHIP : processRelationship(lineItems, true, out);
							break;
						case LANGREFSET : langRefsetStorage.add(lineItems);
							break;
						/*case RELATIONSHIP : processRelationship(lineItems, false, rf2File, fileName);
							break;*/
						default:
					}
				} else {
					out.print(line + LINE_DELIMITER); //print the header line
					isHeader = false;
				}
			}
		} catch (IOException e) {
			throw new ApplicationException("Unable to process " + fileName, e);
		}
	}

	private void processConcept(String[] lineItems, Rf2File rf2File, String fileName, PrintWriter out) {
		//Have we seen this concept before?
		Long id = new Long(lineItems[IDX_ID]);
		if (existingComponents.containsKey(id)) {
			//What changes have been made?
			Concept existingConcept = (Concept)existingComponents.get(id);
			if (!existingConcept.getDefinitionStatusId().equals(lineItems[CON_IDX_DEFINITIONSTATUSID])) {
				String defnStatus = SnomedUtils.translateDefnStatus(lineItems[CON_IDX_DEFINITIONSTATUSID]).toString();
				report (id, ComponentType.CONCEPT, id, ChangeType.MODIFIED, "Definition status changed to " + defnStatus);
			} else if (!existingConcept.isActive() == (lineItems[IDX_ACTIVE].equals(ACTIVE_FLAG))) {
				report (id, ComponentType.CONCEPT, id, ChangeType.MODIFIED, "Active state changed to " + lineItems[IDX_ACTIVE]);
			} else {
				report (id, ComponentType.CONCEPT, id, ChangeType.UNKNOWN, "Unknown change.");
			}
		} else {
			//Report a new concept being created
			report (id, ComponentType.CONCEPT, id, ChangeType.NEW, "New concept");
		}
		//Output the line items to the revised file location
		out.print(StringUtils.join(lineItems, FIELD_DELIMITER));
		out.print(LINE_DELIMITER);
	}

	private void processRelationship(String[] lineItems, boolean isStated, PrintWriter out) throws NumberFormatException, ApplicationException {
		Long id = new Long(lineItems[IDX_ID]);
		Long conceptId = new Long (lineItems[REL_IDX_SOURCEID]);
		Long typeId = new Long (lineItems[REL_IDX_TYPEID]);
		Long destId = new Long (lineItems[REL_IDX_DESTINATIONID]);
		ComponentType componentType = isStated? ComponentType.STATED_RELATIONSHIP : ComponentType.RELATIONSHIP;
		String relStr = relationshipToString(typeId, destId);
		
		//Have we seen this relationship before?
		if (existingComponents.containsKey(id)) {
			//What changes have been made?
			Relationship existingRelationship = (Relationship)existingComponents.get(id);
			 if (!existingRelationship.getActive().equals(lineItems[IDX_ACTIVE])) {
				report (conceptId, componentType, id, ChangeType.MODIFIED, "Active state changed to " + lineItems[IDX_ACTIVE] + ": " + relStr);
			} else {
				report (conceptId, componentType, id, ChangeType.UNKNOWN, "Unknown change.");
			}
		} else {
			//Do we need to replace this extension relationship with a core one?
			if (SnomedUtils.isExtensionNamespace(id.toString())) {
				lineItems[IDX_ID] = relIdGenerator.getSCTID(PartionIdentifier.RELATIONSHIP);
				id = new Long (lineItems[IDX_ID]);
			}
			//Report a new relationship being created
			String msg = "New relationship: " + relStr; // + (newIdAssigned?" (reassigned id)":"");
			if (lineItems[IDX_ACTIVE].equals(ACTIVE_FLAG)) {
				report (conceptId, componentType, id, ChangeType.NEW, msg);
			} else {
				report (conceptId, componentType, id, ChangeType.UNKNOWN, "Unexpected inactive state on new relationship");
			}
		}
		//Output the revised line items to the revised file location
		out.print(StringUtils.join(lineItems, FIELD_DELIMITER));
		out.print(LINE_DELIMITER);
	}
	

	private void processDescription(String[] lineItems, PrintWriter out) throws ApplicationException {
		Long id = new Long(lineItems[IDX_ID]);
		Long conceptId = new Long (lineItems[DES_IDX_CONCEPTID]);
		String term = lineItems[DES_IDX_TERM];
		//Have we seen this description before?
		if (existingComponents.containsKey(id)) {
			//What changes have been made?
			Description existingDescription = (Description)existingComponents.get(id);
			 if (!(existingDescription.isActive()?"1":"0").equals(lineItems[IDX_ACTIVE])) {
				report (conceptId, ComponentType.DESCRIPTION, id, ChangeType.MODIFIED, "Active state changed to " + lineItems[IDX_ACTIVE] + ": " + term);
				if (lineItems[IDX_ACTIVE].equals(INACTIVE_FLAG)) {
					addInactivationIndicator(id, SCTID_NONCON_EDITORIAL_POLICY);
				}
			 } else {
				report (conceptId, ComponentType.DESCRIPTION, id, ChangeType.UNKNOWN, "Unknown change.");
			}
		} else {
			//Do we need to replace this extension Description with a core one?
			if (SnomedUtils.isExtensionNamespace(id.toString())) {
				String origId = lineItems[IDX_ID];
				lineItems[IDX_ID] = descIdGenerator.getSCTID(PartionIdentifier.DESCRIPTION);
				id = new Long (lineItems[IDX_ID]);
				replacedIds.put(origId, lineItems[IDX_ID]);
			}
			//Report a new Description being created
			String msg = "New Description: " + term;
			if (lineItems[IDX_ACTIVE].equals(ACTIVE_FLAG)) {
				report (conceptId, ComponentType.DESCRIPTION, id, ChangeType.NEW, msg);
			} else {
				report (conceptId, ComponentType.DESCRIPTION, id, ChangeType.UNKNOWN, "Unexpected inactive state on new Description");
			}
		}
		
		//Check the case significance and change if necessary
		String sctIdCaseSignificance = lineItems[DES_IDX_CASESIGNIFICANCEID];
		if (sctIdCaseSignificance.equals(SCTID_ONLY_INITIAL_CHAR_CASE_INSENSITIVE) && !isCaseSensitive(term)) {
			lineItems[DES_IDX_CASESIGNIFICANCEID] = SCTID_ENTIRE_TERM_CASE_INSENSITIVE;
			report (conceptId, ComponentType.DESCRIPTION, id, ChangeType.MODIFIED, "Case significance cI corrected to ci" );
		} else if (sctIdCaseSignificance.equals(SCTID_ENTIRE_TERM_CASE_INSENSITIVE) && isCaseSensitive(term)) {
			lineItems[DES_IDX_CASESIGNIFICANCEID] = SCTID_ONLY_INITIAL_CHAR_CASE_INSENSITIVE;
			report (conceptId, ComponentType.DESCRIPTION, id, ChangeType.MODIFIED, "Case significance ci corrected to cI" );
		}
			
		//Output the revised line items to the revised file location
		out.print(StringUtils.join(lineItems, FIELD_DELIMITER));
		out.print(LINE_DELIMITER);
	}
	
	private void outputLangRefset() throws FileNotFoundException, IOException {
		String refsetFile = SnomedRf2File.getOutputFile(revisedDeltaLocation, Rf2File.LANGREFSET, edition, FileType.DELTA, languageCode, maxTargetEffectiveTime.toString());
		try(	OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(refsetFile, true), StandardCharsets.UTF_8);
				BufferedWriter bw = new BufferedWriter(osw);
				PrintWriter out = new PrintWriter(bw)) {
			
			for (String[] langRefsetRow : langRefsetStorage) {
				//If the referencedComponentId has been mapped to a new ID, we need to use that mapped value
				if (replacedIds.containsKey(langRefsetRow[LANG_IDX_REFCOMPID])) {
					langRefsetRow[LANG_IDX_REFCOMPID] = replacedIds.get(langRefsetRow[LANG_IDX_REFCOMPID]);
				}
				//Output the revised line items to the revised file location
				out.print(StringUtils.join(langRefsetRow, FIELD_DELIMITER));
				out.print(LINE_DELIMITER);
			}
		}
	}



	// id	effectiveTime	active	moduleId	refsetId	referencedComponentId	valueId
	private void addInactivationIndicator(Long descriptionId, String inactivationReasonSCTID) {
		String[] row = new String[7];
		row[0] = UUID.randomUUID().toString().toLowerCase();
		row[1] = ""; //No effectiveTime for a delta import
		row[2] = "1"; //active
		row[3] = SCTID_CORE_MODULE;
		row[4] = SCTID_DESC_INACTIVATION_REFSET;
		row[5] = descriptionId.toString();
		row[6] = inactivationReasonSCTID;
		inactivationIndicators.add(row);
	}

	private String relationshipToString(Long typeId, Long destId) throws ApplicationException {
		String typeStr = SnomedUtils.deconstructFSN(conceptMap.get(typeId).getFsn())[0];
		if (conceptMap.get(destId) == null) {
			throw new ApplicationException ("No knowledge of concept " + destId + " used in relationship");
		}
		String destStr = SnomedUtils.deconstructFSN(conceptMap.get(destId).getFsn())[0];
		return typeStr + " -> " + destId + "|"  + destStr + "|";
	}

	private void report(Long conceptId, ComponentType componentType, Long componentId, ChangeType changeType, String msg) {
		//Have we seen detail for this concept before
		List<ReportDetail> reportDetails = report.get(conceptId);
		if (reportDetails == null) {
			reportDetails = new ArrayList<ReportDetail>();
			report.put(conceptId, reportDetails);
		}
		reportDetails.add(new ReportDetail(componentType, componentId, changeType, msg));
	}

	private void importPreviousRelease() throws ReleaseImportException {
		String releaseLocationStr = releaseLocation.getAbsolutePath();
		print ("Loading previous release from " + releaseLocationStr);
		// Create release importer
		ReleaseImporter releaseImporter = new ReleaseImporter();

		// Load SNOMED CT components into memory
		ComponentStore componentStore = new ComponentStore();
		LoadingProfile loadingProfile = LoadingProfile.complete;
		releaseImporter.loadSnapshotReleaseFiles(releaseLocationStr, loadingProfile, new ComponentFactoryImpl(componentStore));
		conceptMap = componentStore.getConcepts();
	}

	private void outputInactivationIndicators() throws FileNotFoundException, IOException, ApplicationException {
		String inactivationFile = SnomedRf2File.getOutputFile(revisedDeltaLocation, Rf2File.ATTRIBUTE_VALUE, edition, FileType.DELTA, languageCode, maxTargetEffectiveTime.toString());
		SnomedUtils.ensureFileExists(inactivationFile);
		try(	OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(inactivationFile, true), StandardCharsets.UTF_8);
				BufferedWriter bw = new BufferedWriter(osw);
				PrintWriter out = new PrintWriter(bw)) {
			SnomedRf2File inactivationMetadata = SnomedRf2File.get(Rf2File.ATTRIBUTE_VALUE);
			//Output the file headers
			out.print(inactivationMetadata.getHeader() + LINE_DELIMITER);
			//And the rest of the rows
			for (String[] row : inactivationIndicators) {
				out.print(StringUtils.join(row, FIELD_DELIMITER));
				out.print(LINE_DELIMITER);
			}
		}
		filesProcessed.add(Rf2File.ATTRIBUTE_VALUE);
	}

	private void outputReport() throws IOException {
		
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String inputFilename = SnomedUtils.deconstructFilename(deltaArchive.getAbsoluteFile())[1];
		String reportFilename = "analysis_" + inputFilename + "_" + df.format(new Date()) + ".csv";
		File reportFile = new File(reportFilename);
		reportFile.createNewFile();
		print ("Outputting Report to " + reportFile.getAbsolutePath());
		
		try(	OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(reportFile, true), StandardCharsets.UTF_8);
				BufferedWriter bw = new BufferedWriter(osw);
				PrintWriter out = new PrintWriter(bw))
		{
			StringBuffer line = new StringBuffer();
			out.println("ConceptId, FSN, ComponentType, ComponentId, ChangeType, Detail");
			for (Map.Entry<Long, List<ReportDetail>> mapEntry : report.entrySet()) {
				Long conceptId = mapEntry.getKey();
				for (ReportDetail detail : mapEntry.getValue()) {
					line.setLength(0);
					line.append(conceptId).append(COMMA_QUOTE)
						.append(conceptMap.get(conceptId).getFsn()).append(QUOTE_COMMA_QUOTE)
						.append(detail.getComponentType().toString()).append(QUOTE_COMMA_QUOTE)
						.append(detail.getSctid()).append(QUOTE_COMMA_QUOTE)
						.append(detail.getChangeType().toString()).append(QUOTE_COMMA_QUOTE)
						.append(detail.getDetailStr()).append(QUOTE);
					out.println(line.toString());
				}
			}
		} catch (Exception e) {
			print ("Unable to output report due to " + e.getMessage());
		}
		
	}

	private void print (String msg)  {
		System.out.println(msg);
	}
	
	
	private boolean isCaseSensitive(String term) {
		String afterFirst = term.substring(1);
		boolean allLowerCase = afterFirst.equals(afterFirst.toLowerCase());
		return !allLowerCase;
	}
	
	private static String timeDiff (Timestamp earlier, Timestamp later) {
		int totalSecs = (int)(later.getTime() - earlier.getTime()) / 1000;
		int minutes = totalSecs / 60;
		int seconds = totalSecs % 60;
		return String.format("%02d:%02d", minutes, seconds);
	}

	private void cleanUp() {
		print (relIdGenerator.finish());
		print (descIdGenerator.finish());
		print("Cleaning up...");
		GlobalUtils.delete(revisedDeltaRoot);
	}
	
	Long getMaxTargetEffectiveTime() {
		if (maxTargetEffectiveTime == null) {
			for (String effectiveTimeStr : targetEffectiveTimes) {
				Long effectiveTime = Long.parseLong(effectiveTimeStr);
				if (maxTargetEffectiveTime == null || effectiveTime > maxTargetEffectiveTime) {
					maxTargetEffectiveTime = effectiveTime;
				}
			}
		}
		return maxTargetEffectiveTime;
	}
}

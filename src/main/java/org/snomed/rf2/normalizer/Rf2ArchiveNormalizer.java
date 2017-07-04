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
import java.util.List;
import java.util.Map;
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
	
	Map<Long, ? extends Concept> conceptMap;
	Map<Long, List<ReportDetail>> report = new HashMap<Long, List<ReportDetail>>();
	Map<Long, Object> existingComponents;
	IdGenerator idGenerator;

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
			print ("Usage deltaProcessor [-p previousRelease] [-r relationshipSCTIDs file] [-d deltaArchive] [-t effectiveTime]");
			System.exit(-1);
		}
		
		idGenerator = IdGenerator.initiateIdGenerator("dummy");
		
		for (int i=0; i < args.length; i++) {
			if (args[i].equals("-p")) {
				releaseLocation = new File (args[i+1]);
				if (!releaseLocation.isDirectory()) {
					throw new ApplicationException("Could not read from directory " + args[i+1]);
				}
			} else if (args[i].equals("-r")) {
				idGenerator = IdGenerator.initiateIdGenerator(args[i+1]);
			} else if (args[i].equals("-t")) {
				maxTargetEffectiveTime = new Long(args[i+1]);
			} else if (args[i].equals("-d")) {
				deltaArchive = new File (args[i+1]);
				if (!deltaArchive.canRead()) {
					throw new ApplicationException("Could not read from delta archive " + args[i+1]);
				}
			}
		}
		
		if (deltaArchive == null) {
			throw new ApplicationException("Did not receive a deltaArchive parameter (-d) on command line");
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
	

	private void processDelta() throws ApplicationException {

		try {
			ZipInputStream zis = new ZipInputStream(new FileInputStream(deltaArchive));
			ZipEntry ze = zis.getNextEntry();
			try {
				while (ze != null) {
					if (!ze.isDirectory()) {
						Path p = Paths.get(ze.getName());
						String fileName = p.getFileName().toString();
						Rf2File rf2File = SnomedRf2File.getRf2File(fileName, FileType.DELTA);
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
						case CONCEPT : processConcept(lineItems, rf2File, fileName, out);
							break;
						/*case DESCRIPTION : processDescription(lineItems, rf2File, fileName);
							break; */
						case STATED_RELATIONSHIP : processRelationship(lineItems, true, rf2File, fileName, out);
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
	

	private void processRelationship(String[] lineItems, boolean isStated, Rf2File rf2File, String fileName, PrintWriter out) throws NumberFormatException, ApplicationException {
		//Have we seen this relationship before?
		Long id = new Long(lineItems[IDX_ID]);
		Long conceptId = new Long (lineItems[REL_IDX_SOURCEID]);
		Long typeId = new Long (lineItems[REL_IDX_TYPEID]);
		Long destId = new Long (lineItems[REL_IDX_DESTINATIONID]);
		ComponentType componentType = isStated? ComponentType.STATED_RELATIONSHIP : ComponentType.RELATIONSHIP;
		String relStr = relationshipToString(typeId, destId);
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
				lineItems[IDX_ID] = idGenerator.getSCTID(PartionIdentifier.RELATIONSHIP);
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
		
	}


	private String relationshipToString(Long typeId, Long destId) {
		String typeStr = SnomedUtils.deconstructFSN(conceptMap.get(typeId).getFsn())[0];
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
		LoadingProfile loadingProfile = LoadingProfile.light.withFullRelationshipObjects().withStatedRelationships().withInactiveComponents();
		releaseImporter.loadSnapshotReleaseFiles(releaseLocationStr, loadingProfile, new ComponentFactoryImpl(componentStore));
		conceptMap = componentStore.getConcepts();
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
	
	private static String timeDiff (Timestamp earlier, Timestamp later) {
		int totalSecs = (int)(later.getTime() - earlier.getTime()) / 1000;
		int minutes = totalSecs / 60;
		int seconds = totalSecs % 60;
		return String.format("%02d:%02d", minutes, seconds);
	}

	private void cleanUp() {
		print (idGenerator.finish());
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

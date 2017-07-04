package org.snomed.rf2.normalizer;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class SnomedRf2File implements SnomedConstants {

	static Map<Rf2File, SnomedRf2File> snomedRf2Files = new HashMap<Rf2File,SnomedRf2File>();
	static {
		String termDir = "TYPE/Terminology/";
		String refDir =  "TYPE/Refset/";
		snomedRf2Files.put(Rf2File.CONCEPT, new SnomedRf2File("sct2_Concept_TYPE", 
				termDir + "sct2_Concept_TYPE_EDITION_DATE.txt"));
		snomedRf2Files.put(Rf2File.DESCRIPTION, new SnomedRf2File("sct2_Description_TYPE",  
				termDir + "sct2_Description_TYPE-LNG_EDITION_DATE.txt"));
		/*SnomedRf2Files.put(Rf2File.TEXT_DEFINITION, new SnomedRf2File("textdefinition","sct2_TextDefinition_TYPE", 
		 + "sct2_TextDefinition_Snapshot-LNG_EDITION_DATE.txt"));
		SnomedRf2Files.put(Rf2File.new SnomedRf2File("langrefset","der2_cRefset_LanguageTYPE", 
				refDir + "Language/der2_cRefset_LanguageTYPE-LNG_EDITION_DATE.txt")); */
		snomedRf2Files.put(Rf2File.RELATIONSHIP, new SnomedRf2File("sct2_Relationship_TYPE",
				termDir + "sct2_Relationship_TYPE_EDITION_DATE.txt"));
		snomedRf2Files.put(Rf2File.STATED_RELATIONSHIP, new SnomedRf2File("sct2_StatedRelationship_TYPE", 
				termDir + "sct2_StatedRelationship_TYPE_EDITION_DATE.txt"));
		/*SnomedRf2Files.put(Rf2File.new SnomedRf2File("simplerefset","der2_Refset_SimpleTYPE", 
				refDir + "Content/der2_Refset_SimpleTYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId"));
		SnomedRf2Files.put(Rf2File.new SnomedRf2File("associationrefset","der2_cRefset_AssociationReferenceTYPE",  
				refDir + "Content/der2_cRefset_AssociationReferenceTYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\ttargetComponentId"));
		SnomedRf2Files.put(Rf2File.new SnomedRf2File("attributevaluerefset","der2_cRefset_AttributeValueTYPE", 
				refDir + "Content/der2_cRefset_AttributeValueTYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tvalueId"));
		SnomedRf2Files.put(Rf2File.new SnomedRf2File("extendedmaprefset","der2_iisssccRefset_ExtendedMapTYPE", 
				refDir + "Map/der2_iisssccRefset_ExtendedMapTYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tmapGroup\tmapPriority\tmapRule\tmapAdvice\tmapTarget\tcorrelationId\tmapCategoryId"));
		SnomedRf2Files.put(Rf2File.new SnomedRf2File("refsetDescriptor", "der2_cciRefset_RefsetDescriptorTYPE",
				refDir + "Metadata/der2_cciRefset_RefsetDescriptorTYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tattributeDescription\tattributeType\tattributeOrder"));
		SnomedRf2Files.put(Rf2File.new SnomedRf2File("descriptionType", "der2_ciRefset_DescriptionTypeTYPE",
				refDir + "Metadata/der2_ciRefset_DescriptionTypeTYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tdescriptionFormat\tdescriptionLength"));
		SnomedRf2Files.put(Rf2File.new SnomedRf2File("simplemaprefset","der2_sRefset_SimpleMapTYPE", 
				refDir + "Map/der2_sRefset_SimpleMapTYPE_EDITION_DATE.txt",
				"id\teffectiveTime\tactive\tmoduleId\trefsetId\treferencedComponentId\tmapTarget")); */
	}
	
	private String filenamePart;
	private String filenameTemplate;

	
	public SnomedRf2File (String filenamePart, String filenameTemplate) {
		this.filenamePart = filenamePart;
		this.filenameTemplate = filenameTemplate;
	}

	public String getFilenamePart() {
		return filenamePart;
	}

	public String getFilenameTemplate() {
		return filenameTemplate;
	}

	public String getFilename(String edition, String languageCode, String targetEffectiveTime,
			FileType FileType) {
		return filenameTemplate.replace("EDITION", edition).
				replace("DATE", targetEffectiveTime).
				replace("LNG", languageCode).
				replaceAll(TYPE, getFileType(FileType));
	}
	
	public static String getFileType(FileType FileType) {
		switch (FileType) {
			case DELTA : return DELTA;
			case SNAPSHOT : return SNAPSHOT;
			case FULL : 
			default:return FULL;
		}
	}
	
	public static Rf2File getRf2File(String fileName, FileType fileType) {
		String fileTypeStr = getFileType(fileType);
		for (Map.Entry<Rf2File, SnomedRf2File> mapEntry : snomedRf2Files.entrySet()) {
			String fileNamePart = mapEntry.getValue().getFilenamePart().replace(TYPE, fileTypeStr);
			if (fileName.contains(fileNamePart)) {
				return mapEntry.getKey();
			}
		}
		return null;
	}

	public static String getOutputFile(File fileRoot, Rf2File rf2File, String edition, FileType fileType, String languageCode, String targetEffectiveTime) {
		SnomedRf2File rf2FileObj = snomedRf2Files.get(rf2File);
		String fileName = rf2FileObj.getFilename(edition, languageCode, targetEffectiveTime, fileType);
		return fileRoot + File.separator +  fileName;
	}
}

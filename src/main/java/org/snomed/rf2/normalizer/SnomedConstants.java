package org.snomed.rf2.normalizer;

public interface SnomedConstants {
	
	public static final String DELETION_PREFIX = "d";
	public static final String MODIFIER_PREFIX = "modified_";
	public static final String FIELD_DELIMITER = "\t";
	public String SEMANTIC_TAG_START = "(";
	public static final String TYPE = "TYPE";
	
	public static final String SCTID_NONCON_EDITORIAL_POLICY = "723277005"; // |Nonconformance to editorial policy component (foundation metadata concept)|
	public static final String SCTID_CORE_MODULE = "900000000000207008";
	public static final String SCTID_DESC_INACTIVATION_REFSET = "900000000000490003"; // |Description inactivation indicator attribute value reference set (foundation metadata concept)|
	
	static final String SCTID_ENTIRE_TERM_CASE_SENSITIVE = "900000000000017005";
	static final String SCTID_ENTIRE_TERM_CASE_INSENSITIVE = "900000000000448009";
	static final String SCTID_ONLY_INITIAL_CHAR_CASE_INSENSITIVE = "900000000000020002";
	
	final String COMMA = ",";
	final String QUOTE = "\"";
	final String COMMA_QUOTE = ",\"";
	final String QUOTE_COMMA = "\",";
	final String QUOTE_COMMA_QUOTE = "\",\"";
	
	public enum FileType { DELTA, SNAPSHOT, FULL };
	
	public static final String DELTA = "Delta";
	public static final String SNAPSHOT = "Snapshot";
	public static final String FULL = "Full";
	
	public static final int IDX_ID = 0; 
	public static final int IDX_EFFECTIVETIME = 1; 
	public static final int IDX_ACTIVE = 2; 
	public static final int IDX_MODULEID = 3; 
	
	public static final String ACTIVE = "1";
	
	public enum DefinitionStatus { PRIMITIVE, FULLY_DEFINED };
	public static String SCTID_PRIMITIVE = "900000000000074008";
	public static String SCTID_FULLY_DEFINED = "900000000000073002";
	
	public enum Rf2File { CONCEPT, DESCRIPTION, STATED_RELATIONSHIP, RELATIONSHIP, LANGREFSET, ATTRIBUTE_VALUE }
	
	enum ComponentType { CONCEPT, DESCRIPTION, STATED_RELATIONSHIP, RELATIONSHIP }
	
	enum PartionIdentifier {CONCEPT, DESCRIPTION, RELATIONSHIP};
	
	enum ChangeType { NEW, INACTIVATION, REACTIVATION, MODIFIED, UNKNOWN }
	
	public static final String LINE_DELIMITER = "\r\n";
	public static final String ACTIVE_FLAG = "1";
	public static final String INACTIVE_FLAG = "0";

	// Relationship columns
	public static final int REL_IDX_ID = 0;
	public static final int REL_IDX_EFFECTIVETIME = 1;
	public static final int REL_IDX_ACTIVE = 2;
	public static final int REL_IDX_MODULEID = 3;
	public static final int REL_IDX_SOURCEID = 4;
	public static final int REL_IDX_DESTINATIONID = 5;
	public static final int REL_IDX_RELATIONSHIPGROUP = 6;
	public static final int REL_IDX_TYPEID = 7;
	public static final int REL_IDX_CHARACTERISTICTYPEID = 8;
	public static final int REL_IDX_MODIFIERID = 9;
	public static final int REL_MAX_COLUMN = 9;

	// Concept columns
	// id effectiveTime active moduleId definitionStatusId
	public static final int CON_IDX_ID = 0;
	public static final int CON_IDX_EFFECTIVETIME = 1;
	public static final int CON_IDX_ACTIVE = 2;
	public static final int CON_IDX_MODULID = 3;
	public static final int CON_IDX_DEFINITIONSTATUSID = 4;

	// Description columns
	// id effectiveTime active moduleId conceptId languageCode typeId term caseSignificanceId
	public static final int DES_IDX_ID = 0;
	public static final int DES_IDX_EFFECTIVETIME = 1;
	public static final int DES_IDX_ACTIVE = 2;
	public static final int DES_IDX_MODULID = 3;
	public static final int DES_IDX_CONCEPTID = 4;
	public static final int DES_IDX_LANGUAGECODE = 5;
	public static final int DES_IDX_TYPEID = 6;
	public static final int DES_IDX_TERM = 7;
	public static final int DES_IDX_CASESIGNIFICANCEID = 8;
	
	// Language Refset columns
	// id	effectiveTime	active	moduleId	refsetId	referencedComponentId	acceptabilityId
	public static final int LANG_IDX_ID = 0;
	public static final int LANG_IDX_EFFECTIVETIME = 1;
	public static final int LANG_IDX_ACTIVE = 2;
	public static final int LANG_IDX_MODULID = 3;
	public static final int LANG_IDX_REFSETID = 4;
	public static final int LANG_IDX_REFCOMPID = 5;
	public static final int LANG_IDX_ACCEPTABILITY_ID = 6;
	
	// Inactivation Refset columns
	// id	effectiveTime	active	moduleId	refsetId	referencedComponentId	reasonId
	public static final int INACT_IDX_ID = 0;
	public static final int INACT_IDX_EFFECTIVETIME = 1;
	public static final int INACT_IDX_ACTIVE = 2;
	public static final int INACT_IDX_MODULID = 3;
	public static final int INACT_IDX_REFSETID = 4;
	public static final int INACT_IDX_REFCOMPID = 5;
	public static final int INACT_IDX_REASON_ID = 6;
	
	// Refset columns
	public static final int REF_IDX_ID = 0;
	public static final int REF_IDX_EFFECTIVETIME = 1;
	public static final int REF_IDX_ACTIVE = 2;
	public static final int REF_IDX_MODULEID = 3;
	public static final int REF_IDX_REFSETID = 4;
	public static final int REF_IDX_REFCOMPID = 5;
	public static final int REF_IDX_FIRST_ADDITIONAL = 6;
	
}

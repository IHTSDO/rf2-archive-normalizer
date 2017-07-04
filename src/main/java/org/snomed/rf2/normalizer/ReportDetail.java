package org.snomed.rf2.normalizer;

public class ReportDetail implements SnomedConstants {

	ComponentType componentType;
	Long sctid;
	ChangeType changeType;
	String detailStr;
	
	ReportDetail (ComponentType componentType, Long sctid, ChangeType changeType, String detailStr) {
		this.componentType = componentType;
		this.sctid = sctid;
		this.changeType = changeType;
		this.detailStr = detailStr;
	}

	public ComponentType getComponentType() {
		return componentType;
	}

	public Long getSctid() {
		return sctid;
	}

	public ChangeType getChangeType() {
		return changeType;
	}

	public String getDetailStr() {
		return detailStr;
	}
}

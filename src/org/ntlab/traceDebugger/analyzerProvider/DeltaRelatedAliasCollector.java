package org.ntlab.traceDebugger.analyzerProvider;

import java.util.ArrayList;
import java.util.List;

public class DeltaRelatedAliasCollector implements IAliasCollector {
	private List<Alias> srcSideRelatedAliases = new ArrayList<>();
	private List<Alias> dstSideRelatedAliases = new ArrayList<>();
	private List<String> srcSideIdList = new ArrayList<>();
	private List<String> dstSideIdList = new ArrayList<>();
	
	public DeltaRelatedAliasCollector(String srcId, String dstId) {
		srcSideIdList.add(srcId);
		dstSideIdList.add(dstId);
	}
	
	@Override
	public void addAlias(Alias alias) {
		String objId = alias.getObjectId();
		if (srcSideIdList.contains(objId)) {
			if (alias.getAliasType().equals(Alias.AliasType.ACTUAL_ARGUMENT)) {
				Alias formalParameterAlias = srcSideRelatedAliases.get(srcSideRelatedAliases.size() - 1);
				alias.setIndex(formalParameterAlias.getIndex());
			}
			srcSideRelatedAliases.add(alias);
			System.out.println("Src " + alias.getAliasType() + ": 			" + alias.getMethodSignature() + " line" + alias.getLineNo() + "		index" + alias.getIndex());
		} else if (dstSideIdList.contains(objId)) {
			if (alias.getAliasType().equals(Alias.AliasType.ACTUAL_ARGUMENT)) {
				Alias formalParameterAlias = dstSideRelatedAliases.get(dstSideRelatedAliases.size() - 1);
				alias.setIndex(formalParameterAlias.getIndex());
			}
			dstSideRelatedAliases.add(alias);
			System.out.println("Dst " + alias.getAliasType() + ": 			" + alias.getMethodSignature() + " line" + alias.getLineNo() + "		index" + alias.getIndex());
		}
	}

	@Override
	public void changeTrackingObject(String from, String to) {
		if (srcSideIdList.contains(from)) {
			srcSideIdList.add(to);
		} else if (dstSideIdList.contains(from)) {
			dstSideIdList.add(to);
		}
	}
	
	public List<Alias> getSrcSideRelatedAliases() {
		return srcSideRelatedAliases;
	}

	public List<Alias> getDstSideRelatedAliases() {
		return dstSideRelatedAliases;
	}

	public void addSrcSideRelatedAlias(Alias alias) {
		srcSideRelatedAliases.add(alias);
	}
	
	public void addDstSideRelatedAlias(Alias alias) {
		dstSideRelatedAliases.add(alias);
	}
}

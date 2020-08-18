package org.ntlab.traceDebugger.analyzerProvider;

import java.util.ArrayList;
import java.util.List;

import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Reference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;


public class ExtractedStructure {

	private Delta delta = new Delta();
	private MethodExecution coordinator = null;
	private MethodExecution parent = null;
	private MethodExecution creationCallTree;
//	private List<TracePoint> srcSideRelatedTracePoints = new ArrayList<>();
//	private List<TracePoint> dstSideRelatedTracePoints = new ArrayList<>();
//	private List<Alias> srcSideRelatedAliases = new ArrayList<>();
//	private List<Alias> dstSideRelatedAliases = new ArrayList<>();
	
	public Delta getDelta() {
		return delta;
	}

	public MethodExecution getCoordinator() {
		return coordinator;
	}

	/**
	 * âºé¿ëï é¿ëïå„Ç…çÌèúÇ∑ÇÈÇ±Ç∆
	 * @param coordinator
	 */
	public void setCoordinator(MethodExecution coordinator) {
		this.coordinator = coordinator;
	}

	public void createParent(MethodExecution methodExecution) {
		coordinator = methodExecution;
		parent = null;
	}

//	public void addParent(MethodExecution callTree) {
//		if (parent == null)
//			coordinator.addChild(parent = callTree);
//		else
//			parent.addChild(parent = callTree);
//	}
//
//	public void addChild(MethodExecution callTree) {
//		if (parent == null)
//			coordinator.addChild(callTree);
//		else
//			parent.addChild(callTree);
//	}
//	
	public void addSrcSide(Reference reference) {
		delta.addSrcSide(reference);
	}

	public void addDstSide(Reference reference) {
		delta.addDstSide(reference);
	}

	public void changeParent() {
	}

	public void setCreationMethodExecution(MethodExecution callTree) {
		creationCallTree = callTree;
	}

	public MethodExecution getCreationCallTree() {
		return creationCallTree;
	}

//	public List<TracePoint> getSrcSideRelatedTracePoints() {
//		return srcSideRelatedTracePoints;
//	}
//	
//	public List<TracePoint> getDstSideRelatedTracePoints() {
//		return dstSideRelatedTracePoints;
//	}	
//
//	public void addSrcSideRelatedTracePoint(TracePoint tp) {
//		srcSideRelatedTracePoints.add(tp);
//	}
//	
//	public void addDstSideRelatedTracePoint(TracePoint tp) {
//		dstSideRelatedTracePoints.add(tp);
//	}
	
//	public List<Alias> getSrcSideRelatedAliases() {
//		return srcSideRelatedAliases;
//	}
//	
//	public List<Alias> getDstSideRelatedAliases() {
//		return dstSideRelatedAliases;
//	}	
//
//	public void addSrcSideRelatedAlias(Alias alias) {
//		srcSideRelatedAliases.add(alias);
//	}
//	
//	public void addDstSideRelatedAlias(Alias alias) {
//		dstSideRelatedAliases.add(alias);
//	}
}

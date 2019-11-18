package org.ntlab.traceDebugger.analyzerProvider;

import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Reference;


public class ExtractedStructure {

	private Delta delta = new Delta();
	private MethodExecution coordinator = null;
	private MethodExecution parent = null;
	private MethodExecution creationCallTree;

	public Delta getDelta() {
		return delta;
	}

	public MethodExecution getCoordinator() {
		return coordinator;
	}

	/**
	 * 仮実装 実装後に削除すること
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
	
}

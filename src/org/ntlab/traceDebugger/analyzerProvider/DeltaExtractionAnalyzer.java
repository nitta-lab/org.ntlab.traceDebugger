package org.ntlab.traceDebugger.analyzerProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Reference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Trace;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TraceJSON;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
import org.ntlab.traceDebugger.JavaEditorOperator;
import org.ntlab.traceDebugger.Variable;

public class DeltaExtractionAnalyzer extends AbstractAnalyzer {
	private static DeltaExtractionAnalyzer getInstance = null;
	private DeltaExtractorJSON deltaExtractor;
	private MethodExecution coordinator;
	private ReferencePoint coordinatorPoint;
	private List<ReferencePoint> srcSidePoints = new ArrayList<>();
	private List<ReferencePoint> dstSidePoints = new ArrayList<>();
	private ReferencePoint bottomPoint;
	private Delta delta;
	private static final String DELTA_MARKER_ID = "org.ntlab.traceDebugger.deltaMarker";
	
	public DeltaExtractionAnalyzer(Trace trace) {
		super(trace);
		deltaExtractor = new DeltaExtractorJSON((TraceJSON)trace);
		JavaEditorOperator.deleteMarkers(DELTA_MARKER_ID);
	}
	
	private static DeltaExtractionAnalyzer getInstance() {
		if (getInstance == null) {
			getInstance = new DeltaExtractionAnalyzer(TraceJSON.getInstance());
		}
		return getInstance;
	}
	
	public ReferencePoint getBottomPoint() {
		return bottomPoint;
	}
	
	public ReferencePoint getCoordinatorPoint() {
		return coordinatorPoint;
	}
	
	public List<ReferencePoint> getSrcSidePoints() {
		return srcSidePoints;
	}
	
	public List<ReferencePoint> getDstSidePoints() {
		return dstSidePoints;
	}

	public void extractDelta(Variable variable) {
		String srcId = variable.getContainerId();
		String srcClassName = variable.getContainerClassName();
		String dstId = variable.getId();
		String dstClassName = variable.getClassName();
		TracePoint before = variable.getTracePoint();
		Reference reference = new Reference(srcId, dstId, srcClassName, dstClassName);
		
		reset();
		bottomPoint = createReferencePoint(reference, before, true);
		coordinatorPoint = createCoordinatorReferencePoint(coordinator, bottomPoint.getMethodExecution());
		if (delta != null) {
			for (Reference srcSideReference : delta.getSrcSide()) {
				ReferencePoint rp = createReferencePoint(srcSideReference, before, false);
				if (rp != null) srcSidePoints.add(rp);
			}
			for (Reference dstSideReference : delta.getDstSide()) {
				ReferencePoint rp = createReferencePoint(dstSideReference, before, false);
				if (rp != null) dstSidePoints.add(rp);
			}
			Collections.reverse(srcSidePoints);
			Collections.reverse(dstSidePoints);
		}
		confirm();

		String message;
		int cnt = 1;
		for (ReferencePoint rp : srcSidePoints) {
			message = String.format("%s%03d: %s", "SrcSide", cnt, rp.getReferenceMessage());
			markAndOpenJavaFile(rp, message);
			cnt++;
		}
		cnt = 1;
		for (ReferencePoint rp : dstSidePoints) {
			message = String.format("%s%03d: %s", "DstSide", cnt, rp.getReferenceMessage());
			markAndOpenJavaFile(rp, message);
			cnt++;
		}
		message = String.format("%s: %s", "Coordinator", coordinatorPoint.getReferenceMessage());
		markAndOpenJavaFile(coordinatorPoint, message);
		message = String.format("%s: %s", "Bottom", bottomPoint.getReferenceMessage());
		markAndOpenJavaFile(bottomPoint, message);
	}
	
	private ReferencePoint createReferencePoint(Reference reference, TracePoint before, boolean isBottom) {
		ExtractedStructure extractedStructure = deltaExtractor.extract(reference, before.duplicate());
		if (extractedStructure == null) return null;
		MethodExecution creationCallTree = extractedStructure.getCreationCallTree();
		long beforeTime = before.getStatement().getTimeStamp();
		if (isBottom) {
			coordinator = extractedStructure.getCoordinator();
			delta = extractedStructure.getDelta();
		}
		return new ReferencePoint(reference, creationCallTree, beforeTime);
	}
	
	private ReferencePoint createCoordinatorReferencePoint(MethodExecution coordinator, MethodExecution bottom) {
		MethodExecution me = bottom;
		MethodExecution childMe = null;
		while (me != null) {
			if (coordinator.equals(me)) break;
			childMe = me;
			me = me.getParent();
		}
		Statement statement = null;
		if (me != null && childMe != null) {
			statement = me.getStatements().get(childMe.getCallerStatementExecution());	
		}
		return new ReferencePoint(null, me, statement);
	}
	
	private void reset() {
		coordinator = null;
		srcSidePoints.clear();
		dstSidePoints.clear();
		bottomPoint = null;
		delta = null;
		JavaEditorOperator.deleteMarkers(DELTA_MARKER_ID);
	}
	
	private void markAndOpenJavaFile(ReferencePoint rp, String message) {
		if (rp == null) return;
		MethodExecution methodExecution = rp.getMethodExecution();
		int lineNo = rp.getLineNo();
		JavaEditorOperator.markAndOpenJavaFile(methodExecution, lineNo, message, DELTA_MARKER_ID);		
	}
	
	private void confirm() {
		System.out.println();
		System.out.println();
		System.out.println("Coordinator:");
		System.out.println("	" + coordinator);
		System.out.println();
		System.out.println("SrcSide:");
		for (ReferencePoint point : srcSidePoints) {
			System.out.println("	" + point);
		}
		System.out.println();
		System.out.println("DstSide: ");
		for (ReferencePoint point : dstSidePoints) {
			System.out.println("	" + point);
		}
		System.out.println();
		System.out.println("Bottom:");
		System.out.println("	" + bottomPoint);
		System.out.println();
	}
}
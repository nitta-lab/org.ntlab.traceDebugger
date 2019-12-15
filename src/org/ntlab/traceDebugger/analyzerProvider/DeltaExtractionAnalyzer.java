package org.ntlab.traceDebugger.analyzerProvider;

import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Reference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Trace;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TraceJSON;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
import org.ntlab.traceDebugger.JavaEditorOperator;
import org.ntlab.traceDebugger.Variable;

public class DeltaExtractionAnalyzer extends AbstractAnalyzer {
	private static DeltaExtractionAnalyzer theInstance = null;
	private DeltaExtractorJSON deltaExtractor;
	private ExtractedStructure extractedStructure;
	private TracePoint bottomPoint;
	
	public DeltaExtractionAnalyzer(Trace trace) {
		super(trace);
		deltaExtractor = new DeltaExtractorJSON((TraceJSON)trace);
		DeltaMarkerManager mgr = DeltaMarkerManager.getInstance();
		mgr.deleteMarkers(DeltaMarkerManager.DELTA_MARKER_ID);
		mgr.deleteMarkers(DeltaMarkerManager.DELTA_MARKER_ID_2);
	}
	
	private static DeltaExtractionAnalyzer getInstance() {
		if (theInstance == null) {
			theInstance = new DeltaExtractionAnalyzer(TraceJSON.getInstance());
		}
		return theInstance;
	}
	
	public TracePoint getBottomPoint() {
		return bottomPoint;
	}
	
	public ExtractedStructure geExtractedStructure() {
		return extractedStructure;
	}

	public void extractDelta(Variable variable) {
		String srcId = variable.getContainerId();
		String srcClassName = variable.getContainerClassName();
		String dstId = variable.getId();
		String dstClassName = variable.getClassName();
		TracePoint before = variable.getTracePoint();
		Reference reference = new Reference(srcId, dstId, srcClassName, dstClassName);
		
		// デルタ抽出
		reset();
		DeltaRelatedAliasCollector aliasCollector = new DeltaRelatedAliasCollector(srcId, dstId);
		extractedStructure = deltaExtractor.extract(reference, before.duplicate(), aliasCollector);
		MethodExecution creationCallTree = extractedStructure.getCreationCallTree();
		List<Alias> srcSideRelatedAliases = aliasCollector.getSrcSideRelatedAliases();
		List<Alias> dstSideRelatedAliases = aliasCollector.getDstSideRelatedAliases();
		MethodExecution coordinator = extractedStructure.getCoordinator();
		bottomPoint = findTracePoint(reference, creationCallTree, before.getStatement().getTimeStamp());

		// デルタ抽出の結果を元にソースコードを反転表示する
		mark(bottomPoint, srcSideRelatedAliases, dstSideRelatedAliases, coordinator);
	}
	
	private void mark(TracePoint bottomPoint, List<Alias> srcSideRelatedAliases, List<Alias> dstSideRelatedAliases, MethodExecution coordinator) {
		String message = String.format("Bottom %s", "");
		markAndOpenJavaFile(bottomPoint.getMethodExecution(), bottomPoint.getStatement().getLineNo(), message, DeltaMarkerManager.DELTA_MARKER_ID);
		int cnt = 1;
		for (Alias alias: srcSideRelatedAliases) {
			message = String.format("SrcSide%03d %s (id = %s)", cnt, alias.getAliasType().toString(), alias.getObjectId());
			markAndOpenJavaFile(alias, message, DeltaMarkerManager.DELTA_MARKER_ID);
			cnt++;
		}
		cnt = 1;
		for (Alias alias : dstSideRelatedAliases) {
			message = String.format("DstSide%03d %s (id = %s)", cnt, alias.getAliasType().toString(), alias.getObjectId());
			markAndOpenJavaFile(alias, message, DeltaMarkerManager.DELTA_MARKER_ID_2);
			cnt++;
		}
		markAndOpenJavaFile(coordinator, -1 , "Coordinator", DeltaMarkerManager.DELTA_MARKER_ID);		
	}
	
	private TracePoint findTracePoint(Reference reference, MethodExecution methodExecution, long beforeTime) {
		List<Statement> statements = methodExecution.getStatements();
		for (int i = statements.size() - 1; i >= 0; i--) {
			Statement statement = statements.get(i);
			if (!(statement instanceof FieldUpdate)) continue;
			if (statement.getTimeStamp() > beforeTime) continue;
			FieldUpdate fu = (FieldUpdate)statement;
			if (fu.getContainerObjId().equals(reference.getSrcObjectId())
					&& fu.getValueObjId().equals(reference.getDstObjectId())) {
				return new TracePoint(methodExecution, i);
			}
		}
		return null;
	}
	
	private void reset() {
		bottomPoint = null;
		DeltaMarkerManager mgr = DeltaMarkerManager.getInstance();
		mgr.deleteMarkers(DeltaMarkerManager.DELTA_MARKER_ID);
		mgr.deleteMarkers(DeltaMarkerManager.DELTA_MARKER_ID_2);
	}
	
	private void markAndOpenJavaFile(Alias alias, String message, String markerId) {
		IFile file = JavaEditorOperator.findIFile(alias.getMethodExecution());
		DeltaMarkerManager mgr = DeltaMarkerManager.getInstance();
		IMarker marker = mgr.addMarker(alias, file, message, markerId);
		JavaEditorOperator.markAndOpenJavaFile(marker);
	}
	
	private void markAndOpenJavaFile(MethodExecution methodExecution, int lineNo, String message, String markerId) {
		IFile file = JavaEditorOperator.findIFile(methodExecution);
		DeltaMarkerManager mgr = DeltaMarkerManager.getInstance();
		IMarker marker = mgr.addMarker(methodExecution, lineNo, file, message, markerId);
		JavaEditorOperator.markAndOpenJavaFile(marker);
	}
}
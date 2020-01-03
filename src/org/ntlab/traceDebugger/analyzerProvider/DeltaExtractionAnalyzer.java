package org.ntlab.traceDebugger.analyzerProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Reference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Trace;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TraceJSON;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
import org.ntlab.traceDebugger.DeltaMarkerView;
import org.ntlab.traceDebugger.Variable;

public class DeltaExtractionAnalyzer extends AbstractAnalyzer {
	private static DeltaExtractionAnalyzer theInstance = null;
	private DeltaExtractorJSON deltaExtractor;
	private ExtractedStructure extractedStructure;
	private Map<String, DeltaMarkerView> deltaMarkerViews = new HashMap<>();
	
	public DeltaExtractionAnalyzer(Trace trace) {
		super(trace);
		deltaExtractor = new DeltaExtractorJSON((TraceJSON)trace);
		reset();
	}
	
	private static DeltaExtractionAnalyzer getInstance() {
		if (theInstance == null) {
			theInstance = new DeltaExtractionAnalyzer(TraceJSON.getInstance());
		}
		return theInstance;
	}
	
	public ExtractedStructure geExtractedStructure() {
		return extractedStructure;
	}

	public void extractDelta(Variable variable, DeltaMarkerView deltaMarkerView, String deltaMarkerViewSubId) {
		addDeltaMarkerView(deltaMarkerViewSubId, deltaMarkerView);
		String srcId = variable.getContainerId();
		String srcClassName = variable.getContainerClassName();
		String dstId = variable.getId();
		String dstClassName = variable.getClassName();
		TracePoint before = variable.getBeforeTracePoint();
		Reference reference = new Reference(srcId, dstId, srcClassName, dstClassName);
		
		// デルタ抽出
		DeltaRelatedAliasCollector aliasCollector = new DeltaRelatedAliasCollector(srcId, dstId);
		extractedStructure = deltaExtractor.extract(reference, before.duplicate(), aliasCollector);
		MethodExecution creationCallTree = extractedStructure.getCreationCallTree();
		List<Alias> srcSideRelatedAliases = aliasCollector.getSrcSideRelatedAliases();
		List<Alias> dstSideRelatedAliases = aliasCollector.getDstSideRelatedAliases();
		MethodExecution coordinator = extractedStructure.getCoordinator();
		TracePoint bottomPoint = findTracePoint(reference, creationCallTree, before.getStatement().getTimeStamp());
		deltaMarkerView.setBottomPoint(bottomPoint);
		
		MethodExecution me = bottomPoint.getMethodExecution();
		MethodExecution childMe = null;
		while (me != null) {
			childMe = me;
			me = me.getParent();
			if (coordinator.equals(me)) {
				TracePoint coordinatorPoint = childMe.getCallerTracePoint();
				deltaMarkerView.setCoordinatorPoint(coordinatorPoint);		
				break;
			}
		}

		// デルタ抽出の結果を元にソースコードを反転表示する
		DeltaMarkerManager mgr = deltaMarkerView.getDeltaMarkerManager();
		mark(mgr, bottomPoint, srcSideRelatedAliases, dstSideRelatedAliases, coordinator);
		deltaMarkerView.update();
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

	private void mark(DeltaMarkerManager mgr, TracePoint bottomPoint, List<Alias> srcSideRelatedAliases, List<Alias> dstSideRelatedAliases, MethodExecution coordinator) {
		mgr.markAndOpenJavaFile(bottomPoint, "Bottom", DeltaMarkerManager.BOTTOM_DELTA_MARKER);
		mgr.markAndOpenJavaFile(coordinator, -1 , "Coordinator", DeltaMarkerManager.COORDINATOR_DELTA_MARKER);
		int cnt = 1;
		for (Alias alias: srcSideRelatedAliases) {
			String message = String.format("SrcSide%03d", cnt);
			mgr.markAndOpenJavaFile(alias, message, DeltaMarkerManager.SRC_SIDE_DELTA_MARKER);
			cnt++;
		}
		cnt = 1;
		for (Alias alias : dstSideRelatedAliases) {
			String message = String.format("DstSide%03d", cnt);
			mgr.markAndOpenJavaFile(alias, message, DeltaMarkerManager.DST_SIDE_DELTA_MARKER);
			cnt++;
		}
	}

	private void reset() {
		for (DeltaMarkerView deltaMarkerView : deltaMarkerViews.values()) {
			deltaMarkerView.getDeltaMarkerManager().clearAllMarkers();
		}
		deltaMarkerViews.clear();
	}
		
	private void addDeltaMarkerView(String subId, DeltaMarkerView deltaMarkerView) {
		deltaMarkerView.setSubId(subId);
		deltaMarkerViews.put(subId, deltaMarkerView);
	}
	
	public String getNextDeltaMarkerSubId() {
		return String.valueOf(deltaMarkerViews.size() + 1);
	}
}
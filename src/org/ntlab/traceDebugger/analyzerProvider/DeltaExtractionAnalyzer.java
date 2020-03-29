package org.ntlab.traceDebugger.analyzerProvider;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.ui.IViewPart;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ObjectReference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Reference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Trace;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TraceJSON;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
import org.ntlab.traceDebugger.DeltaMarkerView;
import org.ntlab.traceDebugger.TraceDebuggerPlugin;
import org.ntlab.traceDebugger.Variable;

public class DeltaExtractionAnalyzer extends AbstractAnalyzer {
	private static DeltaExtractionAnalyzer theInstance = null;
	private DeltaExtractorJSON deltaExtractor;
	private ExtractedStructure extractedStructure;
	private Map<String, DeltaMarkerView> deltaMarkerViews = new HashMap<>();
	private DeltaMarkerView activeDeltaMarkerView;
	
	public DeltaExtractionAnalyzer(Trace trace) {
		super(trace);
		deltaExtractor = new DeltaExtractorJSON((TraceJSON)trace);
		reset();
	}
	
	/**
	 * note: オンライン解析用
	 * @return
	 */
	private static DeltaExtractionAnalyzer getInstance() {
		if (theInstance == null) {
			theInstance = new DeltaExtractionAnalyzer(TraceJSON.getInstance());
		}
		return theInstance;
	}
	
	public ExtractedStructure geExtractedStructure() {
		return extractedStructure;
	}

	public void extractDelta(Variable variable, boolean isColection, DeltaMarkerView deltaMarkerView, String deltaMarkerViewSubId) {
		addDeltaMarkerView(deltaMarkerViewSubId, deltaMarkerView);
		String srcId = variable.getContainerId();
		String srcClassName = variable.getContainerClassName();
		String dstId = variable.getId();
		String dstClassName = variable.getClassName();
		TracePoint before = variable.getBeforeTracePoint();
		Reference reference = new Reference(srcId, dstId, srcClassName, dstClassName);
		reference.setCollection(isColection);
		
		// デルタ抽出
		DeltaRelatedAliasCollector aliasCollector = new DeltaRelatedAliasCollector(srcId, dstId);
		extractedStructure = deltaExtractor.extract(reference, before.duplicate(), aliasCollector);
		MethodExecution creationCallTree = extractedStructure.getCreationCallTree();
		MethodExecution coordinator = extractedStructure.getCoordinator();
		TracePoint bottomPoint = findTracePoint(reference, creationCallTree, before.getStatement().getTimeStamp());
		deltaMarkerView.setBottomPoint(bottomPoint);
		
//		deltaMarkerView.setCoordinatorPoint(coordinator.getEntryPoint());		
		MethodExecution me = bottomPoint.getMethodExecution();
		MethodExecution childMe = null;
		while (me != null) {
			if (coordinator.equals(me)) {
				TracePoint coordinatorPoint;
				if (childMe != null) {
					coordinatorPoint = childMe.getCallerTracePoint();
				} else {
					coordinatorPoint = bottomPoint;
				}
				deltaMarkerView.setCoordinatorPoint(coordinatorPoint);		
				break;
			}
			childMe = me;
			me = me.getParent();
		}

		// デルタ抽出の結果を元にソースコードを反転表示する
		DeltaMarkerManager mgr = deltaMarkerView.getDeltaMarkerManager();
		mark(mgr, coordinator, aliasCollector, bottomPoint, reference);
		deltaMarkerView.update();
	}

	private TracePoint findTracePoint(Reference reference, MethodExecution methodExecution, long beforeTime) {
		List<Statement> statements = methodExecution.getStatements();
		for (int i = statements.size() - 1; i >= 0; i--) {
			Statement statement = statements.get(i);
			if (statement.getTimeStamp() > beforeTime) continue;
			if (statement instanceof FieldUpdate) {
				FieldUpdate fu = (FieldUpdate)statement;
				if (fu.getContainerObjId().equals(reference.getSrcObjectId())
						&& fu.getValueObjId().equals(reference.getDstObjectId())) {
					return new TracePoint(methodExecution, i);
				}				
			} else if (statement instanceof MethodInvocation) {
				MethodInvocation mi = (MethodInvocation)statement;
				MethodExecution me = mi.getCalledMethodExecution();
				if (me.getThisObjId().equals(reference.getSrcObjectId())) {
					for (ObjectReference arg : me.getArguments()) {
						if (arg.getId().equals(reference.getDstObjectId())) {
							return new TracePoint(methodExecution, i);		
						}
					}
				}
			}
		}
		return null;
	}
	
//	private TracePoint findTracePoint(Reference reference, MethodExecution methodExecution, long beforeTime) {
//		List<Statement> statements = methodExecution.getStatements();
//		for (int i = statements.size() - 1; i >= 0; i--) {
//			Statement statement = statements.get(i);
//			if (!(statement instanceof FieldUpdate)) continue;
//			if (statement.getTimeStamp() > beforeTime) continue;
//			FieldUpdate fu = (FieldUpdate)statement;
//			if (fu.getContainerObjId().equals(reference.getSrcObjectId())
//					&& fu.getValueObjId().equals(reference.getDstObjectId())) {
//				return new TracePoint(methodExecution, i);
//			}
//		}
//		return null;
//	}

	private void mark(DeltaMarkerManager mgr, MethodExecution coordinator, DeltaRelatedAliasCollector aliasCollector, TracePoint bottomPoint, Reference creationReference) {
		int srcSideCnt = 1;
		int dstSideCnt = 1;
		mgr.markAndOpenJavaFileForCoordinator(coordinator, "Coordinator", DeltaMarkerManager.COORDINATOR_DELTA_MARKER);
		List<Alias> relatedAliases = aliasCollector.getRelatedAliases();
		Collections.reverse(relatedAliases);
		for (Alias alias : relatedAliases) {
			String side = aliasCollector.resolveSideInTheDelta(alias);
			if (side.contains(DeltaRelatedAliasCollector.SRC_SIDE)) {
				String message = String.format("SrcSide%03d", srcSideCnt);
				mgr.markAndOpenJavaFileForAlias(alias, message, DeltaMarkerManager.SRC_SIDE_DELTA_MARKER);
				srcSideCnt++;
			} else if (side.contains(DeltaRelatedAliasCollector.DST_SIDE)) {
				String message = String.format("DstSide%03d", dstSideCnt);
				mgr.markAndOpenJavaFileForAlias(alias, message, DeltaMarkerManager.DST_SIDE_DELTA_MARKER);
				dstSideCnt++;				
			}
		}
		mgr.markAndOpenJavaFileForCreationPoint(bottomPoint, creationReference, "CreationPoint", DeltaMarkerManager.BOTTOM_DELTA_MARKER);
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
	
	public DeltaMarkerView getActiveDeltaMarkerView() {
		return activeDeltaMarkerView;
	}
	
	public void setActiveDeltaMarkerView(DeltaMarkerView deltaMarkerView) {
		this.activeDeltaMarkerView = deltaMarkerView;
	}
}
package org.ntlab.traceDebugger.analyzerProvider;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.text.FindReplaceDocumentAdapter;
import org.eclipse.jface.text.IRegion;
import org.eclipse.ui.PartInitException;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.BlockEnter;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ObjectReference;
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
	private ExtractedStructure extractedStructure;
//	private MethodExecution coordinator;
//	private ReferencePoint coordinatorPoint;
//	private List<TracePoint> srcSideRelatedPoints = new ArrayList<>();
//	private List<TracePoint> dstSideRelatedPoints = new ArrayList<>();

	private TracePoint bottomPoint;
	public static final String DELTA_MARKER_ID = "org.ntlab.traceDebugger.deltaMarker";
	public static final String DELTA_MARKER_ID_2 = "org.ntlab.traceDebugger.deltaMarker2"; // 仮名
	
	public DeltaExtractionAnalyzer(Trace trace) {
		super(trace);
		deltaExtractor = new DeltaExtractorJSON((TraceJSON)trace);
		DeltaMarkerManager mgr = DeltaMarkerManager.getInstance();
		mgr.deleteMarkers(DELTA_MARKER_ID);
		mgr.deleteMarkers(DELTA_MARKER_ID_2);
	}
	
	private static DeltaExtractionAnalyzer getInstance() {
		if (getInstance == null) {
			getInstance = new DeltaExtractionAnalyzer(TraceJSON.getInstance());
		}
		return getInstance;
	}
	
	public TracePoint getBottomPoint() {
		return bottomPoint;
	}
	
//	public ReferencePoint getCoordinatorPoint() {
//		return coordinatorPoint;
//	}
//	
//	public List<TracePoint> getSrcSideRelatedPoints() {
//		return srcSideRelatedPoints;
//	}
//
//	public List<TracePoint> getDstSideRelatedPoints() {
//		return dstSideRelatedPoints;
//	}
	
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
		
		reset();
		DeltaRelatedAliasCollector aliasCollector = new DeltaRelatedAliasCollector(srcId, dstId);
		extractedStructure = deltaExtractor.extract(reference, before.duplicate(), aliasCollector);
		MethodExecution creationCallTree = extractedStructure.getCreationCallTree();
		List<Alias> srcSideRelatedAliases = aliasCollector.getSrcSideRelatedAliases();
		List<Alias> dstSideRelatedAliases = aliasCollector.getDstSideRelatedAliases();
		MethodExecution coordinator = extractedStructure.getCoordinator();
		
		bottomPoint = findTracePoint(reference, creationCallTree, before.getStatement().getTimeStamp());
//		String message = String.format("Bottom %s", createMessage(bottomPoint.getStatement()));
		String message = String.format("Bottom %s", "");
		markAndOpenJavaFile(bottomPoint.getMethodExecution(), bottomPoint.getStatement().getLineNo(), message, DELTA_MARKER_ID);
		for (Alias alias: srcSideRelatedAliases) {
			markAndOpenJavaFile(alias, DELTA_MARKER_ID);
		}
		for (Alias alias : dstSideRelatedAliases) {
			markAndOpenJavaFile(alias, DELTA_MARKER_ID_2);
		}
		markAndOpenJavaFile(coordinator, -1 , "Coordinator", DELTA_MARKER_ID);
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
	
	
//	private String createMessage(Alias alias) {
//		switch (alias.getAliasType()) {
//		// メソッドへの入口
//		case FORMAL_PARAMETOR:
//		case THIS: 
//		case METHOD_INVOCATION: 
//		case CONSTRACTOR_INVOCATION: 
//		
//		// 追跡オブジェクトの切り替え
//		case FIELD: 
//		case CONTAINER: 
//		case ARRAY_ELEMENT: 
//		case ARRAY: 
//
//		// メソッドからの出口
//		case ACTUAL_ARGUMENT: 
//		case RECEIVER: 
//		case RETURN_VALUE:
//
//		}		
//		return "";
//	}
	
//	private String createFindString(Statement statement) {
//		if (statement instanceof FieldAccess) {
//			FieldAccess fa = (FieldAccess)statement;
//			String fieldName = fa.getFieldName();
//			return fieldName.substring(fieldName.lastIndexOf(".") + 1);	
//		} else if (statement instanceof ArrayAccess) {
//			ArrayAccess aa = (ArrayAccess)statement;
//			return "";
//		} else if (statement instanceof MethodInvocation) {
//			MethodInvocation mi = (MethodInvocation)statement;
//			MethodExecution me = mi.getCalledMethodExecution();
//			String signature = me.getSignature();
//			signature = signature.substring(signature.lastIndexOf(".") + 1, signature.lastIndexOf("("));
//			return signature;
//		}
//		return "";
//	}
//	
//	private String createMessage(Statement statement) {
//		if (statement instanceof FieldAccess) {
//			FieldAccess fa = (FieldAccess)statement;
//			String fieldName = fa.getFieldName();
//			String containerObjId = fa.getContainerObjId();
//			String valueObjId = fa.getValueObjId();
//			return "FA: " + fieldName + " (" + containerObjId + " -> " + valueObjId + ")";
//		} else if (statement instanceof ArrayAccess) {
//			ArrayAccess aa = (ArrayAccess)statement;
//			String arrayObjId = aa.getArrayObjectId();
//			String valueObjId = aa.getValueObjectId();
//			return "AA: " + " (" + arrayObjId + " -> " + valueObjId + ")";
//		} else if (statement instanceof MethodInvocation) {
//			MethodInvocation mi = (MethodInvocation)statement;
//			MethodExecution me = mi.getCalledMethodExecution();
//			String signature = me.getSignature();
//			return "MI: " + signature;
//		}
//		return "";
//	}
	
	private void reset() {
		bottomPoint = null;
		DeltaMarkerManager mgr = DeltaMarkerManager.getInstance();
		mgr.deleteMarkers(DELTA_MARKER_ID);
		mgr.deleteMarkers(DELTA_MARKER_ID_2);
	}
	
	private void markAndOpenJavaFile(Alias alias, String markerId) {
//		MethodExecution me = tp.getMethodExecution();
//		int lineNo = tp.getStatement().getLineNo();
//		JavaEditorOperator.markAndOpenJavaFile(me, lineNo, findString, message, markerId, deltaMarkerMgr);

		IFile file = JavaEditorOperator.findIFile(alias.getMethodExecution());
		DeltaMarkerManager mgr = DeltaMarkerManager.getInstance();
		IMarker marker = mgr.addMarker(alias, file, markerId);
		JavaEditorOperator.markAndOpenJavaFile(marker);
	}
	
	private void markAndOpenJavaFile(MethodExecution methodExecution, int lineNo, String message, String markerId) {
		IFile file = JavaEditorOperator.findIFile(methodExecution);
		DeltaMarkerManager mgr = DeltaMarkerManager.getInstance();
		IMarker marker = mgr.addMarker(methodExecution, lineNo, file, message, markerId);
		JavaEditorOperator.markAndOpenJavaFile(marker);
	}
}
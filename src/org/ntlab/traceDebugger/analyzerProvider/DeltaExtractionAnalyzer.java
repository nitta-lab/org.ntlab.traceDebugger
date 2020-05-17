package org.ntlab.traceDebugger.analyzerProvider;

import java.util.List;
import java.util.Map;

import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ObjectReference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Reference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Trace;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TraceJSON;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
import org.ntlab.traceDebugger.Variable;

public class DeltaExtractionAnalyzer extends AbstractAnalyzer {
	private static DeltaExtractionAnalyzer theInstance = null;
	private DeltaExtractorJSON deltaExtractor;
	private ExtractedStructure extractedStructure;
	
	public DeltaExtractionAnalyzer(Trace trace) {
		super(trace);
		deltaExtractor = new DeltaExtractorJSON((TraceJSON)trace);
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

	public DeltaMarkerManager extractDeltaForContainerToComponent(Variable variable) {
		String srcId = variable.getContainerId();
		String srcClassName = variable.getContainerClassName();
		String dstId = variable.getValueId();
		String dstClassName = variable.getValueClassName();
		TracePoint before = variable.getBeforeTracePoint();				
		Reference reference = new Reference(srcId, dstId, srcClassName, dstClassName);
		reference.setCollection(srcClassName.startsWith("java.util.")); // trueにするとコレクション以外抽出できなくなる
		
		// デルタ抽出
		DeltaRelatedAliasCollector aliasCollector = new DeltaRelatedAliasCollector(srcId, dstId);
		extractedStructure = deltaExtractor.extract(reference, before.duplicate(), aliasCollector);
		MethodExecution creationCallTree = extractedStructure.getCreationCallTree();
		MethodExecution coordinator = extractedStructure.getCoordinator();
		TracePoint bottomPoint = findTracePoint(reference, creationCallTree, before.getStatement().getTimeStamp());
		return new DeltaMarkerManager(coordinator, bottomPoint, reference, aliasCollector);
	}
	
	public DeltaMarkerManager extractDeltaForThisToAnother(Variable variable) {
		TracePoint before = variable.getBeforeTracePoint();
		String srcId = before.getMethodExecution().getThisObjId();
		String srcClassName = before.getMethodExecution().getThisClassName();
		String dstId = variable.getValueId();
		String dstClassName = variable.getValueClassName();
		MethodExecution me = before.getMethodExecution();
		Map<ObjectReference, TracePoint> references = me.getObjectReferences(dstClassName);
		ObjectReference objectReference = null;
		TracePoint tp = null;
		for (Map.Entry<ObjectReference, TracePoint> entry : references.entrySet()) {
			ObjectReference key = entry.getKey();
			if (key.getId().equals(dstId)) {
				objectReference = key;
				tp = entry.getValue();
				break;
			}
		}
		
		// デルタ抽出
		TracePoint bottomPoint = tp.duplicate();
		DeltaRelatedAliasCollector aliasCollector = new DeltaRelatedAliasCollector(srcId, dstId);
		extractedStructure = deltaExtractor.extract(tp, objectReference, aliasCollector);
		MethodExecution coordinator = extractedStructure.getCoordinator();
		Reference reference = new Reference(srcId, dstId, srcClassName, dstClassName);
		return new DeltaMarkerManager(coordinator, bottomPoint, reference, aliasCollector);
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
				if (!(me.getThisObjId().equals(reference.getSrcObjectId()))) continue;
				for (ObjectReference arg : me.getArguments()) {
					if (arg.getId().equals(reference.getDstObjectId())) {
						return new TracePoint(methodExecution, i);		
					}
				}				
			}
		}
		return null;
	}
}
package org.ntlab.traceDebugger.analyzerProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ObjectReference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ThreadInstance;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Trace;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class VariableUpdatePointFinder {
	private static VariableUpdatePointFinder theInstance = new VariableUpdatePointFinder();
	private Trace trace;
	private Map<String, Map<String, List<TracePoint>>> updatePoints = new HashMap<>();
	private Map<String, Map<String, List<TracePoint>>> definitionInvocationPoints = new HashMap<>();

	public void setTrace(Trace trace) {
		this.trace = trace;
		init();
	}
	
	public static VariableUpdatePointFinder getInstance() {
		return theInstance;
	}

	private void init() {
		registerUpdatePoints();
		System.out.println(updatePoints);
		System.out.println(definitionInvocationPoints);
		sort(updatePoints);
		sort(definitionInvocationPoints);
		System.out.println(updatePoints);
		System.out.println(definitionInvocationPoints);
	}

	private void registerUpdatePoints() {
		for (Map.Entry<String, ThreadInstance> entry : trace.getAllThreads().entrySet()) {
			ThreadInstance thread = entry.getValue();
			for (MethodExecution me : thread.getRoot()) {
				TracePoint start = me.getEntryPoint();
				while (start.stepFull()) {
					Statement statement = start.getStatement();
					if (statement instanceof FieldUpdate) {
						registerFieldUpdatePoints(start, (FieldUpdate)statement);
					} else if (statement instanceof MethodInvocation) {
						registerdefinitionInvocationPoints(start, (MethodInvocation)statement);
					}
				}
			}
		}		
	}
	
	private void registerFieldUpdatePoints(TracePoint tp, FieldUpdate fu) {
		String objectId = fu.getContainerObjId();
		String fieldName = fu.getFieldName();
		register(updatePoints, objectId, fieldName, tp);
	}

	private void registerdefinitionInvocationPoints(TracePoint tp, MethodInvocation mi) {
		MethodExecution calledME = mi.getCalledMethodExecution();
		String methodName = calledME.getSignature();
		List<ObjectReference> args = calledME.getArguments();
		if (methodName.contains(".add(") || methodName.contains(".addElement(")) {
			if (args.size() == 1) {
				String receiverId = calledME.getThisObjId();
				String argId = args.get(0).getId();
				register(definitionInvocationPoints, receiverId, argId, tp);
			}
		}
	}
	
	private void register(Map<String, Map<String, List<TracePoint>>> map, String key1, String key2, TracePoint tp) {
		Map<String, List<TracePoint>> innerMap = map.get(key1);
		if (innerMap == null) {
			innerMap = new HashMap<>();
			map.put(key1, innerMap);
		}
		List<TracePoint> tracePoints = innerMap.get(key2);
		if (tracePoints == null) {
			tracePoints = new ArrayList<>();
			innerMap.put(key2, tracePoints);
		}
		tracePoints.add(tp.duplicate());
	}

	private void sort(Map<String, Map<String, List<TracePoint>>> map) {
		for (Map<String, List<TracePoint>> innerMap : map.values()) {
			for (List<TracePoint> tracePoints : innerMap.values()) {
				Collections.sort(tracePoints, new Comparator<TracePoint>() {
					@Override
					public int compare(TracePoint arg0, TracePoint arg1) {
						long time0 = arg0.getStatement().getTimeStamp();
						long time1 = arg1.getStatement().getTimeStamp();
						return (time0 < time1) ? -1 : 1;
					}
				});
			}
		}		
	}

	public TracePoint getPoint(String objectId, String fieldName, TracePoint before) {
		return getPoint(updatePoints, objectId, fieldName, before);
	}
	
	public TracePoint getDefinitionInvocationPoint(String receiverId, String argId, TracePoint before) {
		return getPoint(definitionInvocationPoints, receiverId, argId, before);
	}
	
	private TracePoint getPoint(Map<String, Map<String, List<TracePoint>>> map, String key1, String key2, TracePoint before) {
		Map<String, List<TracePoint>> innerMap = map.get(key1);
		if (innerMap == null) return null;
		List<TracePoint> tracePoints = innerMap.get(key2);
		if (tracePoints == null) return null;
		long beforeTime = before.getStatement().getTimeStamp();
		TracePoint tmp = null;
		for (TracePoint tp : tracePoints) {
			long time = tp.getStatement().getTimeStamp();
			if (time >= beforeTime) return tmp;
			tmp = tp;
		}
		return tmp;		
	}
}

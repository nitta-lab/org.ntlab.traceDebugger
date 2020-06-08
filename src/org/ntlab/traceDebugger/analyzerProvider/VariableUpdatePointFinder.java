package org.ntlab.traceDebugger.analyzerProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ThreadInstance;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Trace;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class VariableUpdatePointFinder {
	private static VariableUpdatePointFinder theInstance = new VariableUpdatePointFinder();
	private Trace trace;
	private Map<String, Map<String, List<TracePoint>>> updatePoints = new HashMap<>();

	public void setTrace(Trace trace) {
		this.trace = trace;
		init();
	}
	
	public static VariableUpdatePointFinder getInstance() {
		return theInstance;
	}

	private void init() {
		registerVariableUpdatePoints();
		System.out.println(updatePoints);
		sort();
		System.out.println(updatePoints);
	}

	private void registerVariableUpdatePoints() {
		for (Map.Entry<String, ThreadInstance> entry : trace.getAllThreads().entrySet()) {
			ThreadInstance thread = entry.getValue();
			for (MethodExecution me : thread.getRoot()) {
				TracePoint start = me.getEntryPoint();
				while (start.stepFull()) {
					Statement statement = start.getStatement();
					if (statement instanceof FieldUpdate) {
						FieldUpdate fu = (FieldUpdate)statement;
						String objectId = fu.getContainerObjId();
						String fieldName = fu.getFieldName();
						Map<String, List<TracePoint>> innerMap = updatePoints.get(objectId);
						if (innerMap == null) {
							innerMap = new HashMap<>();
							updatePoints.put(objectId, innerMap);
						}
						List<TracePoint> tracePoints = innerMap.get(fieldName);
						if (tracePoints == null) {
							tracePoints = new ArrayList<>();
							innerMap.put(fieldName, tracePoints);
						}
						tracePoints.add(start.duplicate());
					}
				}
			}
		}		
	}
	
	private void sort() {
		for (Map<String, List<TracePoint>> innerMap : updatePoints.values()) {
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
		Map<String, List<TracePoint>> innerMap = updatePoints.get(objectId);
		if (innerMap == null) return null;
		List<TracePoint> tracePoints = innerMap.get(fieldName);
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

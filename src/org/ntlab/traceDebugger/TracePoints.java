package org.ntlab.traceDebugger;

import java.util.ArrayList;
import java.util.List;

import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class TracePoints {
	List<TracePoint> tracePoints = new ArrayList<>();
	
	public List<TracePoint> getTracePoints() {
		return tracePoints;
	}
	
	public TracePoint[] getTracePointsArray() {
		return tracePoints.toArray(new TracePoint[tracePoints.size()]);
	}
	
	public void addTracePoints(TracePoint tp) {
		tracePoints.add(tp);
	}
	
	public void removeTracePoints(TracePoint tp) {
		tracePoints.remove(tp);
	}
}

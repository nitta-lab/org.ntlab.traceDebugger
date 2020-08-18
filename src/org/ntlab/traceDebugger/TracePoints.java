package org.ntlab.traceDebugger;

import java.util.ArrayList;
import java.util.List;

import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class TracePoints {
	List<TracePoint> tracePoints = new ArrayList<>();
	
	public List<TracePoint> get() {
		return tracePoints;
	}
	
	public TracePoint[] getToArray() {
		return tracePoints.toArray(new TracePoint[tracePoints.size()]);
	}
	
	public void add(TracePoint tp) {
		tracePoints.add(tp);
	}
	
	public void remove(TracePoint tp) {
		tracePoints.remove(tp);
	}
	
	public void clear() {
		tracePoints.clear();
	}
}

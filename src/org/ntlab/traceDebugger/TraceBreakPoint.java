package org.ntlab.traceDebugger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Trace;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class TraceBreakPoint {
	private List<TracePoint> tracePoints = new ArrayList<>();
	private String methodSignature;
	private int lineNo;
	private List<MethodExecution> methodExecutions = new ArrayList<>();
	private boolean isAvailable = false;

	public TraceBreakPoint(String methodSignature, int lineNo, long currentTime) {
		this.methodSignature = methodSignature;
		this.lineNo = lineNo;
		isAvailable = initTracePoints(methodSignature, lineNo);
	}

	private boolean initTracePoints(String methodSignature, int lineNo) {
		Trace trace = TraceDebuggerPlugin.getAnalyzer().getTrace();
		methodExecutions = trace.getMethodExecutions(methodSignature);
		if (methodExecutions.isEmpty()) return false;
		tracePoints.clear();
		for (MethodExecution me : methodExecutions) {
			int order = 0;
			for (Statement statement : me.getStatements()) {
				if (statement.getLineNo() == lineNo) {
					tracePoints.add(me.getTracePoint(order));
				}
				order++;
			}
		}
		if (tracePoints.isEmpty()) return false;
		Collections.sort(tracePoints, new Comparator<TracePoint>() {
			@Override
			public int compare(TracePoint o1, TracePoint o2) {
				long o1Time = getTime(o1);
				long o2Time = getTime(o2);
				return (o1Time < o2Time) ? -1 : 1;
			}			
			private long getTime(TracePoint tp) {
				Statement statement = tp.getStatement();
				if (statement instanceof MethodInvocation) {
					return ((MethodInvocation)statement).getCalledMethodExecution().getEntryTime();
				}
				return statement.getTimeStamp();
			}
		});
		return true;
	}
	
	public String getMethodSignature() {
		return methodSignature;
	}
	
	public int getLineNo() {
		return lineNo;
	}
	
	public List<MethodExecution> getMethodExecutions() {
		return methodExecutions;
	}
	
	public List<TracePoint> getTracePoints() {
		return tracePoints;
	}

	public boolean isAvailable() {
		return isAvailable;
	}
	
	public void changeAvailable() {
		isAvailable = !isAvailable;
	}	
}

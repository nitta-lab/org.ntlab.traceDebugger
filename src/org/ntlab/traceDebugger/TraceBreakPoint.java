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
//	private int currentIndex = 0;

	public TraceBreakPoint(String methodSignature, int lineNo, long currentTime) {
		this.methodSignature = methodSignature;
		this.lineNo = lineNo;
		isAvailable = initTracePoints(methodSignature, lineNo);
//		if (isAvailable) forwardIndex(currentTime);
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
//					break;
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
	
//	public void reset() {
//		initTracePoints(methodSignature, lineNo);
//		currentIndex = 0;
//	}
//	
//	public TracePoint peekTracePoint() {
//		if ((currentIndex < 0) || (currentIndex >= tracePoints.size())) return null;
//		return tracePoints.get(currentIndex);
//	}
//	
//	public TracePoint previousTracePoint() {
//		if ((currentIndex - 1 < 0) || (currentIndex - 1 >= tracePoints.size())) return null;
//		return tracePoints.get(currentIndex - 1);
//	}
//	
//	public TracePoint dequeueTracePoint(boolean isForward) {
//		TracePoint result = null;
//		if (isForward) {
//			result = peekTracePoint();
//			currentIndex++;
//		} else {
//			result = previousTracePoint();
//			currentIndex--;
//		}
//		return result;
//	}
//	
//	public void forwardIndex(long currentTime) {
//		int start = currentIndex;
//		for (int i = start; i < tracePoints.size(); i++) {
//			long time = getTime(tracePoints.get(i).getStatement());
//			if (time > currentTime) {
//				currentIndex = i;
//				return;
//			}
//		}
//		currentIndex = tracePoints.size();
//	}
//	
//	public void reverseIndex(long currentTime) {
//		for (int i = tracePoints.size() - 1; i >= 0; i--) {
//			long time = getTime(tracePoints.get(i).getStatement());
//			if (time <= currentTime) {
//				currentIndex = i + 1;
//				return;
//			}
//		}
//		currentIndex = 0;
//	}
//
//	private long getTime(Statement statement) {
//		if (statement instanceof MethodInvocation) {
//			return ((MethodInvocation)statement).getCalledMethodExecution().getEntryTime();
//		}
//		return statement.getTimeStamp();
//	}
}

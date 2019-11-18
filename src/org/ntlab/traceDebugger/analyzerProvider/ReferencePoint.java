package org.ntlab.traceDebugger.analyzerProvider;

import java.util.List;

import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Reference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TraceJSON;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
import org.ntlab.traceDebugger.TraceBreakPoint;

public class ReferencePoint {
	private Reference reference;
	private MethodExecution methodExecution;
	private Statement statement;
	
	public ReferencePoint(Reference reference, MethodExecution methodExecution, long beforeTime) {
		this.reference = reference;
		this.methodExecution = methodExecution;
		findFieldUpdate(beforeTime);
	}
	
	public ReferencePoint(Reference reference, MethodExecution methodExecution, Statement statement) {
		this.reference = reference;
		this.methodExecution = methodExecution;
		this.statement = statement;
	}
	
	private void findFieldUpdate(long beforeTime) {
		List<Statement> statements = methodExecution.getStatements();
		for (int i = statements.size() - 1; i >= 0; i--) {
			Statement statement = statements.get(i);
			if (!(statement instanceof FieldUpdate)) continue;
			if (statement.getTimeStamp() > beforeTime) continue;
			FieldUpdate fu = (FieldUpdate)statement;
			if (fu.getContainerObjId().equals(reference.getSrcObjectId())
					&& fu.getValueObjId().equals(reference.getDstObjectId())) {
				this.statement = fu;
				return;
			}
		}
	}
	
	public Reference getReference() {
		return reference;
	}
	
	public MethodExecution getMethodExecution() {
		return methodExecution;
	}
	
	public int getLineNo() {
		return (statement != null) ? statement.getLineNo() : -1; 
	}
	
	public long getTime() {
		if (statement instanceof MethodInvocation) {
			return ((MethodInvocation)statement).getCalledMethodExecution().getEntryTime();
		}
		return statement.getTimeStamp();
	}
	
	public TracePoint getTracePoint() {
		int order = 0;
		for (Statement statement : methodExecution.getStatements()) {
			if (statement.equals(this.statement)) break;
			order++;
		}
		return new TracePoint(methodExecution, order);
	}

	public String getReferenceMessage() {
		if (reference == null) return "";
		StringBuilder ref = new StringBuilder();
		ref.append(reference.getSrcClassName() + "(" + reference.getSrcObjectId() + ")");
		ref.append(" -> ");
		ref.append(reference.getDstClassName() + "(" + reference.getDstObjectId() + ")");
		return ref.toString();
	}
	
	@Override
	public String toString() {
		int lineNo = getLineNo();
		String location = methodExecution.getSignature() + " line: " + lineNo;
		String ref = getReferenceMessage();
		return String.format("%-50s %s", location, ref);
	}
}
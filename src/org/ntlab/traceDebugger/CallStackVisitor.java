package org.ntlab.traceDebugger;

import org.ntlab.traceAnalysisPlatform.tracer.trace.IStatementVisitor;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class CallStackVisitor implements IStatementVisitor {
	private TracePoint before = null;
	
	public CallStackVisitor() {
		
	}
	
	public CallStackVisitor(TracePoint before) {
		this.before = before;
	}
	
	@Override
	public boolean preVisitStatement(Statement statement) {
		System.out.println("CallStackVisitor#preVisitStatement(Statement)");
		if (!(statement instanceof MethodInvocation)) return false;
		if (before == null) return true;
		MethodInvocation mi = (MethodInvocation)statement;
		return (mi.getTimeStamp() < before.getStatement().getTimeStamp());
	}

	@Override
	public boolean postVisitStatement(Statement statement) {
		return false;
	}
}

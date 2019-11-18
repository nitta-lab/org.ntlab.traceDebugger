package org.ntlab.traceDebugger.analyzerProvider;

import org.ntlab.traceAnalysisPlatform.tracer.trace.Trace;

public abstract class AbstractAnalyzer {
	protected Trace trace;
	
	public AbstractAnalyzer(Trace trace) {
		this.trace = trace;
	}
	
	public Trace getTrace() {
		return trace;
	}
}

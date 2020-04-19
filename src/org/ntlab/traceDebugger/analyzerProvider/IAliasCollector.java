package org.ntlab.traceDebugger.analyzerProvider;

public interface IAliasCollector {
	
	void addAlias(Alias alias);
	
	void changeTrackingObject(String from, String to, boolean isSrcSide);
	
}
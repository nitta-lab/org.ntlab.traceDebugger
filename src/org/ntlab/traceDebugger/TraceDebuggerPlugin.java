package org.ntlab.traceDebugger;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.ntlab.traceDebugger.analyzerProvider.AbstractAnalyzer;
import org.ntlab.traceDebugger.analyzerProvider.ObjectFlowAnalyzer;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class TraceDebuggerPlugin extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "org.ntlab.helloWorld"; //$NON-NLS-1$

//	private static ObjectFlowAnalyzer objectFlowAnalyzer;
	private static AbstractAnalyzer analyzer;
	
	// The shared instance
	private static TraceDebuggerPlugin plugin;
	
	/**
	 * The constructor
	 */
	public TraceDebuggerPlugin() {
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
	}

	/*
	 * (non-Javadoc)
	 * @see org.eclipse.ui.plugin.AbstractUIPlugin#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 *
	 * @return the shared instance
	 */
	public static TraceDebuggerPlugin getDefault() {
		return plugin;
	}

//	public static ObjectFlowAnalyzer getObjectFlowAnalyzer() {
//		return objectFlowAnalyzer;
//	}
//	
//	public static void setObjectFlowAnalyzer(ObjectFlowAnalyzer objectFlowAnalyzer) {
//		TraceDebuggerPlugin.objectFlowAnalyzer = objectFlowAnalyzer;
//	}
	
	public static AbstractAnalyzer getAnalyzer() {
		return analyzer;
	}
	
	public static void setAnalyzer(AbstractAnalyzer analyzer) {
		TraceDebuggerPlugin.analyzer = analyzer;
	}
}

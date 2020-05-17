package org.ntlab.traceDebugger;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.ntlab.traceDebugger.analyzerProvider.AbstractAnalyzer;
import org.osgi.framework.BundleContext;

/**
 * The activator class controls the plug-in life cycle
 */
public class TraceDebuggerPlugin extends AbstractUIPlugin {

	// The plug-in ID
	public static final String PLUGIN_ID = "org.ntlab.helloWorld"; //$NON-NLS-1$

	private static AbstractAnalyzer analyzer;
	
	private static int uniqueIdForViews = 0;
	
	private static Map<String, IViewPart> viewIdToActiveView = new HashMap<>();
	
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
	
	public static AbstractAnalyzer getAnalyzer() {
		return analyzer;
	}
	
	public static IViewPart getActiveView(String viewId) {
		return viewIdToActiveView.get(viewId);
	}
	
	public static void setAnalyzer(AbstractAnalyzer analyzer) {
		TraceDebuggerPlugin.analyzer = analyzer;
	}
	
	public static void setActiveView(String viewId, IViewPart activeView) {
		viewIdToActiveView.put(viewId, activeView);
	}

	public static IViewPart createNewView(String viewId, int mode) {
		String secondaryId = "View" + (uniqueIdForViews++);
		IWorkbenchPage workbenchPage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		try {
			return workbenchPage.showView(viewId, secondaryId, mode);
		} catch (PartInitException e) {
			throw new RuntimeException(e);
		}
	}	
}

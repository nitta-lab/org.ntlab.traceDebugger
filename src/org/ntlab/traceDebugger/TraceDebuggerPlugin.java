package org.ntlab.traceDebugger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
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
	public static final String PLUGIN_ID = "org.ntlab.traceDebugger"; //$NON-NLS-1$

	private static AbstractAnalyzer analyzer;
	
	private static int uniqueIdForViews = 0;
	
	private static Map<String, Set<IViewPart>> viewIdToAllViews = new HashMap<>();
	
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
	
	public static Map<String, Set<IViewPart>> getAllViews() {
		return viewIdToAllViews;
	}
	
	public static Set<IViewPart> getViews(String viewId) {
		return viewIdToAllViews.get(viewId);
	}
	
	public static void setAnalyzer(AbstractAnalyzer analyzer) {
		TraceDebuggerPlugin.analyzer = analyzer;
	}
	
	public static void setActiveView(String viewId, IViewPart activeView) {
		viewIdToActiveView.put(viewId, activeView);
		addView(viewId, activeView);
	}
	
	private static void addView(String viewId, IViewPart view) {
		Set<IViewPart> views = viewIdToAllViews.get(viewId);
		if (views == null) {
			views = new HashSet<IViewPart>();
			viewIdToAllViews.put(viewId, views);
		}
		views.add(view);
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
	
	@Override
	protected void initializeImageRegistry(ImageRegistry reg) {
		reg.put(BreakPointViewRelatedDelta.STEP_BACK_INTO_ELCL, getImageDescriptor("/icons/debug/stepbackinto_elcl.png"));
		reg.put(BreakPointViewRelatedDelta.STEP_BACK_INTO_DLCL, getImageDescriptor("/icons/debug/stepbackinto_dlcl.png"));
		reg.put(BreakPointViewRelatedDelta.STEP_BACK_OVER_ELCL, getImageDescriptor("/icons/debug/stepbackover_elcl.png"));
		reg.put(BreakPointViewRelatedDelta.STEP_BACK_OVER_DLCL, getImageDescriptor("/icons/debug/stepbackover_dlcl.png"));
		reg.put(BreakPointViewRelatedDelta.STEP_BACK_RETURN_ELCL, getImageDescriptor("/icons/debug/stepbackreturn_elcl.png"));
		reg.put(BreakPointViewRelatedDelta.STEP_BACK_RETURN_DLCL, getImageDescriptor("/icons/debug/stepbackreturn_dlcl.png"));
		reg.put(BreakPointViewRelatedDelta.BACK_RESUME_ELCL, getImageDescriptor("/icons/debug/backresume_elcl.png"));		
		reg.put(BreakPointViewRelatedDelta.BACK_RESUME_DLCL, getImageDescriptor("/icons/debug/backresume_dlcl.png"));			
	}
	
	public static ImageDescriptor getImageDescriptor(String path) {
		return imageDescriptorFromPlugin(PLUGIN_ID, path);
	}
}

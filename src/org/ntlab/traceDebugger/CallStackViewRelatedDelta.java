package org.ntlab.traceDebugger;

import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
import org.ntlab.traceDebugger.analyzerProvider.DeltaMarkerManager;

public class CallStackViewRelatedDelta extends CallStackView {
	private IAction deltaAction;
	public static final String ID = "org.ntlab.traceDebugger.callStackViewRelatedDelta";
	
	public CallStackViewRelatedDelta() {
		// TODO Auto-generated constructor stub
		System.out.println("callStackViewRelatedDeltaÉNÉâÉXÇ™ê∂ê¨Ç≥ÇÍÇΩÇÊ");
	}

	@Override
	public void createPartControl(Composite parent) {
		// TODO Auto-generated method stub
		System.out.println("CallStackViewRelatedDelta#createPartControl(Composite)Ç™åƒÇŒÇÍÇΩÇÊ!");
		super.createPartControl(parent);
		TraceDebuggerPlugin.setActiveView(ID, this);
	}

	@Override
	public void setFocus() {
		// TODO Auto-generated method stub
		TraceDebuggerPlugin.setActiveView(ID, this);
		viewer.getControl().setFocus();
	}
	
	@Override
	protected void createActions() {
		deltaAction = new Action() {
			@Override
			public void run() {
				if (selectionCallStackModel != null) {
					MethodExecution callee = selectionCallStackModel.getMethodExecution();
					MethodExecution caller = callee.getParent();
					String callerClassName = caller.getThisClassName();
					String callerId = caller.getThisObjId();
					String calleeClassName = callee.getThisClassName();
					String calleeId = callee.getThisObjId();
					TracePoint before = callee.getCallerTracePoint();
					Variable variable = new Variable("tmp", callerClassName, callerId, calleeClassName, calleeId, before, false);
					DeltaMarkerView newDeltaMarkerView = (DeltaMarkerView)TraceDebuggerPlugin.createNewView(DeltaMarkerView.ID, IWorkbenchPage.VIEW_ACTIVATE);
					newDeltaMarkerView.extractDelta(variable, false);
				}
			}
		};
		deltaAction.setText("Extract Delta");
		deltaAction.setToolTipText("Extract Delta");		
	}
	
	protected void createPopupMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			@Override
			public void menuAboutToShow(IMenuManager manager) {
				if (selectionCallStackModel != null) {
					MethodExecution callee = selectionCallStackModel.getMethodExecution();
					MethodExecution caller = callee.getParent();
					String callerId = caller.getThisObjId();
					String callerClassName = caller.getThisClassName();
					callerClassName = callerClassName.substring(callerClassName.lastIndexOf(".") + 1);
					String calleeId = callee.getThisObjId();
					String calleeClassName = callee.getThisClassName();
					calleeClassName = calleeClassName.substring(calleeClassName.lastIndexOf(".") + 1);
					String text = String.format("Extract Delta (%s: %s Å® %s: %s)", callerId, callerClassName, calleeId, calleeClassName);
					deltaAction.setText(text);
					deltaAction.setToolTipText(text);
					manager.add(deltaAction);
					manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
				}
			}
		});
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}
	
	@Override
	protected void additonalActionOnSelectionChanged(CallStackModel selectedCallStackModel) {
		super.additonalActionOnSelectionChanged(selectedCallStackModel);
		DeltaMarkerView deltaMarkerView = (DeltaMarkerView)TraceDebuggerPlugin.getActiveView(DeltaMarkerView.ID);
		if (deltaMarkerView != null) {
			DeltaMarkerManager deltaMarkerManager = deltaMarkerView.getDeltaMarkerManager();
			if (deltaMarkerManager != null) {
				Map<String, List<IMarker>> deltaMarkers = deltaMarkerManager.getMarkers();
				if (deltaMarkers != null) {
					VariableViewRelatedDelta variableView = (VariableViewRelatedDelta)TraceDebuggerPlugin.getActiveView(VariableViewRelatedDelta.ID);
					variableView.markAndExpandVariablesByDeltaMarkers(deltaMarkers);	
				}
			}
		}
	}
}

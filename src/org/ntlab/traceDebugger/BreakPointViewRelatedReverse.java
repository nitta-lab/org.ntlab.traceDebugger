package org.ntlab.traceDebugger;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.swt.widgets.Composite;

public class BreakPointViewRelatedReverse extends BreakPointView {
	private IAction stepBackIntoAction;
	private IAction stepBackOverAction;
	private IAction stepBackReturnAction;
	private IAction backResumeAction;
	private DebuggingController debuggingController = DebuggingController.getInstance();
	public static final String ID = "org.ntlab.traceDebugger.breakPointViewRelatedReverse";

	public BreakPointViewRelatedReverse() {
		// TODO Auto-generated constructor stub
		System.out.println("BreakPointViewRelatedReverseÉNÉâÉXÇ™ê∂ê¨Ç≥ÇÍÇΩÇÊ!");
	}

	@Override
	public void createPartControl(Composite parent) {
		// TODO Auto-generated method stub
		System.out.println("BreakPointViewRelatedReverse#createPartControl(Composite)Ç™åƒÇŒÇÍÇΩÇÊ!");
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
		super.createActions();
		stepBackIntoAction = new Action() {
			@Override
			public void run() {
				debuggingController.stepBackIntoAction();
			}
		};
		stepBackIntoAction.setText("Step Back Into");
		stepBackIntoAction.setToolTipText("Step Back Into");
		
		stepBackOverAction = new Action() {
			@Override
			public void run() {
				debuggingController.stepBackOverAction();
			}
		};
		stepBackOverAction.setText("Step Back Over");
		stepBackOverAction.setToolTipText("Step Back Over");
		
		stepBackReturnAction = new Action() {
			@Override
			public void run() {
				debuggingController.stepBackReturnAction();
			}
		};
		stepBackReturnAction.setText("Step Back Return");
		stepBackReturnAction.setToolTipText("Step Back Return");
		
		backResumeAction = new Action() {
			@Override
			public void run() {
				debuggingController.backResumeAction();
			}
		};
		backResumeAction.setText("Back Resume");
		backResumeAction.setToolTipText("Back Resume");
	}
	
	@Override
	protected void createToolBar() {
		super.createToolBar();
		IToolBarManager mgr = getViewSite().getActionBars().getToolBarManager();
		mgr.add(stepBackIntoAction);
		mgr.add(stepBackOverAction);
		mgr.add(stepBackReturnAction);
		mgr.add(backResumeAction);
	}
	
	@Override
	protected void createMenuBar() {
		super.createMenuBar();
		IMenuManager mgr = getViewSite().getActionBars().getMenuManager();
		mgr.add(stepBackIntoAction);
		mgr.add(stepBackOverAction);
		mgr.add(stepBackReturnAction);
		mgr.add(backResumeAction);
	}	
}

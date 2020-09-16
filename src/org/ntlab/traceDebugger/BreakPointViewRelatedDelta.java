package org.ntlab.traceDebugger;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.widgets.Composite;

public class BreakPointViewRelatedDelta extends BreakPointView {
	private IAction stepBackIntoAction;
	private IAction stepBackOverAction;
	private IAction stepBackReturnAction;
	private IAction backResumeAction;
	private DebuggingController debuggingController = DebuggingController.getInstance();
	public static final String ID = "org.ntlab.traceDebugger.breakPointViewRelatedDelta";
	public static final String STEP_BACK_INTO_ELCL = "StepBackInto_ELCL";
	public static final String STEP_BACK_INTO_DLCL = "StepBackInto_DLCL";
	public static final String STEP_BACK_OVER_ELCL = "StepBackOver_ELCL";
	public static final String STEP_BACK_OVER_DLCL = "StepBackOver_DLCL";
	public static final String STEP_BACK_RETURN_ELCL = "StepBackReturn_ELCL";
	public static final String STEP_BACK_RETURN_DLCL = "StepBackReturn_DLCL";
	public static final String BACK_RESUME_ELCL = "BackResume_ELCL";
	public static final String BACK_RESUME_DLCL = "BackResume_DLCL";

	public BreakPointViewRelatedDelta() {
		// TODO Auto-generated constructor stub
		System.out.println("BreakPointViewRelatedDeltaクラスが生成されたよ!");
	}

	@Override
	public void createPartControl(Composite parent) {
		// TODO Auto-generated method stub
		System.out.println("BreakPointViewRelatedDelta#createPartControl(Composite)が呼ばれたよ!");
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
	public void dispose() {
		super.dispose();
		TraceDebuggerPlugin.removeView(ID, this);
	}
	
	@Override
	protected void createActions() {
		super.createActions();
		ImageRegistry registry = TraceDebuggerPlugin.getDefault().getImageRegistry();
		boolean isJapanese = TraceDebuggerPlugin.isJapanese();
		String msg;
		
		backResumeAction = new Action() {
			@Override
			public void run() {
				debuggingController.backResumeAction();
			}
		};
		msg = (isJapanese) ? "逆向きに再開" : "Back Resume";
		backResumeAction.setText(msg);
		backResumeAction.setToolTipText(msg);
		ImageDescriptor backResumeIcon = registry.getDescriptor(BACK_RESUME_DLCL);
		backResumeAction.setImageDescriptor(backResumeIcon);
		
		stepBackIntoAction = new Action() {
			@Override
			public void run() {
				debuggingController.stepBackIntoAction();
			}
		};
		msg = (isJapanese) ? "ステップバックイン" : "Step Back Into";
		stepBackIntoAction.setText(msg);
		stepBackIntoAction.setToolTipText(msg);
		ImageDescriptor stepBackIntoIcon = registry.getDescriptor(STEP_BACK_INTO_DLCL);
		stepBackIntoAction.setImageDescriptor(stepBackIntoIcon);
		
		stepBackOverAction = new Action() {
			@Override
			public void run() {
				debuggingController.stepBackOverAction();
			}
		};
		msg = (isJapanese) ? "ステップバックオーバー" : "Step Back Over";
		stepBackOverAction.setText(msg);
		stepBackOverAction.setToolTipText(msg);
		ImageDescriptor stepBackOverIcon = registry.getDescriptor(STEP_BACK_OVER_DLCL);
		stepBackOverAction.setImageDescriptor(stepBackOverIcon);

		stepBackReturnAction = new Action() {
			@Override
			public void run() {
				debuggingController.stepBackReturnAction();
			}
		};
		msg = (isJapanese) ? "ステップバックリターン" : "Step Back Return";
		stepBackReturnAction.setText(msg);
		stepBackReturnAction.setToolTipText(msg);
		ImageDescriptor stepBackReturnIcon = registry.getDescriptor(STEP_BACK_RETURN_DLCL);
		stepBackReturnAction.setImageDescriptor(stepBackReturnIcon);		
	}
	
	@Override
	protected void createToolBar() {
		IToolBarManager mgr = getViewSite().getActionBars().getToolBarManager();
		mgr.add(fileOpenAction);
		mgr.add(importBreakpointAction);
		mgr.add(debugAction);
		mgr.add(stepBackReturnAction);
		mgr.add(stepBackOverAction);
		mgr.add(stepBackIntoAction);
		mgr.add(backResumeAction);
		mgr.add(terminateAction);
		mgr.add(resumeAction);
		mgr.add(stepIntoAction);
		mgr.add(stepOverAction);
		mgr.add(stepReturnAction);
		mgr.add(stepNextAction);
	}
	
	@Override
	protected void createMenuBar() {
		IMenuManager mgr = getViewSite().getActionBars().getMenuManager();		
		mgr.add(fileOpenAction);
		mgr.add(importBreakpointAction);
		mgr.add(debugAction);
		mgr.add(stepBackReturnAction);
		mgr.add(stepBackOverAction);
		mgr.add(stepBackIntoAction);
		mgr.add(backResumeAction);
		mgr.add(terminateAction);
		mgr.add(resumeAction);
		mgr.add(stepIntoAction);
		mgr.add(stepOverAction);
		mgr.add(stepReturnAction);
		mgr.add(stepNextAction);
	}
	
	@Override
	public void updateImagesForDebug(boolean isRunning) {
		super.updateImagesForDebug(isRunning);
		ImageRegistry registry = TraceDebuggerPlugin.getDefault().getImageRegistry();
		if (isRunning) {
			ImageDescriptor stepBackIntoImage = registry.getDescriptor(STEP_BACK_INTO_ELCL);
			stepBackIntoAction.setImageDescriptor(stepBackIntoImage);
			ImageDescriptor stepBackOverImage = registry.getDescriptor(STEP_BACK_OVER_ELCL);
			stepBackOverAction.setImageDescriptor(stepBackOverImage);
			ImageDescriptor stepBackReturnImage = registry.getDescriptor(STEP_BACK_RETURN_ELCL);
			stepBackReturnAction.setImageDescriptor(stepBackReturnImage);
			ImageDescriptor backResumeImage = registry.getDescriptor(BACK_RESUME_ELCL);
			backResumeAction.setImageDescriptor(backResumeImage);
		} else {
			ImageDescriptor stepBackIntoImage = registry.getDescriptor(STEP_BACK_INTO_DLCL);
			stepBackIntoAction.setImageDescriptor(stepBackIntoImage);
			ImageDescriptor stepBackOverImage = registry.getDescriptor(STEP_BACK_OVER_DLCL);
			stepBackOverAction.setImageDescriptor(stepBackOverImage);
			ImageDescriptor stepBackReturnImage = registry.getDescriptor(STEP_BACK_RETURN_DLCL);
			stepBackReturnAction.setImageDescriptor(stepBackReturnImage);
			ImageDescriptor backResumeImage = registry.getDescriptor(BACK_RESUME_DLCL);
			backResumeAction.setImageDescriptor(backResumeImage);			
		}
	}
}

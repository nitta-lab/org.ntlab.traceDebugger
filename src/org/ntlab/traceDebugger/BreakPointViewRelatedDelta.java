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
	public static final String STEP_BACK_OVER_ELCL = "StepOverInto_ELCL";
	public static final String STEP_BACK_OVER_DLCL = "StepOverInto_DLCL";
	public static final String STEP_BACK_RETURN_ELCL = "StepReturnInto_ELCL";
	public static final String STEP_BACK_RETURN_DLCL = "StepReturnInto_DLCL";
	public static final String BACK_RESUME_ELCL = "BackResume_ELCL";
	public static final String BACK_RESUME_DLCL = "BackResume_DLCL";

	public BreakPointViewRelatedDelta() {
		// TODO Auto-generated constructor stub
		System.out.println("BreakPointViewRelatedDeltaÉNÉâÉXÇ™ê∂ê¨Ç≥ÇÍÇΩÇÊ!");
	}

	@Override
	public void createPartControl(Composite parent) {
		// TODO Auto-generated method stub
		System.out.println("BreakPointViewRelatedDelta#createPartControl(Composite)Ç™åƒÇŒÇÍÇΩÇÊ!");
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
		ImageRegistry registry = TraceDebuggerPlugin.getDefault().getImageRegistry();		
		backResumeAction = new Action() {
			@Override
			public void run() {
				debuggingController.backResumeAction();
			}
		};
		backResumeAction.setText("Back Resume");
		backResumeAction.setToolTipText("Back Resume");
		ImageDescriptor backResumeIcon = registry.getDescriptor(BACK_RESUME_DLCL);
		backResumeAction.setImageDescriptor(backResumeIcon);
		
		stepBackIntoAction = new Action() {
			@Override
			public void run() {
				debuggingController.stepBackIntoAction();
			}
		};
		stepBackIntoAction.setText("Step Back Into");
		stepBackIntoAction.setToolTipText("Step Back Into");
		ImageDescriptor stepBackIntoIcon = registry.getDescriptor(STEP_BACK_INTO_DLCL);
		stepBackIntoAction.setImageDescriptor(stepBackIntoIcon);
		
		stepBackOverAction = new Action() {
			@Override
			public void run() {
				debuggingController.stepBackOverAction();
			}
		};
		stepBackOverAction.setText("Step Back Over");
		stepBackOverAction.setToolTipText("Step Back Over");
		ImageDescriptor stepBackOverIcon = registry.getDescriptor(STEP_BACK_OVER_DLCL);
		stepBackOverAction.setImageDescriptor(stepBackOverIcon);

		stepBackReturnAction = new Action() {
			@Override
			public void run() {
				debuggingController.stepBackReturnAction();
			}
		};
		stepBackReturnAction.setText("Step Back Return");
		stepBackReturnAction.setToolTipText("Step Back Return");
		ImageDescriptor stepBackReturnIcon = registry.getDescriptor(STEP_BACK_RETURN_DLCL);
		stepBackReturnAction.setImageDescriptor(stepBackReturnIcon);		
	}
	
	@Override
	protected void createToolBar() {
		super.createToolBar();
		IToolBarManager mgr = getViewSite().getActionBars().getToolBarManager();
		mgr.add(backResumeAction);
		mgr.add(stepBackIntoAction);
		mgr.add(stepBackOverAction);
		mgr.add(stepBackReturnAction);
	}
	
	@Override
	protected void createMenuBar() {
		super.createMenuBar();
		IMenuManager mgr = getViewSite().getActionBars().getMenuManager();
		mgr.add(backResumeAction);
		mgr.add(stepBackIntoAction);
		mgr.add(stepBackOverAction);
		mgr.add(stepBackReturnAction);
	}
	
	@Override
	public void updateImages(boolean isRunning) {
		super.updateImages(isRunning);
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

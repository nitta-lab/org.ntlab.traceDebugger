package org.ntlab.traceDebugger;

import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.BlockEnter;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TraceJSON;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
import org.ntlab.traceDebugger.analyzerProvider.DeltaExtractionAnalyzer;

public class DebuggingController {
	private static final DebuggingController theInstance = new DebuggingController();
	private TracePoint debuggingTp;
	private TraceBreakPoint selectedTraceBreakPoint;
	private TraceBreakPoints traceBreakPoints = new TraceBreakPoints();
	
	private DebuggingController() {
		
	}
	
	public static DebuggingController getInstance() {
		return theInstance;
	}
	
	public void setDebuggingTp(TracePoint tp) {
		this.debuggingTp = tp;
	}
	
	public void setSelectedTraceBreakPoint(TraceBreakPoint tbp) {
		this.selectedTraceBreakPoint = tbp;
	}
	
	public TracePoint getCurrentTp() {
		return debuggingTp.duplicate();
	}
	
	public boolean fileOpenAction(Shell shell) {
		FileDialog fileDialog = new FileDialog(shell, SWT.OPEN);
		fileDialog.setText("Open Trace File");
		fileDialog.setFilterExtensions(new String[]{"*.*"});
		String path = fileDialog.open();
		if (path == null) return false;
		TraceDebuggerPlugin.setAnalyzer(new DeltaExtractionAnalyzer(new TraceJSON(path)));
		traceBreakPoints.clear();
		((CallStackView)getOtherView(CallStackView.ID)).reset();
		((VariableView)getOtherView(VariableView.ID)).reset();
		((BreakPointView)getOtherView(BreakPointView.ID)).update(traceBreakPoints);
		return true;
	}
	
	public boolean addTraceBreakPointAction() {
		if (TraceDebuggerPlugin.getAnalyzer() == null) {
			MessageDialog.openInformation(null, "Error", "Trace file was not found");
			return false;
		}
//		InputDialog inputDialog = new InputDialog(null, "method signature dialog", "Input method signature", "void E.setC(C)", null);
//		InputDialog inputDialog = new InputDialog(null, "method signature dialog", "Input method signature", "void _arraySample.D.setC(_arraySample.C)", null);		
//		InputDialog inputDialog = new InputDialog(null, "method signature dialog", "Input method signature", "void Company.pay(Money,Person)", null);
//		InputDialog inputDialog = new InputDialog(null, "method signature dialog", "Input method signature", "void worstCase.P.setM(worstCase.M)", null);
//		InputDialog inputDialog = new InputDialog(null, "method signature dialog", "Input method signature", "public void org.jhotdraw.draw.DefaultDrawingView.addToSelection(org.jhotdraw.draw.Figure)", null);
//		InputDialog inputDialog = new InputDialog(null, "method signature dialog", "Input method signature", "protected void org.tigris.gef.base.SelectionManager.addFig(org.tigris.gef.presentation.Fig)", null);
//		InputDialog inputDialog = new InputDialog(null, "method signature dialog", "Input method signature", "public static org.tigris.gef.base.Selection org.tigris.gef.base.SelectionManager.makeSelectionFor(org.tigris.gef.presentation.Fig)", null);
		InputDialog inputDialog = new InputDialog(null, "method signature dialog", "Input method signature", "public void org.argouml.uml.diagram.ui.ActionRemoveFromDiagram.actionPerformed(java.awt.event.ActionEvent)", null);
		if (inputDialog.open() != InputDialog.OK) return false;
		String methodSignature = inputDialog.getValue();
		inputDialog = new InputDialog(null, "line No dialog", "Input line no", "", null);
		if (inputDialog.open() != InputDialog.OK) return false;
		int lineNo = Integer.parseInt(inputDialog.getValue());
		long currentTime = 0L;
		if (debuggingTp != null) {
			currentTime = debuggingTp.getStatement().getTimeStamp();
		}
		boolean isSuccess = traceBreakPoints.addTraceBreakPoint(methodSignature, lineNo, currentTime);
		if (!isSuccess) {
			MessageDialog.openInformation(null, "Error", "This trace point does not exist in the trace.");
			return false;
		}
		((BreakPointView)getOtherView(BreakPointView.ID)).update(traceBreakPoints);
		return true;
	}
	
	public boolean removeTraceBreakPointAction() {
		if (selectedTraceBreakPoint == null) return false;
		traceBreakPoints.removeTraceBreakPoint(selectedTraceBreakPoint);
		((BreakPointView)getOtherView(BreakPointView.ID)).update(traceBreakPoints);
		return true;
	}
	
	public boolean changeAvailableAction() {
		if (selectedTraceBreakPoint == null) return false;
		selectedTraceBreakPoint.changeAvailable();
		((BreakPointView)getOtherView(BreakPointView.ID)).update(traceBreakPoints);
		return true;
	}
	
	public boolean debugAction() {
		if (TraceDebuggerPlugin.getAnalyzer() == null) {
			MessageDialog.openInformation(null, "Error", "Trace file was not found");
			return false;
		}
		terminateAction();
		traceBreakPoints.reset();
		debuggingTp = traceBreakPoints.getFirstTracePoint();
		if (debuggingTp == null) return false;
		refresh(null, debuggingTp, false);
		return true;
	}

	public void terminateAction() {
		debuggingTp = null;
		((CallStackView)getOtherView(CallStackView.ID)).reset();
		((VariableView)getOtherView(VariableView.ID)).reset();
	}

	public boolean stepIntoAction() {
		if (debuggingTp == null) return false;
		TracePoint previousTp = debuggingTp;
		debuggingTp = debuggingTp.duplicate();
		debuggingTp.stepFull();
		if (debuggingTp.getStatement() instanceof BlockEnter) {
			debuggingTp.stepFull();
		}
		if (!debuggingTp.isValid()) {
			terminateAction();
			MessageDialog.openInformation(null, "Terminate", "This trace is terminated");
			return false;
		}
		refresh(null, debuggingTp, false);
		return true;
	}

	public boolean stepOverAction() {
		if (debuggingTp == null) return false;
		TracePoint previousTp = debuggingTp;
		debuggingTp = debuggingTp.duplicate();
		int currentLineNo = debuggingTp.getStatement().getLineNo();
		boolean isReturned = false;

		// ステップオーバーを用いて到達点を見つけておく
		TracePoint startTp = debuggingTp.duplicate();
		while (!(isReturned = !(debuggingTp.stepOver()))) {
			if (currentLineNo != debuggingTp.getStatement().getLineNo()) break;
			previousTp = debuggingTp.duplicate();
		}
		TracePoint goalTp = debuggingTp.duplicate();
		
		// ステップフルを用いて今度は呼び出し先にも潜りながら予め見つけておいた到達点まで進ませる (変数差分更新のため途中のアップデートを全て拾う)
		debuggingTp = startTp;
		do {
			Statement statement = debuggingTp.getStatement();
			if (statement.equals(goalTp.getStatement())) break;
			if (statement instanceof FieldUpdate || statement instanceof ArrayUpdate) {
				Variables.getInstance().addDifferentialUpdatePoint(debuggingTp);
			}
		} while (debuggingTp.stepFull());

		if (debuggingTp.getStatement() instanceof BlockEnter) {
			debuggingTp.stepFull();
		}
		if (!debuggingTp.isValid()) {
			terminateAction();
			MessageDialog.openInformation(null, "Terminate", "This trace is terminated");
			return false;
		}
		refresh(previousTp, debuggingTp, isReturned, true);
		return true;
	}
	
	public boolean stepReturnAction() {
		if (debuggingTp == null) return false;
		TracePoint previousTp = debuggingTp;
		debuggingTp = debuggingTp.duplicate();
		while (debuggingTp.stepOver());
		if (!debuggingTp.isValid()) {
			terminateAction();
			MessageDialog.openInformation(null, "Terminate", "This trace is terminated");
			return false;
		}
		refresh(previousTp, debuggingTp, true);
		return true;
	}
	
	public boolean stepNextAction() {
		if (debuggingTp == null) return false;
		TracePoint previousTp = debuggingTp;
		debuggingTp = debuggingTp.duplicate();
		TracePoint startTp = debuggingTp.duplicate();
		debuggingTp.stepNext();
		TracePoint goalTp = debuggingTp.duplicate();
		
		// ステップフルを用いて今度は呼び出し先にも潜りながら予め見つけておいた到達点まで進ませる (変数差分更新のため途中のアップデートを全て拾う)
		debuggingTp = startTp;
		do {
			Statement statement = debuggingTp.getStatement();
			if (statement.equals(goalTp.getStatement())) break;
			if (statement instanceof FieldUpdate || statement instanceof ArrayUpdate) {
				Variables.getInstance().addDifferentialUpdatePoint(debuggingTp);
			}
		} while (debuggingTp.stepFull());
		
		if (debuggingTp.getStatement() instanceof BlockEnter) {
			debuggingTp.stepFull();
		}
		if (!debuggingTp.isValid()) {
			terminateAction();
			MessageDialog.openInformation(null, "Terminate", "This trace is terminated");
			return false;
		}
		boolean isReturned = false;
		refresh(previousTp, debuggingTp, isReturned, true);
		return true;	
	}
	
	public boolean resumeAction() {
		if (debuggingTp == null) return false;
		long currentTime = debuggingTp.getStatement().getTimeStamp();
		TracePoint previousTp = debuggingTp;
		debuggingTp = traceBreakPoints.getNextTracePoint(currentTime);
		if (debuggingTp == null) {
			terminateAction();
			MessageDialog.openInformation(null, "Terminate", "This trace is terminated");
			return false;
		}
		refresh(null, debuggingTp, false);
		return true;
	}
	
	public boolean stepBackIntoAction() {
		if (debuggingTp == null) return false;
		TracePoint previousTp = debuggingTp;
		debuggingTp = debuggingTp.duplicate();
		debuggingTp.stepBackFull();
		if (!debuggingTp.isValid()) {
			terminateAction();
			MessageDialog.openInformation(null, "Terminate", "This trace is terminated");
			return false;
		}
		refresh(null, debuggingTp, true);
		return true;
	}
	
	public boolean stepBackOverAction() {
		if (debuggingTp == null) return false;
		TracePoint previousTp = debuggingTp;
		debuggingTp = debuggingTp.duplicate();
		int currentLineNo = debuggingTp.getStatement().getLineNo();
		boolean isReturned;
		while (!(isReturned = !debuggingTp.stepBackOver())) {
			if (currentLineNo != debuggingTp.getStatement().getLineNo()) break;
		}
		if (!debuggingTp.isValid()) {
			terminateAction();
			MessageDialog.openInformation(null, "Terminate", "This trace is terminated");
			return false;
		}
		refresh(null, debuggingTp, !isReturned);
		return true;
	}
	
	public boolean stepBackReturnAction() {
		if (debuggingTp == null) return false;
		TracePoint previousTp = debuggingTp;
		debuggingTp = debuggingTp.duplicate();
		while (debuggingTp.stepBackOver());
		if (!debuggingTp.isValid()) {
			terminateAction();
			MessageDialog.openInformation(null, "Terminate", "This trace is terminated");
			return false;
		}
		refresh(null, debuggingTp, false);
		return true;
	}
	
	public boolean backResumeAction() {
		if (debuggingTp == null) return false;
		long currentTime = debuggingTp.getStatement().getTimeStamp();
		TracePoint previousTp = debuggingTp;
		debuggingTp = traceBreakPoints.getPreviousTracePoint(currentTime);
		if (debuggingTp == null) {
			terminateAction();
			MessageDialog.openInformation(null, "Terminate", "This trace is terminated");
			return false;
		}
		refresh(null, debuggingTp, false);
		return true;
	}

	/**
	 * 現在のデバッグ位置を指定したトレースポイントに合わせる
	 * @return
	 */
	public boolean jumpToTheTracePoint(TracePoint tp, boolean isReturned) {
		if (tp == null) return false;
		TracePoint previousTp = debuggingTp;
		debuggingTp = tp.duplicate();
		refresh(null, debuggingTp, isReturned);
		return true;
	}
	
	private void refresh(TracePoint from, TracePoint to, boolean isReturned) {
		refresh(from, to, isReturned, false);
	}
	
	private void refresh(TracePoint from, TracePoint to, boolean isReturned, boolean canDifferentialUpdateVariables) {
		MethodExecution me = to.getMethodExecution();
		int lineNo = to.getStatement().getLineNo();
		JavaEditorOperator.openSrcFileOfMethodExecution(me, lineNo);		
		CallStackView callStackView = ((CallStackView)getOtherView(CallStackView.ID));
		callStackView.updateByTracePoint(to);
		VariableView variableView = ((VariableView)getOtherView(VariableView.ID));
		if (!isReturned && canDifferentialUpdateVariables) {
			variableView.updateVariablesForDifferential(from, to, isReturned);
		} else {
			variableView.updateVariablesByTracePoint(from, to, isReturned);
		}
	}

	private IViewPart getOtherView(String viewId) {
		IWorkbenchPage workbenchPage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		try {
			return workbenchPage.showView(viewId);
		} catch (PartInitException e) {
			throw new RuntimeException(e);
		}
	}
}

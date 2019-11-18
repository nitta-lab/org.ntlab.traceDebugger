package org.ntlab.traceDebugger;

import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TraceJSON;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
import org.ntlab.traceDebugger.analyzerProvider.DeltaExtractionAnalyzer;
import org.ntlab.traceDebugger.analyzerProvider.ReferencePoint;

public class DebuggingController {
	private TracePoint debuggingTp;
	private TraceBreakPoint selectedTraceBreakPoint;
	private TraceBreakPoints traceBreakPoints = new TraceBreakPoints();
	
	public void setDebuggingTp(TracePoint tp) {
		this.debuggingTp = tp;
	}
	
	public void setSelectedTraceBreakPoint(TraceBreakPoint tbp) {
		this.selectedTraceBreakPoint = tbp;
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
//		InputDialog inputDialog = new InputDialog(null, "method signature dialog", "Input method signature", "void Company.pay(Money,Person)", null);
//		InputDialog inputDialog = new InputDialog(null, "method signature dialog", "Input method signature", "void worstCase.P.setM(worstCase.M)", null);
		InputDialog inputDialog = new InputDialog(null, "method signature dialog", "Input method signature", "public void org.jhotdraw.draw.DefaultDrawingView.addToSelection(org.jhotdraw.draw.Figure)", null);
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
	
//	public boolean debugAction() {
//		if (TraceDebuggerPlugin.getAnalyzer() == null) {
//			MessageDialog.openInformation(null, "Error", "Trace file was not found");
//			return false;
//		}
//		terminateAction();		
//		traceBreakPoints.resetAll();
//		debuggingTp = traceBreakPoints.getNextTracePoint(0L);
//		if (debuggingTp == null) return false;
//		refresh(false);
//		return true;
//	}
	
	public boolean debugAction() {
		if (TraceDebuggerPlugin.getAnalyzer() == null) {
			MessageDialog.openInformation(null, "Error", "Trace file was not found");
			return false;
		}
		terminateAction();		
//		traceBreakPoints.resetAll();
		traceBreakPoints.reset();
//		debuggingTp = traceBreakPoints.getNextTracePoint(0L);
		debuggingTp = traceBreakPoints.getFirstTracePoint();
		if (debuggingTp == null) return false;
		refresh(false);
		return true;
	}

	public void terminateAction() {
		debuggingTp = null;
		((CallStackView)getOtherView(CallStackView.ID)).reset();
		((VariableView)getOtherView(VariableView.ID)).reset();
	}

	public boolean stepIntoAction() {
		if (debuggingTp == null) return false;
		debuggingTp = debuggingTp.duplicate();
		debuggingTp.stepFull();
		if (!debuggingTp.isValid()) {
			terminateAction();
			MessageDialog.openInformation(null, "Terminate", "This trace is terminated");
			return false;
		}
//		traceBreakPoints.forwardAll(debuggingTp.getStatement().getTimeStamp());
		refresh(false);
		return true;
	}
	
	public boolean stepOverAction() {
		if (debuggingTp == null) return false;
		debuggingTp = debuggingTp.duplicate();
		int currentLineNo = debuggingTp.getStatement().getLineNo();
		boolean isReturned;
		while (!(isReturned = !(debuggingTp.stepOver()))) {
			if (currentLineNo != debuggingTp.getStatement().getLineNo()) break;
		}
		if (!debuggingTp.isValid()) {
			terminateAction();
			MessageDialog.openInformation(null, "Terminate", "This trace is terminated");
			return false;
		}
//		traceBreakPoints.forwardAll(debuggingTp.getStatement().getTimeStamp());
		refresh(isReturned);
		return true;
	}
	
	public boolean stepReturnAction() {
		if (debuggingTp == null) return false;
		debuggingTp = debuggingTp.duplicate();
		while (debuggingTp.stepOver());
		if (!debuggingTp.isValid()) {
			terminateAction();
			MessageDialog.openInformation(null, "Terminate", "This trace is terminated");
			return false;
		}
//		traceBreakPoints.forwardAll(debuggingTp.getStatement().getTimeStamp());
		refresh(true);
		return true;
	}
	
	public boolean resumeAction() {
		if (debuggingTp == null) return false;
		long currentTime = debuggingTp.getStatement().getTimeStamp();
		debuggingTp = traceBreakPoints.getNextTracePoint(currentTime);
		if (debuggingTp == null) {
			terminateAction();
			MessageDialog.openInformation(null, "Terminate", "This trace is terminated");
			return false;
		}
//		traceBreakPoints.forwardAll(debuggingTp.getStatement().getTimeStamp());
		refresh(false);
		return true;
	}
	
	public boolean stepBackIntoAction() {
		if (debuggingTp == null) return false;
		debuggingTp = debuggingTp.duplicate();
		debuggingTp.stepBackFull();
		if (!debuggingTp.isValid()) {
			terminateAction();
			MessageDialog.openInformation(null, "Terminate", "This trace is terminated");
			return false;
		}
//		traceBreakPoints.reverseAll(debuggingTp.getStatement().getTimeStamp());
		refresh(true);
		return true;
	}
	
	public boolean stepBackOverAction() {
		if (debuggingTp == null) return false;
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
//		traceBreakPoints.reverseAll(debuggingTp.getStatement().getTimeStamp());
		refresh(!isReturned);
		return true;
	}
	
	public boolean stepBackReturnAction() {
		if (debuggingTp == null) return false;
		debuggingTp = debuggingTp.duplicate();
		while (debuggingTp.stepBackOver());
		if (!debuggingTp.isValid()) {
			terminateAction();
			MessageDialog.openInformation(null, "Terminate", "This trace is terminated");
			return false;
		}
//		traceBreakPoints.reverseAll(debuggingTp.getStatement().getTimeStamp());
		refresh(false);
		return true;
	}
	
	public boolean backResumeAction() {
		if (debuggingTp == null) return false;
		long currentTime = debuggingTp.getStatement().getTimeStamp();
		debuggingTp = traceBreakPoints.getPreviousTracePoint(currentTime);
		if (debuggingTp == null) {
			terminateAction();
			MessageDialog.openInformation(null, "Terminate", "This trace is terminated");
			return false;
		}
//		traceBreakPoints.reverseAll(debuggingTp.getStatement().getTimeStamp());
		refresh(false);
		return true;
	}
	
	/**
	 * ���݂̃f�o�b�O�ʒu�𒊏o�����f���^�̒�ӂ̃g���[�X�|�C���g�ɍ��킹�� (�Ƃ肠�����m�F���邾���p)
	 * @return
	 */
	public boolean tmp() {
		DeltaExtractionAnalyzer analyzer = (DeltaExtractionAnalyzer)TraceDebuggerPlugin.getAnalyzer();
		ReferencePoint rp = analyzer.getBottomPoint();
		long previousTime = debuggingTp.getStatement().getTimeStamp();
		long rpTime = rp.getTime();
		debuggingTp = rp.getTracePoint();
//		if (rpTime < previousTime) {
//			traceBreakPoints.reverseAll(rpTime);
//		} else {
//			traceBreakPoints.forwardAll(rpTime);
//		}
		refresh(false);
		return true;
	}
	
	private void refresh(boolean isReturned) {
		MethodExecution me = debuggingTp.getMethodExecution();
		int lineNo = debuggingTp.getStatement().getLineNo();
		JavaEditorOperator.openSrcFileOfMethodExecution(me, lineNo);		
		CallStackView callStackView = ((CallStackView)getOtherView(CallStackView.ID));
		callStackView.updateByTracePoint(debuggingTp);
		callStackView.refresh();
		((VariableView)getOtherView(VariableView.ID)).updateVariablesByTracePoint(debuggingTp, isReturned);
		((BreakPointView)getOtherView(BreakPointView.ID)).update(traceBreakPoints);
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
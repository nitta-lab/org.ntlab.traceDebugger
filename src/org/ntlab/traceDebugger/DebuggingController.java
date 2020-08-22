package org.ntlab.traceDebugger;

import java.util.HashMap;
import java.util.Map;
import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Shell;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TraceJSON;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
import org.ntlab.traceDebugger.analyzerProvider.DeltaExtractionAnalyzer;
import org.ntlab.traceDebugger.analyzerProvider.DeltaMarkerManager;
import org.ntlab.traceDebugger.analyzerProvider.VariableUpdatePointFinder;

public class DebuggingController {
	private static final DebuggingController theInstance = new DebuggingController();
	private TracePoint debuggingTp;
	private TraceBreakPoint selectedTraceBreakPoint;
	private IMarker currentLineMarker;
	private LoadingTraceFileStatus loadingTraceFileStatus = LoadingTraceFileStatus.NOT_YET;
	private boolean isRunning = false;
	public static final String CURRENT_MARKER_ID = "org.ntlab.traceDebugger.currentMarker";
	
	private enum LoadingTraceFileStatus {
		NOT_YET, PROGRESS, DONE
	}
	
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
	
	public boolean isRunning() {
		return isRunning;
	}
	
	public boolean fileOpenAction(Shell shell) {
		if (loadingTraceFileStatus == LoadingTraceFileStatus.PROGRESS) {
			MessageDialog.openInformation(null, "Loading", "This debugger is loading the trace.");
			return false;
		}
		if (isRunning) {
			MessageDialog.openInformation(null, "Running", "This debugger is running on the trace.");
			return false;
		}
		FileDialog fileDialog = new FileDialog(shell, SWT.OPEN);
		fileDialog.setText("Open Trace File");
		fileDialog.setFilterExtensions(new String[]{"*.*"});
		String path = fileDialog.open();
		if (path == null) return false;
		
		((CallStackView)TraceDebuggerPlugin.getActiveView(CallStackView.ID)).reset();
		((VariableView)TraceDebuggerPlugin.getActiveView(VariableView.ID)).reset();
		((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).reset();
		((TracePointsView)TraceDebuggerPlugin.getActiveView(TracePointsView.ID)).reset();
		CallTreeView callTreeView = (CallTreeView)TraceDebuggerPlugin.getActiveView(CallTreeView.ID);
		if (callTreeView != null) callTreeView.reset();
		loadTraceFileOnOtherThread(path);
		return true;
	}
	
	private void loadTraceFileOnOtherThread(final String filePath) {
		Job job = new Job("Loading Trace File") {
			@Override
			protected IStatus run(IProgressMonitor monitor) {							
				monitor.beginTask("Loading Trace File" + " (" + filePath + ")", IProgressMonitor.UNKNOWN);
				loadingTraceFileStatus = LoadingTraceFileStatus.PROGRESS;
				TraceDebuggerPlugin.setAnalyzer(null);
				TraceJSON trace = new TraceJSON(filePath);
				TraceDebuggerPlugin.setAnalyzer(new DeltaExtractionAnalyzer(trace));
				VariableUpdatePointFinder.getInstance().setTrace(trace);
				final TraceBreakPoints traceBreakPoints = new TraceBreakPoints(trace);

				// GUIの操作はGUIのイベントディスパッチを行っているスレッドからしか操作できないのでそうする
				final BreakPointView breakpointView = (BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID);
				Control control = breakpointView.getViewer().getControl();
				control.getDisplay().syncExec(new Runnable() {
					@Override
					public void run() {
						breakpointView.updateTraceBreakPoints(traceBreakPoints);
						breakpointView.updateImagesForBreakPoint(true);
					}
				});
				monitor.done();
				if (!(monitor.isCanceled())) {
					loadingTraceFileStatus = LoadingTraceFileStatus.DONE;
					return Status.OK_STATUS;
				} else {
					loadingTraceFileStatus = LoadingTraceFileStatus.NOT_YET;
					return Status.CANCEL_STATUS;
				}
			}
		};
		job.setUser(true);
		job.schedule();
	}
	
	public boolean addTraceBreakPointAction() {
		if (loadingTraceFileStatus != LoadingTraceFileStatus.DONE) {
			MessageDialog.openInformation(null, "Error", "Trace file was not found");
			return false;
		}
		InputDialog inputDialog = new InputDialog(null, "method signature dialog", "Input method signature", "", null);
		if (inputDialog.open() != InputDialog.OK) return false;
		String methodSignature = inputDialog.getValue();
		inputDialog = new InputDialog(null, "line No dialog", "Input line no", "", null);
		if (inputDialog.open() != InputDialog.OK) return false;
		int lineNo = Integer.parseInt(inputDialog.getValue());
		TraceBreakPoints traceBreakPoints = ((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).getTraceBreakPoints();
		boolean isSuccess = traceBreakPoints.addTraceBreakPoint(methodSignature, lineNo);
		if (!isSuccess) {
			MessageDialog.openInformation(null, "Error", "This trace point does not exist in the trace.");
			return false;
		}
		((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).updateTraceBreakPoints(traceBreakPoints);
		return true;
	}

	public boolean importBreakpointAction() {
		if (loadingTraceFileStatus != LoadingTraceFileStatus.DONE) {
			MessageDialog.openInformation(null, "Error", "Trace file was not found");
			return false;
		}
		TraceBreakPoints traceBreakPoints = ((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).getTraceBreakPoints();
		traceBreakPoints.importBreakpointFromEclipse();
		((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).updateTraceBreakPoints(traceBreakPoints);
		return true;
	}
	
	public boolean removeTraceBreakPointAction() {
		if (selectedTraceBreakPoint == null) return false;
		TraceBreakPoints traceBreakPoints = ((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).getTraceBreakPoints();
		traceBreakPoints.removeTraceBreakPoint(selectedTraceBreakPoint);
		((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).updateTraceBreakPoints(traceBreakPoints);
		return true;
	}
	
	public boolean debugAction() {
		if (loadingTraceFileStatus != LoadingTraceFileStatus.DONE) {
			MessageDialog.openInformation(null, "Error", "Trace file was not found");
			return false;
		}
		if (isRunning) {
			MessageDialog.openInformation(null, "Error", "This Debugger is running");			
			return false;
		}
		TraceBreakPoints traceBreakPoints = ((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).getTraceBreakPoints();
		debuggingTp = traceBreakPoints.getFirstTracePoint();
		if (debuggingTp == null) {
			MessageDialog.openInformation(null, "Error", "An available breakpoint was not found");
			return false;
		}
		refresh(null, debuggingTp, false);
		((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).updateImagesForDebug(true);
		isRunning = true;
		return true;
	}

	public void terminateAction() {
		debuggingTp = null;
		if (currentLineMarker != null) {
			try {
				currentLineMarker.delete();
			} catch (CoreException e) {
				e.printStackTrace();
			}			
		}
		((CallStackView)TraceDebuggerPlugin.getActiveView(CallStackView.ID)).reset();
		((VariableView)TraceDebuggerPlugin.getActiveView(VariableView.ID)).reset();
		((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).updateImagesForDebug(false);
		isRunning = false;
	}

	public boolean stepIntoAction() {
		if (!isRunning) return false;
		if (debuggingTp == null) return false;
		TracePoint callStackTp = getTracePointSelectedOnCallStack();
		if (callStackTp != null && !(callStackTp.equals(debuggingTp))) {
			debuggingTp = callStackTp;
		}
		TracePoint previousTp = debuggingTp;
		debuggingTp = debuggingTp.duplicate();
		debuggingTp.stepFull();
		if (!debuggingTp.isValid()) {
			terminateAction();
			MessageDialog.openInformation(null, "Terminate", "This trace is terminated");
			return false;
		}
		refresh(null, debuggingTp, false);
		return true;
	}
	
	public boolean stepOverAction() {
		if (!isRunning) return false;
		if (debuggingTp == null) return false;
		TracePoint callStackTp = getTracePointSelectedOnCallStack();
		if (callStackTp != null && !(callStackTp.equals(debuggingTp))) {
			debuggingTp = callStackTp;
		}
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
		if (!isReturned) {
			// ステップフルを用いて今度は呼び出し先にも潜りながら予め見つけておいた到達点まで進ませる (変数差分更新のため途中のアップデートを全て拾う)
			debuggingTp = startTp;
			do {
				Statement statement = debuggingTp.getStatement();
				if (statement.equals(goalTp.getStatement())) break;
				if (statement instanceof FieldUpdate || statement instanceof ArrayUpdate) {
					Variables.getInstance().addDifferentialUpdatePoint(debuggingTp);
				}
			} while (debuggingTp.stepFull());			
		} else {
			debuggingTp = goalTp;
			while (!debuggingTp.stepOver()); // 呼び出し元での次のステートメントまで進める
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
		if (!isRunning) return false;
		if (debuggingTp == null) return false;
		TracePoint callStackTp = getTracePointSelectedOnCallStack();
		if (callStackTp != null && !(callStackTp.equals(debuggingTp))) {
			debuggingTp = callStackTp;
		}
		TracePoint previousTp = debuggingTp;
		debuggingTp = debuggingTp.duplicate();
		
		// note: 呼び出し元に戻るまで進み続ける
		while (debuggingTp.stepOver()) {
			previousTp = debuggingTp.duplicate();
		}
		while (!debuggingTp.stepOver()); // 呼び出し元での次のステートメントまで進める
		if (!debuggingTp.isValid()) {
			terminateAction();
			MessageDialog.openInformation(null, "Terminate", "This trace is terminated");
			return false;
		}
		refresh(previousTp, debuggingTp, true);
		return true;
	}
	
	public boolean stepNextAction() {
		if (!isRunning) return false;
		if (debuggingTp == null) return false;
		TracePoint callStackTp = getTracePointSelectedOnCallStack();
		if (callStackTp != null && !(callStackTp.equals(debuggingTp))) {
			debuggingTp = callStackTp;
		}
		TracePoint previousTp = debuggingTp;
		debuggingTp = debuggingTp.duplicate();
		TracePoint startTp = debuggingTp.duplicate();
		boolean isReturned = !(debuggingTp.stepNext());
		TracePoint goalTp = debuggingTp.duplicate();
		if (!isReturned) {
			// ステップフルを用いて今度は呼び出し先にも潜りながら予め見つけておいた到達点まで進ませる (変数差分更新のため途中のアップデートを全て拾う)
			debuggingTp = startTp;
			do {
				Statement statement = debuggingTp.getStatement();
				if (statement.equals(goalTp.getStatement())) break;
				if (statement instanceof FieldUpdate || statement instanceof ArrayUpdate) {
					Variables.getInstance().addDifferentialUpdatePoint(debuggingTp);
				}
			} while (debuggingTp.stepFull());			
		} else {
			debuggingTp = startTp;
			while (!debuggingTp.stepOver()); // 呼び出し元での次のステートメントまで進める
		}

		if (!debuggingTp.isValid()) {
			terminateAction();
			MessageDialog.openInformation(null, "Terminate", "This trace is terminated");
			return false;
		}
		refresh(previousTp, debuggingTp, isReturned, true);
		return true;	
	}
	
	public boolean resumeAction() {
		if (!isRunning) return false;
		if (debuggingTp == null) return false;
		long currentTime = debuggingTp.getStatement().getTimeStamp();
		TracePoint previousTp = debuggingTp;
		TraceBreakPoints traceBreakPoints = ((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).getTraceBreakPoints();
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
		if (!isRunning) return false;
		if (debuggingTp == null) return false;
		TracePoint callStackTp = getTracePointSelectedOnCallStack();
		if (callStackTp != null && !(callStackTp.equals(debuggingTp))) {
			debuggingTp = callStackTp;
		}
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
		if (!isRunning) return false;
		if (debuggingTp == null) return false;
		TracePoint callStackTp = getTracePointSelectedOnCallStack();
		if (callStackTp != null && !(callStackTp.equals(debuggingTp))) {
			debuggingTp = callStackTp;
		}
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
		if (!isRunning) return false;
		if (debuggingTp == null) return false;
		TracePoint callStackTp = getTracePointSelectedOnCallStack();
		if (callStackTp != null && !(callStackTp.equals(debuggingTp))) {
			debuggingTp = callStackTp;
		}
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
		if (!isRunning) return false;
		if (debuggingTp == null) return false;		
		TracePoint previousTp = debuggingTp;
		long currentTime = debuggingTp.getStatement().getTimeStamp();
		TraceBreakPoints traceBreakPoints = ((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).getTraceBreakPoints();
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
		if (!isRunning) return false;
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
		IMarker marker = createCurrentLineMarker(me, lineNo);
		JavaEditorOperator.markAndOpenJavaFile(marker);
		CallStackView callStackView = ((CallStackView)TraceDebuggerPlugin.getActiveView(CallStackView.ID));
		callStackView.updateByTracePoint(to);
		VariableView variableView = ((VariableView)TraceDebuggerPlugin.getActiveView(VariableView.ID));
		if (!isReturned && canDifferentialUpdateVariables) {
//			variableView.updateVariablesByTracePoint(from, to, isReturned);
			variableView.updateVariablesForDifferential(from, to, isReturned);
		} else {
			variableView.updateVariablesByTracePoint(from, to, isReturned);
		}
		if (TraceDebuggerPlugin.getActiveView(DeltaMarkerView.ID) != null) {
			refreshRelatedDelta(to);
		}
	}
	
	private void refreshRelatedDelta(TracePoint tp) {
		DeltaMarkerView deltaMarkerView = (DeltaMarkerView)TraceDebuggerPlugin.getActiveView(DeltaMarkerView.ID);
		if (deltaMarkerView == null) return;
		DeltaMarkerManager deltaMarkerManager = deltaMarkerView.getDeltaMarkerManager();
		if (deltaMarkerManager == null) return;
		IMarker coordinatorMarker = deltaMarkerManager.getCoordinatorDeltaMarker();
		if (coordinatorMarker == null) return;
		MethodExecution coordinatorME = DeltaMarkerManager.getMethodExecution(coordinatorMarker);
		CallStackView callStackView = (CallStackView)TraceDebuggerPlugin.getActiveView(CallStackView.ID);
		callStackView.highlight(coordinatorME);
		CallTreeView callTreeView = (CallTreeView)TraceDebuggerPlugin.getActiveView(CallTreeView.ID);
		callTreeView.highlight(tp.getMethodExecution());
		VariableViewRelatedDelta variableView = (VariableViewRelatedDelta)TraceDebuggerPlugin.getActiveView(VariableViewRelatedDelta.ID);
		variableView.markAndExpandVariablesByDeltaMarkers(deltaMarkerManager.getMarkers());
	}

	public IMarker createCurrentLineMarker(MethodExecution methodExecution, int highlightLineNo) {
		IFile file = JavaElementFinder.findIFile(methodExecution);
		try {
			if (currentLineMarker != null) currentLineMarker.delete();
			currentLineMarker = file.createMarker(CURRENT_MARKER_ID);
			Map<String, Object> attributes = new HashMap<>();
			attributes.put(IMarker.TRANSIENT, true);
			attributes.put(IMarker.LINE_NUMBER, highlightLineNo);			
			
			IPath path = file.getFullPath();
			ITextFileBufferManager manager = FileBuffers.getTextFileBufferManager();			
			manager.connect(path, LocationKind.IFILE, null);
			ITextFileBuffer buffer = manager.getTextFileBuffer(path, LocationKind.IFILE);
			IDocument document = buffer.getDocument();
			try {
				IRegion region = document.getLineInformation(highlightLineNo - 1);
				attributes.put(IMarker.CHAR_START, region.getOffset());
				attributes.put(IMarker.CHAR_END, region.getOffset() + region.getLength());
			} catch (BadLocationException e) {
				e.printStackTrace();
			}
			currentLineMarker.setAttributes(attributes);
		} catch (CoreException e) {
			e.printStackTrace();
		}
		return currentLineMarker;
	}
	
	private TracePoint getTracePointSelectedOnCallStack() {
		CallStackView callStackView = (CallStackView)TraceDebuggerPlugin.getActiveView(CallStackView.ID);
		CallStackModel callStackModel = callStackView.getSelectionCallStackModel();
		if (callStackModel != null) {
			return callStackModel.getTracePoint();
		}
		return null;
	}
}

package org.ntlab.traceDebugger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
	private TraceBreakPoints traceBreakPoints;
	private List<IMarker> currentLineMarkers = new ArrayList<>();
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
	
	public boolean hasLoadedTraceFileStatus() {
		return (loadingTraceFileStatus == LoadingTraceFileStatus.DONE);
	}
	
	public boolean isRunning() {
		return isRunning;
	}
	
	public boolean fileOpenAction(Shell shell) {
		if (loadingTraceFileStatus == LoadingTraceFileStatus.PROGRESS) {
			if (TraceDebuggerPlugin.isJapanese()) {
				MessageDialog.openInformation(null, "読み込み中", "トレースファイルを読み込み中です");
			} else {
				MessageDialog.openInformation(null, "Loading", "This debugger is loading the trace.");	
			}
			return false;
		}
		if (isRunning) {
			if (TraceDebuggerPlugin.isJapanese()) {
				MessageDialog.openInformation(null, "実行中", "トレース上で実行中です");
			} else {
				MessageDialog.openInformation(null, "Running", "This debugger is running on the trace.");	
			}
			return false;
		}
		FileDialog fileDialog = new FileDialog(shell, SWT.OPEN);
		fileDialog.setText(TraceDebuggerPlugin.isJapanese() ? "トレースファイルを開く" : "Open Trace File");
		fileDialog.setFilterExtensions(new String[]{"*.*"});
		String path = fileDialog.open();
		if (path == null) return false;
		
		((CallStackView)TraceDebuggerPlugin.getActiveView(CallStackView.ID)).reset();
		((VariableView)TraceDebuggerPlugin.getActiveView(VariableView.ID)).reset();
		((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).reset();
		TracePointsView tracePointsView = (TracePointsView)TraceDebuggerPlugin.getActiveView(TracePointsView.ID);
		if (tracePointsView != null) tracePointsView.reset();
		CallTreeView callTreeView = (CallTreeView)TraceDebuggerPlugin.getActiveView(CallTreeView.ID);
		if (callTreeView != null) callTreeView.reset();
		loadTraceFileOnOtherThread(path);
		return true;
	}
	
	private void loadTraceFileOnOtherThread(final String filePath) {
		final String msg = TraceDebuggerPlugin.isJapanese() ? "トレースファイルを読み込み中" : "Loading Trace File";
		Job job = new Job(msg) {
			@Override
			protected IStatus run(IProgressMonitor monitor) {
				monitor.beginTask(msg + " (" + filePath + ")", IProgressMonitor.UNKNOWN);
				loadingTraceFileStatus = LoadingTraceFileStatus.PROGRESS;
				TraceDebuggerPlugin.setAnalyzer(null);
				TraceJSON trace = new TraceJSON(filePath);
				TraceDebuggerPlugin.setAnalyzer(new DeltaExtractionAnalyzer(trace));
				VariableUpdatePointFinder.getInstance().setTrace(trace);
//				final TraceBreakPoints traceBreakPoints = new TraceBreakPoints(trace);
				traceBreakPoints = new TraceBreakPoints(trace);

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
			if (TraceDebuggerPlugin.isJapanese()) {
				MessageDialog.openInformation(null, "エラー", "トレースが見つかりませんでした");
			} else {
				MessageDialog.openInformation(null, "Error", "Trace was not found");	
			}
			return false;
		}
		InputDialog inputDialog = TraceDebuggerPlugin.isJapanese() ? new InputDialog(null, "メソッドダイアログ", "メソッドシグニチャを入力", "", null)
																	: new InputDialog(null, "method signature dialog", "Input method signature", "", null);
		if (inputDialog.open() != InputDialog.OK) return false;
		String methodSignature = inputDialog.getValue();
		inputDialog = TraceDebuggerPlugin.isJapanese() ? new InputDialog(null, "行数ダイアログ", "行数を入力", "", null)
														: new InputDialog(null, "line Number dialog", "Input line number", "", null);
		if (inputDialog.open() != InputDialog.OK) return false;
		int lineNo = Integer.parseInt(inputDialog.getValue());
//		TraceBreakPoints traceBreakPoints = ((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).getTraceBreakPoints();
		boolean isSuccess = traceBreakPoints.addTraceBreakPoint(methodSignature, lineNo);
		if (!isSuccess) {
			if (TraceDebuggerPlugin.isJapanese()) {
				MessageDialog.openInformation(null, "エラー", "トレース中に入力したポイントが存在しません");
			} else {
				MessageDialog.openInformation(null, "Error", "This point does not exist in the trace.");	
			}
			return false;
		}
		((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).updateTraceBreakPoints(traceBreakPoints);
		return true;
	}

	public boolean importBreakpointAction() {
		if (loadingTraceFileStatus != LoadingTraceFileStatus.DONE) {
			if (TraceDebuggerPlugin.isJapanese()) {
				MessageDialog.openInformation(null, "エラー", "トレースが見つかりません");
			} else {
				MessageDialog.openInformation(null, "Error", "Trace was not found");	
			}
			return false;
		}
//		TraceBreakPoints traceBreakPoints = ((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).getTraceBreakPoints();
		traceBreakPoints.importBreakpointFromEclipse();
		((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).updateTraceBreakPoints(traceBreakPoints);
		return true;
	}
	
	public boolean removeTraceBreakPointAction() {
		if (selectedTraceBreakPoint == null) return false;
//		TraceBreakPoints traceBreakPoints = ((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).getTraceBreakPoints();
		traceBreakPoints.removeTraceBreakPoint(selectedTraceBreakPoint);
		((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).updateTraceBreakPoints(traceBreakPoints);
		return true;
	}
	
	public boolean debugAction() {
		if (loadingTraceFileStatus != LoadingTraceFileStatus.DONE) {
			if (TraceDebuggerPlugin.isJapanese()) {
				MessageDialog.openInformation(null, "エラー", "トレースが見つかりません");
			} else {
				MessageDialog.openInformation(null, "Error", "Trace was not found");				
			}
			return false;
		}
		if (isRunning) {
			if (TraceDebuggerPlugin.isJapanese()) {
				MessageDialog.openInformation(null, "エラー", "トレース上で実行中です");
			} else {
				MessageDialog.openInformation(null, "Error", "This Debugger is running on the trace.");	
			}
			return false;
		}
//		TraceBreakPoints traceBreakPoints = ((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).getTraceBreakPoints();
		debuggingTp = traceBreakPoints.getFirstTracePoint();
		if (debuggingTp == null) {
			if (TraceDebuggerPlugin.isJapanese()) {
				MessageDialog.openInformation(null, "エラー", "利用可能なブレークポイントが見つかりません");
			} else {
				MessageDialog.openInformation(null, "Error", "An available breakpoint was not found");	
			}
			return false;
		}
		refresh(null, debuggingTp, false);
		((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).updateImagesForDebug(true);
		isRunning = true;
		return true;
	}

//	public void terminateAction() {
//		debuggingTp = null;
//		if (currentLineMarker != null) {
//			try {
//				currentLineMarker.delete();
//			} catch (CoreException e) {
//				e.printStackTrace();
//			}			
//		}
//		((CallStackView)TraceDebuggerPlugin.getActiveView(CallStackView.ID)).reset();
//		((VariableView)TraceDebuggerPlugin.getActiveView(VariableView.ID)).reset();
//		((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).updateImagesForDebug(false);
//		isRunning = false;
//	}
	
	public void terminateAction() {
		debuggingTp = null;
		if (!(currentLineMarkers.isEmpty())) {
			for (IMarker currentLineMarker : currentLineMarkers) {
				try {
					currentLineMarker.delete();
				} catch (CoreException e) {
					e.printStackTrace();
				}				
			}
		}
		CallStackView callStackView = (CallStackView)TraceDebuggerPlugin.getActiveView(CallStackView.ID);
		if (callStackView != null) callStackView.reset();
		VariableView variableView = (VariableView)TraceDebuggerPlugin.getActiveView(VariableView.ID);
		if (variableView != null) variableView.reset();
		BreakPointView breakPointView = (BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID);
		if (breakPointView != null) breakPointView.updateImagesForDebug(false);		
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
		if (debugExecutionIsTerminated(debuggingTp)) return false;
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

		if (debugExecutionIsTerminated(debuggingTp)) return false;
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
		
		if (debugExecutionIsTerminated(debuggingTp)) return false;
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

		if (debugExecutionIsTerminated(debuggingTp)) return false;
		refresh(previousTp, debuggingTp, isReturned, true);
		return true;	
	}
	
	public boolean resumeAction() {
		if (!isRunning) return false;
		if (debuggingTp == null) return false;
		long currentTime = debuggingTp.getStatement().getTimeStamp();
		TracePoint previousTp = debuggingTp;
//		TraceBreakPoints traceBreakPoints = ((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).getTraceBreakPoints();
		debuggingTp = traceBreakPoints.getNextTracePoint(currentTime);
		if (debugExecutionIsTerminated(debuggingTp)) return false;
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
		if (debugExecutionIsTerminated(debuggingTp)) return false;
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
		if (debugExecutionIsTerminated(debuggingTp)) return false;
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
		if (debugExecutionIsTerminated(debuggingTp)) return false;
		refresh(null, debuggingTp, false);
		return true;
	}
	
	public boolean backResumeAction() {
		if (!isRunning) return false;
		if (debuggingTp == null) return false;		
		TracePoint previousTp = debuggingTp;
		long currentTime = debuggingTp.getStatement().getTimeStamp();
//		TraceBreakPoints traceBreakPoints = ((BreakPointView)TraceDebuggerPlugin.getActiveView(BreakPointView.ID)).getTraceBreakPoints();
		debuggingTp = traceBreakPoints.getPreviousTracePoint(currentTime);
		if (debugExecutionIsTerminated(debuggingTp)) return false;
		refresh(null, debuggingTp, false);
		return true;
	}
	
	private boolean debugExecutionIsTerminated(TracePoint tp) {
		if (tp == null || !(tp.isValid())) {
			terminateAction();
			if (TraceDebuggerPlugin.isJapanese()) {
				MessageDialog.openInformation(null, "終了", "トレース上での実行は終了しました");
			} else {
				MessageDialog.openInformation(null, "Terminate", "This execution is terminated");	
			}
			return true;
		}
		return false;
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
		List<IMarker> markers = createCurrentLineMarkers(to);
		if (!(markers.isEmpty())) JavaEditorOperator.markAndOpenJavaFile(markers.get(0));

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

	public List<IMarker> createCurrentLineMarkers(TracePoint tp) {
		deleteCurrentLineMarkers();
		while (tp != null) {
			try {
				MethodExecution methodExecution = tp.getMethodExecution();
				int highlightLineNo = tp.getStatement().getLineNo();
				IFile file = JavaElementFinder.findIFile(methodExecution);
				IMarker currentLineMarker = file.createMarker(CURRENT_MARKER_ID);
				currentLineMarkers.add(currentLineMarker);
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
			tp = tp.getMethodExecution().getCallerTracePoint();
		}
		return currentLineMarkers;
	}
	
	private void deleteCurrentLineMarkers() {
		for (IMarker currentLineMarker : currentLineMarkers) {
			try {
				currentLineMarker.delete();
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
		currentLineMarkers.clear();
	}
	
	private TracePoint getTracePointSelectedOnCallStack() {
		CallStackView callStackView = (CallStackView)TraceDebuggerPlugin.getActiveView(CallStackView.ID);
		CallStackModel callStackModel = callStackView.getSelectionCallStackModel();
		if (callStackModel != null) {
			return callStackModel.getTracePoint();
		}
		return null;
	}
	
	public void resetExcludingForLoadingStatusOfTheTrace() {
		terminateAction();
		selectedTraceBreakPoint = null;
	}
}

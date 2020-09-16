package org.ntlab.traceDebugger;

import java.util.ArrayList;

import org.eclipse.debug.internal.ui.IInternalDebugUIConstants;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;

public class BreakPointView extends ViewPart {
	protected TableViewer viewer;
	protected IAction fileOpenAction;
	protected IAction addTraceBreakPointAction;
	protected IAction removeTraceBreakPointAction;
	protected IAction debugAction;
	protected IAction terminateAction;
	protected IAction stepIntoAction;
	protected IAction stepOverAction;
	protected IAction stepReturnAction;
	protected IAction stepNextAction;
	protected IAction resumeAction;
	protected IAction importBreakpointAction;
	protected Shell shell;
//	protected TraceBreakPoints traceBreakPoints;
	protected DebuggingController debuggingController = DebuggingController.getInstance();
	public static final String ID = "org.ntlab.traceDebugger.breakPointView";
	public static final String DEBUG_ELCL = "Debug_elcl";
	public static final String DEBUG_DLCL = "Debug_dlcl";
	public static final String IMPORT_BREAKPOINT_ELCL = "ImportBreakPoint_ELCL";
	public static final String IMPORT_BREAKPOINT_DLCL = "ImportBreakPoint_DLCL";
	public static final String STEP_NEXT_ELCL = "StepNext_ELCL";
	public static final String STEP_NEXT_DLCL = "StepNext_DLCL";

	public BreakPointView() {
		// TODO Auto-generated constructor stub
		System.out.println("BreakPointViewクラスが生成されたよ!");
	}

	@Override
	public void createPartControl(Composite parent) {
		// TODO Auto-generated method stub
		System.out.println("BreakPointView#createPartControl(Composite)が呼ばれたよ!");
		shell = parent.getShell();
		viewer = CheckboxTableViewer.newCheckList(parent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER | SWT.FULL_SELECTION);
		final Table table = viewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);

		// テーブルのカラムを作成
		String[] tableColumnTexts = TraceDebuggerPlugin.isJapanese() ? new String[]{"", "行", "メソッド"} 
																		: new String[]{"", "Line", "Signature"};
		int[] tableColumnWidth = {50, 80, 500};
		TableColumn[] tableColumns = new TableColumn[tableColumnTexts.length];
		for (int i = 0; i < tableColumns.length; i++) {
			tableColumns[i] = new TableColumn(table, SWT.NULL);
			tableColumns[i].setText(tableColumnTexts[i]);
			tableColumns[i].setWidth(tableColumnWidth[i]);
		}
		viewer.setContentProvider(new ArrayContentProvider());
		viewer.setLabelProvider(new BreakPointLabelProvider());
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection sel = (IStructuredSelection)event.getSelection();
				Object element = sel.getFirstElement();
				if (element instanceof TraceBreakPoint) {
					TraceBreakPoint tbp = (TraceBreakPoint)element;
					debuggingController.setSelectedTraceBreakPoint(tbp);
					
					// 選択したTraceBreakPointの場所を開いて反転表示する (した方がいい?)
					MethodExecution methodExecution = tbp.getMethodExecutions().iterator().next();
					int highlightLineNo = tbp.getLineNo();
					JavaEditorOperator.openSrcFileOfMethodExecution(methodExecution, highlightLineNo);
				}
			}
		});
		
		table.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDown(MouseEvent e) {
				Point point = new Point(e.x, e.y);
				TableItem item = table.getItem(point);
				if (item == null) return;
				boolean checked = item.getChecked();
				Object data = item.getData();
				if (data instanceof TraceBreakPoint) {
					TraceBreakPoint tbp = (TraceBreakPoint)data;
					tbp.setAvailable(checked);
					viewer.refresh();
				}
			}
		});

		createActions();
		createToolBar();
		createMenuBar();
		createPopupMenu();
		updateImagesForBreakPoint(DebuggingController.getInstance().hasLoadedTraceFileStatus());
		TraceDebuggerPlugin.setActiveView(ID, this);
	}

	@Override
	public String getTitle() {
		return TraceDebuggerPlugin.isJapanese() ? "ブレークポイント" : "Breakpoints";
	}
	
	public Viewer getViewer() {
		return viewer;
	}
	
	@Override
	public void setFocus() {
		// TODO Auto-generated method stub
		TraceDebuggerPlugin.setActiveView(ID, this);
		viewer.getControl().setFocus();
	}
	
	@Override
	public void dispose() {
		DebuggingController.getInstance().resetExcludingForLoadingStatusOfTheTrace();
		TraceDebuggerPlugin.removeView(ID, this);
	}
	
	protected void createActions() {
		ImageRegistry registry = TraceDebuggerPlugin.getDefault().getImageRegistry();
		ImageDescriptor fileOpenIcon = PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_OBJ_FOLDER);
		boolean isJapanese = TraceDebuggerPlugin.isJapanese();
		String msg;
		
		msg = (isJapanese) ? "トレースファイルを開く" : "Open Trace File..."; 
		fileOpenAction = new Action(msg, fileOpenIcon) {
			@Override
			public void run() {
				// トレース出力先参照ウィザード
				debuggingController.fileOpenAction(shell);
			}
		};

		addTraceBreakPointAction = new Action() {
			@Override
			public void run() {
				debuggingController.addTraceBreakPointAction();
			}
		};
		msg = (isJapanese) ? "ブレークポイントを追加する" : "Add a New Breakpoint";
		addTraceBreakPointAction.setText(msg);
		addTraceBreakPointAction.setToolTipText(msg);
		
		removeTraceBreakPointAction = new Action() {
			@Override
			public void run() {
				debuggingController.removeTraceBreakPointAction();
			}
		};
		msg = (isJapanese) ? "ブレークポイントを削除する" : "Remove a Breakpoint";
		removeTraceBreakPointAction.setText(msg);
		removeTraceBreakPointAction.setToolTipText(msg);
		
		importBreakpointAction = new Action() {
			@Override
			public void run() {
				debuggingController.importBreakpointAction();
			}
		};
		msg = (isJapanese) ? "ブレークポイントを Eclipse から取り入れる" : "Import Breakpoints from Eclipse";
		importBreakpointAction.setText(msg);
		importBreakpointAction.setToolTipText(msg);
		ImageDescriptor importBreakpointIcon = registry.getDescriptor(IMPORT_BREAKPOINT_DLCL);
		importBreakpointAction.setImageDescriptor(importBreakpointIcon);
		
		debugAction = new Action() {
			@Override
			public void run() {
				debuggingController.debugAction();
			}
		};
		msg = (isJapanese) ? "デバッグ" : "Debug";
		debugAction.setText(msg);
		debugAction.setToolTipText(msg);
		ImageDescriptor debugIcon = registry.getDescriptor(DEBUG_DLCL);
		debugAction.setImageDescriptor(debugIcon);

		terminateAction = new Action() {
			@Override
			public void run() {
				debuggingController.terminateAction();
			}
		};
		msg = (isJapanese) ? "終了" : "Terminate";
		terminateAction.setText(msg);
		terminateAction.setToolTipText(msg);
		ImageDescriptor terminateImage = DebugUITools.getImageDescriptor(IInternalDebugUIConstants.IMG_DLCL_TERMINATE);
		terminateAction.setImageDescriptor(terminateImage);

		stepIntoAction = new Action() {
			@Override
			public void run() {
				debuggingController.stepIntoAction();
			}
		};
		msg = (isJapanese) ? "ステップイン" : "Step Into";
		stepIntoAction.setText(msg);
		stepIntoAction.setToolTipText(msg);
		ImageDescriptor stepIntoImage = DebugUITools.getImageDescriptor(IInternalDebugUIConstants.IMG_DLCL_STEP_INTO);
		stepIntoAction.setImageDescriptor(stepIntoImage);
		
		stepOverAction = new Action() {
			@Override
			public void run() {
				debuggingController.stepOverAction();
			}
		};
		msg = (isJapanese) ? "ステップオーバー" : "Step Over";
		stepOverAction.setText(msg);
		stepOverAction.setToolTipText(msg);
		ImageDescriptor stepOverImage = DebugUITools.getImageDescriptor(IInternalDebugUIConstants.IMG_DLCL_STEP_OVER);
		stepOverAction.setImageDescriptor(stepOverImage);

		stepReturnAction = new Action() {
			@Override
			public void run() {
				debuggingController.stepReturnAction();
			}
		};
		msg = (isJapanese) ? "ステップリターン" : "Step Return";
		stepReturnAction.setText(msg);
		stepReturnAction.setToolTipText(msg);
		ImageDescriptor stepReturnImage = DebugUITools.getImageDescriptor(IInternalDebugUIConstants.IMG_DLCL_STEP_RETURN);
		stepReturnAction.setImageDescriptor(stepReturnImage);

		stepNextAction = new Action() {
			@Override
			public void run() {
				debuggingController.stepNextAction();
			}
		};
		msg = (isJapanese) ? "ステップネクスト" : "Step Next";
		stepNextAction.setText(msg);
		stepNextAction.setToolTipText(msg);
		ImageDescriptor stepNextIcon = registry.getDescriptor(STEP_NEXT_DLCL);
		stepNextAction.setImageDescriptor(stepNextIcon);
		
		resumeAction = new Action() {
			@Override
			public void run() {
				debuggingController.resumeAction();
			}
		};
		msg = (isJapanese) ? "再開" : "Resume";
		resumeAction.setText(msg);
		resumeAction.setToolTipText(msg);
		ImageDescriptor image = DebugUITools.getImageDescriptor(IInternalDebugUIConstants.IMG_DLCL_RESUME);
		resumeAction.setImageDescriptor(image);
	}
	
	protected void createToolBar() {
		IToolBarManager mgr = getViewSite().getActionBars().getToolBarManager();
		mgr.add(fileOpenAction);
		mgr.add(importBreakpointAction);
		mgr.add(debugAction);
		mgr.add(terminateAction);
		mgr.add(resumeAction);
		mgr.add(stepIntoAction);
		mgr.add(stepOverAction);
		mgr.add(stepReturnAction);
		mgr.add(stepNextAction);
	}
	
	protected void createMenuBar() {
		IMenuManager mgr = getViewSite().getActionBars().getMenuManager();
		mgr.add(fileOpenAction);
		mgr.add(importBreakpointAction);
		mgr.add(debugAction);
		mgr.add(terminateAction);
		mgr.add(resumeAction);
		mgr.add(stepIntoAction);
		mgr.add(stepOverAction);
		mgr.add(stepReturnAction);
		mgr.add(stepNextAction);
	}
	
	private void createPopupMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			@Override
			public void menuAboutToShow(IMenuManager manager) {
				manager.add(addTraceBreakPointAction);
				manager.add(removeTraceBreakPointAction);
				manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
			}
		});
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}
	
//	public TraceBreakPoints getTraceBreakPoints() {
//		return traceBreakPoints;
//	}

	public void reset() {
		viewer.setInput(new ArrayList<TraceBreakPoint>());
		viewer.refresh();
		updateImagesForDebug(false);
		updateImagesForBreakPoint(false);
	}
	
	public void updateTraceBreakPoints(TraceBreakPoints traceBreakPoints) {
//		this.traceBreakPoints = traceBreakPoints;
		viewer.setInput(traceBreakPoints.getAllTraceBreakPoints());
		final Table table = viewer.getTable();
		for (TableItem item : table.getItems()) {
			Object data = item.getData();
			if (data instanceof TraceBreakPoint) {
				TraceBreakPoint tbp = (TraceBreakPoint)data;
				boolean isAvailable = tbp.isAvailable();
				item.setChecked(isAvailable);
			}
		}
		viewer.refresh();
	}
	
	public void updateImagesForBreakPoint(boolean hasLoadedTraceFile) {
		ImageRegistry registry = TraceDebuggerPlugin.getDefault().getImageRegistry();		
		if (hasLoadedTraceFile) {
			ImageDescriptor debugIcon = registry.getDescriptor(DEBUG_ELCL);
			debugAction.setImageDescriptor(debugIcon);
			ImageDescriptor importBreakpointIcon = registry.getDescriptor(IMPORT_BREAKPOINT_ELCL);
			importBreakpointAction.setImageDescriptor(importBreakpointIcon);
		} else {
			ImageDescriptor debugIcon = registry.getDescriptor(DEBUG_DLCL);
			debugAction.setImageDescriptor(debugIcon);
			ImageDescriptor importBreakpointIcon = registry.getDescriptor(IMPORT_BREAKPOINT_DLCL);
			importBreakpointAction.setImageDescriptor(importBreakpointIcon);
		}
	}
	
	public void updateImagesForDebug(boolean isRunning) {
		ImageRegistry registry = TraceDebuggerPlugin.getDefault().getImageRegistry();
		if (isRunning) {
			ImageDescriptor debugIcon = registry.getDescriptor(DEBUG_DLCL);
			debugAction.setImageDescriptor(debugIcon);			
			ImageDescriptor terminateImage = DebugUITools.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_TERMINATE);
			terminateAction.setImageDescriptor(terminateImage);
			ImageDescriptor stepIntoImage = DebugUITools.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_STEP_INTO);
			stepIntoAction.setImageDescriptor(stepIntoImage);
			ImageDescriptor stepOverImage = DebugUITools.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_STEP_OVER);
			stepOverAction.setImageDescriptor(stepOverImage);
			ImageDescriptor stepReturnImage = DebugUITools.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_STEP_RETURN);
			stepReturnAction.setImageDescriptor(stepReturnImage);
			ImageDescriptor stepNextIcon = registry.getDescriptor(STEP_NEXT_ELCL);
			stepNextAction.setImageDescriptor(stepNextIcon);
			ImageDescriptor image = DebugUITools.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_RESUME);
			resumeAction.setImageDescriptor(image);
		} else {
			ImageDescriptor debugIcon = registry.getDescriptor(DEBUG_ELCL);
			debugAction.setImageDescriptor(debugIcon);
			ImageDescriptor terminateImage = DebugUITools.getImageDescriptor(IInternalDebugUIConstants.IMG_DLCL_TERMINATE);
			terminateAction.setImageDescriptor(terminateImage);
			ImageDescriptor stepIntoImage = DebugUITools.getImageDescriptor(IInternalDebugUIConstants.IMG_DLCL_STEP_INTO);
			stepIntoAction.setImageDescriptor(stepIntoImage);
			ImageDescriptor stepOverImage = DebugUITools.getImageDescriptor(IInternalDebugUIConstants.IMG_DLCL_STEP_OVER);
			stepOverAction.setImageDescriptor(stepOverImage);
			ImageDescriptor stepReturnImage = DebugUITools.getImageDescriptor(IInternalDebugUIConstants.IMG_DLCL_STEP_RETURN);
			stepReturnAction.setImageDescriptor(stepReturnImage);
			ImageDescriptor stepNextIcon = registry.getDescriptor(STEP_NEXT_DLCL);
			stepNextAction.setImageDescriptor(stepNextIcon);
			ImageDescriptor image = DebugUITools.getImageDescriptor(IInternalDebugUIConstants.IMG_DLCL_RESUME);
			resumeAction.setImageDescriptor(image);
		}
	}
}

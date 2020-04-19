package org.ntlab.traceDebugger;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;

public class BreakPointView extends ViewPart {
	private TableViewer viewer;
	private IAction fileOpenAction;
	private IAction addTraceBreakPointAction;
	private IAction removeTraceBreakPointAction;
	private IAction changeAvailableAction;
	private IAction debugAction;
	private IAction terminateAction;
	private IAction stepIntoAction;
	private IAction stepOverAction;
	private IAction stepReturnAction;
	private IAction resumeAction;
	private IAction stepBackIntoAction;
	private IAction stepBackOverAction;
	private IAction stepBackReturnAction;
	private IAction backResumeAction;
	private Shell shell;
	private DebuggingController debuggingController = DebuggingController.getInstance();
	public static final String ID = "org.ntlab.traceDebugger.breakPointView";

	public BreakPointView() {
		// TODO Auto-generated constructor stub
		System.out.println("BreakPointViewクラスが生成されたよ!");
	}

	@Override
	public void createPartControl(Composite parent) {
		// TODO Auto-generated method stub
		System.out.println("BreakPointView#createPartControl(Composite)が呼ばれたよ!");
		shell = parent.getShell();
		viewer = new TableViewer(parent, SWT.FULL_SELECTION);
		Table table = viewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);

		// テーブルのカラムを作成
		String[] tableColumnTexts = {"Available", "Line No", "Signature"};
		int[] tableColumnWidth = {80, 80, 500};
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

		createActions();
		createToolBar();
		createMenuBar();
		createPopupMenu();
	}

	@Override
	public void setFocus() {
		// TODO Auto-generated method stub
		viewer.getControl().setFocus();
	}
	
	private void createActions() {				
		ImageDescriptor fileOpenIcon = PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_OBJ_FOLDER);
		fileOpenAction = new Action("Open Trace File...", fileOpenIcon) {
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
		addTraceBreakPointAction.setText("Add new trace breakpoint");
		addTraceBreakPointAction.setToolTipText("Add new trace breakpoint");
		
		removeTraceBreakPointAction = new Action() {
			@Override
			public void run() {
				debuggingController.removeTraceBreakPointAction();
			}
		};
		removeTraceBreakPointAction.setText("Remove selected trace breakpoint");
		removeTraceBreakPointAction.setToolTipText("Remove selected trace breakpoint");
		
		changeAvailableAction = new Action() {
			@Override
			public void run() {
				debuggingController.changeAvailableAction();
			}
		};
		changeAvailableAction.setText("Change available of selected trace breakpoint");
		changeAvailableAction.setToolTipText("Change available of selected trace breakpoint");
		
		debugAction = new Action() {
			@Override
			public void run() {
				debuggingController.debugAction();
			}
		};
		debugAction.setText("Debug");
		debugAction.setToolTipText("Debug");
		
		terminateAction = new Action() {
			@Override
			public void run() {
				debuggingController.terminateAction();
			}
		};
		terminateAction.setText("Terminate");
		terminateAction.setToolTipText("Terminate");

		stepIntoAction = new Action() {
			@Override
			public void run() {
				debuggingController.stepIntoAction();
			}
		};
		stepIntoAction.setText("Step Into");
		stepIntoAction.setToolTipText("Step Into");
		
		stepOverAction = new Action() {
			@Override
			public void run() {
				debuggingController.stepOverAction();
			}
		};
		stepOverAction.setText("Step Over");
		stepOverAction.setToolTipText("Step Over");
		
		stepReturnAction = new Action() {
			@Override
			public void run() {
				debuggingController.stepReturnAction();
			}
		};
		stepReturnAction.setText("Step Return");
		stepReturnAction.setToolTipText("Step Return");
		
		resumeAction = new Action() {
			@Override
			public void run() {
				debuggingController.resumeAction();
			}
		};
		resumeAction.setText("Resume");
		resumeAction.setToolTipText("Resume");
		
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
	
	private void createToolBar() {
		IToolBarManager mgr = getViewSite().getActionBars().getToolBarManager();
		mgr.add(fileOpenAction);
		mgr.add(debugAction);
		mgr.add(terminateAction);
		mgr.add(resumeAction);
		mgr.add(stepIntoAction);
		mgr.add(stepOverAction);
		mgr.add(stepReturnAction);
		mgr.add(stepBackIntoAction);
		mgr.add(stepBackOverAction);
		mgr.add(stepBackReturnAction);
		mgr.add(backResumeAction);
	}
	
	private void createMenuBar() {
		IMenuManager mgr = getViewSite().getActionBars().getMenuManager();
		mgr.add(fileOpenAction);
		mgr.add(debugAction);
		mgr.add(terminateAction);
		mgr.add(resumeAction);
		mgr.add(stepIntoAction);
		mgr.add(stepOverAction);
		mgr.add(stepReturnAction);
		mgr.add(stepBackIntoAction);
		mgr.add(stepBackOverAction);
		mgr.add(stepBackReturnAction);
		mgr.add(backResumeAction);
	}
	
	private void createPopupMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			@Override
			public void menuAboutToShow(IMenuManager manager) {
				manager.add(addTraceBreakPointAction);
				manager.add(removeTraceBreakPointAction);
				manager.add(changeAvailableAction);
				manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
			}
		});
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}
	
	public void update(TraceBreakPoints traceBreakPoints) {
		viewer.setInput(traceBreakPoints.getAllTraceBreakPoints());
		viewer.refresh();
	}
}

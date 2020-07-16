package org.ntlab.traceDebugger;

import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
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
import org.eclipse.ui.part.ViewPart;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
import org.ntlab.traceDebugger.analyzerProvider.AbstractAnalyzer;
import org.ntlab.traceDebugger.analyzerProvider.DeltaExtractionAnalyzer;
import org.ntlab.traceDebugger.analyzerProvider.DeltaMarkerManager;

public class TracePointsView extends ViewPart {
	private TableViewer viewer;
	private Shell shell;
	private IAction addAction;
	private IAction removeAction;
	private IAction jumpAction;
	private TracePoint selectedTp;
	private TracePoints tracePoints = new TracePoints();
	public static final String ID = "org.ntlab.traceDebugger.tracePointsView";

	public TracePointsView() {
		// TODO Auto-generated constructor stub
		System.out.println("BreakPointViewクラスが生成されたよ!");
	}

	@Override
	public void createPartControl(Composite parent) {
		// TODO Auto-generated method stub
		System.out.println("TracePointsView#createPartControl(Composite)が呼ばれたよ!");
		shell = parent.getShell();
		viewer = new TableViewer(parent, SWT.FULL_SELECTION);
		Table table = viewer.getTable();
		table.setHeaderVisible(true);
		table.setLinesVisible(true);

		// テーブルのカラムを作成
		String[] tableColumnTexts = {"Line", "Signature"};
		int[] tableColumnWidth = {80, 1000};
		TableColumn[] tableColumns = new TableColumn[tableColumnTexts.length];
		for (int i = 0; i < tableColumns.length; i++) {
			tableColumns[i] = new TableColumn(table, SWT.NULL);
			tableColumns[i].setText(tableColumnTexts[i]);
			tableColumns[i].setWidth(tableColumnWidth[i]);
		}
		viewer.setContentProvider(new ArrayContentProvider());
		viewer.setLabelProvider(new TracePointsLabelProvider());
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection sel = (IStructuredSelection)event.getSelection();
				Object element = sel.getFirstElement();
				if (element instanceof TracePoint) {
					selectedTp = (TracePoint)element;
					MethodExecution me = selectedTp.getMethodExecution();
					int lineNo = selectedTp.getStatement().getLineNo();
					JavaEditorOperator.openSrcFileOfMethodExecution(me, lineNo);
				}
			}
		});
		viewer.addDoubleClickListener(new IDoubleClickListener() {
			@Override
			public void doubleClick(DoubleClickEvent event) {
				IStructuredSelection sel = (IStructuredSelection)event.getSelection();
				Object element = sel.getFirstElement();
				if (element instanceof TracePoint) {
					selectedTp = (TracePoint)element;
					if (DebuggingController.getInstance().isRunning()) {
						jumpToTheTracePoint(selectedTp);	
					}
				}
			}
		});

		createActions();
		createToolBar();
		createMenuBar();
		createPopupMenu();
		TraceDebuggerPlugin.setActiveView(ID, this);
	}

	@Override
	public void setFocus() {
		// TODO Auto-generated method stub
		TraceDebuggerPlugin.setActiveView(ID, this);
		viewer.getControl().setFocus();
	}
	
	private void createActions() {		
		addAction = new Action() {
			@Override
			public void run() {
				DebuggingController debuggingController = DebuggingController.getInstance();
				TracePoint currentTp = debuggingController.getCurrentTp();
				addTracePoint(currentTp);
			}
		};
		addAction.setText("Add");
		addAction.setToolTipText("Add");
		
		removeAction = new Action() {
			@Override
			public void run() {
				if (selectedTp != null) {
					tracePoints.remove(selectedTp);
					update();					
				}
			}
		};
		removeAction.setText("Remove");
		removeAction.setToolTipText("Remove");
		
		jumpAction = new Action() {
			@Override
			public void run() {
				if (selectedTp != null && DebuggingController.getInstance().isRunning()) {
					jumpToTheTracePoint(selectedTp);	
				}
			}
		};
		jumpAction.setText("Jump");
		jumpAction.setToolTipText("Jump");
	}
	
	private void createToolBar() {
		IToolBarManager mgr = getViewSite().getActionBars().getToolBarManager();
		mgr.add(addAction);
		mgr.add(removeAction);
		mgr.add(jumpAction);
	}
	
	private void createMenuBar() {
		IMenuManager mgr = getViewSite().getActionBars().getMenuManager();
		mgr.add(addAction);
		mgr.add(removeAction);
		mgr.add(jumpAction);
	}
	
	private void createPopupMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			@Override
			public void menuAboutToShow(IMenuManager manager) {
//				manager.add(addAction);
//				manager.add(removeAction);
//				manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
			}
		});
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}
	
	public void addTracePoint(TracePoint tp) {
		tracePoints.add(tp);
		update();
	}

	public void reset() {
		tracePoints.clear();
		update();
	}
	
	private void update() {
		viewer.setInput(tracePoints.getToArray());
		viewer.refresh();
	}
	
	private void jumpToTheTracePoint(TracePoint tp) {
		DebuggingController debuggingController = DebuggingController.getInstance();
		debuggingController.jumpToTheTracePoint(tp, false);
		MethodExecution currentME = tp.getMethodExecution();
		int lineNo = tp.getStatement().getLineNo();
		IMarker marker = DebuggingController.getInstance().createCurrentLineMarker(currentME, lineNo);
		JavaEditorOperator.markAndOpenJavaFile(marker);

		CallStackView callStackView = ((CallStackView)TraceDebuggerPlugin.getActiveView(CallStackView.ID));
		VariableView variableView = ((VariableView)TraceDebuggerPlugin.getActiveView(VariableView.ID));

		AbstractAnalyzer analyzer = TraceDebuggerPlugin.getAnalyzer();
		if (analyzer instanceof DeltaExtractionAnalyzer) {
			DeltaMarkerView deltaMarkerView = (DeltaMarkerView)TraceDebuggerPlugin.getActiveView(DeltaMarkerView.ID);
			if (deltaMarkerView != null) {
				DeltaMarkerManager deltaMarkerManager = deltaMarkerView.getDeltaMarkerManager();
				Map<String, List<IMarker>> deltaMarkers = deltaMarkerManager.getMarkers();
				if (deltaMarkers != null) {
					variableView.markAndExpandVariablesByDeltaMarkers(deltaMarkers);
				}
				IMarker coordinatorMarker = deltaMarkerManager.getCoordinatorDeltaMarker();
				if (coordinatorMarker != null) {
					MethodExecution coordinatorME = DeltaMarkerManager.getMethodExecution(coordinatorMarker);
					callStackView.highlight(coordinatorME);					
				}
				CallTreeView callTreeView = ((CallTreeView)TraceDebuggerPlugin.getActiveView(CallTreeView.ID));
				callTreeView.highlight(currentME);
			}
		}
	}	
}
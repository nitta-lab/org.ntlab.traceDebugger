package org.ntlab.traceDebugger;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeNode;
import org.eclipse.jface.viewers.TreeNodeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.ViewPart;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
import org.ntlab.traceDebugger.analyzerProvider.Alias;
import org.ntlab.traceDebugger.analyzerProvider.Alias.AliasType;

import org.ntlab.traceDebugger.analyzerProvider.DeltaMarkerManager;

public class DeltaMarkerView extends ViewPart {
	private TreeViewer viewer;
	private Shell shell;
	private TracePoint bottomPoint;
	private TracePoint coordinatorPoint;
	private DeltaMarkerManager deltaMarkerManager = new DeltaMarkerManager();
	private String subId;
	public static String ID = "org.ntlab.traceDebugger.deltaMarkerView";

	@Override
	public void createPartControl(Composite parent) {
		// TODO Auto-generated method stub
		System.out.println("DeltaMarkerView#createPartControl(Composite)が呼ばれたよ!");
		shell = parent.getShell();
		viewer = new TreeViewer(parent, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
		Tree tree = viewer.getTree();
		tree.setHeaderVisible(true);
		tree.setLinesVisible(true);

		// テーブルのカラムを作成
		String[] tableColumnTexts = {"Description", "Object ID", "Object Type", "Alias Type", "Source", "Line"};
		int[] tableColumnWidth = {120, 100, 120, 120, 100, 80};
		TreeColumn[] tableColumns = new TreeColumn[tableColumnTexts.length];
		for (int i = 0; i < tableColumns.length; i++) {
			tableColumns[i] = new TreeColumn(tree, SWT.NULL);
			tableColumns[i].setText(tableColumnTexts[i]);
			tableColumns[i].setWidth(tableColumnWidth[i]);
		}
		viewer.setContentProvider(new TreeNodeContentProvider());
		viewer.setLabelProvider(new DeltaMarkerLabelProvider());
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection sel = (IStructuredSelection)event.getSelection();
				Object element = sel.getFirstElement();
				if (!(element instanceof TreeNode)) return;
				Object value = ((TreeNode)element).getValue();
				if (!(value instanceof IMarker)) return;
				IMarker selectionMarker = (IMarker)value;
				try {
					DebuggingController controller = DebuggingController.getInstance();
					Object obj = selectionMarker.getAttribute(DeltaMarkerManager.DELTA_MARKER_ATR_DATA);
					TracePoint jumpPoint;
					MethodExecution selectionME;
					boolean isReturned = false;
					if (obj instanceof Alias) {
						Alias alias = (Alias)obj;
						jumpPoint = alias.getOccurrencePoint();
						selectionME = jumpPoint.getMethodExecution();
						Alias.AliasType type = alias.getAliasType();
						isReturned = type.equals(AliasType.METHOD_INVOCATION)
										|| type.equals(AliasType.CONSTRACTOR_INVOCATION); 
					} else if (obj instanceof TracePoint) {
						jumpPoint = (TracePoint)obj;
						selectionME = jumpPoint.getMethodExecution();
					} else {
						jumpPoint = coordinatorPoint;
						selectionME = coordinatorPoint.getMethodExecution();
					}
					controller.jumpToTheTracePoint(jumpPoint, isReturned);
					IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
					IDE.openEditor(page, selectionMarker);

					CallStackView callStackView = (CallStackView)getOtherView(CallStackView.ID, null);
					callStackView.highlight(coordinatorPoint.getMethodExecution());
					VariableView variableView = (VariableView)getOtherView(VariableView.ID, null);
					variableView.markAndExpandVariablesByDeltaMarkers(deltaMarkerManager.getMarkers());
					CallTreeView callTreeView = ((CallTreeView)getOtherView(CallTreeView.ID, null));
//					CallTreeView callTreeView = ((CallTreeView)getOtherView(CallTreeView.ID, subId));
					callTreeView.highlight(selectionME);
					setFocus();
				} catch (CoreException e) {
					e.printStackTrace();
				}
			}
		});
		viewer.refresh();

		createActions();
		createToolBar();
		createMenuBar();
		createPopupMenu();
	}
	
	private void createActions() {
		// TODO Auto-generated method stub
	}
	
	private void createToolBar() {
		IToolBarManager mgr = getViewSite().getActionBars().getToolBarManager();
	}

	private void createMenuBar() {
		IMenuManager mgr = getViewSite().getActionBars().getMenuManager();
	}
	
	private void createPopupMenu() {
		// TODO Auto-generated method stub
	}

	@Override
	public void setFocus() {
		// TODO Auto-generated method stub
		viewer.getControl().setFocus();
	}
	
	public void update() {
		viewer.setInput(deltaMarkerManager.getMarkerTreeNodes());
		viewer.expandAll();
		viewer.refresh();
	}
	
	public DeltaMarkerManager getDeltaMarkerManager() {
		return deltaMarkerManager;
	}

	public String getSubId() {
		return subId;
	}
	
	public TracePoint getBottomPoint() {
		return bottomPoint;
	}
	
	public TracePoint getCoordinatorPoint() {
		return coordinatorPoint;
	}

	public void setSubId(String subId) {
		this.subId = subId;
	}
	
	public void setBottomPoint(TracePoint bottomPoint) {
		this.bottomPoint = bottomPoint;
	}
	
	public void setCoordinatorPoint(TracePoint coordinatorPoint) {
		this.coordinatorPoint = coordinatorPoint;
	}
	
	public void dispose() {
		deltaMarkerManager.clearAllMarkers();
		CallTreeView callTreeView = ((CallTreeView)getOtherView(CallTreeView.ID, null));
		callTreeView.reset();
		super.dispose();
	}
	
	private IViewPart getOtherView(String viewId, String subId) {
		IWorkbenchPage workbenchPage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		try {
			if (subId == null) return workbenchPage.showView(viewId);
			return workbenchPage.showView(viewId, subId, IWorkbenchPage.VIEW_ACTIVATE);
		} catch (PartInitException e) {
			throw new RuntimeException(e);
		}	
	}
}

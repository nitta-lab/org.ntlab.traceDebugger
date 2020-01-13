package org.ntlab.traceDebugger;

import java.util.List;
import java.util.Map;
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
		int[] tableColumnWidth = {120, 100, 80, 120, 100, 50};
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
				IMarker marker = (IMarker)value;
				try {
					DebuggingController controller = DebuggingController.getInstance();
					Object obj = marker.getAttribute("data");
					TracePoint jumpPoint = coordinatorPoint;
					if (obj instanceof Alias) {
						jumpPoint = ((Alias)obj).getOccurrencePoint();
					} else if (obj instanceof TracePoint) {
						jumpPoint = (TracePoint)obj;
					}
					controller.jumpToTheTracePoint(jumpPoint);
					
					IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
					IDE.openEditor(page, marker);
					highlightInCallStack(deltaMarkerManager.getCoordinatorDeltaMarker());
					VariableView variableView = (VariableView)getOtherView(VariableView.ID);
					variableView.expandParticularNodes(deltaMarkerManager.getMarkers());
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
	
	private void highlightInCallStack(IMarker marker) {
		CallStackView callStackView = (CallStackView)getOtherView(CallStackView.ID);
		try {
			Object obj = marker.getAttribute("data");
			String signature1 = "";
			if (obj instanceof Alias) {
				signature1 = ((Alias)obj).getMethodSignature();
			} else if (obj instanceof TracePoint) {
				signature1 = ((TracePoint)obj).getMethodExecution().getCallerSideSignature();
			} else if (obj instanceof MethodExecution) {
				signature1 = ((MethodExecution)obj).getCallerSideSignature();
			}
			Map<String, List<CallStackModel>> threadIdTocallStackModels = callStackView.getCallStackModels();
			for (List<CallStackModel> callStackModels : threadIdTocallStackModels.values()) {
				for (CallStackModel callStackModel : callStackModels) {
					String signature2 = callStackModel.getMethodExecution().getCallerSideSignature();
					callStackModel.setHighlighting(signature1.equals(signature2));
				}
			}
			callStackView.refresh();
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}
	
	public void dispose() {
		deltaMarkerManager.clearAllMarkers();
		super.dispose();
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

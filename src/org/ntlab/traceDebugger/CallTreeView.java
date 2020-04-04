package org.ntlab.traceDebugger;

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeNode;
import org.eclipse.jface.viewers.TreeNodeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
import org.ntlab.traceDebugger.analyzerProvider.DeltaMarkerManager;

public class CallTreeView extends ViewPart {
	private TreeViewer viewer;
	private CallTreeModels callTreeModels = new CallTreeModels();
	private String subId;
	public static final String ID = "org.ntlab.traceDebugger.callTreeView";
	
	public CallTreeView() {
		// TODO Auto-generated constructor stub
		System.out.println("callTreeViewクラスが生成されたよ");
	}

	@Override
	public void createPartControl(Composite parent) {
		// TODO Auto-generated method stub
		System.out.println("CallTreeView#createPartControl(Composite)が呼ばれたよ!");
		viewer = new TreeViewer(parent);
		viewer.setContentProvider(new TreeNodeContentProvider());
		viewer.setLabelProvider(new CallTreeLabelProvider());
		
		// 選択したカラムに対応するメソッド実行のソースファイルを開かせるリスナーを登録する
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection sel = (IStructuredSelection)event.getSelection();
				Object element = sel.getFirstElement();
				if (element instanceof TreeNode) {
					Object value = ((TreeNode)element).getValue();					
					if (value instanceof CallTreeModel) {
						CallTreeModel callTreeModel = (CallTreeModel)value;
						MethodExecution methodExecution = callTreeModel.getMethodExecution();
						highlight(methodExecution);
						TracePoint tp = methodExecution.getEntryPoint();
						JavaEditorOperator.openSrcFileOfMethodExecution(methodExecution, -1);
						DeltaMarkerView deltaMarkerView = ((DeltaMarkerView)getOtherView(DeltaMarkerView.ID, subId));
						DeltaMarkerManager deltaMarkerManager = deltaMarkerView.getDeltaMarkerManager();
						CallStackView callStackView = (CallStackView)getOtherView(CallStackView.ID, null);
						callStackView.updateByTracePoint(tp);
						try {
							Object coordinatorME = deltaMarkerManager.getCoordinatorDeltaMarker().getAttribute("data");
							if (coordinatorME instanceof MethodExecution) {
								callStackView.highlight((MethodExecution)coordinatorME);
							}
						} catch (CoreException e) {
							e.printStackTrace();
						}
						VariableView variableView = ((VariableView)getOtherView(VariableView.ID, null));
						variableView.updateVariablesByTracePoint(tp, false);
						variableView.markAndExpandVariablesByDeltaMarkers(deltaMarkerManager.getMarkers());
					}
				}
			}
		});
		createActions();
		createToolBar();
		createMenuBar();
	}

	@Override
	public void setFocus() {
		// TODO Auto-generated method stub
		viewer.getControl().setFocus();
	}
	
	private void createActions() {
		
	}
	
	private void createToolBar() {
		IToolBarManager mgr = getViewSite().getActionBars().getToolBarManager();
	}
	
	private void createMenuBar() {
		IMenuManager mgr = getViewSite().getActionBars().getMenuManager();
	}

	public String getSubId() {
		return subId;
	}
	
	public void setSubId(String subId) {
		this.subId = subId;
	}
	
	public void update(DeltaMarkerManager deltaMarkerManager) {
		callTreeModels.update(deltaMarkerManager);
		viewer.setInput(callTreeModels.getCallTreeModels());
		viewer.expandAll();		
	}
	
	public void highlight(MethodExecution theMe) {
		List<CallTreeModel> callTreeModelList = callTreeModels.getCallTreeModelList();
		for (CallTreeModel callTreeModel : callTreeModelList) {
			MethodExecution me = callTreeModel.getMethodExecution();
			callTreeModel.setHighlighting(me.equals(theMe));
		}
		viewer.refresh();
	}
	
	public void refresh() {
		
	}
	
	public void reset() {
		callTreeModels.reset();
		viewer.setInput(callTreeModels.getCallTreeModelList());
		viewer.refresh();
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

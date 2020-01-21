package org.ntlab.traceDebugger;

import java.util.List;
import java.util.Map;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeNode;
import org.eclipse.jface.viewers.TreeNodeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class CallStackView extends ViewPart {
	private TreeViewer viewer;
	private IAction refreshAction;
	private CallStackModels callStackModels = new CallStackModels();
	public static final String ID = "org.ntlab.traceDebugger.callStackView";
	
	public CallStackView() {
		// TODO Auto-generated constructor stub
		System.out.println("callStackViewクラスが生成されたよ");
	}

	@Override
	public void createPartControl(Composite parent) {
		// TODO Auto-generated method stub
		System.out.println("CallStackView#createPartControl(Composite)が呼ばれたよ!");
		viewer = new TreeViewer(parent);
		viewer.setContentProvider(new TreeNodeContentProvider());
		viewer.setLabelProvider(new CallStackLabelProvider());
		viewer.expandAll();
		
		// 選択したカラムに対応するメソッド実行のソースファイルを開かせるリスナーを登録する
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection sel = (IStructuredSelection)event.getSelection();
				Object element = sel.getFirstElement();
				if (element instanceof TreeNode) {
					Object value = ((TreeNode)element).getValue();
					if (value instanceof CallStackModel) {
						CallStackModel callStackModel = (CallStackModel)value;
						MethodExecution methodExecution = callStackModel.getMethodExecution();
						TracePoint tp = callStackModel.getTracePoint();
						JavaEditorOperator.openSrcFileOfMethodExecution(methodExecution, callStackModel.getCallLineNo());
						((VariableView)getOtherView(VariableView.ID)).updateVariablesByTracePoint(tp, false);
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
		refreshAction = new Action() {
			@Override
			public void run() {
				refresh();
			}
		};
		refreshAction.setText("refresh");
		refreshAction.setToolTipText("refresh");
		refreshAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_ELCL_SYNCED));		
	}
	
	private void createToolBar() {
		IToolBarManager mgr = getViewSite().getActionBars().getToolBarManager();
		mgr.add(refreshAction);
	}
	
	private void createMenuBar() {
		IMenuManager mgr = getViewSite().getActionBars().getMenuManager();
		mgr.add(refreshAction);
	}
	
	public void updateByTracePoint(TracePoint tp) {
		callStackModels.updateByTracePoint(tp);
		refresh();
	}

	public void refresh() {
		TreeNode[] nodes = callStackModels.getAllCallStacksTree();
		if (nodes == null || nodes[0] == null) {
			viewer.setInput(null);
			viewer.expandAll();
			return;
		}
		viewer.setInput(nodes);
		viewer.expandAll();
	}
	
	public void reset() {
		callStackModels.reset();
//		viewer.setInput(null);
//		viewer.expandAll();
		refresh();
	}
	
	public Map<String, List<CallStackModel>> getCallStackModels() {
		return callStackModels.getAllCallStacks();
	}
	
	public void highlight(MethodExecution methodExecution) {
		callStackModels.highlight(methodExecution);
		viewer.refresh();
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

package org.ntlab.traceDebugger;

import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeNode;
import org.eclipse.jface.viewers.TreeNodeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.part.ViewPart;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
import org.ntlab.traceDebugger.analyzerProvider.AbstractAnalyzer;
import org.ntlab.traceDebugger.analyzerProvider.DeltaExtractionAnalyzer;
import org.ntlab.traceDebugger.analyzerProvider.DeltaMarkerManager;

public class CallTreeView extends ViewPart {
	private TreeViewer viewer;
	private CallTreeModels callTreeModels = new CallTreeModels();
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
				if (!(element instanceof TreeNode)) return;
				Object value = ((TreeNode)element).getValue();
				if (!(value instanceof CallTreeModel)) return;
				CallTreeModel callTreeModel = (CallTreeModel)value;
				MethodExecution methodExecution = callTreeModel.getMethodExecution();				
				TracePoint tp = methodExecution.getEntryPoint();

				if ((DebuggingController.getInstance().isRunning())) {
					highlight(methodExecution);
					DebuggingController controller = DebuggingController.getInstance();
					controller.jumpToTheTracePoint(tp, false);
					CallStackViewRelatedDelta callStackView = (CallStackViewRelatedDelta)TraceDebuggerPlugin.getActiveView(CallStackViewRelatedDelta.ID);
					VariableViewRelatedDelta variableView = ((VariableViewRelatedDelta)TraceDebuggerPlugin.getActiveView(VariableViewRelatedDelta.ID));
					DeltaMarkerView deltaMarkerView = (DeltaMarkerView)TraceDebuggerPlugin.getActiveView(DeltaMarkerView.ID);
					DeltaMarkerManager deltaMarkerManager = deltaMarkerView.getDeltaMarkerManager();
					IMarker coodinatorMarker = deltaMarkerManager.getCoordinatorDeltaMarker();
					MethodExecution coordinatorME = DeltaMarkerManager.getMethodExecution(coodinatorMarker);
					if (coordinatorME != null) callStackView.highlight((MethodExecution)coordinatorME);						
					variableView.markAndExpandVariablesByDeltaMarkers(deltaMarkerManager.getMarkers());
				} else {
					int lineNo = tp.getStatement().getLineNo();
					JavaEditorOperator.openSrcFileOfMethodExecution(methodExecution, lineNo);
				}
			}
		});
		createActions();
		createToolBar();
		createMenuBar();
		TraceDebuggerPlugin.setActiveView(ID, this);
	}

	@Override
	public void setFocus() {
		// TODO Auto-generated method stub
		TraceDebuggerPlugin.setActiveView(ID, this);
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
}

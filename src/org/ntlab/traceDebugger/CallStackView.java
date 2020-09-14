package org.ntlab.traceDebugger;

import java.util.List;
import java.util.Map;

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

public class CallStackView extends ViewPart {
	protected TreeViewer viewer;
	protected CallStackModel selectionCallStackModel;
	protected CallStackModels callStackModels = new CallStackModels();
	public static final String ID = "org.ntlab.traceDebugger.callStackView";
	
	public CallStackView() {
		// TODO Auto-generated constructor stub
		System.out.println("callStackView�N���X���������ꂽ��");
	}

	@Override
	public void createPartControl(Composite parent) {
		// TODO Auto-generated method stub
		System.out.println("CallStackView#createPartControl(Composite)���Ă΂ꂽ��!");
		viewer = new TreeViewer(parent);
		viewer.setContentProvider(new TreeNodeContentProvider());
		viewer.setLabelProvider(new CallStackLabelProvider());
		viewer.expandAll();
		
		// �I�������J�����ɑΉ����郁�\�b�h���s�̃\�[�X�t�@�C�����J�����郊�X�i�[��o�^����
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection sel = (IStructuredSelection)event.getSelection();
				Object element = sel.getFirstElement();
				if (element instanceof TreeNode) {
					Object value = ((TreeNode)element).getValue();
					if (value instanceof CallStackModel) {
						CallStackModel callStackModel = (CallStackModel)value;
						selectionCallStackModel = callStackModel;
						MethodExecution methodExecution = callStackModel.getMethodExecution();
						JavaEditorOperator.openSrcFileOfMethodExecution(methodExecution, callStackModel.getCallLineNo());
						additonalActionOnSelectionChanged(callStackModel);
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
	public String getTitle() {
		return TraceDebuggerPlugin.isJapanese() ? "�Ăяo���X�^�b�N" : "CallStack";
	}

	@Override
	public void setFocus() {
		// TODO Auto-generated method stub
		TraceDebuggerPlugin.setActiveView(ID, this);
		viewer.getControl().setFocus();
	}
	
	protected void createActions() {

	}
	
	protected void createToolBar() {
		IToolBarManager mgr = getViewSite().getActionBars().getToolBarManager();
	}
	
	protected void createMenuBar() {
		IMenuManager mgr = getViewSite().getActionBars().getMenuManager();
	}
	
	protected void createPopupMenu() {

	}
	
	protected void additonalActionOnSelectionChanged(CallStackModel selectedCallStackModel) {
		TracePoint tp = selectedCallStackModel.getTracePoint();
		TracePoint debuggingTp = DebuggingController.getInstance().getCurrentTp();
		VariableView variableView = (VariableView)TraceDebuggerPlugin.getActiveView(VariableView.ID);
		variableView.updateVariablesByTracePoint(tp, false, debuggingTp);
	}
	
	public void updateByTracePoint(TracePoint tp) {
		callStackModels.updateByTracePoint(tp);
		refresh();
		selectionCallStackModel = null;
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
		refresh();
	}
	
	public CallStackModel getSelectionCallStackModel() {
		return selectionCallStackModel;
	}
	
	public boolean isSelectionOnTop() {
		if (selectionCallStackModel == null) return false;
		TreeNode[] nodes = callStackModels.getAllCallStacksTree();
		if (nodes == null || nodes[0] == null) return false;
		TreeNode[] children = nodes[0].getChildren();
		Object obj = children[0].getValue();
		if (!(obj instanceof CallStackModel)) return false;
		CallStackModel topCallStackModel = (CallStackModel)obj;
		return topCallStackModel.equals(selectionCallStackModel);
	}
	
	public Map<String, List<CallStackModel>> getCallStackModels() {
		return callStackModels.getAllCallStacks();
	}
	
	public void highlight(MethodExecution methodExecution) {
		callStackModels.highlight(methodExecution);
		viewer.refresh();
	}
	
	public void removeHighlight() {
		callStackModels.removeHighlight();
		viewer.refresh();
	}
}

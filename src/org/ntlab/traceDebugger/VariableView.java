package org.ntlab.traceDebugger;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeViewerListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeExpansionEvent;
import org.eclipse.jface.viewers.TreeNode;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.part.ViewPart;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class VariableView extends ViewPart {	
	protected TreeViewer viewer;
	protected Variable selectedVariable;
	protected Variables variables = Variables.getInstance();
	public static final String ID = "org.ntlab.traceDebugger.variableView";

	public VariableView() {
		// TODO Auto-generated constructor stub
		System.out.println("VariableViewクラスが生成されたよ!");
	}

	@Override
	public void createPartControl(Composite parent) {
		// TODO Auto-generated method stub
		System.out.println("VariableView#createPartControl(Composite)が呼ばれたよ!");
		viewer = new TreeViewer(parent, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
		Tree tree = viewer.getTree();
		tree.setHeaderVisible(true);
		tree.setLinesVisible(true);

		String[] treeColumnTexts = (TraceDebuggerPlugin.isJapanese()) ? new String[]{"名前", "値"} 																		: new String[]{"Name", "Value"};
		int[] treeColumnWidth = {200, 300};
		TreeColumn[] treeColumns = new TreeColumn[treeColumnTexts.length];
		for (int i = 0; i < treeColumns.length; i++) {
			treeColumns[i] = new TreeColumn(tree, SWT.NULL);
			treeColumns[i].setText(treeColumnTexts[i]);
			treeColumns[i].setWidth(treeColumnWidth[i]);
		}
		viewer.setContentProvider(new MyTreeNodeContentProvider());
		viewer.setLabelProvider(new VariableLabelProvider());
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent event) {				
				IStructuredSelection sel = (IStructuredSelection)event.getSelection();
				Object element = sel.getFirstElement();
				if (element instanceof TreeNode) {
					Object value = ((TreeNode)element).getValue();
					if (value instanceof Variable) {
						selectedVariable = (Variable)value;
					}	
				}
			}
		});
		viewer.addTreeListener(new ITreeViewerListener() {
			@Override
			public void treeExpanded(TreeExpansionEvent event) {
				// ツリーを開いた後に実行される。 ここでは開いたノードから3つ先のノードを生成して追加する。
				Object element = event.getElement();
				if (!(element instanceof MyTreeNode)) return;
				MyTreeNode expandedNode = (MyTreeNode)element;
				Object value = expandedNode.getValue();
				if (!(value instanceof Variable)) return;
				List<MyTreeNode> childNodes = expandedNode.getChildList();
				if (childNodes == null) return;
				for (MyTreeNode childNode : childNodes) {
					List<MyTreeNode> grandChildNodes = childNode.getChildList();
					if (grandChildNodes == null) continue;
					for (MyTreeNode grandChildNode : grandChildNodes) {
						Variable grandChildVariable = (Variable)grandChildNode.getValue();
						grandChildVariable.createNextHierarchyState();
						List<Variable> list = grandChildVariable.getChildren();
						List<MyTreeNode> nodes = new ArrayList<>();
						for (int i = 0; i < list.size(); i++) {
							nodes.add(i, new MyTreeNode(list.get(i)));
						}
						grandChildNode.setChildList(nodes);
					}
				}
				viewer.refresh();
			}
			@Override
			public void treeCollapsed(TreeExpansionEvent event) {}
		});
		createActions();
		createToolBar();
		createMenuBar();
		createPopupMenu();
		TraceDebuggerPlugin.setActiveView(ID, this);
	}

	@Override
	public String getTitle() {
		return TraceDebuggerPlugin.isJapanese() ? "変数" : "Variables";
	}
	
	@Override
	public void setFocus() {
		// TODO Auto-generated method stub
		TraceDebuggerPlugin.setActiveView(ID, this);
		viewer.getControl().setFocus();
	}
	
	@Override
	public void dispose() {
		TraceDebuggerPlugin.removeView(ID, this);
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
	
	public void reset() {
		variables.resetData();
		viewer.setInput(variables.getVariablesTreeNodesList());
		viewer.refresh();
	}	
	
	public void updateVariablesByTracePoint(TracePoint tp, boolean isReturned) {
		updateVariablesByTracePoint(tp, isReturned, null);
	}
		
	public void updateVariablesByTracePoint(TracePoint tp, boolean isReturned, TracePoint before) {
		updateVariablesByTracePoint(null, tp, isReturned, before);
	}

	public void updateVariablesByTracePoint(TracePoint from, TracePoint to, boolean isReturned) {
		updateVariablesByTracePoint(from, to, isReturned, null);
	}
	
	public void updateVariablesByTracePoint(TracePoint from, TracePoint to, boolean isReturned, TracePoint before) {
		variables.updateAllObjectDataByTracePoint(from, to, isReturned, before);
		viewer.setInput(variables.getVariablesTreeNodesList());
	}
	
	public void updateVariablesForDifferential(TracePoint from, TracePoint to, boolean isReturned) {
		variables.updateForDifferential(from, to, isReturned);
//		viewer.setInput(variables.getVariablesTreeNodes());
		viewer.refresh();
	}	
}

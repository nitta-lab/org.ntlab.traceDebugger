package org.ntlab.traceDebugger;

import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeViewerListener;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeExpansionEvent;
import org.eclipse.jface.viewers.TreeNode;
import org.eclipse.jface.viewers.TreeNodeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
import org.ntlab.traceDebugger.analyzerProvider.AbstractAnalyzer;
import org.ntlab.traceDebugger.analyzerProvider.DeltaExtractionAnalyzer;

public class VariableView extends ViewPart {	
	private TreeViewer viewer; 
	private IAction deltaAction;
	private Variable selectedVariable;
	private Variables variables = Variables.getInstance();
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

		String[] treeColumnTexts = {"Name", "Value"};
		int[] treeColumnWidth = {100, 200};
		TreeColumn[] treeColumns = new TreeColumn[treeColumnTexts.length];
		for (int i = 0; i < treeColumns.length; i++) {
			treeColumns[i] = new TreeColumn(tree, SWT.NULL);
			treeColumns[i].setText(treeColumnTexts[i]);
			treeColumns[i].setWidth(treeColumnWidth[i]);
		}
		viewer.setContentProvider(new TreeNodeContentProvider());
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
				if (!(element instanceof TreeNode)) return;
				TreeNode expandedNode = (TreeNode)element;
				Object value = expandedNode.getValue();
				if (!(value instanceof Variable)) return;
				TreeNode[] childNodes = expandedNode.getChildren();
				if (childNodes == null) return;
				for (TreeNode childNode : childNodes) {
					TreeNode[] grandChildNodes = childNode.getChildren();
					if (grandChildNodes == null) continue;
					for (TreeNode grandChildNode : grandChildNodes) {
						Variable grandChildVariable = (Variable)grandChildNode.getValue();
						grandChildVariable.createNextHierarchyState();
						List<Variable> list = grandChildVariable.getChildren();
						TreeNode[] nodes = new TreeNode[list.size()];
						for (int i = 0; i < list.size(); i++) {
							nodes[i] = new TreeNode(list.get(i));
						}
						grandChildNode.setChildren(nodes);
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
	}

	@Override
	public void setFocus() {
		// TODO Auto-generated method stub
		viewer.getControl().setFocus();
	}
	
	private void createActions() {
		deltaAction = new Action() {
			@Override
			public void run() {
				AbstractAnalyzer analyzer = TraceDebuggerPlugin.getAnalyzer();
				if (analyzer instanceof DeltaExtractionAnalyzer) {
					DeltaExtractionAnalyzer deltaAnalyzer = (DeltaExtractionAnalyzer)analyzer;
					deltaAnalyzer.extractDelta(selectedVariable);
				}
			}
		};
		deltaAction.setText("Extract Delta");
		deltaAction.setToolTipText("Extract Delta");
	}
	
	private void createToolBar() {
		IToolBarManager mgr = getViewSite().getActionBars().getToolBarManager();
	}
	
	private void createMenuBar() {
		IMenuManager mgr = getViewSite().getActionBars().getMenuManager();
	}
	
	private void createPopupMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			@Override
			public void menuAboutToShow(IMenuManager manager) {
				manager.add(deltaAction);
				manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
			}
		});
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}
	
	public void reset() {
		variables.resetData();
		viewer.setInput(variables.getVariablesTreeNodes());
		viewer.refresh();
	}
	
	public void updateVariablesByTracePoint(TracePoint tp, boolean isReturned) {
		variables.getAllObjectDataByTracePoint(tp, isReturned);
		viewer.setInput(variables.getVariablesTreeNodes());
	}
	
//	public void updateVariablesByAlias(Alias alias) {
//		variables.getAllObjectDataByAlias(alias);
//		viewer.setInput(variables.getVariablesTreeNodesList());
//	}
	
	private IViewPart getOtherView(String viewId) {
		IWorkbenchPage workbenchPage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		try {
			return workbenchPage.showView(viewId);
		} catch (PartInitException e) {
			throw new RuntimeException(e);
		}	
	}
}
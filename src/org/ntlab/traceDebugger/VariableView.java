package org.ntlab.traceDebugger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.InputDialog;
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
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
import org.ntlab.traceDebugger.analyzerProvider.AbstractAnalyzer;
import org.ntlab.traceDebugger.analyzerProvider.Alias;
import org.ntlab.traceDebugger.analyzerProvider.DeltaExtractionAnalyzer;
import org.ntlab.traceDebugger.analyzerProvider.DeltaMarkerManager;

public class VariableView extends ViewPart {	
	private TreeViewer viewer;
	private IAction jumpAction;
	private IAction deltaAction;
	private IAction deltaActionForCollection;
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
		jumpAction = new Action() {
			public void run() {
				TracePoint tp = selectedVariable.getLastUpdatePoint();
				if (tp == null) return;
				DebuggingController controller = DebuggingController.getInstance();
				controller.jumpToTheTracePoint(tp, false);
				controller.stepOverAction();
			}
		};
		jumpAction.setText("Jump to Creation Point");
		jumpAction.setToolTipText("Jump to Creation Point");
		
		deltaAction = new Action() {
			@Override
			public void run() {
				if (selectedVariable.getVariableName().equals(Variables.RETURN_VARIABLE_NAME)) {
					String[] texts = {"Caller to Callee", "This to Another"};
					RadioButtonDialog dialog = new RadioButtonDialog(null, "Which patterns?", texts);
					if (dialog.open() != InputDialog.OK) return;
					String selectionType = dialog.getValue();
					delta(selectedVariable, true, selectionType.startsWith("This"));
				} else {
					delta(selectedVariable, true, false);
				}
			}
		};
		deltaAction.setText("Extract Delta");
		deltaAction.setToolTipText("Extract Delta");
		
		deltaActionForCollection = new Action() {
			@Override
			public void run() {
//				InputDialog inputContainerIdDialog = new InputDialog(null, "Extract Delta for Collection", "Input cotainer id", "87478208", null);
				InputDialog inputContainerIdDialog = new InputDialog(null, "Extract Delta for Collection", "Input cotainer id", "155140910", null);
				if (inputContainerIdDialog.open() != InputDialog.OK) return;
				String containerId = inputContainerIdDialog.getValue();
//				InputDialog inputContainerTypeDialog = new InputDialog(null, "Extract Delta for Collection", "Input cotainer type", "java.util.LinkedHashSet", null);
				InputDialog inputContainerTypeDialog = new InputDialog(null, "Extract Delta for Collection", "Input cotainer type", "java.util.ArrayList", null);
				if (inputContainerTypeDialog.open() != InputDialog.OK) return;
				String containerType = inputContainerTypeDialog.getValue();
				String valueId = selectedVariable.getId();
				String valueType = selectedVariable.getClassName();
				TracePoint tp = DebuggingController.getInstance().getCurrentTp();
				Variable variable = new Variable("tmp", containerType, containerId, valueType, valueId, tp, false);				
				delta(variable, true, false);
			}
		};
		deltaActionForCollection.setText("Extract Delta for Collection");
		deltaActionForCollection.setToolTipText("Extract Delta for Collection");		
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
				manager.add(jumpAction);
				manager.add(deltaAction);
				manager.add(deltaActionForCollection);
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
	
	private void delta(Variable variable, boolean isCollection, boolean isForThisToAnother) {
		AbstractAnalyzer analyzer = TraceDebuggerPlugin.getAnalyzer();
		if (analyzer instanceof DeltaExtractionAnalyzer) {
			DeltaExtractionAnalyzer deltaAnalyzer = (DeltaExtractionAnalyzer)analyzer;					
			IWorkbench workbench = PlatformUI.getWorkbench();
			IWorkbenchPage workbenchPage = workbench.getActiveWorkbenchWindow().getActivePage();
			try {
				// note: 同一ビューを複数開くテスト
				String subIdWithNewView = deltaAnalyzer.getNextDeltaMarkerSubId();
				DeltaMarkerView newDeltaMarkerView = (DeltaMarkerView)workbenchPage.showView(DeltaMarkerView.ID, subIdWithNewView, IWorkbenchPage.VIEW_ACTIVATE);
				if (isForThisToAnother) {
					deltaAnalyzer.extractDeltaForThisToAnother(variable, isCollection, newDeltaMarkerView, subIdWithNewView);	
				} else {
					deltaAnalyzer.extractDelta(variable, isCollection, newDeltaMarkerView, subIdWithNewView);					
				}
				TracePoint coordinatorPoint = newDeltaMarkerView.getCoordinatorPoint();
				TracePoint creationPoint = newDeltaMarkerView.getCreationPoint();
				DebuggingController controller = DebuggingController.getInstance();
				controller.jumpToTheTracePoint(creationPoint, false);

				DeltaMarkerManager deltaMarkerManager = newDeltaMarkerView.getDeltaMarkerManager();
				markAndExpandVariablesByDeltaMarkers(deltaMarkerManager.getMarkers());
				MethodExecution coordinatorME = coordinatorPoint.getMethodExecution();
				MethodExecution bottomME = newDeltaMarkerView.getCreationPoint().getMethodExecution();
				CallStackView callStackView = (CallStackView)getOtherView(CallStackView.ID);
				callStackView.highlight(coordinatorME);
				CallTreeView callTreeView = (CallTreeView)getOtherView(CallTreeView.ID);
				callTreeView.update(deltaMarkerManager);
				callTreeView.highlight(bottomME);
				TracePointsView tracePointsView = (TracePointsView)getOtherView(TracePointsView.ID);
				tracePointsView.addTracePoint(creationPoint);
			} catch (PartInitException e) {
				e.printStackTrace();
			}
		}		
	}
	
	public void updateVariablesByTracePoint(TracePoint tp, boolean isReturned) {
		updateVariablesByTracePoint(null, tp, isReturned);
	}
	
	public void updateVariablesByTracePoint(TracePoint from, TracePoint to, boolean isReturned) {
		variables.updateAllObjectDataByTracePoint(from, to, isReturned);
		viewer.setInput(variables.getVariablesTreeNodes());
	}

	public void markAndExpandVariablesByDeltaMarkers(Map<String, List<IMarker>> markers) {
		List<IMarker> srcSideDeltaMarkers = markers.get(DeltaMarkerManager.SRC_SIDE_DELTA_MARKER);
		List<IMarker> dstSideDeltaMarkers = markers.get(DeltaMarkerManager.DST_SIDE_DELTA_MARKER);
		List<IMarker> coordinatorMarker = markers.get(DeltaMarkerManager.COORDINATOR_DELTA_MARKER);
		if (srcSideDeltaMarkers != null) {
			markVariables(DeltaMarkerManager.SRC_SIDE_DELTA_MARKER, srcSideDeltaMarkers);	
		}
		if (dstSideDeltaMarkers != null) {
			markVariables(DeltaMarkerManager.DST_SIDE_DELTA_MARKER, dstSideDeltaMarkers);	
		}
		if (coordinatorMarker != null) {
			markVariables(DeltaMarkerManager.COORDINATOR_DELTA_MARKER, coordinatorMarker);	
		}
		viewer.refresh();
		expandAllMarkedNodes();
	}
	
	private void markVariables(String markerId, List<IMarker> markerList) {
		Set<String> idSet = new HashSet<>();
		Map<String, Object> additionalAttributesForVariables = new HashMap<>();
		additionalAttributesForVariables.put("markerId", markerId);
		for (IMarker marker : markerList) {
			try {
				Object data = marker.getAttribute(DeltaMarkerManager.DELTA_MARKER_ATR_DATA);
				if (data instanceof Alias) {
					idSet.add(((Alias)data).getObjectId());
				} else if (data instanceof MethodExecution) {
					idSet.add(((MethodExecution)data).getThisObjId());
				}
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
		variables.addAdditionalAttributes(idSet, additionalAttributesForVariables);
	}
	
	private void expandAllMarkedNodes() {
		Set<TreeNode> expandedNodes = new HashSet<>();
		for (TreeItem item : viewer.getTree().getItems()) {
			Object obj = item.getData();
			if (!(obj instanceof TreeNode)) continue;
			collectNodes((TreeNode)obj, expandedNodes);
		}
		viewer.setExpandedElements(expandedNodes.toArray(new Object[expandedNodes.size()]));
	}
	
	private void collectNodes(TreeNode node, final Set<TreeNode> expandedNodes) {
		Object value = node.getValue();
		if (!(value instanceof Variable)) return;
		Variable variable = (Variable)value;
		if (variable.getAdditionalAttribute("markerId") != null) {
			TreeNode parent = node.getParent();
			if (parent != null) {
				expandedNodes.add(parent);				
			}
		}
		TreeNode[] children = node.getChildren();
		if (children == null) return;
		for (TreeNode child : children) {
			collectNodes(child, expandedNodes);
		}
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

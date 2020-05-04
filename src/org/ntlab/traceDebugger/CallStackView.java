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
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeNode;
import org.eclipse.jface.viewers.TreeNodeContentProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.ui.ISharedImages;
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
import org.ntlab.traceDebugger.analyzerProvider.DeltaExtractionAnalyzer;
import org.ntlab.traceDebugger.analyzerProvider.DeltaMarkerManager;

public class CallStackView extends ViewPart {
	private TreeViewer viewer;
	private IAction refreshAction;
	private IAction deltaAction;
	private CallStackModel selectionCallStackModel;
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
						selectionCallStackModel = callStackModel;
						MethodExecution methodExecution = callStackModel.getMethodExecution();
						TracePoint tp = callStackModel.getTracePoint();
						JavaEditorOperator.openSrcFileOfMethodExecution(methodExecution, callStackModel.getCallLineNo());
						CallTreeView callTreeView = (CallTreeView)(getOtherView(CallTreeView.ID));
						callTreeView.highlight(methodExecution);

						VariableView variableView = (VariableView)(getOtherView(VariableView.ID));
						variableView.updateVariablesByTracePoint(tp, false);						
						AbstractAnalyzer analyzer = TraceDebuggerPlugin.getAnalyzer();
						if (analyzer instanceof DeltaExtractionAnalyzer) {
							DeltaMarkerView deltaMarkerView = ((DeltaExtractionAnalyzer)analyzer).getActiveDeltaMarkerView();
							if (deltaMarkerView != null) {
								DeltaMarkerManager deltaMarkerManager = deltaMarkerView.getDeltaMarkerManager();
								Map<String, List<IMarker>> deltaMarkers = deltaMarkerManager.getMarkers();
								if (deltaMarkers != null) {
									variableView.markAndExpandVariablesByDeltaMarkers(deltaMarkers);	
								}
							}
						}
					}
				}
			}
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
		refreshAction = new Action() {
			@Override
			public void run() {
				refresh();
			}
		};
		refreshAction.setText("refresh");
		refreshAction.setToolTipText("refresh");
		refreshAction.setImageDescriptor(PlatformUI.getWorkbench().getSharedImages().getImageDescriptor(ISharedImages.IMG_ELCL_SYNCED));
		
		deltaAction = new Action() {
			@Override
			public void run() {
				if (selectionCallStackModel == null) return;
				MethodExecution callee = selectionCallStackModel.getMethodExecution();
				MethodExecution caller = callee.getParent();
				String callerClassName = caller.getThisClassName();
				String callerId = caller.getThisObjId();
				String calleeClassName = callee.getThisClassName();
				String calleeId = callee.getThisObjId();
				TracePoint before = callee.getCallerTracePoint();
				Variable variable = new Variable("tmp", callerClassName, callerId, calleeClassName, calleeId, before, false);				
				delta(variable, false);
			}
		};
		deltaAction.setText("Extract Delta");
		deltaAction.setToolTipText("Extract Delta");		
	}
	
	private void createToolBar() {
		IToolBarManager mgr = getViewSite().getActionBars().getToolBarManager();
		mgr.add(refreshAction);
	}
	
	private void createMenuBar() {
		IMenuManager mgr = getViewSite().getActionBars().getMenuManager();
		mgr.add(refreshAction);
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
		refresh();
	}
	
	public Map<String, List<CallStackModel>> getCallStackModels() {
		return callStackModels.getAllCallStacks();
	}
	
	public void highlight(MethodExecution methodExecution) {
		callStackModels.highlight(methodExecution);
		viewer.refresh();
	}
	
	private void delta(Variable variable, boolean isCollection) {
		AbstractAnalyzer analyzer = TraceDebuggerPlugin.getAnalyzer();
		if (analyzer instanceof DeltaExtractionAnalyzer) {
			DeltaExtractionAnalyzer deltaAnalyzer = (DeltaExtractionAnalyzer)analyzer;					
			IWorkbench workbench = PlatformUI.getWorkbench();
			IWorkbenchPage workbenchPage = workbench.getActiveWorkbenchWindow().getActivePage();
			try {
				// note: 同一ビューを複数開くテスト
				String subIdWithNewView = deltaAnalyzer.getNextDeltaMarkerSubId();
				DeltaMarkerView newDeltaMarkerView = (DeltaMarkerView)workbenchPage.showView(DeltaMarkerView.ID, subIdWithNewView, IWorkbenchPage.VIEW_ACTIVATE);
				deltaAnalyzer.extractDeltaForThisToAnother(variable, isCollection, newDeltaMarkerView, subIdWithNewView);
				TracePoint coordinatorPoint = newDeltaMarkerView.getCoordinatorPoint();
				TracePoint creationPoint = newDeltaMarkerView.getCreationPoint();
				DebuggingController controller = DebuggingController.getInstance();
				controller.jumpToTheTracePoint(creationPoint, false);
				
				DeltaMarkerManager deltaMarkerManager = newDeltaMarkerView.getDeltaMarkerManager();
				VariableView variableView = (VariableView)getOtherView(VariableView.ID);
				variableView.markAndExpandVariablesByDeltaMarkers(deltaMarkerManager.getMarkers());
				MethodExecution coordinatorME = coordinatorPoint.getMethodExecution();
				MethodExecution bottomME = newDeltaMarkerView.getCreationPoint().getMethodExecution();
				highlight(coordinatorME);
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

	private IViewPart getOtherView(String viewId) {
		IWorkbenchPage workbenchPage = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
		try {
			return workbenchPage.showView(viewId);
		} catch (PartInitException e) {
			throw new RuntimeException(e);
		}	
	}
}

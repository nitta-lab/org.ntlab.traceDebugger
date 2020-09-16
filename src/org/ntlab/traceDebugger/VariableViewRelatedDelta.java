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
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.TreeNode;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
import org.ntlab.traceDebugger.Variable.VariableType;
import org.ntlab.traceDebugger.analyzerProvider.Alias;
import org.ntlab.traceDebugger.analyzerProvider.DeltaMarkerManager;
import org.ntlab.traceDebugger.analyzerProvider.VariableUpdatePointFinder;

public class VariableViewRelatedDelta extends VariableView {
	protected IAction jumpAction;
	private IAction deltaActionForContainerToComponent;
	private IAction deltaActionForThisToAnother;
	public static final String ID = "org.ntlab.traceDebugger.variableViewRelatedDelta";

	public VariableViewRelatedDelta() {
		// TODO Auto-generated constructor stub
		System.out.println("VariableViewRelatedDeltaクラスが生成されたよ!");
	}

	@Override
	public void createPartControl(Composite parent) {
		// TODO Auto-generated method stub
		System.out.println("VariableViewRelatedDelta#createPartControl(Composite)が呼ばれたよ!");
		super.createPartControl(parent);
		TraceDebuggerPlugin.setActiveView(ID, this);
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

	@Override
	protected void createActions() {
		super.createActions();
		jumpAction = new Action() {
			public void run() {
				TracePoint before = DebuggingController.getInstance().getCurrentTp();
				TracePoint jumpPoint = findJumpPoint(selectedVariable, before);
				if (jumpPoint == null) return;
				DebuggingController controller = DebuggingController.getInstance();
				controller.jumpToTheTracePoint(jumpPoint, false);
			}
		};

		deltaActionForContainerToComponent = new Action() {
			@Override
			public void run() {				
				DeltaMarkerView newDeltaMarkerView = (DeltaMarkerView)TraceDebuggerPlugin.createNewView(DeltaMarkerView.ID, IWorkbenchPage.VIEW_ACTIVATE);
				newDeltaMarkerView.extractDeltaForContainerToComponent(selectedVariable);
			}
		};

		deltaActionForThisToAnother = new Action() {
			@Override
			public void run() {
				TracePoint before = selectedVariable.getBeforeTracePoint();
				String thisId = before.getMethodExecution().getThisObjId();
				String thisClassName = before.getMethodExecution().getThisClassName();
				String anotherId;
				String anotherClassName;
				if (selectedVariable.getVariableType().isContainerSide()) {
					anotherId = selectedVariable.getContainerId();
					anotherClassName = selectedVariable.getContainerClassName();
				} else {
					anotherId = selectedVariable.getValueId();
					anotherClassName = selectedVariable.getValueClassName();
				}
				DeltaMarkerView newDeltaMarkerView = (DeltaMarkerView)TraceDebuggerPlugin.createNewView(DeltaMarkerView.ID, IWorkbenchPage.VIEW_ACTIVATE);
				newDeltaMarkerView.extractDeltaForThisToAnother(thisId, thisClassName, anotherId, anotherClassName, before);
			}
		};
	}
	
	private TracePoint findJumpPoint(Variable variable, TracePoint before) {
		VariableType variableType = selectedVariable.getVariableType();
		if (variableType.equals(VariableType.USE_VALUE)) {
			String containerId = selectedVariable.getContainerId();
			String fieldName = selectedVariable.getFullyQualifiedVariableName();
			return VariableUpdatePointFinder.getInstance().getPoint(containerId, fieldName, before);
		} else if (variableType.equals(VariableType.USE_RETURN)) {
			return findJumpPointWithReturnValue(variable, before);
		}
		return null;
	}
	
	private TracePoint findJumpPointWithReturnValue(Variable variable, TracePoint before) {
		TracePoint tp = null;
		String receiverId = selectedVariable.getContainerId();
		String valueId = selectedVariable.getValueId();
		String receiverClassName = selectedVariable.getContainerClassName();
		VariableUpdatePointFinder finder = VariableUpdatePointFinder.getInstance();

		// note: イテレータの場合はそのイテレータの取得元のコレクションのIDを取りにいく
		if (receiverClassName.contains("Iterator") || receiverClassName.contains("Itr")
				|| receiverClassName.contains("Collections$UnmodifiableCollection$1")) {
			tp = finder.getIteratorPoint(receiverId); // イテレータ取得時のポイントを取得
			if (tp == null) return null;
			MethodInvocation mi = ((MethodInvocation)tp.getStatement()); 
			receiverId = mi.getCalledMethodExecution().getThisObjId(); // イテレータの取得元のコレクションのID
			receiverClassName = mi.getCalledMethodExecution().getThisClassName(); // イテレータの取得元のコレクションのクラス名
		}
		
		// note: ジャンプ先となる オブジェクトの代入ポイントもしくはコレクションへの追加ポイントを取得
		tp = finder.getDefinitionInvocationPoint(receiverId, valueId, before);
		
		// note: ジャンプ先のポイントが見つからなかった場合で、かつコレクションへの追加ポイントを探している場合はコレクションの乗せ換え元で追加していないかを探しに行く
		if (tp == null && receiverClassName.startsWith("java.util.")) {
			String afterCollectionId = receiverId;
			while (true) {
				tp = finder.getTransferCollectionPoint(afterCollectionId, before);
				if (tp == null) break; // コレクションの乗せ換え元がそれ以上ない場合
				MethodInvocation mi = ((MethodInvocation)tp.getStatement()); 
				String fromCollectionId = mi.getCalledMethodExecution().getArguments().get(0).getId();
				tp = finder.getDefinitionInvocationPoint(fromCollectionId, valueId, before);
				if (tp != null) break; // コレクションの乗せ換え元でオブジェクトの追加が見つかった場合
				afterCollectionId = fromCollectionId;
			}
		}
		return tp;
	}

	@Override
	protected void createPopupMenu() {
		MenuManager menuMgr = new MenuManager("#PopupMenu");
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			@Override
			public void menuAboutToShow(IMenuManager manager) {
				// 右クリックする度に呼び出される
				VariableType variableType = selectedVariable.getVariableType();
				if (variableType.equals(VariableType.USE_VALUE)) {
					manager.add(jumpAction);
					String msg = TraceDebuggerPlugin.isJapanese() ? "値の代入時点に飛ぶ" : "Back to Value Stored Moment";
					jumpAction.setText(msg);
					jumpAction.setToolTipText(msg);
				} else if (variableType.equals(VariableType.USE_RETURN)) {
					manager.add(jumpAction);
					if (updateDeltaActionForThisToAnotherTexts(selectedVariable)) {
						manager.add(deltaActionForThisToAnother);
					}
					String msg = TraceDebuggerPlugin.isJapanese() ? "オブジェクトの追加時点に飛ぶ" : "Back to Object Added Moment";
					jumpAction.setText(msg);
					jumpAction.setToolTipText(msg);
				} else if (variableType.isDef()) {
					if (updateDeltaActionForContainerToComponentTexts(selectedVariable)) {
						manager.add(deltaActionForContainerToComponent);
					}
					if (updateDeltaActionForThisToAnotherTexts(selectedVariable)) {
						String text1 = deltaActionForThisToAnother.getText();
						String text2 = deltaActionForContainerToComponent.getText();
						if (!(text1.equals(text2))) {
							manager.add(deltaActionForThisToAnother);
						}
					}
				} else if (variableType.equals(VariableType.PARAMETER)) {
					if (updateDeltaActionForThisToAnotherTexts(selectedVariable)) {
						manager.add(deltaActionForThisToAnother);
					}
				}
				manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
			}
		});
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}

	private boolean updateDeltaActionForContainerToComponentTexts(Variable variable) {
		String valueId = selectedVariable.getValueId();
		String valueClassName = selectedVariable.getValueClassName();
		valueClassName = valueClassName.substring(valueClassName.lastIndexOf(".") + 1);
		if (!(valueId.isEmpty()) && !(valueClassName.isEmpty())) {
			String containerId = selectedVariable.getContainerId();
			String containerClassName = selectedVariable.getContainerClassName();
			if (containerId != null  && containerClassName != null) {
				containerClassName = containerClassName.substring(containerClassName.lastIndexOf(".") + 1);
				String msg = TraceDebuggerPlugin.isJapanese() ? "オブジェクトの接近過程抽出" : "Extract Process to Relate";
				String textForContainerToComponent = String.format("%s [ %s (id = %s) -> %s (id = %s) ]", msg, containerClassName, containerId, valueClassName, valueId);
				deltaActionForContainerToComponent.setText(textForContainerToComponent);
				deltaActionForContainerToComponent.setToolTipText(textForContainerToComponent);
				return true;
			}
		}
		deltaActionForContainerToComponent.setText("");
		deltaActionForContainerToComponent.setToolTipText("");
		return false;
	}

	private boolean updateDeltaActionForThisToAnotherTexts(Variable variable) {
		VariableType variableType = variable.getVariableType();
		String anotherId;
		String anotherClassName;
		if (variableType.isContainerSide()) {
			anotherId = selectedVariable.getContainerId();
			anotherClassName = selectedVariable.getContainerClassName();
		} else {
			anotherId = selectedVariable.getValueId();
			anotherClassName = selectedVariable.getValueClassName();
		}
		anotherClassName = anotherClassName.substring(anotherClassName.lastIndexOf(".") + 1);		
		TracePoint before = selectedVariable.getBeforeTracePoint();
		String thisId = before.getMethodExecution().getThisObjId();
		String thisClassName = before.getMethodExecution().getThisClassName();
		if (thisId != null && thisClassName != null) {			
			thisClassName = thisClassName.substring(thisClassName.lastIndexOf(".") + 1);
			String msg = TraceDebuggerPlugin.isJapanese() ? "オブジェクトの接近過程抽出" : "Extract Process to Relate";
			String textForThisToAnother = String.format("%s [ %s (id = %s) -> %s (id = %s) ]", msg, thisClassName, thisId, anotherClassName, anotherId);
			deltaActionForThisToAnother.setText(textForThisToAnother);
			deltaActionForThisToAnother.setToolTipText(textForThisToAnother);
			return true;
		} else {
			deltaActionForThisToAnother.setText("");
			deltaActionForThisToAnother.setToolTipText("");
			return false;
		}
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
	
	public void removeDeltaMarkers(Map<String, List<IMarker>> markers) {
		List<IMarker> srcSideDeltaMarkers = markers.get(DeltaMarkerManager.SRC_SIDE_DELTA_MARKER);
		List<IMarker> dstSideDeltaMarkers = markers.get(DeltaMarkerManager.DST_SIDE_DELTA_MARKER);
		List<IMarker> coordinatorMarker = markers.get(DeltaMarkerManager.COORDINATOR_DELTA_MARKER);
		if (srcSideDeltaMarkers != null) {
			markVariables("", srcSideDeltaMarkers);	
		}
		if (dstSideDeltaMarkers != null) {
			markVariables("", dstSideDeltaMarkers);	
		}
		if (coordinatorMarker != null) {
			markVariables("", coordinatorMarker);	
		}
		viewer.refresh();
	}
}

package org.ntlab.traceDebugger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.viewers.TreeNode;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceDebugger.analyzerProvider.DeltaMarkerManager;

public class CallTreeModels {
	private TreeNode[] roots;
	private List<CallTreeModel> callTreeModelList = new ArrayList<>();
	private Map<MethodExecution, CallTreeModel> callTreeModelsMemo = new HashMap<>();
	
	public TreeNode[] getCallTreeModels() {
		return roots;
	}
	
	public List<CallTreeModel> getCallTreeModelList() {
		return callTreeModelList;
	}
	
	public void reset() {
		roots = new TreeNode[1];
		callTreeModelList.clear();
		callTreeModelsMemo.clear();
	}
	
//	public void update(MethodExecution from, MethodExecution to) {
//		reset();
//		createCallTreeModels(roots, 0, from, to);
//	}
//	
//	private boolean createCallTreeModels(TreeNode[] nodes, int index, MethodExecution me, final MethodExecution theLast) {
//		CallTreeModel callTreeModel = new CallTreeModel(me);
//		nodes[index] = new TreeNode(callTreeModel);
//		callTreeModelList.add(callTreeModel);
//		if (me.equals(theLast)) return true;
//		List<MethodExecution> children = me.getChildren();
//		TreeNode[] childNodes = new TreeNode[children.size()];
//		nodes[index].setChildren(childNodes);
//		for (int i = 0; i < children.size(); i++) {
//			MethodExecution child = children.get(i);
//			boolean isTheLast = createCallTreeModels(childNodes, i, child, theLast);
//			childNodes[i].setParent(nodes[index]);
////			if (isTheLast) return true;
//			if (isTheLast) {
//				for (int j = i + 1; j < children.size(); j++) {
//					childNodes[j] = new TreeNode(null);
//				}
//				return true;
//			}
//		}
//		return false;
//	}
	
	public void update(DeltaMarkerManager deltaMarkerManager) {
		reset();
		IMarker coordinatorMarker = deltaMarkerManager.getCoordinatorDeltaMarker();
		List<IMarker> srcSideMarkers = deltaMarkerManager.getMarkers().get(DeltaMarkerManager.SRC_SIDE_DELTA_MARKER);
		List<IMarker> dstSideMarkers = deltaMarkerManager.getMarkers().get(DeltaMarkerManager.DST_SIDE_DELTA_MARKER);
		IMarker bottomMarker = deltaMarkerManager.getBottomDeltaMarker();

		MethodExecution coordinatorME = deltaMarkerManager.getMethodExecution(coordinatorMarker);
		CallTreeModel coordinator = new CallTreeModel(coordinatorME);
		callTreeModelsMemo.put(coordinatorME, coordinator);
		for (IMarker srcSideMarker : srcSideMarkers) {
			MethodExecution me = deltaMarkerManager.getMethodExecution(srcSideMarker);
			CallTreeModel callTreeModel = new CallTreeModel(me);
			if (!(callTreeModelsMemo.containsKey(me))) {
				callTreeModelsMemo.put(me, callTreeModel);
				create(callTreeModel, coordinatorME);
			}
		}
		for (IMarker dstSideMarker : dstSideMarkers) {
			MethodExecution me = deltaMarkerManager.getMethodExecution(dstSideMarker);
			CallTreeModel callTreeModel = new CallTreeModel(me);
			if (!(callTreeModelsMemo.containsKey(me))) {
				callTreeModelsMemo.put(me, callTreeModel);
				create(callTreeModel, coordinatorME);
			}
		}
		MethodExecution bottomME = deltaMarkerManager.getMethodExecution(bottomMarker);
		CallTreeModel bottom = new CallTreeModel(bottomME);
		if (!(callTreeModelsMemo.containsKey(bottomME))) {
			callTreeModelsMemo.put(bottomME, bottom);
			create(bottom, coordinatorME);
		}
		createCallTreeModels(coordinator);
	}
	
	private void create(CallTreeModel currentModel, final MethodExecution coordinatorME) {
		MethodExecution currentME = currentModel.getMethodExecution();
		MethodExecution parentME = currentME.getParent();
		if (parentME == null) return;
		CallTreeModel parentModel = callTreeModelsMemo.get(parentME);
		boolean isNewParentModel = (parentModel == null);
		if (isNewParentModel) {
			parentModel = new CallTreeModel(parentME);
			callTreeModelsMemo.put(parentME, parentModel);
		}
		currentModel.setParent(parentModel);
		parentModel.addChildren(currentModel);
		if (isNewParentModel && !(parentME.equals(coordinatorME))) {
			create(parentModel, coordinatorME);
		}
	}
	
	private void createCallTreeModels(CallTreeModel coordinator) {
		roots = new TreeNode[1];
		callTreeModelList.clear();
		create(roots, 0, coordinator, null);
	}
	
	private void create(TreeNode[] nodes, int index, CallTreeModel callTreeModel, TreeNode parent) {
		callTreeModelList.add(callTreeModel);
		nodes[index] = new TreeNode(callTreeModel);
		nodes[index].setParent(parent);
		List<CallTreeModel> children = callTreeModel.getChildren();
		TreeNode[] childNodes = new TreeNode[children.size()];
		nodes[index].setChildren(childNodes);
		for (int i = 0; i < childNodes.length; i++) {
			CallTreeModel child = children.get(i);
			create(childNodes, i, child, nodes[index]);
		}
	}	
}

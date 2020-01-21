package org.ntlab.traceDebugger;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.viewers.TreeNode;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;

public class CallTreeModels {
	private TreeNode[] roots;
	private List<CallTreeModel> callTreeModelList = new ArrayList<>();
	
	public TreeNode[] getCallTreeModels() {
		return roots;
	}
	
	public List<CallTreeModel> getCallTreeModelList() {
		return callTreeModelList;
	}
	
	public void update(MethodExecution from, MethodExecution to) {
		reset();
		createCallTreeModels(roots, 0, from, to);
	}
	
	private boolean createCallTreeModels(TreeNode[] nodes, int index, MethodExecution me, final MethodExecution theLast) {
		CallTreeModel callTreeModel = new CallTreeModel(me);
		nodes[index] = new TreeNode(callTreeModel);
		callTreeModelList.add(callTreeModel);
		if (me.equals(theLast)) return true;
		List<MethodExecution> children = me.getChildren();
		TreeNode[] childNodes = new TreeNode[children.size()];
		nodes[index].setChildren(childNodes);
		for (int i = 0; i < children.size(); i++) {
			MethodExecution child = children.get(i);
			boolean isTheLast = createCallTreeModels(childNodes, i, child, theLast);
			childNodes[i].setParent(nodes[index]);
			if (isTheLast) return true;
		}
		return false;
	}
	
	public void reset() {
		roots = new TreeNode[1];
		callTreeModelList.clear();
	}
}

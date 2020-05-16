package org.ntlab.traceDebugger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jface.viewers.TreeNode;

public class MyTreeNode extends TreeNode {
	private List<MyTreeNode> children = new ArrayList<>();
	
	public MyTreeNode(Object value) {
		super(value);
	}

	@Override
	public MyTreeNode[] getChildren() {
		if (children != null && children.size() == 0) {
			return null;
		}
		return children.toArray(new MyTreeNode[children.size()]);
	}
	
	public List<MyTreeNode> getChildList() {
		return children;
	}

	@Override
	public boolean hasChildren() {
		return children != null && children.size() > 0;
	}

	public void setChildren(final MyTreeNode[] children) {
		this.children = new ArrayList<MyTreeNode>(Arrays.asList(children));
	}
	
	public void setChildList(final List<MyTreeNode> children) {
		this.children = children;
	}
}

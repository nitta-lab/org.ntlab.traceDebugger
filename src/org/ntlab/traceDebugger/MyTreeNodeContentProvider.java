package org.ntlab.traceDebugger;

import java.util.List;

import org.eclipse.jface.viewers.TreeNodeContentProvider;

public class MyTreeNodeContentProvider extends TreeNodeContentProvider {
	@Override
	public Object[] getElements(final Object inputElement) {
		if (inputElement instanceof List<?>) {
			List<?> list = (List<?>)inputElement;
			MyTreeNode[] nodes = list.toArray(new MyTreeNode[list.size()]);
			return super.getElements(nodes);
		}
		return new Object[0];
	}
}

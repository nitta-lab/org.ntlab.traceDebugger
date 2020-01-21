package org.ntlab.traceDebugger;

import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeNode;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

public class CallTreeLabelProvider extends LabelProvider implements IColorProvider {
	@Override
	public String getText(Object element) {
		if (element instanceof TreeNode) {
			Object value = ((TreeNode)element).getValue();
			if (value instanceof CallTreeModel) {
				CallTreeModel callTreeModel = (CallTreeModel)value;
				return callTreeModel.getCallTreeSignature();
			}
		}
		return "";
	}

	@Override
	public Image getImage(Object element) {
		if (element instanceof TreeNode) {
			return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_ELEMENT);
		}
		return null;
	}

	@Override
	public Color getForeground(Object element) {
		return null;
	}

	@Override
	public Color getBackground(Object element) {
		if (element instanceof TreeNode) {
			Object value = ((TreeNode)element).getValue();
			if (value instanceof CallTreeModel) {
				CallTreeModel callTreeModel = (CallTreeModel)value;
				if (callTreeModel.isHighlighting()) {
					return new Color(Display.getDefault(), 0, 192, 255);
				}
			}
		}
		return null;
	}
}

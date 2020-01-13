package org.ntlab.traceDebugger;

import org.eclipse.jface.viewers.IColorProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeNode;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

public class CallStackLabelProvider extends LabelProvider implements IColorProvider {
	
	@Override
	public String getText(Object element) {
		if (element instanceof TreeNode) {
			Object value = ((TreeNode)element).getValue();
			if (value instanceof String) {
				String threadId = (String)value;
				return "ThreadID: " + threadId;
			}
			if (value instanceof CallStackModel) {
				CallStackModel callStackModel = (CallStackModel)value;
				StringBuilder text = new StringBuilder();
				text.append(callStackModel.getCallStackSignature());
				text.append(" line: ");
				text.append(callStackModel.getCallLineNo());
				return text.toString();
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
			if (value instanceof CallStackModel) {
				CallStackModel callStackModel = (CallStackModel)value;
				if (callStackModel.isHighlighting()) {
					return Display.getDefault().getSystemColor(SWT.COLOR_GREEN);
				}
			}
		}
		return null;
	}
}

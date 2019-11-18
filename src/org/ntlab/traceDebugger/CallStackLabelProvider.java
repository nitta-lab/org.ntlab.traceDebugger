package org.ntlab.traceDebugger;

import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeNode;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ThreadInstance;

public class CallStackLabelProvider extends LabelProvider {
	
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
}

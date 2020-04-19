package org.ntlab.traceDebugger;

import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

public class BreakPointLabelProvider  extends LabelProvider implements ITableLabelProvider {

	@Override
	public String getColumnText(Object element, int columnIndex) {
		if (element instanceof TraceBreakPoint) {
			TraceBreakPoint tbp = (TraceBreakPoint)element;
			switch (columnIndex) {
			case 0:
				return tbp.isAvailable() ? "True" : "False";
			case 1:
				return String.valueOf(tbp.getLineNo());
			case 2:
				return tbp.getReadableSignature();
			}
		}
		return "テスト用テキスト" + columnIndex;
	}
	
	@Override
	public Image getColumnImage(Object element, int columnIndex) {
		return getImage(element);
	}
	
	@Override
	public Image getImage(Object element) {
		return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_ELEMENT);
	}	
}

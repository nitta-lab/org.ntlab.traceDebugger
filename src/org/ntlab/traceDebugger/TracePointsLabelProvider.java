package org.ntlab.traceDebugger;

import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class TracePointsLabelProvider extends LabelProvider implements ITableLabelProvider {

	@Override
	public String getColumnText(Object element, int columnIndex) {
		if (element instanceof TracePoint) {
			TracePoint tp = (TracePoint)element;
			switch (columnIndex) {
			case 0:
				return String.valueOf(tp.getStatement().getLineNo());
			case 1:
				return tp.getMethodExecution().getSignature();
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
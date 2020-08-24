package org.ntlab.traceDebugger;

import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Trace;
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
				return getReadableSignature(tp.getMethodExecution());
			}
		}
		return "�e�X�g�p�e�L�X�g" + columnIndex;
	}
	
	@Override
	public Image getColumnImage(Object element, int columnIndex) {
//		return getImage(element);
		return null;
	}
	
//	@Override
//	public Image getImage(Object element) {
//		return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_ELEMENT);
//	}	
	
	private String getReadableSignature(MethodExecution methodExecution) {
		String signature = methodExecution.getSignature();
		String objectType = methodExecution.getThisClassName();
		objectType = objectType.substring(objectType.lastIndexOf(".") + 1);
		boolean isConstructor = methodExecution.isConstructor();
		String declaringType = Trace.getDeclaringType(signature, isConstructor);
		declaringType = declaringType.substring(declaringType.lastIndexOf(".") + 1);
		String methodName = Trace.getMethodName(signature);
		String args = "(";
		String delimiter = "";
		String[] argArray = signature.split("\\(")[1].split(",");
		for (String arg : argArray) {
			args += (delimiter + arg.substring(arg.lastIndexOf(".") + 1));
			delimiter = ", ";
		}
		
		StringBuilder sb = new StringBuilder();
		sb.append(objectType);
		if (!declaringType.equals(objectType)) {
			sb.append("(" + declaringType + ")");
		}
		sb.append("." + methodName + args);
		return sb.toString();
	}
}
package org.ntlab.traceDebugger;

import org.eclipse.jface.viewers.ITableColorProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.TreeNode;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.ntlab.traceDebugger.analyzerProvider.DeltaMarkerManager;

public class VariableLabelProvider extends LabelProvider implements ITableLabelProvider, ITableColorProvider {
	@Override
	public String getColumnText(Object element, int columnIndex) {
		if (element instanceof TreeNode) {
			Object value = ((TreeNode)element).getValue();
			if (value instanceof Variable) {
				Variable variableData = (Variable)value;
				switch (columnIndex) {
				case 0:
					String variableName = variableData.getVariableName();
					if (variableName.contains("[")) {
						return variableName.substring(variableName.lastIndexOf("["));
					} else if (variableName.contains(".")) {
						return variableName.substring(variableName.lastIndexOf(".") + 1);	
					}
					return variableName;
				case 1:
					return variableData.getClassName() + " (" + "id = " + variableData.getId() + ")";
				}				
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

	@Override
	public Color getForeground(Object element, int columnIndex) {
		return null;
	}

	@Override
	public Color getBackground(Object element, int columnIndex) {
		if (element instanceof TreeNode) {
			Object value = ((TreeNode)element).getValue();
			if (value instanceof Variable) {
				Variable variable = (Variable)value;
				Object markerId = variable.getAdditionalAttribute("markerId");
				if (!(markerId instanceof String)) return null;
				switch ((String)markerId) {
				case DeltaMarkerManager.SRC_SIDE_DELTA_MARKER:
					return new Color(Display.getDefault(), 255, 128, 0);
				case DeltaMarkerManager.DST_SIDE_DELTA_MARKER:
					return Display.getDefault().getSystemColor(SWT.COLOR_CYAN);
				case DeltaMarkerManager.COORDINATOR_DELTA_MARKER:
					return Display.getDefault().getSystemColor(SWT.COLOR_GREEN);
				}
			}
		}
		return null;
	}
}

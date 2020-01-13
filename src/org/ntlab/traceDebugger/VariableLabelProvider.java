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
				if (variable.isSrcSideRelatedDelta()) {
					return new Color(Display.getDefault(), 255, 128, 0);
				}
				if (variable.isDstSideRelatedDelta()) {
					return Display.getDefault().getSystemColor(SWT.COLOR_CYAN); 
				}
			}
		}
		return null;
	}
}

package org.ntlab.traceDebugger;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
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
import org.ntlab.traceDebugger.analyzerProvider.Alias;
import org.ntlab.traceDebugger.analyzerProvider.DeltaMarkerManager;

public class DeltaMarkerLabelProvider extends LabelProvider implements ITableLabelProvider, ITableColorProvider {

	@Override
	public String getColumnText(Object element, int columnIndex) {
		if (element instanceof TreeNode) {
			Object value = ((TreeNode)element).getValue();
			if (value instanceof String) return (columnIndex == 0) ? (String)value : null;
			if (value instanceof IMarker) {
				IMarker marker = (IMarker)value;
				try {
					switch (columnIndex) {
					case 0:
						return "" + marker.getAttribute(IMarker.MESSAGE);
					case 1:
						Object objectId = marker.getAttribute("objectId");
						return (objectId != null) ? "" + objectId : null;
					case 2:
						Object objectType = marker.getAttribute("objectType");
						return (objectType != null) ? "" + objectType : null;
					case 3:
						Object aliasType = marker.getAttribute("aliasType");
						return (aliasType != null) ? ((Alias.AliasType)aliasType).toString() : null;
					case 4:
						return marker.getResource().toString();
					case 5:
						return "line " + marker.getAttribute(IMarker.LINE_NUMBER);
					case 6:
						String markerType = marker.getType();
						return markerType.substring(markerType.lastIndexOf(".") + 1);
					}
				} catch(CoreException e) {
					e.printStackTrace();
				}
			}
		}
		return "テスト用テキスト";
	}
	
	@Override
	public Image getColumnImage(Object element, int columnIndex) {
		if (element instanceof TreeNode) {
			Object value = ((TreeNode)element).getValue(); 
			if (value instanceof String && columnIndex == 0) {
				return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_ELEMENT);	
			}
		}
		return null;
	}

	@Override
	public Color getForeground(Object element, int columnIndex) {
		return null;
	}

	@Override
	public Color getBackground(Object element, int columnIndex) {
		if (element instanceof TreeNode) {
			Object value = ((TreeNode)element).getValue();
			if (value instanceof String) {
				String str = (String)value;
				if (str.contains("Bottom")) {
					return new Color(Display.getDefault(), 255, 128, 128);
				} else if (str.contains("Coordinator")) {
					return Display.getDefault().getSystemColor(SWT.COLOR_GREEN);
				} else if (str.contains("Src")) {
					return new Color(Display.getDefault(), 255, 128, 0);
				} else if (str.contains("Dst")) {
					return Display.getDefault().getSystemColor(SWT.COLOR_CYAN);
				}
				return null;
			}
			if (value instanceof IMarker) {
				IMarker marker = (IMarker)value;
				try {
					String markerType = marker.getType();
					if (markerType.equals(DeltaMarkerManager.BOTTOM_DELTA_MARKER)) {
						return new Color(Display.getDefault(), 255, 128, 128);
					} else if (markerType.equals(DeltaMarkerManager.COORDINATOR_DELTA_MARKER)) {
						return Display.getDefault().getSystemColor(SWT.COLOR_GREEN);
					} else if (markerType.equals(DeltaMarkerManager.SRC_SIDE_DELTA_MARKER)) {
						return new Color(Display.getDefault(), 255, 128, 0);			
					} else if (markerType.equals(DeltaMarkerManager.DST_SIDE_DELTA_MARKER)) {
						return Display.getDefault().getSystemColor(SWT.COLOR_CYAN);
					}
				} catch (CoreException e) {
					e.printStackTrace();
				}
			}
		}
		return null;
	}	
}

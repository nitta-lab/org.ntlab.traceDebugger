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
						Object objectId = marker.getAttribute(DeltaMarkerManager.DELTA_MARKER_ATR_OBJECT_ID);
						return (objectId != null) ? objectId.toString() : null;
					case 2:
						Object objectType = marker.getAttribute(DeltaMarkerManager.DELTA_MARKER_ATR_OBJECT_TYPE);
						if (objectType == null) return null;
						StringBuilder simpleObjectTypeName = new StringBuilder();
						String[] simpleNames = objectType.toString().split(" -> ");
						simpleObjectTypeName.append(simpleNames[0].substring(simpleNames[0].lastIndexOf(".") + 1));
						if (simpleNames.length == 2) {
							simpleObjectTypeName.append(" -> ");
							simpleObjectTypeName.append(simpleNames[1].substring(simpleNames[1].lastIndexOf(".") + 1));
						}
						return simpleObjectTypeName.toString();
					case 3:
						Object obj = marker.getAttribute(DeltaMarkerManager.DELTA_MARKER_ATR_ALIAS_TYPE);
						if (obj == null) return null;
						// note: スネークケースをパスカルケース(ただし単語間を空白で区切る)に変える
						String aliasType = obj.toString();
						aliasType = aliasType.toLowerCase().replace("_", " ");
						StringBuilder sb = new StringBuilder();
						for (int index = -1;;) {
							sb.append(aliasType.substring(index + 1, index + 2).toUpperCase());
							int nextIndex = aliasType.indexOf(" ", index + 1);
							if (nextIndex == -1) {
								sb.append(aliasType.substring(index + 2));
								break;
							} else {
								sb.append(aliasType.substring(index + 2, nextIndex + 1));
								index = nextIndex;
							}
						}
						aliasType = sb.toString();
						return aliasType;
					case 4:
						String resource = marker.getResource().toString();
						return resource.substring(resource.lastIndexOf("/") + 1);
					case 5:
						return "line: " + marker.getAttribute(IMarker.LINE_NUMBER);
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
//		if (element instanceof TreeNode) {
//			Object value = ((TreeNode)element).getValue(); 
//			if (value instanceof String && columnIndex == 0) {
//				return PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJ_ELEMENT);	
//			}
//		}
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

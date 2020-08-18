package org.ntlab.traceDebugger;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

public class TraceDebuggerPerspectiveRelatedDelta implements IPerspectiveFactory {

	@Override
	public void createInitialLayout(IPageLayout layout) {
		// エディタの場所を取得
		String editorArea = layout.getEditorArea();

		// 右にブレークポイントのビューを配置
		IFolderLayout breakpointViewArea = layout.createFolder("BreakpointViewArea", IPageLayout.RIGHT, 0.5f, editorArea);
		breakpointViewArea.addView(BreakPointViewRelatedDelta.ID);

		// 左上にコールツリーのビューを配置
		IFolderLayout callTreeViewArea = layout.createFolder("CallTreeViewArea", IPageLayout.BOTTOM, 0.25f, "BreakpointViewArea");
		callTreeViewArea.addView(CallTreeView.ID);
		
		// 右下にトレースポイントのビューを配置
		IFolderLayout tracePointsViewArea = layout.createFolder("TracePointsViewArea", IPageLayout.BOTTOM, 0.5f, "CallTreeViewArea");
		tracePointsViewArea.addView(TracePointsView.ID);
		
		// 右上に変数のビューを配置
		IFolderLayout variableViewArea = layout.createFolder("VariableViewArea", IPageLayout.TOP, 0.25f, editorArea);
		variableViewArea.addView(VariableViewRelatedDelta.ID);
		
		// 左上にコールスタックのビューを配置
		IFolderLayout callStackViewArea = layout.createFolder("CallStackViewArea", IPageLayout.LEFT, 0.25f, "VariableViewArea");
		callStackViewArea.addView(CallStackViewRelatedDelta.ID);		
	}
}

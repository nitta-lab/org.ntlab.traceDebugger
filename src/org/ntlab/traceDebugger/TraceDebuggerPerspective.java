package org.ntlab.traceDebugger;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

public class TraceDebuggerPerspective implements IPerspectiveFactory {

	@Override
	public void createInitialLayout(IPageLayout layout) {
		// エディタの場所を取得
		String editorArea = layout.getEditorArea();

		// 右にブレークポイントのビューを配置
		IFolderLayout breakpointViewArea = layout.createFolder("BreakPointViewArea", IPageLayout.RIGHT, 0.5f, editorArea);
		breakpointViewArea.addView(BreakPointView.ID);
		
		// 左上にコールスタックのビューを配置
		IFolderLayout callStackViewArea = layout.createFolder("CallStackViewArea", IPageLayout.TOP, 0.25f, editorArea);
		callStackViewArea.addView(CallStackView.ID);
		
		// 右上に変数のビューを配置
		IFolderLayout variableViewArea = layout.createFolder("VariableViewArea", IPageLayout.TOP, 0.25f, "BreakPointViewArea");
		variableViewArea.addView(VariableView.ID);
	}
}

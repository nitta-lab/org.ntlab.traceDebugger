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
		IFolderLayout right = layout.createFolder("right", IPageLayout.RIGHT, 0.5f, editorArea);
		right.addView(BreakPointView.ID);
		
		// 左上にコールスタックのビューを配置
		IFolderLayout topLeft = layout.createFolder("topLeft", IPageLayout.TOP, 0.25f, editorArea);
		topLeft.addView(CallStackView.ID);
		
		// 右上に変数のビューを配置
		IFolderLayout topRight = layout.createFolder("topRight", IPageLayout.TOP, 0.25f, "right");
		topRight.addView(VariableView.ID);
	}
}

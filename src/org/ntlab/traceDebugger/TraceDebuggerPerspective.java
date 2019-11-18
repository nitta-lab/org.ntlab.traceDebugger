package org.ntlab.traceDebugger;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

public class TraceDebuggerPerspective implements IPerspectiveFactory {

	@Override
	public void createInitialLayout(IPageLayout layout) {
		// エディタの場所を取得
		String editorArea = layout.getEditorArea();

		// 左上にコールスタックのビューを配置
		IFolderLayout topLeft = layout.createFolder("topLeft", IPageLayout.TOP, 0.35f, editorArea);
		topLeft.addView(CallStackView.ID);
		
		// 右上にブレークポイントのビューを配置
		IFolderLayout topRight = layout.createFolder("topRight", IPageLayout.RIGHT, 0.5f, "topLeft");
		topRight.addView(BreakPointView.ID);
		
		// 右上に変数のビューを配置
		IFolderLayout topRight2 = layout.createFolder("topRight2", IPageLayout.RIGHT, 0.5f, "topLeft");
		topRight2.addView(VariableView.ID);		
	}
}

package org.ntlab.traceDebugger;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

public class TraceDebuggerPerspectiveRelatedReverse implements IPerspectiveFactory {

	@Override
	public void createInitialLayout(IPageLayout layout) {
		// エディタの場所を取得
		String editorArea = layout.getEditorArea();

		// 右にブレークポイントのビューを配置
		IFolderLayout right = layout.createFolder("right", IPageLayout.RIGHT, 0.5f, editorArea);
		right.addView(BreakPointViewRelatedReverse.ID);
		
		// 右下にトレースポイントのビューを配置
		IFolderLayout rightBottom = layout.createFolder("rightBottom", IPageLayout.BOTTOM, 0.5f, "right");
		rightBottom.addView(TracePointsView.ID);
		
		// 左上にコールスタックのビューを配置
		IFolderLayout topLeft = layout.createFolder("topLeft", IPageLayout.TOP, 0.25f, editorArea);
		topLeft.addView(CallStackView.ID);
		
		// 右上に変数のビューを配置
		IFolderLayout topRight = layout.createFolder("topRight", IPageLayout.RIGHT, 0.25f, "topLeft");
		topRight.addView(VariableViewRelatedReverse.ID);
	}
}

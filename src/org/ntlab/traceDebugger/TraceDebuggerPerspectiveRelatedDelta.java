package org.ntlab.traceDebugger;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

public class TraceDebuggerPerspectiveRelatedDelta implements IPerspectiveFactory {

	@Override
	public void createInitialLayout(IPageLayout layout) {
		// �G�f�B�^�̏ꏊ���擾
		String editorArea = layout.getEditorArea();

		// �E�Ƀu���[�N�|�C���g�̃r���[��z�u
		IFolderLayout right = layout.createFolder("right", IPageLayout.RIGHT, 0.5f, editorArea);
		right.addView(BreakPointViewRelatedReverse.ID);
		
		// �E���Ƀg���[�X�|�C���g�̃r���[��z�u
		IFolderLayout rightBottom = layout.createFolder("rightBottom", IPageLayout.BOTTOM, 0.5f, "right");
		rightBottom.addView(TracePointsView.ID);
		
		// �E��ɕϐ��̃r���[��z�u
		IFolderLayout topRight = layout.createFolder("topRight", IPageLayout.TOP, 0.25f, editorArea);
		topRight.addView(VariableViewRelatedDelta.ID);

		// ����ɃR�[���c���[�̃r���[��z�u
		IFolderLayout topLeft = layout.createFolder("topLeft", IPageLayout.LEFT, 0.25f, "topRight");
		topLeft.addView(CallTreeView.ID);
		
		// ����ɃR�[���X�^�b�N�̃r���[��z�u
		IFolderLayout topLeft2 = layout.createFolder("topLeft2", IPageLayout.TOP, 0.25f, "topLeft");
		topLeft2.addView(CallStackViewRelatedDelta.ID);
	}
}

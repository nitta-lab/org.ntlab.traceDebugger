package org.ntlab.traceDebugger;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

public class TraceDebuggerPerspective implements IPerspectiveFactory {

	@Override
	public void createInitialLayout(IPageLayout layout) {
		// �G�f�B�^�̏ꏊ���擾
		String editorArea = layout.getEditorArea();

		// ����ɃR�[���X�^�b�N�̃r���[��z�u
		IFolderLayout topLeft = layout.createFolder("topLeft", IPageLayout.TOP, 0.35f, editorArea);
		topLeft.addView(CallStackView.ID);
		
		// �E��Ƀu���[�N�|�C���g�̃r���[��z�u
		IFolderLayout topRight = layout.createFolder("topRight", IPageLayout.RIGHT, 0.5f, "topLeft");
		topRight.addView(BreakPointView.ID);
		
		// �E��ɕϐ��̃r���[��z�u
		IFolderLayout topRight2 = layout.createFolder("topRight2", IPageLayout.RIGHT, 0.5f, "topLeft");
		topRight2.addView(VariableView.ID);		
	}
}
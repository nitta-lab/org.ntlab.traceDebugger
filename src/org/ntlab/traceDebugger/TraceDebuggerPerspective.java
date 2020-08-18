package org.ntlab.traceDebugger;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;

public class TraceDebuggerPerspective implements IPerspectiveFactory {

	@Override
	public void createInitialLayout(IPageLayout layout) {
		// �G�f�B�^�̏ꏊ���擾
		String editorArea = layout.getEditorArea();

		// �E�Ƀu���[�N�|�C���g�̃r���[��z�u
		IFolderLayout breakpointViewArea = layout.createFolder("BreakPointViewArea", IPageLayout.RIGHT, 0.5f, editorArea);
		breakpointViewArea.addView(BreakPointView.ID);
		
		// ����ɃR�[���X�^�b�N�̃r���[��z�u
		IFolderLayout callStackViewArea = layout.createFolder("CallStackViewArea", IPageLayout.TOP, 0.25f, editorArea);
		callStackViewArea.addView(CallStackView.ID);
		
		// �E��ɕϐ��̃r���[��z�u
		IFolderLayout variableViewArea = layout.createFolder("VariableViewArea", IPageLayout.TOP, 0.25f, "BreakPointViewArea");
		variableViewArea.addView(VariableView.ID);
	}
}

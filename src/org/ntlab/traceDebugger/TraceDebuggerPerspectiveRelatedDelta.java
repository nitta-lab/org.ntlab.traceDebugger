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
		IFolderLayout breakpointViewArea = layout.createFolder("BreakpointViewArea", IPageLayout.RIGHT, 0.5f, editorArea);
		breakpointViewArea.addView(BreakPointViewRelatedDelta.ID);

		// ����ɃR�[���c���[�̃r���[��z�u
		IFolderLayout callTreeViewArea = layout.createFolder("CallTreeViewArea", IPageLayout.BOTTOM, 0.25f, "BreakpointViewArea");
		callTreeViewArea.addView(CallTreeView.ID);
		
		// �E���Ƀg���[�X�|�C���g�̃r���[��z�u
		IFolderLayout tracePointsViewArea = layout.createFolder("TracePointsViewArea", IPageLayout.BOTTOM, 0.5f, "CallTreeViewArea");
		tracePointsViewArea.addView(TracePointsView.ID);
		
		// �E��ɕϐ��̃r���[��z�u
		IFolderLayout variableViewArea = layout.createFolder("VariableViewArea", IPageLayout.TOP, 0.25f, editorArea);
		variableViewArea.addView(VariableViewRelatedDelta.ID);
		
		// ����ɃR�[���X�^�b�N�̃r���[��z�u
		IFolderLayout callStackViewArea = layout.createFolder("CallStackViewArea", IPageLayout.LEFT, 0.25f, "VariableViewArea");
		callStackViewArea.addView(CallStackViewRelatedDelta.ID);		
	}
}

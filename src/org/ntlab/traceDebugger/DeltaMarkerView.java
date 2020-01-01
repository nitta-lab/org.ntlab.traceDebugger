package org.ntlab.traceDebugger;

import org.eclipse.ui.IMemento;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.views.markers.MarkerSupportView;

public class DeltaMarkerView extends MarkerSupportView {
	public static String ID = "org.ntlab.traceDebugger.deltaMarkerView";
	
	public DeltaMarkerView() {
		super("org.ntlab.traceDebugger.markerContentGenerator");
	}
	
	@Override
	public void init(IViewSite site, IMemento m) throws PartInitException {
		// note: ���̃��\�b�h���I�[�o�[���C�h����IMemento��null�ɂ��Ă����Ȃ��ƃR���X�g���N�^�Őݒ肵��Generator�����������
		super.init(site, null);
	}
}

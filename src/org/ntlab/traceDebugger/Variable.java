package org.ntlab.traceDebugger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TraceJSON;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class Variable {
	private String variableName;
	private String className;
	private String id;
	private Variable parent;
	private List<Variable> children = new ArrayList<>();
	private String containerClassName;
	private String containerId;
	private TracePoint lastUpdatePoint;
	private TracePoint before;
	private boolean isReturned;
	private DeepHierarchy deepHierarchy;
	private boolean alreadyCreatedChildHierarchy = false;
	private boolean alreadyCreatedGrandChildHierarchy = false;
	private boolean isSrcSideRelatedDelta = false;
	private boolean isDstSideRelatedDelta = false;
	
	public Variable(String variableName, String containerClassName, String containerId,
			String className, String id, TracePoint before, boolean isReturned) {
		this(variableName, containerClassName, containerId, className, id, null, before, isReturned);
	}
	
	public Variable(String variableName, String containerClassName, String containerId,
			String className, String id, TracePoint lastUpdatePoint, TracePoint before, boolean isReturned) {
		this.variableName = variableName;
		this.containerClassName = containerClassName;
		this.containerId = containerId;
		this.className = className;
		this.id = id;
		this.lastUpdatePoint = lastUpdatePoint;
		this.before = before;
		this.isReturned = isReturned;
		this.deepHierarchy = checkDeepHierarchy();		
	}
	
	public String getVariableName() {
		return variableName;
	}
	
	public String getContainerClassName() {
		return containerClassName;
	}
	
	public String getContainerId() {
		return containerId;
	}
	
	public String getClassName() {
		return className;
	}
	
	public String getId() {
		return id;
	}
	
	public TracePoint getLastUpdatePoint() {
		return lastUpdatePoint;
	}
	
	public TracePoint getBeforeTracePoint() {
		return before;
	}
	
	public Variable getParent() {
		return parent;
	}
	
	public List<Variable> getChildren() {
		return children;
	}
	
	private void addChild(Variable child) {
		children.add(child);
		child.parent = this;
	}
	
	@Override
	public String toString() {
		return variableName + ": " + className + "(" + "id = " + id + ")";
	}
	
	/**
	 * ���̃t�B�[���h���Q�ƌ^�I�u�W�F�N�g���z�񂩂𔻒肵�Ĕ��茋�ʂ�Ԃ�.<br>
	 * (�ϐ��r���[�ɕ\��������f�[�^���ċA�I�ɋ��߂邽�߂�, �Ăяo�����Ŏ��ɂǂ̃��\�b�h���ĂԂ��𔻒f����̂ɗ��p)
	 * 
	 * @param objData
	 * @return FIELD: �Q�ƌ^�I�u�W�F�N�g�̏ꍇ, ARRAY: �z��̏ꍇ, NONE: ����ȊO�̏ꍇ
	 */
	private DeepHierarchy checkDeepHierarchy() {
		// �t�B�[���h��ID��Type���Ȃ��ꍇ��AType(=ActualType)��"---"�̏ꍇ�͉������Ȃ�
		if (this.getId() == null || this.getId().isEmpty() 
				|| this.getClassName() == null || this.getClassName().isEmpty()) {
			return DeepHierarchy.NONE;
		}
		final String NULL_ACTUAL_TYPE = "---"; // �t�B�[���h�ɑ΂��Ė����I��null����ꂽ�ꍇ��ActualType�̎擾������
		if (this.getClassName().equals(NULL_ACTUAL_TYPE)) return DeepHierarchy.NONE;

		final String ARRAY_SIGNATURE_HEAD = "["; // �z��̃V�O�l�`���̐擪�́A�z��̎��������� [ ���A�Ȃ�
		if (this.getClassName().startsWith(ARRAY_SIGNATURE_HEAD)) {
			// �t�B�[���h��Type���z��^(�@[ �Ŏn�܂�@)�ꍇ (���̔z�񂪎��e�v�f�ɂ��Ă���Ȃ�f�[�^�擾�������Ăяo��)
			return DeepHierarchy.ARRAY;
		} else {
			String[] primitives = {"byte", "short", "int", "long", "float", "double", "char", "boolean"};
			if (!Arrays.asList(primitives).contains(this.getClassName())) {
				// �t�B�[���h��Type���Q�ƌ^(=�I�u�W�F�N�g)�̏ꍇ (���̃I�u�W�F�N�g�������Ă���t�B�[���h�ɂ��Ă���Ȃ�f�[�^�擾�������Ăяo��)
				return DeepHierarchy.FIELD;
			}
		}
		return DeepHierarchy.NONE;
	}

	public void createNextHierarchyState() {
		if (alreadyCreatedGrandChildHierarchy) return;
		getDeepHierarchyState();
		for (Variable child : children) {
			child.getDeepHierarchyState();
		}
		alreadyCreatedGrandChildHierarchy = true;
	}

	private void getDeepHierarchyState() {
		if (alreadyCreatedChildHierarchy) return;
		switch (this.deepHierarchy) {
		case FIELD:
			getFieldsState();
			Collections.sort(children, new Comparator<Variable>() {
				@Override
				public int compare(Variable arg0, Variable arg1) {
					// �ϐ����̏����ɕ��ёւ���
					return arg0.getVariableName().compareTo(arg1.getVariableName());
				}
			});
			break;
		case ARRAY:
			getArrayState();
			Collections.sort(children, new Comparator<Variable>() {
				@Override
				public int compare(Variable arg0, Variable arg1) {
					// �z��C���f�b�N�X�̏����ɕ��ёւ���
					String arg0Name = arg0.variableName;
					String arg1Name = arg1.variableName;
					int arg0Index = Integer.parseInt(arg0Name.substring(arg0Name.indexOf("[") + 1, arg0Name.lastIndexOf("]"))); 
					int arg1Index = Integer.parseInt(arg1Name.substring(arg0Name.indexOf("[") + 1, arg1Name.lastIndexOf("]")));
					return (arg0Index < arg1Index) ? -1 : 1;
				}
			});
			break;
		case NONE:
			break;
		}
		alreadyCreatedChildHierarchy = true;
	}
	
	private void getFieldsState() {
		// �t�B�[���h��ID��Type���擾���ĕ\��
		TraceJSON trace = (TraceJSON)TraceDebuggerPlugin.getAnalyzer().getTrace();
		String declaringClassName = className;
		IType type = JavaEditorOperator.findIType(null, declaringClassName);
		if (type == null) {
			System.out.println("IType == null: " + declaringClassName);
			return;
		}
		try {
			for (IField field : type.getFields()) {
				if (Flags.isStatic(field.getFlags())) continue;
				String fieldName = field.getDeclaringType().getElementName() + "." + field.getElementName(); // ���S����N���X��
				String fullyQualifiedFieldName = field.getDeclaringType().getFullyQualifiedName() + "." + field.getElementName(); // ���S����N���X��

				// ���̃t�B�[���h�ɂ��Ă̍ŐV�̍X�V�����擾(FieldUpdate)
//				FieldUpdate fieldUpdate = trace.getRecentlyFieldUpdate(thisObjData.getId(), fieldName, tp);
//				FieldUpdate fieldUpdate = trace.getFieldUpdate(id, fullyQualifiedFieldName, before, isReturned);
				TracePoint updateTracePoint = trace.getFieldUpdateTracePoint(id, fullyQualifiedFieldName, before, isReturned);
				if (updateTracePoint == null) continue;
				FieldUpdate fieldUpdate = (FieldUpdate)updateTracePoint.getStatement();

				// �t�B�[���h��ID��Type���擾(String)
				String fieldObjId = (fieldUpdate != null) ? fieldUpdate.getValueObjId()     : "0";
				String fieldType  = (fieldUpdate != null) ? fieldUpdate.getValueClassName() : "---";
				Variable fieldData = new Variable(fieldName, className, id, fieldType, fieldObjId, updateTracePoint, before, isReturned);
				this.addChild(fieldData);
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
	}

	private void getArrayState() {
		TraceJSON trace = (TraceJSON)TraceDebuggerPlugin.getAnalyzer().getTrace();
		for (int i = 0;; i++){
			// ���̔z��v�f�ɂ��Ă̍ŐV�̍X�V�����擾(ArrayUpdate)
			ArrayUpdate arrayUpdate = trace.getRecentlyArrayUpdate(id, i, before);
			if (arrayUpdate == null) {
				// �z��̃T�C�Y���擾�ł��Ȃ����߁A�C���f�b�N�X���T�C�Y���߂̂Ƃ��Ɋm���ɔ���������@�Ƃ��ĉ�����
				// �������A�z��v�f�̓r���ɖ���`���������ꍇ�ł��A�����Ă��܂��̂����_
				break;
			}
			String arrayIndexName = this.getVariableName() + "[" + i + "]";
			
			// �z��v�f��ID��Type���擾(String)
			String valueObjId = arrayUpdate.getValueObjectId();
			String valueType = arrayUpdate.getValueClassName();
			Variable arrayIndexData = new Variable(arrayIndexName, className, id, valueType, valueObjId, before, isReturned);
			this.addChild(arrayIndexData);
		}
	}
	
	public boolean isSrcSideRelatedDelta() {
		return isSrcSideRelatedDelta;
	}
	
	public void setSrcSideRelatedDelta(boolean isSrcSideRelatedDelta) {
		this.isSrcSideRelatedDelta = isSrcSideRelatedDelta;
	}
	
	public boolean isDstSideRelatedDelta() {
		return isDstSideRelatedDelta;
	}
	
	public void setDstSideRelatedDelta(boolean isDstSideRelatedDelta) {
		this.isDstSideRelatedDelta = isDstSideRelatedDelta;
	}
	
	
	private enum DeepHierarchy {
		NONE, FIELD, ARRAY;
	}
}
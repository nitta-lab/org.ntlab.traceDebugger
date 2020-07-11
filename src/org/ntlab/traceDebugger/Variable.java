package org.ntlab.traceDebugger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TraceJSON;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
import org.ntlab.traceDebugger.analyzerProvider.VariableUpdatePointFinder;

public class Variable {
	private String variableName;
	private VariableType variableType;
	private String fullyQualifiedVariableName;
	private String valueClassName;
	private String valueId;
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
	private Map<String, Object> additionalAttributes = new HashMap<>();
	public static final String RETURN_VARIABLE_NAME = "return";
	public static final String ARG_VARIABLE_NAME = "arg";
	public static final String RECEIVER_VARIABLE_NAME = "receiver";
	public static final String VALUE_VARIABLE_NAME = "value";
	public static final String CONTAINER_VARIABLE_NAME = "container";

	public enum VariableType {
		USE_VALUE, USE_CONTAINER, USE_RECEIVER, USE_RETURN,
		DEF_VALUE, DEF_CONTAINER, DEF_RECEIVER, DEF_ARG, 
		PARAMETER;
		public boolean isContainerSide() {
			return this.equals(USE_CONTAINER) || this.equals(DEF_CONTAINER) 
					|| this.equals(USE_RECEIVER) || this.equals(DEF_RECEIVER);
		}
		public boolean isDef() {
			return this.equals(DEF_CONTAINER) || this.equals(DEF_VALUE)
					|| this.equals(DEF_RECEIVER) || this.equals(DEF_ARG);
		}
		public boolean isUse() {
			return this.equals(USE_CONTAINER) || this.equals(USE_VALUE)
					|| this.equals(USE_RECEIVER) || this.equals(USE_RETURN);
		}
	}
	
	public Variable(String variableName, String containerClassName, String containerId,
			String valueClassName, String valueId, TracePoint before, boolean isReturned) {
		this(variableName, containerClassName, containerId, valueClassName, valueId, before, isReturned, VariableType.USE_VALUE);
	}
	
	public Variable(String variableName, String containerClassName, String containerId,
			String valueClassName, String valueId, TracePoint before, boolean isReturned, VariableType variableType) {
		init(variableName, variableName, containerClassName, containerId, valueClassName, valueId, null, before, isReturned, variableType);
	}
	
	public Variable(String variableName, String fullyQualifiedVariableName, String containerClassName, String containerId,
			String valueClassName, String valueId, TracePoint lastUpdatePoint, TracePoint before, boolean isReturned, VariableType variableType) {
		init(variableName, fullyQualifiedVariableName, containerClassName, containerId, valueClassName, valueId, lastUpdatePoint, before, isReturned, variableType);
	}	
	
	private void init(String variableName, String fullyQualifiedVariableName, String containerClassName, String containerId,
			String valueClassName, String valueId, TracePoint lastUpdatePoint, TracePoint before, boolean isReturned, VariableType variableType) {
		this.variableName = variableName;
		this.fullyQualifiedVariableName = fullyQualifiedVariableName;
		this.containerClassName = containerClassName;
		this.containerId = containerId;
		this.valueClassName = valueClassName;
		this.valueId = valueId;
		this.lastUpdatePoint = lastUpdatePoint;
		this.before = before;
		this.isReturned = isReturned;
		this.deepHierarchy = checkDeepHierarchy();
		this.alreadyCreatedChildHierarchy = false;
		this.alreadyCreatedGrandChildHierarchy = false;
		this.children.clear();
		this.additionalAttributes.clear();
		this.variableType = variableType;
	}
	
	public void update(String valueClassName, String valueId, TracePoint lastUpdatePoint, boolean isReturned) {
		init(variableName, fullyQualifiedVariableName, containerClassName, containerId, valueClassName, valueId, lastUpdatePoint, lastUpdatePoint, isReturned, variableType);
	}
	
	public String getVariableName() {
		return variableName;
	}
	
	public VariableType getVariableType() {
		return variableType;
	}
	
	public String getFullyQualifiedVariableName() {
		return fullyQualifiedVariableName;
	}
	
	public String getContainerClassName() {
		return containerClassName;
	}
	
	public String getContainerId() {
		return containerId;
	}
	
	public String getValueClassName() {
		return valueClassName;
	}
	
	public String getValueId() {
		return valueId;
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
		return variableName + ": " + valueClassName + "(" + "id = " + valueId + ")";
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
		if (this.getValueId() == null || this.getValueId().isEmpty() 
				|| this.getValueClassName() == null || this.getValueClassName().isEmpty()) {
			return DeepHierarchy.NONE;
		}
		final String NULL_ACTUAL_TYPE = "---"; // �t�B�[���h�ɑ΂��Ė����I��null����ꂽ�ꍇ��ActualType�̎擾������
		if (this.getValueClassName().equals(NULL_ACTUAL_TYPE)) return DeepHierarchy.NONE;

		final String ARRAY_SIGNATURE_HEAD = "["; // �z��̃V�O�l�`���̐擪�́A�z��̎��������� [ ���A�Ȃ�
		if (this.getValueClassName().startsWith(ARRAY_SIGNATURE_HEAD)) {
			// �t�B�[���h��Type���z��^(�@[ �Ŏn�܂�@)�ꍇ (���̔z�񂪎��e�v�f�ɂ��Ă���Ȃ�f�[�^�擾�������Ăяo��)
			return DeepHierarchy.ARRAY;
		} else {
			String[] primitives = {"byte", "short", "int", "long", "float", "double", "char", "boolean"};
			if (!Arrays.asList(primitives).contains(this.getValueClassName())) {
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
		IType type = null;
		if (variableType.isContainerSide()) {
			type = JavaElementFinder.findIType(null, containerClassName);
		} else {
			type = JavaElementFinder.findIType(null, valueClassName);
		}
		if (type == null) return;
		getFieldsState(type);		
		getFieldStateForSuperClass(type); // �e�N���X��k���Ă����A�����̃N���X�Œ�`���ꂽ�t�B�[���h�̏����擾���Ă��� (�������������������Ĕ��ɏd���Ȃ�)
	}
	
	/**
	 * // �e�N���X��k���Ă����A�����̃N���X�Œ�`���ꂽ�t�B�[���h�̏����擾���Ă��� (�������������������Ĕ��ɏd���Ȃ�)
	 * @param type �N�_�ƂȂ�N���X
	 */
	private void getFieldStateForSuperClass(IType type) {
		try {
			while (true) {
				String superClassName = type.getSuperclassName();
				if (superClassName == null) break;
				String fullyQualifiedSuperClassName = JavaElementFinder.resolveType(type, superClassName);
				if (fullyQualifiedSuperClassName == null) break;
				type = JavaElementFinder.findIType(null, fullyQualifiedSuperClassName);
				if (type == null) break;
				getFieldsState(type);
			}				
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
	}

	private void getFieldsState(IType type) {
		try {
			for (IField field : type.getFields()) {
				if (Flags.isStatic(field.getFlags())) continue;
				String fieldName = field.getDeclaringType().getElementName() + "." + field.getElementName(); // ���S����N���X��
				String fullyQualifiedFieldName = field.getDeclaringType().getFullyQualifiedName() + "." + field.getElementName(); // ���S����N���X��

				// ���̃t�B�[���h�ɂ��Ă̍ŐV�̍X�V�����擾(FieldUpdate)
				TraceJSON trace = (TraceJSON)TraceDebuggerPlugin.getAnalyzer().getTrace();
//				FieldUpdate fieldUpdate = trace.getRecentlyFieldUpdate(thisObjData.getId(), fieldName, tp);
//				FieldUpdate fieldUpdate = trace.getFieldUpdate(id, fullyQualifiedFieldName, before, isReturned);
				
//				TracePoint updateTracePoint = trace.getFieldUpdateTracePoint(valueId, fullyQualifiedFieldName, before, isReturned);
				TracePoint updateTracePoint = VariableUpdatePointFinder.getInstance().getPoint(valueId, fullyQualifiedFieldName, before);
				
//				if (updateTracePoint == null) continue;
				if (updateTracePoint != null) {
					FieldUpdate fieldUpdate = (FieldUpdate)updateTracePoint.getStatement();
					// �t�B�[���h��ID��Type���擾(String)
					String fieldObjId = (fieldUpdate != null) ? fieldUpdate.getValueObjId()     : "0";
					String fieldType  = (fieldUpdate != null) ? fieldUpdate.getValueClassName() : "---";
					Variable fieldData = new Variable(fieldName, fullyQualifiedFieldName, valueClassName, valueId, fieldType, fieldObjId, updateTracePoint, before, isReturned, VariableType.USE_VALUE);
					this.addChild(fieldData);
				} else {
					Variable fieldData = new Variable(fieldName, fullyQualifiedFieldName, valueClassName, valueId, "?", "???", updateTracePoint, before, isReturned, VariableType.USE_VALUE);
					this.addChild(fieldData);					
				}
			}
		} catch (JavaModelException e) {
			e.printStackTrace();
		}
	}

	private void getArrayState() {
		TraceJSON trace = (TraceJSON)TraceDebuggerPlugin.getAnalyzer().getTrace();
		for (int i = 0;; i++){
			// ���̔z��v�f�ɂ��Ă̍ŐV�̍X�V�����擾(ArrayUpdate)
			ArrayUpdate arrayUpdate = trace.getRecentlyArrayUpdate(valueId, i, before);
			if (arrayUpdate == null) {
				// �z��̃T�C�Y���擾�ł��Ȃ����߁A�C���f�b�N�X���T�C�Y���߂̂Ƃ��Ɋm���ɔ���������@�Ƃ��ĉ�����
				// �������A�z��v�f�̓r���ɖ���`���������ꍇ�ł��A�����Ă��܂��̂����_
				break;
			}
			String arrayIndexName = variableName + "[" + i + "]";
			
			// �z��v�f��ID��Type���擾(String)
			String valueObjId = arrayUpdate.getValueObjectId();
			String valueType = arrayUpdate.getValueClassName();
			Variable arrayIndexData = new Variable(arrayIndexName, valueClassName, valueId, valueType, valueObjId, before, isReturned);
			this.addChild(arrayIndexData);
		}
	}
	
	public void addAdditionalAttribute(String key, Object value) {
		additionalAttributes.put(key, value);
	}
	
	public Object getAdditionalAttribute(String key) {
		return additionalAttributes.get(key);
	}

	private enum DeepHierarchy {
		NONE, FIELD, ARRAY;
	}
}
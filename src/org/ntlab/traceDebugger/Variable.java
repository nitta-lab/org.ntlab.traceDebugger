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

public class Variable {
	private String variableName;
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
	
	public Variable(String variableName, String containerClassName, String containerId,
			String valueClassName, String valueId, TracePoint before, boolean isReturned) {
		this(variableName, containerClassName, containerId, valueClassName, valueId, null, before, isReturned);
	}
	
	public Variable(String variableName, String containerClassName, String containerId,
			String valueClassName, String valueId, TracePoint lastUpdatePoint, TracePoint before, boolean isReturned) {
		init(variableName, variableName, containerClassName, containerId, valueClassName, valueId, lastUpdatePoint, before, isReturned);
	}
	
	public Variable(String variableName, String fullyQualifiedVariableName, String containerClassName, String containerId,
			String valueClassName, String valueId, TracePoint lastUpdatePoint, TracePoint before, boolean isReturned) {
		init(variableName, fullyQualifiedVariableName, containerClassName, containerId, valueClassName, valueId, lastUpdatePoint, before, isReturned);
	}	
	
	private void init(String variableName, String fullyQualifiedVariableName, String containerClassName, String containerId,
			String valueClassName, String valueId, TracePoint lastUpdatePoint, TracePoint before, boolean isReturned) {
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
	}
	
	public void update(String valueClassName, String valueId, TracePoint lastUpdatePoint, boolean isReturned) {
		init(variableName, fullyQualifiedVariableName, containerClassName, containerId, valueClassName, valueId, lastUpdatePoint, lastUpdatePoint, isReturned);
	}
	
	public String getVariableName() {
		return variableName;
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
	 * そのフィールドが参照型オブジェクトか配列かを判定して判定結果を返す.<br>
	 * (変数ビューに表示させるデータを再帰的に求めるために, 呼び出し元で次にどのメソッドを呼ぶかを判断するのに利用)
	 * 
	 * @param objData
	 * @return FIELD: 参照型オブジェクトの場合, ARRAY: 配列の場合, NONE: それ以外の場合
	 */
	private DeepHierarchy checkDeepHierarchy() {
		// フィールドのIDやTypeがない場合や、Type(=ActualType)が"---"の場合は何もしない
		if (this.getValueId() == null || this.getValueId().isEmpty() 
				|| this.getValueClassName() == null || this.getValueClassName().isEmpty()) {
			return DeepHierarchy.NONE;
		}
		final String NULL_ACTUAL_TYPE = "---"; // フィールドに対して明示的にnullを入れた場合のActualTypeの取得文字列
		if (this.getValueClassName().equals(NULL_ACTUAL_TYPE)) return DeepHierarchy.NONE;

		final String ARRAY_SIGNATURE_HEAD = "["; // 配列のシグネチャの先頭は、配列の次元数だけ [ が連なる
		if (this.getValueClassName().startsWith(ARRAY_SIGNATURE_HEAD)) {
			// フィールドのTypeが配列型(　[ で始まる　)場合 (その配列が持つ各要素についてさらなるデータ取得処理を呼び出す)
			return DeepHierarchy.ARRAY;
		} else {
			String[] primitives = {"byte", "short", "int", "long", "float", "double", "char", "boolean"};
			if (!Arrays.asList(primitives).contains(this.getValueClassName())) {
				// フィールドのTypeが参照型(=オブジェクト)の場合 (そのオブジェクトが持っているフィールドについてさらなるデータ取得処理を呼び出す)
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
					// 変数名の昇順に並び替える
					return arg0.getVariableName().compareTo(arg1.getVariableName());
				}
			});
			break;
		case ARRAY:
			getArrayState();
			Collections.sort(children, new Comparator<Variable>() {
				@Override
				public int compare(Variable arg0, Variable arg1) {
					// 配列インデックスの昇順に並び替える
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
		// フィールドのIDとTypeを取得して表示
		IType type = JavaElementFinder.findIType(null, valueClassName);
		if (type == null) return;
		getFieldsState(type);		
//		getFieldStateForSuperClass(type); // 親クラスを遡っていき、それらのクラスで定義されたフィールドの情報も取得していく (ただし処理が増加して非常に重くなる)
	}
	
	/**
	 * // 親クラスを遡っていき、それらのクラスで定義されたフィールドの情報も取得していく (ただし処理が増加して非常に重くなる)
	 * @param type 起点となるクラス
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
				String fieldName = field.getDeclaringType().getElementName() + "." + field.getElementName(); // 完全限定クラス名
				String fullyQualifiedFieldName = field.getDeclaringType().getFullyQualifiedName() + "." + field.getElementName(); // 完全限定クラス名

				// そのフィールドについての最新の更新情報を取得(FieldUpdate)
				TraceJSON trace = (TraceJSON)TraceDebuggerPlugin.getAnalyzer().getTrace();
//				FieldUpdate fieldUpdate = trace.getRecentlyFieldUpdate(thisObjData.getId(), fieldName, tp);
//				FieldUpdate fieldUpdate = trace.getFieldUpdate(id, fullyQualifiedFieldName, before, isReturned);
				TracePoint updateTracePoint = trace.getFieldUpdateTracePoint(valueId, fullyQualifiedFieldName, before, isReturned);
//				if (updateTracePoint == null) continue;
				if (updateTracePoint != null) {
					FieldUpdate fieldUpdate = (FieldUpdate)updateTracePoint.getStatement();
					// フィールドのIDとTypeを取得(String)
					String fieldObjId = (fieldUpdate != null) ? fieldUpdate.getValueObjId()     : "0";
					String fieldType  = (fieldUpdate != null) ? fieldUpdate.getValueClassName() : "---";
					Variable fieldData = new Variable(fieldName, fullyQualifiedFieldName, valueClassName, valueId, fieldType, fieldObjId, updateTracePoint, before, isReturned);
					this.addChild(fieldData);
				} else {
					Variable fieldData = new Variable(fieldName, fullyQualifiedFieldName, valueClassName, valueId, "?", "???", updateTracePoint, before, isReturned);
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
			// その配列要素についての最新の更新情報を取得(ArrayUpdate)
			ArrayUpdate arrayUpdate = trace.getRecentlyArrayUpdate(valueId, i, before);
			if (arrayUpdate == null) {
				// 配列のサイズが取得できないため、インデックスがサイズ超過のときに確実に抜けられる方法として仮処理
				// ただし、配列要素の途中に未定義があった場合でも、抜けてしまうのが問題点
				break;
			}
			String arrayIndexName = variableName + "[" + i + "]";
			
			// 配列要素のIDとTypeを取得(String)
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
package org.ntlab.traceDebugger.analyzerProvider;

import java.util.ArrayList;

import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayCreate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ObjectReference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Trace;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
 
/**
 * オブジェクトの参照情報(エイリアス)を表すクラス
 * @author Isitani
 *
 */
public class Alias {
	private String objectId;
	private TracePoint occurrencePoint; // 当該オブジェクトの参照が行われている実行箇所に対応するTracePoint
	/**
	 * 当該オブジェクトの参照がTracePointにおいてどこに現れているかを表す<br>
	 * 0				フィールドアクセス時のコンテナ もしくは メソッド呼び出し時のレシーバ<br>
	 * 1, 2, 3 …… n		フィールドアクセス時のフィールド(1) もしくは メソッド呼び出し時のn番目の実引数  (1から順番に)<br>
	 * -1				メソッド呼び出し時の戻り値<br>
	 * <br>
	 * 例1: d = a.m(b, c);<br>
	 * <br>
	 * 例1の実行文において, aはメソッド呼び出しのレシーバなので0, bはメソッド呼び出しの1番目の実引数なので1,<br>
	 * cはメソッド呼び出しの2番目の実引数なので2, a.m(b, c)の戻り値は-1 となる.<br>
	 * <br>
	 * 例2: d = a.f;<br>
	 * 例2の実行文において, aはフィールドのコンテナなので0, bはフィールドなので1 となる.
	 * 
	 */
	private int occurrenceExp;
	public static final int OCCURRENCE_EXP_CONTAINER = 0;
	public static final int OCCURRENCE_EXP_RECEIVER = 0;
	public static final int OCCURRENCE_EXP_FIELD = 1;
	public static final int OCCURRENCE_EXP_ARRAY = 1;
	public static final int OCCURRENCE_EXP_FIRST_ARG = 1;
	public static final int OCCURRENCE_EXP_RETURN = -1;
	
	private static final String FIELD_ACCESS = "Field Access";
	private static final String FIELD_UPDATE = "Field Update";
	private static final String ARRAY_ACCESS = "Array Access";
	private static final String ARRAY_UPDATE = "Array Update";
	private static final String ARRAY_CREATE = "Array Create";
	private static final String METHOD_INVOCATION = "Method Invocation";
	private static final String CREATION = "Creation";
 
	public Alias(String objectId, TracePoint occurrencePoint, int occurrenceExp) {
		this.objectId = objectId;
		this.occurrencePoint = occurrencePoint;
		this.occurrenceExp = occurrenceExp;
	}
	
	public String getObjectId() {
		return objectId;
	}
 
	public TracePoint getOccurrencePoint() {
		return occurrencePoint;
	}
 
	public int getOccurrenceExp() {
		return occurrenceExp;
	}
	
	public MethodExecution getMethodExecution() {
		return occurrencePoint.getMethodExecution();
	}
	
	public String getMethodExecutionClassName() {
		MethodExecution methodExecution = occurrencePoint.getMethodExecution();
		return Trace.getDeclaringType(methodExecution.getSignature(), methodExecution.isConstructor());
	}
	
	public String getSourceFileName() {
		String methodExecutionClassName = getMethodExecutionClassName();
		methodExecutionClassName = methodExecutionClassName.replace(".<clinit>", "");
		return methodExecutionClassName + ".java";
	}
 
	public String getMethodSignature() {
		return occurrencePoint.getMethodExecution().getCallerSideSignature();
	}
	
	public int getLineNo() {
		Statement statement = occurrencePoint.getStatement();
		return statement.getLineNo();
	}
 
	public String getStatementType() {
		Statement statement = occurrencePoint.getStatement();
		String statementType = "";
		if (statement instanceof FieldAccess) {
			statementType = FIELD_ACCESS;
		} else if (statement instanceof FieldUpdate) {
			statementType = FIELD_UPDATE;
		} else if (statement instanceof ArrayAccess) {
			statementType = ARRAY_ACCESS;
		} else if (statement instanceof ArrayUpdate) {
			statementType = ARRAY_UPDATE;
		} else if (statement instanceof ArrayCreate) {
			statementType = ARRAY_CREATE;
		} else if (statement instanceof MethodInvocation) {
			MethodInvocation mi = (MethodInvocation)statement;
			if (mi.getCalledMethodExecution().isConstructor()) {
				statementType = CREATION;
			} else {
				statementType = METHOD_INVOCATION;
			}
		}
		return statementType;
	}
	
	public String getStatementSignature() {
		Statement statement = occurrencePoint.getStatement();
		String statementSignature = "";
		if (statement instanceof FieldAccess) {
			FieldAccess fa = (FieldAccess)statement;
			statementSignature = fa.getFieldName();
		} else if (statement instanceof FieldUpdate) {
			FieldUpdate fu = (FieldUpdate)statement;
			statementSignature = fu.getFieldName();
		} else if (statement instanceof ArrayAccess) {
			ArrayAccess aa = (ArrayAccess)statement;
			statementSignature = aa.getArrayClassName() + "[" + aa.getIndex() + "]";
		} else if (statement instanceof ArrayUpdate) {
			ArrayUpdate au = (ArrayUpdate)statement;
			statementSignature = au.getArrayClassName() + "[" + au.getIndex() + "]";
		} else if (statement instanceof ArrayCreate) {
			ArrayCreate ac = (ArrayCreate)statement;
			statementSignature = ac.getArrayClassName();
		} else if (statement instanceof MethodInvocation) {
			MethodExecution me = ((MethodInvocation)statement).getCalledMethodExecution();
			statementSignature = me.getCallerSideSignature();
		}
		return statementSignature;		
	}
	
	public String getClassName() {
		Statement statement = occurrencePoint.getStatement();
		String className = "";
		if (statement instanceof FieldAccess) {
			if (occurrenceExp == OCCURRENCE_EXP_CONTAINER) {
				className = ((FieldAccess) statement).getContainerClassName();
			} else if (occurrenceExp == OCCURRENCE_EXP_FIELD) {
				className = ((FieldAccess) statement).getValueClassName();				
			}
		} else if (statement instanceof FieldUpdate) {
			if (occurrenceExp == OCCURRENCE_EXP_CONTAINER) {
				className = ((FieldUpdate) statement).getContainerClassName();
			} else if (occurrenceExp == OCCURRENCE_EXP_FIELD) {
				className = ((FieldUpdate) statement).getValueClassName();				
			}
		} else if (statement instanceof ArrayAccess) {
			className = ((ArrayAccess) statement).getValueClassName();
		} else if (statement instanceof ArrayUpdate) {
			className = ((ArrayUpdate) statement).getValueClassName();
		} else if (statement instanceof ArrayCreate) {
			className = ((ArrayCreate) statement).getArrayClassName();
		} else if (statement instanceof MethodInvocation) {
			MethodExecution me = ((MethodInvocation)statement).getCalledMethodExecution();
			if (occurrenceExp == OCCURRENCE_EXP_RETURN) {
				className = me.getReturnValue().getActualType();
			} else if (occurrenceExp == OCCURRENCE_EXP_RECEIVER) {
				className = me.getThisClassName();
			} else {
				int index = occurrenceExp - OCCURRENCE_EXP_FIRST_ARG;
				ArrayList<ObjectReference> args = me.getArguments();
				if (index >= 0 && index < args.size()) {
					className = me.getArguments().get(index).getActualType();	
				}
			}
		}
		return className;
	}
	
	public String getOccurrenceText() {		
		String statementType = getStatementType();
		switch (statementType) {
		case FIELD_ACCESS:
		case FIELD_UPDATE:
			return (occurrenceExp == OCCURRENCE_EXP_CONTAINER) ? "Container" : "Field";
		case ARRAY_ACCESS:
		case ARRAY_UPDATE:
			return (occurrenceExp == OCCURRENCE_EXP_CONTAINER) ? "Array Object" : "Array Value";
		case ARRAY_CREATE:
			return "New Array";
		case METHOD_INVOCATION:
			if (occurrenceExp <= 0) {
				return (occurrenceExp == OCCURRENCE_EXP_RECEIVER) ? "Receiver" : "Return Value";
			}
			final String[] ORDER_TEXT = {"th", "st", "nd", "rd", "th", "th", "th", "th", "th", "th", "th", "th", "th", "th"}; // 0-13に対応
			if (occurrenceExp % 100 >= ORDER_TEXT.length) {
				return occurrenceExp + ORDER_TEXT[occurrenceExp % 10] + " arg"; // 下2桁が14以上なら, 下1桁の数字に対応させる
			} else if (occurrenceExp % 100 >= 0) {
				return occurrenceExp + ORDER_TEXT[occurrenceExp % 100] + " arg"; // 下2桁が0以上13以下なら, 下2桁の数字に対応させる
			}
		case CREATION:
			return "New Object";
		}
		return String.valueOf(occurrenceExp);
	}
 
	public boolean isOrigin() {
		Statement statement = occurrencePoint.getStatement();
		if (statement instanceof MethodInvocation) {
			MethodExecution calledMethodExecution = ((MethodInvocation)statement).getCalledMethodExecution();
			return calledMethodExecution.isConstructor();
		} else if (statement instanceof ArrayCreate) {
			return true;
		}
		return false;
	}
 
	@Override
	public String toString() {
		Statement statement = occurrencePoint.getStatement();
		String className = getClassName();
		String methodSignature = getMethodSignature();
		String statementType = getStatementType();
		String statementSigunarure = getStatementSignature();
		String indent = "  ";
		StringBuilder str = new StringBuilder();
		str.append("objId: " + objectId + " (class = " + className + ")" + "\n");
		str.append("tp: " + occurrencePoint + "\n");
		str.append(indent + "signature: " + methodSignature + "\n");
		str.append(indent + "lineNo: " + statement.getLineNo() + "\n");
		str.append(indent + "statementType: " + statementType + " -> " + statementSigunarure + "\n");
		str.append("occurrenceExp: " + occurrenceExp + "\n");
		return str.toString();
	}
}
package org.ntlab.traceDebugger.analyzerProvider;

import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayCreate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

/**
 * オブジェクトの参照情報(エイリアス)を表すクラス
 * @author Isitani
 *
 */
public class Alias {
	private String objectId;
	private TracePoint occurrencePoint; // 当該オブジェクトの参照が行われている実行箇所に対応するTracePoint
	private AliasType aliasType;
	private int index;
	
	public enum AliasType {
		// メソッドへの入口
		FORMAL_PARAMETER, 
		THIS, 
		METHOD_INVOCATION, 
		CONSTRACTOR_INVOCATION, 
		
		// 追跡オブジェクトの切り替え
		FIELD, 
		CONTAINER, 
		ARRAY_ELEMENT, 
		ARRAY, 
		ARRAY_CREATE, 
 
		// メソッドからの出口
		ACTUAL_ARGUMENT, 
		RECEIVER, 
		RETURN_VALUE
	}
	
	public Alias(AliasType aliasType, int index, String objectId, TracePoint occurrencePoint) {
		this.aliasType = aliasType;
		this.index = index;
		this.objectId = objectId;
		this.occurrencePoint = occurrencePoint;
	}
	
	public AliasType getAliasType() {
		return aliasType;
	}
	
	public int getIndex() {
		return index;
	}
	
	public String getObjectId() {
		return objectId;
	}
	
	public TracePoint getOccurrencePoint() {
		return occurrencePoint;
	}
	
	public MethodExecution getMethodExecution() {
		return occurrencePoint.getMethodExecution();
	}
	
	public String getMethodSignature() {
		String signature = occurrencePoint.getMethodExecution().getCallerSideSignature();
		if (signature != null) return signature;
		return occurrencePoint.getMethodExecution().getSignature();
//		return occurrencePoint.getMethodExecution().getCallerSideSignature();
	}
	
	public int getLineNo() {
		try {
			Statement statement = occurrencePoint.getStatement();
			return statement.getLineNo();
		} catch (Exception e) {
			return -1;
		}
	}
	
	public void setIndex(int index) {
		this.index = index;
	}
	
	public String getObjectType() {
		TracePoint tmpTp;
		Statement callerStatement;
		Statement statement = occurrencePoint.getStatement();
		switch (aliasType) {
		// メソッドへの入口
		case FORMAL_PARAMETER:
			tmpTp = occurrencePoint.duplicate();
			tmpTp.stepBackOver();
			callerStatement = tmpTp.getStatement();
			if (callerStatement instanceof MethodInvocation) {
				MethodExecution me = ((MethodInvocation)callerStatement).getCalledMethodExecution();
				return me.getArguments().get(index).getActualType();
			}
		case THIS:
			if (statement instanceof FieldAccess) {
				return ((FieldAccess)statement).getContainerClassName();
			}
		case METHOD_INVOCATION:
		case CONSTRACTOR_INVOCATION:
			if (statement instanceof MethodInvocation) {
				MethodExecution me = ((MethodInvocation)statement).getCalledMethodExecution();
				return me.getReturnValue().getActualType();
			}
		// 追跡オブジェクトの切り替え
		case FIELD:
			if (statement instanceof FieldAccess) {
				return ((FieldAccess)statement).getValueClassName();
			}
		case CONTAINER:
			if (statement instanceof FieldAccess) {
				return ((FieldAccess)statement).getContainerClassName();
			}
		case ARRAY_ELEMENT:
			if (statement instanceof ArrayAccess) {
				return ((ArrayAccess)statement).getValueClassName();
			}
		case ARRAY:
			if (statement instanceof ArrayAccess) {
				return ((ArrayAccess)statement).getArrayClassName();
			}
		case ARRAY_CREATE:
			if (statement instanceof ArrayCreate) {
				return ((ArrayCreate)statement).getArrayClassName();
			}

		// メソッドからの出口
		case ACTUAL_ARGUMENT:
			if (statement instanceof MethodInvocation) {
				MethodExecution me = ((MethodInvocation)statement).getCalledMethodExecution();
				return me.getArguments().get(index).getActualType();
			}
		case RECEIVER:
			if (statement instanceof MethodInvocation) {
				MethodExecution me = ((MethodInvocation)statement).getCalledMethodExecution();
				return me.getThisClassName();
			}
		case RETURN_VALUE:
			tmpTp = occurrencePoint.duplicate();
			tmpTp.stepOver();
			callerStatement = tmpTp.getStatement();
			if (callerStatement instanceof MethodInvocation) {
				MethodExecution me = ((MethodInvocation)callerStatement).getCalledMethodExecution();
				return me.getReturnValue().getActualType();
			}
		}
		return "";
	}
}
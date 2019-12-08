package org.ntlab.traceDebugger.analyzerProvider;

import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;
 
public class Alias {
	private String objectId;
	private TracePoint occurrencePoint; // ���Y�I�u�W�F�N�g�̎Q�Ƃ��s���Ă�����s�ӏ��ɑΉ�����TracePoint
	private AliasType aliasType;
	private int index;
	
	public enum AliasType {
		// ���\�b�h�ւ̓���
		FORMAL_PARAMETER, 
		THIS, 
		METHOD_INVOCATION, 
		CONSTRACTOR_INVOCATION, 
		
		// �ǐՃI�u�W�F�N�g�̐؂�ւ�
		FIELD, 
		CONTAINER, 
		ARRAY_ELEMENT, 
		ARRAY, 
		ARRAY_CREATE, 

		// ���\�b�h����̏o��
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
		return occurrencePoint.getMethodExecution().getCallerSideSignature();
	}
	
	public int getLineNo() {
		Statement statement = occurrencePoint.getStatement();
		return statement.getLineNo();
	}
	
	public void setIndex(int index) {
		this.index = index;
	}
}
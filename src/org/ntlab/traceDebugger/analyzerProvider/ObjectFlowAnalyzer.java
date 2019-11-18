package org.ntlab.traceDebugger.analyzerProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayCreate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ObjectReference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Reference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Trace;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TraceJSON;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class ObjectFlowAnalyzer extends AbstractAnalyzer {
	private static ObjectFlowAnalyzer getInstance = null;
	
	public ObjectFlowAnalyzer(Trace trace) {
		super(trace);
	}
	
	private static ObjectFlowAnalyzer getInstance() {
		if (getInstance == null) {
			getInstance = new ObjectFlowAnalyzer(TraceJSON.getInstance());
		}
		return getInstance;
	}

	public static ArrayList<Alias> findAllSeedAliasesStatic(MethodExecution me) {
		return getInstance().findAllSeedAliases(me);
	}
	
	public ArrayList<Alias> findAllSeedAliases(MethodExecution me) {
		ArrayList<Alias> seedAliasList = new ArrayList<>();
		List<Statement> statements = me.getStatements();
		String[] primitives = {"byte", "short", "int", "long", "float", "double", "char", "boolean"};
		List<String> primitiveList = Arrays.asList(primitives);
		for (int i = 0; i < statements.size(); i++) {
			TracePoint tp = me.getTracePoint(i);
			Statement statement = statements.get(i);
			if (statement instanceof FieldAccess) {
				FieldAccess fa = (FieldAccess)statement;
				String objId = fa.getContainerObjId();
				if (objId != null && !(objId.equals("0")) && !(primitiveList.contains(fa.getContainerClassName()))) {
					seedAliasList.add(new Alias(objId, tp, Alias.OCCURRENCE_EXP_CONTAINER));
				}
				objId = fa.getValueObjId();
				if (objId != null && !(objId.equals("0")) && !(primitiveList.contains(fa.getValueClassName()))) {
					seedAliasList.add(new Alias(objId, tp, Alias.OCCURRENCE_EXP_FIELD));
				}
			} else if (statement instanceof FieldUpdate) {
				FieldUpdate fu = (FieldUpdate)statement;
				String objId = fu.getContainerObjId();
				if (objId != null && !(objId.equals("0")) && !(primitiveList.contains(fu.getContainerClassName()))) {
					seedAliasList.add(new Alias(objId, tp, Alias.OCCURRENCE_EXP_CONTAINER));
				}
				objId = fu.getValueObjId();
				if (objId != null && !(objId.equals("0")) && !(primitiveList.contains(fu.getValueClassName()))) {
					seedAliasList.add(new Alias(objId, tp, Alias.OCCURRENCE_EXP_FIELD));
				}
			} else if (statement instanceof ArrayAccess) {
				ArrayAccess aa = (ArrayAccess)statement;
				String valueObjId = aa.getValueObjectId();
				if (valueObjId != null && !(valueObjId.equals("0")) && !(primitiveList.contains(aa.getValueClassName()))) {
					seedAliasList.add(new Alias(valueObjId, tp, Alias.OCCURRENCE_EXP_ARRAY));
				}				
			} else if (statement instanceof ArrayUpdate) {
				ArrayUpdate au = (ArrayUpdate)statement;
				String valueObjId = au.getValueObjectId();
				if (valueObjId != null && !(valueObjId.equals("0")) && !(primitiveList.contains(au.getValueClassName()))) {
					seedAliasList.add(new Alias(valueObjId, tp, Alias.OCCURRENCE_EXP_ARRAY));
				}
			} else if (statement instanceof ArrayCreate) {
				ArrayCreate ac = (ArrayCreate)statement;
				String arrayObjId = ac.getArrayObjectId();
				if (arrayObjId != null && !(arrayObjId.equals("0")) && !(primitiveList.contains(ac.getArrayClassName()))) {
					seedAliasList.add(new Alias(arrayObjId, tp, Alias.OCCURRENCE_EXP_RETURN));
				}
			} else if (statement instanceof MethodInvocation) {
				MethodExecution calledMe = ((MethodInvocation)statement).getCalledMethodExecution();
				String thisObjId = calledMe.getThisObjId();
				if (thisObjId != null && !(thisObjId.equals("0"))) {
					seedAliasList.add(new Alias(thisObjId, tp, Alias.OCCURRENCE_EXP_RECEIVER));
				}
				List<ObjectReference> args = calledMe.getArguments();
				for (int j = 0; j < args.size(); j++) {
					ObjectReference arg = args.get(j);
					String argValueId = arg.getId();
					if (argValueId != null && !(argValueId.equals("0")) && !(primitiveList.contains(arg.getActualType()))) {
						seedAliasList.add(new Alias(argValueId, tp, (j + Alias.OCCURRENCE_EXP_FIRST_ARG)));
					}
				}
				ObjectReference returnValue = calledMe.getReturnValue();
				if (returnValue != null) {
					String returnValueId = returnValue.getId();
					if (returnValueId != null && !(returnValueId.equals("0") && !(primitiveList.contains(returnValue.getActualType())))) {
						seedAliasList.add(new Alias(returnValueId, tp, Alias.OCCURRENCE_EXP_RETURN));
					}
				}
			}
		}
		return seedAliasList;
	}

	private TracePoint getRecentlyFieldUpdate(TracePoint tp) {
		Statement statement = tp.getStatement();
		if (statement instanceof FieldAccess) {
			FieldAccess fa = (FieldAccess)statement;
			return trace.getFieldUpdateTracePoint(fa.getReference(), tp);
		}
		return null;
	}
	
	private TracePoint getRecentlyArrayUpdate(TracePoint tp) {
		Statement statement = tp.getStatement();
		if (statement instanceof ArrayAccess) {
			ArrayAccess aa = (ArrayAccess)statement;
			// aa.getReference()���Ȃ��̂ŉ��ɂ�����ۂ�Reference������ēn��
			return trace.getArraySetTracePoint(new Reference(aa.getArrayObjectId(), aa.getValueObjectId(), aa.getArrayClassName(), aa.getValueClassName()), tp);			
		}
		return null;
	}

	public static ArrayList<ArrayList<Alias>> getObjectFlowStatic(Alias startAlias) {
		return getInstance().getObjectFlow(startAlias);
	}
	
	public ArrayList<ArrayList<Alias>> getObjectFlow(Alias startAlias) {
		ArrayList<ArrayList<Alias>> aliasLists = new ArrayList<>();
		ArrayList<Alias> aliasList = new ArrayList<>();
		aliasLists.add(aliasList);
//		aliasList.add(alias);
		String objId = startAlias.getObjectId();
		TracePoint tp = startAlias.getOccurrencePoint().duplicate();
		ArrayList<ArrayList<Alias>> resultLists = getObjectFlow(aliasLists, objId, tp, 0);
		return resultLists;
	}

	private ArrayList<ArrayList<Alias>> getObjectFlow(ArrayList<ArrayList<Alias>> aliasLists, 
			String objId, TracePoint tp, int side) {
		ArrayList<Alias> aliasList = aliasLists.get(aliasLists.size() - 1); // ����getObjectFlow���\�b�h���s���Ō��������G�C���A�X�����Ă������X�g
		do {
			Statement statement = tp.getStatement();
			if (statement instanceof FieldAccess) {
				// �t�B�[���h�Q�Ƃ̏ꍇ
				FieldAccess fa = (FieldAccess)statement;
				if (fa.getValueObjId().equals(objId)) {
					// ���Y�n�_�ł̃G�C���A�X�����X�g�ɒǉ��������, �t�B�[���h�ŏI�X�V�ɔ�ԃp�^�[���Ƃ��̂܂ܑk��p�^�[���Ƃŕ���
					aliasList.add(new Alias(objId, tp.duplicate(), Alias.OCCURRENCE_EXP_FIELD));
					aliasList = new ArrayList<>(aliasList); // ���X�g���̂��f�B�[�v�R�s�[���Ă���(�t�B�[���h�ŏI�X�V�ɔ�ԍċA�����I�����, ���̂܂ܑk��p�^�[���ŗp����)
					TracePoint fieldUpdateTp = getRecentlyFieldUpdate(tp);
					aliasLists = getObjectFlow(aliasLists, objId, fieldUpdateTp, 0);
					aliasLists.add(aliasList); // �ċA�����ɓ���O�Ƀf�B�[�v�R�s�[���Ă������X�g���Ō���ɒǉ� (�ȍ~�̑k��ɂ���Č������G�C���A�X�͂��̃��X�g�ɓ������)
				}
			} else if (statement instanceof ArrayAccess) {
				// �z��v�f�Q�Ƃ̏ꍇ
				ArrayAccess aa = (ArrayAccess)statement;
				if (aa.getValueObjectId().equals(objId)) {
					aliasList.add(new Alias(objId, tp.duplicate(), Alias.OCCURRENCE_EXP_ARRAY));
					aliasList = new ArrayList<>(aliasList);
					TracePoint arrayUpdateTp = getRecentlyArrayUpdate(tp);
					aliasLists = getObjectFlow(aliasLists, objId, arrayUpdateTp, 0);
					aliasLists.add(aliasList);
				}
			} else if (statement instanceof FieldUpdate) {
				// �t�B�[���h�X�V�̏ꍇ
				FieldUpdate fu = (FieldUpdate)statement;
				if (fu.getValueObjId().equals(objId)) {
					aliasList.add(new Alias(objId, tp.duplicate(), Alias.OCCURRENCE_EXP_FIELD));
				}
			} else if (statement instanceof ArrayUpdate) {
				// �z��v�f�X�V�̏ꍇ
				ArrayUpdate au = (ArrayUpdate)statement;
				if (au.getValueObjectId().equals(objId)) {
					aliasList.add(new Alias(objId, tp.duplicate(), Alias.OCCURRENCE_EXP_ARRAY));
				}
			} else if (statement instanceof ArrayCreate) {
				// �z�񐶐��̏ꍇ
				ArrayCreate ac = (ArrayCreate)statement;
				if (ac.getArrayObjectId().equals(objId)) {
					aliasList.add(new Alias(objId, tp.duplicate(), Alias.OCCURRENCE_EXP_RETURN)); // �z�񐶐��� new �^��[] �̖߂�l
					return aliasLists; // �z�񐶐��ӏ��̓G�C���A�X�̋N���Ȃ̂ł���ȑO�ɂ͂����Ȃ��͂�
				}
			} else if (statement instanceof MethodInvocation) {
				// ���\�b�h�Ăяo���̏ꍇ
				MethodExecution calledMethodExecution = ((MethodInvocation)statement).getCalledMethodExecution();
				ObjectReference returnValue = calledMethodExecution.getReturnValue();
				if (returnValue.getId().equals(objId)) {
					// �߂�l�ɃG�C���A�X�̃I�u�W�F�N�gID����v�����ꍇ
					ArrayList<Alias> aliasListBeforeMethodBackEntry = new ArrayList<>(aliasList); // �Ăяo����̃��\�b�h���s�ɐ���O�̃G�C���A�X���X�g���R�s�[���Ă���
					aliasList.add(new Alias(objId, tp.duplicate(), Alias.OCCURRENCE_EXP_RETURN));
					if (calledMethodExecution.isConstructor()) {
						return aliasLists; // �R���X�g���N�^�Ăяo���ӏ��̓G�C���A�X�̋N���Ȃ̂ł���ȑO�ɂ͂����Ȃ��͂�
					}
					TracePoint exitTp = calledMethodExecution.getExitPoint(); // �Ăяo�����\�b�h���s�̍ŏI�X�e�[�g�����g���w��tp���擾
					aliasLists = getObjectFlow(aliasLists, objId, exitTp, side + 1); // �Ăяo����̃��\�b�h���s�ɐ���
					aliasList = aliasLists.get(aliasLists.size() - 1);
					if (aliasList.get(aliasList.size() - 1).isOrigin()) {
						// �Ăяo����̃��\�b�h���s�ɐ�������ł��̃I�u�W�F�N�g�̋N��(�R���X�g���N�^or�z�񐶐�)�ɓ��B���Ă����ꍇ, �Ăяo����̃��\�b�h���s�ɐ���O�̃��X�g��p���ĐV�K�ɒǐՂ𑱍s����
						aliasLists.add(aliasListBeforeMethodBackEntry);
						aliasList = aliasListBeforeMethodBackEntry;
					}
				}
			}
		} while (tp.stepBackOver()); // �Ăяo�����ɖ߂邩����ȏ�H��Ȃ��Ȃ�܂Ń��[�v
		if (!tp.isValid()) {
			return aliasLists; // ����ȏチ�\�b�h���s��k��Ȃ��ꍇ(main���\�b�h�̂���ɑO�Ȃ�)�͂��̎��_�ŏI��
		}
		// --- ���̎��_�� tracePoint�� �Ăяo�������w���Ă��� (���O�܂ők���Ă������\�b�h���s�ɂ��Ẵ��\�b�h�Ăяo�����w���Ă���) ---
		MethodExecution calledMethodExecution = ((MethodInvocation)tp.getStatement()).getCalledMethodExecution();
		ArrayList<ObjectReference> args = calledMethodExecution.getArguments();
		boolean isExistingInArgs = false;
		for (int i = 0; i < args.size(); i++) {
			if (args.get(i).getId().equals(objId)) {
				// ���\�b�h�Ăяo���̎������ɃG�C���A�X�̃I�u�W�F�N�gID����v�����ꍇ
				aliasList.add(new Alias(objId, tp.duplicate(), (i + Alias.OCCURRENCE_EXP_FIRST_ARG)));
				isExistingInArgs = true;
				if (side == 0) {
					// �T���J�n���\�b�h���s�܂��̓t�B�[���h��z��v�f�̍ŏI�X�V�T���Ŕ�񂾐�̃��\�b�h���s����, �X�^�b�N�g���[�X�ł��ǂ��S���\�b�h���s�̏ꍇ
					TracePoint previousTp = tp.duplicate();
					previousTp.stepBackOver();
					aliasLists = getObjectFlow(aliasLists, objId, previousTp, 0); // �Ăяo�����̃��\�b�h���s�ɖ߂�
				}
			}
		}
		if (!isExistingInArgs) {
			aliasLists.remove(aliasLists.size() - 1); // �����ɃG�C���A�X���Ȃ������ꍇ�͂��̉�̒ǐՃG�C���A�X���X�g���폜����
		}
		return aliasLists;
	}
}

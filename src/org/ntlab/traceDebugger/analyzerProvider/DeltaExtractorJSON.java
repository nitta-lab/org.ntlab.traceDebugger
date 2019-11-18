package org.ntlab.traceDebugger.analyzerProvider;

import java.util.ArrayList;

import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ArrayCreate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ObjectReference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Reference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Trace;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TraceJSON;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;


/**
 * �f���^���o�A���S���Y��(�z��ւ̃A�N�Z�X�����m�ł���Javassist��JSON�g���[�X�ɑΉ����A�A���S���Y����P����)
 * 
 * @author Nitta
 *
 */
public class DeltaExtractorJSON extends DeltaExtractor {
	public DeltaExtractorJSON(String traceFile) {
		super(new TraceJSON(traceFile));
	}

	public DeltaExtractorJSON(TraceJSON trace) {
		super(trace);
	}

	/**
	 * �f���^���o�A���S���Y���̌Ăяo�����T�������icalleeSearch�Ƒ��ݍċA�ɂȂ��Ă���j
	 * @param trace�@��͑Ώۃg���[�X
	 * @param methodExecution �T�����郁�\�b�h���s
	 * @param objList�@�ǐՒ��̃I�u�W�F�N�g
	 * @param child�@���O�ɒT�����Ă����Ăяo����̃��\�b�h���s
	 * @return ���������R�[�f�B�l�[�^
	 * @throws TraceFileException
	 */
	protected MethodExecution callerSearch(Trace trace, TracePoint tracePoint, ArrayList<String> objList, MethodExecution childMethodExecution) {
		MethodExecution methodExecution = tracePoint.getMethodExecution();
		methodExecution.setAugmentation(new DeltaAugmentationInfo());
		eStructure.createParent(methodExecution);
		String thisObjectId = methodExecution.getThisObjId();
		ArrayList<String> removeList = new ArrayList<String>();		// �ǐՂ��Ă���I�u�W�F�N�g���ō폜�ΏۂƂȂ��Ă������
		ArrayList<String> creationList = new ArrayList<String>();	// ���̃��\�b�h���s���ɐ������ꂽ�I�u�W�F�N�g
		int existsInFields = 0;			// ���̃��\�b�h���s���Ńt�B�[���h�ɗR�����Ă���I�u�W�F�N�g�̐�(1�ȏ�Ȃ炱�̃��\�b�h���s����this�Ɉˑ�)
		boolean isTrackingThis = false;	// �Ăяo�����this�Ɉˑ�����
		boolean isSrcSide = true;		// �Q�ƌ����Q�Ɛ�̂�����̑��̃I�u�W�F�N�g�̗R�������ǂ���this�I�u�W�F�N�g�ɓ��B������?
		ObjectReference thisObj = new ObjectReference(thisObjectId, methodExecution.getThisClassName(), 
				Trace.getDeclaringType(methodExecution.getSignature(), methodExecution.isConstructor()), Trace.getDeclaringType(methodExecution.getCallerSideSignature(), methodExecution.isConstructor()));
		
		if (childMethodExecution == null) {
			// �T���J�n���͈�U�폜���A�Ăяo�����̒T���𑱂���ۂɕ���������
			removeList.add(thisObjectId);		// ��ň�U�AthisObject ����菜��
			isTrackingThis = true;				// �Ăяo�����T���O�ɕ���
		}
		
		if (childMethodExecution != null && objList.contains(childMethodExecution.getThisObjId())) {
			// �Ăяo�����this�Ɉˑ�����
			if (thisObjectId.equals(childMethodExecution.getThisObjId())) {
				// �I�u�W�F�N�g���Ăяo���̂Ƃ��݈̂�U�폜���A�Ăяo�����̒T���𑱂���ۂɕ���������
				removeList.add(thisObjectId);		// ��ň�U�AthisObject ����菜��
				isTrackingThis = true;				// �Ăяo�����T���O�ɕ���
			}
		}
		
		if (childMethodExecution != null && childMethodExecution.isConstructor()) {
			// �Ăяo���悪�R���X�g���N�^�������ꍇ
			int newIndex = objList.indexOf(childMethodExecution.getThisObjId());
			if (newIndex != -1) {
				// �Ăяo���悪�ǐՑΏۂ̃R���X�g���N�^��������field�Ɠ��l�ɏ���
				removeList.add(childMethodExecution.getThisObjId());
				existsInFields++;
				removeList.add(thisObjectId);		// ��ň�U�AthisObject ����菜��
			}
		}
		
		if (childMethodExecution != null && Trace.getMethodName(childMethodExecution.getSignature()).startsWith("access$")) {
			// �G���N���[�W���O�C���X�^���X�ɑ΂��郁�\�b�h�Ăяo���������ꍇ
			String enclosingObj = childMethodExecution.getArguments().get(0).getId();	// �G���N���[�W���O�C���X�^���X�͑������ɓ����Ă���炵��
			int encIndex = objList.indexOf(enclosingObj);
			if (encIndex != -1) {
				// thisObject �ɒu����������Afield�Ɠ��l�ɏ���
				removeList.add(enclosingObj);
				existsInFields++;
				removeList.add(thisObjectId);		// ��ň�U�AthisObject ����菜��
			}
		}

		// �߂�l�ɒT���Ώۂ��܂܂�Ă����calleeSearch���ċA�Ăяo��
		while (tracePoint.stepBackOver()) {
			Statement statement = tracePoint.getStatement();
			// ���ڎQ�ƁA�t�B�[���h�Q�Ƃ���єz��A�N�Z�X�̒T��
			if (statement instanceof FieldAccess) {
				FieldAccess fs = (FieldAccess)statement;
				String refObjectId = fs.getValueObjId();
				int index = objList.indexOf(refObjectId);
				if (index != -1) {
					String ownerObjectId = fs.getContainerObjId();
					if (ownerObjectId.equals(thisObjectId)) {
						// �t�B�[���h�Q�Ƃ̏ꍇ
						removeList.add(refObjectId);
						existsInFields++;					// set�������get�����o���Ă���\��������
						removeList.add(thisObjectId);		// ��ň�U�AthisObject ����菜��
					} else {
						// ���ڎQ�Ƃ̏ꍇ
						if (refObjectId.equals(srcObject.getId())) {
							eStructure.addSrcSide(new Reference(ownerObjectId, refObjectId,
									fs.getContainerClassName(), srcObject.getActualType()));
							srcObject = new ObjectReference(ownerObjectId, fs.getContainerClassName());
						} else if(refObjectId.equals(dstObject.getId())) {
							eStructure.addDstSide(new Reference(ownerObjectId, refObjectId,
									fs.getContainerClassName(), dstObject.getActualType()));
							dstObject = new ObjectReference(ownerObjectId, fs.getContainerClassName());
						}
						objList.set(index, ownerObjectId);
					}
				}
			} else if (statement instanceof ArrayAccess) {
				ArrayAccess aa = (ArrayAccess)statement;
				String elementObjectId = aa.getValueObjectId();
				int index = objList.indexOf(elementObjectId);
				if (index != -1) {
					// �z��A�N�Z�X�̏ꍇ
					String arrayObjectId = aa.getArrayObjectId();
					if (elementObjectId.equals(srcObject.getId())) {
						eStructure.addSrcSide(new Reference(arrayObjectId, elementObjectId,
								aa.getArrayClassName(), srcObject.getActualType()));
						srcObject = new ObjectReference(arrayObjectId, aa.getArrayClassName());
					} else if(elementObjectId.equals(dstObject.getId())) {
						eStructure.addDstSide(new Reference(arrayObjectId, elementObjectId,
								aa.getArrayClassName(), dstObject.getActualType()));
						dstObject = new ObjectReference(arrayObjectId, aa.getArrayClassName());
					}
					objList.set(index, arrayObjectId);
				}
			} else if (statement instanceof ArrayCreate) {
				ArrayCreate ac = (ArrayCreate)statement;
				String arrayObjectId = ac.getArrayObjectId();
				int index = objList.indexOf(arrayObjectId);
				if (index != -1) {
					// �z�񐶐��̏ꍇfield�Ɠ��l�ɏ���
					creationList.add(arrayObjectId);
					removeList.add(arrayObjectId);
					existsInFields++;
					removeList.add(thisObjectId);		// ��ň�U�AthisObject ����菜��
				}
			} else if (statement instanceof MethodInvocation) {
				MethodExecution prevChildMethodExecution = ((MethodInvocation)statement).getCalledMethodExecution();
				if (!prevChildMethodExecution.equals(childMethodExecution)) {
					// �߂�l
					ObjectReference ret = prevChildMethodExecution.getReturnValue();
					if (ret != null) {
						int retIndex = -1;
						retIndex = objList.indexOf(ret.getId());
						if (retIndex != -1) {
							// �߂�l���R��������
							prevChildMethodExecution.setAugmentation(new DeltaAugmentationInfo());
							if (prevChildMethodExecution.isConstructor()) {
								// �ǐՑΏۂ�constractor���Ă�ł�����(�I�u�W�F�N�g�̐�����������)field�Ɠ��l�ɏ���
								String newObjId = ret.getId();
								creationList.add(newObjId);
								removeList.add(newObjId);
								existsInFields++;
								removeList.add(thisObjectId);		// ��ň�U�AthisObject ����菜��
								((DeltaAugmentationInfo)prevChildMethodExecution.getAugmentation()).setTraceObjectId(Integer.parseInt(newObjId));		// �ǐՑΏ�
								((DeltaAugmentationInfo)prevChildMethodExecution.getAugmentation()).setSetterSide(false);	// getter�Ăяo���Ɠ��l
								continue;
							}
							String retObj = objList.get(retIndex);
							if (removeList.contains(retObj)) {
								// ��xget�Ō��o���ăt�B�[���h�Ɉˑ����Ă���Ɣ��f�������{���̗R�����߂�l���������Ƃ����������̂ŁA�t�B�[���h�ւ̈ˑ����L�����Z������
								removeList.remove(retObj);
								existsInFields--;
								if (existsInFields == 0) {
									removeList.remove(thisObjectId);
								}
							}
							((DeltaAugmentationInfo)prevChildMethodExecution.getAugmentation()).setTraceObjectId(Integer.parseInt(retObj));					// �ǐՑΏ�
							TracePoint prevChildTracePoint = tracePoint.duplicate();
							prevChildTracePoint.stepBackNoReturn();
							calleeSearch(trace, prevChildTracePoint, objList, prevChildMethodExecution.isStatic(), retIndex);	// �Ăяo�����T��
							if (objList.get(retIndex) != null && objList.get(retIndex).equals(prevChildMethodExecution.getThisObjId()) 
									&& thisObjectId.equals(prevChildMethodExecution.getThisObjId())) {
								// �Ăяo����Ńt�B�[���h�Ɉˑ����Ă����ꍇ�̏���
								removeList.add(thisObjectId);		// ��ň�U�AthisObject ����菜��
								isTrackingThis = true;				// �Ăяo�����T���O�ɕ���
							}
							if (isLost) {
								checkList.add(objList.get(retIndex));
								isLost = false;
							}
						}
					}
				}
			}
		}
		// --- ���̎��_�� tracePoint �͌Ăяo�������w���Ă��� ---
		
		// �R���N�V�����^�Ή�
		if (methodExecution.isCollectionType()) {
			objList.add(thisObjectId);
		}		

		// �����̎擾
		ArrayList<ObjectReference> argments = methodExecution.getArguments();
		
		// �����ƃt�B�[���h�ɓ���ID�̃I�u�W�F�N�g������ꍇ��z��
		Reference r;
		for (int i = 0; i < removeList.size(); i++) {
			String removeId = removeList.get(i);
			if (argments.contains(new ObjectReference(removeId))) { 
				removeList.remove(removeId);	// �t�B�[���h�ƈ����̗����ɒǐՑΏۂ����݂����ꍇ�A������D��
			} else if(objList.contains(removeId)) {
				// �t�B�[���h�ɂ����Ȃ������ꍇ(�������A�I�u�W�F�N�g�̐������t�B�[���h�Ɠ��l�Ɉ���)
				objList.remove(removeId);		// �ǐՑΏۂ���O��
				if (!removeId.equals(thisObjectId)) {
					// �t�B�[���h�ithis ���� removeId �ւ̎Q�Ɓj���f���^�̍\���v�f�ɂȂ�
					if (removeId.equals(srcObject.getId())) {
						r = new Reference(thisObj, srcObject);
						r.setCreation(creationList.contains(removeId));		// �I�u�W�F�N�g�̐�����?
						eStructure.addSrcSide(r);
						srcObject = thisObj;
						isSrcSide = true;
					} else if (removeId.equals(dstObject.getId())) {
						r = new Reference(thisObj, dstObject);
						r.setCreation(creationList.contains(removeId));		// �I�u�W�F�N�g�̐�����?
						eStructure.addDstSide(r);
						dstObject = thisObj;
						isSrcSide = false;
					}					
				}
			}
		}
		// --- ���̎��_�� this ���ǐՑΏۂł������Ƃ��Ă� objList �̒����炢������폜����Ă��� ---
		
		// �����T��
		boolean existsInAnArgument = false;
		for (int i = 0; i < objList.size(); i++) {
			String objectId = objList.get(i);
			if (objectId != null) {
				ObjectReference trackingObj = new ObjectReference(objectId);
				if (argments.contains(trackingObj)) {
					// �������R��������
					existsInAnArgument = true;
					((DeltaAugmentationInfo)methodExecution.getAugmentation()).setTraceObjectId(Integer.parseInt(objectId));
				} else {
					// �R�����ǂ��ɂ�������Ȃ�����
					boolean isSrcSide2 = true;
					trackingObj = null;
					if (objectId.equals(srcObject.getId())) {
						isSrcSide2 = true;
						trackingObj = srcObject;
					} else if (objectId.equals(dstObject.getId())) {
						isSrcSide2 = false;
						trackingObj = dstObject;
					}
				}
			}
		}
		if (existsInAnArgument) {
			// ������1�ł��ǐՑΏۂ����݂����ꍇ
			if (existsInFields > 0 || isTrackingThis) {
				// this�I�u�W�F�N�g��ǐՒ��̏ꍇ
				if (!Trace.isNull(thisObjectId)) {
					objList.add(thisObjectId);	// ����ɒT������ꍇ�A��U��菜���� thisObject �𕜊�
				} else {
					objList.add(null);			// ������static�Ăяo���������ꍇ�A����ȏ�ǐՂ��Ȃ�
				}				
			}
			if (tracePoint.isValid()) {
				finalCount = 0;
				return callerSearch(trace, tracePoint, objList, methodExecution);		// �Ăяo����������ɒT��				
			}
		}
		
		for (int i = 0; i < objList.size(); i++) {
			objList.remove(null);
		}
		if (objList.isEmpty()) {
			((DeltaAugmentationInfo)methodExecution.getAugmentation()).setCoodinator(true);
		} else {
			// �R���������ł��Ȃ�����
			if (!methodExecution.isStatic()) {
				finalCount++;
				if (finalCount <= LOST_DECISION_EXTENSION) {
					// final�ϐ����Q�Ƃ��Ă���ꍇ�R���������ł��Ȃ��\��������̂ŁA�ǐՂ������I�������P�\���Ԃ�݂���
					if (tracePoint.isValid()) { 
						MethodExecution c = callerSearch(trace, tracePoint, objList, methodExecution);		// �Ăяo����������ɒT��	
						if (((DeltaAugmentationInfo)c.getAugmentation()).isCoodinator()) {
							methodExecution = c;		// �ǐՂ𑱂������ʃR�[�f�B�l�[�^����������
						}
					}
				} else if (thisObj.getActualType().contains("$")) {
					// �����������܂��͖����N���X�̏ꍇ�A���������I�u�W�F�N�g���O�����\�b�h�̓���final�ϐ�����擾�����Ƃ݂Ȃ��A����Ɏ����̒��̃t�B�[���h�̈��Ƃ݂Ȃ�
					for (int i = objList.size() - 1; i >= 0; i--) {
						String objectId = objList.get(i);
						if (objectId != null) {
							ObjectReference trackingObj = new ObjectReference(objectId);
							boolean isSrcSide2 = true;
							trackingObj = null;
							if (objectId.equals(srcObject.getId())) {
								isSrcSide2 = true;
								trackingObj = srcObject;
							} else if (objectId.equals(dstObject.getId())) {
								isSrcSide2 = false;
								trackingObj = dstObject;
							}
							if (trackingObj != null) {
								r = new Reference(thisObjectId, trackingObj.getId(),
										methodExecution.getThisClassName(), trackingObj.getActualType());
								r.setFinalLocal(true);
								if (isSrcSide2) {
									eStructure.addSrcSide(r);
									srcObject = thisObj;
									isSrcSide = true;
								} else {
									eStructure.addDstSide(r);
									dstObject = thisObj;
									isSrcSide = false;
								}
								existsInFields++;
								objList.remove(objectId);
							}
						}
					}
				}
			}
			((DeltaAugmentationInfo)methodExecution.getAugmentation()).setCoodinator(false);
		}
		finalCount = 0;
		return methodExecution;
	}

	/**
	 * �f���^���o�A���S���Y���̌Ăяo����T������(�ċA�Ăяo���ɂȂ��Ă���)
	 * @param trace ��͑Ώۃg���[�X
	 * @param methodExecution �T�����郁�\�b�h���s
	 * @param objList �ǐՒ��̃I�u�W�F�N�g
	 * @param isStatic�@�ÓI���\�b�h���ۂ�
	 * @param index�@objList���̂ǂ̃I�u�W�F�N�g��ǐՂ��Ă��̃��\�b�h���s�ɓ����Ă����̂�
	 * @throws TraceFileException
	 */
	protected void calleeSearch(Trace trace, TracePoint tracePoint, ArrayList<String> objList, Boolean isStatic, int index) {
		MethodExecution methodExecution = tracePoint.getMethodExecution();
		Boolean isResolved = false;
		String objectId = objList.get(index);		// calleeSearch() �ł͒ǐՑΏۂ̃I�u�W�F�N�g�͈�����A��objList��index�Ԗڂ̗v�f�ȊO�ύX���Ă͂����Ȃ�
		String thisObjectId = methodExecution.getThisObjId();
		ObjectReference thisObj = new ObjectReference(thisObjectId, methodExecution.getThisClassName(), 
				Trace.getDeclaringType(methodExecution.getSignature(), methodExecution.isConstructor()), 
				Trace.getDeclaringType(methodExecution.getCallerSideSignature(), methodExecution.isConstructor()));
		
		((DeltaAugmentationInfo)methodExecution.getAugmentation()).setSetterSide(false);		// ��{�I��getter�Ăяo���̂͂������A����
		ArrayList<ObjectReference> argments = methodExecution.getArguments();
		ObjectReference trackingObj = null;
		//static���o�R�����null�������Ă��鎞������
		if (objectId != null) {
			String returnType = Trace.getReturnType(methodExecution.getSignature());
			if (objectId.equals(srcObject.getId())) {
				trackingObj = srcObject;
				trackingObj.setCalleeType(returnType);
			} else if(objectId.equals(dstObject.getId())) {
				trackingObj = dstObject;
				trackingObj.setCalleeType(returnType);
			} else {
				trackingObj = new ObjectReference(objectId, null, returnType);
			}
			
			Reference r;
			// �߂�l�ɒT���Ώۂ��܂܂�Ă����calleeSearch�Ăяo��
			do {
				if (!tracePoint.isValid()) break;
				Statement statement = tracePoint.getStatement();
				// ���ڎQ�Ƃ���уt�B�[���h�Q�Ƃ̒T��
				if (statement instanceof FieldAccess) {
					FieldAccess fs = (FieldAccess)statement;
					if (objectId != null && objectId.equals(fs.getValueObjId())) {
						String ownerObjectId = fs.getContainerObjId();
						if (ownerObjectId.equals(thisObjectId)) {							
							// �t�B�[���h�Q�Ƃ̏ꍇ
							if (objectId.equals(srcObject.getId())) {
								eStructure.addSrcSide(new Reference(thisObj, srcObject));
								srcObject = thisObj;
								trackingObj = srcObject;
							} else if(objectId.equals(dstObject.getId())) {
								eStructure.addDstSide(new Reference(thisObj, dstObject));
								dstObject = thisObj;
								trackingObj = dstObject;
							}
							if (Trace.isNull(thisObjectId)) objectId = null;	// static�ϐ��̏ꍇ
							else objectId = thisObjectId;
							objList.set(index, objectId);
						} else {
							// ���ڎQ�Ƃ̏ꍇ
							if (objectId.equals(srcObject.getId())) {
								eStructure.addSrcSide(new Reference(ownerObjectId, objectId,
										fs.getContainerClassName(), srcObject.getActualType()));
								srcObject = new ObjectReference(ownerObjectId, fs.getContainerClassName());
								trackingObj = srcObject;
							} else if(objectId.equals(dstObject.getId())) {
								eStructure.addDstSide(new Reference(ownerObjectId, objectId,
										fs.getContainerClassName(), dstObject.getActualType()));
								dstObject = new ObjectReference(ownerObjectId, fs.getContainerClassName());
								trackingObj = dstObject;
							}
							if (Trace.isNull(ownerObjectId)) objectId = null;	// static�ϐ��̏ꍇ
							else objectId = ownerObjectId;
							objList.set(index, objectId);
						}
						isResolved = true;
					}
				} else if (statement instanceof ArrayAccess) {
					ArrayAccess aa = (ArrayAccess)statement;
					if (objectId != null && objectId.equals(aa.getValueObjectId())) {
						// �z��A�N�Z�X�̏ꍇ
						String arrayObjectId = aa.getArrayObjectId();
						if (objectId.equals(srcObject.getId())) {
							eStructure.addSrcSide(new Reference(arrayObjectId, objectId,
									aa.getArrayClassName(), srcObject.getActualType()));
							srcObject = new ObjectReference(arrayObjectId, aa.getArrayClassName());
							trackingObj = srcObject;
						} else if(objectId.equals(dstObject.getId())) {
							eStructure.addDstSide(new Reference(arrayObjectId, objectId,
									aa.getArrayClassName(), dstObject.getActualType()));
							dstObject = new ObjectReference(arrayObjectId, aa.getArrayClassName());
							trackingObj = dstObject;
						}
						objectId = arrayObjectId;
						objList.set(index, objectId);
						isResolved = true;
					}
				} else if (statement instanceof ArrayCreate) {
					ArrayCreate ac = (ArrayCreate)statement;
					if (objectId != null && objectId.equals(ac.getArrayObjectId())) {
						// �z�񐶐��̏ꍇ
						if (objectId.equals(srcObject.getId())) {
							eStructure.addSrcSide(new Reference(thisObj, srcObject));
							srcObject = thisObj;
							trackingObj = srcObject;
						} else if(objectId.equals(dstObject.getId())) {
							eStructure.addDstSide(new Reference(thisObj, dstObject));
							dstObject = thisObj;
							trackingObj = dstObject;
						}
						if (Trace.isNull(thisObjectId)) objectId = null;	// static�ϐ��̏ꍇ
						else objectId = thisObjectId;
						objList.set(index, objectId);
					}
				} else if (statement instanceof MethodInvocation) {
					// �߂�l
					MethodExecution childMethodExecution = ((MethodInvocation)statement).getCalledMethodExecution();
					ObjectReference ret = childMethodExecution.getReturnValue();
					if (ret != null && objectId != null && objectId.equals(ret.getId())) {
						childMethodExecution.setAugmentation(new DeltaAugmentationInfo());
						((DeltaAugmentationInfo)childMethodExecution.getAugmentation()).setTraceObjectId(Integer.parseInt(objectId));
						TracePoint childTracePoint = tracePoint.duplicate();
						childTracePoint.stepBackNoReturn();
						calleeSearch(trace, childTracePoint, objList, childMethodExecution.isStatic(), index);		// �Ăяo���������ɒT��	
						if (childMethodExecution.isConstructor()) {
							// �R���X�g���N�^�Ăяo���������ꍇ
							if (objectId.equals(srcObject.getId())) {
								r = new Reference(thisObj, srcObject);
								r.setCreation(true);
								eStructure.addSrcSide(r);
								srcObject = thisObj;
								trackingObj = srcObject;
							} else if (objectId.equals(dstObject.getId())) {
								r = new Reference(thisObj, dstObject);
								r.setCreation(true);
								eStructure.addDstSide(r);
								dstObject = thisObj;
								trackingObj = dstObject;
							}
							if (Trace.isNull(thisObjectId)) objectId = null;	// static�ϐ��̏ꍇ
							else objectId = thisObjectId;
							objList.set(index, objectId);
							isResolved = true;
							isLost = false;
							continue;
						}
						objectId = objList.get(index);
						if (objectId == null) {
							// static�Ăяo���̖߂�l�������ꍇ�i���Ԃ�j
							trackingObj = null;
							isResolved = true;
						} else if (objectId.equals(srcObject.getId())) {
							trackingObj = srcObject;
						} else if (objectId.equals(dstObject.getId())) {
							trackingObj = dstObject;
						}
						if (isLost) {
							checkList.add(objList.get(index));
							isLost = false;
						}
					}
				}
			} while (tracePoint.stepBackOver());
			
			//�����T��
			if (argments.contains(new ObjectReference(objectId))) {
				((DeltaAugmentationInfo)methodExecution.getAugmentation()).setSetterSide(true);		// �������K�v?
				isResolved = true;
			}
		}
		
		//�R���N�V�����^�Ή�
		Reference r;
		if (methodExecution.isCollectionType()) {
			if (objectId != null) {
				// �R���N�V�����^�̏ꍇ�A�����ŌX�̗v�f�𒼐ڕێ����Ă���Ɖ��肷��
				if (objectId.equals(srcObject.getId())) {
					r = new Reference(thisObj, srcObject);
					r.setCollection(true);
					eStructure.addSrcSide(r);
					srcObject = thisObj;
				} else if(objectId.equals(dstObject.getId())) {
					r = new Reference(thisObj, dstObject);
					r.setCollection(true);
					eStructure.addDstSide(r);
					dstObject =thisObj;
				}
			}
			objList.set(index, methodExecution.getThisObjId());
			isResolved = true;		// �K�v�Ȃ̂ł�?
		}
		
		if (objectId == null && isResolved && !isStatic) {	// static �Ăяo������̖߂�l��Ԃ��Ă���ꍇ
			objList.set(index, thisObjectId);	// ������ǐՂ�����
			if (Trace.isNull(srcObject.getId())) {
				srcObject = thisObj;
			} else if (Trace.isNull(dstObject.getId())) {
				dstObject = thisObj;
			}
		}
		
		if (isStatic && !isResolved) {		// ���͋N���肦�Ȃ�?(get�|�C���g�J�b�g���擾����悤�ɂ�������)
			objList.set(index, null);
		}
		if(!isStatic && !isResolved){
			isLost = true;					// final�ϐ�������N���X�ŎQ�Ƃ��Ă���\�������邪�AcalleeSearch()�͕K���Ăяo�����ɕ��A���Ă����̂ŁA�����ł͉������Ȃ�
		}
	}
}
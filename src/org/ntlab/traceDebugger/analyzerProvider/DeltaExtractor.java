package org.ntlab.traceDebugger.analyzerProvider;

import java.util.ArrayList;

import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldAccess;
import org.ntlab.traceAnalysisPlatform.tracer.trace.FieldUpdate;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodExecution;
import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.ObjectReference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Reference;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Trace;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

/**
 * �f���^���o�A���S���Y��(�z��ւ̃A�N�Z�X�𐄑�����]���̃o�[�W����)
 *    extract(...)���\�b�h�Q�Œ��o����B
 * 
 * @author Nitta
 *
 */
public class DeltaExtractor {
	protected static final int LOST_DECISION_EXTENSION = 0;		// ��{�� 0 �ɐݒ�Bfinal�ϐ��̒ǐՃA���S���Y���̕s��C����͕s�v�̂͂��B
	protected ArrayList<String> data = new ArrayList<String>();
	protected ArrayList<String> objList = new ArrayList<String>(2);
	protected ArrayList<String> methodList = new ArrayList<String>();
	protected ExtractedStructure eStructure = new ExtractedStructure();
	protected ObjectReference srcObject = null;
	protected ObjectReference dstObject = null;
	protected String returnValue;
	protected String threadNo;
	protected boolean isLost = false;
	protected ArrayList<String> checkList = new ArrayList<String>();
	protected Trace trace = null;
	protected int finalCount = 0;			// final�ϐ������o�ł��Ȃ��\��������̂ŁA�R���̉������ł��Ȃ������ꍇ�ł����΂炭�ǐՂ��Â���
	
	protected static final boolean DEBUG1 = true;
	protected static final boolean DEBUG2 = true;
	
	public DeltaExtractor(String traceFile) {
		trace = new Trace(traceFile);
	}

	public DeltaExtractor(Trace trace) {
		this.trace = trace;
	}
	
//	public MethodExecution getMethodExecution(Reference createdReference, MethodExecution before) {
//		return trace.getMethodExecution(createdReference, before);
//	}
//	
//	public MethodExecution getMethodExecution(String methodSignature) {
//		return trace.getMethodExecution(methodSignature);
//	}
//	
//	public MethodExecution getMethodExecutionBackwardly(String methodSignature) {
//		return trace.getMethodExecutionBackwardly(methodSignature);
//	}
//	
//	public MethodExecution getCollectionTypeMethodExecution(Reference r, MethodExecution before) {
//		return trace.getCollectionTypeMethodExecution(r, before);
//	}
//	
//	public MethodExecution getArraySetMethodExecution(Reference r, MethodExecution before)  {
//		return trace.getArraySetMethodExecution(r, before);
//	}
//	
//	public CallTree getLastCallTree(ArrayList<Reference> refs, 
//			ArrayList<Reference> colls, 
//			ArrayList<Reference> arrys, 
//			int endLine, 
//			Reference[] lastRef) throws TraceFileException {
//		return trace.getLastCallTree(refs, colls, arrys, endLine, lastRef);
//	}

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
		ArrayList<ObjectReference> fieldArrays = new ArrayList<ObjectReference>();
		ArrayList<ObjectReference> fieldArrayElements = new ArrayList<ObjectReference>();
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
			// ���ڎQ�Ƃ���уt�B�[���h�Q�Ƃ̒T��
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
				} else {
					// �ŏI�I�ɃI�u�W�F�N�g�̗R����������Ȃ������ꍇ�ɁA�����ŎQ�Ƃ����z������̗v�f�ɗR�����Ă���\��������
					String refObjType = fs.getValueClassName();
					if (refObjType.startsWith("[L")) {
						// �Q�Ƃ����t�B�[���h���z��̏ꍇ
						ObjectReference trackingObj = null;
						if ((srcObject.getActualType() != null && refObjType.endsWith(srcObject.getActualType() + ";"))
								|| (srcObject.getCalleeType() != null && refObjType.endsWith(srcObject.getCalleeType() + ";"))
								|| (srcObject.getCallerType() != null && refObjType.endsWith(srcObject.getCallerType() + ";"))) {
							trackingObj = srcObject;
						} else if ((dstObject.getActualType() != null && refObjType.endsWith(dstObject.getActualType() + ";")) 
								|| (dstObject.getCalleeType() != null && refObjType.endsWith(dstObject.getCalleeType() + ";"))
								|| (dstObject.getCallerType() != null && refObjType.endsWith(dstObject.getCallerType() + ";"))) {
							trackingObj = dstObject;
						}
						if (trackingObj != null) {
							// �ǐՒ��̃I�u�W�F�N�g�ɁA�z��v�f�Ɠ����^�����I�u�W�F�N�g�����݂���ꍇ
							String ownerObjectId = fs.getContainerObjId();
							if (ownerObjectId.equals(thisObjectId)) {
								// �t�B�[���h�Q�Ƃ̏ꍇ�i���ɗR���̉\�����Ȃ��Ƃ킩�������_�ŁA���̔z��̗v�f�ɗR�����Ă�����̂Ɛ�������B�j
								fieldArrays.add(new ObjectReference(refObjectId, refObjType));
								fieldArrayElements.add(trackingObj);
							} else {
								// ���ڎQ�Ƃ̏ꍇ(�{���ɂ��̔z��̗v�f����擾���ꂽ���̂Ȃ炱���ŒǐՑΏۂ�u��������ׂ������A
								// ���̎��_�ő��̗R���̉\����r���ł��Ȃ��B�����ŒǐՑΏۂ�u�������Ă��܂��ƁA��ŕʂɗR�������邱�Ƃ��킩�����ꍇ��
								// ��蒼��������B)
							}
						}
					}
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
		//						objList.remove(callTree.getThisObjId());
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
						} else {
							// �ŏI�I�ɃI�u�W�F�N�g�̗R����������Ȃ������ꍇ�ɁA���̖߂�l�Ŏ擾�����z������̗v�f�ɗR�����Ă���\��������
							String retType = ret.getActualType();
							if (retType.startsWith("[L")) {
								// �߂�l���z��̏ꍇ
								if ((srcObject.getActualType() != null && retType.endsWith(srcObject.getActualType() + ";")) 
										|| (srcObject.getCalleeType() != null && retType.endsWith(srcObject.getCalleeType() + ";"))
										|| (srcObject.getCallerType() != null && retType.endsWith(srcObject.getCallerType() + ";"))) {
									retType = srcObject.getActualType();
								} else if ((dstObject.getActualType() != null && retType.endsWith(dstObject.getActualType() + ";"))
										|| (dstObject.getCalleeType() != null && retType.endsWith(dstObject.getCalleeType() + ";"))
										|| (dstObject.getCallerType() != null && retType.endsWith(dstObject.getCallerType() + ";"))) {
									retType = dstObject.getActualType();
								} else {
									retType = null;
								}
								if (retType != null) {
									// �{���ɂ��̔z��̗v�f����擾���ꂽ���̂Ȃ炱���ŒǐՑΏۂ�u�������āA�Ăяo�����T�����ׂ������A
									// ���̎��_�ő��̗R���̉\����r���ł��Ȃ��B�����ŒǐՑΏۂ�u�������Ă��܂��ƁA��ŕʂɗR�������邱�Ƃ��킩�����ꍇ��
									// ��蒼��������B
								}
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
					if (trackingObj != null) {
						// �܂��z������̗v�f��R���Ƃ��ċ^��(������D��)
						for (int j = 0; j < argments.size(); j++) {
							ObjectReference argArray = argments.get(j);
							if (argArray.getActualType().startsWith("[L") 
									&& (trackingObj.getActualType() != null && (argArray.getActualType().endsWith(trackingObj.getActualType() + ";"))
											|| (trackingObj.getCalleeType() != null && argArray.getActualType().endsWith(trackingObj.getCalleeType() + ";"))
											|| (trackingObj.getCallerType() != null && argArray.getActualType().endsWith(trackingObj.getCallerType() + ";")))) {
								// �^����v������z������̗v�f��R���Ƃ݂Ȃ�
								existsInAnArgument = true;
								objList.remove(objectId);
								objList.add(argArray.getId());	// �ǐՑΏۂ�z��v�f����z��ɒu������
								((DeltaAugmentationInfo)methodExecution.getAugmentation()).setTraceObjectId(Integer.parseInt(argArray.getId()));
								r = new Reference(argArray.getId(), trackingObj.getId(), 
										argArray.getActualType(), trackingObj.getActualType());
								r.setArray(true);
								if (isSrcSide2) {
									eStructure.addSrcSide(r);
									srcObject = new ObjectReference(argArray.getId(), argArray.getActualType());
								} else {
									eStructure.addDstSide(r);
									dstObject = new ObjectReference(argArray.getId(), argArray.getActualType());
								}
								objectId = null;
								break;
							}
						}
						if (objectId != null) {
							// ���ɔz��t�B�[���h�̗v�f��R���Ƃ��ċ^��(�t�B�[���h�͈�������)
							int index = fieldArrayElements.indexOf(trackingObj);
							if (index != -1) {
								// �^����v���Ă�̂Ŕz��t�B�[���h�̗v�f��R���Ƃ݂Ȃ�
								ObjectReference fieldArray = fieldArrays.get(index);
								existsInFields++;
								objList.remove(objectId);
								r = new Reference(fieldArray.getId(), trackingObj.getId(),
										fieldArray.getActualType(), trackingObj.getActualType());
								r.setArray(true);
								if (isSrcSide2) {
									eStructure.addSrcSide(r);
									eStructure.addSrcSide(new Reference(thisObjectId, fieldArray.getId(),
											methodExecution.getThisClassName(), fieldArray.getActualType()));
									srcObject = thisObj;
									isSrcSide = true;
								} else {
									eStructure.addDstSide(r);
									eStructure.addDstSide(new Reference(thisObjectId, fieldArray.getId(),
											methodExecution.getThisClassName(), fieldArray.getActualType()));
									dstObject = thisObj;
									isSrcSide = false;
								}
							}
						}
						if (trackingObj.getActualType() != null && trackingObj.getActualType().startsWith("[L")) {
							// �ǂ��ɂ�������Ȃ������ꍇ�A�T���Ă���̂��z��^�Ȃ�΁A���̃��\�b�h���Ő������ꂽ���̂ƍl����
							objList.remove(objectId);
							if (isSrcSide2) {
								eStructure.addSrcSide(new Reference(thisObjectId, trackingObj.getId(),
										methodExecution.getThisClassName(), trackingObj.getActualType()));
								srcObject = thisObj;
								isSrcSide = true;
							} else {
								eStructure.addDstSide(new Reference(thisObjectId, trackingObj.getId(),
										methodExecution.getThisClassName(), trackingObj.getActualType()));
								dstObject = thisObj;
								isSrcSide = false;
							}
						}
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
//			if (existsInFields > 0) {
//				// �t�B�[���h��R���Ɏ��I�u�W�F�N�g�����݂����ꍇ
//				if (isSrcSide) {
//					srcObject = thisObj;
//				} else {
//					dstObject = thisObj;
//				}
//			}
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
		ArrayList<ObjectReference> fieldArrays = new ArrayList<ObjectReference>();
		ArrayList<ObjectReference> fieldArrayElements = new ArrayList<ObjectReference>();
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
					} else {
						// �I�u�W�F�N�g�̗R�������ڌ�����Ȃ������ꍇ�ł��A�����ꂩ�̔z��̗v�f�ɗR�����Ă���\��������
						String refObjType = fs.getValueClassName();
						if (refObjType.startsWith("[L")) {
							// �Q�Ƃ����t�B�[���h���z��̏ꍇ
							if ((trackingObj.getActualType() != null && refObjType.endsWith(trackingObj.getActualType() + ";")) 
									|| (trackingObj.getCalleeType() != null && refObjType.endsWith(trackingObj.getCalleeType() + ";"))
									|| (trackingObj.getCallerType() != null && refObjType.endsWith(trackingObj.getCallerType() + ";"))) {
								// �z��̗v�f�̕����ǐՒ��̃I�u�W�F�N�g�̌^�ƈ�v�����ꍇ
								String ownerObjectId = fs.getContainerObjId();
								if (ownerObjectId.equals(thisObjectId)) {
									// �t�B�[���h�Q�Ƃ̏ꍇ�i���ɗR���̉\�����Ȃ��Ƃ킩�������_�ŁA���̔z��̗v�f�ɗR�����Ă�����̂Ɛ�������B�j
									fieldArrays.add(new ObjectReference(fs.getValueObjId(), refObjType));
									fieldArrayElements.add(trackingObj);
								} else {
									// ���ڎQ�Ƃ̏ꍇ(�{���ɂ��̔z��̗v�f����擾���ꂽ���̂Ȃ炱���ŒǐՑΏۂ�u��������ׂ������A
									// ���̎��_�ő��̗R���̉\����r���ł��Ȃ��B�����ŒǐՑΏۂ�u�������Ă��܂��ƁA��ŕʂɗR�������邱�Ƃ��킩�����ꍇ��
									// ��蒼��������B)
								}
							}
						}
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
					} else {
						// �I�u�W�F�N�g�̗R�������ڌ�����Ȃ������ꍇ�ł��A�ǂ����̔z��̗v�f�ɗR�����Ă���\��������
						String retType = ret.getActualType();
						if (retType.startsWith("[L")) {
							// �߂�l���z��̏ꍇ
							if ((trackingObj.getActualType() != null && retType.endsWith(trackingObj.getActualType() + ";"))
											|| (trackingObj.getCalleeType() != null && retType.endsWith(trackingObj.getCalleeType() + ";"))
											|| (trackingObj.getCallerType() != null && retType.endsWith(trackingObj.getCallerType() + ";"))) {
								// �{���ɂ��̔z��̗v�f����擾���ꂽ���̂Ȃ炱���ŒǐՑΏۂ�u�������āA�Ăяo�����T�����ׂ������A
								// ���̎��_�ő��̗R���̉\����r���ł��Ȃ��B�����ŒǐՑΏۂ�u�������Ă��܂��ƁA��ŕʂɗR�������邱�Ƃ��킩�����ꍇ��
								// ��蒼��������B
							}
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
		
		if (!isResolved && objectId != null) {
			// �R�����ǂ��ɂ�������Ȃ�����
			boolean isSrcSide = true;
			if (objectId.equals(srcObject.getId())) {
				isSrcSide = true;
			} else if (objectId.equals(dstObject.getId())) {
				isSrcSide = false;				
			}
			if (trackingObj != null) {
				// �܂��z������̗v�f��R���Ƃ��ċ^��(�������D��)
				for (int i = 0; i < argments.size(); i++) {
					ObjectReference argArray = argments.get(i);
					if (argArray.getActualType().startsWith("[L") 
							&& ((trackingObj.getActualType() != null && argArray.getActualType().endsWith(trackingObj.getActualType() + ";"))
									|| (trackingObj.getCalleeType() != null && argArray.getActualType().endsWith(trackingObj.getCalleeType() + ";"))
									|| (trackingObj.getCallerType() != null && argArray.getActualType().endsWith(trackingObj.getCallerType() + ";")))) {
						// �^����v������z������̗v�f��R���Ƃ݂Ȃ�
						isResolved = true;
						objList.set(index, argArray.getId());	// �ǐՑΏۂ�z��v�f����z��ɒu������
						((DeltaAugmentationInfo)methodExecution.getAugmentation()).setTraceObjectId(Integer.parseInt(argArray.getId()));
						r = new Reference(argArray.getId(), trackingObj.getId(),
								argArray.getActualType(), trackingObj.getActualType());
						r.setArray(true);
						if (isSrcSide) {
							eStructure.addSrcSide(r);
							srcObject = new ObjectReference(argArray.getId(), argArray.getActualType());
						} else {
							eStructure.addDstSide(r);
							dstObject = new ObjectReference(argArray.getId(), argArray.getActualType());
						}
						objectId = null;
						break;
					}
				}
				if (objectId != null) {
					// ���ɔz��t�B�[���h�̗v�f��R���Ƃ��ċ^��(�t�B�[���h�͈�������)
					int indArg = fieldArrayElements.indexOf(trackingObj);
					if (indArg != -1) {
						// �^����v���Ă�̂Ŕz��t�B�[���h�̗v�f��R���Ƃ݂Ȃ�
						isResolved = true;
						ObjectReference fieldArray = fieldArrays.get(indArg);
						objList.set(index, thisObjectId);	// �ǐՑΏۂ�this�ɒu������
						r = new Reference(fieldArray.getId(), trackingObj.getId(),
								fieldArray.getActualType(), trackingObj.getActualType());
						r.setArray(true);
						if (isSrcSide) {
							eStructure.addSrcSide(r);
							eStructure.addSrcSide(new Reference(thisObjectId, fieldArray.getId(),
									methodExecution.getThisClassName(), fieldArray.getActualType()));
							srcObject = thisObj;
						} else {
							eStructure.addDstSide(r);
							eStructure.addDstSide(new Reference(thisObjectId, fieldArray.getId(),
									methodExecution.getThisClassName(), fieldArray.getActualType()));
							dstObject = thisObj;
						}
					}
				}
				if (trackingObj.getActualType() != null && trackingObj.getActualType().startsWith("[L")) {
					// �ǂ��ɂ�������Ȃ������ꍇ�A�T���Ă���̂��z��^�Ȃ�΁A���̃��\�b�h���Ő������ꂽ���̂ƍl����
					isResolved = true;
					objList.set(index, thisObjectId);	// �ǐՑΏۂ�this�ɒu������
					if (isSrcSide) {
						eStructure.addSrcSide(new Reference(thisObjectId, trackingObj.getId(),
								methodExecution.getThisClassName(), trackingObj.getActualType()));
						srcObject = thisObj;
					} else {
						eStructure.addDstSide(new Reference(thisObjectId, trackingObj.getId(),
								methodExecution.getThisClassName(), trackingObj.getActualType()));
						dstObject = thisObj;
					}
				}
			}
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
	
	/**
	 * �݌v�ύX��̃A���S���Y���̋N�����\�b�h(������)
	 * @param targetRef �ΏۂƂȂ�Q��
	 * @param before �T���J�n�g���[�X�|�C���g(������ȑO��T��)
	 * @return ���o����
	 */
	public ExtractedStructure extract(Reference targetRef, TracePoint before) {
		TracePoint creationTracePoint;
		if (targetRef.isArray()) {
			// srcId �̔z��� dstId ���������Ă���\�������郁�\�b�h���s���擾�i�z���p�̏����j
			creationTracePoint = trace.getArraySetTracePoint(targetRef, before);					
		} else if (targetRef.isCollection()) {
			// srcId �̃R���N�V�����^�I�u�W�F�N�g�� dstId ���n����Ă��郁�\�b�h���s���擾�i�R���N�V�����^��p�̏����j
			creationTracePoint = trace.getCollectionAddTracePoint(targetRef, before);
		} else if (targetRef.isFinalLocal()) {
			// srcId �̓����܂��͖����N���X�̃C���X�^���X�� final local �ϐ��ɑ������Ă��� dstId �� �I�u�W�F�N�g���n���ꂽ�\�������郁�\�b�h���s���擾�ifinal local�̋^��������ꍇ�̏����j
			creationTracePoint = trace.getCreationTracePoint(targetRef.getSrcObject(), before);
			targetRef = new Reference(creationTracePoint.getMethodExecution().getThisObjId(), targetRef.getDstObjectId(), creationTracePoint.getMethodExecution().getThisClassName(), targetRef.getDstClassName());	
		} else {
			// �I�u�W�F�N�g�ԎQ�� r ���������ꂽ���\�b�h���s���擾�i�ʏ�j
			creationTracePoint = trace.getFieldUpdateTracePoint(targetRef, before);
		}
		if (creationTracePoint == null) {
			return null;
		}
		return extractSub(creationTracePoint, targetRef);
	}
	
	/**
	 * �݌v�ύX��̃A���S���Y���̋N�����\�b�h(������)
	 * @param creationTracePoint �I�u�W�F�N�g�ԎQ�Ɛ����g���[�X�|�C���g(�t�B�[���h�ւ̑��)
	 * @return ���o����
	 */
	public ExtractedStructure extract(TracePoint creationTracePoint) {
		creationTracePoint = creationTracePoint.duplicate();
		Statement statement = creationTracePoint.getStatement();
		if (statement instanceof FieldUpdate) {
			Reference targetRef = ((FieldUpdate)statement).getReference();
			return extractSub(creationTracePoint, targetRef);
		} else {
			return null;
		}
	}

	private ExtractedStructure extractSub(TracePoint creationTracePoint, Reference targetRef) {
		eStructure = new ExtractedStructure();
		ArrayList<String> objList = new ArrayList<String>(); 
		srcObject = targetRef.getSrcObject();
		dstObject = targetRef.getDstObject();
if (DEBUG1) {
		System.out.println("extract delta of:" + targetRef.getSrcObject().getActualType() + "(" + targetRef.getSrcObjectId() + ")" + " -> " + targetRef.getDstObject().getActualType()  + "(" + targetRef.getDstObjectId() + ")");
}
		if (!Trace.isNull(targetRef.getSrcObjectId())) {
			objList.add(targetRef.getSrcObjectId());
		} else {
			objList.add(null);
		}
		if (!Trace.isNull(targetRef.getDstObjectId())) {
			objList.add(targetRef.getDstObjectId());
		} else {
			objList.add(null);
		}
		return extractSub2(creationTracePoint, objList);
	}

	public ExtractedStructure extract(TracePoint tracePoint, ObjectReference argObj) {
		MethodExecution methodExecution = tracePoint.getMethodExecution();
		eStructure = new ExtractedStructure();
		ArrayList<String> objList = new ArrayList<String>();
		String thisObjectId = methodExecution.getThisObjId();
		objList.add(thisObjectId);
		objList.add(argObj.getId());
		srcObject = new ObjectReference(thisObjectId, methodExecution.getThisClassName(), 
				Trace.getDeclaringType(methodExecution.getSignature(), methodExecution.isConstructor()), Trace.getDeclaringType(methodExecution.getCallerSideSignature(), methodExecution.isConstructor()));
		dstObject = argObj;
if (DEBUG1) {
		System.out.println("extract delta of:" + methodExecution.getSignature() + " -> " + argObj.getActualType()  + "(" + argObj.getId() + ")");
}
		return extractSub2(tracePoint, objList);
	}
	
	private ExtractedStructure extractSub2(TracePoint creationTracePoint, ArrayList<String> objList) {
		eStructure.setCreationMethodExecution(creationTracePoint.getMethodExecution());
		MethodExecution coordinator = callerSearch(trace, creationTracePoint, objList, null);
		eStructure.setCoordinator(coordinator);
if (DEBUG2) {
		if (((DeltaAugmentationInfo)coordinator.getAugmentation()).isCoodinator()) {
			System.out.println("Coordinator");
		} else {
			System.out.println("Warning");
		}
		System.out.println("coordinator:" + coordinator.getSignature());
		System.out.println("srcSide:");
		for (int i = 0; i < eStructure.getDelta().getSrcSide().size(); i++) {
			Reference ref = eStructure.getDelta().getSrcSide().get(i);
			if (!ref.isCreation() || !ref.getSrcObjectId().equals(ref.getDstObjectId())) {
				System.out.println("\t" + ref.getSrcClassName() + "(" + ref.getSrcObjectId() + ")" + " -> " + ref.getDstClassName() + "(" + ref.getDstObjectId() + ")");
			}
		}
		System.out.println("dstSide:");
		for (int i = 0; i < eStructure.getDelta().getDstSide().size(); i++) {
			Reference ref = eStructure.getDelta().getDstSide().get(i);
			if (!ref.isCreation() || !ref.getSrcObjectId().equals(ref.getDstObjectId())) {
				System.out.println("\t" + ref.getSrcClassName() + "(" + ref.getSrcObjectId() + ")" + " -> " + ref.getDstClassName() + "(" + ref.getDstObjectId() + ")");
			}
		}
		System.out.println("overCoordinator:");
		MethodExecution parent = coordinator.getParent();
		while (parent != null) {
			System.out.println("\t" + parent.getSignature());
			parent = parent.getParent();
		}
}
		return eStructure;
	}
	
	/**
	 * �Q�ƌ��ƎQ�Ɛ�̃I�u�W�F�N�g���w�肵�ăf���^�𒊏o����(�I�����C����͗p)
	 * @param srcObj �Q�ƌ��I�u�W�F�N�g
	 * @param dstObj �Q�Ɛ�I�u�W�F�N�g
	 * @param before �T���J�n�g���[�X�|�C���g(������ȑO��T��)
	 * @return�@���o����
	 */
	public ExtractedStructure extract(Object srcObj, Object dstObj, TracePoint before) {
		Reference targetRef = new Reference(Integer.toString(System.identityHashCode(srcObj)), Integer.toString(System.identityHashCode(dstObj)), null, null);
		return extract(targetRef, before);		
	}

	
	/**
	 * ���\�b�h���s���̃g���[�X�|�C���g�ƎQ�Ɛ�I�u�W�F�N�g���w�肵�ăf���^�𒊏o����(�I�����C����͗p)
	 * @param tracePoint ���\�b�h���s���̃g���[�X�|�C���g
	 * @param arg �Q�Ɛ�I�u�W�F�N�g(���[�J���ϐ�������ɂ��Q�Ɛ�)
	 * @return ���o����
	 */
	public ExtractedStructure extract(TracePoint tracePoint, Object arg) {
		ObjectReference argObj = new ObjectReference(Integer.toString(System.identityHashCode(arg)));
		return extract(tracePoint, argObj);
	}
	
	/**
	 * �w�肵���X���b�h��Ō��ݎ��s���̃��\�b�h���s���擾����(�I�����C����͗p)
	 * @param thread �ΏۃX���b�h
	 * @return thread ��Ō��ݎ��s���̃��\�b�h���s
	 */
	public MethodExecution getCurrentMethodExecution(Thread thread) {
		return trace.getCurrentMethodExecution(thread);
	}

	/**
	 * methodSignature �ɑO����v���郁�\�b�h���������\�b�h�̍Ō�̎��s
	 * @param methodSignature ���\�b�h��(�O����v�Ō�������)
	 * @return �Y������Ō�̃��\�b�h���s
	 */
	public MethodExecution getLastMethodExecution(String methodSignature) {
		return trace.getLastMethodExecution(methodSignature);
	}

	/**
	 * methodSignature �ɑO����v���郁�\�b�h���������\�b�h�� before �ȑO�̍Ō�̎��s
	 * @param methodSignature ���\�b�h��(�O����v�Ō�������)
	 * @param before�@�T���J�n�g���[�X�|�C���g(������ȑO��T��)
	 * @return�@�Y������Ō�̃��\�b�h���s
	 */
	public MethodExecution getLastMethodExecution(String methodSignature, TracePoint before) {
		return trace.getLastMethodExecution(methodSignature, before);
	}

	public ArrayList<MethodExecution> getMethodExecutions(String methodSignature) {
		return trace.getMethodExecutions(methodSignature);
	}
		
//	public ExtractedStructure extract(MethodExecution caller, MethodExecution callee) {
//		eStructure = new ExtractedStructure();
//		ArrayList<String> objList = new ArrayList<String>();
//		String thisObjectId = caller.getThisObjId();
//		objList.add(thisObjectId);
//		objList.add(callee.getThisObjId());
//		srcObject = new ObjectReference(thisObjectId, caller.getThisClassName(), 
//				Trace.getDeclaringType(caller.getSignature(), caller.isConstractor()), Trace.getDeclaringType(caller.getCallerSideSignature(), caller.isConstractor()));
//		dstObject = new ObjectReference(callee.getThisObjId(), callee.getThisClassName(), 
//				Trace.getDeclaringType(callee.getSignature(), callee.isConstractor()), Trace.getDeclaringType(callee.getCallerSideSignature(), callee.isConstractor()));
//if (DEBUG1) {
//		System.out.println("extract delta of:" + caller.getSignature() + " -> " + callee.getSignature());
//}
//		
//		caller = new MethodExecution(caller);		// ��͗p�p�����[�^���������������̂��g�p����
//		eStructure.setCreationMethodExecution(caller);
//		MethodExecution coordinator = callerSearch(trace, caller, objList, null);
//		eStructure.setCoordinator(coordinator);
//if (DEBUG2) {
//		if (coordinator.isCoodinator()) {
//			System.out.println("Coordinator");
//		} else {
//			System.out.println("Warning");
//		}
//		System.out.println("coordinator:" + coordinator.getSignature());
//		System.out.println("srcSide:");
//		for (int i = 0; i < eStructure.getDelta().getSrcSide().size(); i++) {
//			Reference ref = eStructure.getDelta().getSrcSide().get(i);
//			if (!ref.isCreation() || !ref.getSrcObjectId().equals(ref.getDstObjectId())) {
//				System.out.println("\t" + ref.getSrcClassName() + "(" + ref.getSrcObjectId() + ")" + " -> " + ref.getDstClassName() + "(" + ref.getDstObjectId() + ")");
//			}
//		}
//		System.out.println("dstSide:");
//		for (int i = 0; i < eStructure.getDelta().getDstSide().size(); i++) {
//			Reference ref = eStructure.getDelta().getDstSide().get(i);
//			if (!ref.isCreation() || !ref.getSrcObjectId().equals(ref.getDstObjectId())) {
//				System.out.println("\t" + ref.getSrcClassName() + "(" + ref.getSrcObjectId() + ")" + " -> " + ref.getDstClassName() + "(" + ref.getDstObjectId() + ")");
//			}
//		}
//		System.out.println("overCoordinator:");
//		MethodExecution parent = coordinator.getParent();
//		while (parent != null) {
//			System.out.println("\t" + parent.getSignature());
//			parent = parent.getParent();
//		}
//}
//		return eStructure;	
//	}
//	
//	
//	/**
//	 * ���\�b�h�̈����Ƃ��ăI�u�W�F�N�g���Q�Ƃ����ꍇ�̃f���^�𒊏o����
//	 * @param caller �Q�ƌ��̃��\�b�h
//	 * @param argObj �����Ƃ��ĎQ�Ƃ����I�u�W�F�N�g
//	 * @return�@���o����
//	 */
//	public ExtractedStructure extract(MethodExecution caller, ObjectReference argObj) {
//		eStructure = new ExtractedStructure();
//		ArrayList<String> objList = new ArrayList<String>();
//		String thisObjectId = caller.getThisObjId();
//		objList.add(thisObjectId);
//		objList.add(argObj.getId());
//		srcObject = new ObjectReference(thisObjectId, caller.getThisClassName(), 
//				Trace.getDeclaringType(caller.getSignature(), caller.isConstractor()), Trace.getDeclaringType(caller.getCallerSideSignature(), caller.isConstractor()));
//		dstObject = argObj;
//if (DEBUG1) {
//		System.out.println("extract delta of:" + caller.getSignature() + " -> " + argObj.getActualType()  + "(" + argObj.getId() + ")");
//}
//		
//		caller = new MethodExecution(caller);		// ��͗p�p�����[�^���������������̂��g�p����
//		eStructure.setCreationMethodExecution(caller);
//		MethodExecution coordinator = callerSearch(trace, caller, objList, null);
//		eStructure.setCoordinator(coordinator);
//if (DEBUG2) {
//		if (coordinator.isCoodinator()) {
//			System.out.println("Coordinator");
//		} else {
//			System.out.println("Warning");
//		}
//		System.out.println("coordinator:" + coordinator.getSignature());
//		System.out.println("srcSide:");
//		for (int i = 0; i < eStructure.getDelta().getSrcSide().size(); i++) {
//			Reference ref = eStructure.getDelta().getSrcSide().get(i);
//			if (!ref.isCreation() || !ref.getSrcObjectId().equals(ref.getDstObjectId())) {
//				System.out.println("\t" + ref.getSrcClassName() + "(" + ref.getSrcObjectId() + ")" + " -> " + ref.getDstClassName() + "(" + ref.getDstObjectId() + ")");
//			}
//		}
//		System.out.println("dstSide:");
//		for (int i = 0; i < eStructure.getDelta().getDstSide().size(); i++) {
//			Reference ref = eStructure.getDelta().getDstSide().get(i);
//			if (!ref.isCreation() || !ref.getSrcObjectId().equals(ref.getDstObjectId())) {
//				System.out.println("\t" + ref.getSrcClassName() + "(" + ref.getSrcObjectId() + ")" + " -> " + ref.getDstClassName() + "(" + ref.getDstObjectId() + ")");
//			}
//		}
//		System.out.println("overCoordinator:");
//		MethodExecution parent = coordinator.getParent();
//		while (parent != null) {
//			System.out.println("\t" + parent.getSignature());
//			parent = parent.getParent();
//		}
//}
//		return eStructure;	
//	}
}
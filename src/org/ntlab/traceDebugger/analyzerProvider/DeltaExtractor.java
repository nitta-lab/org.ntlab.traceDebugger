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
 * デルタ抽出アルゴリズム(配列へのアクセスを推測する従来のバージョン)
 *    extract(...)メソッド群で抽出する。
 * 
 * @author Nitta
 *
 */
public class DeltaExtractor {
	protected static final int LOST_DECISION_EXTENSION = 0;		// 基本は 0 に設定。final変数の追跡アルゴリズムの不具合修正後は不要のはず。
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
	protected int finalCount = 0;			// final変数を検出できない可能性があるので、由来の解決ができなかった場合でもしばらく追跡しつづける
	
	protected static final boolean DEBUG1 = true;
	protected static final boolean DEBUG2 = true;
	protected final IAliasCollector defaultAliasCollector = new IAliasCollector() {
		@Override
		public void changeTrackingObject(String from, String to) {
		}
		@Override
		public void addAlias(Alias alias) {
		}
	};
	
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
	 * デルタ抽出アルゴリズムの呼び出し元探索部分（calleeSearchと相互再帰になっている）
	 * @param trace　解析対象トレース
	 * @param methodExecution 探索するメソッド実行
	 * @param objList　追跡中のオブジェクト
	 * @param child　直前に探索していた呼び出し先のメソッド実行
	 * @return 見つかったコーディネータ
	 * @throws TraceFileException
	 */
	protected MethodExecution callerSearch(Trace trace, TracePoint tracePoint, ArrayList<String> objList, MethodExecution childMethodExecution) {
		return callerSearch(trace, tracePoint, objList, childMethodExecution, defaultAliasCollector);
	}
 
	/**
	 * デルタ抽出アルゴリズムの呼び出し元探索部分（calleeSearchと相互再帰になっている）
	 * @param trace　解析対象トレース
	 * @param methodExecution 探索するメソッド実行
	 * @param objList　追跡中のオブジェクト
	 * @param child　直前に探索していた呼び出し先のメソッド実行
	 * @return 見つかったコーディネータ
	 * @throws TraceFileException
	 */
	protected MethodExecution callerSearch(Trace trace, TracePoint tracePoint, ArrayList<String> objList, MethodExecution childMethodExecution, IAliasCollector aliasCollector) {
		MethodExecution methodExecution = tracePoint.getMethodExecution();
		methodExecution.setAugmentation(new DeltaAugmentationInfo());
		eStructure.createParent(methodExecution);
		String thisObjectId = methodExecution.getThisObjId();
		ArrayList<String> removeList = new ArrayList<String>();		// 追跡しているオブジェクト中で削除対象となっているもの
		ArrayList<String> creationList = new ArrayList<String>();	// このメソッド実行中に生成されたオブジェクト
		int existsInFields = 0;			// このメソッド実行内でフィールドに由来しているオブジェクトの数(1以上ならこのメソッド実行内でthisに依存)
		boolean isTrackingThis = false;	// 呼び出し先でthisに依存した
		boolean isSrcSide = true;		// 参照元か参照先のいずれの側のオブジェクトの由来をたどってthisオブジェクトに到達したか?
		ArrayList<ObjectReference> fieldArrays = new ArrayList<ObjectReference>();
		ArrayList<ObjectReference> fieldArrayElements = new ArrayList<ObjectReference>();
		ObjectReference thisObj = new ObjectReference(thisObjectId, methodExecution.getThisClassName(), 
				Trace.getDeclaringType(methodExecution.getSignature(), methodExecution.isConstructor()), Trace.getDeclaringType(methodExecution.getCallerSideSignature(), methodExecution.isConstructor()));
		
		if (childMethodExecution == null) {
			// 探索開始時は一旦削除し、呼び出し元の探索を続ける際に復活させる
			removeList.add(thisObjectId);		// 後で一旦、thisObject を取り除く
			isTrackingThis = true;				// 呼び出し元探索前に復活
		}
		
		if (childMethodExecution != null && objList.contains(childMethodExecution.getThisObjId())) {
			// 呼び出し先でthisに依存した
			if (thisObjectId.equals(childMethodExecution.getThisObjId())) {
				// オブジェクト内呼び出しのときのみ一旦削除し、呼び出し元の探索を続ける際に復活させる
				removeList.add(thisObjectId);		// 後で一旦、thisObject を取り除く
				isTrackingThis = true;				// 呼び出し元探索前に復活
			}
		}
		
		if (childMethodExecution != null && childMethodExecution.isConstructor()) {
			// 呼び出し先がコンストラクタだった場合
			int newIndex = objList.indexOf(childMethodExecution.getThisObjId());
			if (newIndex != -1) {
				// 呼び出し先が追跡対象のコンストラクタだったらfieldと同様に処理
				removeList.add(childMethodExecution.getThisObjId());
				existsInFields++;
				removeList.add(thisObjectId);		// 後で一旦、thisObject を取り除く
			}
		}
		
		if (childMethodExecution != null && Trace.getMethodName(childMethodExecution.getSignature()).startsWith("access$")) {
			// エンクロージングインスタンスに対するメソッド呼び出しだった場合
			String enclosingObj = childMethodExecution.getArguments().get(0).getId();	// エンクロージングインスタンスは第一引数に入っているらしい
			int encIndex = objList.indexOf(enclosingObj);
			if (encIndex != -1) {
				// thisObject に置き換えた後、fieldと同様に処理
				removeList.add(enclosingObj);
				existsInFields++;
				removeList.add(thisObjectId);		// 後で一旦、thisObject を取り除く
			}
		}
 
		// 戻り値に探索対象が含まれていればcalleeSearchを再帰呼び出し
		while (tracePoint.stepBackOver()) {
			Statement statement = tracePoint.getStatement();
			// 直接参照およびフィールド参照の探索
			if (statement instanceof FieldAccess) {
				FieldAccess fs = (FieldAccess)statement;
				String refObjectId = fs.getValueObjId();
				int index = objList.indexOf(refObjectId);
				if (index != -1) {
					String ownerObjectId = fs.getContainerObjId();
					if (ownerObjectId.equals(thisObjectId)) {
						// フィールド参照の場合
						removeList.add(refObjectId);
						existsInFields++;					// setした後のgetを検出している可能性がある
						removeList.add(thisObjectId);		// 後で一旦、thisObject を取り除く
					} else {
						// 直接参照の場合
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
					// 最終的にオブジェクトの由来が見つからなかった場合に、ここで参照した配列内部の要素に由来している可能性がある
					String refObjType = fs.getValueClassName();
					if (refObjType.startsWith("[L")) {
						// 参照したフィールドが配列の場合
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
							// 追跡中のオブジェクトに、配列要素と同じ型を持つオブジェクトが存在する場合
							String ownerObjectId = fs.getContainerObjId();
							if (ownerObjectId.equals(thisObjectId)) {
								// フィールド参照の場合（他に由来の可能性がないとわかった時点で、この配列の要素に由来しているものと推測する。）
								fieldArrays.add(new ObjectReference(refObjectId, refObjType));
								fieldArrayElements.add(trackingObj);
							} else {
								// 直接参照の場合(本当にこの配列の要素から取得されたものならここで追跡対象を置き換えるべきだが、
								// この時点で他の由来の可能性を排除できない。ここで追跡対象を置き換えてしまうと、後で別に由来があることがわかった場合に
								// やり直しが困難。)
							}
						}
					}
				}
			} else if (statement instanceof MethodInvocation) {
				MethodExecution prevChildMethodExecution = ((MethodInvocation)statement).getCalledMethodExecution();
				if (!prevChildMethodExecution.equals(childMethodExecution)) {
					// 戻り値
					ObjectReference ret = prevChildMethodExecution.getReturnValue();
					if (ret != null) {
						int retIndex = -1;
						retIndex = objList.indexOf(ret.getId());
						if (retIndex != -1) {
							// 戻り値が由来だった
							prevChildMethodExecution.setAugmentation(new DeltaAugmentationInfo());
							if (prevChildMethodExecution.isConstructor()) {
								// 追跡対象のconstractorを呼んでいたら(オブジェクトの生成だったら)fieldと同様に処理
								String newObjId = ret.getId();
								creationList.add(newObjId);
								removeList.add(newObjId);
								existsInFields++;
		//						objList.remove(callTree.getThisObjId());
								removeList.add(thisObjectId);		// 後で一旦、thisObject を取り除く
								((DeltaAugmentationInfo)prevChildMethodExecution.getAugmentation()).setTraceObjectId(Integer.parseInt(newObjId));		// 追跡対象
								((DeltaAugmentationInfo)prevChildMethodExecution.getAugmentation()).setSetterSide(false);	// getter呼び出しと同様
								continue;
							}
							String retObj = objList.get(retIndex);
							if (removeList.contains(retObj)) {
								// 一度getで検出してフィールドに依存していると判断したが本当の由来が戻り値だったことが判明したので、フィールドへの依存をキャンセルする
								removeList.remove(retObj);
								existsInFields--;
								if (existsInFields == 0) {
									removeList.remove(thisObjectId);
								}
							}
							((DeltaAugmentationInfo)prevChildMethodExecution.getAugmentation()).setTraceObjectId(Integer.parseInt(retObj));					// 追跡対象
							TracePoint prevChildTracePoint = tracePoint.duplicate();
							prevChildTracePoint.stepBackNoReturn();
							calleeSearch(trace, prevChildTracePoint, objList, prevChildMethodExecution.isStatic(), retIndex, aliasCollector);	// 呼び出し先を探索
							if (objList.get(retIndex) != null && objList.get(retIndex).equals(prevChildMethodExecution.getThisObjId()) 
									&& thisObjectId.equals(prevChildMethodExecution.getThisObjId())) {
								// 呼び出し先でフィールドに依存していた場合の処理
								removeList.add(thisObjectId);		// 後で一旦、thisObject を取り除く
								isTrackingThis = true;				// 呼び出し元探索前に復活
							}
							if (isLost) {
								checkList.add(objList.get(retIndex));
								isLost = false;
							}
						} else {
							// 最終的にオブジェクトの由来が見つからなかった場合に、この戻り値で取得した配列内部の要素に由来している可能性がある
							String retType = ret.getActualType();
							if (retType.startsWith("[L")) {
								// 戻り値が配列の場合
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
									// 本当にこの配列の要素から取得されたものならここで追跡対象を置き換えて、呼び出し先を探索すべきだが、
									// この時点で他の由来の可能性を排除できない。ここで追跡対象を置き換えてしまうと、後で別に由来があることがわかった場合に
									// やり直しが困難。
								}
							}
						}
					}
				}
			}
		}
		// --- この時点で tracePoint は呼び出し元を指している ---
		
		// コレクション型対応
		if (methodExecution.isCollectionType()) {
			objList.add(thisObjectId);
		}		
 
		// 引数の取得
		ArrayList<ObjectReference> argments = methodExecution.getArguments();
		
		// 引数とフィールドに同じIDのオブジェクトがある場合を想定
		Reference r;
		for (int i = 0; i < removeList.size(); i++) {
			String removeId = removeList.get(i);
			if (argments.contains(new ObjectReference(removeId))) { 
				removeList.remove(removeId);	// フィールドと引数の両方に追跡対象が存在した場合、引数を優先
			} else if(objList.contains(removeId)) {
				// フィールドにしかなかった場合(ただし、オブジェクトの生成もフィールドと同様に扱う)
				objList.remove(removeId);		// 追跡対象から外す
				if (!removeId.equals(thisObjectId)) {
					// フィールド（this から removeId への参照）がデルタの構成要素になる
					if (removeId.equals(srcObject.getId())) {
						r = new Reference(thisObj, srcObject);
						r.setCreation(creationList.contains(removeId));		// オブジェクトの生成か?
						eStructure.addSrcSide(r);
						srcObject = thisObj;
						isSrcSide = true;
					} else if (removeId.equals(dstObject.getId())) {
						r = new Reference(thisObj, dstObject);
						r.setCreation(creationList.contains(removeId));		// オブジェクトの生成か?
						eStructure.addDstSide(r);
						dstObject = thisObj;
						isSrcSide = false;
					}					
				}
			}
		}
		// --- この時点で this が追跡対象であったとしても objList の中からいったん削除されている ---
		
		// 引数探索
		boolean existsInAnArgument = false;
		for (int i = 0; i < objList.size(); i++) {
			String objectId = objList.get(i);
			if (objectId != null) {
				ObjectReference trackingObj = new ObjectReference(objectId);
				if (argments.contains(trackingObj)) {
					// 引数が由来だった
					existsInAnArgument = true;
					((DeltaAugmentationInfo)methodExecution.getAugmentation()).setTraceObjectId(Integer.parseInt(objectId));
				} else {
					// 由来がどこにも見つからなかった
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
						// まず配列引数の要素を由来として疑う(引数を優先)
						for (int j = 0; j < argments.size(); j++) {
							ObjectReference argArray = argments.get(j);
							if (argArray.getActualType().startsWith("[L") 
									&& (trackingObj.getActualType() != null && (argArray.getActualType().endsWith(trackingObj.getActualType() + ";"))
											|| (trackingObj.getCalleeType() != null && argArray.getActualType().endsWith(trackingObj.getCalleeType() + ";"))
											|| (trackingObj.getCallerType() != null && argArray.getActualType().endsWith(trackingObj.getCallerType() + ";")))) {
								// 型が一致したら配列引数の要素を由来とみなす
								existsInAnArgument = true;
								objList.remove(objectId);
								objList.add(argArray.getId());	// 追跡対象を配列要素から配列に置き換え
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
							// 次に配列フィールドの要素を由来として疑う(フィールドは引数より後)
							int index = fieldArrayElements.indexOf(trackingObj);
							if (index != -1) {
								// 型が一致してるので配列フィールドの要素を由来とみなす
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
							// どこにも見つからなかった場合、探しているのが配列型ならば、このメソッド内で生成されたものと考える
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
			// 引数に1つでも追跡対象が存在した場合
			if (existsInFields > 0 || isTrackingThis) {
				// thisオブジェクトを追跡中の場合
				if (!Trace.isNull(thisObjectId)) {
					objList.add(thisObjectId);	// さらに探索する場合、一旦取り除いた thisObject を復活
				} else {
					objList.add(null);			// ただしstatic呼び出しだった場合、それ以上追跡しない
				}				
			}
//			if (existsInFields > 0) {
//				// フィールドを由来に持つオブジェクトが存在した場合
//				if (isSrcSide) {
//					srcObject = thisObj;
//				} else {
//					dstObject = thisObj;
//				}
//			}
			if (tracePoint.isValid()) {
				finalCount = 0;
				return callerSearch(trace, tracePoint, objList, methodExecution, aliasCollector);		// 呼び出し元をさらに探索				
			}
		}
		
		for (int i = 0; i < objList.size(); i++) {
			objList.remove(null);
		}
		if (objList.isEmpty()) {
			((DeltaAugmentationInfo)methodExecution.getAugmentation()).setCoodinator(true);
		} else {
			// 由来を解決できなかった
			if (!methodExecution.isStatic()) {
				finalCount++;
				if (finalCount <= LOST_DECISION_EXTENSION) {
					// final変数を参照している場合由来を解決できない可能性があるので、追跡をすぐ終了せず猶予期間を設ける
					if (tracePoint.isValid()) { 
						MethodExecution c = callerSearch(trace, tracePoint, objList, methodExecution, aliasCollector);		// 呼び出し元をさらに探索	
						if (((DeltaAugmentationInfo)c.getAugmentation()).isCoodinator()) {
							methodExecution = c;		// 追跡を続けた結果コーディネータが見つかった
						}
					}
				} else if (thisObj.getActualType().contains("$")) {
					// 自分が内部または無名クラスの場合、見失ったオブジェクトを外側メソッドの内のfinal変数から取得したとみなし、さらに自分の中のフィールドの一種とみなす
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
	 * デルタ抽出アルゴリズムの呼び出し先探索部分(再帰呼び出しになっている)
	 * @param trace 解析対象トレース
	 * @param methodExecution 探索するメソッド実行
	 * @param objList 追跡中のオブジェクト
	 * @param isStatic　静的メソッドか否か
	 * @param index　objList中のどのオブジェクトを追跡してこのメソッド実行に入ってきたのか
	 * @throws TraceFileException
	 */
	protected void calleeSearch(Trace trace, TracePoint tracePoint, ArrayList<String> objList, Boolean isStatic, int index, IAliasCollector aliasCollector) {
		MethodExecution methodExecution = tracePoint.getMethodExecution();
		Boolean isResolved = false;
		String objectId = objList.get(index);		// calleeSearch() では追跡対象のオブジェクトは一つだけ、※objListはindex番目の要素以外変更してはいけない
		String thisObjectId = methodExecution.getThisObjId();
		ArrayList<ObjectReference> fieldArrays = new ArrayList<ObjectReference>();
		ArrayList<ObjectReference> fieldArrayElements = new ArrayList<ObjectReference>();
		ObjectReference thisObj = new ObjectReference(thisObjectId, methodExecution.getThisClassName(), 
				Trace.getDeclaringType(methodExecution.getSignature(), methodExecution.isConstructor()), 
				Trace.getDeclaringType(methodExecution.getCallerSideSignature(), methodExecution.isConstructor()));
		
		((DeltaAugmentationInfo)methodExecution.getAugmentation()).setSetterSide(false);		// 基本的にgetter呼び出しのはずだが、注意
		ArrayList<ObjectReference> argments = methodExecution.getArguments();
		ObjectReference trackingObj = null;
		//staticを経由するとnullが入っている時がある
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
			// 戻り値に探索対象が含まれていればcalleeSearch呼び出し
			do {
				if (!tracePoint.isValid()) break;
				Statement statement = tracePoint.getStatement();
				// 直接参照およびフィールド参照の探索
				if (statement instanceof FieldAccess) {
					FieldAccess fs = (FieldAccess)statement;
					if (objectId != null && objectId.equals(fs.getValueObjId())) {
						String ownerObjectId = fs.getContainerObjId();
						if (ownerObjectId.equals(thisObjectId)) {							
							// フィールド参照の場合
							if (objectId.equals(srcObject.getId())) {
								eStructure.addSrcSide(new Reference(thisObj, srcObject));
								srcObject = thisObj;
								trackingObj = srcObject;
							} else if(objectId.equals(dstObject.getId())) {
								eStructure.addDstSide(new Reference(thisObj, dstObject));
								dstObject = thisObj;
								trackingObj = dstObject;
							}
							if (Trace.isNull(thisObjectId)) objectId = null;	// static変数の場合
							else objectId = thisObjectId;
							objList.set(index, objectId);
						} else {
							// 直接参照の場合
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
							if (Trace.isNull(ownerObjectId)) objectId = null;	// static変数の場合
							else objectId = ownerObjectId;
							objList.set(index, objectId);
						}
						isResolved = true;
					} else {
						// オブジェクトの由来が直接見つからなかった場合でも、いずれかの配列の要素に由来している可能性がある
						String refObjType = fs.getValueClassName();
						if (refObjType.startsWith("[L")) {
							// 参照したフィールドが配列の場合
							if ((trackingObj.getActualType() != null && refObjType.endsWith(trackingObj.getActualType() + ";")) 
									|| (trackingObj.getCalleeType() != null && refObjType.endsWith(trackingObj.getCalleeType() + ";"))
									|| (trackingObj.getCallerType() != null && refObjType.endsWith(trackingObj.getCallerType() + ";"))) {
								// 配列の要素の方が追跡中のオブジェクトの型と一致した場合
								String ownerObjectId = fs.getContainerObjId();
								if (ownerObjectId.equals(thisObjectId)) {
									// フィールド参照の場合（他に由来の可能性がないとわかった時点で、この配列の要素に由来しているものと推測する。）
									fieldArrays.add(new ObjectReference(fs.getValueObjId(), refObjType));
									fieldArrayElements.add(trackingObj);
								} else {
									// 直接参照の場合(本当にこの配列の要素から取得されたものならここで追跡対象を置き換えるべきだが、
									// この時点で他の由来の可能性を排除できない。ここで追跡対象を置き換えてしまうと、後で別に由来があることがわかった場合に
									// やり直しが困難。)
								}
							}
						}
					}
				} else if (statement instanceof MethodInvocation) {
					// 戻り値
					MethodExecution childMethodExecution = ((MethodInvocation)statement).getCalledMethodExecution();
					ObjectReference ret = childMethodExecution.getReturnValue();
					if (ret != null && objectId != null && objectId.equals(ret.getId())) {
						childMethodExecution.setAugmentation(new DeltaAugmentationInfo());
						((DeltaAugmentationInfo)childMethodExecution.getAugmentation()).setTraceObjectId(Integer.parseInt(objectId));
						TracePoint childTracePoint = tracePoint.duplicate();
						childTracePoint.stepBackNoReturn();
						calleeSearch(trace, childTracePoint, objList, childMethodExecution.isStatic(), index, aliasCollector);		// 呼び出し先をさらに探索	
						if (childMethodExecution.isConstructor()) {
							// コンストラクタ呼び出しだった場合
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
							if (Trace.isNull(thisObjectId)) objectId = null;	// static変数の場合
							else objectId = thisObjectId;
							objList.set(index, objectId);
							isResolved = true;
							isLost = false;
							continue;
						}
						objectId = objList.get(index);
						if (objectId == null) {
							// static呼び出しの戻り値だった場合（たぶん）
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
						// オブジェクトの由来が直接見つからなかった場合でも、どこかの配列の要素に由来している可能性がある
						String retType = ret.getActualType();
						if (retType.startsWith("[L")) {
							// 戻り値が配列の場合
							if ((trackingObj.getActualType() != null && retType.endsWith(trackingObj.getActualType() + ";"))
											|| (trackingObj.getCalleeType() != null && retType.endsWith(trackingObj.getCalleeType() + ";"))
											|| (trackingObj.getCallerType() != null && retType.endsWith(trackingObj.getCallerType() + ";"))) {
								// 本当にこの配列の要素から取得されたものならここで追跡対象を置き換えて、呼び出し先を探索すべきだが、
								// この時点で他の由来の可能性を排除できない。ここで追跡対象を置き換えてしまうと、後で別に由来があることがわかった場合に
								// やり直しが困難。
							}
						}
					}
				}
			} while (tracePoint.stepBackOver());
			
			//引数探索
			if (argments.contains(new ObjectReference(objectId))) {
				((DeltaAugmentationInfo)methodExecution.getAugmentation()).setSetterSide(true);		// ※多分必要?
				isResolved = true;
			}
		}
		
		//コレクション型対応
		Reference r;
		if (methodExecution.isCollectionType()) {
			if (objectId != null) {
				// コレクション型の場合、内部で個々の要素を直接保持していると仮定する
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
			isResolved = true;		// 必要なのでは?
		}
		
		if (!isResolved && objectId != null) {
			// 由来がどこにも見つからなかった
			boolean isSrcSide = true;
			if (objectId.equals(srcObject.getId())) {
				isSrcSide = true;
			} else if (objectId.equals(dstObject.getId())) {
				isSrcSide = false;				
			}
			if (trackingObj != null) {
				// まず配列引数の要素を由来として疑う(引数が優先)
				for (int i = 0; i < argments.size(); i++) {
					ObjectReference argArray = argments.get(i);
					if (argArray.getActualType().startsWith("[L") 
							&& ((trackingObj.getActualType() != null && argArray.getActualType().endsWith(trackingObj.getActualType() + ";"))
									|| (trackingObj.getCalleeType() != null && argArray.getActualType().endsWith(trackingObj.getCalleeType() + ";"))
									|| (trackingObj.getCallerType() != null && argArray.getActualType().endsWith(trackingObj.getCallerType() + ";")))) {
						// 型が一致したら配列引数の要素を由来とみなす
						isResolved = true;
						objList.set(index, argArray.getId());	// 追跡対象を配列要素から配列に置き換え
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
					// 次に配列フィールドの要素を由来として疑う(フィールドは引数より後)
					int indArg = fieldArrayElements.indexOf(trackingObj);
					if (indArg != -1) {
						// 型が一致してるので配列フィールドの要素を由来とみなす
						isResolved = true;
						ObjectReference fieldArray = fieldArrays.get(indArg);
						objList.set(index, thisObjectId);	// 追跡対象をthisに置き換え
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
					// どこにも見つからなかった場合、探しているのが配列型ならば、このメソッド内で生成されたものと考える
					isResolved = true;
					objList.set(index, thisObjectId);	// 追跡対象をthisに置き換え
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
		
		if (objectId == null && isResolved && !isStatic) {	// static 呼び出しからの戻り値を返している場合
			objList.set(index, thisObjectId);	// 自分を追跡させる
			if (Trace.isNull(srcObject.getId())) {
				srcObject = thisObj;
			} else if (Trace.isNull(dstObject.getId())) {
				dstObject = thisObj;
			}
		}
		
		if (isStatic && !isResolved) {		// 今は起こりえない?(getポイントカットを取得するようにしたため)
			objList.set(index, null);
		}
		if(!isStatic && !isResolved){
			isLost = true;					// final変数を内部クラスで参照している可能性もあるが、calleeSearch()は必ず呼び出し元に復帰していくので、ここでは何もしない
		}
	}
	
	/**
	 * 設計変更後のアルゴリズムの起動メソッド(高速化)
	 * @param targetRef 対象となる参照
	 * @param before 探索開始トレースポイント(これより以前を探索)
	 * @return 抽出結果
	 */
	public ExtractedStructure extract(Reference targetRef, TracePoint before) {
		return extract(targetRef, before, defaultAliasCollector);
	}
	
	/**
	 * 設計変更後のアルゴリズムの起動メソッド(高速化)
	 * @param targetRef 対象となる参照
	 * @param before 探索開始トレースポイント(これより以前を探索)
	 * @param aliasCollector デルタ抽出時に追跡したオブジェクトの全エイリアスを収集するリスナ
	 * @return 抽出結果
	 */
	public ExtractedStructure extract(Reference targetRef, TracePoint before, IAliasCollector aliasCollector) {
		TracePoint creationTracePoint;
		if (targetRef.isArray()) {
			// srcId の配列に dstId が代入されている可能性があるメソッド実行を取得（配列専用の処理）
			creationTracePoint = trace.getArraySetTracePoint(targetRef, before);					
		} else if (targetRef.isCollection()) {
			// srcId のコレクション型オブジェクトに dstId が渡されているメソッド実行を取得（コレクション型専用の処理）
			creationTracePoint = trace.getCollectionAddTracePoint(targetRef, before);
		} else if (targetRef.isFinalLocal()) {
			// srcId の内部または無名クラスのインスタンスに final local 変数に代入されている dstId の オブジェクトが渡された可能性があるメソッド実行を取得（final localの疑いがある場合の処理）
			creationTracePoint = trace.getCreationTracePoint(targetRef.getSrcObject(), before);
			targetRef = new Reference(creationTracePoint.getMethodExecution().getThisObjId(), targetRef.getDstObjectId(), creationTracePoint.getMethodExecution().getThisClassName(), targetRef.getDstClassName());	
		} else {
			// オブジェクト間参照 r が生成されたメソッド実行を取得（通常）
			creationTracePoint = trace.getFieldUpdateTracePoint(targetRef, before);
		}
		if (creationTracePoint == null) {
			return null;
		}
		return extractSub(creationTracePoint, targetRef, aliasCollector);
	}
	
	/**
	 * 設計変更後のアルゴリズムの起動メソッド(高速化)
	 * @param creationTracePoint オブジェクト間参照生成トレースポイント(フィールドへの代入)
	 * @return 抽出結果
	 */
	public ExtractedStructure extract(TracePoint creationTracePoint) {
		creationTracePoint = creationTracePoint.duplicate();
		Statement statement = creationTracePoint.getStatement();
		if (statement instanceof FieldUpdate) {
			Reference targetRef = ((FieldUpdate)statement).getReference();
			return extractSub(creationTracePoint, targetRef, defaultAliasCollector);
		} else {
			return null;
		}
	}
	
	/**
	 * 設計変更後のアルゴリズムの起動メソッド(高速化)
	 * @param creationTracePoint オブジェクト間参照生成トレースポイント(フィールドへの代入)
	 * @param aliasCollector デルタ抽出時に追跡したオブジェクトの全エイリアスを収集するリスナ
	 * @return 抽出結果
	 */
	public ExtractedStructure extract(TracePoint creationTracePoint, IAliasCollector aliasCollector) {
		creationTracePoint = creationTracePoint.duplicate();
		Statement statement = creationTracePoint.getStatement();
		if (statement instanceof FieldUpdate) {
			Reference targetRef = ((FieldUpdate)statement).getReference();
			return extractSub(creationTracePoint, targetRef, aliasCollector);
		} else {
			return null;
		}
	}
 
	private ExtractedStructure extractSub(TracePoint creationTracePoint, Reference targetRef, IAliasCollector aliasCollector) {
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
		return extractSub2(creationTracePoint, objList, aliasCollector);
	}
	
	public ExtractedStructure extract(TracePoint tracePoint, ObjectReference argObj) {
		return extract(tracePoint, argObj, defaultAliasCollector);
	}
 
	public ExtractedStructure extract(TracePoint tracePoint, ObjectReference argObj, IAliasCollector aliasCollector) {
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
		return extractSub2(tracePoint, objList, aliasCollector);
	}
	
	private ExtractedStructure extractSub2(TracePoint creationTracePoint, ArrayList<String> objList, IAliasCollector aliasCollector) {
		eStructure.setCreationMethodExecution(creationTracePoint.getMethodExecution());
		MethodExecution coordinator = callerSearch(trace, creationTracePoint, objList, null, aliasCollector);
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
	 * 実際の参照元と参照先のオブジェクトを指定してデルタを抽出する(オンライン解析用)
	 * @param srcObj メモリ上にある参照元オブジェクト
	 * @param dstObj メモリ上にある参照先オブジェクト
	 * @param before 探索開始トレースポイント(これより以前を探索)
	 * @return　抽出結果
	 */
	public ExtractedStructure extract(Object srcObj, Object dstObj, TracePoint before) {
		return extract(srcObj, dstObj, before, defaultAliasCollector);
	}
	
	/**
	 * 実際の参照元と参照先のオブジェクトを指定してデルタを抽出する(オンライン解析用)
	 * @param srcObj メモリ上にある参照元オブジェクト
	 * @param dstObj メモリ上にある参照先オブジェクト
	 * @param before 探索開始トレースポイント(これより以前を探索)
	 * @param aliasCollector デルタ抽出時に追跡したオブジェクトの全エイリアスを収集するリスナ
	 * @return　抽出結果
	 */
	public ExtractedStructure extract(Object srcObj, Object dstObj, TracePoint before, IAliasCollector aliasCollector) {
		Reference targetRef = new Reference(Integer.toString(System.identityHashCode(srcObj)), Integer.toString(System.identityHashCode(dstObj)), null, null);
		return extract(targetRef, before, aliasCollector);
	}
	
	/**
	 * メソッド実行内のトレースポイントと実際の参照先オブジェクトを指定してデルタを抽出する(オンライン解析用)
	 * @param tracePoint メソッド実行内のトレースポイント
	 * @param arg メモリ上にある参照先オブジェクト(ローカル変数や引数による参照先)
	 * @return 抽出結果
	 */
	public ExtractedStructure extract(TracePoint tracePoint, Object arg) {
		return extract(tracePoint, arg, defaultAliasCollector);
	}
	
	/**
	 * メソッド実行内のトレースポイントと実際の参照先オブジェクトを指定してデルタを抽出する(オンライン解析用)
	 * @param tracePoint メソッド実行内のトレースポイント
	 * @param arg メモリ上にある参照先オブジェクト(ローカル変数や引数による参照先)
	 * @return 抽出結果
	 */
	public ExtractedStructure extract(TracePoint tracePoint, Object arg, IAliasCollector aliasCollector) {
		ObjectReference argObj = new ObjectReference(Integer.toString(System.identityHashCode(arg)));
		return extract(tracePoint, argObj, aliasCollector);
	}
	
	/**
	 * 指定した実際のスレッド上で現在実行中のメソッド実行を取得する(オンライン解析用)
	 * @param thread 現在実行中の対象スレッド
	 * @return thread 上で現在実行中のメソッド実行
	 */
	public MethodExecution getCurrentMethodExecution(Thread thread) {
		return trace.getCurrentMethodExecution(thread);
	}
 
	/**
	 * methodSignature に前方一致するメソッド名を持つメソッドの最後の実行
	 * @param methodSignature メソッド名(前方一致で検索する)
	 * @return 該当する最後のメソッド実行
	 */
	public MethodExecution getLastMethodExecution(String methodSignature) {
		return trace.getLastMethodExecution(methodSignature);
	}
 
	/**
	 * methodSignature に前方一致するメソッド名を持つメソッドの before 以前の最後の実行
	 * @param methodSignature メソッド名(前方一致で検索する)
	 * @param before　探索開始トレースポイント(これより以前を探索)
	 * @return　該当する最後のメソッド実行
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
//		caller = new MethodExecution(caller);		// 解析用パラメータを初期化したものを使用する
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
//	 * メソッドの引数としてオブジェクトを参照した場合のデルタを抽出する
//	 * @param caller 参照元のメソッド
//	 * @param argObj 引数として参照したオブジェクト
//	 * @return　抽出結果
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
//		caller = new MethodExecution(caller);		// 解析用パラメータを初期化したものを使用する
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
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
			// aa.getReference()がないので仮にそれっぽいReferenceを作って渡す
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
		ArrayList<Alias> aliasList = aliasLists.get(aliasLists.size() - 1); // このgetObjectFlowメソッド実行内で見つかったエイリアスを入れていくリスト
		do {
			Statement statement = tp.getStatement();
			if (statement instanceof FieldAccess) {
				// フィールド参照の場合
				FieldAccess fa = (FieldAccess)statement;
				if (fa.getValueObjId().equals(objId)) {
					// 当該地点でのエイリアスをリストに追加した後に, フィールド最終更新に飛ぶパターンとそのまま遡るパターンとで分岐
					aliasList.add(new Alias(objId, tp.duplicate(), Alias.OCCURRENCE_EXP_FIELD));
					aliasList = new ArrayList<>(aliasList); // リスト自体をディープコピーしておく(フィールド最終更新に飛ぶ再帰処理終了後に, そのまま遡るパターンで用いる)
					TracePoint fieldUpdateTp = getRecentlyFieldUpdate(tp);
					aliasLists = getObjectFlow(aliasLists, objId, fieldUpdateTp, 0);
					aliasLists.add(aliasList); // 再帰処理に入る前にディープコピーしていたリストを最後尾に追加 (以降の遡りによって見つけたエイリアスはこのリストに入れられる)
				}
			} else if (statement instanceof ArrayAccess) {
				// 配列要素参照の場合
				ArrayAccess aa = (ArrayAccess)statement;
				if (aa.getValueObjectId().equals(objId)) {
					aliasList.add(new Alias(objId, tp.duplicate(), Alias.OCCURRENCE_EXP_ARRAY));
					aliasList = new ArrayList<>(aliasList);
					TracePoint arrayUpdateTp = getRecentlyArrayUpdate(tp);
					aliasLists = getObjectFlow(aliasLists, objId, arrayUpdateTp, 0);
					aliasLists.add(aliasList);
				}
			} else if (statement instanceof FieldUpdate) {
				// フィールド更新の場合
				FieldUpdate fu = (FieldUpdate)statement;
				if (fu.getValueObjId().equals(objId)) {
					aliasList.add(new Alias(objId, tp.duplicate(), Alias.OCCURRENCE_EXP_FIELD));
				}
			} else if (statement instanceof ArrayUpdate) {
				// 配列要素更新の場合
				ArrayUpdate au = (ArrayUpdate)statement;
				if (au.getValueObjectId().equals(objId)) {
					aliasList.add(new Alias(objId, tp.duplicate(), Alias.OCCURRENCE_EXP_ARRAY));
				}
			} else if (statement instanceof ArrayCreate) {
				// 配列生成の場合
				ArrayCreate ac = (ArrayCreate)statement;
				if (ac.getArrayObjectId().equals(objId)) {
					aliasList.add(new Alias(objId, tp.duplicate(), Alias.OCCURRENCE_EXP_RETURN)); // 配列生成は new 型名[] の戻り値
					return aliasLists; // 配列生成箇所はエイリアスの起源なのでそれ以前にはもうないはず
				}
			} else if (statement instanceof MethodInvocation) {
				// メソッド呼び出しの場合
				MethodExecution calledMethodExecution = ((MethodInvocation)statement).getCalledMethodExecution();
				ObjectReference returnValue = calledMethodExecution.getReturnValue();
				if (returnValue.getId().equals(objId)) {
					// 戻り値にエイリアスのオブジェクトIDが一致した場合
					ArrayList<Alias> aliasListBeforeMethodBackEntry = new ArrayList<>(aliasList); // 呼び出し先のメソッド実行に潜る前のエイリアスリストをコピーしておく
					aliasList.add(new Alias(objId, tp.duplicate(), Alias.OCCURRENCE_EXP_RETURN));
					if (calledMethodExecution.isConstructor()) {
						return aliasLists; // コンストラクタ呼び出し箇所はエイリアスの起源なのでそれ以前にはもうないはず
					}
					TracePoint exitTp = calledMethodExecution.getExitPoint(); // 呼び出しメソッド実行の最終ステートメントを指すtpを取得
					aliasLists = getObjectFlow(aliasLists, objId, exitTp, side + 1); // 呼び出し先のメソッド実行に潜る
					aliasList = aliasLists.get(aliasLists.size() - 1);
					if (aliasList.get(aliasList.size() - 1).isOrigin()) {
						// 呼び出し先のメソッド実行に潜った先でそのオブジェクトの起源(コンストラクタor配列生成)に到達していた場合, 呼び出し先のメソッド実行に潜る前のリストを用いて新規に追跡を続行する
						aliasLists.add(aliasListBeforeMethodBackEntry);
						aliasList = aliasListBeforeMethodBackEntry;
					}
				}
			}
		} while (tp.stepBackOver()); // 呼び出し元に戻るかこれ以上辿れなくなるまでループ
		if (!tp.isValid()) {
			return aliasLists; // これ以上メソッド実行を遡れない場合(mainメソッドのさらに前など)はその時点で終了
		}
		// --- この時点で tracePointは 呼び出し元を指している (直前まで遡っていたメソッド実行についてのメソッド呼び出しを指している) ---
		MethodExecution calledMethodExecution = ((MethodInvocation)tp.getStatement()).getCalledMethodExecution();
		ArrayList<ObjectReference> args = calledMethodExecution.getArguments();
		boolean isExistingInArgs = false;
		for (int i = 0; i < args.size(); i++) {
			if (args.get(i).getId().equals(objId)) {
				// メソッド呼び出しの実引数にエイリアスのオブジェクトIDが一致した場合
				aliasList.add(new Alias(objId, tp.duplicate(), (i + Alias.OCCURRENCE_EXP_FIRST_ARG)));
				isExistingInArgs = true;
				if (side == 0) {
					// 探索開始メソッド実行またはフィールドや配列要素の最終更新探索で飛んだ先のメソッド実行から, スタックトレースでたどれる全メソッド実行の場合
					TracePoint previousTp = tp.duplicate();
					previousTp.stepBackOver();
					aliasLists = getObjectFlow(aliasLists, objId, previousTp, 0); // 呼び出し元のメソッド実行に戻る
				}
			}
		}
		if (!isExistingInArgs) {
			aliasLists.remove(aliasLists.size() - 1); // 引数にエイリアスがなかった場合はその回の追跡エイリアスリストを削除する
		}
		return aliasLists;
	}
}

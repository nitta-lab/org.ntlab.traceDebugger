package org.ntlab.traceDebugger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.ntlab.traceAnalysisPlatform.tracer.trace.MethodInvocation;
import org.ntlab.traceAnalysisPlatform.tracer.trace.Statement;
import org.ntlab.traceAnalysisPlatform.tracer.trace.TracePoint;

public class TraceBreakPoints {
	private Map<String, Map<Integer, TraceBreakPoint>> traceBreakPoints = new HashMap<>();
	private List<TracePoint> histories = new LinkedList<>();
	private ListIterator<TracePoint> historyIt = histories.listIterator();
	private TracePoint curHistPoint;
	private int curIdx = -1;

	public List<TraceBreakPoint> getAllTraceBreakPoints() {
		List<TraceBreakPoint> list = new ArrayList<>();
		for (Map<Integer, TraceBreakPoint> innerMap : traceBreakPoints.values()) {
			list.addAll(innerMap.values());
		}
		Collections.sort(list, new Comparator<TraceBreakPoint>() {
			@Override
			public int compare(TraceBreakPoint arg0, TraceBreakPoint arg1) {
				if (arg0.getMethodSignature().equals(arg1.getMethodSignature())) {
					return (arg0.getLineNo() < arg1.getLineNo()) ? -1 : 1;	
				}
				return arg0.getMethodSignature().compareTo(arg1.getMethodSignature());
			}
		});
		return list;
	}

	public boolean addTraceBreakPoint(String methodSignature, int lineNo, long currentTime) {
		Map<Integer, TraceBreakPoint> innerMap = traceBreakPoints.get(methodSignature);
		if (innerMap == null) {
			innerMap = new HashMap<>();
			traceBreakPoints.put(methodSignature, innerMap);
		}
		TraceBreakPoint tbp = new TraceBreakPoint(methodSignature, lineNo, currentTime);
		if (!tbp.isAvailable()) return false;
		innerMap.put(lineNo, tbp);
		addHistories(tbp);
		return true;
	}
	
	public boolean addTraceBreakPoint(String methodSignature, int lineNo) {
		return addTraceBreakPoint(methodSignature, lineNo, 0L);
	}
	
	public void removeTraceBreakPoint(String methodSignature, int lineNo) {
		Map<Integer, TraceBreakPoint> innerMap = traceBreakPoints.get(methodSignature);
		if (innerMap == null) return;
		TraceBreakPoint tbp = innerMap.remove(lineNo);
		if (tbp != null) removeHistories(tbp);
		if (innerMap.isEmpty()) traceBreakPoints.remove(methodSignature);
	}
	
	public void removeTraceBreakPoint(TraceBreakPoint traceBreakPoint) {
		String methodSignature = traceBreakPoint.getMethodSignature();
		int lineNo = traceBreakPoint.getLineNo();
		removeTraceBreakPoint(methodSignature, lineNo);
	}

	private void addHistories(TraceBreakPoint tbp) {		
		ListIterator<TracePoint> it = histories.listIterator();
		Iterator<TracePoint> tbpIt = tbp.getTracePoints().iterator();
		if (!(tbpIt.hasNext())) return;
		TracePoint addedTp = tbpIt.next();
		int idx = 0;
		while (it.hasNext()) {
			TracePoint tp = it.next();
			if (getTime(addedTp) < getTime(tp)) {
				it.previous();
				it.add(addedTp);
				if (idx <= curIdx) {
					curIdx++;
				}
				addedTp = null;
				if (!(tbpIt.hasNext())) break;
				addedTp = tbpIt.next();
			}
			idx++;
		}
		if (addedTp != null) {
			it.add(addedTp);
			while (tbpIt.hasNext()) {
				it.add(tbpIt.next());
			}
		}
		historyIt = histories.listIterator(curIdx + 1); // 次に取得するインデックスに合わせたイテレータを再取得
		confirm();
	}
	
	private void removeHistories(TraceBreakPoint tbp) {
		ListIterator<TracePoint> it = histories.listIterator();
		Iterator<TracePoint> tbpIt = tbp.getTracePoints().iterator();
		if (!(tbpIt.hasNext())) return;
		TracePoint removedTp = tbpIt.next();
		int idx = 0;
		while (it.hasNext()) {
			TracePoint tp = it.next();
			if (tp.equals(removedTp)) {
				it.remove();
				if (tp.equals(curHistPoint)) {
					curHistPoint = null; // 現在位置に対応するブレークポイントが削除された場合は現在位置をなしにする
					curIdx = -1;
				} else if (-1 < curIdx && idx <= curIdx) {
					curIdx--;
				}
				if (!(tbpIt.hasNext())) break;
				removedTp = tbpIt.next();
			} else {
				idx++;
			}
		}
		if (curHistPoint == null) {
			historyIt = null;
		} else {
			historyIt = histories.listIterator(curIdx + 1); // 次に取得するインデックスに合わせたイテレータを再取得			
		}
		confirm();
	}

	public TracePoint getFirstTracePoint() {
		return getNextTracePoint(0L);
	}

	public TracePoint getNextTracePoint(long time) {
		long curHistTime;
		if (curHistPoint == null) {
			curHistTime = 0L;
			curIdx = -1;
			historyIt = histories.listIterator();
		} else {
			curHistTime = getTime(curHistPoint);
		}
		if (curHistTime <= time) {
			while (historyIt.hasNext()) {
				TracePoint tp = historyIt.next();
				if (tp.equals(curHistPoint)) continue;
				curHistPoint = tp;
				curIdx++;
				if (!checkAvailable(curHistPoint)) continue;
				curHistTime = getTime(curHistPoint);
				if (curHistTime > time) {
					confirm();
					return curHistPoint;
				}
			}
		} else {
			while (historyIt.hasPrevious()) {
				TracePoint tp = historyIt.previous();
				if (tp.equals(curHistPoint)) continue;
				curIdx--;
				if (!checkAvailable(tp)) continue;
				curHistTime = getTime(tp); 
				if (curHistTime <= time) {
					confirm();
					return curHistPoint;
				}
				curHistPoint = tp;
			}
			confirm();
			return curHistPoint;
		}
		confirm();
		return null;
	}
	
	public TracePoint getPreviousTracePoint(long time) {
		long curHistTime;
		if (curHistPoint == null) {
			curHistTime = Long.MAX_VALUE;
			curIdx = histories.size();
			historyIt = histories.listIterator(histories.size());
		} else {
			curHistTime = getTime(curHistPoint);
		}
		if (curHistTime >= time) {
			while (historyIt.hasPrevious()) {
				TracePoint tp = historyIt.previous();
				if (tp.equals(curHistPoint)) continue;
				curHistPoint = tp;
				curIdx--;
				if (!checkAvailable(curHistPoint)) continue;
				curHistTime = getTime(curHistPoint);
				if (curHistTime < time) {
					confirm();
					return curHistPoint;
				}
			}
		} else {
			while (historyIt.hasNext()) {
				TracePoint tp = historyIt.next();
				if (tp.equals(curHistPoint)) continue;
				curIdx++;
				if (!checkAvailable(tp)) continue;
				curHistTime = getTime(tp); 
				if (curHistTime >= time) {
					confirm();
					return curHistPoint;
				}
				curHistPoint = tp;
			}
			confirm();
			return curHistPoint;
		}
		confirm();
		return null;
	}

	private TraceBreakPoint getTraceBreakPoint(TracePoint tp) {
		String signature = tp.getMethodExecution().getSignature();
		int lineNo = tp.getStatement().getLineNo();
		Map<Integer, TraceBreakPoint> innerMap = traceBreakPoints.get(signature);
		return (innerMap != null) ? innerMap.get(lineNo) : null;
	}
	
	private boolean checkAvailable(TracePoint tp) {
		TraceBreakPoint tbp = getTraceBreakPoint(tp);
		return (tbp != null) ? tbp.isAvailable() : false;
	}

	public void reset() {
		curIdx = -1;
		historyIt = histories.listIterator();
		curHistPoint = null;
	}
	
	public void clear() {
		traceBreakPoints.clear();
		histories.clear();
		curIdx = -1;
		historyIt = null;
		curHistPoint = null;
	}
	
	private long getTime(TracePoint tp) {
		Statement statement = tp.getStatement();
		if (statement instanceof MethodInvocation) {
			return ((MethodInvocation)statement).getCalledMethodExecution().getEntryTime();
		}
		return statement.getTimeStamp();
	}
	
	private void confirm() {
		System.out.println();
		if (curHistPoint == null) {
			System.out.println("cur: " + "Not Exist");
		} else {
			System.out.println("cur: " + getTime(curHistPoint));	
		}
		int idx = 0;
		for (TracePoint tp : histories) {
			String signature = tp.getMethodExecution().getSignature();
			int lineNo = tp.getStatement().getLineNo();
			long time = getTime(tp);
			String idxStr = (idx == curIdx) ? "←←←←←" : "";
			System.out.println(time + "	" + signature + " line: " + lineNo + " " + idxStr);
			idx++;
		}
		System.out.println();
	}

//	public TracePoint getNextTracePoint(long currentTime) {
//		TraceBreakPoint resultTbp = null;
//		long resultTpTime = 0L;
//		for (Map<Integer, TraceBreakPoint> innerMap : traceBreakPoints.values()) {
//			for (TraceBreakPoint tbp: innerMap.values()) {
//				if (!tbp.isAvailable()) continue;
//				TracePoint tp = tbp.peekTracePoint();
//				if (tp == null) continue;
//				long tpTime = tp.getStatement().getTimeStamp();
//				if (tpTime <= currentTime) continue;
//				if (resultTbp == null) {
//					resultTbp = tbp;
//					resultTpTime = tp.getStatement().getTimeStamp();
//				} else if (tpTime < resultTpTime) {
//					resultTbp = tbp;
//					resultTpTime = tp.getStatement().getTimeStamp();
//				}
//			}
//		}
//		return (resultTbp != null) ? resultTbp.dequeueTracePoint(true) : null;
//	}
//
//	public TracePoint getPreviousTracePoint(long currentTime) {
//		TraceBreakPoint resultTbp = null;
//		long resultTpTime = 0L;
//		for (Map<Integer, TraceBreakPoint> innerMap : traceBreakPoints.values()) {
//			for (TraceBreakPoint tbp: innerMap.values()) {
//				if (!tbp.isAvailable()) continue;
//				TracePoint tp = tbp.previousTracePoint();
//				if (tp == null) continue;
//				long tpTime = tp.getStatement().getTimeStamp();
//				if (tpTime >= currentTime) continue;
//				if (resultTbp == null) {
//					resultTbp = tbp;
//					resultTpTime = tp.getStatement().getTimeStamp();
//				} else if (tpTime > resultTpTime) {
//					resultTbp = tbp;
//					resultTpTime = tp.getStatement().getTimeStamp();
//				}
//			}
//		}
//		return (resultTbp != null) ? resultTbp.dequeueTracePoint(false) : null;		
//	}
//	
//	public void forwardAll(long currentTime) {
//		for (Map<Integer, TraceBreakPoint> innerMap : traceBreakPoints.values()) {
//			for (TraceBreakPoint tbp : innerMap.values()) {
//				tbp.forwardIndex(currentTime);
//			}
//		}		
//	}
//	
//	public void reverseAll(long currentTime) {
//		for (Map<Integer, TraceBreakPoint> innerMap : traceBreakPoints.values()) {
//			for (TraceBreakPoint tbp : innerMap.values()) {
//				tbp.reverseIndex(currentTime);
//			}
//		}		
//	}
//	
//	public void resetAll() {
//		for (Map<Integer, TraceBreakPoint> innerMap : traceBreakPoints.values()) {
//			for (TraceBreakPoint tbp : innerMap.values()) {
//				tbp.reset();
//			}
//		}
//	}	
}

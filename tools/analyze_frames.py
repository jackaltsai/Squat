#!/usr/bin/env python3
"""分析研究模式輸出的逐幀 CSV（squat_frames_*.csv）。

回答三個調參時最常問、但光看每下一列的紀錄無從得知的問題：
  1. 哪個關鍵點、在什麼信心值上把整幀擋掉（決定 requiredPoints 與門檻上限）
  2. 動作進度的實際軌跡 —— 峰值多高、手放下時落到哪（決定進場/返回門檻）
  3. 掉幀集中在哪個狀態（決定是不是該換追蹤的關鍵點）

用法：
    python3 tools/analyze_frames.py squat_frames_20261001_212523.csv
    python3 tools/analyze_frames.py *.csv
"""
import csv
import glob
import os
import statistics as st
import sys

CONFIDENCE_THRESHOLD = 0.6

LOWER = ["LEFT_HIP", "RIGHT_HIP", "LEFT_KNEE", "RIGHT_KNEE", "LEFT_ANKLE", "RIGHT_ANKLE"]
UPPER = ["LEFT_SHOULDER", "RIGHT_SHOULDER", "LEFT_ELBOW", "RIGHT_ELBOW",
         "LEFT_WRIST", "RIGHT_WRIST"]


def fnum(row, key):
    v = row.get(key, "")
    try:
        return float(v)
    except (TypeError, ValueError):
        return None


def present(rows, point):
    return f"{point}_raw_confidence" in (rows[0].keys() if rows else [])


def report(path):
    with open(path, newline="", encoding="utf-8") as fh:
        rows = list(csv.DictReader(fh))
    if not rows:
        print(f"{path}: 空檔案")
        return

    print("=" * 72)
    print(os.path.basename(path))
    print("=" * 72)

    ts = [int(r["frameTimestampMs"]) for r in rows]
    dur = (ts[-1] - ts[0]) / 1000 if len(ts) > 1 else 0
    print(f"幀數 {len(rows)}　時長 {dur:.1f}s　平均 {len(rows)/dur:.1f} fps" if dur else f"幀數 {len(rows)}")

    has_quality = "qualityOk" in rows[0]
    if not has_quality:
        print("\n⚠️  這份 CSV 沒有 qualityOk 欄位（舊格式）。")
        print("   被品質檢查擋下的幀沒有被記錄，掉幀率只能從時間戳空隙推估，")
        print("   也看不出是哪個關鍵點造成的。請用新版 App 重錄。")

    missing_upper = [p for p in UPPER if not present(rows, p)]
    if missing_upper:
        print(f"\n⚠️  這份 CSV 缺少上肢關鍵點：{', '.join(missing_upper)}")
        print("   舊版 FrameLogger 寫死只記下肢六點，手臂動作無法分析。請用新版 App 重錄。")

    # ---- 狀態流程與每下耗時 ----
    print("\n【狀態流程】")
    seq = []
    for r in rows:
        if not seq or seq[-1][0] != r["state"]:
            seq.append((r["state"], int(r["frameTimestampMs"])))
    t0 = seq[0][1]
    for name, t in seq:
        print(f"  {(t - t0)/1000:7.2f}s  {name}")

    cycles = []
    start = None
    for name, t in seq:
        if name == "DOWN" and start is None:
            start = t
        elif name == "STAND" and start is not None:
            cycles.append((t - start) / 1000)
            start = None
    if cycles:
        print(f"\n  完成 {len(cycles)} 下　每下耗時 平均 {st.mean(cycles):.2f}s　"
              f"範圍 {min(cycles):.2f}~{max(cycles):.2f}s")
        slow = [c for c in cycles if c > 3 * st.median(cycles)]
        if slow:
            print(f"  ⚠️  有 {len(slow)} 下明顯卡住（{', '.join(f'{c:.1f}s' for c in slow)}）"
                  f"，中位數只有 {st.median(cycles):.2f}s")

    # ---- 掉幀分析 ----
    print("\n【品質檢查】")
    if has_quality:
        rejected = [r for r in rows if r["qualityOk"].lower() == "false"]
        print(f"  被擋下 {len(rejected)} / {len(rows)} 幀（{len(rejected)/len(rows):.0%}）")
        if rejected:
            print("\n  被擋下的幀裡，各關鍵點低於門檻的次數：")
            counts = {}
            for p in LOWER + UPPER:
                if not present(rows, p):
                    continue
                n = sum(1 for r in rejected
                        if (fnum(r, f"{p}_raw_confidence") or 0) < CONFIDENCE_THRESHOLD)
                if n:
                    counts[p] = n
            for p, n in sorted(counts.items(), key=lambda kv: -kv[1]):
                print(f"    {p:16} {n:5} 次（佔被擋下的 {n/len(rejected):.0%}）")
            if counts:
                worst = max(counts.items(), key=lambda kv: kv[1])[0]
                print(f"\n  → 主要瓶頸是 {worst}。若該點並非動作判定所需，"
                      f"應從 requiredPoints 移除。")
            print("\n  被擋下的幀分布於各狀態：")
            by_state = {}
            for r in rejected:
                by_state[r["state"]] = by_state.get(r["state"], 0) + 1
            total_by_state = {}
            for r in rows:
                total_by_state[r["state"]] = total_by_state.get(r["state"], 0) + 1
            for s, n in sorted(by_state.items(), key=lambda kv: -kv[1]):
                print(f"    {s:18} {n:5} / {total_by_state[s]:5}（{n/total_by_state[s]:.0%}）")
    else:
        med = st.median([ts[i + 1] - ts[i] for i in range(len(ts) - 1)])
        est = sum(round((ts[i + 1] - ts[i]) / med) - 1
                  for i in range(len(ts) - 1) if (ts[i + 1] - ts[i]) > 1.5 * med)
        print(f"  推估被擋下約 {est} 幀（由時間戳空隙推算，非實測）")

    # ---- 上肢：進度軌跡 ----
    if not missing_upper:
        print("\n【雙臂高舉的進度軌跡】")
        calib = [r for r in rows if r["state"] == "STAND_HOLD"
                 and (not has_quality or r["qualityOk"].lower() == "true")]
        if not calib:
            print("  找不到 STAND_HOLD 階段的有效幀，無法推算判準。")
            return
        drops, widths = [], []
        for r in calib:
            ly, ry = fnum(r, "LEFT_SHOULDER_raw_y"), fnum(r, "RIGHT_SHOULDER_raw_y")
            lx, rx = fnum(r, "LEFT_SHOULDER_raw_x"), fnum(r, "RIGHT_SHOULDER_raw_x")
            wl, wr = fnum(r, "LEFT_WRIST_raw_y"), fnum(r, "RIGHT_WRIST_raw_y")
            if None in (ly, ry, lx, rx, wl, wr):
                continue
            drops.append((wl + wr) / 2 - (ly + ry) / 2)
            widths.append(abs(lx - rx))
        if not drops or st.mean(widths) <= 0:
            print("  站姿校正資料不足。")
            return
        rest_drop, width = st.mean(drops), st.mean(widths)
        target = rest_drop / width
        print(f"  站姿腕肩落差 {rest_drop:.1f}px　肩寬 {width:.1f}px　"
              f"→ 判準 target = {target:.3f} 個肩寬")

        def progress(r):
            ly, ry = fnum(r, "LEFT_SHOULDER_raw_y"), fnum(r, "RIGHT_SHOULDER_raw_y")
            wl, wr = fnum(r, "LEFT_WRIST_raw_y"), fnum(r, "RIGHT_WRIST_raw_y")
            if None in (ly, ry, wl, wr):
                return None
            return (rest_drop - ((wl + wr) / 2 - (ly + ry) / 2)) / width

        for state in ["STAND", "DOWN", "UP"]:
            vals = [p for p in (progress(r) for r in rows if r["state"] == state)
                    if p is not None]
            if vals:
                print(f"  {state:6} 進度：中位數 {st.median(vals):6.3f}　"
                      f"5% {sorted(vals)[len(vals)//20]:6.3f}　"
                      f"95% {sorted(vals)[-max(1, len(vals)//20)]:6.3f}　"
                      f"（÷target = {st.median(vals)/target:.2f}）")
        stand = [p for p in (progress(r) for r in rows if r["state"] == "STAND")
                 if p is not None]
        if stand:
            p95 = sorted(stand)[-max(1, len(stand) // 20)]
            print(f"\n  → 手放下時 95% 的幀進度低於 {p95:.3f}"
                  f"（= {p95/target:.2f}×target）。")
            print(f"    返回門檻應略高於此值；目前設定請對照 Config.ARM_RAISE_RETURN_FRACTION。")

        # 手腕信心值 vs 高度
        print("\n  手腕信心值 vs 進度（看信心值是否在高處垮掉）：")
        buckets = {}
        for r in rows:
            p = progress(r)
            cl, cr = fnum(r, "LEFT_WRIST_raw_confidence"), fnum(r, "RIGHT_WRIST_raw_confidence")
            if p is None or cl is None or cr is None:
                continue
            b = round(p * 4) / 4
            buckets.setdefault(b, []).append(min(cl, cr))
        for b in sorted(buckets):
            v = buckets[b]
            below = sum(1 for x in v if x < CONFIDENCE_THRESHOLD)
            print(f"    進度 {b:5.2f}　幀數 {len(v):5}　手腕信心中位數 {st.median(v):.3f}　"
                  f"低於門檻 {below/len(v):.0%}")


def main():
    paths = []
    for arg in sys.argv[1:]:
        paths += sorted(glob.glob(arg)) if any(c in arg for c in "*?[") else [arg]
    if not paths:
        print(__doc__)
        sys.exit(1)
    for p in paths:
        report(p)
        print()


if __name__ == "__main__":
    main()

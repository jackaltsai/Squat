#!/usr/bin/env python3
"""分析研究模式輸出的逐幀 CSV（squat_frames_*.csv）。

回答三個調參時最常問、但光看每下一列的紀錄無從得知的問題：
  1. 哪個關鍵點、在什麼信心值上把整幀擋掉（決定 requiredPoints 與門檻上限）
  2. 動作進度的實際軌跡 —— 峰值多高、手放下時落到哪（決定進場/返回門檻）
  3. 掉幀集中在哪個狀態（決定是不是該換追蹤的關鍵點）

逐幀 CSV 是在手機上產生的（研究模式開啟時），要先用訓練結束畫面的分享鍵
傳到電腦 —— 通常會落在 ~/Downloads，不在專案目錄裡。

用法：
    python3 tools/analyze_frames.py                      # 自動在常見位置尋找
    python3 tools/analyze_frames.py ~/Downloads/squat_frames_20261001_212523.csv
    python3 tools/analyze_frames.py ~/Downloads/squat_frames_*.csv
"""
import csv
import glob
import os
import statistics as st
import sys

CONFIDENCE_THRESHOLD = 0.6

LOWER = ["LEFT_HIP", "RIGHT_HIP", "LEFT_KNEE", "RIGHT_KNEE", "LEFT_ANKLE", "RIGHT_ANKLE"]
FOOT = ["LEFT_HEEL", "RIGHT_HEEL", "LEFT_TOE", "RIGHT_TOE"]
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


# 從手機分享出來的檔案通常會落在這幾個地方，沒給參數時自動找。
SEARCH_DIRS = ["", "~/Downloads", "~/Desktop", "~/Documents"]
FRAME_GLOB = "squat_frames_*.csv"


def autodiscover():
    found = []
    for d in SEARCH_DIRS:
        found += glob.glob(os.path.join(os.path.expanduser(d), FRAME_GLOB))
    # 依修改時間排序，最新的最後印出
    return sorted(set(found), key=os.path.getmtime)


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

    missing_foot = [p for p in FOOT if not present(rows, p)]
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

    # ---- 腳部：踮腳尖可行性 ----
    if not missing_foot:
        heel_raise_report(rows, has_quality)
    else:
        print("\n【踮腳尖訊號可行性】")
        print(f"  這份 CSV 缺少腳部關鍵點：{', '.join(missing_foot)}")
        print("  請用新版 App（2026-10-02 之後）重錄，才會記錄腳跟與腳尖。")

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


def heel_raise_report(rows, has_quality):
    """踮腳尖的可行性診斷：腳跟/腳尖信心值夠不夠、位移有沒有高過雜訊。"""
    print("\n【踮腳尖訊號可行性】")
    good = [r for r in rows if not has_quality or r["qualityOk"].lower() == "true"]
    if not good:
        good = rows

    print("  腳部關鍵點的信心值：")
    usable = True
    for point in FOOT:
        vals = [v for v in (fnum(r, f"{point}_raw_confidence") for r in rows) if v is not None]
        if not vals:
            print(f"    {point:12} 沒有資料")
            usable = False
            continue
        above = sum(1 for v in vals if v >= CONFIDENCE_THRESHOLD)
        print(f"    {point:12} 中位數 {st.median(vals):.3f}　"
              f"達門檻 {above/len(vals):.0%}　最低 {min(vals):.3f}")
        if above / len(vals) < 0.8:
            usable = False
    if not usable:
        print("\n  ⚠️  腳部關鍵點的信心值不足，以腳跟/腳尖為訊號不可行。")
        print("     改用「髖或踝的上升量」會被身體晃動污染，需要另想判準。")

    # 腳跟相對腳尖的垂直落差（局部量測，不受身體晃動影響）
    def heel_above_toe(row, side):
        heel = fnum(row, f"{side}_HEEL_raw_y")
        toe = fnum(row, f"{side}_TOE_raw_y")
        if heel is None or toe is None:
            return None
        return toe - heel          # 腳跟抬起 → heel 的 y 變小 → 值變大

    scale_vals = []
    for r in good:
        hip = fnum(r, "LEFT_HIP_raw_y")
        ankle = fnum(r, "LEFT_ANKLE_raw_y")
        if hip is not None and ankle is not None and ankle > hip:
            scale_vals.append(ankle - hip)
    scale = st.median(scale_vals) if scale_vals else None
    if scale:
        print(f"\n  身體比例尺（髖-踝）= {scale:.1f}px")

    for side in ("LEFT", "RIGHT"):
        vals = [v for v in (heel_above_toe(r, side) for r in good) if v is not None]
        if len(vals) < 10:
            continue
        lo = sorted(vals)[len(vals) // 20]
        hi = sorted(vals)[-max(1, len(vals) // 20)]
        span = hi - lo
        print(f"  {side:5} 腳跟高於腳尖：5% {lo:7.1f}px　95% {hi:7.1f}px　"
              f"活動範圍 {span:6.1f}px", end="")
        if scale:
            print(f"（= {span/scale:.3f} 個腿長）")
        else:
            print()
        # 雜訊底：取變化最小的連續 30 幀當作「靜止段」
        window = 30
        if len(vals) > window:
            noise = min(
                st.pstdev(vals[i:i + window])
                for i in range(0, len(vals) - window, 5)
            )
            ratio = span / noise if noise > 0 else float("inf")
            mark = "✅" if ratio >= 10 else ("⚠️ " if ratio >= 4 else "❌")
            print(f"        最靜止 30 幀的標準差（雜訊底）= {noise:.2f}px　"
                  f"訊噪比 {ratio:.1f} {mark}")
    print("\n  訊噪比 ≥10 代表訊號清楚可用；4~10 勉強；<4 代表位移淹在雜訊裡，")
    print("  那就不該用這個量當訊號，也不該憑推算訂門檻。")


def main():
    args = sys.argv[1:]
    paths = []
    missing = []
    for arg in args:
        expanded = os.path.expanduser(arg)
        if any(c in expanded for c in "*?["):
            hits = sorted(glob.glob(expanded))
            if hits:
                paths += hits
            else:
                missing.append(arg)
        elif os.path.isfile(expanded):
            paths.append(expanded)
        else:
            missing.append(arg)

    if missing:
        for m in missing:
            print(f"找不到：{m}")

    # 沒給參數，或給的都找不到 —— 自動在常見位置找一次，不要只吐一個 traceback
    if not paths:
        found = autodiscover()
        if found:
            print(f"\n自動找到 {len(found)} 個逐幀 CSV：")
            for f in found:
                print(f"  {f}")
            print()
            paths = found
        else:
            if not missing:
                print(__doc__)
            print("在以下位置都找不到 squat_frames_*.csv：")
            for d in SEARCH_DIRS:
                print(f"  {os.path.expanduser(d) or os.getcwd()}")
            print()
            print("逐幀 CSV 是在手機上產生的，取得方式：")
            print("  1. 在選擇動作畫面最下方打開「研究模式」")
            print("  2. 完成一組訓練後按「停止」")
            print("  3. 結束畫面按分享，把 squat_frames_*.csv 傳到電腦")
            print("     （Session CSV 是每下一列的紀錄，逐幀的是 squat_frames_ 開頭那個）")
            sys.exit(1)

    for p in paths:
        try:
            report(p)
        except Exception as exc:  # 一個檔案壞掉不該讓整批中斷
            print(f"{os.path.basename(p)}: 無法分析（{type(exc).__name__}: {exc}）")
        print()


if __name__ == "__main__":
    main()

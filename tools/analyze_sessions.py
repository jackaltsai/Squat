#!/usr/bin/env python3
"""
分析「深蹲教練」匯出的每下紀錄 CSV（squat_session_*.csv）。

用法:
    python3 tools/analyze_sessions.py <csv 或資料夾> [...]

除了基本統計，這支腳本會做兩件在論文分析階段最需要的事：

1. 重算三段式分級並跟 App 寫入的 feedbackColor 比對，驗證判定邏輯（M5 一致率指標的基礎）。
2. 檢查每一場的校正品質。p = dNow / duser，只看 p 無法分辨「蹲得深」和「校正基準太淺」，
   所以這裡直接看 duser 本身跟 p 的平均，把可疑的場次標出來。
"""
import sys
import glob
import os

import pandas as pd

# 對應論文表 1；跟 app 的 TrainingMode.kt 一致
THRESHOLDS = {
    "BEGINNER": (0.90, 0.80),
    "NORMAL": (1.00, 0.85),
    "ADVANCED": (1.10, 0.95),
}

# 平均 p 落在這個範圍外，代表校正基準跟實際訓練深度落差太大，該場資料要打問號
SUSPECT_MEAN_P_LOW, SUSPECT_MEAN_P_HIGH = 0.85, 1.15

# 深蹲家族的 p 分母是「兩下基準動作取得的 Duser」；其餘動作的分母是固定解剖判準，
# 診斷的方向完全不同，不能共用同一句話。
SQUAT_FAMILY = {"SQUAT", "CHAIR_SQUAT"}

# 上肢動作的 duser 可反推「手臂長 ÷ 肩寬」，據此檢查站姿校正品質。
# 實測三場雙臂高舉量到 1.348 / 1.394 / 0.962，解剖學上成人約 1.4；
# 明顯偏離代表站姿校正時手沒有自然下垂。
ARM_TO_SHOULDER_EXPECTED = 1.37
ARM_TO_SHOULDER_TOLERANCE = 0.25
CHEST_TARGET_FRACTION_OF_FULL = 0.6

# 一場裡「正常」會出現幾個 Duser。原地高抬腿左右腳各一台狀態機、各自的判準，
# 所以兩個值是正常的；其餘動作一場只校正一次，只該有一個。
EXPECTED_DUSER_COUNT = {"HIGH_KNEES": 2}

# 哪些動作該量膝內夾。這張表**鏡像** app 的 `judgesKneeValgus()`，判據是
# 「同時宣告需要兩膝與兩踝」：
#
#   SQUAT / CHAIR_SQUAT / HEEL_RAISE → LOWER_BODY           → 量
#   HIGH_KNEES                        → HIP_AND_KNEE        → 不量（一腳離地）
#   ARM_RAISE / CHEST_EXPANSION       → SHOULDER_AND_WRIST  → 不量（不看下肢）
#
# Kotlin 那邊有 `RepSignalTest` 逐一鎖住六個動作，這張表只負責檢查**裝置實際
# 輸出**有沒有照辦。兩邊不一致就該在這裡被報出來 —— 這正是 2026-10-02
# 才發現的那種污染（高抬腿記了 −0.26~−0.55 的垃圾比值，看起來像「量到了」）。
JUDGES_VALGUS = {
    "SQUAT": True,
    "CHAIR_SQUAT": True,
    "HEEL_RAISE": True,
    "HIGH_KNEES": False,
    "ARM_RAISE": False,
    "CHEST_EXPANSION": False,
}

# 膝內夾閘門上線的時間（2026-10-02 20:39 實機確認）。在這之前的紀錄即使
# 帶著不該有的 ratio 也**不是 bug**，是舊資料 —— 不該讓迴歸檢查對它們亮紅燈，
# 否則每次跑都一片紅，真正的迴歸就被淹掉了。
VALGUS_GATE_SINCE = "2026-10-02 20:39"


def check_valgus_gate(d, exercise):
    """回傳 (通過?, 說明)。None 代表這份 CSV 太舊、無從檢查。"""
    if "kneeValgusRatio" not in d.columns:
        return None, "舊格式沒有 kneeValgusRatio 欄位"
    # `duser` 與 `kneeValgusRatio` 是同一批加進 Room 的原始值欄位，更早的紀錄整欄是
    # null。那種場次「沒有 ratio」是**欄位還不存在**，不是閘門擋過頭 ——
    # 用資料自己判斷，而不是再寫一個硬編日期（匯出的全部歷史裡有 15 場是這種）。
    if "duser" in d.columns and d["duser"].isna().all() \
            and d["kneeValgusRatio"].isna().all():
        return None, "舊格式（duser 與 ratio 原始值欄位當時還不存在）"
    expected = JUDGES_VALGUS.get(exercise)
    if expected is None:
        return None, f"未知動作 {exercise}"
    has_ratio = bool(d["kneeValgusRatio"].notna().any())
    if has_ratio == expected:
        return True, "量" if expected else "不量（寫 null）"
    if not expected and has_ratio:
        latest = str(d["localTime"].max())
        if latest < VALGUS_GATE_SINCE:
            return None, f"這一場在閘門上線（{VALGUS_GATE_SINCE}）之前，舊資料"
        return False, (f"{exercise} 不該量膝內夾，但這一場有 ratio —— "
                       f"judgesKneeValgus() 的閘門沒生效，M5 的驗證集會被污染")
    return False, (f"{exercise} 應該要量膝內夾，但這一場的 ratio 全是空的 —— "
                   f"閘門擋過頭了，深蹲家族少了唯一的姿勢判定")


def arm_to_shoulder_ratio(exercise, duser):
    """從 duser 反推「手臂長 ÷ 肩寬」。無法反推時回傳 None。"""
    if exercise == "ARM_RAISE":
        # duser = 手臂長 / 肩寬
        return duser
    if exercise == "CHEST_EXPANSION" and duser > 1.0:
        # duser = (肩寬 + 2×手臂長) × 0.6 / 肩寬　→　手臂長/肩寬 = (duser/0.6 − 1) / 2
        return (duser / CHEST_TARGET_FRACTION_OF_FULL - 1) / 2
    return None

# squat_all_records_*.csv 把所有場次倒在同一個檔案裡，用相鄰兩下的時間間隔切開。
# 一場訓練裡每下大約隔 3~10 秒，換場至少要重做站姿校正 3 秒 + 兩下校正深蹲 + 倒數，
# 120 秒是保守的分界。
SESSION_GAP_SECONDS = 120


def expected_color(p, mode):
    green, yellow = THRESHOLDS[mode]
    return "GREEN" if p >= green else ("YELLOW" if p >= yellow else "RED")


def split_sessions(df):
    """把單一檔案內的紀錄依時間間隔切成場次，回傳 [(標籤, 子 DataFrame), ...]。"""
    df = df.sort_values("timestampMs").reset_index(drop=True)
    gap = df["timestampMs"].diff().fillna(0) / 1000
    # 時間隔太久、訓練模式換了、或動作換了，都視為新的一場
    new_session = (gap > SESSION_GAP_SECONDS) | (df["mode"] != df["mode"].shift())
    if "exerciseType" in df.columns:
        # ⚠️ 必須先 fillna。pandas 裡 NaN != NaN 是 True，而把「有 exerciseType 的新檔」
        # 和「沒有這個欄位的舊檔」一起讀進來時，舊檔那幾列整欄都是 NaN ——
        # 直接比較會讓**每一列都被當成新的一場**（實測 260 下被切成 260 場）。
        ex = df["exerciseType"].fillna("__UNKNOWN__")
        new_session |= ex != ex.shift()
    df = df.assign(_session=new_session.cumsum())
    out = []
    for i, (_, d) in enumerate(df.groupby("_session"), start=1):
        # 動作名稱放進標籤：坐站與深蹲的 p 值分布完全不同（椅面固定了深度，
        # 2026-10-01 實測坐站 CV 4.5% vs 深蹲 20%），混在一起看會得到錯誤結論
        raw_ex = d["exerciseType"].iloc[0] if "exerciseType" in d.columns else None
        ex = f" {raw_ex}" if isinstance(raw_ex, str) else ""
        label = f"{d['sourceFile'].iloc[0]} #{i}{ex} ({d['localTime'].iloc[0]})"
        out.append((label, d))
    return out


# 從手機分享出來的 CSV 通常會落在這幾個地方，沒給參數時自動找。
SEARCH_DIRS = ["", "~/Downloads", "~/Desktop", "~/Documents"]
SESSION_GLOB = "squat_*records*.csv"
SESSION_GLOB_ALT = "squat_session_*.csv"


def autodiscover():
    found = []
    for d in SEARCH_DIRS:
        base = os.path.expanduser(d)
        for pattern in (SESSION_GLOB, SESSION_GLOB_ALT):
            found += glob.glob(os.path.join(base, pattern))
    return sorted(set(found), key=os.path.getmtime)


def collect(paths):
    files = []
    missing = []
    for path in paths:
        expanded = os.path.expanduser(path)
        if os.path.isdir(expanded):
            files += sorted(glob.glob(os.path.join(expanded, "*.csv")))
        elif any(c in expanded for c in "*?["):
            hits = sorted(glob.glob(expanded))
            files += hits
            if not hits:
                missing.append(path)
        elif os.path.isfile(expanded):
            files.append(expanded)
        else:
            missing.append(path)
    for m in missing:
        print(f"找不到：{m}")
    if not files:
        files = autodiscover()
        if files:
            print(f"自動找到 {len(files)} 個訓練紀錄 CSV：")
            for f in files:
                print(f"  {f}")
            print()
        else:
            print("在以下位置都找不到訓練紀錄 CSV：")
            for d in SEARCH_DIRS:
                print(f"  {os.path.expanduser(d) or os.getcwd()}")
            print()
            print("取得方式：訓練結束畫面按分享，把 squat_session_*.csv 或")
            print("squat_all_records_*.csv 傳到電腦（逐幀的 squat_frames_*.csv")
            print("請改用 tools/analyze_frames.py）。")
            sys.exit(1)
    frames = []
    for f in files:
        df = pd.read_csv(f)
        df["sourceFile"] = os.path.basename(f)
        frames.append(df)
    if not frames:
        sys.exit("找不到任何 CSV")
    return pd.concat(frames, ignore_index=True)


def main():
    df = collect(sys.argv[1:])
    has_raw = "duser" in df.columns

    sessions = []
    for _, per_file in df.groupby("sourceFile", sort=True):
        sessions += split_sessions(per_file)

    # 迴歸檢查的失敗清單。逐場印一行容易被滾動吞掉，所以最後再集中報一次。
    regressions = []

    for name, d in sessions:
        mode = d["mode"].iloc[0]
        p = d["depthRatio"]
        green, yellow = THRESHOLDS[mode]
        print(f"\n=== {name} | {mode} (綠≥{green}, 黃≥{yellow}) ===")
        print(f"  次數 {len(d)}   p: 平均={p.mean():.3f} 標準差={p.std():.3f} "
              f"範圍={p.min():.3f}~{p.max():.3f}  CV={p.std()/p.mean()*100:.1f}%")
        print("  分級:", d["feedbackColor"].value_counts().to_dict())

        mismatch = (p.apply(lambda v: expected_color(v, mode)) != d["feedbackColor"]).sum()
        print(f"  分級邏輯: {'✅ 全部相符' if mismatch == 0 else f'❌ {mismatch} 筆不符'}")
        if mismatch:
            regressions.append((name, "—", f"分級邏輯 {mismatch} 筆與論文表 1 不符"))

        raw_ex = d["exerciseType"].iloc[0] if "exerciseType" in d.columns else None
        exercise = raw_ex if isinstance(raw_ex, str) else "SQUAT"

        valgus = d["kneeValgus"].sum()
        print(f"  膝內夾: {int(valgus)}/{len(d)}", end="")
        if "kneeValgusRatio" in d.columns and d["kneeValgusRatio"].notna().any():
            r = d["kneeValgusRatio"].dropna()
            print(f"   ratio 範圍={r.min():.3f}~{r.max():.3f} 平均={r.mean():.3f}")
        else:
            print()
        passed, why = check_valgus_gate(d, exercise)
        if passed is True:
            print(f"  膝內夾閘門: ✅ {exercise} → {why}")
        elif passed is False:
            print(f"  膝內夾閘門: ❌ {why}")
            regressions.append((name, exercise, why))
        else:
            print(f"  膝內夾閘門: —— {why}")

        if has_raw and d["duser"].notna().any():
            du = d["duser"].dropna().unique()
            print(f"  Duser = {', '.join(f'{v:.4f}' for v in du)}")
            expected = EXPECTED_DUSER_COUNT.get(exercise, 1)
            if exercise == "HIGH_KNEES" and len(du) == 2:
                print("     （兩個 Duser 是左右腳各自的判準，不是兩場被合併）")
            elif len(du) > expected:
                # 不自動切場：高抬腿的兩個 Duser 會交替出現，「變了就切」會把它
                # 切成一堆單下場次。改為指出可疑的邊界，讓人自己判斷。
                print(f"  ⚠️  這一場出現 {len(du)} 個 Duser，{exercise} 每場只該有 "
                      f"{expected} 個 —— 很可能是兩場訓練被合併")
                ts = d["timestampMs"].to_numpy()
                dus = d["duser"].to_numpy()
                times = d["localTime"].to_numpy()
                for i in range(1, len(dus)):
                    if dus[i] != dus[i - 1]:
                        gap = (ts[i] - ts[i - 1]) / 1000
                        print(f"     邊界：{times[i - 1]} → {times[i]}"
                              f"（間隔 {gap:.0f}s，未達切場門檻 "
                              f"{SESSION_GAP_SECONDS}s）")
            if exercise in SQUAT_FAMILY:
                if p.mean() > SUSPECT_MEAN_P_HIGH:
                    print(f"  ⚠️  平均 p={p.mean():.2f} 偏高 —— 兩下基準動作可能做得太淺，"
                          f"Duser 被拉低，綠燈是假性達標")
                elif p.mean() < SUSPECT_MEAN_P_LOW:
                    print(f"  ⚠️  平均 p={p.mean():.2f} 偏低 —— 兩下基準動作可能過深，"
                          f"或訓練時明顯偷懶")
            else:
                # 非深蹲家族的分母是固定解剖判準，不是基準動作，所以 p 偏高只代表
                # 幅度做得比判準大，不是校正出問題 —— 但可以反推解剖比例來查校正品質。
                ratio = arm_to_shoulder_ratio(exercise, float(du[0]))
                if ratio is not None:
                    off = abs(ratio - ARM_TO_SHOULDER_EXPECTED)
                    mark = "⚠️ " if off > ARM_TO_SHOULDER_TOLERANCE else "✅"
                    print(f"  {mark} 反推 手臂長/肩寬 = {ratio:.3f}"
                          f"（成人約 {ARM_TO_SHOULDER_EXPECTED}）")
                    if off > ARM_TO_SHOULDER_TOLERANCE:
                        print(f"     偏離參考值 —— 站姿校正時手可能沒有自然下垂。"
                              f"手臂長被低估會讓判準變小、整場 p 偏高")
                        print(f"     （參考值目前只有少數場次可依據，容差 "
                              f"±{ARM_TO_SHOULDER_TOLERANCE} 是暫定的）")
                if p.mean() > SUSPECT_MEAN_P_HIGH:
                    print(f"  平均 p={p.mean():.2f} 高於判準 —— 幅度做得比判準大，"
                          f"若整場都綠代表判準偏鬆（見 Config 的 TARGET_FRACTION 註解）")
        else:
            print("  ⚠️  這份 CSV 沒有 duser / dNow 欄位（v3 以前的舊格式），無法診斷校正品質")

    print("\n\n=== 全部場次摘要 ===")
    rows = []
    for name, d in sessions:
        row = {
            "場次": name,
            "動作": (
                d["exerciseType"].iloc[0]
                if "exerciseType" in d.columns and isinstance(d["exerciseType"].iloc[0], str)
                else "?"
            ),
            "模式": d["mode"].iloc[0],
            "次數": len(d),
            "p平均": round(d["depthRatio"].mean(), 3),
            "p標準差": round(d["depthRatio"].std(), 3),
            "膝內夾": int(d["kneeValgus"].sum()),
        }
        if has_raw and d["duser"].notna().any():
            row["Duser"] = round(d["duser"].dropna().iloc[0], 4)
        rows.append(row)
    print(pd.DataFrame(rows).to_string(index=False))

    print("\n\n=== 迴歸檢查 ===")
    if regressions:
        print(f"❌ {len(regressions)} 項不通過：")
        for name, exercise, why in regressions:
            print(f"  {name}（{exercise}）：{why}")
        print("\n這些是**資料完整性**問題，不是顯示問題 —— "
              "帶著它們蒐集受試者資料，M5 的門檻掃描會被污染。")
    else:
        print("✅ 全部場次通過（分級邏輯、膝內夾閘門、Duser 數量）")
        print("   註：這只檢查「App 寫出來的數字自我一致」，"
              "不保證計次次數正確 —— 次數要靠人自己數。")

    covered = {
        d["exerciseType"].iloc[0]
        for _, d in sessions
        if "exerciseType" in d.columns and isinstance(d["exerciseType"].iloc[0], str)
        and str(d["localTime"].max()) >= VALGUS_GATE_SINCE
    }
    todo = sorted(set(JUDGES_VALGUS) - covered - {"HEEL_RAISE"})
    if todo:
        print(f"\n⚠️  閘門上線（{VALGUS_GATE_SINCE}）之後還沒有這些動作的場次："
              f"{', '.join(todo)}")
        print("   共用程式碼改過之後，每個動作都要有一場才算驗過。"
              "（踮腳尖不做，已排除）")


if __name__ == "__main__":
    main()

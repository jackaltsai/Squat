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
    # 時間隔太久，或訓練模式換了，都視為新的一場
    new_session = (gap > SESSION_GAP_SECONDS) | (df["mode"] != df["mode"].shift())
    df = df.assign(_session=new_session.cumsum())
    out = []
    for i, (_, d) in enumerate(df.groupby("_session"), start=1):
        label = f"{d['sourceFile'].iloc[0]} #{i} ({d['localTime'].iloc[0]})"
        out.append((label, d))
    return out


def collect(paths):
    files = []
    for path in paths:
        if os.path.isdir(path):
            files += sorted(glob.glob(os.path.join(path, "*.csv")))
        else:
            files.append(path)
    frames = []
    for f in files:
        df = pd.read_csv(f)
        df["sourceFile"] = os.path.basename(f)
        frames.append(df)
    if not frames:
        sys.exit("找不到任何 CSV")
    return pd.concat(frames, ignore_index=True)


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    df = collect(sys.argv[1:])
    has_raw = "duser" in df.columns

    sessions = []
    for _, per_file in df.groupby("sourceFile", sort=True):
        sessions += split_sessions(per_file)

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

        valgus = d["kneeValgus"].sum()
        print(f"  膝內夾: {int(valgus)}/{len(d)}", end="")
        if has_raw and d["kneeValgusRatio"].notna().any():
            r = d["kneeValgusRatio"]
            print(f"   ratio 範圍={r.min():.3f}~{r.max():.3f} 平均={r.mean():.3f}")
        else:
            print()

        if has_raw and d["duser"].notna().any():
            du = d["duser"].dropna().unique()
            print(f"  Duser = {', '.join(f'{v:.4f}' for v in du)}")
            if p.mean() > SUSPECT_MEAN_P_HIGH:
                print(f"  ⚠️  平均 p={p.mean():.2f} 偏高 —— 校正深蹲可能蹲得太淺，Duser 被拉低，綠燈是假性達標")
            elif p.mean() < SUSPECT_MEAN_P_LOW:
                print(f"  ⚠️  平均 p={p.mean():.2f} 偏低 —— 校正深蹲可能過深，或訓練時明顯偷懶")
        else:
            print("  ⚠️  這份 CSV 沒有 duser / dNow 欄位（v3 以前的舊格式），無法診斷校正品質")

    print("\n\n=== 全部場次摘要 ===")
    rows = []
    for name, d in sessions:
        row = {
            "場次": name,
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


if __name__ == "__main__":
    main()

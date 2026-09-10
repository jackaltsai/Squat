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


def expected_color(p, mode):
    green, yellow = THRESHOLDS[mode]
    return "GREEN" if p >= green else ("YELLOW" if p >= yellow else "RED")


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

    for name, d in df.groupby("sourceFile", sort=True):
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
    agg = {"mode": ("mode", "first"), "次數": ("depthRatio", "size"),
           "p平均": ("depthRatio", "mean"), "p標準差": ("depthRatio", "std"),
           "膝內夾": ("kneeValgus", "sum")}
    if has_raw:
        agg["Duser"] = ("duser", "first")
    print(df.groupby("sourceFile").agg(**agg).round(3).to_string())


if __name__ == "__main__":
    main()

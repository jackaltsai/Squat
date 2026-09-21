package com.heartchen.squat.squat

import com.heartchen.squat.pose.KeyPointType

/**
 * 動作的計量方式。九個動作不是同一種「計次」，統計與紀錄都必須跟著分開。
 */
enum class ExerciseMeasurement {
    /** 次數型：數完成幾下。 */
    REPS,

    /** 持續型：記撐了幾秒。單腳站立記「做了 5 下」沒有意義，它是平衡測試。 */
    HOLD_SECONDS,

    /** 限時計次型：固定秒數內數幾下（如 30 秒坐站測試）。 */
    TIMED_REPS,
}

/**
 * 校正方式。現有的「站姿校正 + 兩下基準深蹲 → Duser → p = Dnow/Duser」
 * 只對深蹲家族有意義：單腳站立沒有深度、側身伸展的位移在 X 軸、
 * 踮腳尖的位移小到會淹沒在關鍵點雜訊裡。所以由動作自己宣告要哪一種。
 */
enum class CalibrationKind {
    /** 站姿基準 + 兩下基準動作，取得個人化深度 Duser。 */
    STAND_AND_BASELINE_REPS,

    /** 只需要站姿基準（取得身體比例尺），不需要基準動作。 */
    STAND_ONLY,

    /** 不需要校正。 */
    NONE,
}

/**
 * 訓練動作。
 *
 * 這個 enum 會被寫進 Room（`SquatRepRecord.exerciseType`，以 name 字串儲存），
 * **既有名稱不可更動**，否則舊紀錄讀回來會在 `valueOf` 丟例外。新增項目是安全的。
 *
 * [detectionImplemented] 標示偵測邏輯是否已實作。UI 會據此把尚未實作的動作標灰，
 * 而不是讓使用者點進去後對著沒有反應的畫面猜哪裡壞了。
 */
enum class ExerciseType(
    val label: String,
    val measurement: ExerciseMeasurement,
    val calibration: CalibrationKind,
    val requiredPoints: Set<KeyPointType>,
    val guidance: String,
    val detectionImplemented: Boolean,
    /** TIMED_REPS 與 HOLD_SECONDS 的目標秒數；REPS 為 null。 */
    val targetSeconds: Int? = null,
    /** 長者使用時的安全提醒，會顯示在動作說明下方。 */
    val safetyNote: String? = null,
) {
    SQUAT(
        label = "深蹲",
        measurement = ExerciseMeasurement.REPS,
        calibration = CalibrationKind.STAND_AND_BASELINE_REPS,
        requiredPoints = KeyPointType.LOWER_BODY,
        guidance = "雙腳與肩同寬，緩慢下蹲再站起。",
        detectionImplemented = true,
        safetyNote = "感覺膝蓋不適就停止，不需要蹲到最低。",
    ),

    CHAIR_SQUAT(
        label = "坐站練習",
        measurement = ExerciseMeasurement.REPS,
        calibration = CalibrationKind.STAND_AND_BASELINE_REPS,
        requiredPoints = KeyPointType.LOWER_BODY,
        guidance = "椅子放在身後，蹲到輕觸椅面再站起。",
        detectionImplemented = false,
        safetyNote = "椅子要靠牆固定，不可使用有輪子的椅子。",
    ),

    HIGH_KNEES(
        label = "原地高抬腿",
        measurement = ExerciseMeasurement.REPS,
        calibration = CalibrationKind.STAND_ONLY,
        requiredPoints = KeyPointType.LOWER_BODY,
        guidance = "原地踏步，輪流將膝蓋抬高。",
        detectionImplemented = false,
        safetyNote = "覺得不穩就扶著椅背進行。",
    ),

    HEEL_RAISE(
        label = "踮腳尖",
        measurement = ExerciseMeasurement.REPS,
        calibration = CalibrationKind.STAND_ONLY,
        requiredPoints = KeyPointType.LOWER_BODY,
        guidance = "雙腳踮起再放下，訓練小腿與平衡。",
        detectionImplemented = false,
        safetyNote = "建議扶著穩固的桌椅進行。",
    ),

    ARM_RAISE(
        label = "雙臂高舉",
        measurement = ExerciseMeasurement.REPS,
        calibration = CalibrationKind.STAND_ONLY,
        requiredPoints = KeyPointType.UPPER_BODY,
        guidance = "雙臂向上高舉再放下，可改為側平舉。",
        detectionImplemented = false,
        safetyNote = "肩膀會痛就降低高度，不必舉到頂。",
    ),

    CHEST_EXPANSION(
        label = "擴胸推掌",
        measurement = ExerciseMeasurement.REPS,
        calibration = CalibrationKind.STAND_ONLY,
        requiredPoints = KeyPointType.UPPER_BODY,
        guidance = "雙臂向後擴胸，再向前推掌。",
        detectionImplemented = false,
    ),

    SIDE_STRETCH(
        label = "側身伸展",
        measurement = ExerciseMeasurement.HOLD_SECONDS,
        calibration = CalibrationKind.STAND_ONLY,
        requiredPoints = KeyPointType.TORSO,
        guidance = "單手上舉，身體向側邊彎，停住數秒。",
        detectionImplemented = false,
        targetSeconds = 15,
        safetyNote = "緩慢進行，感覺輕微拉伸即可，不要彈振。",
    ),

    SINGLE_LEG_STANCE(
        label = "單腳站立",
        measurement = ExerciseMeasurement.HOLD_SECONDS,
        calibration = CalibrationKind.STAND_ONLY,
        requiredPoints = KeyPointType.LOWER_BODY,
        guidance = "單腳離地站立，盡量維持平衡。",
        detectionImplemented = false,
        targetSeconds = 30,
        safetyNote = "務必在可扶手的位置進行，旁邊建議有人陪同。",
    ),

    STST_30S(
        label = "30秒坐站",
        measurement = ExerciseMeasurement.TIMED_REPS,
        calibration = CalibrationKind.STAND_AND_BASELINE_REPS,
        requiredPoints = KeyPointType.LOWER_BODY,
        guidance = "30 秒內反覆坐下站起，盡量多做。",
        detectionImplemented = false,
        targetSeconds = 30,
        safetyNote = "這是體能檢測，感覺頭暈或喘不過氣請立刻停止。",
    );

    /** 統計與畫面上顯示的單位。 */
    val unit: String
        get() = when (measurement) {
            ExerciseMeasurement.REPS, ExerciseMeasurement.TIMED_REPS -> "下"
            ExerciseMeasurement.HOLD_SECONDS -> "秒"
        }

    companion object {
        /** 選擇畫面的 3×3 排列順序。 */
        val GRID_ORDER: List<ExerciseType> = listOf(
            SQUAT, CHAIR_SQUAT, STST_30S,
            HIGH_KNEES, HEEL_RAISE, SINGLE_LEG_STANCE,
            ARM_RAISE, CHEST_EXPANSION, SIDE_STRETCH,
        )
    }
}

package com.heartchen.squat.squat

import com.heartchen.squat.pose.KeyPointType

/**
 * 校正方式。現有的「站姿校正 + 兩下基準深蹲 → Duser → p = Dnow/Duser」
 * 只對深蹲家族有意義：手臂動作沒有「深度」可言，踮腳尖的位移也小到會淹沒在
 * 關鍵點雜訊裡。所以由動作自己宣告要哪一種。
 */
enum class CalibrationKind {
    /** 站姿基準 + 兩下基準動作，取得個人化深度 Duser。 */
    STAND_AND_BASELINE_REPS,

    /** 只需要站姿基準（取得身體比例尺），不需要基準動作。 */
    STAND_ONLY,
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
    val calibration: CalibrationKind,
    val requiredPoints: Set<KeyPointType>,
    val guidance: String,
    val detectionImplemented: Boolean,
    /** 長者使用時的安全提醒，會顯示在動作說明下方。 */
    val safetyNote: String? = null,
) {
    SQUAT(
        label = "深蹲",
        calibration = CalibrationKind.STAND_AND_BASELINE_REPS,
        requiredPoints = KeyPointType.LOWER_BODY,
        guidance = "雙腳與肩同寬，緩慢下蹲再站起。",
        detectionImplemented = true,
        safetyNote = "感覺膝蓋不適就停止，不需要蹲到最低。",
    ),

    CHAIR_SQUAT(
        label = "坐站練習",
        calibration = CalibrationKind.STAND_AND_BASELINE_REPS,
        requiredPoints = KeyPointType.LOWER_BODY,
        guidance = "椅子放在身後，蹲到輕觸椅面再站起。",
        detectionImplemented = false,
        safetyNote = "椅子要靠牆固定，不可使用有輪子的椅子。",
    ),

    HIGH_KNEES(
        label = "原地高抬腿",
        calibration = CalibrationKind.STAND_ONLY,
        requiredPoints = KeyPointType.LOWER_BODY,
        guidance = "原地踏步，輪流將膝蓋抬高。",
        detectionImplemented = false,
        safetyNote = "覺得不穩就扶著椅背進行。",
    ),

    HEEL_RAISE(
        label = "踮腳尖",
        calibration = CalibrationKind.STAND_ONLY,
        requiredPoints = KeyPointType.LOWER_BODY,
        guidance = "雙腳踮起再放下，訓練小腿與平衡。",
        detectionImplemented = false,
        safetyNote = "建議扶著穩固的桌椅進行。",
    ),

    ARM_RAISE(
        label = "雙臂高舉",
        calibration = CalibrationKind.STAND_ONLY,
        requiredPoints = KeyPointType.UPPER_BODY,
        guidance = "雙臂向上高舉再放下，可改為側平舉。",
        detectionImplemented = false,
        safetyNote = "肩膀會痛就降低高度，不必舉到頂。",
    ),

    CHEST_EXPANSION(
        label = "擴胸推掌",
        calibration = CalibrationKind.STAND_ONLY,
        requiredPoints = KeyPointType.UPPER_BODY,
        guidance = "雙臂向後擴胸，再向前推掌。",
        detectionImplemented = false,
    );




    companion object {
        /** 選擇畫面的排列順序：每列三格，共兩列。 */
        val GRID_ORDER: List<ExerciseType> = listOf(
            SQUAT, CHAIR_SQUAT, HIGH_KNEES,
            HEEL_RAISE, ARM_RAISE, CHEST_EXPANSION,
        )
    }
}

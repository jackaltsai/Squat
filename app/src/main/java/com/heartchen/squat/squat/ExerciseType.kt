package com.heartchen.squat.squat

import androidx.annotation.DrawableRes
import com.heartchen.squat.R
import com.heartchen.squat.pose.KeyPointType

/**
 * 訓練動作。
 *
 * 這個 enum 會被寫進 Room（`SquatRepRecord.exerciseType`，以 name 字串儲存），
 * **既有名稱不可更動**，否則舊紀錄讀回來會在 `valueOf` 丟例外。新增項目是安全的。
 *
 * [detectionImplemented] 標示偵測邏輯是否已實作。UI 會據此把尚未實作的動作標灰，
 * 而不是讓使用者點進去後對著沒有反應的畫面猜哪裡壞了。
 *
 * ⚠️ 原本還有一個 `calibration: CalibrationKind` 欄位宣告校正方式，**已刪除** ——
 * 它從宣告之日起就沒有任何地方讀取，流程一律跑「兩下基準深蹲」，
 * 手臂動作也會被要求做兩下基準深蹲。現在改由 `RepSignal.target` 唯一決定：
 * 為 null 代表分母是個人化的 Duser、需要基準校正；非 null 代表判準是固定解剖學地標。
 * 同一件事只記在一處，才不會像那個欄位一樣悄悄失去同步。
 */
enum class ExerciseType(
    val label: String,
    val requiredPoints: Set<KeyPointType>,
    val guidance: String,
    val detectionImplemented: Boolean,
    /** 動作圖示。六個動作都有自己的圖，不共用 —— 借用別的動作的圖會誤導使用者。 */
    @DrawableRes val iconRes: Int,
    /**
     * 三段式回饋的文案。寫死「蹲」會讓雙臂高舉舉不夠高時念出「蹲太淺了」——
     * 這些字會被 TTS 念給兩公尺外的使用者聽，講錯直接誤導動作。
     */
    val feedback: FeedbackMessages,
    /** 長者使用時的安全提醒，會顯示在動作說明下方。 */
    val safetyNote: String? = null,
) {
    SQUAT(
        label = "深蹲",
        requiredPoints = KeyPointType.LOWER_BODY,
        guidance = "雙腳與肩同寬，緩慢下蹲再站起。",
        detectionImplemented = true,
        feedback = FeedbackMessages("深度達標！", "再蹲深一點", "蹲太淺了"),
        iconRes = R.drawable.ic_exercise_squat,
        safetyNote = "感覺膝蓋不適就停止，不需要蹲到最低。",
    ),

    CHAIR_SQUAT(
        label = "坐站練習",
        requiredPoints = KeyPointType.LOWER_BODY,
        guidance = "椅子放在身後，坐下再站起。",
        detectionImplemented = true,
        feedback = FeedbackMessages("深度達標！", "再坐低一點", "坐得太淺了"),
        iconRes = R.drawable.ic_exercise_chair_squat,
        safetyNote = "椅子要靠牆固定，不可使用有輪子的椅子。",
    ),

    HIGH_KNEES(
        label = "原地高抬腿",
        // 只宣告訊號與校正真正會讀的髖與膝。踝雖然校正時會用到（算腿長），
        // 但抬腿時被抬起那腳的踝最容易掉信心值，列入必要點會讓動作峰值的幀
        // 被品質檢查整幀丟掉 —— 累加器自己會跳過缺踝的幀。
        requiredPoints = KeyPointType.HIP_AND_KNEE,
        // 「一下」是單腳抬一次，所以文案講「輪流」而不是「左右各一次算一下」。
        guidance = "原地踏步，輪流把膝蓋抬高到腰的高度。",
        detectionImplemented = true,
        feedback = FeedbackMessages("高度達標！", "膝蓋再抬高", "膝蓋抬太低"),
        iconRes = R.drawable.ic_exercise_high_knees,
        safetyNote = "覺得不穩就扶著椅背進行。",
    ),

    HEEL_RAISE(
        label = "踮腳尖",
        // 訊號是「髖相對腳尖的上升量」，所以只宣告髖與腳尖。
        // 不宣告膝與踝的理由見 KeyPointType.HIP_AND_TOE 與 HeelRaiseSignal：
        // 腳部關鍵點低估抬升 1.6~2.2 倍，判定不靠它們；
        // 校正需要的踝由累加器自己挑（靜止時可靠）。
        requiredPoints = KeyPointType.HIP_AND_TOE,
        guidance = "雙腳踮起再放下，訓練小腿與平衡。站近一點，讓整雙腿清楚入鏡。",
        detectionImplemented = true,
        feedback = FeedbackMessages("高度達標！", "再踮高一點", "踮太低了"),
        iconRes = R.drawable.ic_exercise_heel_raise,
        safetyNote = "建議扶著穩固的桌椅進行。",
    ),

    ARM_RAISE(
        label = "雙臂高舉",
        // 只宣告真正會讀的點：訊號與框位都只用肩與腕，手肘從未被讀取，
        // 卻會因為被軀幹遮住而讓整幀被品質檢查丟掉。
        requiredPoints = KeyPointType.SHOULDER_AND_WRIST,
        guidance = "雙臂向上舉起再放下，舉到肩膀高度就夠。",
        detectionImplemented = true,
        feedback = FeedbackMessages("舉到位了！", "再舉高一點", "手舉太低了"),
        iconRes = R.drawable.ic_exercise_arm_raise,
        safetyNote = "肩膀會痛就降低高度，不必舉到頂。",
    ),

    CHEST_EXPANSION(
        label = "擴胸推掌",
        // 同雙臂高舉：訊號只讀手腕，框位只讀肩與腕，手肘從未被讀取。
        requiredPoints = KeyPointType.SHOULDER_AND_WRIST,
        // 文案強調「向兩側打開」：系統量的是腕距，向前推掌在正面視角幾乎沒有位移、
        // 量不到，所以要讓使用者知道達標靠的是打開的幅度。
        guidance = "雙臂向兩側打開擴胸，再向前推掌。",
        detectionImplemented = true,
        feedback = FeedbackMessages("幅度達標！", "再張開一點", "幅度太小了"),
        iconRes = R.drawable.ic_exercise_chest_expansion,
        safetyNote = "肩膀或胸口有拉扯感就縮小幅度。",
    );




    companion object {
        /** 選擇畫面的排列順序：每列三格，共兩列。 */
        val GRID_ORDER: List<ExerciseType> = listOf(
            SQUAT, CHAIR_SQUAT, HIGH_KNEES,
            HEEL_RAISE, ARM_RAISE, CHEST_EXPANSION,
        )
    }
}

package com.heartchen.squat.squat

/**
 * 訓練動作類型。
 *
 * 目前只實作深蹲，但紀錄一開始就帶上這個欄位，是為了避免之後新增動作時要再做一次
 * Room migration —— 每一次遷移都是一次讓測試人員既有資料消失的機會。
 *
 * 之後預計加入的動作（見規劃討論）：
 * - SIT_TO_STAND 坐站：Senior Fitness Test 的 30 秒坐站測試，有臨床常模；
 *   軌跡同樣是髖部垂直位移，可複用現有狀態機，且椅子在身後等於安全網。
 */
enum class ExerciseType(val label: String) {
    SQUAT("深蹲"),
}

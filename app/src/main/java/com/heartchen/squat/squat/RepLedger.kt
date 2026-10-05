package com.heartchen.squat.squat

/**
 * 把每一下的紀錄配對到**產生它的那一台**狀態機。
 *
 * ### 為什麼需要這個類別
 * 紀錄在 `BOTTOM` 那一幀就能算出（深度達成率、膝內夾比值都在極點判定），
 * 但要到該台狀態機 `UP → STAND` 時這一下才算完成、才該寫進資料庫。
 * 中間隔了好幾幀，所以必須暫存。
 *
 * 原本整個畫面共用**一個**暫存格子。單一狀態機（深蹲家族）沒問題，
 * 但原地高抬腿左右腳各一台，兩腳的抬腿一旦在時間上重疊就會互相蓋掉：
 *
 * ```
 * 左腳 BOTTOM        → 格子 = 左腳的紀錄
 * 右腳 BOTTOM        → 格子 = 右腳的紀錄（左腳那筆被蓋掉）
 * 左腳 UP → STAND    → 存下的是「右腳的紀錄」，然後清空格子
 * 右腳 UP → STAND    → 格子已空，**整筆遺失**
 * ```
 *
 * 模擬（兩腳半波重疊 ≥60% 的踏步節奏）：畫面顯示 26 下、資料庫只有 13 筆，
 * 而且**全部掛在同一隻腳的判準上**。
 * **畫面數字是對的**，所以這個資料遺失完全看不出來 —— 而 M5 的門檻掃描
 * 會拿那份只有一半、而且標錯腿的資料去跑。
 *
 * 2026-10-05 實機的簽名正是如此：一場 10 筆紀錄的 `duser` 全是 0.48798656，
 * 到最後一位都相同，而使用者說他是刻意左右交替的。
 *
 * ### 為什麼另外開一個類別而不是留在畫面裡
 * 這是純粹的配對邏輯，**抽出來才測得到**。
 * 原本那段在 Compose 的相機分析回呼裡，純 JVM 測試碰不到，
 * 於是「少存一半紀錄」這種錯只能靠實機撞到。
 */
class RepLedger<T> {
    private val pending = LinkedHashMap<SquatStateMachine, T>()

    /** 某台狀態機到達極點，暫存它這一下的紀錄（同一台重複呼叫會覆蓋，那是對的）。 */
    fun hold(machine: SquatStateMachine, record: T) {
        pending[machine] = record
    }

    /**
     * 回傳本幀**完成**的那些紀錄並移出暫存。
     *
     * [repCountsBefore] 是推進這一幀**之前**每台各自的次數，順序與 [machines] 相同。
     * 判據必須是「**哪一台**的次數增加了」—— 取總和會分不出是誰完成的，
     * 也就無法把紀錄配對回正確的那一台。
     */
    fun harvest(machines: List<SquatStateMachine>, repCountsBefore: List<Int>): List<T> {
        val finished = mutableListOf<T>()
        machines.forEachIndexed { index, machine ->
            val before = repCountsBefore.getOrNull(index) ?: return@forEachIndexed
            if (machine.repCount > before) {
                pending.remove(machine)?.let { finished += it }
            }
        }
        return finished
    }

    /** 狀態機被重建時必須清空：暫存是以狀態機物件為鍵的，舊鍵永遠不會再被比對到。 */
    fun clear() = pending.clear()

    /** 目前暫存著幾筆（除錯與測試用）。 */
    val heldCount: Int get() = pending.size
}

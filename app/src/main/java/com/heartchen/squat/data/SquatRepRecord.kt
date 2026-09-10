package com.heartchen.squat.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.heartchen.squat.squat.DepthFeedback
import com.heartchen.squat.squat.TrainingMode

/**
 * M4：每次深蹲（一下）完成時的分析結果紀錄，供訓練歷程頁面與後續量化驗證使用。
 *
 * [depthRatio]（p = dNow / duser）本身無法回答「p 偏高是因為蹲得深，還是因為校正基準太淺」，
 * 所以另外把分子分母 [dNow] / [duser] 原樣存下來；[kneeValgusRatio] 同理，存門檻判定前的
 * 連續值，之後做驗證集試門檻（見 CLAUDE.md 第 6 節）時才有辦法重新掃描不同門檻。
 * 三個欄位都可為 null，是為了相容 v1 就已經寫入、沒有這些值的舊紀錄。
 */
@Entity(tableName = "squat_rep_records")
data class SquatRepRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val depthRatio: Float,
    val kneeValgus: Boolean,
    val feedbackColor: DepthFeedback,
    val mode: TrainingMode,
    /** 本次最低點的原始深度（以站姿的髖-踝距離正規化），即 p 的分子。 */
    val dNow: Float? = null,
    /** 當次訓練校正取得的個人化基準深度，即 p 的分母。 */
    val duser: Float? = null,
    /** 膝內夾原始比值 (踝距 - 膝距) / 髖寬，門檻判定前的連續值。 */
    val kneeValgusRatio: Float? = null
)

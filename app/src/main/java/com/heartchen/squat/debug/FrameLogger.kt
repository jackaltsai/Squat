package com.heartchen.squat.debug

import android.content.Context
import android.util.Log
import com.heartchen.squat.pose.KeyPoint
import com.heartchen.squat.pose.KeyPointType
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "FrameLogger"

/**
 * 記錄**全部**關鍵點，不挑。
 *
 * ⚠️ 原本寫死下肢六點，於是雙臂高舉的研究模式 CSV 裡**完全沒有肩膀與手腕** ——
 * 正好是那個動作唯一會用到的點，等於儀器壞掉卻還在量
 * （2026-10-01：為了調舉手門檻特地錄了一場，結果一個需要的欄位都沒有）。
 *
 * 用 `entries` 而不是手寫清單：以後新增關鍵點會自動被記錄，不會再漏。
 */
private val LOGGED_KEYPOINTS = KeyPointType.entries

/**
 * M4 除錯/研究模式：把每一幀的原始關鍵點座標、EMA 平滑後座標與狀態機狀態寫成 CSV，
 * 供後續用 Python 讀取、跟人工標註比對（見 CLAUDE.md M4 驗收標準）。
 *
 * 每一列都帶 `exerciseType` —— 沒有它的話，分析工具無從判斷該套用哪一種診斷，
 * 只能把所有區段都印出來（曾經對一場高抬腿錄影印出「雙臂高舉的進度軌跡」）。
 *
 * **被品質檢查擋下的幀也會記錄**（`qualityOk = false`，EMA 欄位留空）。
 * 只記通過的幀的話，CSV 完全看不出是哪個關鍵點、在什麼數值上把整幀擋掉 ——
 * 而「為什麼不計次」的答案往往就在被擋掉的那些幀裡。
 * 先前只能從時間戳的空隙去**推估**掉幀率，那不該是推估的。
 * 檔案存在 app 專屬外部儲存（getExternalFilesDir），可用 adb pull 取出，不需額外儲存權限，
 * 且會隨 App 解除安裝一併清除，符合「僅存骨架數據」的隱私規劃。
 */
class FrameLogger(context: Context) {
    private val file: File
    private val writer: BufferedWriter

    init {
        val dir = context.getExternalFilesDir("debug_logs") ?: File(context.filesDir, "debug_logs")
        if (!dir.exists()) dir.mkdirs()
        val name = "squat_frames_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.csv"
        file = File(dir, name)
        writer = BufferedWriter(FileWriter(file))
        writer.write(buildHeader())
        writer.newLine()
        writer.flush()
        Log.i(TAG, "Frame log started: ${file.absolutePath}")
    }

    val filePath: String get() = file.absolutePath

    private fun buildHeader(): String {
        val cols = mutableListOf(
            "frameTimestampMs", "exerciseType", "state", "qualityOk", "framingIssue"
        )
        for (type in LOGGED_KEYPOINTS) {
            cols += "${type.name}_raw_x"
            cols += "${type.name}_raw_y"
            cols += "${type.name}_raw_confidence"
        }
        for (type in LOGGED_KEYPOINTS) {
            cols += "${type.name}_ema_x"
            cols += "${type.name}_ema_y"
        }
        return cols.joinToString(",")
    }

    /** 呼叫端須自行確保 raw/ema 座標對應同一幀，此類別本身不做執行緒同步（僅供單一相機分析執行緒呼叫）。 */
    fun logFrame(
        rawByType: Map<KeyPointType, KeyPoint>,
        emaByType: Map<KeyPointType, KeyPoint>,
        state: String,
        exerciseType: String,
        qualityOk: Boolean = true,
        framingIssue: String = ""
    ) {
        val values = mutableListOf(
            System.currentTimeMillis().toString(),
            exerciseType,
            state,
            qualityOk.toString(),
            framingIssue
        )
        for (type in LOGGED_KEYPOINTS) {
            val p = rawByType[type]
            values += p?.x?.toString().orEmpty()
            values += p?.y?.toString().orEmpty()
            values += p?.inFrameLikelihood?.toString().orEmpty()
        }
        for (type in LOGGED_KEYPOINTS) {
            val p = emaByType[type]
            values += p?.x?.toString().orEmpty()
            values += p?.y?.toString().orEmpty()
        }
        try {
            writer.write(values.joinToString(","))
            writer.newLine()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write frame log", e)
        }
    }

    fun close() {
        try {
            writer.flush()
            writer.close()
            Log.i(TAG, "Frame log saved: $filePath")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close frame log", e)
        }
    }
}

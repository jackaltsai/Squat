package com.heartchen.squat.debug

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.heartchen.squat.data.SquatRepRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "SessionExporter"

/**
 * 把一次訓練的每下深蹲紀錄寫成 CSV，並用 Android 系統分享選單（Sharesheet）讓**使用者自己**
 * 決定要寄給誰、存到哪。
 *
 * 這裡刻意「不」由 App 自動把檔案上傳或寄給開發者：
 * Google Play 的 User Data 政策要求任何把個人資料傳出裝置的行為都必須事先揭露並取得同意，
 * 而本 App 的隱私權政策與 Play Data safety 宣告都聲明全程離線、不傳送任何資料。
 * 改成由使用者主動點「分享研究資料」、在系統選單裡自己挑收件者，屬於 user-initiated transfer，
 * 不計入 App 的 collection/sharing，才不會牴觸既有宣告，也不必重送審。
 */
object SessionExporter {

    /** 匯出檔案放在 app 專屬外部儲存，不需額外儲存權限，且會隨 App 解除安裝一併清除。 */
    private fun exportDir(context: Context): File {
        val dir = context.getExternalFilesDir("exports") ?: File(context.filesDir, "exports")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * 寫出本次訓練的每下紀錄 CSV。欄位刻意保持扁平、無引號跳脫需求（列舉值與數字），
     * 可直接用 pandas.read_csv 讀取比對人工標註。
     *
     * dNow / duser 兩欄是 depthRatio（p）的分子與分母，kneeValgusRatio 是膝內夾的原始比值。
     * 只存判定結果的話，事後無法分辨「p 偏高」是蹲得深還是校正基準太淺，也無法重新掃描門檻。
     */
    fun writeSessionCsv(context: Context, records: List<SquatRepRecord>): File? {
        if (records.isEmpty()) return null
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(exportDir(context), "squat_session_$stamp.csv")
        return try {
            file.bufferedWriter().use { writer ->
                writer.write(
                    "repIndex,timestampMs,localTime,mode," +
                        "dNow,duser,depthRatio,feedbackColor,kneeValgusRatio,kneeValgus"
                )
                writer.newLine()
                val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                records.forEachIndexed { index, record ->
                    writer.write(
                        listOf(
                            (index + 1).toString(),
                            record.timestamp.toString(),
                            timeFormat.format(Date(record.timestamp)),
                            record.mode.name,
                            record.dNow?.toString().orEmpty(),
                            record.duser?.toString().orEmpty(),
                            record.depthRatio.toString(),
                            record.feedbackColor.name,
                            record.kneeValgusRatio?.toString().orEmpty(),
                            record.kneeValgus.toString()
                        ).joinToString(",")
                    )
                    writer.newLine()
                }
            }
            Log.i(TAG, "Session CSV written: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write session CSV", e)
            null
        }
    }

    /**
     * 開啟系統分享選單，把 [files] 交給使用者選擇的 App（Gmail、雲端硬碟、LINE…）。
     * 一定要用 FileProvider 產生 content:// URI，直接傳 file:// 在 API 24+ 會丟 FileUriExposedException。
     */
    fun share(context: Context, files: List<File>) {
        val existing = files.filter { it.exists() && it.length() > 0 }
        if (existing.isEmpty()) {
            Log.w(TAG, "Nothing to share")
            return
        }
        val authority = "${context.packageName}.fileprovider"
        val uris = ArrayList<Uri>()
        for (file in existing) {
            try {
                uris += FileProvider.getUriForFile(context, authority, file)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "File not covered by FileProvider paths: ${file.absolutePath}", e)
            }
        }
        if (uris.isEmpty()) return

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris.first()) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
        }
        intent.type = "text/csv"
        intent.putExtra(Intent.EXTRA_SUBJECT, "深蹲教練訓練資料")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val chooser = Intent.createChooser(intent, "分享研究資料")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start share chooser", e)
        }
    }
}

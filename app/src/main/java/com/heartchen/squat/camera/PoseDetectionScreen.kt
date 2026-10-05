package com.heartchen.squat.camera

import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.heartchen.squat.config.Config
import com.heartchen.squat.data.SquatDatabase
import com.heartchen.squat.data.SquatRepRecord
import com.heartchen.squat.debug.FrameLogger
import com.heartchen.squat.debug.SessionExporter
import com.heartchen.squat.pose.EmaSmoother
import com.heartchen.squat.pose.FramingIssue
import com.heartchen.squat.pose.KeyPointType
import com.heartchen.squat.pose.PoseAnalyzer
import com.heartchen.squat.pose.PoseConfidenceList
import com.heartchen.squat.pose.PoseFrame
import com.heartchen.squat.pose.PoseOverlay
import com.heartchen.squat.pose.evaluateFraming
import com.heartchen.squat.pose.passesQualityCheck
import com.heartchen.squat.squat.DepthFeedback
import com.heartchen.squat.squat.ExerciseType
import com.heartchen.squat.squat.ExercisePicker
import com.heartchen.squat.squat.RepLedger
import com.heartchen.squat.squat.RepSignal
import com.heartchen.squat.squat.StandCalibrator
import com.heartchen.squat.squat.SquatState
import com.heartchen.squat.squat.SquatStateMachine
import com.heartchen.squat.squat.TrainingMode
import com.heartchen.squat.squat.detectKneeValgus
import com.heartchen.squat.squat.evaluateDepthFeedback
import com.heartchen.squat.squat.judgesKneeValgus
import com.heartchen.squat.squat.kneeValgusRatio
import com.heartchen.squat.squat.needsCountdownRebaseline
import com.heartchen.squat.squat.standCalibratorFor
import com.heartchen.squat.stats.DailyExerciseCounts
import com.heartchen.squat.stats.DateBuckets
import com.heartchen.squat.stats.TrainingStatsOverlay
import com.heartchen.squat.stats.buildDailyExerciseCounts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

private const val TAG = "PoseDetectionScreen"
private const val KNEE_VALGUS_MESSAGE = "膝蓋往外一點"

/**
 * 流程：選動作 → 站姿校正（3 秒）→ 基準深蹲校正（2 次）→ 準備倒數（3、2、1）→ 正式訓練 → 結束。
 *
 * READY_COUNTDOWN 是為了把「校正的兩下」跟「正式計次的第一下」明確切開：
 * 沒有倒數的話，使用者做完第二下校正深蹲會直接接上訓練，不知道什麼時候開始算數。
 */
private enum class FlowStep { SELECT_EXERCISE, STAND_HOLD, SQUAT_CALIBRATION, READY_COUNTDOWN, TRAINING, FINISHED }

/**
 * M1+M2+M3 Demo 畫面：CameraX 即時預覽 + ML Kit 骨架疊圖 + 品質過濾/EMA 平滑 +
 * 五階段狀態機自動計次 + 個人化基準深度校正 + 三段式（綠/黃/紅）深度回饋。
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun PoseDetectionScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 固定用前鏡頭：深蹲過程中使用者要看著螢幕確認自己的姿勢與綠/黃/紅回饋，
    // 用後鏡頭就等於背對畫面，看不到任何提示，所以不提供前後鏡頭切換。
    val lensFacing = CameraSelector.LENS_FACING_FRONT
    var poseFrame by remember { mutableStateOf<PoseFrame?>(null) }
    var previewViewSize by remember { mutableStateOf(IntSize.Zero) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    var flowStep by remember { mutableStateOf(FlowStep.SELECT_EXERCISE) }
    // 訓練模式選擇器已移除，一律使用入門門檻。
    // TrainingMode 這個 enum 與三段式門檻邏輯**刻意保留**：那是論文表 1 的設計主張，
    // 也是 M3 驗收標準的受測對象，從程式碼刪掉就再也無法驗證或在口試上 demo。
    // 對長者而言「挑戰更深」本來就不該是預設框架，所以只是不暴露給使用者選。
    val trainingMode = TrainingMode.BEGINNER
    var selectedExercise by remember { mutableStateOf(ExerciseType.SQUAT) }

    var standHoldStartMs by remember { mutableStateOf<Long?>(null) }
    var standHoldElapsedMs by remember { mutableStateOf(0L) }
    // 站姿校正要量什麼由動作決定：深蹲家族量「髖部基準 + 髖踝距離」，
    // 雙臂高舉量「肩寬 + 站姿時手腕低於肩的距離」。
    // 不能統一累加四組關鍵點 —— 品質檢查只保證當前動作需要的點到齊，
    // 手臂動作時髖與踝可能根本沒偵測到，一起累加會讓站姿校正永遠跑不完。
    var standCalibrator by remember { mutableStateOf<StandCalibrator?>(null) }
    // 倒數期間重新量一次靜止基準，只有踮腳尖需要（見 HeelRaiseSignal 的說明）：
    // 它的振幅只有腿長的 6~8%，而使用者在站姿校正與訓練之間移動幾個百分點是常態，
    // 用站姿校正的基準跑狀態機會是 0 下。倒數是正式開始前站定的最後一刻。
    var countdownCalibrator by remember { mutableStateOf<StandCalibrator?>(null) }
    var standCalibrationWarning by remember { mutableStateOf<String?>(null) }
    // 站姿校正被判定不合理（如手臂長/肩寬超出解剖學區間）時要求重做的次數。
    // 用完上限就照收 —— 與深蹲的基準校正同樣的取捨：卡在校正出不去更糟，
    // 而 CSV 的 duser 會留下證據，事後分析看得出那一場校正品質不佳。
    var standCalibrationRetryCount by remember { mutableIntStateOf(0) }
    // 站姿校正產生的動作訊號，整場訓練固定不變，確保 p 的計算基準前後一致。
    // **是清單**：原地高抬腿左右腳各一個訊號、各一台狀態機 ——
    // 兩腳的抬腿半波連續鋪滿，用單一訊號會讓進度永遠回不到返回門檻以下，
    // 第一下之後就卡在 UP 再也計不到（見 `HighKneeSignal` 註解）。
    var repSignals by remember { mutableStateOf<List<RepSignal>>(emptyList()) }
    var calibrationStateMachine by remember { mutableStateOf<SquatStateMachine?>(null) }
    var calibrationSquatState by remember { mutableStateOf(SquatState.STAND) }
    var calibrationDepths by remember { mutableStateOf<List<Float>>(emptyList()) }
    // 校正品質檢查：兩下校正深蹲深度差太多就要求重做，避免試探性的淺蹲把 Duser 拉低。
    var calibrationRetryCount by remember { mutableIntStateOf(0) }
    var calibrationWarning by remember { mutableStateOf<String?>(null) }

    var trainingMachines by remember { mutableStateOf<List<SquatStateMachine>>(emptyList()) }
    // 達成率 p 的分母，**只有深蹲家族會用到**：校正兩下基準動作得到的個人化深度 Duser。
    // 其餘動作的分母是各自訊號的 `RepSignal.target`（固定解剖學判準），
    // 在計次完成時直接從觸發的那台狀態機讀 —— 高抬腿左右腳的判準可能略有差異，
    // 存一個全域值會把其中一腳算錯。
    // 兩者都寫進紀錄的 duser 欄位 —— 那個欄位的定義就是「p 的分母」，
    // 留空的話事後無從還原分子分母，M5 重新掃描門檻就做不了。
    var duser by remember { mutableStateOf<Float?>(null) }
    var repCount by remember { mutableIntStateOf(0) }
    var depthFeedback by remember { mutableStateOf<DepthFeedback?>(null) }
    var kneeValgusFlag by remember { mutableStateOf(false) }
    // 每一下的紀錄要配對到**產生它的那一台**狀態機，不能共用一個格子
    // （高抬腿左右腳各一台，重疊時會互相蓋掉並靜默遺失一半紀錄）。
    // 配對邏輯抽到 [RepLedger] 才測得到 —— 留在這個回呼裡純 JVM 測試碰不到。
    val repLedger = remember { RepLedger<SquatRepRecord>() }
    var sessionRecords by remember { mutableStateOf<List<SquatRepRecord>>(emptyList()) }
    // 準備倒數目前要顯示的大字：「準備」→「3」→「2」→「1」→「開始！」，null 表示不在倒數。
    var readyCountdownText by remember { mutableStateOf<String?>(null) }
    var exportFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var showStats by remember { mutableStateOf(false) }
    var statsCounts by remember { mutableStateOf<DailyExerciseCounts?>(null) }
    // 統計頁正在看哪一天（午夜時間戳）。可以往前翻，所以不能寫死「今天」。
    var statsDayStart by remember { mutableLongStateOf(0L) }
    // 骨架疊圖一律顯示（見下方 PoseOverlay），這個開關只控制信心值數字列表跟
    // M4 研究模式的每幀 CSV 紀錄（原始座標 + EMA 平滑座標 + 狀態機狀態），一般使用者不需要開啟。
    var debugMode by remember { mutableStateOf(false) }
    var frameLogger by remember { mutableStateOf<FrameLogger?>(null) }
    // 每按一次「停止」就 +1，用來逼 DisposableEffect 重建一個新的逐幀檔。
    // 研究模式開著就該**每一場**都錄到，不是只錄開關打開後的那一場（見 stopTraining）。
    var frameLogSession by remember { mutableIntStateOf(0) }
    // 這次停止時，逐幀檔實際錄到幾幀。0 = 這場沒有逐幀資料。
    var exportFrameCount by remember { mutableIntStateOf(0) }
    var framingIssue by remember { mutableStateOf(FramingIssue.OK) }

    val database = remember { SquatDatabase.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    // 除錯模式開啟時才建立 CSV 紀錄檔；關閉或離開畫面時 flush + 關閉檔案，避免資料遺失。
    DisposableEffect(debugMode, frameLogSession) {
        if (debugMode) {
            frameLogger = FrameLogger(context)
        }
        onDispose {
            frameLogger?.close()
            frameLogger = null
        }
    }

    val emaSmoother = remember { EmaSmoother(Config.EMA_ALPHA) }
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 90) }
    DisposableEffect(Unit) {
        onDispose { toneGenerator.release() }
    }

    // M4：語音提示，BOTTOM 觸發的深度回饋（綠/黃/紅）同步用語音念出訊息。
    val textToSpeech = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        lateinit var tts: TextToSpeech
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.TAIWAN
            }
        }
        textToSpeech.value = tts
        onDispose {
            tts.stop()
            tts.shutdown()
        }
    }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    // BOTTOM 觸發的顏色回饋只維持短暫時間，避免一直卡在畫面上；同步用語音念出訊息。
    // 用 QUEUE_ADD 而非 QUEUE_FLUSH，避免跟下面膝內夾的語音互相蓋掉。
    LaunchedEffect(depthFeedback) {
        val feedback = depthFeedback
        if (feedback != null) {
            // 文案取自當前動作：雙臂高舉舉不夠高時該說「手舉太低了」，不是「蹲太淺了」
            val message = selectedExercise.feedback.of(feedback)
            textToSpeech.value?.speak(message, TextToSpeech.QUEUE_ADD, null, null)
            delay(2000)
            depthFeedback = null
        }
    }

    // M4：膝內夾同樣只在 BOTTOM 觸發一次，語音提示「膝蓋往外一點」。
    LaunchedEffect(kneeValgusFlag) {
        if (kneeValgusFlag) {
            textToSpeech.value?.speak(KNEE_VALGUS_MESSAGE, TextToSpeech.QUEUE_ADD, null, null)
            delay(2000)
            kneeValgusFlag = false
        }
    }

    // 受測者可能離鏡頭較遠看不清楚文字，流程轉換（進入站姿校正/基準校正）也用語音提示一次。
    LaunchedEffect(flowStep, selectedExercise) {
        val message = when (flowStep) {
            // ⚠️ 原本念的是「請站直不動，準備校正站姿基準」—— **完全沒提手要放哪裡**，
            // 而上肢動作的判準整個建立在「站姿時手腕低於肩多少」上。
            // 校正失敗後才念的 calibrationHintFor() 正是事前從來沒給過的那一個指示。
            //
            // 實測後果：同一個解剖量（手臂長÷肩寬）由同一段校正程式碼量到，
            // 雙臂高舉五場 σ = 0.018（1.348~1.394），
            // 擴胸推掌三場 σ = **0.142**（1.069~1.409）—— 差 8 倍。
            // 兩者差別在站姿校正時畫面上寫什麼：擴胸的說明是
            // 「雙臂向兩側打開擴胸，再向前推掌」，使用者讀著它就把手擺開了，
            // 而系統正在量他「自然下垂」的手腕位置。
            FlowStep.STAND_HOLD ->
                "${calibrationHintFor(selectedExercise)}，準備校正站姿基準"
            // 用動作名稱而非寫死「深蹲」：坐站練習走同一條校正流程，
            // 叫使用者「做兩次深蹲」會讓他以為選錯動作了。
            FlowStep.SQUAT_CALIBRATION ->
                "請完成兩次${selectedExercise.label}，校正基準深度"
            else -> null
        }
        message?.let { textToSpeech.value?.speak(it, TextToSpeech.QUEUE_ADD, null, null) }
    }

    // 站姿量測失敗（例如舉手動作校正時就把手舉著）也要出聲，理由同下。
    LaunchedEffect(standCalibrationWarning) {
        standCalibrationWarning?.let {
            textToSpeech.value?.speak(it, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    // 校正被判定不一致而要求重做時，語音講一次（使用者離手機遠，只看文字可能沒注意到）。
    // key 用 retryCount 而不是 warning 字串：連續兩次被退回時訊息內容相同，
    // 以字串當 key 的話 LaunchedEffect 不會重跑，第二次就不會出聲。
    LaunchedEffect(calibrationRetryCount) {
        if (calibrationRetryCount > 0) {
            calibrationWarning?.let { textToSpeech.value?.speak(it, TextToSpeech.QUEUE_FLUSH, null, null) }
        }
    }

    // 校正完成 → 正式訓練之間的「準備 3 2 1 開始」倒數。
    // 使用者站在離手機 2 公尺外，字要夠大、也要有聲音，不能只靠畫面。
    // 用 QUEUE_FLUSH 讓每個數字蓋掉前一個，避免倒數念不完就進訓練、跟計次語音疊在一起。
    LaunchedEffect(flowStep) {
        if (flowStep != FlowStep.READY_COUNTDOWN) return@LaunchedEffect
        readyCountdownText = "準備"
        textToSpeech.value?.speak("準備", TextToSpeech.QUEUE_FLUSH, null, null)
        delay(1000)
        for (n in Config.READY_COUNTDOWN_SECONDS downTo 1) {
            readyCountdownText = n.toString()
            textToSpeech.value?.speak(n.toString(), TextToSpeech.QUEUE_FLUSH, null, null)
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
            delay(1000)
        }
        readyCountdownText = "開始！"
        textToSpeech.value?.speak("開始", TextToSpeech.QUEUE_FLUSH, null, null)
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 250)
        delay(800)
        readyCountdownText = null
        // 倒數期間量到的基準取代站姿校正的那一份。量不到（例如整段倒數腳尖都沒入鏡）
        // 就沿用站姿校正的訊號 —— 退回一個已知較差的基準，比讓使用者卡在這裡好。
        if (needsCountdownRebaseline(repSignals)) {
            val rebuilt = countdownCalibrator?.build(strict = true).orEmpty()
            if (rebuilt.isNotEmpty()) {
                repSignals = rebuilt
                trainingMachines = rebuilt.map { SquatStateMachine(it) }
                repLedger.clear()
            } else {
                Log.w(
                    TAG,
                    "Countdown rebaseline unavailable for $selectedExercise " +
                        "(samples=${countdownCalibrator?.sampleCount ?: 0}), " +
                        "falling back to stand-hold baseline"
                )
            }
            countdownCalibrator = null
        }
        flowStep = FlowStep.TRAINING
    }

    // 讀取訓練統計。日/週/月的區間邊界一律在 Kotlin 端算（見 DateBuckets 的說明），
    // 摘要數字與每日圖表才會用同一套定義，跨日跨月那幾筆不會對不起來。
    // 讀某一天的「各動作做了幾下」。只查那一天的區間，往前翻很多天也不會
    // 把整個資料庫讀進來（`recordsSince` 會，所以統計頁不用它）。
    val loadStatsFor: (Long) -> Unit = { dayStart ->
        statsDayStart = dayStart
        statsCounts = null
        coroutineScope.launch {
            val dao = database.squatRepDao()
            val counts = withContext(Dispatchers.IO) {
                val dayEnd = DateBuckets.addDays(dayStart, 1)
                buildDailyExerciseCounts(dao.recordsBetween(dayStart, dayEnd), dayStart)
            }
            statsCounts = counts
        }
    }

    // 匯出資料庫裡的全部歷史紀錄。
    // 畫面上的訓練歷程與單場 CSV 都只看記憶體裡的本次紀錄，按「重新開始」就清空；
    // 這條路徑直接讀 Room，救得回使用者忘記在停止後分享的那些組。
    val exportAllHistory: () -> Unit = {
        coroutineScope.launch {
            val records = withContext(Dispatchers.IO) { database.squatRepDao().getAll() }
            if (records.isEmpty()) {
                Toast.makeText(context, "資料庫裡還沒有任何紀錄", Toast.LENGTH_SHORT).show()
            } else {
                val file = withContext(Dispatchers.IO) {
                    SessionExporter.writeAllRecordsCsv(context, records)
                }
                if (file == null) {
                    Toast.makeText(context, "匯出失敗", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "共 ${records.size} 筆紀錄", Toast.LENGTH_SHORT).show()
                    SessionExporter.share(context, listOf(file))
                }
            }
        }
    }

    // 停止訓練：關掉逐幀 CSV（flush 到檔案）、把本次每下紀錄另外寫成一份 session CSV，
    // 然後停在結束摘要畫面讓使用者決定要不要分享。
    //
    // ⚠️ **不**把 debugMode 關掉。原本為了維持「除錯模式開 ⇔ 有 frameLogger」而順手關掉，
    // 代價是研究者每錄一場都得回選單重開一次開關，忘了就**靜默沒有逐幀資料** ——
    // 2026-10-02 連續兩場踮腳尖錄影就是這樣只剩 session CSV。
    // 改為把那條不變式維持在「debugMode 開 ⇔ 有 frameLogger，每場一個新檔」：
    // frameLogSession +1 會讓 DisposableEffect 重跑並開一個新檔。
    val stopTraining: () -> Unit = {
        val logger = frameLogger
        logger?.close()
        val frameCsv = logger?.filePath?.let { File(it) }
        exportFrameCount = logger?.frameCount ?: 0
        frameLogger = null
        frameLogSession++
        val sessionCsv = SessionExporter.writeSessionCsv(context, sessionRecords)
        // 只有表頭的空檔不要塞進分享清單 —— 多一個空檔只會讓人以為錄到了。
        exportFiles = listOfNotNull(sessionCsv, frameCsv?.takeIf { exportFrameCount > 0 })
        depthFeedback = null
        kneeValgusFlag = false
        flowStep = FlowStep.FINISHED
        textToSpeech.value?.speak("訓練結束", TextToSpeech.QUEUE_FLUSH, null, null)
    }

    DisposableEffect(previewView) {
        val pv = previewView
        if (pv == null) {
            return@DisposableEffect onDispose {}
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val isFrontCamera = lensFacing == CameraSelector.LENS_FACING_FRONT
        var wasReady = false
        var framingIssueStreakValue = FramingIssue.OK
        var framingIssueStreakCount = 0
        // 狀態機卡住偵測：記住上一次「狀態組合」改變的時間。
        // 用狀態**有沒有變**而不是「在不在 STAND」，因為卡住的定義就是不再變化。
        var lastMachineStates: List<SquatState> = emptyList()
        var lastStateChangeMs = System.currentTimeMillis()
        val analyzer = PoseAnalyzer(isFrontCamera = isFrontCamera) { frame ->
            poseFrame = frame
            // 只檢查當前動作需要的關鍵點：深蹲用不到手腕，手臂動作用不到腳踝，
            // 要求全部到齊會讓大量可用的幀被丟棄。
            val isReady = frame != null && frame.passesQualityCheck(selectedExercise.requiredPoints)
            if (isReady && !wasReady) {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            }
            wasReady = isReady

            // 框位是「擺位」問題，只在使用者回到靜止姿勢時判斷。
            // 動作進行中本來就會讓關鍵點跑到畫面邊緣（舉手時手腕會接近甚至超出上緣），
            // 拿動作中的幀去判框位只會一直誤報 —— 實測時整場只聽得到「舉到肩膀就好」
            // 就是這樣來的。訓練尚未開始時沒有狀態機，一律視為靜止。
            // 所有狀態機都在 STAND 才算靜止。高抬腿左右腳各一台，
            // 只要有一腳還抬著就不該拿那一幀去判框位。
            //
            // ⚠️ **但這個閘門只能套在通過品質檢查的幀上。**
            // 狀態機要靠通過的幀才會前進；幀一直被丟棄時它會**凍結**在 DOWN 或 UP，
            // 於是 `atRest` 永遠是 false、框位永遠被當成 OK ——
            // 畫面不顯示、語音不念，使用者在那裡舉了很多下卻**一個提示都沒有**
            // （2026-10-05 實機：雙臂高舉站太遠，整場 0 下且全程靜默，
            // 往前一步才恢復）。
            //
            // 被丟棄的幀**沒有動作資訊**，閘門的理由（動作中會誤報）根本不適用，
            // 而那正是最該講話的時刻 —— 系統看不清楚使用者，就該說出來。
            val atRest = trainingMachines.all { it.state == SquatState.STAND }

            val machineStates = trainingMachines.map { it.state }
            if (machineStates != lastMachineStates) {
                lastMachineStates = machineStates
                lastStateChangeMs = System.currentTimeMillis()
            }
            // 卡在非 STAND 太久 → 使用者很可能離開了校正時的位置。
            // 這是安全網：根因各自修（見 ScaleTracker），但沉默不該是失敗模式。
            val stuck = !atRest &&
                System.currentTimeMillis() - lastStateChangeMs > Config.STUCK_STATE_TIMEOUT_MS

            // 框位引導同一個問題須連續穩定幾幀才算數，避免動作快速移動時單幀關鍵點掉點造成誤報/誤觸語音。
            val currentFramingIssue = when {
                stuck -> FramingIssue.STUCK
                atRest || !isReady -> evaluateFraming(frame, selectedExercise)
                else -> FramingIssue.OK
            }
            if (currentFramingIssue == framingIssueStreakValue) {
                framingIssueStreakCount++
            } else {
                framingIssueStreakValue = currentFramingIssue
                framingIssueStreakCount = 1
            }
            if (framingIssueStreakCount >= Config.FRAMING_STABLE_FRAMES) {
                framingIssue = currentFramingIssue
            }

            if (frame == null || !isReady) {
                // 被品質檢查擋下的幀也要記錄。只記通過的幀的話，研究模式 CSV 完全看不出
                // 是哪個關鍵點、在什麼數值上把整幀擋掉 —— 而「為什麼不計次」的答案
                // 往往就在被擋掉的那些幀裡。
                frame?.let { rejected ->
                    frameLogger?.logFrame(
                        rawByType = rejected.keyPoints.associateBy { it.type },
                        emaByType = emptyMap(),
                        state = flowStep.name,
                        exerciseType = selectedExercise.name,
                        qualityOk = false,
                        framingIssue = currentFramingIssue.name
                    )
                }
                return@PoseAnalyzer
            }

            val smoothedByType = emaSmoother.smooth(frame.keyPoints).associateBy { it.type }
            // 給站姿／倒數累加器用的「只有可信點」版本。
            //
            // ⚠️ `smoothedByType` **沒有**信心值過濾 —— 品質檢查擋的是整幀，
            // 判據只有 `requiredPoints`，其餘關鍵點 ML Kit 照樣回傳（偵測到人就全給，
            // 不管看不看得到）。累加器會讀 `requiredPoints` 以外的點：
            // 高抬腿與踮腳尖都要用踝算腿長比例尺，卻刻意不宣告踝
            // （抬起那腳的踝會掉信心值，宣告了會讓整幀被丟掉）。
            //
            // 那些點若信心值很低，`points[type] ?: return false` 攔不住它 ——
            // ML Kit 不會把點省略掉，所以「累加器自己會跳過缺點的幀」這個說法
            // 對低信心的點根本不成立，會把亂猜的座標平均進基準。
            // 這與 `evaluateFraming` 當初沒過濾信心值是同一個錯（那次害整場
            // 一直念「舉到肩膀就好」）。過濾後 `progress()` 拿不到點會回 null、該幀跳過，
            // 對五個已驗證的動作沒有行為改變（它們讀的點都在 `requiredPoints` 裡、
            // 必定已達門檻）。
            val confidentByType = smoothedByType.filterValues {
                it.inFrameLikelihood >= Config.CONFIDENCE_THRESHOLD
            }
            var stateForLog = flowStep.name

            when (flowStep) {
                // 倒數期間與結束後都不餵狀態機：倒數時使用者可能還在從校正的最後一下站起來，
                // 結束後畫面停在摘要，兩者都不該再計次。
                FlowStep.SELECT_EXERCISE, FlowStep.FINISHED -> Unit

                FlowStep.READY_COUNTDOWN -> {
                    // 一樣不餵狀態機，但需要重新取基準的動作在這裡累加量測。
                    // 不是所有動作都做：其餘五個已實機驗證，不動它們的基準來源。
                    if (needsCountdownRebaseline(repSignals)) {
                        val calibrator = countdownCalibrator
                            ?: standCalibratorFor(selectedExercise)
                                ?.also { countdownCalibrator = it }
                            ?: return@PoseAnalyzer
                        calibrator.accumulate(confidentByType)
                    }
                }

                FlowStep.STAND_HOLD -> {
                    // 尚未實作偵測的動作沒有累加器（UI 已標灰不可點，這裡只是防線）。
                    val calibrator = standCalibrator
                        ?: standCalibratorFor(selectedExercise)?.also { standCalibrator = it }
                        ?: return@PoseAnalyzer
                    if (calibrator.accumulate(confidentByType)) {
                        val startMs = standHoldStartMs ?: System.currentTimeMillis().also { standHoldStartMs = it }
                        standHoldElapsedMs = System.currentTimeMillis() - startMs
                        if (standHoldElapsedMs >= Config.STAND_HOLD_DURATION_MS) {
                            // 重試次數用完就放寬解剖學合理性檢查，照收當下的量測。
                            val strict = standCalibrationRetryCount < Config.CALIBRATION_MAX_RETRIES
                            val signals = calibrator.build(strict = strict)
                            if (signals.isEmpty()) {
                                // 站姿量測無效，例如舉手動作在校正時就把手舉著，
                                // 手腕沒有低於肩、判準會是 0 或負數。必須重來並說明原因 ——
                                // 不說的話使用者會卡在「倒數結束了卻什麼都沒發生」的畫面。
                                Log.w(
                                    TAG,
                                    "Stand calibration rejected for $selectedExercise " +
                                        "(retry=$standCalibrationRetryCount strict=$strict)"
                                )
                                standCalibrator = null
                                standHoldStartMs = null
                                standHoldElapsedMs = 0L
                                standCalibrationRetryCount += 1
                                standCalibrationWarning = calibrationHintFor(selectedExercise)
                            } else {
                                repSignals = signals
                                standCalibrationWarning = null
                                if (signals.first().target == null) {
                                    // 深蹲家族：分母是個人化的 Duser，還要再做兩下基準動作。
                                    // 深蹲家族只會有一個訊號。
                                    calibrationStateMachine = SquatStateMachine(signals.first())
                                    flowStep = FlowStep.SQUAT_CALIBRATION
                                } else {
                                    // 其餘動作的判準是固定解剖學地標（手腕舉到肩高、膝抬到髖高…），
                                    // 站姿校正本身就取得了分母，不需要兩下基準動作 ——
                                    // 也不該要求，對手臂動作而言「兩下基準深蹲」毫無意義。
                                    trainingMachines = signals.map { SquatStateMachine(it) }
                                    repLedger.clear()
                                    flowStep = FlowStep.READY_COUNTDOWN
                                }
                            }
                        }
                    }
                }

                FlowStep.SQUAT_CALIBRATION -> {
                    val sm = calibrationStateMachine ?: return@PoseAnalyzer
                    val newState = sm.update(smoothedByType)
                    calibrationSquatState = newState
                    stateForLog = "CALIBRATION_${newState.name}"
                    if (newState == SquatState.BOTTOM) {
                        sm.lastPeakProgress?.let { depth ->
                            calibrationDepths = calibrationDepths + depth
                        }
                    }
                    if (newState == SquatState.STAND && calibrationDepths.size >= Config.CALIBRATION_SQUAT_REPS) {
                        val depths = calibrationDepths.takeLast(Config.CALIBRATION_SQUAT_REPS)
                        val mean = depths.average().toFloat()
                        // 全距除以平均：兩下差太多代表其中一下是試探性的淺蹲，取平均當 Duser 不可信。
                        val spread = if (mean > 0f) (depths.max() - depths.min()) / mean else 0f
                        val signal = repSignals.firstOrNull()
                        if (spread > Config.CALIBRATION_MAX_DEPTH_SPREAD &&
                            calibrationRetryCount < Config.CALIBRATION_MAX_RETRIES &&
                            signal != null
                        ) {
                            Log.w(TAG, "Calibration rejected: depths=$depths spread=$spread")
                            calibrationRetryCount += 1
                            calibrationDepths = emptyList()
                            calibrationSquatState = SquatState.STAND
                            calibrationStateMachine = SquatStateMachine(signal)
                            calibrationWarning = "兩次深度差太多，請重做"
                        } else if (signal != null) {
                            // 重試次數用完仍不一致就照收，避免使用者卡在校正出不去；
                            // Duser 會寫進每筆紀錄的 CSV，事後分析看得出這場校正品質不佳。
                            duser = mean
                            calibrationWarning = null
                            trainingMachines = listOf(SquatStateMachine(signal))
                            // 格子是以狀態機物件為鍵的，重建狀態機後舊鍵永遠不會再被比對到。
                            repLedger.clear()
                            // 先進倒數而不是直接開始訓練，讓使用者知道從哪一下開始算數。
                            flowStep = FlowStep.READY_COUNTDOWN
                        }
                    }
                }

                FlowStep.TRAINING -> {
                    val machines = trainingMachines
                    if (machines.isEmpty()) return@PoseAnalyzer
                    // 推進前先記下每台各自的次數，才能知道是**哪一台**完成了一下。
                    // 取總和會分不出來，於是無法把紀錄配對到正確的那一台。
                    val repCountsBefore = machines.map { it.repCount }
                    // 每台狀態機各自推進。高抬腿是左右腳各一台，彼此不互相影響 ——
                    // 這正是「一下 = 單腳抬一次」能正確計數的原因。
                    // 同一幀可能有兩台同時 BOTTOM，所以收集**全部**而不是只留最後一台。
                    val bottomedNow = mutableListOf<SquatStateMachine>()
                    machines.forEach { machine ->
                        if (machine.update(smoothedByType) == SquatState.BOTTOM) {
                            bottomedNow += machine
                        }
                    }
                    repCount = machines.sumOf { it.repCount }
                    stateForLog = machines.joinToString("/") { it.state.name }
                    bottomedNow.forEach { triggered ->
                        val dNow = triggered.lastPeakProgress
                        // 深蹲家族用校正所得的 Duser，其餘動作用觸發那台狀態機自己的判準。
                        val dUser = duser ?: triggered.signal.target
                        // 膝內夾只對雙腳站地的下肢動作有意義。不擋的話，高抬腿（一腳離地）
                        // 與手臂動作會寫入看似合理卻毫無意義的數值，污染 M5 的驗證集。
                        val judgeValgus = judgesKneeValgus(selectedExercise)
                        val kneeValgus = if (judgeValgus) detectKneeValgus(smoothedByType) else null
                        val valgusRatio = if (judgeValgus) kneeValgusRatio(smoothedByType) else null
                        kneeValgusFlag = kneeValgus == true
                        Log.d(TAG, "kneeValgusRatio=$valgusRatio kneeValgus=$kneeValgus")
                        if (dNow != null && dUser != null && dUser > 0f) {
                            val p = dNow / dUser
                            val feedback = evaluateDepthFeedback(p, trainingMode)
                            depthFeedback = feedback
                            // dNow / dUser / valgusRatio 是門檻判定前的原始值，一併留存供 M5 重新掃描門檻。
                            repLedger.hold(triggered, SquatRepRecord(
                                timestamp = System.currentTimeMillis(),
                                depthRatio = p,
                                kneeValgus = kneeValgus == true,
                                feedbackColor = feedback,
                                mode = trainingMode,
                                exerciseType = selectedExercise,
                                dNow = dNow,
                                duser = dUser,
                                kneeValgusRatio = valgusRatio
                            ))
                        }
                    }
                    // 計次在 UP → STAND 那一刻才 +1，此時才算這一下真正完成，寫入該次紀錄。
                    val finished = repLedger.harvest(machines, repCountsBefore)
                    val currentRepCount = machines.sumOf { it.repCount }
                    if (finished.isNotEmpty()) {
                        textToSpeech.value?.speak(currentRepCount.toString(), TextToSpeech.QUEUE_ADD, null, null)
                        sessionRecords = sessionRecords + finished
                        coroutineScope.launch(Dispatchers.IO) {
                            finished.forEach { database.squatRepDao().insert(it) }
                        }
                    }
                    Log.d(TAG, "state=$stateForLog count=$currentRepCount")
                }
            }

            // 研究模式：每一幀（品質通過後）都記錄原始座標、EMA 平滑座標與目前狀態，供後續匯出 CSV 分析。
            frameLogger?.logFrame(
                rawByType = frame.keyPoints.associateBy { it.type },
                emaByType = smoothedByType,
                state = stateForLog,
                exerciseType = selectedExercise.name,
                qualityOk = true,
                framingIssue = currentFramingIssue.name
            )
        }

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = pv.surfaceProvider
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, analyzer) }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            poseFrame = null
            cameraProviderFuture.get().unbindAll()
            analyzer.close()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                    previewView = this
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { previewViewSize = it }
        )

        // 骨架線條與關鍵點一律顯示，方便使用者自行確認有沒有站在鏡頭前、姿勢有沒有被偵測到；
        // 信心值數字（PoseConfidenceList、下面的「已偵測 X/6」文字）才是研究用的除錯資訊，維持只在除錯模式顯示。
        poseFrame?.let { frame ->
            PoseOverlay(
                poseFrame = frame,
                viewSize = previewViewSize,
                modifier = Modifier.fillMaxSize()
            )
        }

        val currentFrame = poseFrame
        if (debugMode) {
            Text(
                text = if (currentFrame == null) {
                    "偵測不到關鍵點"
                } else {
                    "已偵測 ${currentFrame.keyPoints.size}/${KeyPointType.entries.size} 個關鍵點"
                },
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // 訓練中的右上角只留停止鍵。訓練歷程按鈕已移除 —— 結束摘要本來就會顯示
        // 同樣的次數/達標比例/膝內夾比例，訓練途中多一個按鈕只是多一個干擾。
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (flowStep == FlowStep.TRAINING) {
                Text(
                    text = "停止",
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color(0xFFD50000), RoundedCornerShape(12.dp))
                        .clickable { stopTraining() }
                        .padding(horizontal = 28.dp, vertical = 14.dp)
                )
            }
            frameLogger?.let { _ ->
                Text(
                    text = "研究紀錄中",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (flowStep) {
                FlowStep.TRAINING -> {
                    Text(
                        text = selectedExercise.label,
                        color = Color.White,
                        fontSize = 20.sp,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                    // 只顯示次數，不顯示狀態機的 STAND/DOWN/BOTTOM/UP ——
                    // 那是開發用的代號，對使用者沒有意義，而且會把真正要看的數字擠小。
                    // 使用者站在兩公尺外，這個數字是他唯一需要遠距離讀取的資訊。
                    Text(
                        text = repCount.toString(),
                        color = Color.White,
                        fontSize = 96.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 36.dp, vertical = 4.dp)
                    )
                    if (kneeValgusFlag) {
                        Text(
                            text = "膝蓋往外",
                            color = Color.White,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color(0xFFD50000), RoundedCornerShape(12.dp))
                                .padding(horizontal = 24.dp, vertical = 10.dp)
                        )
                    }
                }

                FlowStep.SQUAT_CALIBRATION -> {
                    Text(
                        text = "校正 ${calibrationDepths.size} / ${Config.CALIBRATION_SQUAT_REPS}",
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                    calibrationWarning?.let { warning ->
                        Text(
                            text = warning,
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .background(Color(0xFFFF6D00).copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                else -> Unit
            }
        }

        // 結束摘要停在畫面上時，使用者通常已經走向手機、不再站在鏡頭前，
        // 這時候的框位警告是必然的假警報，不該再顯示或發聲。
        val showFramingIssue = framingIssue != FramingIssue.OK && flowStep != FlowStep.FINISHED

        // framingIssue 已在 analyzer 內做連續幀確認，這裡只有在真的穩定改變時才會觸發，不會每幀都重複念。
        LaunchedEffect(framingIssue, showFramingIssue) {
            if (showFramingIssue) {
                textToSpeech.value?.speak(framingIssue.message, TextToSpeech.QUEUE_ADD, null, null)
            }
        }
        // 放在畫面下方：StandHoldOverlay、DepthFeedbackBanner、SQUAT_CALIBRATION 的提示
        // 都是用 Alignment.Center，這裡改置中反而會互相蓋住；頂部又是訓練歷程/除錯模式的常駐 HUD。
        // 下方是唯一不會跟其他流程專屬疊圖衝突的位置。
        if (showFramingIssue) {
            Text(
                text = framingIssue.message,
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 32.dp, vertical = 48.dp)
                    .background(Color(0xFFFF6D00).copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        if (debugMode) {
            PoseConfidenceList(
                poseFrame = currentFrame,
                required = selectedExercise.requiredPoints,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )
        }

        when (flowStep) {
            FlowStep.SELECT_EXERCISE -> ExercisePicker(
                onSelect = { exercise ->
                    selectedExercise = exercise
                    flowStep = FlowStep.STAND_HOLD
                },
                onShowStats = {
                    loadStatsFor(DateBuckets.startOfDay(System.currentTimeMillis()))
                    showStats = true
                },
                onExportAll = exportAllHistory,
                // 研究模式的開關留在這裡而不是訓練畫面：它控制的是 M5 要用的逐幀 CSV，
                // 刪掉等於拿掉論文參數校準的資料來源；但長輩訓練時不該看到它。
                researchMode = debugMode,
                onToggleResearchMode = { debugMode = !debugMode }
            )

            FlowStep.STAND_HOLD -> StandHoldOverlay(
                exercise = selectedExercise,
                remainingSeconds = ((Config.STAND_HOLD_DURATION_MS - standHoldElapsedMs) / 1000L + 1)
                    .coerceIn(0, Config.STAND_HOLD_DURATION_MS / 1000L + 1),
                warning = standCalibrationWarning
            )

            FlowStep.READY_COUNTDOWN -> readyCountdownText?.let { ReadyCountdownOverlay(it) }

            FlowStep.TRAINING -> {
                depthFeedback?.let { feedback ->
                    DepthFeedbackBanner(feedback, selectedExercise.feedback.of(feedback))
                }
            }

            FlowStep.FINISHED -> SessionSummaryOverlay(
                records = sessionRecords,
                hasExport = exportFiles.isNotEmpty(),
                frameCount = exportFrameCount,
                debugMode = debugMode,
                onShare = { SessionExporter.share(context, exportFiles) },
                onExportAll = exportAllHistory,
                onRestart = {
                    // 回到選模式重跑一輪：站姿基準與 Duser 都要重新校正，
                    // 因為手機位置/使用者站位很可能已經移動過了。
                    sessionRecords = emptyList()
                    exportFiles = emptyList()
                    exportFrameCount = 0
                    repCount = 0
                    repLedger.clear()
                    trainingMachines = emptyList()
                    calibrationStateMachine = null
                    calibrationDepths = emptyList()
                    calibrationSquatState = SquatState.STAND
                    calibrationRetryCount = 0
                    calibrationWarning = null
                    standCalibrator = null
                    countdownCalibrator = null
                    repSignals = emptyList()
                    standCalibrationWarning = null
                    standCalibrationRetryCount = 0
                    duser = null
                    standHoldStartMs = null
                    standHoldElapsedMs = 0L
                    flowStep = FlowStep.SELECT_EXERCISE
                }
            )

            FlowStep.SQUAT_CALIBRATION -> Unit
        }

        if (showStats) {
            TrainingStatsOverlay(
                counts = statsCounts,
                isToday = statsDayStart >= DateBuckets.startOfDay(System.currentTimeMillis()),
                onPreviousDay = { loadStatsFor(DateBuckets.addDays(statsDayStart, -1)) },
                onNextDay = { loadStatsFor(DateBuckets.addDays(statsDayStart, 1)) },
                onClose = { showStats = false },
                modifier = Modifier.fillMaxSize()
            )
        }

    }
}

@Composable
private fun StandHoldOverlay(
    exercise: ExerciseType,
    remainingSeconds: Long,
    warning: String? = null
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = exercise.label,
                color = Color(0xFF7FE3D4),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            // **這一刻唯一重要的指示是「現在身體要擺成什麼樣」**，所以排在最前面、
            // 字級最大。這 3 秒量到的數字就是整場訓練的分母。
            //
            // 原本最醒目的是 exercise.guidance（例如「雙臂向兩側打開擴胸，再向前推掌」），
            // 而「請站直不動」對手臂一個字都沒說 —— 使用者讀著動作說明把手擺開，
            // 系統同時在量他「自然下垂」的手腕位置。實測擴胸的校正重現性
            // 因此比雙臂高舉差 8 倍（σ 0.142 vs 0.018）。
            Text(
                text = calibrationHintFor(exercise),
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            // 動作說明降級到校正指示之後：它要回答的是「等一下怎麼做」，
            // 不是「現在怎麼站」，而現在只有後者會影響量到的基準。
            Text(
                text = exercise.guidance,
                color = Color(0xFFBDBDBD),
                fontSize = 17.sp,
                textAlign = TextAlign.Center
            )
            exercise.safetyNote?.let { note ->
                Text(
                    text = note,
                    color = Color(0xFFFFCC80),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                text = remainingSeconds.coerceAtLeast(0).toString(),
                color = Color.White,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold
            )
            // 站姿量測失敗時必須說出原因並重新倒數，否則畫面會停在
            // 「數到 0 卻什麼都沒發生」，使用者只會以為程式壞了。
            warning?.let { text ->
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(Color(0xFFFF6D00).copy(alpha = 0.9f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * 站姿校正量測失敗時要說的話。
 *
 * 不能統一寫「請雙手自然下垂」—— 那只對手臂動作有意義，對原地高抬腿是錯的指示。
 * 這跟當初把「蹲太淺了」念給舉手聽是同一類錯誤。
 */
private fun calibrationHintFor(exercise: ExerciseType): String = when (exercise) {
    ExerciseType.ARM_RAISE, ExerciseType.CHEST_EXPANSION -> "請雙手自然下垂"
    ExerciseType.HIGH_KNEES -> "請雙腳站地站直"
    ExerciseType.SQUAT, ExerciseType.CHAIR_SQUAT, ExerciseType.HEEL_RAISE -> "請站直，全身入鏡"
}

/**
 * 校正完成後的「準備 3 2 1 開始」倒數。
 *
 * 使用者站在離手機約 2 公尺外，所以字級刻意開到很大（數字 180sp），並鋪一層半透明黑底
 * 讓文字在任何背景/光線下都讀得到；語音由呼叫端的 LaunchedEffect 同步念出。
 */
@Composable
private fun ReadyCountdownOverlay(text: String) {
    // 純數字用最大字級，「準備」「開始！」是多個字，太大會在窄螢幕上被切掉。
    val fontSize = if (text.length <= 1) 180.sp else 88.sp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 16.dp)
        )
    }
}

/**
 * 結束摘要上那一行「這次到底錄到什麼」。
 *
 * 研究模式的逐幀 CSV 是 M5 要拿來跟人工標註比對的唯一資料來源，
 * 錄沒錄到不該等到檔案傳到電腦、跑了分析腳本才發現。
 */
private fun frameLogStatus(frameCount: Int, debugMode: Boolean): String = when {
    frameCount > 0 -> "含逐幀資料 $frameCount 幀（兩個檔案都要傳）"
    debugMode -> "⚠️ 研究模式開著，但這場一幀都沒錄到"
    else -> "⚠️ 只有每下紀錄，沒有逐幀資料（研究模式沒開）"
}

/**
 * 按下「停止」後的結束摘要：本次統計 + 分享研究資料。
 *
 * 分享一律走系統分享選單，由使用者自己挑收件者（見 [SessionExporter] 的說明），
 * App 不會自動把資料傳給任何人。
 */
@Composable
private fun SessionSummaryOverlay(
    records: List<SquatRepRecord>,
    hasExport: Boolean,
    frameCount: Int,
    debugMode: Boolean,
    onShare: () -> Unit,
    onExportAll: () -> Unit,
    onRestart: () -> Unit
) {
    val total = records.size
    val greenCount = records.count { it.feedbackColor == DepthFeedback.GREEN }
    val valgusCount = records.count { it.kneeValgus }
    val greenRatio = if (total > 0) greenCount * 100 / total else 0
    val valgusRatio = if (total > 0) valgusCount * 100 / total else 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .background(Color(0xFF212121), RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "訓練結束",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Text(text = "完成次數：$total", color = Color.White, fontSize = 18.sp)
            Text(text = "深度達標比例：$greenRatio%（$greenCount/$total）", color = Color.White, fontSize = 18.sp)
            Text(text = "膝內夾比例：$valgusRatio%（$valgusCount/$total）", color = Color.White, fontSize = 18.sp)

            if (hasExport) {
                Text(
                    text = "分享研究資料（CSV）",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2962FF), RoundedCornerShape(12.dp))
                        .clickable { onShare() }
                        .padding(vertical = 14.dp)
                )
                Text(
                    // 分享裡有幾個檔、逐幀錄到幾幀，在**按下分享之前**就要看得到。
                    // 之前只寫「分享研究資料（CSV）」，結果傳出去才發現逐幀檔根本沒產生。
                    text = frameLogStatus(frameCount, debugMode),
                    color = if (frameCount > 0) Color(0xFF69F0AE) else Color(0xFFFFAB40),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "會開啟系統分享選單，由你自己選擇要傳給誰。\nApp 不會自動上傳或寄出任何資料。",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "本次沒有完成任何一下，也沒有逐幀資料，沒有可匯出的檔案。\n"
                        + "要錄研究用的逐幀 CSV，請回選擇動作畫面打開「研究模式」。",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            Text(
                text = "匯出全部歷史紀錄",
                color = Color.White,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF00695C), RoundedCornerShape(12.dp))
                    .clickable { onExportAll() }
                    .padding(vertical = 12.dp)
            )
            Text(
                text = "包含之前每一組的紀錄，不只這一組",
                color = Color.Gray,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )

            Text(
                text = "重新開始",
                color = Color.White,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF424242), RoundedCornerShape(12.dp))
                    .clickable { onRestart() }
                    .padding(vertical = 14.dp)
            )
        }
    }
}

@Composable
private fun DepthFeedbackBanner(feedback: DepthFeedback, message: String) {
    val color = when (feedback) {
        DepthFeedback.GREEN -> Color(0xFF00C853)
        DepthFeedback.YELLOW -> Color(0xFFFFAB00)
        DepthFeedback.RED -> Color(0xFFD50000)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color.copy(alpha = 0.35f))
    ) {
        Text(
            text = message,
            color = Color.White,
            // 這是使用者站在兩公尺外最需要立刻讀到的一句話，字級對齊倒數的量級
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 16.dp)
                .background(color.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
                .padding(horizontal = 32.dp, vertical = 24.dp)
        )
    }
}

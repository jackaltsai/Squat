package com.heartchen.squat.camera

import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import android.util.Log
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
import com.heartchen.squat.pose.KeyPoint
import com.heartchen.squat.pose.KeyPointType
import com.heartchen.squat.pose.PoseAnalyzer
import com.heartchen.squat.pose.PoseConfidenceList
import com.heartchen.squat.pose.PoseFrame
import com.heartchen.squat.pose.PoseOverlay
import com.heartchen.squat.pose.evaluateFraming
import com.heartchen.squat.pose.passesQualityCheck
import com.heartchen.squat.squat.DepthFeedback
import com.heartchen.squat.squat.SquatState
import com.heartchen.squat.squat.SquatStateMachine
import com.heartchen.squat.squat.TrainingMode
import com.heartchen.squat.squat.detectKneeValgus
import com.heartchen.squat.squat.evaluateDepthFeedback
import com.heartchen.squat.squat.kneeValgusRatio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

private const val TAG = "PoseDetectionScreen"
private const val KNEE_VALGUS_MESSAGE = "膝蓋往外一點"

/**
 * M3 流程：選模式 → 站姿校正（3 秒）→ 基準深蹲校正（2 次）→ 準備倒數（3、2、1）→ 正式訓練 → 結束。
 *
 * READY_COUNTDOWN 是為了把「校正的兩下」跟「正式計次的第一下」明確切開：
 * 沒有倒數的話，使用者做完第二下校正深蹲會直接接上訓練，不知道什麼時候開始算數。
 */
private enum class FlowStep { SELECT_MODE, STAND_HOLD, SQUAT_CALIBRATION, READY_COUNTDOWN, TRAINING, FINISHED }

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

    var flowStep by remember { mutableStateOf(FlowStep.SELECT_MODE) }
    var trainingMode by remember { mutableStateOf<TrainingMode?>(null) }

    var standHoldStartMs by remember { mutableStateOf<Long?>(null) }
    var standHoldElapsedMs by remember { mutableStateOf(0L) }
    var standHoldHipSum by remember { mutableStateOf(0f) }
    var standHoldAnkleSum by remember { mutableStateOf(0f) }
    var standHoldSampleCount by remember { mutableStateOf(0) }

    var calibratedBaselineY by remember { mutableStateOf<Float?>(null) }
    var calibratedScale by remember { mutableStateOf<Float?>(null) }
    var calibrationStateMachine by remember { mutableStateOf<SquatStateMachine?>(null) }
    var calibrationSquatState by remember { mutableStateOf(SquatState.STAND) }
    var calibrationDepths by remember { mutableStateOf<List<Float>>(emptyList()) }
    // 校正品質檢查：兩下校正深蹲深度差太多就要求重做，避免試探性的淺蹲把 Duser 拉低。
    var calibrationRetryCount by remember { mutableIntStateOf(0) }
    var calibrationWarning by remember { mutableStateOf<String?>(null) }

    var trainingStateMachine by remember { mutableStateOf<SquatStateMachine?>(null) }
    var duser by remember { mutableStateOf<Float?>(null) }
    var squatState by remember { mutableStateOf(SquatState.STAND) }
    var repCount by remember { mutableIntStateOf(0) }
    var depthFeedback by remember { mutableStateOf<DepthFeedback?>(null) }
    var kneeValgusFlag by remember { mutableStateOf(false) }
    var pendingRecord by remember { mutableStateOf<SquatRepRecord?>(null) }
    var sessionRecords by remember { mutableStateOf<List<SquatRepRecord>>(emptyList()) }
    var showHistory by remember { mutableStateOf(false) }
    // 準備倒數目前要顯示的大字：「準備」→「3」→「2」→「1」→「開始！」，null 表示不在倒數。
    var readyCountdownText by remember { mutableStateOf<String?>(null) }
    var exportFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    // 骨架疊圖一律顯示（見下方 PoseOverlay），這個開關只控制信心值數字列表跟
    // M4 研究模式的每幀 CSV 紀錄（原始座標 + EMA 平滑座標 + 狀態機狀態），一般使用者不需要開啟。
    var debugMode by remember { mutableStateOf(false) }
    var frameLogger by remember { mutableStateOf<FrameLogger?>(null) }
    var framingIssue by remember { mutableStateOf(FramingIssue.OK) }

    val database = remember { SquatDatabase.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()

    // 除錯模式開啟時才建立 CSV 紀錄檔；關閉或離開畫面時 flush + 關閉檔案，避免資料遺失。
    DisposableEffect(debugMode) {
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
            textToSpeech.value?.speak(feedback.message, TextToSpeech.QUEUE_ADD, null, null)
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

    // 受測者可能離鏡頭較遠看不清楚文字，流程轉換（進入站姿校正/深蹲校正）也用語音提示一次。
    LaunchedEffect(flowStep) {
        val message = when (flowStep) {
            FlowStep.STAND_HOLD -> "請站直不動，準備校正站姿基準"
            FlowStep.SQUAT_CALIBRATION -> "請完成兩次深蹲，校正基準深度"
            else -> null
        }
        message?.let { textToSpeech.value?.speak(it, TextToSpeech.QUEUE_ADD, null, null) }
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
        flowStep = FlowStep.TRAINING
    }

    // 停止訓練：關掉逐幀 CSV（flush 到檔案）、把本次每下紀錄另外寫成一份 session CSV，
    // 然後停在結束摘要畫面讓使用者決定要不要分享。
    // 一併把 debugMode 關掉，維持「除錯模式開 ⇔ 有 frameLogger」的一致性；
    // 下一輪訓練要記錄逐幀資料的話再開一次即可。
    val stopTraining: () -> Unit = {
        frameLogger?.close()
        val frameCsv = frameLogger?.filePath?.let { File(it) }
        frameLogger = null
        debugMode = false
        val sessionCsv = SessionExporter.writeSessionCsv(context, sessionRecords)
        exportFiles = listOfNotNull(sessionCsv, frameCsv?.takeIf { it.exists() && it.length() > 0 })
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
        val analyzer = PoseAnalyzer(isFrontCamera = isFrontCamera) { frame ->
            poseFrame = frame
            val isReady = frame != null && frame.passesQualityCheck()
            if (isReady && !wasReady) {
                toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            }
            wasReady = isReady

            // 框位引導同一個問題須連續穩定幾幀才算數，避免深蹲快速移動時單幀關鍵點掉點造成誤報/誤觸語音。
            val currentFramingIssue = evaluateFraming(frame)
            if (currentFramingIssue == framingIssueStreakValue) {
                framingIssueStreakCount++
            } else {
                framingIssueStreakValue = currentFramingIssue
                framingIssueStreakCount = 1
            }
            if (framingIssueStreakCount >= Config.FRAMING_STABLE_FRAMES) {
                framingIssue = currentFramingIssue
            }

            if (frame == null || !isReady) return@PoseAnalyzer

            val smoothedByType = emaSmoother.smooth(frame.keyPoints).associateBy { it.type }
            var stateForLog = flowStep.name

            when (flowStep) {
                // 倒數期間與結束後都不餵狀態機：倒數時使用者可能還在從校正的最後一下站起來，
                // 結束後畫面停在摘要，兩者都不該再計次。
                FlowStep.SELECT_MODE, FlowStep.READY_COUNTDOWN, FlowStep.FINISHED -> Unit

                FlowStep.STAND_HOLD -> {
                    val hipY = averageY(smoothedByType, KeyPointType.LEFT_HIP, KeyPointType.RIGHT_HIP)
                    val ankleY = averageY(smoothedByType, KeyPointType.LEFT_ANKLE, KeyPointType.RIGHT_ANKLE)
                    if (hipY != null && ankleY != null) {
                        val startMs = standHoldStartMs ?: System.currentTimeMillis().also { standHoldStartMs = it }
                        standHoldHipSum += hipY
                        standHoldAnkleSum += ankleY
                        standHoldSampleCount += 1
                        standHoldElapsedMs = System.currentTimeMillis() - startMs
                        if (standHoldElapsedMs >= Config.STAND_HOLD_DURATION_MS && standHoldSampleCount > 0) {
                            val baselineY = standHoldHipSum / standHoldSampleCount
                            val scale = standHoldAnkleSum / standHoldSampleCount - baselineY
                            calibratedBaselineY = baselineY
                            calibratedScale = scale
                            calibrationStateMachine = SquatStateMachine(baselineY, scale)
                            flowStep = FlowStep.SQUAT_CALIBRATION
                        }
                    }
                }

                FlowStep.SQUAT_CALIBRATION -> {
                    val sm = calibrationStateMachine ?: return@PoseAnalyzer
                    val newState = sm.update(smoothedByType)
                    calibrationSquatState = newState
                    stateForLog = "CALIBRATION_${newState.name}"
                    if (newState == SquatState.BOTTOM) {
                        sm.lastBottomDepthRatio?.let { depth ->
                            calibrationDepths = calibrationDepths + depth
                        }
                    }
                    if (newState == SquatState.STAND && calibrationDepths.size >= Config.CALIBRATION_SQUAT_REPS) {
                        val depths = calibrationDepths.takeLast(Config.CALIBRATION_SQUAT_REPS)
                        val mean = depths.average().toFloat()
                        // 全距除以平均：兩下差太多代表其中一下是試探性的淺蹲，取平均當 Duser 不可信。
                        val spread = if (mean > 0f) (depths.max() - depths.min()) / mean else 0f
                        val baselineY = calibratedBaselineY
                        val scale = calibratedScale
                        if (spread > Config.CALIBRATION_MAX_DEPTH_SPREAD &&
                            calibrationRetryCount < Config.CALIBRATION_MAX_RETRIES &&
                            baselineY != null && scale != null
                        ) {
                            Log.w(TAG, "Calibration rejected: depths=$depths spread=$spread")
                            calibrationRetryCount += 1
                            calibrationDepths = emptyList()
                            calibrationSquatState = SquatState.STAND
                            calibrationStateMachine = SquatStateMachine(baselineY, scale)
                            calibrationWarning = "兩次深蹲深度差太多，請重做兩次一樣深的深蹲"
                        } else if (baselineY != null && scale != null) {
                            // 重試次數用完仍不一致就照收，避免使用者卡在校正出不去；
                            // Duser 會寫進每筆紀錄的 CSV，事後分析看得出這場校正品質不佳。
                            duser = mean
                            calibrationWarning = null
                            trainingStateMachine = SquatStateMachine(baselineY, scale)
                            // 先進倒數而不是直接開始訓練，讓使用者知道從哪一下開始算數。
                            flowStep = FlowStep.READY_COUNTDOWN
                        }
                    }
                }

                FlowStep.TRAINING -> {
                    val sm = trainingStateMachine ?: return@PoseAnalyzer
                    val previousRepCount = sm.repCount
                    val newState = sm.update(smoothedByType)
                    squatState = newState
                    repCount = sm.repCount
                    stateForLog = newState.name
                    if (newState == SquatState.BOTTOM) {
                        val dNow = sm.lastBottomDepthRatio
                        val mode = trainingMode
                        val dUser = duser
                        val kneeValgus = detectKneeValgus(smoothedByType)
                        val valgusRatio = kneeValgusRatio(smoothedByType)
                        kneeValgusFlag = kneeValgus == true
                        Log.d(TAG, "kneeValgusRatio=$valgusRatio kneeValgus=$kneeValgus")
                        if (dNow != null && mode != null && dUser != null && dUser > 0f) {
                            val p = dNow / dUser
                            val feedback = evaluateDepthFeedback(p, mode)
                            depthFeedback = feedback
                            // dNow / dUser / valgusRatio 是門檻判定前的原始值，一併留存供 M5 重新掃描門檻。
                            pendingRecord = SquatRepRecord(
                                timestamp = System.currentTimeMillis(),
                                depthRatio = p,
                                kneeValgus = kneeValgus == true,
                                feedbackColor = feedback,
                                mode = mode,
                                dNow = dNow,
                                duser = dUser,
                                kneeValgusRatio = valgusRatio
                            )
                        }
                    }
                    // 計次在 UP → STAND 那一刻才 +1，此時才算這一下真正完成，寫入該次紀錄。
                    if (sm.repCount > previousRepCount) {
                        textToSpeech.value?.speak(sm.repCount.toString(), TextToSpeech.QUEUE_ADD, null, null)
                        pendingRecord?.let { record ->
                            sessionRecords = sessionRecords + record
                            coroutineScope.launch(Dispatchers.IO) {
                                database.squatRepDao().insert(record)
                            }
                        }
                        pendingRecord = null
                    }
                    Log.d(TAG, "state=$newState count=${sm.repCount} p=${duser?.let { d -> sm.lastBottomDepthRatio?.div(d) }}")
                }
            }

            // 研究模式：每一幀（品質通過後）都記錄原始座標、EMA 平滑座標與目前狀態，供後續匯出 CSV 分析。
            frameLogger?.logFrame(
                rawByType = frame.keyPoints.associateBy { it.type },
                emaByType = smoothedByType,
                state = stateForLog
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
                    "已偵測 ${currentFrame.keyPoints.size}/6 個關鍵點"
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

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 停止鍵放在這個常駐控制列，是唯一不會跟其他疊圖打架的位置：
            // 畫面正中央被站姿倒數/準備倒數/深度回饋佔用，下方被框位警告佔用。
            if (flowStep == FlowStep.TRAINING) {
                Text(
                    text = "■ 停止",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color(0xFFD50000).copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                        .clickable { stopTraining() }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
                Text(
                    text = "訓練歷程",
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable { showHistory = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                text = if (debugMode) "除錯模式：開" else "除錯模式：關",
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clickable { debugMode = !debugMode }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            frameLogger?.let { logger ->
                Text(
                    text = "研究紀錄中：${File(logger.filePath).name}",
                    color = Color.White,
                    fontSize = 10.sp,
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
                    trainingMode?.let { mode ->
                        Text(
                            text = "模式：${mode.label}",
                            color = Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                    Text(
                        text = "${squatState.label}　次數 $repCount",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                    if (kneeValgusFlag) {
                        Text(
                            text = "膝蓋內夾，往外一點",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(Color(0xFFD50000).copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                FlowStep.SQUAT_CALIBRATION -> {
                    Text(
                        text = "基準深蹲校正 ${calibrationSquatState.label}　${calibrationDepths.size}/${Config.CALIBRATION_SQUAT_REPS}",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                    calibrationWarning?.let { warning ->
                        Text(
                            text = warning,
                            color = Color.White,
                            fontSize = 18.sp,
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
                fontSize = 18.sp,
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
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            )
        }

        when (flowStep) {
            FlowStep.SELECT_MODE -> ModeSelectOverlay { mode ->
                trainingMode = mode
                flowStep = FlowStep.STAND_HOLD
            }

            FlowStep.STAND_HOLD -> StandHoldOverlay(
                remainingSeconds = ((Config.STAND_HOLD_DURATION_MS - standHoldElapsedMs) / 1000L + 1)
                    .coerceIn(0, Config.STAND_HOLD_DURATION_MS / 1000L + 1)
            )

            FlowStep.READY_COUNTDOWN -> readyCountdownText?.let { ReadyCountdownOverlay(it) }

            FlowStep.TRAINING -> {
                depthFeedback?.let { feedback ->
                    DepthFeedbackBanner(feedback)
                }
            }

            FlowStep.FINISHED -> SessionSummaryOverlay(
                records = sessionRecords,
                hasExport = exportFiles.isNotEmpty(),
                onShare = { SessionExporter.share(context, exportFiles) },
                onRestart = {
                    // 回到選模式重跑一輪：站姿基準與 Duser 都要重新校正，
                    // 因為手機位置/使用者站位很可能已經移動過了。
                    sessionRecords = emptyList()
                    exportFiles = emptyList()
                    repCount = 0
                    squatState = SquatState.STAND
                    pendingRecord = null
                    trainingStateMachine = null
                    calibrationStateMachine = null
                    calibrationDepths = emptyList()
                    calibrationSquatState = SquatState.STAND
                    calibrationRetryCount = 0
                    calibrationWarning = null
                    calibratedBaselineY = null
                    calibratedScale = null
                    duser = null
                    standHoldStartMs = null
                    standHoldElapsedMs = 0L
                    standHoldHipSum = 0f
                    standHoldAnkleSum = 0f
                    standHoldSampleCount = 0
                    trainingMode = null
                    flowStep = FlowStep.SELECT_MODE
                }
            )

            FlowStep.SQUAT_CALIBRATION -> Unit
        }

        if (showHistory) {
            TrainingHistoryOverlay(
                records = sessionRecords,
                onClose = { showHistory = false }
            )
        }
    }
}

@Composable
private fun ModeSelectOverlay(onSelect: (TrainingMode) -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "選擇訓練模式",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            TrainingMode.entries.forEach { mode ->
                Text(
                    text = mode.label,
                    color = Color.White,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2962FF), RoundedCornerShape(12.dp))
                        .clickable { onSelect(mode) }
                        .padding(vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun StandHoldOverlay(remainingSeconds: Long) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "請站直不動，準備校正站姿基準",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = remainingSeconds.coerceAtLeast(0).toString(),
                color = Color.White,
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
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
 * 按下「停止」後的結束摘要：本次統計 + 分享研究資料。
 *
 * 分享一律走系統分享選單，由使用者自己挑收件者（見 [SessionExporter] 的說明），
 * App 不會自動把資料傳給任何人。
 */
@Composable
private fun SessionSummaryOverlay(
    records: List<SquatRepRecord>,
    hasExport: Boolean,
    onShare: () -> Unit,
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
                    text = "會開啟系統分享選單，由你自己選擇要傳給誰。\nApp 不會自動上傳或寄出任何資料。",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "本次沒有完成任何一下，沒有可匯出的資料。",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

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
private fun DepthFeedbackBanner(feedback: DepthFeedback) {
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
            text = feedback.message,
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .background(color.copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                .padding(horizontal = 32.dp, vertical = 20.dp)
        )
    }
}

/** M4：訓練歷程頁面，顯示本次訓練（尚未離開此畫面前）的次數、深度達標比例、膝內夾比例。 */
@Composable
private fun TrainingHistoryOverlay(records: List<SquatRepRecord>, onClose: () -> Unit) {
    val total = records.size
    val greenCount = records.count { it.feedbackColor == DepthFeedback.GREEN }
    val valgusCount = records.count { it.kneeValgus }
    val greenRatio = if (total > 0) greenCount * 100 / total else 0
    val valgusRatio = if (total > 0) valgusCount * 100 / total else 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onClose() }
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
                text = "本次訓練歷程",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(text = "次數：$total", color = Color.White, fontSize = 18.sp)
            Text(text = "深度達標比例：$greenRatio%（$greenCount/$total）", color = Color.White, fontSize = 18.sp)
            Text(text = "膝內夾比例：$valgusRatio%（$valgusCount/$total）", color = Color.White, fontSize = 18.sp)
            Text(
                text = "點擊任意處關閉",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private fun averageY(byType: Map<KeyPointType, KeyPoint>, a: KeyPointType, b: KeyPointType): Float? {
    val pa = byType[a] ?: return null
    val pb = byType[b] ?: return null
    return (pa.y + pb.y) / 2f
}

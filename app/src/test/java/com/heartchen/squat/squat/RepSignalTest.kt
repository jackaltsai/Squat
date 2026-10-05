package com.heartchen.squat.squat

import com.heartchen.squat.config.Config
import com.heartchen.squat.pose.KeyPoint
import com.heartchen.squat.pose.KeyPointType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [RepSignal] 抽象層與雙臂高舉訊號的測試（純 JVM）。
 *
 * 這一層存在的理由是：原本整條管線都假設「下肢垂直位移」，
 * `ExerciseType.calibration` 宣告了校正方式卻從未被讀取，
 * 於是手臂動作也會被要求做兩下基準深蹲。
 */
class RepSignalTest {

    // 一組**解剖學上合理**的身體尺寸：肩在 y=400、肩寬 140、手臂長 196（= 1.4 倍肩寬）。
    //
    // ⚠️ 原本手臂長取 300（2.14 倍肩寬），刻意誇張以放大訊號 —— 結果加入
    // `isPlausibleArmToShoulder` 檢查後整組測試掛掉，因為那個身材本身就不符合解剖學
    // （成人約 1.19~1.72）。fixture 失真的代價不只是這次的紅燈：
    // 它讓 target 算出 2.14，而實測四場正常的場次是 1.348~1.409，
    // 所有以它為基礎的斷言都在描述一個不存在的人。
    private val shoulderY = 400f
    private val leftShoulderX = 290f
    private val rightShoulderX = 430f
    private val shoulderWidth = rightShoulderX - leftShoulderX   // 140
    private val restDrop = shoulderWidth * 1.4f                  // 196
    private val restWristY = shoulderY + restDrop                // 596

    private fun upperBody(wristY: Float): Map<KeyPointType, KeyPoint> = mapOf(
        KeyPointType.LEFT_SHOULDER to KeyPoint(KeyPointType.LEFT_SHOULDER, leftShoulderX, shoulderY, 0.9f),
        KeyPointType.RIGHT_SHOULDER to KeyPoint(KeyPointType.RIGHT_SHOULDER, rightShoulderX, shoulderY, 0.9f),
        KeyPointType.LEFT_WRIST to KeyPoint(KeyPointType.LEFT_WRIST, leftShoulderX, wristY, 0.9f),
        KeyPointType.RIGHT_WRIST to KeyPoint(KeyPointType.RIGHT_WRIST, rightShoulderX, wristY, 0.9f),
    )

    private fun armRaiseSignal() = ArmRaiseSignal(restDrop, shoulderWidth)

    /** 手腕抬到「站姿到肩高」的 [fraction] 處。1.0 = 剛好肩高 = 判準。 */
    private fun wristAt(fraction: Float): Float = restWristY - fraction * restDrop

    // ---- 不變式：偵測有沒有實作，必須與「有沒有對應的校正累加器」完全一致 ----

    /**
     * 這條測試鎖住的就是今天連續踩到三次的那個模式：
     * 欄位宣告了、註解寫了、UI 看起來接上了，實際上沒有接。
     * `detectionImplemented` 與 `standCalibratorFor` 一旦不同步，
     * 使用者會點進一個「看起來在偵測、判準卻是錯的」動作。
     */
    @Test
    fun `每個動作的 detectionImplemented 與是否有校正累加器必須一致`() {
        ExerciseType.entries.forEach { exercise ->
            val calibrator = standCalibratorFor(exercise)
            if (exercise.detectionImplemented) {
                assertNotNull("${exercise.name} 已標示實作偵測，卻沒有校正累加器", calibrator)
            } else {
                assertNull("${exercise.name} 尚未實作偵測，卻有校正累加器", calibrator)
            }
        }
    }

    /**
     * 回饋文案一度寫死在 `DepthFeedback` 上，於是雙臂高舉舉不夠高時會顯示、
     * 並用 TTS 對兩公尺外的使用者念出「蹲太淺了」。這條鎖住那個迴歸。
     */
    @Test
    fun `非深蹲動作的回饋文案不可出現蹲這個字`() {
        val squatFamily = setOf(ExerciseType.SQUAT, ExerciseType.CHAIR_SQUAT)
        ExerciseType.entries.filterNot { it in squatFamily }.forEach { exercise ->
            listOf(
                exercise.feedback.green,
                exercise.feedback.yellow,
                exercise.feedback.red,
            ).forEach { message ->
                assertTrue(
                    "${exercise.name} 的回饋文案「$message」用了深蹲的用語",
                    !message.contains("蹲")
                )
            }
        }
    }

    /**
     * 雙臂高舉只宣告肩與腕。手肘從未被任何地方讀取，卻會因為被軀幹遮住而
     * 讓整幀被品質檢查丟掉 —— 2026-10-01 實機舉了很多下只計到 2 下。
     */
    @Test
    fun `動作宣告的關鍵點不可包含沒有人讀取的點`() {
        assertTrue(
            "雙臂高舉不該要求手肘：訊號與框位都沒有讀取它",
            KeyPointType.LEFT_ELBOW !in ExerciseType.ARM_RAISE.requiredPoints
        )
        // 訊號真正會讀的點必須被宣告，否則品質檢查放行了、訊號卻拿不到點
        listOf(
            KeyPointType.LEFT_SHOULDER, KeyPointType.RIGHT_SHOULDER,
            KeyPointType.LEFT_WRIST, KeyPointType.RIGHT_WRIST,
        ).forEach {
            assertTrue("雙臂高舉必須宣告 $it", it in ExerciseType.ARM_RAISE.requiredPoints)
        }
    }

    /**
     * 膝內夾判定只對雙腳站地的下肢動作有意義。
     *
     * 2026-10-02 實機的高抬腿紀錄裡 `kneeValgusRatio` 落在 −0.26 ~ −0.55，
     * 看起來像「量到了而且沒內夾」，實際上一腳離地、公式毫無意義。
     * 不擋就會把垃圾數值寫進資料庫，污染 M5 的門檻掃描驗證集。
     */
    @Test
    fun `膝內夾判定只開給雙腳站地的下肢動作`() {
        // 踮腳尖原本在這張名單上（當時宣告 LOWER_BODY）。改為只宣告髖與腳尖之後
        // 自然被擋掉，而那是對的：雙腳踩地、膝不彎曲，膝內夾在這個動作上不會發生，
        // 記一個算得出來卻無意義的數字只會污染 M5 的驗證集。
        val shouldJudge = setOf(ExerciseType.SQUAT, ExerciseType.CHAIR_SQUAT)
        ExerciseType.entries.forEach { exercise ->
            assertEquals(
                "${exercise.name} 的膝內夾判定開關不對",
                exercise in shouldJudge,
                judgesKneeValgus(exercise)
            )
        }
        // 高抬腿刻意不宣告踝（抬起那腳的踝會掉信心值），所以自然被擋掉
        assertTrue(
            KeyPointType.LEFT_ANKLE !in ExerciseType.HIGH_KNEES.requiredPoints
        )
    }

    /**
     * 上肢動作的判準建立在站姿量到的手臂長上。手沒有完全自然下垂時手臂長被低估，
     * 判準跟著變小、整場 p 偏高、假性達標 —— 實測六場裡有兩場如此
     * （0.962 / 1.069，正常的四場是 1.348~1.409）。
     *
     * 區間刻意從解剖學推導（成人肩峰間距 36~42cm、肩到腕 50~62cm → 1.19~1.72），
     * 不是從那六場配出來的（都是同一人，跨受試者變異未知）。
     */
    @Test
    fun `站姿校正會擋掉解剖學上不合理的手臂長肩寬比`() {
        // 實測正常的四場都該通過
        listOf(1.348f, 1.370f, 1.394f, 1.409f).forEach {
            assertTrue("$it 應視為合理", isPlausibleArmToShoulder(it))
        }
        // 實測偏低的兩場都該被擋
        listOf(0.962f, 1.069f).forEach {
            assertTrue("$it 應被擋掉", !isPlausibleArmToShoulder(it))
        }
    }

    @Test
    fun `手臂長肩寬比不合理時拒絕產生訊號，但放寬後照收`() {
        // 手腕只低於肩 100px、肩寬 140px → 比值 0.71，遠低於下界
        val c = ArmRaiseStandCalibrator()
        repeat(5) { c.accumulate(upperBody(shoulderY + 100f)) }
        assertTrue("嚴格模式應拒絕", c.build().isEmpty())
        assertEquals("放寬後應照收", 1, c.build(strict = false).size)
    }

    @Test
    fun `擴胸的站姿校正也會檢查手臂長肩寬比`() {
        // chest() 的手臂長固定為 ceArmLength=196、肩寬 140 → 比值 1.4，合理
        val good = ChestExpansionStandCalibrator()
        repeat(5) { good.accumulate(chest(ceRestSeparation)) }
        assertEquals(1, good.build().size)
    }

    @Test
    fun `深蹲與高抬腿不受手臂長肩寬比檢查影響`() {
        // 這兩個動作的判準與手臂無關，strict 與否都該照常產生訊號
        val lower = LowerBodyStandCalibrator()
        repeat(5) { lower.accumulate(knees(0f, 0f)) }
        assertEquals(1, lower.build().size)
        assertEquals(1, lower.build(strict = false).size)

        val hk = HighKneesStandCalibrator()
        repeat(5) { hk.accumulate(knees(0f, 0f)) }
        assertEquals(2, hk.build().size)
    }

    @Test
    fun `深蹲家族的 target 為 null，其餘動作為非 null`() {
        // target == null 是「需要兩下基準校正取得 Duser」的唯一判斷依據
        assertNull(SquatSignal(100f, 300f).target)
        assertNotNull(armRaiseSignal().target)
    }

    // ---- 雙臂高舉訊號 ----

    @Test
    fun `站姿時進度為零`() {
        assertEquals(0f, armRaiseSignal().progress(upperBody(restWristY))!!, 1e-4f)
    }

    @Test
    fun `手腕舉到肩高時進度正好等於判準，達成率為一`() {
        val signal = armRaiseSignal()
        val progress = signal.progress(upperBody(shoulderY))!!
        assertEquals(signal.target!!, progress, 1e-4f)
        assertEquals(1f, progress / signal.target!!, 1e-4f)
    }

    @Test
    fun `判準等於站姿手腕落差除以肩寬`() {
        // 196 / 140 = 1.4 個肩寬 —— 量級比深蹲的 0.3 個腿長大一個數量級，
        // 這就是門檻必須取判準比例而非沿用深蹲絕對值的原因
        assertEquals(restDrop / shoulderWidth, armRaiseSignal().target!!, 1e-4f)
        // 而且必須落在解剖學合理區間，否則站姿校正會拒絕這個身材
        assertTrue(
            "測試身材的手臂長/肩寬 = ${armRaiseSignal().target} 不合理",
            isPlausibleArmToShoulder(armRaiseSignal().target!!)
        )
    }

    @Test
    fun `手舉過頭時達成率大於一`() {
        val signal = armRaiseSignal()
        val p = signal.progress(upperBody(300f))!! / signal.target!!
        assertTrue("舉過肩應 p > 1，實際 $p", p > 1f)
    }

    @Test
    fun `缺少手腕關鍵點時回傳 null 而非亂猜`() {
        val onlyShoulders = upperBody(restWristY)
            .filterKeys { it != KeyPointType.LEFT_WRIST && it != KeyPointType.RIGHT_WRIST }
        assertNull(armRaiseSignal().progress(onlyShoulders))
    }

    @Test
    fun `肩寬為零時回傳 null，避免除以零把比值炸成無限大`() {
        assertNull(ArmRaiseSignal(restDrop, 0f).progress(upperBody(restWristY)))
    }

    // ---- 站姿校正累加器 ----

    @Test
    fun `舉手動作的站姿校正取平均並產生可用訊號`() {
        val c = ArmRaiseStandCalibrator()
        // 手臂自然晃動：手腕在 586~606 之間，平均仍是 596
        listOf(586f, 606f, 591f, 601f, 596f).forEach { assertTrue(c.accumulate(upperBody(it))) }
        assertEquals(5, c.sampleCount)
        val signal = c.build().single()
        assertEquals(restDrop / shoulderWidth, signal.target!!, 1e-3f)
    }

    /**
     * 使用者在站姿校正時就把手舉著 —— 手腕沒有低於肩，判準會是 0 或負數，
     * 之後每一下的 p 都會是無意義的數字。必須在這裡擋下來，
     * 而不是讓它產生一個壞訊號、整場訓練的資料全部不可用。
     */
    @Test
    fun `校正時手已舉高則拒絕產生訊號`() {
        val c = ArmRaiseStandCalibrator()
        repeat(5) { c.accumulate(upperBody(shoulderY - 50f)) }   // 手腕高於肩
        assertTrue(c.build().isEmpty())
    }

    @Test
    fun `沒有樣本時拒絕產生訊號`() {
        assertTrue(ArmRaiseStandCalibrator().build().isEmpty())
        assertTrue(LowerBodyStandCalibrator().build().isEmpty())
        assertTrue(ChestExpansionStandCalibrator().build().isEmpty())
        assertTrue(HighKneesStandCalibrator().build().isEmpty())
    }

    @Test
    fun `舉手動作的站姿校正拿不到上肢關鍵點時不計入樣本`() {
        val c = ArmRaiseStandCalibrator()
        assertTrue(!c.accumulate(emptyMap()))
        assertEquals(0, c.sampleCount)
    }

    // ---- 擴胸推掌 ----

    // 解剖學上合理的測試身材：肩寬 140、手臂長 1.4 倍肩寬 = 196、站姿腕距 0.9 倍肩寬 = 126。
    // （上面舉手那組刻意用了手臂長 300 的誇張值來放大訊號，這裡換成真實比例，
    //  因為擴胸的判準直接由「肩寬 + 2×手臂長」算出，比例失真會讓斷言失去意義。）
    private val ceShoulderWidth = 140f
    private val ceArmLength = 196f
    private val ceRestSeparation = 126f

    /** 左右手腕對稱地以 [separation] 的間距放在身前。 */
    private fun chest(separation: Float): Map<KeyPointType, KeyPoint> {
        val cx = 360f
        val wristY = shoulderY + ceArmLength
        return mapOf(
            KeyPointType.LEFT_SHOULDER to
                KeyPoint(KeyPointType.LEFT_SHOULDER, cx - ceShoulderWidth / 2f, shoulderY, 0.9f),
            KeyPointType.RIGHT_SHOULDER to
                KeyPoint(KeyPointType.RIGHT_SHOULDER, cx + ceShoulderWidth / 2f, shoulderY, 0.9f),
            KeyPointType.LEFT_WRIST to
                KeyPoint(KeyPointType.LEFT_WRIST, cx - separation / 2f, wristY, 0.9f),
            KeyPointType.RIGHT_WRIST to
                KeyPoint(KeyPointType.RIGHT_WRIST, cx + separation / 2f, wristY, 0.9f),
        )
    }

    private fun chestSignal() =
        ChestExpansionSignal(ceRestSeparation, ceShoulderWidth, ceArmLength)

    @Test
    fun `擴胸的進度是絕對腕距，站姿時等於站姿腕距除以肩寬`() {
        // 進度刻意**不**扣掉站姿腕距：扣掉的話 p 的分母會含有一個姿勢選擇
        assertEquals(
            ceRestSeparation / ceShoulderWidth,
            chestSignal().progress(chest(ceRestSeparation))!!,
            1e-4f
        )
    }

    @Test
    fun `擴胸判準由肩寬與手臂長算出，不是寫死的距離`() {
        // 幾何最大側展腕距 = 肩寬 + 2×手臂長 = 140 + 392 = 532
        // 判準腕距 = 532 × 0.6 = 319.2　→　target = 319.2 / 140 = 2.28
        val full = ceShoulderWidth + 2f * ceArmLength
        assertEquals(full * 0.6f / ceShoulderWidth, chestSignal().target!!, 1e-4f)
    }

    /**
     * 這條是這次修正的核心：`p = 峰值腕距 / 判準腕距`，判準只含解剖量。
     * 原本進度扣掉站姿腕距，`p = (峰值 − 站姿) / (判準 − 站姿)`，分母含有
     * 「手垂下時離身體多遠」這個姿勢選擇 —— 2026-10-02 實機那場站姿腕距約
     * 1.48 個肩寬，同樣「打開到最大側展 75%」得到 p = 1.73，若站姿腕距是 0.90
     * 則只得到 p = 1.41，在 55% 處更是一個 RED 一個 YELLOW。p 因此不可跨受試者比較。
     */
    @Test
    fun `判準不受站姿腕距影響，同一幅度在不同站姿下得到同一個達成率`() {
        val narrow = ChestExpansionSignal(100f, ceShoulderWidth, ceArmLength)
        val wide = ChestExpansionSignal(200f, ceShoulderWidth, ceArmLength)
        assertEquals(narrow.target!!, wide.target!!, 1e-4f)

        // 同一個絕對腕距（打開到 300px）在兩種站姿下必須得到同一個 p
        val pNarrow = narrow.progress(chest(300f))!! / narrow.target!!
        val pWide = wide.progress(chest(300f))!! / wide.target!!
        assertEquals(pNarrow, pWide, 1e-4f)
    }

    /**
     * 狀態機的門檻換算回**絕對腕距**後，必須與「進度相對站姿」的舊寫法完全相同 ——
     * 計次行為已經實機驗證過（15 下全中），這次只改 p 的算法，不能動到它。
     */
    @Test
    fun `三個門檻換算回絕對腕距與舊寫法一致`() {
        val signal = chestSignal()
        val travel = signal.target!! - ceRestSeparation / ceShoulderWidth
        // 舊寫法：enter = 0.30 × travel（相對站姿）→ 絕對腕距 = 站姿 + 0.30 × travel × 肩寬
        assertEquals(
            ceRestSeparation + 0.30f * travel * ceShoulderWidth,
            signal.enterThreshold * ceShoulderWidth,
            1e-2f
        )
        assertEquals(
            ceRestSeparation + 0.20f * travel * ceShoulderWidth,
            signal.returnThreshold * ceShoulderWidth,
            1e-2f
        )
        // 轉折量是「回退多少」的差值，沒有起點偏移，兩種寫法本來就相同
        assertEquals(0.08f * travel, signal.turnConfirmRise, 1e-4f)
    }

    @Test
    fun `腕距達到判準時達成率為一`() {
        val signal = chestSignal()
        val full = ceShoulderWidth + 2f * ceArmLength
        val p = signal.progress(chest(full * 0.6f))!! / signal.target!!
        assertEquals(1f, p, 1e-3f)
    }

    /**
     * 推掌（雙手在身前併攏）時腕距比站姿更小，進度低於返回門檻。
     * 推掌在正面視角量不到，它在這個模型裡就是這一下的返回階段。
     */
    @Test
    fun `雙手在身前併攏時進度低於返回門檻`() {
        val signal = chestSignal()
        val p = signal.progress(chest(40f))!!
        assertTrue("併攏時進度 $p 應低於站姿進度", p < ceRestSeparation / ceShoulderWidth)
        assertTrue("併攏時進度 $p 應低於返回門檻 ${signal.returnThreshold}",
            p < signal.returnThreshold)
    }

    @Test
    fun `擴胸缺少手腕關鍵點時回傳 null`() {
        val noWrists = chest(ceRestSeparation)
            .filterKeys { it != KeyPointType.LEFT_WRIST && it != KeyPointType.RIGHT_WRIST }
        assertNull(chestSignal().progress(noWrists))
    }

    @Test
    fun `擴胸的站姿校正取平均並產生可用訊號`() {
        val c = ChestExpansionStandCalibrator()
        listOf(120f, 132f, 124f, 128f, 126f).forEach { assertTrue(c.accumulate(chest(it))) }
        assertEquals(5, c.sampleCount)
        val built = c.build().single()
        assertEquals(chestSignal().target!!, built.target!!, 1e-2f)
        // 站姿腕距的平均值仍然會影響門檻（門檻以站姿為起點），只是不再影響 target
        assertEquals(chestSignal().enterThreshold, built.enterThreshold, 1e-2f)
    }

    /**
     * 使用者在站姿校正時就把手張開 —— 站姿腕距已經超過判準，target 會是 0 或負數，
     * 之後每一下的 p 都沒有意義。必須在這裡擋下來。
     */
    @Test
    fun `擴胸校正時手已張開則拒絕產生訊號`() {
        val c = ChestExpansionStandCalibrator()
        // 站姿腕距 400 > 判準腕距 319 → travel 為負，門檻會錯亂
        repeat(5) { c.accumulate(chest(400f)) }
        assertTrue(c.build().isEmpty())
    }

    @Test
    fun `狀態機以擴胸訊號計為一下`() {
        val signal = chestSignal()
        val m = SquatStateMachine(signal)
        // 打開：126 → 330（略超過判準腕距 319）
        listOf(126f, 160f, 200f, 260f, 320f, 330f).forEach { m.update(chest(it)) }
        // 收回推掌：330 → 126，並在身前停留足夠幀數
        listOf(310f, 290f, 270f, 220f, 160f).forEach { m.update(chest(it)) }
        repeat(8) { m.update(chest(126f)) }

        assertEquals(1, m.repCount)
        assertEquals(SquatState.STAND, m.state)
        assertTrue("打開到略超過判準，p 應略大於 1",
            m.lastPeakProgress!! / signal.target!! in 1.0f..1.2f)
    }

    @Test
    fun `擴胸幅度太小不會被計次`() {
        val m = SquatStateMachine(chestSignal())
        repeat(4) {
            // 腕距只開到 180（1.29 個肩寬），進度 0.386 < 進場門檻 0.414
            listOf(126f, 150f, 180f, 150f, 126f).forEach { m.update(chest(it)) }
            repeat(6) { m.update(chest(126f)) }
        }
        assertEquals(0, m.repCount)
    }

    @Test
    fun `擴胸幅度不足但有動作仍會計次並給出紅燈級的達成率`() {
        val signal = chestSignal()
        val m = SquatStateMachine(signal)
        // 開到 250（0.886 進度）—— 超過進場門檻，應被計次，但 p 只有約 0.64
        listOf(126f, 180f, 220f, 250f).forEach { m.update(chest(it)) }
        listOf(230f, 210f, 190f, 170f, 150f).forEach { m.update(chest(it)) }
        repeat(8) { m.update(chest(126f)) }
        assertEquals(1, m.repCount)
        val p = m.lastPeakProgress!! / signal.target!!
        assertTrue("幅度不足時 p 應低於入門綠燈門檻 0.90，實際 $p", p < 0.90f)
    }

    // ---- 原地高抬腿 ----

    // 解剖學比例：腿長（髖-踝）400，大腿長（髖-膝）200 → target = 0.5
    private val hkHipY = 600f
    private val hkAnkleY = 1000f
    private val hkKneeY = 800f
    private val hkScale = hkAnkleY - hkHipY          // 400
    private val hkTarget = (hkKneeY - hkHipY) / hkScale  // 0.5

    /** 左右膝各自抬到指定高度（以站姿膝高往上的像素數表示）。 */
    private fun knees(leftLift: Float, rightLift: Float): Map<KeyPointType, KeyPoint> = mapOf(
        KeyPointType.LEFT_HIP to KeyPoint(KeyPointType.LEFT_HIP, 330f, hkHipY, 0.9f),
        KeyPointType.RIGHT_HIP to KeyPoint(KeyPointType.RIGHT_HIP, 390f, hkHipY, 0.9f),
        KeyPointType.LEFT_KNEE to KeyPoint(KeyPointType.LEFT_KNEE, 330f, hkKneeY - leftLift, 0.9f),
        KeyPointType.RIGHT_KNEE to KeyPoint(KeyPointType.RIGHT_KNEE, 390f, hkKneeY - rightLift, 0.9f),
        KeyPointType.LEFT_ANKLE to KeyPoint(KeyPointType.LEFT_ANKLE, 330f, hkAnkleY, 0.9f),
        KeyPointType.RIGHT_ANKLE to KeyPoint(KeyPointType.RIGHT_ANKLE, 390f, hkAnkleY, 0.9f),
    )

    private fun hkSignals() = HighKneesStandCalibrator().also {
        repeat(5) { _ -> it.accumulate(knees(0f, 0f)) }
    }.build()

    @Test
    fun `高抬腿左右腳各產生一個訊號`() {
        assertEquals(2, hkSignals().size)
    }

    @Test
    fun `高抬腿判準是膝抬到髖高`() {
        val left = hkSignals().first()
        assertEquals(hkTarget, left.target!!, 1e-4f)
        // 膝抬到髖高時進度正好等於判準
        assertEquals(left.target!!, left.progress(knees(hkKneeY - hkHipY, 0f))!!, 1e-4f)
        // 大腿長/腿長 ≈ 0.5，與其他動作的 target 同量級
        assertTrue("target 應在 0.3~0.7，實際 ${left.target}", left.target!! in 0.3f..0.7f)
    }

    @Test
    fun `高抬腿兩個訊號只各自讀自己那側的膝`() {
        val (left, right) = hkSignals()
        // 只抬左腳：左訊號有進度，右訊號維持 0
        assertTrue(left.progress(knees(150f, 0f))!! > 0.3f)
        assertEquals(0f, right.progress(knees(150f, 0f))!!, 1e-4f)
    }

    @Test
    fun `高抬腿站姿時膝未低於髖則拒絕產生訊號`() {
        val c = HighKneesStandCalibrator()
        // 校正時膝已經抬到髖以上
        repeat(5) { c.accumulate(knees(hkKneeY - hkHipY + 20f, hkKneeY - hkHipY + 20f)) }
        assertTrue(c.build().isEmpty())
    }

    // 模擬 25fps 的交替踏步：每腳抬 10 幀、休息 10 幀（每腳 0.8 秒）。
    // 關鍵是**一腳抬的時候另一腳在地上**，兩腳的半波連續鋪滿、中間沒有雙腳落地的空檔。
    private val hkLiftFrames = 10
    private val hkPeriod = 20

    private fun marchLift(frame: Int, isLeft: Boolean, amplitude: Float): Float {
        val x = frame % hkPeriod
        val active = if (isLeft) x < hkLiftFrames else x >= hkLiftFrames
        if (!active) return 0f
        val phase = if (isLeft) x else x - hkLiftFrames
        val peak = hkKneeY - hkHipY
        return (kotlin.math.sin(kotlin.math.PI * phase / hkLiftFrames) * peak * amplitude)
            .toFloat()
    }

    /**
     * 這條是「一下 = 單腳抬一次」的核心保護。
     *
     * 左右交替踏步、兩腳的抬腿半波**連續鋪滿**（一腳落地的同時另一腳已抬起）。
     * 若用「較高的那隻膝」當單一訊號，進度永遠湊不到連續
     * [Config.STAND_STABLE_FRAMES] 幀低於返回門檻，第一下之後就卡在 UP ——
     * 模擬 12 次抬腿只計到 1 下。左右各一台則彼此不受影響，12 次全中。
     */
    @Test
    fun `交替踏步時左右各一台狀態機能正確計次，單一訊號會漏算`() {
        val (leftSignal, rightSignal) = hkSignals()
        val left = SquatStateMachine(leftSignal)
        val right = SquatStateMachine(rightSignal)
        // 對照組：餵「較高的那隻膝」給單一狀態機
        val single = SquatStateMachine(leftSignal)

        val cycles = 6
        for (frame in 0 until cycles * hkPeriod) {
            val l = marchLift(frame, isLeft = true, amplitude = 1f)
            val r = marchLift(frame, isLeft = false, amplitude = 1f)
            left.update(knees(l, r))
            right.update(knees(l, r))
            single.update(knees(maxOf(l, r), 0f))
        }
        // 結束時雙腳落地，讓進行中的那一下完成
        repeat(12) {
            left.update(knees(0f, 0f))
            right.update(knees(0f, 0f))
            single.update(knees(0f, 0f))
        }

        assertEquals(cycles, left.repCount)
        assertEquals(cycles, right.repCount)
        assertEquals(2 * cycles, left.repCount + right.repCount)
        assertTrue(
            "單一訊號應嚴重漏算（實際 ${single.repCount}，真實 ${2 * cycles}）",
            single.repCount <= 2
        )
    }

    @Test
    fun `抬腿只到判準一半仍然計次，交給分級去判不夠高`() {
        val (leftSignal, rightSignal) = hkSignals()
        val left = SquatStateMachine(leftSignal)
        val right = SquatStateMachine(rightSignal)
        val cycles = 6
        for (frame in 0 until cycles * hkPeriod) {
            val l = marchLift(frame, isLeft = true, amplitude = 0.5f)
            val r = marchLift(frame, isLeft = false, amplitude = 0.5f)
            left.update(knees(l, r))
            right.update(knees(l, r))
        }
        repeat(12) {
            left.update(knees(0f, 0f))
            right.update(knees(0f, 0f))
        }
        assertEquals(2 * cycles, left.repCount + right.repCount)
        // 幅度只有一半，達成率應落在紅燈區（入門綠燈門檻 0.90）
        assertTrue(
            "半高抬腿的 p 應明顯低於 0.90，實際 ${left.lastPeakProgress!! / leftSignal.target!!}",
            left.lastPeakProgress!! / leftSignal.target!! < 0.90f
        )
    }

    @Test
    fun `高抬腿抬得太低不會被計次`() {
        val left = SquatStateMachine(hkSignals().first())
        val peak = hkKneeY - hkHipY
        repeat(4) {
            // 只抬到判準的 25%，低於進場門檻 30%
            listOf(0f, peak * 0.15f, peak * 0.25f, peak * 0.15f, 0f)
                .forEach { left.update(knees(it, 0f)) }
            repeat(6) { left.update(knees(0f, 0f)) }
        }
        assertEquals(0, left.repCount)
    }

    // ---- 端到端：狀態機數舉手 ----

    /**
     * 同一個五階段狀態機，不改一行，直接數舉手。
     * 這是整層抽象的目的：狀態機不知道自己在數什麼。
     */
    @Test
    fun `狀態機以舉手訊號計為一下，達成率為一`() {
        val signal = armRaiseSignal()
        val m = SquatStateMachine(signal)

        // 舉起：站姿 → 肩高（以手臂長的比例表示，數字自我說明）
        listOf(0f, 0.25f, 0.5f, 0.75f, 1.0f).forEach { m.update(upperBody(wristAt(it))) }
        // 放下：肩高 → 身側，並在身側停留足夠幀數
        listOf(0.9f, 0.75f, 0.6f, 0.4f, 0.1f).forEach { m.update(upperBody(wristAt(it))) }
        repeat(8) { m.update(upperBody(wristAt(0f))) }

        assertEquals(1, m.repCount)
        assertEquals(SquatState.STAND, m.state)
        assertEquals(1f, m.lastPeakProgress!! / signal.target!!, 1e-3f)
    }

    @Test
    fun `只舉到一半不會被計次`() {
        val signal = armRaiseSignal()
        val m = SquatStateMachine(signal)
        repeat(4) {
            // 只舉到手臂長的 26%，進度 0.364 < 進場門檻 0.42
            listOf(0f, 0.13f, 0.26f, 0.13f, 0f).forEach { m.update(upperBody(wristAt(it))) }
            repeat(6) { m.update(upperBody(wristAt(0f))) }
        }
        assertEquals(0, m.repCount)
    }

    @Test
    fun `手臂在身側自然晃動不會被計次`() {
        val m = SquatStateMachine(armRaiseSignal())
        val rng = java.util.Random(23)
        // 晃動幅度 ±30px（約 0.21 個肩寬），遠小於進場門檻 0.42
        repeat(400) { m.update(upperBody(restWristY + (rng.nextFloat() - 0.5f) * 60f)) }
        assertEquals(0, m.repCount)
        assertEquals(SquatState.STAND, m.state)
    }

    @Test
    fun `舉到最高點停頓不會誤觸發，放下後才計一下`() {
        val signal = armRaiseSignal()
        val m = SquatStateMachine(signal)
        listOf(0f, 0.25f, 0.5f, 0.75f, 1.0f).forEach { m.update(upperBody(wristAt(it))) }
        // 在最高點撐住，只有雜訊在動
        val rng = java.util.Random(5)
        repeat(100) {
            val state = m.update(upperBody(wristAt(1.0f) + (rng.nextFloat() - 0.5f) * 8f))
            assertEquals("撐在最高點時不應離開 DOWN", SquatState.DOWN, state)
        }
        listOf(0.85f, 0.7f, 0.55f, 0.3f, 0.1f).forEach { m.update(upperBody(wristAt(it))) }
        repeat(8) { m.update(upperBody(wristAt(0f))) }
        assertEquals(1, m.repCount)
    }

    // ---- 踮腳尖 ----
    //
    // 身體尺寸取實測量級：腿長（髖-踝）256px、站姿「腳尖-髖」300px
    // （實測 1.155~1.180 個腿長，這裡是 1.17）。
    // 判準 0.06 個腿長 = 15.36px 的髖上升。
    private val hrLeg = 256f
    private val hrToeToHip = 300f
    private val hrHipY = 400f
    private val hrAnkleY = hrHipY + hrLeg          // 656
    private val hrToeY = hrHipY + hrToeToHip       // 700

    /** 髖上升到「判準的 [fraction] 倍」。1.0 = 剛好達標。 */
    private fun heelRaiseFrame(fraction: Float): Map<KeyPointType, KeyPoint> {
        val hipY = hrHipY - fraction * Config.HEEL_RAISE_TARGET_RISE_RATIO * hrLeg
        return mapOf(
            KeyPointType.LEFT_HIP to KeyPoint(KeyPointType.LEFT_HIP, 320f, hipY, 0.9f),
            KeyPointType.RIGHT_HIP to KeyPoint(KeyPointType.RIGHT_HIP, 400f, hipY, 0.9f),
            // 腳尖是錨點：踮起時它踩在地上不動。
            KeyPointType.LEFT_TOE to KeyPoint(KeyPointType.LEFT_TOE, 320f, hrToeY, 0.9f),
            KeyPointType.RIGHT_TOE to KeyPoint(KeyPointType.RIGHT_TOE, 400f, hrToeY, 0.9f),
        )
    }

    private fun heelRaiseSignal(): RepSignal {
        val c = HeelRaiseStandCalibrator()
        repeat(5) {
            c.accumulate(
                heelRaiseFrame(0f) + mapOf(
                    KeyPointType.LEFT_ANKLE to KeyPoint(KeyPointType.LEFT_ANKLE, 320f, hrAnkleY, 0.9f),
                    KeyPointType.RIGHT_ANKLE to KeyPoint(KeyPointType.RIGHT_ANKLE, 400f, hrAnkleY, 0.9f),
                )
            )
        }
        return c.build().single()
    }

    /** 回到靜止並停住足夠久（`STAND_STABLE_FRAMES` = 5，加上轉折那幾幀的餘裕）。 */
    private fun SquatStateMachine.settle() = repeat(8) { update(heelRaiseFrame(0f)) }

    @Test
    fun `踮腳尖的判準是髖上升到腿長的固定比例`() {
        val signal = heelRaiseSignal()
        assertEquals(Config.HEEL_RAISE_TARGET_RISE_RATIO, signal.target!!, 1e-6f)
        // 站姿時進度為 0
        assertEquals(0f, signal.progress(heelRaiseFrame(0f))!!, 1e-5f)
        // 踮到判準時進度剛好等於判準
        assertEquals(signal.target!!, signal.progress(heelRaiseFrame(1f))!!, 1e-5f)
    }

    @Test
    fun `踮一下完整的會計次且達成率為一`() {
        val signal = heelRaiseSignal()
        val m = SquatStateMachine(signal)
        listOf(0f, 0.25f, 0.5f, 0.75f, 1.0f).forEach { m.update(heelRaiseFrame(it)) }
        listOf(0.9f, 0.75f, 0.6f, 0.4f).forEach { m.update(heelRaiseFrame(it)) }
        m.settle()
        assertEquals(1, m.repCount)
        assertEquals(1.0f, m.lastPeakProgress!! / signal.target!!, 1e-4f)
    }

    @Test
    fun `只踮到判準的四分之一不計次`() {
        val m = SquatStateMachine(heelRaiseSignal())
        listOf(0f, 0.1f, 0.2f, 0.25f, 0.2f, 0.15f, 0.1f, 0.05f)
            .forEach { m.update(heelRaiseFrame(it)) }
        m.settle()
        assertEquals(0, m.repCount)
        assertEquals(SquatState.STAND, m.state)
    }

    @Test
    fun `踮到判準一半會計次但達成率只有一半`() {
        val signal = heelRaiseSignal()
        val m = SquatStateMachine(signal)
        listOf(0f, 0.2f, 0.35f, 0.5f, 0.45f, 0.4f, 0.3f, 0.2f)
            .forEach { m.update(heelRaiseFrame(it)) }
        m.settle()
        assertEquals("有動作就該計次，幅度由 p 表達", 1, m.repCount)
        assertEquals(0.5f, m.lastPeakProgress!! / signal.target!!, 1e-4f)
    }

    /**
     * 踮腳尖的位移只有腿長的 6~8%，所以「雜訊會不會自己湊出一下」是這個動作
     * 最實際的風險。這裡用的 ±0.18 個判準是**實測最差的雜訊底**
     * （兩份逐幀資料量到 0.003~0.011 個腿長，而判準是 0.06）。
     *
     * 餘裕只有 1.67 倍，是六個動作裡最窄的 —— 這條測試就是為了讓它別再變窄。
     */
    @Test
    fun `站著不動時實測最差的雜訊底不會湊出一下`() {
        val m = SquatStateMachine(heelRaiseSignal())
        val rng = java.util.Random(7)
        repeat(600) { m.update(heelRaiseFrame((rng.nextFloat() - 0.5f) * 0.36f)) }
        assertEquals(0, m.repCount)
        assertEquals(SquatState.STAND, m.state)
    }

    @Test
    fun `踮在最高點停頓不會誤觸發轉折`() {
        val m = SquatStateMachine(heelRaiseSignal())
        listOf(0f, 0.3f, 0.6f, 1.0f).forEach { m.update(heelRaiseFrame(it)) }
        repeat(10) {
            assertEquals(
                "撐在最高點時不應離開 DOWN",
                SquatState.DOWN,
                m.update(heelRaiseFrame(1.0f))
            )
        }
        listOf(0.9f, 0.75f, 0.6f, 0.4f).forEach { m.update(heelRaiseFrame(it)) }
        m.settle()
        assertEquals(1, m.repCount)
    }

    @Test
    fun `踮過判準一倍半的達成率大於一`() {
        val signal = heelRaiseSignal()
        val m = SquatStateMachine(signal)
        listOf(0f, 0.4f, 0.8f, 1.2f, 1.5f, 1.3f, 1.1f, 0.9f, 0.5f)
            .forEach { m.update(heelRaiseFrame(it)) }
        m.settle()
        assertEquals(1, m.repCount)
        assertEquals(1.5f, m.lastPeakProgress!! / signal.target!!, 1e-4f)
    }

    @Test
    fun `連續踮三下計到三下`() {
        val m = SquatStateMachine(heelRaiseSignal())
        repeat(3) {
            listOf(0f, 0.3f, 0.6f, 1.0f, 0.9f, 0.75f, 0.6f, 0.4f)
                .forEach { f -> m.update(heelRaiseFrame(f)) }
            m.settle()
        }
        assertEquals(3, m.repCount)
    }

    /**
     * 校正時人就已經踮著 → 「腳尖-髖 ÷ 腿長」會跑掉，必須拒絕並要求重做。
     * 不拒絕的話基準偏高，整場的進度會是負的、一下都計不到。
     */
    @Test
    fun `校正時腳尖與髖的比例不合理則拒絕產生訊號`() {
        val c = HeelRaiseStandCalibrator()
        repeat(5) {
            c.accumulate(
                mapOf(
                    KeyPointType.LEFT_HIP to KeyPoint(KeyPointType.LEFT_HIP, 320f, 400f, 0.9f),
                    KeyPointType.RIGHT_HIP to KeyPoint(KeyPointType.RIGHT_HIP, 400f, 400f, 0.9f),
                    KeyPointType.LEFT_ANKLE to KeyPoint(KeyPointType.LEFT_ANKLE, 320f, 656f, 0.9f),
                    KeyPointType.RIGHT_ANKLE to KeyPoint(KeyPointType.RIGHT_ANKLE, 400f, 656f, 0.9f),
                    // 腳尖只比髖低 100px = 0.39 個腿長，遠在合理區間（1.05~1.45）之外
                    KeyPointType.LEFT_TOE to KeyPoint(KeyPointType.LEFT_TOE, 320f, 500f, 0.9f),
                    KeyPointType.RIGHT_TOE to KeyPoint(KeyPointType.RIGHT_TOE, 400f, 500f, 0.9f),
                )
            )
        }
        assertTrue("不合理的站姿量測應拒絕", c.build(strict = true).isEmpty())
        assertTrue("放寬後仍應產生訊號（避免卡在校正出不去）",
            c.build(strict = false).isNotEmpty())
    }

    /**
     * 「要不要在倒數期間重新取基準」綁在**判準有多小**上，不綁在動作名稱上。
     *
     * 實測一場使用者在站姿校正後退了 6%（腿長 291.9 → 274.2px），
     * 基準誤差達腿長 11%，而踮腳尖的振幅只有 6~8% —— 用站姿校正的基準是 0 下。
     * 判準大的動作無傷，所以不該連它們一起改（它們都已實機驗證過）。
     */
    @Test
    fun `只有判準夠小的動作需要在倒數期間重新取基準`() {
        assertTrue("踮腳尖必須重取", needsCountdownRebaseline(listOf(heelRaiseSignal())))
        assertTrue("雙臂高舉不該重取", !needsCountdownRebaseline(listOf(armRaiseSignal())))
        // 深蹲家族的 target 是 null（分母是 Duser），一律不重取
        assertTrue(
            "深蹲家族不該重取",
            !needsCountdownRebaseline(listOf(SquatSignal(600f, 400f)))
        )
        assertTrue("沒有訊號時不該重取", !needsCountdownRebaseline(emptyList()))
    }

    // ---- 尺度追蹤：使用者離開校正距離／轉身之後仍然要能計次 ----
    //
    // 2026-10-05 實機：雙臂高舉第 1 下 12:11:54、第 2 下 12:12:17 ——
    // 中間 23.8 秒舉了很多次毫無反應，而且**一個提示都沒有**。
    //
    // 原因：進度是 `(校正時落差 − 當下落差) ÷ 校正時肩寬`，分子逐幀、分母過期，
    // 不是尺度不變的。身體投影縮小 x% 時，手垂下的進度就變成 `判準 × x%`：
    // 縮到 0.8（退到 1.25 倍距離）就剛好等於返回門檻，再遠一點就
    // **永遠回不到 STAND** —— 一下都不計，而且 atRest 永遠 false，框位被當成 OK。

    /**
     * 把身體整體縮放 [scale] 倍，模擬「站遠」或「轉身」造成的投影縮小
     * （實測 2026-10-02 那場：肩寬 −30.5% 而腿長只 −6%，主因是轉身）。
     * 手腕抬到縮放後手臂長的 [fraction] 處。
     */
    private fun upperBodyScaled(scale: Float, fraction: Float): Map<KeyPointType, KeyPoint> {
        val cx = (leftShoulderX + rightShoulderX) / 2f
        val half = shoulderWidth * scale / 2f
        val drop = restDrop * scale
        val wristY = shoulderY + drop - fraction * drop
        return mapOf(
            KeyPointType.LEFT_SHOULDER to
                KeyPoint(KeyPointType.LEFT_SHOULDER, cx - half, shoulderY, 0.9f),
            KeyPointType.RIGHT_SHOULDER to
                KeyPoint(KeyPointType.RIGHT_SHOULDER, cx + half, shoulderY, 0.9f),
            KeyPointType.LEFT_WRIST to
                KeyPoint(KeyPointType.LEFT_WRIST, cx - half, wristY, 0.9f),
            KeyPointType.RIGHT_WRIST to
                KeyPoint(KeyPointType.RIGHT_WRIST, cx + half, wristY, 0.9f),
        )
    }

    @Test
    fun `站在校正距離時進度與加入尺度追蹤前完全相同`() {
        // 這條鎖住「修這個 bug 不會動到已實機驗證的行為」。
        // 追蹤器的初始值就是校正肩寬，使用者沒移動時 EMA 停在原地。
        val signal = armRaiseSignal()
        listOf(0f to 0f, 0.5f to 0.7f, 1.0f to 1.4f, 1.5f to 2.1f).forEach { (fraction, want) ->
            assertEquals(
                "fraction=$fraction",
                want,
                signal.progress(upperBodyScaled(1.0f, fraction))!!,
                1e-5f
            )
        }
    }

    @Test
    fun `退到校正距離的一點三倍時手垂下仍然低於返回門檻`() {
        val signal = armRaiseSignal()
        // 投影縮到 0.75（距離 1.33 倍）。舊寫法在這裡是 0.350，高於返回門檻 0.280，
        // 於是手垂下也回不到 STAND。
        repeat(20) { signal.progress(upperBodyScaled(0.75f, 0f)) }
        val atRest = signal.progress(upperBodyScaled(0.75f, 0f))!!
        assertTrue(
            "手垂下的進度 $atRest 必須低於返回門檻 ${signal.returnThreshold}",
            atRest < signal.returnThreshold
        )
    }

    @Test
    fun `退到校正距離的一點三倍仍然計得到一下且達成率不變`() {
        val signal = armRaiseSignal()
        val m = SquatStateMachine(signal)
        // 先站著讓尺度追蹤器收斂到新的距離
        repeat(12) { m.update(upperBodyScaled(0.75f, 0f)) }
        listOf(0.25f, 0.5f, 0.75f, 1.0f).forEach { m.update(upperBodyScaled(0.75f, it)) }
        listOf(0.9f, 0.75f, 0.6f, 0.4f).forEach { m.update(upperBodyScaled(0.75f, it)) }
        repeat(10) { m.update(upperBodyScaled(0.75f, 0f)) }
        assertEquals("舊寫法在這裡是 0 下且永久停在 UP", 1, m.repCount)
        // 舉到肩高 = p 1.0，與在校正距離做同樣動作相同
        assertEquals(1.0f, m.lastPeakProgress!! / signal.target!!, 1e-3f)
    }

    @Test
    fun `擴胸在非校正距離上同樣的動作幅度得到同樣的進度`() {
        val signal = chestSignal()
        val target = signal.target!!
        // 在校正距離上張開到判準腕距
        val atCalibration = signal.progress(chest(target * ceShoulderWidth))!!
        // 縮到 0.75 之後張開到「同樣是 target 個自己的肩寬」
        val scaled = ChestExpansionSignal(ceRestSeparation, ceShoulderWidth, ceArmLength)
        val k = 0.75f
        val cx = 360f
        val half = ceShoulderWidth * k / 2f
        fun scaledFrame(separationInShoulders: Float): Map<KeyPointType, KeyPoint> {
            val sep = separationInShoulders * ceShoulderWidth * k
            return mapOf(
                KeyPointType.LEFT_SHOULDER to
                    KeyPoint(KeyPointType.LEFT_SHOULDER, cx - half, shoulderY, 0.9f),
                KeyPointType.RIGHT_SHOULDER to
                    KeyPoint(KeyPointType.RIGHT_SHOULDER, cx + half, shoulderY, 0.9f),
                KeyPointType.LEFT_WRIST to
                    KeyPoint(KeyPointType.LEFT_WRIST, cx - sep / 2f, shoulderY + ceArmLength * k, 0.9f),
                KeyPointType.RIGHT_WRIST to
                    KeyPoint(KeyPointType.RIGHT_WRIST, cx + sep / 2f, shoulderY + ceArmLength * k, 0.9f),
            )
        }
        repeat(20) { scaled.progress(scaledFrame(ceRestSeparation / ceShoulderWidth)) }
        val atDistance = scaled.progress(scaledFrame(target))!!
        assertEquals(
            "同樣的動作幅度在不同距離上必須得到同樣的進度",
            atCalibration, atDistance, 1e-2f
        )
    }
}

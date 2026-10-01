package com.heartchen.squat.squat

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

    // 一組固定的身體尺寸：肩在 y=400、肩寬 140、站姿手腕垂在 y=700。
    private val shoulderY = 400f
    private val leftShoulderX = 290f
    private val rightShoulderX = 430f
    private val shoulderWidth = rightShoulderX - leftShoulderX   // 140
    private val restWristY = 700f
    private val restDrop = restWristY - shoulderY                // 300

    private fun upperBody(wristY: Float): Map<KeyPointType, KeyPoint> = mapOf(
        KeyPointType.LEFT_SHOULDER to KeyPoint(KeyPointType.LEFT_SHOULDER, leftShoulderX, shoulderY, 0.9f),
        KeyPointType.RIGHT_SHOULDER to KeyPoint(KeyPointType.RIGHT_SHOULDER, rightShoulderX, shoulderY, 0.9f),
        KeyPointType.LEFT_WRIST to KeyPoint(KeyPointType.LEFT_WRIST, leftShoulderX, wristY, 0.9f),
        KeyPointType.RIGHT_WRIST to KeyPoint(KeyPointType.RIGHT_WRIST, rightShoulderX, wristY, 0.9f),
    )

    private fun armRaiseSignal() = ArmRaiseSignal(restDrop, shoulderWidth)

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
        // 300 / 140 ≈ 2.143 個肩寬 —— 量級比深蹲的 0.3 個腿長大一個數量級，
        // 這就是門檻必須取判準比例而非沿用深蹲絕對值的原因
        assertEquals(restDrop / shoulderWidth, armRaiseSignal().target!!, 1e-4f)
        assertTrue(armRaiseSignal().target!! > 2f)
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
        // 手臂自然晃動：手腕在 690~710 之間，平均仍是 700
        listOf(690f, 710f, 695f, 705f, 700f).forEach { assertTrue(c.accumulate(upperBody(it))) }
        assertEquals(5, c.sampleCount)
        val signal = c.build()!!
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
        assertNull(c.build())
    }

    @Test
    fun `沒有樣本時拒絕產生訊號`() {
        assertNull(ArmRaiseStandCalibrator().build())
        assertNull(LowerBodyStandCalibrator().build())
    }

    @Test
    fun `舉手動作的站姿校正拿不到上肢關鍵點時不計入樣本`() {
        val c = ArmRaiseStandCalibrator()
        assertTrue(!c.accumulate(emptyMap()))
        assertEquals(0, c.sampleCount)
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

        // 舉起：700 → 400（肩高）
        listOf(700f, 650f, 600f, 500f, 420f, 400f).forEach { m.update(upperBody(it)) }
        // 放下：400 → 700，並在身側停留足夠幀數
        listOf(430f, 460f, 490f, 520f, 600f, 660f).forEach { m.update(upperBody(it)) }
        repeat(8) { m.update(upperBody(700f)) }

        assertEquals(1, m.repCount)
        assertEquals(SquatState.STAND, m.state)
        assertEquals(1f, m.lastPeakProgress!! / signal.target!!, 1e-3f)
    }

    @Test
    fun `只舉到一半不會被計次`() {
        val signal = armRaiseSignal()
        val m = SquatStateMachine(signal)
        repeat(4) {
            // 手腕只到 640，進度 0.43 < 進場門檻 0.96
            listOf(700f, 670f, 640f, 670f, 700f).forEach { m.update(upperBody(it)) }
            repeat(6) { m.update(upperBody(700f)) }
        }
        assertEquals(0, m.repCount)
    }

    @Test
    fun `手臂在身側自然晃動不會被計次`() {
        val m = SquatStateMachine(armRaiseSignal())
        val rng = java.util.Random(23)
        // 晃動幅度 ±30px（約 0.21 個肩寬），遠小於進場門檻 0.96
        repeat(400) { m.update(upperBody(restWristY + (rng.nextFloat() - 0.5f) * 60f)) }
        assertEquals(0, m.repCount)
        assertEquals(SquatState.STAND, m.state)
    }

    @Test
    fun `舉到最高點停頓不會誤觸發，放下後才計一下`() {
        val signal = armRaiseSignal()
        val m = SquatStateMachine(signal)
        listOf(700f, 650f, 600f, 500f, 400f).forEach { m.update(upperBody(it)) }
        // 在最高點撐住，只有雜訊在動
        val rng = java.util.Random(5)
        repeat(100) {
            val state = m.update(upperBody(400f + (rng.nextFloat() - 0.5f) * 8f))
            assertEquals("撐在最高點時不應離開 DOWN", SquatState.DOWN, state)
        }
        listOf(440f, 480f, 520f, 600f, 670f).forEach { m.update(upperBody(it)) }
        repeat(8) { m.update(upperBody(700f)) }
        assertEquals(1, m.repCount)
    }
}

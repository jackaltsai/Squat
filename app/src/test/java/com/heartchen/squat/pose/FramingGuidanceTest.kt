package com.heartchen.squat.pose

import com.heartchen.squat.squat.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 框位引導的動作差異化測試。
 *
 * 原本 `evaluateFraming` 硬性要求腳踝，拿不到就回「請往後站一點」——
 * 手臂動作既拿不到腳踝也不需要，使用者會被一直叫往後站。
 * 反過來，上肢動作有一個下肢動作沒有的要求：**頭頂必須留得下舉起的手**，
 * 而原本的框位引導對手腕出框一句話都不會說。
 */
class FramingGuidanceTest {

    private val width = 720
    private val height = 1280

    private fun kp(type: KeyPointType, x: Float, y: Float) = KeyPoint(type, x, y, 0.9f)

    private fun frame(vararg points: KeyPoint) =
        PoseFrame(points.toList(), width, height, isFrontCamera = true)

    /** 肩在畫面 31%（頭頂有空間）、肩寬 140px（約 19% 畫面寬）、手腕垂在 55% 處。 */
    private fun upperBody(
        shoulderY: Float = 400f,
        wristY: Float = 700f,
        shoulderWidth: Float = 140f,
    ): PoseFrame {
        val cx = width / 2f
        val lx = cx - shoulderWidth / 2f
        val rx = cx + shoulderWidth / 2f
        return frame(
            kp(KeyPointType.LEFT_SHOULDER, lx, shoulderY),
            kp(KeyPointType.RIGHT_SHOULDER, rx, shoulderY),
            kp(KeyPointType.LEFT_WRIST, lx, wristY),
            kp(KeyPointType.RIGHT_WRIST, rx, wristY),
        )
    }

    private fun lowerBody(hipY: Float = 600f, ankleY: Float = 1100f): PoseFrame = frame(
        kp(KeyPointType.LEFT_HIP, 320f, hipY),
        kp(KeyPointType.RIGHT_HIP, 400f, hipY),
        kp(KeyPointType.LEFT_ANKLE, 320f, ankleY),
        kp(KeyPointType.RIGHT_ANKLE, 400f, ankleY),
    )

    // ---- 這條是重點迴歸：手臂動作不該因為看不到腳踝就被罵 ----

    @Test
    fun `舉手動作在腳踝完全不在畫面內時框位仍然良好`() {
        // upperBody() 裡根本沒有腳踝關鍵點。抽象化之前這裡會回 MISSING_ANKLE，
        // 使用者會在整場舉手訓練中一直看到「請往後站一點」。
        assertEquals(FramingIssue.OK, evaluateFraming(upperBody(), ExerciseType.ARM_RAISE))
    }

    @Test
    fun `深蹲動作缺少腳踝仍然要提示`() {
        val noAnkles = frame(
            kp(KeyPointType.LEFT_HIP, 320f, 600f),
            kp(KeyPointType.RIGHT_HIP, 400f, 600f),
        )
        assertEquals(FramingIssue.MISSING_ANKLE, evaluateFraming(noAnkles, ExerciseType.SQUAT))
    }

    // ---- 上肢動作的自有判準 ----

    @Test
    fun `舉手動作偵測不到手腕時提示讓雙手入鏡`() {
        val noWrists = frame(
            kp(KeyPointType.LEFT_SHOULDER, 290f, 400f),
            kp(KeyPointType.RIGHT_SHOULDER, 430f, 400f),
        )
        assertEquals(FramingIssue.MISSING_WRIST, evaluateFraming(noWrists, ExerciseType.ARM_RAISE))
    }

    @Test
    fun `肩膀太靠近畫面上緣時提示頭頂空間不足`() {
        // 肩在畫面 23% 處，上方空間不足以容納舉起的手
        assertEquals(
            FramingIssue.NO_HEADROOM,
            evaluateFraming(upperBody(shoulderY = 300f), ExerciseType.ARM_RAISE)
        )
    }

    @Test
    fun `手腕已貼齊畫面上緣時提示頭頂空間不足`() {
        assertEquals(
            FramingIssue.NO_HEADROOM,
            evaluateFraming(upperBody(wristY = 20f), ExerciseType.ARM_RAISE)
        )
    }

    @Test
    fun `上肢動作以肩寬判斷遠近`() {
        assertEquals(
            FramingIssue.TOO_FAR,
            evaluateFraming(upperBody(shoulderWidth = 70f), ExerciseType.ARM_RAISE)
        )
        assertEquals(
            FramingIssue.TOO_CLOSE,
            evaluateFraming(upperBody(shoulderWidth = 360f), ExerciseType.ARM_RAISE)
        )
    }

    @Test
    fun `上肢動作偵測不到肩膀時回報沒有偵測到人`() {
        val onlyWrists = frame(
            kp(KeyPointType.LEFT_WRIST, 290f, 700f),
            kp(KeyPointType.RIGHT_WRIST, 430f, 700f),
        )
        assertEquals(FramingIssue.NO_POSE, evaluateFraming(onlyWrists, ExerciseType.ARM_RAISE))
    }

    // ---- 下肢動作的行為不可改變 ----

    @Test
    fun `深蹲動作正常框位回傳良好`() {
        assertEquals(FramingIssue.OK, evaluateFraming(lowerBody(), ExerciseType.SQUAT))
    }

    @Test
    fun `深蹲動作太遠太近仍依腿長判斷`() {
        // 腿長只佔畫面 16%
        assertEquals(
            FramingIssue.TOO_FAR,
            evaluateFraming(lowerBody(hipY = 900f, ankleY = 1100f), ExerciseType.SQUAT)
        )
        // 腿長佔畫面 70%
        assertEquals(
            FramingIssue.TOO_CLOSE,
            evaluateFraming(lowerBody(hipY = 200f, ankleY = 1100f), ExerciseType.SQUAT)
        )
    }

    @Test
    fun `沒有偵測到任何姿態時回報沒有偵測到人`() {
        assertEquals(FramingIssue.NO_POSE, evaluateFraming(null, ExerciseType.SQUAT))
        assertEquals(FramingIssue.NO_POSE, evaluateFraming(null, ExerciseType.ARM_RAISE))
    }
}

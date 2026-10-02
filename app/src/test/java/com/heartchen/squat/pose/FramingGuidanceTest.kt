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

    // 預設腿長 550px = 畫面高的 43%。這個值不是隨便取的：踮腳尖另有一個更嚴的
    // 距離下限（0.40），而**實機驗證通過的那場正是 0.433** ——
    // 原本的 1100f（0.39）會讓踮腳尖被判太遠，六動作分流測試就會紅。
    private fun lowerBody(hipY: Float = 600f, ankleY: Float = 1150f): PoseFrame = frame(
        kp(KeyPointType.LEFT_HIP, 320f, hipY),
        kp(KeyPointType.RIGHT_HIP, 400f, hipY),
        kp(KeyPointType.LEFT_ANKLE, 320f, ankleY),
        kp(KeyPointType.RIGHT_ANKLE, 400f, ankleY),
    )

    // ---- 這條是重點迴歸：手臂動作不該因為看不到腳踝就被罵 ----

    /**
     * 框位分流曾經寫成 `requiredPoints == KeyPointType.UPPER_BODY` 的集合相等比較。
     * 那把判斷綁在集合的「身分」而非「內容」上：雙臂高舉改成只宣告肩與腕
     * （不含手肘）之後，比較就不成立，整個掉回下肢分支、「請往後站一點」全部回來。
     * 現在改為問「這個動作需不需要腳踝」，綁在集合的內容上。
     */
    @Test
    fun `上肢動作一律走上肢框位判準`() {
        assertEquals(FramingIssue.OK, evaluateFraming(upperBody(), ExerciseType.ARM_RAISE))
        assertEquals(FramingIssue.OK, evaluateFraming(upperBody(), ExerciseType.CHEST_EXPANSION))
    }

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

    /**
     * 曾經有一條「肩膀上方要留得下整隻舉直的手」的檢查，門檻 0.30。
     * 它在物理上無法滿足 —— 直向手機 2~2.5 公尺拍全身時肩只落在 0.24~0.29，
     * 要過關得退到 3 公尺外，那時又快被判太遠，於是「請往後站」與「請往前站」
     * 互相拉扯，使用者完全抓不到距離（2026-10-01 實機，每下間隔 18~47 秒）。
     * 這條測試鎖住它不會被加回來。
     */
    @Test
    fun `全身入鏡時肩膀位置偏高仍視為框位良好`() {
        // 肩在畫面 23% 處 —— 這是直向手機拍全身的常態，不該被當成問題
        assertEquals(
            FramingIssue.OK,
            evaluateFraming(upperBody(shoulderY = 300f), ExerciseType.ARM_RAISE)
        )
        // 2.5 公尺處的典型值
        assertEquals(
            FramingIssue.OK,
            evaluateFraming(upperBody(shoulderY = 372f), ExerciseType.ARM_RAISE)
        )
    }

    @Test
    fun `手腕快被畫面上緣裁掉時提示舉到肩膀就好`() {
        assertEquals(
            FramingIssue.WRIST_NEAR_TOP_EDGE,
            evaluateFraming(upperBody(wristY = 20f), ExerciseType.ARM_RAISE)
        )
    }

    @Test
    fun `手腕舉到肩膀上方但未貼邊時框位良好`() {
        // 判準是舉到肩高，舉過肩很正常，不該因此報框位問題
        assertEquals(
            FramingIssue.OK,
            evaluateFraming(upperBody(wristY = 200f), ExerciseType.ARM_RAISE)
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

    /**
     * ML Kit 偵測到人就會回傳**全部**關鍵點，不管看不看得到。
     * 不依信心值過濾的話，`MISSING_*` 幾乎永遠不會觸發，
     * 而位置判斷會拿低信心值的（可能是亂猜的）座標去算 ——
     * 實測時整場只聽得到「舉到肩膀就好」就是這樣來的。
     */
    @Test
    fun `低信心值的關鍵點不列入框位判斷`() {
        val lowConfidenceWrists = frame(
            kp(KeyPointType.LEFT_SHOULDER, 290f, 400f),
            kp(KeyPointType.RIGHT_SHOULDER, 430f, 400f),
            // 手腕被回報在畫面最上緣，但信心值只有 0.1 —— 這是亂猜的位置，
            // 不該因此報「舉到肩膀就好」
            KeyPoint(KeyPointType.LEFT_WRIST, 290f, 5f, 0.1f),
            KeyPoint(KeyPointType.RIGHT_WRIST, 430f, 5f, 0.1f),
        )
        assertEquals(
            FramingIssue.MISSING_WRIST,
            evaluateFraming(lowConfidenceWrists, ExerciseType.ARM_RAISE)
        )
    }

    @Test
    fun `低信心值的腳踝不列入框位判斷`() {
        val lowConfidenceAnkles = frame(
            kp(KeyPointType.LEFT_HIP, 320f, 600f),
            kp(KeyPointType.RIGHT_HIP, 400f, 600f),
            KeyPoint(KeyPointType.LEFT_ANKLE, 320f, 1270f, 0.2f),
            KeyPoint(KeyPointType.RIGHT_ANKLE, 400f, 1270f, 0.2f),
        )
        assertEquals(
            FramingIssue.MISSING_ANKLE,
            evaluateFraming(lowConfidenceAnkles, ExerciseType.SQUAT)
        )
    }

    /**
     * 框位分流只有一個依據：這個動作要不要看手腕。
     * 這個判斷換過兩次 —— 先是比對集合是否等於 `UPPER_BODY`（改關鍵點就壞），
     * 再是問「要不要腳踝」（原地高抬腿為了不讓抬起那腳的踝拖累品質檢查
     * 刻意不宣告踝，會被誤判成上肢動作）。這條測試逐一驗證六個動作都分對。
     */
    @Test
    fun `六個動作都分流到正確的框位判準`() {
        val upperBodyExercises = setOf(ExerciseType.ARM_RAISE, ExerciseType.CHEST_EXPANSION)
        ExerciseType.entries.forEach { exercise ->
            if (exercise in upperBodyExercises) {
                // 上肢判準：沒有腳踝也該是 OK
                assertEquals(
                    "${exercise.name} 應走上肢判準",
                    FramingIssue.OK,
                    evaluateFraming(upperBody(), exercise)
                )
            } else {
                // 下肢判準：缺腳踝要提示，而不是去檢查手腕
                assertEquals(
                    "${exercise.name} 應走下肢判準",
                    FramingIssue.OK,
                    evaluateFraming(lowerBody(), exercise)
                )
                val noAnkles = frame(
                    kp(KeyPointType.LEFT_HIP, 320f, 600f),
                    kp(KeyPointType.RIGHT_HIP, 400f, 600f),
                )
                assertEquals(
                    "${exercise.name} 缺腳踝應提示 MISSING_ANKLE",
                    FramingIssue.MISSING_ANKLE,
                    evaluateFraming(noAnkles, exercise)
                )
            }
        }
    }

    /**
     * 踮腳尖的位移只有腿長的 6~8%，**訊噪比對拍攝距離極度敏感** ——
     * 實測腿長 226px 時只有 7.1、277px 時是 25.0。
     * 一般下肢動作的 0.20 門檻完全擋不住前者（226/640 = 0.35 照樣通過），
     * 所以踮腳尖另有一個 0.40 的下限。
     *
     * 這條測試鎖住「同一個距離，深蹲可以、踮腳尖不行」。
     */
    @Test
    fun `踮腳尖的距離下限比其他下肢動作嚴`() {
        // 腿長 500px = 畫面高的 39%：深蹲沒問題
        val borderline = lowerBody(hipY = 600f, ankleY = 1100f)
        assertEquals(FramingIssue.OK, evaluateFraming(borderline, ExerciseType.SQUAT))
        assertEquals(
            "踮腳尖在這個距離應請使用者往前站",
            FramingIssue.TOO_FAR,
            evaluateFraming(borderline, ExerciseType.HEEL_RAISE)
        )
        // 腿長 550px = 43%（實機驗證通過的那場）：兩者都該良好
        assertEquals(FramingIssue.OK, evaluateFraming(lowerBody(), ExerciseType.HEEL_RAISE))
    }

    @Test
    fun `沒有偵測到任何姿態時回報沒有偵測到人`() {
        assertEquals(FramingIssue.NO_POSE, evaluateFraming(null, ExerciseType.SQUAT))
        assertEquals(FramingIssue.NO_POSE, evaluateFraming(null, ExerciseType.ARM_RAISE))
    }
}

package com.heartchen.squat.pose

import com.heartchen.squat.squat.ExerciseType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        val issue = evaluateFraming(lowConfidenceWrists, ExerciseType.ARM_RAISE)
        // 這條測試的重點是**不可以**拿那個亂猜的 y=5 去判「手舉出畫面上緣」。
        // 2026-10-05 我一度改成讀原始座標來分辨「舉太高」與「站太遠」，
        // 這條就立刻紅了 —— 而它是對的：低信心值的座標本身就不能當依據。
        assertTrue(
            "不可因為亂猜的座標報 WRIST_NEAR_TOP_EDGE，實際得到 $issue",
            issue != FramingIssue.WRIST_NEAR_TOP_EDGE
        )
        // 手腕關鍵點存在、只是追不準 → 站近一點才會準。
        assertEquals(FramingIssue.TOO_FAR, issue)
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
                // 缺腳踝只對**宣告需要腳踝**的動作才算問題。
                // 高抬腿與踮腳尖刻意不宣告腳踝，拿它報「請往後站一點」是錯的指示
                // （2026-10-05 實機被念了 1.8 秒，而站位完全正確）。
                val declaresAnkle = KeyPointType.LEFT_ANKLE in exercise.requiredPoints
                assertEquals(
                    "${exercise.name} 缺腳踝時的判定不對",
                    if (declaresAnkle) FramingIssue.MISSING_ANKLE else FramingIssue.OK,
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

    // ---- 關鍵點「在畫面裡但信心值不足」要給對方向的建議 ----
    //
    // 2026-10-05 實機：雙臂高舉站太遠，手腕信心值掉到門檻下，整場 0 下。
    // 使用者得到的（修好靜默問題後）會是「請讓雙手入鏡」—— 手明明在畫面裡，
    // 建議的方向還是錯的。正確的動作是往前站。

    @Test
    fun `手腕在畫面裡但信心值不足時請使用者往前站`() {
        val cx = width / 2f
        val far = frame(
            kp(KeyPointType.LEFT_SHOULDER, cx - 70f, 400f),
            kp(KeyPointType.RIGHT_SHOULDER, cx + 70f, 400f),
            // 手腕垂在畫面 55% 處，位置完全正常，只是信心值追不上
            KeyPoint(KeyPointType.LEFT_WRIST, cx - 70f, 700f, 0.2f),
            KeyPoint(KeyPointType.RIGHT_WRIST, cx + 70f, 700f, 0.2f),
        )
        assertEquals(
            "手在畫面裡卻追不準 → 站近一點才會準",
            FramingIssue.TOO_FAR,
            evaluateFraming(far, ExerciseType.ARM_RAISE)
        )
    }

    @Test
    fun `舉出畫面上緣只有在手腕信心值足夠時才判定`() {
        val cx = width / 2f
        // 信心值足夠：座標可信，y=40（畫面 3%）就是真的舉出去了
        val trusted = frame(
            kp(KeyPointType.LEFT_SHOULDER, cx - 70f, 400f),
            kp(KeyPointType.RIGHT_SHOULDER, cx + 70f, 400f),
            kp(KeyPointType.LEFT_WRIST, cx - 70f, 40f),
            kp(KeyPointType.RIGHT_WRIST, cx + 70f, 40f),
        )
        assertEquals(
            FramingIssue.WRIST_NEAR_TOP_EDGE,
            evaluateFraming(trusted, ExerciseType.ARM_RAISE)
        )
    }

    @Test
    fun `完全沒有手腕關鍵點時才說請讓雙手入鏡`() {
        val cx = width / 2f
        val noWrist = frame(
            kp(KeyPointType.LEFT_SHOULDER, cx - 70f, 400f),
            kp(KeyPointType.RIGHT_SHOULDER, cx + 70f, 400f),
        )
        assertEquals(
            FramingIssue.MISSING_WRIST,
            evaluateFraming(noWrist, ExerciseType.ARM_RAISE)
        )
    }

    /**
     * 原地高抬腿刻意不宣告腳踝（抬起那腳的踝必然掉信心值）。
     * 但框位檢查仍拿腳踝報問題：2026-10-05 的逐幀資料裡，訓練中有 **39 連續幀**
     * 報 `MISSING_ANKLE`（門檻只要 8 幀），使用者被念了約 1.8 秒的
     * 「請往後站一點」—— 而他站的位置完全正確。
     *
     * 與雙臂高舉那個「請讓雙手入鏡」同一類：拿一個這個動作根本不需要的關鍵點，
     * 去報一個不存在的問題。
     */
    @Test
    fun `高抬腿的腳踝不可信時不該報請往後站`() {
        val liftedFoot = frame(
            kp(KeyPointType.LEFT_HIP, 320f, 600f),
            kp(KeyPointType.RIGHT_HIP, 400f, 600f),
            kp(KeyPointType.LEFT_KNEE, 320f, 850f),
            kp(KeyPointType.RIGHT_KNEE, 400f, 850f),
            // 一腳剛離地，踝的信心值掉到門檻下
            KeyPoint(KeyPointType.LEFT_ANKLE, 320f, 1100f, 0.3f),
            KeyPoint(KeyPointType.RIGHT_ANKLE, 400f, 1100f, 0.3f),
        )
        assertEquals(
            "高抬腿不宣告腳踝，所以腳踝不可信不是框位問題",
            FramingIssue.OK,
            evaluateFraming(liftedFoot, ExerciseType.HIGH_KNEES)
        )
        // 同一幀對深蹲仍然是問題 —— 它宣告了腳踝，判定必須依賴它
        assertEquals(
            "深蹲宣告腳踝，行為不可改變",
            FramingIssue.MISSING_ANKLE,
            evaluateFraming(liftedFoot, ExerciseType.SQUAT)
        )
    }

    // ---- 腳踝不可信時改用其他下肢點換算腿長 ----
    //
    // 不宣告腳踝的兩個動作（原地高抬腿、踮腳尖）原本在腳踝不可信時一律回 OK，
    // 遠近判斷整個消失。使用者回報「偵測人物距離可否修正正確一點」——
    // 跳過依賴不可信點的檢查是對的，「無話可說」不是。
    //
    // 換算係數（Config.LEG_SPAN_FROM_*）由三段逐幀錄影量出，下面幾條測試的
    // 座標都照實測數值換算到 height=1280，不是湊出來的。

    /**
     * 10-05 實機錄影：訓練中腳踝不可信的 29 幀裡，52% 原始踝座標已過畫面下緣，
     * 髖→膝從靜止的 118px 漲到 206~246px（H=640）—— 使用者是真的走近手機了。
     * 換算到 H=1280 即髖→膝 464px，腿長推估 883px = 69%，遠超 0.60 的太近門檻。
     *
     * 這一幀原本（回 OK）是沉默的，而正確答案是請他往後站。
     */
    @Test
    fun `高抬腿腳踝不可信但人站太近時仍要提示往後站`() {
        val tooClose = frame(
            kp(KeyPointType.LEFT_HIP, 320f, 600f),
            kp(KeyPointType.RIGHT_HIP, 400f, 600f),
            kp(KeyPointType.LEFT_KNEE, 320f, 1064f),
            kp(KeyPointType.RIGHT_KNEE, 400f, 1064f),
            // 踝已被下緣裁掉，信心值掉到門檻下
            KeyPoint(KeyPointType.LEFT_ANKLE, 320f, 1290f, 0.2f),
            KeyPoint(KeyPointType.RIGHT_ANKLE, 400f, 1290f, 0.2f),
        )
        assertEquals(
            "髖→膝 464px 換算腿長 69% 畫面高，這是真的太近",
            FramingIssue.TOO_CLOSE,
            evaluateFraming(tooClose, ExerciseType.HIGH_KNEES)
        )
    }

    /**
     * 10-02 實機錄影的對照組：同樣是腳踝不可信，但原始踝座標沒有一幀出界，
     * 膝換算得到 0.494~0.578 —— 腳只是離地，站位正確，**就該安靜**。
     *
     * 這條與上一條合起來才是重點：換算值要分得開「抬腳」與「站太近」，
     * 只會其中一邊的話不如不做。
     */
    @Test
    fun `高抬腿腳踝不可信且站位正確時維持安靜`() {
        val liftedButFine = frame(
            kp(KeyPointType.LEFT_HIP, 320f, 600f),
            kp(KeyPointType.RIGHT_HIP, 400f, 600f),
            // 髖→膝 348px → 腿長推估 663px = 52% 畫面高，落在 0.20~0.60 之間
            kp(KeyPointType.LEFT_KNEE, 320f, 948f),
            kp(KeyPointType.RIGHT_KNEE, 400f, 948f),
            KeyPoint(KeyPointType.LEFT_ANKLE, 320f, 1150f, 0.3f),
            KeyPoint(KeyPointType.RIGHT_ANKLE, 400f, 1150f, 0.3f),
        )
        assertEquals(
            FramingIssue.OK,
            evaluateFraming(liftedButFine, ExerciseType.HIGH_KNEES)
        )
    }

    /** 踮腳尖宣告的是腳尖，腳踝不可信時就用腳尖換算，且仍適用較嚴的 0.40 下限。 */
    @Test
    fun `踮腳尖腳踝不可信時用腳尖換算遠近`() {
        fun withToes(toeY: Float) = frame(
            kp(KeyPointType.LEFT_HIP, 320f, 600f),
            kp(KeyPointType.RIGHT_HIP, 400f, 600f),
            kp(KeyPointType.LEFT_TOE, 320f, toeY),
            kp(KeyPointType.RIGHT_TOE, 400f, toeY),
            KeyPoint(KeyPointType.LEFT_ANKLE, 320f, toeY - 40f, 0.3f),
            KeyPoint(KeyPointType.RIGHT_ANKLE, 400f, toeY - 40f, 0.3f),
        )
        // 髖→腳尖 654px → 腿長推估 554px = 43%（實機驗證通過的那場距離）
        assertEquals(
            "實機驗證通過的距離不該被判太遠",
            FramingIssue.OK,
            evaluateFraming(withToes(1254f), ExerciseType.HEEL_RAISE)
        )
        // 髖→腳尖 529px → 腿長推估 448px = 35%，低於踮腳尖的 0.40 下限
        assertEquals(
            "踮腳尖在 35% 腿長的距離訊噪比不足，應請使用者往前站",
            FramingIssue.TOO_FAR,
            evaluateFraming(withToes(1129f), ExerciseType.HEEL_RAISE)
        )
    }

    /**
     * 迴歸：腳踝**可信**時換算一律不得介入。
     *
     * 驗證資料說腳踝可信的 1481 幀裡換算與直接量測判定一致率 100%，
     * 但那是統計；這裡要的是結構保證 —— 給一個離譜的膝座標，
     * 只要腳踝可信，判定就必須完全由腳踝決定。
     */
    @Test
    fun `腳踝可信時換算不介入判斷`() {
        val absurdKnee = frame(
            kp(KeyPointType.LEFT_HIP, 320f, 600f),
            kp(KeyPointType.RIGHT_HIP, 400f, 600f),
            // 這個膝座標換算出來是 1219px = 95% 畫面高，會被判太近
            kp(KeyPointType.LEFT_KNEE, 320f, 1240f),
            kp(KeyPointType.RIGHT_KNEE, 400f, 1240f),
            // 但腳踝可信，腿長 550px = 43%，答案必須是 OK
            kp(KeyPointType.LEFT_ANKLE, 320f, 1150f),
            kp(KeyPointType.RIGHT_ANKLE, 400f, 1150f),
        )
        ExerciseType.entries
            .filter { KeyPointType.LEFT_WRIST !in it.requiredPoints }
            .forEach { exercise ->
                assertEquals(
                    "${exercise.name}：腳踝可信時不該受膝座標影響",
                    FramingIssue.OK,
                    evaluateFraming(absurdKnee, exercise)
                )
            }
    }

    /** 髖以下一個可信的點都沒有時不猜，維持 OK（真的卡住由 STUCK 接手）。 */
    @Test
    fun `髖以下完全沒有可信點時不猜遠近`() {
        val hipsOnly = frame(
            kp(KeyPointType.LEFT_HIP, 320f, 600f),
            kp(KeyPointType.RIGHT_HIP, 400f, 600f),
        )
        assertEquals(FramingIssue.OK, evaluateFraming(hipsOnly, ExerciseType.HIGH_KNEES))
        assertEquals(FramingIssue.OK, evaluateFraming(hipsOnly, ExerciseType.HEEL_RAISE))
    }

    @Test
    fun `沒有偵測到任何姿態時回報沒有偵測到人`() {
        assertEquals(FramingIssue.NO_POSE, evaluateFraming(null, ExerciseType.SQUAT))
        assertEquals(FramingIssue.NO_POSE, evaluateFraming(null, ExerciseType.ARM_RAISE))
    }
}

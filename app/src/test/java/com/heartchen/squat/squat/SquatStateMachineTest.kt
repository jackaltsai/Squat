package com.heartchen.squat.squat

import com.heartchen.squat.config.Config
import com.heartchen.squat.pose.KeyPoint
import com.heartchen.squat.pose.KeyPointType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * 狀態機計次測試（純 JVM，`./gradlew test` 可跑）。
 *
 * 這些測試是為了「坐站練習」而寫的。坐站與深蹲的軌跡同為髖部垂直位移，
 * 共用同一個狀態機，但它多了兩個深蹲不會出現的情境：
 *
 * 1. **慢速起身** —— 長輩從椅子站起來很慢，每幀位移接近關鍵點雜訊量級
 * 2. **坐在椅面上停頓** —— 停頓期間髖部高度是平的
 *
 * 舊版的轉折判據是「這一幀比上一幀高」，兩種情境都會壞掉（見下方各測試的註解）。
 * [Config.TURN_CONFIRM_RISE_RATIO] 把判據換成「距離本次最低點的回升量」後才成立。
 */
class SquatStateMachineTest {

    /** 站立基準取 0、正規化尺度取 1，如此 hipY 的數值就等於 depthRatio，斷言可以直接讀。 */
    private fun machine() = SquatStateMachine(standBaselineY = 0f, normalizeScale = 1f)

    /** 狀態機只讀左右髖的 Y，其餘關鍵點不影響判定，所以這裡只餵髖部。 */
    private fun hip(y: Float): Map<KeyPointType, KeyPoint> = mapOf(
        KeyPointType.LEFT_HIP to KeyPoint(KeyPointType.LEFT_HIP, 0.4f, y, 0.9f),
        KeyPointType.RIGHT_HIP to KeyPoint(KeyPointType.RIGHT_HIP, 0.6f, y, 0.9f),
    )

    private fun SquatStateMachine.feed(ys: List<Float>) = ys.forEach { update(hip(it)) }

    /** 回到站立高度並停留足夠幀數，讓 UP → STAND 完成計次。 */
    private fun SquatStateMachine.returnToStand() {
        feed(List(Config.STAND_STABLE_FRAMES + 3) { 0f })
    }

    // ---- 迴歸保護：深蹲原本的行為不可改變 ----

    @Test
    fun `正常速度的深蹲計為一下`() {
        val m = machine()
        m.feed(listOf(0f, 0f, 0.05f, 0.15f, 0.30f, 0.45f, 0.50f)) // 下降
        m.feed(listOf(0.46f, 0.40f, 0.30f, 0.18f, 0.08f, 0.02f))  // 起身
        m.returnToStand()

        assertEquals(1, m.repCount)
        assertEquals(SquatState.STAND, m.state)
    }

    @Test
    fun `連續三下深蹲計為三下`() {
        val m = machine()
        repeat(3) {
            m.feed(listOf(0.05f, 0.20f, 0.40f, 0.50f))
            m.feed(listOf(0.44f, 0.30f, 0.15f, 0.02f))
            m.returnToStand()
        }
        assertEquals(3, m.repCount)
    }

    @Test
    fun `站立時的雜訊晃動不計次`() {
        val m = machine()
        val rng = Random(42)
        // 站著小幅晃動，振幅遠小於 DOWN_ENTER_RATIO，不應該進入 DOWN
        repeat(300) { m.update(hip((rng.nextFloat() - 0.5f) * 0.06f)) }

        assertEquals(0, m.repCount)
        assertEquals(SquatState.STAND, m.state)
    }

    @Test
    fun `深度取本次下降過程的最低點，與轉折何時被確認無關`() {
        val m = machine()
        m.feed(listOf(0.10f, 0.30f, 0.62f, 0.55f)) // 最低點是 0.62，之後才開始回升
        m.feed(listOf(0.50f, 0.45f, 0.40f, 0.30f))
        m.returnToStand()

        assertEquals(0.62f, m.lastBottomDepthRatio!!, 1e-4f)
    }

    // ---- 坐站練習的兩個新情境 ----

    /**
     * 慢速起身。
     *
     * 每幀上升 1/256，雜訊 ±1/512 逐幀交替，因此相鄰幀的差值在「下降 3/512」與
     * 「完全不動」之間交替，永遠不會有連續兩幀嚴格下降。舊判據要求「連續 3 幀
     * 嚴格比上一幀高」，平手的那幾幀會讓計數歸零，整下不會被計次
     * （實測舊版在此序列下停在 DOWN、repCount = 0）。
     *
     * 位移量刻意取 2 的冪次：Float 能精確表示，平手才是真的平手。
     * 用 0.004 / 0.002 這種十進位值時，float32 的捨入誤差會把平手變成微小下降，
     * 測試就測不到要測的東西了。
     */
    @Test
    fun `慢速起身仍然計為一下`() {
        val m = machine()
        m.feed(listOf(0.05f, 0.20f, 0.40f, 0.50f))

        val slowAscent = (0 until 140).map { i ->
            0.5f - i / 256f + if (i % 2 == 0) 1 / 512f else -1 / 512f
        }
        m.feed(slowAscent)
        m.returnToStand()

        assertEquals(1, m.repCount)
    }

    /**
     * 坐在椅面上停頓。
     *
     * 停頓期間髖部高度只有雜訊在動。舊判據下，隨機雜訊遲早會湊出 3 幀連續「下降」，
     * 在人還坐著的時候就誤觸發最低點；新判據以「距離最低點的回升量」為準，
     * 停頓期間回升量始終遠小於門檻，不會觸發。
     */
    @Test
    fun `坐在椅面上停頓期間不會誤觸發最低點`() {
        val m = machine()
        m.feed(listOf(0.05f, 0.25f, 0.45f, 0.55f))

        val rng = Random(7)
        repeat(120) {
            val state = m.update(hip(0.55f + (rng.nextFloat() - 0.5f) * 0.01f))
            assertEquals("坐著停頓時不應離開 DOWN", SquatState.DOWN, state)
        }

        // 真的站起來之後才該計次
        m.feed(listOf(0.48f, 0.40f, 0.28f, 0.15f, 0.05f))
        m.returnToStand()
        assertEquals(1, m.repCount)
        // 深度應該落在停頓時的實際最低點附近，不是停頓中段的某個雜訊值
        assertTrue(
            "深度 ${m.lastBottomDepthRatio} 應接近 0.555",
            m.lastBottomDepthRatio!! in 0.550f..0.560f
        )
    }

    /**
     * 坐站的完整一下：下降 → 坐定停頓 → 慢速起身。
     * 這是長輩實際做坐站練習時最常見的軌跡。
     */
    @Test
    fun `下降加停頓加慢速起身計為一下`() {
        val m = machine()
        val rng = Random(11)

        m.feed(listOf(0.06f, 0.18f, 0.34f, 0.48f, 0.56f))
        repeat(90) { m.update(hip(0.56f + (rng.nextFloat() - 0.5f) * 0.012f)) }
        m.feed(
            (0 until 150).map { i ->
                0.56f - 0.004f * i + (rng.nextFloat() - 0.5f) * 0.006f
            }.map { it.coerceAtLeast(0f) }
        )
        m.returnToStand()

        assertEquals(1, m.repCount)
        assertEquals(SquatState.STAND, m.state)
        // 深度須反映坐定時的實際最低點（0.56 加上雜訊），
        // 而不是停頓途中被雜訊誤判出來的某個較淺的值
        assertTrue(
            "深度 ${m.lastBottomDepthRatio} 應接近 0.566",
            m.lastBottomDepthRatio!! in 0.560f..0.572f
        )
    }

    @Test
    fun `半蹲沒有到達下蹲門檻時不計次`() {
        val m = machine()
        repeat(5) {
            // 振幅 0.07 < DOWN_ENTER_RATIO 0.08
            m.feed(listOf(0.03f, 0.07f, 0.03f, 0f))
        }
        assertEquals(0, m.repCount)
    }
}

package com.heartchen.squat.squat

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap

/**
 * 六個動作的圖示，以 Canvas 畫線條人物。
 *
 * 用向量繪製而非點陣圖，是因為要在 3×3 格線裡隨螢幕寬度縮放，
 * 且尚未實作的動作要能整組變灰（只換 tint，不必準備兩套圖檔）。
 *
 * App 圖示是 3D 算圖的白色人偶，線條圖無法重現那個質感；
 * 但六個並排時，一致的線條風格反而比六張各自算圖的人偶更耐看、也更好辨識。
 *
 * 座標一律用 0..1 正規化（y 軸向下），實際繪製時對映到畫布的正方形內接區域，
 * 所以同一組座標在任何尺寸下比例都不變。
 */
@Composable
fun ExerciseIcon(
    type: ExerciseType,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val side = size.minDimension
        val ox = (size.width - side) / 2f
        val oy = (size.height - side) / 2f
        val stroke = side * 0.055f

        fun at(x: Float, y: Float) = Offset(ox + x * side, oy + y * side)

        fun path(vararg points: Pair<Float, Float>) {
            for (i in 0 until points.size - 1) {
                drawLine(
                    color = tint,
                    start = at(points[i].first, points[i].second),
                    end = at(points[i + 1].first, points[i + 1].second),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
            }
        }

        fun head(x: Float, y: Float, r: Float = 0.085f) {
            drawCircle(color = tint, radius = r * side, center = at(x, y))
        }

        fun ground(y: Float = 0.87f) {
            drawLine(
                color = tint.copy(alpha = 0.45f),
                start = at(0.18f, y), end = at(0.82f, y),
                strokeWidth = stroke * 0.7f, cap = StrokeCap.Round
            )
        }

        /** 椅子：坐站練習用，側視。 */
        fun chair() {
            path(0.56f to 0.60f, 0.86f to 0.60f)          // 椅面
            path(0.86f to 0.60f, 0.86f to 0.30f)          // 椅背
            path(0.60f to 0.60f, 0.60f to 0.86f)          // 前腳
            path(0.84f to 0.60f, 0.84f to 0.86f)          // 後腳
        }

        /** 向上箭頭，用來表示「站起來」或「踮起來」的方向。 */
        fun arrowUp(x: Float, yBottom: Float, yTop: Float) {
            path(x to yBottom, x to yTop)
            path(x - 0.05f to yTop + 0.06f, x to yTop, x + 0.05f to yTop + 0.06f)
        }

        when (type) {
            ExerciseType.SQUAT -> {
                head(0.40f, 0.19f)
                path(0.40f to 0.28f, 0.36f to 0.50f)                  // 軀幹
                path(0.38f to 0.33f, 0.58f to 0.37f, 0.72f to 0.35f)  // 前伸的手臂
                path(0.36f to 0.50f, 0.60f to 0.56f, 0.58f to 0.82f)  // 大腿 + 小腿
                path(0.50f to 0.82f, 0.68f to 0.82f)                  // 腳掌
                ground()
            }

            ExerciseType.CHAIR_SQUAT -> {
                chair()
                head(0.30f, 0.21f, r = 0.075f)
                path(0.30f to 0.29f, 0.27f to 0.49f)
                path(0.29f to 0.33f, 0.46f to 0.37f)
                path(0.27f to 0.49f, 0.48f to 0.55f, 0.46f to 0.83f)
                ground()
            }

            ExerciseType.HIGH_KNEES -> {
                head(0.50f, 0.17f)
                path(0.50f to 0.26f, 0.50f to 0.54f)
                path(0.50f to 0.32f, 0.34f to 0.40f, 0.36f to 0.54f)  // 擺動的手臂
                path(0.50f to 0.32f, 0.66f to 0.36f, 0.64f to 0.24f)
                path(0.50f to 0.54f, 0.58f to 0.70f, 0.57f to 0.86f)  // 支撐腳
                path(0.50f to 0.54f, 0.34f to 0.50f, 0.30f to 0.68f)  // 抬起的腿
                ground()
            }

            ExerciseType.HEEL_RAISE -> {
                head(0.50f, 0.18f)
                path(0.50f to 0.27f, 0.50f to 0.56f)
                path(0.50f to 0.33f, 0.37f to 0.50f)
                path(0.50f to 0.33f, 0.63f to 0.50f)
                path(0.50f to 0.56f, 0.42f to 0.76f, 0.38f to 0.86f)  // 腳跟抬起
                path(0.50f to 0.56f, 0.58f to 0.76f, 0.62f to 0.86f)
                arrowUp(0.80f, 0.56f, 0.34f)
                ground()
            }

            ExerciseType.ARM_RAISE -> {
                head(0.50f, 0.20f)
                path(0.50f to 0.29f, 0.50f to 0.58f)
                path(0.50f to 0.34f, 0.34f to 0.24f, 0.29f to 0.09f)  // 雙臂高舉
                path(0.50f to 0.34f, 0.66f to 0.24f, 0.71f to 0.09f)
                path(0.50f to 0.58f, 0.41f to 0.86f)
                path(0.50f to 0.58f, 0.59f to 0.86f)
                ground()
            }

            ExerciseType.CHEST_EXPANSION -> {
                head(0.50f, 0.18f)
                path(0.50f to 0.27f, 0.50f to 0.58f)
                path(0.50f to 0.34f, 0.30f to 0.34f, 0.25f to 0.19f)  // 手肘外展、前臂上抬
                path(0.50f to 0.34f, 0.70f to 0.34f, 0.75f to 0.19f)
                path(0.50f to 0.58f, 0.41f to 0.86f)
                path(0.50f to 0.58f, 0.59f to 0.86f)
                ground()
            }

        }
    }
}

package com.heartchen.squat.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        // ⚠️ 這裡原本是 `lineHeight = 24.sp`（絕對值），而那是**整個 App 文字重疊的根因**。
        //
        // Material3 的 `Text` 沒有傳 `style` 時吃的是 `LocalTextStyle.current`，
        // 也就是這個 `bodyLarge`。於是全專案 17 個只覆寫 `fontSize` 的 `Text`
        // （26~96sp）**行距全都還是 24dp** —— 字高 56dp 的兩行文字會疊掉 30dp 以上。
        //
        // 2026-10-12 實機：`DepthFeedback.SUSPECT` 的「請站回原來位置」是 7 個字、
        // 56sp，在手機寬度下必然換行，兩行直接糊在一起。
        // 使用者的要求是「調整上下間距，文字大小不要變小，因為長者使用怕看不到文字」——
        // 所以修的是行距，不是字級。
        //
        // 改用 **em（相對 fontSize）**：`1.5.em` 在 16sp 時正好 = 24sp，
        // **原本的 body 文字行距逐像素不變**，而每一個放大的覆寫都自動跟著放大。
        // 一行改掉 17 處，而不是在 17 個 call site 各寫一次 `lineHeight`
        // —— 後者就是這個專案栽過好幾次的「同一件事記在很多處」。
        lineHeight = 1.5.em,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)
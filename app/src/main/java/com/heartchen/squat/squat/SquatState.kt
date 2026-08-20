package com.heartchen.squat.squat

/** 深蹲計次的五階段狀態機（STAND 同時是起點，也是計次完成後的終點）。 */
enum class SquatState {
    STAND, DOWN, BOTTOM, UP
}

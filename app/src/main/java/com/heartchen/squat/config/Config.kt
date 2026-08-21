package com.heartchen.squat.config

/**
 * 集中管理品質控管與狀態機的可調參數，方便之後用驗證集試門檻調參（見 CLAUDE.md 第 6 節）。
 * 目前皆為初始建議值，尚未經實測調參。
 */
object Config {
    /** 關鍵點信心值門檻，低於此值視為不可靠。 */
    const val CONFIDENCE_THRESHOLD = 0.6f

    /** EMA 平滑係數，越大代表越接近原始值（反應快但抖動大），越小代表越平滑（反應慢）。 */
    const val EMA_ALPHA = 0.3f

    /** 髖部相對站立基準的下降比例（以「髖-踝」垂直距離正規化）超過此值視為開始下蹲。 */
    const val DOWN_ENTER_RATIO = 0.08f

    /** 髖部下降比例低於此值視為已回到站立高度附近。 */
    const val STAND_RETURN_RATIO = 0.04f

    /** DOWN 狀態下，髖部須連續上升達此幀數才確認為最低點轉折。 */
    const val TURN_CONFIRM_FRAMES = 3

    /** UP 狀態下，須連續穩定在站立高度附近此幀數才計次並回到 STAND。 */
    const val STAND_STABLE_FRAMES = 5

    /** M3 站姿校正：需連續收集此毫秒數的樣本，取平均作為站立基準髖部高度。 */
    const val STAND_HOLD_DURATION_MS = 3000L

    /** M3 基準深蹲校正：需完成幾次深蹲取平均深度作為 Duser。 */
    const val CALIBRATION_SQUAT_REPS = 2

    /** 框位引導：髖-踝垂直距離佔畫面高度比例，低於此值視為拍攝距離太遠。 */
    const val FRAMING_TOO_FAR_RATIO = 0.20f

    /** 框位引導：髖-踝垂直距離佔畫面高度比例，高於此值視為拍攝距離太近。 */
    const val FRAMING_TOO_CLOSE_RATIO = 0.60f

    /** 框位引導：腳踝 Y 座標超過畫面高度此比例，視為腳踝可能快被裁到畫面外，需請使用者調整。 */
    const val FRAMING_ANKLE_NEAR_EDGE_RATIO = 0.97f

    /** 框位引導：髖部 Y 座標低於畫面高度此比例，視為上半身可能被裁掉，需請使用者調整。 */
    const val FRAMING_HIP_NEAR_TOP_RATIO = 0.08f

    /** M4 膝內夾判定：（踝距 - 膝距）除以髖寬，超過此比例視為膝蓋內夾，需後續用驗證集調參。 */
    const val KNEE_VALGUS_RATIO_THRESHOLD = 0.15f
}

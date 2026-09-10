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

    /** 校正完成後、正式訓練開始前的倒數秒數（畫面顯示大字並語音念出 3、2、1）。 */
    const val READY_COUNTDOWN_SECONDS = 3

    /**
     * M3 基準深蹲校正品質檢查：兩次校正深蹲的深度全距除以平均，超過此比例視為校正不可信，要求重做。
     *
     * Duser 只取 2 下的平均，只要其中一下試探性地淺蹲，基準就會被拉低，之後每一下的 p 值
     * 都整體偏高、假性達標（2026-09-10 實測：同一人相隔 3 分鐘的兩場，p 平均 0.98 vs 1.25）。
     * 這個門檻是初始值，需後續用驗證集調參。
     */
    const val CALIBRATION_MAX_DEPTH_SPREAD = 0.15f

    /** 校正品質檢查最多重做幾次；超過仍不一致就採用當下結果，避免使用者卡在校正出不去。 */
    const val CALIBRATION_MAX_RETRIES = 2

    /** 框位引導：髖-踝垂直距離佔畫面高度比例，低於此值視為拍攝距離太遠。 */
    const val FRAMING_TOO_FAR_RATIO = 0.20f

    /** 框位引導：髖-踝垂直距離佔畫面高度比例，高於此值視為拍攝距離太近。 */
    const val FRAMING_TOO_CLOSE_RATIO = 0.60f

    /** 框位引導：腳踝 Y 座標超過畫面高度此比例，視為腳踝可能快被裁到畫面外，需請使用者調整。 */
    const val FRAMING_ANKLE_NEAR_EDGE_RATIO = 0.97f

    /** 框位引導：髖部 Y 座標低於畫面高度此比例，視為上半身可能被裁掉，需請使用者調整。 */
    const val FRAMING_HIP_NEAR_TOP_RATIO = 0.08f

    /** 框位引導：同一個問題須連續出現此幀數才算數，避免深蹲快速移動時單幀關鍵點掉點造成誤報。 */
    const val FRAMING_STABLE_FRAMES = 8

    /** M4 膝內夾判定：（踝距 - 膝距）除以髖寬，超過此比例視為膝蓋內夾，需後續用驗證集調參。 */
    const val KNEE_VALGUS_RATIO_THRESHOLD = 0.15f

    /** M4 膝內夾判定：髖寬須至少達腿長的此比例，太小（如側身）視為角度不可靠、不判定。 */
    const val KNEE_VALGUS_MIN_HIP_WIDTH_TO_LEG_RATIO = 0.2f
}

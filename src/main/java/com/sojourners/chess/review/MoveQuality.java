package com.sojourners.chess.review;

import java.math.BigDecimal;

/**
 * 复盘时对每一步着法的评价等级。
 * 参考 chess.com 的分类：最佳 / 优秀 / 良好 / 不精确 / 失误 / 大漏。
 */
public enum MoveQuality {

    NONE("——", 0, ""),
    BEST("最佳", 1, "review-best"),
    EXCELLENT("优秀", 2, "review-excellent"),
    GOOD("良好", 3, "review-good"),
    INACCURACY("不精确", 4, "review-inaccuracy"),
    MISTAKE("失误", 5, "review-mistake"),
    BLUNDER("大漏", 6, "review-blunder");

    private final String label;
    private final int level;
    private final String styleClass;

    MoveQuality(String label, int level, String styleClass) {
        this.label = label;
        this.level = level;
        this.styleClass = styleClass;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 等级，数值越大问题越严重，0 表示未复盘。
     */
    public int getLevel() {
        return level;
    }

    public String getStyleClass() {
        return styleClass;
    }

    /**
     * 根据损失分数（厘兵，越小越好）对非最佳着法进行分级。
     */
    public static MoveQuality byLoss(int loss) {
        if (loss <= 20) {
            return EXCELLENT;
        } else if (loss <= 60) {
            return GOOD;
        } else if (loss <= 150) {
            return INACCURACY;
        } else if (loss <= 400) {
            return MISTAKE;
        } else {
            return BLUNDER;
        }
    }

    /** 胜率下降 ≤ 3%：优秀 */
    private static final BigDecimal WIN_RATE_DROP_EXCELLENT = new BigDecimal("3.0");
    /** 胜率下降 ≤ 8%：良好 */
    private static final BigDecimal WIN_RATE_DROP_GOOD = new BigDecimal("8.0");
    /** 胜率下降 ≤ 15%：不够精确 */
    private static final BigDecimal WIN_RATE_DROP_INACCURACY = new BigDecimal("15.0");
    /** 胜率下降 ≤ 25%：失误 */
    private static final BigDecimal WIN_RATE_DROP_MISTAKE = new BigDecimal("25.0");

    /**
     * 根据走子方视角的分数变化对非最佳着法进行分级。
     * <p>
     * 入参应为皮卡鱼的 <b>Elo 胜率分</b>（ScoreType = Elo）。
     * 该方法先通过 ELO 公式把分数转成胜率，再用“胜率下降幅度”分级，
     * 从而避免在胜势/败势局面下，因为固定分数损失阈值而产生的误判。
     * <p>
     * 例如：-2000 → -2500 虽然损失了 500 分，但胜率几乎没变，
     * 会被判为 EXCELLENT 而非 BLUNDER。
     *
     * @param winRateDrop 走这步棋之后，损失的胜率
     */
    public static MoveQuality byWinRatio(BigDecimal winRateDrop) {
        if (winRateDrop.compareTo(WIN_RATE_DROP_EXCELLENT) <= 0) {
            return EXCELLENT;
        } else if (winRateDrop.compareTo(WIN_RATE_DROP_GOOD) <= 0) {
            return GOOD;
        } else if (winRateDrop.compareTo(WIN_RATE_DROP_INACCURACY) <= 0) {
            return INACCURACY;
        } else if (winRateDrop.compareTo(WIN_RATE_DROP_MISTAKE) <= 0) {
            return MISTAKE;
        } else {
            return BLUNDER;
        }
    }
}

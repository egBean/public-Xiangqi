package com.sojourners.chess.review;

import java.util.EnumMap;
import java.util.Map;

/**
 * 复盘汇总：双方各类着法的数量和准确率。
 */
public class ReviewSummary {

    private final Map<MoveQuality, Integer> redCounts = new EnumMap<>(MoveQuality.class);

    private final Map<MoveQuality, Integer> blackCounts = new EnumMap<>(MoveQuality.class);

    private double redAccuracySum;

    private int redMoves;

    private double blackAccuracySum;

    private int blackMoves;

    /**
     * 记录一步棋的复盘结果。
     *
     * @param red          是否红方走子
     * @param quality      评价等级
     * @param scoreBefore  走子前该方视角的分值
     * @param scoreAfter   走子后该方实际获得的分值
     */
    public void add(boolean red, MoveQuality quality, int scoreBefore, int scoreAfter) {
        if (quality == null || quality == MoveQuality.NONE) {
            // 胜负已定、未评价的着法不参与统计
            return;
        }
        Map<MoveQuality, Integer> counts = red ? redCounts : blackCounts;
        counts.merge(quality, 1, Integer::sum);

        double accuracy = accuracy(scoreBefore, scoreAfter);
        if (red) {
            redAccuracySum += accuracy;
            redMoves++;
        } else {
            blackAccuracySum += accuracy;
            blackMoves++;
        }
    }

    public double getRedAccuracy() {
        return redMoves == 0 ? 0 : redAccuracySum / redMoves;
    }

    public double getBlackAccuracy() {
        return blackMoves == 0 ? 0 : blackAccuracySum / blackMoves;
    }

    public int getCount(boolean red, MoveQuality quality) {
        return (red ? redCounts : blackCounts).getOrDefault(quality, 0);
    }

    public String toText() {
        StringBuilder sb = new StringBuilder();
        sb.append("红方  准确率 ").append(format(getRedAccuracy())).append("%").append(System.lineSeparator());
        sb.append(countLine(redCounts)).append(System.lineSeparator());
        sb.append(System.lineSeparator());
        sb.append("黑方  准确率 ").append(format(getBlackAccuracy())).append("%").append(System.lineSeparator());
        sb.append(countLine(blackCounts));
        return sb.toString();
    }

    private String countLine(Map<MoveQuality, Integer> counts) {
        StringBuilder sb = new StringBuilder();
        for (MoveQuality q : MoveQuality.values()) {
            // 未评价（胜负已定）不参与统计展示
            if (q == MoveQuality.NONE) {
                continue;
            }
            sb.append(q.getLabel()).append(' ')
                    .append(counts.getOrDefault(q, 0)).append("   ");
        }
        return sb.toString().trim();
    }

    private String format(double value) {
        return String.format("%.1f", value);
    }

    /**
     * 胜率降幅标定系数。
     * <p>
     * 准确率拟合式 {@code 103.1668*exp(-0.04354*drop)-3.1669} 是按 Lichess 自己的
     * cp→胜率曲线拟合出来的；本项目统一使用 {@link GameReview#eloToWinRate(int)}
     * （400 分 → 76%），同样的厘兵损失在这条曲线上算出的降幅约为 Lichess 曲线的 1.55 倍，
     * 因此先把降幅折算回去再代入，保证准确率数值仍在 0~100 的合理区间。
     */
    private static final double WIN_DROP_SCALE = 0.65;

    /**
     * 参考 Lichess/chess.com 的准确率算法：由胜率变化换算准确率。
     */
    private static double accuracy(int scoreBefore, int scoreAfter) {
        double winBefore = winPercent(scoreBefore);
        double winAfter = winPercent(scoreAfter);
        // 只降不升：涨分不算“超额准确”，避免兑子/重复局面把准确率堆到 100 以上
        double drop = Math.max(0, winBefore - winAfter) * WIN_DROP_SCALE;
        double accuracy = 103.1668 * Math.exp(-0.04354 * drop) - 3.1669;
        if (accuracy < 0) {
            return 0;
        }
        if (accuracy > 100) {
            return 100;
        }
        return accuracy;
    }

    /**
     * 分值 → 走子方胜率（百分比）。
     * <p>
     * 直接复用 {@link GameReview#eloToWinRate(int)}，保证复盘面板里的准确率
     * 与棋盘左侧胜率条使用同一条换算曲线。
     */
    private static double winPercent(int cp) {
        return GameReview.eloToWinRate(cp).doubleValue();
    }
}

package com.sojourners.chess.review;

import java.math.BigDecimal;
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
     * @param winRateDrop  走这步造成的胜率降幅（与 ReviewItem 展示的是同一个值）
     */
    public void add(boolean red, MoveQuality quality, BigDecimal winRateDrop) {
        if (quality == null || quality == MoveQuality.NONE) {
            return;
        }
        Map<MoveQuality, Integer> counts = red ? redCounts : blackCounts;
        counts.merge(quality, 1, Integer::sum);

        double accuracy = accuracy(winRateDrop.doubleValue());
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
     * 参考 Lichess/chess.com 的准确率算法：由胜率变化换算准确率。
     * <p>
     * 胜率来自 {@link GameReview#eloToWinRate(int)}，即皮卡鱼官方标准胜率
     * （200 score对应 76% 胜率）
     */
    private static double accuracy(double drop) {
        double accuracy = 103.1668 * Math.exp(-0.04354 * drop) - 3.1669;
        // 涨分（drop < 0）会让公式结果 > 100，统一钳到 100
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

package com.sojourners.chess.review;

import java.math.BigDecimal;

/**
 * 单步复盘结果。
 */
public class ReviewItem {

    /**
     * 棋谱中的步数（1 开始），对应棋谱表格中的行号。
     */
    private final int index;

    /**
     * 实际走出的着法（引擎着法）。
     */
    private final String move;

    /**
     * 引擎给出的最佳着法（引擎着法）。
     */
    private final String bestMove;

    /**
     * 引擎给出的最佳着法（中文记谱）。
     */
    private final String bestCnMove;

    /**
     * 与最佳着法相比损失的分数（厘兵），不会小于 0。
     */
    private final int loss;

    /**
     * 走完这一步后的局势分（按棋盘底部一方视角）。
     */
    private final int eval;

    public BigDecimal getWinRateDrop() {
        return winRateDrop;
    }

    /**
     * 走完这一步后的损失的胜率
     */
    private final BigDecimal winRateDrop;

    public BigDecimal getWinRateBottom() {
        return winRateBottom;
    }

    /**
     * 走完这一步后的棋盘底部一方的胜率
     */
    private final BigDecimal winRateBottom;

    private final MoveQuality quality;

    public ReviewItem(int index, String move, String bestMove, String bestCnMove, int loss,BigDecimal winRateDrop,BigDecimal winRateBottom, int eval, MoveQuality quality) {
        this.index = index;
        this.move = move;
        this.bestMove = bestMove;
        this.bestCnMove = bestCnMove;
        this.loss = loss;
        this.winRateDrop = winRateDrop;
        this.winRateBottom = winRateBottom;
        this.eval = eval;
        this.quality = quality;
    }

    public int getIndex() {
        return index;
    }

    public String getMove() {
        return move;
    }

    public String getBestMove() {
        return bestMove;
    }

    public String getBestCnMove() {
        return bestCnMove;
    }

    public int getLoss() {
        return loss;
    }

    public int getEval() {
        return eval;
    }

    public MoveQuality getQuality() {
        return quality;
    }
}

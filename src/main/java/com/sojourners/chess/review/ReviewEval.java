package com.sojourners.chess.review;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个局面的引擎分析结果。
 */
public class ReviewEval {

    /**
     * 引擎给出的最佳着法，空表示无着可走（被将死或困毙）。
     */
    private String bestMove;

    /**
     * 分值类型，cp 或 mate。
     */
    private String scoreType;

    private int scoreValue;

    private boolean mate;

    /**
     * 是否解析到了引擎分值。
     */
    private boolean scored;

    private List<String> pv = new ArrayList<>();

    public String getBestMove() {
        return bestMove;
    }

    public void setBestMove(String bestMove) {
        this.bestMove = bestMove;
    }

    public String getScoreType() {
        return scoreType;
    }

    public void setScoreType(String scoreType) {
        this.scoreType = scoreType;
    }

    public int getScoreValue() {
        return scoreValue;
    }

    public void setScoreValue(int scoreValue) {
        this.scoreValue = scoreValue;
    }

    public boolean isMate() {
        return mate;
    }

    public void setMate(boolean mate) {
        this.mate = mate;
    }

    public boolean isScored() {
        return scored;
    }

    public void setScored(boolean scored) {
        this.scored = scored;
    }

    public List<String> getPv() {
        return pv;
    }

    public void setPv(List<String> pv) {
        this.pv = pv;
    }

    /**
     * 统一为厘兵分值，正数表示当前行棋方占优。
     */
    public int cp() {
        if (mate) {
            return scoreValue > 0 ? GameReview.MATE - scoreValue : -GameReview.MATE - scoreValue;
        }
        return scoreValue;
    }
}

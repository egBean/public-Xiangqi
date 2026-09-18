package com.sojourners.chess.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.io.Serializable;

public class ManualRecord implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer id;

    private Integer score;

    private String move;

    private String cnMove;

    private String remark;

    private int next;

    private List<ManualRecord> list = new ArrayList<>();

    /**
     * 以下为复盘信息，仅用于界面展示，不写入棋谱文件。
     */

    /**
     * 复盘评价，如“最佳”“大漏”。
     */
    private transient String review;

    /**
     * 复盘等级，0 表示未复盘，数值越大问题越严重。
     */
    private transient int reviewLevel;

    /**
     * 引擎给出的正着（中文记谱）。
     */
    private transient String bestMove;

    /**
     * 与正着相比损失的分数（厘兵）。
     */
    private transient Integer loss;

    public BigDecimal getWinRateDrop() {
        return winRateDrop;
    }

    public void setWinRateDrop(BigDecimal winRateDrop) {
        this.winRateDrop = winRateDrop;
    }

    /**
     * 走完这一步后的损失的胜率
     */
    private BigDecimal winRateDrop;


    /**
     * 走完这一步后的棋盘底部一方的胜率
     */
    private BigDecimal winRateBottom;

    public BigDecimal getWinRateBottom() {
        return winRateBottom;
    }

    public void setWinRateBottom(BigDecimal winRateBottom) {
        this.winRateBottom = winRateBottom;
    }

    public ManualRecord(Integer id, String move, String cnMove) {
        this.id = id;
        this.move = move;
        this.cnMove = cnMove;
    }
    public ManualRecord(Integer id, String name, Integer score) {
        this.id = id;
        this.cnMove = name;
        this.score = score;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public String getMove() {
        return move;
    }

    public void setMove(String move) {
        this.move = move;
    }

    public String getCnMove() {
        return cnMove;
    }

    public void setCnMove(String cnMove) {
        this.cnMove = cnMove;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public int getNext() {
        return next;
    }

    public void setNext(int next) {
        this.next = next;
    }

    public List<ManualRecord> getList() {
        return list;
    }

    public void setList(List<ManualRecord> list) {
        this.list = list;
    }

    public String getReview() {
        return review;
    }

    public void setReview(String review) {
        this.review = review;
    }

    public int getReviewLevel() {
        return reviewLevel;
    }

    public void setReviewLevel(int reviewLevel) {
        this.reviewLevel = reviewLevel;
    }

    public String getBestMove() {
        return bestMove;
    }

    public void setBestMove(String bestMove) {
        this.bestMove = bestMove;
    }

    public Integer getLoss() {
        return loss;
    }

    public void setLoss(Integer loss) {
        this.loss = loss;
    }
}

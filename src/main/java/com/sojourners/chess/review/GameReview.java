package com.sojourners.chess.review;

import com.sojourners.chess.model.EngineConfig;
import com.sojourners.chess.model.ManualRecord;
import com.sojourners.chess.util.XiangqiUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 棋谱复盘。
 * <p>
 * 依次分析棋谱的每一个局面，得到每一步的最佳着法和分值，
 * 与棋谱中实际走出的着法比较，从而给出“正着是什么、有没有大漏”等评价。
 */
public class GameReview {

    /**
     * 绝杀分值。
     */
    public static final int MATE = 30000;

    /**
     * 复盘回调。
     */
    public interface ReviewListener {

        /**
         * 开始局面的分值分析完成。
         *
         * @param recordIndex 对应棋谱表格中的行号（开始局面为 0）
         * @param score       开始局面的局势分
         * @param winRate     开始局面棋盘底部一方的胜率（百分比，保留两位小数）
         */
        void onStartScore(int recordIndex, int score, BigDecimal winRate);

        /**
         * 完成一步复盘。
         *
         * @param current     当前步数（1 开始）
         * @param total       总步数
         * @param recordIndex 对应棋谱表格中的行号
         * @param item        复盘结果
         */
        void onProgress(int current, int total, int recordIndex, ReviewItem item);

        /**
         * 全部复盘完成。
         */
        void onFinished(ReviewSummary summary);

        /**
         * 复盘出错。
         */
        void onError(Exception e);
    }

    private GameReview() {
    }

    /**
     * 在后台线程中开始复盘。
     *
     * @param engineConfig 引擎配置（会另外启动一个引擎进程）
     * @param fenCode      棋谱起始局面
     * @param records      棋谱主变，第 0 个为“开始局面”，之后每个元素为一步棋
     * @param movetime     每步分析时间（毫秒）
     * @param isReverse    棋盘是否翻转（用于分值方向与界面保持一致）
     * @param listener     复盘回调
     */
    public static void start(EngineConfig engineConfig, String fenCode, List<ManualRecord> records,
                             long movetime, boolean isReverse, ReviewListener listener) {
        List<ManualRecord> line = new ArrayList<>(records);
        Thread.startVirtualThread(() -> doReview(engineConfig, fenCode, line, movetime, isReverse, listener));
    }

    /** 复盘胜率下限 */
    private static final BigDecimal WIN_RATE_LOW_LIMIT = new BigDecimal("3.0");

    private static void doReview(EngineConfig engineConfig, String fenCode, List<ManualRecord> records,
                                 long movetime, boolean isReverse, ReviewListener listener) {
        ReviewEngine engine = null;
        try {
            engine = new ReviewEngine(engineConfig);

            int total = records.size() - 1;
            if (total <= 0) {
                listener.onFinished(new ReviewSummary());
                return;
            }

            boolean initialRed = !fenCode.contains(" b");
            boolean[] redToMove = new boolean[total + 1];
            for (int i = 0; i <= total; i++) {
                redToMove[i] = i % 2 == 0 ? initialRed : !initialRed;
            }

            char[][] board = XiangqiUtils.fenToBoard(fenCode);
            List<String> moves = new ArrayList<>();

            // 先分析开始局面
            ReviewEval eval = engine.analyze(fenCode, moves, movetime);
            int prevScore = eval.cp();
            boolean prevScored = eval.isScored();
            String prevBest = eval.getBestMove();
            String prevBestCn = prevBest == null ? null : translate(board, prevBest);

            // 开始局面棋盘底部一方的局势分与胜率
            int startEval = redToMove[0] == isReverse ? -prevScore : prevScore;
            listener.onStartScore(0, startEval, eloToWinRate(startEval));

            ReviewSummary summary = new ReviewSummary();
            for (int i = 1; i <= total; i++) {
                String move = records.get(i).getMove();
                applyMove(board, move);
                moves.add(move);

                // 分析走完该步后的局面
                eval = engine.analyze(fenCode, moves, movetime);
                int curScore = eval.cp();
                boolean curScored = eval.isScored();
                String curBest = eval.getBestMove();
                String curBestCn = curBest == null ? null : translate(board, curBest);

                // 走子方视角：最佳着法分值与实际走法分值
                boolean red = redToMove[i - 1];
                int scoreBefore = prevScore;
                int scoreAfter = -curScore;
                int loss = Math.max(0, scoreBefore - scoreAfter);

                //胜率计算
                BigDecimal wrBefore = eloToWinRate(scoreBefore);
                BigDecimal wrAfter  = eloToWinRate(scoreAfter);
                BigDecimal winRateDrop = wrBefore.subtract(wrAfter);

                // 当前走子方在走这步之前的胜率已低于 3%，说明胜负已定，不再评价这步棋
                boolean decided = wrBefore.compareTo(WIN_RATE_LOW_LIMIT) < 0;

                MoveQuality quality;
                if (decided) {
                    // 胜负已定，不再评价
                    quality = MoveQuality.NONE;
                }else if (prevBest != null && prevBest.equals(move)) {
                    // 正着优先，仍显示“最佳”
                    quality = MoveQuality.BEST;
                } else if (!prevScored || !curScored) {
                    // 分析时间过短等情况导致没有拿到分值，只按是否与正着一致评价
                    quality = MoveQuality.GOOD;
                } else {
                    quality = MoveQuality.byWinRatio(winRateDrop);
                }

                // 走出正着时无需再重复展示正着
                String bestCn = quality == MoveQuality.BEST ? null : prevBestCn;

                // 走完该步后的局势分，转换为棋盘底部一方视角，与界面分数保持一致
                int evalBottom = redToMove[i] == isReverse ? -curScore : curScore;
                // 底部一方胜率（百分比，保留两位小数）
                BigDecimal winRateBottom = eloToWinRate(evalBottom);
                summary.add(red, quality, scoreBefore, scoreAfter);
                listener.onProgress(i, total, i,
                        new ReviewItem(i, move, prevBest, bestCn, loss,winRateDrop,winRateBottom, evalBottom, quality));

                prevScore = curScore;
                prevScored = curScored;
                prevBest = curBest;
                prevBestCn = curBestCn;
            }

            listener.onFinished(summary);
        } catch (Exception e) {
            listener.onError(e);
        } finally {
            if (engine != null) {
                engine.close();
            }
        }
    }


    /**
     * 皮卡鱼 Elo 分 → 走子方胜率（百分比，保留两位小数）。
     * 与官方“200 分优势对应 76% 胜率”的模型一致。
     * 返回值示例：4.56 表示 4.56%。
     */
    public static BigDecimal eloToWinRate(int elo) {
        double winRate = 1.0 / (1.0 + Math.pow(10, -elo / 400.0));
        // 放大 100 倍，保留两位小数，四舍五入
        return BigDecimal.valueOf(winRate * 100.0)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 把引擎着法翻译成中文记谱。
     */
    private static String translate(char[][] board, String move) {
        if (move == null || move.length() < 4) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        try {
            XiangqiUtils.translate(board, sb, move, false);
        } catch (Exception e) {
            return null;
        }
        return sb.toString();
    }

    /**
     * 在棋盘上执行一步引擎着法。
     */
    private static void applyMove(char[][] board, String move) {
        if (move == null || move.length() < 4) {
            return;
        }
        int fromJ = move.charAt(0) - 'a';
        int fromI = 9 - (move.charAt(1) - '0');
        int toJ = move.charAt(2) - 'a';
        int toI = 9 - (move.charAt(3) - '0');
        if (fromI < 0 || fromI > 9 || fromJ < 0 || fromJ > 8 || toI < 0 || toI > 9 || toJ < 0 || toJ > 8) {
            return;
        }
        board[toI][toJ] = board[fromI][fromJ];
        board[fromI][fromJ] = ' ';
    }
}

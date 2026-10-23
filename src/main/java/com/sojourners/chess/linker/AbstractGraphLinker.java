package com.sojourners.chess.linker;

import com.sojourners.chess.board.ChessBoard;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.util.DateUtils;
import com.sojourners.chess.util.NativeScreenshotUtil;
import com.sojourners.chess.util.XiangqiUtils;
import com.sojourners.chess.yolo.ChessRecognitionModel;
import com.sojourners.chess.yolo.OnnxModel;
import com.sojourners.chess.yolo.Yolo11Model;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;


public abstract class AbstractGraphLinker implements GraphLinker, Runnable {

    /**
     * 扫描线程
     */
    private volatile Thread thread;
    /**
     * 棋盘区域
     */
    private Rectangle boardPos;

    /**
     * 连续识别失败多少次后重新寻找棋盘坐标（只重算裁剪区域，不重新初始化棋盘）
     */
    private static final int RECALIBRATE_FAILS = 10;

    /**
     * flag=4 连续多少帧后重新寻找一次棋盘坐标
     */
    private static final int RECALIBRATE_FLAG4_STEP = 9;

    /**
     * flag=4 时最多重新寻找棋盘坐标几次，超过后回外层重新初始化棋盘（9 * 3 = 27 帧）
     */
    private static final int RECALIBRATE_FLAG4_TIMES = 3;

    /**
     * 重新寻找棋盘坐标时每次尝试的放大比例（相对上一帧棋盘区域的宽/高），数组长度即尝试次数
     */
    private static final double[] RECALIBRATE_EXPAND = {0.1, 0.15, 0.2};

    /**
     * 每次重试找棋盘坐标前的等待时间（毫秒），避免连续抓到同一帧异常画面
     */
    private static final long RECALIBRATE_RETRY_SLEEP = 500;

    /**
     * 日志时间格式：时分秒
     */
    private static final DateTimeFormatter LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * 识别棋盘 暂存
     */
    private char[][] board2 = new char[10][9];

    private char[][] board1 = new char[10][9];

    private OnnxModel aiModel;

    private OnnxModel supportAiModel;

    private LinkerCallBack callBack;

    private Robot robot;


    private volatile boolean pause;

    private Properties prop;

    /**
     * 最近一次用于识别棋盘的截图，识别异常时保存用于调试
     */
    private volatile BufferedImage lastBoardImg;

    public AbstractGraphLinker(LinkerCallBack callBack) throws AWTException {
        this.callBack = callBack;
        robot = new Robot();
        this.prop = Properties.getInstance();
        this.aiModel = new Yolo11Model();
//        this.supportAiModel = !"yolo11".equalsIgnoreCase(prop.getLinkAiModel()) ? new Yolo11Model() : new ChessRecognitionModel();
        this.pause = false;
    }

    /**
     * 开始连线
     */
    @Override
    public void start() {
        getTargetWindowId();
    }

    void scan() {
        // 重新开始时清除上次可能残留的暂停状态
        this.pause = false;
        this.thread = Thread.ofVirtual().unstarted(this);
        this.thread.start();
    }

    private boolean isSame(char[][] board1, char[][] board2) {
        if (board1 == null || board2 == null) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (board1[i][j] != board2[i][j]) {
                    return false;
                }
            }
        }
        return true;
    }

    public void pause() {
        this.pause = true;
    }
    public void resume() {
        this.pause = false;
    }

    @Override
    public void run() {
        scanBoards();
    }

    /**
     * 识别扫描主循环
     */
    private void scanBoards() {
        while (!Thread.currentThread().isInterrupted()) {
            if (!findBoardPosition()) {
                sleep(1000);
                continue;
            }
            if (Thread.currentThread().isInterrupted()) {
                return;
            }
            if (!initChessBoard()) {
                sleep(1000);
                continue;
            }
            int count = 0;
            // 连续识别失败次数
            int failCount = 0;
            while (!Thread.currentThread().isInterrupted()) {
                sleep(prop.getLinkScanTime());
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (!callBack.isThinking() && !pause) {

                    if (!findChessBoard(board2)) {
                        // 连续识别棋盘内容失败：先重新寻找棋盘坐标（只重算裁剪区域，不调 initChessBoard，棋谱与引擎局面保留）
                        if (++failCount >= RECALIBRATE_FAILS) {
                            failCount = 0;
                            if (!recalibrateBoardPosition("连续 " + RECALIBRATE_FAILS + " 次识别棋盘内容失败")) {
                                saveErrorBoardImage();
                                break;
                            }
                        }
                        continue;
                    }
                    failCount = 0;

                    boolean isReverse;
                    try {
                        isReverse = reverse(board2);
                    } catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }

                    if (isSame(board2, callBack.getEngineBoard())) {
                        count = 0;
                        continue;
                    }

                    Action action = compareBoard(board2, callBack.getEngineBoard(), isReverse, callBack.isWatchMode());
                    if (prop.isLinkAnimation() && needConfirm(board2, callBack.getEngineBoard(), action)) {
                        boolean f = false;
                        do {
                            if (Thread.currentThread().isInterrupted()) {
                                return;
                            }
                            char[][] tmp = board1;
                            board1 = board2;
                            board2 = tmp;

                            if (!findChessBoard(board2)) {
                                f = true;
                                break;
                            }

                            try {
                                isReverse = reverse(board2);
                            } catch (Exception e) {
                                e.printStackTrace();
                                f = true;
                                break;
                            }
                        } while (!isSame(board1, board2));

                        if (f) continue;

                        action = compareBoard(board2, callBack.getEngineBoard(), isReverse, callBack.isWatchMode());
                    }

                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    if (action != null) {
                        System.out.println("action " + action);

                        if (action.flag == 1) {
                            //目标平台同步我方走棋
                            callBack.linkerMove(action.x1, action.y1, action.x2, action.y2);

                        } else if (action.flag == 2) {

                            if (isReverse) {
                                action.y1 = 9 - action.y1;
                                action.y2 = 9 - action.y2;
                                action.x1 = 8 - action.x1;
                                action.x2 = 8 - action.x2;
                            }
                            //触发目标平台走棋
                            autoClick(action.x1, action.y1, action.x2, action.y2);

                        } else if (action.flag == 3) {
                            break;
                        }
                        if (action.flag == 4) {
                            count++;
                            // 每连续 9 帧无法解释的局面差异，先重找一次棋盘坐标后原地继续识别（不重新初始化棋盘）
                            if (count % RECALIBRATE_FLAG4_STEP == 0) {
                                if (count >= RECALIBRATE_FLAG4_STEP * RECALIBRATE_FLAG4_TIMES) {
                                    // 重找过多次仍然连续异常：回外层重新找棋盘并初始化棋盘
                                    logRecognitionError("连续 " + count + " 帧识别到无法解释的局面差异（flag=4），回外层重新找棋盘并初始化棋盘");
                                    saveErrorBoardImage();
                                    break;
                                }
                                // 重找棋盘坐标失败（已打日志）：保存截图并回外层重新找棋盘并初始化棋盘
                                if (!recalibrateBoardPosition("连续 " + count + " 帧识别到无法解释的局面差异（flag=4）")) {
                                    break;
                                }
                            }
                        } else {
                            count = 0;
                        }
                    }

                }
            }
        }
    }

    class Action {
        /**
         * 1	目标平台/连线棋盘走了一步，需要同步给引擎	compareBoard() 判断为对方走棋	callBack.linkerMove(...)
         * 2	引擎走了一步，需要同步到目标平台	compareBoard() 判断为引擎走棋	按 isReverse 转换坐标后 autoClick(...)
         * 3	确认识别到新棋局/局面差异过大	diff1 > 2 || diff2 >= 2 && diff3 > 2	break 跳出内层，回到外层重新找棋盘、重新初始化
         * 4	可能识别到新棋局	差异总数 > 2，但没达到 flag=3	count++，每 RECALIBRATE_FLAG4_STEP（9）帧先重找一次棋盘坐标，累计 RECALIBRATE_FLAG4_STEP * RECALIBRATE_FLAG4_TIMES（27）帧仍异常才 break 重新初始化
         */
        int flag;
        int x1;
        int y1;
        int x2;
        int y2;
        public Action(int flag) {
            this.flag = flag;
        }
        public Action(int flag, int x1, int y1, int x2, int y2) {
            this.flag = flag;
            this.x1 = x1;
            this.y1 = y1;
            this.x2 = x2;
            this.y2 = y2;
        }

        @Override
        public String toString() {
            return "Action{" +
                    "flag=" + flag +
                    ", x1=" + x1 +
                    ", y1=" + y1 +
                    ", x2=" + x2 +
                    ", y2=" + y2 +
                    '}';
        }
    }

    private boolean needConfirm(char[][] linkBoard, char[][] engineBoard, Action action) {
        if (action == null) {
            return false;
        }
        if (action.flag == 3) {
            return true;
        }
        if (action.flag != 1 || !(linkBoard[action.y2][action.x2] == 'r' || linkBoard[action.y2][action.x2] == 'R' || linkBoard[action.y2][action.x2] == 'c' || linkBoard[action.y2][action.x2] == 'C') || !(engineBoard[action.y2][action.x2] == ' ')) {
            return false;
        }
        if (linkBoard[action.y2][action.x2] == 'r' || linkBoard[action.y2][action.x2] == 'R') {
            int x = -1, y = -1;
            if (action.x1 == action.x2) {
                x = action.x1;
                if (action.y2 > action.y1) {
                    y = action.y2 + 1;
                } else {
                    y = action.y2 - 1;
                }
            }
            if (action.y1 == action.y2) {
                y = action.y1;
                if (action.x2 > action.x1) {
                    x = action.x2 + 1;
                } else {
                    x = action.x2 - 1;
                }
            }
            if (x < 0 || x > 8 || y < 0 || y > 9 || engineBoard[y][x] != ' ' && XiangqiUtils.isRed(engineBoard[action.y1][action.x1]) == XiangqiUtils.isRed(engineBoard[y][x])) {
                return false;
            }
        }
        if (linkBoard[action.y2][action.x2] == 'c' || linkBoard[action.y2][action.x2] == 'C') {
            if (action.x1 == action.x2) {
                int x = action.x1, y;
                int p;
                if (action.y2 > action.y1) {
                    y = action.y2 + 1;
                    p = 1;
                } else {
                    y = action.y2 - 1;
                    p = -1;
                }
                if (y < 0 || y > 9) {
                    return false;
                }
                if (engineBoard[y][x] != ' ') {
                    for (int i = y + p; i >= 0 && i <= 9; i += p) {
                        if (engineBoard[i][x] != ' ' && XiangqiUtils.isRed(engineBoard[i][x]) == XiangqiUtils.isRed(engineBoard[action.y1][action.x1])) {
                            return false;
                        } else if (engineBoard[i][x] != ' ' && XiangqiUtils.isRed(engineBoard[i][x]) != XiangqiUtils.isRed(engineBoard[action.y1][action.x1])) {
                            return true;
                        }
                    }
                    return false;
                }
            }
            if (action.y1 == action.y2) {
                int x, y = action.y1;
                int p;
                if (action.x2 > action.x1) {
                    x = action.x2 + 1;
                    p = 1;
                } else {
                    x = action.x2 - 1;
                    p = -1;
                }
                if (x < 0 || x > 8 || y < 0 || y > 9) {
                    return false;
                }
                if (engineBoard[y][x] != ' ') {
                    for (int j = x + p; j >= 0 && j <= 8; j += p) {
                        if (engineBoard[y][j] != ' ' && XiangqiUtils.isRed(engineBoard[y][j]) == XiangqiUtils.isRed(engineBoard[action.y1][action.x1])) {
                            return false;
                        } else if (engineBoard[y][j] != ' ' && XiangqiUtils.isRed(engineBoard[y][j]) != XiangqiUtils.isRed(engineBoard[action.y1][action.x1])) {
                            return true;
                        }
                    }
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 对比棋盘，计算出当前操作
     * flag： 1对方已走棋，需要同步到引擎
     *      2引擎已走棋，需要同步到目标平台
     *      3识别到新棋局
     *      4可能识别到新棋局
     * @param linkBoard
     * @param engineBoard
     * @param robotBlack
     * @return
     */
    private Action compareBoard(char[][] linkBoard, char[][] engineBoard, boolean robotBlack, boolean analysisMode) {
        int diff1 = 0, diff2 = 0, diff3 = 0;

        List<Point> diffList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 9; j++) {
                if (linkBoard[i][j] != engineBoard[i][j]) {
                    diffList.add(new Point(i, j));
                    if (linkBoard[i][j] != ' ' && engineBoard[i][j] != ' ') {
                        diff1++;
                    } else if (linkBoard[i][j] != ' ' && engineBoard[i][j] == ' ') {
                        diff2++;
                    } else {
                        diff3++;
                    }
                }
            }
        }

        int total = diff1 + diff2 + diff3;
        // 无差异：本轮没有任何变化，交给调用方处理
        if (total == 0) {
            return null;
        }
        //变化太多 直接重新识别棋盘
        if(total > 4){
            return new Action(3);
        }
        // 一步棋必然产生 2 个差异点；其它数量说明是识别错误或新棋局
        if (total != 2) {
            return new Action(4);
        }

        Action action = null;
        int flag = 0, sum = 0;
        Point from = null, to = null;
        for (int i = 0; i < diffList.size(); i++) {
            for (int j = i + 1; j < diffList.size(); j++) {
                Point p1 = diffList.get(i), p2 = diffList.get(j);
                boolean f = false;
                if (linkBoard[p1.x][p1.y] == engineBoard[p2.x][p2.y] && linkBoard[p1.x][p1.y] != ' ') {
                    if (linkBoard[p2.x][p2.y] == ' ' && engineBoard[p1.x][p1.y] == ' ') {
                        if (analysisMode || robotBlack && XiangqiUtils.isRed(linkBoard[p1.x][p1.y]) || !robotBlack && !XiangqiUtils.isRed(linkBoard[p1.x][p1.y])) {
                            flag = 1;
                            from = p2;
                            to = p1;
                            f = true;
                        } else if (robotBlack && !XiangqiUtils.isRed(linkBoard[p1.x][p1.y]) || !robotBlack && XiangqiUtils.isRed(linkBoard[p1.x][p1.y])) {
                            flag = 2;
                            from = p1;
                            to = p2;
                            f = true;
                        }
                    }
                    if (linkBoard[p2.x][p2.y] == ' ' && engineBoard[p1.x][p1.y] != ' ' && XiangqiUtils.isRed(linkBoard[p1.x][p1.y]) != XiangqiUtils.isRed(engineBoard[p1.x][p1.y])) {
                        flag = 1;
                        from = p2;
                        to = p1;
                        f = true;
                    }
                    if (!analysisMode && engineBoard[p1.x][p1.y] == ' ' && linkBoard[p2.x][p2.y] != ' ' && XiangqiUtils.isRed(engineBoard[p2.x][p2.y]) != XiangqiUtils.isRed(linkBoard[p2.x][p2.y])) {
                        flag = 2;
                        from = p1;
                        to = p2;
                        f = true;
                    }
                }
                if (linkBoard[p2.x][p2.y] == engineBoard[p1.x][p1.y] && linkBoard[p2.x][p2.y] != ' ') {
                    if (linkBoard[p1.x][p1.y] == ' ' && engineBoard[p2.x][p2.y] == ' ') {
                        if (analysisMode || robotBlack && XiangqiUtils.isRed(linkBoard[p2.x][p2.y]) || !robotBlack && !XiangqiUtils.isRed(linkBoard[p2.x][p2.y])) {
                            flag = 1;
                            from = p1;
                            to = p2;
                            f = true;
                        } else if (robotBlack && !XiangqiUtils.isRed(linkBoard[p2.x][p2.y]) || !robotBlack && XiangqiUtils.isRed(linkBoard[p2.x][p2.y])) {
                            flag = 2;
                            from = p2;
                            to = p1;
                            f = true;
                        }
                    }
                    if (linkBoard[p1.x][p1.y] == ' ' && engineBoard[p2.x][p2.y] != ' ' && XiangqiUtils.isRed(linkBoard[p2.x][p2.y]) != XiangqiUtils.isRed(engineBoard[p2.x][p2.y])) {
                        flag = 1;
                        from = p1;
                        to = p2;
                        f = true;
                    }
                    if (!analysisMode && engineBoard[p2.x][p2.y] == ' ' && linkBoard[p1.x][p1.y] != ' ' && XiangqiUtils.isRed(engineBoard[p1.x][p1.y]) != XiangqiUtils.isRed(linkBoard[p1.x][p1.y])) {
                        flag = 2;
                        from = p2;
                        to = p1;
                        f = true;
                    }
                }
//                if (f && (flag == 1 && XiangqiUtils.canGo(engineBoard, from.x, from.y, to.x, to.y) || flag == 2 && XiangqiUtils.canGo(linkBoard, from.x, from.y, to.x, to.y))) {
//                    sum++;
//                    action = new Action(flag, from.y, from.x, to.y, to.x);
//                }
                if (f && (flag == 1 && XiangqiUtils.canGo(engineBoard, from.x, from.y, to.x, to.y) || flag == 2 && XiangqiUtils.canGo(linkBoard, from.x, from.y, to.x, to.y))) {

                    if (flag == 2) {
                        // 引擎走棋时，被移动的棋子必须是引擎方的棋子
                        // 引擎方颜色：robotBlack=true -> 引擎黑；robotBlack=false -> 引擎红
                        char movedPiece = linkBoard[from.x][from.y];
                        boolean movedIsRed = XiangqiUtils.isRed(movedPiece);
                        boolean engineIsRed = !robotBlack;
                        if (movedIsRed != engineIsRed) {
                            continue;
                        }
                    }
                    sum++;
                    action = new Action(flag, from.y, from.x, to.y, to.x);
                }
            }
        }

        if (sum == 1) {
            return action;
        }

        // total == 2 但拼不出唯一合法走法 → 也是识别错误，需要重新初始化棋盘
        return new Action(4);
    }

    void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            // 正常停止：保留中断标记，由调用方退出扫描流程。
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 前台截图
     * @param windowPos
     * @return
     */
    public BufferedImage screenshotByFront(Rectangle windowPos) {
        if (windowPos.width == 0 || windowPos.height == 0) {
            return null;
        }
        // 使用 JNA 原生截图替换 Robot 的截图，解决高 DPI 模糊和色彩失真问题
        return NativeScreenshotUtil.capture(windowPos);
    }

    /**
     * 前台点击
     * @param windowPos
     * @param p1
     * @param p2
     */
    @Override
    public void mouseClickByFront(Rectangle windowPos, Point p1, Point p2) {

        Point mouse = MouseInfo.getPointerInfo().getLocation();

        robot.mouseMove(windowPos.x + p1.x, windowPos.y+ p1.y);

        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        if (prop.getMouseClickDelay() > 0) {
            robot.delay(prop.getMouseClickDelay());
        }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        if (prop.getMouseMoveDelay() > 0) {
            robot.delay(prop.getMouseMoveDelay());
        }
        robot.mouseMove(windowPos.x + p2.x, windowPos.y + p2.y);

        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        if (prop.getMouseClickDelay() > 0) {
            robot.delay(prop.getMouseClickDelay());
        }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        robot.mouseMove((int) mouse.getX(), (int) mouse.getY());

    }

    /**
     * 寻找棋盘区域（整窗截图后识别）
     * @return
     */
    boolean findBoardPosition() {
        return findBoardPosition(null, 0);
    }

    /**
     * 寻找棋盘区域
     * @param prev  上一帧的棋盘区域（窗口内坐标），非空时只截取它向外放大 scale 倍后的局部区域再识别
     * @param scale 放大比例（相对棋盘宽/高），如 0.1 表示每边放大 10%
     * @return
     */
    boolean findBoardPosition(Rectangle prev, double scale) {
        Rectangle region = null;
        if (prev != null) {
            int dx = (int) Math.round(prev.width * scale);
            int dy = (int) Math.round(prev.height * scale);
            region = new Rectangle(prev.x - dx, prev.y - dy, prev.width + dx * 2, prev.height + dy * 2);
        }
        BufferedImage img = screenshot(region);
        if (img == null) {
            return false;
        }
        Rectangle pos = this.aiModel.findBoardPosition(img);
        if (pos == null) {
            return false;
        }
        if (region != null) {
            // 模型返回的是局部裁剪图内的坐标，换算回窗口坐标
            pos.x += region.x;
            pos.y += region.y;
        }
        this.boardPos = pos;
        return true;
    }

    /**
     * 重新寻找棋盘坐标（只重算裁剪区域，不重新初始化棋盘，棋谱与引擎局面保留）。
     *
     * 与整窗截图识别相比：只在上一帧棋盘区域向外放大 0.1/0.15/0.2（RECALIBRATE_EXPAND）的范围内重找，
     * 棋盘在模型输入里的有效分辨率更高，也不会误抓到画面里其它棋盘。
     *
     * @param reason 日志里的触发原因，如「连续 10 次识别棋盘内容失败」
     * @return 找到新坐标并更新 boardPos 时返回 true；
     *         连续 RECALIBRATE_EXPAND.length 次都失败返回 false（调用方负责回外层重新找棋盘并初始化棋盘）
     */
    private boolean recalibrateBoardPosition(String reason) {
        Rectangle prevBoardPos = this.boardPos;
        for (int attempt = 1; attempt <= RECALIBRATE_EXPAND.length; attempt++) {
            if (attempt > 1) {
                sleep(RECALIBRATE_RETRY_SLEEP);
                if (Thread.currentThread().isInterrupted()) {
                    return false;
                }
            }
            if (findBoardPosition(prevBoardPos, RECALIBRATE_EXPAND[attempt - 1])) {
                logRecognitionError(reason + "，重新寻找棋盘坐标第 " + attempt + " 次成功");
                return true;
            }
        }
        logRecognitionError(reason + "，重新寻找棋盘坐标连续 " + RECALIBRATE_EXPAND.length
                + " 次也失败，回外层重新找棋盘并初始化棋盘");
        return false;
    }

    /**
     * 输出带时间的识别错误日志（System.err 由 Main 双写到 log/app.log）
     */
    private static void logRecognitionError(String message) {
        System.err.println("[linker] " + LOG_TIME_FORMATTER.format(LocalTime.now()) + " 错误：" + message);
    }

    /**
     * 截图
     * @param region 需要截取的窗口内区域，null 表示整窗
     * @return
     */
    BufferedImage screenshot(Rectangle region) {
        if (prop.isLinkBackMode()) {
            return screenshotByBack(region);
        }
        Rectangle pos = getTargetWindowPosition();
        if (region != null) {
            pos.setLocation(pos.x + region.x, pos.y + region.y);
            pos.setSize(region.width, region.height);
        }
        return screenshotByFront(pos);
    }

    /**
     * 截图
     * @param fullScreen true 截整窗，false 只截当前棋盘区域
     * @return
     */
    BufferedImage screenshot(boolean fullScreen) {
        return screenshot(fullScreen ? null : boardPos);
    }


    private boolean findChessBoard(char[][] board) {
        // 截图
        BufferedImage img = screenshot(false);
        this.lastBoardImg = img;
        return findChessBoardWithModel(img, board, this.aiModel);
//        if(result1){
//            return true;
//        }
//        //第一个模型识别不出来 尝试使用第二个
//        return findChessBoardWithModel(img, board, this.supportAiModel);
    }

    private boolean findChessBoardWithModel(BufferedImage img,char[][] board,OnnxModel model){
        // ai识别棋盘棋子
        if (!model.findChessBoard(img, board)) {
            return false;
        }
        boolean f = XiangqiUtils.validateChessBoard(board);
        if (!f) {
            // 识别出的局面不合法：把 board 对应的 FEN 打到 app.log（System.err 由 Main 双写到 log/app.log），便于排查识别问题
            System.err.println("[linker] " + LOG_TIME_FORMATTER.format(LocalTime.now())
                    + " 识别局面不合法，FEN：" + ChessBoard.fenCode(board, null));
        }
        return f;
    }

    /**
     * 识别异常时，把识别的棋盘截图保存到项目根目录 errorImg 下，便于后期调试
     */
    private void saveErrorBoardImage() {
        BufferedImage img = this.lastBoardImg;
        if (img == null) {
            return;
        }
        try {
            File dir = new File(System.getProperty("user.dir"), "errorImg");
            if (!dir.exists() && !dir.mkdirs()) {
                System.err.println("create errorImg dir failed: " + dir.getAbsolutePath());
                return;
            }
            File file = new File(dir, "error_" + DateUtils.getDateTimeString(new Date()) + ".png");
            ImageIO.write(img, "png", file);
            System.out.println("save error board image: " + file.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean reverse(char[][] board) throws Exception {
        // 是否翻转
        int rowRedKing = -1, rowBlackKing = -1;
        for (int i = 0; i < 10; i++) {
            for (int j = 3; j < 6; j++) {
                if (board[i][j] == 'k') {
                    rowBlackKing = i;
                } else if (board[i][j] == 'K') {
                    rowRedKing = i;
                }
            }
        }
        if (rowBlackKing == -1 && rowRedKing == -1) {
            throw new Exception("find king failed.");
        }
        boolean isReverse = rowRedKing >= 0 && rowRedKing <= 2 || rowBlackKing >= 7 && rowBlackKing <= 9;
        if (isReverse) {
            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 9; j++) {
                    char tmp = board[i][j];
                    board[i][j] = board[9 - i][8 - j];
                    board[9 - i][8 - j] = tmp;
                }
            }
        }
        return isReverse;
    }

    /**
     * 初始化棋盘局面.如果棋盘没有翻转，默认是红走，如果是开始局面，也默认是红走。只是一种猜测，实际上可能棋盘同步时，是轮到黑走
     * @return
     */
    private boolean initChessBoard() {
        if (!findChessBoard(board2)) {
            return false;
        }

        boolean isReverse = false;
        try {
            isReverse = reverse(board2);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        // 是否红走
        String fenCode = ChessBoard.fenCode(board2, null);
        boolean redGo = !isReverse || "rnbakabnr/9/1c5c1/p1p1p1p1p/9/9/P1P1P1P1P/1C5C1/9/RNBAKABNR".equals(fenCode);
        fenCode = ChessBoard.fenCode(board2, redGo);
        // 回调，初始化棋盘
        if (Thread.currentThread().isInterrupted()) {
            return false;
        }
        callBack.linkerInitChessBoard(fenCode, isReverse);
        return true;
    }

    /**
     * 自动点击走棋
     * @param x1
     * @param y1
     * @param x2
     * @param y2
     */
    public void autoClick(int x1, int y1, int x2, int y2) {

        Point p1 = getPosition(x1, y1);
        Point p2 = getPosition(x2, y2);
        if (prop.isLinkBackMode()) {
            mouseClickByBack(p1, p2);
        } else {
            Rectangle windowPos = getTargetWindowPosition();
            mouseClickByFront(windowPos, p1, p2);
        }
    }
    private Point getPosition(int x, int y) {
        double pieceWith = boardPos.width / (8 + OnnxModel.PADDING * 2);
        double pieceHeight = boardPos.height / (9 + OnnxModel.PADDING * 2);
        Point p = new Point((int) (boardPos.x + pieceWith * OnnxModel.PADDING + (x * pieceWith)),
                (int) (boardPos.y + pieceHeight * OnnxModel.PADDING + (y * pieceHeight)));
        if (x == 0) {
            p.x += 0.2 * pieceWith;
        } else if (x == 8) {
            p.x -= 0.2 * pieceWith;
        }
        if (y == 0) {
            p.y += 0.2 * pieceHeight;
        } else if (y == 9) {
            p.y -= 0.2 * pieceHeight;
        }
        return p;
    }

    /**
     * 请求停止连线，扫描线程在检查到中断后退出。
     */
    @Override
    public void stop() {
        Thread scanThread = this.thread;
        if (scanThread != null) {
            scanThread.interrupt();
        }
    }

    // find chess board from image
    public char[][] findChessBoard(BufferedImage img) {
        char[][] tmp = new char[10][9];
        if (this.aiModel.findChessBoard(img, tmp)) {
            return tmp;
        } else {
            return null;
        }
    }

    public char[][] findChessBoardUseSupportModel(BufferedImage img) {
        char[][] tmp = new char[10][9];
        if (this.supportAiModel.findChessBoard(img, tmp)) {
            return tmp;
        } else {
            return null;
        }
    }
}

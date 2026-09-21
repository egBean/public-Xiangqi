package com.sojourners.chess.linker;

import com.sojourners.chess.board.ChessBoard;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.util.NativeScreenshotUtil;
import com.sojourners.chess.util.XiangqiUtils;
import com.sojourners.chess.yolo.ChessRecognitionModel;
import com.sojourners.chess.yolo.OnnxModel;
import com.sojourners.chess.yolo.Yolo11Model;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
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

    public AbstractGraphLinker(LinkerCallBack callBack) throws AWTException {
        this.callBack = callBack;
        robot = new Robot();
        this.prop = Properties.getInstance();
        this.aiModel = "yolo11".equalsIgnoreCase(prop.getLinkAiModel()) ? new Yolo11Model() : new ChessRecognitionModel();
        this.supportAiModel = !"yolo11".equalsIgnoreCase(prop.getLinkAiModel()) ? new Yolo11Model() : new ChessRecognitionModel();
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
            while (!Thread.currentThread().isInterrupted()) {
                sleep(prop.getLinkScanTime());
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (!callBack.isThinking() && !pause) {

                    if (!findChessBoard(board2)) {
                        continue;
                    }

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
                            if (count > 9) {
                                break;
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
         * 4	可能识别到新棋局	差异总数 > 2，但没达到 flag=3	count++，连续超过 9 次也 break 重新初始化
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
     * 寻找棋盘区域
     * @return
     */
    boolean findBoardPosition() {
        BufferedImage img = screenshot(true);
        this.boardPos = this.aiModel.findBoardPosition(img);
        return this.boardPos != null;
    }

    /**
     * 截图
     * @param fullScreen
     * @return
     */
    BufferedImage screenshot(boolean fullScreen) {
        if (prop.isLinkBackMode()) {
            BufferedImage img = screenshotByBack(fullScreen ? null : boardPos);
            return img;

        } else {
            Rectangle pos = getTargetWindowPosition();
            if (!fullScreen) {
                pos.setLocation(pos.x + boardPos.x, pos.y + boardPos.y);
                pos.setSize(boardPos.width, boardPos.height);
            }
            BufferedImage img = screenshotByFront(pos);
            return img;
        }
    }


    private boolean findChessBoard(char[][] board) {
        BufferedImage img = screenshot(false);
        boolean result1 = findChessBoardWithModel(img, board, this.aiModel);
        if(result1){
            return result1;
        }
        //第一个模型识别不出来 尝试使用第二个
        return findChessBoardWithModel(img, board, this.supportAiModel);
    }

    private boolean findChessBoardWithModel(BufferedImage img,char[][] board,OnnxModel model){
        // ai识别棋盘棋子
        if (!model.findChessBoard(img, board)) {
            return false;
        }
        boolean f = XiangqiUtils.validateChessBoard(board);
        if (!f) {
            for (int i = 0; i < 10; i++) {
                for (int j = 0; j < 9; j++) {
                    System.out.print(board[i][j]);
                }
                System.out.println();
            }
        }
        return f;
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

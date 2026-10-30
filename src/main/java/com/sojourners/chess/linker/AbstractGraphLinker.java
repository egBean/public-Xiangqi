package com.sojourners.chess.linker;

import com.sojourners.chess.board.ChessBoard;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.util.NativeScreenshotUtil;
import com.sojourners.chess.util.XiangqiUtils;
import com.sojourners.chess.yolo.OnnxModel;
import com.sojourners.chess.yolo.Yolo11Model;

import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;


public abstract class AbstractGraphLinker implements GraphLinker, Runnable {

    /**
     * OpenCV native 加载，只需一次
     */
    static {
        try {
            OpenCV.loadLocally();
        } catch (Throwable t) {
            System.err.println("[linker] OpenCV 加载失败: " + t.getMessage());
        }
    }

    /**
     * 续盘按钮模板根目录（相对项目根目录），其下按平台名分子目录，
     * 例如 clickImage/天天、clickImage/相弈
     */
    private static final String CONTINUE_TEMPLATE_DIR = "clickImage";
    /**
     * 支持的图片扩展名
     */
    private static final Set<String> IMAGE_EXTS = Set.of("png", "jpg", "jpeg", "bmp");
    /**
     * 每多少次 scan 检测一次续盘按钮
     */
    private static final int CONTINUE_CHECK_INTERVAL = 20;
    /**
     * 模板匹配阈值，TM_CCOEFF_NORMED，范围 [-1,1]，一般 0.75~0.9
     */
    private static final double CONTINUE_MATCH_THRESHOLD = 0.8;
    /**
     * 两次点击之间的最小间隔，避免按钮还没消失时重复点击
     */
    private static final long CONTINUE_CLICK_COOLDOWN_MS = 5000;

    /**
     * 扫描线程
     */
    private volatile Thread thread;
    /**
     * 棋盘区域
     */
    private Rectangle boardPos;

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

    private LinkerCallBack callBack;

    private Robot robot;

    private volatile boolean pause;

    private Properties prop;


    /**
     * 续盘按钮模板缓存：key = 平台名（prop.getLinkPlatform()），value = 该平台目录下加载的模板+文件名。
     * 首次用到某平台时加载，之后一直复用；切换平台时按 key 直接命中对应缓存，无需重启、无需重载。
     */
    private final Map<String, TemplateBundle> continueTemplateCache = new ConcurrentHashMap<>();

    /**
     * 某个平台目录下的一组模板，templates 与 names 一一对应
     */
    private record TemplateBundle(List<Mat> templates, List<String> names) {
    }

    /**
     * 上次点击续盘按钮的时间戳，用于冷却
     */
    private long lastContinueClickTime = 0;


    public AbstractGraphLinker(LinkerCallBack callBack) throws AWTException {
        this.callBack = callBack;
        robot = new Robot();
        this.prop = Properties.getInstance();
        this.aiModel = new Yolo11Model();
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

            // 每进入一轮识别，scan 计数从 0 开始
            int scanCount = 0;

            // 内层识别循环：失败只计数并继续重试，只有 flag=3 才退出重新初始化棋盘
            while (!Thread.currentThread().isInterrupted()) {
                scanCount++;
                // 每 CONTINUE_CHECK_INTERVAL 次 scan，整窗截屏一次，检测续盘按钮（非观战模式下）
                if (prop.isAutoNext() && !callBack.isWatchMode() && scanCount % CONTINUE_CHECK_INTERVAL == 0) {
                    checkAndClickContinue();
                }

                sleep(prop.getLinkScanTime());
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (callBack.isThinking() || pause) {
                    continue;
                }

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

                    if (f) {
                        continue;
                    }

                    action = compareBoard(board2, callBack.getEngineBoard(), isReverse, callBack.isWatchMode());
                }

                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (action == null) {
                    continue;
                }

                System.out.println("action " + action);

                if (action.flag == 1) {
                    // 目标平台同步我方走棋
                    callBack.linkerMove(action.x1, action.y1, action.x2, action.y2);

                } else if (action.flag == 2) {
                    if (isReverse) {
                        action.y1 = 9 - action.y1;
                        action.y2 = 9 - action.y2;
                        action.x1 = 8 - action.x1;
                        action.x2 = 8 - action.x2;
                    }
                    // 触发目标平台走棋
                    autoClick(action.x1, action.y1, action.x2, action.y2);

                } else if (action.flag == 3) {
                    // 重新初始化棋盘时，可能目标平台动画还未走完导致局面识别错误 睡眠2s保证稳定
                    sleep(2000);
                    break;
                } else if (action.flag == 4) {
                    String fen = ChessBoard.fenCode(board2, null);
                    System.err.println("[linker] 棋盘识别错误，新fen为：" + fen);
                }
            }
        }
    }

    /**
     * 整窗截屏一次，用 clickImage/<平台名> 目录下所有模板做匹配。
     * 任意一张命中阈值立即点击并返回，不再匹配后续模板
     */
    private void checkAndClickContinue() {
        if (Thread.currentThread().isInterrupted() || pause) {
            return;
        }
        // 冷却期内直接跳过，避免按钮还没消失时连点
        long now = System.currentTimeMillis();
        if (now - lastContinueClickTime < CONTINUE_CLICK_COOLDOWN_MS) {
            return;
        }
        // 按当前平台取缓存，没有就加载该平台目录（computeIfAbsent 保证同一 key 只加载一次）
        String linkPlatform = prop.getLinkPlatform();
        TemplateBundle bundle = continueTemplateCache.computeIfAbsent(linkPlatform, this::loadContinueTemplates);
        List<Mat> templates = bundle.templates();
        List<String> names = bundle.names();
        if (templates.isEmpty()) {
            return;
        }

        BufferedImage screen;
        try {
            // true = 整窗截图，按钮可能在棋盘之外
            screen = screenshot(true);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        if (screen == null) {
            return;
        }

        Mat src = null;
        Mat result = null;
        try {
            src = bufferedImageToGrayMat(screen);
            result = new Mat();

            // 逐个模板匹配，任意一张命中即点击并返回，不再匹配后续模板
            for (int i = 0; i < templates.size(); i++) {
                Mat tmpl = templates.get(i);
                // 模板比截图还大，跳过
                if (tmpl.cols() > src.cols() || tmpl.rows() > src.rows()) {
                    continue;
                }
                Imgproc.matchTemplate(src, tmpl, result, Imgproc.TM_CCOEFF_NORMED);
                Core.MinMaxLocResult mmr = Core.minMaxLoc(result);
                if (mmr.maxVal >= CONTINUE_MATCH_THRESHOLD) {
                    int cx = (int) mmr.maxLoc.x + tmpl.cols() / 2;
                    int cy = (int) mmr.maxLoc.y + tmpl.rows() / 2;
                    String name = names.get(i);
                    System.out.println("[linker] 续盘按钮命中，模板=" + name
                            + " score=" + mmr.maxVal + " @" + cx + "," + cy);
                    clickContinue(new Point(cx, cy));
                    lastContinueClickTime = System.currentTimeMillis();
                    // 命中即返回，finally 会释放 src / result
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (src != null) {
                src.release();
            }
            if (result != null) {
                result.release();
            }
        }
    }

    /**
     * 从 clickImage/<平台名> 目录加载续盘按钮模板（灰度）。
     * - platform 为 "无"（或空）时返回空 bundle，相当于禁用自动续盘
     * - 目录不存在 / 目录为空时返回空 bundle
     * 任何一步失败都返回已成功加载的部分，不会抛异常。
     */
    private TemplateBundle loadContinueTemplates(String platform) {
        List<Mat> result = new ArrayList<>();
        List<String> names = new ArrayList<>();

        // 设置为"无"时，直接返回空列表，不启用自动续盘识别
        if ("无".equals(platform)) {
            return new TemplateBundle(result, names);
        }

        File dir = new File(CONTINUE_TEMPLATE_DIR, platform);
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("[linker] 续盘模板目录不存在: " + dir.getAbsolutePath());
            return new TemplateBundle(result, names);
        }
        File[] files = dir.listFiles((d, name) -> {
            int idx = name.lastIndexOf('.');
            if (idx < 0 || idx == name.length() - 1) {
                return false;
            }
            return IMAGE_EXTS.contains(name.substring(idx + 1).toLowerCase());
        });
        if (files == null || files.length == 0) {
            System.err.println("[linker] 续盘模板目录为空: " + dir.getAbsolutePath());
            return new TemplateBundle(result, names);
        }

        for (File f : files) {
            try {
                Mat t = readGrayMat(f);
                if (!t.empty()) {
                    result.add(t);
                    names.add(f.getName());
                } else {
                    // 加载失败返回的是空 Mat，也 release 掉，避免留下无用的 native 对象
                    t.release();
                    System.err.println("[linker] 模板加载失败: " + f.getAbsolutePath());
                }
            } catch (Exception e) {
                System.err.println("[linker] 模板加载异常: " + f.getAbsolutePath() + " - " + e.getMessage());
            }
        }
        System.out.println("[linker] 加载续盘模板 " + result.size()
                + " 个（平台=" + platform + "）: " + names);
        return new TemplateBundle(result, names);
    }

    /**
     * 读取图片文件为灰度 Mat。
     * 用 Files.readAllBytes + imdecode 而不是 imread，
     * 避免 Windows 下 OpenCV 的 imread 对中文路径按 GBK 解码导致读不到文件。
     */
    private static Mat readGrayMat(File f) throws IOException {
        byte[] bytes = Files.readAllBytes(f.toPath());
        MatOfByte mob = new MatOfByte(bytes);
        try {
            return Imgcodecs.imdecode(mob, Imgcodecs.IMREAD_GRAYSCALE);
        } finally {
            mob.release();
        }
    }

    /**
     * BufferedImage 转灰度 Mat
     */
    private static Mat bufferedImageToGrayMat(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        // 统一转换为 3BYTE_BGR，保证后续取 DataBufferByte 一定可用
        BufferedImage converted = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        Graphics2D g = converted.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();

        byte[] data = ((DataBufferByte) converted.getRaster().getDataBuffer()).getData();
        Mat bgr = new Mat(h, w, CvType.CV_8UC3);
        bgr.put(0, 0, data);

        Mat gray = new Mat();
        Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY);
        bgr.release();
        return gray;
    }

    /**
     * 单击续盘按钮（窗口内坐标）
     */
    private void clickContinue(Point p) {
        if (prop.isLinkBackMode()) {
            // 后台模式：传同一个点，具体行为取决于子类实现
            mouseClickByBack(p, p);
        } else {
            clickSingleByFront(getTargetWindowPosition(), p);
        }
    }

    /**
     * 前台单击（区别于 mouseClickByFront 的双击行为）
     */
    public void clickSingleByFront(Rectangle windowPos, Point p) {
        Point mouse = MouseInfo.getPointerInfo() != null
                ? MouseInfo.getPointerInfo().getLocation()
                : new Point(0, 0);

        robot.mouseMove(windowPos.x + p.x, windowPos.y + p.y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        if (prop.getMouseClickDelay() > 0) {
            robot.delay(prop.getMouseClickDelay());
        }
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseMove((int) mouse.getX(), (int) mouse.getY());
    }


    class Action {
        /**
         * 1    目标平台/连线棋盘走了一步，需要同步给引擎    compareBoard() 判断为对方走棋    callBack.linkerMove(...)
         * 2    引擎走了一步，需要同步到目标平台    compareBoard() 判断为引擎走棋    按 isReverse 转换坐标后 autoClick(...)
         * 3    确认识别到新棋局/局面差异过大    diff1 > 2 || diff2 >= 2 && diff3 > 2    break 跳出内层，回到外层重新找棋盘、重新初始化
         * 4    可能识别到新棋局    差异总数异常但未达到 flag=3，连续 10 次才记录日志和截图，然后继续下一轮识别
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
     *       2引擎已走棋，需要同步到目标平台
     *       3识别到新棋局
     *       4可能识别到新棋局
     *
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
        // 变化太多 直接重新识别棋盘
        if (total > 4) {
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
     *
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
     *
     * @param windowPos
     * @param p1
     * @param p2
     */
    @Override
    public void mouseClickByFront(Rectangle windowPos, Point p1, Point p2) {

        Point mouse = MouseInfo.getPointerInfo().getLocation();

        robot.mouseMove(windowPos.x + p1.x, windowPos.y + p1.y);

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
     *
     * @return
     */
    boolean findBoardPosition() {
        BufferedImage img = screenshot(null);
        if (img == null) {
            return false;
        }
        Rectangle pos = this.aiModel.findBoardPosition(img);
        if (pos == null) {
            return false;
        }
        this.boardPos = pos;
        return true;
    }

    /**
     * 截图
     *
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
     *
     * @param fullScreen true 截整窗，false 只截当前棋盘区域
     * @return
     */
    BufferedImage screenshot(boolean fullScreen) {
        return screenshot(fullScreen ? null : boardPos);
    }


    private boolean findChessBoard(char[][] board) {
        // 截图
        BufferedImage img = screenshot(false);
        return findChessBoardWithModel(img, board, this.aiModel);
    }

    private boolean findChessBoardWithModel(BufferedImage img, char[][] board, OnnxModel model) {
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
     *
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
     *
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
}
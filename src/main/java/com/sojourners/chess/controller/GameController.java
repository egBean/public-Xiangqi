package com.sojourners.chess.controller;

import com.sojourners.chess.App;
import com.sojourners.chess.board.ChessBoard;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.enginee.Engine;
import com.sojourners.chess.enginee.EngineCallBack;
import com.sojourners.chess.model.BookData;
import com.sojourners.chess.model.GameConfig;
import com.sojourners.chess.model.ThinkData;
import com.sojourners.chess.openbook.BhOpenBook;
import com.sojourners.chess.openbook.MoveRule;
import com.sojourners.chess.openbook.OpenBook;
import com.sojourners.chess.openbook.PfOpenBook;
import com.sojourners.chess.openbook.XqbOpenBook;
import com.sojourners.chess.util.DialogUtils;
import com.sojourners.chess.util.StringUtils;
import com.sojourners.chess.util.XiangqiUtils;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.File;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 对弈模式控制器。
 *
 * 与打谱（分析）模式隔离：对弈引擎配置、开局库、出招延迟、局时均使用独立的
 * {@link GameConfig} 单独保存。
 */
public class GameController implements EngineCallBack {

    @FXML
    private BorderPane rootPane;
    @FXML
    private StackPane canvasPane;
    @FXML
    private Canvas canvas;
    @FXML
    private Label statusLabel;
    @FXML
    private ComboBox<String> skillComboBox;
    @FXML
    private ComboBox<String> sideComboBox;
    @FXML
    private ComboBox<String> clockComboBox;
    @FXML
    private Button settingButton;
    @FXML
    private Button startButton;
    @FXML
    private Button resignButton;
    @FXML
    private Button reviewButton;
    @FXML
    private VBox leftPanel;
    @FXML
    private VBox rightPanel;
    @FXML
    private VBox clockBox;
    @FXML
    private HBox redClockPanel;
    @FXML
    private Region clockSpacer;
    @FXML
    private Label lowSecondsLabel;
    private Boolean clockOrderReversed;
    @FXML
    private HBox blackClockPanel;
    @FXML
    private Label redClockMain;
    @FXML
    private Label redClockMove;
    @FXML
    private Label blackClockMain;
    @FXML
    private Label blackClockMove;

    private Properties prop;
    private GameConfig gameConfig;
    private ChessBoard board;
    private Engine engine;

    private boolean redGo = true;
    private boolean isReverse = false;
    private boolean started = false;
    private final List<String> moves = new ArrayList<>();
    private String initialFen = "";

    private final Random random = new SecureRandom();

    // ===== 计时：下标 0=红方，1=黑方 =====
    private final long[] mainLeft = new long[2];
    private final long[] moveLeft = new long[2];
    private Timeline clockTimeline;
    private long clockLastTick;
    // 低时倒计时闪烁（与每秒的秒数变化同步触发）
    private Timeline blinkTimeline;
    private long lastLowSecond = -1;

    public void initialize() {
        prop = Properties.getInstance();
        gameConfig = GameConfig.load();
        // 执子不记忆：每次进入默认玩家执红（引擎执黑）
        gameConfig.setEngineSide("black");
        isReverse = engineRed();

        initBoard();
        initAutoFitBoard();
        initListeners();
        initSkillCombo();
        initSide();
        initClockCombo();
        initClockDisplay();

        showConfigView();
        setVisible(reviewButton, false);
        setControlsLocked(false);
        setStatus("请设置对弈配置后开始");
    }

    private void initBoard() {
        board = new ChessBoard(canvas, ChessBoard.BoardSize.AUTOFIT_BOARD, prop.getBoardStyle(),
                prop.isStepTip(), true, false, prop.isStepSound(), prop.isShowNumber(), null);
        board.setBoardStyle(prop.getBoardStyle(), canvas);
        // 对弈模式隐藏左侧胜率条
        board.setShowWinRate(false);
        board.reverse(isReverse);
        redGo = true;
        moves.clear();
        initialFen = board.fenCode(true);
    }

    /** 左右两侧为时钟 / 状态文字预留的最小宽度 */
    private static final double SIDE_MIN = 240;

    /** 棋盘随窗口大小动态自适应 */
    private void initAutoFitBoard() {
        rootPane.widthProperty().addListener((obs, o, n) -> autoFitBoard());
        canvasPane.heightProperty().addListener((obs, o, n) -> autoFitBoard());
        Platform.runLater(this::autoFitBoard);
    }

    private void autoFitBoard() {
        if (board == null) {
            return;
        }
        double totalW = rootPane.getWidth();
        double h = canvasPane.getHeight();
        if (totalW <= 0 || h <= 0) {
            return;
        }
        // 先按窗口宽度减去左右最小预留来定棋盘大小
        board.autoFitSize(Math.max(1, totalW - 2 * SIDE_MIN), h);
        double boardW = canvas.getWidth();
        // 左右栏宽度 = 棋盘两侧的实际区域宽度，棋盘居中后即“棋盘边 ~ 窗体边”
        double side = Math.max(1, (totalW - boardW) / 2.0);
        if (leftPanel != null) {
            leftPanel.setPrefWidth(side);
        }
        if (rightPanel != null) {
            rightPanel.setPrefWidth(side);
        }
        // 状态文字区域随该宽度动态调整，左右各留 1/10 空白后居中
        if (statusLabel != null) {
            statusLabel.setMaxWidth(Math.max(1, side * 0.8));
        }
        applyClockScale(side);
        layoutClockOrder();
    }

    /**
     * 时钟字号与「局时/步时」间隔随窗体大小动态缩放。
     *
     * 以左侧栏宽度（棋盘边到窗体边）为基准：侧栏越宽时钟越大，
     * 同时限制在合理范围内，避免过小看不清或过大溢出。
     * 红/黑徽标、「局时/步时」小标题与时间数字一起按比例缩放，
     * 保证窗体缩小时整个时钟也能完整显示。
     */
    private void applyClockScale(double side) {
        // 按比例估算在不同字体/取整下可能刚好超宽而被省略号截断，
        // 这里改为实际测量、二分出“能完整放进侧栏”的最大字号。
        double lo = 8;
        double hi = Math.min(72, Math.max(8, side));
        if (redClockPanel != null && blackClockPanel != null && side > 0) {
            for (int i = 0; i < 12; i++) {
                double mid = (lo + hi) / 2;
                applyClockLayout(side, mid);
                if (clockPanelsFit(side, mid)) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
        }
        applyClockLayout(side, lo);
        applyStatusScale(lo);
    }

    /** 棋盘右侧提示文字与外边距随窗体缩放（与时钟基准字号成比例，限制在合理范围）。 */
    private void applyStatusScale(double baseFontSize) {
        if (statusLabel != null) {
            double f = Math.max(14, Math.min(48, baseFontSize * 0.58));
            statusLabel.setStyle("-fx-font-size: " + Math.round(f) + "px; -fx-font-weight: bold;");
        }
        if (rightPanel != null) {
            double pad = Math.max(6, baseFontSize * 0.27);
            rightPanel.setStyle("-fx-padding: 10px " + Math.round(pad) + "px 10px "
                    + Math.round(pad) + "px;");
        }
    }

    /** 时钟面板（连同一侧内边距）能否完整放进侧栏宽度。 */
    private boolean clockPanelsFit(double side, double fontSize) {
        double panelPad = Math.max(6, fontSize * 0.27);
        double available = side - 2 * panelPad - 2; // 预留 2px，避免舍入后刚好被截断
        for (HBox panel : new HBox[] { redClockPanel, blackClockPanel }) {
            if (panel == null) {
                continue;
            }
            panel.applyCss();
            if (panel.prefWidth(-1) > available) {
                return false;
            }
        }
        return true;
    }

    /** 按给定基准字号实际应用时钟各元素的大小、间距与内边距。 */
    private void applyClockLayout(double side, double fontSize) {
        double gap = Math.max(8, fontSize * 0.46);
        // 左栏左右 padding 与时钟卡片内边距也按比例缩放，避免缩小时边距偏大
        double panelPad = Math.max(6, fontSize * 0.27);
        double cardPadV = Math.max(3, fontSize * 0.115);
        double cardPadH = Math.max(5, fontSize * 0.19);
        String cardPadding = "-fx-padding: " + Math.round(cardPadV) + "px " + Math.round(cardPadH)
                + "px " + Math.round(cardPadV) + "px " + Math.round(cardPadH) + "px;";
        if (leftPanel != null) {
            // 局时上下各留 1/4 间距，避免两个时钟贴得太远
            double vInset = Math.max(0, canvasPane.getHeight() / 4.0);
            leftPanel.setStyle("-fx-padding: " + vInset + "px " + Math.round(panelPad) + "px "
                    + vInset + "px " + Math.round(panelPad) + "px;");
        }
        if (redClockPanel != null) {
            scaleClockNode(redClockPanel, fontSize);
            redClockPanel.setSpacing(gap);
            redClockPanel.setStyle(cardPadding);
        }
        if (blackClockPanel != null) {
            scaleClockNode(blackClockPanel, fontSize);
            blackClockPanel.setSpacing(gap);
            blackClockPanel.setStyle(cardPadding);
        }
        // 中间的低时倒计时数字与时钟数字等大
        if (lowSecondsLabel != null) {
            lowSecondsLabel.setStyle("-fx-font-size: " + Math.round(fontSize) + "px;");
        }
    }

    /** 递归给时钟块内的标签按样式类设置随基准字号缩放的内联样式。 */
    private void scaleClockNode(Node node, double fontSize) {
        if (node instanceof Label label) {
            if (label.getStyleClass().contains("clock-side")) {
                double f = Math.max(10, fontSize * 0.25);
                double v = Math.max(2, fontSize * 0.08);
                double h = Math.max(4, fontSize * 0.18);
                // 保留 fxml 里徽标的背景色（文字为“红”/“黑”，据此取色）
                String bg = "红".equals(label.getText()) ? "#c0392b" : "#2f3b46";
                label.setStyle("-fx-background-color: " + bg
                        + "; -fx-font-size: " + Math.round(f) + "px; -fx-padding: "
                        + Math.round(v) + "px " + Math.round(h) + "px "
                        + Math.round(v) + "px " + Math.round(h) + "px;");
            } else if (label.getStyleClass().contains("clock-caption")) {
                label.setStyle("-fx-font-size: " + Math.round(Math.max(9, fontSize * 0.21)) + "px;");
            } else if (label.getStyleClass().contains("clock-time")) {
                label.setStyle("-fx-font-size: " + Math.round(fontSize) + "px;");
            }
        } else if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                scaleClockNode(child, fontSize);
            }
        }
    }

    /** 时钟分别贴近棋盘上/下底线；翻转时红黑时钟随之上下互换。 */
    private void layoutClockOrder() {
        if (clockBox == null || redClockPanel == null || blackClockPanel == null || clockSpacer == null) {
            return;
        }
        if (clockOrderReversed != null && clockOrderReversed == isReverse) {
            return;
        }
        clockOrderReversed = isReverse;
        if (isReverse) {
            // 翻转后黑方在下方
            clockBox.getChildren().setAll(redClockPanel, clockSpacer, blackClockPanel);
        } else {
            clockBox.getChildren().setAll(blackClockPanel, clockSpacer, redClockPanel);
        }
    }

    private void initListeners() {
        skillComboBox.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) {
                GameConfig.SkillLevel s = GameConfig.SkillLevel.fromTitle(n);
                gameConfig.setSkillLevel(s.getValue());
                gameConfig.getEngineOptions().put("Skill Level", String.valueOf(s.getValue()));
                gameConfig.save();
            }
        });

        sideComboBox.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) {
                gameConfig.setEngineSide("引擎执红".equals(n) ? "red" : "black");
                // 玩家执黑时自动翻转棋盘，保证玩家一方在下方
                isReverse = engineRed();
                if (board != null) {
                    board.reverse(isReverse);
                }
                layoutClockOrder();
            }
        });

        clockComboBox.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) {
                gameConfig.setClockTier(GameConfig.ClockTier.fromTitle(n).name());
                gameConfig.save();
                if (!started) {
                    initClockDisplay();
                }
            }
        });
    }

    private void initSkillCombo() {
        skillComboBox.getItems().clear();
        for (GameConfig.SkillLevel s : GameConfig.SkillLevel.values()) {
            skillComboBox.getItems().add(s.getTitle());
        }
        skillComboBox.setValue(GameConfig.SkillLevel.fromValue(gameConfig.getSkillLevel()).getTitle());
    }

    private void initSide() {
        sideComboBox.getItems().clear();
        sideComboBox.getItems().addAll("引擎执红", "引擎执黑");
        sideComboBox.setValue("red".equals(gameConfig.getEngineSide()) ? "引擎执红" : "引擎执黑");
    }

    private void initClockCombo() {
        clockComboBox.getItems().clear();
        for (GameConfig.ClockTier t : GameConfig.ClockTier.values()) {
            clockComboBox.getItems().add(t.getTitle());
        }
        clockComboBox.setValue(GameConfig.ClockTier.fromName(gameConfig.getClockTier()).getTitle());
    }

    private void setControlsLocked(boolean locked) {
        skillComboBox.setDisable(locked);
        sideComboBox.setDisable(locked);
        clockComboBox.setDisable(locked);
        settingButton.setDisable(locked);
        startButton.setDisable(locked);
        resignButton.setDisable(!locked);
    }

    /** 开局前 / 对局结束后：显示“开始对弈”，隐藏“认输”。 */
    private void showConfigView() {
        setVisible(startButton, true);
        setVisible(resignButton, false);
    }

    /** 对局结束后：在顶部显示“复盘”按钮（仅在已有棋谱时）。 */
    private void showGameOverView() {
        showConfigView();
        setVisible(reviewButton, !moves.isEmpty());
    }

    /** 对局进行中：隐藏“开始对弈”，显示“认输”。 */
    private void showGameView() {
        setVisible(startButton, false);
        setVisible(resignButton, true);
        setVisible(reviewButton, false);
    }

    private void setVisible(javafx.scene.Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    // ===================== 计时 =====================

    private GameConfig.ClockTier clockTier() {
        return GameConfig.ClockTier.fromName(gameConfig.getClockTier());
    }

    /** 初始化双方的主局时与显示（未开始时按所选档位回显满时间）。 */
    private void initClockDisplay() {
        long main = clockTier().getMainMillis();
        long per = clockTier().getPerMoveMillis();
        mainLeft[0] = main;
        mainLeft[1] = main;
        moveLeft[0] = per;
        moveLeft[1] = per;
        updateClockLabels();
    }

    /** 开局时重置全部计时，并重置当前行棋方的步时。 */
    private void resetGameClock() {
        initClockDisplay();
        resetMoveClock();
    }

    /** 轮到新的一方走棋时，重置其步时可用时间。 */
    private void resetMoveClock() {
        int s = redGo ? 0 : 1;
        if (mainLeft[s] > 0) {
            moveLeft[s] = clockTier().getPerMoveMillis();
        } else {
            moveLeft[s] = clockTier().getBonusMillis();
        }
        updateClockLabels();
    }

    private void startClock() {
        clockLastTick = System.nanoTime();
        if (clockTimeline == null) {
            clockTimeline = new Timeline(new KeyFrame(Duration.millis(200), e -> tickClock()));
            clockTimeline.setCycleCount(Timeline.INDEFINITE);
        }
        clockTimeline.play();
    }

    private void stopClock() {
        if (clockTimeline != null) {
            clockTimeline.stop();
        }
        stopBlink();
    }

    private void tickClock() {
        if (!started) {
            return;
        }
        long now = System.nanoTime();
        long dt = (now - clockLastTick) / 1_000_000L;
        clockLastTick = now;
        if (dt <= 0) {
            return;
        }
        int s = redGo ? 0 : 1;
        boolean mainJustExhausted = false;
        if (mainLeft[s] > 0) {
            mainLeft[s] = Math.max(0, mainLeft[s] - dt);
            if (mainLeft[s] <= 0) {
                mainJustExhausted = true;
            }
        }
        moveLeft[s] -= dt;
        // 局时刚用完：本步剩余时间超过每步加时的话，把它降为每步加时
        if (mainJustExhausted) {
            long bonus = clockTier().getBonusMillis();
            if (moveLeft[s] > bonus) {
                moveLeft[s] = bonus;
            }
        }
        if (moveLeft[s] <= 0) {
            moveLeft[s] = 0;
            updateClockLabels();
            endGameByTimeout(s);
            return;
        }
        updateClockLabels();
    }

    private void updateClockLabels() {
        int current = redGo ? 0 : 1;
        redClockMain.setText(formatTime(mainLeft[0]));
        blackClockMain.setText(formatTime(mainLeft[1]));
        redClockMove.setText(formatTime(moveLeft[0]));
        blackClockMove.setText(formatTime(moveLeft[1]));
        setClockActive(redClockPanel, current == 0);
        setClockActive(blackClockPanel, current == 1);
        setClockLow(redClockMain, mainLeft[0]);
        setClockLow(redClockMove, moveLeft[0]);
        setClockLow(blackClockMain, mainLeft[1]);
        setClockLow(blackClockMove, moveLeft[1]);
        updateLowSeconds();
    }

    /** 我方“局时/步时”不足 10 秒（变红）时，在红黑时钟中间闪烁显示剩余秒数。 */
    private void updateLowSeconds() {
        if (lowSecondsLabel == null) {
            return;
        }
        int mySide = engineRed() ? 1 : 0;
        long low = Long.MAX_VALUE;
        if (mainLeft[mySide] > 0 && mainLeft[mySide] < 10_000) {
            low = Math.min(low, mainLeft[mySide]);
        }
        if (moveLeft[mySide] > 0 && moveLeft[mySide] < 10_000) {
            low = Math.min(low, moveLeft[mySide]);
        }
        if (low == Long.MAX_VALUE) {
            stopBlink();
            return;
        }
        long sec = (low + 999) / 1000;
        lowSecondsLabel.setText(String.valueOf(sec));
        // 仅在秒数变化的那一帧闪烁，保证与下面时间数字的跳秒同步
        if (sec != lastLowSecond) {
            lastLowSecond = sec;
            flashLowSeconds();
        }
    }

    /** 闪一下：立即显示，500ms 后隐藏；由每秒跳秒触发，故每秒闪烁一次且与时间同步。 */
    private void flashLowSeconds() {
        if (lowSecondsLabel == null) {
            return;
        }
        lowSecondsLabel.setOpacity(1.0);
        if (blinkTimeline == null) {
            blinkTimeline = new Timeline(new KeyFrame(Duration.millis(500),
                    e -> lowSecondsLabel.setOpacity(0.0)));
            blinkTimeline.setCycleCount(1);
        }
        blinkTimeline.stop();
        blinkTimeline.playFromStart();
    }

    private void stopBlink() {
        if (blinkTimeline != null) {
            blinkTimeline.stop();
        }
        lastLowSecond = -1;
        if (lowSecondsLabel != null) {
            lowSecondsLabel.setText("");
            lowSecondsLabel.setOpacity(1.0);
        }
    }

    /** 剩余时间不足 10 秒时数字变红 */
    private void setClockLow(Label label, long ms) {
        if (ms > 0 && ms < 10_000) {
            if (!label.getStyleClass().contains("clock-low")) {
                label.getStyleClass().add("clock-low");
            }
        } else {
            label.getStyleClass().remove("clock-low");
        }
    }

    private void setClockActive(HBox panel, boolean active) {
        if (panel == null) {
            return;
        }
        if (active) {
            if (!panel.getStyleClass().contains("clock-panel-active")) {
                panel.getStyleClass().add("clock-panel-active");
            }
        } else {
            panel.getStyleClass().remove("clock-panel-active");
        }
    }

    private String formatTime(long ms) {
        if (ms < 0) {
            ms = 0;
        }
        long totalSec = (ms + 999) / 1000;
        return String.format("%02d:%02d", totalSec / 60, totalSec % 60);
    }

    private void endGameByTimeout(int side) {
        if (!started) {
            return;
        }
        String loser = side == 0 ? "红方" : "黑方";
        started = false;
        stopClock();
        closeEngine();
        showGameOverView();
        setControlsLocked(false);
        setStatus(loser + " 超时判负，对局结束");
        // 本方法由计时 Timeline（动画处理中）调用，showAndWait 会抛
        // “not allowed during animation or layout processing”，改为下一帧再弹窗
        Platform.runLater(() -> DialogUtils.showInfoDialog("对局结束", loser + " 超时判负，本局结束。"));
    }

    // ===================== 按钮事件 =====================

    @FXML
    void backToMenuClick(ActionEvent e) {
        // 对局未结束（将死/困毙/超时/认输）前不允许返回主菜单
        if (started) {
            DialogUtils.showWarningDialog("提示", "对局进行中，请先点击“认输”结束本局，或将杀分出胜负后再返回主菜单。");
            return;
        }
        App.showMenu();
    }

    @FXML
    void settingButtonClick(ActionEvent e) {
        // 引擎高级参数与对弈开局库在弹窗中设置，直接读写 gameConfig
        App.openGameSetting(gameConfig);
    }

    @FXML
    void startButtonClick(ActionEvent e) {
        if (started) {
            return;
        }
        if (!validateConfig()) {
            return;
        }
        if (!loadEngine()) {
            return;
        }
        started = true;
        // 开始对弈即新的一局；玩家执黑时自动翻转棋盘
        isReverse = engineRed();
        resetBoard();
        showGameView();
        setControlsLocked(true);
        resetGameClock();
        startClock();
        if (engineTurn()) {
            engineGo();
        } else {
            setStatus("对局开始，请走棋");
        }
    }

    @FXML
    void resignButtonClick(ActionEvent e) {
        if (!started) {
            return;
        }
        closeEngine();
        started = false;
        stopClock();
        showGameOverView();
        setControlsLocked(false);
        setStatus("您已认输，对局结束");
        DialogUtils.showInfoDialog("对局结束", "您已认输，本局结束。");
    }

    @FXML
    void reviewButtonClick(ActionEvent e) {
        if (started || moves.isEmpty()) {
            return;
        }
        // 把整局棋谱转入分析（打谱）模式
        App.showManualWithGame(initialFen, moves);
    }

    @FXML
    void canvasClick(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        if (!humanTurn()) {
            return;
        }
        // 当前轮到玩家走棋，玩家只能移动自己一方的棋子
        boolean canRedGo = redGo;
        boolean canBlackGo = !redGo;
        String move = board.mouseClick((int) event.getX(), (int) event.getY(), canRedGo, canBlackGo);
        if (move != null) {
            moves.add(move);
            redGo = !redGo;
            resetMoveClock();
            checkGameOver();
            if (started) {
                if (engineTurn()) {
                    engineGo();
                } else {
                    setStatus("请走棋");
                }
            }
        }
    }

    // ===================== 引擎 =====================

    private boolean validateConfig() {
        if (StringUtils.isEmpty(gameConfig.getEngineName()) || StringUtils.isEmpty(gameConfig.getEnginePath())) {
            DialogUtils.showWarningDialog("提示", "请先选择对弈引擎");
            return false;
        }
        if (!new File(gameConfig.getEnginePath()).exists()) {
            DialogUtils.showWarningDialog("提示", "对弈引擎文件不存在，请重新选择引擎");
            return false;
        }
        if (!"uci".equals(gameConfig.getEngineProtocol()) && !"ucci".equals(gameConfig.getEngineProtocol())) {
            DialogUtils.showWarningDialog("提示", "对弈引擎协议无效");
            return false;
        }
        return true;
    }

    private boolean loadEngine() {
        closeEngine();
        try {
            engine = new Engine(gameConfig.toEngineConfig(), this);
            // 对弈模式的出招延迟由本控制器自行控制，关闭引擎内部的全局延迟
            engine.setEngineDelay(0, 0);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            engine = null;
            DialogUtils.showWarningDialog("提示", "引擎启动失败：" + (e.getMessage() == null ? "未知错误" : e.getMessage()));
            return false;
        }
    }

    private void closeEngine() {
        if (engine != null) {
            engine.close();
            engine = null;
        }
    }

    private void resetBoard() {
        moves.clear();
        redGo = true;
        board = new ChessBoard(canvas, ChessBoard.BoardSize.AUTOFIT_BOARD, prop.getBoardStyle(),
                prop.isStepTip(), true, false, prop.isStepSound(), prop.isShowNumber(), null);
        board.setBoardStyle(prop.getBoardStyle(), canvas);
        board.setShowWinRate(false);
        board.reverse(isReverse);
        initialFen = board.fenCode(true);
        autoFitBoard();
    }

    private boolean engineRed() {
        return "red".equals(gameConfig.getEngineSide());
    }

    private boolean engineTurn() {
        return started && ((engineRed() && redGo) || (!engineRed() && !redGo));
    }

    private boolean humanTurn() {
        return started && !engineTurn();
    }

    private void engineGo() {
        if (!started || engine == null || !engineTurn()) {
            return;
        }
        setStatus("引擎思考中...");
        if (gameConfig.isUseBook() && !offManual()) {
            Thread.startVirtualThread(() -> {
                List<BookData> results = queryGameBook();
                if (!results.isEmpty()) {
                    Platform.runLater(() -> {
                        if (started) {
                            setStatus("开局库出招...");
                        }
                    });
                    delayedPlay(results.get(0).getMove());
                } else {
                    runEngineAnalysis();
                }
            });
        } else {
            runEngineAnalysis();
        }
    }

    private boolean offManual() {
        return moves.size() / 2 >= gameConfig.getOffManualSteps();
    }

    private void runEngineAnalysis() {
        if (engine == null || !started) {
            return;
        }
        engine.setThreadNum(gameConfig.getThreadNum());
        engine.setHashSize(gameConfig.getHashSize());
        engine.setAnalysisModel(Engine.AnalysisModel.FIXED_STEPS, gameConfig.getDepth());
        engine.analysis(initialFen, moves, null);
    }

    private List<BookData> queryGameBook() {
        List<BookData> results = new ArrayList<>();
        for (String path : gameConfig.getOpenBookList()) {
            try {
                OpenBook ob;
                if (path.endsWith(".xqb")) {
                    ob = new XqbOpenBook(path);
                } else if (path.endsWith(".obk")) {
                    ob = new BhOpenBook(path);
                } else if (path.endsWith(".pfBook")) {
                    ob = new PfOpenBook(path);
                } else {
                    continue;
                }
                results.addAll(ob.query(board.getBoard(), redGo, gameConfig.getMoveRule()));
                ob.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        sortBookResults(results, gameConfig.getMoveRule());
        return results;
    }

    private void sortBookResults(List<BookData> list, MoveRule mr) {
        list.sort((o1, o2) -> switch (mr) {
            case BEST_SCORE -> Integer.compare(o2.getScore(), o1.getScore());
            case BEST_WINRATE -> Double.compare(o2.getWinRate(), o1.getWinRate());
            case FULL_RANDOM -> random.nextBoolean() ? 1 : -1;
            case POSITIVE_RANDOM -> (o1.getScore() > 0 && o2.getScore() > 0)
                    ? (random.nextBoolean() ? 1 : -1)
                    : Integer.compare(o2.getScore(), o1.getScore());
        });
    }

    private void delayedPlay(String move) {
        // 出招延迟独立于引擎思考时间：引擎算 3s、延迟 10s，则引擎算完后再等 10s（共约 13s）。
        // 延迟区间已按“加时 - 10s”设计（20→5~10 / 30→10~20 / 60→30~50），
        // 即使局时在等待期间耗尽、本步降为加时，也不会超时，故无需再算本步剩余。
        long wait = clockTier().randomDelayMillis();
        try {
            Thread.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        Platform.runLater(() -> {
            if (started) {
                playMove(move);
            }
        });
    }

    private void playMove(String move) {
        if (!started || move == null || move.length() != 4) {
            return;
        }
        board.move(move);
        moves.add(move);
        redGo = !redGo;
        resetMoveClock();
        checkGameOver();
        if (started) {
            if (engineTurn()) {
                engineGo();
            } else {
                setStatus("请走棋");
            }
        }
    }

    private void checkGameOver() {
        if (!started) {
            return;
        }
        if (XiangqiUtils.isSha(board.getBoard(), redGo)) {
            String loser = redGo ? "红方" : "黑方";
            started = false;
            stopClock();
            closeEngine();
            showGameOverView();
            setControlsLocked(false);
            setStatus("对局结束，" + loser + " 被将死（或困毙）");
            DialogUtils.showInfoDialog("对局结束", loser + " 被将死（或困毙），本局结束。");
        }
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    public GameConfig getGameConfig() {
        return gameConfig;
    }

    public void dispose() {
        started = false;
        stopClock();
        closeEngine();
        // 独立记忆对弈窗口的位置与大小
        if (App.getMainStage() != null && App.getMainStage().isShowing()) {
            gameConfig.setStageX(App.getMainStage().getX());
            gameConfig.setStageY(App.getMainStage().getY());
            gameConfig.setStageWidth(App.getMainStage().getWidth());
            gameConfig.setStageHeight(App.getMainStage().getHeight());
            gameConfig.setStagePositionSet(true);
        }
        gameConfig.save();
    }

    @Override
    public void bestMove(String first, String second) {
        if (!started || !engineTurn()) {
            return;
        }
        delayedPlay(first);
    }

    @Override
    public void thinkDetail(ThinkData td) {
        if (!started || td == null) {
            return;
        }
        Platform.runLater(() -> {
            if (started) {
                // 只提示思考中，不在状态栏显示深度/分数
                statusLabel.setText("引擎思考中...");
            }
        });
    }

    @Override
    public void showBookResults(List<BookData> list) {
        // 对弈模式自行查询开局库，不使用引擎内部的库招结果
    }
}

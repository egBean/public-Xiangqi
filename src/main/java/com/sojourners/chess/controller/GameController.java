package com.sojourners.chess.controller;

import com.sojourners.chess.App;
import com.sojourners.chess.board.ChessBoard;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.enginee.Engine;
import com.sojourners.chess.enginee.EngineCallBack;
import com.sojourners.chess.model.BookData;
import com.sojourners.chess.model.GameConfig;
import com.sojourners.chess.model.GameSnapshot;
import com.sojourners.chess.model.ThinkData;
import com.sojourners.chess.openbook.BhOpenBook;
import com.sojourners.chess.openbook.MoveRule;
import com.sojourners.chess.openbook.OpenBook;
import com.sojourners.chess.openbook.PfOpenBook;
import com.sojourners.chess.openbook.XqbOpenBook;
import com.sojourners.chess.util.DialogUtils;
import com.sojourners.chess.util.PathUtils;
import com.sojourners.chess.util.StringUtils;
import com.sojourners.chess.util.XiangqiUtils;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
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
    private StackPane statusOverlay;
    @FXML
    private Canvas canvas;
    @FXML
    private Label statusLabel;
    @FXML
    private ComboBox<String> skillComboBox;
    @FXML
    private Button sideButton;
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
    private Button suspendButton;
    @FXML
    private VBox leftPanel;
    @FXML
    private StackPane rightPanel;
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

    /** 对弈背景图（skin/back.png），不存在时为 null */
    private Image backgroundImage;

    /** 状态文字过长时悬停显示的完整内容提示 */
    private Tooltip statusTooltip;

    private boolean redGo = true;
    private boolean isReverse = false;
    private volatile boolean started = false;
    /** 对局代号：每次开新局/恢复/封棋都递增，用于丢弃旧引擎与旧延迟出招的回调 */
    private volatile long gameGeneration = 0;
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

        applyBackgroundImage();
        initStatusOverlay();
        initFrostedGlass();

        showConfigView();
        setVisible(reviewButton, false);
        setControlsLocked(false);
        setStatus("请设置对弈配置后开始");
        // 进入对弈后检查是否有未完成的封棋，有则提示是否恢复
        Platform.runLater(this::checkSuspendRestore);
    }

    /**
     * 对弈模式背景图片：若 skin/back.png 存在，则将其作为整个对弈界面的背景。
     * 图片随窗口大小自动缩放铺满（cover），文件不存在时保持主题默认背景。
     */
    private void applyBackgroundImage() {
        backgroundImage = loadBackgroundImage();
        if (rootPane == null || backgroundImage == null) {
            return;
        }
        BackgroundSize size = new BackgroundSize(1.0, 1.0, true, true, false, true);
        BackgroundImage bg = new BackgroundImage(backgroundImage, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER, size);
        rootPane.setBackground(new Background(bg));
    }

    /** 查找对弈背景图 skin/back.png（兼容 IDE 工作目录与打包后的 jar 目录）。 */
    private Image loadBackgroundImage() {
        File file = new File("skin", "back.png");
        if (!file.isFile()) {
            String jarPath = PathUtils.getJarPath();
            if (jarPath != null) {
                file = new File(jarPath, "skin/back.png");
            }
        }
        if (!file.isFile()) {
            return null;
        }
        try {
            return new Image(file.toURI().toString());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ===================== 磨砂（高斯模糊）面板 =====================

    private static final double FROST_BLUR = 16;
    private static final double FROST_ARC = 22;
    private static final double FROST_MARGIN = 20;
    private static final double FROST_INSET = 2;

    /** 给时钟 / 状态面板加一层磨砂高斯模糊背景（存在 skin/back.png 时生效）。 */
    private void initFrostedGlass() {
        if (backgroundImage == null) {
            return;
        }
        installFrostedGlass(redClockPanel);
        installFrostedGlass(blackClockPanel);
        installFrostedGlass(statusOverlay);
    }

    /**
     * 在面板内容下方插入一层与主背景图对齐、经高斯模糊的 ImageView，
     * 形成“毛玻璃”效果；原面板的半透明底色与边框保留作为颜色罩与描边。
     */
    private void installFrostedGlass(Pane panel) {
        if (panel == null || backgroundImage == null) {
            return;
        }
        ImageView frost = new ImageView(backgroundImage);
        frost.setManaged(false);
        frost.setPreserveRatio(false);
        frost.setMouseTransparent(true);
        frost.setOpacity(0.72);
        frost.setEffect(new GaussianBlur(FROST_BLUR));
        Rectangle clip = new Rectangle();
        clip.setArcWidth(FROST_ARC);
        clip.setArcHeight(FROST_ARC);
        frost.setClip(clip);
        panel.getChildren().add(0, frost);

        Runnable update = () -> updateFrostedGlass(panel, frost, clip);
        panel.widthProperty().addListener((obs, o, n) -> update.run());
        panel.heightProperty().addListener((obs, o, n) -> update.run());
        panel.localToSceneTransformProperty().addListener((obs, o, n) -> update.run());
        panel.sceneProperty().addListener((obs, o, n) -> update.run());
        Platform.runLater(update);
    }

    /** 根据面板在 rootPane 中的位置，同步磨砂图层的放大区域（与 cover 背景对齐）。 */
    private void updateFrostedGlass(Pane panel, ImageView frost, Rectangle clip) {
        if (backgroundImage == null || rootPane == null || panel.getScene() == null) {
            return;
        }
        double pw = panel.getWidth();
        double ph = panel.getHeight();
        double rootW = rootPane.getWidth();
        double rootH = rootPane.getHeight();
        double imgW = backgroundImage.getWidth();
        double imgH = backgroundImage.getHeight();
        if (pw <= 0 || ph <= 0 || rootW <= 0 || rootH <= 0 || imgW <= 0 || imgH <= 0) {
            return;
        }
        // 背景图在 rootPane 中以 cover + center 方式铺满
        double scale = Math.max(rootW / imgW, rootH / imgH);
        double offX = (rootW - imgW * scale) / 2.0;
        double offY = (rootH - imgH * scale) / 2.0;
        Point2D p = rootPane.sceneToLocal(panel.localToScene(0, 0));
        double vx = (p.getX() - offX) / scale;
        double vy = (p.getY() - offY) / scale;
        double vw = pw / scale;
        double vh = ph / scale;
        // 向外扩一圈，避免模糊在视口边缘出现透明软边
        double m = FROST_MARGIN / scale;
        double x0 = Math.max(0, vx - m);
        double y0 = Math.max(0, vy - m);
        // 布局/缩放过程中面板可能瞬时落在背景图范围外，这里夹取到图片内，
        // 避免 Viewport 宽高为负导致 Rectangle2D 抛 IllegalArgumentException
        double x1 = Math.max(x0, Math.min(imgW, vx + vw + m));
        double y1 = Math.max(y0, Math.min(imgH, vy + vh + m));
        double vpW = x1 - x0;
        double vpH = y1 - y0;
        if (!(vpW > 0) || !(vpH > 0)) {
            return;
        }
        frost.setViewport(new Rectangle2D(x0, y0, vpW, vpH));
        frost.setFitWidth(vpW * scale);
        frost.setFitHeight(vpH * scale);
        frost.relocate((x0 - vx) * scale, (y0 - vy) * scale);
        // 磨砂层向内缩 2px，保留面板自身的半透明底色与边框（含行棋方高亮边框）
        clip.setX((vx - x0) * scale + FROST_INSET);
        clip.setY((vy - y0) * scale + FROST_INSET);
        clip.setWidth(Math.max(0, pw - 2 * FROST_INSET));
        clip.setHeight(Math.max(0, ph - 2 * FROST_INSET));
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
        // 右栏为棋盘右侧的空白区域，状态浮层位于其右下角
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
    }

    /**
     * 初始化棋盘右侧空白区域底部的状态栏。
     * 宽度占满右栏，高度为右栏的 1/4，靠右下角对齐。
     */
    private void initStatusOverlay() {
        if (rightPanel == null || statusOverlay == null) {
            return;
        }
        statusOverlay.prefWidthProperty().bind(rightPanel.widthProperty());
        statusOverlay.prefHeightProperty().bind(rightPanel.heightProperty().divide(4));
        statusOverlay.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        statusOverlay.widthProperty().addListener((obs, o, n) -> applyStatusScale());
        statusOverlay.heightProperty().addListener((obs, o, n) -> applyStatusScale());
        if (statusLabel != null) {
            statusLabel.widthProperty().addListener((obs, o, n) -> updateStatusTooltip());
            statusLabel.heightProperty().addListener((obs, o, n) -> updateStatusTooltip());
        }
    }

    /** 状态文字字号随浮层大小缩放。 */
    private void applyStatusScale() {
        if (statusLabel == null || statusOverlay == null) {
            return;
        }
        double w = statusOverlay.getWidth();
        double h = statusOverlay.getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        double f = Math.max(13, Math.min(40, Math.min(w, h) * 0.13));
        statusLabel.setStyle("-fx-font-size: " + Math.round(f) + "px; -fx-font-weight: bold;");
        updateStatusTooltip();
    }

    /**
     * 文字过长（超出浮层可视高度）时安装 Tooltip，鼠标悬停显示完整内容；
     * 文字放得下时移除 Tooltip。
     */
    private void updateStatusTooltip() {
        if (statusLabel == null || statusOverlay == null) {
            return;
        }
        statusLabel.applyCss();
        double w = statusLabel.getWidth();
        double h = statusLabel.getHeight();
        boolean overflow = w > 1 && h > 1 && statusLabel.prefHeight(w) > h + 1;
        if (overflow) {
            if (statusTooltip == null) {
                statusTooltip = new Tooltip();
                statusTooltip.setWrapText(true);
                statusTooltip.setMaxWidth(360);
                statusTooltip.textProperty().bind(statusLabel.textProperty());
                Tooltip.install(statusOverlay, statusTooltip);
            }
        } else if (statusTooltip != null) {
            Tooltip.uninstall(statusOverlay, statusTooltip);
            statusTooltip.textProperty().unbind();
            statusTooltip = null;
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
        sideButton.setText(sideText());
    }

    /** “执棋”按钮文字（表示引擎执红还是执黑）。 */
    private String sideText() {
        return engineRed() ? "引擎执红" : "引擎执黑";
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
        sideButton.setDisable(locked);
        clockComboBox.setDisable(locked);
        settingButton.setDisable(locked);
        startButton.setDisable(locked);
        resignButton.setDisable(!locked);
        suspendButton.setDisable(!locked);
    }

    /** 开局前 / 对局结束后：显示“开始对弈”，隐藏“认输 / 封棋”。 */
    private void showConfigView() {
        setVisible(startButton, true);
        setVisible(resignButton, false);
        setVisible(suspendButton, false);
    }

    /** 对局结束后：在顶部显示“复盘”按钮（仅在已有棋谱时）。 */
    private void showGameOverView() {
        showConfigView();
        setVisible(reviewButton, !moves.isEmpty());
    }

    /** 对局进行中：隐藏“开始对弈”，显示“认输 / 封棋”。 */
    private void showGameView() {
        setVisible(startButton, false);
        setVisible(resignButton, true);
        setVisible(suspendButton, true);
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
        moveLeft[s] = freshMoveMillis(mainLeft[s]);
        updateClockLabels();
    }

    /** 一步的“步时”满值：局时未用完 → 步时；局时用完 → 每步加秒。 */
    private long freshMoveMillis(long mainLeft) {
        return mainLeft > 0 ? clockTier().getPerMoveMillis() : clockTier().getBonusMillis();
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
        // 对局未结束（将死/困毙/超时/认输/封棋）前不允许直接返回主菜单
        if (started) {
            DialogUtils.showWarningDialog("提示", "对局进行中，若想离开可点击“封棋”保存本局后再返回；或点击“认输”结束本局。");
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
    void sideButtonClick(ActionEvent e) {
        // 点击在“引擎执红 / 引擎执黑”之间切换
        gameConfig.setEngineSide(engineRed() ? "black" : "red");
        // 玩家执黑时自动翻转棋盘，保证玩家一方在下方
        isReverse = engineRed();
        if (board != null) {
            board.reverse(isReverse);
        }
        layoutClockOrder();
        sideButton.setText(sideText());
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

    /**
     * 封棋：把当前棋局（棋谱 / 计时 / 引擎执红黑）保存到临时文件并回到主菜单。
     */
    @FXML
    void suspendButtonClick(ActionEvent e) {
        if (!started) {
            return;
        }
        // 先停止对局流程：置 started=false 并递增 gameGeneration 使引擎回调失效，
        // 同时让正在 sleep 的延迟出招线程在唤醒后直接丢弃，避免封棋后继续走子/计时
        started = false;
        gameGeneration++;
        stopClock();
        closeEngine();

        GameSnapshot snapshot = new GameSnapshot();
        snapshot.setInitialFen(initialFen);
        snapshot.setMoves(new ArrayList<>(moves));
        snapshot.setRedGo(redGo);
        snapshot.setEngineSide(gameConfig.getEngineSide());
        snapshot.setClockTier(gameConfig.getClockTier());
        snapshot.setRedMain(mainLeft[0]);
        snapshot.setBlackMain(mainLeft[1]);
        GameSnapshot.save(snapshot);

        // 回到主菜单（showMenu 内部会 dispose 当前控制器并保存窗口位置）
        App.showMenu();
    }

    @FXML
    void reviewButtonClick(ActionEvent e) {
        if (started || moves.isEmpty()) {
            return;
        }
        // 把整局棋谱转入分析（打谱）模式
        App.showManualWithGame(initialFen, moves);
    }

    /** 进入对弈模式后检查是否存在未完成的封棋，有则提示是否恢复。 */
    private void checkSuspendRestore() {
        if (!GameSnapshot.exists()) {
            return;
        }
        // 先读出内容并删除临时文件，再处理后续的恢复逻辑
        GameSnapshot snapshot = GameSnapshot.load();
        GameSnapshot.delete();
        if (snapshot == null) {
            return;
        }
        boolean restore = DialogUtils.showConfirmDialog("恢复棋局",
                "检测到上次未完成的棋局，是否继续对局？\n\n选择“确定”继续对局，选择“取消”开始新对局。");
        if (!restore) {
            return;
        }
        restoreGame(snapshot);
    }

    /** 从封棋快照恢复棋局并继续对弈。 */
    private void restoreGame(GameSnapshot snapshot) {
        if (!validateConfig()) {
            DialogUtils.showWarningDialog("提示", "无法恢复棋局：对弈引擎不可用，请重新选择引擎。");
            return;
        }
        // 引擎执红黑
        String side = snapshot.getEngineSide();
        if (side != null && !side.isBlank()) {
            gameConfig.setEngineSide(side);
        }
        isReverse = engineRed();
        sideButton.setText(sideText());
        layoutClockOrder();
        // 局时档位
        String tier = snapshot.getClockTier();
        if (tier != null && !tier.isBlank()) {
            gameConfig.setClockTier(tier);
            clockComboBox.setValue(GameConfig.ClockTier.fromName(tier).getTitle());
        }
        // 棋谱与行棋方
        initialFen = snapshot.getInitialFen();
        moves.clear();
        if (snapshot.getMoves() != null) {
            moves.addAll(snapshot.getMoves());
        }
        redGo = snapshot.isRedGo();
        // 棋盘恢复到当前局面（恢复期间关闭走棋音效）
        boolean sound = prop.isStepSound();
        board.setStepSound(false);
        board.browseChessRecord(initialFen, new ArrayList<>(moves));
        board.setStepSound(sound);
        board.reverse(isReverse);
        // 时间：局时按快照恢复；步时直接“白送”满值
        // （局时用完送加秒、否则送步时），双方都刷新保证显示一致
        mainLeft[0] = snapshot.getRedMain();
        mainLeft[1] = snapshot.getBlackMain();
        moveLeft[0] = freshMoveMillis(mainLeft[0]);
        moveLeft[1] = freshMoveMillis(mainLeft[1]);
        updateClockLabels();
        // 开始对局
        if (!loadEngine()) {
            return;
        }
        started = true;
        showGameView();
        setControlsLocked(true);
        startClock();
        if (engineTurn()) {
            engineGo();
        } else {
            setStatus("已恢复对局，请走棋");
        }
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
            // 当前引擎绑定的对局代号：代号变化后其回调不再生效
            final long gen = ++gameGeneration;
            EngineCallBack callback = new EngineCallBack() {
                @Override
                public void bestMove(String first, String second) {
                    if (gen != gameGeneration) {
                        return;
                    }
                    GameController.this.bestMove(first, second, gen);
                }

                @Override
                public void thinkDetail(ThinkData td) {
                    if (gen != gameGeneration) {
                        return;
                    }
                    GameController.this.thinkDetail(td);
                }

                @Override
                public void showBookResults(List<BookData> list) {
                    if (gen != gameGeneration) {
                        return;
                    }
                    GameController.this.showBookResults(list);
                }
            };
            engine = new Engine(gameConfig.toEngineConfig(), callback);
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
        final long gen = gameGeneration;
        setStatus("引擎思考中...");
        if (gameConfig.isUseBook() && !offManual()) {
            Thread.startVirtualThread(() -> {
                List<BookData> results = queryGameBook();
                if (gen != gameGeneration) {
                    return;
                }
                if (!results.isEmpty()) {
                    Platform.runLater(() -> {
                        if (gen == gameGeneration && started) {
                            setStatus("开局库出招...");
                        }
                    });
                    delayedPlay(results.get(0).getMove(), gen);
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
        List<String> paths;
        String active = gameConfig.getBookPath();
        if (active != null && !active.isBlank()) {
            // 对弈设置中已选定某个开局库时，只查询该库
            paths = List.of(active);
        } else {
            paths = gameConfig.getOpenBookList();
        }
        for (String path : paths) {
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

    private void delayedPlay(String move, long gen) {
        // 出招延迟独立于引擎思考时间：引擎算 3s、延迟 10s，则引擎算完后再等 10s（共约 13s）。
        // 延迟区间已按“加时 - 10s”设计（20→5~10 / 30→10~20 / 60→30~50），不会超时。
        long wait = clockTier().randomDelayMillis();
        try {
            Thread.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        Platform.runLater(() -> {
            // 封棋 / 重新开始后 gameGeneration 会变化，旧的延迟出招直接丢弃
            if (gen == gameGeneration && started) {
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
        // 布局更新后再按实际尺寸判断是否需要悬停提示
        Platform.runLater(this::updateStatusTooltip);
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
        bestMove(first, second, gameGeneration);
    }

    /** 引擎返回最佳着法（gen 为产生该着法的对局代号）。 */
    private void bestMove(String first, String second, long gen) {
        if (!started || !engineTurn()) {
            return;
        }
        delayedPlay(first, gen);
    }

    @Override
    public void thinkDetail(ThinkData td) {
        if (!started || td == null) {
            return;
        }
        Platform.runLater(() -> {
            if (started) {
                // 只提示思考中，不在状态栏显示深度/分数
                setStatus("引擎思考中...");
            }
        });
    }

    @Override
    public void showBookResults(List<BookData> list) {
        // 对弈模式自行查询开局库，不使用引擎内部的库招结果
    }
}

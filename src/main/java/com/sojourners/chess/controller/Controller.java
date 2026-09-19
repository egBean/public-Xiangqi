package com.sojourners.chess.controller;

import com.sojourners.chess.App;
import com.sojourners.chess.board.ChessBoard;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.controller.handle.ChessManualCallBack;
import com.sojourners.chess.controller.handle.ChessManualHandle;
import com.sojourners.chess.enginee.Engine;
import com.sojourners.chess.enginee.EngineCallBack;
import com.sojourners.chess.linker.*;
import com.sojourners.chess.menu.BoardContextMenu;
import com.sojourners.chess.model.BookData;
import com.sojourners.chess.model.EngineConfig;
import com.sojourners.chess.model.ManualRecord;
import com.sojourners.chess.model.ThinkData;
import com.sojourners.chess.openbook.OpenBookManager;
import com.sojourners.chess.review.GameReview;
import com.sojourners.chess.review.ReviewItem;
import com.sojourners.chess.review.ReviewSummary;
import com.sojourners.chess.util.*;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.canvas.Canvas;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.*;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.WritableImage;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Callback;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class Controller implements EngineCallBack, LinkerCallBack, ChessManualCallBack {

    @FXML
    private Canvas canvas;

    @FXML
    private BorderPane borderPane;
    @FXML
    private StackPane loadingOverlay;
    @FXML
    private Label loadingLabel;
    @FXML
    private Label infoShowLabel;
    @FXML
    private ToolBar statusToolBar;
    @FXML
    private Label timeShowLabel;
    @FXML
    private SplitPane splitPane;
    @FXML
    private SplitPane splitPane2;

    @FXML
    private ListView<ThinkData> listView;

    @FXML
    private ComboBox<String> engineComboBox;

    @FXML
    private ComboBox<String> linkComboBox;

    @FXML
    private ComboBox<String> hashComboBox;

    @FXML
    private ComboBox<String> threadComboBox;

    @FXML
    private RadioMenuItem menuOfLargeBoard;
    @FXML
    private RadioMenuItem menuOfBigBoard;
    @FXML
    private RadioMenuItem menuOfMiddleBoard;
    @FXML
    private RadioMenuItem menuOfSmallBoard;
    @FXML
    private RadioMenuItem menuOfAutoFitBoard;

    /* ============ 棋盘样式相关 ============ */
    @FXML private Menu boardTypeMenu;
    @FXML private ToggleGroup boardTypeGroup;
    @FXML
    private RadioMenuItem menuOfDefaultBoard;

    @FXML
    private CheckMenuItem menuOfStepTip;
    @FXML
    private CheckMenuItem menuOfStepSound;
    @FXML
    private CheckMenuItem menuOfLinkBackMode;
    @FXML
    private CheckMenuItem menuOfLinkAnimation;
    @FXML
    private CheckMenuItem menuOfShowStatus;
    @FXML
    private CheckMenuItem menuOfShowNumber;

    @FXML
    private CheckMenuItem menuOfTopWindow;

    private Properties prop;

    private Engine engine;

    private ChessBoard board;

    private AbstractGraphLinker graphLinker;

    @FXML
    private Button analysisButton;
    @FXML
    private Button blackButton;
    @FXML
    private Button redButton;
    @FXML
    private Button reverseButton;
    @FXML
    private Button newButton;
    @FXML
    private Button copyButton;
    @FXML
    private Button pasteButton;
    @FXML
    private Button regretButton;

    @FXML
    private BorderPane charPane;
    private XYChart.Series lineChartSeries;

    @FXML
    private Button immediateButton;
    @FXML
    private Button reviewButton;
    @FXML
    private Button bookSwitchButton;
    @FXML
    private Button linkButton;
    @FXML
    private Button deductionButton;
    @FXML
    private Button changeTacticButton;

    @FXML
    private TableView<ManualRecord> recordTable;

    @FXML
    private TableView<BookData> bookTable;

    private SimpleObjectProperty<Boolean> robotRed = new SimpleObjectProperty<>(false);
    private SimpleObjectProperty<Boolean> robotBlack = new SimpleObjectProperty<>(false);
    private SimpleObjectProperty<Boolean> robotAnalysis = new SimpleObjectProperty<>(false);
    private SimpleObjectProperty<Boolean> isReverse = new SimpleObjectProperty<>(false);
    private SimpleObjectProperty<Boolean> linkMode = new SimpleObjectProperty<>(false);
    private SimpleObjectProperty<Boolean> useOpenBook = new SimpleObjectProperty<>(false);

    /**
     * 走棋方
     */
    private boolean redGo;

    /**
     * 正在思考（用于连线判断）
     */
    private volatile boolean isThinking;

    /**
     * 正在复盘
     */
    private volatile boolean reviewing;

    /**
     * 变招列表
     */
    private List<String> tacticList;


    /** 当前皮肤，内置默认用 "default" 表示 */
    private String currentBoardSkin = "default";
    /** 动态生成的皮肤菜单项 */
    private final List<RadioMenuItem> skinItems = new ArrayList<>();


    /** 扫描 skin 目录并生成菜单项 */
    private void initBoardTypeMenu() {

        String boardStyle = prop.getBoardStyle();

        // 校验皮肤：非 default 时，如果 skin 目录或对应皮肤目录不存在，则回退 default
        if (!"default".equalsIgnoreCase(boardStyle)) {
            File projectSkinDir = new File("skin");
            File targetSkinDir = new File(projectSkinDir, boardStyle);

            if (!projectSkinDir.isDirectory() || !targetSkinDir.isDirectory()) {
                boardStyle = "default";
                prop.setBoardStyle(boardStyle);
            }
        }

        currentBoardSkin = boardStyle;
        // 从皮肤目录读 config.json（没有就创建，默认 0,0,0,1）
        if (!"default".equalsIgnoreCase(this.currentBoardSkin)) {
            loadOrCreateSkinConfig(this.currentBoardSkin);
        }

        menuOfDefaultBoard.setUserData("default");
        skinItems.add(menuOfAutoFitBoard);
        File skinDir = resolveSkinDir();
        if (skinDir != null) {
            File[] dirs = skinDir.listFiles(File::isDirectory);
            if (dirs != null) {
                Arrays.sort(dirs,
                        Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                for (File dir : dirs) {
                    String name = dir.getName();
                    if (name.startsWith(".")) continue;   // 跳过隐藏目录

                    RadioMenuItem item = new RadioMenuItem(name);
                    item.setToggleGroup(boardTypeGroup);
                    item.setUserData(name);               // 皮肤目录名
                    item.setOnAction(this::boardTypeSelected);
                    boardTypeMenu.getItems().add(item);
                    skinItems.add(item);
                }
            }
        }

        // 恢复上次选择（首次运行就是默认）
        selectBoardSkin(currentBoardSkin);
    }

    /** 根据皮肤名恢复单选状态 */
    private void selectBoardSkin(String skin) {
        if ("default".equalsIgnoreCase(skin)) {
            menuOfDefaultBoard.setSelected(true);
            return;
        }
        for (RadioMenuItem item : skinItems) {
            if (skin.equalsIgnoreCase(item.getText())) {
                item.setSelected(true);
                return;
            }
        }
        menuOfDefaultBoard.setSelected(true);
    }


    /** 定位 skin 目录：存在就返回，不存在就创建 */
    private File resolveSkinDir() {
        File dir = new File("skin");
        if (dir.isDirectory()) return dir;
        if (dir.mkdirs()) return dir;
        return null;
    }


    @FXML
    public void newButtonClick(ActionEvent event) {
        if (linkMode.getValue()) {
            stopGraphLink();
        }

        newChessBoard(null);
    }

    @FXML
    void boardTypeSelected(ActionEvent event) {
        RadioMenuItem item = (RadioMenuItem) event.getTarget();
        Object data = item.getUserData();
        this.currentBoardSkin = data.toString();
        prop.setBoardStyle(this.currentBoardSkin);

        // 从皮肤目录读 config.json（没有就创建，默认 0,0,0,1）
        if (this.currentBoardSkin == null || "default".equalsIgnoreCase(this.currentBoardSkin)) {
            board.setBoardStyle(currentBoardSkin,this.canvas);
            return;
        }
        loadOrCreateSkinConfig(this.currentBoardSkin);

        board.setBoardStyle(currentBoardSkin,this.canvas);

    }


    private void writeeSkinConfig(String skin) {

        File skinDir = findSkinDir(skin);

        File cfgFile = new File(skinDir, "config.json");

        String json = String.format(
                "{%n  \"boardOffsetX\": %d,%n  \"pieceOffsetX\": %s,%n" +
                        "  \"pieceOffsetY\": %s,%n  \"pieceScale\": %s,%n" +
                        "  \"pieceShadow\": %d%n}%n",
                prop.getBoardOffsetX(),
                prop.getPieceOffsetX(),
                prop.getPieceOffsetY(),
                prop.getPieceScale(),
                prop.getPieceShadow());
        try {
            java.nio.file.Files.writeString(
                    cfgFile.toPath(), json, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void loadOrCreateSkinConfig(String skin) {

        File skinDir = findSkinDir(skin);
        if (skinDir == null) return;

        File cfgFile = new File(skinDir, "config.json");

        if (!cfgFile.exists()) {
            prop.setBoardOffsetX(0);
            prop.setPieceOffsetX(0.0);
            prop.setPieceOffsetY(0.0);
            prop.setPieceScale(1.0);
            prop.setPieceShadow(0);

            String json = String.format(
                    "{%n  \"boardOffsetX\": %d,%n  \"pieceOffsetX\": %s,%n" +
                            "  \"pieceOffsetY\": %s,%n  \"pieceScale\": %s,%n" +
                            "  \"pieceShadow\": %d%n}%n",
                    0, 0, 0, 1, 0);
            try {
                java.nio.file.Files.writeString(
                        cfgFile.toPath(), json, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        try {
            String json = java.nio.file.Files.readString(
                    cfgFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            prop.setBoardOffsetX((int)readDouble(json, "boardOffsetX", 0));
            prop.setPieceOffsetX(readDouble(json, "pieceOffsetX", 0));
            prop.setPieceOffsetY(readDouble(json, "pieceOffsetY", 0));
            prop.setPieceScale(readDouble(json, "pieceScale",   1));
            prop.setPieceShadow((int) readDouble(json, "pieceShadow", 0));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private double readDouble(String json, String key, double def) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"" + key + "\"\\s*:\\s*(-?\\d*\\.?\\d+)")
                .matcher(json);
        return m.find() ? Double.parseDouble(m.group(1)) : def;
    }


    /** 从 jarPath 往上找 skin/<皮肤名> */
    private File findSkinDir(String skin) {
        File dir = new File("skin", skin);
        if (dir.isDirectory()) return dir;

        if (dir.mkdirs()) return dir;
        return null;
    }

    /** 皮肤配置 */

    @FXML
    void shadow(ActionEvent event) {
        RadioMenuItem item = (RadioMenuItem) event.getTarget();
        if (item.equals(menuOfLargeBoard)) {
            prop.setBoardSize(ChessBoard.BoardSize.LARGE_BOARD);
        } else if (item.equals(menuOfBigBoard)) {
            prop.setBoardSize(ChessBoard.BoardSize.BIG_BOARD);
        } else if (item.equals(menuOfMiddleBoard)) {
            prop.setBoardSize(ChessBoard.BoardSize.MIDDLE_BOARD);
        } else if (item.equals(menuOfAutoFitBoard)) {
            prop.setBoardSize(ChessBoard.BoardSize.AUTOFIT_BOARD);
        } else {
            prop.setBoardSize(ChessBoard.BoardSize.SMALL_BOARD);
        }
        board.setBoardSize(prop.getBoardSize());
        if (prop.getBoardSize() == ChessBoard.BoardSize.AUTOFIT_BOARD) {
            board.autoFitSize(borderPane.getWidth(), borderPane.getHeight(), splitPane.getDividerPositions()[0]);
        }
    }

    @FXML
    void boardSizeSelected(ActionEvent event) {
        RadioMenuItem item = (RadioMenuItem) event.getTarget();
        if (item.equals(menuOfLargeBoard)) {
            prop.setBoardSize(ChessBoard.BoardSize.LARGE_BOARD);
        } else if (item.equals(menuOfBigBoard)) {
            prop.setBoardSize(ChessBoard.BoardSize.BIG_BOARD);
        } else if (item.equals(menuOfMiddleBoard)) {
            prop.setBoardSize(ChessBoard.BoardSize.MIDDLE_BOARD);
        } else if (item.equals(menuOfAutoFitBoard)) {
            prop.setBoardSize(ChessBoard.BoardSize.AUTOFIT_BOARD);
        } else {
            prop.setBoardSize(ChessBoard.BoardSize.SMALL_BOARD);
        }
        board.setBoardSize(prop.getBoardSize());
        if (prop.getBoardSize() == ChessBoard.BoardSize.AUTOFIT_BOARD) {
            board.autoFitSize(borderPane.getWidth(), borderPane.getHeight(), splitPane.getDividerPositions()[0]);
        }
    }
    @FXML
    void stepTipChecked(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem) event.getTarget();
        prop.setStepTip(item.isSelected());
        board.setStepTip(prop.isStepTip());
    }

    @FXML
    void showNumberClick(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem) event.getTarget();
        prop.setShowNumber(item.isSelected());
        board.setShowNumber(prop.isShowNumber());
    }

    @FXML
    void topWindowClick(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem) event.getTarget();
        prop.setTopWindow(item.isSelected());
        App.topWindow(prop.isTopWindow());
    }

    @FXML
    void linkBackModeChecked(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem) event.getTarget();
        if (linkMode.getValue()) {
            stopGraphLink();
        }
        prop.setLinkBackMode(item.isSelected());
    }

    @FXML
    void linkAnimationChecked(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem) event.getTarget();
        prop.setLinkAnimation(item.isSelected());
    }

    @FXML
    void stepSoundClick(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem) event.getTarget();
        prop.setStepSound(item.isSelected());
        board.setStepSound(prop.isStepSound());
    }

    @FXML
    void showStatusBarClick(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem) event.getTarget();
        prop.setLinkShowInfo(item.isSelected());
        statusToolBar.setVisible(item.isSelected());
        board.autoFitSize(borderPane.getWidth(), borderPane.getHeight(), splitPane.getDividerPositions()[0]);
    }

    @FXML
    public void analysisButtonClick(ActionEvent event) {
        if (engine == null) {
            DialogUtils.showWarningDialog("提示", "引擎未加载");
            return;
        }

        robotAnalysis.setValue(!robotAnalysis.getValue());
        if (robotAnalysis.getValue()) {
            robotRed.setValue(false);
            robotBlack.setValue(false);
            engineGo();
        } else {
            engineStop();
        }

        redButton.setDisable(robotAnalysis.getValue());
        blackButton.setDisable(robotAnalysis.getValue());
        immediateButton.setDisable(robotAnalysis.getValue());

        if (linkMode.getValue() && !robotAnalysis.getValue()) {
            stopGraphLink();
        }
    }

    private void engineStop() {
        if (engine != null) {
            engine.stop();
        }
    }

    @FXML
    public void immediateButtonClick(ActionEvent event) {
        if (redGo && robotRed.getValue() || !redGo && robotBlack.getValue()) {
            if (engine != null) {
                engine.moveNow();
            }
        }
    }

    @FXML
    public void changeTacticButtonClick(ActionEvent event) {
        if (robotRed.getValue() && redGo || robotBlack.getValue() && !redGo || robotAnalysis.getValue()) {
            engineStop();
            if (tacticList == null || tacticList.size() <= 1) {
                tacticList = board.getTacticList(redGo);
            }
            if (!listView.getItems().isEmpty()) {
                for (ThinkData td : listView.getItems()) {
                    if (td.getPv() == 1) {
                        tacticList.remove(td.getDetail().get(0));
                        break;
                    }
                }
            }
            engine.setThreadNum(prop.getThreadNum());
            engine.setHashSize(prop.getHashSize());
            engine.setAnalysisModel(robotAnalysis.getValue() ? Engine.AnalysisModel.INFINITE : prop.getAnalysisModel(), prop.getAnalysisValue());
            engine.analysis(chessManualHandle.getFenCode(), chessManualHandle.getMoveList(), tacticList);
        }
    }

    @FXML
    public void blackButtonClick(ActionEvent event) {
        if (engine == null) {
            DialogUtils.showWarningDialog("提示", "引擎未加载");
            return;
        }

        robotBlack.setValue(!robotBlack.getValue());
        if (robotBlack.getValue() && !redGo) {
            engineGo();
        }
        if (!robotBlack.getValue() && !redGo) {
            engineStop();
        }

        if (linkMode.getValue() && !robotBlack.getValue()) {
            stopGraphLink();
        }
    }

    @FXML
    public void engineManageClick(ActionEvent e) {
        App.openEngineDialog();
        // 重新设置引擎列表
        refreshEngineComboBox();
        // 如果引擎被卸载，则关闭
        if (StringUtils.isEmpty(prop.getEngineName())) {
            // 重置按钮
            robotRed.setValue(false);
            robotBlack.setValue(false);
            robotAnalysis.setValue(false);
            // 关闭引擎
            if (engine != null) {
                engine.close();
                engine = null;
            }
        }
    }

    @FXML
    public void redButtonClick(ActionEvent event) {
        if (engine == null) {
            DialogUtils.showWarningDialog("提示", "引擎未加载");
            return;
        }

        robotRed.setValue(!robotRed.getValue());
        if (robotRed.getValue() && redGo) {
            engineGo();
        }
        if (!robotRed.getValue() && redGo) {
            engineStop();
        }

        if (linkMode.getValue() && !robotRed.getValue()) {
            stopGraphLink();
        }
    }

    private void stopGraphLink() {
        graphLinker.stop();

        engineStop();

        redButton.setDisable(false);
        robotRed.setValue(false);

        blackButton.setDisable(false);
        robotBlack.setValue(false);

        analysisButton.setDisable(false);
        robotAnalysis.setValue(false);

        linkMode.setValue(false);
    }

    private void engineGo() {
        if (engine == null) {
            DialogUtils.showWarningDialog("提示", "引擎未加载");
            return;
        }

        if (robotRed.getValue() && redGo || robotBlack.getValue() && !redGo) {
            this.isThinking = true;
        } else {
            this.isThinking = false;
        }

        // 重置变招列表
        tacticList = null;

        engine.setThreadNum(prop.getThreadNum());
        engine.setHashSize(prop.getHashSize());
        engine.setAnalysisModel(robotAnalysis.getValue() ? Engine.AnalysisModel.INFINITE : prop.getAnalysisModel(), prop.getAnalysisValue());
        engine.analysis(chessManualHandle.getFenCode(), chessManualHandle.getMoveList(), this.board.getBoard(), redGo);
    }

    @FXML
    public void canvasClick(MouseEvent event) {

        if (event.getButton() == MouseButton.PRIMARY) {
            String move = board.mouseClick((int) event.getX(), (int) event.getY(),
                    redGo && !robotRed.getValue(), !redGo && !robotBlack.getValue());

            if (move != null) {
                goCallBack(move);
            }

            BoardContextMenu.getInstance().hide();

        } else if (event.getButton() == MouseButton.SECONDARY) {

            BoardContextMenu.getInstance().show(this.canvas, Side.RIGHT, event.getX() - this.canvas.widthProperty().doubleValue(), event.getY());
        }

    }
    private void goCallBack(String move) {
        // 记录棋谱
        List<String> nextList = chessManualHandle.boardMove(move, board.translate(move, true));
        board.setManualList(nextList);
        // 趋势图
        refreshLineChart();
        // 切换行棋方
        redGo = !redGo;
        // 触发引擎走棋
        if (redGo && robotRed.getValue() || !redGo && robotBlack.getValue() || robotAnalysis.getValue()) {
            engineGo();
        } else {
            doOpenBook();
            // 未分析时，用棋谱“分数/胜率”列里的胜率回显胜率条
            applyManualWinRate();
        }
        // 主棋盘走子后，若推演窗口已打开则同步到最新局面
        App.syncDeduction(board.getBoard(), redGo, isReverse.getValue());
    }

    @Override
    public void refreshLineChart() {
        List<XYChart.Data> oldList = lineChartSeries.getData();
        List<XYChart.Data> newList = chessManualHandle.getScoreList();
        int i = 0;
        while (i < oldList.size() && i < newList.size()) {
            XYChart.Data o = oldList.get(i);
            XYChart.Data n = newList.get(i);
            if (!o.getXValue().equals(n.getXValue()) || !o.getYValue().equals(n.getYValue())) {
                for (int j = oldList.size() - 1; j >= i; j--) {
                    oldList.remove(j);
                }
                break;
            }
            i++;
        }
        if (i < oldList.size()) {
            for (int j = oldList.size() - 1; j >= i; j--) {
                oldList.remove(j);
            }
        } else if (i < newList.size()) {
            oldList.addAll(newList.subList(i, newList.size()));
        }
    }

    private void doOpenBook() {
        if (useOpenBook.getValue()) {
            Thread.startVirtualThread(() -> {
                List<BookData> results = OpenBookManager.getInstance().queryBook(board.getBoard(), redGo, chessManualHandle.getP() / 2 >= Properties.getInstance().getOffManualSteps());
                this.showBookResults(results);
            });
        } else {
            this.bookTable.getItems().clear();
        }
    }

    @FXML
    public void copyButtonClick(ActionEvent e) {
        String fenCode = board.fenCode(redGo);
        ClipboardUtils.setText(fenCode);
    }

    @FXML
    public void pasteButtonClick(ActionEvent e) {
        String fenCode = ClipboardUtils.getText();
        if (StringUtils.isNotEmpty(fenCode) && fenCode.split("/").length == 10) {
            newFromOriginFen(fenCode);
        }
    }

    @FXML
    public void importImageMenuClick(ActionEvent e) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(new File(PathUtils.getJarPath()));
        File file = fileChooser.showOpenDialog(App.getMainStage());
        if (file != null) {
            importFromImgFile(file);
        }
    }

    @FXML
    public void exportImageMenuClick(ActionEvent e) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setInitialDirectory(new File(PathUtils.getJarPath()));
        fileChooser.setInitialFileName("tchess_export_" + DateUtils.getDateTimeString(new Date()) + ".png");
        File file = fileChooser.showSaveDialog(App.getMainStage());
        if (file != null) {
            try {
                WritableImage writableImage = new WritableImage((int) this.canvas.getWidth(), (int) this.canvas.getHeight());
                canvas.snapshot(null, writableImage);
                RenderedImage renderedImage = SwingFXUtils.fromFXImage(writableImage, null);
                ImageIO.write(renderedImage, "png", file);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    @FXML
    public void aboutClick(ActionEvent e) {
        DialogUtils.showInfoDialog("关于", "TCHESS"
                + System.lineSeparator() + "Built on : " + App.BUILT_ON
                + System.lineSeparator() + "Author : T"
                + System.lineSeparator() + "Version : " + App.VERSION);
    }

    @FXML
    public void upgradeClick(ActionEvent e) {
        SystemUtils.openBrowser("https://github.com/sojourners/public-Xiangqi/releases");
    }

    @FXML
    public void instructionClick(ActionEvent e) {
        SystemUtils.openBrowser("https://github.com/sojourners/public-Xiangqi/blob/master/MANUAL.md");
    }

    @FXML
    public void homeClick(ActionEvent e) {
        SystemUtils.openBrowser("https://github.com/sojourners/public-Xiangqi");
    }

    @FXML
    void localBookManageButtonClick(ActionEvent e) {
        if (App.openLocalBookDialog()) {
            OpenBookManager.getInstance().setLocalOpenBooks();
        }

    }

    @FXML
    void timeSettingButtonClick(ActionEvent e) {
        App.openTimeSetting();
    }

    @FXML
    void bookSettingButtonClick(ActionEvent e) {
        App.openBookSetting();
    }

    @FXML
    void linkSettingClick(ActionEvent e) {
        App.openLinkSetting();

    }

    @FXML
    public void reverseButtonClick(ActionEvent event) {
        isReverse.setValue(!isReverse.getValue());
        board.reverse(isReverse.getValue());
    }

    @FXML
    void colorSettingClick(ActionEvent e) {
        if (App.openColorSetting()) {
            App.refreshTheme();
            writeeSkinConfig(this.currentBoardSkin);
            board.refresh();
        }
    }

    @FXML
    private void bookSwitchButtonClick(ActionEvent e) {
        useOpenBook.setValue(!useOpenBook.getValue());
        prop.setBookSwitch(useOpenBook.getValue());

        doOpenBook();
    }

    @FXML
    private void linkButtonClick(ActionEvent e) {
        if (engine == null) {
            DialogUtils.showWarningDialog("提示", "引擎未加载");
            return;
        }

        linkMode.setValue(!linkMode.getValue());
        if (linkMode.getValue()) {
            graphLinker.start();
        } else {
            stopGraphLink();
        }
    }

    /**
     * 推演：从当前局面弹出一个独立的小棋盘
     */
    @FXML
    private void deductionButtonClick(ActionEvent e) {
        App.openDeduction(board.getBoard(), redGo, isReverse.getValue());
    }

    private void initLineChart() {
        final NumberAxis xAxis = new NumberAxis();
        final NumberAxis yAxis = new NumberAxis(-1000, 1000, 500);
        xAxis.setTickLabelsVisible(false);
        xAxis.setTickMarkVisible(false);
        xAxis.setMinorTickVisible(false);
        yAxis.setTickMarkVisible(false);
        yAxis.setMinorTickVisible(false);

        LineChart<Number,Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setMinHeight(100);
        lineChart.setLegendVisible(false);
        lineChart.setCreateSymbols(false);
        lineChart.setVerticalGridLinesVisible(false);
        lineChart.getStylesheets().add(this.getClass().getResource("/style/table.css").toString());

        lineChartSeries = new XYChart.Series();
        lineChart.getData().add(lineChartSeries);

        charPane.setCenter(lineChart);
    }
    public void initialize() {
        // 读取配置
        prop = Properties.getInstance();
        // 思考细节listView
        listView.setCellFactory(new Callback() {
            @Override
            public Object call(Object param) {
                ListCell<ThinkData> cell = new ListCell<ThinkData>() {
                    @Override
                    protected void updateItem(ThinkData item, boolean bln) {
                        super.updateItem(item, bln);
                        if (!bln) {
                            VBox box = new VBox();

                            Label title = new Label();
                            title.setText(item.getTitle());
                            setScoreStyle(title, item.getScore());
                            box.getChildren().add(title);

                            Label body = new Label();
                            body.setText(item.getBody());
                            body.setWrapText(true);
                            body.setMaxWidth(listView.getWidth() / 1.124);//bind(listView.widthProperty().divide(1.124));
                            box.getChildren().add(body);

                            setGraphic(box);
                        }
                    }
                };
                return cell;
            }

        });
        // 按钮
        setButtonTips();
        // 棋盘
        initChessBoard();
        // 库招表
        initBookTable();
        // 引擎view
        initEngineView();
        // 连线器
        initGraphLinker();
        // 按钮监听
        initButtonListener();
        // autofit board size listener
        initAutoFitBoardListener();
        // canvas drag listener
        initCanvasDragListener();
        // line chart
        initLineChart();
        // init chess manual
        chessManualHandle = new ChessManualHandle(chessManualPane, menuOfChessNotation, menuOfShowTactic, notationTree,
                manualTitleLabel, recordTable, subRecordTable, remarkText,
                manualBackButton, manualDeleteButton, manualDownButton, manualFinalButton,
                manualForwardButton, manualFrontButton, manualPlayButton, manualUpButton,
                openManualButton, saveManualButton, competitionNameText, competitionCityText, competitionDateText,
                competitionRedText, competitionBlackText, this);

        useOpenBook.setValue(prop.getBookSwitch());
        // 初始化棋局
        newChessBoard(null);
        // 加载引擎
        loadEngine(prop.getEngineName());
    }

    private void importFromBufferImage(BufferedImage img) {
        char[][] result = graphLinker.findChessBoard(img);
        if (result == null || !XiangqiUtils.validateChessBoard(result)) {
            //如果局面不合法 尝试用辅助模型
            result = graphLinker.findChessBoardUseSupportModel(img);
        }
        if (result == null) {
            return;
        }
        if (!XiangqiUtils.validateChessBoard(result) && !DialogUtils.showConfirmDialog("提示", "检测到局面不合法，可能会导致引擎退出或者崩溃，是否继续？")) {
            return;
        }
        String fenCode = ChessBoard.fenCode(result, true);
        newFromOriginFen(fenCode);
    }

    private void importFromImgFile(File f) {
        if (f.exists() && PathUtils.isImage(f.getAbsolutePath())) {
            try {
                BufferedImage img = ImageIO.read(f);
                importFromBufferImage(img);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void initCanvasDragListener() {
        this.canvas.setOnDragDropped(event -> {
            File f = event.getDragboard().getFiles().get(0);
            importFromImgFile(f);
        });
        this.canvas.setOnDragOver(event -> {
            event.acceptTransferModes(TransferMode.ANY);
            event.consume();
        });
    }

    private void initAutoFitBoardListener() {
        borderPane.widthProperty().addListener((observableValue, number, t1) -> {
            board.autoFitSize(t1.doubleValue(), borderPane.getHeight(), splitPane.getDividerPositions()[0]);
        });
        borderPane.heightProperty().addListener((observableValue, number, t1) -> {
            board.autoFitSize(borderPane.getWidth(), t1.doubleValue(), splitPane.getDividerPositions()[0]);
        });
        splitPane.getDividers().get(0).positionProperty().addListener((observableValue, number, t1) -> {
            board.autoFitSize(borderPane.getWidth(), borderPane.getHeight(), t1.doubleValue());
        });
    }

    private void initBookTable() {
        TableColumn moveCol = bookTable.getColumns().get(0);
        moveCol.setCellValueFactory(new PropertyValueFactory<BookData, String>("word"));
        TableColumn scoreCol = bookTable.getColumns().get(1);
        scoreCol.setCellValueFactory(new PropertyValueFactory<BookData, Integer>("score"));
        TableColumn winRateCol = bookTable.getColumns().get(2);
        winRateCol.setCellValueFactory(new PropertyValueFactory<BookData, Double>("winRate"));
        TableColumn winNumCol = bookTable.getColumns().get(3);
        winNumCol.setCellValueFactory(new PropertyValueFactory<BookData, Integer>("winNum"));
        TableColumn drawNumCol = bookTable.getColumns().get(4);
        drawNumCol.setCellValueFactory(new PropertyValueFactory<BookData, Integer>("drawNum"));
        TableColumn loseNumCol = bookTable.getColumns().get(5);
        loseNumCol.setCellValueFactory(new PropertyValueFactory<BookData, Integer>("loseNum"));
        TableColumn noteCol = bookTable.getColumns().get(6);
        noteCol.setCellValueFactory(new PropertyValueFactory<BookData, String>("note"));
        TableColumn sourceCol = bookTable.getColumns().get(7);
        sourceCol.setCellValueFactory(new PropertyValueFactory<BookData, String>("source"));
    }

    public void initStage() {
        borderPane.setPrefWidth(prop.getStageWidth());
        borderPane.setPrefHeight(prop.getStageHeight());
        splitPane.setDividerPosition(0, prop.getSplitPos());
        splitPane2.setDividerPosition(0, prop.getSplitPos2());

        // 窗口置顶
        menuOfTopWindow.setSelected(prop.isTopWindow());
        App.topWindow(prop.isTopWindow());
    }

    private void setButtonTips() {
        newButton.setTooltip(new Tooltip("新局面"));
        copyButton.setTooltip(new Tooltip("复制局面"));
        pasteButton.setTooltip(new Tooltip("粘贴局面"));
        regretButton.setTooltip(new Tooltip("悔棋"));
        reverseButton.setTooltip(new Tooltip("翻转"));
        redButton.setTooltip(new Tooltip("引擎执红"));
        blackButton.setTooltip(new Tooltip("引擎执黑"));
        analysisButton.setTooltip(new Tooltip("分析模式"));
        immediateButton.setTooltip(new Tooltip("立即出招"));
        reviewButton.setTooltip(new Tooltip("复盘"));
        changeTacticButton.setTooltip(new Tooltip("变招"));
        linkButton.setTooltip(new Tooltip("连线"));
        deductionButton.setTooltip(new Tooltip("推演"));
        bookSwitchButton.setTooltip(new Tooltip("启用库招"));

    }

    private void initChessBoard() {
        // 棋步提示
        menuOfStepTip.setSelected(prop.isStepTip());
        // 走棋音效
        menuOfStepSound.setSelected(prop.isStepSound());
        // 连线后台模式
        menuOfLinkBackMode.setSelected(prop.isLinkBackMode());
        // 连线动画确认
        menuOfLinkAnimation.setSelected(prop.isLinkAnimation());
        // show number
        menuOfShowNumber.setSelected(prop.isShowNumber());
        // 显示状态栏
        menuOfShowStatus.setSelected(prop.isLinkShowInfo());
        // 棋盘大小
        if (prop.getBoardSize() == ChessBoard.BoardSize.LARGE_BOARD) {
            menuOfLargeBoard.setSelected(true);
        } else if (prop.getBoardSize() == ChessBoard.BoardSize.BIG_BOARD) {
            menuOfBigBoard.setSelected(true);
        } else if (prop.getBoardSize() == ChessBoard.BoardSize.MIDDLE_BOARD) {
            menuOfMiddleBoard.setSelected(true);
        } else if (prop.getBoardSize() == ChessBoard.BoardSize.AUTOFIT_BOARD) {
            menuOfAutoFitBoard.setSelected(true);
        } else {
            menuOfSmallBoard.setSelected(true);
        }
        // 棋盘样式
        initBoardTypeMenu();
        // 右键菜单
        initBoardContextMenu();
        // 状态栏
        this.infoShowLabel.prefWidthProperty().bind(statusToolBar.widthProperty().subtract(120));
        this.timeShowLabel.setText(getTimeStrategyString());
        this.statusToolBar.setVisible(prop.isLinkShowInfo());
    }

    private void initBoardContextMenu() {
        BoardContextMenu.getInstance().setOnAction(event -> {
            MenuItem item = (MenuItem) event.getTarget();
            if ("复制局面FEN".equals(item.getText())) {
                copyButtonClick(null);
            } else if ("粘贴局面FEN".equals(item.getText())) {
                pasteButtonClick(null);
            } else if ("交换行棋方".equals(item.getText())) {
                switchPlayer(true);
            } else if ("编辑局面".equals(item.getText())) {
                editChessBoardClick(null);
            } else if ("复制局面图片".equals(item.getText())) {
                copyImageMenuClick(null);
            } else if ("粘贴局面图片".equals(item.getText())) {
                pasteImageMenuClick(null);
            } else if ("复制棋谱".equals(item.getText())) {
                chessManualHandle.copyChessManual();
            } else if ("粘贴棋谱".equals(item.getText())) {
                chessManualHandle.pasteChessManual();
            }
        });
    }

    @FXML
    public void copyImageMenuClick(ActionEvent event) {
        WritableImage writableImage = new WritableImage((int) canvas.getWidth(), (int) canvas.getHeight());
        canvas.snapshot(null, writableImage);
        BufferedImage bi =SwingFXUtils.fromFXImage(writableImage, null);
        ClipboardUtils.setImage(bi);
    }

    @FXML
    public void pasteImageMenuClick(ActionEvent event) {
        Image img = ClipboardUtils.getImage();
        if (img != null) {
            importFromBufferImage((BufferedImage) img);
        }
    }

    @FXML
    public void editChessBoardClick(ActionEvent e) {
        String fenCode = App.openEditChessBoard(board.getBoard(), redGo, isReverse.getValue());
        newFromOriginFen(fenCode);
    }

    /**
     * new from origin fen that maybe reverse, and stop link mode at the same time
     * @param fenCode
     */
    private void newFromOriginFen(String fenCode) {
        if (StringUtils.isNotEmpty(fenCode)) {
            if (linkMode.getValue()) {
                stopGraphLink();
            }

            newChessBoard(fenCode);
            if (XiangqiUtils.isReverse(fenCode)) {
                reverseButtonClick(null);
            }
            // 若推演窗口已打开，同步到新局面
            App.syncDeduction(board.getBoard(), redGo, isReverse.getValue());
        }
    }

    private void newChessBoard(String fenCode) {
        newChessBoard(fenCode, false);
    }

    /**
     * 新建局面
     * @param fenCode 传null 新建默认初始局面；传fenCode 则根据fen创建局面
     */
    private void newChessBoard(String fenCode, boolean fromManual) {
        // 重置按钮
        robotRed.setValue(false);
        redButton.setDisable(false);
        robotBlack.setValue(false);
        blackButton.setDisable(false);
        robotAnalysis.setValue(false);
        immediateButton.setDisable(false);
        isReverse.setValue(false);
        // 引擎停止计算
        engineStop();
        // 绘制棋盘
        board = new ChessBoard(this.canvas, prop.getBoardSize(), prop.getBoardStyle(), prop.isStepTip(), prop.isManualTip(),
                engine != null && engine.getMultiPV() > 1, prop.isStepSound(), prop.isShowNumber(), fenCode);
        // 设置局面
        redGo = StringUtils.isEmpty(fenCode) ? true : fenCode.contains("w");
        fenCode = board.fenCode(redGo);
        // 设置棋谱
        if (!fromManual)
            chessManualHandle.newChessManual(fenCode);
        // 重置趋势图
        refreshLineChart();
        // 重置引擎思考输出
        listView.getItems().clear();
        // 清空思考状态信息
        this.infoShowLabel.setText("");

        // 库招显示
        doOpenBook();

        System.gc();
        // 若推演窗口已打开，同步到新局面
        App.syncDeduction(board.getBoard(), redGo, isReverse.getValue());
    }

    private void initEngineView() {
        // 引擎列表 线程数 哈希表大小
        refreshEngineComboBox();
        for (int i = 1; i <= Runtime.getRuntime().availableProcessors(); i++) {
            threadComboBox.getItems().add(String.valueOf(i));
        }
        hashComboBox.getItems().addAll( "128", "256", "512", "1024", "2048", "4096");
        // 加载设置
        threadComboBox.setValue(String.valueOf(prop.getThreadNum()));
        hashComboBox.setValue(String.valueOf(prop.getHashSize()));
    }


    private void initGraphLinker() {
        try {
            this.graphLinker = com.sun.jna.Platform.isWindows() ?
                    new WindowsGraphLinker(this) : (com.sun.jna.Platform.isLinux() ?
                    new LinuxGraphLinker(this) : new MacosGraphLinker(this));
        } catch (Exception e) {
            e.printStackTrace();
        }

        linkComboBox.getItems().addAll("自动走棋", "观战模式");
        linkComboBox.setValue("观战模式");
    }

    private void refreshEngineComboBox() {
        engineComboBox.getItems().clear();
        for (EngineConfig ec : prop.getEngineConfigList()) {
            engineComboBox.getItems().add(ec.getName());
        }
        engineComboBox.setValue(prop.getEngineName());
    }

    private void initButtonListener() {
        addListener(redButton, robotRed);
        addListener(blackButton, robotBlack);
        addListener(analysisButton, robotAnalysis);
        addListener(reverseButton, isReverse);
        addListener(linkButton, linkMode);
        addListener(bookSwitchButton, useOpenBook);

        threadComboBox.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String s, String t1) {
                int num = Integer.parseInt(t1);
                if (num != prop.getThreadNum()) {
                    prop.setThreadNum(num);
                }
            }
        });
        hashComboBox.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String s, String t1) {
                int size = Integer.parseInt(t1);
                if (size != prop.getHashSize()) {
                    prop.setHashSize(size);
                }
            }
        });
        engineComboBox.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String s, String t1) {
                if (StringUtils.isNotEmpty(t1) && !t1.equals(prop.getEngineName())) {
                    // 保存引擎设置
                    prop.setEngineName(t1);
                    // 重置三个按钮
                    robotRed.setValue(false);
                    redButton.setDisable(false);
                    robotBlack.setValue(false);
                    blackButton.setDisable(false);
                    robotAnalysis.setValue(false);
                    immediateButton.setDisable(false);
                    // 停止连线
                    if (linkMode.getValue()) {
                        stopGraphLink();
                    }
                    // 加载新引擎
                    loadEngine(t1);
                }
            }
        });
        linkComboBox.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observableValue, String s, String t1) {
                setLinkMode(t1);
            }
        });
    }

    private void setLinkMode(String t1) {
        if (linkMode.getValue()) {
            if ("自动走棋".equals(t1)) {
                // 观战模式切换自动走棋，先停止引擎
                engineStop();
                // 走黑棋/红棋
                if (isReverse.getValue()) {
                    blackButton.setDisable(false);
                    robotBlack.setValue(true);

                    redButton.setDisable(true);
                    robotRed.setValue(false);

                    analysisButton.setDisable(true);
                    robotAnalysis.setValue(false);

                    if (!redGo) {
                        engineGo();
                    }
                } else {
                    redButton.setDisable(false);
                    robotRed.setValue(true);

                    blackButton.setDisable(true);
                    robotBlack.setValue(false);

                    analysisButton.setDisable(true);
                    robotAnalysis.setValue(false);

                    if (redGo) {
                        engineGo();
                    }
                }
            } else {
                analysisButton.setDisable(false);
                robotAnalysis.setValue(true);

                blackButton.setDisable(true);
                robotBlack.setValue(false);

                redButton.setDisable(true);
                robotRed.setValue(false);

                immediateButton.setDisable(true);

                engineGo();
            }
        }
    }

    private void addListener(Button button, ObjectProperty property) {
        property.addListener((ChangeListener<Boolean>) (observableValue, aBoolean, t1) -> {
            setButtonSelected(button, t1);
        });
        setButtonSelected(button, Boolean.TRUE.equals(property.getValue()));
    }

    private void setButtonSelected(Button button, boolean selected) {
        String selectedStylesheet = this.getClass().getResource("/style/selected-button.css").toString();
        if (selected) {
            if (!button.getStylesheets().contains(selectedStylesheet)) {
                button.getStylesheets().add(selectedStylesheet);
            }
            if (!button.getStyleClass().contains("selected-state")) {
                button.getStyleClass().add("selected-state");
            }
        } else {
            button.getStylesheets().remove(selectedStylesheet);
            button.getStyleClass().remove("selected-state");
        }
    }

    private void loadEngine(String name) {
        try {
            if (StringUtils.isNotEmpty(name)) {
                for (EngineConfig ec : prop.getEngineConfigList()) {
                    if (name.equals(ec.getName())) {
                        if (engine != null) {
                            engine.close();
                        }
                        engine = new Engine(ec, this);
                        board.showMultiPV(engine.getMultiPV() > 1);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 连线模式下自动点击走棋
     * @param step
     */
    private void trickAutoClick(ChessBoard.Step step) {
        if (step != null) {
            int x1 = step.getStart().getX(), y1 = step.getStart().getY();
            int x2 = step.getEnd().getX(), y2 = step.getEnd().getY();
            if (robotBlack.getValue()) {
                y1 = 9 - y1;
                y2 = 9 - y2;
                x1 = 8 - x1;
                x2 = 8 - x2;
            }
            graphLinker.autoClick(x1, y1, x2, y2);
        }
        this.isThinking = false;
    }

    @Override
    public void bestMove(String first, String second) {
        if (redGo && robotRed.getValue() || !redGo && robotBlack.getValue()) {
            ChessBoard.Step s = board.stepForBoard(first);

            Platform.runLater(() -> {
                board.move(s.getStart().getX(), s.getStart().getY(), s.getEnd().getX(), s.getEnd().getY());
                board.setTip(second, null, 1);

                goCallBack(first);
            });

            if (linkMode.getValue()) {
                trickAutoClick(s);
            }
        }
    }

    @Override
    public void thinkDetail(ThinkData td) {
        if (redGo && robotRed.getValue() || !redGo && robotBlack.getValue() || robotAnalysis.getValue()) {
            td.generate(redGo, isReverse.getValue(), board);
            if (td.getValid()) {
                Platform.runLater(() -> {
                    listView.getItems().addFirst(td);
                    if (listView.getItems().size() > 128) {
                        listView.getItems().removeLast();
                    }

                    if (prop.isLinkShowInfo()) {
                        infoShowLabel.setText(td.getTitle() + " | " + td.getBody());
                        setScoreStyle(infoShowLabel, td.getScore());
                        timeShowLabel.setText(getTimeStrategyString());
                    }

                    board.setTip(td.getDetail().get(0), td.getDetail().size() > 1 ? td.getDetail().get(1) : null, td.getPv());

                    if (td.getPv() == 1) {
                        chessManualHandle.setScore(td.getScore(), td.getMate());
                        // 实时更新棋盘左侧胜率条
                        board.setWinRate(scoreToWinRate(td.getScore(), td.getMate()));
                    }
                });
            }
        }
    }

    private String getTimeStrategyString() {
        switch (prop.getAnalysisModel()) {
            case FIXED_TIME:
                return "固定时间" + prop.getAnalysisValue() / 1000d + "秒";
            case FIXED_STEPS:
                return "固定深度" + prop.getAnalysisValue() + "层";
            case FIXED_NODES:
                long nodes = prop.getAnalysisValue();
                if (nodes > 1000) {
                    nodes /= 1000;
                    return "固定节点" + nodes + "K个";
                } else {
                    return "固定节点" + nodes + "个";
                }
            default:
                return "";
        }
    }

    private void setScoreStyle(Label label, double score) {
        label.getStyleClass().removeAll("positive-score", "negative-score");
        label.getStyleClass().add(score >= 0 ? "positive-score" : "negative-score");
    }

    /**
     * 将引擎分值与绝杀步数换算为棋盘底部一方的胜率（0~1）。
     * 与棋谱“分数/胜率”列使用同一套 Elo 公式。
     *
     * @param score 底部一方视角的分值
     * @param mate  绝杀步数（可能为 null）
     * @return 底部一方胜率，范围 0~1
     */
    private double scoreToWinRate(Integer score, Integer mate) {
        if (score == null && mate == null) {
            return 0.5;
        }
        int s;
        if (mate != null) {
            int sc = score == null ? 0 : score;
            s = (sc < 0 ? -30000 : 30000) - sc;
        } else {
            s = score;
        }
        return GameReview.eloToWinRate(s).doubleValue() / 100d;
    }

    @Override
    public void showBookResults(List<BookData> list) {
        this.bookTable.getItems().clear();
        for (BookData bd : list) {
            String move = bd.getMove();
            bd.setWord(board.translate(move, false));
            this.bookTable.getItems().add(bd);
        }
    }

    @FXML
    public void bookTableClick(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
            if (redGo && !robotRed.getValue() || !redGo && !robotBlack.getValue() ||robotAnalysis.getValue()) {
                BookData bd = bookTable.getSelectionModel().getSelectedItem();
                if (bd == null) {
                    return;
                }
                Platform.runLater(() -> {
                    board.move(bd.getMove());
                    goCallBack(bd.getMove());
                });
            }
        }
    }

    @FXML
    public void exit() {
        if (engine != null) {
            engine.close();
        }

        OpenBookManager.getInstance().close();
//        ExecutorsUtils.getInstance().close();

        graphLinker.stop();

        prop.setStageWidth(borderPane.getWidth());
        prop.setStageHeight(borderPane.getHeight());
        prop.setSplitPos(splitPane.getDividerPositions()[0]);
        prop.setSplitPos2(splitPane2.getDividerPositions()[0]);

        prop.save();

        Platform.exit();
    }

    /**
     * 图形连线初始化棋盘
     * @param fenCode
     * @param isReverse
     */
    @Override
    public void linkerInitChessBoard(String fenCode, boolean isReverse) {
        Platform.runLater(() -> {
            newChessBoard(fenCode);
            if (isReverse) {
                reverseButtonClick(null);
            }
            setLinkMode(linkComboBox.getValue());
            // 若推演窗口已打开，同步到新局面
            App.syncDeduction(board.getBoard(), redGo, this.isReverse.getValue());
        });
    }

    @Override
    public char[][] getEngineBoard() {
        return board.getBoard();
    }

    @Override
    public boolean isThinking() {
        return this.isThinking;
    }

    @Override
    public boolean isWatchMode() {
        return "观战模式".equals(linkComboBox.getValue());
    }

    @Override
    public void linkerMove(int x1, int y1, int x2, int y2) {
        Platform.runLater(() -> {
            String move = board.move(x1, y1, x2, y2);
            if (move != null) {
                boolean red = XiangqiUtils.isRed(board.getBoard()[y2][x2]);
                if (isWatchMode() && (!redGo && red || redGo && !red)) {
                    System.out.println(move + "," + red + ", " + redGo);
                    // 连线识别行棋方错误，自动切换行棋方
                    switchPlayer(false);
                } else {
                    goCallBack(move);
                }
            }
        });
    }

    private void switchPlayer(boolean f) {
        engineStop();

        graphLinker.pause();

        boolean tmpRed = robotRed.getValue(), tmpBlack = robotBlack.getValue(), tmpAnalysis = robotAnalysis.getValue(), tmpLink = linkMode.getValue(), tmpReverse = isReverse.getValue();

        String fenCode = board.fenCode(f ? !redGo : redGo);
        newChessBoard(fenCode);

        isReverse.setValue(tmpReverse);
        board.reverse(tmpReverse);
        robotRed.setValue(tmpRed);
        robotBlack.setValue(tmpBlack);
        robotAnalysis.setValue(tmpAnalysis);
        linkMode.setValue(tmpLink);

        graphLinker.resume();
        if (robotRed.getValue() && redGo || robotBlack.getValue() && !redGo || robotAnalysis.getValue()) {
            engineGo();
        }
    }

    // ------------- 棋谱管理 start -----------------
    private ChessManualHandle chessManualHandle;
    @FXML
    private BorderPane chessManualPane;
    @FXML
    private CheckMenuItem menuOfChessNotation;
    @FXML
    private CheckMenuItem menuOfShowTactic;
    @FXML
    private TreeView notationTree;
    @FXML
    private Label manualTitleLabel;
    @FXML
    private ListView subRecordTable;
    @FXML
    private TextArea remarkText;
    @FXML
    private Button manualBackButton;
    @FXML
    private Button manualDeleteButton;
    @FXML
    private Button manualDownButton;
    @FXML
    private Button manualFinalButton;
    @FXML
    private Button manualForwardButton;
    @FXML
    private Button manualFrontButton;
    @FXML
    private Button manualPlayButton;
    @FXML
    private Button manualUpButton;
    @FXML
    private Button openManualButton;
    @FXML
    private Button saveManualButton;
    @FXML
    private TextField competitionNameText;
    @FXML
    private TextField competitionCityText;
    @FXML
    private TextField competitionDateText;
    @FXML
    private TextField competitionRedText;
    @FXML
    private TextField competitionBlackText;

    @FXML
    void menuOfShowTacticClick(ActionEvent event) {
        CheckMenuItem item = (CheckMenuItem) event.getTarget();
        prop.setManualTip(item.isSelected());
        board.setManualTip(item.isSelected());
    }
    @FXML
    void openChessManualFolder(ActionEvent event) {
        chessManualHandle.openChessNotationFolder(event);
    }
    @FXML
    void deleteButtonClick(ActionEvent event) {
        checkLinkMode();
        chessManualHandle.deleteButtonClick(event);
    }

    /**
     * 复盘：逐局面分析棋谱，给出正着以及每步的好坏。
     */
    @FXML
    void reviewButtonClick(ActionEvent event) {
        if (engine == null) {
            DialogUtils.showWarningDialog("提示", "引擎未加载");
            return;
        }
        if (reviewing) {
            DialogUtils.showWarningDialog("提示", "正在复盘中，请稍候");
            return;
        }
        EngineConfig engineConfig = null;
        for (EngineConfig ec : prop.getEngineConfigList()) {
            if (ec.getName().equals(prop.getEngineName())) {
                engineConfig = ec;
                break;
            }
        }
        if (engineConfig == null) {
            DialogUtils.showWarningDialog("提示", "未找到当前引擎配置");
            return;
        }
        List<ManualRecord> records = chessManualHandle.getMainLineRecords();
        if (records.size() <= 1) {
            DialogUtils.showWarningDialog("提示", "当前棋谱为空，无法复盘");
            return;
        }

        TextInputDialog dialog = new TextInputDialog("500");
        dialog.setTitle("复盘");
        dialog.setHeaderText("请设置每步分析时间(毫秒)，最低 500");
        dialog.setContentText("");
        dialog.initOwner(App.getMainStage());
        Optional<String> result = dialog.showAndWait();
        if (result.isEmpty()) {
            return;
        }
        String value = result.get().trim();
        if (value.isEmpty()) {
            return;
        }
        long movetime;
        try {
            movetime = Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return;
        }
        if (movetime < 500) {
            DialogUtils.showWarningDialog("提示", "每步分析时间最低为 500 毫秒");
            return;
        }

        // 关闭“引擎走红”“引擎走黑”“引擎分析”，并停止引擎
        if (linkMode.getValue()) {
            stopGraphLink();
        }
        robotRed.setValue(false);
        robotBlack.setValue(false);
        robotAnalysis.setValue(false);
        redButton.setDisable(false);
        blackButton.setDisable(false);
        analysisButton.setDisable(false);
        immediateButton.setDisable(false);
        engineStop();
        chessManualHandle.clearReview();

        reviewing = true;
        showLoading("复盘准备中...");
        final EngineConfig config = engineConfig;
        final boolean reverse = isReverse.getValue();
        final String reviewFen = chessManualHandle.getFenCode();
        // 停止引擎后等待 3 秒，确认引擎完全退出后再开始分析
        Thread.startVirtualThread(() -> {
            for (int second = 3; second > 0; second--) {
                final int remain = second;
                Platform.runLater(() -> loadingLabel.setText("复盘准备中，" + remain + " 秒后开始..."));
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ignore) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            Platform.runLater(() -> startReview(config, reviewFen, records, movetime, reverse));
        });
    }

    /**
     * 真正开始复盘分析。
     */
    private void startReview(EngineConfig config, String fenCode, List<ManualRecord> records, long movetime, boolean reverse) {
        GameReview.start(config, fenCode, records, movetime, reverse,
                new GameReview.ReviewListener() {
                    @Override
                    public void onStartScore(int recordIndex, int score, java.math.BigDecimal winRate) {
                        Platform.runLater(() -> {
                            chessManualHandle.applyReviewScore(recordIndex, score, winRate);
                            if (winRate != null) {
                                board.setWinRate(winRate.doubleValue() / 100d);
                            }
                        });
                    }

                    @Override
                    public void onProgress(int current, int total, int recordIndex, ReviewItem item) {
                        Platform.runLater(() -> {
                            chessManualHandle.applyReview(recordIndex, item);
                            loadingLabel.setText("复盘中 " + current + "/" + total);
                            if (item != null && item.getWinRateBottom() != null) {
                                board.setWinRate(item.getWinRateBottom().doubleValue() / 100d);
                            }
                        });
                    }

                    @Override
                    public void onFinished(ReviewSummary summary) {
                        Platform.runLater(() -> {
                            reviewing = false;
                            hideLoading();
                            refreshLineChart();
                            DialogUtils.showInfoDialog("复盘结果", summary.toText());
                        });
                    }

                    @Override
                    public void onError(Exception e) {
                        Platform.runLater(() -> {
                            reviewing = false;
                            hideLoading();
                            DialogUtils.showWarningDialog("复盘失败", e.getMessage() == null ? "复盘失败" : e.getMessage());
                        });
                    }
                });
    }

    /**
     * 显示全屏 loading 遮罩，并阻止鼠标操作。
     */
    private void showLoading(String text) {
        loadingLabel.setText(text);
        loadingOverlay.setVisible(true);
        loadingOverlay.toFront();
        // 禁止键盘/鼠标等所有交互，避免复盘过程中误操作
        borderPane.setDisable(true);
    }

    /**
     * 隐藏全屏 loading 遮罩。
     */
    private void hideLoading() {
        loadingOverlay.setVisible(false);
        borderPane.setDisable(false);
    }

    /**
     * 复盘结束后，如果引擎原本处于走棋/分析状态，则恢复分析。
     */
    private void resumeEngineIfNeeded() {
        if (robotRed.getValue() && redGo || robotBlack.getValue() && !redGo || robotAnalysis.getValue()) {
            engineGo();
        }
    }
    @FXML
    void playButtonClick(ActionEvent event) {
        checkLinkMode();
        chessManualHandle.playButtonClick(event);
    }
    @FXML
    void downwardButtonClick(ActionEvent event) {
        checkLinkMode();
        chessManualHandle.manualButtonClick(8);
    }
    @FXML
    void upwardButtonClick(ActionEvent event) {
        checkLinkMode();
        chessManualHandle.manualButtonClick(7);
    }

    @Override
    public void turnOnAnalysisMode() {
        if (!robotAnalysis.getValue()) {
            analysisButtonClick(null);
        }
    }

    @Override
    public void turnOffAnalysisMode() {
        if (robotAnalysis.getValue()) {
            analysisButtonClick(null);
        }
    }

    @Override
    public void newChessBoardFromManual(String fenCode) {
        newChessBoard(fenCode, true);
    }

    @Override
    public void browseChessRecord(String fenCode, List<String> moveList, boolean redGo, List<String> nextList) {
        checkLinkMode();
        // 棋盘
        board.browseChessRecord(fenCode, moveList);
        board.setManualList(nextList);
        this.redGo = redGo;
        // 趋势图
        refreshLineChart();
        // 引擎走棋
        if (robotRed.getValue() && robotBlack.getValue()) {
            // 如果引擎执红同时执黑，取消状态（否则会有问题）
            robotRed.setValue(false);
            robotBlack.setValue(false);
            engineStop();
        } else if (redGo && robotRed.getValue() || !redGo && robotBlack.getValue() || robotAnalysis.getValue()) {
            // 轮到引擎走棋或者分析模式
            engineGo();
        } else {
            // 其他情况，停止引擎思考
            engineStop();
            // 库招显示
            doOpenBook();
        }
        // 没有引擎在分析时，用棋谱“分数/胜率”列里的胜率回显胜率条；
        // 引擎分析中则以分析结果为准，不覆盖
        boolean engineActive = robotRed.getValue() && redGo || robotBlack.getValue() && !redGo || robotAnalysis.getValue();
        if (!engineActive) {
            applyManualWinRate();
        }
        // 若推演窗口已打开，同步到浏览到的局面
        App.syncDeduction(board.getBoard(), redGo, isReverse.getValue());
    }

    /**
     * 用当前棋谱记录的“分数/胜率”列里的胜率更新棋盘左侧胜率条。
     * 仅当该记录存在胜率时才更新，否则保持当前胜率条不变。
     */
    private void applyManualWinRate() {
        List<ManualRecord> records = chessManualHandle.getMainLineRecords();
        int index = chessManualHandle.getP();
        if (index >= 0 && index < records.size()) {
            java.math.BigDecimal winRate = records.get(index).getWinRateBottom();
            if (winRate != null) {
                board.setWinRate(winRate.doubleValue() / 100d);
            }
        }
    }

    @Override
    public void setNextList(List<String> nextList) {
        board.setManualList(nextList);
    }

    private void checkLinkMode() {
        if (linkMode.getValue()) {
            stopGraphLink();
        }
    }

    @FXML
    void recordTableClick(MouseEvent event) {
        checkLinkMode();
        chessManualHandle.manualButtonClick(5);
    }

    @FXML
    public void backButtonClick(ActionEvent event) {
        checkLinkMode();
        chessManualHandle.manualButtonClick(2);
    }

    @FXML
    public void regretButtonClick(ActionEvent event) {
        checkLinkMode();
        if (redGo && robotRed.getValue() || !redGo && robotBlack.getValue()) {
            chessManualHandle.manualButtonClick(2);
        } else {
            chessManualHandle.manualButtonClick(6);
        }
    }

    @FXML
    void forwardButtonClick(ActionEvent event) {
        checkLinkMode();
        chessManualHandle.manualButtonClick(3);
    }

    @FXML
    void finalButtonClick(ActionEvent event) {
        checkLinkMode();
        chessManualHandle.manualButtonClick(4);
    }

    @FXML
    void frontButtonClick(ActionEvent event) {
        checkLinkMode();
        chessManualHandle.manualButtonClick(1);
    }

    @FXML
    void openChessManualFile(ActionEvent event) {
        chessManualHandle.openChessManualFile(event);
    }

    @FXML
    void saveAsChessManualFile(ActionEvent event) {
        chessManualHandle.saveAsChessManualFile(event);
    }

    @FXML
    void saveChessManualFile(ActionEvent event) {
        chessManualHandle.saveChessManualFile(event);
    }
    // ------------- 棋谱管理 end -----------------
}

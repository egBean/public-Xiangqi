package com.sojourners.chess;

import com.sojourners.chess.config.Properties;
import com.sojourners.chess.controller.ColorSettingController;
import com.sojourners.chess.controller.Controller;
import com.sojourners.chess.controller.DeductionController;
import com.sojourners.chess.controller.EditChessBoardController;
import com.sojourners.chess.controller.LocalBookController;
import javafx.application.Application;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;

import java.net.URL;

/**
 * 主窗口
 */
public class App extends Application {

    public static final String VERSION = "1.9";
    public static final String BUILT_ON = "20260801";

    private static Stage engineAdd;
    private static Stage engineSetting;
    private static Stage localBookSetting;
    private static Stage mainStage;
    private static Stage timeSetting;
    private static Stage bookSetting;
    private static Stage linkSetting;
    private static Stage editChessBoard;
    private static Stage deduction;
    private static DeductionController deductionController;

    private static final String LIGHT_THEME = themeResource("/style/light-theme.css");
    private static final String DARK_THEME = themeResource("/style/dark-theme.css");

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader();
        fxmlLoader.setLocation(getClass().getResource("/fxml/app.fxml"));
        Parent root = fxmlLoader.load();
        primaryStage.setTitle("TCHESS  V" + VERSION);
        Scene scene = new Scene(root);
        applyTheme(scene);
        primaryStage.setScene(scene);
        primaryStage.getIcons().add(new Image(getClass().getResourceAsStream("/image/icon.png")));

        primaryStage.setOnCloseRequest(new EventHandler() {
            @Override
            public void handle(Event event) {
                Controller controller = fxmlLoader.getController();
                controller.exit();
            }
        });
        primaryStage.setOnShowing(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent windowEvent) {
                Controller controller = fxmlLoader.getController();
                controller.initStage();
            }
        });

        mainStage = primaryStage;

        primaryStage.show();
    }

    public static void topWindow(boolean top) {
        mainStage.setAlwaysOnTop(top);
    }

    /**
     * 引擎管理对话框
     */
    public static void openEngineDialog() {
        engineSetting = createStage("/fxml/engineDialog.fxml");
        engineSetting.setTitle("引擎管理");
        engineSetting.initModality(Modality.APPLICATION_MODAL);
        engineSetting.initOwner(mainStage);

        engineSetting.showAndWait();
    }

    /**
     * 本地库管理对话框
     */
    public static boolean openLocalBookDialog() {
        localBookSetting = createStage("/fxml/localBook.fxml");
        localBookSetting.setTitle("本地库管理");
        localBookSetting.initModality(Modality.APPLICATION_MODAL);
        localBookSetting.initOwner(mainStage);

        localBookSetting.showAndWait();

        return LocalBookController.change;
    }

    /**
     * 添加引擎
     */
    public static void openEngineAdd() {
        engineAdd = createStage("/fxml/engineAdd.fxml");
        engineAdd.setTitle("添加引擎");
        engineAdd.initModality(Modality.APPLICATION_MODAL);
        engineAdd.initOwner(engineSetting);

        engineAdd.showAndWait();
    }
    public static void closeEngineAdd() {
        engineAdd.close();
    }

    /**
     * 时间设置
     */
    public static void openTimeSetting() {

        timeSetting = createStage("/fxml/timeSetting.fxml");
        timeSetting.setTitle("时间设置");
        timeSetting.initModality(Modality.APPLICATION_MODAL);
        timeSetting.initOwner(mainStage);

        timeSetting.showAndWait();
    }
    public static void closeTimeSetting() {
        timeSetting.close();
    }

    /**
     * 库招设置
     */
    public static void openBookSetting() {

        bookSetting = createStage("/fxml/bookSetting.fxml");
        bookSetting.setTitle("库招设置");
        bookSetting.initModality(Modality.APPLICATION_MODAL);
        bookSetting.initOwner(mainStage);

        bookSetting.showAndWait();
    }
    public static void closeBookSetting() {
        bookSetting.close();
    }

    /**
     * 连线设置
     */
    public static void openLinkSetting() {

        linkSetting = createStage("/fxml/linkSetting.fxml");
        linkSetting.setTitle("连线设置");
        linkSetting.initModality(Modality.APPLICATION_MODAL);
        linkSetting.initOwner(mainStage);

        linkSetting.showAndWait();
    }
    public static void closeLinkSetting() {
        linkSetting.close();
    }

    public static String openEditChessBoard(char[][] board, boolean redGo, boolean isReverse) {
        try {
            Stage stage = new Stage();
            FXMLLoader fxmlLoader = new FXMLLoader();
            fxmlLoader.setLocation(App.class.getResource("/fxml/editChessBoard.fxml"));
            Parent pane = fxmlLoader.load();
            Scene scene = new Scene(pane);
            applyTheme(scene);
            stage.setScene(scene);

            editChessBoard = stage;
            editChessBoard.setTitle("编辑局面");
            editChessBoard.initModality(Modality.APPLICATION_MODAL);
            editChessBoard.initOwner(mainStage);

            EditChessBoardController controller = fxmlLoader.getController();
            controller.setBoard(board, isReverse);
            controller.setFirstMover(redGo);

            editChessBoard.showAndWait();
            return controller.getFenCode();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    public static void closeEditChessBoard() {
        editChessBoard.close();
    }

    /**
     * 推演：从当前局面弹出一个独立的小棋盘
     *
     * @param board     当前局面
     * @param redGo     当前行棋方
     * @param isReverse 是否与主棋盘一致地翻转
     */
    public static void openDeduction(char[][] board, boolean redGo, boolean isReverse) {
        try {
            // 已存在则先关闭，避免弹窗叠加
            if (deduction != null && deduction.isShowing()) {
                deduction.close();
            }
            Stage stage = new Stage();
            FXMLLoader fxmlLoader = new FXMLLoader();
            fxmlLoader.setLocation(App.class.getResource("/fxml/deduction.fxml"));
            Parent pane = fxmlLoader.load();
            Scene scene = new Scene(pane);
            applyTheme(scene);
            stage.setScene(scene);

            deduction = stage;
            deduction.setTitle("推演");
            deduction.setResizable(false);
            deduction.initOwner(mainStage);

            DeductionController controller = fxmlLoader.getController();
            deductionController = controller;
            controller.init(board, redGo, isReverse);

            deduction.show();
            positionDeductionStage();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 将推演窗口默认摆放到屏幕横向约 2/3 处（左边缘位于屏幕 2/3 位置），
     * 尽量不遮挡居中的主棋盘；若放不下则贴屏幕右边缘，并保证不超出屏幕。
     */
    private static void positionDeductionStage() {
        if (deduction == null || mainStage == null) {
            return;
        }

        Screen screen;
        java.util.List<Screen> screens = Screen.getScreensForRectangle(
                mainStage.getX(), mainStage.getY(), mainStage.getWidth(), mainStage.getHeight());
        if (!screens.isEmpty()) {
            screen = screens.get(0);
        } else {
            screen = Screen.getPrimary();
        }

        Rectangle2D bounds = screen.getVisualBounds();
        // 窗口左边缘放在屏幕横向约 2/3 处
        double x = bounds.getMinX() + bounds.getWidth() * 5.5 / 11.0;
        double y = bounds.getMinY() + (bounds.getHeight() - deduction.getHeight()) / 2.0;

        if (x + deduction.getWidth() > bounds.getMaxX()) {
            x = bounds.getMaxX() - deduction.getWidth();
        }
        if (x < bounds.getMinX()) {
            x = bounds.getMinX();
        }
        if (y + deduction.getHeight() > bounds.getMaxY()) {
            y = bounds.getMaxY() - deduction.getHeight();
        }
        if (y < bounds.getMinY()) {
            y = bounds.getMinY();
        }

        deduction.setX(x);
        deduction.setY(y);
    }

    /**
     * 主棋盘局面变化时，若推演窗口已打开，则同步到最新局面（等同于重新打开推演棋盘，
     * 会清空推演窗口里的走棋数据）。
     *
     * @param board     最新局面
     * @param redGo     最新行棋方
     * @param isReverse 是否翻转
     */
    public static void syncDeduction(char[][] board, boolean redGo, boolean isReverse) {
        if (deduction != null && deduction.isShowing() && deductionController != null) {
            deductionController.resetTo(board, redGo, isReverse);
        }
    }

    public static void closeDeduction() {
        if (deduction != null) {
            deduction.close();
        }
    }

    public static boolean openColorSetting() {
        try {
            Stage stage = new Stage();
            FXMLLoader fxmlLoader = new FXMLLoader();
            fxmlLoader.setLocation(App.class.getResource("/fxml/colorSetting.fxml"));
            Parent pane = fxmlLoader.load();
            Scene scene = new Scene(pane);
            applyTheme(scene);
            stage.setScene(scene);
            stage.setTitle("主题配置");
            stage.setResizable(false);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.initOwner(mainStage);

            ColorSettingController controller = fxmlLoader.getController();
            stage.showAndWait();
            return controller.isSaved();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void refreshTheme() {
        for (Window window : Window.getWindows()) {
            if (window.getScene() != null) {
                applyTheme(window.getScene());
            }
        }
    }

    public static void applyTheme(Scene scene) {
        scene.getStylesheets().removeAll(LIGHT_THEME, DARK_THEME);
        scene.getRoot().getStyleClass().removeAll("light-theme", "dark-theme");
        String theme;
        if (Properties.getInstance().getColorTheme() == Properties.ColorTheme.DARK) {
            theme = DARK_THEME;
            scene.getRoot().getStyleClass().add("dark-theme");
        } else {
            theme = LIGHT_THEME;
            scene.getRoot().getStyleClass().add("light-theme");
        }
        scene.getStylesheets().add(theme);
        applyThemeToLocalStylesheets(scene.getRoot(), theme);
    }

    private static void applyThemeToLocalStylesheets(Parent parent, String theme) {
        boolean hasLocalStylesheet = parent.getStylesheets().stream()
                .anyMatch(stylesheet -> !LIGHT_THEME.equals(stylesheet) && !DARK_THEME.equals(stylesheet));
        parent.getStylesheets().removeAll(LIGHT_THEME, DARK_THEME);
        if (hasLocalStylesheet) {
            // 控件自身的样式表优先级高于 Scene 样式表，主题需要在其后加载。
            parent.getStylesheets().add(theme);
        }
        for (Node child : parent.getChildrenUnmodifiable()) {
            if (child instanceof Parent childParent) {
                applyThemeToLocalStylesheets(childParent, theme);
            }
        }
    }

    private static Stage createStage(String resource) {
        try {
            Stage stage = new Stage();
            FXMLLoader fxmlLoader = new FXMLLoader();
            fxmlLoader.setLocation(App.class.getResource(resource));
            Parent pane = fxmlLoader.load();
            Scene scene = new Scene(pane);
            applyTheme(scene);
            stage.setScene(scene);
            return stage;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static Stage getEngineAdd() {
        return engineAdd;
    }

    public static Stage getEngineDialog() {
        return engineSetting;
    }

    public static Stage getMainStage() {
        return mainStage;
    }

    public static Stage getLocalBookSetting() {
        return localBookSetting;
    }

    private static String themeResource(String resource) {
        URL url = App.class.getResource(resource);
        if (url == null) {
            throw new IllegalStateException("找不到主题样式文件: " + resource);
        }
        return url.toExternalForm();
    }
}

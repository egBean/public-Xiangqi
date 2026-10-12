package com.sojourners.chess.controller;

import com.sojourners.chess.App;
import com.sojourners.chess.util.DialogUtils;
import com.sojourners.chess.util.PathUtils;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundRepeat;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

import java.io.File;

/**
 * 启动主菜单：对弈 / 打谱 / 训练 三个入口。
 *
 * <p>背景图取 {@code skin/menuBack.png}（按 cover 居中铺满整个窗口），
 * 三个入口按钮做“磨砂玻璃”处理：在按钮下方插入一层与背景图完全对齐的高斯模糊图层，
 * 按钮文字保持清晰并带投影，避免和背景图混在一起看不清。</p>
 */
public class MenuController {

    /** 磨砂高斯模糊半径 */
    private static final double FROST_BLUR = 18;
    /** 磨砂层圆角半径（与按钮 CSS 的 -fx-background-radius 保持一致） */
    private static final double FROST_ARC = 16;
    /** 磨砂层向外扩的边距（像素），避免模糊在边缘出现透明软边 */
    private static final double FROST_MARGIN = 24;

    @FXML
    private StackPane rootPane;
    @FXML
    private StackPane playButtonWrap;
    @FXML
    private StackPane manualButtonWrap;
    @FXML
    private StackPane trainButtonWrap;

    /** 主菜单背景图（skin/menuBack.png），不存在时为 null */
    private Image backgroundImage;

    @FXML
    private void initialize() {
        applyBackgroundImage();
        if (backgroundImage != null) {
            installFrostedGlass(playButtonWrap);
            installFrostedGlass(manualButtonWrap);
            installFrostedGlass(trainButtonWrap);
        }
    }

    @FXML
    void playButtonClick(ActionEvent event) {
        App.showGame();
    }

    @FXML
    void manualButtonClick(ActionEvent event) {
        App.showManual();
    }

    @FXML
    void trainButtonClick(ActionEvent event) {
        DialogUtils.showInfoDialog("训练", "训练模式开发中，敬请期待。");
    }

    // ===================== 背景图 =====================

    /** 若 skin/menuBack.png 存在，则作为整个主菜单的背景（cover 居中铺满）。 */
    private void applyBackgroundImage() {
        backgroundImage = loadBackgroundImage();
        if (rootPane == null || backgroundImage == null) {
            return;
        }
        BackgroundSize size = new BackgroundSize(1.0, 1.0, true, true, false, true);
        BackgroundImage bg = new BackgroundImage(backgroundImage, BackgroundRepeat.NO_REPEAT,
                BackgroundRepeat.NO_REPEAT, BackgroundPosition.CENTER, size);
        rootPane.setBackground(new Background(bg));
    }

    /** 查找主菜单背景图 skin/menuBack.png（兼容 IDE 工作目录与打包后的 jar 目录）。 */
    private Image loadBackgroundImage() {
        File file = new File("skin", "menuBack.png");
        if (!file.isFile()) {
            String jarPath = PathUtils.getJarPath();
            if (jarPath != null) {
                file = new File(jarPath, "skin/menuBack.png");
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

    // ===================== 磨砂（高斯模糊）按钮 =====================

    /**
     * 在按钮所在的 StackPane 最底层插入一层与主背景图对齐、经高斯模糊的 ImageView，
     * 形成“毛玻璃”效果；按钮自身的半透明底色与描边保留作为颜色罩与描边。
     */
    private void installFrostedGlass(StackPane wrap) {
        if (wrap == null || backgroundImage == null) {
            return;
        }
        ImageView frost = new ImageView(backgroundImage);
        frost.setManaged(false);
        frost.setPreserveRatio(false);
        frost.setMouseTransparent(true);
        frost.setOpacity(0.85);
        frost.setEffect(new GaussianBlur(FROST_BLUR));
        Rectangle clip = new Rectangle();
        clip.setArcWidth(FROST_ARC * 2);
        clip.setArcHeight(FROST_ARC * 2);
        frost.setClip(clip);
        wrap.getChildren().add(0, frost);

        Runnable update = () -> updateFrostedGlass(wrap, frost, clip);
        wrap.widthProperty().addListener((obs, o, n) -> update.run());
        wrap.heightProperty().addListener((obs, o, n) -> update.run());
        wrap.localToSceneTransformProperty().addListener((obs, o, n) -> update.run());
        wrap.sceneProperty().addListener((obs, o, n) -> update.run());
        Platform.runLater(update);
    }

    /** 根据按钮卡片在背景图中的位置，同步磨砂图层的放大区域（与 cover 背景对齐）。 */
    private void updateFrostedGlass(StackPane wrap, ImageView frost, Rectangle clip) {
        if (backgroundImage == null || rootPane == null || wrap.getScene() == null) {
            return;
        }
        double pw = wrap.getWidth();
        double ph = wrap.getHeight();
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
        Point2D p = rootPane.sceneToLocal(wrap.localToScene(0, 0));
        double vx = (p.getX() - offX) / scale;
        double vy = (p.getY() - offY) / scale;
        double vw = pw / scale;
        double vh = ph / scale;
        // 向外扩一圈，避免模糊在视口边缘出现透明软边
        double m = FROST_MARGIN / scale;
        double x0 = Math.max(0, vx - m);
        double y0 = Math.max(0, vy - m);
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
        clip.setX((vx - x0) * scale);
        clip.setY((vy - y0) * scale);
        clip.setWidth(Math.max(0, pw));
        clip.setHeight(Math.max(0, ph));
    }
}

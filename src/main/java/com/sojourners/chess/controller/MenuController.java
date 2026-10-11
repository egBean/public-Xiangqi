package com.sojourners.chess.controller;

import com.sojourners.chess.App;
import com.sojourners.chess.util.DialogUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;

/**
 * 启动主菜单：对弈 / 打谱 / 训练 三个入口。
 */
public class MenuController {

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
}

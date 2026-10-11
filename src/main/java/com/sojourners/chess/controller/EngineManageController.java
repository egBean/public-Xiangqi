package com.sojourners.chess.controller;

import com.sojourners.chess.App;
import com.sojourners.chess.config.Properties;
import com.sojourners.chess.model.EngineConfig;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

public class EngineManageController {

    /**
     * 引擎管理对话框操作的目标列表。为 null 时操作打谱模式的全局引擎列表；
     * 对弈模式会传入 {@code GameConfig#getEngineConfigList()} 以实现数据隔离。
     */
    public static java.util.List<EngineConfig> targetEngineList;

    /** 目标列表变更后的保存回调。为 null 时保存全局配置。 */
    public static Runnable onEngineListChanged;

    @FXML
    private TableView table;

    private Properties prop;

    /** 当前操作的目标引擎列表 */
    public static java.util.List<EngineConfig> engineList() {
        return targetEngineList != null ? targetEngineList : Properties.getInstance().getEngineConfigList();
    }

    /** 通知引擎列表发生变更并持久化。 */
    public static void notifyChanged() {
        if (onEngineListChanged != null) {
            onEngineListChanged.run();
        } else {
            Properties.getInstance().save();
        }
    }


    @FXML
    void addButtonClick(ActionEvent e) {
        EngineAddController.ec = null;
        App.openEngineAdd();
        refreshTable();
    }

    @FXML
    void editButtonClick(ActionEvent e) {
        EngineConfig ec = (EngineConfig) table.getSelectionModel().getSelectedItem();
        if (ec != null) {
            EngineAddController.ec = ec;
            App.openEngineAdd();
            refreshTable();
        }
    }

    @FXML
    void deleteButtonClick(ActionEvent event) {
        int index = table.getSelectionModel().getSelectedIndex();
        if (index >= 0) {
            EngineConfig e = engineList().remove(index);
            if (targetEngineList == null && prop.getEngineName().equals(e.getName())) {
                prop.setEngineName("");
            }
            notifyChanged();
            refreshTable();
        }
    }

    private void refreshTable() {
        table.getItems().clear();
        for (EngineConfig ec : engineList()) {
            table.getItems().add(ec);
        }
    }

    public void initialize() {

        TableColumn nameCol = (TableColumn) table.getColumns().get(0);
        nameCol.setCellValueFactory(new PropertyValueFactory<EngineConfig, String>("name"));
        TableColumn pathCol = (TableColumn) table.getColumns().get(1);
        pathCol.setCellValueFactory(new PropertyValueFactory<EngineConfig, String>("path"));
        TableColumn proCol = (TableColumn) table.getColumns().get(2);
        proCol.setCellValueFactory(new PropertyValueFactory<EngineConfig, String>("protocol"));

        prop = Properties.getInstance();

        refreshTable();

    }
}

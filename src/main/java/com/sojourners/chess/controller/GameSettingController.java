package com.sojourners.chess.controller;

import com.sojourners.chess.App;
import com.sojourners.chess.model.EngineConfig;
import com.sojourners.chess.model.GameConfig;
import com.sojourners.chess.openbook.MoveRule;
import com.sojourners.chess.util.PathUtils;
import com.sojourners.chess.util.StringUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * 对弈设置对话框：引擎、引擎高级参数（每步层数 / 线程 / Hash）与对弈专用开局库。
 *
 * 界面上的修改先写入草稿，点击“保存”时才写回 {@link GameConfig} 并持久化；
 * “取消”不会保存任何修改。
 */
public class GameSettingController {

    @FXML
    private ComboBox<String> engineComboBox;
    @FXML
    private Button engineManageButton;
    @FXML
    private TextField depthField;
    @FXML
    private ComboBox<String> threadComboBox;
    @FXML
    private ComboBox<String> hashComboBox;
    @FXML
    private CheckBox useBookCheckBox;
    @FXML
    private ComboBox<String> bookComboBox;
    @FXML
    private ComboBox<String> moveRuleComboBox;
    @FXML
    private TextField offManualStepsField;
    @FXML
    private Button bookSelectButton;
    @FXML
    private Button bookDeleteButton;

    /** 每步层数限制范围：最高 20 层、最低 10 层（限制引擎思考时间） */
    private static final int DEPTH_MIN = 10;
    private static final int DEPTH_MAX = 20;

    private GameConfig config;

    // 草稿值
    private String draftEngineName;
    private int draftDepth;
    private int draftThread;
    private int draftHash;
    private boolean draftUseBook;
    private MoveRule draftMoveRule;
    private int draftOffManualSteps;
    private List<EngineConfig> draftEngineList;
    private List<String> draftBookList;
    private String draftBookPath;

    public void init(GameConfig config) {
        this.config = config;
        this.draftEngineName = config.getEngineName();
        this.draftDepth = config.getDepth();
        this.draftThread = config.getThreadNum();
        this.draftHash = config.getHashSize();
        this.draftUseBook = config.isUseBook();
        this.draftMoveRule = config.getMoveRule();
        this.draftOffManualSteps = config.getOffManualSteps();
        this.draftEngineList = copyEngineList(config.getEngineConfigList());
        this.draftBookList = new ArrayList<>(config.getOpenBookList());
        this.draftBookPath = config.getBookPath();

        initEngineCombo();
        initDepth();
        initThreadAndHash();
        initBook();
        initMoveRule();
        initListeners();
    }

    // ===================== 初始化 =====================

    private void initEngineCombo() {
        engineComboBox.getItems().clear();
        for (EngineConfig ec : draftEngineList) {
            engineComboBox.getItems().add(ec.getName());
        }
        String name = draftEngineName;
        if (StringUtils.isNotEmpty(name) && engineComboBox.getItems().contains(name)) {
            engineComboBox.setValue(name);
        } else if (!draftEngineList.isEmpty()) {
            engineComboBox.setValue(draftEngineList.get(0).getName());
        } else {
            engineComboBox.setValue(null);
        }
    }

    private void initDepth() {
        draftDepth = Math.max(DEPTH_MIN, Math.min(DEPTH_MAX, draftDepth));
        depthField.setText(String.valueOf(draftDepth));
    }

    private void initThreadAndHash() {
        threadComboBox.getItems().clear();
        for (int i = 1; i <= Runtime.getRuntime().availableProcessors(); i++) {
            threadComboBox.getItems().add(String.valueOf(i));
        }
        String t = String.valueOf(draftThread);
        if (!threadComboBox.getItems().contains(t)) {
            threadComboBox.getItems().add(t);
        }
        threadComboBox.setValue(t);

        hashComboBox.getItems().clear();
        hashComboBox.getItems().addAll("128", "256", "512", "1024", "2048", "4096");
        String h = String.valueOf(draftHash);
        if (!hashComboBox.getItems().contains(h)) {
            hashComboBox.getItems().add(h);
        }
        hashComboBox.setValue(h);
    }

    private void initBook() {
        useBookCheckBox.setSelected(draftUseBook);
        refreshBookCombo();
    }

    private void initMoveRule() {
        moveRuleComboBox.getItems().clear();
        moveRuleComboBox.getItems().addAll("取最高分", "取最高胜率", "正分数随机", "完全随机");
        moveRuleComboBox.setValue(ruleTitle(draftMoveRule));
        offManualStepsField.setText(String.valueOf(draftOffManualSteps));
    }

    private void refreshBookCombo() {
        bookComboBox.getItems().clear();
        for (String path : draftBookList) {
            File f = new File(path);
            // 不存在的库标记为已失效，方便识别与删除
            bookComboBox.getItems().add(f.getName() + (f.exists() ? "" : "（已失效）"));
        }
        if (draftBookList.isEmpty()) {
            draftBookPath = "";
            bookComboBox.setValue(null);
            return;
        }
        int idx = draftBookPath == null ? -1 : draftBookList.indexOf(draftBookPath);
        if (idx < 0) {
            idx = 0;
            draftBookPath = draftBookList.get(0);
        }
        bookComboBox.getSelectionModel().select(idx);
        bookComboBox.setTooltip(new Tooltip(draftBookPath));
    }

    private void initListeners() {
        engineComboBox.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) {
                draftEngineName = n;
            }
        });

        depthField.focusedProperty().addListener((obs, o, n) -> {
            if (!n) {
                readDepth();
            }
        });
        depthField.setOnAction(e -> readDepth());

        threadComboBox.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null && StringUtils.isPositiveInt(n)) {
                draftThread = Integer.parseInt(n);
            }
        });

        hashComboBox.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null && StringUtils.isPositiveInt(n)) {
                draftHash = Integer.parseInt(n);
            }
        });

        useBookCheckBox.selectedProperty().addListener((obs, o, n) -> draftUseBook = n);

        // 切换下拉框即选择当前对弈使用的开局库
        bookComboBox.getSelectionModel().selectedIndexProperty().addListener((obs, o, n) -> {
            int idx = n == null ? -1 : n.intValue();
            if (idx >= 0 && idx < draftBookList.size()) {
                draftBookPath = draftBookList.get(idx);
                bookComboBox.setTooltip(new Tooltip(draftBookPath));
            }
        });

        offManualStepsField.focusedProperty().addListener((obs, o, n) -> {
            if (!n) {
                readOffManualSteps();
            }
        });
        offManualStepsField.setOnAction(e -> readOffManualSteps());

        moveRuleComboBox.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> {
            if (n != null) {
                draftMoveRule = ruleFromTitle(n);
            }
        });
    }

    private void readOffManualSteps() {
        String txt = offManualStepsField.getText();
        if (txt != null && StringUtils.isPositiveInt(txt.trim())) {
            draftOffManualSteps = Math.max(1, Integer.parseInt(txt.trim()));
        } else {
            offManualStepsField.setText(String.valueOf(draftOffManualSteps));
        }
    }

    private void readDepth() {
        String txt = depthField.getText();
        if (txt != null && StringUtils.isPositiveInt(txt.trim())) {
            int d = Integer.parseInt(txt.trim());
            draftDepth = Math.max(DEPTH_MIN, Math.min(DEPTH_MAX, d));
        }
        // 无论输入是否合法，都回显限制后的值
        depthField.setText(String.valueOf(draftDepth));
    }

    // ===================== 事件 =====================

    @FXML
    void engineManageClick(ActionEvent e) {
        // 引擎管理只修改草稿列表，取消设置时一并丢弃
        App.openEngineDialog(draftEngineList, () -> {
        });
        refreshEngineCombo();
        if (engineComboBox.getValue() != null) {
            draftEngineName = engineComboBox.getValue();
        }
    }

    private void refreshEngineCombo() {
        String cur = engineComboBox.getValue();
        engineComboBox.getItems().clear();
        for (EngineConfig ec : draftEngineList) {
            engineComboBox.getItems().add(ec.getName());
        }
        if (cur != null && engineComboBox.getItems().contains(cur)) {
            engineComboBox.setValue(cur);
        } else if (!draftEngineList.isEmpty()) {
            engineComboBox.setValue(draftEngineList.get(0).getName());
        } else {
            engineComboBox.setValue(null);
            draftEngineName = "";
        }
    }

    @FXML
    void bookSelectButtonClick(ActionEvent e) {
        FileChooser fc = new FileChooser();
        fc.setTitle("选择开局库");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("开局库文件", "*.xqb", "*.obk", "*.pfBook"));
        fc.setInitialDirectory(new File(PathUtils.getJarPath()));
        List<File> files = fc.showOpenMultipleDialog(App.getMainStage());
        if (files == null) {
            return;
        }
        for (File f : files) {
            String p = f.getAbsolutePath();
            if (!draftBookList.contains(p)) {
                draftBookList.add(p);
            }
        }
        if (draftBookPath == null || draftBookPath.isBlank()) {
            draftBookPath = draftBookList.get(0);
        }
        refreshBookCombo();
    }

    @FXML
    void bookDeleteButtonClick(ActionEvent e) {
        int idx = bookComboBox.getSelectionModel().getSelectedIndex();
        if (idx < 0 || idx >= draftBookList.size()) {
            return;
        }
        draftBookList.remove(idx);
        if (draftBookList.isEmpty()) {
            draftBookPath = "";
        } else if (!draftBookList.contains(draftBookPath)) {
            draftBookPath = draftBookList.get(0);
        }
        refreshBookCombo();
    }

    @FXML
    void saveButtonClick(ActionEvent e) {
        // 防止深度输入框尚未来得及提交
        readDepth();
        readOffManualSteps();

        config.setDepth(draftDepth);
        config.setThreadNum(draftThread);
        config.setHashSize(draftHash);
        config.setUseBook(draftUseBook);
        config.setMoveRule(draftMoveRule);
        config.setOffManualSteps(draftOffManualSteps);
        config.setEngineConfigList(draftEngineList);
        config.setOpenBookList(draftBookList);
        config.setBookPath(draftBookPath);

        EngineConfig base = findEngine(draftEngineName);
        if (base != null) {
            config.copyEngineIdentity(base);
            config.getEngineOptions().put("Skill Level", String.valueOf(config.getSkillLevel()));
        } else {
            config.setEngineName("");
            config.setEnginePath("");
            config.setEngineProtocol("");
        }

        config.save();
        App.closeGameSetting();
    }

    @FXML
    void cancelButtonClick(ActionEvent e) {
        // 不保存任何修改
        App.closeGameSetting();
    }

    // ===================== 辅助 =====================

    private EngineConfig findEngine(String name) {
        if (name == null) {
            return null;
        }
        for (EngineConfig ec : draftEngineList) {
            if (name.equals(ec.getName())) {
                return ec;
            }
        }
        return null;
    }

    private List<EngineConfig> copyEngineList(List<EngineConfig> src) {
        List<EngineConfig> copy = new ArrayList<>();
        if (src != null) {
            for (EngineConfig ec : src) {
                LinkedHashMap<String, String> opts = new LinkedHashMap<>();
                if (ec.getOptions() != null) {
                    opts.putAll(ec.getOptions());
                }
                copy.add(new EngineConfig(ec.getName(), ec.getPath(), ec.getProtocol(), opts));
            }
        }
        return copy;
    }

    private String ruleTitle(MoveRule mr) {
        return switch (mr) {
            case BEST_SCORE -> "取最高分";
            case BEST_WINRATE -> "取最高胜率";
            case POSITIVE_RANDOM -> "正分数随机";
            case FULL_RANDOM -> "完全随机";
        };
    }

    private MoveRule ruleFromTitle(String title) {
        return switch (title) {
            case "取最高胜率" -> MoveRule.BEST_WINRATE;
            case "正分数随机" -> MoveRule.POSITIVE_RANDOM;
            case "完全随机" -> MoveRule.FULL_RANDOM;
            default -> MoveRule.BEST_SCORE;
        };
    }
}

package com.sojourners.chess.controller;

import com.sojourners.chess.config.Properties;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.util.Locale;

public class ColorSettingController {

    @FXML
    private ColorPicker firstStepColor;
    @FXML
    private Spinner<Integer> firstStepOpacity;
    @FXML
    private ColorPicker firstStepNumberColor;

    @FXML
    private ColorPicker secondStepColor;
    @FXML
    private Spinner<Integer> secondStepOpacity;
    @FXML
    private ColorPicker secondStepNumberColor;

    @FXML
    private ColorPicker branchStepColor;
    @FXML
    private Spinner<Integer> branchStepOpacity;
    @FXML
    private ColorPicker branchStepNumberColor;

    @FXML
    private RadioButton shadowPiece;
    @FXML
    private RadioButton noShadowPiece;

    @FXML
    private RadioButton lightTheme;
    @FXML
    private RadioButton darkTheme;
    @FXML
    private Button saveButton;

    @FXML private Spinner<Double> pieceOffsetX;
    @FXML private Spinner<Double> pieceOffsetY;

    @FXML private Spinner<Integer> boardOffsetX;

    @FXML private Spinner<Double> pieceScale;

    // 默认偏移量（相对棋子半径的比例）
    private static final double DEFAULT_OFFSET_X = 0.0;
    private static final double DEFAULT_OFFSET_Y = 0.0;

    private final Properties prop = Properties.getInstance();
    private boolean saved;

    @FXML
    public void initialize() {
        configureOpacity(firstStepOpacity, prop.getFirstStepOpacity());
        configureOpacity(secondStepOpacity, prop.getSecondStepOpacity());
        configureOpacity(branchStepOpacity, prop.getBranchStepOpacity());

        firstStepColor.setValue(Color.web(prop.getFirstStepColor()));
        firstStepNumberColor.setValue(Color.web(prop.getFirstStepNumberColor()));
        secondStepColor.setValue(Color.web(prop.getSecondStepColor()));
        secondStepNumberColor.setValue(Color.web(prop.getSecondStepNumberColor()));
        branchStepColor.setValue(Color.web(prop.getBranchStepColor()));
        branchStepNumberColor.setValue(Color.web(prop.getBranchStepNumberColor()));

        if (prop.getColorTheme() == Properties.ColorTheme.DARK) {
            darkTheme.setSelected(true);
        } else {
            lightTheme.setSelected(true);
        }

        if (prop.getPieceShadow() == 0) {
            noShadowPiece.setSelected(true);
        } else {
            shadowPiece.setSelected(true);
        }
        configureOpacityDouble(pieceOffsetX, prop.getPieceOffsetX(),-1.0,1.0);
        configureOpacityDouble(pieceOffsetY, prop.getPieceOffsetY(),-1.0,1.0);

        configureBoardOffset(boardOffsetX, prop.getBoardOffsetX(),1000);

        configureOpacityDouble(pieceScale, prop.getPieceScale(),0.1,2.0);
    }

    @FXML
    private void resetFirstStep() {
        firstStepColor.setValue(Color.web(Properties.DEFAULT_FIRST_STEP_COLOR));
        firstStepOpacity.getValueFactory().setValue(toPercent(Properties.DEFAULT_STEP_OPACITY));
        firstStepNumberColor.setValue(Color.web(Properties.DEFAULT_STEP_NUMBER_COLOR));
    }

    @FXML
    private void resetSecondStep() {
        secondStepColor.setValue(Color.web(Properties.DEFAULT_SECOND_STEP_COLOR));
        secondStepOpacity.getValueFactory().setValue(toPercent(Properties.DEFAULT_STEP_OPACITY));
        secondStepNumberColor.setValue(Color.web(Properties.DEFAULT_STEP_NUMBER_COLOR));
    }

    @FXML
    private void resetBranchStep() {
        branchStepColor.setValue(Color.web(Properties.DEFAULT_BRANCH_STEP_COLOR));
        branchStepOpacity.getValueFactory().setValue(toPercent(Properties.DEFAULT_STEP_OPACITY));
        branchStepNumberColor.setValue(Color.web(Properties.DEFAULT_STEP_NUMBER_COLOR));
    }

    @FXML
    private void resetTheme() {
        lightTheme.setSelected(true);
    }

    @FXML
    private void resetPieceShadow() {
        noShadowPiece.setSelected(true);
    }

    @FXML
    public void resetPieceOffset() {
        pieceOffsetX.getValueFactory().setValue(DEFAULT_OFFSET_X);
        pieceOffsetY.getValueFactory().setValue(DEFAULT_OFFSET_Y);
        boardOffsetX.getValueFactory().setValue(0);
        pieceScale.getValueFactory().setValue(1.00);
    }

    @FXML
    private void save() {
        prop.setFirstStepColor(toHex(firstStepColor.getValue()));
        prop.setFirstStepOpacity(toOpacity(firstStepOpacity));
        prop.setFirstStepNumberColor(toHex(firstStepNumberColor.getValue()));

        prop.setSecondStepColor(toHex(secondStepColor.getValue()));
        prop.setSecondStepOpacity(toOpacity(secondStepOpacity));
        prop.setSecondStepNumberColor(toHex(secondStepNumberColor.getValue()));

        prop.setBranchStepColor(toHex(branchStepColor.getValue()));
        prop.setBranchStepOpacity(toOpacity(branchStepOpacity));
        prop.setBranchStepNumberColor(toHex(branchStepNumberColor.getValue()));

        prop.setColorTheme(darkTheme.isSelected() ? Properties.ColorTheme.DARK : Properties.ColorTheme.LIGHT);
        prop.setPieceShadow(noShadowPiece.isSelected()?0: 1);

        prop.setPieceOffsetX(pieceOffsetX.getValue()==null?0:pieceOffsetX.getValue());
        prop.setPieceOffsetY(pieceOffsetY.getValue()==null?0:pieceOffsetY.getValue());

        prop.setPieceScale(pieceScale.getValue()==null?0:pieceScale.getValue());
        prop.setBoardOffsetX(boardOffsetX.getValue() == null ? 0 : boardOffsetX.getValue());
        prop.save();

        saved = true;
        close();
    }

    @FXML
    private void cancel() {
        close();
    }

    public boolean isSaved() {
        return saved;
    }

    private void configureOpacity(Spinner<Integer> spinner, double opacity) {
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100, toPercent(opacity), 5));
        spinner.getEditor().setTextFormatter(new javafx.scene.control.TextFormatter<>(change ->
                change.getControlNewText().matches("\\d{0,3}") ? change : null));
    }

    private void configureBoardOffset(Spinner<Integer> spinner, Integer opacity,Integer maxLimit) {
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, maxLimit, opacity, 5));
        spinner.getEditor().setTextFormatter(new javafx.scene.control.TextFormatter<>(change ->
                change.getControlNewText().matches("\\d{0,4}") ? change : null));
    }


    private void configureOpacityDouble(Spinner<Double> spinner, double value,Double minLimit,Double maxLimit) {
        spinner.setValueFactory(new SpinnerValueFactory.DoubleSpinnerValueFactory(minLimit, maxLimit, value, 0.01));
        spinner.getEditor().setTextFormatter(new javafx.scene.control.TextFormatter<>(change -> {
            String newText = change.getControlNewText();
            // 允许：空、负号、纯数字、带小数点的数字（最多两位小数）
            if (newText.isEmpty() || newText.equals("-") || newText.equals(".") || newText.equals("-.")) {
                return change;
            }
            return newText.matches("-?\\d{0,1}(\\.\\d{0,2})?") ? change : null;
        }));
    }

    private int toPercent(double opacity) {
        return (int) Math.round(opacity * 100d);
    }

    private double toOpacity(Spinner<Integer> spinner) {
        try {
            int percent = Integer.parseInt(spinner.getEditor().getText());
            percent = Math.max(0, Math.min(100, percent));
            spinner.getValueFactory().setValue(percent);
            return percent / 100d;
        } catch (NumberFormatException e) {
            return spinner.getValue() / 100d;
        }
    }

    private String toHex(Color color) {
        return String.format(Locale.ROOT, "#%02X%02X%02X",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }

    private void close() {
        ((Stage) saveButton.getScene().getWindow()).close();
    }
}
